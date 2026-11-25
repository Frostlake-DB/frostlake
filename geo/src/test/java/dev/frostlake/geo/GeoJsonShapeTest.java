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
 * GeoJSON coordinates that do not hold the shape their type names are refused with the reader's own sentence,
 * a GEOMETRY's {@code Invalid GeoJSON. ...} family or a GEOGRAPHY's path down to the fault; a GeometryCollection
 * of no members is NULL and a GEOGRAPHY's empty MultiPoint is a value WKB cannot write. Two geospatial values
 * do not compare: an operator, IN, BETWEEN and IS DISTINCT FROM are refused at their places, a simple CASE or a
 * DECODE as a conversion (live-verified).
 */
public class GeoJsonShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void emptyCoordinatesAndComparisons() {
        engine.execute("CREATE OR REPLACE TABLE tg(g GEOMETRY, h GEOGRAPHY)");
        engine.execute("INSERT INTO tg SELECT TO_GEOMETRY('POINT(1 2)'), TO_GEOGRAPHY('POINT(1 2)')");
        assertCells(new String[][] {
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[]}'))",
                "REFUSED GeoJSON::Point: 'coordinates' malformed. Point array should have 2 elements "
                + "(Lng/Lat)."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"MultiLineString\",\"coordinates\":[]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[]}'))",
                "REFUSED GeoJSON::MultiPolygon: 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[]}'))",
                "null"},
            {"SELECT "
                + "ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[]}]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[]]}'))",
                "REFUSED Invalid GeoJSON. LineString 'coordinates' must have at least 2 elements."},
            {"SELECT ST_ASGEOJSON(TRY_TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[]}'))",
                "null"},
            {"SELECT ST_ASGEOJSON(TRY_TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[]}'))",
                "null"},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[1]}'))",
                "REFUSED Invalid GeoJSON. Point coordinates must have at least 2 elements."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":{}}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' field must be of type ARRAY."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"MultiLineString\",\"coordinates\":[[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[]]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY('{\"coordinates\":[],\"type\":\"Point\"}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASGEOJSON(TO_GEOMETRY(PARSE_JSON('{\"type\":\"Point\",\"coordinates\":[]}')))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT TO_GEOMETRY('POINT EMPTY') = TO_GEOMETRY('POINT EMPTY')",
                "REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for "
                + "function '=': (GEOMETRY, GEOMETRY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') = TO_GEOMETRY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function '=': (GEOMETRY, GEOMETRY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') <> TO_GEOMETRY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function '<>': (GEOMETRY, GEOMETRY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') < TO_GEOMETRY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function '<': (GEOMETRY, GEOMETRY)"},
            {"SELECT TO_GEOGRAPHY('POINT(1 2)') = TO_GEOGRAPHY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for "
                + "function '=': (GEOGRAPHY, GEOGRAPHY)"},
            {"SELECT TO_GEOGRAPHY('POINT(1 2)') != TO_GEOGRAPHY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for "
                + "function '!=': (GEOGRAPHY, GEOGRAPHY)"},
            {"SELECT TO_GEOGRAPHY('POINT(1 2)') >= TO_GEOGRAPHY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for "
                + "function '>=': (GEOGRAPHY, GEOGRAPHY)"},
            {"SELECT TO_GEOGRAPHY('POINT(1 2)') = TO_GEOMETRY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for "
                + "function '=': (GEOGRAPHY, GEOMETRY)"},
            {"SELECT TO_GEOGRAPHY('POINT(1 2)') IS DISTINCT FROM TO_GEOGRAPHY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function 'EQUAL_NULL': (GEOGRAPHY, GEOGRAPHY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') IS NOT DISTINCT FROM TO_GEOMETRY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function 'EQUAL_NULL': (GEOMETRY, GEOMETRY)"},
            {"SELECT EQUAL_NULL(TO_GEOMETRY('POINT(1 2)'), TO_GEOMETRY('POINT(1 2)'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'EQUAL_NULL': (GEOMETRY, GEOMETRY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') IN (TO_GEOMETRY('POINT(1 2)'))",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function 'IN': (GEOMETRY, GEOMETRY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') BETWEEN TO_GEOMETRY('POINT(1 2)') AND TO_GEOMETRY('POINT(1 "
                + "2)')",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function '>=': (GEOMETRY, GEOMETRY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') = 'POINT(1 2)'",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function '=': (GEOMETRY, VARCHAR(10))"},
            {"SELECT 'POINT(1 2)' = TO_GEOGRAPHY('POINT(1 2)')",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function '=': (VARCHAR(10), GEOGRAPHY)"},
            {"SELECT TO_GEOMETRY('POINT(1 2)') = NULL",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function '=': (GEOMETRY, NULL)"},
            {"SELECT GREATEST(TO_GEOMETRY('POINT(1 2)'), TO_GEOMETRY('POINT(1 2)'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'GREATEST': (GEOMETRY, GEOMETRY)"},
            {"SELECT COUNT(DISTINCT g) FROM (SELECT TO_GEOMETRY('POINT(1 2)') g)",
                "1"},
            {"SELECT COUNT(*) FROM tg WHERE g = g",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for "
                + "function '=': (GEOMETRY, GEOMETRY)"},
            {"SELECT COUNT(*) FROM tg a JOIN tg b ON a.h = b.h",
                "REFUSED SQL compilation error: error line 1 at position 43 | Invalid argument types for "
                + "function '=': (GEOGRAPHY, GEOGRAPHY)"},
            {"SELECT ST_ASWKT(g) FROM tg ORDER BY g",
                "REFUSED Expressions of type GEOMETRY cannot be used as ORDER BY keys"},
            {"SELECT COUNT(*) FROM tg GROUP BY g",
                "REFUSED Expressions of type GEOMETRY cannot be used as GROUP BY keys"},
            {"SELECT CASE g WHEN g THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT IFF(g = g, 1, 0) FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 13 | Invalid argument types for "
                + "function '=': (GEOMETRY, GEOMETRY)"},
            {"SELECT DECODE(g, g, 1, 0) FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT MIN(g) FROM tg",
                "REFUSED SQL compilation error: | Function MIN does not support GEOMETRY argument type"},
        });
    }

    @Test
    public void shapeFaults() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2]]}'))",
                "REFUSED Invalid GeoJSON. LineString 'coordinates' must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[],[1,2]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2],[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[[]]]}'))",
                "REFUSED Invalid GeoJSON. Loop 'coordinates' must have at least 4 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[[1,2],[]]]}'))",
                "REFUSED Invalid GeoJSON. Loop 'coordinates' must have at least 4 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[[1]]}'))",
                "REFUSED Invalid GeoJSON. Point coordinates must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[[]]}'))",
                "REFUSED Invalid GeoJSON. Point coordinates must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiLineString\",\"coordinates\":[[[]]]}'))",
                "REFUSED Invalid GeoJSON. LineString 'coordinates' must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiLineString\",\"coordinates\":[[[1,2]]]}'))",
                "REFUSED Invalid GeoJSON. LineString 'coordinates' must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\"}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' field is missing."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":null}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' field must be of type ARRAY."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[[1,2],[3,4],[1,2]]]}'))",
                "REFUSED Invalid GeoJSON. Loop 'coordinates' must have at least 4 elements."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]],[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[1,0],[1,1],[0,0]]],[]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[]}', 4326))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[1,2]},{\"type\":\"LineString\",\"coordinates\":[]}]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[]}'))",
                "null"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' malformed. LineString array should have at least "
                + "2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[]}'))",
                "REFUSED GeoJSON::Polygon: 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "MULTIPOINT EMPTY"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiLineString\",\"coordinates\":[]}'))",
                "REFUSED GeoJSON::MultiLineString: 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[[]]}'))",
                "REFUSED GeoJSON::Polygon::Loop: 'coordinates' malformed. Loop array should have at least 4 "
                + "elements."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[[]]}'))",
                "REFUSED GeoJSON::MultiPoint::Point: 'coordinates' malformed. Point array should have 2 "
                + "elements (Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[]]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' malformed. LineString array should have at least "
                + "2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1]}'))",
                "REFUSED GeoJSON::Point: 'coordinates' malformed. Point array should have 2 elements "
                + "(Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":{}}'))",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'OBJECT'. Expected: "
                + "'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiLineString\",\"coordinates\":[[]]}'))",
                "REFUSED GeoJSON::MultiLineString::LineString: 'coordinates' malformed. LineString array "
                + "should have at least 2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[[]]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon: 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[]]]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon::Loop: 'coordinates' malformed. Loop array should "
                + "have at least 4 elements."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[]}]}'))",
                "REFUSED GeoJSON::GeometryCollection::Point: 'coordinates' malformed. Point array should have"
                + " 2 elements (Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[1,2]]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' malformed. LineString array should have at least "
                + "2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[[[]]]}'))",
                "REFUSED GeoJSON::Polygon::Loop: 'coordinates' malformed. Loop array should have at least 4 "
                + "elements."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[1,2],[]]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' malformed. Point array should have 2 elements "
                + "(Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[[1]]}'))",
                "REFUSED GeoJSON::MultiPoint::Point: 'coordinates' malformed. Point array should have 2 "
                + "elements (Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[[]]}'))",
                "REFUSED GeoJSON::Point: 'coordinates' malformed. Point array should have 2 elements "
                + "(Lng/Lat)."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[1,0],[1,1],[0,0]]],[]]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon: 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiLineString\",\"coordinates\":[[[]]]}'))",
                "REFUSED GeoJSON::MultiLineString::LineString: 'coordinates' malformed. LineString array "
                + "should have at least 2 elements (start/end point)."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]],[]]}'))",
                "REFUSED GeoJSON::Polygon::Loop: 'coordinates' malformed. Loop array should have at least 4 "
                + "elements."},
        });
    }

    @Test
    public void faultOrder() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[200,0],[]]}'))",
                "REFUSED GeoJSON::LineString: Invalid Lng/Lat pair: '200,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[],[200,0]]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' malformed. Point array should have 2 elements "
                + "(Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1],[]]}'))",
                "REFUSED Invalid GeoJSON. Point coordinates must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[],[1]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[[[200,0],[1,1]]]}'))",
                "REFUSED GeoJSON::Polygon::Loop: 'coordinates' malformed. Loop array should have at least 4 "
                + "elements."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[[[200,0],[1,1],[2,2],[200,0]],[]]}'))",
                "REFUSED GeoJSON::Polygon::Loop: Invalid Lng/Lat pair: '200,0'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[200,0]]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' malformed. LineString array should have at least "
                + "2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":null}'))",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'NULL_VALUE'. Expected: "
                + "'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\"}'))",
                "REFUSED GeoJSON::Point: object is missing the field: 'coordinates'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":\"x\"}'))",
                "REFUSED GeoJSON::LineString: type of the GeoJSON field 'coordinates' is 'TEXT'. Expected: "
                + "'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":7}'))",
                "REFUSED GeoJSON::MultiPolygon: type of the GeoJSON field 'coordinates' is 'FIXED'. Expected:"
                + " 'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":\"x\"}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' field must be of type ARRAY."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],[3]]}'))",
                "REFUSED Invalid GeoJSON. Point coordinates must have at least 2 elements."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[1,0],[0,0]]]]}'))",
                "REFUSED Invalid GeoJSON. Loop 'coordinates' must have at least 4 elements."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[1,0],[0,0]]]]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon::Loop: 'coordinates' malformed. Loop array should "
                + "have at least 4 elements."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiLineString\",\"coordinates\":[[[1,2]]]}'))",
                "REFUSED GeoJSON::MultiLineString::LineString: 'coordinates' malformed. LineString array "
                + "should have at least 2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiLineString\",\"coordinates\":[[[1,2],[]]]}'))",
                "REFUSED GeoJSON::MultiLineString::LineString: 'coordinates' malformed. Point array should "
                + "have 2 elements (Lng/Lat)."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[1,0],[1,1],[]]]]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon::Loop: 'coordinates' malformed. Point array should "
                + "have 2 elements (Lng/Lat)."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[]]]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' must not be empty."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"LineString\",\"coordinates\":[]}]}'))",
                "REFUSED GeoJSON::GeometryCollection::LineString: 'coordinates' malformed. LineString array "
                + "should have at least 2 elements (start/end point)."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[1,2]},{\"type\":\"Polygon\",\"coordinates\":[]}]}'))",
                "REFUSED GeoJSON::GeometryCollection::Polygon: 'coordinates' must not be empty."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[\"a\",\"b\"]}'))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'TEXT'."},
            {"SELECT ST_NPOINTS(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "0"},
            {"SELECT TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}') IS NULL",
                "false"},
            {"SELECT ST_ASWKB(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "REFUSED Error converting GeoJSON to WKB: GeoJSON::MultiPoint: 'coordinates' must not be "
                + "empty."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"MultiPoint\",\"coordinates\":[]}]}'))",
                "GEOMETRYCOLLECTION(MULTIPOINT EMPTY)"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[[]]}'))",
                "REFUSED GeoJSON::MultiPoint::Point: 'coordinates' malformed. Point array should have 2 "
                + "elements (Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],[]]}'))",
                "REFUSED GeoJSON::MultiPoint::Point: 'coordinates' malformed. Point array should have 2 "
                + "elements (Lng/Lat)."},
        });
    }

    @Test
    public void coordinateTypes() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":1.5}'))",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'FIXED'. Expected: "
                + "'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":true}'))",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'BOOLEAN'. Expected: "
                + "'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[\"a\",1]}'))",
                "REFUSED GeoJSON::Point: point coordinate is of non numeric type: 'TEXT'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1,null]}'))",
                "REFUSED GeoJSON::Point: point coordinate is of non numeric type: 'NULL_VALUE'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[1,null]}'))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'NULL_VALUE'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[true,1]}'))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'BOOLEAN'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[{},1]}'))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'OBJECT'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[1.5,[]]}'))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[1,2]}'))",
                "REFUSED Invalid GeoJSON. Malformed `coordinates`, expect nested array but found FIXED."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[1,2]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' field does not hold arrays of Lng/Lat pairs."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[1]}'))",
                "REFUSED Invalid GeoJSON. Malformed `coordinates`, expect nested array but found FIXED."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[1]}'))",
                "REFUSED GeoJSON::Polygon::Loop: 'coordinates' field does not hold arrays of Point."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOMETRY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\"}]}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' field is missing."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\"}]}'))",
                "REFUSED GeoJSON::GeometryCollection::Point: object is missing the field: 'coordinates'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":1.5}'))",
                "REFUSED Invalid GeoJSON. 'coordinates' field must be of type ARRAY."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":\"1\"}'))",
                "REFUSED GeoJSON::Point: type of the GeoJSON field 'coordinates' is 'TEXT'. Expected: "
                + "'ARRAY'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[\"1\",\"2\"]}'))",
                "REFUSED GeoJSON::Point: point coordinate is of non numeric type: 'TEXT'."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[\"1\",\"2\"]}'))",
                "REFUSED Invalid GeoJSON. Coordinate value is not numeric: 'TEXT'."},
        });
    }

    @Test
    public void nestedArrays() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[1]}'))",
                "REFUSED GeoJSON::MultiPoint::Point: 'coordinates' field does not hold arrays of Lng/Lat "
                + "pairs."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiLineString\",\"coordinates\":[1]}'))",
                "REFUSED GeoJSON::MultiLineString::LineString: 'coordinates' field does not hold arrays of "
                + "Point."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiLineString\",\"coordinates\":[[1]]}'))",
                "REFUSED GeoJSON::MultiLineString::LineString: 'coordinates' malformed. LineString array "
                + "should have at least 2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[1]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon: 'coordinates' field does not hold arrays of Loop."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[[1]]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon::Loop: 'coordinates' field does not hold arrays of "
                + "Point."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[1]]]}'))",
                "REFUSED GeoJSON::MultiPolygon::Polygon::Loop: 'coordinates' malformed. Loop array should "
                + "have at least 4 elements."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Polygon\",\"coordinates\":[[1]]}'))",
                "REFUSED GeoJSON::Polygon::Loop: 'coordinates' malformed. Loop array should have at least 4 "
                + "elements."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[\"a\"]}'))",
                "REFUSED GeoJSON::Point: 'coordinates' malformed. Point array should have 2 elements "
                + "(Lng/Lat)."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Point\",\"coordinates\":[\"a\"]}'))",
                "REFUSED Invalid GeoJSON. Point coordinates must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPoint\",\"coordinates\":[1]}'))",
                "REFUSED Invalid GeoJSON. Malformed `coordinates`, expect nested array but found FIXED."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiLineString\",\"coordinates\":[[1]]}'))",
                "REFUSED Invalid GeoJSON. LineString 'coordinates' must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[[[1]]]}'))",
                "REFUSED Invalid GeoJSON. Loop 'coordinates' must have at least 4 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"Polygon\",\"coordinates\":[[\"x\"]]}'))",
                "REFUSED Invalid GeoJSON. Loop 'coordinates' must have at least 4 elements."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[1]}'))",
                "REFUSED Invalid GeoJSON. LineString 'coordinates' must have at least 2 elements."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[1]}'))",
                "REFUSED GeoJSON::LineString: 'coordinates' malformed. LineString array should have at least "
                + "2 elements (start/end point)."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[1,{}]}'))",
                "REFUSED GeoJSON::Point: point coordinate is of non numeric type: 'OBJECT'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"LineString\",\"coordinates\":[[1,2],[\"a\",3]]}'))",
                "REFUSED GeoJSON::LineString: point coordinate is of non numeric type: 'TEXT'."},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],5]}'))",
                "REFUSED GeoJSON::MultiPoint::Point: 'coordinates' field does not hold arrays of Lng/Lat "
                + "pairs."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"LineString\",\"coordinates\":[[1,2],\"z\"]}'))",
                "REFUSED Invalid GeoJSON. Malformed `coordinates`, expect nested array but found TEXT."},
            {"SELECT ST_ASWKT(TO_GEOMETRY('{\"type\":\"MultiPolygon\",\"coordinates\":[[1]]}'))",
                "REFUSED Invalid GeoJSON. Malformed `coordinates`, expect nested array but found FIXED."},
        });
    }

    @Test
    public void searchesAndRanges() {
        engine.execute("CREATE OR REPLACE TABLE tg(g GEOMETRY, h GEOGRAPHY, n INT)");
        engine.execute("INSERT INTO tg SELECT TO_GEOMETRY('POINT(1 2)'), TO_GEOGRAPHY('POINT(1 2)'), 1");
        assertCells(new String[][] {
            {"SELECT CASE g WHEN 1 THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT CASE n WHEN g THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [NUMBER(38,0)]"},
            {"SELECT CASE g WHEN h THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT CASE h WHEN TO_GEOGRAPHY('POINT(1 2)') THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.H' of type [GEOGRAPHY] into "
                + "expected type [ANY]"},
            {"SELECT CASE TO_GEOGRAPHY('POINT(1 2)') WHEN h THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TO_GEOGRAPHY('POINT(1 2)')' of "
                + "type [GEOGRAPHY] into expected type [ANY]"},
            {"SELECT DECODE(g, 1, 1, 0) FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT DECODE(n, g, 1, 0) FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [NUMBER(38,0)]"},
            {"SELECT DECODE(h, h, 1, 0) FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.H' of type [GEOGRAPHY] into "
                + "expected type [ANY]"},
            {"SELECT CASE g WHEN g THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT CASE tg.g WHEN g THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT CASE g WHEN NULL THEN 1 END FROM tg",
                "REFUSED SQL compilation error: | Can not convert parameter 'TG.G' of type [GEOMETRY] into "
                + "expected type [ANY]"},
            {"SELECT g BETWEEN g AND g FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '>=': (GEOMETRY, GEOMETRY)"},
            {"SELECT n BETWEEN g AND g FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '>=': (NUMBER(38,0), GEOMETRY)"},
            {"SELECT g BETWEEN 1 AND 2 FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '>=': (GEOMETRY, NUMBER(1,0))"},
            {"SELECT n BETWEEN 1 AND g FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '<=': (NUMBER(38,0), GEOMETRY)"},
            {"SELECT h NOT BETWEEN h AND h FROM tg",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function '>=': (GEOGRAPHY, GEOGRAPHY)"},
            {"SELECT COUNT(*) FROM tg WHERE g BETWEEN g AND g",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for "
                + "function '>=': (GEOMETRY, GEOMETRY)"},
            {"SELECT h BETWEEN g AND g FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '>=': (GEOGRAPHY, GEOMETRY)"},
            {"SELECT n BETWEEN g AND h FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '>=': (NUMBER(38,0), GEOMETRY)"},
            {"SELECT g IN (g) FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function 'IN': (GEOMETRY, GEOMETRY)"},
            {"SELECT COUNT(*) FROM tg WHERE g IN (g, g)",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for "
                + "function 'IN': (GEOMETRY, GEOMETRY, GEOMETRY)"},
            {"SELECT g NOT IN (g) FROM tg",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function 'IN': (GEOMETRY, GEOMETRY)"},
            {"SELECT n IN (g) FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function 'IN': (NUMBER(38,0), GEOMETRY)"},
            {"SELECT g IS DISTINCT FROM g FROM tg",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function 'EQUAL_NULL': (GEOMETRY, GEOMETRY)"},
            {"SELECT COUNT(*) FROM tg WHERE h IS NOT DISTINCT FROM h",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for "
                + "function 'EQUAL_NULL': (GEOGRAPHY, GEOGRAPHY)"},
            {"SELECT n IS DISTINCT FROM g FROM tg",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function 'EQUAL_NULL': (NUMBER(38,0), GEOMETRY)"},
            {"SELECT g <> g FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '<>': (GEOMETRY, GEOMETRY)"},
            {"SELECT g != g FROM tg",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for "
                + "function '!=': (GEOMETRY, GEOMETRY)"},
            {"SELECT COUNT(*) FROM tg WHERE h <> h",
                "REFUSED SQL compilation error: error line 1 at position 32 | Invalid argument types for "
                + "function '<>': (GEOGRAPHY, GEOGRAPHY)"},
        });
    }

    @Test
    public void emptyMultiPointOutput() {
        assertCells(new String[][] {
            {"SELECT ST_ASBINARY(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "REFUSED Error converting GeoJSON to WKB: GeoJSON::MultiPoint: 'coordinates' must not be "
                + "empty."},
            {"SELECT ST_ASEWKT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "SRID=4326;MULTIPOINT EMPTY"},
            {"SELECT ST_ASTEXT(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "MULTIPOINT EMPTY"},
            {"SELECT "
                + "ST_ASWKB(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"MultiPoint\",\"coordinates\":[]}]}'))",
                "REFUSED Error converting GeoJSON to WKB: GeoJSON::GeometryCollection::MultiPoint: "
                + "'coordinates' must not be empty."},
            {"SELECT "
                + "ST_ASWKT(TO_GEOGRAPHY('{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"MultiPoint\",\"coordinates\":[]},{\"type\":\"Point\",\"coordinates\":[1,2]}]}'))",
                "GEOMETRYCOLLECTION(MULTIPOINT EMPTY,POINT(1 2))"},
            {"SELECT ST_NPOINTS(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[]}'))",
                "0"},
            {"SELECT ST_ASWKB(TO_GEOGRAPHY('{\"type\":\"MultiPoint\",\"coordinates\":[[1,2]]}'))",
                "0104000000010000000101000000000000000000F03F0000000000000040"},
        });
    }
}
