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

package dev.frostlake.executor.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A function argument refused for its value, read over JDBC and as a block handler's SQLCODE and SQLSTATE
 * (live-verified): an argument taken only as a constant is 1015, a constant the function does not take 1046 and a value
 * a running call does not take 100050, all 22023 - the counts of MAX_BY, MIN_BY and APPROX_TOP_K, percentiles,
 * NTH_VALUE, LAG, LEAD, LISTAGG's separator and GENERATOR's row count among them.
 */
public class ArgumentValueHandlerCodesTest extends MeasuredCellsTestSupport {

    @Test
    public void aggregateAndWindowArguments() {
        engine.execute("CREATE OR REPLACE TABLE t (g INT, o INT, x VARCHAR, p FLOAT)");
        engine.execute("INSERT INTO t VALUES (1,1,'a',0.5),(1,2,'b',0.5),(2,5,'d',0.5)");
        assertCells(new String[][] {
            {"SELECT MAX_BY(x, o, 0) FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'MAX_BY', parameter 3: N "
                + "(last argument) must be a positive number"},
            {"SELECT MAX_BY(x, o, 1001) FROM t",
                "REFUSED SQL compilation error: | Invalid value [1001] for function 'MAX_BY', parameter 3: N "
                + "(last argument) cannot be larger than 1000"},
            {"SELECT MIN_BY(x, o, NULL) FROM t",
                "REFUSED SQL compilation error: | Invalid value [null] for function 'MIN_BY', parameter 3: N "
                + "(last argument) must be non-null"},
            {"SELECT MAX_BY(x, o, g) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'T.G'"},
            {"SELECT MIN_BY(x, o, 2.5) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MIN_BY needs to be constant, found "
                + "'CAST(2.5 AS NUMBER(18,0))'"},
            {"SELECT MAX_BY(x, o, 0) OVER () FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'MAX_BY', parameter 3: N "
                + "(last argument) must be a positive number"},
            {"SELECT MIN_BY(x, o, g) OVER () FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MIN_BY needs to be constant, found "
                + "'T.G'"},
            {"SELECT APPROX_TOP_K(x, 0) FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'APPROX_TOP_K', parameter 1:"
                + " Number of items must be a positive integer"},
            {"SELECT APPROX_TOP_K(x, 100001) FROM t",
                "REFUSED SQL compilation error: | Invalid value [100001] for function 'APPROX_TOP_K', "
                + "parameter 1: Number of items cannot be larger than 100000"},
            {"SELECT APPROX_TOP_K(x, g) FROM t",
                "REFUSED SQL compilation error: | argument 2 to function APPROX_TOP_K needs to be constant, "
                + "found 'T.G'"},
            {"SELECT APPROX_TOP_K(x, 1.5) FROM t",
                "REFUSED SQL compilation error: | Invalid value [CAST(1.5 AS NUMBER(18,0))] for function "
                + "'{1}', parameter {2}: {3}"},
            {"SELECT APPROX_TOP_K(x, 2, 0) FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'APPROX_TOP_K', parameter 2:"
                + " Number of counters must be a positive integer"},
            {"SELECT APPROX_PERCENTILE(o, 2) FROM t",
                "REFUSED SQL compilation error: | Invalid value [2] for function 'APPROX_PERCENTILE', "
                + "parameter 2: Percentile must be between 0 and 1 inclusive."},
            {"SELECT APPROX_PERCENTILE(o, '0.5') FROM t",
                "REFUSED SQL compilation error: | Invalid value ['0.5'] for function 'APPROX_PERCENTILE', "
                + "parameter 2: Percentile must be a numeric value."},
            {"SELECT APPROX_PERCENTILE(o, p) FROM t",
                "REFUSED SQL compilation error: | Invalid value [T.P] for function 'APPROX_PERCENTILE', "
                + "parameter 2: Percentile must be a constant numberic value between 0 and 1 inclusive."},
            {"SELECT PERCENTILE_CONT(p) WITHIN GROUP (ORDER BY o) FROM t",
                "REFUSED SQL compilation error: | argument 1 to function PERCENTILE_CONT needs to be "
                + "constant, found 'T.P'"},
            {"SELECT PERCENTILE_CONT(2) WITHIN GROUP (ORDER BY o) FROM t",
                "REFUSED Invalid parameter value: 2. Reason: percentile must be between 0 and 1"},
            {"SELECT PERCENTILE_DISC(p) WITHIN GROUP (ORDER BY o) FROM t",
                "REFUSED SQL compilation error: | argument 1 to function PERCENTILE_DISC needs to be "
                + "constant, found 'T.P'"},
            {"SELECT NTILE(0) OVER (ORDER BY o) FROM t",
                "REFUSED Invalid parameter value: 0. Reason: NTILE argument must be at least 1"},
            {"SELECT LAG(x, g) OVER (ORDER BY o) FROM t",
                "REFUSED SQL compilation error: | argument 2 to function LAG needs to be constant, found "
                + "'T.G'"},
            {"SELECT NTH_VALUE(x, g) OVER (ORDER BY o) FROM t",
                "REFUSED SQL compilation error: | argument 2 to function NTH_VALUE needs to be constant, "
                + "found 'T.G'"},
            {"SELECT NTH_VALUE(x, 0) OVER (ORDER BY o) FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'NTH_VALUE', parameter 2: N "
                + "(i.e., second argument) cannot be zero."},
            {"SELECT LISTAGG(x, x) FROM t",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'T.X'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => -1))",
                "REFUSED SQL compilation error: | Invalid value [-1] for function 'GENERATOR', parameter "
                + "ROWCOUNT:  must be non-negative."},
            {"SELECT MAX_BY(x, o, 2, 3) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[MAX_BY(T.X, T.O, 2, 3)] expected 3, got 4"},
            {"SELECT MAX_BY(x) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [MAX_BY(T.X)], expected 2, got 1"},
            {"SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(x, 10), g) FROM t GROUP BY g",
                "REFUSED SQL compilation error: | Invalid value [T.G] for function '{1}', parameter {2}: {3}"},
            {"SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(o), 1.5) FROM t",
                "REFUSED SQL compilation error: | Invalid value [1.5] for function "
                + "'APPROX_PERCENTILE_ESTIMATE', parameter 2: Percentile must be between 0 and 1 inclusive."},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT PERCENTILE_CONT(2) WITHIN GROUP "
                + "(ORDER BY o) FROM t);\n  RETURN 'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' ||"
                + " SQLSTATE || ' ' || SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: 2. Reason: percentile must be between 0 and 1"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT NTILE(0) OVER (ORDER BY o) FROM "
                + "t);\n  RETURN 'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: 0. Reason: NTILE argument must be at least 1"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT LAG(x, -1) OVER (ORDER BY o) FROM "
                + "t);\n  RETURN 'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM;\nEND;\n$$",
                "ok"},
        });
    }

    @Test
    public void otherFunctionArguments() {
        engine.execute("CREATE OR REPLACE TABLE t (g INT, o INT, x VARCHAR, p FLOAT)");
        engine.execute("INSERT INTO t VALUES (1,1,'a',0.5),(1,2,'b',0.5),(2,5,'d',0.5)");
        assertCells(new String[][] {
            {"SELECT TIME_SLICE('2024-01-01'::TIMESTAMP, 0, 'DAY')",
                "REFUSED SQL compilation error: | invalid value [0] for parameter 'slice-length'"},
            {"SELECT RANDSTR(-1, 1)",
                "REFUSED Invalid parameter value: -1. Reason: length must not be negative"},
            {"SELECT SHA2('a', 100)",
                "REFUSED Invalid parameter value: 100. Reason: invalid SHA2 digest size"},
            {"SELECT BITSHIFTLEFT(1, -1)",
                "REFUSED Invalid parameter value: -1. Reason: shift parameter must be greater or equal to "
                + "zero"},
            {"SELECT ZIPF(-1, 10, 1)",
                "REFUSED Invalid parameter value: -1. Reason: alpha must be >= 0.0"},
            {"SELECT UNIFORM(NULL, 1, 1)",
                "REFUSED Invalid parameter value: NULL. Reason: a must not be NULL"},
            {"SELECT REGEXP_LIKE('a', 'a', 'z')",
                "REFUSED Invalid parameter value: z. Reason: Unknown parameter: 'z'"},
            {"SELECT SEQ1(2)",
                "REFUSED Invalid parameter value: 2. Reason: sign must be 0 or 1"},
            {"SELECT SYSTEM$GET_TAG(NULL, 't', 'table')",
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG needs to be constant,"
                + " found 'null'"},
            {"SELECT GET_DDL('table', 't', 'maybe')",
                "REFUSED SQL compilation error: | Invalid value [CAST('maybe' AS BOOLEAN)] for function '2', "
                + "parameter IMPORT_DDL: constant arguments expected"},
            {"SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('nostream')",
                "REFUSED SQL compilation error: | Invalid value ['nostream'] for function "
                + "'SYSTEM$STREAM_GET_TABLE_TIMESTAMP', parameter 1: must be a valid stream name"},
            {"SELECT PERCENTILE_CONT(2) WITHIN GROUP (ORDER BY o) OVER () FROM t",
                "REFUSED Invalid parameter value: 2. Reason: percentile must be between 0 and 1"},
            {"SELECT NTILE(-1) OVER (ORDER BY o) FROM t",
                "REFUSED Invalid parameter value: -1. Reason: NTILE argument must be at least 1"},
            {"SELECT LEAD(x, g) OVER (ORDER BY o) FROM t",
                "REFUSED SQL compilation error: | argument 2 to function LEAD needs to be constant, found "
                + "'T.G'"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT "
                + "TIME_SLICE('2024-01-01'::TIMESTAMP, 0, 'DAY'));\n  RETURN 'ok';\nEXCEPTION\n  WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM;\nEND;\n$$",
                "1008 22023 SQL compilation error: | invalid value [0] for parameter 'slice-length'"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT RANDSTR(-1, 1));\n  RETURN "
                + "'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: -1. Reason: length must not be negative"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT SHA2('a', 100));\n  RETURN "
                + "'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: 100. Reason: invalid SHA2 digest size"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT BITSHIFTLEFT(1, -1));\n  RETURN "
                + "'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: -1. Reason: shift parameter must be greater or equal "
                + "to zero"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT ZIPF(-1, 10, 1));\n  RETURN "
                + "'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: -1. Reason: alpha must be >= 0.0"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT UNIFORM(NULL, 1, 1));\n  RETURN "
                + "'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: NULL. Reason: a must not be NULL"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT REGEXP_LIKE('a', 'a', 'z'));\n  "
                + "RETURN 'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: z. Reason: Unknown parameter: 'z'"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT SEQ1(2));\n  RETURN "
                + "'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: 2. Reason: sign must be 0 or 1"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT PERCENTILE_CONT(2) WITHIN GROUP "
                + "(ORDER BY o) OVER () FROM t);\n  RETURN 'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE "
                + "|| ' ' || SQLSTATE || ' ' || SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: 2. Reason: percentile must be between 0 and 1"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  LET r RESULTSET := (SELECT NTILE(-1) OVER (ORDER BY o) FROM "
                + "t);\n  RETURN 'ok';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM;\nEND;\n$$",
                "100050 22023 Invalid parameter value: -1. Reason: NTILE argument must be at least 1"},
        });
    }
}
