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

package dev.frostlake.geo;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GEOGRAPHY edges are great-circle arcs, live-verified: a point on the straight longitude/latitude line between two
 * far vertices is far from the arc, a polygon's long east-west edges bow toward the pole, and an edge between
 * longitudes 170 and -170 crosses the antimeridian.
 */
public class GeoSphericalEdgesTest extends MeasuredCellsTestSupport {

    @Test
    public void greatCircleEdges() {
        assertCells(new String[][] {
            {"SELECT ST_DWITHIN(TO_GEOGRAPHY('POINT(0 40)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'), "
                + "1000)",
                "false"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('POINT(0 40)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-50 40,50 40,50 60,-50 60,-50 40))'), "
                + "TO_GEOGRAPHY('POINT(0 41)'))",
                "false"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(170 0,-170 0)'), TO_GEOGRAPHY('POINT(180 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-50 40,50 40,50 60,-50 60,-50 40))'), "
                + "TO_GEOGRAPHY('POINT(0 65)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-50 40,50 40,50 60,-50 60,-50 40))'), "
                + "TO_GEOGRAPHY('POINT(0 55)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(170 1,-170 -1)'), TO_GEOGRAPHY('LINESTRING(180"
                + " 5,180 -5)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(-50 52.5, 50 52.5)'), "
                + "TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "false"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 30, 0 60)'), TO_GEOGRAPHY('LINESTRING(-50 "
                + "40, 50 40)'))",
                "true"},
            {"SELECT ST_DWITHIN(TO_GEOGRAPHY('POINT(0 40)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'), "
                + "1400000)",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(5"
                + " 10.05)'))",
                "false"},
            {"SELECT ST_LENGTH(TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "7990915.48187297"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 80,90 80,180 80,-90 80,0 80))'), "
                + "TO_GEOGRAPHY('POINT(0 90)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 80,90 80,180 80,-90 80,0 80))'), "
                + "TO_GEOGRAPHY('POINT(45 85)'))",
                "true"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POLYGON((0 0,1 0,1 1,0 1,0 0))'), TO_GEOGRAPHY('POLYGON((3 "
                + "0,4 0,4 1,3 1,3 0))'))",
                "222356.327811102"},
            {"SELECT ST_DWITHIN(TO_GEOGRAPHY('POINT(0 52)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'), "
                + "60000)",
                "false"},
            {"SELECT ST_DWITHIN(TO_GEOGRAPHY('POINT(0 52)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'), "
                + "61000)",
                "true"},
            {"SELECT ST_DISJOINT(TO_GEOGRAPHY('POINT(0 40)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0,0 10)'), TO_GEOGRAPHY('POLYGON((-5 2,5 2,5"
                + " 4,-5 4,-5 2))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-10 -10,10 -10,10 10,-10 10,-10 -10))'), "
                + "TO_GEOGRAPHY('LINESTRING(-5 0,5 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-10 -10,10 -10,10 10,-10 10,-10 -10))'), "
                + "TO_GEOGRAPHY('POLYGON((-5 -5,5 -5,5 5,-5 5,-5 -5))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-10 -10,10 -10,10 10,-10 10,-10 -10))'), "
                + "TO_GEOGRAPHY('LINESTRING(-5 9.9,5 9.9)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-10 -10,10 -10,10 10,-10 10,-10 -10))'), "
                + "TO_GEOGRAPHY('LINESTRING(-5 -9.9,5 -9.9)'))",
                "true"},
        });
    }

    /**
     * Distances to and between great-circle edges; the account prints 15 significant digits and the sphere's last
     * bits may differ, so each answer is compared within a relative 1e-9.
     */
    @Test
    public void distancesToGreatCircleEdges() {
        final String[][] cases = {
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 40)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "1395084.92324878"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 60)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "828817.100300897"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 52)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "60743.709118973"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOGRAPHY('LINESTRING(10 10, 20 20)'))",
                "1568523.01876774"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(1 1)'), TO_GEOGRAPHY('LINESTRING(0 0, 2 0)'))",
                "111195.101177484"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('LINESTRING(-50 60, 50 60)'), TO_GEOGRAPHY('LINESTRING(-50 40, 50 40)'))",
                "1751789.29083884"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 70)'), "
                + "TO_GEOGRAPHY('POLYGON((-50 40,50 40,50 60,-50 60,-50 40))'))",
                "40094.159720258"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 10)'), TO_GEOGRAPHY('LINESTRING(170 0,-170 0)'))",
                "18446595.1931794"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(179 1)'), TO_GEOGRAPHY('POINT(-179 1)'))",
                "222356.327811101"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(5 -1)'), TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'))",
                "111195.101177484"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(5 11)'), TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'))",
                "107033.841801073"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 89)'), TO_GEOGRAPHY('LINESTRING(-90 80, 90 80)'))",
                "111195.101177484"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 40)'), TO_GEOGRAPHY('POINT(1 41)'))",
                "139688.853803867"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(5 5)'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'))",
                "110771.9275222"},
            {"SELECT ST_DISTANCE(TO_GEOGRAPHY('POLYGON((0 0,1 0,1 1,0 1,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((3 0,4 0,4 1,3 1,3 0))'))",
                "222356.327811102"},
            {"SELECT ST_DISTANCE(TO_GEOMETRY('LINESTRING(0 0,10 10)'), TO_GEOMETRY('LINESTRING(0 10,4 6)'))",
                "1.414213562"},
        };
        for (final String[] c : cases) {
            final double expected = Double.parseDouble(c[1]);
            final double actual = ((Number) engine.executeQuery(c[0]).getRows().get(0).getValue(0)).doubleValue();
            assertEquals(expected, actual, Math.abs(expected) * 1e-9, c[0]);
        }
    }
}
