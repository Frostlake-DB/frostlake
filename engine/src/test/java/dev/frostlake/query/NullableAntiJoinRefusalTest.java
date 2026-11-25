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
 * A NOT IN over a query, and its spellings {@code <> ALL} and {@code != ALL}, standing anywhere but as a filter — a
 * select item, a CASE or IFF, an OR, the ORDER BY, a MERGE's WHEN condition or SET value, an UPDATE's SET value, a
 * block's expression — is refused when its uncorrelated query may answer NULL, judged by what the tables' rows hold
 * rather than by the columns' declarations: a nullable column holding no NULL is answered, one holding a NULL is
 * refused whatever the query's WHERE keeps (live-verified).
 */
public class NullableAntiJoinRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void columnsAndPlaces() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT)");
        engine.execute("INSERT INTO o VALUES (1, 1), (2, 2), (3, NULL), (4, 2), (5, 5)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE nd (k INT NOT NULL)");
        engine.execute("INSERT INTO nd VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE wd (k INT)");
        engine.execute("INSERT INTO wd VALUES (1), (NULL)");
        engine.execute("DELETE FROM wd WHERE k IS NULL");
        engine.execute("CREATE OR REPLACE TABLE wu (k INT)");
        engine.execute("INSERT INTO wu VALUES (1), (NULL)");
        engine.execute("UPDATE wu SET k = 3 WHERE k IS NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL)");
        engine.execute("CREATE OR REPLACE VIEW vwn AS SELECT k FROM wn");
        engine.execute("CREATE OR REPLACE VIEW vnn AS SELECT k FROM nn");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertCells(new String[][] {
            {"SELECT id, o.k NOT IN (SELECT k FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k IS NOT NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT NULLIF(k, 1) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k + 1 FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT 2 UNION ALL SELECT NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM nn UNION ALL SELECT k FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM nn UNION ALL SELECT k FROM nd) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT b.k FROM nn a LEFT JOIN nd b ON a.k = b.k + 5) FROM o ORDER BY"
                + " id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT b.k FROM nn a JOIN wn b ON a.k = b.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT column1 FROM VALUES (1), (NULL)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM vwn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"WITH c AS (SELECT k FROM wn) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 52"},
            {"SELECT id, o.k NOT IN (SELECT IFF(k > 1, NULL, k) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM (SELECT k FROM wn WHERE k IS NOT NULL)) FROM o ORDER BY"
                + " id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k <> ALL (SELECT k FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k != ALL (SELECT k FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k <> ALL (SELECT NULLIF(k, 1) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn), o.k <> ALL (SELECT k FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 49"},
            {"SELECT id, id NOT IN (SELECT k FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT 1 NOT IN (SELECT k FROM wn)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT 1 NOT IN (SELECT k FROM nn)",
                "false"},
            {"SELECT 1 <> ALL (SELECT k FROM wn)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, CASE WHEN o.k NOT IN (SELECT k FROM wn) THEN 1 ELSE 0 END FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 33"},
            {"SELECT id, IFF(o.k NOT IN (SELECT k FROM wn), 1, 0) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 27"},
            {"SELECT id, IFF(o.k <> ALL (SELECT k FROM wn), 1, 0) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT id, CASE WHEN o.k <> ALL (SELECT k FROM wn) THEN 1 ELSE 0 END FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 28"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT k FROM wn) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT k FROM wn) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT k FROM wn) OR id = 5 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM o ORDER BY o.k NOT IN (SELECT k FROM wn), id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"SELECT k, COUNT(*) FROM o GROUP BY k HAVING k NOT IN (SELECT k FROM wn) ORDER BY k",
                ""},
            {"SELECT k, COUNT(*) FROM o GROUP BY k HAVING COUNT(*) > 0 AND k NOT IN (SELECT k FROM wn) "
                + "ORDER BY k",
                ""},
            {"SELECT k, COUNT(*) FROM o GROUP BY k HAVING k <> ALL (SELECT k FROM wn) ORDER BY k",
                ""},
            {"SELECT id, COALESCE(o.k NOT IN (SELECT k FROM wn), FALSE) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
        });
    }

    @Test
    public void dmlAndBlocks() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT)");
        engine.execute("INSERT INTO o VALUES (1, 1), (2, 2), (3, NULL), (4, 2), (5, 5)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t (id INT, k INT, b BOOLEAN) AS SELECT id, k, NULL FROM o");
        assertCells(new String[][] {
            {"MERGE INTO t USING o src ON t.id = src.id WHEN MATCHED AND src.k NOT IN (SELECT k FROM wn) "
                + "THEN UPDATE SET k = -1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 73"},
            {"MERGE INTO t USING o src ON t.id = src.id WHEN MATCHED AND src.k <> ALL (SELECT k FROM wn) "
                + "THEN UPDATE SET k = -1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 68"},
            {"MERGE INTO t USING o src ON t.id = src.id + 10 WHEN NOT MATCHED AND src.k NOT IN (SELECT k "
                + "FROM wn) THEN INSERT (id, k) VALUES (src.id, src.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 82"},
            {"MERGE INTO t USING o src ON t.id = src.id WHEN MATCHED THEN UPDATE SET k = IFF(src.k NOT IN "
                + "(SELECT k FROM wn), 1, 0)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 93"},
            {"UPDATE t SET b = (k NOT IN (SELECT k FROM wn))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 28"},
            {"UPDATE t SET b = (k <> ALL (SELECT k FROM wn))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"INSERT INTO t (id, k, b) SELECT id, k, k NOT IN (SELECT k FROM wn) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 49"},
            {"EXECUTE IMMEDIATE $$DECLARE x BOOLEAN; BEGIN x := 2 NOT IN (SELECT k FROM wn); RETURN x; "
                + "END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25"},
            {"EXECUTE IMMEDIATE $$DECLARE x BOOLEAN; BEGIN x := 2 NOT IN (SELECT k FROM nn); RETURN x; "
                + "END$$",
                "false"},
            {"EXECUTE IMMEDIATE $$DECLARE x BOOLEAN; BEGIN x := 2 <> ALL (SELECT k FROM wn); RETURN x; "
                + "END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 20"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (3 NOT IN (SELECT k FROM wn)) THEN RETURN 'y'; END IF; RETURN "
                + "'n'; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 17"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 3 NOT IN (SELECT k FROM wn); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 17"},
        });
    }

    @Test
    public void oneRowAndFilters() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, s VARCHAR)");
        engine.execute("INSERT INTO o VALUES (1, 1, 'a'), (2, 2, 'b'), (3, NULL, NULL), (4, 2, 'b'), (5, 5, 'e')");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO nn VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO wn VALUES (1, 'a'), (NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL)");
        engine.execute("CREATE OR REPLACE TABLE an2 (k INT)");
        engine.execute("INSERT INTO an2 VALUES (NULL), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE e (k INT)");
        assertCells(new String[][] {
            {"SELECT id, o.k NOT IN (SELECT k FROM wn LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT MAX(k) FROM wn GROUP BY k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM an UNION ALL SELECT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT s FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT UPPER(s) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s <> ALL (SELECT s FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.s NOT IN (SELECT s || 'x' FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE (o.k NOT IN (SELECT k FROM wn)) = TRUE ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT k FROM wn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id FROM o WHERE id > 0 AND (o.k NOT IN (SELECT k FROM wn) OR id = 5) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE CASE WHEN o.k NOT IN (SELECT k FROM wn) THEN TRUE END ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT k FROM wn) OR id = 5 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT o.id FROM o JOIN nn ON o.k = nn.k AND o.k NOT IN (SELECT k FROM wn) ORDER BY o.id",
                ""},
            {"SELECT id FROM o QUALIFY o.k NOT IN (SELECT k FROM wn) ORDER BY id",
                "REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
                + "window function."},
            {"SELECT k, COUNT(*) FROM o GROUP BY k HAVING k NOT IN (SELECT k FROM wn) OR COUNT(*) > 5 "
                + "ORDER BY k",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 54"},
            {"SELECT k, COUNT(*) FROM o GROUP BY k HAVING MAX(id) NOT IN (SELECT k FROM wn) ORDER BY k",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60"},
            {"SELECT k, COUNT(*) FROM o GROUP BY k HAVING k NOT IN (SELECT k FROM wn) = TRUE ORDER BY k",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 54"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT k FROM wn) AND o.k NOT IN (SELECT k FROM nn) ORDER"
                + " BY id",
                ""},
            {"SELECT id, COUNT(*) FROM o GROUP BY id HAVING id NOT IN (SELECT k FROM wn) ORDER BY id",
                ""},
            {"SELECT id, k FROM o WHERE o.k NOT IN (SELECT k FROM wn) IS NULL ORDER BY id",
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'IS'."},
        });
    }

    @Test
    public void setOperationsAndExpressions() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, s VARCHAR)");
        engine.execute("INSERT INTO o VALUES (1, 1, 'a'), (2, 2, 'b'), (3, NULL, NULL), (4, 2, 'b'), (5, 5, 'e')");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO nn VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE TABLE nd (k INT NOT NULL, s VARCHAR NOT NULL)");
        engine.execute("INSERT INTO nd VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO wn VALUES (1, 'a'), (NULL, NULL)");
        assertCells(new String[][] {
            {"SELECT id, o.k NOT IN (SELECT k FROM nd UNION ALL SELECT k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM nn UNION ALL SELECT k FROM nd) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn EXCEPT SELECT k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM nn UNION ALL SELECT k + 0 FROM nd) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k * 2 FROM wn WHERE k IS NOT NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT LOWER(s) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT b.k FROM nn a LEFT JOIN nn b ON a.k = b.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM nn a, wn b WHERE a.k = b.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | ambiguous column name 'K'"},
        });
    }

    @Test
    public void answeredValues() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT)");
        engine.execute("INSERT INTO o VALUES (1, 1), (2, 2), (3, NULL), (4, 2), (5, 5)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE nd (k INT NOT NULL)");
        engine.execute("INSERT INTO nd VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE wd (k INT)");
        engine.execute("INSERT INTO wd VALUES (1), (NULL)");
        engine.execute("DELETE FROM wd WHERE k IS NULL");
        engine.execute("CREATE OR REPLACE TABLE wu (k INT)");
        engine.execute("INSERT INTO wu VALUES (1), (NULL)");
        engine.execute("UPDATE wu SET k = 3 WHERE k IS NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL)");
        engine.execute("CREATE OR REPLACE VIEW vwn AS SELECT k FROM wn");
        engine.execute("CREATE OR REPLACE VIEW vnn AS SELECT k FROM nn");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE nds (k INT NOT NULL)");
        engine.execute("INSERT INTO nds VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE an2 (k INT)");
        engine.execute("INSERT INTO an2 VALUES (NULL), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE ns (s VARCHAR)");
        engine.execute("INSERT INTO ns VALUES ('a'), ('b')");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertCells(new String[][] {
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM nn)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM wd)) FROM o",
                "3"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM wu)) FROM o",
                "3"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k + 1 FROM nn)) FROM o",
                "2"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT COALESCE(k, 0) FROM wn)) FROM o",
                "3"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT 2 UNION ALL SELECT 3)) FROM o",
                "2"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM nn UNION ALL SELECT k FROM nn)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM nn MINUS SELECT k FROM wn)) FROM o",
                "2"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM nn INTERSECT SELECT k FROM wn)) FROM o",
                "3"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT MAX(k) FROM wn)) FROM o",
                "3"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT b.k FROM nn a JOIN nd b ON a.k = b.k)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT a.k FROM nn a LEFT JOIN wn b ON a.k = b.k)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM vnn)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM an2)) FROM o",
                "0"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM an)) FROM o",
                "0"},
            {"SELECT COUNT_IF(o.k <> ALL (SELECT k FROM nn)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k != ALL (SELECT k FROM nds)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM wn WHERE wn.k = o.id OR wn.k IS NULL)) FROM o",
                "0"},
            {"SELECT COUNT_IF(CASE WHEN o.k NOT IN (SELECT k FROM nn) THEN TRUE END) FROM o",
                "1"},
            {"SELECT COUNT(*) FROM o WHERE o.k NOT IN (SELECT k FROM wn) AND id > 0",
                "0"},
            {"SELECT COUNT(*) FROM o JOIN nn ON o.k = nn.k AND o.k NOT IN (SELECT k FROM wn)",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT k FROM o GROUP BY k HAVING COUNT(*) > 0 AND k NOT IN (SELECT k "
                + "FROM wn))",
                "0"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT ABS(k) FROM nn)) FROM o",
                "1"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT k FROM nn WHERE k > 1)) FROM o",
                "2"},
            {"SELECT 1 NOT IN (SELECT k FROM nn)",
                "false"},
            {"SELECT COUNT_IF(TO_VARCHAR(id) NOT IN (SELECT s FROM ns)) FROM o",
                "5"},
            {"SELECT COUNT_IF(TO_VARCHAR(id) NOT IN (SELECT UPPER(s) FROM ns)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"SELECT COUNT_IF(o.k NOT IN (SELECT s FROM ns WHERE s <> 'x')) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 28"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 3 NOT IN (SELECT k FROM nn); END$$",
                "true"},
        });
        engine.execute("MERGE INTO t USING o src ON t.id = src.id WHEN MATCHED AND src.k NOT IN (SELECT k FROM nn) "
            + "THEN UPDATE SET k = -1");
        assertCells(new String[][] {
            {"SELECT SUM(k) FROM t", "4"},
        });
        engine.execute("UPDATE t SET k = IFF(k NOT IN (SELECT k FROM nn), 100, k)");
        assertCells(new String[][] {
            {"SELECT SUM(k) FROM t", "105"},
        });
        engine.execute("MERGE INTO t USING o src ON t.id = src.id AND src.k NOT IN (SELECT k FROM wn) WHEN MATCHED "
            + "THEN UPDATE SET k = -1");
    }
}
