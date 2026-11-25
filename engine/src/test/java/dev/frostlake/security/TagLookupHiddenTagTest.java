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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * SYSTEM$GET_TAG and TAG_REFERENCES resolve the tag before the object: a tag in a database the session's role cannot
 * see is refused as that database, whatever the object and its kind — a stage, a stream, a task, an alert, a policy,
 * a function, a procedure, a role, a warehouse or the account (live-verified, the role being PUBLIC alone).
 */
public class TagLookupHiddenTagTest extends MeasuredCellsTestSupport {

    private static final String HIDDEN = "REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not "
        + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.";

    /** The role the session ran with before the test moved it to PUBLIC, or null when it did not. */
    private String formerRole;

    /** Puts the session back on the role it ran with, before the test's database is dropped. */
    @AfterEach
    public void restoreRole() {
        if (formerRole != null) {
            engine.execute("USE ROLE " + formerRole);
            engine.execute("USE SECONDARY ROLES ALL");
            formerRole = null;
        }
    }

    @Test
    public void aTagOfADatabaseTheRoleCannotSeeIsRefusedAsTheDatabase() {
        engine.execute("CREATE TAG test_db.test_schema.tg");
        engine.execute("CREATE TABLE test_db.test_schema.t (x INT)");
        engine.execute("CREATE STAGE test_db.test_schema.stg");
        engine.execute("CREATE STREAM test_db.test_schema.st ON TABLE test_db.test_schema.t");
        engine.execute("CREATE TASK test_db.test_schema.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE PASSWORD POLICY test_db.test_schema.pp");
        engine.execute("CREATE FUNCTION test_db.test_schema.f(x NUMBER) RETURNS NUMBER AS 'x'");
        engine.execute("ALTER STAGE test_db.test_schema.stg SET TAG test_db.test_schema.tg = 'v'");
        engine.execute("ALTER FUNCTION test_db.test_schema.f(NUMBER) SET TAG test_db.test_schema.tg = 'v'");
        final String role = answer("SELECT CURRENT_ROLE()");
        engine.execute("USE ROLE PUBLIC");
        formerRole = role;
        engine.execute("USE SECONDARY ROLES NONE");
        final String[][] objects = {
            {"test_db.test_schema.t", "TABLE"}, {"test_db.test_schema.stg", "STAGE"},
            {"test_db.test_schema.st", "STREAM"}, {"test_db.test_schema.tk", "TASK"},
            {"test_db.test_schema.pp", "PASSWORD POLICY"}, {"test_db.test_schema.f(NUMBER)", "FUNCTION"},
            {"test_db.test_schema.nosuch", "STAGE"}, {"ACCOUNTADMIN", "ROLE"}, {"NOSUCHROLE", "ROLE"},
            {"NOSUCHWH", "WAREHOUSE"}, {"NOSUCHUSER", "USER"}, {"NOSUCHINT", "INTEGRATION"},
        };
        for (final String[] object : objects) {
            assertAnswer(HIDDEN, "SELECT SYSTEM$GET_TAG('test_db.test_schema.tg', '" + object[0] + "', '"
                + object[1] + "')");
        }
        assertAnswer(HIDDEN, "SELECT SYSTEM$GET_TAG('test_db.test_schema.nosuch', 'ACCOUNTADMIN', 'ROLE')");
        assertAnswer(HIDDEN, "SELECT SYSTEM$GET_TAG('test_db.nosuch.nosuch', 'ACCOUNTADMIN', 'ROLE')");
    }
}
