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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * VARIANT values that {@code =} finds equal meet in every keyed operation, live-verified: a join's ON and a comma
 * join's WHERE, IN, INTERSECT, EXCEPT, UNION, SELECT DISTINCT, GROUP BY and COUNT(DISTINCT) agree with {@code =} on
 * numbers whatever their notation or carrier (1, 1.0, 1e0, a FLOAT, a scaled NUMBER cast to VARIANT), on arrays and
 * objects whose numbers differ in notation or whose keys differ in order, on the two zeros and on NaN; a string never
 * meets the number it spells, nor a JSON {@code true} the number 1. A VARIANT branch beside a NUMBER branch keys alike.
 */
public class VariantJoinKeyEqualityTest extends MeasuredCellsTestSupport {

    @Test
    public void storedAndInlinePairs() {
        engine.execute("CREATE TABLE p(id INT, v VARIANT, w VARIANT)");
        engine.execute("INSERT INTO p SELECT 1, PARSE_JSON('1'), TO_VARIANT(1::FLOAT)");
        engine.execute("INSERT INTO p SELECT 2, PARSE_JSON('1'), PARSE_JSON('1.0')");
        engine.execute("INSERT INTO p SELECT 3, PARSE_JSON('1'), PARSE_JSON('1e0')");
        engine.execute("INSERT INTO p SELECT 4, PARSE_JSON('1'), TO_VARIANT('1')");
        engine.execute("INSERT INTO p SELECT 5, PARSE_JSON('1'), PARSE_JSON('\"1\"')");
        engine.execute("INSERT INTO p SELECT 6, PARSE_JSON('{\"a\":1}'), PARSE_JSON('{\"a\":1.0}')");
        engine.execute("INSERT INTO p SELECT 7, PARSE_JSON('{\"a\":1,\"b\":2}'), PARSE_JSON('{\"b\":2,\"a\":1}')");
        engine.execute("INSERT INTO p SELECT 8, PARSE_JSON('[1]'), PARSE_JSON('[1.0]')");
        engine.execute("INSERT INTO p SELECT 9, TO_VARIANT(-0.0::FLOAT), TO_VARIANT(0::FLOAT)");
        engine.execute("INSERT INTO p SELECT 10, PARSE_JSON('0'), PARSE_JSON('-0.0')");
        engine.execute("INSERT INTO p SELECT 11, TO_VARIANT('NaN'::FLOAT), TO_VARIANT('NaN'::FLOAT)");
        engine.execute("INSERT INTO p SELECT 12, 2.500::NUMBER(10,3)::VARIANT, PARSE_JSON('2.5')");
        engine.execute("INSERT INTO p SELECT 13, TO_VARIANT(1), TO_VARIANT(1.00::NUMBER(5,2))");
        engine.execute("INSERT INTO p SELECT 14, PARSE_JSON('12345678901234567890'), "
            + "PARSE_JSON('1.2345678901234567e19')");
        engine.execute("INSERT INTO p SELECT 15, PARSE_JSON('0.1'), TO_VARIANT(0.1::FLOAT)");
        engine.execute("INSERT INTO p SELECT 16, PARSE_JSON('\"abc\"'), PARSE_JSON('\"ABC\"')");
        engine.execute("INSERT INTO p SELECT 17, PARSE_JSON('true'), PARSE_JSON('\"true\"')");
        engine.execute("INSERT INTO p SELECT 18, PARSE_JSON('null'), PARSE_JSON('null')");
        engine.execute("INSERT INTO p SELECT 19, PARSE_JSON('9007199254740993'), PARSE_JSON('9007199254740992')");
        engine.execute("INSERT INTO p SELECT 20, PARSE_JSON('{\"a\":[1,{\"b\":1}]}'), "
            + "PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}')");
        engine.execute("INSERT INTO p SELECT 21, TO_VARIANT('2020-01-01'::DATE), PARSE_JSON('\"2020-01-01\"')");
        engine.execute("INSERT INTO p SELECT 22, PARSE_JSON('1.5'), TO_VARIANT(1.5::NUMBER(3,1))");
        engine.execute("INSERT INTO p SELECT 23, PARSE_JSON('1'), TO_VARIANT(TRUE)");
        engine.execute("INSERT INTO p SELECT 24, PARSE_JSON('0.30000000000000004'), PARSE_JSON('0.3')");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=1) a JOIN (SELECT w FROM p WHERE id=1) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=1) a, (SELECT w FROM p WHERE id=1) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=1) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=1))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=1) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=1))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=1) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=1))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=1) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=1))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=1) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=1))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=1) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=1)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a JOIN (SELECT TO_VARIANT(1::FLOAT) w) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a, (SELECT TO_VARIANT(1::FLOAT) w) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "TO_VARIANT(1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) INTERSECT SELECT w FROM "
                + "(SELECT TO_VARIANT(1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) EXCEPT SELECT w FROM (SELECT "
                + "TO_VARIANT(1::FLOAT) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) UNION SELECT w FROM (SELECT "
                + "TO_VARIANT(1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL SELECT w"
                + " FROM (SELECT TO_VARIANT(1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL "
                + "SELECT w FROM (SELECT TO_VARIANT(1::FLOAT) w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=2) a JOIN (SELECT w FROM p WHERE id=2) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=2) a, (SELECT w FROM p WHERE id=2) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=2) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=2))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=2) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=2))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=2) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=2))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=2) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=2))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=2) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=2))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=2) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=2)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a JOIN (SELECT PARSE_JSON('1.0') w) b ON a.v"
                + " = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a, (SELECT PARSE_JSON('1.0') w) b WHERE a.v "
                + "= b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('1.0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) INTERSECT SELECT w FROM "
                + "(SELECT PARSE_JSON('1.0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) EXCEPT SELECT w FROM (SELECT "
                + "PARSE_JSON('1.0') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) UNION SELECT w FROM (SELECT "
                + "PARSE_JSON('1.0') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL SELECT w"
                + " FROM (SELECT PARSE_JSON('1.0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('1.0') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=3) a JOIN (SELECT w FROM p WHERE id=3) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=3) a, (SELECT w FROM p WHERE id=3) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=3) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=3))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=3) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=3))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=3) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=3))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=3) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=3))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=3) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=3))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=3) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=3)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a JOIN (SELECT PARSE_JSON('1e0') w) b ON a.v"
                + " = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a, (SELECT PARSE_JSON('1e0') w) b WHERE a.v "
                + "= b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('1e0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) INTERSECT SELECT w FROM "
                + "(SELECT PARSE_JSON('1e0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) EXCEPT SELECT w FROM (SELECT "
                + "PARSE_JSON('1e0') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) UNION SELECT w FROM (SELECT "
                + "PARSE_JSON('1e0') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL SELECT w"
                + " FROM (SELECT PARSE_JSON('1e0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('1e0') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=4) a JOIN (SELECT w FROM p WHERE id=4) b ON "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=4) a, (SELECT w FROM p WHERE id=4) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=4) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=4))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=4) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=4))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=4) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=4))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=4) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=4))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=4) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=4))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=4) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=4)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a JOIN (SELECT TO_VARIANT('1') w) b ON a.v ="
                + " b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a, (SELECT TO_VARIANT('1') w) b WHERE a.v = "
                + "b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "TO_VARIANT('1') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) INTERSECT SELECT w FROM "
                + "(SELECT TO_VARIANT('1') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) EXCEPT SELECT w FROM (SELECT "
                + "TO_VARIANT('1') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) UNION SELECT w FROM (SELECT "
                + "TO_VARIANT('1') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL SELECT w"
                + " FROM (SELECT TO_VARIANT('1') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL "
                + "SELECT w FROM (SELECT TO_VARIANT('1') w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=5) a JOIN (SELECT w FROM p WHERE id=5) b ON "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=5) a, (SELECT w FROM p WHERE id=5) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=5) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=5))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=5) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=5))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=5) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=5))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=5) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=5))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=5) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=5))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=5) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=5)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a JOIN (SELECT PARSE_JSON('\"1\"') w) b ON "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a, (SELECT PARSE_JSON('\"1\"') w) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('\"1\"') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) INTERSECT SELECT w FROM "
                + "(SELECT PARSE_JSON('\"1\"') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) EXCEPT SELECT w FROM (SELECT "
                + "PARSE_JSON('\"1\"') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) UNION SELECT w FROM (SELECT "
                + "PARSE_JSON('\"1\"') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL SELECT w"
                + " FROM (SELECT PARSE_JSON('\"1\"') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('\"1\"') w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=6) a JOIN (SELECT w FROM p WHERE id=6) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=6) a, (SELECT w FROM p WHERE id=6) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=6) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=6))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=6) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=6))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=6) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=6))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=6) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=6))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=6) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=6))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=6) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=6)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":1}') v) a JOIN (SELECT "
                + "PARSE_JSON('{\"a\":1.0}') w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":1}') v) a, (SELECT PARSE_JSON('{\"a\":1.0}')"
                + " w) b WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":1}') v) a WHERE a.v IN (SELECT w FROM "
                + "(SELECT PARSE_JSON('{\"a\":1.0}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":1}') v) INTERSECT SELECT w "
                + "FROM (SELECT PARSE_JSON('{\"a\":1.0}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":1}') v) EXCEPT SELECT w FROM "
                + "(SELECT PARSE_JSON('{\"a\":1.0}') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":1}') v) UNION SELECT w FROM "
                + "(SELECT PARSE_JSON('{\"a\":1.0}') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('{\"a\":1}') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('{\"a\":1.0}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('{\"a\":1}') v) "
                + "UNION ALL SELECT w FROM (SELECT PARSE_JSON('{\"a\":1.0}') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=7) a JOIN (SELECT w FROM p WHERE id=7) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=7) a, (SELECT w FROM p WHERE id=7) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=7) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=7))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=7) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=7))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=7) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=7))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=7) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=7))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=7) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=7))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=7) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=7)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}') v) a JOIN (SELECT "
                + "PARSE_JSON('{\"b\":2,\"a\":1}') w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}') v) a, (SELECT "
                + "PARSE_JSON('{\"b\":2,\"a\":1}') w) b WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}') v) a WHERE a.v IN (SELECT w "
                + "FROM (SELECT PARSE_JSON('{\"b\":2,\"a\":1}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}') v) INTERSECT "
                + "SELECT w FROM (SELECT PARSE_JSON('{\"b\":2,\"a\":1}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}') v) EXCEPT SELECT"
                + " w FROM (SELECT PARSE_JSON('{\"b\":2,\"a\":1}') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}') v) UNION SELECT "
                + "w FROM (SELECT PARSE_JSON('{\"b\":2,\"a\":1}') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}') v) "
                + "UNION ALL SELECT w FROM (SELECT PARSE_JSON('{\"b\":2,\"a\":1}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('{\"a\":1,\"b\":2}')"
                + " v) UNION ALL SELECT w FROM (SELECT PARSE_JSON('{\"b\":2,\"a\":1}') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=8) a JOIN (SELECT w FROM p WHERE id=8) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=8) a, (SELECT w FROM p WHERE id=8) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=8) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=8))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=8) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=8))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=8) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=8))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=8) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=8))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=8) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=8))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=8) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=8)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('[1]') v) a JOIN (SELECT PARSE_JSON('[1.0]') w) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('[1]') v) a, (SELECT PARSE_JSON('[1.0]') w) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('[1]') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('[1.0]') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('[1]') v) INTERSECT SELECT w FROM "
                + "(SELECT PARSE_JSON('[1.0]') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('[1]') v) EXCEPT SELECT w FROM "
                + "(SELECT PARSE_JSON('[1.0]') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('[1]') v) UNION SELECT w FROM (SELECT"
                + " PARSE_JSON('[1.0]') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('[1]') v) UNION ALL SELECT"
                + " w FROM (SELECT PARSE_JSON('[1.0]') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('[1]') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('[1.0]') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=9) a JOIN (SELECT w FROM p WHERE id=9) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=9) a, (SELECT w FROM p WHERE id=9) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=9) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=9))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=9) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=9))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=9) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=9))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=9) UNION SELECT w FROM (SELECT"
                + " w FROM p WHERE id=9))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=9) UNION ALL SELECT"
                + " w FROM (SELECT w FROM p WHERE id=9))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=9) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=9)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) a JOIN (SELECT TO_VARIANT(0::FLOAT) "
                + "w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) a, (SELECT TO_VARIANT(0::FLOAT) w) b"
                + " WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) a WHERE a.v IN (SELECT w FROM "
                + "(SELECT TO_VARIANT(0::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) INTERSECT SELECT w "
                + "FROM (SELECT TO_VARIANT(0::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) EXCEPT SELECT w FROM "
                + "(SELECT TO_VARIANT(0::FLOAT) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) UNION SELECT w FROM "
                + "(SELECT TO_VARIANT(0::FLOAT) w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) UNION ALL "
                + "SELECT w FROM (SELECT TO_VARIANT(0::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT TO_VARIANT(-0.0::FLOAT) v) "
                + "UNION ALL SELECT w FROM (SELECT TO_VARIANT(0::FLOAT) w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=10) a JOIN (SELECT w FROM p WHERE id=10) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=10) a, (SELECT w FROM p WHERE id=10) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=10) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=10))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=10) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=10))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=10) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=10))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=10) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=10))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=10) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=10))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=10) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=10)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0') v) a JOIN (SELECT PARSE_JSON('-0.0') w) b ON "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0') v) a, (SELECT PARSE_JSON('-0.0') w) b WHERE a.v"
                + " = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('-0.0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0') v) INTERSECT SELECT w FROM "
                + "(SELECT PARSE_JSON('-0.0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0') v) EXCEPT SELECT w FROM (SELECT "
                + "PARSE_JSON('-0.0') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0') v) UNION SELECT w FROM (SELECT "
                + "PARSE_JSON('-0.0') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('0') v) UNION ALL SELECT w"
                + " FROM (SELECT PARSE_JSON('-0.0') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('0') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('-0.0') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=11) a JOIN (SELECT w FROM p WHERE id=11) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=11) a, (SELECT w FROM p WHERE id=11) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=11) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=11))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=11) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=11))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=11) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=11))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=11) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=11))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=11) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=11))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=11) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=11)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) a JOIN (SELECT "
                + "TO_VARIANT('NaN'::FLOAT) w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) a, (SELECT TO_VARIANT('NaN'::FLOAT)"
                + " w) b WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) a WHERE a.v IN (SELECT w FROM "
                + "(SELECT TO_VARIANT('NaN'::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) INTERSECT SELECT w "
                + "FROM (SELECT TO_VARIANT('NaN'::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) EXCEPT SELECT w FROM"
                + " (SELECT TO_VARIANT('NaN'::FLOAT) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) UNION SELECT w FROM "
                + "(SELECT TO_VARIANT('NaN'::FLOAT) w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) UNION ALL"
                + " SELECT w FROM (SELECT TO_VARIANT('NaN'::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT TO_VARIANT('NaN'::FLOAT) v) "
                + "UNION ALL SELECT w FROM (SELECT TO_VARIANT('NaN'::FLOAT) w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=12) a JOIN (SELECT w FROM p WHERE id=12) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=12) a, (SELECT w FROM p WHERE id=12) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=12) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=12))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=12) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=12))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=12) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=12))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=12) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=12))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=12) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=12))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=12) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=12)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) a JOIN (SELECT "
                + "PARSE_JSON('2.5') w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) a, (SELECT PARSE_JSON('2.5') w)"
                + " b WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) a WHERE a.v IN (SELECT w FROM "
                + "(SELECT PARSE_JSON('2.5') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) INTERSECT SELECT"
                + " w FROM (SELECT PARSE_JSON('2.5') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) EXCEPT SELECT w "
                + "FROM (SELECT PARSE_JSON('2.5') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) UNION SELECT w "
                + "FROM (SELECT PARSE_JSON('2.5') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) UNION"
                + " ALL SELECT w FROM (SELECT PARSE_JSON('2.5') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v)"
                + " UNION ALL SELECT w FROM (SELECT PARSE_JSON('2.5') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=13) a JOIN (SELECT w FROM p WHERE id=13) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=13) a, (SELECT w FROM p WHERE id=13) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=13) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=13))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=13) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=13))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=13) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=13))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=13) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=13))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=13) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=13))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=13) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=13)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(1) v) a JOIN (SELECT TO_VARIANT(1.00::NUMBER(5,2)) "
                + "w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(1) v) a, (SELECT TO_VARIANT(1.00::NUMBER(5,2)) w) b "
                + "WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(1) v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "TO_VARIANT(1.00::NUMBER(5,2)) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT(1) v) INTERSECT SELECT w FROM (SELECT"
                + " TO_VARIANT(1.00::NUMBER(5,2)) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT(1) v) EXCEPT SELECT w FROM (SELECT "
                + "TO_VARIANT(1.00::NUMBER(5,2)) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT(1) v) UNION SELECT w FROM (SELECT "
                + "TO_VARIANT(1.00::NUMBER(5,2)) w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT TO_VARIANT(1) v) UNION ALL SELECT w "
                + "FROM (SELECT TO_VARIANT(1.00::NUMBER(5,2)) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT TO_VARIANT(1) v) UNION ALL "
                + "SELECT w FROM (SELECT TO_VARIANT(1.00::NUMBER(5,2)) w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=14) a JOIN (SELECT w FROM p WHERE id=14) b ON"
                + " a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=14) a, (SELECT w FROM p WHERE id=14) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=14) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=14))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=14) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=14))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=14) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=14))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=14) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=14))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=14) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=14))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=14) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=14)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('12345678901234567890') v) INTERSECT "
                + "SELECT w FROM (SELECT PARSE_JSON('1.2345678901234567e19') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('12345678901234567890') v) EXCEPT "
                + "SELECT w FROM (SELECT PARSE_JSON('1.2345678901234567e19') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('12345678901234567890') v) UNION "
                + "SELECT w FROM (SELECT PARSE_JSON('1.2345678901234567e19') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('12345678901234567890') v)"
                + " UNION ALL SELECT w FROM (SELECT PARSE_JSON('1.2345678901234567e19') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT "
                + "PARSE_JSON('12345678901234567890') v) UNION ALL SELECT w FROM (SELECT "
                + "PARSE_JSON('1.2345678901234567e19') w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=15) a JOIN (SELECT w FROM p WHERE id=15) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=15) a, (SELECT w FROM p WHERE id=15) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=15) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=15))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=15) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=15))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=15) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=15))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=15) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=15))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=15) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=15))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=15) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=15)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0.1') v) a JOIN (SELECT TO_VARIANT(0.1::FLOAT) w) b"
                + " ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0.1') v) a, (SELECT TO_VARIANT(0.1::FLOAT) w) b "
                + "WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0.1') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "TO_VARIANT(0.1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0.1') v) INTERSECT SELECT w FROM "
                + "(SELECT TO_VARIANT(0.1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0.1') v) EXCEPT SELECT w FROM "
                + "(SELECT TO_VARIANT(0.1::FLOAT) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0.1') v) UNION SELECT w FROM (SELECT"
                + " TO_VARIANT(0.1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('0.1') v) UNION ALL SELECT"
                + " w FROM (SELECT TO_VARIANT(0.1::FLOAT) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('0.1') v) UNION ALL "
                + "SELECT w FROM (SELECT TO_VARIANT(0.1::FLOAT) w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=16) a JOIN (SELECT w FROM p WHERE id=16) b ON"
                + " a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=16) a, (SELECT w FROM p WHERE id=16) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=16) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=16))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=16) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=16))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=16) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=16))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=16) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=16))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=16) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=16))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=16) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=16)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('\"abc\"') v) a JOIN (SELECT PARSE_JSON('\"ABC\"') "
                + "w) b ON a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('\"abc\"') v) a, (SELECT PARSE_JSON('\"ABC\"') w) b "
                + "WHERE a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('\"abc\"') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('\"ABC\"') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('\"abc\"') v) INTERSECT SELECT w FROM"
                + " (SELECT PARSE_JSON('\"ABC\"') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('\"abc\"') v) EXCEPT SELECT w FROM "
                + "(SELECT PARSE_JSON('\"ABC\"') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('\"abc\"') v) UNION SELECT w FROM "
                + "(SELECT PARSE_JSON('\"ABC\"') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('\"abc\"') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('\"ABC\"') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('\"abc\"') v) UNION "
                + "ALL SELECT w FROM (SELECT PARSE_JSON('\"ABC\"') w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=17) a JOIN (SELECT w FROM p WHERE id=17) b ON"
                + " a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=17) a, (SELECT w FROM p WHERE id=17) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=17) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=17))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=17) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=17))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=17) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=17))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=17) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=17))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=17) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=17))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=17) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=17)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('true') v) a JOIN (SELECT PARSE_JSON('\"true\"') w) "
                + "b ON a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('true') v) a, (SELECT PARSE_JSON('\"true\"') w) b "
                + "WHERE a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('true') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('\"true\"') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('true') v) INTERSECT SELECT w FROM "
                + "(SELECT PARSE_JSON('\"true\"') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('true') v) EXCEPT SELECT w FROM "
                + "(SELECT PARSE_JSON('\"true\"') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('true') v) UNION SELECT w FROM "
                + "(SELECT PARSE_JSON('\"true\"') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('true') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('\"true\"') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('true') v) UNION ALL"
                + " SELECT w FROM (SELECT PARSE_JSON('\"true\"') w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=18) a JOIN (SELECT w FROM p WHERE id=18) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=18) a, (SELECT w FROM p WHERE id=18) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=18) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=18))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=18) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=18))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=18) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=18))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=18) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=18))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=18) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=18))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=18) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=18)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('null') v) a JOIN (SELECT PARSE_JSON('null') w) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('null') v) a, (SELECT PARSE_JSON('null') w) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('null') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "PARSE_JSON('null') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('null') v) INTERSECT SELECT w FROM "
                + "(SELECT PARSE_JSON('null') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('null') v) EXCEPT SELECT w FROM "
                + "(SELECT PARSE_JSON('null') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('null') v) UNION SELECT w FROM "
                + "(SELECT PARSE_JSON('null') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('null') v) UNION ALL "
                + "SELECT w FROM (SELECT PARSE_JSON('null') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('null') v) UNION ALL"
                + " SELECT w FROM (SELECT PARSE_JSON('null') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=19) a JOIN (SELECT w FROM p WHERE id=19) b ON"
                + " a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=19) a, (SELECT w FROM p WHERE id=19) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=19) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=19))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=19) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=19))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=19) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=19))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=19) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=19))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=19) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=19))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=19) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=19)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('9007199254740993') v) a JOIN (SELECT "
                + "PARSE_JSON('9007199254740992') w) b ON a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('9007199254740993') v) a, (SELECT "
                + "PARSE_JSON('9007199254740992') w) b WHERE a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('9007199254740993') v) a WHERE a.v IN (SELECT w FROM"
                + " (SELECT PARSE_JSON('9007199254740992') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('9007199254740993') v) INTERSECT "
                + "SELECT w FROM (SELECT PARSE_JSON('9007199254740992') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('9007199254740993') v) EXCEPT SELECT "
                + "w FROM (SELECT PARSE_JSON('9007199254740992') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('9007199254740993') v) UNION SELECT w"
                + " FROM (SELECT PARSE_JSON('9007199254740992') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('9007199254740993') v) "
                + "UNION ALL SELECT w FROM (SELECT PARSE_JSON('9007199254740992') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('9007199254740993') "
                + "v) UNION ALL SELECT w FROM (SELECT PARSE_JSON('9007199254740992') w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=20) a JOIN (SELECT w FROM p WHERE id=20) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=20) a, (SELECT w FROM p WHERE id=20) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=20) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=20))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=20) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=20))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=20) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=20))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=20) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=20))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=20) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=20))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=20) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=20)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":[1,{\"b\":1}]}') v) a JOIN (SELECT "
                + "PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":[1,{\"b\":1}]}') v) a, (SELECT "
                + "PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w) b WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('{\"a\":[1,{\"b\":1}]}') v) a WHERE a.v IN (SELECT w"
                + " FROM (SELECT PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":[1,{\"b\":1}]}') v) INTERSECT"
                + " SELECT w FROM (SELECT PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":[1,{\"b\":1}]}') v) EXCEPT "
                + "SELECT w FROM (SELECT PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('{\"a\":[1,{\"b\":1}]}') v) UNION "
                + "SELECT w FROM (SELECT PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('{\"a\":[1,{\"b\":1}]}') "
                + "v) UNION ALL SELECT w FROM (SELECT PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT "
                + "PARSE_JSON('{\"a\":[1,{\"b\":1}]}') v) UNION ALL SELECT w FROM (SELECT "
                + "PARSE_JSON('{\"a\":[1.0,{\"b\":1e0}]}') w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=21) a JOIN (SELECT w FROM p WHERE id=21) b ON"
                + " a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=21) a, (SELECT w FROM p WHERE id=21) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=21) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=21))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=21) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=21))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=21) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=21))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=21) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=21))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=21) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=21)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT('2020-01-01'::DATE) v) a JOIN (SELECT "
                + "PARSE_JSON('\"2020-01-01\"') w) b ON a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT('2020-01-01'::DATE) v) a, (SELECT "
                + "PARSE_JSON('\"2020-01-01\"') w) b WHERE a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT('2020-01-01'::DATE) v) INTERSECT "
                + "SELECT w FROM (SELECT PARSE_JSON('\"2020-01-01\"') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT('2020-01-01'::DATE) v) EXCEPT SELECT "
                + "w FROM (SELECT PARSE_JSON('\"2020-01-01\"') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT TO_VARIANT('2020-01-01'::DATE) v) UNION SELECT w"
                + " FROM (SELECT PARSE_JSON('\"2020-01-01\"') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT TO_VARIANT('2020-01-01'::DATE) v) "
                + "UNION ALL SELECT w FROM (SELECT PARSE_JSON('\"2020-01-01\"') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT TO_VARIANT('2020-01-01'::DATE) "
                + "v) UNION ALL SELECT w FROM (SELECT PARSE_JSON('\"2020-01-01\"') w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=22) a JOIN (SELECT w FROM p WHERE id=22) b ON"
                + " a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=22) a, (SELECT w FROM p WHERE id=22) b WHERE "
                + "a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=22) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=22))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=22) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=22))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=22) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=22))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=22) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=22))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=22) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=22))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=22) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=22)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1.5') v) a JOIN (SELECT "
                + "TO_VARIANT(1.5::NUMBER(3,1)) w) b ON a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1.5') v) a, (SELECT TO_VARIANT(1.5::NUMBER(3,1)) w)"
                + " b WHERE a.v = b.w",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1.5') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "TO_VARIANT(1.5::NUMBER(3,1)) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1.5') v) INTERSECT SELECT w FROM "
                + "(SELECT TO_VARIANT(1.5::NUMBER(3,1)) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1.5') v) EXCEPT SELECT w FROM "
                + "(SELECT TO_VARIANT(1.5::NUMBER(3,1)) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1.5') v) UNION SELECT w FROM (SELECT"
                + " TO_VARIANT(1.5::NUMBER(3,1)) w))",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('1.5') v) UNION ALL SELECT"
                + " w FROM (SELECT TO_VARIANT(1.5::NUMBER(3,1)) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('1.5') v) UNION ALL "
                + "SELECT w FROM (SELECT TO_VARIANT(1.5::NUMBER(3,1)) w)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=23) a JOIN (SELECT w FROM p WHERE id=23) b ON"
                + " a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=23) a, (SELECT w FROM p WHERE id=23) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=23) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=23))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=23) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=23))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=23) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=23))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=23) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=23))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=23) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=23))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=23) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=23)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a JOIN (SELECT TO_VARIANT(TRUE) w) b ON a.v "
                + "= b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a, (SELECT TO_VARIANT(TRUE) w) b WHERE a.v ="
                + " b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a WHERE a.v IN (SELECT w FROM (SELECT "
                + "TO_VARIANT(TRUE) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) INTERSECT SELECT w FROM "
                + "(SELECT TO_VARIANT(TRUE) w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) EXCEPT SELECT w FROM (SELECT "
                + "TO_VARIANT(TRUE) w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('1') v) UNION SELECT w FROM (SELECT "
                + "TO_VARIANT(TRUE) w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL SELECT w"
                + " FROM (SELECT TO_VARIANT(TRUE) w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT PARSE_JSON('1') v) UNION ALL "
                + "SELECT w FROM (SELECT TO_VARIANT(TRUE) w)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=24) a JOIN (SELECT w FROM p WHERE id=24) b ON"
                + " a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=24) a, (SELECT w FROM p WHERE id=24) b WHERE "
                + "a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM p WHERE id=24) a WHERE a.v IN (SELECT w FROM (SELECT w "
                + "FROM p WHERE id=24))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=24) INTERSECT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=24))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=24) EXCEPT SELECT w FROM "
                + "(SELECT w FROM p WHERE id=24))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT v FROM p WHERE id=24) UNION SELECT w FROM "
                + "(SELECT w FROM p WHERE id=24))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT v FROM p WHERE id=24) UNION ALL "
                + "SELECT w FROM (SELECT w FROM p WHERE id=24))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT v FROM p WHERE id=24) UNION ALL"
                + " SELECT w FROM (SELECT w FROM p WHERE id=24)) GROUP BY x)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0.30000000000000004') v) a JOIN (SELECT "
                + "PARSE_JSON('0.3') w) b ON a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0.30000000000000004') v) a, (SELECT "
                + "PARSE_JSON('0.3') w) b WHERE a.v = b.w",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('0.30000000000000004') v) a WHERE a.v IN (SELECT w "
                + "FROM (SELECT PARSE_JSON('0.3') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0.30000000000000004') v) INTERSECT "
                + "SELECT w FROM (SELECT PARSE_JSON('0.3') w))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0.30000000000000004') v) EXCEPT "
                + "SELECT w FROM (SELECT PARSE_JSON('0.3') w))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('0.30000000000000004') v) UNION "
                + "SELECT w FROM (SELECT PARSE_JSON('0.3') w))",
                "2"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT v x FROM (SELECT PARSE_JSON('0.30000000000000004') v) "
                + "UNION ALL SELECT w FROM (SELECT PARSE_JSON('0.3') w))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT v x FROM (SELECT "
                + "PARSE_JSON('0.30000000000000004') v) UNION ALL SELECT w FROM (SELECT PARSE_JSON('0.3') w)) "
                + "GROUP BY x)",
                "2"},
        });
    }

    @Test
    public void mixedBranches() {
        engine.execute("CREATE TABLE n(k NUMBER(10,0), s VARCHAR)");
        engine.execute("INSERT INTO n VALUES (1, '1'), (2, 'abc')");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v UNION ALL SELECT PARSE_JSON('1.0')) a JOIN n "
                + "ON a.v = n.k",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(1::FLOAT) v) a JOIN n ON a.v = n.k",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('\"abc\"') v) a JOIN n ON a.v = n.s",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('\"1\"') v) a JOIN n ON a.v = n.k",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') v) a JOIN n ON a.v = n.s",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('\"ABC\"') v) a JOIN n ON a.v = n.s",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT TO_VARIANT(TRUE) v) a JOIN (SELECT TRUE b) x ON a.v = x.b",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT 2.500::NUMBER(10,3)::VARIANT v) a JOIN (SELECT 2.5 b) x ON a.v "
                + "= x.b",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') x UNION SELECT 1)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('1') x INTERSECT SELECT 1.0::FLOAT)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('\"1\"') x INTERSECT SELECT 1)",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT DISTINCT x FROM (SELECT PARSE_JSON('1') x UNION ALL SELECT 1))",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT PARSE_JSON('1') x UNION ALL SELECT 1.0::FLOAT) "
                + "GROUP BY x)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT x FROM (SELECT 2.500::NUMBER(10,3)::VARIANT x UNION ALL SELECT "
                + "TO_VARIANT(2.5::FLOAT)) GROUP BY x)",
                "1"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT TO_VARIANT(1::FLOAT) x UNION ALL SELECT "
                + "TO_VARIANT(1.000::NUMBER(5,3)))",
                "1"},
        });
    }
}
