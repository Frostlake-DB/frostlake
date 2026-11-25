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
 * The cryptographic functions in a VALUES clause and their arity sentences (live-verified): a FROM clause's VALUES
 * refuses RANDOM, UUID_STRING, the crypto calls and the other functions an INSERT's VALUES refuses; a
 * not-enough-arguments sentence redacts a crypto call's literals; DECRYPT_RAW counts against two arguments and
 * ENCRYPT_RAW against six. The cells are the agreeing answers of a two-sided measurement.
 */
public class CryptoValuesArityTest extends MeasuredCellsTestSupport {

    @Test
    public void valuesRefusalsAndAritySentences() {
        engine.execute("CREATE OR REPLACE TABLE enc_t(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES "
            + "('msg','key'),('msg','key'),('x','y')");
        engine.execute("CREATE OR REPLACE TABLE enc_t(m VARCHAR, k VARCHAR) AS SELECT * FROM VALUES "
            + "('msg','key'),('msg','key'),('x','y')");
        engine.execute("CREATE OR REPLACE TABLE enc_b(b BINARY, s VARCHAR)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM VALUES (COMPRESS('a', 'SNAPPY'))",
                "REFUSED SQL compilation error: | Invalid expression [COMPRESS('a', 'SNAPPY')] in VALUES "
                + "clause"},
            {"SELECT COUNT(*) FROM VALUES (MD5_BINARY('a'))",
                "REFUSED SQL compilation error: | Invalid expression [MD5_BINARY('a')] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (HEX_DECODE_BINARY('61'))",
                "REFUSED SQL compilation error: | Invalid expression [HEX_DECODE_BINARY('61')] in VALUES "
                + "clause"},
            {"SELECT COUNT(*) FROM VALUES (SHA2('a'))",
                "REFUSED SQL compilation error: | Invalid expression [SHA2('a')] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (UNIFORM(1, 10, 5))",
                "REFUSED SQL compilation error: | Invalid expression [UNIFORM(1, 10, 5)] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (SEQ4())",
                "REFUSED SQL compilation error: | Invalid expression [SEQ4()] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (UPPER(UUID_STRING()))",
                "REFUSED SQL compilation error: | Invalid expression [UPPER(UUID_STRING())] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (1), (RANDOM())",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (TO_BINARY('00','HEX'))",
                "1"},
            {"SELECT COUNT(*) FROM VALUES (UPPER('a'))",
                "1"},
            {"SELECT COUNT(*) FROM VALUES (UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', 'foo'))",
                "REFUSED SQL compilation error: | Invalid expression "
                + "[UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', 'foo')] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (RANDOM(5))",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM(5)] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (SEQ1())",
                "REFUSED SQL compilation error: | Invalid expression [SEQ1()] in VALUES clause"},
            {"SELECT COUNT(*) FROM VALUES (RANDSTR(5, 1))",
                "REFUSED SQL compilation error: | Invalid expression [RANDSTR(5, 1)] in VALUES clause"},
            {"SELECT * FROM VALUES (RANDOM()) v(x) WHERE 1 = 0",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT * FROM (VALUES (RANDOM()))",
                "REFUSED SQL compilation error: | Invalid expression [RANDOM()] in VALUES clause"},
            {"SELECT ENCRYPT(m) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(ENC_T.M)], expected 2, got 1"},
            {"SELECT DECRYPT(TO_BINARY(m)) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [DECRYPT(CAST(ENC_T.M AS BINARY(67108864)))], expected 2, got 1"},
            {"SELECT ENCRYPT(1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT ENCRYPT(NULL)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(null)], expected 2, got 1"},
            {"SELECT ENCRYPT(UPPER(m)) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(UPPER(ENC_T.M))], expected 2, got 1"},
            {"SELECT ENCRYPT('a' || m) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT((redacted_argument) || ENC_T.M)], expected 2, got 1"},
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), TO_BINARY('00','HEX'), TO_BINARY('00','HEX'), "
                + "TO_BINARY('00','HEX'), 'AES-GCM', TO_BINARY('00','HEX'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'ENCRYPT_RAW': (BINARY(67108864), BINARY(67108864), BINARY(67108864), "
                + "BINARY(67108864), VARCHAR(7), BINARY(67108864))"},
            {"SELECT TRY_DECRYPT('a')",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [TRY_DECRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT TRY_DECRYPT_RAW('a')",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [TRY_DECRYPT_RAW(redacted_argument)], expected 2, got 1"},
            {"SELECT DECRYPT_RAW()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [DECRYPT_RAW()], expected 2, got 0"},
            {"SELECT ENCRYPT(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT ENCRYPT(1.5)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT ENCRYPT(-1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT ENCRYPT('a'::VARCHAR)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT UPPER(ENCRYPT('a'))",
                "REFUSED SQL compilation error: error line 1 at position 13 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT TRY_DECRYPT()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [TRY_DECRYPT()], expected 2, got 0"},
            {"INSERT INTO enc_b(s) VALUES (RANDSTR(5, 1))",
                "REFUSED SQL compilation error: | Invalid expression [RANDSTR(5, 1)] in VALUES clause"},
            {"INSERT INTO enc_b(s) VALUES (UPPER(UUID_STRING()))",
                "REFUSED SQL compilation error: | Invalid expression [UPPER(UUID_STRING())] in VALUES clause"},
            {"SELECT TRY_DECRYPT_RAW(TO_BINARY('00','HEX'), "
                + "TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'))",
                "null"},
            {"SELECT ENCRYPT_RAW(TO_BINARY('00','HEX'), TO_BINARY('00','HEX'), TO_BINARY('00','HEX'), "
                + "TO_BINARY('00','HEX'), 'AES-GCM', 'x')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'ENCRYPT_RAW': (BINARY(67108864), BINARY(67108864), BINARY(67108864), "
                + "BINARY(67108864), VARCHAR(7), VARCHAR(1))"},
            {"SELECT DECRYPT_RAW(NULL, TO_BINARY('00112233445566778899AABBCCDDEEFF','HEX'))",
                "null"},
            {"SELECT ENCRYPT(LENGTH(m) + 1) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT((LENGTH(ENC_T.M)) + (redacted_argument))], expected 2, got 1"},
            {"SELECT ENCRYPT(m || 'a') FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(ENC_T.M || (redacted_argument))], expected 2, got 1"},
            {"SELECT ENCRYPT(X'00')",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT DECRYPT(TO_BINARY(m, 'HEX')) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [DECRYPT(TO_BINARY(ENC_T.M, redacted_argument))], expected 2, got 1"},
            {"SELECT ENCRYPT(TO_DATE('2020-01-01'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(CAST(redacted_argument AS DATE))], expected 2, got 1"},
            {"SELECT ENCRYPT(CONCAT('a', m)) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(CONCAT(redacted_argument, ENC_T.M))], expected 2, got 1"},
            {"SELECT ENCRYPT(-1.5)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(redacted_argument)], expected 2, got 1"},
            {"SELECT ENCRYPT(NULL::VARCHAR)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(SYSTEM$NULL_TO_TEXT(null))], expected 2, got 1"},
            {"SELECT DECRYPT_RAW(TO_BINARY('00','HEX'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [DECRYPT_RAW(TO_BINARY(redacted_argument, redacted_argument))], expected 2, got 1"},
            {"SELECT ENCRYPT('a' || 'b')",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT((redacted_argument) || (redacted_argument))], expected 2, got 1"},
            {"SELECT ENCRYPT(IFF(m = 'x', 'a', m)) FROM enc_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [ENCRYPT(IFF(ENC_T.M = (redacted_argument), redacted_argument, ENC_T.M))], expected"
                + " 2, got 1"},
        });
    }
}
