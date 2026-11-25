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
 * ST_COVERS / ST_COVEREDBY, the SRID agreement of GEOMETRY relations, and points carrying a third (Z) coordinate
 * through WKT, WKB and GeoJSON (live-verified).
 */
public class GeoCoversAndZTest extends MeasuredCellsTestSupport {

    @Test
    public void covers() {
        assertCells(new String[][] {
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POINT(5 "
                + "0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POINT(5 "
                + "5)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POINT(15 "
                + "5)'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(0 0,10 0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POLYGON((0 "
                + "0,10 0,10 10,0 10,0 0))'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('LINESTRING(0 0,10 0)'), TO_GEOMETRY('POINT(0 0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('LINESTRING(0 0,10 0)'), TO_GEOMETRY('POINT(5 0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('LINESTRING(0 0,10 0)'), TO_GEOMETRY('LINESTRING(0 0,5 0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('POINT(1 2)'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(2 2,4 2,4 4,2 4,2 2))'), "
                + "TO_GEOMETRY('POINT(3 3)'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(2 2,4 2,4 4,2 4,2 2))'), "
                + "TO_GEOMETRY('POINT(2 3)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POINT(5 0)'), TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 "
                + "0))'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('MULTIPOINT((0 0),(10 10))'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOMETRY('LINESTRING(5 5,15 5)'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 1))'), TO_GEOMETRY('POINT(1 1)'))",
                "REFUSED Operation 'ST_COVERS/ST_COVEREDBY' is not supported for GeometryCollection and "
                + "FeatureCollection."},
            {"SELECT ST_COVERS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 1))'))",
                "REFUSED Operation 'ST_COVERS/ST_COVEREDBY' is not supported for GeometryCollection and "
                + "FeatureCollection."},
            {"SELECT ST_COVERS(TO_GEOMETRY('POINT(1 1)'), NULL)",
                "null"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POLYGON((0 "
                + "0,5 0,5 5,0 5,0 0))'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POLYGON((5 "
                + "5,15 5,15 15,5 15,5 5))'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOMETRY('LINESTRING(0 0,10 0)'), TO_GEOMETRY('LINESTRING(0 0,10 0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('POINT EMPTY'))",
                "null"},
            {"SELECT ST_COVERS(TO_GEOMETRY('SRID=4326;POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))",
                "REFUSED Incompatible SRID: 4326 and 0"},
            {"SELECT ST_COVEREDBY(TO_GEOMETRY('POINT(5 0)'), TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 "
                + "0))'))",
                "true"},
            {"SELECT ST_COVEREDBY(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOMETRY('POINT(5 "
                + "0)'))",
                "false"},
            {"SELECT ST_COVEREDBY(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 1))'), TO_GEOMETRY('POINT(1 "
                + "1)'))",
                "REFUSED Operation 'ST_COVERS/ST_COVEREDBY' is not supported for GeometryCollection and "
                + "FeatureCollection."},
            {"SELECT ST_COVEREDBY(TO_GEOMETRY('LINESTRING(0 0,10 0)'), TO_GEOMETRY('LINESTRING(0 0,10 "
                + "0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(5 "
                + "5)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(0 "
                + "0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(0 "
                + "5)'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(5 "
                + "0)'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(1 1,2 2)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('LINESTRING(0 0,10 0)'), TO_GEOGRAPHY('POINT(0 0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('LINESTRING(0 0,10 0)'), TO_GEOGRAPHY('LINESTRING(0 0,10 0)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POINT(1 1)'), TO_GEOGRAPHY('POINT(1 1)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 1))'), TO_GEOGRAPHY('POINT(1 1)'))",
                "REFUSED Operation 'ST_COVERS' is not supported for GeometryCollection and FeatureCollection."},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POINT(1 1)'), TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 1))'))",
                "REFUSED Operation 'ST_COVERS' is not supported for GeometryCollection and FeatureCollection."},
            {"SELECT ST_COVEREDBY(TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 1))'), TO_GEOGRAPHY('POINT(1 "
                + "1)'))",
                "REFUSED Operation 'ST_COVERS' is not supported for GeometryCollection and FeatureCollection."},
            {"SELECT ST_COVEREDBY(TO_GEOGRAPHY('POINT(5 5)'), TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0"
                + " 0))'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('POLYGON((0 0,5 0,5 5,0 5,0 0))'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('MULTIPOINT((1 1),(20 20))'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(10 "
                + "10)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), TO_GEOGRAPHY('POINT(5 "
                + "10)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(0 0,0 10)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(0 5,5 5)'))",
                "false"},
            {"SELECT ST_CONTAINS(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0))'), "
                + "TO_GEOGRAPHY('LINESTRING(0 5,5 5)'))",
                "false"},
            {"SELECT ST_COVERS(TO_GEOGRAPHY('POINT(1 1)'), TO_GEOGRAPHY('POINT EMPTY'))",
                "null"},
            {"SELECT TYPEOF(ST_COVERS(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))::VARIANT)",
                "BOOLEAN"},
        });
    }

    @Test
    public void relationSrid() {
        assertCells(new String[][] {
            {"SELECT ST_CONTAINS(TO_GEOMETRY('SRID=4326;POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))",
                "REFUSED Incompatible SRID: 4326 and 0"},
            {"SELECT ST_INTERSECTS(TO_GEOMETRY('SRID=4326;POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))",
                "REFUSED Incompatible SRID: 4326 and 0"},
            {"SELECT ST_DISTANCE(TO_GEOMETRY('SRID=4326;POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))",
                "REFUSED Incompatible SRID: 4326 and 0"},
            {"SELECT ST_WITHIN(TO_GEOMETRY('POINT(1 1)'), TO_GEOMETRY('SRID=3857;POINT(1 1)'))",
                "REFUSED Incompatible SRID: 3857 and 0"},
            {"SELECT ST_DISJOINT(TO_GEOMETRY('SRID=4326;POINT(1 1)'), TO_GEOMETRY('POINT(1 1)'))",
                "REFUSED Incompatible SRID: 4326 and 0"},
            {"SELECT ST_COVEREDBY(TO_GEOMETRY('SRID=4326;POINT(1 1)'), TO_GEOMETRY('SRID=3857;POINT(1 "
                + "1)'))",
                "REFUSED Incompatible SRID: 3857 and 4326"},
            {"SELECT ST_COVERS(TO_GEOMETRY('SRID=4326;POINT(1 1)'), TO_GEOMETRY('SRID=4326;POINT(1 1)'))",
                "true"},
            {"SELECT ST_COVERS(TO_GEOMETRY('SRID=4326;GEOMETRYCOLLECTION(POINT(1 1))'), "
                + "TO_GEOMETRY('POINT(1 1)'))",
                "REFUSED Incompatible SRID: 4326 and 0"},
            {"SELECT ST_COVERS(TO_GEOMETRY('SRID=4326;POINT(1 1)'), NULL)",
                "null"},
            {"SELECT ST_COVERS(TO_GEOMETRY('SRID=4326;POINT EMPTY'), TO_GEOMETRY('POINT(1 1)'))",
                "null"},
        });
    }

    @Test
    public void zCoordinates() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z(1 2 3)'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINTZ(1 2 3)'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT(1 2 3)'))",
                "REFUSED Error parsing Geo input: POINT(1 2 3). Did not recognize valid GeoJSON, (E)WKT or "
                + "(E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT Z(1 2 3)'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('LINESTRING Z(1 2 3,4 5 6)'))",
                "LINESTRINGZ(1 2 3,4 5 6)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POLYGON Z((0 0 1,1 0 1,1 1 1,0 0 1))'))",
                "POLYGONZ((0 0 1,1 0 1,1 1 1,0 0 1))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('MULTIPOINT Z((1 2 3),(4 5 6))'))",
                "MULTIPOINTZ((1 2 3),(4 5 6))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('GEOMETRYCOLLECTION Z(POINT Z(1 2 3))'))",
                "GEOMETRYCOLLECTIONZ(POINTZ(1 2 3))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('01E9030000000000000000F03F00000000000000400000000000000840'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('POINT Z(1 2 3)'))",
                "01E9030000000000000000F03F00000000000000400000000000000840"},
            {"SELECT ST_ASEWKT(TO_GEOMETRY('POINT Z(1 2 3)'))",
                "SRID=0;POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[1,2,3]}'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT M(1 2 3)'))",
                "REFUSED Error parsing Geo input: POINT M(1 2 3). Did not recognize valid GeoJSON, (E)WKT or "
                + "(E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT ZM(1 2 3 4)'))",
                "REFUSED Error parsing Geo input: POINT ZM(1 2 3 4). Did not recognize valid GeoJSON, (E)WKT "
                + "or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('LINESTRING(1 2 3,4 5)'))",
                "REFUSED Error parsing Geo input: LINESTRING(1 2 3,4 5). Did not recognize valid GeoJSON, "
                + "(E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('LINESTRING Z(1 2,4 5)'))",
                "REFUSED Error parsing Geo input: LINESTRING Z(1 2,4 5). Did not recognize valid GeoJSON, "
                + "(E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[4,5]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::LineString: points in 'coordinates' should have"
                + " the same dimension."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z EMPTY'))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('0101000080000000000000F03F00000000000000400000000000000840'))",
                "REFUSED Error parsing Geo input: 0101000080000000000000F03F00000000000000400000000000000840."
                + " Did not recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z(1.5 2 -3.25)'))",
                "POINTZ(1.5 2 -3.25)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z(1 2 3)')::VARCHAR::GEOMETRY)",
                "REFUSED SQL compilation error: | invalid type [CAST(TO_GEOMETRY('POINT Z(1 2 3)') AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'"},
            {"SELECT TO_VARCHAR(TO_GEOMETRY('POINT Z(1 2 3)'))",
                "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(TO_GEOMETRY('POINT Z(1 2 3)'))] "
                + "for parameter 'TO_VARCHAR'"},
            {"SELECT ST_ASWKT(ST_CENTROID(TO_GEOMETRY('LINESTRING Z(0 0 0,2 2 2)')))",
                "POINT(1 1)"},
            {"SELECT ST_NPOINTS(TO_GEOMETRY('LINESTRING Z(0 0 0,2 2 2)'))",
                "2"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('SRID=4326;POINT Z(1 2 3)'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASEWKT(TO_GEOGRAPHY('POINT Z(1 2 3)'))",
                "SRID=4326;POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[1,2,3,4]}'))",
                "REFUSED Invalid GeoJSON. Point array can have at most 3 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT Z(1 2 3))'))",
                "REFUSED Error parsing Geo input: GEOMETRYCOLLECTION(POINT Z(1 2 3)). Did not recognize valid"
                + " GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT Z(1 2 3),POINT(1 2))'))",
                "REFUSED Error parsing Geo input: GEOMETRYCOLLECTION(POINT Z(1 2 3),POINT(1 2)). Did not "
                + "recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('GEOMETRYCOLLECTION Z(POINT(1 2))'))",
                "REFUSED Error parsing Geo input: GEOMETRYCOLLECTION Z(POINT(1 2)). Did not recognize valid "
                + "GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('GEOMETRYCOLLECTION Z(POINT(1 2 3))'))",
                "REFUSED Error parsing Geo input: GEOMETRYCOLLECTION Z(POINT(1 2 3)). Did not recognize valid"
                + " GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 2),LINESTRING Z(1 2 3,4 5 6))'))",
                "REFUSED Error parsing Geo input: GEOMETRYCOLLECTION(POINT(1 2),LINESTRING Z(1 2 3,4 5 6)). "
                + "Did not recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKB(TO_GEOMETRY('GEOMETRYCOLLECTION Z(POINT Z(1 2 3))'))",
                "01EF0300000100000001E9030000000000000000F03F00000000000000400000000000000840"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT Z(1 2 3),POINT(1 2))'))",
                "REFUSED Error parsing Geo input: GEOMETRYCOLLECTION(POINT Z(1 2 3),POINT(1 2)). Did not "
                + "recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKB(TO_GEOMETRY('MULTIPOINT Z((1 2 3))'))",
                "01EC0300000100000001E9030000000000000000F03F00000000000000400000000000000840"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('LINESTRING Z(1 2 3,4 5 6)'))",
                "01EA03000002000000000000000000F03F00000000000000400000000000000840000000000000104000000000000014400000000000001840"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('POLYGON Z((0 0 1,1 0 1,1 1 1,0 0 1))'))",
                "01EB030000010000000400000000000000000000000000000000000000000000000000F03F000000000000F03F0000000000000000000000000000F03F000000000000F03F000000000000F03F000000000000F03F00000000000000000000000000000000000000000000F03F"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('MULTILINESTRING Z((1 2 3,4 5 6))'))",
                "01ED0300000100000001EA03000002000000000000000000F03F00000000000000400000000000000840000000000000104000000000000014400000000000001840"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('MULTIPOLYGON Z(((0 0 1,1 0 1,1 1 1,0 0 1)))'))",
                "01EE0300000100000001EB030000010000000400000000000000000000000000000000000000000000000000F03F000000000000F03F0000000000000000000000000000F03F000000000000F03F000000000000F03F000000000000F03F00000000000000000000000000000000000000000000F03F"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('MULTIPOINT Z(1 2 3,4 5 6)'))",
                "MULTIPOINTZ((1 2 3),(4 5 6))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('point z (1 2 3)'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT  Z  (1 2 3)'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('MULTILINESTRING Z((1 2 3,4 5 6),(7 8 9,1 1 1))'))",
                "MULTILINESTRINGZ((1 2 3,4 5 6),(7 8 9,1 1 1))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('MULTIPOLYGON Z(((0 0 1,1 0 1,1 1 1,0 0 1)))'))",
                "MULTIPOLYGONZ(((0 0 1,1 0 1,1 1 1,0 0 1)))"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1,2,3]}'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('LINESTRING Z(1.5 2 3,4 5 6.25)'))",
                "LINESTRINGZ(1.5 2 3,4 5 6.25)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('01E9030000000000000000F03F00000000000000400000000000000840'))",
                "POINTZ(1 2 3)"},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('01EA03000002000000000000000000F03F0000000000000040000000000000084000000000000010400000000000001440000000000000184000'))",
                "REFUSED Error parsing Geo input: "
                + "01EA03000002000000000000000000F03F0000000000000040000000000000084000000000000010400000000000001440000000000000184000."
                + " Did not recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('01EA03000002000000000000000000F03F00000000000000400000000000000840000000000000104000000000000014400000000000001840'))",
                "LINESTRINGZ(1 2 3,4 5 6)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('0101000000000000000000F03F0000000000000040'))",
                "POINT(1 2)"},
            {"SELECT ST_Z(TO_GEOMETRY('POINT Z(1 2 3)'))",
                "REFUSED SQL compilation error: | Unknown function ST_Z."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z(1e2 2 3e-1)'))",
                "POINTZ(100 2 0.3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINTZ EMPTY'))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z (1 2 3)'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z((1 2 3))'))",
                "REFUSED Error parsing Geo input: POINT Z((1 2 3)). Did not recognize valid GeoJSON, (E)WKT "
                + "or (E)WKB."},
            {"SELECT ST_ASEWKT(TO_GEOMETRY('SRID=3857;LINESTRING Z(1 2 3,4 5 6)'))",
                "SRID=3857;LINESTRINGZ(1 2 3,4 5 6)"},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('01EB03000001000000010000000400000000000000000000000000000000000000000000000000F03F000000000000F03F0000000000000000000000000000F03F000000000000F03F000000000000F03F000000000000F03F00000000000000000000000000000000000000000000F03F'))",
                "REFUSED Error parsing Geo input: "
                + "01EB03000001000000010000000400000000000000000000000000000000000000000000000000F03F000000000000F03F0000000000000000000000000000F03F000000000000F03F000000000000F03F000000000000F03F00000000000000000000000000000000000000000000F03F."
                + " Did not recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('01EC03000001000000' || "
                + "'01E9030000000000000000F03F00000000000000400000000000000840'))",
                "MULTIPOINTZ((1 2 3))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('01EF03000001000000' || "
                + "'01E9030000000000000000F03F00000000000000400000000000000840'))",
                "GEOMETRYCOLLECTIONZ(POINTZ(1 2 3))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('01EF03000001000000' || "
                + "'0101000000000000000000F03F0000000000000040'))",
                "GEOMETRYCOLLECTION(POINT(1 2))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('010700000001000000' || "
                + "'01E9030000000000000000F03F00000000000000400000000000000840'))",
                "GEOMETRYCOLLECTIONZ(POINTZ(1 2 3))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('01EC03000001000000' || "
                + "'0101000000000000000000F03F0000000000000040'))",
                "MULTIPOINT((1 2))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('00000003E93FF000000000000040000000000000004008000000000000'))",
                "POINTZ(1 2 3)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('MULTIPOINT Z((1 2 3),(4 5))'))",
                "REFUSED Error parsing Geo input: MULTIPOINT Z((1 2 3),(4 5)). Did not recognize valid "
                + "GeoJSON, (E)WKT or (E)WKB."},
        });
    }

    @Test
    public void zDimensions() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[4,5]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::LineString: points in 'coordinates' should have"
                + " the same dimension."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2],[4,5,6]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::LineString: points in 'coordinates' should have"
                + " the same dimension."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[[1,2,3],[4,5]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::MultiPoint::Point: points in 'coordinates' "
                + "should have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[[0,0,1],[1,0],[1,1,1],[0,0,1]]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::Polygon::Loop: points in 'coordinates' should "
                + "have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[[0,0,1],[9,0,1],[9,9,1],[0,0,1]],[[1,1],[2,1],[2,2],[1,1]]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::Polygon::Loop: points in 'coordinates' should "
                + "have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiLineString\",\"coordinates\":[[[1,2,3],[4,5,6]],[[1,2],[4,5]]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::MultiLineString::LineString: points in "
                + "'coordinates' should have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0,1],[1,0,1],[1,1,1],[0,0,1]]],[[[5,5],[6,5],[6,6],[5,5]]]]}'))",
                "REFUSED Geometry validation failed: GeoJSON::MultiPolygon::Polygon::Loop: points in "
                + "'coordinates' should have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[1,2,3]},{\"type\":\"Point\",\"coordinates\":[1,2]}]}'))",
                "REFUSED Geometry validation failed: GeoJSON::GeometryCollection::Point: points in "
                + "'coordinates' should have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[1,2]},{\"type\":\"Point\",\"coordinates\":[1,2,3]}]}'))",
                "REFUSED Geometry validation failed: GeoJSON::GeometryCollection::Point: points in "
                + "'coordinates' should have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[4,5]]}]}'))",
                "REFUSED Geometry validation failed: GeoJSON::GeometryCollection::LineString: points in "
                + "'coordinates' should have the same dimension."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2,3,4],[4,5,6,7]]}'))",
                "REFUSED Invalid GeoJSON. Point array can have at most 3 elements."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[4,5,6,7]]}'))",
                "REFUSED Invalid GeoJSON. Point array can have at most 3 elements."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[400,5]]}'))",
                "REFUSED GeoJSON::LineString: Invalid Lng/Lat pair: '400,5'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[400,2],[4,5,6]]}'))",
                "REFUSED GeoJSON::LineString: Invalid Lng/Lat pair: '400,2'."},
            {"SELECT "
                + "ST_ASWKB(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[1,2,3]},{\"type\":\"Point\",\"coordinates\":[1,2]}]}'))",
                "REFUSED Geometry validation failed: GeoJSON::GeometryCollection::Point: points in "
                + "'coordinates' should have the same dimension."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[[1,2,3],[4,5,6]]}'))",
                "MULTIPOINTZ((1 2 3),(4 5 6))"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('010400000002000000' || "
                + "'01E9030000000000000000F03F00000000000000400000000000000840' || "
                + "'0101000000000000000000F03F0000000000000040'))",
                "REFUSED Geometry validation failed: GeoJSON::MultiPoint::Point: points in 'coordinates' "
                + "should have the same dimension."},
            {"SELECT ST_LENGTH(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[4,5]]}')) "
                + "> 0",
                "true"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('LINESTRING Z(0 0 5,1 1 7)'))",
                "LINESTRINGZ(0 0 5,1 1 7)"},
            {"SELECT ST_COVERS(TO_GEOMETRY('POLYGON Z((0 0 1,10 0 1,10 10 1,0 10 1,0 0 1))'), "
                + "TO_GEOMETRY('POINT(5 5)'))",
                "true"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT Z(1 2 3)')) = "
                + "ST_ASWKT(TO_GEOMETRY(ST_ASWKB(TO_GEOMETRY('POINT Z(1 2 3)'))))",
                "true"},
        });
    }
}
