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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A query reads a virtual column by computing it as it reads the row, from the expression as DESCRIBE TABLE tells it,
 * at that expression's type (live-verified): {@code b NUMBER(7,6) AS (1 / a)} is told {@code (CAST(CAST(1 AS
 * NUMBER(7,6)) AS NUMBER(7,6))) / A} and reads as a NUMBER(13,12), in a filter, a sort, a grouping, a copy and a view
 * as in a select list, while DESCRIBE TABLE, SHOW COLUMNS, GET_DDL and INFORMATION_SCHEMA keep the declared type and a
 * CREATE TABLE ... LIKE refuses a column its type no longer takes. A text of a TIMESTAMP_LTZ, or of a value an output
 * format spells, reads in the reading session.
 */
public class VirtualColumnReadTimeTest extends MeasuredCellsTestSupport {

    /** Puts back the session parameters a test moved. */
    @AfterEach
    public void restoreSession() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
        engine.execute("ALTER SESSION UNSET DATE_OUTPUT_FORMAT");
        engine.execute("ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT");
        engine.execute("ALTER SESSION UNSET TIMESTAMP_LTZ_OUTPUT_FORMAT");
    }

    @Test
    public void numberReadsAtTheToldExpressionsType() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("CREATE OR REPLACE TABLE vr1 (a INT, b NUMBER(7,6) AS (1 / a))");
        engine.execute("INSERT INTO vr1 (a) VALUES (2), (3), (4)");
        assertAnswer("2|0.500000000000 ;; 3|0.333333333333 ;; 4|0.250000000000",
            "SELECT a, b FROM vr1 ORDER BY a");
        assertAnswer("NUMBER(13,12)[SB8]",
            "SELECT SYSTEM$TYPEOF(b) FROM vr1 LIMIT 1");
        assertAnswer("1.000000000000|1.500000000000 ;; 0.666666666666|1.333333333333 ;; "
                + "0.500000000000|1.250000000000",
            "SELECT b * 2, b + 1 FROM vr1 ORDER BY a");
        assertAnswer("2 ;; 3",
            "SELECT a FROM vr1 WHERE b > 0.3 ORDER BY a");
        assertAnswer("4",
            "SELECT a FROM vr1 WHERE b = 0.25 ORDER BY a");
        assertAnswer("4 ;; 3 ;; 2",
            "SELECT a FROM vr1 ORDER BY b");
        assertAnswer("0.250000000000|1 ;; 0.333333333333|1 ;; 0.500000000000|1",
            "SELECT b, COUNT(*) FROM vr1 GROUP BY b ORDER BY b");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(7,6)|VIRTUAL|Y|null|N|N|null|(CAST(CAST(1 AS NUMBER(7,6)) AS NUMBER(7,6))) / "
                + "A|null|null|null|null",
            "DESCRIBE TABLE vr1");
        assertAnswer("VR1|TEST_SCHEMA|A|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true}"
                + "|true||COLUMN|||TEST_DB||null|null ;; "
                + "VR1|TEST_SCHEMA|B|{\"type\":\"FIXED\",\"precision\":7,\"scale\":6,\"nullable\":true}|"
                + "true||VIRTUAL_COLUMN|(CAST(CAST(1 AS NUMBER(7,6)) AS NUMBER(7,6))) / "
                + "A||TEST_DB||null|null",
            "SHOW COLUMNS IN TABLE vr1");
        assertAnswer("create or replace TABLE VR1 ( | \tA NUMBER(38,0), | \tB NUMBER(7,6) AS ((CAST(CAST(1 AS"
                + " NUMBER(7,6)) AS NUMBER(7,6))) / A) | );",
            "SELECT GET_DDL('TABLE', 'VR1')");
        assertAnswer("A|NUMBER|38|0 ;; B|NUMBER|7|6",
            "SELECT column_name, data_type, numeric_precision, numeric_scale FROM "
                + "information_schema.columns WHERE table_name = 'VR1' ORDER BY ordinal_position");
        engine.execute("CREATE OR REPLACE TABLE vr1c AS SELECT b FROM vr1");
        assertAnswer("B|NUMBER(13,12)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE vr1c");
        assertAnswer("0.250000000000 ;; 0.333333333333 ;; 0.500000000000",
            "SELECT b FROM vr1c ORDER BY b");
        engine.execute("CREATE OR REPLACE TABLE vr1d (b NUMBER(7,6))");
        engine.execute("INSERT INTO vr1d SELECT b FROM vr1");
        assertAnswer("0.250000 ;; 0.333333 ;; 0.500000",
            "SELECT b FROM vr1d ORDER BY b");
        engine.execute("UPDATE vr1 SET a = 8 WHERE a = 2");
        assertAnswer("3|0.333333333333 ;; 4|0.250000000000 ;; 8|0.125000000000",
            "SELECT a, b FROM vr1 ORDER BY a");
        assertAnswer("0.333333333333 ;; 0.250000000000 ;; 0.125000000000",
            "SELECT b::VARCHAR FROM vr1 ORDER BY a");
        engine.execute("CREATE OR REPLACE VIEW vr1v AS SELECT b FROM vr1");
        assertAnswer("B|NUMBER(13,12)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW vr1v");
        assertAnswer("0.125000000000 ;; 0.250000000000 ;; 0.333333333333",
            "SELECT b FROM vr1v ORDER BY b");
        engine.execute("CREATE OR REPLACE TABLE vr1k CLONE vr1");
        assertAnswer("0.125000000000|NUMBER(13,12)[SB8] ;; 0.250000000000|NUMBER(13,12)[SB8] ;; "
                + "0.333333333333|NUMBER(13,12)[SB8]",
            "SELECT b, SYSTEM$TYPEOF(b) FROM vr1k ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Data type of virtual "
                + "column does not match the data type of its expression for column 'B'. Expected data "
                + "type 'NUMBER(7,6)', found 'NUMBER(13,12)'",
            "CREATE OR REPLACE TABLE vr1l LIKE vr1");
    }

    @Test
    public void addedColumnReadsAtTheToldExpressionsType() {
        engine.execute("CREATE OR REPLACE TABLE vr5 (a INT, b INT AS (a + 1))");
        engine.execute("INSERT INTO vr5 (a) VALUES (1)");
        engine.execute("ALTER TABLE vr5 ADD COLUMN c NUMBER(7,6) AS (1 / a)");
        assertAnswer("2|1.000000000000|NUMBER(13,12)[SB8]",
            "SELECT b, c, SYSTEM$TYPEOF(c) FROM vr5");
        assertAnswer("1|2|1.000000000000",
            "SELECT * FROM vr5");
        assertAnswer("1.000000000000",
            "SELECT w.c FROM vr5 w JOIN vr5 x ON x.c = w.c");
        assertAnswer("1.000000000000",
            "SELECT c FROM (SELECT * FROM vr5)");
    }

    @Test
    public void eachToldExpressionTypesItsRead() {
        engine.execute("CREATE OR REPLACE TABLE vr2 (a INT, s VARCHAR(3), n NUMBER(10,2), f FLOAT, c "
                + "NUMBER(38,6) AS (a / 3), d NUMBER(38,2) AS (n + 1), e NUMBER(38,1) AS (a + 1.5), g "
                + "FLOAT AS (a + f), h VARCHAR AS (s), k VARCHAR(10) AS (s || 'x'), m NUMBER(16,8) AS (n "
                + "/ a), p NUMBER(38,5) AS (s * 2))");
        engine.execute("INSERT INTO vr2 (a, s, n, f) VALUES (7, '12', 3.25, 0.5)");
        assertAnswer("2.333333|4.25|8.5|7.5|12|12x|0.464285714286|24.00000",
            "SELECT c, d, e, g, h, k, m, p FROM vr2");
        assertAnswer("NUMBER(38,6)[SB4]|NUMBER(11,2)[SB2]|NUMBER(38,1)[SB1]|FLOAT[DOUBLE]|VARCHAR(3)[L"
                + "OB]|VARCHAR(4)[LOB]|NUMBER(20,12)[SB8]",
            "SELECT SYSTEM$TYPEOF(c), SYSTEM$TYPEOF(d), SYSTEM$TYPEOF(e), SYSTEM$TYPEOF(g), "
                + "SYSTEM$TYPEOF(h), SYSTEM$TYPEOF(k), SYSTEM$TYPEOF(m) FROM vr2");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "S|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "N|NUMBER(10,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "F|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|NUMBER(38,6)|VIRTUAL|Y|null|N|N|null|SCALED_ROUND_INT_DIVIDE(A, 3)|null|null|nu"
                + "ll|null ;; D|NUMBER(38,2)|VIRTUAL|Y|null|N|N|null|N + (CAST(CAST(1 AS NUMBER(10,2)) AS"
                + " NUMBER(10,2)))|null|null|null|null ;; E|NUMBER(38,1)|VIRTUAL|Y|null|N|N|null|(CAST(A "
                + "AS NUMBER(38,1))) + 1.5|null|null|null|null ;; G|FLOAT|VIRTUAL|Y|null|N|N|null|(CAST(A"
                + " AS FLOAT)) + F|null|null|null|null ;; "
                + "H|VARCHAR(16777216)|VIRTUAL|Y|null|N|N|null|S|null|null|null|null ;; "
                + "K|VARCHAR(10)|VIRTUAL|Y|null|N|N|null|S || 'x'|null|null|null|null ;; "
                + "M|NUMBER(16,8)|VIRTUAL|Y|null|N|N|null|(CAST(CAST(N AS NUMBER(16,8)) AS NUMBER(16,8)))"
                + " / A|null|null|null|null ;; "
                + "P|NUMBER(38,5)|VIRTUAL|Y|null|N|N|null|(CAST(TO_NUMBER(S, 18, 5) AS NUMBER(18,5))) * "
                + "2|null|null|null|null",
            "DESCRIBE TABLE vr2");
        engine.execute("CREATE OR REPLACE TABLE vr2c AS SELECT * FROM vr2");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "S|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "N|NUMBER(10,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "F|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|NUMBER(38,6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "D|NUMBER(11,2)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "E|NUMBER(38,1)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "G|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "H|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "K|VARCHAR(4)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "M|NUMBER(20,12)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "P|NUMBER(19,5)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE vr2c");
    }

    @Test
    public void likeCopiesAColumnItsTypeTakes() {
        engine.execute("CREATE OR REPLACE TABLE vr6 (a INT, s VARCHAR(3), h VARCHAR AS (s), d NUMBER(38,2) AS "
                + "(a + 1.25))");
        engine.execute("CREATE OR REPLACE TABLE vr6l LIKE vr6");
        engine.execute("INSERT INTO vr6l (a, s) VALUES (4, 'x')");
        assertAnswer("x|5.25|VARCHAR(3)[LOB]|NUMBER(38,2)[SB2]",
            "SELECT h, d, SYSTEM$TYPEOF(h), SYSTEM$TYPEOF(d) FROM vr6l");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "S|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "H|VARCHAR(16777216)|VIRTUAL|Y|null|N|N|null|S|null|null|null|null ;; "
                + "D|NUMBER(38,2)|VIRTUAL|Y|null|N|N|null|(CAST(A AS NUMBER(38,2))) + "
                + "1.25|null|null|null|null",
            "DESCRIBE TABLE vr6l");
    }

    @Test
    public void textOfATimestampReadsInTheReadingSession() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("CREATE OR REPLACE TABLE vr3 (a TIMESTAMP_LTZ, t VARCHAR AS (a::VARCHAR), d DATE, e "
                + "VARCHAR AS (d::VARCHAR), f FLOAT, g VARCHAR AS (f::VARCHAR), h VARCHAR AS "
                + "(TO_VARCHAR(a, 'YYYY-MM-DD HH24:MI TZH')))");
        engine.execute("INSERT INTO vr3 (a, d, f) VALUES ('2024-01-01 10:00:00 +00:00', '2024-02-03', 1.5)");
        assertAnswer("2024-01-01 10:00:00.000 Z|2024-02-03|1.5|2024-01-01 10:00 Z",
            "SELECT t, e, g, h FROM vr3");
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/New_York'");
        assertAnswer("2024-01-01 05:00:00.000 -0500|2024-02-03|1.5|2024-01-01 05:00 -05",
            "SELECT t, e, g, h FROM vr3");
        assertAnswer("2024-02-03",
            "SELECT d FROM vr3 WHERE t LIKE '%-0500'");
        engine.execute("CREATE OR REPLACE TABLE vr3c AS SELECT t FROM vr3");
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertAnswer("2024-01-01 05:00:00.000 -0500",
            "SELECT t FROM vr3c");
        assertAnswer("2024-01-01 10:00:00.000 Z",
            "SELECT t FROM vr3");
        engine.execute("ALTER SESSION SET TIMESTAMP_LTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI'");
        assertAnswer("2024-01-01 10:00",
            "SELECT t FROM vr3");
        engine.execute("ALTER SESSION UNSET TIMESTAMP_LTZ_OUTPUT_FORMAT");
        engine.execute("CREATE OR REPLACE TABLE vr4 (a TIMESTAMP_NTZ, t TIMESTAMP_LTZ AS (a::TIMESTAMP_LTZ))");
        engine.execute("INSERT INTO vr4 (a) VALUES ('2024-01-01 10:00:00')");
        assertAnswer("2024-01-01 10:00:00.000 Z",
            "SELECT t::VARCHAR FROM vr4");
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/New_York'");
        assertAnswer("2024-01-01 10:00:00.000 -0500",
            "SELECT t::VARCHAR FROM vr4");
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        engine.execute("CREATE OR REPLACE TABLE vr7 (a TIMESTAMP_LTZ, t VARCHAR AS (a::VARCHAR))");
        engine.execute("INSERT INTO vr7 (a) VALUES ('2024-01-01 10:00:00 +00:00')");
        assertAnswer("2024-01-01 19:00:00.000 +0900",
            "SELECT t FROM vr7 WHERE t = '2024-01-01 19:00:00.000 +0900'");
        assertAnswer("2024-01-01 19:00:00.000 +0900|1",
            "SELECT t, COUNT(*) FROM vr7 GROUP BY t");
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertAnswer("2024-01-01 10:00:00.000 Z",
            "SELECT t FROM vr7");
    }

    @Test
    public void textReadsInTheSessionsOutputFormats() {
        engine.execute("CREATE OR REPLACE TABLE vr8 (a FLOAT, g VARCHAR AS (a::VARCHAR), d DATE, e VARCHAR AS "
                + "(d::VARCHAR), t TIMESTAMP_NTZ, u VARCHAR AS (t::VARCHAR))");
        engine.execute("INSERT INTO vr8 (a, d, t) VALUES (1.5, '2024-02-03', '2024-01-01 10:00:00')");
        engine.execute("ALTER SESSION SET DATE_OUTPUT_FORMAT = 'DD/MM/YYYY'");
        engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24'");
        assertAnswer("1.5|03/02/2024|2024-01-01 10",
            "SELECT g, e, u FROM vr8");
        engine.execute("ALTER SESSION UNSET DATE_OUTPUT_FORMAT");
        engine.execute("ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT");
    }

    @Test
    public void rowFaultIsRaisedWhereTheColumnIsRead() {
        engine.execute("CREATE OR REPLACE TABLE vr9 (a INT, b NUMBER(38,6) AS (a / 0))");
        engine.execute("INSERT INTO vr9 (a) VALUES (1)");
        assertAnswer("1",
            "SELECT a FROM vr9");
        assertAnswer("REFUSED Division by zero",
            "SELECT b FROM vr9");
    }
}
