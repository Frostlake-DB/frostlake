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
 * ALTER FILE FORMAT ... SET: SET is required, a comma may stand between the options it changes, and IF EXISTS forgives
 * only the format's absence - an option its type does not take, or a change of type, is still refused, and the
 * options' names and values are judged before the format is looked for. Every cell is live-verified.
 */
public class AlterFileFormatOptionsTest extends MeasuredCellsTestSupport {

    @Test
    public void setTakesCommas() {
        engine.execute("CREATE OR REPLACE FILE FORMAT ff1 TYPE = CSV");
        assertCells(new String[][] {
            {"ALTER FILE FORMAT ff1 COMMENT = 'd', SKIP_HEADER = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET , COMMENT = 'e'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'f',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'g',, SKIP_HEADER = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET TYPE = JSON, COMMENT = 'h'",
                "REFUSED SQL compilation error: | File format type cannot be changed."},
            {"ALTER FILE FORMAT IF EXISTS ff1 SET COMMENT = 'j', STRIP_OUTER_ARRAY = TRUE",
                "REFUSED SQL compilation error: | Option STRIP_OUTER_ARRAY is not valid for file format type "
                + "CSV."},
            {"ALTER FILE FORMAT ff1 COMMENT = 'm'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SKIP_HEADER = 1 COMMENT = 'n'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
        });
    }

    @Test
    public void ifExistsForgivesOnlyAbsence() {
        engine.execute("CREATE OR REPLACE FILE FORMAT ff1 TYPE = CSV");
        assertCells(new String[][] {
            {"ALTER FILE FORMAT ff1 SET STRIP_OUTER_ARRAY = TRUE",
                "REFUSED SQL compilation error: | Option STRIP_OUTER_ARRAY is not valid for file format type "
                + "CSV."},
            {"ALTER FILE FORMAT IF EXISTS ff1 SET STRIP_OUTER_ARRAY = TRUE",
                "REFUSED SQL compilation error: | Option STRIP_OUTER_ARRAY is not valid for file format type "
                + "CSV."},
            {"CREATE OR REPLACE FILE FORMAT ff2 TYPE = CSV STRIP_OUTER_ARRAY = TRUE",
                "REFUSED SQL compilation error: | Option STRIP_OUTER_ARRAY is not valid for file format type "
                + "CSV."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'j', STRIP_OUTER_ARRAY = TRUE",
                "REFUSED SQL compilation error: | Option STRIP_OUTER_ARRAY is not valid for file format type "
                + "CSV."},
            {"ALTER FILE FORMAT ff1 SET TYPE = JSON",
                "REFUSED SQL compilation error: | File format type cannot be changed."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'z' COMMA",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT IF EXISTS ff1 SET TYPE = JSON",
                "REFUSED SQL compilation error: | File format type cannot be changed."},
            {"ALTER FILE FORMAT IF EXISTS nope SET SKIP_HEADER = -1",
                "REFUSED SQL compilation error: | invalid value [-1] for parameter 'SKIP_HEADER'"},
        });
    }

    @Test
    public void optionsJudgedBeforeTheFormat() {
        assertCells(new String[][] {
            {"ALTER FILE FORMAT nope SET SKIP_HEADER = -1",
                "REFUSED SQL compilation error: | invalid value [-1] for parameter 'SKIP_HEADER'"},
            {"ALTER FILE FORMAT IF EXISTS nope SET COMPRESSION = 'BOGUS'",
                "REFUSED SQL compilation error: | invalid value ['BOGUS'] for parameter 'COMPRESSION'"},
            {"ALTER FILE FORMAT IF EXISTS nope SET FIELD_DELIMITER = 'abcdefghijklmnopqrstuvwxyz'",
                "REFUSED SQL compilation error: | invalid value ['abcdefghijklmnopqrstuvwxyz'] for parameter "
                + "'FIELD_DELIMITER'"},
            {"ALTER FILE FORMAT IF EXISTS nope SET TYPE = BOGUS",
                "REFUSED SQL compilation error: | invalid value [BOGUS] for parameter 'TYPE'"},
            {"ALTER FILE FORMAT IF EXISTS nope SET BOGUS_OPT = 1",
                "REFUSED SQL compilation error: | invalid property 'BOGUS_OPT' for 'FILE_FORMAT'"},
            {"ALTER FILE FORMAT nope SET BOGUS_OPT = 1",
                "REFUSED SQL compilation error: | invalid property 'BOGUS_OPT' for 'FILE_FORMAT'"},
            {"ALTER FILE FORMAT IF EXISTS nope SET ESCAPE = 'ab'",
                "REFUSED SQL compilation error: | invalid value ['ab'] for parameter 'ESCAPE'"},
        });
    }
}
