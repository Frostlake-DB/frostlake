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
package dev.frostlake.http;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The statement type id the Snowflake driver reads from a query's answer, read from the statement's parse tree. The
 * driver takes a DML type's rows as the counts it reports, a type that answers no rows as an update count of 0, and
 * every other type as a result set: SELECT 0x1000, INSERT 0x3100, UPDATE 0x3200, DELETE 0x3300, MERGE 0x3400, a
 * multi-table INSERT 0x3500, COPY INTO a table 0x3600 and into a stage 0x3700, a session variable 0x4000, ALTER
 * SESSION 0x4100, USE 0x4300 (and 1, 2, 3 for a database, a schema, a warehouse), SHOW 0x4400, DESCRIBE 0x4500,
 * LIST 0x4701, a transaction's statement 0x5000, GET 0x7101, PUT 0x7102, REMOVE 0x7103, and any other DDL 0x6000.
 * A flow chain answers as its last statement, the one whose result it hands back.
 */
final class SnowflakeStatementTypes {

    static final long SELECT = 0x1000;
    private static final long INSERT = 0x3100;
    private static final long UPDATE = 0x3200;
    private static final long DELETE = 0x3300;
    private static final long MERGE = 0x3400;
    private static final long MULTI_INSERT = 0x3500;
    private static final long COPY = 0x3600;
    private static final long UNLOAD = 0x3700;
    static final long SCL = 0x4000;
    static final long ALTER_SESSION = 0x4100;
    private static final long USE = 0x4300;
    private static final long SHOW = 0x4400;
    private static final long DESCRIBE = 0x4500;
    private static final long LIST = 0x4701;
    private static final long GET = 0x7101;
    private static final long PUT = 0x7102;
    private static final long REMOVE = 0x7103;
    private static final long TCL = 0x5000;
    private static final long DDL = 0x6000;

    private SnowflakeStatementTypes() {
    }

