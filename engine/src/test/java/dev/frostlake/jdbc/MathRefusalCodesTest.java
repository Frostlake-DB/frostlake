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

package dev.frostlake.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseJdbcTest;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The refusals of a math function's argument and of a window frame a function does not take carry their code and
 * state, to the client's driver and to a block's handler alike (live-verified): a FLOAT operation 100044 / 22P01, a
 * logarithm 100043 / 2201E, a power 100053 / 2201F, a DECFLOAT operation 320002 / 22P01, a DECFLOAT past its range
 * 320001 / P0000, WIDTH_BUCKET's arguments 100150 / 22000, a number past what FACTORIAL or HEX_ENCODE computes
 * 100045 / 22003, and a sliding or cumulative frame 2303 / 0A000.
 */
public class MathRefusalCodesTest extends BaseJdbcTest {

    /** The code, the state and the sentence a statement is refused with. */
    private String refusal(final String sql) {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
        return refused.getErrorCode() + "|" + refused.getSQLState() + "|" + refused.getMessage();
    }

    /** What a block's handler reads of the query's refusal: its SQLCODE, SQLSTATE and SQLERRM. */
    private String handled(final String query) throws SQLException {
        final String block = """
            EXECUTE IMMEDIATE $$
            BEGIN
              LET r VARIANT := (%s);
              RETURN r;
            EXCEPTION
              WHEN OTHER THEN RETURN SQLCODE || '|' || SQLSTATE || '|' || SQLERRM;
            END;
            $$""".formatted(query);
        try (ResultSet rows = statement.executeQuery(block)) {
            rows.next();
            return rows.getString(1);
        }
    }

    @Test
    public void floatingPointOperations() throws SQLException {
        assertEquals("100044|22P01|Invalid floating point operation: sqrt(-4)", refusal("SELECT SQRT(-4)"));
        assertEquals("100044|22P01|Invalid floating point operation: acos(2)", refusal("SELECT ACOS(2)"));
        assertEquals("100044|22P01|Invalid floating point operation: atanh(2)", refusal("SELECT ATANH(2)"));
        assertEquals("100043|2201E|Invalid floating point operation: log(10,-1)", refusal("SELECT LOG(10, -1)"));
        assertEquals("100043|2201E|Invalid floating point operation: log(e,0)", refusal("SELECT LN(0)"));
        assertEquals("100053|2201F|Invalid floating point operation: pow(-8, 0.333333)",
            refusal("SELECT POW(-8, 1/3)"));
        assertEquals("100044|22P01|Invalid floating point operation: sqrt(-4)", handled("SELECT SQRT(-4)"));
        assertEquals("100043|2201E|Invalid floating point operation: log(10,-1)", handled("SELECT LOG(10, -1)"));
        assertEquals("100053|2201F|Invalid floating point operation: pow(-8, 0.333333)",
            handled("SELECT POW(-8, 1/3)"));
    }

    @Test
    public void decfloatOperations() throws SQLException {
        assertEquals("320002|22P01|Decfloat invalid operation: SQRT(-1)", refusal("SELECT SQRT(-1::DECFLOAT)"));
        assertEquals("320002|22P01|Decfloat invalid operation: POW(0, -1)", refusal("SELECT POW(0::DECFLOAT, -1)"));
        assertEquals("320001|P0000|Decfloat out of representable range, operation: EXP(100000)",
            refusal("SELECT EXP(100000::DECFLOAT)"));
        assertEquals("320002|22P01|Decfloat invalid operation: SQRT(-1)", handled("SELECT SQRT(-1::DECFLOAT)"));
        assertEquals("320001|P0000|Decfloat out of representable range, operation: EXP(100000)",
            handled("SELECT EXP(100000::DECFLOAT)"));
    }

    @Test
    public void widthBucketAndOutOfRangeArguments() throws SQLException {
        assertEquals("100150|22000|Invalid argument for width bucket function, want 0 (num_buckets) > 0 and"
            + " 0 (min_value) != 14 (max_value)", refusal("SELECT WIDTH_BUCKET(5, 0, 14, 0)"));
        assertEquals("100150|22000|Invalid argument for width bucket function, want 3 (num_buckets) > 0 and"
            + " 1 (min_value) != 1 (max_value)", handled("SELECT WIDTH_BUCKET(1, 1, 1, 3)"));
        assertEquals("100045|22003|Numeric value is out of range, error: 40 is too large for factorial function.",
            refusal("SELECT FACTORIAL(40)"));
        assertEquals("100045|22003|Numeric value is out of range, error: Input of factorial function can't be"
            + " negative. -1 is invalid.", handled("SELECT FACTORIAL(-1)"));
        assertEquals("100045|22003|Numeric value is out of range, error: 2", refusal("SELECT HEX_ENCODE('a', 2)"));
    }

    @Test
    public void slidingAndCumulativeFrames() throws SQLException {
        statement.execute("CREATE TABLE mrc_t (x FLOAT, y INT)");
        assertEquals("2303|0A000|SQL compilation error: error line 1 at position 35\n"
            + "Sliding window frame unsupported for function CORR",
            refusal("SELECT CORR(y, x) OVER (ORDER BY y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM mrc_t"));
        assertEquals("2303|0A000|SQL compilation error: error line 1 at position 41\n"
            + "Sliding window frame unsupported for function COVAR_SAMP",
            refusal("SELECT COVAR_SAMP(y, x) OVER (ORDER BY y ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM mrc_t"));
        assertEquals("2303|0A000|SQL compilation error: error line 1 at position 34\n"
            + "Sliding window frame unsupported for function MEDIAN",
            refusal("SELECT MEDIAN(y) OVER (ORDER BY y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM mrc_t"));
        assertEquals("2303|0A000|SQL compilation error: error line 1 at position 23\n"
            + "Cumulative window frame unsupported for function COVAR_POP",
            refusal("SELECT COVAR_POP(y, x) OVER (ORDER BY y) FROM mrc_t"));
    }
}
