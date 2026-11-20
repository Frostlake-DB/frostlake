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
 * A VARCHAR whose text is {@code null} or looks like JSON stays a STRING inside an ARRAY or OBJECT, and a
 * VARIANT string read out of one (by path, GET, GET_PATH, GET_IGNORE_CASE or FLATTEN) is neither a JSON null
 * nor JSON structure, and reads as its own unquoted text wherever a VARCHAR is expected (live-verified).
 */
public class VariantStringMemberTest extends MeasuredCellsTestSupport {

    @Test
    public void variantStringsStayStrings() {
        engine.execute("CREATE OR REPLACE TABLE t1 (s VARCHAR, n INT)");
        engine.execute("INSERT INTO t1 VALUES ('null', 1), ('[1,2]', 2), ('{\"a\":1}', 3), ('abc', 4), (NULL, 5)");
        engine.execute("CREATE OR REPLACE TABLE t2 (v VARIANT)");
        engine.execute("INSERT INTO t2 SELECT PARSE_JSON('{\"v\":\"[a]\",\"n\":\"null\",\"o\":\"{x}\"}')");
        assertCells(new String[][] {
            {"SELECT ARRAY_CONSTRUCT('null')::VARCHAR",
                "[\"null\"]"},
            {"SELECT OBJECT_CONSTRUCT('k','null')::VARCHAR",
                "{\"k\":\"null\"}"},
            {"SELECT TYPEOF(ARRAY_CONSTRUCT('null')[0])",
                "VARCHAR"},
            {"SELECT TYPEOF(OBJECT_CONSTRUCT('k','null'):k)",
                "VARCHAR"},
            {"SELECT ARRAY_AGG(s)[0]::STRING IS NULL FROM (SELECT column1 s FROM VALUES ('null'))",
                "false"},
            {"SELECT ARRAY_AGG(s) WITHIN GROUP (ORDER BY n)::VARCHAR FROM t1",
                "[\"null\",\"[1,2]\",\"{\\\"a\\\":1}\",\"abc\"]"},
            {"SELECT ARRAY_CONTAINS('null'::VARIANT, ARRAY_CONSTRUCT('null'))",
                "true"},
            {"SELECT ARRAY_CONTAINS(PARSE_JSON('null'), ARRAY_CONSTRUCT('null'))",
                "false"},
            {"SELECT LISTAGG(ARRAY_CONSTRUCT(s)::VARCHAR, ' ') WITHIN GROUP (ORDER BY n) FROM t1",
                "[\"null\"] [\"[1,2]\"] [\"{\\\"a\\\":1}\"] [\"abc\"] [undefined]"},
            {"SELECT LISTAGG(TYPEOF(OBJECT_CONSTRUCT('k', s):k), ' ') WITHIN GROUP (ORDER BY n) FROM t1",
                "VARCHAR VARCHAR VARCHAR VARCHAR"},
            {"SELECT ARRAY_CONSTRUCT_COMPACT('null', NULL, 'x')::VARCHAR",
                "[\"null\",\"x\"]"},
            {"SELECT OBJECT_CONSTRUCT_KEEP_NULL('k','null','j',NULL)::VARCHAR",
                "{\"j\":null,\"k\":\"null\"}"},
            {"SELECT ARRAY_PREPEND(ARRAY_CONSTRUCT(1), 'null')::VARCHAR",
                "[\"null\",1]"},
            {"SELECT ARRAY_INSERT(ARRAY_CONSTRUCT(1), 0, 'null')::VARCHAR",
                "[\"null\",1]"},
            {"SELECT OBJECT_INSERT(OBJECT_CONSTRUCT('a',1), 'k', 'null')::VARCHAR",
                "{\"a\":1,\"k\":\"null\"}"},
            {"SELECT TO_JSON(ARRAY_CONSTRUCT('null'))",
                "[\"null\"]"},
            {"SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT('null', PARSE_JSON('null'), 'null'))::VARCHAR",
                "[\"null\",null]"},
            {"SELECT ARRAY_POSITION('null'::VARIANT, ARRAY_CONSTRUCT(PARSE_JSON('null'), 'null'))",
                "1"},
            {"SELECT ARRAY_POSITION(PARSE_JSON('null'), ARRAY_CONSTRUCT('null', PARSE_JSON('null')))",
                "1"},
            {"SELECT ARRAY_UNIQUE_AGG(s)::VARCHAR FROM (SELECT column1 s FROM VALUES ('null'))",
                "[\"null\"]"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('null','a'), '-')",
                "null-a"},
            {"SELECT ARRAY_SORT(ARRAY_CONSTRUCT('null','a'))::VARCHAR",
                "[\"a\",\"null\"]"},
            {"SELECT ARRAY_COMPACT(ARRAY_CONSTRUCT('null', NULL))::VARCHAR",
                "[\"null\"]"},
            {"SELECT ARRAY_CONSTRUCT('null') = PARSE_JSON('[\"null\"]')",
                "true"},
            {"SELECT ARRAY_CONSTRUCT('\"null\"')::VARCHAR",
                "[\"\\\"null\\\"\"]"},
            {"SELECT OBJECT_CONSTRUCT(*)::VARCHAR FROM (SELECT 'null' AS k, '[1]' AS j)",
                "{\"J\":\"[1]\",\"K\":\"null\"}"},
            {"SELECT TYPEOF(f.value) FROM TABLE(FLATTEN(PARSE_JSON('[\"null\"]'))) f",
                "VARCHAR"},
            {"SELECT IS_NULL_VALUE(f.value) FROM TABLE(FLATTEN(PARSE_JSON('[\"null\"]'))) f",
                "false"},
            {"SELECT f.value::STRING IS NULL FROM TABLE(FLATTEN(PARSE_JSON('[\"null\"]'))) f",
                "false"},
            {"SELECT TYPEOF(f.value) FROM TABLE(FLATTEN(PARSE_JSON('[null]'))) f",
                "NULL_VALUE"},
            {"SELECT LENGTH(f.value) FROM TABLE(FLATTEN(PARSE_JSON('[\"null\"]'))) f",
                "4"},
            {"SELECT TYPEOF(GET(PARSE_JSON('{\"a\":\"null\"}'),'a'))",
                "VARCHAR"},
            {"SELECT GET(PARSE_JSON('[\"null\"]'),0)::VARCHAR IS NULL",
                "false"},
            {"SELECT TYPEOF(GET_PATH(PARSE_JSON('{\"a\":\"null\"}'),'a'))",
                "VARCHAR"},
            {"SELECT TYPEOF(GET_IGNORE_CASE(PARSE_JSON('{\"a\":\"null\"}'),'A'))",
                "VARCHAR"},
            {"SELECT TYPEOF(GET(PARSE_JSON('{\"a\":null}'),'a'))",
                "NULL_VALUE"},
            {"SELECT TO_JSON(GET(PARSE_JSON('{\"a\":\"null\"}'),'a'))",
                "\"null\""},
            {"SELECT TYPEOF(PARSE_JSON('{\"a\":\"null\"}'):a)",
                "VARCHAR"},
            {"SELECT ARRAY_CONSTRUCT(GET_IGNORE_CASE(PARSE_JSON('{\"a\":[1]}'),'A'))::VARCHAR",
                "[[1]]"},
            {"SELECT OBJECT_KEYS(PARSE_JSON('{\"v\":\"{\\\\\"b\\\\\":1,\\\\\"a\\\\\":2}\"}'):v)::VARCHAR",
                "[\"a\",\"b\"]"},
            {"SELECT OBJECT_KEYS(PARSE_JSON('\"[1]\"'))",
                "REFUSED Failed to cast variant value \"[1]\" to OBJECT"},
            {"SELECT PARSE_JSON('{\"v\":\"[a]\"}'):v = '[a]'",
                "true"},
            {"SELECT PARSE_JSON('{\"v\":\"null\"}'):v = 'null'",
                "true"},
            {"SELECT PARSE_JSON('{\"v\":\"[a]\"}'):v = PARSE_JSON('\"[a]\"')",
                "true"},
            {"SELECT v:v || 'x' FROM (SELECT PARSE_JSON('{\"v\":\"[a]\"}') v)",
                "[a]x"},
            {"SELECT UPPER(v:v) FROM (SELECT PARSE_JSON('{\"v\":\"[a]\"}') v)",
                "[A]"},
            {"SELECT LENGTH(v:v) FROM (SELECT PARSE_JSON('{\"v\":\"[a]\"}') v)",
                "3"},
            {"SELECT SUBSTR(v:v,1,2) FROM (SELECT PARSE_JSON('{\"v\":\"[a]\"}') v)",
                "[a"},
            {"SELECT LENGTH(v:v) FROM (SELECT PARSE_JSON('{\"v\":\"null\"}') v)",
                "4"},
            {"SELECT v:v || 'x' FROM (SELECT PARSE_JSON('{\"v\":\"{b}\"}') v)",
                "{b}x"},
            {"SELECT STARTSWITH(PARSE_JSON('{\"v\":\"[a]\"}'):v, '[')",
                "true"},
            {"SELECT SPLIT(PARSE_JSON('{\"v\":\"[a,b]\"}'):v, ',')::VARCHAR",
                "[\"[a\",\"b]\"]"},
            {"SELECT LENGTH(PARSE_JSON('[\"[1]\"]')[0])",
                "3"},
            {"SELECT TO_JSON(PARSE_JSON('{\"v\":\"[a]\"}'):v)",
                "\"[a]\""},
            {"SELECT PARSE_JSON('{\"v\":\"[a]\"}'):v IN ('[a]', 'b')",
                "true"},
            {"SELECT REPLACE(PARSE_JSON('{\"v\":\"[a]\"}'):v, 'a', 'z')",
                "[z]"},
            {"SELECT TRIM(PARSE_JSON('{\"v\":\" [a] \"}'):v)",
                "[a]"},
            {"SELECT CASE PARSE_JSON('{\"v\":\"null\"}'):v WHEN 'null' THEN 'y' ELSE 'n' END",
                "y"},
            {"SELECT HASH(PARSE_JSON('{\"v\":\"[a]\"}'):v) = HASH(PARSE_JSON('\"[a]\"'))",
                "true"},
            {"SELECT PARSE_JSON('{\"v\":\"[1,2]\"}'):v::ARRAY::VARCHAR",
                "[\"[1,2]\"]"},
            {"SELECT TYPEOF(PARSE_JSON(PARSE_JSON('{\"v\":\"[1,2]\"}'):v))",
                "ARRAY"},
            {"SELECT IS_VARCHAR(PARSE_JSON('{\"v\":\"null\"}'):v)",
                "true"},
            {"SELECT IS_NULL_VALUE(PARSE_JSON('{\"v\":\"null\"}'):v)",
                "false"},
            {"SELECT ARRAY_AGG(v:x) WITHIN GROUP (ORDER BY v:i)::VARCHAR FROM (SELECT PARSE_JSON(column1) "
                + "v FROM VALUES ('{\"i\":1,\"x\":\"null\"}'), ('{\"i\":2,\"x\":null}'), "
                + "('{\"i\":3,\"x\":\"[1]\"}'))",
                "[\"null\",null,\"[1]\"]"},
            {"SELECT OBJECT_AGG('k'||v:i, v:x)::VARCHAR FROM (SELECT PARSE_JSON(column1) v FROM VALUES "
                + "('{\"i\":1,\"x\":\"null\"}'), ('{\"i\":2,\"x\":null}'), ('{\"i\":3,\"x\":\"[1]\"}'))",
                "{\"k1\":\"null\",\"k2\":null,\"k3\":\"[1]\"}"},
            {"SELECT LISTAGG(v:x, ',') WITHIN GROUP (ORDER BY v:x) FROM (SELECT PARSE_JSON(column1) v FROM"
                + " VALUES ('{\"x\":\"null\"}'), ('{\"x\":\"[q]\"}'))",
                "[q],null"},
            {"SELECT COUNT(*) FROM t2 WHERE v:v = '[a]'",
                "1"},
            {"SELECT COUNT(*) FROM t1 JOIN t2 ON t1.s = t2.v:n",
                "1"},
            {"SELECT LENGTH(v:n) FROM t2",
                "4"},
        });
    }
}
