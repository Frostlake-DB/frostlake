/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import net.snowflake.client.api.statement.SnowflakeStatement;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A {@link DatabaseEngine} whose SQL surface ({@code execute} / {@code executeQuery} /
 * {@code executeUpdate}) runs against a LIVE Snowflake account over JDBC — the {@code SF_LIVE=1}
 * backend for the test base classes, letting the same suite validate Frostlake and Snowflake give
 * the same answers. Only the SQL entry points are rerouted; internal accessors (catalog, managers,
 * function registry) still belong to the embedded engine this class inherits, so tests that poke
 * engine internals exercise Frostlake even in live mode.
 *
 * <p>Engine-style multi-statement scripts are split with the engine's OWN parser and submitted one
 * statement at a time. Snowflake's server-side multi-statement splitter mis-splits unquoted
 * procedure bodies at their internal semicolons; the engine's grammar understands
 * {@code BEGIN…END}, {@code $$} bodies and string literals, so its statement boundaries are the
 * ones the embedded engine itself would execute. Text the engine cannot parse is submitted as-is.
 *
 * <p>JDBC values are coerced toward the engine's runtime shapes so assertions written against
 * Frostlake keep working: temporals become {@code java.time} values (locals, and a zoned timestamp the offset
 * value the engine hands back, see {@link #zonedCell}), BINARY becomes
 * {@link BinaryValue}, whole {@code BigDecimal}s become {@code Long}, and semi-structured columns'
 * pretty-printed JSON is re-serialized compactly (Snowflake pretty-prints VARIANT output over
 * JDBC; the engine's canonical text is compact). Every statement is read through
 * {@code executeQuery}, which hands back the account's own result for it — the rows of a query, the
 * count grid of a DML statement ({@code number of rows inserted}, the UPDATE pair, MERGE's per-action
 * columns), and the one-row status sentence of everything else — exactly the shapes the embedded
 * engine returns. A DML grid also carries the engine's affected-row mark, so {@code executeUpdate}
 * counts what the embedded engine counts. Statement errors surface as {@link RuntimeException}s,
 * matching the embedded engine's throw-on-error contract.
 */
public class LiveSnowflakeEngine extends DatabaseEngine {

    /** Whether the test's teardown runs, which passes over a statement held back rather than skip the test. */
    private boolean tearingDown;

    /** Marks the test's teardown begun: a statement held back from the account is passed over from now on. */
    public void beginTeardown() {
        tearingDown = true;
    }

    @Override
    public ExecutionResult execute(final String sql) {
        // A statement that would change the account's roles, users or grants is not sent, nor one that would make a new
        // account (see LiveAccountStatements).
        final String heldBack = LiveAccountStatements.sent() ? LiveAccountStatements.neverSent(sql)
            : LiveAccountStatements.heldBack(sql);
        if (heldBack != null) {
            if (tearingDown) {
                return new ExecutionResult(true, new ArrayList<ResultSet>(), null, null);
            }
            LiveAccountStatements.skipOver(heldBack);
        }
        markDirtyIfSessionMutating(sql);
        final List<ResultSet> results = new ArrayList<ResultSet>();
        String queryId = null;
        final List<String> script = scriptStatements(sql);
        if (script != null) {
            for (final String statementText : script) {
                markIfAccountObjectStatement(statementText);
                LiveAccountObjects.observe(statementText);
            }
            return new ExecutionResult(true, results, null, executeScript(sql, script.size(), results));
        }
        for (final String statementText : splitStatements(sql)) {
            markIfAccountObjectStatement(statementText);
            LiveAccountObjects.observe(statementText);
            queryId = executeOne(statementText, results);
        }
        return new ExecutionResult(true, results, null, queryId);
    }

    /**
     * Runs a script of several statements as ONE submission, the way a client sends one: under a statement
     * count that matches it. The account then runs it as a script — one statement's failure ends it, named in
     * the account's own multi-statement sentence — which is what the embedded engine is measured against when a
     * test hands it a script. Each child's result is recorded as the driver hands it over: a result set as it
     * is, and a statement that has none as the count the driver reports for it.
     */
    private String executeScript(final String sql, final int statements, final List<ResultSet> results) {
        try (final Statement statement = LiveSnowflake.shared().createStatement()) {
            if (statement.isWrapperFor(SnowflakeStatement.class)) {
                statement.unwrap(SnowflakeStatement.class).setParameter("MULTI_STATEMENT_COUNT", statements);
            }
            boolean answersRows = statement.execute(sql);
            final String queryId = queryIdOf(statement);
            for (int child = 0; child < statements; child++) {
                if (child > 0) {
                    answersRows = statement.getMoreResults();
                }
                // A statement that answers no rows — a BEGIN, a DDL — hands over no result set at all.
                final java.sql.ResultSet rs = answersRows ? statement.getResultSet() : null;
                if (rs != null) {
                    try {
                        results.add(SnowflakeDriverRows.convert(rs));
                    } finally {
                        rs.close();
                    }
                    continue;
                }
                final int count = statement.getUpdateCount();
                if (count < 0) {
                    continue;
                }
                final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
                columns.add(new ResultSetColumn("number of rows affected", NumericType.NUMBER));
                final List<Row> rows = new ArrayList<Row>();
                final List<Object> values = new ArrayList<Object>();
                values.add(Long.valueOf(count));
                rows.add(new Row(values));
                results.add(new ResultSet(columns, rows));
            }
            return queryId;
        } catch (final SQLException e) {
            LiveSnowflake.markSharedDirty();   // a failed script can abandon a transaction mid-flight
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Runs one statement and records the account's result for it. Every submission is ONE statement —
     * {@link LiveSnowflake#pinSingleStatement} pins the statement count, so a text holding several is
     * refused before anything runs — and the account answers every single statement with a result:
     * its rows, a DML statement's count grid, or the status sentence of anything else
     * ({@code Table T successfully created.}, {@code Statement executed successfully.}).
     * {@code executeQuery} surfaces that result as it is; {@code execute()} would hide the last two
     * behind a bare update count (0 for every DDL and session statement), which is not what the
     * embedded engine answers.
     */
    private String executeOne(final String sql, final List<ResultSet> results) {
        try (final Statement statement = LiveSnowflake.shared().createStatement()) {
            LiveSnowflake.pinSingleStatement(statement);
            try (final java.sql.ResultSet rs = statement.executeQuery(sql)) {
                results.add(SnowflakeDriverRows.markAffectedRows(sql, SnowflakeDriverRows.convert(rs)));
            }
            return queryIdOf(statement);
        } catch (final SQLException e) {
            LiveSnowflake.markSharedDirty();   // a failed script can abandon a transaction mid-flight
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String queryIdOf(final Statement statement) {
        try {
            if (statement.isWrapperFor(SnowflakeStatement.class)) {
                return statement.unwrap(SnowflakeStatement.class).getQueryID();
            }
        } catch (final SQLException ignored) {
            // Query-id decoration only — never fail the statement over it.
        }
        return null;
    }

    /**
     * The statements of a text that is a script: two or more statements, every one but the last ended by its
     * own semicolon, as the engine's grammar reads them. Null for a single statement, for text that does not
     * parse, and where the engine's parser finds two statements with nothing between them — that reading is
     * the engine's own and may be exactly the divergence under test, so such text goes over as one statement.
     */
    private List<String> scriptStatements(final String sql) {
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final FrostlakeParser parser = new FrostlakeParser(tokens);
            final boolean[] failed = new boolean[1];
            final BaseErrorListener silent = new BaseErrorListener() {
                @Override
                public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                        final int line, final int charPositionInLine, final String msg,
                        final RecognitionException e) {
                    failed[0] = true;
                }
            };
            lexer.removeErrorListeners();
            lexer.addErrorListener(silent);
            parser.removeErrorListeners();
            parser.addErrorListener(silent);
            final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
            if (failed[0] || script.flowChain().size() < 2) {
                return null;
            }
            final List<String> statements = new ArrayList<String>();
            for (int i = 0; i < script.flowChain().size(); i++) {
                final FrostlakeParser.FlowChainContext chain = script.flowChain().get(i);
                if (i > 0) {
                    final FrostlakeParser.FlowChainContext before = script.flowChain().get(i - 1);
                    final boolean ended = before.getStop().getType() == FrostlakeLexer.SEMI
                        || sql.substring(before.getStop().getStopIndex() + 1,
                            chain.getStart().getStartIndex()).indexOf(';') >= 0;
                    if (!ended) {
                        return null;
                    }
                }
                statements.add(sql.substring(chain.getStart().getStartIndex(), chain.getStop().getStopIndex() + 1));
            }
            return statements;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Splits a script into its top-level statements using the engine's grammar (each
     * {@code flowChain} — a statement, or a {@code ->>} chain that must stay together — is one
     * submission). Unparseable text is returned whole so Snowflake reports its own error.
     */
    private List<String> splitStatements(final String sql) {
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final FrostlakeParser parser = new FrostlakeParser(tokens);
            final boolean[] failed = new boolean[1];
            final BaseErrorListener silent = new BaseErrorListener() {
                @Override
                public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                        final int line, final int charPositionInLine, final String msg,
                        final RecognitionException e) {
                    failed[0] = true;
                }
            };
            lexer.removeErrorListeners();
            lexer.addErrorListener(silent);
            parser.removeErrorListeners();
            parser.addErrorListener(silent);
            final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
            if (failed[0] || script.flowChain().isEmpty()) {
                return Collections.singletonList(sql);
            }
            // A single statement goes over VERBATIM. Slicing it from its first token's start index
            // would drop the leading whitespace, and Snowflake reports a syntax error at the token's
            // own line and column — so the split would silently move the error being measured, and a
            // position test would read the account's answer to text it never sent.
            if (script.flowChain().size() == 1) {
                return Collections.singletonList(sql);
            }
            // Only text the SOURCE separates with a semicolon is split. Where the engine's parser
            // finds two statements with nothing between them, that reading is the engine's own and
            // may be exactly the divergence under test — forwarding the halves separately would ask
            // the account about text it was never given, and a wrong reading would come back green.
            final List<String> statements = new ArrayList<String>();
            for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
                if (!statements.isEmpty()) {
                    final int gapStart = script.flowChain().get(statements.size() - 1)
                        .getStop().getStopIndex() + 1;
                    if (sql.substring(gapStart, chain.getStart().getStartIndex()).indexOf(';') < 0) {
                        return Collections.singletonList(sql);
                    }
                }
                statements.add(sql.substring(chain.getStart().getStartIndex(),
                    chain.getStop().getStopIndex() + 1));
            }
            return statements;
        } catch (final RuntimeException e) {
            return Collections.singletonList(sql);
        }
    }

    /**
     * Marks the shared session dirty for statements that can leave transaction, autocommit, role or
     * warehouse state behind — detected from the statement's leading lexer tokens, so comments and
     * case don't fool it. The next test's setup then restores the session baseline.
     */
    private void markDirtyIfSessionMutating(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        Token first = lexer.nextToken();
        while (first.getType() != Token.EOF && first.getChannel() != Token.DEFAULT_CHANNEL) {
            first = lexer.nextToken();
        }
        if (first.getType() == Token.EOF) {
            return;
        }
        final String keyword = first.getText().toUpperCase(Locale.ROOT);
        // SET and UNSET name a session VARIABLE, which outlives the test that set it just as a role or
        // a parameter does — and nothing in the parameter baseline reaches one.
        if (keyword.equals("SET") || keyword.equals("UNSET")) {
            LiveSnowflake.markSharedDirty();
            return;
        }
        if (keyword.equals("BEGIN") || keyword.equals("START") || keyword.equals("ALTER")
                || keyword.equals("USE") || keyword.equals("CREATE") || keyword.equals("DROP")
                || keyword.equals("CALL") || keyword.equals("EXECUTE") || keyword.equals("DECLARE")) {
            final String detail = objectWord(lexer, keyword);
            final boolean mutating;
            switch (keyword) {
                case "BEGIN":
                case "START":
                case "DECLARE":
                    // BEGIN/BEGIN TRANSACTION/anonymous blocks: blocks can run transaction control
                    // and procedure calls, so treat all of them as potentially session-mutating.
                    mutating = true;
                    break;
                case "CALL":
                case "EXECUTE":
                    // Procedures and dynamic SQL can do anything — reset afterwards.
                    mutating = true;
                    break;
                case "ALTER":
                    mutating = detail.equals("SESSION");
                    break;
                case "USE":
                    mutating = detail.equals("ROLE") || detail.equals("WAREHOUSE")
                        || detail.equals("SECONDARY");
                    break;
                default:
                    // CREATE/DROP WAREHOUSE change the active warehouse (CREATE auto-uses it).
                    mutating = detail.equals("WAREHOUSE");
                    break;
            }
            if (mutating) {
                LiveSnowflake.markSharedDirty();
            }
        }
    }

    /** The word after a statement's verb, past the OR REPLACE or OR ALTER of a CREATE, which name no object. */
    private String objectWord(final FrostlakeLexer lexer, final String verb) {
        final Token next = SnowflakeDriverRows.nextDefaultToken(lexer);
        if (verb.equals("CREATE") && next.getText().equalsIgnoreCase("OR")) {
            SnowflakeDriverRows.nextDefaultToken(lexer);
            return SnowflakeDriverRows.nextDefaultToken(lexer).getText().toUpperCase(Locale.ROOT);
        }
        return next.getText().toUpperCase(Locale.ROOT);
    }

    /**
     * Flags statements that create, drop or alter an ACCOUNT-level object (database, role, user,
     * warehouse) so {@link BaseDatabaseTest}'s teardown knows the account-object diff in
     * {@link LiveAccountObjects} is worth running — the vast majority of tests touch nothing outside
     * {@code test_db} and must not pay for the {@code SHOW}s.
     *
     * <p>Read off the leading lexer tokens, skipping the modifiers that can sit between the verb and
     * the object kind ({@code CREATE OR REPLACE DATABASE}, {@code CREATE TRANSIENT DATABASE}), so
     * comments, case and whitespace cannot fool it and a table column merely NAMED {@code user}
     * cannot trip it.
     */
    private void markIfAccountObjectStatement(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        final Token verb = SnowflakeDriverRows.nextDefaultToken(lexer);
        if (verb.getType() == Token.EOF || !isObjectStatementVerb(verb.getText().toUpperCase(Locale.ROOT))) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            final Token token = SnowflakeDriverRows.nextDefaultToken(lexer);
            if (token.getType() == Token.EOF) {
                return;
            }
            final String text = token.getText().toUpperCase(Locale.ROOT);
            // NETWORK opens the one account-level kind two words name; a network rule's statement is
            // marked with it, which costs a listing and misses nothing.
            if (isAccountObjectKind(text) || "NETWORK".equals(text)) {
                LiveAccountObjects.markAccountObjectStatement();
                return;
            }
            if (!isObjectKindModifier(text)) {
                return;
            }
        }
    }

    private boolean isObjectStatementVerb(final String keyword) {
        switch (keyword) {
            case "CREATE":
            case "DROP":
            case "ALTER":
            case "UNDROP":
                return true;
            default:
                return false;
        }
    }

    private boolean isObjectKindModifier(final String keyword) {
        switch (keyword) {
            case "OR":
            case "REPLACE":
            case "TRANSIENT":
            case "TEMPORARY":
            case "TEMP":
            case "VOLATILE":
            case "SECURE":
            case "LOCAL":
            case "GLOBAL":
                return true;
            default:
                return false;
        }
    }

    private boolean isAccountObjectKind(final String keyword) {
        switch (keyword) {
            case "DATABASE":
            case "ROLE":
            case "USER":
            case "WAREHOUSE":
                return true;
            default:
                return false;
        }
    }

}
