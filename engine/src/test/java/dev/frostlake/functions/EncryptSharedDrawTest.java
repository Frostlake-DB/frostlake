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
 * ENCRYPT and ENCRYPT_RAW calls written alike read one drawn value in a row (live-verified): the plan computes the
 * call once for the row, so {@code ENCRYPT('msg', 'key') = ENCRYPT('msg', 'key')} is true while every row, and every
 * statement, draws its own ciphertext. Calls are alike by what they read - the same columns of the row, the same
 * constants of the same types - and a call that differs in an argument, its count, a cast or its relation draws its
 * own. ENCRYPT_RAW draws its IV where none is given or the one given is NULL.
 */
public class EncryptSharedDrawTest extends MeasuredCellsTestSupport {

    @Test
    public void equalEncryptCallsShareARowsCiphertext() {
        engine.execute("CREATE OR REPLACE TABLE enc_t(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES "
            + "('msg','key'),('msg','key'),('x','y')");
        engine.execute("CREATE OR REPLACE TABLE enc_one(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES ('msg','key')");
        engine.execute("CREATE OR REPLACE TABLE enc_v(x VARCHAR)");
        engine.execute("INSERT INTO enc_v SELECT TO_VARCHAR(ENCRYPT(m, k)) FROM enc_one");
        engine.execute("INSERT INTO enc_v SELECT TO_VARCHAR(ENCRYPT(m, k)) FROM enc_one");
        engine.execute("INSERT INTO enc_v SELECT TO_VARCHAR(ENCRYPT('msg', 'key'))");
        engine.execute("INSERT INTO enc_v SELECT TO_VARCHAR(ENCRYPT('msg', 'key'))");
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(ENCRYPT('msg', 'key')) = TO_VARCHAR(ENCRYPT('msg', 'key'))",
                "true"},
            {"SELECT ENCRYPT('msg', 'key') = ENCRYPT('msg', 'key')",
                "true"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(ENCRYPT('msg', 'key'))) FROM TABLE(GENERATOR(ROWCOUNT => "
                + "5))",
                "5"},
            {"SELECT TO_VARCHAR(ENCRYPT('msg', 'key')) = TO_VARCHAR(ENCRYPT('msg', 'key', 'a'))",
                "false"},
            {"SELECT ENCRYPT('msg', 'key') = ENCRYPT('msg', 'KEY')",
                "false"},
            {"SELECT ENCRYPT('msg', 'key') = ENCRYPT( 'msg' , 'key' )",
                "true"},
            {"SELECT ENCRYPT('msg', 'key') = encrypt('msg', 'key')",
                "true"},
            {"SELECT ENCRYPT('msg', 'key', 'a') = ENCRYPT('msg', 'key', 'a')",
                "true"},
            {"SELECT ENCRYPT('msg', 'key', 'a', 'aes-gcm') = ENCRYPT('msg', 'key', 'a', 'aes-gcm')",
                "true"},
            {"SELECT ENCRYPT('msg', 'key', 'a', 'aes-gcm') = ENCRYPT('msg', 'key', 'a', 'AES-GCM')",
                "false"},
            {"SELECT ENCRYPT('msg', 'key') = ENCRYPT('ms' || 'g', 'key')",
                "true"},
            {"SELECT ENCRYPT('ms' || 'g', 'key') = ENCRYPT('ms' || 'g', 'key')",
                "true"},
            {"SELECT a = b FROM (SELECT ENCRYPT('msg', 'key') a, ENCRYPT('msg', 'key') b)",
                "true"},
            {"SELECT COUNT_IF(ENCRYPT('msg', 'key') = ENCRYPT('msg', 'key')) FROM TABLE(GENERATOR(ROWCOUNT"
                + " => 3))",
                "3"},
            {"SELECT COUNT(DISTINCT a) || '/' || COUNT(DISTINCT b) || '/' || COUNT_IF(a = b) FROM (SELECT "
                + "TO_VARCHAR(ENCRYPT('msg', 'key')) a, TO_VARCHAR(ENCRYPT('msg', 'key')) b FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 4)))",
                "4/4/4"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = ENCRYPT(m, k)) || '/' || COUNT(DISTINCT "
                + "TO_VARCHAR(ENCRYPT(m, k))) FROM enc_t",
                "3/3"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = ENCRYPT(enc_t.m, k)) FROM enc_t",
                "3"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = ENCRYPT(M, K)) FROM enc_t",
                "3"},
            {"SELECT COUNT(*) FROM enc_t WHERE ENCRYPT(m, k) = ENCRYPT(m, k)",
                "3"},
            {"SELECT COUNT(*) FROM enc_t WHERE ENCRYPT(m, k) <> ENCRYPT(m, k)",
                "0"},
            {"SELECT COUNT_IF(a = b) FROM (SELECT ENCRYPT(m, k) a FROM enc_t) x JOIN (SELECT ENCRYPT(m, k)"
                + " b FROM enc_t) y",
                "0"},
            {"SELECT COUNT_IF(a = b) FROM (SELECT ENCRYPT(m, k) a, ENCRYPT(m, k) b FROM enc_t ORDER BY m)",
                "3"},
            {"SELECT DECRYPT(ENCRYPT('msg', 'key'), 'key') = DECRYPT(ENCRYPT('msg', 'key'), 'key')",
                "true"},
            {"SELECT CASE WHEN ENCRYPT('msg', 'key') = ENCRYPT('msg', 'key') THEN 'same' ELSE 'diff' END",
                "same"},
            {"SELECT ENCRYPT('msg', 'key') <> ENCRYPT('msg', 'key')",
                "false"},
            {"SELECT ENCRYPT('msg', 'key') IN (ENCRYPT('msg', 'key'))",
                "true"},
            {"SELECT ENCRYPT(TO_BINARY('6D7367','HEX'), 'key') = ENCRYPT('msg', 'key')",
                "false"},
            {"SELECT ENCRYPT('msg', 'key', NULL) = ENCRYPT('msg', 'key')",
                "false"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = ENCRYPT('msg', 'key')) FROM enc_t",
                "0"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = x) FROM (SELECT m, k, ANY_VALUE(ENCRYPT(m, k)) x FROM enc_t "
                + "GROUP BY m, k)",
                "0"},
            {"SELECT ENCRYPT('msg', 'key') = (SELECT ENCRYPT('msg', 'key') FROM enc_t LIMIT 1)",
                "false"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = ENCRYPT(m || '', k)) FROM enc_t",
                "0"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = ENCRYPT((m), k)) FROM enc_t",
                "3"},
            {"SELECT COUNT_IF(ENCRYPT(m, k) = ENCRYPT(m::VARCHAR, k)) FROM enc_t",
                "0"},
            {"SELECT ENCRYPT('msg', 'key') = ENCRYPT('msg'::VARCHAR, 'key')",
                "false"},
            {"SELECT ENCRYPT('msg', 'key') = ENCRYPT('msg', 'key', NULL, 'AES-GCM')",
                "false"},
            {"SELECT ENCRYPT('msg', 'key', 'a') = ENCRYPT('msg', 'key', 'a', 'AES-GCM')",
                "false"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT TO_VARCHAR(ENCRYPT('msg', 'key')) x FROM enc_t UNION "
                + "ALL SELECT TO_VARCHAR(ENCRYPT('msg', 'key')) FROM enc_t)",
                "6"},
            {"SELECT COUNT_IF(ENCRYPT(x.m, x.k) = ENCRYPT(y.m, y.k)) FROM enc_t x JOIN enc_t y ON x.m = "
                + "y.m",
                "0"},
            {"SELECT COUNT_IF(ENCRYPT(x.m, x.k) = ENCRYPT(x.m, x.k)) FROM enc_t x JOIN enc_t y ON x.m = "
                + "y.m",
                "5"},
            {"SELECT COUNT(*) FROM enc_t WHERE ENCRYPT(m, k) <> ENCRYPT(m, k)",
                "0"},
            {"SELECT MIN(ENCRYPT('msg', 'key')) = MAX(ENCRYPT('msg', 'key')) FROM enc_t",
                "false"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(e)) FROM (SELECT ENCRYPT('msg', 'key') e FROM enc_t)",
                "3"},
            {"SELECT COUNT(*) FROM (SELECT ENCRYPT(m, k) a, ENCRYPT(m, k) b FROM enc_t QUALIFY "
                + "ROW_NUMBER() OVER (ORDER BY m) <= 3) WHERE a = b",
                "3"},
            {"SELECT COUNT_IF(a = b) FROM (SELECT ENCRYPT(m, k) a FROM enc_t) x, LATERAL (SELECT "
                + "ENCRYPT('msg', 'key') b)",
                "0"},
            {"SELECT TRY_DECRYPT(ENCRYPT('msg', 'key'), 'key') = TRY_DECRYPT(ENCRYPT('msg', 'key'), 'key')",
                "true"},
            {"SELECT COUNT_IF(IFF(m = 'x', ENCRYPT(m, k), NULL) = IFF(m = 'x', ENCRYPT(m, k), NULL)) FROM "
                + "enc_t",
                "1"},
            {"SELECT ENCRYPT('msg', 'key', '') = ENCRYPT('msg', 'key')",
                "false"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT TO_VARCHAR(ENCRYPT(m, k)) x FROM enc_one UNION ALL "
                + "SELECT TO_VARCHAR(ENCRYPT(m, k)) FROM enc_one)",
                "2"},
            {"SELECT a = b FROM (SELECT ENCRYPT(m, k) a FROM enc_one) x JOIN (SELECT ENCRYPT(m, k) b FROM "
                + "enc_one) y",
                "false"},
            {"SELECT COUNT(DISTINCT TO_VARCHAR(a)) FROM (SELECT ENCRYPT('msg', 'key') a UNION ALL SELECT "
                + "ENCRYPT('msg', 'key'))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM enc_v",
                "4"},
        });
    }

    @Test
    public void equalEncryptRawCallsShareARowsDraw() {
        engine.execute("CREATE OR REPLACE TABLE enc_t(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES "
            + "('msg','key'),('msg','key'),('x','y')");
        assertCells(new String[][] {
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'), NULL) = "
                + "ENCRYPT_RAW(TO_BINARY('00','HEX'), TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'), "
                + "NULL)",
                "true"},
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'), NULL):iv = "
                + "ENCRYPT_RAW(TO_BINARY('00','HEX'), TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'), "
                + "NULL):iv",
                "true"},
            {"SELECT COUNT(DISTINCT ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'), NULL):iv) FROM enc_t",
                "3"},
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'), NULL) = "
                + "ENCRYPT_RAW(TO_BINARY('00','HEX'), TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'))",
                "false"},
            {"SELECT COUNT(DISTINCT ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX')):iv) FROM enc_t",
                "3"},
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX')) = ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'))",
                "true"},
            {"SELECT ARRAY_TO_STRING(OBJECT_KEYS(ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'))), '+')",
                "ciphertext+iv+tag"},
            {"SELECT TO_VARCHAR(DECRYPT_RAW(e:ciphertext::BINARY, "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'), e:iv::BINARY, NULL, 'AES-GCM', "
                + "e:tag::BINARY), 'HEX') FROM (SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX')) e)",
                "00"},
            {"SELECT LENGTH(ENCRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX')):iv::BINARY)",
                "12"},
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), NULL) IS NULL",
                "true"},
        });
    }
}
