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
 * A built-in function's SHOW FUNCTIONS row as the account lists it (live-verified): created at the account's epoch,
 * with the account's is_ansi, valid_for_clustering and counts of arguments, the arguments text naming each
 * signature with its return type, and the account's description. A name may have one row for all its signatures
 * or one row each; the table functions, CURRENT_ACCOUNT and GROUPING run but are not listed at all.
 */
public class ShowBuiltinFunctionRowsTest extends MeasuredCellsTestSupport {

    private static final String CELLS = " ->> SELECT \"is_aggregate\" || '|' || \"is_ansi\" || '|' || "
        + "\"min_num_arguments\" || '|' || \"max_num_arguments\" || '|' || \"valid_for_clustering\" || '|' || "
        + "\"is_table_function\" || '|' || \"arguments\" || '|' || \"description\" FROM $1";

    @Test
    public void aBuiltInRowCarriesTheAccountCells() {
        assertCells(new String[][] {
            {"SHOW FUNCTIONS LIKE 'ABS'" + CELLS,
                "N|Y|1|1|Y|N|ABS(NUMBER) RETURN NUMBER, ABS(FLOAT) RETURN FLOAT, ABS(DECFLOAT) RETURN DECFLOAT, "
                + "ABS(INTERVAL YEAR TO MONTH) RETURN INTERVAL YEAR TO MONTH, ABS(INTERVAL DAY TO SECOND) RETURN "
                + "INTERVAL DAY TO SECOND|returns absolute value of numeric"},
            {"SHOW FUNCTIONS LIKE 'COUNT'" + CELLS,
                "Y|Y|1|-1|N|N|COUNT(ANY) RETURN NUMBER|Return number of non-null values of a given expression"},
            {"SHOW BUILTIN FUNCTIONS LIKE 'SUBSTR'" + CELLS,
                "N|N|2|3|Y|N|SUBSTR(VARCHAR, NUMBER, DEFAULT NUMBER) RETURN VARCHAR, SUBSTR(BINARY, NUMBER, DEFAULT "
                + "NUMBER) RETURN BINARY|Returns string fragment of the input string"},
            {"SHOW FUNCTIONS LIKE 'LAG'" + CELLS,
                "N|N|1|3|Y|N|LAG(ANY, DEFAULT NUMBER, DEFAULT ANY) RETURN ANY|Returns the value of an expression at "
                + "the specified offset row"},
            {"SHOW FUNCTIONS LIKE 'IFF'" + CELLS,
                "N|N|3|3|Y|N|IFF(BOOLEAN, ANY, ANY) RETURN ANY|Returns arg2 if arg1 is true, arg3 otherwise."},
            {"SHOW FUNCTIONS LIKE 'HASH'" + CELLS, "N|N|1|-1|N|N|HASH(ANY) RETURN NUMBER|hash value"},
            {"SHOW FUNCTIONS LIKE 'SYSTEM$WAIT'" + CELLS,
                "N|N|1|2|N|N|SYSTEM$WAIT(NUMBER, DEFAULT VARCHAR) RETURN VARCHAR|results"},
            {"SHOW FUNCTIONS LIKE 'TO_CHAR'" + CELLS, "N|N|1|2|N|N||"},
            {"SHOW FUNCTIONS LIKE 'ABS' ->> SELECT DATE_PART(EPOCH_SECOND, \"created_on\") FROM $1", "1343804400"},
        });
    }

    @Test
    public void aNameMayHaveOneRowPerSignature() {
        assertCells(new String[][] {
            {"SHOW FUNCTIONS LIKE 'DATE' ->> SELECT COUNT(*) || '|' || LISTAGG(\"arguments\", ';') WITHIN GROUP "
                + "(ORDER BY \"arguments\") FROM $1",
                "5|DATE(TIMESTAMP_LTZ) RETURN DATE;DATE(TIMESTAMP_NTZ) RETURN DATE;DATE(TIMESTAMP_TZ) RETURN DATE;"
                + "DATE(VARCHAR) RETURN DATE;DATE(VARCHAR, VARCHAR) RETURN DATE"},
        });
    }

    @Test
    public void namesTheAccountDoesNotListAreLeftOut() {
        assertCells(new String[][] {
            {"SHOW FUNCTIONS LIKE 'FLATTEN' ->> SELECT COUNT(*) FROM $1", "0"},
            {"SHOW FUNCTIONS LIKE 'RESULT_SCAN' ->> SELECT COUNT(*) FROM $1", "0"},
            {"SHOW FUNCTIONS LIKE 'SPLIT_TO_TABLE' ->> SELECT COUNT(*) FROM $1", "0"},
            {"SHOW BUILTIN FUNCTIONS LIKE 'CURRENT_ACCOUNT' ->> SELECT COUNT(*) FROM $1", "0"},
            {"SHOW FUNCTIONS LIKE 'GROUPING' ->> SELECT COUNT(*) FROM $1", "0"},
            {"SHOW FUNCTIONS LIKE 'QUERY_HISTORY' ->> SELECT COUNT(*) FROM $1", "0"},
        });
    }
}
