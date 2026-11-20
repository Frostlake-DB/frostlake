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
 * OBJECT_AGG refuses a key met twice, "Duplicate field key 'a'", in the grouped and the window form and even for equal
 * values; pairs with a NULL key or value drop out first, and DISTINCT first drops a repeated pair. Live-verified cells.
 */
public class ObjectAggDuplicateKeyTest extends MeasuredCellsTestSupport {

    @Test
    public void duplicateKeyIsRefused() {
        assertCells(new String[][] {
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',2))",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',1))",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v) OVER (PARTITION BY g)) FROM (SELECT column1 g, column2 k, "
                + "column3 v FROM VALUES (1,'a',1),(1,'a',2))",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT LISTAGG(o, ';') WITHIN GROUP (ORDER BY g) FROM (SELECT g, TO_JSON(OBJECT_AGG(k, v)) o"
                + " FROM (SELECT column1 g, column2 k, column3 v FROM VALUES (1,'a',1),(2,'a',2),(2,'b',3)) "
                + "GROUP BY g)",
                "{\"a\":1};{\"a\":2,\"b\":3}"},
            {"SELECT LISTAGG(o, ';') WITHIN GROUP (ORDER BY g) FROM (SELECT g, TO_JSON(OBJECT_AGG(k, v)) o"
                + " FROM (SELECT column1 g, column2 k, column3 v FROM VALUES (1,'a',1),(2,'a',2),(2,'a',3)) "
                + "GROUP BY g)",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',NULL))",
                "{\"a\":1}"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',NULL),('a',NULL))",
                "{}"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "(NULL,1),(NULL,2),('b',3))",
                "{\"b\":3}"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, PARSE_JSON(column2) v FROM VALUES "
                + "('a','null'),('a','1'))",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, PARSE_JSON(column2) v FROM VALUES "
                + "('a','null'),('b','1'))",
                "{\"a\":null,\"b\":1}"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('A',2))",
                "{\"A\":2,\"a\":1}"},
            {"SELECT TO_JSON(OBJECT_AGG(k::VARIANT, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',2))",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT TO_JSON(OBJECT_AGG(DISTINCT k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',1))",
                "{\"a\":1}"},
            {"SELECT TO_JSON(OBJECT_AGG(k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('b',1),('a',1),('b',2))",
                "REFUSED Duplicate field key 'b'"},
            {"SELECT OBJECT_AGG(k, v) OVER (ORDER BY k) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',2))",
                "REFUSED SQL compilation error: error line 1 at position 24 | Cumulative window frame "
                + "unsupported for function OBJECT_AGG"},
            {"SELECT OBJECT_CONSTRUCT('a',1,'a',2)",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT MAP_INSERT(MAP_CONSTRUCT('a',1),'a',2)",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT TO_JSON(OBJECT_AGG(DISTINCT k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',2))",
                "REFUSED Duplicate field key 'a'"},
            {"SELECT TO_JSON(OBJECT_AGG(DISTINCT k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1),('a',1),('b',2))",
                "{\"a\":1,\"b\":2}"},
            {"SELECT TO_JSON(OBJECT_AGG(DISTINCT k, v)) FROM (SELECT column1 k, column2 v FROM VALUES "
                + "('a',1.0),('a',1))",
                "{\"a\":1}"},
            {"SELECT LISTAGG(o, ';') WITHIN GROUP (ORDER BY g) FROM (SELECT g, TO_JSON(OBJECT_AGG(DISTINCT"
                + " k, v)) o FROM (SELECT column1 g, column2 k, column3 v FROM VALUES "
                + "(1,'a',1),(1,'a',1),(2,'a',2)) GROUP BY g)",
                "{\"a\":1};{\"a\":2}"},
            {"SELECT TO_JSON(OBJECT_AGG(DISTINCT k, PARSE_JSON(v))) FROM (SELECT column1 k, column2 v FROM"
                + " VALUES ('a','{\"x\":1}'),('a','{\"x\":1}'))",
                "{\"a\":{\"x\":1}}"},
            {"SELECT TO_JSON(OBJECT_AGG(DISTINCT k, v) OVER (PARTITION BY g)) FROM (SELECT 1 g, 'a' k, 1 "
                + "v)",
                "{\"a\":1}"},
        });
    }

    @Test
    public void handlerReadsTheCode() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$\nDECLARE r VARIANT;\nBEGIN\n  r := (SELECT OBJECT_AGG(k, v) FROM "
                + "(SELECT column1 k, column2 v FROM VALUES ('a',1),('a',2)));\n  RETURN r;\nEXCEPTION\n  WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM;\nEND;\n$$",
                "100103 22000 Duplicate field key 'a'"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  RETURN OBJECT_CONSTRUCT('a',1,'a',2);\nEXCEPTION\n  WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM;\nEND;\n$$",
                "100103 22000 Duplicate field key 'a'"},
        });
    }
}
