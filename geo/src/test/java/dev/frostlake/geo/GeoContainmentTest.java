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

/**
 * ST_CONTAINS / ST_WITHIN / ST_INTERSECTS / ST_DISJOINT decided on whole edges, live-verified: a line or polygon that
 * leaves a concave container between its vertices, or covers one of its holes, is not contained; a point on a
 * polygon's boundary or at a line's end point is not contained, while a point contains itself and a line contains
 * its inner points; a GEOGRAPHY polygon does not contain a value with a vertex on the inside of one of its edges; a
 * GeometryCollection is refused on either side of ST_CONTAINS / ST_WITHIN.
 */
public class GeoContainmentTest extends MeasuredCellsTestSupport {

    @Test
    public void edgesAndBoundariesDecideContainment() {
        assertCells(new String[][] {
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 8,8 8,8 2,0 2,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(1 1,1 9)'))",
                "false"},
            {"SELECT ST_WITHIN(TO_GEOMETRY('LINESTRING(1 1,1 9)'), TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 "
                + "10,0 8,8 8,8 2,0 2,0 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'), "
                + "TO_GEOMETRY('POLYGON((1 1,9 1,9 9,1 9,1 1))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'), "
                + "TO_GEOMETRY('POLYGON((1 1,3 1,3 3,1 3,1 1))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('POLYGON((1 1,9 1,9 9,1 9,1 1))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('POLYGON((0 0,5 0,5 5,0 5,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(0 0,5 5)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('MULTIPOINT((1 1),(10 5))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'), "
                + "TO_GEOMETRY('LINESTRING(1 5,9 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'), "
                + "TO_GEOMETRY('POINT(4 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('MULTIPOLYGON(((0 0,2 0,2 2,0 2,0 0)),((3 0,5 0,5 2,3 2,3 "
                + "0)))'), TO_GEOMETRY('LINESTRING(1 1,4 1)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POINT(10 "
                + "5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(0 0,10 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,2 2)'), TO_GEOMETRY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,2 2)'), TO_GEOMETRY('POINT(0 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,2 2)'), TO_GEOMETRY('LINESTRING(0 0,1 1)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,2 2)'), TO_GEOMETRY('LINESTRING(0 0,2 2)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('MULTIPOINT((1 1),(2 2))'), TO_GEOMETRY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('POINT(1 2)'))",
                "false"},
            {"SELECT ST_WITHIN(TO_GEOMETRY('POINT(10 5)'), TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 "
                + "0))'))",
                "false"},
            {"SELECT ST_WITHIN(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('LINESTRING(0 0,2 2)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('POINT(10 5)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOMETRY('LINESTRING(0 0,2 2)'), TO_GEOMETRY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POINT(0 "
                + "0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(5 0,5 10)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,2 2,4 0)'), TO_GEOMETRY('POINT(2 2)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 1),LINESTRING(2 2,3 3))'))",
                "REFUSED Operation 'ST_CONTAINS/ST_WITHIN' is not supported for GeometryCollection and "
                + "FeatureCollection."},
            {"SELECT ST_INTERSECTS(TO_GEOMETRY('LINESTRING(0 0,10 0)'), TO_GEOMETRY('LINESTRING(5 0,15 "
                + "0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POINT(10 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(0"
                + " 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(0 0,10 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POINT(1 1)'), TO_GEOGRAPHY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 2)'), TO_GEOGRAPHY('POINT(0 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('POINT(1 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 8,8 8,8 2,0 2,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(1 1,1 9)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'),"
                + " TO_GEOGRAPHY('POLYGON((1 1,9 1,9 9,1 9,1 1))'))",
                "false"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POINT(0 0)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POINT(10 5)'))",
                "true"},
            {"SELECT ST_WITHIN(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 "
                + "0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(0 0,5 5)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,5 0,5 5,0 5,0 0))'))",
                "false"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0,2 2)'), TO_GEOGRAPHY('POINT(0 0)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('POINT(1 0)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('LINESTRING(1 0,1 "
                + "1)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0,1 1)'), TO_GEOGRAPHY('LINESTRING(1 1,2 "
                + "0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('MULTIPOINT((1 1),(2 2))'), TO_GEOGRAPHY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(5"
                + " 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(5"
                + " 10)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(0"
                + " 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POINT(10 10)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POINT(10 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(0"
                + " 10)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('LINESTRING(0 0,2 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('LINESTRING(0 0,1 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 0,4 0)'), TO_GEOGRAPHY('POINT(2 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(5"
                + " 5)'))",
                "true"},
            {"SELECT ST_WITHIN(TO_GEOGRAPHY('POINT(5 5)'), TO_GEOGRAPHY('POINT(5 5)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 1,5 1,5 5,0 5,0 1))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((1 0,5 0,5 5,1 5,1 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,10 0,10 5,0 5,0 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((1 1,5 1,5 5,1 5,1 1))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,5 1,1 5,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((5 0,6 1,4 1,5 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(5 0,5 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(0 5,5 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(1 0,5 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(1 0,5 0,5 5)'))",
                "false"},
            {"SELECT ST_WITHIN(TO_GEOGRAPHY('POLYGON((0 0,5 0,5 5,0 5,0 0))'), TO_GEOGRAPHY('POLYGON((0 "
                + "0,10 0,10 10,0 10,0 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 1),POINT(2 2))'))",
                "REFUSED Operation 'ST_CONTAINS' is not supported for GeometryCollection and "
                + "FeatureCollection."},
            {"SELECT ST_WITHIN(TO_GEOGRAPHY('POINT(1 1)'), TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 "
                + "1),POINT(2 2))'))",
                "REFUSED Operation 'ST_CONTAINS' is not supported for GeometryCollection and "
                + "FeatureCollection."},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 1),POINT(2 2))'))",
                "true"},
            {"SELECT ST_WITHIN(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 "
                + "1),POINT(2 2))'))",
                "REFUSED Operation 'ST_CONTAINS/ST_WITHIN' is not supported for GeometryCollection and "
                + "FeatureCollection."},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((1 1,10 1,10 5,1 5,1 1))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((1 1,5 1,5 10,1 10,1 1))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((-175 -5,175 -5,175 5,-175 5,-175 -5))'), "
                + "TO_GEOGRAPHY('POINT(180 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POINT(-175 -5)'))",
                "false"},
            {"SELECT ST_DISJOINT(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('POINT(1 0)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,10 0,5 5,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 10,0 0,10 0,10 10,0 10))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,0 10,10 10,10 0,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(0 0,10 0,5 5)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('LINESTRING(1 0,3 "
                + "0)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(1 -1,1 1)'), TO_GEOGRAPHY('LINESTRING(0 0,2 "
                + "0)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(1 0,1 1)'), TO_GEOGRAPHY('LINESTRING(0 0,2 "
                + "0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,5 5,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(5 1,5 5)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,5 1,5 5,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('MULTIPOINT((1 1),(5 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('MULTIPOINT((1 1),(0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 0,4 0)'), TO_GEOGRAPHY('LINESTRING(1 0,3 "
                + "0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,4 0)'), TO_GEOGRAPHY('LINESTRING(0 0,4 0,2 "
                + "0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,4 0)'), TO_GEOGRAPHY('LINESTRING(4 0,0 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('MULTIPOINT((1 1),(2 2))'), TO_GEOGRAPHY('MULTIPOINT((1 "
                + "1),(2 2))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('MULTIPOINT((1 1),(2 2))'), TO_GEOGRAPHY('MULTIPOINT((1 "
                + "1),(3 3))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POINT(1 1)'), TO_GEOGRAPHY('LINESTRING(1 1,2 2)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 0)'), TO_GEOGRAPHY('POLYGON((0 0,10 0,10 "
                + "10,0 10,0 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'),"
                + " TO_GEOGRAPHY('POINT(4 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'),"
                + " TO_GEOGRAPHY('POLYGON((1 1,3 1,3 3,1 3,1 1))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'),"
                + " TO_GEOGRAPHY('LINESTRING(1 5,9 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('MULTIPOLYGON(((0 0,2 0,2 2,0 2,0 0)),((3 0,5 0,5 2,3 2,3 "
                + "0)))'), TO_GEOGRAPHY('LINESTRING(1 1,4 1)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('MULTIPOLYGON(((0 0,2 0,2 2,0 2,0 0)),((3 0,5 0,5 2,3 2,3 "
                + "0)))'), TO_GEOGRAPHY('MULTIPOINT((1 1),(4 1))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('POLYGON((0 0,10 0,5 5,0 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'), "
                + "TO_GEOMETRY('POLYGON((4 4,6 4,6 6,4 6,4 4))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))'),"
                + " TO_GEOGRAPHY('POLYGON((4 4,6 4,6 6,4 6,4 4))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(0 0,10 0,5 5)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,4 0)'), TO_GEOMETRY('LINESTRING(4 0,0 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('LINESTRING(1 1,2 2)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('MULTIPOINT((1 1),(2 2))'), TO_GEOMETRY('MULTIPOINT((1 1),(2 "
                + "2))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,2 0,2 2,0 0)'), TO_GEOMETRY('POINT(0 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('LINESTRING(0 0,2 0,2 2,0 0)'), TO_GEOGRAPHY('POINT(0 0)'))",
                "false"},
            {"SELECT ST_INTERSECTS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('LINESTRING(0 0,2 2)'))",
                "true"},
            {"SELECT ST_INTERSECTS(TO_GEOMETRY('POLYGON((0 1,3 1,3 2,0 2,0 1))'), TO_GEOMETRY('POLYGON((1 "
                + "0,2 0,2 3,1 3,1 0))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,0 10,10 10,10 0,0 0))'), TO_GEOGRAPHY('POINT(5"
                + " 5)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,5 5,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(1 6,9 6)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,5 5,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(1 4,9 4)'))",
                "true"},
            {"SELECT ST_WITHIN(TO_GEOMETRY('MULTIPOINT((1 1),(20 20))'), TO_GEOMETRY('POLYGON((0 0,10 0,10"
                + " 10,0 10,0 0))'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('MULTIPOLYGON(((0 0,2 0,2 2,0 2,0 0)),((3 0,5 0,5 2,3 2,3 "
                + "0)))'), TO_GEOMETRY('MULTIPOINT((1 1),(4 1))'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('MULTILINESTRING((0 0,2 0),(2 0,4 0))'), TO_GEOMETRY('POINT(2"
                + " 0)'))",
                "true"},
            {"SELECT ST_CONTAINS(TO_GEOMETRY('LINESTRING(0 0,2 0,2 2,0 0)'), TO_GEOMETRY('LINESTRING(0 0,1"
                + " 0)'))",
                "true"},
        });
    }
}
