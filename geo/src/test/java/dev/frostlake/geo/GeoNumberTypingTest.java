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
 * How the numbers of a geospatial value are typed (live-verified). ST_ASGEOJSON hands a GEOMETRY's coordinates out
 * as DOUBLEs whatever its input spelled; a GEOGRAPHY's keep their input's family, descaled the way a JSON reader
 * descales them ({@code 1.0} reads 1, {@code 2.50} reads 2.5), with a GeoJSON number written with an exponent a
 * DOUBLE, which the refusal of a non-array {@code coordinates} names {@code 'REAL'} where a plain number is
 * {@code 'FIXED'}.
 */
public class GeoNumberTypingTest extends MeasuredCellsTestSupport {

    @Test
    public void numbersAsTyped() {
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(ST_ASGEOJSON(TO_GEOMETRY('POINT(1 2)')))",
                "{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"}"},
            {"SELECT TO_VARCHAR(ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1 2)')))",
                "{\"coordinates\":[1,2],\"type\":\"Point\"}"},
            {"SELECT TYPEOF(ST_ASGEOJSON(TO_GEOMETRY('POINT(1 2)')):coordinates[0])",
                "DOUBLE"},
            {"SELECT TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1 2)')):coordinates[0])",
                "INTEGER"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINT(1.5 2.25)'))::VARCHAR",
                "{\"coordinates\":[1.500000000000000e+00,2.250000000000000e+00],\"type\":\"Point\"}"},
                        {"SELECT TO_JSON(ST_ASGEOJSON(TO_GEOMETRY('POINT(1 2)')))",
                "{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"}"},
            {"SELECT TO_JSON(PARSE_JSON('{\"a\":1.0e0}'))",
                "{\"a\":1.000000000000000e+00}"},
            {"SELECT TO_JSON(ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1 2)')))",
                "{\"coordinates\":[1,2],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINT(1 2 3)'))",
                "REFUSED Error parsing Geo input: POINT(1 2 3). Did not recognize valid GeoJSON, (E)WKT or "
                + "(E)WKB."},
            {"SELECT TYPEOF(ST_ASGEOJSON(TO_GEOMETRY('POINT(1 2)')))",
                "OBJECT"},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":1e5}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'REAL'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":1.5}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'FIXED'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":1}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'FIXED'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":1.0e0}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'REAL'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":1e5}')",
                "REFUSED Invalid GeoJSON. 'coordinates' field must be of type ARRAY."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":\"x\"}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'TEXT'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":true}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'BOOLEAN'. Expected: "
                + "'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1e1, 2.5e0]}'))",
                "POINT(10 2.5)"},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[1e1, 2]}')",
                "REFUSED GeoJSON::LineString: 'coordinates' field does not hold arrays of Lng/Lat pairs."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[1,2],1e1]}')",
                "REFUSED GeoJSON::LineString: 'coordinates' field does not hold arrays of Lng/Lat pairs."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":-2.5e-3}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'REAL'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":1e5}'))",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'REAL'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[1e0]}')",
                "REFUSED GeoJSON::Polygon::Loop: 'coordinates' field does not hold arrays of Point."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":12345678901234567890}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'FIXED'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":null}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'NULL_VALUE'. Expected: "
                + "'ARRAY'."},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":{\"a\":1}}')",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'OBJECT'. Expected: "
                + "'ARRAY'."},
            {"SELECT TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.5 2)')):coordinates[0])",
                "DECIMAL"},
            {"SELECT TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.0 2)')):coordinates[0])",
                "INTEGER"},
            {"SELECT "
                + "TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('0101000000000000000000F03F0000000000000040')):coordinates[0])",
                "DOUBLE"},
            {"SELECT "
                + "TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1e1,2]}')):coordinates[0])",
                "DOUBLE"},
            {"SELECT "
                + "TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1.5,2]}')):coordinates[0])",
                "DECIMAL"},
            {"SELECT "
                + "TYPEOF(ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[1,2]}')):coordinates[0])",
                "DOUBLE"},
            {"SELECT "
                + "TYPEOF(ST_ASGEOJSON(TO_GEOMETRY('0101000000000000000000F03F0000000000000040')):coordinates[0])",
                "DOUBLE"},
            {"SELECT TYPEOF(ST_ASGEOJSON(ST_MAKEPOINT(1, 2)):coordinates[0])",
                "DOUBLE"},
            {"SELECT TYPEOF(ST_ASGEOJSON(ST_MAKEGEOMPOINT(1, 2)):coordinates[0])",
                "DOUBLE"},
            {"SELECT TYPEOF(ST_ASGEOJSON(ST_CENTROID(TO_GEOGRAPHY('LINESTRING(0 0, 2 "
                + "0)'))):coordinates[0])",
                "DOUBLE"},
            {"SELECT TYPEOF(ST_ASGEOJSON(ST_CENTROID(TO_GEOMETRY('LINESTRING(0 0, 2 0)'))):coordinates[0])",
                "DOUBLE"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.5 2)'))::VARCHAR",
                "{\"coordinates\":[1.5,2],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('0101000000000000000000F03F0000000000000040'))::VARCHAR",
                "{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"}"},
            {"SELECT "
                + "ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1e1,2.50]}'))::VARCHAR",
                "{\"coordinates\":[1.000000000000000e+01,2.5],\"type\":\"Point\"}"},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1e1,2.50]}')::VARCHAR",
                "REFUSED SQL compilation error: | invalid type "
                + "[CAST(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1e1,2.50]}') AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POLYGON((0 0, 1 0, 1 1, 0 0))'))::VARCHAR",
                "{\"coordinates\":[[[0.000000000000000e+00,0.000000000000000e+00],[1.000000000000000e+00,0.000000000000000e+00],[1.000000000000000e+00,1.000000000000000e+00],[0.000000000000000e+00,0.000000000000000e+00]]],\"type\":\"Polygon\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('MULTIPOINT((1 2), (3 4))'))::VARCHAR",
                "{\"coordinates\":[[1.000000000000000e+00,2.000000000000000e+00],[3.000000000000000e+00,4.000000000000000e+00]],\"type\":\"MultiPoint\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 2), LINESTRING(1 2, 3 "
                + "4))'))::VARCHAR",
                "{\"geometries\":[{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"},{\"coordinates\":[[1.000000000000000e+00,2.000000000000000e+00],[3.000000000000000e+00,4.000000000000000e+00]],\"type\":\"LineString\"}],\"type\":\"GeometryCollection\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINT EMPTY'))::VARCHAR",
                "null"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINTZ(1 2 3)'))::VARCHAR",
                "{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00,3.000000000000000e+00],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINT(-0.5 100000000)'))::VARCHAR",
                "{\"coordinates\":[-5.000000000000000e-01,1.000000000000000e+08],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINT(1e-7 123456789.123)'))::VARCHAR",
                "{\"coordinates\":[1.000000000000000e-07,1.234567891230000e+08],\"type\":\"Point\"}"},
            {"SELECT TO_GEOMETRY('POINT(1 2)')::VARCHAR",
                "REFUSED SQL compilation error: | invalid type [CAST(TO_GEOMETRY('POINT(1 2)') AS "
                + "VARCHAR(134217728))] for parameter 'TO_VARCHAR'"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINT(1 2)')):coordinates[0]::NUMBER(10,2)",
                "1.00"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('SRID=3857;POINT(1 2)'))::VARCHAR",
                "{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"}"},
            {"SELECT TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('MULTIPOINT((1 2), (3 4))')):coordinates[0][0])",
                "INTEGER"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(0.1 2)'))::VARCHAR",
                "{\"coordinates\":[0.1,2],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1e1 2.50)'))::VARCHAR",
                "{\"coordinates\":[10,2.5],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.0 2.000)'))::VARCHAR",
                "{\"coordinates\":[1,2],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.5e1 2E-1)'))::VARCHAR",
                "{\"coordinates\":[15,0.2],\"type\":\"Point\"}"},
            {"SELECT TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.5e1 2E-1)')):coordinates[1])",
                "DECIMAL"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(0.00001 -0.0)'))::VARCHAR",
                "{\"coordinates\":[0.00001,0],\"type\":\"Point\"}"},
            {"SELECT "
                + "ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1.0,2.000]}'))::VARCHAR",
                "{\"coordinates\":[1,2],\"type\":\"Point\"}"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1e1,2.50]}'))",
                "POINT(10 2.5)"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT(0.1 123456789.123)'))",
                "POINT(0.1 123456789.123)"},
            {"SELECT "
                + "ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1E0,-0e0]}'))::VARCHAR",
                "{\"coordinates\":[1.000000000000000e+00,-0.000000000000000e+00],\"type\":\"Point\"}"},
            {"SELECT "
                + "ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[12345678901234567890e-18,1]}'))::VARCHAR",
                "{\"coordinates\":[1.234567890123457e+01,1],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.50 2)')):coordinates[0]::VARCHAR",
                "1.5"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('POINT(1.50 2)')):coordinates[0]::VARCHAR",
                "1.5"},
            {"SELECT "
                + "TYPEOF(ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[12345678901234567890,1]}')):coordinates[0])",
                "REFUSED GeoJSON::Point: Invalid Lng/Lat pair: '1.23457e+19,1'."},
            {"SELECT "
                + "ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1.23456789012345678,1]}'))::VARCHAR",
                "{\"coordinates\":[1.23456789012345678,1],\"type\":\"Point\"}"},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(1.23456789012345678 1)'))::VARCHAR",
                "{\"coordinates\":[1.23456789012345678,1],\"type\":\"Point\"}"},
        });
    }
}
