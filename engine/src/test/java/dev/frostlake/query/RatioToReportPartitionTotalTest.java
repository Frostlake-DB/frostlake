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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RATIO_TO_REPORT adds a partition's total up once and divides every row by it. Its answers must be the ones the
 * per-row path gives ({@code execution.window.frameVectors} off, where each row re-reads the whole partition): the
 * same values at the same scale and in the same carrier, over NUMBER, FLOAT, DECFLOAT, text and expression
 * arguments, NULL cells, partitions whose total is zero or NULL, and an ORDER BY inside OVER.
 */
public class RatioToReportPartitionTotalTest {

    private static final int ROWS = 600;

    private DatabaseEngine totalEngine;
    private DatabaseEngine rowPathEngine;

    @AfterEach
    public void teardown() {
        if (totalEngine != null) {
            totalEngine.shutdown();
        }
        if (rowPathEngine != null) {
            rowPathEngine.shutdown();
        }
    }

    private DatabaseEngine engineWithFlag(final boolean frameVectors) {
        final Properties overrides = new Properties();
        overrides.setProperty(EngineConfig.PROP_EXECUTION_WINDOW_FRAME_VECTORS, String.valueOf(frameVectors));
        final DatabaseEngine engine = new DatabaseEngine(new EngineConfig(overrides));
        engine.execute("CREATE DATABASE ratio_db");
        engine.execute("USE DATABASE ratio_db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("""
            CREATE TABLE t (id INTEGER, g INTEGER, n NUMBER(10, 2), f FLOAT, d DECFLOAT, s VARCHAR)
            """);
        final StringBuilder insert = new StringBuilder("INSERT INTO t VALUES ");
        for (int i = 0; i < ROWS; i++) {
            if (i > 0) {
                insert.append(", ");
            }
            // Group 3 sums to zero on every column, group 4 holds only NULLs; the others mix NULLs in.
            final int g = i % 5;
            final String n;
            final String f;
            final String d;
            final String s;
            if (g == 3) {
                final int sign = i % 2 == 0 ? 1 : -1;
                n = String.valueOf(sign * 2.5);
                f = String.valueOf(sign * 0.75);
                d = String.valueOf(sign * 7);
                s = "'" + sign * 4 + "'";
            } else if (g == 4) {
                n = "NULL";
                f = "NULL";
                d = "NULL";
                s = "NULL";
            } else {
                n = i % 7 == 0 ? "NULL" : (i % 101) + ".25";
                f = i % 11 == 0 ? "NULL" : String.valueOf(i * 0.1);
                d = i % 13 == 0 ? "NULL" : (i % 50) + "e-3";
                s = i % 17 == 0 ? "NULL" : "'" + (i % 9) + "'";
            }
            insert.append('(').append(i).append(", ").append(g).append(", ").append(n).append(", ").append(f)
                .append(", ").append(d).append(", ").append(s).append(')');
        }
        engine.execute(insert.toString());
        return engine;
    }

    private List<Row> assertSameOnBothPaths(final String sql) {
        if (totalEngine == null) {
            totalEngine = engineWithFlag(true);
            rowPathEngine = engineWithFlag(false);
        }
        final ResultSet withTotals = totalEngine.executeQuery(sql);
        final ResultSet perRow = rowPathEngine.executeQuery(sql);
        assertEquals(perRow.getRows().size(), withTotals.getRows().size(), sql);
        for (int i = 0; i < withTotals.getRows().size(); i++) {
            final List<Object> expected = perRow.getRows().get(i).getValues();
            final List<Object> actual = withTotals.getRows().get(i).getValues();
            assertEquals(expected, actual, sql + " row " + i);
            for (int c = 0; c < expected.size(); c++) {
                if (expected.get(c) != null) {
                    assertEquals(expected.get(c).getClass(), actual.get(c).getClass(), sql + " row " + i + " col " + c);
                }
            }
        }
        return withTotals.getRows();
    }

    @Test
    public void numberSharesMatch() {
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(n) OVER () FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(n) OVER (PARTITION BY g) FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(n * 2 + 1) OVER (PARTITION BY g) FROM t ORDER BY id");
    }

    @Test
    public void floatSharesMatch() {
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(f) OVER () FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(f) OVER (PARTITION BY g) FROM t ORDER BY id");
    }

    @Test
    public void decfloatSharesMatch() {
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(d) OVER () FROM t ORDER BY id");
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(d) OVER (PARTITION BY g) FROM t ORDER BY id");
    }

    @Test
    public void textSharesMatch() {
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(s) OVER (PARTITION BY g) FROM t ORDER BY id");
    }

    @Test
    public void orderedWindowSharesMatch() {
        assertSameOnBothPaths("SELECT id, RATIO_TO_REPORT(n) OVER (PARTITION BY g ORDER BY id DESC) FROM t ORDER BY id");
    }

    @Test
    public void severalCallsInOneSelectMatch() {
        assertSameOnBothPaths("""
            SELECT id, RATIO_TO_REPORT(n) OVER (PARTITION BY g), RATIO_TO_REPORT(f) OVER (PARTITION BY g),
                   RATIO_TO_REPORT(n) OVER (), RATIO_TO_REPORT(d) OVER (PARTITION BY g)
            FROM t ORDER BY id
            """);
    }

    @Test
    public void zeroAndNullTotalsAndNullCellsAreNull() {
        final List<Row> rows = assertSameOnBothPaths("""
            SELECT id, g, n, RATIO_TO_REPORT(n) OVER (PARTITION BY g), RATIO_TO_REPORT(f) OVER (PARTITION BY g),
                   RATIO_TO_REPORT(d) OVER (PARTITION BY g), RATIO_TO_REPORT(s) OVER (PARTITION BY g)
            FROM t ORDER BY id
            """);
        BigDecimal groupZeroShares = BigDecimal.ZERO;
        for (final Row row : rows) {
            final int g = ((Number) row.getValue(1)).intValue();
            if (g == 3 || g == 4) {
                for (int c = 3; c < 7; c++) {
                    assertNull(row.getValue(c), "row " + row.getValue(0) + " col " + c);
                }
            } else if (row.getValue(2) == null) {
                assertNull(row.getValue(3), "row " + row.getValue(0));
            } else if (g == 0) {
                groupZeroShares = groupZeroShares.add((BigDecimal) row.getValue(3));
            }
        }
        // The shares of one partition add up to one, give or take each share's rounding to its declared scale.
        assertEquals(1.0, groupZeroShares.doubleValue(), 1e-4);
    }
}
