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
 * The GETNEXTVAL table function (live-verified): {@code TABLE(GETNEXTVAL(seq))} answers one row, column NEXTVAL
 * NUMBER(19,0) [SB8], for every row of the relations written before it, each a value of its own, an ON or a CROSS
 * JOIN changing nothing; written first it joins a row of its own, so {@code SELECT *} answers COLUMN1 (an untyped
 * NULL) beside NEXTVAL while its alias reaches NEXTVAL alone. The sequence is named as a name — plain, qualified,
 * quoted, {@code IDENTIFIER('…')}, under any argument name — and a name that is no sequence is an invalid identifier
 * at the argument.
 */
public class GetnextvalTest extends MeasuredCellsTestSupport {

    @Test
    public void getnextval() {
        engine.execute("CREATE OR REPLACE TABLE gt (a INT)");
        engine.execute("INSERT INTO gt VALUES (1),(2),(3)");
        engine.execute("CREATE OR REPLACE VIEW gv AS SELECT 1 x");
        engine.execute("CREATE OR REPLACE TABLE gins (n INT)");
        engine.execute("CREATE OR REPLACE SEQUENCE q1");
        engine.execute("CREATE OR REPLACE SEQUENCE q2");
        engine.execute("CREATE OR REPLACE SEQUENCE q3");
        engine.execute("CREATE OR REPLACE SEQUENCE q4");
        engine.execute("CREATE OR REPLACE SEQUENCE q5");
        engine.execute("CREATE OR REPLACE SEQUENCE q6");
        engine.execute("CREATE OR REPLACE SEQUENCE q7");
        engine.execute("CREATE OR REPLACE SEQUENCE q8");
        engine.execute("CREATE OR REPLACE SEQUENCE q9");
        engine.execute("CREATE OR REPLACE SEQUENCE q10");
        engine.execute("CREATE OR REPLACE SEQUENCE q11");
        engine.execute("CREATE OR REPLACE SEQUENCE q12");
        engine.execute("CREATE OR REPLACE SEQUENCE q13");
        engine.execute("CREATE OR REPLACE SEQUENCE q14");
        engine.execute("CREATE OR REPLACE SEQUENCE q15");
        engine.execute("CREATE OR REPLACE SEQUENCE q16");
        engine.execute("CREATE OR REPLACE SEQUENCE q17");
        engine.execute("CREATE OR REPLACE SEQUENCE q18");
        engine.execute("CREATE OR REPLACE SEQUENCE q19");
        engine.execute("CREATE OR REPLACE SEQUENCE q20");
        engine.execute("CREATE OR REPLACE SEQUENCE q21");
        engine.execute("CREATE OR REPLACE SEQUENCE q22");
        engine.execute("CREATE OR REPLACE SEQUENCE q23");
        engine.execute("CREATE OR REPLACE SEQUENCE q24");
        engine.execute("CREATE OR REPLACE SEQUENCE q25");
        engine.execute("CREATE OR REPLACE SEQUENCE q26");
        engine.execute("CREATE OR REPLACE SEQUENCE q27");
        engine.execute("CREATE OR REPLACE SEQUENCE q28");
        engine.execute("CREATE OR REPLACE SEQUENCE q29");
        engine.execute("CREATE OR REPLACE SEQUENCE q30");
        engine.execute("CREATE OR REPLACE SEQUENCE qn START = -3 INCREMENT = -2");
        engine.execute("CREATE OR REPLACE SEQUENCE qs START = 10 INCREMENT = 5");
        engine.execute("CREATE OR REPLACE SEQUENCE \"MixQ\"");
        engine.execute("INSERT INTO gins SELECT nextval FROM gt, TABLE(GETNEXTVAL(q21))");
        assertCells(new String[][] {
            {"SELECT nextval FROM TABLE(GETNEXTVAL(q1))",
                "1"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(q1))",
                "2"},
            {"SELECT q1.NEXTVAL",
                "3"},
            {"SELECT column1 FROM TABLE(GETNEXTVAL(q2))",
                "null"},
            {"SELECT COUNT(*) FROM TABLE(GETNEXTVAL(q2))",
                "1"},
            {"SELECT SYSTEM$TYPEOF(nextval) FROM TABLE(GETNEXTVAL(q3))",
                "NUMBER(19,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(column1) FROM TABLE(GETNEXTVAL(q3))",
                "NULL[LOB]"},
            {"SELECT OBJECT_CONSTRUCT_KEEP_NULL(*)::VARCHAR FROM TABLE(GETNEXTVAL(q4))",
                "{\"COLUMN1\":null,\"NEXTVAL\":1}"},
            {"SELECT OBJECT_CONSTRUCT_KEEP_NULL(s.*)::VARCHAR FROM TABLE(GETNEXTVAL(q4)) s",
                "{\"NEXTVAL\":2}"},
            {"SELECT s.nextval FROM TABLE(GETNEXTVAL(q5)) s",
                "1"},
            {"SELECT s.v FROM TABLE(GETNEXTVAL(q5)) s(v)",
                "2"},
            {"SELECT \"NEXTVAL\" FROM TABLE(GETNEXTVAL(q5))",
                "3"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(qs))",
                "10"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(qs))",
                "15"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(qn))",
                "-3"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(qn))",
                "-5"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(test_db.test_schema.q6))",
                "1"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(test_schema.q6))",
                "2"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(IDENTIFIER('q6')))",
                "3"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(IDENTIFIER('test_schema.q6')))",
                "4"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(\"Q6\"))",
                "5"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(\"MixQ\"))",
                "1"},
            {"SELECT nextval FROM TABLE(getnextval(q6))",
                "6"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(seq => q6))",
                "7"},
            {"SELECT nextval FROM TABLE(GETNEXTVAL(whatever => q6))",
                "8"},
            {"SELECT LISTAGG(gt.a || ':' || s.nextval, ',') WITHIN GROUP (ORDER BY gt.a) FROM gt, "
                + "TABLE(GETNEXTVAL(q7)) s",
                "1:1,2:2,3:3"},
            {"SELECT q7.NEXTVAL",
                "4"},
            {"SELECT LISTAGG(gt.a || ':' || s.nextval, ',') WITHIN GROUP (ORDER BY gt.a) FROM gt JOIN "
                + "TABLE(GETNEXTVAL(q8)) s ON TRUE",
                "1:1,2:2,3:3"},
            {"SELECT q8.NEXTVAL",
                "4"},
            {"SELECT LISTAGG(gt.a || ':' || s.nextval, ',') WITHIN GROUP (ORDER BY gt.a) FROM gt CROSS "
                + "JOIN TABLE(GETNEXTVAL(q9)) s",
                "1:1,2:2,3:3"},
            {"SELECT q9.NEXTVAL",
                "4"},
            {"SELECT COUNT(DISTINCT s.nextval) FROM gt x, gt y, TABLE(GETNEXTVAL(q10)) s",
                "9"},
            {"SELECT q10.NEXTVAL",
                "10"},
            {"SELECT COUNT(DISTINCT s.nextval) FROM TABLE(GETNEXTVAL(q11)) s, gt",
                "1"},
            {"SELECT q11.NEXTVAL",
                "2"},
            {"SELECT COUNT(*) FROM gt, TABLE(GETNEXTVAL(q12)) s WHERE gt.a > 1",
                "2"},
            {"SELECT q12.NEXTVAL",
                "3"},
            {"SELECT s.nextval FROM gt, TABLE(GETNEXTVAL(q13)) s ORDER BY gt.a LIMIT 1",
                "1"},
            {"SELECT q13.NEXTVAL",
                "4"},
            {"SELECT COUNT(*) FROM (SELECT * FROM gt WHERE 1 = 0) e, TABLE(GETNEXTVAL(q14)) s",
                "0"},
            {"SELECT q14.NEXTVAL",
                "1"},
            {"SELECT (a.nextval = b.nextval)::VARCHAR FROM TABLE(GETNEXTVAL(q15)) a, TABLE(GETNEXTVAL(q15)) b",
                "false"},
            {"SELECT LISTAGG(n, ',') WITHIN GROUP (ORDER BY n) FROM (SELECT nextval n FROM "
                + "TABLE(GETNEXTVAL(q16)) UNION ALL SELECT nextval FROM TABLE(GETNEXTVAL(q16)))",
                "1,2"},
            {"WITH c AS (SELECT nextval FROM TABLE(GETNEXTVAL(q17))) SELECT nextval FROM c",
                "1"},
            {"SELECT (SELECT nextval FROM TABLE(GETNEXTVAL(q18)))",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(GETNEXTVAL(q19)) GROUP BY nextval",
                "1"},
            {"SELECT LISTAGG(v.column1 || ':' || s.nextval, ',') WITHIN GROUP (ORDER BY v.column1) FROM "
                + "(VALUES (1), (2)) v, TABLE(GETNEXTVAL(q20)) s",
                "1:1,2:2"},
            {"SELECT LISTAGG(n, ',') WITHIN GROUP (ORDER BY n) FROM gins",
                "1,2,3"},
            {"SELECT COUNT(*) FROM gt, LATERAL GETNEXTVAL(q22)",
                "3"},
            {"SELECT * FROM TABLE(GETNEXTVAL(nosuch))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(nosuchdb.public.nosuch))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier "
                + "'NOSUCHDB.PUBLIC.NOSUCH'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(\"nosuch\"))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier '\"nosuch\"'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(IDENTIFIER('nosuch')))",
                "REFUSED SQL compilation error: error line 1 at position 42 | invalid identifier 'NOSUCH'"},
            {"SELECT *\nFROM TABLE( GETNEXTVAL(  nosuch ))",
                "REFUSED SQL compilation error: error line 2 at position 25 | invalid identifier 'NOSUCH'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(gt))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'GT'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(gv))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'GV'"},
            {"SELECT * FROM TABLE(GETNEXTVAL())",
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid number of arguments for"
                + " [GETNEXTVAL], expected 1, got 0"},
            {"SELECT * FROM TABLE(GETNEXTVAL(q23, q24))",
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid number of arguments for"
                + " [GETNEXTVAL], expected 1, got 2"},
            {"SELECT * FROM gt, TABLE(GETNEXTVAL(q23)) s(v, w)",
                "REFUSED SQL compilation error: | Mismatch between number of columns produced by 'S' and the "
                + "number of aliases specified."},
            {"SELECT q23.NEXTVAL",
                "1"},
            {"SELECT * FROM GETNEXTVAL(q25)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"SELECT COUNT(*) FROM gt, LATERAL TABLE(GETNEXTVAL(q25))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'TABLE'."},
        });
    }
}
