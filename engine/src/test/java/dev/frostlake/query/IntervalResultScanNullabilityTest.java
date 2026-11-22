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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RESULT_SCAN over a result with an interval column names the column as the scan reads it, with STRIP_NULL_VALUE
 * only where the column can hold NULL (live-verified): a NOT NULL column, an interval constant, an operation, a
 * cast, a COALESCE or an IFF over what is never NULL, a derived relation's or a set operation's column of such, and
 * a grouping key are read bare; a nullable column, a NULL, an aggregate, a CASE without an ELSE and the side an
 * outer join extends are stripped.
 */
public class IntervalResultScanNullabilityTest extends BaseDatabaseTest {

    /** Each result's query, what the scan selects, whether the read strips, the column's place and its type. */
    private static final String[][] SCANS = {
            {"SELECT INTERVAL '1' YEAR AS ym, 1 AS n",
                "n", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT INTERVAL '1 2:03:04' DAY TO SECOND AS ds, 1 AS n",
                "n", "reads", "0", "INTERVAL DAY(9) TO SECOND(9)"},
            {"SELECT a FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT b FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT c FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL DAY(9) TO SECOND(9)"},
            {"SELECT d FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL DAY(9) TO SECOND(9)"},
            {"SELECT 1 AS n, NULL::INTERVAL YEAR TO MONTH AS x",
                "n r", "strips", "1", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT a + INTERVAL '1' YEAR AS x FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT b + INTERVAL '1' YEAR AS x FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT IFF(n = 1, INTERVAL '1' YEAR, INTERVAL '2' YEAR) AS x FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT IFF(n = 1, INTERVAL '1' YEAR, NULL) AS x FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9)"},
            {"SELECT INTERVAL '1' YEAR AS x FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT MAX(a) AS x FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT a AS x FROM ti UNION ALL SELECT b FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT INTERVAL '1' MONTH AS x, INTERVAL '2' DAY AS y",
                "COUNT(*)", "reads", "0", "INTERVAL MONTH(9)"},
            {"SELECT n, INTERVAL '2' HOUR AS y FROM ti",
                "COUNT(*)", "reads", "1", "INTERVAL HOUR(9)"},
            {"SELECT x FROM (SELECT INTERVAL '1' YEAR AS x)",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT INTERVAL '1' YEAR + INTERVAL '2' MONTH AS x",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT a AS x FROM ti LEFT JOIN (SELECT 1 k) z ON z.k = 2",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT z.x FROM ti LEFT JOIN (SELECT 1 k, INTERVAL '1' YEAR x) z ON z.k = 2",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9)"},
            {"SELECT COALESCE(b, INTERVAL '1' YEAR) AS x FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT CASE WHEN n = 1 THEN a END AS x FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT -a AS x FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT a::INTERVAL YEAR AS x FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT INTERVAL '1' YEAR AS x WHERE 1 = 0",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT n, NVL(d, INTERVAL '1' DAY) AS x, a FROM ti",
                "COUNT(*) r", "reads", "1", "INTERVAL DAY(9) TO SECOND(9)"},
            {"WITH c AS (SELECT INTERVAL '3' MINUTE AS x) SELECT x FROM c",
                "COUNT(*)", "reads", "0", "INTERVAL MINUTE(9)"},
            {"SELECT INTERVAL '1' YEAR AS x UNION ALL SELECT INTERVAL '2' YEAR",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT x FROM (SELECT b AS x FROM ti)",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT ANY_VALUE(INTERVAL '1' YEAR) AS x",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9)"},
            {"SELECT INTERVAL '1' YEAR AS x FROM ti GROUP BY 1",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9)"},
            {"SELECT a AS x FROM ti GROUP BY a",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT * FROM ti",
                "COUNT(*)", "reads", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT b, a FROM ti",
                "COUNT(*)", "strips", "0", "INTERVAL YEAR(9) TO MONTH"},
            {"SELECT CURRENT_DATE AS dt, INTERVAL '1' SECOND AS x",
                "dt IS NOT NULL", "reads", "1", "INTERVAL SECOND(9,9)"},
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ti (a INTERVAL YEAR TO MONTH NOT NULL, b INTERVAL YEAR TO MONTH,"
            + " c INTERVAL DAY TO SECOND NOT NULL, d INTERVAL DAY TO SECOND, n INT NOT NULL, m INT)");
        engine.execute("INSERT INTO ti SELECT INTERVAL '1-2' YEAR TO MONTH, INTERVAL '1-2' YEAR TO MONTH,"
            + " INTERVAL '1 2:03:04' DAY TO SECOND, INTERVAL '1 2:03:04' DAY TO SECOND, 1, 1");
    }

    @Test
    public void aColumnThatCannotHoldNullIsReadWithoutStripping() {
        for (final String[] scan : SCANS) {
            engine.executeQuery(scan[0]);
            final boolean aliased = scan[1].endsWith(" r");
            final String selected = aliased ? scan[1].substring(0, scan[1].length() - 2) : scan[1];
            final String read = "GET(RESULT_SCAN_" + (aliased ? "R" : "RESULT_SCAN") + ".$1, " + scan[3] + ")";
            final String family = scan[4].contains("YEAR") || scan[4].contains("MONTH")
                ? "TO_INTERVAL_YEAR_MONTH" : "TO_INTERVAL_DAY_TIME";
            assertEquals("SQL compilation error:\ninvalid type [CAST("
                + ("strips".equals(scan[2]) ? "STRIP_NULL_VALUE(" + read + ")" : read) + " AS " + scan[4]
                + ")] for parameter '" + family + "'",
                refusal("SELECT " + selected + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))" + (aliased ? " r" : "")),
                scan[0]);
        }
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
        return "answered";
    }
}
