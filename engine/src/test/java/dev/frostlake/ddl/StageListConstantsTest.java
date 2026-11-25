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
 * A list of constants or NULL given inside a stage's property lists (live-verified): a CREDENTIALS key given a
 * parenthesized value is refused by the parser's word for a list, a bare NULL as written, and an ENCRYPTION given a
 * list of constants, at CREATE and at ALTER STAGE alike, is no property list, judged before the stage is looked up and
 * before the CREDENTIALS are judged against the location.
 */
public class StageListConstantsTest extends MeasuredCellsTestSupport {

    @Test
    public void credentialValues() {
        assertCells(new String[][] {
            {"CREATE STAGE st1 CREDENTIALS = (AWS_KEY_ID = (NULL))",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'AWS_KEY_ID'"},
            {"CREATE STAGE st1 CREDENTIALS = (AWS_KEY_ID = ('a'))",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'AWS_KEY_ID'"},
            {"CREATE STAGE st1 CREDENTIALS = (AWS_KEY_ID = null)",
                "REFUSED SQL compilation error: | invalid value [null] for parameter 'AWS_KEY_ID'"},
            {"CREATE STAGE st1 URL = 's3://b/p/' CREDENTIALS = (AWS_KEY_ID = (NULL))",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'AWS_KEY_ID'"},
            {"CREATE STAGE st1 CREDENTIALS = (AWS_KEY_ID = ())",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'AWS_KEY_ID'"},
        });
    }

    @Test
    public void encryptionLists() {
        engine.execute("CREATE STAGE st1 URL = 's3://bucket/p/' CREDENTIALS = (AWS_KEY_ID = 'k' AWS_SECRET_KEY = "
            + "'s')");
        assertCells(new String[][] {
            {"CREATE STAGE st2 URL = 's3://bucket/p/' ENCRYPTION = (1) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"CREATE STAGE st2 URL = 's3://bucket/p/' ENCRYPTION = (NULL) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE st1 SET ENCRYPTION = (1) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE st1 SET ENCRYPTION = (NULL) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE st1 SET ENCRYPTION = (TYPE = NULL) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'"},
            {"CREATE STAGE st2 URL = 's3://bucket/p/' ENCRYPTION = (TYPE = NULL) CREDENTIALS = (AWS_KEY_ID"
                + " = 'k')",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'"},
            {"ALTER STAGE st1 SET COMMENT = (1) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"CREATE STAGE st2 URL = 's3://bucket/p/' FILE_FORMAT = (1) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE st1 SET DIRECTORY = (ENABLE = NULL) CREDENTIALS = (AWS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ENABLE'"},
            {"ALTER STAGE st1 SET ENCRYPTION = (1)",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE nosuch SET ENCRYPTION = (1)",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
        });
    }
}
