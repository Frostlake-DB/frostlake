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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Equal ENCRYPT calls share one draw in a row where the plan folds them together (live-verified): two SET values of
 * an UPDATE's row, a scalar subquery that reads no relation beside the call it stands in, and arguments that read
 * the same column under an alias and without it. The cells are the agreeing answers of a two-sided measurement.
 */
public class EncryptFoldedDrawTest extends MeasuredCellsTestSupport {

    @Test
    public void equalCallsFoldedIntoOneRowShareADraw() {
        engine.execute("CREATE OR REPLACE TABLE enc_u1(a BINARY, b BINARY, c INT) AS SELECT * FROM VALUES (NULL, "
            + "NULL, 1), (NULL, NULL, 2)");
        engine.execute("UPDATE enc_u1 SET a = ENCRYPT('msg','key'), b = ENCRYPT('msg','key')");
        engine.execute("CREATE OR REPLACE TABLE enc_u2(a BINARY, b BINARY, c INT) AS SELECT * FROM VALUES (NULL, "
            + "NULL, 1), (NULL, NULL, 2)");
        engine.execute("UPDATE enc_u2 SET a = ENCRYPT('msg','key'), b = ENCRYPT('msg','k2')");
        engine.execute("CREATE OR REPLACE TABLE enc_u3(a BINARY, b BINARY, c INT) AS SELECT * FROM VALUES (NULL, "
            + "NULL, 1), (NULL, NULL, 2)");
        engine.execute("UPDATE enc_u3 x SET a = ENCRYPT(TO_VARCHAR(x.c), 'key'), b = ENCRYPT(TO_VARCHAR(c), 'key')");
        engine.execute("CREATE OR REPLACE TABLE enc_u4(a BINARY, b BINARY, c INT) AS SELECT * FROM VALUES (NULL, "
            + "NULL, 1), (NULL, NULL, 2)");
        engine.execute("UPDATE enc_u4 SET a = ENCRYPT('msg','key'), b = (SELECT ENCRYPT('msg','key'))");
        engine.execute("CREATE OR REPLACE TABLE enc_u5(a BINARY, b BINARY, c INT) AS SELECT * FROM VALUES (NULL, "
            + "NULL, 1), (NULL, NULL, 2)");
        engine.execute("CREATE OR REPLACE TABLE enc_t5(m VARCHAR) AS SELECT 'x'");
        engine.execute("UPDATE enc_u5 SET a = ENCRYPT('msg','key'), b = ENCRYPT('msg','key') FROM enc_t5 WHERE "
            + "enc_t5.m = 'x'");
        engine.execute("CREATE OR REPLACE TABLE enc_u6(a BINARY, b BINARY, c INT) AS SELECT * FROM VALUES (NULL, "
            + "NULL, 1), (NULL, NULL, 2)");
        engine.execute("UPDATE enc_u6 SET b = ENCRYPT('msg','key'), a = ENCRYPT('msg','key') WHERE c = 1");
        engine.execute("CREATE OR REPLACE TABLE enc_u7(a BINARY, b BINARY, c INT) AS SELECT * FROM VALUES (NULL, "
            + "NULL, 1), (NULL, NULL, 2)");
        engine.execute("UPDATE enc_u7 SET a = ENCRYPT(TO_VARCHAR(c), 'key'), b = ENCRYPT(TO_VARCHAR(c) || '', 'key')");
        engine.execute("CREATE OR REPLACE TABLE enc_t(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES "
            + "('msg','key'),('msg','key'),('x','y')");
        engine.execute("CREATE OR REPLACE TABLE enc_one(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES ('msg','key')");
        assertCells(new String[][] {
            {"SELECT COUNT_IF(a = b) || '/' || COUNT(DISTINCT a) FROM enc_u1",
                "2/2"},
            {"SELECT COUNT_IF(a = b) FROM enc_u2",
                "0"},
            {"SELECT COUNT_IF(a = b) || '/' || COUNT(DISTINCT a) FROM enc_u3",
                "2/2"},
            {"SELECT COUNT_IF(a = b) FROM enc_u4",
                "2"},
            {"SELECT COUNT_IF(a = b) FROM enc_u5",
                "2"},
            {"SELECT COUNT_IF(a = b) || '/' || COUNT(a) FROM enc_u6",
                "1/1"},
            {"SELECT COUNT_IF(ENCRYPT(UPPER(x.m), 'k') = ENCRYPT(UPPER(m), 'k')) FROM enc_t5 x",
                "1"},
            {"SELECT COUNT_IF(ENCRYPT(TO_VARCHAR(c), 'k') = ENCRYPT(TO_VARCHAR(enc_u1.c), 'k')) FROM "
                + "enc_u1",
                "2"},
            {"SELECT (SELECT ENCRYPT('msg','key') AS e) = ENCRYPT('msg','key')",
                "true"},
            {"SELECT (SELECT DISTINCT ENCRYPT('msg','key')) = ENCRYPT('msg','key')",
                "true"},
            {"SELECT (SELECT TO_VARCHAR(ENCRYPT('msg','key'))) = TO_VARCHAR(ENCRYPT('msg','key'))",
                "true"},
            {"SELECT COUNT_IF((SELECT ENCRYPT(c::VARCHAR, 'k')) = ENCRYPT(c::VARCHAR, 'k')) FROM enc_u1",
                "2"},
            {"SELECT (SELECT ENCRYPT('msg','key') FROM enc_t5) = ENCRYPT('msg','key')",
                "false"},
            {"SELECT EXISTS (SELECT ENCRYPT('msg','key'))",
                "true"},
            {"SELECT (SELECT RANDOM()) = (SELECT RANDOM())",
                "false"},
            {"SELECT ENCRYPT(m, k) = x FROM (SELECT m, k, ENCRYPT(m, k) x FROM enc_one)",
                "false"},
            {"SELECT (SELECT ENCRYPT('msg','key')) = ENCRYPT('msg','key')",
                "true"},
            {"SELECT (SELECT ENCRYPT('msg','key')) = (SELECT ENCRYPT('msg','key'))",
                "true"},
            {"SELECT COUNT_IF((SELECT ENCRYPT('msg','key')) = ENCRYPT('msg','key')) FROM enc_t",
                "3"},
            {"SELECT COUNT_IF((SELECT ENCRYPT(m, k)) = ENCRYPT(m, k)) FROM enc_t",
                "3"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a.x)) || '/' || COUNT(DISTINCT TO_VARCHAR(b.y)) FROM "
                + "(SELECT ENCRYPT(m,k) x FROM enc_t) a, (SELECT ENCRYPT('msg','k2') y FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 3))) b",
                "3/3"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a.x)) FROM (SELECT RANDOM() x FROM TABLE(GENERATOR(ROWCOUNT"
                + " => 3))) a, (SELECT 1 y FROM TABLE(GENERATOR(ROWCOUNT => 3))) b",
                "3"},
            {"SELECT COUNT(*) FROM VALUES (RANDOM())",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (UUID_STRING())",
                "REFUSED SQL compilation error: | Invalid expression [UUID_STRING()] in VALUES clause"},
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT_RAW(TO_BINARY(redacted_argument, redacted_argument))], expected 2, got 1"},
            {"SELECT ENCRYPT_RAW('a', 'b', 'c', 'd', 'e', 'f')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'ENCRYPT_RAW': (VARCHAR(1), VARCHAR(1), VARCHAR(1), VARCHAR(1), VARCHAR(1), "
                + "VARCHAR(1))"},
            {"SELECT ENCRYPT('a')",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT DECRYPT_RAW('a')",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [DECRYPT_RAW(redacted_argument)], expected 2, got 1"},
            {"SELECT DECRYPT('a')",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [DECRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT ENCRYPT_RAW()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT_RAW()], expected 2, got 0"},
            {"SELECT ENCRYPT()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT()], expected 2, got 0"},
        });
    }
}
