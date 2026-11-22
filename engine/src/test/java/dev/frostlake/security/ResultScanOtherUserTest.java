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
package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A query's result is read back only by the user who ran the query (documented: not even ACCOUNTADMIN reads the
 * results of another user's query through RESULT_SCAN). To any other user its ID names no statement, whatever role
 * that user holds - RESULT_SCAN and DESCRIBE RESULT are refused as for an ID nothing answered under - while the
 * user who ran it reads it from any of their sessions. Engine only: the live account allows no CREATE USER or GRANT.
 */
public class ResultScanOtherUserTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE TABLE d1.s1.t (id INT, s STRING)");
        engine.execute("INSERT INTO d1.s1.t VALUES (1, 'secret')");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("CREATE USER u2 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT ROLE r1 TO USER u2");
        engine.execute("GRANT ROLE " + systemRole + " TO USER u2");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
        }
        return out.toString();
    }

    /** The ID of the statement the session ran last. */
    private String lastQueryId() {
        return String.valueOf(engine.executeQuery("SELECT LAST_QUERY_ID()").getRows().get(0).getValue(0));
    }

    @Test
    public void anotherUsersResultIsNoStatement() {
        engine.execute("SELECT * FROM d1.s1.t");
        final String queryId = lastQueryId();
        as("u1", "r1");
        assertEquals("Statement " + queryId + " not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))"));
        assertEquals("Statement " + queryId + " not found", refusal("DESCRIBE RESULT '" + queryId + "'"));
    }

    @Test
    public void theRoleOfTheUserWhoRanItDoesNotLetAnotherUserReadIt() {
        engine.execute("SELECT * FROM d1.s1.t");
        final String queryId = lastQueryId();
        as("u2", systemRole);
        assertEquals("Statement " + queryId + " not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))"));
    }

    @Test
    public void anotherUsersFailedStatementIsNoStatementEither() {
        refusal("SELECT * FROM d1.s1.nosuch");
        final String queryId = lastQueryId();
        assertEquals("Query " + queryId + " has no result because it failed",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))"));
        as("u1", "r1");
        assertEquals("Statement " + queryId + " not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))"));
    }

    @Test
    public void theUserWhoRanItReadsItAgainUnderAnyRole() {
        as("u2", systemRole);
        engine.execute("SELECT * FROM d1.s1.t");
        final String queryId = lastQueryId();
        as("u2", "r1");
        assertEquals("[1, secret]", rows("SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))"));
        as("u1", "r1");
        assertEquals("Statement " + queryId + " not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('" + queryId + "'))"));
    }
}
