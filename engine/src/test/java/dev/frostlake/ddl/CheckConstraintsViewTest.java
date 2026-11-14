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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA.CHECK_CONSTRAINTS lists every CHECK constraint of the database's tables, one row each, under
 * the name a CONSTRAINT clause gave it — SYS_CONSTRAINT_&lt;uuid&gt; where none was written — with its clause as
 * written, blanks included; a constraint dropped from its table, or a dropped table's, leaves no row
 * (live-verified). The tables take a name of their own each run: the account goes on listing the constraints of a
 * database replaced by one of the same name, a fault of its own this view does not imitate.
 */
public class CheckConstraintsViewTest extends BaseDatabaseTest {

    private static String rows(final ResultSet rs) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void everyCheckIsListedWithItsClauseAsWritten() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String one = "CKV1_" + suffix;
        final String two = "CKV2_" + suffix;
        final String three = "CKV3_" + suffix;
        engine.execute("CREATE TABLE " + one + " (n INT CHECK (n > 0), m INT, CONSTRAINT ck_m CHECK (m > 0))");
        engine.execute("CREATE TABLE " + two + " (a INT, b INT, CONSTRAINT c_ab CHECK (a < b))");
        engine.execute("ALTER TABLE " + two + " ADD CONSTRAINT c2 CHECK (a < 10) ENABLE NOVALIDATE");
        engine.execute("CREATE TABLE " + three + " (n INT, s VARCHAR, CONSTRAINT c3 CHECK (n BETWEEN 1 AND 10),"
            + " CONSTRAINT c4 CHECK (s LIKE 'a%' AND UPPER(s) <> s), CONSTRAINT \"Quoted c\" CHECK ( n  >  0 ))");
        final String listing = "SELECT CONSTRAINT_CATALOG, CONSTRAINT_SCHEMA, REPLACE(CONSTRAINT_TABLE, '_" + suffix
            + "'), REGEXP_REPLACE(CONSTRAINT_NAME, '^SYS_CONSTRAINT_[0-9a-f-]{36}$', 'SYS_CONSTRAINT_<ID>'),"
            + " CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS WHERE STARTSWITH(CONSTRAINT_TABLE, 'CKV')"
            + " AND ENDSWITH(CONSTRAINT_TABLE, '_" + suffix + "') ORDER BY 3, 4";
        assertEquals("TEST_DB|TEST_SCHEMA|CKV1|CK_M|m > 0 ;; TEST_DB|TEST_SCHEMA|CKV1|SYS_CONSTRAINT_<ID>|n > 0 ;; "
            + "TEST_DB|TEST_SCHEMA|CKV2|C2|a < 10 ;; TEST_DB|TEST_SCHEMA|CKV2|C_AB|a < b ;; "
            + "TEST_DB|TEST_SCHEMA|CKV3|C3|n BETWEEN 1 AND 10 ;; "
            + "TEST_DB|TEST_SCHEMA|CKV3|C4|s LIKE 'a%' AND UPPER(s) <> s ;; TEST_DB|TEST_SCHEMA|CKV3|Quoted c|n  >  0",
            rows(engine.executeQuery(listing)));

        // A constraint dropped from its table, and a dropped table's, leave no row.
        engine.execute("ALTER TABLE " + two + " DROP CONSTRAINT c2");
        engine.execute("DROP TABLE " + three);
        assertEquals("TEST_DB|TEST_SCHEMA|CKV1|CK_M|m > 0 ;; TEST_DB|TEST_SCHEMA|CKV1|SYS_CONSTRAINT_<ID>|n > 0 ;; "
            + "TEST_DB|TEST_SCHEMA|CKV2|C_AB|a < b", rows(engine.executeQuery(listing)));
    }

    @Test
    public void everySchemaOfTheDatabaseIsListed() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        engine.execute("CREATE SCHEMA ckv_s");
        engine.execute("CREATE TABLE ckv_s.CKV5_" + suffix + " (n INT CHECK (n <> 5))");
        engine.execute("CREATE VIEW CKV6_" + suffix + " AS SELECT * FROM ckv_s.CKV5_" + suffix);
        assertEquals("CKV_S|n <> 5", rows(engine.executeQuery("SELECT CONSTRAINT_SCHEMA, CHECK_CLAUSE"
            + " FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS WHERE CONSTRAINT_TABLE IN ('CKV5_" + suffix + "', 'CKV6_"
            + suffix + "')")));
    }
}
