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
 * Joins on null-safe and computed equalities, live-verified: EQUAL_NULL and IS NOT DISTINCT FROM pair NULL keys and
 * keep a JSON null apart from a SQL NULL; an equality between expressions of one side each (COALESCE, casts,
 * arithmetic, UPPER, TO_VARCHAR) joins the rows its values compare equal on, and a key text that does not read as a
 * number is refused as the comparison refuses it.
 */
public class JoinComputedKeyCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void nullSafeAndComputedKeys() {
        engine.execute("CREATE TABLE na(id INT, k INT, v VARIANT, s VARCHAR)");
        engine.execute("CREATE TABLE nb(id INT, k INT, v VARIANT, s VARCHAR)");
        engine.execute("INSERT INTO na SELECT 1, 1, PARSE_JSON('1'), 'a' UNION ALL SELECT 2, NULL, "
            + "PARSE_JSON('null'), NULL UNION ALL SELECT 3, NULL, NULL, 'b' UNION ALL SELECT 4, 4, "
            + "PARSE_JSON('{\"a\":1}'), 'A'");
        engine.execute("INSERT INTO nb SELECT 10, 1, TO_VARIANT(1::FLOAT), 'a' UNION ALL SELECT 20, NULL, "
            + "PARSE_JSON('null'), 'A' UNION ALL SELECT 30, NULL, NULL, NULL UNION ALL SELECT 40, 5, "
            + "PARSE_JSON('{\"a\":1.0}'), 'b'");
        engine.execute("CREATE TABLE xa(id INT, k INT, s VARCHAR, d DATE, v VARIANT, c VARCHAR COLLATE 'en-ci')");
        engine.execute("CREATE TABLE xb(id INT, k INT, s VARCHAR, d DATE, v VARIANT, c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO xa SELECT 1, 1, '1', '2024-01-01'::DATE, PARSE_JSON('1'), 'abc' UNION ALL SELECT"
            + " 2, 2, ' 2', '2024-01-02'::DATE, PARSE_JSON('\"x\"'), 'X' UNION ALL SELECT 3, NULL, 'abc', "
            + "NULL, NULL, NULL");
        engine.execute("INSERT INTO xb SELECT 10, 1, '01', '2024-01-01'::DATE, TO_VARIANT(1::FLOAT), 'ABC' UNION ALL"
            + " SELECT 20, 2, '2', '2024-01-01'::DATE, PARSE_JSON('\"X\"'), 'x' UNION ALL SELECT 30, NULL, "
            + "'ABC', NULL, NULL, NULL");
        assertCells(new String[][] {
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON EQUAL_NULL(na.k, nb.k)",
                "1-10/2-20/2-30/3-20/3-30"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON na.k IS NOT DISTINCT FROM nb.k",
                "1-10/2-20/2-30/3-20/3-30"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON EQUAL_NULL(na.v, nb.v)",
                "1-10/2-20/3-30/4-40"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON na.v IS NOT DISTINCT FROM nb.v",
                "1-10/2-20/3-30/4-40"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na, nb WHERE EQUAL_NULL(na.s, nb.s)",
                "1-10/2-30/3-40/4-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na LEFT JOIN nb ON EQUAL_NULL(na.k, nb.k) AND "
                + "na.s IS NOT DISTINCT FROM nb.s",
                "1-10/2-30/3-n/4-n"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na FULL JOIN nb ON nb.k IS NOT DISTINCT FROM na.k",
                "1-10/2-20/2-30/3-20/3-30/4-n/n-40"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON na.id IS DISTINCT FROM nb.id AND "
                + "EQUAL_NULL(na.k, nb.k)",
                "1-10/2-20/2-30/3-20/3-30"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON EQUAL_NULL(na.k, nb.s)",
                "REFUSED Numeric value 'a' is not recognized"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON COALESCE(na.k, -1) = COALESCE(nb.k,"
                + " -1)",
                "1-10/2-20/2-30/3-20/3-30"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON na.k::VARCHAR = nb.k::VARCHAR",
                "1-10"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON na.k + 0 = nb.k",
                "1-10"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(na.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(nb.id::VARCHAR, 'n'))), '/') FROM na JOIN nb ON UPPER(na.s) = UPPER(nb.s)",
                "1-10/1-20/3-40/4-10/4-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.s = xb.k + 0",
                "REFUSED Numeric value 'abc' is not recognized"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.k::VARCHAR = xb.s",
                "2-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON UPPER(xa.c) = UPPER(xb.c)",
                "1-10/2-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.c || '' = xb.c",
                "1-10/2-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON TO_VARCHAR(xa.d) = xb.s",
                ""},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.d + 0 = xb.d",
                "1-10/1-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.s::INT = xb.k",
                "REFUSED Numeric value 'abc' is not recognized"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON TRY_CAST(xa.s AS INT) = xb.k",
                "1-10/2-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa LEFT JOIN xb ON COALESCE(xa.k, 0) = "
                + "COALESCE(xb.k, 0)",
                "1-10/2-20/3-30"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa FULL JOIN xb ON xa.k * 10 = xb.id",
                "1-10/2-20/3-n/n-30"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa, xb WHERE xa.k + 1 = xb.k + 1 AND xa.id < 5",
                "1-10/2-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.s = xb.s::INT",
                "REFUSED Numeric value 'ABC' is not recognized"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON TRIM(xa.s) = xb.k",
                "REFUSED Numeric value 'abc' is not recognized"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.v = TO_VARIANT(xb.k)",
                "1-10"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.k = xb.k AND xa.id IS DISTINCT "
                + "FROM xb.id",
                "1-10/2-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON xa.s::INT = xb.k AND FALSE",
                ""},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN (SELECT * FROM xb WHERE FALSE) xb ON "
                + "xa.s::INT = xb.k",
                ""},
            {"SELECT ARRAY_TO_STRING(ARRAY_SORT(ARRAY_AGG(COALESCE(xa.id::VARCHAR, 'n') || '-' || "
                + "COALESCE(xb.id::VARCHAR, 'n'))), '/') FROM xa JOIN xb ON -xa.k = -xb.k",
                "1-10/2-20"},
        });
    }
}
