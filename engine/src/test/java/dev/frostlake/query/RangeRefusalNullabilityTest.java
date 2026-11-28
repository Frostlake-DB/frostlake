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
 * The nullability a range refusal spells, {@code {nullable}} or {@code {not null}}, and which fault a select list
 * reports first, measured against the account: the refused value's own judgement (a literal, a cast of one, a VALUES
 * column no row of which can be NULL, a derived relation's column judged so are not null; NULLIF, a TRY_ function,
 * a scalar subquery, an aggregate, a window function and a side an outer join extends are nullable), for a cast, a
 * rounding function and a constant operand an addition rescales; and faults reported item by item over each batch of
 * 256 rows.
 */
public class RangeRefusalNullabilityTest extends MeasuredCellsTestSupport {

    @Test
    public void valuesCasts() {
        engine.execute("CREATE OR REPLACE TABLE tn (a NUMBER(20,4) NOT NULL, b NUMBER(20,4))");
        engine.execute("INSERT INTO tn VALUES (1234567.1234, 1234567.1234), (7654321.4321, 7654321.4321)");
        engine.execute("CREATE OR REPLACE TABLE tr (a NUMBER(38,0) NOT NULL, b NUMBER(38,0))");
        engine.execute("INSERT INTO tr VALUES (1e37, 1e37)");
        assertCells(new String[][] {
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1), (1234567.1234), (7654321.4321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234, 1), (7654321.4321, 2)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (7654321.4321), (1234567.1234)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1), (NULL), (1234567.1234), (7654321.4321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (NULL)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (7654321.4321) ORDER BY column1 "
                + "DESC",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (7654321.4321) WHERE column1 > "
                + "2000000",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (7654321.4321) LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) c FROM VALUES (1234567.1234), (7654321.4321) ORDER BY c",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT CAST(column1 AS NUMBER(10,4)) FROM VALUES (1234567.1234), (7654321.4321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567), (7654321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value 1234567"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (NULLIF(7654321.4321, 0))",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM (VALUES (1234567.1234), (7654321.4321)) t",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM (SELECT * FROM VALUES (1234567.1234), (7654321.4321)) t",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT 1234567.1234 x UNION ALL SELECT 7654321.4321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT 1234567.1234 x UNION ALL SELECT NULL)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"WITH c AS (SELECT 1234567.1234 x) SELECT x::NUMBER(10,4) FROM c",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT 1234567.1234::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT (1234567.1234 + 1)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234568.1234"},
            {"SELECT (SELECT 1234567.1234)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234::NUMBER(20,4)), (7654321.4321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES ('1234567.1234'::NUMBER(20,4)), (7654321.4321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1), (2) , (7654321.4321), (1234567.1234) ORDER BY "
                + "1",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4), column2::NUMBER(10,4) FROM VALUES (1, 7654321.4321), "
                + "(1234567.1234, 1)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(4,0) FROM VALUES (12345), (54321)",
                "REFUSED Number out of representable range: type FIXED[SB2](4,0){not null}, value 12345"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (7654321.4321) WHERE column1 IS NOT"
                + " NULL",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1), (NULL), (1234567.1234) WHERE column1 IS NOT "
                + "NULL",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4) FROM tn",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT b::NUMBER(10,4) FROM tn",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT (a + 0)::NUMBER(10,4) FROM tn",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT COALESCE(b, 0)::NUMBER(10,4) FROM tn",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT a x FROM tn)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT b x FROM tn)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4) FROM tn WHERE a > 2000000",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT (SELECT MAX(a) FROM tn)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT a x FROM tn UNION ALL SELECT b FROM tn)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT 1e37::NUMBER(38,0) + 1::NUMBER(38,6)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT 1e15::NUMBER(38,3) * 1e15::NUMBER(38,3) + 1e37::NUMBER(38,0)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) - 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) + "
                + "1e37::NUMBER(38,0)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT a + 1::NUMBER(38,6) FROM (SELECT 1e37::NUMBER(38,0) a)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT 1e37::NUMBER(38,0) - 1::NUMBER(38,6)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT 1::NUMBER(38,6) + 1e37::NUMBER(38,0)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT column1 + 1::NUMBER(38,6) FROM VALUES (1e37::NUMBER(38,0)), (2e37::NUMBER(38,0))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT column1 + 1::NUMBER(38,6) FROM VALUES (1e37::NUMBER(38,0)), (NULL)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT a + 1::NUMBER(38,6) FROM tr",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT b + 1::NUMBER(38,6) FROM tr",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT NULLIF(1e37::NUMBER(38,0), 0) + 1::NUMBER(38,6)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT GREATEST(1e37::NUMBER(38,0), 1) + 1::NUMBER(38,6)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT 1e37::NUMBER(38,0) + a FROM (SELECT 1::NUMBER(38,6) a)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT a + b FROM (SELECT 1e37::NUMBER(38,0) a, 1::NUMBER(38,6) b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT a + b FROM (SELECT 1e37::NUMBER(38,0) a, NULLIF(1::NUMBER(38,6), 0) b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){not null}, value "
                + "10000000000000000000000000000000000000"},
            {"SELECT a + b FROM (SELECT NULLIF(1e37::NUMBER(38,0), 0) a, 1::NUMBER(38,6) b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "10000000000000000000000000000000000000"},
        });
    }

    @Test
    public void expressionForms() {
        assertCells(new String[][] {
            {"SELECT NULLIF(1234567.1234, 0)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT ABS(1234567.1234)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT IFF(TRUE, 1234567.1234, 0)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT IFF(TRUE, 1234567.1234, NULL)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT CASE WHEN TRUE THEN 1234567.1234 ELSE 0 END::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT CASE WHEN TRUE THEN 1234567.1234 END::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT COALESCE(NULL, 1234567.1234)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT GREATEST(1234567.1234, 1)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT TRY_TO_NUMBER('1234567.1234', 20, 4)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT TO_NUMBER('1234567.1234', 20, 4)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT ROUND(1234567.1234, 4)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT (-1234567.1234)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "-1234567.1234"},
            {"SELECT (1234567.1234 * 1)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT 1234567.1234::NUMBER(20,4)::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT TRY_CAST('1234567.1234' AS NUMBER(20,4))::NUMBER(10,4)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT NULLIF(1234567.1234, 0) x)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT ABS(1234567.1234) x)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT MAX(column1)::NUMBER(10,4) FROM VALUES (1234567.1234), (1)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT SUM(column1)::NUMBER(10,4) FROM VALUES (1234567.1234), (1)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234568.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234) UNION ALL SELECT 1",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4) FROM (SELECT column1 a FROM VALUES (1234567.1234), (NULL)) WHERE a IS"
                + " NOT NULL",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT LAG(column1, 0) OVER (ORDER BY column1)::NUMBER(10,4) FROM VALUES (1234567.1234)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (1) t, VALUES (1) u",
                "REFUSED SQL compilation error: | ambiguous column name 'COLUMN1'"},
            {"SELECT t.column1::NUMBER(10,4) FROM VALUES (1) u LEFT JOIN VALUES (1234567.1234) t ON TRUE",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT ROUND(column1, -1) FROM VALUES (99999999999999999999999999999999999999)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "100000000000000000000000000000000000000"},
            {"SELECT ROUND(column1, -1) FROM VALUES (99999999999999999999999999999999999999), (NULL)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value "
                + "100000000000000000000000000000000000000"},
            {"SELECT ROUND(NULLIF(99999999999999999999999999999999999999, 0), -1)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value "
                + "100000000000000000000000000000000000000"},
            {"SELECT ROUND(ABS(99999999999999999999999999999999999999), -1)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "100000000000000000000000000000000000000"},
            {"SELECT ROUND((SELECT 99999999999999999999999999999999999999), -1)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value "
                + "100000000000000000000000000000000000000"},
            {"SELECT ROUND(x, -1) FROM (SELECT 99999999999999999999999999999999999999 x)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "100000000000000000000000000000000000000"},
            {"SELECT ROUND(x, -1) FROM (SELECT NULLIF(99999999999999999999999999999999999999, 0) x)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value "
                + "100000000000000000000000000000000000000"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234, 'a'), (7654321.4321, NULL)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (1 + NULL)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234), (NULL::INT)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
        });
    }

    @Test
    public void itemOrder() {
        engine.execute("CREATE OR REPLACE TABLE tw (a NUMBER(20,4), b NUMBER(20,4), n INT)");
        engine.execute("INSERT INTO tw SELECT IFF(SEQ4() = 500, 1234567.1234, 1), IFF(SEQ4() = 10, 7654321.4321, 1),"
            + " SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        engine.execute("CREATE OR REPLACE TABLE t2 (a NUMBER(20,4), b NUMBER(20,4))");
        engine.execute("INSERT INTO t2 VALUES (1, 7654321.4321), (1234567.1234, 1)");
        assertCells(new String[][] {
            {"SELECT column2::NUMBER(10,4), column1::NUMBER(10,4) FROM VALUES (1, 7654321.4321), "
                + "(1234567.1234, 1)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4), 1/column2 FROM VALUES (1, 0), (1234567.1234, 1)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT 1/column2, column1::NUMBER(10,4) FROM VALUES (1, 1), (1234567.1234, 0)",
                "REFUSED Division by zero"},
            {"SELECT column1::NUMBER(10,4), column2::NUMBER(10,4) FROM VALUES (1234567.1234, 7654321.4321)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tw",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT b::NUMBER(10,4), a::NUMBER(10,4) FROM tw",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4), column2::NUMBER(10,4) FROM VALUES (1, 7654321.4321), "
                + "(1234567.1234, 1) WHERE column1 > 0",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT x, y FROM (SELECT column1::NUMBER(10,4) x, column2::NUMBER(10,4) y FROM VALUES (1, "
                + "7654321.4321), (1234567.1234, 1))",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1), (2), (1234567.1234), (7654321.4321) ORDER BY "
                + "column1 DESC",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1), (2), (1234567.1234), (7654321.4321) ORDER BY "
                + "column1",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (7654321.4321), (2), (1234567.1234) ORDER BY 1 DESC",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT column1::NUMBER(10,4) c FROM VALUES (7654321.4321), (2), (1234567.1234) ORDER BY c "
                + "DESC",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM t2",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
        });
    }

    @Test
    public void batchOrder() {
        engine.execute("CREATE OR REPLACE TABLE tg100 AS SELECT IFF(SEQ4() = 100, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg200 AS SELECT IFF(SEQ4() = 200, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg300 AS SELECT IFF(SEQ4() = 300, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg400 AS SELECT IFF(SEQ4() = 400, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg450 AS SELECT IFF(SEQ4() = 450, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg499 AS SELECT IFF(SEQ4() = 499, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg511 AS SELECT IFF(SEQ4() = 511, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg512 AS SELECT IFF(SEQ4() = 512, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg513 AS SELECT IFF(SEQ4() = 513, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg1023 AS SELECT IFF(SEQ4() = 1023, 1234567.1234, 1)::NUMBER(20,4) "
            + "a, IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT"
            + " => 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg1024 AS SELECT IFF(SEQ4() = 1024, 1234567.1234, 1)::NUMBER(20,4) "
            + "a, IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT"
            + " => 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg1025 AS SELECT IFF(SEQ4() = 1025, 1234567.1234, 1)::NUMBER(20,4) "
            + "a, IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT"
            + " => 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg230 AS SELECT IFF(SEQ4() = 230, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg250 AS SELECT IFF(SEQ4() = 250, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg255 AS SELECT IFF(SEQ4() = 255, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg256 AS SELECT IFF(SEQ4() = 256, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg257 AS SELECT IFF(SEQ4() = 257, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg270 AS SELECT IFF(SEQ4() = 270, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE th AS SELECT IFF(SEQ4() = 300, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 290, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT "
            + "=> 3000)) ORDER BY n");
        assertCells(new String[][] {
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg100",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg200",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg300",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg400",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg450",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg499",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg511",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg512",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg513",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg1023",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg1024",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg1025",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg230",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg250",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg255",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg256",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg257",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg270",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM th",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
        });
    }

    @Test
    public void joinsAndStars() {
        engine.execute("CREATE OR REPLACE TABLE tg255 AS SELECT IFF(SEQ4() = 255, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tg256 AS SELECT IFF(SEQ4() = 256, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 10, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT =>"
            + " 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE th AS SELECT IFF(SEQ4() = 300, 1234567.1234, 1)::NUMBER(20,4) a, "
            + "IFF(SEQ4() = 290, 7654321.4321, 1)::NUMBER(20,4) b, SEQ4() n FROM TABLE(GENERATOR(ROWCOUNT "
            + "=> 3000)) ORDER BY n");
        engine.execute("CREATE OR REPLACE TABLE tnn (c NUMBER(3,0) NOT NULL)");
        assertCells(new String[][] {
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg255",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM tg256",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "7654321.4321"},
            {"SELECT a::NUMBER(10,4), b::NUMBER(10,4) FROM th",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"INSERT INTO tnn SELECT column1 FROM VALUES (12345)",
                "REFUSED DML operation to table TNN failed on column C with error: Number out of "
                + "representable range: type FIXED[SB2](3,0){not null}, value 12345"},
            {"INSERT INTO tnn SELECT column1 FROM VALUES (12345), (NULL)",
                "REFUSED DML operation to table TNN failed on column C with error: Number out of "
                + "representable range: type FIXED[SB2](3,0){nullable}, value 12345"},
            {"INSERT INTO tnn SELECT column1 FROM VALUES (12345), (NULLIF(1, 0))",
                "REFUSED DML operation to table TNN failed on column C with error: Number out of "
                + "representable range: type FIXED[SB2](3,0){nullable}, value 12345"},
            {"SELECT x::NUMBER(10,4) FROM VALUES (1) u LEFT JOIN (SELECT 1234567.1234 x) t ON TRUE",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM VALUES (1) u(k) LEFT JOIN VALUES (1234567.1234) t(x) ON TRUE",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT t.column1 x FROM VALUES (1) u LEFT JOIN VALUES "
                + "(1234567.1234) t ON TRUE)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM VALUES (1) u(k) INNER JOIN VALUES (1234567.1234) t(x) ON TRUE",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT x FROM VALUES (1) u(k) LEFT JOIN (SELECT 1234567.1234 x)"
                + " t ON TRUE)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM (SELECT * FROM VALUES (1234567.1234), (NULL))",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM (SELECT * FROM VALUES (1234567.1234), (2) t(x))",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM (SELECT * FROM VALUES (1) u(k) LEFT JOIN VALUES "
                + "(1234567.1234) t ON TRUE)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){nullable}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM VALUES (1234567.1234) t(x)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT column1::NUMBER(10,4) FROM VALUES (1234567.1234) t",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT t.column1::NUMBER(10,4) FROM VALUES (1) u INNER JOIN VALUES (1234567.1234) t ON TRUE",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
            {"SELECT x::NUMBER(10,4) FROM VALUES (1) u INNER JOIN (SELECT 1234567.1234 x) t ON TRUE",
                "REFUSED Number out of representable range: type FIXED[SB8](10,4){not null}, value "
                + "1234567.1234"},
        });
    }
}
