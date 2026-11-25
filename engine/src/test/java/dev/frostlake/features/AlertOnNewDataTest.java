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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * An alert on new data, one written without a schedule (live-verified): it never takes a schedule nor loses one it
 * has not got - an alert on a schedule keeps its own - and its condition is compiled as it is written, then judged
 * for its shape: no SHOW, no common table expression, no procedure and no VALUES clause or missing FROM, and one
 * table or view read, never a join, a list, a set operation, a table function, a stream or a dynamic table.
 */
public class AlertOnNewDataTest extends MeasuredCellsTestSupport {

    @Test
    public void kindAndConditionRefusals() {
        engine.execute("CREATE OR REPLACE TABLE t(a INT)");
        engine.execute("CREATE OR REPLACE TABLE u(a INT)");
        engine.execute("CREATE OR REPLACE STREAM st ON TABLE t");
        engine.execute("CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a "
            + "FROM t");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT * FROM t");
        engine.execute("CREATE ALERT an WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t)) THEN SELECT 1");
        engine.execute("CREATE ALERT anul WAREHOUSE = COMPUTE_WH SCHEDULE = NULL IF (EXISTS (SELECT * FROM v1)) THEN"
            + " SELECT 1");
        engine.execute("CREATE ALERT asc WAREHOUSE = COMPUTE_WH SCHEDULE = 'USING CRON 0 0 1 1 * UTC' IF (EXISTS "
            + "(SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))))) THEN SELECT 1");
        engine.execute("CREATE ALERT anr WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t)) THEN SELECT 1");
        engine.execute("CREATE ALERT ascr WAREHOUSE = COMPUTE_WH SCHEDULE = 'USING CRON 0 0 1 1 * UTC' IF (EXISTS "
            + "(SELECT * FROM t)) THEN SELECT 1");
        engine.execute("ALTER ALERT anr RESUME");
        engine.execute("ALTER ALERT ascr RESUME");
        engine.execute("ALTER ALERT an UNSET SCHEDULE, COMMENT");
        engine.execute("ALTER ALERT an SET SCHEDULE = NULL");
        engine.execute("ALTER ALERT asc MODIFY CONDITION EXISTS (SELECT * FROM t, u)");
        assertCells(new String[][] {
            {"ALTER ALERT an SET SCHEDULE = '1 MINUTE'",
                "REFUSED Streaming alert AN cannot be changed to a scheduled alert"},
            {"ALTER ALERT an SET SCHEDULE = 'bogus'",
                "REFUSED Streaming alert AN cannot be changed to a scheduled alert"},
            {"ALTER ALERT an SET COMMENT = 'x' SCHEDULE = 'USING CRON 0 * * * * UTC'",
                "REFUSED Streaming alert AN cannot be changed to a scheduled alert"},
            {"ALTER ALERT an SET SCHEDULE = '1 MINUTE' SCHEDULE = '2 MINUTE'",
                "REFUSED SQL compilation error: | duplicate property 'SCHEDULE';"},
            {"CREATE OR ALTER ALERT an WAREHOUSE = COMPUTE_WH SCHEDULE = '1 MINUTE' IF (EXISTS (SELECT * "
                + "FROM t)) THEN SELECT 1",
                "REFUSED Streaming alert AN cannot be changed to a scheduled alert"},
            {"ALTER ALERT asc UNSET SCHEDULE",
                "REFUSED Scheduled alert ASC cannot be changed to a streaming alert"},
            {"ALTER ALERT asc UNSET COMMENT, SCHEDULE",
                "REFUSED Scheduled alert ASC cannot be changed to a streaming alert"},
            {"ALTER ALERT asc SET SCHEDULE = NULL",
                "REFUSED Scheduled alert ASC cannot be changed to a streaming alert"},
            {"CREATE OR ALTER ALERT asc WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t)) THEN SELECT 1",
                "REFUSED Scheduled alert ASC cannot be changed to a streaming alert"},
            {"ALTER ALERT anr SET SCHEDULE = '1 MINUTE'",
                "REFUSED Streaming alert ANR cannot be changed to a scheduled alert"},
            {"ALTER ALERT anr UNSET SCHEDULE",
                "REFUSED Unable to alter property for alert ANR since it is not suspended."},
            {"ALTER ALERT anr MODIFY CONDITION EXISTS (SELECT * FROM t, u)",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"ALTER ALERT anr MODIFY CONDITION EXISTS (SELECT * FROM t)",
                "REFUSED Unable to alter property for alert ANR since it is not suspended."},
            {"CREATE OR ALTER ALERT anr WAREHOUSE = COMPUTE_WH SCHEDULE = '1 MINUTE' IF (EXISTS (SELECT * "
                + "FROM t)) THEN SELECT 1",
                "REFUSED Streaming alert ANR cannot be changed to a scheduled alert"},
            {"ALTER ALERT ascr UNSET SCHEDULE",
                "REFUSED Scheduled alert ASCR cannot be changed to a streaming alert"},
            {"CREATE OR ALTER ALERT ascr WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t)) THEN SELECT "
                + "1",
                "REFUSED Scheduled alert ASCR cannot be changed to a streaming alert"},
            {"EXECUTE ALERT an",
                "REFUSED Execute Alert is not supported for a streaming alert."},
            {"CREATE ALERT ac1 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM TABLE(GENERATOR(ROWCOUNT "
                + "=> 1)))) THEN SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
            {"CREATE ALERT ac2 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM TABLE(FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))))) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac3 WAREHOUSE = COMPUTE_WH IF (EXISTS (WITH v AS (SELECT * FROM VALUES (1)) "
                + "SELECT * FROM v)) THEN SELECT 1",
                "REFUSED Streaming alert condition must not contain a common table expression"},
            {"CREATE ALERT ac4 WAREHOUSE = COMPUTE_WH IF (EXISTS (WITH v AS (SELECT * FROM t) SELECT * "
                + "FROM v)) THEN SELECT 1",
                "REFUSED Streaming alert condition must not contain a common table expression"},
            {"CREATE ALERT ac5 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t, LATERAL FLATTEN(INPUT "
                + "=> PARSE_JSON('[1]')))) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac6 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM VALUES (1))) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac7 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT 1)) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac8 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM "
                + "TABLE(SPLIT_TO_TABLE('a,b', ',')))) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac9 WAREHOUSE = COMPUTE_WH IF (EXISTS (WITH v AS (SELECT * FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 1))) SELECT * FROM v)) THEN SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 11 | SELECT with no columns"},
            {"CREATE ALERT ac10 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM (SELECT * FROM VALUES "
                + "(1)))) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac11 WAREHOUSE = COMPUTE_WH IF (EXISTS (WITH RECURSIVE r(n) AS (SELECT 1 UNION "
                + "ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r)) THEN SELECT 1",
                "REFUSED Streaming alert condition must not contain a common table expression"},
            {"ALTER ALERT an MODIFY CONDITION EXISTS (SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 1)))",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
            {"ALTER ALERT an MODIFY CONDITION EXISTS (WITH v AS (SELECT * FROM VALUES (1)) SELECT * FROM "
                + "v)",
                "REFUSED Streaming alert condition must not contain a common table expression"},
            {"CREATE ALERT ac14 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t JOIN u ON t.a = u.a)) "
                + "THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac15 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT a FROM t UNION ALL SELECT a FROM"
                + " t)) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac16 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t x, t y)) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac17 WAREHOUSE = COMPUTE_WH IF (EXISTS (SHOW TABLES)) THEN SELECT 1",
                "REFUSED Streaming alert condition must not contain a show statement"},
            {"CREATE ALERT ac18 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL p1())) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac19 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT (SELECT COUNT(*) FROM t))) THEN "
                + "SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac20 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT SEQ4() FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 1)))) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac21 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT nosuchcol FROM t)) THEN SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCHCOL'"},
            {"CREATE ALERT ac22 WAREHOUSE = COMPUTE_WH IF (EXISTS (WITH w AS (SELECT * FROM t) SELECT * "
                + "FROM w, TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))))) THEN SELECT 1",
                "REFUSED Streaming alert condition must not contain a common table expression"},
            {"CREATE ALERT ac23 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t, VALUES (1))) THEN "
                + "SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac24 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT a FROM t WHERE a = 'x'::DATE)) "
                + "THEN SELECT 1",
                "REFUSED SQL compilation error: | Can not convert parameter 'CAST('x' AS DATE)' of type "
                + "[DATE] into expected type [NUMBER(38,0)]"},
            {"CREATE ALERT ac25 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM VALUES (1) WHERE 1 = "
                + "(SELECT COUNT(*) FROM t))) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac26 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM (VALUES (1)) v, t)) THEN "
                + "SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac27 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM (SELECT 1) x, t)) THEN "
                + "SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac28 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT 1 FROM t UNION ALL SELECT 1)) "
                + "THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac29 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM (SELECT * FROM t UNION "
                + "ALL SELECT * FROM t))) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac30 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM (SELECT * FROM t, u))) "
                + "THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac31 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM st)) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac32 WAREHOUSE = COMPUTE_WH IF (EXISTS (SHOW TABLES IN DATABASE nosuchdb)) THEN"
                + " SELECT 1",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"CREATE ALERT ac33 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT 1 FROM VALUES (1) UNION ALL "
                + "SELECT 1 FROM t)) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac34 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT 1 WHERE EXISTS (SELECT * FROM "
                + "t))) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac35 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t INTERSECT SELECT * FROM"
                + " t)) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac36 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM (VALUES (1)))) THEN "
                + "SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac37 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM TABLE(FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))), t)) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac38 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t WHERE a = nosuchcol)) "
                + "THEN SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCHCOL'"},
            {"CREATE ALERT ac39 WAREHOUSE = COMPUTE_WH IF (EXISTS (WITH w AS (SELECT 1) SELECT * FROM "
                + "VALUES (1))) THEN SELECT 1",
                "REFUSED Streaming alert condition must not contain a common table expression"},
            {"CREATE ALERT ac40 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM dt)) THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE ALERT ac41 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t x JOIN t y USING (a))) "
                + "THEN SELECT 1",
                "REFUSED Streaming alert condition must query a single existing table or view"},
            {"CREATE OR REPLACE ALERT ac42 WAREHOUSE = COMPUTE_WH SCHEDULE = NULL IF (EXISTS (SELECT 1)) "
                + "THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE ALERT ac43 WAREHOUSE = COMPUTE_WH IF (EXISTS (SELECT * FROM t MATCH_RECOGNIZE (ORDER "
                + "BY a PATTERN (x) DEFINE x AS a > 0))) THEN SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 16 | SELECT with no columns"},
            {"SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 1))",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
        });
    }

    @Test
    public void scheduleNullWritesAnAlertOnNewData() {
        engine.execute("CREATE OR REPLACE TABLE t(a INT)");
        engine.execute("CREATE ALERT anul WAREHOUSE = COMPUTE_WH SCHEDULE = NULL IF (EXISTS (SELECT * FROM t)) THEN "
            + "SELECT 1");
        engine.execute("ALTER ALERT anul UNSET SCHEDULE, COMMENT");
        engine.execute("ALTER ALERT anul SET SCHEDULE = NULL");
        engine.execute("SHOW ALERTS LIKE 'ANUL'");
        assertAnswer("null|suspended", "SELECT \"schedule\", \"state\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
