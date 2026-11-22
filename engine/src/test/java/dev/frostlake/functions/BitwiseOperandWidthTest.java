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
 * The widths GETBIT and the bitwise functions over a VARIANT declare (live-verified): GETBIT declares what BITAND
 * does over the value and the bit's place — the wider argument's whole digits, two at the least, thirteen for a
 * text — and a VARIANT counts as a FLOAT does, for nothing in BITAND, BITXOR, BITNOT, BITSHIFTRIGHT and GETBIT
 * and for eighteen in BITOR. A held VARIANT is read through a 64-bit integer, which its storage tag shows, where an
 * element read out of a value keeps the declared width's tag.
 */
public class BitwiseOperandWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void getbitAndVariantOperandsDeclareTheirWidth() {
        engine.execute("CREATE OR REPLACE TABLE bt (n5 NUMBER(5,0), n10 NUMBER(10,0), n18 NUMBER(18,0), n20 "
            + "NUMBER(20,0), n35 NUMBER(35,0), i INT, d NUMBER(10,2), f FLOAT, s VARCHAR, n1 NUMBER(1,0), v"
            + " VARIANT, o OBJECT, w VARIANT, a ARRAY) AS SELECT 7, 7, 7, 7, 7, 7, 7.5, 2.5, '7', 7, "
            + "7::VARIANT, OBJECT_CONSTRUCT('a', 7), PARSE_JSON('{\"a\": 7}'), ARRAY_CONSTRUCT(7)");
        engine.execute("CREATE OR REPLACE TABLE x3 AS SELECT GETBIT(n5, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x32 AS SELECT GETBIT(n10, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x33 AS SELECT GETBIT(n18, 3) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x34 AS SELECT GETBIT(n20, 3) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x35 AS SELECT GETBIT(i, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x36 AS SELECT GETBIT(3, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x37 AS SELECT GETBIT(123456, 5) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x38 AS SELECT GETBIT(d, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x39 AS SELECT GETBIT(f, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x40 AS SELECT GETBIT(n1, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x41 AS SELECT GETBIT(s, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x42 AS SELECT GETBIT(n5, n10) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x43 AS SELECT GETBIT(-3, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x44 AS SELECT GETBIT(v, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x51 AS SELECT BITAND(v, 3) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x52 AS SELECT BITNOT(v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x59 AS SELECT GETBIT(NULL, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x62 AS SELECT GETBIT(n5 + 1, 0) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x67 AS SELECT GETBIT(n5, s) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x68 AS SELECT GETBIT(s, s) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x69 AS SELECT GETBIT(s, n5) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x70 AS SELECT GETBIT(n35, s) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x71 AS SELECT GETBIT(i, s) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x74 AS SELECT GETBIT(1, 100) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x75 AS SELECT GETBIT(1.5, 2) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x76 AS SELECT GETBIT(123.5, 2) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x77 AS SELECT GETBIT(n5, 1.5) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x78 AS SELECT GETBIT(n5, d) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x79 AS SELECT GETBIT(n5, f) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x80 AS SELECT GETBIT(f, f) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x81 AS SELECT GETBIT(n5, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x82 AS SELECT GETBIT(v, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x83 AS SELECT GETBIT(n5, NULL) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x84 AS SELECT GETBIT(NULL, NULL) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x85 AS SELECT GETBIT(n1, n1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x86 AS SELECT GETBIT(n5, i) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x87 AS SELECT BITAND(v, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x88 AS SELECT BITAND(n5, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x89 AS SELECT BITAND(v, n5) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x90 AS SELECT BITXOR(v, 3) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x91 AS SELECT BITXOR(n10, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x92 AS SELECT BITOR(v, 3) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x93 AS SELECT BITOR(v, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x94 AS SELECT BITOR(n5, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x95 AS SELECT BITSHIFTRIGHT(v, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x96 AS SELECT BITSHIFTRIGHT(n5, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x97 AS SELECT BITSHIFTLEFT(v, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x98 AS SELECT BITNOT(o:a) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x99 AS SELECT BITAND(o:a, n5) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x100 AS SELECT GETBIT(o:a, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x101 AS SELECT BITAND(v, s) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x102 AS SELECT BITAND(s, v) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x103 AS SELECT BITAND(v, f) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x104 AS SELECT BITOR(v, f) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x105 AS SELECT BITNOT(w:a) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x110 AS SELECT BITNOT(v::VARIANT) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x111 AS SELECT BITAND(w:a, n5) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x112 AS SELECT GETBIT(w:a, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x114 AS SELECT BITSHIFTRIGHT(o:a, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x115 AS SELECT BITOR(o:a, 1) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x116 AS SELECT BITXOR(w:a, w:a) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x117 AS SELECT BITNOT(IFF(n5 > 0, v, v)) AS c FROM bt");
        engine.execute("CREATE OR REPLACE TABLE x118 AS SELECT BITSHIFTRIGHT(n5, o:a) AS c FROM bt");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, 3)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, n5)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(n5)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, 0)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT GETBIT(n5, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X3'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTLEFT(n5, 1)) FROM bt",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTRIGHT(n5, 1)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(3, 5)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(3)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(f, 3)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITOR(n5, 3)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(n5, 3)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITOR(n5, n5)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(n5, n10)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, n10)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n10, n5)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n18, n5)) FROM bt",
                "NUMBER(18,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n20, n5)) FROM bt",
                "NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(i, n5)) FROM bt",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, -3)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, 123456)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, 12345678901234567890)) FROM bt",
                "NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITOR(n5, 123456)) FROM bt",
                "NUMBER(7,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(n5, 123456)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(n10)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(n18)) FROM bt",
                "NUMBER(18,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(n20)) FROM bt",
                "NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(i)) FROM bt",
                "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(-3)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(d)) FROM bt",
                "NUMBER(8,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(f)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(n1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(s)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n10, 0)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT GETBIT(n10, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X32'",
                "NUMBER(10,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n18, 3)) FROM bt",
                "NUMBER(18,0)[SB8]"},
            {"SELECT GETBIT(n18, 3) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X33'",
                "NUMBER(18,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n20, 3)) FROM bt",
                "NUMBER(20,0)[SB16]"},
            {"SELECT GETBIT(n20, 3) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X34'",
                "NUMBER(20,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(i, 0)) FROM bt",
                "NUMBER(38,0)[SB16]"},
            {"SELECT GETBIT(i, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X35'",
                "NUMBER(38,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(3, 0)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(3, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X36'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(123456, 5)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT GETBIT(123456, 5) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X37'",
                "NUMBER(6,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(d, 0)) FROM bt",
                "NUMBER(8,0)[SB4]"},
            {"SELECT GETBIT(d, 0) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X38'",
                "NUMBER(8,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(f, 0)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT GETBIT(f, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X39'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n1, 0)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(n1, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X40'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(s, 0)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT GETBIT(s, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X41'",
                "NUMBER(13,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, n10)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT GETBIT(n5, n10) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X42'",
                "NUMBER(10,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(-3, 1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(-3, 1) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X43'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(v, 0)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT GETBIT(v, 0) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X44'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(d, 3)) FROM bt",
                "NUMBER(8,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(d, d)) FROM bt",
                "NUMBER(8,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n1, n1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n1, 1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(s, 3)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, s)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(v, 3)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITAND(v, 3) FROM bt",
                "3"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X51'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(v)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITNOT(v) FROM bt",
                "-8"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X52'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITOR(d, 3)) FROM bt",
                "NUMBER(9,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(d, n5)) FROM bt",
                "NUMBER(8,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(f, 3)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITOR(f, 3)) FROM bt",
                "NUMBER(19,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, NULL)) FROM bt",
                "NUMBER(5,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(NULL)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(NULL, 1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(NULL, 1) FROM bt",
                "null"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X59'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5 + 1, 3)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(n5 * 2)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5 + 1, 0)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT GETBIT(n5 + 1, 0) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X62'",
                "NUMBER(6,0)"},
            {"SELECT SYSTEM$TYPEOF(BIT_AND(n5, 3)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BIT_NOT(n5)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(BIT_XOR(n5, n10)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BIT_OR(n5, 3)) FROM bt",
                "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, s)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT GETBIT(n5, s) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X67'",
                "NUMBER(13,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(s, s)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT GETBIT(s, s) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X68'",
                "NUMBER(13,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(s, n5)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT GETBIT(s, n5) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X69'",
                "NUMBER(13,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n35, s)) FROM bt",
                "NUMBER(33,0)[SB16]"},
            {"SELECT GETBIT(n35, s) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X70'",
                "NUMBER(33,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(i, s)) FROM bt",
                "NUMBER(33,0)[SB16]"},
            {"SELECT GETBIT(i, s) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X71'",
                "NUMBER(33,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(i, s)) FROM bt",
                "NUMBER(33,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n35, s)) FROM bt",
                "NUMBER(33,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(1, 100)) FROM bt",
                "NUMBER(3,0)[SB2]"},
            {"SELECT GETBIT(1, 100) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X74'",
                "NUMBER(3,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(1.5, 2)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(1.5, 2) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X75'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(123.5, 2)) FROM bt",
                "NUMBER(3,0)[SB2]"},
            {"SELECT GETBIT(123.5, 2) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X76'",
                "NUMBER(3,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, 1.5)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT GETBIT(n5, 1.5) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X77'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, d)) FROM bt",
                "NUMBER(8,0)[SB4]"},
            {"SELECT GETBIT(n5, d) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X78'",
                "NUMBER(8,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, f)) FROM bt",
                "NUMBER(5,0)[SB8]"},
            {"SELECT GETBIT(n5, f) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X79'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(f, f)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT GETBIT(f, f) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X80'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, v)) FROM bt",
                "NUMBER(5,0)[SB8]"},
            {"SELECT GETBIT(n5, v) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X81'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(v, v)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT GETBIT(v, v) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X82'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, NULL)) FROM bt",
                "NUMBER(5,0)[SB1]"},
            {"SELECT GETBIT(n5, NULL) FROM bt",
                "null"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X83'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(NULL, NULL)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(NULL, NULL) FROM bt",
                "null"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X84'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n1, n1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(n1, n1) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X85'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(n5, i)) FROM bt",
                "NUMBER(38,0)[SB16]"},
            {"SELECT GETBIT(n5, i) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X86'",
                "NUMBER(38,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(v, v)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITAND(v, v) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X87'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(n5, v)) FROM bt",
                "NUMBER(5,0)[SB8]"},
            {"SELECT BITAND(n5, v) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X88'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(v, n5)) FROM bt",
                "NUMBER(5,0)[SB8]"},
            {"SELECT BITAND(v, n5) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X89'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(v, 3)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITXOR(v, 3) FROM bt",
                "4"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X90'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(n10, v)) FROM bt",
                "NUMBER(10,0)[SB8]"},
            {"SELECT BITXOR(n10, v) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X91'",
                "NUMBER(10,0)"},
            {"SELECT SYSTEM$TYPEOF(BITOR(v, 3)) FROM bt",
                "NUMBER(19,0)[SB16]"},
            {"SELECT BITOR(v, 3) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X92'",
                "NUMBER(19,0)"},
            {"SELECT SYSTEM$TYPEOF(BITOR(v, v)) FROM bt",
                "NUMBER(19,0)[SB16]"},
            {"SELECT BITOR(v, v) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X93'",
                "NUMBER(19,0)"},
            {"SELECT SYSTEM$TYPEOF(BITOR(n5, v)) FROM bt",
                "NUMBER(19,0)[SB16]"},
            {"SELECT BITOR(n5, v) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X94'",
                "NUMBER(19,0)"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTRIGHT(v, 1)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITSHIFTRIGHT(v, 1) FROM bt",
                "3"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X95'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTRIGHT(n5, v)) FROM bt",
                "NUMBER(5,0)[SB8]"},
            {"SELECT BITSHIFTRIGHT(n5, v) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X96'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTLEFT(v, 1)) FROM bt",
                "NUMBER(38,0)[SB16]"},
            {"SELECT BITSHIFTLEFT(v, 1) FROM bt",
                "14"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X97'",
                "NUMBER(38,0)"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(o:a)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT BITNOT(o:a) FROM bt",
                "-8"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X98'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(o:a, n5)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT BITAND(o:a, n5) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X99'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(o:a, 1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(o:a, 1) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X100'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(v, s)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT BITAND(v, s) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X101'",
                "NUMBER(13,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(s, v)) FROM bt",
                "NUMBER(13,0)[SB8]"},
            {"SELECT BITAND(s, v) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X102'",
                "NUMBER(13,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(v, f)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITAND(v, f) FROM bt",
                "3"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X103'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITOR(v, f)) FROM bt",
                "NUMBER(19,0)[SB16]"},
            {"SELECT BITOR(v, f) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X104'",
                "NUMBER(19,0)"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(w:a)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT BITNOT(w:a) FROM bt",
                "-8"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X105'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(GET(o, 'a'))) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(TO_VARIANT(n5))) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(PARSE_JSON('7'))) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(a[0])) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(v::VARIANT)) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITNOT(v::VARIANT) FROM bt",
                "-8"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X110'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITAND(w:a, n5)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT BITAND(w:a, n5) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X111'",
                "NUMBER(5,0)"},
            {"SELECT SYSTEM$TYPEOF(GETBIT(w:a, 1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT GETBIT(w:a, 1) FROM bt",
                "1"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X112'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(n5::VARIANT)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTRIGHT(o:a, 1)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT BITSHIFTRIGHT(o:a, 1) FROM bt",
                "3"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X114'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITOR(o:a, 1)) FROM bt",
                "NUMBER(19,0)[SB16]"},
            {"SELECT BITOR(o:a, 1) FROM bt",
                "7"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X115'",
                "NUMBER(19,0)"},
            {"SELECT SYSTEM$TYPEOF(BITXOR(w:a, w:a)) FROM bt",
                "NUMBER(2,0)[SB1]"},
            {"SELECT BITXOR(w:a, w:a) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X116'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(IFF(n5 > 0, v, v))) FROM bt",
                "NUMBER(2,0)[SB8]"},
            {"SELECT BITNOT(IFF(n5 > 0, v, v)) FROM bt",
                "-8"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X117'",
                "NUMBER(2,0)"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTRIGHT(n5, o:a)) FROM bt",
                "NUMBER(5,0)[SB4]"},
            {"SELECT BITSHIFTRIGHT(n5, o:a) FROM bt",
                "0"},
            {"SELECT DATA_TYPE || '(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')' FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'X118'",
                "NUMBER(5,0)"},
        });
    }
}
