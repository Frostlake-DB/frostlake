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

package dev.frostlake.executor;

import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.GeoValue;
import dev.frostlake.values.GeoWriter;
import dev.frostlake.values.VariantJsonText;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * A geospatial value as a client is given it, in the output format the session set for its kind (all live-verified):
 * GeoJSON, the default, is the value itself; WKT and EWKT are text, {@code POINT(1 2)} and
 * {@code SRID=4326;POINT(1 2)}; WKB and EWKB are bytes, EWKB's type word carrying the SRID flag and the SRID following
 * it ({@code 0101000020E6100000…}). A GEOGRAPHY follows GEOGRAPHY_OUTPUT_FORMAT and a GEOMETRY
 * GEOMETRY_OUTPUT_FORMAT alone. Without a writer on the class path a value stays GeoJSON. A GEOMETRY's GeoJSON is
 * text whose numbers are each a DOUBLE in the fifteen-decimal form, {@code 1.000000000000000e+00}, where a
 * GEOGRAPHY's keeps its numbers as written.
 */
public final class GeoOutput {

    /** The flag EWKB sets in a geometry's type word when an SRID follows it. */
    private static final int SRID_FLAG = 0x20000000;

    /** The bytes before a WKB's type word: its byte order. */
    private static final int TYPE_AT = 1;

    /** The bytes of a WKB's type word and of an SRID. */
    private static final int WORD = 4;

    private static final Object LOCK = new Object();

    private static GeoWriter writer;

    private static boolean looked;

    private GeoOutput() {
    }

    /**
     * A value in the output format the session set for its kind.
     *
     * @param value the value
     * @return a GEOGRAPHY itself and a GEOMETRY's text for GeoJSON, the text for WKT and EWKT, the bytes for WKB and
     *     EWKB
     */
    public static Object rendered(final GeoValue value) {
        final String format = SessionOutputFormats.geo(value.isGeography());
        if (SessionOutputFormats.GEOJSON.equals(format)) {
            return value.isGeography() ? value : geoJsonText(value);
        }
        final GeoWriter written = writer();
        if (written == null) {
            return value;
        }
        switch (format) {
            case "WKT":
                return written.wkt(value);
            case "EWKT":
                return "SRID=" + value.getSrid() + ";" + written.wkt(value);
            case "WKB":
                return BinaryValue.of(written.wkb(value));
            case "EWKB":
                return BinaryValue.of(extended(written.wkb(value), value.getSrid()));
            default:
                return value;
        }
    }

    /**
     * A value's GeoJSON as a client reads it: a GEOGRAPHY's as written, a GEOMETRY's with each number a DOUBLE.
     *
     * @param value the value
     * @return its text
     */
    public static String geoJsonText(final GeoValue value) {
        return value.isGeography() ? value.geoJson() : VariantJsonText.render(doubles(value.node()));
    }

    /**
     * A value's GeoJSON tree as ST_ASGEOJSON hands it out: a GEOGRAPHY's numbers as written, a GEOMETRY's each a
     * DOUBLE (live-verified: TYPEOF of a GEOMETRY's coordinate is DOUBLE whatever its input spelled).
     *
     * @param value the value
     * @return its GeoJSON tree; a GEOMETRY's is a copy
     */
    public static JsonNode geoJsonNode(final GeoValue value) {
        return value.isGeography() ? value.node() : doubles(value.node());
    }

    /** A GeoJSON tree with each of its numbers a DOUBLE. */
    private static JsonNode doubles(final JsonNode node) {
        if (node.isNumber()) {
            return new DoubleNode(node.doubleValue());
        }
        if (node.isArray()) {
            final ArrayNode copy = ((ArrayNode) node).arrayNode();
            for (final JsonNode element : node) {
                copy.add(doubles(element));
            }
            return copy;
        }
        if (node.isObject()) {
            final ObjectNode copy = ((ObjectNode) node).objectNode();
            final Iterator<String> names = node.propertyNames().iterator();
            while (names.hasNext()) {
                final String name = names.next();
                copy.set(name, doubles(node.get(name)));
            }
            return copy;
        }
        return node;
    }

    /**
     * A little-endian WKB with its outermost type word flagged and the SRID after it: the EWKB form, a shape with a
     * third coordinate keeping its ISO type code ({@code 01E9030020…}, live-verified).
     *
     * @param wkb the value's WKB
     * @param srid its SRID
     * @return its EWKB
     */
    public static byte[] extended(final byte[] wkb, final int srid) {
        if (wkb.length < TYPE_AT + WORD) {
            return wkb;
        }
        final byte[] out = new byte[wkb.length + WORD];
        out[0] = wkb[0];
        final int type = (wkb[TYPE_AT] & 0xFF) | (wkb[TYPE_AT + 1] & 0xFF) << 8 | (wkb[TYPE_AT + 2] & 0xFF) << 16
            | (wkb[TYPE_AT + 3] & 0xFF) << 24;
        littleEndian(type | SRID_FLAG, out, TYPE_AT);
        littleEndian(srid, out, TYPE_AT + WORD);
        System.arraycopy(wkb, TYPE_AT + WORD, out, TYPE_AT + 2 * WORD, wkb.length - TYPE_AT - WORD);
        return out;
    }

    private static void littleEndian(final int value, final byte[] out, final int at) {
        for (int i = 0; i < WORD; i++) {
            out[at + i] = (byte) (value >>> (8 * i));
        }
    }

    /** The writer the class path supplies, looked for once, or null. */
    private static GeoWriter writer() {
        synchronized (LOCK) {
            if (!looked) {
                final Iterator<GeoWriter> found =
                    ServiceLoader.load(GeoWriter.class, GeoOutput.class.getClassLoader()).iterator();
                writer = found.hasNext() ? found.next() : null;
                looked = true;
            }
            return writer;
        }
    }
}
