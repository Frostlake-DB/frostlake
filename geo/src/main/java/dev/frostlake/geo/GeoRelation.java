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

import dev.frostlake.values.GeoValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The spatial relations behind ST_INTERSECTS, ST_CONTAINS / ST_WITHIN and ST_DISTANCE / ST_DWITHIN, decided on whole
 * edges rather than vertices alone: an edge is cut where the other value's edges cross or touch it, and every piece
 * is placed against the other value. A GEOMETRY is decided in the plane, a GEOGRAPHY on the sphere with great-circle
 * edges.
 *
 * <p>Containment follows the DE-9IM reading (live-verified): {@code a} contains {@code b} when no part of {@code b}
 * lies outside {@code a} and some part of {@code b} lies in the interior of {@code a} — so a point on a polygon's
 * boundary or at a line's end point is not contained, a line or polygon may share boundary with its container, a point
 * contains itself, and a line contains its inner points. A line's boundary is its end points, by the odd-count rule
 * for GEOMETRY (a closed line has none) and every end point for GEOGRAPHY. A GEOGRAPHY polygon in addition does not
 * contain a value with a vertex on the inside of one of its edges (live-verified: such a touch counts as leaving it).
 */
public final class GeoRelation {

    private static final int EXTERIOR = 0;
    private static final int BOUNDARY = 1;
    private static final int INTERIOR = 2;

    private static final GeoSpace PLANE = new PlanarSpace();
    private static final GeoSpace SPHERE = new SphereSpace();

    private GeoRelation() {
    }

    /** The space a value's relations are decided in. */
    public static GeoSpace spaceOf(final GeoValue value) {
        return value.isGeography() ? SPHERE : PLANE;
    }

    /** Whether the two values share at least one point. */
    public static boolean intersects(final GeoValue x, final GeoValue y) {
        return intersects(spaceOf(x), new GeoParts(x.node()), new GeoParts(y.node()));
    }

    /**
     * Refuses a GeometryCollection on either side of ST_CONTAINS / ST_WITHIN, in the words of the kind
     * (live-verified: GEOGRAPHY names {@code 'ST_CONTAINS'} for both functions, GEOMETRY
     * {@code 'ST_CONTAINS/ST_WITHIN'}).
     */
    public static void requireNoCollection(final GeoValue x, final GeoValue y) {
        if ("GeometryCollection".equals(x.geoType()) || "GeometryCollection".equals(y.geoType())) {
            throw new RuntimeException("Operation '" + (x.isGeography() ? "ST_CONTAINS" : "ST_CONTAINS/ST_WITHIN")
                + "' is not supported for GeometryCollection and FeatureCollection.");
        }
    }

    /** Whether {@code outer} contains {@code inner}. */
    public static boolean contains(final GeoValue outer, final GeoValue inner) {
        final GeoSpace space = spaceOf(outer);
        final GeoParts a = new GeoParts(outer.node());
        final GeoParts b = new GeoParts(inner.node());
        if (space.spherical() && touchesEdgeInside(space, a, b)) {
            return false;
        }
        return covers(space, a, b) && interiorsMeet(space, a, b);
    }

    /** The minimum distance between the two values: meters on the sphere, coordinate units in the plane. */
    public static double distance(final GeoValue x, final GeoValue y) {
        final GeoSpace space = spaceOf(x);
        final GeoParts a = new GeoParts(x.node());
        final GeoParts b = new GeoParts(y.node());
        if (intersects(space, a, b)) {
            return 0;
        }
        return Math.min(closest(space, a, b), closest(space, b, a));
    }

    // ------------------------------------------------------------------ relations

