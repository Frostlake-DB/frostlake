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

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A list of properties holding NULL, given to a table's property at CREATE TABLE and at ALTER TABLE … SET
 * (live-verified): the list is read, and the property it is given judges it - a format option refuses the NULL by
 * its name, a switch or a parameter refuses the list, a name that is no property is refused as one, and a COMMENT
 * takes the list as the parser's word for it.
 */
public class TablePropertyListNullTest extends MeasuredCellsTestSupport {

    private static final String CREATE = "CREATE OR REPLACE TABLE null_list_t (a INT) ";

    private static final String SET = "ALTER TABLE null_list_t SET ";

    private static String nullOf(final String option) {
        return "REFUSED SQL compilation error: | invalid value [NULL] for parameter '" + option + "'";
    }

    private static String listFor(final String property) {
        return "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter '" + property + "'";
    }

    private void assertTaken(final String sql) {
        final String answer = answer(sql);
        assertFalse(answer.startsWith("REFUSED"), sql + " => " + answer);
    }

    @Test
    public void aFormatOptionRefusesTheNullByItsName() {
        assertCells(new String[][] {
            {CREATE + "STAGE_FILE_FORMAT = (TYPE = NULL)", nullOf("TYPE")},
            {CREATE + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL)", nullOf("FIELD_DELIMITER")},
            {CREATE + "STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = NULL)", nullOf("FIELD_DELIMITER")},
            {CREATE + "STAGE_FILE_FORMAT = (TYPE = CSV, SKIP_HEADER = NULL)", nullOf("SKIP_HEADER")},
            {CREATE + "STAGE_FILE_FORMAT = (FORMAT_NAME = NULL)", nullOf("FORMAT_NAME")},
            {CREATE + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL, TYPE = CSV)", nullOf("FIELD_DELIMITER")},
            // The NULL is echoed as written.
            {CREATE + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = null)",
                "REFUSED SQL compilation error: | invalid value [null] for parameter 'FIELD_DELIMITER'"},
            // The options are judged in the order written, whatever type the list names.
            {CREATE + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL TYPE = NOPE)", nullOf("FIELD_DELIMITER")},
            {CREATE + "STAGE_FILE_FORMAT = (TYPE = NOPE FIELD_DELIMITER = NULL)",
                "REFUSED SQL compilation error: | invalid value [NOPE] for parameter 'TYPE'"},
            {CREATE + "STAGE_FILE_FORMAT = (TYPE = JSON FIELD_DELIMITER = NULL)", nullOf("FIELD_DELIMITER")},
            // A name that is no option is refused before its NULL.
            {CREATE + "STAGE_FILE_FORMAT = (NOPE = NULL)", "REFUSED SQL compilation error: | invalid parameter 'NOPE'"},
            {"CREATE OR REPLACE TRANSIENT TABLE null_list_t (a INT) STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL)",
                nullOf("FIELD_DELIMITER")},
            {"CREATE OR REPLACE TABLE null_list_t (a INT) STAGE_FILE_FORMAT = (TYPE = NULL) AS SELECT 1 a",
                nullOf("TYPE")},
        });
        engine.execute("CREATE OR REPLACE TABLE null_list_t (a INT)");
        assertCells(new String[][] {
            {SET + "STAGE_FILE_FORMAT = (TYPE = NULL)", nullOf("TYPE")},
            {SET + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL)", nullOf("FIELD_DELIMITER")},
            {SET + "STAGE_FILE_FORMAT = (TYPE = CSV NULL_IF = NULL)", nullOf("NULL_IF")},
            {SET + "STAGE_FILE_FORMAT = (TYPE = CSV COMPRESSION = NULL)", nullOf("COMPRESSION")},
            {SET + "STAGE_FILE_FORMAT = (TYPE = CSV TRIM_SPACE = NULL)", nullOf("TRIM_SPACE")},
            {SET + "STAGE_FILE_FORMAT = (TYPE = CSV ESCAPE = NULL)", nullOf("ESCAPE")},
            {SET + "STAGE_FILE_FORMAT = (TYPE = CSV DATE_FORMAT = NULL)", nullOf("DATE_FORMAT")},
            {SET + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL), COMMENT = 'x'", nullOf("FIELD_DELIMITER")},
            {SET + "STAGE_COPY_OPTIONS = (ON_ERROR = NULL)", nullOf("ON_ERROR")},
        });
    }

    @Test
    public void aSwitchOrAParameterRefusesTheList() {
        assertCells(new String[][] {
            {CREATE + "CHANGE_TRACKING = (A = NULL)", listFor("CHANGE_TRACKING")},
            {CREATE + "CHANGE_TRACKING = (A = NULL B = NULL)", listFor("CHANGE_TRACKING")},
            {CREATE + "CHANGE_TRACKING = (A = NULL, B = 1)", listFor("CHANGE_TRACKING")},
            {CREATE + "DATA_RETENTION_TIME_IN_DAYS = (A = NULL)", listFor("DATA_RETENTION_TIME_IN_DAYS")},
            {CREATE + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL) CHANGE_TRACKING = (A = NULL)",
                listFor("CHANGE_TRACKING")},
            {CREATE + "CHANGE_TRACKING = (A = NULL) STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL)",
                listFor("CHANGE_TRACKING")},
            {"CREATE OR REPLACE EVENT TABLE null_list_et CHANGE_TRACKING = (A = NULL)", listFor("CHANGE_TRACKING")},
        });
        engine.execute("CREATE OR REPLACE TABLE null_list_t (a INT)");
        assertCells(new String[][] {
            {SET + "CHANGE_TRACKING = (A = NULL)", listFor("CHANGE_TRACKING")},
            {SET + "DATA_RETENTION_TIME_IN_DAYS = (A = NULL)", listFor("DATA_RETENTION_TIME_IN_DAYS")},
            {SET + "COMMENT = (A = NULL) CHANGE_TRACKING = (A = NULL)", listFor("CHANGE_TRACKING")},
        });
    }

    @Test
    public void aNameThatIsNoPropertyIsRefusedAsOne() {
        final String unknown = "REFUSED SQL compilation error: | invalid property 'NOPE808' for 'TABLE'";
        engine.execute("CREATE OR REPLACE TABLE null_list_t (a INT)");
        assertCells(new String[][] {
            {CREATE + "NOPE808 = (A = NULL)", unknown},
            {CREATE + "CHANGE_TRACKING = (A = NULL) NOPE808 = (A = NULL)", unknown},
            {"CREATE OR REPLACE TABLE null_list_t NOPE808 = (A = NULL) AS SELECT 1 a", unknown},
            {SET + "NOPE808 = (A = NULL)", unknown},
        });
    }

    @Test
    public void aCommentTakesTheListAsTheParsersWordForIt() {
        assertTaken(CREATE + "COMMENT = (A = NULL)");
        assertAnswer("TOK_PROPERTY_LIST", "SHOW TABLES LIKE 'NULL_LIST_T' ->> SELECT \"comment\" FROM $1");
        assertTaken(CREATE + "COMMENT = (A = 1)");
        assertAnswer("TOK_PROPERTY_LIST", "SHOW TABLES LIKE 'NULL_LIST_T' ->> SELECT \"comment\" FROM $1");
        engine.execute("CREATE OR REPLACE TABLE null_list_t (a INT)");
        assertTaken(SET + "COMMENT = (A = NULL)");
        assertAnswer("TOK_PROPERTY_LIST", "SHOW TABLES LIKE 'NULL_LIST_T' ->> SELECT \"comment\" FROM $1");
    }

    @Test
    public void theListIsStillReadAsAList() {
        assertCells(new String[][] {
            {CREATE + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL,)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected ')'."},
            {CREATE + "STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected 'NULL'."},
        });
    }
}
