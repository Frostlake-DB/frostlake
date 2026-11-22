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
 * TO_GEOGRAPHY / TO_GEOMETRY read WKB and EWKB, as BINARY or as hex text, in either byte order, live-verified: a BINARY
 * that is no well-formed WKB is refused with the WKB reader's sentence, the same bytes as hex text with the general
 * geo-input sentence; a shape with no points is NULL; a GEOGRAPHY takes SRID 4326 alone.
 */
public class GeoWkbInputTest extends MeasuredCellsTestSupport {

    @Test
    public void wkbAndEwkbInput() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(ST_ASWKB(TO_GEOGRAPHY('POINT(1 2)'))))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('0101000000000000000000F03F0000000000000040','HEX')))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101000000000000000000F03F0000000000000040'))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('00000000013FF00000000000004000000000000000'))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101000000000000000000f03f0000000000000040'))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKB(TO_GEOGRAPHY('POINT(1 2)'))",
                "0101000000000000000000F03F0000000000000040"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('SRID=3857;POINT(1 2)'))",
                "0101000000000000000000F03F0000000000000040"},
            {"SELECT ST_ASEWKT(TO_GEOMETRY('0101000020110F0000000000000000F03F0000000000000040'))",
                "SRID=3857;POINT(1 2)"},
            {"SELECT ST_ASEWKT(TO_GEOGRAPHY('0101000020E6100000000000000000F03F0000000000000040'))",
                "SRID=4326;POINT(1 2)"},
            {"SELECT ST_ASEWKT(TO_GEOGRAPHY('0101000020110F0000000000000000F03F0000000000000040'))",
                "REFUSED Error parsing Geo input: 0101000020110F0000000000000000F03F0000000000000040. Did not"
                + " recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOMETRY(ST_ASWKB(TO_GEOMETRY('POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 "
                + "4,6 6,4 6,4 4))'))))",
                "POLYGON((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(ST_ASWKB(TO_GEOGRAPHY('MULTIPOLYGON(((0 0,2 0,2 2,0 2,0 0)),((3"
                + " 0,5 0,5 2,3 2,3 0)))'))))",
                "MULTIPOLYGON(((0 0,2 0,2 2,0 2,0 0)),((3 0,5 0,5 2,3 2,3 0)))"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(ST_ASWKB(TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 "
                + "2),LINESTRING(0 0,1 1))'))))",
                "GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,1 1))"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(ST_ASWKB(TO_GEOGRAPHY('MULTILINESTRING((0 0,1 1),(2 2,3 "
                + "3))'))))",
                "MULTILINESTRING((0 0,1 1),(2 2,3 3))"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(ST_ASWKB(TO_GEOGRAPHY('MULTIPOINT((0 0),(1 1))'))))",
                "MULTIPOINT((0 0),(1 1))"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101000000000000000000F03F00000000000000'))",
                "REFUSED Error parsing Geo input: 0101000000000000000000F03F00000000000000. Did not recognize"
                + " valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('0101000000000000000000F03F00000000000000','HEX')))",
                "REFUSED Error parsing WKB input: 'Point' holds less coordinates than specified."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101000000000000000000F03F0000000000000040FF'))",
                "REFUSED Error parsing Geo input: 0101000000000000000000F03F0000000000000040FF. Did not "
                + "recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101000000000000000000F03F0000000000000040 '))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(' 0101000000000000000000F03F0000000000000040'))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0x0101000000000000000000F03F0000000000000040'))",
                "REFUSED Error parsing Geo input: 0x0101000000000000000000F03F0000000000000040. Did not "
                + "recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('00000000013FF00000000000004000000000000000','HEX')))",
                "POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101000080000000000000F03F00000000000000400000000000000840'))",
                "REFUSED Error parsing Geo input: 0101000080000000000000F03F00000000000000400000000000000840."
                + " Did not recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT "
                + "ST_ASEWKT(TO_GEOMETRY(TO_BINARY('0101000020110F0000000000000000F03F0000000000000040','HEX')))",
                "SRID=3857;POINT(1 2)"},
            {"SELECT ST_ASEWKT(TO_GEOMETRY('0101000000000000000000F03F0000000000000040', 3857))",
                "SRID=3857;POINT(1 2)"},
            {"SELECT ST_ASWKT(TRY_TO_GEOGRAPHY('0101000000000000000000F03F00000000000000'))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('010100000000000000000069400000000000000040'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '200,2'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101000000000000000000F8FF0000000000000040'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '-nan,2'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('010100000000000000000000000000000000000000'))",
                "POINT(0 0)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('01010000009A9999999999B93F0000000000000040'))",
                "POINT(0.1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('0101'))",
                "REFUSED Error parsing Geo input: 0101. Did not recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('01020000000000000000'))",
                "REFUSED Error parsing Geo input: 01020000000000000000. Did not recognize valid GeoJSON, "
                + "(E)WKT or (E)WKB."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('010200000000000000'))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('010200000002000000000000000000F03F','HEX')))",
                "REFUSED Error parsing WKB input: 'LineString' holds less points than specified."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('0101000000000000000000F03F0000000000000040FF','HEX')))",
                "REFUSED Error parsing WKB input: Trailing bytes not recognized."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('0109000000000000000000F03F0000000000000040','HEX')))",
                "REFUSED Error parsing WKB input: Type '9' unknown."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('','HEX')))",
                "REFUSED Error parsing WKB input: Input has less than 21 bytes."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('0101000000000000000000F87F000000000000F87F','HEX')))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: 'nan,nan'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY(TO_BINARY('0101000000000000000000F87F000000000000F87F','HEX')))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'nan'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('0101000000000000000000F87F0000000000000040'))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'nan'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('0201000000000000000000F03F0000000000000040','HEX')))",
                "POINT(1 2)"},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('01030000000100000003000000000000000000000000000000000000000000000000000000','HEX')))",
                "REFUSED Error parsing WKB input: 'LineString' holds less points than specified."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('010300000000000000','HEX')))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('010400000000000000','HEX')))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('010700000000000000','HEX')))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('01','HEX')))",
                "REFUSED Error parsing WKB input: Input has less than 21 bytes."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(0.1 0.2)'))",
                "POINT(0.1 0.2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(ST_ASWKB(TO_GEOGRAPHY('POINT(0.1 1e-7)'))))",
                "POINT(0.1 1e-07)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY(ST_ASWKB(TO_GEOMETRY('POINT(1234567.5 -0.00001)'))))",
                "POINT(1234567.5 -1e-05)"},
            {"SELECT ST_ASEWKT(TO_GEOMETRY('0101000020E6100000000000000000F03F0000000000000040'))",
                "SRID=4326;POINT(1 2)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('01020000000100000000000000000000000000000000000000'))",
                "REFUSED Error parsing Geo input: 01020000000100000000000000000000000000000000000000. Did not"
                + " recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('0103000000010000000400000000000000000000000000000000000000000000000000F03F0000000000000000000000000000F03F000000000000F03F00000000000000000000000000000000'))",
                "POLYGON((0 0,1 0,1 1,0 0))"},
            {"SELECT TRY_TO_GEOGRAPHY(TO_BINARY('0101000000000000000000F03F00000000000000','HEX'))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY(TO_BINARY('0101000000000000000000F03F0000000000006940','HEX')))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '1,200'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('010100000000000000000069400000000000006940'))",
                "POINT(200 200)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(nan 1)'))",
                "REFUSED Error parsing Geo input: POINT(nan 1). Did not recognize valid GeoJSON, (E)WKT or "
                + "(E)WKB."},
        });
    }
}
