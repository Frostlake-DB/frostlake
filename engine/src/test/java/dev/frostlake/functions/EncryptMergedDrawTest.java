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
 * ENCRYPT draws through relations the plan merges into the query over them and through an IN or ANY over a FROM-less
 * query, the echo of a VALUES item refused for a drawing function, and the argument types of the passphrase and raw
 * decryption functions, which are judged ahead of a VALUES refusal (live-verified).
 */
public class EncryptMergedDrawTest extends MeasuredCellsTestSupport {

    @Test
    public void drawsAndRefusals() {
        engine.execute("CREATE OR REPLACE TABLE t1(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES "
            + "('msg','key'),('msg','key'),('x','y')");
        engine.execute("CREATE OR REPLACE TABLE tv(a FLOAT)");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT ENCRYPT('msg','key') x");
        engine.execute("CREATE OR REPLACE VIEW v2 AS SELECT m, k, ENCRYPT(m,k) x FROM t1");
        assertCells(new String[][] {
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) || '/' || COUNT(DISTINCT TO_VARCHAR(b)) || '/' || "
                + "COUNT(*) FROM (SELECT ENCRYPT('msg','key') a FROM t1) x, (SELECT ENCRYPT('msg','key') b FROM"
                + " t1) y",
                "9/9/9"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) || '/' || COUNT(*) FROM (SELECT ENCRYPT('msg','key') a "
                + "FROM t1) x, (SELECT ENCRYPT('msg','key') b FROM t1) y",
                "9/9"},
            {"SELECT COUNT_IF(a = b) FROM (SELECT ENCRYPT('msg','key') a FROM t1) x, (SELECT "
                + "ENCRYPT('msg','key') b FROM t1) y",
                "9"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM (SELECT ENCRYPT('msg','key') a FROM t1) x, t1 y",
                "9"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM (SELECT DISTINCT m, ENCRYPT('msg','key') a FROM "
                + "t1) x, t1 y",
                "3"},
            {"WITH c AS (SELECT ENCRYPT('msg','key') a FROM t1) SELECT COUNT(DISTINCT TO_VARCHAR(c1.a)) "
                + "FROM c c1, c c2",
                "3"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM (SELECT ENCRYPT('msg','key') a FROM t1) x JOIN t1 "
                + "y ON x.a IS NOT NULL",
                "9"},
            {"SELECT COUNT_IF(x.a = x.b) FROM (SELECT ENCRYPT('msg','key') a, ENCRYPT('msg','key') b FROM "
                + "t1) x, t1 y",
                "9"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM (SELECT ENCRYPT(m,k) a FROM t1) x, t1 y",
                "3"},
            {"SELECT ENCRYPT('msg','key') IN (SELECT ENCRYPT('msg','key'))",
                "true"},
            {"SELECT COUNT_IF(ENCRYPT(m,k) IN (SELECT ENCRYPT('msg','key'))) FROM t1",
                "0"},
            {"SELECT ENCRYPT('msg','key') NOT IN (SELECT ENCRYPT('msg','key'))",
                "false"},
            {"SELECT EXISTS (SELECT 1 WHERE ENCRYPT('msg','key') = ENCRYPT('msg','key'))",
                "true"},
            {"SELECT ENCRYPT('msg','key') = ANY (SELECT ENCRYPT('msg','key'))",
                "true"},
            {"INSERT INTO tv VALUES (NORMAL(0, 1, 5))",
                "REFUSED SQL compilation error: | Invalid expression [NORMAL(0.0, 1.0, 5)] in VALUES clause"},
            {"INSERT INTO tv VALUES (CONCAT('p', RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [CONCAT('p', CAST(RANDOM() AS "
                + "VARCHAR(134217728)))] in VALUES clause"},
            {"INSERT INTO tv VALUES (RANDOM() + 1)",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(RANDOM() AS NUMBER(19,0))) + 1] "
                + "in VALUES clause"},
            {"INSERT INTO tv VALUES (IFF(TRUE, RANDOM(), 1))",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT * FROM VALUES (NORMAL(0, 1, 5))",
                "REFUSED SQL compilation error: | Invalid expression [NORMAL(0.0, 1.0, 5)] in VALUES clause"},
            {"SELECT * FROM VALUES (CONCAT('p', RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [CONCAT('p', CAST(RANDOM() AS "
                + "VARCHAR(134217728)))] in VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM() + 1)",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(RANDOM() AS NUMBER(19,0))) + 1] "
                + "in VALUES clause"},
            {"SELECT * FROM VALUES (IFF(TRUE, RANDOM(), 1))",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"INSERT INTO tv VALUES (RANDOM())",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"INSERT INTO tv VALUES (UNIFORM(1, 10, RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [UNIFORM(1, 10, RANDOM())] in VALUES "
                + "clause"},
            {"INSERT INTO tv VALUES (RANDOM() * 2)",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(RANDOM() AS NUMBER(19,0))) * 2] "
                + "in VALUES clause"},
            {"INSERT INTO tv VALUES (NORMAL(0.5, 1, RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [NORMAL(0.5, 1.0, RANDOM())] in VALUES "
                + "clause"},
            {"INSERT INTO tv VALUES (RANDOM(5) + 1)",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(RANDOM(5) AS NUMBER(19,0))) + 1] "
                + "in VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM() || 'x')",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(RANDOM() AS VARCHAR(134217728))) "
                + "|| 'x'] in VALUES clause"},
            {"SELECT * FROM VALUES (ZIPF(1, 10, 5))",
                "REFUSED SQL compilation error: | Invalid expression [ZIPF(1.0, 10, 5)] in VALUES clause"},
            {"SELECT * FROM VALUES (CASE WHEN TRUE THEN RANDOM() END)",
                "REFUSED SQL compilation error: | Invalid expression [ENSURE_NULLABLE(RANDOM())] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (1 + RANDOM())",
                "REFUSED SQL compilation error: | Invalid expression [1 + (CAST(RANDOM() AS NUMBER(19,0)))] "
                + "in VALUES clause"},
            {"SELECT * FROM VALUES (ABS(RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [ABS(CAST(RANDOM() AS NUMBER(19,0)))] in"
                + " VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM()::VARCHAR)",
                "REFUSED SQL compilation error: | Invalid expression [CAST(RANDOM() AS VARCHAR(134217728))] "
                + "in VALUES clause"},
            {"SELECT TRY_DECRYPT_RAW('a','b')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'TRY_DECRYPT_RAW': (VARCHAR(1), VARCHAR(1))"},
            {"SELECT TRY_DECRYPT_RAW('a','b','c')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'TRY_DECRYPT_RAW': (VARCHAR(1), VARCHAR(1), VARCHAR(1))"},
            {"SELECT DECRYPT_RAW('a','b')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'DECRYPT_RAW': (VARCHAR(1), VARCHAR(1))"},
            {"SELECT TRY_DECRYPT_RAW(TO_BINARY('aa','HEX'),'b')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'TRY_DECRYPT_RAW': (BINARY(67108864), VARCHAR(1))"},
            {"SELECT TRY_DECRYPT_RAW('a', TO_BINARY('aa','HEX'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'TRY_DECRYPT_RAW': (VARCHAR(1), BINARY(67108864))"},
            {"SELECT TRY_DECRYPT_RAW(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'TRY_DECRYPT_RAW': (NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT DECRYPT_RAW(TO_BINARY('aa','HEX'),'b')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'DECRYPT_RAW': (BINARY(67108864), VARCHAR(1))"},
            {"SELECT * FROM VALUES (DECRYPT('a','key'))",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function 'DECRYPT': (VARCHAR(1), VARCHAR(3))"},
            {"SELECT DECRYPT('a','key')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'DECRYPT': (VARCHAR(1), VARCHAR(3))"},
            {"INSERT INTO tv VALUES (DECRYPT('a','key'))",
                "REFUSED SQL compilation error: error line 1 at position 23 | Invalid argument types for "
                + "function 'DECRYPT': (VARCHAR(1), VARCHAR(3))"},
            {"SELECT * FROM VALUES (TRY_DECRYPT('a','key'))",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function 'TRY_DECRYPT': (VARCHAR(1), VARCHAR(3))"},
            {"SELECT TRY_DECRYPT('a','key')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'TRY_DECRYPT': (VARCHAR(1), VARCHAR(3))"},
        });
    }

    @Test
    public void valuesEcho() {
        assertCells(new String[][] {
            {"SELECT * FROM VALUES (ROUND(RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [ROUND(CAST(RANDOM() AS NUMBER(19,0)))] "
                + "in VALUES clause"},
            {"SELECT * FROM VALUES (FLOOR(RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [FLOOR(RANDOM())] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_VARCHAR(RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(RANDOM() AS VARCHAR(134217728))] "
                + "in VALUES clause"},
            {"SELECT * FROM VALUES (HASH(RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [HASH(RANDOM())] in VALUES clause"},
            {"SELECT * FROM VALUES (GREATEST(RANDOM(), 1))",
                "REFUSED SQL compilation error: | Invalid expression [GREATEST(RANDOM(), 1)] in VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM()::NUMBER)",
                "REFUSED SQL compilation error: | Invalid expression [CAST(RANDOM() AS NUMBER(38,0))] in "
                + "VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM()::FLOAT)",
                "REFUSED SQL compilation error: | Invalid expression [CAST(RANDOM() AS FLOAT)] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (-RANDOM())",
                "REFUSED SQL compilation error: | Invalid expression [NEGATE(CAST(RANDOM() AS NUMBER(19,0)))]"
                + " in VALUES clause"},
            {"SELECT * FROM VALUES (RANDSTR(5, RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [RANDSTR(5, RANDOM())] in VALUES clause"},
            {"SELECT * FROM VALUES (UNIFORM(1, 10, RANDOM()) + 1)",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(UNIFORM(1, 10, RANDOM()) AS "
                + "NUMBER(2,0))) + 1] in VALUES clause"},
            {"SELECT * FROM VALUES (UNIFORM(1, 10, 5) + 1)",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(UNIFORM(1, 10, 5) AS "
                + "NUMBER(2,0))) + 1] in VALUES clause"},
            {"SELECT * FROM VALUES (NORMAL(0, 1, 5) + 1)",
                "REFUSED SQL compilation error: | Invalid expression [(NORMAL(0.0, 1.0, 5)) + 1.0] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (RANDSTR(5, 5) || 'x')",
                "REFUSED SQL compilation error: | Invalid expression [(RANDSTR(5, 5)) || 'x'] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (UUID_STRING() || 'x')",
                "REFUSED SQL compilation error: | Invalid expression [UUID_STRING() || 'x'] in VALUES clause"},
            {"SELECT * FROM VALUES (CONCAT('p', UUID_STRING()))",
                "REFUSED SQL compilation error: | Invalid expression [CONCAT('p', UUID_STRING())] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (CONCAT('p', UNIFORM(1, 10, 5)))",
                "REFUSED SQL compilation error: | Invalid expression [CONCAT('p', CAST(UNIFORM(1, 10, 5) AS "
                + "VARCHAR(134217728)))] in VALUES clause"},
            {"SELECT * FROM VALUES (CASE WHEN FALSE THEN RANDOM() END)",
                "null"},
            {"SELECT * FROM VALUES (CASE WHEN TRUE THEN RANDOM() ELSE 1 END)",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT * FROM VALUES (IFF(1 = 1, RANDOM(), 1))",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM() + RANDOM())",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(RANDOM() AS NUMBER(19,0))) + "
                + "(CAST(RANDOM() AS NUMBER(19,0)))] in VALUES clause"},
            {"SELECT * FROM VALUES (IFF(FALSE, RANDOM(), 1))",
                "1"},
            {"SELECT * FROM VALUES (NORMAL(0, 1, RANDOM()) * 2)",
                "REFUSED SQL compilation error: | Invalid expression [(NORMAL(0.0, 1.0, RANDOM())) * 2.0] in "
                + "VALUES clause"},
            {"SELECT * FROM VALUES (ZIPF(2, 10, RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [ZIPF(2.0, 10, RANDOM())] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (NORMAL(-1, 2.5, 5))",
                "REFUSED SQL compilation error: | Invalid expression [NORMAL(-1.0, 2.5, 5)] in VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM() - 1)",
                "REFUSED SQL compilation error: | Invalid expression [(CAST(RANDOM() AS NUMBER(19,0))) - 1] "
                + "in VALUES clause"},
            {"SELECT * FROM VALUES (CONCAT(RANDOM(), RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [CONCAT(CAST(RANDOM() AS "
                + "VARCHAR(134217728)), CAST(RANDOM() AS VARCHAR(134217728)))] in VALUES clause"},
            {"SELECT * FROM VALUES (CONCAT('p', NORMAL(0, 1, 5)))",
                "REFUSED SQL compilation error: | Invalid expression [CONCAT('p', CAST(NORMAL(0.0, 1.0, 5) AS"
                + " VARCHAR(134217728)))] in VALUES clause"},
            {"SELECT * FROM VALUES (CASE WHEN TRUE THEN UUID_STRING() END)",
                "REFUSED SQL compilation error: | Invalid expression [CAST(ENSURE_NULLABLE(UUID_STRING()) AS "
                + "VARCHAR(134217728))] in VALUES clause"},
            {"SELECT * FROM VALUES (MD5_BINARY('a') || 'x')",
                "REFUSED SQL compilation error: error line 1 at position 38 | Invalid argument types for "
                + "function '||': (BINARY(16), VARCHAR(1))"},
            {"SELECT * FROM VALUES (SHA2('a') || 'x')",
                "REFUSED SQL compilation error: | Invalid expression [(SHA2('a')) || 'x'] in VALUES clause"},
            {"SELECT * FROM VALUES (1, RANDOM())",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT * FROM VALUES (ABS(UNIFORM(1, 10, 5)))",
                "REFUSED SQL compilation error: | Invalid expression [ABS(UNIFORM(1, 10, 5))] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (NVL(RANDOM(), 1))",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT * FROM VALUES (COALESCE(RANDOM(), 1))",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT * FROM VALUES (CASE WHEN FALSE THEN 1 ELSE RANDOM() END)",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT DECRYPT(1, 'key')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'DECRYPT': (NUMBER(1,0), VARCHAR(3))"},
            {"SELECT TRY_DECRYPT(TO_BINARY('aa','HEX'), 1)",
                "null"},
            {"SELECT * FROM VALUES (DECRYPT_RAW('a','key','iv'))",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function 'DECRYPT_RAW': (VARCHAR(1), VARCHAR(3), VARCHAR(2))"},
            {"SELECT * FROM VALUES (ENCRYPT_RAW('a','key','iv'))",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function 'ENCRYPT_RAW': (VARCHAR(1), VARCHAR(3), VARCHAR(2))"},
            {"SELECT * FROM VALUES (UPPER(TRY_DECRYPT('a','key')))",
                "REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for "
                + "function 'TRY_DECRYPT': (VARCHAR(1), VARCHAR(3))"},
            {"SELECT DECRYPT(PARSE_JSON('1'), 'key')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'DECRYPT': (VARIANT, VARCHAR(3))"},
            {"SELECT TRY_DECRYPT(NULL, 'key')",
                "null"},
        });
    }

    @Test
    public void decryptArguments() {
        engine.execute("CREATE OR REPLACE TABLE tv(a FLOAT)");
        assertCells(new String[][] {
            {"SELECT ENCRYPT_RAW('a','key','iv')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'ENCRYPT_RAW': (VARCHAR(1), VARCHAR(3), VARCHAR(2))"},
            {"SELECT * FROM VALUES (ENCRYPT_RAW('a','key','iv'))",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function 'ENCRYPT_RAW': (VARCHAR(1), VARCHAR(3), VARCHAR(2))"},
            {"SELECT * FROM VALUES (1), (DECRYPT('a','key'))",
                "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for "
                + "function 'DECRYPT': (VARCHAR(1), VARCHAR(3))"},
            {"INSERT INTO tv(a) VALUES (1), (DECRYPT('a','key'))",
                "REFUSED SQL compilation error: error line 1 at position 31 | Invalid argument types for "
                + "function 'DECRYPT': (VARCHAR(1), VARCHAR(3))"},
            {"SELECT * FROM VALUES (TRY_DECRYPT_RAW('a','key','iv'))",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function 'TRY_DECRYPT_RAW': (VARCHAR(1), VARCHAR(3), VARCHAR(2))"},
            {"SELECT TRY_DECRYPT(PARSE_JSON('1'), 'key')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'TRY_DECRYPT': (VARIANT, VARCHAR(3))"},
        });
    }

    @Test
    public void mergedRelations() {
        engine.execute("CREATE OR REPLACE TABLE t1(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES "
            + "('msg','key'),('msg','key'),('x','y')");
        engine.execute("CREATE OR REPLACE VIEW v3 AS SELECT ENCRYPT('msg','key') a FROM t1");
        assertCells(new String[][] {
            {"WITH c AS (SELECT ENCRYPT('msg','key') a FROM t1) SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM "
                + "c, t1 y",
                "9"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM v3, t1 y",
                "9"},
            {"SELECT COUNT_IF(v.a = w.a) FROM v3 v, v3 w",
                "9"},
            {"WITH c AS (SELECT ENCRYPT('msg','key') a FROM t1) SELECT COUNT_IF(c1.a = c2.a) FROM c c1, c "
                + "c2",
                "3"},
            {"SELECT COUNT_IF(x.a = y.a) FROM (SELECT ENCRYPT('msg','key') a FROM t1) x, (SELECT "
                + "ENCRYPT('msg','key') a FROM t1) y",
                "9"},
            {"WITH c AS (SELECT ENCRYPT('msg','key') a FROM t1) SELECT COUNT_IF(a = ENCRYPT('msg','key')) "
                + "FROM c",
                "3"},
            {"WITH c AS (SELECT ENCRYPT('msg','key') a FROM t1) SELECT COUNT_IF(c1.a = "
                + "ENCRYPT('msg','key')) FROM c c1, c c2",
                "9"},
            {"SELECT COUNT_IF(a = ENCRYPT('msg','key')) FROM v3",
                "3"},
            {"SELECT COUNT_IF(a = ENCRYPT('msg','key')) FROM (SELECT ENCRYPT('msg','key') a FROM t1 WHERE "
                + "m = 'msg')",
                "2"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM (SELECT * FROM (SELECT ENCRYPT('msg','key') a FROM"
                + " t1)) x, t1 y",
                "9"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(ENCRYPT_RAW(TO_BINARY('aa','HEX'), "
                + "TO_BINARY(REPEAT('ab',32),'HEX'), TO_BINARY(REPEAT('cd',12),'HEX')):ciphertext)) FROM t1",
                "1"},
        });
    }
}
