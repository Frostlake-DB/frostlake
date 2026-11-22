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

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deprecated {@code ResultSet.getBigDecimal(column, scale)} answers the value at the scale asked for, rounded
 * half up (live-verified through the account's driver).
 */
public class ScaledBigDecimalReadTest extends BaseJdbcTest {

    /** The value of a one-row query's first column read at a scale, by index and by label. */
    @SuppressWarnings("deprecation")
    private String read(final String expression, final int scale) throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT " + expression + " AS v")) {
            assertTrue(rs.next());
            final BigDecimal byIndex = rs.getBigDecimal(1, scale);
            final BigDecimal byLabel = rs.getBigDecimal("V", scale);
            assertEquals(byIndex, byLabel);
            return byIndex == null ? null : byIndex.toPlainString();
        }
    }

    /** A value with more digits than the scale is rounded to it. */
    @Test
    public void aWiderValueIsRounded() throws SQLException {
        assertEquals("1.1", read("1.123456789012::NUMBER(20,12)", 1));
        assertEquals("1.5", read("1.50::NUMBER(10,2)", 1));
        assertEquals("1.3", read("1.25::NUMBER(10,2)", 1));
        assertEquals("-1.3", read("-1.25::NUMBER(10,2)", 1));
        assertEquals("2", read("1.5::NUMBER(10,1)", 0));
    }

    /** A narrower value is widened to the scale. */
    @Test
    public void aNarrowerValueIsWidened() throws SQLException {
        assertEquals("7.000", read("7", 3));
        assertEquals("1.500", read("1.5::NUMBER(10,1)", 3));
    }

    /** NULL stays NULL. */
    @Test
    public void nullStaysNull() throws SQLException {
        assertNull(read("NULL::NUMBER(10,2)", 1));
    }
}
