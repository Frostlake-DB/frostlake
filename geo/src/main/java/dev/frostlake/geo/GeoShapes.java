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

import dev.frostlake.functions.scalar.JsonNumberNotation;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.GeoValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The shared geospatial engine behind the ST_ functions: WKT, GeoJSON and (E)WKB parsing, WKT / WKB / GeoJSON
 * rendering, and the measures (the relations are {@link GeoRelation}'s). Live-verified model: Snowflake's
 * GEOGRAPHY is a pure SPHERE of radius {@code 6371010 m} ({@code ST_DISTANCE} of one degree
 * is 111195.10117748393 m, reproduced exactly), while {@code HAVERSINE} uses {@code 6371 km}; GEOMETRY is planar.
 * Supported shapes: Point, LineString, Polygon (with holes) and their Multi* forms plus
 * GeometryCollection parsing. Coordinates parsed from text stay BigDecimal (written as {@link GeoNumbers#wkt}
 * writes them); computed and WKB-read coordinates are doubles.
 */
public final class GeoShapes {

    /** The live-verified Earth radius Snowflake's GEOGRAPHY measures use: exactly 6371010 m
     *  (ST_DISTANCE of one degree = 111195.10117748393 m reproduces to the last bit). */
    public static final double EARTH_RADIUS_M = 6371010.0;
    /** The radius HAVERSINE uses (kilometers) — live-verified to differ from ST_DISTANCE's. */
    public static final double HAVERSINE_RADIUS_KM = 6371.0;

    private static final ObjectMapper MAPPER =
        JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private GeoShapes() {
    }

    // ------------------------------------------------------------------ parsing

    /** What an EMPTY shape reads as while a WKT text is read: the whole value is then NULL. */
    private static final JsonNode EMPTY_SHAPE = MAPPER.createObjectNode().put("type", "EMPTY");

    /**
     * Parses the input of TO_GEOGRAPHY / TO_GEOMETRY: a BINARY is read as WKB or EWKB, a text as GeoJSON, (E)WKT or
     * hex-written (E)WKB (see {@link #parse(String, boolean)}). A BINARY that is no well-formed WKB is refused with the
     * WKB reader's sentence ({@code Error parsing WKB input: Trailing bytes not recognized.}, live-verified).
     *
     * @return the value, or null for an input holding an EMPTY shape
     */
    public static GeoValue parse(final Object input, final boolean geography) {
        if (input instanceof BinaryValue) {
            final BinaryValue binary = (BinaryValue) input;
            return fromWkb(binary.bytes(), binary.toHex(), geography, true);
        }
        return parse(input.toString(), geography);
    }

    /**
     * Parses WKT, GeoJSON or hex-written (E)WKB input into a {@link GeoValue}; raises Snowflake's message on anything
     * else: {@code Error parsing Geo input: <input>. Did not recognize valid GeoJSON, (E)WKT or (E)WKB.} A WKT holding
     * an EMPTY shape anywhere, {@code POINT EMPTY} or a collection with one among its members, is no value at all:
     * null (live-verified: TO_GEOMETRY and TO_GEOGRAPHY of such a text are NULL), and so is a WKB shape with no points.
     * A GEOGRAPHY's coordinates must be longitudes in [-180, 180] and latitudes in [-90, 90] (see
     * {@link #checkCoordinates}).
     *
     * @return the value, or null for an input holding an EMPTY shape
     */
    public static GeoValue parse(final String input, final boolean geography) {
        final String trimmed = input.trim();
        if (isHex(trimmed)) {
            return fromWkb(hexBytes(trimmed), input, geography, false);
        }
        JsonNode model = null;
        int srid = -1;
        try {
            if (trimmed.startsWith("{")) {
                model = normalizedGeoJson(
                    JsonNumberNotation.applyExponentNotation(MAPPER, trimmed, MAPPER.readTree(trimmed)), geography, "");
            } else {
                // EWKT: an SRID written ahead of the WKT, in any case and with blanks after its semicolon, which a
                // GEOMETRY keeps and a GEOGRAPHY takes as 4326 alone (live-verified).
                srid = ewktSrid(trimmed);
                final String wkt = srid < 0 ? trimmed : trimmed.substring(trimmed.indexOf(';') + 1).trim();
                if (srid >= 0 && geography && srid != 4326) {
                    throw new IllegalArgumentException("a GEOGRAPHY is read in SRID 4326 alone");
                }
                model = parseWkt(wkt);
            }
        } catch (final GeoJsonFormatException malformed) {
            throw malformed;
        } catch (final RuntimeException notGeo) {
            model = null;
        }
        if (model == EMPTY_SHAPE) {
            return null;
        }
        if (model == null) {
            throw unrecognized(input);
        }
        checkCoordinates(model, geography);
        return GeoValue.ofNode(descaled(model), geography, geography ? 4326 : Math.max(srid, 0));
    }

    /**
     * A text-read model with each number written without a JSON exponent descaled, as a JSON reader types it: its
     * trailing zeros dropped and a whole one an INTEGER ({@code 1.0} reads 1, {@code 2.50} reads 2.5, {@code -0.0}
     * reads 0), whether WKT or GeoJSON wrote it; a WKT exponent is no exception ({@code 1e1} reads 10), while a GeoJSON
     * number written with an exponent stays a DOUBLE (live-verified).
     */
    private static JsonNode descaled(final JsonNode node) {
        if (node.isBigDecimal()) {
            final BigDecimal stripped = node.decimalValue().stripTrailingZeros();
            if (stripped.scale() <= 0) {
                return MAPPER.getNodeFactory().numberNode(stripped.toBigIntegerExact());
            }
            return MAPPER.getNodeFactory().numberNode(stripped);
        }
        if (node instanceof ArrayNode) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                final JsonNode element = array.get(i);
                final JsonNode replaced = descaled(element);
                if (replaced != element) {
                    array.set(i, replaced);
                }
            }
            return node;
        }
        if (node instanceof ObjectNode) {
            final ObjectNode object = (ObjectNode) node;
            final List<String> names = new ArrayList<String>();
            final Iterator<String> found = object.propertyNames().iterator();
            while (found.hasNext()) {
                names.add(found.next());
            }
            for (final String name : names) {
                final JsonNode member = object.get(name);
                final JsonNode replaced = descaled(member);
                if (replaced != member) {
                    object.set(name, replaced);
                }
            }
        }
        return node;
    }

    private static RuntimeException unrecognized(final String input) {
        return new RuntimeException("Error parsing Geo input: " + input
            + ". Did not recognize valid GeoJSON, (E)WKT or (E)WKB.");
    }

    /** Reads (E)WKB bytes; {@code text} is how the refusal quotes the input. */
    private static GeoValue fromWkb(final byte[] bytes, final String text, final boolean geography,
                                    final boolean binary) {
        final WkbReader reader = new WkbReader(bytes);
        final JsonNode model;
        try {
            model = reader.read();
        } catch (final WkbFormatException malformed) {
            if (binary) {
                throw malformed;
            }
            throw unrecognized(text);
        } catch (final RuntimeException notGeo) {
            throw unrecognized(text);
        }
        final int srid = reader.srid();
        if (geography && srid >= 0 && srid != 4326) {
            throw unrecognized(text);
        }
        if (model == null) {
            return null;
        }
        checkCoordinates(model, geography);
        return GeoValue.ofNode(model, geography, geography ? 4326 : Math.max(srid, 0));
    }

    /** Whether the text is hex digits alone, an even number of them: (E)WKB written as text. */
    private static boolean isHex(final String text) {
        if (text.isEmpty() || text.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hexBytes(final String hex) {
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            final int high = Character.digit(hex.charAt(2 * i), 16);
            out[i] = (byte) ((high << 4) | Character.digit(hex.charAt(2 * i + 1), 16));
        }
        return out;
    }

    /**
     * Refuses coordinates a value cannot hold (live-verified): a GEOGRAPHY's longitude outside [-180, 180] or latitude
     * outside [-90, 90] ({@code GeoJSON::Polygon::Loop: Invalid Lng/Lat pair: '200,0'.}, the path naming the shapes
     * down to the pair, the numbers to six significant digits), and a GEOMETRY coordinate that is no number
     * ({@code Invalid GeoJSON. Coordinate value is not numeric: 'nan'.}); a GEOMETRY's points must also agree in
     * their number of coordinates (see {@link GeoDimensionCheck}).
     */
    public static void checkCoordinates(final JsonNode model, final boolean geography) {
        final String type = model.get("type").asText();
        if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                checkCoordinates(part, geography, "GeometryCollection::");
            }
        } else {
            checkCoordinates(model, geography, "");
        }
        if (!geography) {
            GeoDimensionCheck.check(model);
        }
    }

    private static void checkCoordinates(final JsonNode model, final boolean geography, final String prefix) {
        final String type = model.get("type").asText();
        final JsonNode coordinates = model.get("coordinates");
        switch (type) {
            case "Point":
                checkPair(coordinates, geography, prefix + "Point");
                return;
            case "LineString":
            case "MultiPoint":
                for (final JsonNode pt : coordinates) {
                    checkPair(pt, geography, prefix + ("LineString".equals(type) ? type : "MultiPoint::Point"));
                }
                return;
            case "Polygon":
            case "MultiLineString":
                for (final JsonNode line : coordinates) {
                    for (final JsonNode pt : line) {
                        checkPair(pt, geography,
                            prefix + ("Polygon".equals(type) ? "Polygon::Loop" : "MultiLineString::LineString"));
                    }
                }
                return;
            case "MultiPolygon":
                for (final JsonNode poly : coordinates) {
                    for (final JsonNode ring : poly) {
                        for (final JsonNode pt : ring) {
                            checkPair(pt, geography, prefix + "MultiPolygon::Polygon::Loop");
                        }
                    }
                }
                return;
            case "GeometryCollection":
                checkCoordinates(model, geography);
                return;
            default:
                // no other shape carries coordinates
        }
    }

    static void checkPair(final JsonNode pt, final boolean geography, final String path) {
        final double lon = pt.get(0).asDouble();
        final double lat = pt.get(1).asDouble();
        if (geography) {
            if (!(lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90)) {
                throw new RuntimeException("GeoJSON::" + path + ": Invalid Lng/Lat pair: '"
                    + GeoNumbers.quoted(lon) + "," + GeoNumbers.quoted(lat) + "'.");
            }
        } else if (Double.isNaN(lon) || Double.isNaN(lat)) {
            throw new RuntimeException("Invalid GeoJSON. Coordinate value is not numeric: '"
                + GeoNumbers.quoted(Double.isNaN(lon) ? lon : lat) + "'.");
        }
    }

    /** The SRID an EWKT text writes ahead of its WKT ({@code SRID=4326;POINT(1 2)}), or -1 where it writes none. */
    private static int ewktSrid(final String text) {
        final int semicolon = text.indexOf(';');
        if (semicolon < 6 || !text.regionMatches(true, 0, "SRID=", 0, 5)) {
            return -1;
        }
        final String digits = text.substring(5, semicolon).trim();
        if (digits.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(digits);
    }

    /** The value as a GeoValue, or null when it is not one (callers raise their own type errors). */
    public static GeoValue asGeo(final Object value) {
        return value instanceof GeoValue ? (GeoValue) value : null;
    }

    /**
     * The two arguments as same-kind geo values; a kind mismatch raises Snowflake's compile-style
     * error ({@code Invalid argument types for function 'X': (GEOGRAPHY, GEOMETRY)}, live-verified).
     */
    public static void requireSameKind(final String functionName, final GeoValue a, final GeoValue b) {
        if (a.isGeography() != b.isGeography()) {
            throw new RuntimeException("Invalid argument types for function '" + functionName
                + "': (" + a.kindName() + ", " + b.kindName() + ")");
        }
    }

    /**
     * Refuses two GEOMETRY values of different SRIDs ({@code Incompatible SRID: 4326 and 0}, live-verified), naming
     * the SRIDs in the order the relation reads its operands: ST_WITHIN and ST_COVEREDBY name their second argument
     * first.
     */
    public static void requireSameSrid(final GeoValue first, final GeoValue second) {
        if (first.getSrid() != second.getSrid()) {
            throw new RuntimeException("Incompatible SRID: " + first.getSrid() + " and " + second.getSrid());
        }
    }

    /**
     * GeoJSON: {"coordinates": ..., "type": "..."} with sorted keys (coordinates < type). A shape of a known type
     * whose coordinates do not hold that shape is refused with the reader's sentence (see
     * {@link GeoJsonShapeCheck}); a GeometryCollection of no members is no value at all, as an EMPTY WKT shape is
     * (live-verified).
     */
    private static JsonNode normalizedGeoJson(final JsonNode json, final boolean geography, final String prefix) {
        if (!json.isObject() || json.get("type") == null) {
            return null;
        }
        final String type = json.get("type").asText();
        if ("GeometryCollection".equals(type)) {
            final JsonNode geometries = json.get("geometries");
            if (geometries == null || !geometries.isArray()) {
                return null;
            }
            final ArrayNode parts = MAPPER.createArrayNode();
            for (final JsonNode member : geometries) {
                final JsonNode normalized = normalizedGeoJson(member, geography, "GeometryCollection::");
                if (normalized == null) {
                    return null;
                }
                parts.add(normalized);
            }
            if (parts.isEmpty() && prefix.isEmpty()) {
                return EMPTY_SHAPE;
            }
            final ObjectNode out = MAPPER.createObjectNode();
            out.set("geometries", parts);
            out.put("type", "GeometryCollection");
            return out;
        }
        if (!isKnownType(type)) {
            return null;
        }
        final JsonNode coordinates = json.get("coordinates");
        GeoJsonShapeCheck.check(type, coordinates, geography, prefix);
        final ObjectNode out = MAPPER.createObjectNode();
        out.set("coordinates", coordinates);
        out.put("type", type);
        return out;
    }

    private static boolean isKnownType(final String type) {
        return "Point".equals(type) || "LineString".equals(type) || "Polygon".equals(type)
            || "MultiPoint".equals(type) || "MultiLineString".equals(type) || "MultiPolygon".equals(type);
    }

    // ------------------------------------------------------------------ WKT

    private static JsonNode parseWkt(final String wkt) {
        final WktCursor cursor = new WktCursor(wkt);
        final JsonNode model = parseWktBody(cursor, 0);
        cursor.skipSpace();
        return cursor.atEnd() ? model : null;
    }

    /**
     * One WKT shape. A shape tagged Z ({@code POINT Z(1 2 3)} or {@code POINTZ(1 2 3)}) writes three coordinates per
     * point, any other two; a collection's members carry the collection's own tag, written on each (live-verified:
     * {@code GEOMETRYCOLLECTION Z(POINT Z(1 2 3))} is read, {@code GEOMETRYCOLLECTION Z(POINT(1 2 3))} and
     * {@code GEOMETRYCOLLECTION(POINT Z(1 2 3))} are refused).
     *
     * @param requiredDims the dimension a collection member must be tagged with, or 0 at the top
     */
    private static JsonNode parseWktBody(final WktCursor c, final int requiredDims) {
        String keyword = c.word().toUpperCase(Locale.ROOT);
        boolean z = false;
        if (keyword.length() > 1 && keyword.endsWith("Z") && isWktShape(keyword.substring(0, keyword.length() - 1))) {
            keyword = keyword.substring(0, keyword.length() - 1);
            z = true;
        } else if (isWktShape(keyword)) {
            z = c.zWord();
        }
        final int dims = z ? 3 : 2;
        if (requiredDims != 0 && requiredDims != dims) {
            return null;
        }
        if (isWktShape(keyword) && c.emptyWord()) {
            return EMPTY_SHAPE;
        }
        c.dimensions(dims);
        final ObjectNode out = MAPPER.createObjectNode();
        switch (keyword) {
            case "POINT": {
                final List<JsonNode> pt = c.pointList(1);
                if (pt == null || pt.size() != 1) {
                    return null;
                }
                out.set("coordinates", pt.get(0));
                out.put("type", "Point");
                return out;
            }
            case "LINESTRING": {
                final ArrayNode line = c.coordinateArray(2);
                if (line == null) {
                    return null;
                }
                out.set("coordinates", line);
                out.put("type", "LineString");
                return out;
            }
            case "POLYGON": {
                final ArrayNode rings = c.ringArray();
                if (rings == null) {
                    return null;
                }
                out.set("coordinates", rings);
                out.put("type", "Polygon");
                return out;
            }
            case "MULTIPOINT": {
                final ArrayNode points = c.multiPointArray();
                if (points == null) {
                    return null;
                }
                out.set("coordinates", points);
                out.put("type", "MultiPoint");
                return out;
            }
            case "MULTILINESTRING": {
                final ArrayNode lines = c.ringArrayAllowShort();
                if (lines == null) {
                    return null;
                }
                out.set("coordinates", lines);
                out.put("type", "MultiLineString");
                return out;
            }
            case "MULTIPOLYGON": {
                if (!c.consume('(')) {
                    return null;
                }
                final ArrayNode polys = MAPPER.createArrayNode();
                do {
                    final ArrayNode rings = c.ringArray();
                    if (rings == null) {
                        return null;
                    }
                    polys.add(rings);
                } while (c.consume(','));
                if (!c.consume(')')) {
                    return null;
                }
                out.set("coordinates", polys);
                out.put("type", "MultiPolygon");
                return out;
            }
            case "GEOMETRYCOLLECTION": {
                if (!c.consume('(')) {
                    return null;
                }
                final ArrayNode parts = MAPPER.createArrayNode();
                boolean empty = false;
                do {
                    final JsonNode part = parseWktBody(c, dims);
                    if (part == null) {
                        return null;
                    }
                    empty |= part == EMPTY_SHAPE;
                    parts.add(part);
                } while (c.consume(','));
                if (!c.consume(')')) {
                    return null;
                }
                if (empty) {
                    return EMPTY_SHAPE;
                }
                out.set("geometries", parts);
                out.put("type", "GeometryCollection");
                return out;
            }
            default:
                return null;
        }
    }

    /** Whether a WKT keyword names a shape, one that may be written EMPTY. */
    private static boolean isWktShape(final String keyword) {
        switch (keyword) {
            case "POINT":
            case "LINESTRING":
            case "POLYGON":
            case "MULTIPOINT":
            case "MULTILINESTRING":
            case "MULTIPOLYGON":
            case "GEOMETRYCOLLECTION":
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Whether the shape carries a third coordinate: its first point does, or, for a collection, its first member
     * does. A shape read from GeoJSON with three numbers per point, or from WKT or WKB tagged Z, carries one; the
     * spatial functions ignore it.
     */
    public static boolean hasZ(final JsonNode model) {
        if ("GeometryCollection".equals(model.get("type").asText())) {
            final JsonNode parts = model.get("geometries");
            return parts != null && parts.size() > 0 && hasZ(parts.get(0));
        }
        JsonNode c = model.get("coordinates");
        while (c != null && c.isArray() && c.size() > 0 && c.get(0).isArray()) {
            c = c.get(0);
        }
        return c != null && c.isArray() && c.size() >= 3 && c.get(0).isNumber();
    }

    /**
     * WKT in Snowflake's normalized form: {@code LINESTRING(0 0,1 0)} — comma without space; a shape with a third
     * coordinate is tagged Z, written against its keyword ({@code POINTZ(1 2 3)}, live-verified).
     */
    public static String toWkt(final JsonNode model) {
        final String type = model.get("type").asText();
        final StringBuilder out = new StringBuilder();
        final String z = hasZ(model) ? "Z" : "";
        switch (type) {
            case "Point":
                out.append("POINT").append(z).append('(');
                appendWktPoint(model.get("coordinates"), out);
                return out.append(')').toString();
            case "LineString":
                out.append("LINESTRING").append(z).append('(');
                appendWktLine(model.get("coordinates"), out);
                return out.append(')').toString();
            case "Polygon":
                out.append("POLYGON").append(z).append('(');
                appendWktRings(model.get("coordinates"), out);
                return out.append(')').toString();
            case "MultiPoint": {
                if (model.get("coordinates").isEmpty()) {
                    // a GEOGRAPHY read from GeoJSON may hold a MultiPoint of no points
                    return "MULTIPOINT EMPTY";
                }
                out.append("MULTIPOINT").append(z).append('(');
                boolean first = true;
                for (final JsonNode pt : model.get("coordinates")) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append('(');
                    appendWktPoint(pt, out);
                    out.append(')');
                }
                return out.append(')').toString();
            }
            case "MultiLineString":
                out.append("MULTILINESTRING").append(z).append('(');
                appendWktRings(model.get("coordinates"), out);
                return out.append(')').toString();
            case "MultiPolygon": {
                out.append("MULTIPOLYGON").append(z).append('(');
                boolean first = true;
                for (final JsonNode poly : model.get("coordinates")) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append('(');
                    appendWktRings(poly, out);
                    out.append(')');
                }
                return out.append(')').toString();
            }
            case "GeometryCollection": {
                out.append("GEOMETRYCOLLECTION").append(z).append('(');
                boolean first = true;
                for (final JsonNode part : model.get("geometries")) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append(toWkt(part));
                }
                return out.append(')').toString();
            }
            default:
                return type.toUpperCase(Locale.ROOT);
        }
    }

    private static void appendWktRings(final JsonNode rings, final StringBuilder out) {
        boolean first = true;
        for (final JsonNode ring : rings) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('(');
            appendWktLine(ring, out);
            out.append(')');
        }
    }

    private static void appendWktLine(final JsonNode line, final StringBuilder out) {
        boolean first = true;
        for (final JsonNode pt : line) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendWktPoint(pt, out);
        }
    }

    private static void appendWktPoint(final JsonNode pt, final StringBuilder out) {
        out.append(coordinateText(pt.get(0))).append(' ').append(coordinateText(pt.get(1)));
        if (pt.size() >= 3) {
            out.append(' ').append(coordinateText(pt.get(2)));
        }
    }

    private static String coordinateText(final JsonNode c) {
        return c.isBigDecimal() ? GeoNumbers.wkt(c.decimalValue()) : GeoNumbers.wkt(c.asDouble());
    }

    /**
     * Refuses a value WKB cannot write: a MultiPoint of no points, which a GEOGRAPHY read from GeoJSON may hold, at
     * the top or as a collection's member ({@code Error converting GeoJSON to WKB: GeoJSON::MultiPoint: 'coordinates'
     * must not be empty.}, live-verified).
     */
    public static void requireWkbWritable(final JsonNode model) {
        final String type = model.get("type").asText();
        if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                if ("MultiPoint".equals(part.get("type").asText()) && part.get("coordinates").isEmpty()) {
                    throw emptyMultiPointWkb("GeometryCollection::");
                }
            }
        } else if ("MultiPoint".equals(type) && model.get("coordinates").isEmpty()) {
            throw emptyMultiPointWkb("");
        }
    }

    private static RuntimeException emptyMultiPointWkb(final String prefix) {
        return new RuntimeException("Error converting GeoJSON to WKB: GeoJSON::" + prefix
            + "MultiPoint: 'coordinates' must not be empty.");
    }

    /**
     * Little-endian WKB bytes (the ST_ASWKB form: {@code 0101000000…}); a shape with a third coordinate is written
     * as ISO WKB, its type code raised by 1000 ({@code 01E9030000…}, live-verified).
     */
    public static byte[] toWkb(final JsonNode model) {
        final List<Byte> out = new ArrayList<Byte>();
        writeWkb(model, out);
        final byte[] bytes = new byte[out.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = out.get(i);
        }
        return bytes;
    }

    private static void writeWkb(final JsonNode model, final List<Byte> out) {
        final String type = model.get("type").asText();
        final int zCode = hasZ(model) ? 1000 : 0;
        out.add((byte) 1);   // little-endian
        switch (type) {
            case "Point":
                writeWkbInt(1 + zCode, out);
                writeWkbPoint(model.get("coordinates"), out);
                return;
            case "LineString":
                writeWkbInt(2 + zCode, out);
                writeWkbLine(model.get("coordinates"), out);
                return;
            case "Polygon": {
                writeWkbInt(3 + zCode, out);
                final JsonNode rings = model.get("coordinates");
                writeWkbInt(rings.size(), out);
                for (final JsonNode ring : rings) {
                    writeWkbLine(ring, out);
                }
                return;
            }
            case "MultiPoint": {
                writeWkbInt(4 + zCode, out);
                final JsonNode points = model.get("coordinates");
                writeWkbInt(points.size(), out);
                for (final JsonNode pt : points) {
                    out.add((byte) 1);
                    writeWkbInt(1 + zCode, out);
                    writeWkbPoint(pt, out);
                }
                return;
            }
            default: {
                // MultiLineString(5) / MultiPolygon(6) / GeometryCollection(7)
                final int code = "MultiLineString".equals(type) ? 5 : "MultiPolygon".equals(type) ? 6 : 7;
                writeWkbInt(code + zCode, out);
                if (code == 7) {
                    final JsonNode parts = model.get("geometries");
                    writeWkbInt(parts.size(), out);
                    for (final JsonNode part : parts) {
                        writeWkb(part, out);
                    }
                    return;
                }
                final JsonNode members = model.get("coordinates");
                writeWkbInt(members.size(), out);
                for (final JsonNode member : members) {
                    out.add((byte) 1);
                    if (code == 5) {
                        writeWkbInt(2 + zCode, out);
                        writeWkbLine(member, out);
                    } else {
                        writeWkbInt(3 + zCode, out);
                        writeWkbInt(member.size(), out);
                        for (final JsonNode ring : member) {
                            writeWkbLine(ring, out);
                        }
                    }
                }
            }
        }
    }

    private static void writeWkbLine(final JsonNode line, final List<Byte> out) {
        writeWkbInt(line.size(), out);
        for (final JsonNode pt : line) {
            writeWkbPoint(pt, out);
        }
    }

    private static void writeWkbPoint(final JsonNode pt, final List<Byte> out) {
        writeWkbDouble(pt.get(0).asDouble(), out);
        writeWkbDouble(pt.get(1).asDouble(), out);
        if (pt.size() >= 3) {
            writeWkbDouble(pt.get(2).asDouble(), out);
        }
    }

    private static void writeWkbInt(final int v, final List<Byte> out) {
        for (int i = 0; i < 4; i++) {
            out.add((byte) ((v >> (8 * i)) & 0xFF));
        }
    }

    private static void writeWkbDouble(final double d, final List<Byte> out) {
        final long bits = Double.doubleToLongBits(d);
        for (int i = 0; i < 8; i++) {
            out.add((byte) ((bits >> (8 * i)) & 0xFF));
        }
    }

    // ------------------------------------------------------------------ traversal helpers

    /** Every coordinate pair of the model, flattened. */
    public static List<double[]> allPoints(final JsonNode model) {
        final List<double[]> points = new ArrayList<double[]>();
        collectPoints(model, points);
        return points;
    }

    private static void collectPoints(final JsonNode model, final List<double[]> out) {
        final String type = model.get("type").asText();
        if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                collectPoints(part, out);
            }
            return;
        }
        collectFromCoordinates(model.get("coordinates"), out);
    }

    private static void collectFromCoordinates(final JsonNode coordinates, final List<double[]> out) {
        if (coordinates.size() > 0 && coordinates.get(0).isNumber()) {
            out.add(new double[] {coordinates.get(0).asDouble(), coordinates.get(1).asDouble()});
            return;
        }
        for (final JsonNode member : coordinates) {
            collectFromCoordinates(member, out);
        }
    }

    private static void collectSegments(final JsonNode model, final List<double[][]> out) {
        final String type = model.get("type").asText();
        switch (type) {
            case "GeometryCollection":
                for (final JsonNode part : model.get("geometries")) {
                    collectSegments(part, out);
                }
                return;
            case "LineString":
                segmentsOfLine(model.get("coordinates"), out);
                return;
            case "MultiLineString":
            case "Polygon":
                for (final JsonNode line : model.get("coordinates")) {
                    segmentsOfLine(line, out);
                }
                return;
            case "MultiPolygon":
                for (final JsonNode poly : model.get("coordinates")) {
                    for (final JsonNode ring : poly) {
                        segmentsOfLine(ring, out);
                    }
                }
                return;
            default:
                // points contribute no segments
        }
    }

    private static void segmentsOfLine(final JsonNode line, final List<double[][]> out) {
        for (int i = 0; i + 1 < line.size(); i++) {
            out.add(new double[][] {
                {line.get(i).get(0).asDouble(), line.get(i).get(1).asDouble()},
                {line.get(i + 1).get(0).asDouble(), line.get(i + 1).get(1).asDouble()}});
        }
    }

    /** The polygon rings of the model (outer first per polygon), or empty when it has none. */
    public static List<JsonNode> polygonRingSets(final JsonNode model) {
        final List<JsonNode> polygons = new ArrayList<JsonNode>();
        final String type = model.get("type").asText();
        if ("Polygon".equals(type)) {
            polygons.add(model.get("coordinates"));
        } else if ("MultiPolygon".equals(type)) {
            for (final JsonNode poly : model.get("coordinates")) {
                polygons.add(poly);
            }
        } else if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                polygons.addAll(polygonRingSets(part));
            }
        }
        return polygons;
    }

    // ------------------------------------------------------------------ measures

    /** Great-circle distance between two lon/lat points on Snowflake's sphere (meters). */
    public static double sphereDistance(final double lon1, final double lat1,
                                        final double lon2, final double lat2) {
        return EARTH_RADIUS_M * centralAngle(lon1, lat1, lon2, lat2);
    }

    private static double centralAngle(final double lon1, final double lat1,
                                       final double lon2, final double lat2) {
        final double p1 = Math.toRadians(lat1);
        final double p2 = Math.toRadians(lat2);
        final double dp = Math.toRadians(lat2 - lat1);
        final double dl = Math.toRadians(lon2 - lon1);
        final double a = Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double pointDistance(final boolean spherical, final double[] p, final double[] q) {
        if (spherical) {
            return sphereDistance(p[0], p[1], q[0], q[1]);
        }
        return Math.hypot(p[0] - q[0], p[1] - q[1]);
    }

    /** Total length: great-circle meters for geography, planar units for geometry. */
    public static double length(final GeoValue value) {
        double total = 0;
        for (final double[][] seg : lineSegmentsOnly(value.node())) {
            total += pointDistance(value.isGeography(), seg[0], seg[1]);
        }
        return total;
    }

    private static List<double[][]> lineSegmentsOnly(final JsonNode model) {
        final List<double[][]> segments = new ArrayList<double[][]>();
        final String type = model.get("type").asText();
        if ("LineString".equals(type)) {
            segmentsOfLine(model.get("coordinates"), segments);
        } else if ("MultiLineString".equals(type)) {
            for (final JsonNode line : model.get("coordinates")) {
                segmentsOfLine(line, segments);
            }
        } else if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                segments.addAll(lineSegmentsOnly(part));
            }
        }
        return segments;
    }

    /** Perimeter of the polygon rings: spherical meters or planar units. */
    public static double perimeter(final GeoValue value) {
        double total = 0;
        for (final JsonNode rings : polygonRingSets(value.node())) {
            for (final JsonNode ring : rings) {
                final List<double[][]> segments = new ArrayList<double[][]>();
                segmentsOfLine(ring, segments);
                for (final double[][] seg : segments) {
                    total += pointDistance(value.isGeography(), seg[0], seg[1]);
                }
            }
        }
        return total;
    }

    /**
     * Area: planar shoelace for geometry; for geography the spherical polygon excess (L'Huilier
     * over a fan triangulation), matching Snowflake's sphere to within floating tolerance.
     */
    public static double area(final GeoValue value) {
        double total = 0;
        for (final JsonNode rings : polygonRingSets(value.node())) {
            for (int r = 0; r < rings.size(); r++) {
                final double ringArea = value.isGeography()
                    ? sphericalRingArea(rings.get(r)) : planarRingArea(rings.get(r));
                total += r == 0 ? ringArea : -ringArea;
            }
        }
        return Math.abs(total);
    }

    private static double planarRingArea(final JsonNode ring) {
        double sum = 0;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            sum += ring.get(j).get(0).asDouble() * ring.get(i).get(1).asDouble()
                 - ring.get(i).get(0).asDouble() * ring.get(j).get(1).asDouble();
        }
        return Math.abs(sum) / 2;
    }

    private static double sphericalRingArea(final JsonNode ring) {
        // Fan triangulation from the first vertex; each triangle's spherical excess via L'Huilier.
        double total = 0;
        final double[] a = {ring.get(0).get(0).asDouble(), ring.get(0).get(1).asDouble()};
        for (int i = 1; i + 1 < ring.size(); i++) {
            final double[] b = {ring.get(i).get(0).asDouble(), ring.get(i).get(1).asDouble()};
            final double[] c = {ring.get(i + 1).get(0).asDouble(), ring.get(i + 1).get(1).asDouble()};
            total += signedTriangleExcess(a, b, c);
        }
        return Math.abs(total) * EARTH_RADIUS_M * EARTH_RADIUS_M;
    }

    private static double signedTriangleExcess(final double[] a, final double[] b, final double[] c) {
        final double ab = centralAngle(a[0], a[1], b[0], b[1]);
        final double bc = centralAngle(b[0], b[1], c[0], c[1]);
        final double ca = centralAngle(c[0], c[1], a[0], a[1]);
        final double s = (ab + bc + ca) / 2;
        final double t = Math.tan(s / 2) * Math.tan((s - ab) / 2)
            * Math.tan((s - bc) / 2) * Math.tan((s - ca) / 2);
        final double excess = 4 * Math.atan(Math.sqrt(Math.max(0, t)));
        // Orientation sign so holes/fan overlaps cancel correctly.
        final double orientation = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        return orientation >= 0 ? excess : -excess;
    }

    /** The [minX, maxX, minY, maxY] extent of the model. */
    public static double[] extent(final JsonNode model) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (final double[] p : allPoints(model)) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        return new double[] {minX, maxX, minY, maxY};
    }

    /** Vertex-averaged centroid (length-weighted for lines, ring-average for polygons). */
    public static double[] centroid(final JsonNode model) {
        final String type = model.get("type").asText();
        if ("Point".equals(type)) {
            return new double[] {model.get("coordinates").get(0).asDouble(),
                model.get("coordinates").get(1).asDouble()};
        }
        if ("LineString".equals(type) || "MultiLineString".equals(type)) {
            double weight = 0;
            double x = 0;
            double y = 0;
            final List<double[][]> segments = new ArrayList<double[][]>();
            collectSegments(model, segments);
            for (final double[][] seg : segments) {
                final double len = Math.hypot(seg[1][0] - seg[0][0], seg[1][1] - seg[0][1]);
                weight += len;
                x += len * (seg[0][0] + seg[1][0]) / 2;
                y += len * (seg[0][1] + seg[1][1]) / 2;
            }
            if (weight == 0) {
                return null;
            }
            return new double[] {x / weight, y / weight};
        }
        // Polygon family: planar area-weighted centroid over outer rings (an approximation of
        // Snowflake's spherical centroid — within ~1e-4 degrees at test scales).
        double areaSum = 0;
        double cx = 0;
        double cy = 0;
        for (final JsonNode rings : polygonRingSets(model)) {
            final JsonNode ring = rings.get(0);
            double a = 0;
            double sx = 0;
            double sy = 0;
            for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
                final double xi = ring.get(i).get(0).asDouble();
                final double yi = ring.get(i).get(1).asDouble();
                final double xj = ring.get(j).get(0).asDouble();
                final double yj = ring.get(j).get(1).asDouble();
                final double f = xj * yi - xi * yj;
                a += f;
                sx += (xj + xi) * f;
                sy += (yj + yi) * f;
            }
            if (a != 0) {
                areaSum += a / 2;
                cx += sx / 6;
                cy += sy / 6;
            }
        }
        if (areaSum == 0) {
            // degenerate: fall back to the vertex average
            final List<double[]> pts = allPoints(model);
            double x = 0;
            double y = 0;
            for (final double[] p : pts) {
                x += p[0];
                y += p[1];
            }
            return pts.isEmpty() ? null : new double[] {x / pts.size(), y / pts.size()};
        }
        return new double[] {cx / areaSum, cy / areaSum};
    }

    /** A computed Point model with double coordinates (renders like Snowflake's computed points). */
    public static JsonNode pointModel(final double x, final double y) {
        final ObjectNode out = MAPPER.createObjectNode();
        final ArrayNode coords = MAPPER.createArrayNode();
        coords.add(x);
        coords.add(y);
        out.set("coordinates", coords);
        out.put("type", "Point");
        return out;
    }
}
