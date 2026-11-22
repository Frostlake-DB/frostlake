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
 * A GEOGRAPHY reaches a client in the session's GEOGRAPHY_OUTPUT_FORMAT and a GEOMETRY in its GEOMETRY_OUTPUT_FORMAT,
 * each alone (all live-verified): GeoJSON by default, WKT and EWKT as text - {@code POINT(1 2)},
 * {@code SRID=4326;POINT(1 2)} - and WKB and EWKB as bytes, EWKB's type word flagged and the SRID after it. A
 * conversion to text stays refused under every format.
 */
public class GeoOutputFormatTest extends MeasuredCellsTestSupport {

    @Test
    public void eachKindFollowsItsOwnOutputFormat() {
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE TABLE gt80 (g GEOGRAPHY, m GEOMETRY)",
                    "Table GT80 successfully created."},
                {"INSERT INTO gt80 SELECT TO_GEOGRAPHY('POINT(1 2)'), TO_GEOMETRY('POINT(1 2)')",
                    "1"},
                {"SELECT g, m FROM gt80",
                    "{\"coordinates\":[1,2],\"type\":\"Point\"}|"
                        + "{\"coordinates\":[1.000000000000000e+00,2.00000000000000"
                        + "0e+00],\"type\":\"Point\"}"},
                {"SELECT TO_VARCHAR(g), g::VARCHAR, TO_VARCHAR(m) FROM gt80",
                    "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(GT80.G)] for parameter 'TO_VARCHAR'"},
                {"ALTER SESSION SET GEOGRAPHY_OUTPUT_FORMAT = 'WKT'",
                    "Statement executed successfully."},
                {"SELECT g, m FROM gt80",
                    "POINT(1 2)|{\"coordinates\":[1.000000000000000e+00,2.000000000000000e+00],\"type\":\"Point\"}"},
                {"SELECT TO_VARCHAR(g), g::VARCHAR, TO_VARCHAR(m), m::VARCHAR FROM gt80",
                    "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(GT80.G)] for parameter 'TO_VARCHAR'"},
                {"SELECT TO_VARIANT(g)::VARCHAR, OBJECT_CONSTRUCT('g', g)::VARCHAR FROM gt80",
                    "REFUSED SQL compilation error: | invalid type [TO_VARIANT(GT80.G)] for parameter 'TO_VARIANT'"},
                {"SELECT TO_GEOGRAPHY('LINESTRING(1 2, 3 4.5)'), TO_GEOGRAPHY('POLYGON((0 0, 1 0, 1 1, 0 0))')",
                    "LINESTRING(1 2,3 4.5)|POLYGON((0 0,1 0,1 1,0 0))"},
                {"ALTER SESSION SET GEOGRAPHY_OUTPUT_FORMAT = 'EWKT'",
                    "Statement executed successfully."},
                {"SELECT g FROM gt80",
                    "SRID=4326;POINT(1 2)"},
                {"SELECT TO_VARCHAR(g) FROM gt80",
                    "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(GT80.G)] for parameter 'TO_VARCHAR'"},
                {"ALTER SESSION SET GEOGRAPHY_OUTPUT_FORMAT = 'WKB'",
                    "Statement executed successfully."},
                {"SELECT g FROM gt80",
                    "0101000000000000000000F03F0000000000000040"},
                {"SELECT TO_VARCHAR(g), HEX_ENCODE(g::BINARY) FROM gt80",
                    "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(GT80.G)] for parameter 'TO_VARCHAR'"},
                {"ALTER SESSION SET GEOGRAPHY_OUTPUT_FORMAT = 'EWKB'",
                    "Statement executed successfully."},
                {"SELECT g FROM gt80",
                    "0101000020E6100000000000000000F03F0000000000000040"},
                {"ALTER SESSION SET GEOGRAPHY_OUTPUT_FORMAT = 'GeoJSON'",
                    "Statement executed successfully."},
                {"ALTER SESSION SET GEOMETRY_OUTPUT_FORMAT = 'WKT'",
                    "Statement executed successfully."},
                {"SELECT g, m FROM gt80",
                    "{\"coordinates\":[1,2],\"type\":\"Point\"}|POINT(1 2)"},
                {"SELECT TO_VARCHAR(m) FROM gt80",
                    "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(GT80.M)] for parameter 'TO_VARCHAR'"},
                {"ALTER SESSION SET GEOMETRY_OUTPUT_FORMAT = 'EWKT'",
                    "Statement executed successfully."},
                {"SELECT m FROM gt80",
                    "SRID=0;POINT(1 2)"},
                {"ALTER SESSION SET GEOMETRY_OUTPUT_FORMAT = 'WKB'",
                    "Statement executed successfully."},
                {"SELECT m FROM gt80",
                    "0101000000000000000000F03F0000000000000040"},
                {"ALTER SESSION SET GEOMETRY_OUTPUT_FORMAT = 'EWKB'",
                    "Statement executed successfully."},
                {"SELECT m FROM gt80",
                    "010100002000000000000000000000F03F0000000000000040"},
                {"ALTER SESSION UNSET GEOGRAPHY_OUTPUT_FORMAT",
                    "Statement executed successfully."},
                {"ALTER SESSION UNSET GEOMETRY_OUTPUT_FORMAT",
                    "Statement executed successfully."},
            });
        } finally {
            engine.execute("ALTER SESSION UNSET GEOGRAPHY_OUTPUT_FORMAT");
            engine.execute("ALTER SESSION UNSET GEOMETRY_OUTPUT_FORMAT");
        }
    }
}
