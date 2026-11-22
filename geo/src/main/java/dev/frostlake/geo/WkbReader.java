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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads WKB and EWKB into the GeoJSON model: either byte order (a first byte of 0 is big-endian, any other
 * little-endian), the seven 2D shapes, and the EWKB SRID flag. A shape with no points anywhere in it reads as the
 * EMPTY shape, which makes the whole value NULL, as WKT's EMPTY does (live-verified). Coordinates are doubles.
 */
public final class WkbReader {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final int SRID_FLAG = 0x20000000;
    private static final int Z_FLAG = 0x80000000;
    private static final int M_FLAG = 0x40000000;

    private final byte[] bytes;
    private int pos;
    private int srid = -1;
    private boolean empty;

    /**
     * @param bytes the WKB or EWKB bytes
     */
    public WkbReader(final byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Reads the whole input as one shape.
     *
     * @return the model, or null for a shape with no points
     * @throws WkbFormatException when the bytes are no well-formed WKB
     * @throws IllegalArgumentException when they are, but the shape is not one a value can hold
     */
    public JsonNode read() {
        if (bytes.length < 5) {
            throw new WkbFormatException("Input has less than 21 bytes.");
        }
        final JsonNode model = shape(true);
        if (pos != bytes.length) {
            throw new WkbFormatException("Trailing bytes not recognized.");
        }
        return empty ? null : model;
    }

    /** The SRID the EWKB header names, or -1 where it names none. */
    public int srid() {
        return srid;
    }

    private JsonNode shape(final boolean top) {
        if (pos >= bytes.length) {
            throw new IllegalArgumentException("a shape is missing");
        }
        final boolean little = bytes[pos++] != 0;
        final int code = readInt(little);
        if ((code & (Z_FLAG | M_FLAG)) != 0) {
            throw new IllegalArgumentException("only 2D shapes are read");
        }
        if ((code & SRID_FLAG) != 0) {
            final int named = readInt(little);
            if (top) {
                srid = named;
            }
        }
        final int type = code & ~SRID_FLAG;
        final ObjectNode out = MAPPER.createObjectNode();
        switch (type) {
            case 1:
                out.set("coordinates", point(little, "Point"));
                out.put("type", "Point");
                return out;
            case 2:
                out.set("coordinates", line(little, 2, "LineString"));
                out.put("type", "LineString");
                return out;
            case 3:
                out.set("coordinates", rings(little));
                out.put("type", "Polygon");
                return out;
            case 4:
            case 5:
            case 6: {
                final String member = type == 4 ? "Point" : type == 5 ? "LineString" : "Polygon";
                final int count = readInt(little);
                final ArrayNode members = MAPPER.createArrayNode();
                for (int i = 0; i < count; i++) {
                    final JsonNode part = shape(false);
                    if (!member.equals(part.get("type").asText())) {
                        throw new IllegalArgumentException("a Multi" + member + " holds " + member + " shapes alone");
                    }
                    members.add(part.get("coordinates"));
                }
                empty |= count == 0;
                out.set("coordinates", members);
                out.put("type", "Multi" + member);
                return out;
            }
            case 7: {
                final int count = readInt(little);
                final ArrayNode members = MAPPER.createArrayNode();
                for (int i = 0; i < count; i++) {
                    members.add(shape(false));
                }
                empty |= count == 0;
                out.set("geometries", members);
                out.put("type", "GeometryCollection");
                return out;
            }
            default:
                throw new WkbFormatException("Type '" + type + "' unknown.");
        }
    }

    private ArrayNode rings(final boolean little) {
        final int count = readInt(little);
        final ArrayNode rings = MAPPER.createArrayNode();
        for (int i = 0; i < count; i++) {
            rings.add(line(little, 4, "LineString"));
        }
        empty |= count == 0;
        return rings;
    }

    private ArrayNode line(final boolean little, final int minPoints, final String name) {
        final int count = readInt(little);
        if (count < 0 || (long) count * 16 > bytes.length - pos) {
            throw new WkbFormatException("'" + name + "' holds less points than specified.");
        }
        if (count == 0) {
            empty = true;
        } else if (count < minPoints) {
            throw new IllegalArgumentException("too few points");
        }
        final ArrayNode line = MAPPER.createArrayNode();
        for (int i = 0; i < count; i++) {
            line.add(point(little, name));
        }
        return line;
    }

    private ArrayNode point(final boolean little, final String name) {
        if (bytes.length - pos < 16) {
            throw new WkbFormatException("'" + name + "' holds less coordinates than specified.");
        }
        final ArrayNode pt = MAPPER.createArrayNode();
        pt.add(Double.longBitsToDouble(readLong(little)));
        pt.add(Double.longBitsToDouble(readLong(little)));
        return pt;
    }

    private int readInt(final boolean little) {
        if (bytes.length - pos < 4) {
            throw new IllegalArgumentException("the input ends inside a count");
        }
        int v = 0;
        for (int i = 0; i < 4; i++) {
            final int b = bytes[pos + (little ? i : 3 - i)] & 0xFF;
            v |= b << (8 * i);
        }
        pos += 4;
        return v;
    }

    private long readLong(final boolean little) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            final long b = bytes[pos + (little ? i : 7 - i)] & 0xFFL;
            v |= b << (8 * i);
        }
        pos += 8;
        return v;
    }
}
