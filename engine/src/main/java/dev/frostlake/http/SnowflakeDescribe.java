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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ReadOnlyRequest;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.StatementResultWidths;
import dev.frostlake.types.StringType;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A prepared statement's describe, as the Snowflake driver asks it before the statement runs: the columns the
 * statement's result would carry, worked out without running it, and the values it binds.
 *
 * <ul>
 *   <li>A query, a SHOW, a DESCRIBE or a LIST is compiled over no rows (see
 *       {@link ConcurrentDatabaseEngine#shapeOf}), each placeholder read as the text {@code '?'}: a bare one is a
 *       VARCHAR(1) that is never NULL and a cast one its cast's type, and a query that does not compile is refused as
 *       when it runs. A query that would write - a sequence's NEXTVAL, a system function that acts - answers no
 *       column, as nothing of it may run.</li>
 *   <li>An INSERT, an UPDATE, a DELETE and a MERGE answer the counts they report, each a NUMBER(19,0) that is never
 *       NULL; a MERGE one for each kind of action its clauses take.</li>
 *   <li>ALTER SESSION, SET and UNSET answer no column, and any other statement - DDL, USE, a transaction's - a status
 *       text.</li>
 *   <li>A statement only a run can answer the columns of - a CALL, a script, a COPY, a multi-table INSERT - answers
 *       none, its columns read once it runs.</li>
 * </ul>
 * Each placeholder is described as a text, as the account describes it.
 */
final class SnowflakeDescribe {

    private static final StringType STATUS_TEXT = new StringType("VARCHAR", 16777216);

    private SnowflakeDescribe() {
    }

    /**
     * Writes a describe's {@code rowtype}, an empty {@code rowset} and {@code metaDataOfBinds}, one text for each value
     * the statement binds.
     *
     * @param engine  the engine
     * @param session the session the statement would run in
     * @param sql     the statement, its placeholders unbound
     * @param data    the answer's {@code data}
     * @throws SQLException where its placeholders cannot be read
     */
    static void write(final ConcurrentDatabaseEngine engine, final SessionContext session, final String sql,
                      final ObjectNode data) throws SQLException {
        final int binds = JdbcMarshaling.bindCount(sql);
        final ResultSet shape = shapeOf(engine, session, sql, binds);
        if (shape == null) {
            data.putArray("rowtype");
            data.putArray("rowset");
            data.put("total", 0);
            data.put("returned", 0);
        } else {
            SnowflakeRowset.write(shape, data);
        }
        final ArrayNode bindTypes = data.putArray("metaDataOfBinds");
        for (int i = 1; i <= binds; i++) {
            final ObjectNode bind = bindTypes.addObject();
            bind.put("name", String.valueOf(i));
            bind.put("type", "text");
            bind.put("precision", 0);
            bind.put("scale", 0);
            bind.put("nullable", false);
            bind.put("length", 0);
            bind.put("byteLength", 0);
        }
    }

    /** The statement's result without rows, or null where it answers no column before it runs. */
    private static ResultSet shapeOf(final ConcurrentDatabaseEngine engine, final SessionContext session,
                                     final String sql, final int binds) throws SQLException {
        final FrostlakeParser.StatementContext statement = SnowflakeStatementTypes.onlyStatement(sql);
        if (statement == null) {
            // A text that does not parse is refused at its describe, as the account refuses it.
            QueryExecutor.requireParses(sql);
            return null;
        }
        if (statement.getChildCount() == 0) {
            return null;
        }
        final Object kind = statement.getChild(0);
        if (kind instanceof FrostlakeParser.DmlStatementContext) {
            return counts((FrostlakeParser.DmlStatementContext) kind);
        }
        if (kind instanceof FrostlakeParser.QueryStatementContext
                || kind instanceof FrostlakeParser.ExplainStatementContext
                || kind instanceof FrostlakeParser.ShowStatementContext
                || kind instanceof FrostlakeParser.ShowClassStatementContext
                || kind instanceof FrostlakeParser.SecurityObjectListingContext
                || kind instanceof FrostlakeParser.DescribeStatementContext
                || kind instanceof FrostlakeParser.ListStatementContext) {
            if (statement.getStart().getType() == FrostlakeParser.CALL) {
                return null;
            }
            final Map<Integer, Object> placeholders = new HashMap<>();
            for (int i = 1; i <= binds; i++) {
                placeholders.put(Integer.valueOf(i), "?");
            }
            final String compiled = binds == 0 ? sql : JdbcMarshaling.bindPrepared(sql, placeholders, false);
            if (!ReadOnlyRequest.reads(compiled)) {
                return null;
            }
            final List<ResultSet> results = engine.shapeOf(compiled, session);
            return results.isEmpty() ? null : results.get(0);
        }
        final long type = SnowflakeStatementTypes.typeOf(statement);
        if (kind instanceof FrostlakeParser.ProceduralStatementContext
                || statement.getStart().getType() == FrostlakeParser.CALL
                || SnowflakeStatementTypes.changesSession(type)) {
            return null;
        }
        final List<ResultSetColumn> status = new ArrayList<>();
        status.add(new ResultSetColumn("status", STATUS_TEXT));
        return new ResultSet(status);
    }

    /** The counts a DML statement reports, or null for one whose columns only its run settles. */
    private static ResultSet counts(final FrostlakeParser.DmlStatementContext dml) {
        final List<String> names = new ArrayList<>();
        if (dml.updateStatement() != null) {
            names.add("number of rows updated");
            names.add("number of multi-joined rows updated");
        } else if (dml.deleteStatement() != null) {
            names.add("number of rows deleted");
        } else if (dml.mergeStatement() != null) {
            boolean inserts = false;
            boolean updates = false;
            boolean deletes = false;
            for (final FrostlakeParser.MergeClauseContext clause : dml.mergeStatement().mergeClause()) {
                inserts |= clause.INSERT() != null;
                updates |= clause.UPDATE() != null;
                deletes |= clause.DELETE() != null;
            }
            if (inserts) {
                names.add("number of rows inserted");
            }
            if (updates) {
                names.add("number of rows updated");
            }
            if (deletes) {
                names.add("number of rows deleted");
            }
        } else if (dml.insertStatement() != null) {
            names.add("number of rows inserted");
        } else {
            return null;
        }
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : names) {
            columns.add(new ResultSetColumn(name, StatementResultWidths.DML_COUNT, null,
                StatementResultWidths.DML_COUNT, false));
        }
        return new ResultSet(columns);
    }
}
