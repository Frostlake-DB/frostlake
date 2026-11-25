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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.executor.AccountParameters;
import dev.frostlake.executor.ReadOnlyBodies;
import dev.frostlake.executor.ReadOnlyRequest;
import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.RequestQueryCancels;
import dev.frostlake.executor.RequestSessionAborts;
import dev.frostlake.functions.scalar.file.NamedStage;
import dev.frostlake.http.ExpiredSessionListener;
import dev.frostlake.http.SessionContext;
import dev.frostlake.http.SessionManager;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.security.SessionDirectory;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.transaction.EngineLockRelease;
import dev.frostlake.transaction.TableLockGate;
import dev.frostlake.transaction.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe wrapper around DatabaseEngine for concurrent multi-user access
 * Manages sessions and ensures proper isolation between concurrent requests.
 *
 * <p>A wrapper of its own engine runs read-only requests side by side under a shared lock and every other request
 * alone. A wrapper over an engine other clients in the JVM use too (see {@link #sharing}) runs every request alone,
 * holding the engine's monitor as an in-process connection's statement does.
 */
public class ConcurrentDatabaseEngine {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentDatabaseEngine.class);

    private final DatabaseEngine engine;
    private final SessionManager sessionManager;
    private final ReentrantReadWriteLock engineLock;
    /** Whether the engine is shared with in-process connections, which run their statements on its monitor. */
    private final boolean sharesMonitor;

    // Track per-session state
    private final ConcurrentHashMap<String, SessionState> sessionStates;

    // Each session's entry in the engine's directory of abortable sessions, by session id. The directory holds an
    // entry weakly, so this map is what keeps it for as long as the session lives.
    private final ConcurrentHashMap<String, AbortableHttpSession> abortable =
        new ConcurrentHashMap<String, AbortableHttpSession>();

    public ConcurrentDatabaseEngine() {
        this(new DatabaseEngine());
    }

    /** A concurrent wrapper honoring the given configuration (database.default / schema.default). */
    public ConcurrentDatabaseEngine(final EngineConfig config) {
        this(new DatabaseEngine(config));
    }

    private ConcurrentDatabaseEngine(final DatabaseEngine engine) {
        this(engine, false);
    }

    private ConcurrentDatabaseEngine(final DatabaseEngine engine, final boolean sharesMonitor) {
        this.engine = engine;
        this.sharesMonitor = sharesMonitor;
        // New sessions start where the engine's configuration points, exactly like the engine's
        // own bootstrap session, and idle out after http.session.idleTimeoutMinutes (Snowflake's four hours
        // unless set; 0 keeps them until they are released).
        this.sessionManager = new SessionManager(
            engine.getConfig().getDefaultDatabase().toUpperCase(Locale.ROOT),
            engine.getConfig().getDefaultSchema().toUpperCase(Locale.ROOT),
            engine.getConfig().getSessionIdleTimeoutMinutes() * 60L * 1000L);
        // Non-fair: higher read throughput for this read-heavy engine. The JDK's non-fair RRWL still
        // blocks barging readers when a writer is queued, so writers are not badly starved.
        this.engineLock = new ReentrantReadWriteLock();
        this.sessionStates = new ConcurrentHashMap<>();
        if (!sharesMonitor) {
            // What the engine runs on a thread of its own - a scheduled task run, an alert's evaluation, a dynamic
            // table's refresh, a checkpoint - runs alone as a request that writes does, rather than beside one.
            final ReentrantReadWriteLock lock = engineLock;
            engine.setExclusiveLock(new ExclusiveEngineLock() {
                @Override
                public boolean acquire() {
                    if (lock.getReadHoldCount() > 0 && !lock.isWriteLockedByCurrentThread()) {
                        // A read-only request cannot take the lock alone; it runs on under the shared one.
                        return false;
                    }
                    lock.writeLock().lock();
                    return true;
                }

                @Override
                public void release() {
                    lock.writeLock().unlock();
                }
            });
        }
        // An expired session is ended the way a released one is — its transaction rolled back and the
        // engine's state for it dropped — rather than merely forgotten by the manager.
        this.sessionManager.setExpiredSessionListener(new ExpiredSessionListener() {
            @Override
            public void expired(final SessionContext session) {
                endSession(session);
            }
        });
        logger.info("Initialized concurrent Frostlake engine");
    }

    /**
     * A concurrent wrapper over an engine other clients in this JVM use too - the engine
     * {@code jdbc:frostlake:direct:<name>} connections share (see {@code DatabaseDriver.directEngine}) - so the
     * wrapper's sessions and those connections read and write one database. Every request runs alone, holding the
     * engine's monitor as a direct connection's statement does, so a request and a direct statement never run at
     * once; one that waits for another session's table lock gives the monitor up meanwhile, as a direct statement
     * does. Each session stays its own: its role, its database and schema, and its transaction, which no other session
     * reads before COMMIT. Shutting the wrapper down ends its sessions and leaves the engine running.
     *
     * @param engine the engine
     * @return the wrapper
     */
    public static ConcurrentDatabaseEngine sharing(final DatabaseEngine engine) {
        return new ConcurrentDatabaseEngine(engine, true);
    }

    /**
     * Execute SQL in a session context
     */
    public ExecutionResult execute(final String sql, final SessionContext session) {
        return execute(sql, session, null);
    }

    /**
     * Execute SQL in a session context, the session first following the autocommit mode the client
     * declares for this request (see {@link SessionContext#followClientAutoCommit(Boolean)}).
     *
     * @param sql the statement text
     * @param session the session to run in
     * @param clientAutoCommit the request's autocommit mode, or null when it declares none
     * @return the statement's result
     */
    public ExecutionResult execute(final String sql, final SessionContext session,
                                   final Boolean clientAutoCommit) {
        if (session == null) {
            throw new IllegalArgumentException("Session context is required");
        }
        // Refused before it is registered again: an aborted session that registered anew could be aborted anew.
        requireLiveSession(session);
        registerForAbort(session);
        try {
            return executeInSession(sql, session, clientAutoCommit, false);
        } finally {
            // A session this request aborted — its own included — ends now that the request holds no lock.
            engine.getSessionDirectory().endAborted();
            if (session.getSettings().isAborted()) {
                // The request of a session another one aborted is answered once the session has ended: its
                // transaction rolled back and its locks released, as live answers it. Another request may be ending
                // the session as this one returns; this waits for it, or ends the session itself.
                endSession(session);
            }
        }
    }

    /**
     * Make a session abortable by SYSTEM$ABORT_SESSION under the number CURRENT_SESSION() answers in it. An
     * aborted session ends as a released one does, so its next request names no live session.
     */
    private void registerForAbort(final SessionContext session) {
        if (abortable.containsKey(session.getSessionId())) {
            return;
        }
        final AbortableHttpSession entry = new AbortableHttpSession(this, session);
        if (abortable.putIfAbsent(session.getSessionId(), entry) == null) {
            engine.getSessionDirectory().register(entry.sessionNumber(), entry);
        }
    }

    /**
     * Refuses every request of a session SYSTEM$ABORT_SESSION ended, COMMIT and ROLLBACK included, as the account
     * refuses the requests of a terminated session (error 390111): the session no longer exists, so nothing it sends
     * runs. A client that keeps the session's context after the abort sent its writes and DDL on, and they ran and
     * committed; its COMMIT answered success although the abort had rolled the transaction back.
     *
     * @param session the session a request runs in
     */
    private static void requireLiveSession(final SessionContext session) {
        if (session.getSettings().isAborted()) {
            throw new RuntimeException(SessionDirectory.SESSION_GONE);
        }
    }

    /**
     * Runs a request in a session.
     *
     * @param sql              the statement text
     * @param session          the session to run in
     * @param clientAutoCommit the request's autocommit mode, or null when it declares none
     * @param ending           whether the request is the rollback that ends the session, which runs although the
     *                         session was aborted
     * @return the statement's result
     */
    private ExecutionResult executeInSession(final String sql, final SessionContext session,
                                             final Boolean clientAutoCommit, final boolean ending) {
        session.touch();
        final SessionState state = stateOf(session);
        synchronized (state) {
            // Under the session's lock, so the mode applied below is the one this request declared.
            session.followClientAutoCommit(clientAutoCommit);
            try {
                // Read lock for read-only scripts, write lock for anything that mutates — decided per request
                // from the parse tree, and from the bodies its names reach before it runs (the unlock below must
                // mirror the decision).
                boolean readOnly = isReadOnlyQuery(sql);
                final boolean carriesOut = !readOnly;
                Set<String> canceledBefore = null;
                if (carriesOut) {
                    // An abort of another session is carried out as the request is read, before it waits for the
                    // engine: that session ends what it runs now rather than running on to its end and committing.
                    RequestSessionAborts.carryOut(sql, session.getSettings().getSessionNumber(),
                        engine.getSessionDirectory());
                    // So is a cancel of a running statement, which holds the engine until it ends.
                    canceledBefore = RequestQueryCancels.carryOut(sql);
                }
                try {
                    if (sharesMonitor) {
                        // On an engine shared with in-process connections every request runs alone on its monitor.
                        synchronized (engine) {
                            try {
                                return runLocked(sql, session, false, ending);
                            } finally {
                                // A direct statement waiting for a lock this request may have let go looks again
                                // at once.
                                engine.notifyAll();
                            }
                        }
                    }
                    if (readOnly) {
                        engineLock.readLock().lock();
                        // Judged under the shared lock, which holds the catalog still: a body one of the request's
                        // names stands for may write, and then the request runs alone, before anything of it has
                        // run. So does a read of a stream inside an open transaction, which may wait for another
                        // session's transaction that holds the stream (see TableLockGate#requireStreamFree).
                        if (ReadOnlyBodies.reachWrite(engine.getCatalog(), sql, session.getSettings())
                                || session.isInTransaction()
                                    && ReadOnlyBodies.namesStream(engine.getCatalog(), sql, session.getSettings())) {
                            engineLock.readLock().unlock();
                            engineLock.writeLock().lock();
                            readOnly = false;
                        }
                    } else {
                        engineLock.writeLock().lock();
                    }
                    try {
                        return runLocked(sql, session, readOnly, ending);
                    } finally {
                        if (readOnly) {
                            engineLock.readLock().unlock();
                        } else {
                            engineLock.writeLock().unlock();
                        }
                    }
                } finally {
                    if (carriesOut) {
                        RequestQueryCancels.end(canceledBefore);
                    }
                }
            } catch (final Exception e) {
                logger.error("Error executing SQL in session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * Runs a request in its session's scope, once it holds the engine: the engine's lock, or its monitor where the
     * engine is shared.
     *
     * @param sql      the statement text
     * @param session  the session to run in
     * @param readOnly whether the request holds the shared lock only
     * @param ending   whether the request is the rollback that ends the session
     * @return the statement's result
     */
    private ExecutionResult runLocked(final String sql, final SessionContext session, final boolean readOnly,
                                      final boolean ending) {
        try {
            if (!ending) {
                // Judged again under the engine's lock: an abort that another request carried out while this
                // one waited for the engine must still stop it before it runs.
                requireLiveSession(session);
            }
            // Apply session context before execution (inside lock for thread safety)
            applySessionContext(session);
            if (sharesMonitor) {
                // A statement that waits for another session's table lock waits on the engine's monitor, which it
                // gives up meanwhile, as a direct connection's statement does (see TableLockGate).
                TableLockGate.register(monitorRelease());
            } else if (!readOnly) {
                // A statement that waits for another session's table lock gives the engine's lock up
                // meanwhile, so the holder's own requests can end its transaction (see TableLockGate). The
                // session's context is bound to this thread, so nothing of it moves while it waits.
                TableLockGate.register(new EngineLockRelease() {
                    private int held;

                    @Override
                    public void release() {
                        held = engineLock.getWriteHoldCount();
                        for (int i = 0; i < held; i++) {
                            engineLock.writeLock().unlock();
                        }
                    }

                    @Override
                    public void pause(final long millis) throws InterruptedException {
                        Thread.sleep(millis);
                    }

                    @Override
                    public void reacquire() {
                        for (int i = 0; i < held; i++) {
                            engineLock.writeLock().lock();
                        }
                    }
                });
            }

            final long startTime = System.currentTimeMillis();
            final ExecutionResult result;
            try {
                result = engine.execute(sql);
            } catch (final RuntimeException failed) {
                // A request that fails part-way keeps what its earlier statements did, so the
                // session must take it back: a transaction a BEGIN opened stays open in the session
                // (live-verified — CURRENT_TRANSACTION() is still set, the session reads its own
                // rows, and COMMIT or ROLLBACK ends it), and a USE stays in force. Left uncaptured,
                // the transaction stayed with this pooled thread, the session's next request cleared
                // it, and SHOW TRANSACTIONS listed it for the server's lifetime under no session.
                captureSessionContext(session);
                throw failed;
            }
            final long duration = System.currentTimeMillis() - startTime;

            logger.debug("Executed SQL in session {} ({}ms): {}",
                session.getSessionId(), duration, sql.substring(0, Math.min(50, sql.length())));

            // Update session context after execution (reads this thread's scope, so it must run
            // before the finally block clears it)
            captureSessionContext(session);

            return result;

        } finally {
            TableLockGate.unregister();
            clearScopes();
        }
    }

    /**
     * Drops this thread's per-session scopes (idempotent) — a failed statement must not leak its session's context
     * into whatever runs on this pooled thread next — and unbinds the transaction, which belongs to the session (its
     * request captured it) and not to this thread, which serves other sessions next.
     */
    private void clearScopes() {
        engine.getCatalog().clearSessionScope();
        engine.getTransactionManager().clearSessionAutoCommit();
        engine.getQueryResultCache().clearSessionScope();
        engine.getSecurityManager().getSessionContext().unbindSettings();
        engine.getTransactionManager().setCurrentTransaction(null);
    }

    /** How a request waiting for a table lock gives up the engine's monitor: each pause waits on it. */
    private EngineLockRelease monitorRelease() {
        return new EngineLockRelease() {
            @Override
            public void release() {
                // The monitor is given up by each pause.
            }

            @Override
            public void pause(final long millis) throws InterruptedException {
                engine.wait(millis);
            }

            @Override
            public void reacquire() {
                // Each pause takes the monitor back as it returns.
            }
        };
    }

    /**
     * The object a session's requests synchronize on. putIfAbsent rather than get/put: two requests for one session
     * must synchronize on the SAME state object, so a lost race here would defeat the lock.
     */
    private SessionState stateOf(final SessionContext session) {
        final String sessionId = session.getSessionId();
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            state = new SessionState();
            final SessionState raced = sessionStates.putIfAbsent(sessionId, state);
            if (raced != null) {
                state = raced;
            }
        }
        return state;
    }

    /**
     * The results a query would answer in a session, each with its columns and no rows, compiled as a view's body is
     * (see {@link RelationShapeOnly}) and never run: what the Snowflake driver's describe asks of a prepared statement.
     * It takes no query ID and no history row, and leaves the session as it was. A query that does not compile is
     * refused as it would be when run.
     *
     * @param sql     the query, its placeholders written as values
     * @param session the session it would run in
     * @return its results, without rows
     */
    public List<ResultSet> shapeOf(final String sql, final SessionContext session) {
        try {
            return inSession(session, new SessionWork<List<ResultSet>>() {
                @Override
                public List<ResultSet> run(final DatabaseEngine bound) {
                    final boolean previous = RelationShapeOnly.begin();
                    try {
                        return bound.getExecutor().executeUnrecorded(sql);
                    } finally {
                        RelationShapeOnly.end(previous);
                    }
                }
            });
        } catch (final IOException unreachable) {
            throw new IllegalStateException(unreachable);
        }
    }

    /**
     * Does work on the engine in a session's scope - its current database and schema, its role, its settings and its
     * transaction - alone, as a request of the session runs, and leaves the session as it was: no statement runs, so
     * nothing is recorded and the session takes nothing back.
     *
     * @param session the session
     * @param work    the work
     * @param <T>     what it answers
     * @return what it answers
     * @throws IOException where the work reads or writes a file and fails
     */
    public <T> T inSession(final SessionContext session, final SessionWork<T> work) throws IOException {
        requireLiveSession(session);
        session.touch();
        synchronized (stateOf(session)) {
            engineLock.writeLock().lock();
            try {
                if (sharesMonitor) {
                    synchronized (engine) {
                        return inScope(session, work);
                    }
                }
                return inScope(session, work);
            } finally {
                engineLock.writeLock().unlock();
            }
        }
    }

    /** {@link #inSession}'s work, once it holds the engine. */
    private <T> T inScope(final SessionContext session, final SessionWork<T> work) throws IOException {
        try {
            applySessionContext(session);
            return work.run(engine);
        } finally {
            clearScopes();
        }
    }

    /**
     * Execute query and return single result set
     */
    public ResultSet executeQuery(final String sql, final SessionContext session) {
        final ExecutionResult result = execute(sql, session);
        if (!result.isSuccess()) {
            throw new RuntimeException("Query execution failed: " + result.getErrorMessage());
        }
        if (result.getResultSets().isEmpty()) {
            return new ResultSet(List.of());
        }
        return result.getResultSets().get(0);
    }

    /**
     * Whether EVERY statement in {@code sql} may run under the SHARED read lock — decided from the
     * PARSE TREE, not a text prefix: 'SELECT 1; DROP TABLE t' is a write, and so is
     * 'SELECT seq1.NEXTVAL' (the sequence advances) and a query that reaches a write through TO_QUERY or a
     * system function that acts (see {@link ReadOnlyRequest}). Anything unparseable classifies as a WRITE,
     * so the exclusive lock is the failure mode.
     */
    private boolean isReadOnlyQuery(final String sql) {
        return ReadOnlyRequest.reads(sql);
    }

    /**
     * Apply session context to engine
     */
    private void applySessionContext(final SessionContext session) {
        try {
            // Bind this session's context to the CURRENT THREAD instead of mutating the shared engine
            // state: statements from different sessions run concurrently under the read lock, and the
            // old useDatabase/setAutoCommit calls clobbered one shared field — a USE in one session
            // leaked into another, and captureSessionContext then wrote the WRONG database back into
            // the session (which is how a session ended up pointed at another test's dropped clone).
            // The scopes are cleared in execute()'s finally.
            // The current warehouse is the session's own too: a USE WAREHOUSE in one session moved every
            // session's CURRENT_WAREHOUSE() while the engine kept a single one. A session that has run no
            // statement yet starts from its user's DEFAULT_WAREHOUSE, or from the engine's.
            final Catalog catalog = engine.getCatalog();
            catalog.beginSessionScope(session.getCurrentDatabase(), session.getCurrentSchema(),
                session.isWarehouseSettled() ? session.getCurrentWarehouse() : catalog.sessionStartWarehouse());
            engine.getTransactionManager().beginSessionAutoCommit(session.isAutoCommit());
            // LAST_QUERY_ID must follow the SESSION, not the pool thread that happens to serve this
            // statement: consecutive requests of one session land on different threads, and a
            // thread-keyed history would lose the previous statement's ID between them.
            engine.getQueryResultCache().beginSessionScope(session.getSessionId());
            // ALTER SESSION parameters and SET variables follow the session too: one engine-wide store
            // let a TIMEZONE, a QUERY_TAG or a variable set in one session reach every other.
            engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());

            // ALWAYS restore (or clear, when null) this session's transaction. The shared engine uses a
            // thread-local "current transaction", so if we skipped this when the session has none, the
            // session would inherit the previous session's transaction and see its uncommitted writes —
            // breaking multi-session READ COMMITTED isolation.
            engine.getTransactionManager().setCurrentTransaction(session.getTransactionId());
        } catch (final Exception e) {
            logger.warn("Error applying session context: {}", e.getMessage());
        }
    }

    /**
     * Bind the session's JSON_INDENT to this thread for rendering a statement's rows after the statement's own
     * scope has closed — read from the session's own settings, which only its statements see. The caller clears
     * it with {@code VariantJsonFormat.clearSessionScope}.
     *
     * @param session the session whose rows are rendered
     */
    public void bindJsonIndent(final SessionContext session) {
        engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());
        try {
            engine.bindJsonIndent();
        } finally {
            engine.getSecurityManager().getSessionContext().unbindSettings();
        }
    }

    /**
     * The zone a session's TIMEZONE names — its own, its user's or the account's — read from the session's own
     * settings, which only its statements see.
     *
     * @param session the session
     * @return the zone's name, or null where nothing names one
     */
    public String sessionTimezone(final SessionContext session) {
        engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());
        try {
            final Object zone = engine.getSecurityManager().getSessionContext().getSessionParameter("TIMEZONE");
            return zone == null ? null : zone.toString();
        } finally {
            engine.getSecurityManager().getSessionContext().unbindSettings();
        }
    }

    /**
     * A session's primary role, read from the session's own settings: its own once a USE ROLE set one, else the
     * engine's.
     *
     * @param session the session
     * @return the role's name
     */
    public String sessionRole(final SessionContext session) {
        engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());
        try {
            return engine.getSecurityManager().getSessionContext().getCurrentRole();
        } finally {
            engine.getSecurityManager().getSessionContext().unbindSettings();
        }
    }

    /**
     * A session parameter's value - the session's own, its user's or the account's, the zone this engine's account
     * sets among them (see {@link AccountParameters}) - read from the session's own settings, as SHOW PARAMETERS reads
     * it.
     *
     * @param session the session
     * @param name    the parameter's name, upper-cased
     * @return its value, or null where nothing sets it
     */
    public Object sessionParameter(final SessionContext session, final String name) {
        engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());
        try {
            final Object value = engine.getSecurityManager().getSessionContext().getSessionParameter(name);
            return value != null ? value : AccountParameters.value(engine.getCatalog(), name);
        } finally {
            engine.getSecurityManager().getSessionContext().unbindSettings();
        }
    }

    /**
     * Whether a session's CLIENT_RESULT_COLUMN_CASE_INSENSITIVE is TRUE - its own, its user's or the account's - read
     * from the session's own settings, as {@link #sessionTimezone} reads its zone.
     *
     * @param session the session
     * @return whether it is
     */
    public boolean resultColumnsCaseInsensitive(final SessionContext session) {
        engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());
        try {
            return engine.getSecurityManager().getSessionContext().resultColumnsCaseInsensitive();
        } finally {
            engine.getSecurityManager().getSessionContext().unbindSettings();
        }
    }

    /**
     * The user a session's statements run as, as CURRENT_USER() names it: the engine's own, which no session changes.
     *
     * @return the user's name
     */
    public String currentUser() {
        return engine.getSecurityManager().getSessionContext().getDisplayUser();
    }

    /**
     * Capture current engine state back to session
     */
    private void captureSessionContext(final SessionContext session) {
        try {
            session.setCurrentDatabase(engine.getCatalog().heldDatabaseName());
            session.setCurrentSchema(engine.getCatalog().heldSchemaName());
            session.setCurrentWarehouse(engine.getCatalog().getCurrentWarehouse());
            session.setAutoCommit(engine.isAutoCommit());
            // An aborted transaction the session has not acknowledged is still the session's to end.
            session.setInTransaction(engine.getTransactionManager().hasActiveTransaction()
                || engine.getTransactionManager().getAbortedTransaction() != null);
            session.setTransactionId(engine.getTransactionManager().getCurrentTransactionId());
        } catch (final Exception e) {
            logger.warn("Error capturing session context: {}", e.getMessage());
        }
    }

    // Session management

    public SessionContext createSession() {
        return sessionManager.createSession();
    }

    public SessionContext getOrCreateSession(final String sessionId) {
        engine.getSessionDirectory().endAborted();
        return sessionManager.getOrCreateSession(sessionId);
    }

    /** A live session, or null: a session SYSTEM$ABORT_SESSION ended is none. */
    public SessionContext getSession(final String sessionId) {
        engine.getSessionDirectory().endAborted();
        return sessionManager.getSession(sessionId);
    }

    /**
     * The session held under an id, or null, found without ending the sessions an abort left: ending one waits for the
     * engine, and a cancel must never wait behind the statement it cancels.
     *
     * @param sessionId the session's id
     * @return the session, or null
     */
    public SessionContext findSession(final String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    public void removeSession(final String sessionId) {
        final AbortableHttpSession entry = abortable.remove(sessionId);
        if (entry != null) {
            engine.getSessionDirectory().unregister(entry.sessionNumber());
        }
        sessionStates.remove(sessionId);
        engine.getQueryResultCache().forgetSession(sessionId);
        sessionManager.removeSession(sessionId);
    }

    /**
     * End a session for good: roll back the transaction it left open, drop what it created TEMPORARY, then drop
     * everything the engine kept for it. Both a released session and an expired one end this way, so neither can
     * leave a transaction running with nobody to end it, nor a temporary table or function for the sessions after
     * it to find.
     *
     * @param session the session to end
     */
    public void endSession(final SessionContext session) {
        // A request of the session's that is still running ends first. A transaction it opened is the session's only
        // once the request captures it as it ends, so a session aborted while its request waits for a table lock is
        // judged after that request: the abort ends the wait at once.
        final SessionState state = sessionStates.get(session.getSessionId());
        if (state == null) {
            endSessionNow(session);
            return;
        }
        synchronized (state) {
            endSessionNow(session);
        }
    }

    /**
     * Rolls back every transaction still open under an ending session's number: one its requests left without the
     * session holding it, or one whose ROLLBACK above failed, would otherwise keep its locks with nobody to end it.
     * Called under the engine's write lock.
     */
    private void rollBackLeftTransactions(final long sessionNumber) {
        final List<Transaction> left = new ArrayList<Transaction>();
        for (final Transaction open : engine.getTransactionManager().getActiveTransactions()) {
            if (open.getSessionNumber() != null && open.getSessionNumber().longValue() == sessionNumber
                    && !open.isStatementScoped()) {
                left.add(open);
            }
        }
        for (final Transaction open : left) {
            try {
                engine.getTransactionManager().rollBackLeft(open);
            } catch (final RuntimeException failed) {
                logger.warn("Rolling back a transaction ended session {} left failed: {}", sessionNumber,
                    failed.getMessage());
            }
        }
    }

    /** {@link #endSession}'s work, once no request of the session is running. */
    private void endSessionNow(final SessionContext session) {
        if (session.isInTransaction()) {
            try {
                // Under the engine's lock like any request: the idle sweep's thread is not a request thread. It
                // runs although the session was aborted, since it is what ends the session.
                executeInSession("ROLLBACK", session, null, true);
            } catch (final RuntimeException failed) {
                // The session goes regardless; its transaction's writes were never committed.
                logger.warn("Rolling back ended session {} failed: {}", session.getSessionId(), failed.getMessage());
            }
        }
        // What the session created TEMPORARY ends with it: under the engine's write lock, like the DROP it is.
        engineLock.writeLock().lock();
        try {
            if (sharesMonitor) {
                synchronized (engine) {
                    dropWhatSessionLeft(session);
                }
            } else {
                dropWhatSessionLeft(session);
            }
        } finally {
            engineLock.writeLock().unlock();
        }
        removeSession(session.getSessionId());
    }

    /** Rolls back the transactions an ending session left and drops what it created TEMPORARY. */
    private void dropWhatSessionLeft(final SessionContext session) {
        try {
            rollBackLeftTransactions(session.getSettings().getSessionNumber());
            engine.dropTemporaryObjectsOf(session.getSettings().getSessionNumber());
        } catch (final RuntimeException failed) {
            logger.warn("Dropping the temporary objects of ended session {} failed: {}", session.getSessionId(),
                failed.getMessage());
        }
    }

    /**
     * End every session idle for longer than {@code idleMs} now, rather than at the next scheduled sweep.
     *
     * @param idleMs how long a session may sit idle
     * @return how many sessions were ended
     */
    public int expireIdleSessions(final long idleMs) {
        return sessionManager.expireSessionsIdleFor(idleMs);
    }

    /** How long a session may sit idle before it is ended, in milliseconds; 0 when sessions never idle out. */
    public long getSessionIdleTimeoutMs() {
        return sessionManager.getSessionTimeoutMs();
    }

    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    /**
     * The local file a stage file URL names — BUILD_STAGE_FILE_URL's {@code /api/files/<database>/<schema>/<stage>/<path>}
     * with each name canonical — found under the engine's read lock, so no statement changes the stage meanwhile.
     *
     * @param database the database, canonical
     * @param schema   the schema, canonical
     * @param stage    the stage's name, canonical
     * @param relative the file's stage-relative name
     * @return the file's local path, or null when no named stage has those names or it has no local directory
     */
    public Path stageFileUrlTarget(final String database, final String schema, final String stage,
                                   final String relative) {
        engineLock.readLock().lock();
        try {
            if (sharesMonitor) {
                synchronized (engine) {
                    return stageFile(database, schema, stage, relative);
                }
            }
            return stageFile(database, schema, stage, relative);
        } finally {
            engineLock.readLock().unlock();
        }
    }

    private Path stageFile(final String database, final String schema, final String stage, final String relative) {
        final NamedStage named = engine.getExecutor().namedStage(database, schema, stage);
        return named == null ? null : named.fileAt(relative);
    }

    // Component access

    public DatabaseEngine getEngine() {
        return engine;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Shutdown the concurrent engine
     */
    public void shutdown() {
        logger.info("Shutting down concurrent Frostlake engine");
        if (sharesMonitor) {
            // The engine goes on serving its other clients, so the sessions end as released ones do - every one,
            // however recently it ran: what each left open is rolled back, what it created TEMPORARY dropped.
            sessionManager.expireSessionsIdleFor(-1L);
        }
        sessionManager.shutdown();
        if (!sharesMonitor) {
            engine.shutdown();
        }
        sessionStates.clear();
    }

}
