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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.executor.DynamicStatementCount;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.QueryResultCache;
import dev.frostlake.executor.StatementCancellation;
import dev.frostlake.executor.StatementCount;
import dev.frostlake.executor.commands.FailureCode;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.values.VariantJsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.zip.GZIPInputStream;

/**
 * The endpoints the Snowflake JDBC driver talks to, so that driver connects to this server unchanged:
 * {@code jdbc:snowflake://<host>:<port>/?account=<any>&ssl=off} (the server takes no credentials, so any user and
 * password log in, as on {@code /api/execute}).
 *
 * <ul>
 *   <li>{@code POST /session/v1/login-request} opens a session in the database, schema, role and warehouse the
 *       connection names, its token the session's id, and answers the session parameters the driver renders
 *       values by (see {@link SnowflakeSessionParameters});</li>
 *   <li>{@code POST /queries/v1/query-request} runs a statement in the session its token names, its bindings written
 *       in (see {@link SnowflakeBindings}), and answers its result - as Arrow unless the session asks for JSON
 *       (see {@link SnowflakeRowset}) - or the
 *       statement's refusal with its code and SQLSTATE. A text of several statements, which the driver asks for with
 *       MULTI_STATEMENT_COUNT, answers the ids of each statement's result, read by
 *       {@code GET /queries/<id>/result}; a describe-only request answers the statement's columns without running it
 *       (see {@link SnowflakeDescribe}), and a batch bound as arrays runs as one statement (see
 *       {@link SnowflakeArrayBinds});</li>
 *   <li>{@code POST /session?delete=true} ends the session, {@code /session/heartbeat} keeps it,
 *       {@code /session/token-request} renews its token, {@code /queries/v1/abort-request} cancels its running
 *       statement, {@code /monitoring/queries/<id>} answers a query's status from the query history, and
 *       {@code /telemetry/send} is accepted and dropped;</li>
 *   <li>a PUT or a GET is answered with the transfer the driver carries out itself (see
 *       {@link SnowflakeFileTransfer}), and an asynchronous statement with its query ID at once (see
 *       {@link #runAsync}).</li>
 * </ul>
 */
final class SnowflakeProtocolHandler implements HttpHandler {

    /** The contexts the handler serves. */
    static final String[] CONTEXTS = {"/session", "/queries", "/telemetry", "/monitoring"};

