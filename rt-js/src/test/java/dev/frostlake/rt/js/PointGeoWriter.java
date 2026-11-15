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

import dev.frostlake.values.GeoValue;
import dev.frostlake.values.GeoWriter;

import tools.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The geospatial writer the runtime's tests read a point's WKT and WKB with, as the geo module writes them: the
 * runtime's own tests run without that module.
 */
public final class PointGeoWriter implements GeoWriter {

    /** A WKB point: its byte order, its type word and its two coordinates. */
    private static final int POINT_BYTES = 21;

    @Override
    public String wkt(final GeoValue value) {
        final JsonNode at = value.node().get("coordinates");
        return "POINT(" + number(at.get(0)) + " " + number(at.get(1)) + ")";
    }

    @Override
    public byte[] wkb(final GeoValue value) {
        final JsonNode at = value.node().get("coordinates");
        final ByteBuffer bytes = ByteBuffer.allocate(POINT_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 1).putInt(1).putDouble(at.get(0).asDouble()).putDouble(at.get(1).asDouble());
        return bytes.array();
    }

    private static String number(final JsonNode coordinate) {
        final double value = coordinate.asDouble();
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
