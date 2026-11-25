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

/**
 * Checks that a GeoJSON shape's {@code coordinates} hold what its {@code type} names, walking them in order and
 * refusing the first fault with the reader's sentence (live-verified). A GEOMETRY reads them with the
 * {@code Invalid GeoJSON. ...} family: an empty array anywhere ({@code 'coordinates' must not be empty.}), a point of
 * fewer than 2 numbers, a line of fewer than 2 points, a loop of fewer than 4, a number where an array belongs
 * ({@code Malformed `coordinates`, expect nested array but found FIXED.}) and a coordinate that is no number. A
 * GEOGRAPHY names the path down to the fault ({@code GeoJSON::MultiPolygon::Polygon: 'coordinates' must not be
 * empty.}), takes an empty MultiPoint, and checks each point's longitude and latitude range as it reaches it. A
 * member of a GeometryCollection is checked the same way, a GEOGRAPHY's path starting at the collection.
 */
final class GeoJsonShapeCheck {

    private static final String GEOMETRY = "Invalid GeoJSON. ";
    private static final String PAIRS = "Lng/Lat pairs";

    private final boolean geography;
    private final String prefix;

    private GeoJsonShapeCheck(final boolean geography, final String prefix) {
        this.geography = geography;
        this.prefix = prefix;
    }

    /**
     * Checks one shape of a known type (not a GeometryCollection).
     *
     * @param type        the shape's type, as GeoJSON spells it
     * @param coordinates its {@code coordinates} member, or null where it has none
     * @param geography   whether the text is read as a GEOGRAPHY
     * @param prefix      the path ahead of the shape's own ({@code GeometryCollection::} for a member, else empty)
     */
    static void check(final String type, final JsonNode coordinates, final boolean geography, final String prefix) {
        new GeoJsonShapeCheck(geography, prefix).shape(type, coordinates);
    }

    private void shape(final String type, final JsonNode coordinates) {
        if (coordinates == null) {
            throw fault(type, "object is missing the field: 'coordinates'.", "'coordinates' field is missing.");
        }
        if (!coordinates.isArray()) {
            throw fault(type, "type of the GeoJSON field 'coordinates' is '" + typeName(coordinates)
                + "'. Expected: 'ARRAY'.", "'coordinates' field must be of type ARRAY.");
        }
        switch (type) {
            case "Point":
                point(coordinates, "Point");
                return;
            case "LineString":
                line(coordinates, "LineString");
                return;
            case "Polygon":
                polygon(coordinates, "Polygon");
                return;
            case "MultiPoint":
                if (!geography) {
                    notEmpty(coordinates, "MultiPoint");
                }
                for (final JsonNode pt : coordinates) {
                    element(pt, "MultiPoint::Point", PAIRS);
                    point(pt, "MultiPoint::Point");
                }
                return;
            case "MultiLineString":
                notEmpty(coordinates, "MultiLineString");
                for (final JsonNode line : coordinates) {
                    element(line, "MultiLineString::LineString", "Point");
                    line(line, "MultiLineString::LineString");
                }
                return;
            case "MultiPolygon":
                notEmpty(coordinates, "MultiPolygon");
                for (final JsonNode poly : coordinates) {
                    element(poly, "MultiPolygon::Polygon", "Loop");
                    polygon(poly, "MultiPolygon::Polygon");
                }
                return;
            default:
                // only the known types reach the check
        }
    }

    private void point(final JsonNode pt, final String path) {
        if (!geography) {
            notEmpty(pt, path);
        }
        if (pt.size() < 2) {
            throw fault(path, "'coordinates' malformed. Point array should have 2 elements (Lng/Lat).",
                "Point coordinates must have at least 2 elements.");
        }
        for (int i = 0; i < 2; i++) {
            final JsonNode value = pt.get(i);
            if (!value.isNumber()) {
                throw fault(path, "point coordinate is of non numeric type: '" + typeName(value) + "'.",
                    "Coordinate value is not numeric: '" + typeName(value) + "'.");
            }
        }
        if (geography) {
            try {
                GeoShapes.checkPair(pt, true, prefix + path);
            } catch (final RuntimeException outOfRange) {
                // the range is checked as each point is reached, ahead of a fault further on
                throw new GeoJsonFormatException(outOfRange.getMessage());
            }
        }
    }

    private void line(final JsonNode line, final String path) {
        points(line, path, 2, "LineString array should have at least 2 elements (start/end point).",
            "LineString 'coordinates' must have at least 2 elements.");
    }

    private void loop(final JsonNode ring, final String path) {
        points(ring, path, 4, "Loop array should have at least 4 elements.",
            "Loop 'coordinates' must have at least 4 elements.");
    }

    private void points(final JsonNode points, final String path, final int least, final String geographyShort,
                        final String geometryShort) {
        if (!geography) {
            notEmpty(points, path);
        }
        if (points.size() < least) {
            throw fault(path, "'coordinates' malformed. " + geographyShort, geometryShort);
        }
        for (final JsonNode pt : points) {
            element(pt, path, PAIRS);
            point(pt, path);
        }
    }

    private void polygon(final JsonNode rings, final String path) {
        notEmpty(rings, path);
        for (final JsonNode ring : rings) {
            element(ring, path + "::Loop", "Point");
            loop(ring, path + "::Loop");
        }
    }

    private void notEmpty(final JsonNode array, final String path) {
        if (array.isEmpty()) {
            throw fault(path, "'coordinates' must not be empty.", "'coordinates' must not be empty.");
        }
    }

    /** Refuses a member that is no array where the shape holds arrays of {@code holds}. */
    private void element(final JsonNode member, final String path, final String holds) {
        if (!member.isArray()) {
            throw fault(path, "'coordinates' field does not hold arrays of " + holds + ".",
                "Malformed `coordinates`, expect nested array but found " + typeName(member) + ".");
        }
    }

    private GeoJsonFormatException fault(final String path, final String geographySentence,
                                         final String geometrySentence) {
        return new GeoJsonFormatException(geography
            ? "GeoJSON::" + prefix + path + ": " + geographySentence
            : GEOMETRY + geometrySentence);
    }

    /** The name the reader gives a JSON value's type: a number written without an exponent is FIXED. */
    private static String typeName(final JsonNode value) {
        if (value.isNull()) {
            return "NULL_VALUE";
        }
        if (value.isTextual()) {
            return "TEXT";
        }
        if (value.isBoolean()) {
            return "BOOLEAN";
        }
        if (value.isObject()) {
            return "OBJECT";
        }
        if (value.isArray()) {
            return "ARRAY";
        }
        return value.isIntegralNumber() || value.isBigDecimal() ? "FIXED" : "REAL";
    }
}