    /**
     * The type id of each statement of a text, in order, a flow chain's being its last statement's; a text that does
     * not parse is answered as one SELECT, as it is refused when it runs.
     *
     * @param sql the text
     * @return the ids
     */
    static List<Long> of(final String sql) {
        final List<Long> types = new ArrayList<>();
        try {
            final FrostlakeParser.SqlScriptContext script = QueryExecutor.parsedScriptQuietly(sql);
            for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
                final List<FrostlakeParser.StatementContext> stages = chain.statement();
                if (!stages.isEmpty()) {
                    types.add(Long.valueOf(typeOf(stages.get(stages.size() - 1))));
                }
            }
        } catch (final RuntimeException unparsed) {
            types.clear();
        }
        if (types.isEmpty()) {
            types.add(Long.valueOf(SELECT));
        }
        return types;
    }

    /**
     * The one statement of a text, or null for a text of several, of none, or one that does not parse.
     *
     * @param sql the text
     * @return the statement's parse tree
     */
    static FrostlakeParser.StatementContext onlyStatement(final String sql) {
        final FrostlakeParser.SqlScriptContext script;
        try {
            script = QueryExecutor.parsedScriptQuietly(sql);
        } catch (final RuntimeException unparsed) {
            return null;
        }
        FrostlakeParser.StatementContext only = null;
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                if (only != null) {
                    return null;
                }
                only = statement;
            }
        }
        return only;
    }

    /**
     * The type id of one statement.
     *
     * @param statement the statement's parse tree
     * @return its id
     */
    static long typeOf(final FrostlakeParser.StatementContext statement) {
        final ParseTree kind = statement.getChildCount() == 0 ? null : statement.getChild(0);
        final List<Integer> words = leadingWords(statement, 3);
        final int first = words.isEmpty() ? -1 : words.get(0).intValue();
        final int second = words.size() > 1 ? words.get(1).intValue() : -1;
        if (first == FrostlakeParser.CALL) {
            return SELECT;
        }
        if (kind instanceof FrostlakeParser.QueryStatementContext
                || kind instanceof FrostlakeParser.ExplainStatementContext
                || kind instanceof FrostlakeParser.ProceduralStatementContext) {
            return SELECT;
        }
        if (kind instanceof FrostlakeParser.ShowStatementContext
                || kind instanceof FrostlakeParser.ShowClassStatementContext
                || kind instanceof FrostlakeParser.SecurityObjectListingContext) {
            return first == FrostlakeParser.DESC || first == FrostlakeParser.DESCRIBE ? DESCRIBE : SHOW;
        }
        if (kind instanceof FrostlakeParser.DescribeStatementContext) {
            return DESCRIBE;
        }
        if (kind instanceof FrostlakeParser.ListStatementContext) {
            return LIST;
        }
        if (kind instanceof FrostlakeParser.GetStatementContext) {
            return GET;
        }
        if (kind instanceof FrostlakeParser.PutStatementContext) {
            return PUT;
        }
        if (kind instanceof FrostlakeParser.RemoveStatementContext) {
            return REMOVE;
        }
        if (kind instanceof FrostlakeParser.DmlStatementContext) {
            return dmlType((FrostlakeParser.DmlStatementContext) kind);
        }
        if (kind instanceof FrostlakeParser.TransactionStatementContext) {
            return TCL;
        }
        if (kind instanceof FrostlakeParser.SessionSetStatementContext
                || kind instanceof FrostlakeParser.SessionUnsetStatementContext) {
            return SCL;
        }
        if (kind instanceof FrostlakeParser.UseStatementContext) {
            return second == FrostlakeParser.DATABASE ? USE + 1 : second == FrostlakeParser.SCHEMA ? USE + 2
                : second == FrostlakeParser.WAREHOUSE ? USE + 3 : USE;
        }
        if (first == FrostlakeParser.ALTER && second == FrostlakeParser.SESSION) {
            return ALTER_SESSION;
        }
        // The listings and descriptions other statement families parse (SHOW SERVICES, DESCRIBE TASK, ...) answer
        // rows as well: the leading word decides.
        if (first == FrostlakeParser.SHOW) {
            return SHOW;
        }
        if (first == FrostlakeParser.DESC || first == FrostlakeParser.DESCRIBE) {
            return DESCRIBE;
        }
        return DDL;
    }

    /**
     * The type id a statement's result is answered with: the statement's own, unless the engine reads the result
     * otherwise. The engine records, for each result, the update count a JDBC client reports for it, or none when the
     * client reads rows; an EXECUTE IMMEDIATE answers as the statement its text ran, and each answer of a text of
     * several that one EXECUTE IMMEDIATE ran as that statement. A result read as rows takes a type the driver reads
     * rows by, and a counted one a type the driver counts the same number by: a COPY's for a grid that counts the rows
     * it loaded, a DML type's for a grid of counts, and any other statement's for 0.
     *
     * @param parsed the statement's own type id, or SELECT where the statement is not known
     * @param result the result answered, or null
     * @return the type id to answer with
     */
    static long answered(final long parsed, final ResultSet result) {
        if (result == null) {
            return parsed;
        }
        final Long count = result.getJdbcUpdateCount();
        if (count == null) {
            return readsRows(parsed) ? parsed : SELECT;
        }
        if (!readsRows(parsed)) {
            return parsed;
        }
        for (final ResultSetColumn column : result.getColumns()) {
            if ("rows_loaded".equalsIgnoreCase(column.getName())) {
                return COPY;
            }
        }
        return count.longValue() == 0L ? DDL : INSERT;
    }

    /** Whether the driver reads a result of the type as rows rather than as an update count. */
    private static boolean readsRows(final long type) {
        return type == SELECT || type == SHOW || type == DESCRIBE || type == LIST || type == GET || type == PUT
            || type == REMOVE;
    }

    private static long dmlType(final FrostlakeParser.DmlStatementContext dml) {
        if (dml.multiTableInsertStatement() != null) {
            return MULTI_INSERT;
        }
        if (dml.updateStatement() != null) {
            return UPDATE;
        }
        if (dml.deleteStatement() != null) {
            return DELETE;
        }
        if (dml.mergeStatement() != null) {
            return MERGE;
        }
        if (dml.copyIntoStatement() != null) {
            return unloads(dml.copyIntoStatement()) ? UNLOAD : COPY;
        }
        return INSERT;
    }

    /** Whether a COPY INTO writes into a stage, its target being a stage location. */
    private static boolean unloads(final ParserRuleContext copy) {
        final List<Integer> words = leadingWords(copy, 3);
        return words.size() == 3 && words.get(2).intValue() == FrostlakeParser.AT;
    }

    /** The token types of a node's first terminals, at most {@code count}. */
    private static List<Integer> leadingWords(final ParseTree node, final int count) {
        final List<Integer> words = new ArrayList<>();
        collect(node, count, words);
        return words;
    }

    private static void collect(final ParseTree node, final int count, final List<Integer> words) {
        if (words.size() >= count) {
            return;
        }
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            words.add(Integer.valueOf(token.getType()));
            return;
        }
        for (int i = 0; i < node.getChildCount() && words.size() < count; i++) {
            collect(node.getChild(i), count, words);
        }
    }
}
