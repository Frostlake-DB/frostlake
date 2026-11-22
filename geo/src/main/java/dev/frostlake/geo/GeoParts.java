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

import java.util.ArrayList;
import java.util.List;

/**
 * A geospatial value taken apart into its points, its line strings and its polygons (each polygon its outer ring
 * first, then its holes; every ring closed), across Multi* forms and collection members alike.
 */
public final class GeoParts {

    private final List<double[]> points = new ArrayList<double[]>();
    private final List<double[][]> lines = new ArrayList<double[][]>();
    private final List<double[][][]> polygons = new ArrayList<double[][][]>();

    /** Takes the GeoJSON model apart. */
    public GeoParts(final JsonNode model) {
        collect(model);
    }

    private GeoParts() {
    }

    /** One polygon alone, given as its rings (outer first). */
    public static GeoParts ofPolygon(final double[][][] rings) {
        final GeoParts parts = new GeoParts();
        parts.polygons.add(rings);
        return parts;
    }

    private void collect(final JsonNode model) {
        final String type = model.get("type").asText();
        final JsonNode coordinates = model.get("coordinates");
        switch (type) {
            case "Point":
                points.add(point(coordinates));
                return;
            case "MultiPoint":
                for (final JsonNode pt : coordinates) {
                    points.add(point(pt));
                }
                return;
            case "LineString":
                lines.add(line(coordinates));
                return;
            case "MultiLineString":
                for (final JsonNode line : coordinates) {
                    lines.add(line(line));
                }
                return;
            case "Polygon":
                polygons.add(polygon(coordinates));
                return;
            case "MultiPolygon":
                for (final JsonNode poly : coordinates) {
                    polygons.add(polygon(poly));
                }
                return;
            case "GeometryCollection":
                for (final JsonNode part : model.get("geometries")) {
                    collect(part);
                }
                return;
            default:
                // no other shape carries coordinates
        }
    }

    private static double[] point(final JsonNode pt) {
        return new double[] {pt.get(0).asDouble(), pt.get(1).asDouble()};
    }

    private static double[][] line(final JsonNode line) {
        final double[][] out = new double[line.size()][];
        for (int i = 0; i < line.size(); i++) {
            out[i] = point(line.get(i));
        }
        return out;
    }

    private static double[][][] polygon(final JsonNode rings) {
        final double[][][] out = new double[rings.size()][][];
        for (int r = 0; r < rings.size(); r++) {
            out[r] = line(rings.get(r));
        }
        return out;
    }

    /** The lone points (Point and MultiPoint members). */
    public List<double[]> points() {
        return points;
    }

    /** The line strings, each its vertices in order. */
    public List<double[][]> lines() {
        return lines;
    }

    /** The polygons, each its rings (outer first). */
    public List<double[][][]> polygons() {
        return polygons;
    }

    /** Every vertex: the points, the line vertices and the ring vertices. */
    public List<double[]> vertices() {
        final List<double[]> out = new ArrayList<double[]>(points);
        for (final double[][] line : lines) {
            for (final double[] p : line) {
                out.add(p);
            }
        }
        for (final double[][][] rings : polygons) {
            for (final double[][] ring : rings) {
                for (final double[] p : ring) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Every edge of the line strings, as {@code {from, to}}. */
    public List<double[][]> lineEdges() {
        final List<double[][]> out = new ArrayList<double[][]>();
        for (final double[][] line : lines) {
            addEdges(line, out);
        }
        return out;
    }

    /** Every edge of the polygon rings, as {@code {from, to}}. */
    public List<double[][]> ringEdges() {
        final List<double[][]> out = new ArrayList<double[][]>();
        for (final double[][][] rings : polygons) {
            for (final double[][] ring : rings) {
                addEdges(ring, out);
            }
        }
        return out;
    }

    /** Every edge, line strings' and rings' alike. */
    public List<double[][]> edges() {
        final List<double[][]> out = lineEdges();
        out.addAll(ringEdges());
        return out;
    }

    private static void addEdges(final double[][] line, final List<double[][]> out) {
        for (int i = 0; i + 1 < line.length; i++) {
            out.add(new double[][] {line[i], line[i + 1]});
        }
    }
}
