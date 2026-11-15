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
 * A database is named by one part, or by two whose first is this account's locator. DROP, UNDROP, ALTER and COMMENT ON
 * DATABASE refuse any other name of two or three parts with the generic sentence and one of four or more as a database
 * identifier with too many qualifiers, IF EXISTS forgiving neither, as CREATE DATABASE does; a quoted name holding a dot
 * is one part, and every statement — USE among them — names the database it spells. UNDROP, ALTER and COMMENT ON take
 * the name through IDENTIFIER() too, and an UNDROP over a database that exists is refused for the name before any
 * dropped version is looked for.
 */
public class DatabaseNamePartsTest extends MeasuredCellsTestSupport {

    /** The databases the tests make, dropped before and after each. */
    private static final String[] DATABASES = {"fl_dnp.a", "fl_dnp.b", "FL_DNP_ACC"};

    private static final String NAMES_NOTHING = "REFUSED SQL compilation error: | Object does not exist, or "
        + "operation cannot be performed.";

    private static final String TOO_MANY = "REFUSED SQL compilation error: database identifier "
        + "'FL_DNP_A.FL_DNP_B.FL_DNP_C.FL_DNP_D' has too many qualifiers";

    @Override
    protected void setupTest() {
        dropDatabases();
    }

    @Override
    protected void teardownTest() {
        dropDatabases();
    }

    private void dropDatabases() {
        for (final String name : DATABASES) {
            engine.execute("DROP DATABASE IF EXISTS \"" + name + "\"");
        }
        engine.execute("USE SCHEMA test_db.test_schema");
    }

    /** A syntax error's answer at a position of the statement's first line. */
    private static String syntaxError(final int position, final String token) {
        return "REFUSED SQL compilation error: | syntax error line 1 at position " + position + " unexpected '"
            + token + "'.";
    }

    @Test
    public void aNameOfSeveralPartsNamesNoDatabase() {
        assertCells(new String[][] {
            {"DROP DATABASE fl_dnp_a.fl_dnp_b", NAMES_NOTHING},
            {"DROP DATABASE IF EXISTS fl_dnp_a.fl_dnp_b", NAMES_NOTHING},
            {"DROP DATABASE fl_dnp_a.fl_dnp_b.fl_dnp_c", NAMES_NOTHING},
            {"DROP DATABASE fl_dnp_a.fl_dnp_b.fl_dnp_c.fl_dnp_d", TOO_MANY},
            {"DROP DATABASE IDENTIFIER('fl_dnp_a.fl_dnp_b')", NAMES_NOTHING},
            {"DROP DATABASE IF EXISTS fl_dnp_a..fl_dnp_b", NAMES_NOTHING},
            {"UNDROP DATABASE fl_dnp_a.fl_dnp_b", NAMES_NOTHING},
            {"UNDROP DATABASE fl_dnp_a.fl_dnp_b.fl_dnp_c.fl_dnp_d", TOO_MANY},
            {"ALTER DATABASE fl_dnp_a.fl_dnp_b SET COMMENT = 'x'", NAMES_NOTHING},
            {"ALTER DATABASE IF EXISTS fl_dnp_a.fl_dnp_b SET COMMENT = 'x'", NAMES_NOTHING},
            {"ALTER DATABASE fl_dnp_a.fl_dnp_b.fl_dnp_c.fl_dnp_d SET COMMENT = 'x'", TOO_MANY},
            {"COMMENT IF EXISTS ON DATABASE fl_dnp_a.fl_dnp_b IS 'c'", NAMES_NOTHING},
            {"COMMENT ON DATABASE fl_dnp_a.fl_dnp_b.fl_dnp_c.fl_dnp_d IS 'c'", TOO_MANY},
            {"DESCRIBE DATABASE fl_dnp_a.fl_dnp_b", NAMES_NOTHING},
            {"USE DATABASE fl_dnp_a.fl_dnp_b", NAMES_NOTHING},
        });
    }

