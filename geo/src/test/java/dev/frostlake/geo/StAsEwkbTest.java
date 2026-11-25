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
 * ST_ASEWKB writes a value's little-endian EWKB (live-verified): the WKB with the SRID flag raised in its outermost
 * type word and the SRID behind it, a GEOMETRY of SRID 0 included; a shape with a third coordinate keeps its ISO type
 * code ({@code 01E9030020…}), a collection's members carry no flag, an empty GEOGRAPHY MultiPoint read from GeoJSON is
 * refused, and a value that is no geospatial value at all is NULL.
 */
public class StAsEwkbTest extends MeasuredCellsTestSupport {

    @Test
    public void theExtendedWkb() {
        assertCells(new String[][] {
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POINT(1 2)'))",
                "0101000020E6100000000000000000F03F0000000000000040"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('POINT(1 2)'))",
                "010100002000000000000000000000F03F0000000000000040"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('SRID=3857;POINT(1 2)'))",
                "0101000020110F0000000000000000F03F0000000000000040"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POINT(1 2 3)'))",
                "REFUSED Error parsing Geo input: POINT(1 2 3). Did not recognize valid GeoJSON, (E)WKT or "
                + "(E)WKB."},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('POINTZ(1 2 3)'))",
                "01E903002000000000000000000000F03F00000000000000400000000000000840"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('LINESTRING(1 2, 3 4)'))",
                "0102000020E610000002000000000000000000F03F000000000000004000000000000008400000000000001040"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POLYGON((0 0, 1 0, 1 1, 0 0))'))",
                "0103000020E6100000010000000400000000000000000000000000000000000000000000000000F03F0000000000000000000000000000F03F000000000000F03F00000000000000000000000000000000"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('MULTIPOINT((1 2), (3 4))'))",
                "0104000020E6100000020000000101000000000000000000F03F0000000000000040010100000000000000000008400000000000001040"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('MULTILINESTRING((1 2, 3 4), (5 6, 7 8))'))",
                "0105000020E610000002000000010200000002000000000000000000F03F000000000000004000000000000008400000000000001040010200000002000000000000000000144000000000000018400000000000001C400000000000002040"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 0)))'))",
                "0106000020E6100000010000000103000000010000000400000000000000000000000000000000000000000000000000F03F0000000000000000000000000000F03F000000000000F03F00000000000000000000000000000000"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 2), LINESTRING(1 2, 3 4))'))",
                "0107000020E6100000020000000101000000000000000000F03F0000000000000040010200000002000000000000000000F03F000000000000004000000000000008400000000000001040"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "REFUSED Error converting GeoJSON to WKB: GeoJSON::MultiPoint: 'coordinates' must not be "
                + "empty."},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POINT EMPTY'))",
                "null"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('LINESTRING EMPTY'))",
                "null"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('GEOMETRYCOLLECTION EMPTY'))",
                "null"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('SRID=4326;LINESTRING(1 2 3, 4 5 6)'))",
                "REFUSED Error parsing Geo input: SRID=4326;LINESTRING(1 2 3, 4 5 6). Did not recognize valid"
                + " GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(1 2 3), POINT(4 5 6))'))",
                "REFUSED Error parsing Geo input: GEOMETRYCOLLECTION(POINT(1 2 3), POINT(4 5 6)). Did not "
                + "recognize valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASEWKB(NULL)",
                "null"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY(NULL))",
                "null"},
            {"SELECT TYPEOF(ST_ASEWKB(TO_GEOGRAPHY('POINT(1 2)'))::VARIANT)",
                "BINARY"},
            {"SELECT "
                + "ST_ASEWKB(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"MultiPoint\",\"coordinates\":[]}]}'))",
                "REFUSED Error converting GeoJSON to WKB: GeoJSON::GeometryCollection::MultiPoint: "
                + "'coordinates' must not be empty."},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POINT(1 2)'), 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ST_ASEWKB(TO_GEOGRAPHY('POINT(1 2)'), 1)] expected 1, got 2"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('POLYGON((0 0 1, 1 0 1, 1 1 1, 0 0 1))'))",
                "REFUSED Error parsing Geo input: POLYGON((0 0 1, 1 0 1, 1 1 1, 0 0 1)). Did not recognize "
                + "valid GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('SRID=0;POINT(1 2)'))",
                "010100002000000000000000000000F03F0000000000000040"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('SRID=3857;POINT(1 2)'))",
                "0101000000000000000000F03F0000000000000040"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POINT(1 2)')) = ST_ASWKB(TO_GEOGRAPHY('POINT(1 2)'))",
                "false"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('MULTIPOINT EMPTY'))",
                "null"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POLYGON EMPTY'))",
                "null"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('SRID=32633;GEOMETRYCOLLECTION(POINT(1 2), LINESTRING(1 2, 3 "
                + "4))'))",
                "0107000020797F0000020000000101000000000000000000F03F0000000000000040010200000002000000000000000000F03F000000000000004000000000000008400000000000001040"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('MULTIPOINT((1 2 3), (3 4 5))'))",
                "REFUSED Error parsing Geo input: MULTIPOINT((1 2 3), (3 4 5)). Did not recognize valid "
                + "GeoJSON, (E)WKT or (E)WKB."},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1.5e0,2]}'))",
                "0101000020E6100000000000000000F83F0000000000000040"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('POINT(1 2)', 3857))",
                "0101000020110F0000000000000000F03F0000000000000040"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('MULTIPOINT Z((1 2 3), (4 5 6))'))",
                "01EC030020000000000200000001E9030000000000000000F03F0000000000000040000000000000084001E9030000000000000000104000000000000014400000000000001840"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('GEOMETRYCOLLECTION Z(POINT Z(1 2 3))'))",
                "01EF030020000000000100000001E9030000000000000000F03F00000000000000400000000000000840"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1,2,3]}'))",
                "01E9030020E6100000000000000000F03F00000000000000400000000000000840"},
            {"SELECT "
                + "ST_ASEWKB(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[4,5,6]]}'))",
                "01EA030020E610000002000000000000000000F03F00000000000000400000000000000840000000000000104000000000000014400000000000001840"},
            {"SELECT ST_ASEWKB(TO_GEOMETRY('LINESTRING Z(1 2 3, 4 5 6)', 32633))",
                "01EA030020797F000002000000000000000000F03F00000000000000400000000000000840000000000000104000000000000014400000000000001840"},
            {"SELECT ST_ASEWKT(TO_GEOMETRY(ST_ASEWKB(TO_GEOMETRY('SRID=3857;POINT(1 2)'))))",
                "SRID=3857;POINT(1 2)"},
            {"SELECT ST_ASEWKB(TO_GEOGRAPHY('POLYGON((0 0, 1 0, 1 1, 0 0), (0.1 0.1, 0.2 0.1, 0.2 0.2, 0.1"
                + " 0.1))'))",
                "0103000020E6100000020000000400000000000000000000000000000000000000000000000000F03F0000000000000000000000000000F03F000000000000F03F00000000000000000000000000000000040000009A9999999999B93F9A9999999999B93F9A9999999999C93F9A9999999999B93F9A9999999999C93F9A9999999999C93F9A9999999999B93F9A9999999999B93F"},
        });
    }
}
