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

package dev.frostlake.rt.js;

import dev.frostlake.executor.SessionOutputFormats;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.GeoValue;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A GEOGRAPHY and a GEOMETRY column as a JavaScript procedure's result set reads them under the session's
 * GEOGRAPHY_OUTPUT_FORMAT and GEOMETRY_OUTPUT_FORMAT (live-verified): in WKT and EWKT the value is its text and the
 * column a {@code string} of SQL type TEXT, in WKB and EWKB its bytes - a {@code Uint8Array}, whose text is its JSON by
 * position - and the column a {@code buffer} of SQL type BINARY; a NULL is {@code null} in the format's column type.
 */
public class JavaScriptGeoOutputFormatTest {

    private static final byte[] WKB = {1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 240, 63, 0, 0, 0, 0, 0, 0, 0, 64};

    private static final byte[] EWKB_4326 = {1, 1, 0, 0, 32, (byte) 230, 16, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 240, 63,
        0, 0, 0, 0, 0, 0, 0, 64};

    private static final byte[] EWKB_0 = {1, 1, 0, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 240, 63, 0, 0, 0, 0, 0,
        0, 0, 64};

    private static JavaScriptResultSet rows() {
        final ResultSet result = new ResultSet(Arrays.asList(new ResultSetColumn("G", new GeographyType()),
            new ResultSetColumn("M", new GeometryType()), new ResultSetColumn("N", new GeographyType())));
        result.addRow(new Row(point(true), point(false), null));
        return new JavaScriptResultSet(result, new JavaScriptApiFailures(), "q");
    }

    private static GeoValue point(final boolean geography) {
        return GeoValue.ofNode(ArrayFunctionHelper.MAPPER.readTree("{\"coordinates\":[1,2],\"type\":\"Point\"}"),
            geography, geography ? 4326 : 0);
    }

    /** The bytes' JSON by position, as getColumnValueAsString writes a BINARY's. */
    private static String byPosition(final byte[] bytes) {
        final StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < bytes.length; i++) {
            json.append(i == 0 ? "" : ",").append('"').append(i).append("\":").append(bytes[i] & 0xFF);
        }
        return json.append('}').toString();
    }

    /** Reads the three columns under one format for both kinds. */
    private static void assertFormat(final String format, final Object geography, final Object geometry,
                                     final String type, final String sqlType) {
        final Map<String, String> formats = new HashMap<String, String>();
        formats.put(SessionOutputFormats.GEOGRAPHY, format);
        formats.put(SessionOutputFormats.GEOMETRY, format);
        final Map<String, String> displaced = SessionOutputFormats.pin(formats);
        try {
            final JavaScriptResultSet read = rows();
            assertTrue(read.next());
            assertEquals(JavaScriptArgumentJson.ofColumn(geography), read.columnValue(Integer.valueOf(1), "1"), format);
            assertEquals(JavaScriptArgumentJson.ofColumn(geometry), read.columnValue("M", "M"), format);
            assertEquals(JavaScriptArgumentJson.ofColumn(null), read.columnValue("N", "N"), format);
            assertEquals(geography instanceof BinaryValue ? byPosition(((BinaryValue) geography).bytes())
                : String.valueOf(geography), read.columnText(Integer.valueOf(1), "1"), format);
            assertEquals(geometry instanceof BinaryValue ? byPosition(((BinaryValue) geometry).bytes())
                : String.valueOf(geometry), read.columnText(Integer.valueOf(2), "2"), format);
            for (final String column : new String[] {"G", "M", "N"}) {
                assertEquals(type, read.column(column, column, "type"), format + " " + column);
                assertEquals(sqlType, read.column(column, column, "sqlType"), format + " " + column);
            }
        } finally {
            SessionOutputFormats.restore(displaced);
        }
    }

    @Test
    public void aGeospatialColumnFollowsTheSessionsFormat() {
        assertFormat("WKT", "POINT(1 2)", "POINT(1 2)", "string", "TEXT");
        assertFormat("EWKT", "SRID=4326;POINT(1 2)", "SRID=0;POINT(1 2)", "string", "TEXT");
        assertFormat("WKB", BinaryValue.of(WKB), BinaryValue.of(WKB), "buffer", "BINARY");
        assertFormat("EWKB", BinaryValue.of(EWKB_4326), BinaryValue.of(EWKB_0), "buffer", "BINARY");
    }
}