    @Test
    public void aQuotedNameHoldingADot() {
        assertCells(new String[][] {
            {"CREATE DATABASE \"fl_dnp.a\"", "Database fl_dnp.a successfully created."},
            {"ALTER DATABASE \"fl_dnp.a\" SET COMMENT = 'x'", "Statement executed successfully."},
            {"USE DATABASE \"fl_dnp.a\"", "Statement executed successfully."},
            {"SELECT CURRENT_DATABASE()", "fl_dnp.a"},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"DROP DATABASE \"fl_dnp.a\"", "fl_dnp.a successfully dropped."},
            {"UNDROP DATABASE \"fl_dnp.a\"", "Database fl_dnp.a successfully restored."},
            {"DROP DATABASE IDENTIFIER('\"fl_dnp.a\"')", "fl_dnp.a successfully dropped."},
            {"UNDROP DATABASE IDENTIFIER('\"fl_dnp.a\"')", "Database fl_dnp.a successfully restored."},
            {"ALTER DATABASE \"fl_dnp.a\" RENAME TO \"fl_dnp.b\"", "Statement executed successfully."},
            {"DROP DATABASE IF EXISTS \"fl_dnp.b\"", "fl_dnp.b successfully dropped."},
        });
    }

    @Test
    public void anAccountQualifiedNameAndIdentifier() {
        assertCells(new String[][] {
            {"SET fl_dnp_acc = CURRENT_ACCOUNT() || '.fl_dnp_acc'", "Statement executed successfully."},
            {"CREATE DATABASE IDENTIFIER($fl_dnp_acc)", "Database FL_DNP_ACC successfully created."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"DROP DATABASE IDENTIFIER($fl_dnp_acc)", "FL_DNP_ACC successfully dropped."},
            {"UNDROP DATABASE IDENTIFIER($fl_dnp_acc)", "Database FL_DNP_ACC successfully restored."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"ALTER DATABASE IDENTIFIER($fl_dnp_acc) SET COMMENT = 'y'", "Statement executed successfully."},
            {"COMMENT ON DATABASE IDENTIFIER('fl_dnp_acc') IS 'c'", "Statement executed successfully."},
            {"ALTER DATABASE IDENTIFIER('\"fl_dnp_nosuch\"') SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Database '\"fl_dnp_nosuch\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE \"fl_dnp_nosuch\"."},
            {"ALTER DATABASE IF EXISTS IDENTIFIER('\"fl_dnp_nosuch\"') SET COMMENT = 'x'",
                "Statement executed successfully."},
            {"COMMENT IF EXISTS ON DATABASE IDENTIFIER('fl_dnp_nosuch') IS 'c'", "Statement executed successfully."},
            {"UNDROP DATABASE IDENTIFIER('fl_dnp_never')", "REFUSED Database FL_DNP_NEVER did not exist or was purged."},
            {"UNDROP DATABASE test_db", "REFUSED SQL compilation error: | Object 'TEST_DB' already exists."},
            {"DROP DATABASE IF EXISTS IDENTIFIER($fl_dnp_acc)", "FL_DNP_ACC successfully dropped."},
        });
    }

    @Test
    public void theWordIdentifierWithoutAWholeReference() {
        assertCells(new String[][] {
            {"UNDROP DATABASE IDENTIFIER('a' || 'b')", syntaxError(26, "(")},
            {"UNDROP DATABASE IDENTIFIER()", syntaxError(26, "(")},
            {"UNDROP DATABASE IDENTIFIER('a' || 'b'); SELECT 1 +", syntaxError(26, "(")},
            {"ALTER DATABASE IDENTIFIER('a' || 'b') SET COMMENT = 'x'", syntaxError(30, "||")},
            {"ALTER DATABASE IF EXISTS IDENTIFIER('a' || 'b') SET COMMENT = 'x'", syntaxError(40, "||")},
            {"ALTER DATABASE IDENTIFIER() SET COMMENT = 'x'", syntaxError(26, ")")},
            {"COMMENT ON DATABASE IDENTIFIER('a' || 'b') IS 'c'", syntaxError(35, "||")},
            {"DROP DATABASE IDENTIFIER('a' || 'b')", syntaxError(29, "||")},
            {"DROP DATABASE IF EXISTS IDENTIFIER()", "Drop statement executed successfully (IDENTIFIER already "
                + "dropped)."},
        });
    }
}
