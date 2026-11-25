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
 * A WITHIN GROUP ordering over a join reads each qualified key from the relation its qualifier names,
 * live-verified: over a self-join {@code ORDER BY x.b, y.b} orders by the left side's {@code b} and then the
 * right side's, for LISTAGG, ARRAY_AGG (DISTINCT too), PERCENTILE_CONT and PERCENTILE_DISC, grouped or over the
 * whole input or as a window, over derived tables, CTEs, comma and outer joins and three-way joins; and a key
 * over a column with a collation compares under the collation of the side it names, not the other side's, in a
 * WITHIN GROUP ordering and in a window's ORDER BY and PARTITION BY alike.
 */
public class WithinGroupJoinKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void qualifiedKeysReadTheirOwnRelation() {
        engine.execute("CREATE TABLE lz(a INT, b INT)");
        engine.execute("INSERT INTO lz VALUES (2,20),(1,10),(3,20),(2,30)");
        engine.execute("CREATE TABLE lw(a INT, b INT)");
        engine.execute("INSERT INTO lw VALUES (2,5),(1,7),(3,1),(2,9)");
        engine.execute("CREATE TABLE lc(a INT, b VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO lc VALUES (1,'b'),(1,'A'),(1,'a'),(1,'B')");
        engine.execute("CREATE TABLE lp(a INT, b VARCHAR)");
        engine.execute("INSERT INTO lp VALUES (1,'b'),(1,'A'),(1,'a'),(1,'B')");
        assertCells(new String[][] {
            {"SELECT LISTAGG(x.a || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.a, y.b) FROM lz x JOIN lz y "
                + "ON x.a = y.a",
                "1-10/2-20/2-20/2-30/2-30/3-20"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b, y.b) FROM lz x JOIN lz y "
                + "ON x.a = y.a",
                "10-10/20-20/20-20/20-30/30-20/30-30"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b DESC, y.b) FROM lz x JOIN "
                + "lz y ON x.a = y.a",
                "30-20/30-30/20-20/20-20/20-30/10-10"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY y.b, x.b) FROM lz x JOIN lz y "
                + "ON x.a = y.a",
                "10-10/20-20/20-20/30-20/20-30/30-30"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY y.b DESC, x.b DESC) FROM lz x "
                + "JOIN lz y ON x.a = y.a",
                "30-30/20-30/30-20/20-20/20-20/10-10"},
            {"SELECT LISTAGG(v, '/') WITHIN GROUP (ORDER BY xb, yb) FROM (SELECT x.b || '-' || y.b v, x.b "
                + "xb, y.b yb FROM lz x JOIN lz y ON x.a = y.a)",
                "10-10/20-20/20-20/20-30/30-20/30-30"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b, y.b) FROM (SELECT a, b "
                + "FROM lz) x JOIN (SELECT a, b FROM lz) y ON x.a = y.a",
                "10-10/20-20/20-20/20-30/30-20/30-30"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY y.b, x.b) FROM (SELECT a, b "
                + "FROM lz) x JOIN (SELECT a, b FROM lz) y ON x.a = y.a",
                "10-10/20-20/20-20/30-20/20-30/30-30"},
            {"WITH x AS (SELECT * FROM lz), y AS (SELECT * FROM lz) SELECT LISTAGG(x.b || '-' || y.b, '/')"
                + " WITHIN GROUP (ORDER BY y.b DESC, x.b) FROM x JOIN y ON x.a = y.a",
                "20-30/30-30/20-20/20-20/30-20/10-10"},
            {"SELECT LISTAGG(lz.b || '-' || lw.b, '/') WITHIN GROUP (ORDER BY lz.b, lw.b DESC) FROM lz "
                + "JOIN lw ON lz.a = lw.a",
                "10-7/20-9/20-5/20-1/30-9/30-5"},
            {"SELECT LISTAGG(DISTINCT y.b, '/') WITHIN GROUP (ORDER BY y.b DESC) FROM lz x JOIN lz y ON "
                + "x.a = y.a",
                "30/20/10"},
            {"SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY y.b) FROM lz x JOIN lw y ON x.a = y.a",
                "6.000"},
            {"SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x.b) FROM lz x JOIN lw y ON x.a = y.a",
                "20"},
            {"SELECT LISTAGG(b, '/') WITHIN GROUP (ORDER BY x.b DESC) FROM lz x",
                "30/20/20/10"},
            {"SELECT LISTAGG(lz.b, '/') WITHIN GROUP (ORDER BY lz.b DESC) FROM lz",
                "30/20/20/10"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b, y.b) FROM lz x, lz y "
                + "WHERE x.a = y.a",
                "10-10/20-20/20-20/20-30/30-20/30-30"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b, y.b) FROM lz x LEFT JOIN "
                + "lz y ON x.a = y.a AND y.b > 10",
                "20-20/20-20/20-30/30-20/30-30"},
            {"SELECT LISTAGG(x.b || '-' || y.b || '-' || z.b, '/') WITHIN GROUP (ORDER BY y.b DESC, z.b "
                + "DESC, x.b DESC) FROM lz x JOIN lz y ON x.a = y.a JOIN lz z ON z.a = y.a",
                "30-30-30/20-30-30/30-30-20/20-30-20/30-20-30/20-20-30/30-20-20/20-20-20/20-20-20/10-10-10"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b + y.b DESC, x.b) FROM lz x"
                + " JOIN lz y ON x.a = y.a",
                "30-30/20-30/30-20/20-20/20-20/10-10"},
            {"SELECT ARRAY_TO_STRING(ARRAY_AGG(x.b || '-' || y.b) WITHIN GROUP (ORDER BY x.b, y.b), '/') "
                + "FROM lz x JOIN lz y ON x.a = y.a",
                "10-10/20-20/20-20/20-30/30-20/30-30"},
            {"SELECT ARRAY_TO_STRING(ARRAY_AGG(y.b) WITHIN GROUP (ORDER BY x.b DESC, y.b), '/') FROM lz x "
                + "JOIN lw y ON x.a = y.a",
                "5/9/1/5/9/7"},
            {"SELECT ARRAY_TO_STRING(ARRAY_AGG(x.b) WITHIN GROUP (ORDER BY x.b DESC), '/') FROM lw x JOIN "
                + "lz y ON x.a = y.a",
                "9/9/7/5/5/1"},
            {"SELECT ARRAY_TO_STRING(ARRAY_AGG(DISTINCT x.b) WITHIN GROUP (ORDER BY x.b DESC), '/') FROM "
                + "lz x JOIN lw y ON x.a = y.a",
                "30/20/10"},
            {"SELECT LISTAGG(x.b || y.b, '/') WITHIN GROUP (ORDER BY x.b, y.b, ASCII(x.b)) FROM lc x JOIN "
                + "lp y ON x.a = y.a",
                "AA/aA/AB/aB/Aa/aa/Ab/ab/BA/bA/BB/bB/Ba/ba/Bb/bb"},
            {"SELECT LISTAGG(x.b || y.b, '/') WITHIN GROUP (ORDER BY y.b, x.b, ASCII(x.b)) FROM lc x JOIN "
                + "lp y ON x.a = y.a",
                "AA/aA/BA/bA/AB/aB/BB/bB/Aa/aa/Ba/ba/Ab/ab/Bb/bb"},
            {"SELECT LISTAGG(x.b || y.b, '/') WITHIN GROUP (ORDER BY y.b, x.b, ASCII(y.b)) FROM lp x JOIN "
                + "lc y ON x.a = y.a",
                "AA/Aa/BA/Ba/aA/aa/bA/ba/AB/Ab/BB/Bb/aB/ab/bB/bb"},
            {"SELECT ARRAY_TO_STRING(ARRAY_AGG(x.b || y.b) WITHIN GROUP (ORDER BY x.b DESC, ASCII(x.b), "
                + "y.b DESC), '/') FROM lc x JOIN lp y ON x.a = y.a",
                "Bb/Ba/BB/BA/bb/ba/bB/bA/Ab/Aa/AB/AA/ab/aa/aB/aA"},
        });
    }

    @Test
    public void groupedAndWindowedCallsReadTheirOwnRelation() {
        engine.execute("CREATE TABLE lz(a INT, b INT)");
        engine.execute("INSERT INTO lz VALUES (2,20),(1,10),(3,20),(2,30)");
        assertCells(new String[][] {
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b DESC, y.b DESC) FROM lz x "
                + "JOIN lz y ON x.a = y.a WHERE x.a = 2 GROUP BY x.a",
                "30-30/30-20/20-30/20-20"},
            {"SELECT ARRAY_TO_STRING(ARRAY_AGG(x.b || '-' || y.b) WITHIN GROUP (ORDER BY y.b DESC, x.b), "
                + "'/') FROM lz x JOIN lz y ON x.a = y.a WHERE x.a = 2 GROUP BY x.a",
                "20-30/30-30/20-20/30-20"},
            {"SELECT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b, y.b DESC) FROM lz x JOIN "
                + "lz y ON x.a = y.a GROUP BY y.a HAVING y.a = 2",
                "20-30/20-20/30-30/30-20"},
            {"SELECT DISTINCT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY x.b, y.b DESC) OVER "
                + "(PARTITION BY x.a) FROM lz x JOIN lz y ON x.a = y.a WHERE x.a = 2",
                "20-30/20-20/30-30/30-20"},
            {"SELECT DISTINCT ARRAY_TO_STRING(ARRAY_AGG(x.b || '-' || y.b) WITHIN GROUP (ORDER BY y.b "
                + "DESC, x.b) OVER (PARTITION BY x.a), '/') FROM lz x JOIN lz y ON x.a = y.a WHERE x.a = 2",
                "20-30/30-30/20-20/30-20"},
            {"SELECT DISTINCT LISTAGG(x.b || '-' || y.b, '/') WITHIN GROUP (ORDER BY y.b, x.b DESC) OVER "
                + "() FROM lz x JOIN lz y ON x.a = y.a",
                "10-10/30-20/20-20/20-20/30-30/20-30"},
        });
    }

    @Test
    public void windowKeysTakeTheirOwnSidesCollation() {
        engine.execute("CREATE TABLE lc(a INT, b VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO lc VALUES (1,'b'),(1,'A'),(1,'a'),(1,'B')");
        engine.execute("CREATE TABLE lp(a INT, b VARCHAR)");
        engine.execute("INSERT INTO lp VALUES (1,'b'),(1,'A'),(1,'a'),(1,'B')");
        assertCells(new String[][] {
            {"SELECT LISTAGG(xb || yb, '/') WITHIN GROUP (ORDER BY rn) FROM (SELECT x.b xb, y.b yb, "
                + "ROW_NUMBER() OVER (ORDER BY x.b, y.b, ASCII(x.b)) rn FROM lc x JOIN lp y ON x.a = y.a)",
                "AA/aA/AB/aB/Aa/aa/Ab/ab/BA/bA/BB/bB/Ba/ba/Bb/bb"},
            {"SELECT LISTAGG(xb || yb, '/') WITHIN GROUP (ORDER BY rn) FROM (SELECT x.b xb, y.b yb, "
                + "ROW_NUMBER() OVER (ORDER BY y.b, x.b, ASCII(x.b)) rn FROM lc x JOIN lp y ON x.a = y.a)",
                "AA/aA/BA/bA/AB/aB/BB/bB/Aa/aa/Ba/ba/Ab/ab/Bb/bb"},
            {"SELECT LISTAGG(xb || yb || c, '/') WITHIN GROUP (ORDER BY ASCII(yb), ASCII(xb)) FROM (SELECT"
                + " x.b xb, y.b yb, COUNT(*) OVER (PARTITION BY y.b) c FROM lc x JOIN lp y ON x.a = y.a)",
                "AA4/BA4/aA4/bA4/AB4/BB4/aB4/bB4/Aa4/Ba4/aa4/ba4/Ab4/Bb4/ab4/bb4"},
            {"SELECT LISTAGG(xb || yb || c, '/') WITHIN GROUP (ORDER BY ASCII(yb), ASCII(xb)) FROM (SELECT"
                + " x.b xb, y.b yb, COUNT(*) OVER (PARTITION BY y.b) c FROM lp x JOIN lc y ON x.a = y.a)",
                "AA8/BA8/aA8/bA8/AB8/BB8/aB8/bB8/Aa8/Ba8/aa8/ba8/Ab8/Bb8/ab8/bb8"},
            {"SELECT LISTAGG(xb || yb || c, '/') WITHIN GROUP (ORDER BY ASCII(yb), ASCII(xb)) FROM (SELECT"
                + " x.b xb, y.b yb, COUNT(*) OVER (PARTITION BY x.b) c FROM lc x JOIN lp y ON x.a = y.a)",
                "AA8/BA8/aA8/bA8/AB8/BB8/aB8/bB8/Aa8/Ba8/aa8/ba8/Ab8/Bb8/ab8/bb8"},
            {"SELECT LISTAGG(xb || yb || r, '/') WITHIN GROUP (ORDER BY ASCII(yb), ASCII(xb)) FROM (SELECT"
                + " x.b xb, y.b yb, DENSE_RANK() OVER (PARTITION BY x.b ORDER BY y.b) r FROM lc x JOIN lp y ON "
                + "x.a = y.a)",
                "AA1/BA1/aA1/bA1/AB2/BB2/aB2/bB2/Aa3/Ba3/aa3/ba3/Ab4/Bb4/ab4/bb4"},
        });
    }
}
