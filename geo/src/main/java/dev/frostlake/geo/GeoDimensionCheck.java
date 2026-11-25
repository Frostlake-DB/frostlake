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
 * Refuses a GEOMETRY whose points do not all carry the same number of coordinates (live-verified): a point of more
 * than three ({@code Invalid GeoJSON. Point array can have at most 3 elements.}), and a point of two among points of
 * three or the other way round, anywhere in the value, a collection's members included
 * ({@code Geometry validation failed: GeoJSON::MultiPoint::Point: points in 'coordinates' should have the same
 * dimension.}, the path naming the shapes down to the one holding the odd point). Points are compared in reading
 * order against the value's first point.
 */
final class GeoDimensionCheck {

    /** The number of coordinates of the value's first point, 0 until one is seen. */
    private int dims;

    private GeoDimensionCheck() {
    }

    /** Checks the model of a GEOMETRY. */
    static void check(final JsonNode model) {
        new GeoDimensionCheck().shape(model, "");
    }

    private void shape(final JsonNode model, final String prefix) {
        final String type = model.get("type").asText();
        if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                shape(part, "GeometryCollection::");
            }
            return;
        }
        final JsonNode coordinates = model.get("coordinates");
        switch (type) {
            case "Point":
                point(coordinates, prefix + "Point");
                return;
            case "LineString":
                points(coordinates, prefix + "LineString");
                return;
            case "MultiPoint":
                points(coordinates, prefix + "MultiPoint::Point");
                return;
            case "Polygon":
                for (final JsonNode ring : coordinates) {
                    points(ring, prefix + "Polygon::Loop");
                }
                return;
            case "MultiLineString":
                for (final JsonNode line : coordinates) {
                    points(line, prefix + "MultiLineString::LineString");
                }
                return;
            case "MultiPolygon":
                for (final JsonNode poly : coordinates) {
                    for (final JsonNode ring : poly) {
                        points(ring, prefix + "MultiPolygon::Polygon::Loop");
                    }
                }
                return;
            default:
                // no other shape carries coordinates
        }
    }

    private void points(final JsonNode list, final String path) {
        for (final JsonNode pt : list) {
            point(pt, path);
        }
    }

    private void point(final JsonNode pt, final String path) {
        if (pt.size() > 3) {
            throw new RuntimeException("Invalid GeoJSON. Point array can have at most 3 elements.");
        }
        if (dims == 0) {
            dims = pt.size();
        } else if (pt.size() != dims) {
            throw new RuntimeException("Geometry validation failed: GeoJSON::" + path
                + ": points in 'coordinates' should have the same dimension.");
        }
    }
}
