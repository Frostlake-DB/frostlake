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
 * A HAVING aggregate over a subquery whose own aggregate reads only a column of the grouped query's relation nests the
 * two aggregates. Over a VALUES list written without parentheses the column is named by VALUES or the list's alias;
 * over a derived relation whose source is a VALUES list or a FLATTEN it is printed as its definition -
 * VALUES.COLUMN1, TO_NUMBER(GET(F.VALUE, 'id')), CAST(GET(F.VALUE, 'b') AS BOOLEAN) - and the
 * grouping names the definition's source column. A COUNT of a list's column is answered under a MAX or a MIN where the
 * list's statistics settle the column holds no NULL, and nests where they settle nothing or the subquery filters its
 * rows (all live-verified).
 */
public class HavingDerivedRelationNestingTest extends MeasuredCellsTestSupport {

    @Test
    public void derivedAndBareListsNest() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7)");
        assertCells(new String[][] {
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true},{\"id\":7,\"b\":false}]'))) f) fz GROUP BY "
                + "b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id')))] nested in [MAX(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, "
                + "'id')))))]"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true},{\"id\":7,\"b\":false}]'))) f) fz GROUP BY "
                + "b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id')))] nested in [SUM(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, "
                + "'id')))))]"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true},{\"id\":7,\"b\":false}]'))) f) fz GROUP BY "
                + "b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.v > fz.id)) = 7",
                "REFUSED SQL compilation error: | [F.VALUE] is not a valid group by expression"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true},{\"id\":7,\"b\":false}]'))) f) fz GROUP BY "
                + "b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(TO_NUMBER(GET(F.VALUE, 'id')))] nested in "
                + "[MAX(CORRELATION(COUNT(TO_NUMBER(GET(F.VALUE, 'id')))))]"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true},{\"id\":7,\"b\":false}]'))) f) fz GROUP BY "
                + "b HAVING MAX((SELECT MAX(fz.b) FROM g)) = TRUE",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CAST(GET(F.VALUE, 'b') AS BOOLEAN))] nested in [MAX(CORRELATION(MAX(CAST(GET(F.VALUE, "
                + "'b') AS BOOLEAN))))]"},
            {"SELECT b FROM (SELECT f.value:id::STRING AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true},{\"id\":7,\"b\":false}]'))) f) fz GROUP BY "
                + "b HAVING MAX((SELECT MAX(fz.id) FROM g)) = '7'",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CAST(GET(F.VALUE, 'id') AS VARCHAR(134217728)))] nested in "
                + "[MAX(CORRELATION(MAX(CAST(GET(F.VALUE, 'id') AS VARCHAR(134217728)))))]"},
            {"SELECT b FROM (SELECT f.value:id AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true},{\"id\":7,\"b\":false}]'))) f) fz GROUP BY "
                + "b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(GET(F.VALUE, "
                + "'id'))] nested in [MAX(CORRELATION(MAX(GET(F.VALUE, 'id'))))]"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1)))]"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [SUM(CORRELATION(CAST(MAX(VALUES.COLUMN1) AS NUMBER(1,0))))]"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "MAX(column1) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1)))]"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING SUM((SELECT "
                + "MAX(column1) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [SUM(CORRELATION(CAST(MAX(VALUES.COLUMN1) AS NUMBER(1,0))))]"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "COUNT(column1) FROM g)) = 7",
                ""},
            {"SELECT column2, (SELECT MAX(column1) FROM g) FROM VALUES (5, TRUE), (7, FALSE) GROUP BY "
                + "column2, column1 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 7",
                ""},
        });
    }

    @Test
    public void derivedListShapes() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE t (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO t VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 7",
                ""},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.v > fz.id)) = 7",
                "REFUSED SQL compilation error: | [VALUES.COLUMN1] is not a valid group by expression"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MIN((SELECT MIN(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MIN(VALUES.COLUMN1)] nested in [MIN(CORRELATION(MIN(VALUES.COLUMN1)))]"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) + 1 FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1)) + 1)]"},
            {"SELECT b FROM (SELECT column1::VARCHAR AS id, column2 AS b FROM VALUES (5, TRUE), (7, "
                + "FALSE)) fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = '7'",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CAST(VALUES.COLUMN1 AS VARCHAR(134217728)))] nested in "
                + "[MAX(CORRELATION(MAX(CAST(VALUES.COLUMN1 AS VARCHAR(134217728)))))]"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5.5, TRUE), (7.25, FALSE)) fz"
                + " GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [SUM(CORRELATION(CAST(MAX(VALUES.COLUMN1) AS NUMBER(3,2))))]"},
            {"SELECT b FROM (SELECT v.column1 AS id, v.column2 AS b FROM VALUES (5, TRUE), (7, FALSE) v) "
                + "fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(V.COLUMN1)] "
                + "nested in [MAX(CORRELATION(MAX(V.COLUMN1)))]"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(g.v) FROM g WHERE g.v = fz.id)) = 7",
                "REFUSED SQL compilation error: | [VALUES.COLUMN1] is not a valid group by expression"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MIN((SELECT "
                + "MIN(column1) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MIN(VALUES.COLUMN1)] nested in [MIN(CORRELATION(MIN(VALUES.COLUMN1)))]"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "MAX(column1) FROM g WHERE g.v > column1)) = 7",
                "REFUSED SQL compilation error: | [VALUES.COLUMN1] is not a valid group by expression"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "MAX(g.v) FROM g WHERE g.v = column1)) = 7",
                "REFUSED SQL compilation error: | [VALUES.COLUMN1] is not a valid group by expression"},
            {"SELECT v.column2 FROM VALUES (5, TRUE), (7, FALSE) v GROUP BY v.column2 HAVING MAX((SELECT "
                + "MAX(v.column1) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(V.COLUMN1)] "
                + "nested in [MAX(CORRELATION(MAX(V.COLUMN1)))]"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "MAX(column1) + 1 FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1)) + 1)]"},
            {"SELECT b FROM (VALUES (5, TRUE), (NULL, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) >= 0 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MAX(CORRELATION(COUNT(FZ.ID)))]"},
            {"SELECT b, 1 FROM (VALUES (5, TRUE), (NULL, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 0 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MAX(CORRELATION(COUNT(FZ.ID)))]"},
        });
    }

    @Test
    public void countsOfAListColumn() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7), (9)");
        assertCells(new String[][] {
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) WHERE id > 5 GROUP BY b "
                + "HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY 1",
                "false"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MAX((SELECT COUNT(fz.id) FROM g WHERE g.v > 6)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MAX(CORRELATION(COUNT(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MAX((SELECT COUNT(fz.id) FROM g WHERE g.v > 100)) = 0 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MAX(CORRELATION(COUNT(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MAX((SELECT COUNT(fz.b) FROM g)) = 3 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.B)] nested"
                + " in [MAX(CORRELATION(COUNT(FZ.B)))]"},
        });
    }

    @Test
    public void groupingNamesTheSource() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7)");
        assertCells(new String[][] {
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX(id) > fz.id",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX(id) > "
                + "fz.id",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT id FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 7 | 'FZ.ID' in select clause is "
                + "neither an aggregate nor in the group by clause."},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING (SELECT MAX(g.v) FROM g WHERE g.v = fz.id) > 0",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g WHERE g.v > fz.id)) = 7",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(g.v) FROM g WHERE g.v = fz.id)) = 7",
                "REFUSED SQL compilation error: | [F.VALUE] is not a valid group by expression"},
            {"SELECT b FROM (SELECT column1 + 1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g WHERE g.v > fz.id)) = 7",
                "REFUSED SQL compilation error: | [VALUES.COLUMN1] is not a valid group by expression"},
        });
    }

    @Test
    public void countsByCellKind() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7), (9)");
        assertCells(new String[][] {
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "COUNT(column2) FROM g)) = 3 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(VALUES.COLUMN2)] nested in [MAX(CORRELATION(COUNT(VALUES.COLUMN2)))]"},
            {"SELECT b FROM (SELECT column1::VARCHAR AS id, column2 AS b FROM VALUES (5, TRUE), (7, "
                + "FALSE)) fz GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(CAST(VALUES.COLUMN1 AS VARCHAR(134217728)))] nested in "
                + "[MAX(CORRELATION(COUNT(CAST(VALUES.COLUMN1 AS VARCHAR(134217728)))))]"},
        });
    }

    @Test
    public void settledAndOpenCounts() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7), (9)");
        assertCells(new String[][] {
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MAX((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY b LIMIT 1",
                "false"},
            {"SELECT MAX(id) FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MIN((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY 1 DESC LIMIT 1",
                "8"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) + 1 FROM g)) = 4 ORDER BY b DESC LIMIT 1",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE) AS fz(id, b)) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 3 ORDER BY b LIMIT 1",
                "false"},
            {"SELECT b FROM (VALUES ('x', TRUE), ('y', FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 3 ORDER BY b LIMIT 1",
                "false"},
            {"SELECT b FROM (VALUES (-5, TRUE), (7.5, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 3 ORDER BY b LIMIT 1",
                "false"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) WHERE id > 5 GROUP BY b "
                + "HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 3",
                "false"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "COUNT(column1) FROM g)) = 3 ORDER BY 1 LIMIT 1",
                "false"},
            {"SELECT v.column2 FROM VALUES (5, TRUE), (7, FALSE) v GROUP BY v.column2 HAVING MIN((SELECT "
                + "COUNT(v.column1) FROM g)) = 3 ORDER BY 1 DESC LIMIT 1",
                "true"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY b LIMIT 1",
                "false"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES ('x', TRUE), ('y', FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY b DESC LIMIT 1",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.b) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.B)] nested"
                + " in [MAX(CORRELATION(COUNT(FZ.B)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (NULL, FALSE)) fz(id, b) GROUP BY b HAVING MIN((SELECT "
                + "COUNT(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MIN(CORRELATION(COUNT(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g WHERE g.v > 6)) = 2",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MAX(CORRELATION(COUNT(FZ.ID)))]"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "COUNT(column2) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(VALUES.COLUMN2)] nested in [MAX(CORRELATION(COUNT(VALUES.COLUMN2)))]"},
            {"SELECT b FROM (SELECT column1::VARCHAR AS id, column2 AS b FROM VALUES (5, TRUE), (7, "
                + "FALSE)) fz GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(CAST(VALUES.COLUMN1 AS VARCHAR(134217728)))] nested in "
                + "[MAX(CORRELATION(COUNT(CAST(VALUES.COLUMN1 AS VARCHAR(134217728)))))]"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(TO_NUMBER(GET(F.VALUE, 'id')))] nested in "
                + "[MAX(CORRELATION(COUNT(TO_NUMBER(GET(F.VALUE, 'id')))))]"},
            {"SELECT b FROM (SELECT f.value:id::NUMBER AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MIN((SELECT "
                + "MIN(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MIN(TO_NUMBER(GET(F.VALUE, 'id')))] nested in [MIN(CORRELATION(MIN(TO_NUMBER(GET(F.VALUE, "
                + "'id')))))]"},
            {"SELECT b FROM (SELECT f.value:id::TEXT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g)) = '3'",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CAST(GET(F.VALUE, 'id') AS VARCHAR(134217728)))] nested in "
                + "[MAX(CORRELATION(MAX(CAST(GET(F.VALUE, 'id') AS VARCHAR(134217728)))))]"},
            {"SELECT b FROM (SELECT value:id::INT AS id, value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id')))] nested in [MAX(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, "
                + "'id')))))]"},
            {"SELECT b FROM (SELECT CAST(f.value:id AS INT) AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id')))] nested in [MAX(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, "
                + "'id')))))]"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) + 1 FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id')))] nested in [MAX(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, "
                + "'id')))) + 1)]"},
            {"SELECT b FROM (SELECT f.value:id::NUMBER(10,2) AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id'), 10, 2))] nested in "
                + "[MAX(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, 'id'), 10, 2))))]"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) GROUP BY b HAVING MAX((SELECT "
                + "MAX(id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id')))] nested in [MAX(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, "
                + "'id')))))]"},
        });
    }

    @Test
    public void castDefinitions() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7), (9)");
        assertCells(new String[][] {
            {"SELECT b FROM (SELECT f.value:id::NUMBER(10,0) AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id'), 10, 0))] nested in "
                + "[MAX(CORRELATION(MAX(TO_NUMBER(GET(F.VALUE, 'id'), 10, 0))))]"},
            {"SELECT b FROM (SELECT f.value:id::FLOAT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "MAX(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CAST(GET(F.VALUE, 'id') AS FLOAT))] nested in [MAX(CORRELATION(MAX(CAST(GET(F.VALUE, "
                + "'id') AS FLOAT))))]"},
            {"SELECT b FROM (SELECT f.value:id::NUMBER(10,2) AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING SUM((SELECT "
                + "MAX(fz.id) FROM g)) = 3",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(TO_NUMBER(GET(F.VALUE, 'id'), 10, 2))] nested in "
                + "[SUM(CORRELATION(CAST(MAX(TO_NUMBER(GET(F.VALUE, 'id'), 10, 2)) AS NUMBER(10,2))))]"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) GROUP "
                + "BY b HAVING MAX((SELECT MAX(id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1)))]"},
            {"SELECT column2 FROM (SELECT column1, column2 FROM VALUES (5, TRUE), (7, FALSE)) fz GROUP BY "
                + "column2 HAVING MAX((SELECT MAX(fz.column1) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1)))]"},
            {"SELECT b FROM (SELECT column1::DATE AS id, column2 AS b FROM VALUES ('2024-01-01', TRUE)) fz"
                + " GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) IS NULL",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CAST(VALUES.COLUMN1 AS DATE))] nested in [MAX(CORRELATION(MAX(CAST(VALUES.COLUMN1 AS "
                + "DATE))))]"},
        });
    }
}
