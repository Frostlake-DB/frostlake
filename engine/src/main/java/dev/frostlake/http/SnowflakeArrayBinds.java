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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A batch the Snowflake driver binds as arrays, one request for every row, as the account takes it.
 *
 * <p>The driver binds arrays only for a statement whose describe says it may: an INSERT, OVERWRITE or not, of one
 * table from a VALUES list that binds a value. Every other statement - an INSERT from a query, a multi-table INSERT,
 * UPDATE, DELETE, MERGE - is sent row by row (live-verified).
 *
 * <p>The rows go in as ONE statement: the first VALUES row once for each row of values, so the batch inserts all of
 * its rows or none, a value no column takes refused as the INSERT refuses it -
 * {@code DML operation to table T failed on column X with error: ...} - and its count is the rows inserted. Only a
 * bind that is a whole value of the first row is bound; one inside an expression, {@code (? + 1)}, or in a later
 * row is refused where it stands as {@code Bind variable ? not set.} (live-verified).
 */
final class SnowflakeArrayBinds {

    private static final String UNSET_BIND = "Bind variable %s not set.";

    private SnowflakeArrayBinds() {
    }

    /**
     * Whether the driver may bind a batch of a statement as arrays.
     *
     * @param sql the statement
     * @return true for an INSERT of one table from a VALUES list that binds a value
     */
    static boolean supported(final String sql) {
        return valuesInsert(sql) != null && JdbcMarshaling.bindCount(sql) > 0;
    }

    /**
     * The statement with the rows of an array binding written in, the first VALUES row once for each.
     *
     * @param sql  the statement
     * @param rows each row's values, by 1-based bind number
     * @return the text to run
     * @throws SQLException where the statement takes no array binding, or a bind is no whole value of the first row
     */
    static String bind(final String sql, final List<Map<Integer, Object>> rows) throws SQLException {
        final FrostlakeParser.InsertStatementContext insert = valuesInsert(sql);
        if (insert == null) {
            throw new SQLException("Array binding is taken by an INSERT from a VALUES list only.", "0A000");
        }
        final FrostlakeParser.ValueTupleContext first = insert.valueTupleList().valueTuple(0);
        final Set<Integer> whole = new HashSet<>();
        for (final FrostlakeParser.BooleanExprContext cell : first.valueList().booleanExpr()) {
            if (isBind(cell.getStart(), cell.getStop())) {
                whole.add(Integer.valueOf(cell.getStart().getTokenIndex()));
            }
        }
        final List<Token> binds = new ArrayList<>();
        final List<String> written = new ArrayList<>();
        collectBinds(insert, binds, written, new Token[2]);
        for (int i = 0; i < binds.size(); i++) {
            final Token bind = binds.get(i);
            if (!whole.contains(Integer.valueOf(bind.getTokenIndex()))) {
                throw new SQLException(SqlCompilationError.inline("error line " + bind.getLine() + " at position "
                    + bind.getCharPositionInLine() + "\n" + String.format(UNSET_BIND, written.get(i))),
                    "42601", 2049);
            }
        }
        final int start = first.getStart().getStartIndex();
        final int stop = first.getStop().getStopIndex();
        final String template = sql.substring(start, stop + 1);
        final StringBuilder out = new StringBuilder(sql.length() + rows.size() * template.length());
        out.append(sql, 0, start);
        for (int i = 0; i < rows.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(JdbcMarshaling.bindPrepared(template, rows.get(i)));
        }
        out.append(sql, stop + 1, sql.length());
        return out.toString();
    }

    /** The INSERT of a text that is one INSERT of one table from a VALUES list, or null. */
    private static FrostlakeParser.InsertStatementContext valuesInsert(final String sql) {
        final FrostlakeParser.StatementContext only = SnowflakeStatementTypes.onlyStatement(sql);
        if (only == null || only.getChildCount() == 0
                || !(only.getChild(0) instanceof FrostlakeParser.DmlStatementContext)) {
            return null;
        }
        final FrostlakeParser.InsertStatementContext insert =
            ((FrostlakeParser.DmlStatementContext) only.getChild(0)).insertStatement();
        return insert == null || insert.VALUES() == null ? null : insert;
    }

    /** Whether a run of tokens is one bind: a {@code ?}, or a {@code :n}. */
    private static boolean isBind(final Token start, final Token stop) {
        if (start.getType() == FrostlakeParser.QUESTION) {
            return start.getTokenIndex() == stop.getTokenIndex();
        }
        return start.getType() == FrostlakeParser.COLON && stop.getType() == FrostlakeParser.INTEGER_LITERAL
            && stop.getTokenIndex() == start.getTokenIndex() + 1;
    }

    /**
     * Collects the binds of a statement in order, each with its written form: every {@code ?}, and every colon a
     * number follows unless a quoted key stands before it, as in an object constant's {@code {'k': 1}}.
     *
     * @param node    the statement, or a part of it
     * @param binds   takes each bind's first token
     * @param written takes each bind as written
     * @param last    the two terminals before this node, the latest last
     */
    private static void collectBinds(final ParseTree node, final List<Token> binds, final List<String> written,
                                     final Token[] last) {
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            if (token.getType() == FrostlakeParser.QUESTION) {
                binds.add(token);
                written.add("?");
            } else if (token.getType() == FrostlakeParser.INTEGER_LITERAL && last[1] != null
                    && last[1].getType() == FrostlakeParser.COLON
                    && (last[0] == null || last[0].getType() != FrostlakeParser.STRING_LITERAL)) {
                binds.add(last[1]);
                written.add(":" + token.getText());
            }
            last[0] = last[1];
            last[1] = token;
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectBinds(node.getChild(i), binds, written, last);
        }
    }
}
