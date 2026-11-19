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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * SHOW PROCEDURES lists the account's built-in procedures the engine answers a CALL of (live-verified):
 * SYSTEM$SEND_EMAIL, one row for each of its two overloads, {@code is_builtin} Y, created at the account's epoch,
 * in no schema and no database. The rows stand among the user procedures where the name sorts, in every scope -
 * the current schema, IN SCHEMA, IN DATABASE and IN ACCOUNT - and in SHOW BUILTIN PROCEDURES, and SHOW USER
 * PROCEDURES leaves them out. The account lists more built-in procedures, none of which the engine runs, so each
 * statement here reads the rows by name.
 */
public class ShowBuiltinProceduresListedTest extends MeasuredCellsTestSupport {

    @Test
    public void theSendEmailProcedureIsListedAsBuiltIn() {
        engine.execute("CREATE SCHEMA s2");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE PROCEDURE aaa1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE PROCEDURE t_mid() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE PROCEDURE zzz1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertCells(new String[][] {
            {"SHOW PROCEDURES ->> SELECT LISTAGG(\"name\", ',') FROM $1 WHERE \"name\" IN "
                + "('AAA1','T_MID','ZZZ1','SYSTEM$SEND_EMAIL')",
                "AAA1,SYSTEM$SEND_EMAIL,SYSTEM$SEND_EMAIL,T_MID,ZZZ1"},
            {"SHOW PROCEDURES IN DATABASE test_db ->> SELECT LISTAGG(\"name\", ',') FROM $1 WHERE \"name\""
                + " IN ('AAA1','T_MID','ZZZ1','SYSTEM$SEND_EMAIL')",
                "AAA1,SYSTEM$SEND_EMAIL,SYSTEM$SEND_EMAIL,T_MID,ZZZ1"},
            {"SHOW PROCEDURES IN SCHEMA test_db.test_schema ->> SELECT LISTAGG(\"name\", ',') FROM $1 WHERE "
                + "\"name\" IN ('AAA1','T_MID','ZZZ1','SYSTEM$SEND_EMAIL')",
                "AAA1,SYSTEM$SEND_EMAIL,SYSTEM$SEND_EMAIL,T_MID,ZZZ1"},
            {"SHOW PROCEDURES IN SCHEMA test_db.s2 ->> SELECT LISTAGG(\"name\", ',') FROM $1 WHERE "
                + "\"name\" IN ('AAA1','T_MID','ZZZ1','SYSTEM$SEND_EMAIL')",
                "SYSTEM$SEND_EMAIL,SYSTEM$SEND_EMAIL"},
            {"SHOW USER PROCEDURES IN DATABASE test_db ->> SELECT LISTAGG(\"name\", ',') FROM $1 WHERE "
                + "\"name\" IN ('AAA1','T_MID','ZZZ1','SYSTEM$SEND_EMAIL')",
                "AAA1,T_MID,ZZZ1"},
            {"SHOW BUILTIN PROCEDURES IN SCHEMA test_db.s2 ->> SELECT LISTAGG(\"name\", ',') FROM $1 WHERE"
                + " \"name\" IN ('AAA1','T_MID','ZZZ1','SYSTEM$SEND_EMAIL')",
                "SYSTEM$SEND_EMAIL,SYSTEM$SEND_EMAIL"},
            {"SHOW PROCEDURES LIKE '%SEND_EMAIL%' ->> SELECT SYSTEM$TYPEOF(\"created_on\") FROM $1 LIMIT 1",
                "TIMESTAMP_LTZ(3)[SB8]"},
            {"SHOW PROCEDURES LIKE 'SYSTEM$SEND_EMAIL' IN SCHEMA test_db.test_schema ->> SELECT COUNT(*) FROM "
                + "$1",
                "2"},
            {"SHOW USER PROCEDURES LIKE 'SYSTEM$SEND_EMAIL' ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW BUILTIN PROCEDURES LIKE 'SYSTEM$SEND_EMAIL' ->> SELECT COUNT(*) FROM $1",
                "2"},
            {"SHOW PROCEDURES LIKE 'SYSTEM$SEND_EMAIL' IN ACCOUNT ->> SELECT COUNT(*) FROM $1",
                "2"},
            {"SHOW PROCEDURES LIKE 'system$send_email' ->> SELECT COUNT(*) FROM $1",
                "2"},
            {"SHOW TERSE PROCEDURES LIKE 'SYSTEM$SEND_EMAIL' ->> SELECT COUNT(*) FROM $1",
                "2"},
            {"SHOW PROCEDURES LIKE 'SYSTEM$SEND_EMAIL' ->> SELECT COUNT(*) FROM $1 WHERE \"is_builtin\" = "
                + "'Y'",
                "2"},
            {"SHOW PROCEDURES IN SCHEMA test_db.s2 ->> SELECT COUNT(*) FROM $1 WHERE \"is_builtin\" = 'N'",
                "0"},
            {"SHOW USER PROCEDURES ->> SELECT COUNT(*) FROM $1 WHERE \"is_builtin\" = 'Y'",
                "0"},
            {"SHOW PROCEDURES IN SCHEMA test_db.test_schema ->> SELECT COUNT(*) FROM $1 WHERE \"is_builtin\" = "
                + "'N'",
                "3"},
        });
    }
}