    private static boolean intersects(final GeoSpace space, final GeoParts a, final GeoParts b) {
        for (final double[] p : b.vertices()) {
            if (locate(space, a, p) != EXTERIOR) {
                return true;
            }
        }
        for (final double[] p : a.vertices()) {
            if (locate(space, b, p) != EXTERIOR) {
                return true;
            }
        }
        final List<double[][]> bEdges = b.edges();
        for (final double[][] e : a.edges()) {
            for (final double[][] f : bEdges) {
                if (space.crossing(e[0], e[1], f[0], f[1]) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The smallest distance from a vertex of {@code from} to any point or edge of {@code to}. */
    private static double closest(final GeoSpace space, final GeoParts from, final GeoParts to) {
        double best = Double.MAX_VALUE;
        final List<double[]> targets = to.vertices();
        final List<double[][]> edges = to.edges();
        for (final double[] p : from.vertices()) {
            for (final double[] q : targets) {
                best = Math.min(best, space.distance(p, q));
            }
            for (final double[][] e : edges) {
                best = Math.min(best, space.edgeDistance(p, e[0], e[1]));
            }
        }
        return best;
    }

    /** Whether no part of {@code b} lies outside {@code a}. */
    private static boolean covers(final GeoSpace space, final GeoParts a, final GeoParts b) {
        for (final double[] p : b.points()) {
            if (locate(space, a, p) == EXTERIOR) {
                return false;
            }
        }
        for (final double[][] e : b.edges()) {
            for (final double[] p : piecePoints(space, e, a)) {
                if (locate(space, a, p) == EXTERIOR) {
                    return false;
                }
            }
        }
        if (b.polygons().isEmpty()) {
            return true;
        }
        if (a.polygons().isEmpty()) {
            return false;
        }
        for (final double[][][] poly : b.polygons()) {
            // b's boundary is inside a: what remains is a's exterior (a hole) reaching into b's interior.
            final GeoParts cutter = GeoParts.ofPolygon(poly);
            for (final double[][] e : a.ringEdges()) {
                for (final double[] m : pieceMidpoints(space, e, cutter)) {
                    if (classify(space, poly, m) == INTERIOR) {
                        return false;
                    }
                }
            }
            final double[] inside = interiorPoint(space, poly);
            if (inside != null && locate(space, a, inside) == EXTERIOR) {
                return false;
            }
        }
        return true;
    }

    /** Whether some of {@code b}'s interior lies in {@code a}'s interior ({@code b} known covered by {@code a}). */
    private static boolean interiorsMeet(final GeoSpace space, final GeoParts a, final GeoParts b) {
        if (!b.polygons().isEmpty()) {
            return true;
        }
        for (final double[] p : b.points()) {
            if (locate(space, a, p) == INTERIOR) {
                return true;
            }
        }
        for (final double[][] line : b.lines()) {
            for (final double[] p : line) {
                if (!onLineBoundary(space, b, p) && locate(space, a, p) == INTERIOR) {
                    return true;
                }
            }
            for (int i = 0; i + 1 < line.length; i++) {
                for (final double[] m : pieceMidpoints(space, new double[][] {line[i], line[i + 1]}, a)) {
                    if (locate(space, a, m) == INTERIOR) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether a vertex of {@code b} lies on one of {@code a}'s ring edges other than at that edge's ends. */
    private static boolean touchesEdgeInside(final GeoSpace space, final GeoParts a, final GeoParts b) {
        final List<double[][]> ringEdges = a.ringEdges();
        if (ringEdges.isEmpty()) {
            return false;
        }
        for (final double[] p : b.vertices()) {
            for (final double[][] e : ringEdges) {
                if (space.onEdge(p, e[0], e[1]) && !space.samePoint(p, e[0]) && !space.samePoint(p, e[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ locating points

    /** Where {@code p} lies against the value: its interior, its boundary, or outside it. */
    private static int locate(final GeoSpace space, final GeoParts value, final double[] p) {
        int where = EXTERIOR;
        for (final double[] q : value.points()) {
            if (space.samePoint(p, q)) {
                return INTERIOR;
            }
        }
        for (final double[][][] poly : value.polygons()) {
            final int place = classify(space, poly, p);
            if (place == INTERIOR) {
                return INTERIOR;
            }
            where = Math.max(where, place);
        }
        for (final double[][] line : value.lines()) {
            for (int i = 0; i + 1 < line.length; i++) {
                if (space.onEdge(p, line[i], line[i + 1])) {
                    if (!onLineBoundary(space, value, p)) {
                        return INTERIOR;
                    }
                    where = Math.max(where, BOUNDARY);
                }
            }
        }
        return where;
    }

    /** Whether {@code p} is an end point that bounds the value's lines. */
    private static boolean onLineBoundary(final GeoSpace space, final GeoParts value, final double[] p) {
        int ends = 0;
        for (final double[][] line : value.lines()) {
            if (line.length == 0) {
                continue;
            }
            final boolean first = space.samePoint(p, line[0]);
            final boolean last = space.samePoint(p, line[line.length - 1]);
            if (space.spherical() && (first || last)) {
                return true;
            }
            ends += (first ? 1 : 0) + (last ? 1 : 0);
        }
        return ends % 2 == 1;
    }

    /** Where {@code p} lies against one polygon (outer ring first, then holes). */
    private static int classify(final GeoSpace space, final double[][][] rings, final double[] p) {
        for (final double[][] ring : rings) {
            for (int i = 0; i + 1 < ring.length; i++) {
                if (space.onEdge(p, ring[i], ring[i + 1])) {
                    return BOUNDARY;
                }
            }
        }
        if (!space.ringEncloses(rings[0], p)) {
            return EXTERIOR;
        }
        for (int r = 1; r < rings.length; r++) {
            if (space.ringEncloses(rings[r], p)) {
                return EXTERIOR;
            }
        }
        return INTERIOR;
    }

    /** A point inside the polygon: a tiny step off the middle of one of its outer edges, to the inner side. */
    private static double[] interiorPoint(final GeoSpace space, final double[][][] rings) {
        final double[][] outer = rings[0];
        for (int i = 0; i + 1 < outer.length; i++) {
            if (space.samePoint(outer[i], outer[i + 1])) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                final double[] q = space.offsetFromMiddle(outer[i], outer[i + 1], side);
                if (classify(space, rings, q) == INTERIOR) {
                    return q;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ cutting edges

    /** The edge's end points and the middle of every piece it is cut into by {@code cutter}. */
    private static List<double[]> piecePoints(final GeoSpace space, final double[][] edge, final GeoParts cutter) {
        final List<double[]> out = new ArrayList<double[]>();
        out.add(edge[0]);
        out.add(edge[1]);
        out.addAll(pieceMidpoints(space, edge, cutter));
        return out;
    }

    /** The middle of every piece the edge is cut into where {@code cutter}'s edges and points cross or touch it. */
    private static List<double[]> pieceMidpoints(final GeoSpace space, final double[][] edge, final GeoParts cutter) {
        final double[] from = edge[0];
        final double[] to = edge[1];
        final List<double[]> out = new ArrayList<double[]>();
        if (space.samePoint(from, to)) {
            return out;
        }
        final List<double[]> cuts = new ArrayList<double[]>();
        cuts.add(from);
        cuts.add(to);
        for (final double[][] e : cutter.edges()) {
            if (space.onEdge(e[0], from, to)) {
                cuts.add(e[0]);
            }
            if (space.onEdge(e[1], from, to)) {
                cuts.add(e[1]);
            }
            final double[] x = space.crossing(from, to, e[0], e[1]);
            if (x != null) {
                cuts.add(x);
            }
        }
        for (final double[] q : cutter.points()) {
            if (space.onEdge(q, from, to)) {
                cuts.add(q);
            }
        }
        cuts.sort(new Comparator<double[]>() {
            @Override
            public int compare(final double[] p, final double[] q) {
                return Double.compare(space.position(from, to, p), space.position(from, to, q));
            }
        });
        for (int i = 0; i + 1 < cuts.size(); i++) {
            if (!space.samePoint(cuts.get(i), cuts.get(i + 1))) {
                out.add(space.midpoint(cuts.get(i), cuts.get(i + 1)));
            }
        }
        return out;
    }
}
