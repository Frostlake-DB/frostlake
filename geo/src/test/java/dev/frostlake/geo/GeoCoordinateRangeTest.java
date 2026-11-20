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
 * A GEOGRAPHY coordinate must be a longitude in [-180, 180] and a latitude in [-90, 90], live-verified: anything else
 * is refused naming the shape path and the pair to six significant digits, whatever the input form (WKT, GeoJSON,
 * WKB, ST_MAKEPOINT), and TRY_TO_GEOGRAPHY answers NULL.
 */
public class GeoCoordinateRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void outOfRangeCoordinatesAreRefused() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(0 100)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,100'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(200 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '200,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(180 90)'))",
                "POINT(180 90)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(-180 -90)'))",
                "POINT(-180 -90)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(180.0000001 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '180,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(0 -90.5)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,-90.5'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('LINESTRING(0 0, 0 91)'))",
                "REFUSED GeoJSON::LineString: Invalid Lng/Lat pair: '0,91'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[0,100]}'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,100'."},
            {"SELECT ST_ASWKT(TRY_TO_GEOGRAPHY('POINT(0 100)'))",
                "null"},
            {"SELECT ST_ASWKT(ST_MAKEPOINT(0, 100))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,100'."},
            {"SELECT ST_ASWKT(ST_MAKEPOINT(200, 0))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '200,0'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT(200 100)'))",
                "POINT(200 100)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(-200 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '-200,0'."},
            {"SELECT TO_GEOGRAPHY('POINT(0 100)')",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,100'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POLYGON((0 0,200 0,200 10,0 10,0 0))'))",
                "REFUSED GeoJSON::Polygon::Loop: Invalid Lng/Lat pair: '200,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('SRID=4326;POINT(0 100)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,100'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('MULTIPOINT((0 0),(0 95))'))",
                "REFUSED GeoJSON::MultiPoint::Point: Invalid Lng/Lat pair: '0,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('GEOMETRYCOLLECTION(POINT(0 0),POINT(0 95))'))",
                "REFUSED GeoJSON::GeometryCollection::Point: Invalid Lng/Lat pair: '0,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('010100000000000000000000000000000000005940'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,100'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(360 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '360,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(0 1e3)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '0,1000'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(180.5 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '180.5,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(-180.5 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '-180.5,0'."},
            {"SELECT TO_GEOGRAPHY(NULL)",
                "null"},
            {"SELECT ST_ASWKT(ST_POINT(181, 0))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '181,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(540 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '540,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(1000 10)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '1000,10'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('MULTILINESTRING((0 0,1 1),(0 0,0 95))'))",
                "REFUSED GeoJSON::MultiLineString::LineString: Invalid Lng/Lat pair: '0,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((0 0,200 0,1 1,0 0)))'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon::Loop: Invalid Lng/Lat pair: '200,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POLYGON((0 0,10 0,10 10,0 10,0 0),(1 1,2 1,2 95,1 1))'))",
                "REFUSED GeoJSON::Polygon::Loop: Invalid Lng/Lat pair: '2,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[0,0],[0,91]]}'))",
                "REFUSED GeoJSON::LineString: Invalid Lng/Lat pair: '0,91'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(123456.7 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '123457,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(1234567 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '1.23457e+06,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(0 -0.0000123)'))",
                "POINT(0 -1.23e-05)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(0.000012345678 95)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '1.23457e-05,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(200 95)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '200,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('LINESTRING(0 95, 200 0)'))",
                "REFUSED GeoJSON::LineString: Invalid Lng/Lat pair: '0,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('MULTIPOLYGON(((0 0,1 0,1 1,0 0),(0 0,1 0,1 95,0 0)))'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon::Loop: Invalid Lng/Lat pair: '1,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('GEOMETRYCOLLECTION(LINESTRING(0 0,0 95))'))",
                "REFUSED GeoJSON::GeometryCollection::LineString: Invalid Lng/Lat pair: '0,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('GEOMETRYCOLLECTION(POLYGON((0 0,1 0,1 95,0 0)))'))",
                "REFUSED GeoJSON::GeometryCollection::Polygon::Loop: Invalid Lng/Lat pair: '1,95'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(-0.0 -90)'))",
                "POINT(0 -90)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(0 1e-7)'))",
                "POINT(0 1e-07)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('POINT(1e300 0)'))",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '1e+300,0'."},
        });
    }
}
