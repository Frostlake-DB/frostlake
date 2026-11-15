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
 * A stage takes tags as every other kind does. ALTER STAGE … SET TAG and UNSET TAG are judged whole before any tag
 * changes, as they are on every kind: a missing tag or a value its tag does not allow leaves every tag as it was. A
 * tag action that does not read to the statement's end is read as properties are, TAG their first. CREATE STAGE sets
 * the tags of its WITH TAG clause, judged once its name is; a clone carries its source's tags, and a renamed stage
 * keeps them (all live-verified).
 */
public class StageTagsTest extends MeasuredCellsTestSupport {

    private static final String DONE = "Statement executed successfully.";

    private static final String NO_SUCH_TAG = "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not "
        + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.";

    private static final String NOT_ALLOWED = "REFUSED Value 'b' is not allowed by the specified allowed_values for "
        + "tag 'TG2'.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TAG tg1");
        engine.execute("CREATE TAG tg2 ALLOWED_VALUES 'a'");
        engine.execute("CREATE STAGE st1");
    }

    /** A syntax error's answer at a position of the statement's first line. */
    private static String syntaxError(final int position, final String token) {
        return "REFUSED SQL compilation error: | syntax error line 1 at position " + position + " unexpected '"
            + token + "'.";
    }

    /** A missing stage's answer. */
    private static String noSuchStage(final String name) {
        return "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA." + name + "' does not exist or not "
            + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
            + "TEST_DB.TEST_SCHEMA." + name + ".";
    }

    @Test
    public void aStagesTagsSetAndUnset() {
        assertCells(new String[][] {
            {"ALTER STAGE st1 SET TAG tg1 = 'x', tg1 = 'y'", DONE},
            {"SELECT SYSTEM$GET_TAG('tg1', 'test_db.test_schema.st1', 'stage')", "y"},
            {"ALTER STAGE st1 SET TAG tg2 = 'b'", NOT_ALLOWED},
            {"ALTER STAGE st1 SET TAG tg1 = 'x', nosuch_tag = 'y'", NO_SUCH_TAG},
            {"ALTER STAGE st1 SET TAG tg1 = 'x', tg2 = 'b'", NOT_ALLOWED},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st1', 'stage')", "y"},
            {"ALTER STAGE st1 UNSET TAG nosuch_tag", NO_SUCH_TAG},
            {"ALTER STAGE st1 UNSET TAG tg1, tg1", DONE},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st1', 'stage')", "null"},
            {"ALTER STAGE IF EXISTS st1 UNSET TAG tg1", DONE},
            {"ALTER STAGE IF EXISTS nosuch_st UNSET TAG tg1", DONE},
            {"ALTER STAGE nosuch_st UNSET TAG tg1", noSuchStage("NOSUCH_ST")},
            {"ALTER STAGE nosuch_st SET TAG nosuch_tag = 'x'", noSuchStage("NOSUCH_ST")},
            {"ALTER STAGE st1 SET TAG tg1 = 'w2', tg2 = 'a'", DONE},
            {"SELECT tag_name, tag_value, object_name, domain FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES("
                + "'test_db.test_schema.st1', 'stage')) ORDER BY 1", "TG1|w2|ST1|STAGE ;; TG2|a|ST1|STAGE"},
            {"ALTER STAGE st1 UNSET TAG tg1, tg2", DONE},
            {"SELECT COUNT(*) FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('st1', 'stage'))", "0"},
            {"ALTER STAGE st1 SET TAG tg1 = 1", "REFUSED SQL compilation error: | invalid value [1] for parameter "
                + "'tagValue'"},
            {"ALTER STAGE st1 SET TAG tg1 = $nosuch_var", "REFUSED SQL compilation error: error line 1 at position 30 "
                + "| Session variable '$NOSUCH_VAR' does not exist"},
            {"ALTER STAGE st1 SET TAG test_db.test_schema.tg1 = 'p'", DONE},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st1', 'stage')", "p"},
        });
    }

    @Test
    public void aTagActionThatDoesNotReadWhole() {
        assertCells(new String[][] {
            {"ALTER STAGE st1 SET TAG tg1 = 'x' COMMENT = 'c'", syntaxError(24, "tg1")},
            {"ALTER STAGE st1 SET TAG tg1 = 'x' URL = 's3://b/x'", syntaxError(24, "tg1")},
            {"ALTER STAGE st1 SET TAG tg1 'x'", syntaxError(24, "tg1") + " | syntax error line 1 at position 28 "
                + "unexpected ''x''."},
            {"ALTER STAGE st1 SET TAG tg1 =", syntaxError(24, "tg1")},
            {"ALTER STAGE st1 SET TAG tg1 = 'x' 'y'", syntaxError(24, "tg1") + " | syntax error line 1 at position 34 "
                + "unexpected ''y''."},
            {"ALTER STAGE st1 UNSET TAG tg1 COMMENT", syntaxError(26, "tg1")},
            {"ALTER STAGE st1 UNSET TAG tg1,", syntaxError(26, "tg1")},
            {"ALTER STAGE st1 UNSET TAG", "REFUSED Unsupported feature 'UNSET'."},
            {"ALTER STAGE st1 SET TAG", syntaxError(23, "<EOF>")},
            {"ALTER STAGE st1 SET TAG tg1 = 'x'; SELECT 1 +", syntaxError(45, "<EOF>")},
        });
    }

    @Test
    public void aCreatedClonedAndRenamedStagesTags() {
        assertCells(new String[][] {
            {"CREATE STAGE st3 WITH TAG (tg1 = 'z')", "Stage area ST3 successfully created."},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st3', 'stage')", "z"},
            {"CREATE STAGE st4 WITH TAG (nosuch_tag = 'z')", NO_SUCH_TAG},
            {"CREATE STAGE st3 WITH TAG (nosuch_tag = 'z')", "REFUSED SQL compilation error: | Object 'ST3' already "
                + "exists."},
            {"CREATE STAGE IF NOT EXISTS st3 WITH TAG (nosuch_tag = 'z')", NO_SUCH_TAG},
            {"CREATE STAGE st3 WITH TAG (tg1 = 1)", "REFUSED SQL compilation error: | invalid value [1] for parameter "
                + "'tagValue'"},
            {"CREATE STAGE IF NOT EXISTS st3 WITH TAG (tg2 = 'b')", NOT_ALLOWED},
            {"CREATE STAGE st8 WITH TAG (tg1 = 'a', tg1 = 'b')", "REFUSED Same tag TG1 with multiple values provided, "
                + "values = a, b"},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st8', 'stage')", noSuchStage("ST8")},
            {"CREATE OR REPLACE STAGE st3 WITH TAG (tg1 = 'r')", "Stage area ST3 successfully created."},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st3', 'stage')", "r"},
            {"CREATE OR REPLACE STAGE st3", "Stage area ST3 successfully created."},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st3', 'stage')", "null"},
            {"CREATE STAGE ste URL = 's3://frostlake-nonexistent-bucket/x' WITH TAG (tg1 = 'e')",
                "Stage area STE successfully created."},
            {"CREATE STAGE ste2 CLONE ste", "Stage area STE2 successfully created."},
            {"SELECT SYSTEM$GET_TAG('tg1', 'ste2', 'stage')", "e"},
            {"ALTER STAGE ste RENAME TO ste3", DONE},
            {"SELECT SYSTEM$GET_TAG('tg1', 'ste3', 'stage')", "e"},
            {"CREATE TEMPORARY STAGE st7 WITH TAG (tg1 = 't')", "Stage area ST7 successfully created."},
            {"SELECT SYSTEM$GET_TAG('tg1', 'st7', 'stage')", "t"},
        });
    }

    @Test
    public void everyKindJudgesItsTagActionWhole() {
        engine.execute("CREATE TASK tk1 AS SELECT 1");
        engine.execute("CREATE TABLE t1 (a INT)");
        assertCells(new String[][] {
            {"ALTER TASK tk1 SET TAG tg1 = 'x', nosuch_tag = 'y'", NO_SUCH_TAG},
            {"SELECT SYSTEM$GET_TAG('tg1', 'tk1', 'task')", "null"},
            {"ALTER TABLE t1 SET TAG tg1 = 'x', nosuch_tag = 'y'", NO_SUCH_TAG},
            {"SELECT SYSTEM$GET_TAG('tg1', 't1', 'table')", "null"},
            {"ALTER TASK tk1 SET TAG tg2 = 'b', nosuch_tag = 'y'", NO_SUCH_TAG},
            {"ALTER TASK tk1 SET TAG tg1 = 'x', tg2 = 'b'", NOT_ALLOWED},
            {"SELECT SYSTEM$GET_TAG('tg1', 'tk1', 'task')", "null"},
            {"ALTER TASK tk1 SET TAG tg1 = 'x'", DONE},
            {"ALTER TASK tk1 UNSET TAG tg1, nosuch_tag", NO_SUCH_TAG},
            {"SELECT SYSTEM$GET_TAG('tg1', 'tk1', 'task')", "x"},
        });
    }
}