    private static final Logger logger = LoggerFactory.getLogger(SnowflakeProtocolHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN_TAG = "Token=\"";
    /** How many statements' results a text of several keeps for the driver to read, the oldest dropped first. */
    private static final int KEPT_RESULTS = 256;
    /** The code and SQLSTATE of a statement whose session token is unknown or expired. */
    private static final String EXPIRED_CODE = "390112";

    /** The code of an answer that starts an asynchronous statement. */
    private static final String ASYNC_CODE = "333334";
    /** The JSON_INDENT the account lays semi-structured values out at while a session sets none. */
    private static final int ACCOUNT_JSON_INDENT = 2;

    private final ConcurrentDatabaseEngine engine;
    /** The asynchronous statements still running, by query ID. */
    private final Set<String> asyncRunning = ConcurrentHashMap.newKeySet();
    /** The refusals of asynchronous statements, by query ID, for a status asked after the statement ended. */
    private final Map<String, ObjectNode> asyncRefusals = Collections.synchronizedMap(
        new LinkedHashMap<String, ObjectNode>() {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, ObjectNode> eldest) {
                return size() > KEPT_RESULTS;
            }
        });
    /** The workers asynchronous statements run on. */
    private final ExecutorService asyncWorkers = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable, "frostlake-snowflake-async");
            thread.setDaemon(true);
            return thread;
        }
    });
    /** The results of the statements of a text of several, by query id, until the driver reads them. */
    private final Map<String, ObjectNode> keptResults = Collections.synchronizedMap(
        new LinkedHashMap<String, ObjectNode>() {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, ObjectNode> eldest) {
                return size() > KEPT_RESULTS;
            }
        });

    SnowflakeProtocolHandler(final ConcurrentDatabaseEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        final String path = exchange.getRequestURI().getPath();
        final Map<String, String> query = queryParameters(exchange.getRequestURI().getRawQuery());
        try {
            if ("/session/v1/login-request".equals(path)) {
                send(exchange, login(readJson(exchange), query));
            } else if ("/queries/v1/query-request".equals(path)) {
                send(exchange, query(exchange, readJson(exchange)));
            } else if ("/session".equals(path) && "true".equalsIgnoreCase(query.get("delete"))) {
                send(exchange, logout(exchange));
            } else if ("/session/heartbeat".equals(path)) {
                send(exchange, heartbeat(exchange));
            } else if ("/session/token-request".equals(path)) {
                send(exchange, renewToken(exchange));
            } else if ("/queries/v1/abort-request".equals(path)) {
                send(exchange, abort(exchange));
            } else if ("/telemetry/send".equals(path)) {
                send(exchange, success(null));
            } else if (path.startsWith("/monitoring/queries/")) {
                send(exchange, status(exchange, path.substring("/monitoring/queries/".length())));
            } else if (path.startsWith("/queries/") && path.endsWith("/result")) {
                send(exchange, keptResult(exchange, path.substring("/queries/".length(),
                    path.length() - "/result".length())));
            } else {
                HttpResponses.send(exchange, 404, MAPPER.writeValueAsString(failure("390400",
                    "Unknown endpoint " + path, null)));
            }
        } catch (final IOException | RuntimeException fault) {
            logger.error("Error handling a Snowflake driver request to {}", path, fault);
            HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(failure("000603",
                fault.getMessage() == null ? fault.toString() : fault.getMessage(), null)));
        }
    }

    /** Opens a session for a login, in the namespace the connection names. */
    private ObjectNode login(final JsonNode body, final Map<String, String> query) {
        final JsonNode data = body.path("data");
        final SessionContext session = engine.createSession();
        // The driver reads a semi-structured value laid out at the account's default JSON_INDENT while the session
        // sets none.
        session.getSettings().setDefaultJsonIndent(Integer.valueOf(ACCOUNT_JSON_INDENT));
        final String user = data.path("LOGIN_NAME").isMissingNode() ? engine.currentUser()
            : data.path("LOGIN_NAME").asString();
        // The namespace the connection names, set as a USE sets it; a name that reaches nothing leaves the
        // session where it is, as the account's login does.
        use(session, "DATABASE", query.get("databaseName"));
        use(session, "SCHEMA", query.get("schemaName"));
        use(session, "ROLE", query.get("roleName"));
        use(session, "WAREHOUSE", query.get("warehouse"));
        applySessionParameters(session, data.path("SESSION_PARAMETERS"));
        final ObjectNode answer = JsonNodeFactory.instance.objectNode();
        answer.put("token", session.getSessionId());
        answer.put("masterToken", session.getSessionId());
        answer.put("validityInSeconds", 3600);
        answer.put("masterValidityInSeconds", 14400);
        answer.putNull("idToken");
        answer.putNull("mfaToken");
        answer.put("displayUserName", user);
        answer.put("serverVersion", "Frostlake");
        answer.put("firstLogin", false);
        answer.putNull("remMeToken");
        answer.put("remMeValidityInSeconds", 0);
        answer.put("healthCheckInterval", 45);
        answer.putNull("newClientForUpgrade");
        answer.put("sessionId", Math.abs((long) session.getSessionId().hashCode()));
        answer.set("parameters", SnowflakeSessionParameters.of(engine, session));
        final ObjectNode info = answer.putObject("sessionInfo");
        putText(info, "databaseName", session.getCurrentDatabase());
        putText(info, "schemaName", session.getCurrentSchema());
        putText(info, "warehouseName", session.getCurrentWarehouse());
        putText(info, "roleName", query.get("roleName") == null ? null
            : query.get("roleName").toUpperCase(Locale.ROOT));
        return success(answer);
    }

    /** Runs {@code USE <kind> IDENTIFIER('<name>')} in a session, the name read as the connection wrote it. */
    private void use(final SessionContext session, final String kind, final String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        try {
            engine.execute("USE " + kind + " IDENTIFIER('" + name.replace("'", "''") + "')", session);
        } catch (final RuntimeException refused) {
            logger.debug("Login left {} {} unset: {}", kind, name, refused.getMessage());
        }
    }

    /**
     * Sets the session parameters a login carries, as ALTER SESSION sets them: a text quoted, a number or a boolean as
     * it is. One the engine does not take - most of the driver's own client settings - is left out.
     */
    private void applySessionParameters(final SessionContext session, final JsonNode parameters) {
        if (!parameters.isObject()) {
            return;
        }
        for (final Map.Entry<String, JsonNode> parameter : parameters.properties()) {
            final String name = parameter.getKey();
            final JsonNode value = parameter.getValue();
            // The driver's own client settings stay with the driver, the result format aside: a connection that
            // names JDBC_QUERY_RESULT_FORMAT=JSON reads JSON results, as against the account.
            final boolean clientSetting = name.startsWith("CLIENT_")
                || name.startsWith("JDBC_") && !"JDBC_QUERY_RESULT_FORMAT".equalsIgnoreCase(name);
            if (!isParameterName(name) || clientSetting || value.isNull() || value.isContainer()) {
                continue;
            }
            final String literal = value.isNumber() || value.isBoolean() ? value.asString()
                : "'" + value.asString().replace("'", "''") + "'";
            try {
                engine.execute("ALTER SESSION SET " + name + " = " + literal, session);
            } catch (final RuntimeException refused) {
                logger.debug("Login left session parameter {} unset: {}", name, refused.getMessage());
            }
        }
    }

    /** Whether a text is a parameter's name: letters, digits and underscores, a letter first. */
    private static boolean isParameterName(final String name) {
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs a statement, or a text of several, in the session the request's token names; an asynchronous one is
     * started on a worker and answered at once (see {@link #runAsync}).
     */
    private ObjectNode query(final HttpExchange exchange, final JsonNode body) {
        final SessionContext session = sessionOf(exchange);
        if (session == null) {
            return expired();
        }
        return body.path("asyncExec").asBoolean() ? runAsync(session, body) : query(session, body);
    }

    /**
     * Starts a statement on a worker and answers at once with the query ID it runs under (code 333334), as the account
     * answers {@code executeAsyncQuery}: the driver then follows the statement by that ID through
     * {@code /monitoring/queries/<id>} and reads its rows with RESULT_SCAN. The session's other requests wait for it,
     * as each request of a session waits for the one before.
     */
    private ObjectNode runAsync(final SessionContext session, final JsonNode body) {
        final String queryId = QueryResultCache.newQueryId();
        asyncRunning.add(queryId);
        asyncWorkers.execute(new Runnable() {
            @Override
            public void run() {
                QueryResultCache.assignNextQueryId(queryId);
                try {
                    final ObjectNode answer = query(session, body);
                    if (!answer.path("success").asBoolean()) {
                        asyncRefusals.put(queryId, answer);
                    }
                } catch (final RuntimeException failed) {
                    asyncRefusals.put(queryId, refusal(failed.getMessage(), failed));
                } finally {
                    QueryResultCache.assignNextQueryId(null);
                    asyncRunning.remove(queryId);
                }
            }
        });
        final ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("queryId", queryId);
        data.put("getResultUrl", "/queries/" + queryId + "/result");
        data.put("queryAbortsAfterSecs", 300);
        data.putNull("progressDesc");
        final ObjectNode answer = success(data);
        answer.put("code", ASYNC_CODE);
        answer.put("message", "Asynchronous execution in progress. Use provided query id to perform query monitoring "
            + "and management.");
        return answer;
    }

    /** Runs a statement, or a text of several, in a session. */
    private ObjectNode query(final SessionContext session, final JsonNode body) {
        final String written = body.path("sqlText").asString();
        final JsonNode bindings = body.path("bindings");
        final JsonNode count = body.path("parameters").path("MULTI_STATEMENT_COUNT");
        final int desired = count.isMissingNode() || count.isNull() ? session.getMultiStatementCount() : count.asInt();
        final int actual = StatementCount.countStatements(written);
        if (desired != 0 && actual != 0 && actual != desired) {
            try {
                QueryExecutor.requireParses(written);
            } catch (final RuntimeException syntaxError) {
                return refusal(syntaxError.getMessage(), syntaxError);
            }
            return failure("000008", "Actual statement count " + actual + " did not match the desired statement count "
                + desired + ".", "0A000");
        }
        if (body.path("describeOnly").asBoolean()) {
            // A prepared statement's describe: its columns worked out without running it (see SnowflakeDescribe).
            final ObjectNode data = JsonNodeFactory.instance.objectNode();
            try {
                SnowflakeDescribe.write(engine, session, written, data);
            } catch (final SQLException refused) {
                return failure(codeText(refused.getErrorCode()), refused.getMessage(), refused.getSQLState());
            } catch (final RuntimeException refused) {
                return refusal(refused.getMessage(), refused);
            }
            describe(data, session, null, SnowflakeStatementTypes.of(written).get(0).longValue(), written);
            return success(data);
        }
        final FrostlakeParser.StatementContext only = SnowflakeStatementTypes.onlyStatement(written);
        if (only != null && SnowflakeFileTransfer.isTransfer(only)) {
            // The driver moves a PUT's or a GET's files itself: it is answered with the transfer, not run.
            try {
                return success(SnowflakeFileTransfer.describe(engine, session, only, QueryResultCache.newQueryId()));
            } catch (final IOException | RuntimeException refused) {
                return refusal(refused.getMessage(), refused);
            }
        }
        final String sql;
        try {
            if (SnowflakeBindings.isArray(bindings)) {
                sql = SnowflakeArrayBinds.bind(written, SnowflakeBindings.rows(bindings));
            } else {
                sql = bindings.isObject() && !bindings.isEmpty()
                    ? JdbcMarshaling.bindPrepared(written, SnowflakeBindings.of(bindings)) : written;
            }
        } catch (final SQLException refused) {
            return failure(codeText(refused.getErrorCode()), refused.getMessage(), refused.getSQLState());
        } catch (final RuntimeException refused) {
            return refusal(refused.getMessage(), null);
        }
        final List<Long> types = SnowflakeStatementTypes.of(sql);
        final ExecutionResult result;
        final Integer displacedCount = DynamicStatementCount.beginRequest(desired, actual);
        final StatementCancellation token = new StatementCancellation(0);
        session.setRunning(token);
        StatementCancellation.offer(token);
        try {
            result = engine.execute(sql, session);
        } catch (final RuntimeException statementFailure) {
            return refusal(statementFailure.getMessage(), statementFailure);
        } finally {
            StatementCancellation.offer(null);
            session.setRunning(null);
            DynamicStatementCount.endRequest(displacedCount);
        }
        if (!result.isSuccess()) {
            return refusal(result.getErrorMessage(), null);
        }
        session.followStatementCount(sql);
        engine.bindJsonIndent(session);
        try {
            return answer(session, capped(result, rowsAtMost(body.path("parameters"))), types, written);
        } finally {
            VariantJsonFormat.clearSessionScope();
        }
    }

    /**
     * The most rows a statement hands back, as the driver asks with {@code setMaxRows}: its {@code rows_per_resultset}
     * parameter, 0 for no bound.
     *
     * @param parameters the statement's parameters
     * @return the bound, or 0
     */
    private static int rowsAtMost(final JsonNode parameters) {
        for (final Map.Entry<String, JsonNode> parameter : parameters.properties()) {
            if ("ROWS_PER_RESULTSET".equalsIgnoreCase(parameter.getKey()) && parameter.getValue().isNumber()) {
                return Math.max(0, parameter.getValue().asInt());
            }
        }
        return 0;
    }

    /** A run's results, each holding at most {@code most} rows where that bounds them. */
    private static ExecutionResult capped(final ExecutionResult result, final int most) {
        if (most <= 0 || result.getResultSets() == null) {
            return result;
        }
        final List<ResultSet> results = new ArrayList<>();
        boolean cut = false;
        for (final ResultSet whole : result.getResultSets()) {
            if (whole.getRows().size() <= most) {
                results.add(whole);
                continue;
            }
            final ResultSet first =
                new ResultSet(whole.getColumns(), new ArrayList<>(whole.getRows().subList(0, most)));
            first.markJdbcUpdateCount(whole.getJdbcUpdateCount());
            if (whole.isScriptingAnswer()) {
                first.markScriptingAnswer();
            }
            results.add(first);
            cut = true;
        }
        return cut ? new ExecutionResult(true, results, null, result.getQueryId()) : result;
    }

    /**
     * A successful run's answer: its one result, or for several - a text of several statements, or one EXECUTE
     * IMMEDIATE of several, which hands back each statement's answer - the ids of each result. Each result is typed
     * as its statement reads (see {@link SnowflakeStatementTypes#answered}).
     */
    private ObjectNode answer(final SessionContext session, final ExecutionResult result, final List<Long> types,
                              final String written) {
        final List<ResultSet> results = result.getResultSets() == null ? new ArrayList<ResultSet>()
            : result.getResultSets();
        final String queryId = result.getQueryId() == null ? UUID.randomUUID().toString() : result.getQueryId();
        if (results.size() <= 1) {
            final ObjectNode data = JsonNodeFactory.instance.objectNode();
            if (results.isEmpty()) {
                data.putArray("rowtype");
                data.putArray("rowset");
                data.put("total", 0);
                data.put("returned", 0);
            } else {
                SnowflakeRowset.write(results.get(0), data, arrow(session));
            }
            describe(data, session, queryId, SnowflakeStatementTypes.answered(types.get(0).longValue(),
                results.isEmpty() ? null : results.get(0)), written);
            return success(data);
        }
        // One result for each statement, or each answer of one EXECUTE IMMEDIATE, whose statements are not the text's.
        final boolean eachStatements = results.size() == types.size();
        final List<String> ids = new ArrayList<>();
        final List<String> kinds = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            final String id = UUID.randomUUID().toString();
            final ObjectNode data = JsonNodeFactory.instance.objectNode();
            SnowflakeRowset.write(results.get(i), data, arrow(session));
            final long type = SnowflakeStatementTypes.answered(eachStatements ? types.get(i).longValue()
                : SnowflakeStatementTypes.SELECT, results.get(i));
            describe(data, session, id, type, written);
            keptResults.put(id, success(data));
            ids.add(id);
            kinds.add(String.valueOf(type));
        }
        final ObjectNode data = JsonNodeFactory.instance.objectNode();
        final ObjectNode column = data.putArray("rowtype").addObject();
        column.put("name", "multiple statement execution");
        column.put("type", "text");
        column.put("nullable", false);
        column.put("length", 16777216);
        column.put("byteLength", 16777216);
        column.putNull("precision");
        column.putNull("scale");
        data.putArray("rowset").addArray().add("Multiple statements executed successfully.");
        data.put("total", 1);
        data.put("returned", 1);
        describe(data, session, queryId, SnowflakeStatementTypes.SELECT, written);
        data.put("resultIds", String.join(",", ids));
        data.put("resultTypes", String.join(",", kinds));
        return success(data);
    }

    /**
     * The fields every query's answer carries beside its rows: among them the values the statement binds, and whether
     * the driver may bind a batch of it as arrays (see {@link SnowflakeArrayBinds}).
     */
    private void describe(final ObjectNode data, final SessionContext session, final String queryId,
                          final long statementType, final String written) {
        data.set("parameters", SnowflakeSessionParameters.of(engine, session));
        putText(data, "queryId", queryId == null ? "" : queryId);
        putText(data, "finalDatabaseName", session.getCurrentDatabase());
        putText(data, "finalSchemaName", session.getCurrentSchema());
        putText(data, "finalWarehouseName", session.getCurrentWarehouse());
        data.putNull("finalRoleName");
        data.put("numberOfBinds", JdbcMarshaling.bindCount(written));
        data.put("arrayBindSupported", SnowflakeArrayBinds.supported(written));
        data.put("statementTypeId", statementType);
        data.put("version", 1);
        data.put("sendResultTime", System.currentTimeMillis());
        if (!data.has("queryResultFormat")) {
            data.put("queryResultFormat", "json");
        }
    }

    /** Whether a session takes Arrow results: its JDBC_QUERY_RESULT_FORMAT, ARROW unless it set JSON. */
    private boolean arrow(final SessionContext session) {
        final Object format = engine.sessionParameter(session, "JDBC_QUERY_RESULT_FORMAT");
        return format == null || !"JSON".equalsIgnoreCase(format.toString().trim());
    }

    /** A kept result of one statement of a text of several. */
    private ObjectNode keptResult(final HttpExchange exchange, final String queryId) {
        if (sessionOf(exchange) == null) {
            return expired();
        }
        final ObjectNode kept = keptResults.get(queryId);
        return kept != null ? kept : failure("002003", "SQL compilation error:\nQuery '" + queryId
            + "' does not exist or not authorized.", "02000");
    }

    /**
     * A query's status, as the driver asks it of an asynchronous statement, of a statement of a text of several before
     * reading its result, and through {@code getQueryStatus}: RUNNING while it runs, then SUCCESS or
     * FAILED_WITH_ERROR with its error, read from the query history; no query for an ID the server does not know.
     */
    private ObjectNode status(final HttpExchange exchange, final String queryId) {
        if (sessionOf(exchange) == null) {
            return expired();
        }
        final ObjectNode data = JsonNodeFactory.instance.objectNode();
        final ArrayNode queries = data.putArray("queries");
        final long now = System.currentTimeMillis();
        final QueryHistory history = engine.getEngine().getExecutor().getQueryHistoryTracker().getQueryById(queryId);
        final ObjectNode refused = asyncRefusals.get(queryId);
        if (keptResults.containsKey(queryId)) {
            statusRow(queries, queryId, "SUCCESS", 0, null, now, now, null);
        } else if (history != null && !"RUNNING".equals(history.getStatus())) {
            final boolean succeeded = "SUCCESS".equals(history.getStatus());
            statusRow(queries, queryId, succeeded ? "SUCCESS" : "FAILED_WITH_ERROR",
                succeeded || history.getErrorCode() == null ? 0 : history.getErrorCode().intValue(),
                succeeded ? null : history.getErrorMessage(), millis(history.getStartInstant(), now),
                millis(history.getEndInstant(), now), history.getQueryText());
        } else if (history != null || asyncRunning.contains(queryId)) {
            statusRow(queries, queryId, "RUNNING", 0, null,
                history == null ? now : millis(history.getStartInstant(), now), 0, null);
        } else if (refused != null) {
            statusRow(queries, queryId, "FAILED_WITH_ERROR", Integer.parseInt(refused.path("code").asString("0")),
                refused.path("message").asString(null), now, now, null);
        }
        return success(data);
    }

    private static void statusRow(final ArrayNode queries, final String queryId, final String status,
                                  final int errorCode, final String errorMessage, final long start, final long end,
                                  final String sqlText) {
        final ObjectNode query = queries.addObject();
        query.put("id", queryId);
        query.put("status", status);
        query.put("state", "SUCCESS".equals(status) ? "SUCCEEDED" : "RUNNING".equals(status) ? "RUNNING" : "FAILED");
        query.put("errorCode", errorCode);
        putText(query, "errorMessage", errorMessage);
        query.put("startTime", start);
        query.put("endTime", end);
        query.put("totalDuration", end > 0 ? Math.max(0, end - start) : 0);
        putText(query, "sqlText", sqlText);
    }

    private static long millis(final Instant moment, final long fallback) {
        return moment == null ? fallback : moment.toEpochMilli();
    }

    private ObjectNode logout(final HttpExchange exchange) {
        final SessionContext session = sessionOf(exchange);
        if (session != null) {
            engine.endSession(session);
        }
        return success(null);
    }

    private ObjectNode heartbeat(final HttpExchange exchange) {
        final SessionContext session = sessionOf(exchange);
        if (session == null) {
            return expired();
        }
        session.touch();
        return success(null);
    }

    private ObjectNode renewToken(final HttpExchange exchange) {
        final SessionContext session = sessionOf(exchange);
        if (session == null) {
            return expired();
        }
        final ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("sessionToken", session.getSessionId());
        data.put("masterToken", session.getSessionId());
        data.put("validityInSecondsST", 3600);
        data.put("validityInSecondsMT", 14400);
        data.put("sessionId", Math.abs((long) session.getSessionId().hashCode()));
        return success(data);
    }

    private ObjectNode abort(final HttpExchange exchange) {
        // Found without waiting for the engine: a cancel must not wait behind the statement it cancels.
        final SessionContext session = sessionOf(exchange, true);
        if (session == null) {
            return expired();
        }
        final StatementCancellation running = session.getRunning();
        if (running != null) {
            running.cancel();
        }
        return success(null);
    }

    /** The session a request's {@code Authorization: Snowflake Token="..."} names, or null. */
    private SessionContext sessionOf(final HttpExchange exchange) {
        return sessionOf(exchange, false);
    }

    /** {@link #sessionOf(HttpExchange)}, found without waiting for the engine where {@code cancelling}. */
    private SessionContext sessionOf(final HttpExchange exchange, final boolean cancelling) {
        final String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) {
            return null;
        }
        final int at = header.indexOf(TOKEN_TAG);
        if (at < 0) {
            return null;
        }
        final int end = header.indexOf('"', at + TOKEN_TAG.length());
        final String token = header.substring(at + TOKEN_TAG.length(), end < 0 ? header.length() : end);
        final SessionContext session = token.isEmpty() ? null
            : cancelling ? engine.findSession(token) : engine.getSession(token);
        if (session != null) {
            session.touch();
        }
        return session;
    }

    /** A statement's refusal, with the code and SQLSTATE the account gives it where they are known. */
    private static ObjectNode refusal(final String message, final Throwable failure) {
        final FailureCode family = failure != null ? FailureCode.ofClient(failure)
            : FailureCode.ofClientMessage(message);
        final String text = message == null ? "Statement failed." : message;
        return family == null ? failure("000904", text, "42000")
            : failure(codeText(family.code()), text, family.state());
    }

    /** A vendor code as the driver reads it, six digits. */
    private static String codeText(final int code) {
        return String.format(Locale.ROOT, "%06d", code);
    }

    private static ObjectNode expired() {
        return failure(EXPIRED_CODE, "Session no longer exists.  New login required to access the service.", "08001");
    }

    private static ObjectNode success(final ObjectNode data) {
        final ObjectNode answer = JsonNodeFactory.instance.objectNode();
        if (data == null) {
            answer.putNull("data");
        } else {
            answer.set("data", data);
        }
        answer.putNull("code");
        answer.putNull("message");
        answer.put("success", true);
        return answer;
    }

    private static ObjectNode failure(final String code, final String message, final String sqlState) {
        final ObjectNode answer = JsonNodeFactory.instance.objectNode();
        final ObjectNode data = answer.putObject("data");
        data.put("internalError", false);
        data.put("errorCode", code);
        data.put("age", 0);
        putText(data, "sqlState", sqlState);
        data.put("queryId", UUID.randomUUID().toString());
        data.put("line", -1);
        data.put("pos", -1);
        answer.put("code", code);
        answer.put("message", message);
        answer.put("success", false);
        return answer;
    }

    private static void putText(final ObjectNode node, final String name, final String value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value);
        }
    }

    /** A request's JSON body, unzipped where the driver sent it compressed. */
    private static JsonNode readJson(final HttpExchange exchange) throws IOException {
        final byte[] raw = exchange.getRequestBody().readAllBytes();
        final String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
        final byte[] body;
        if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
            try (InputStream unzipped = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                body = unzipped.readAllBytes();
            }
        } else {
            body = raw;
        }
        return body.length == 0 ? JsonNodeFactory.instance.objectNode()
            : MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
    }

    private static Map<String, String> queryParameters(final String rawQuery) {
        final Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return parameters;
        }
        for (final String pair : rawQuery.split("&")) {
            final int eq = pair.indexOf('=');
            final String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            final String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            parameters.put(name, value);
        }
        return parameters;
    }

    private static void send(final HttpExchange exchange, final ObjectNode answer) throws IOException {
        HttpResponses.send(exchange, 200, MAPPER.writeValueAsString(answer));
    }
}
