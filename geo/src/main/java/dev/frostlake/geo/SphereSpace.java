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

/**
 * The sphere of GEOGRAPHY: an edge is the shorter great-circle arc between its vertices (so an edge from longitude
 * 170 to -170 crosses the antimeridian), distances are meters on the sphere of {@link GeoShapes#EARTH_RADIUS_M}, and
 * a ring encloses the smaller of the two regions it bounds. The math runs on unit vectors.
 */
public final class SphereSpace implements GeoSpace {

    /** The tolerance, in radians, under which a point counts as lying on a great circle (well under a millimeter). */
    private static final double TOLERANCE = 1e-12;
    /** The step {@link #offsetFromMiddle} takes, relative to the edge's length. */
    private static final double OFFSET = 1e-7;

    @Override
    public boolean spherical() {
        return true;
    }

    @Override
    public boolean samePoint(final double[] p, final double[] q) {
        if (p[0] == q[0] && p[1] == q[1]) {
            return true;
        }
        final double[] u = vector(p);
        final double[] v = vector(q);
        return Math.abs(u[0] - v[0]) < 1e-15 && Math.abs(u[1] - v[1]) < 1e-15 && Math.abs(u[2] - v[2]) < 1e-15;
    }

    @Override
    public boolean onEdge(final double[] p, final double[] a, final double[] b) {
        if (samePoint(p, a) || samePoint(p, b)) {
            return true;
        }
        final double[] u = vector(a);
        final double[] v = vector(b);
        final double[] w = vector(p);
        final double[] n = cross(u, v);
        final double len = norm(n);
        if (len < TOLERANCE) {
            return false;
        }
        if (Math.abs(dot(w, n)) > TOLERANCE * len) {
            return false;
        }
        return dot(cross(u, w), n) >= 0 && dot(cross(w, v), n) >= 0 && dot(w, add(u, v)) > 0;
    }

    /** The side of {@code w} against the plane of normal {@code n}: 1, -1, or 0 within the tolerance. */
    private static int side(final double[] n, final double[] w) {
        final double d = dot(w, n);
        if (Math.abs(d) <= TOLERANCE * norm(n)) {
            return 0;
        }
        return d > 0 ? 1 : -1;
    }

    @Override
    public double[] crossing(final double[] a, final double[] b, final double[] c, final double[] d) {
        final double[] u1 = vector(a);
        final double[] v1 = vector(b);
        final double[] u2 = vector(c);
        final double[] v2 = vector(d);
        final double[] n1 = cross(u1, v1);
        final double[] n2 = cross(u2, v2);
        if (side(n1, u2) * side(n1, v2) >= 0 || side(n2, u1) * side(n2, v1) >= 0) {
            return null;
        }
        double[] x = normalize(cross(n1, n2));
        if (dot(x, add(u1, v1)) < 0) {
            x = scale(x, -1);
        }
        if (dot(x, add(u2, v2)) <= 0) {
            return null;
        }
        return lonLat(x);
    }

    @Override
    public double position(final double[] a, final double[] b, final double[] p) {
        final double[] u = vector(a);
        final double[] w = vector(p);
        return Math.atan2(norm(cross(u, w)), dot(u, w));
    }

    @Override
    public double[] midpoint(final double[] a, final double[] b) {
        return lonLat(normalize(add(vector(a), vector(b))));
    }

    @Override
    public double[] offsetFromMiddle(final double[] a, final double[] b, final int side) {
        final double[] u = vector(a);
        final double[] v = vector(b);
        final double[] m = normalize(add(u, v));
        final double[] n = normalize(cross(u, v));
        final double step = side * OFFSET * Math.atan2(norm(cross(u, v)), dot(u, v));
        return lonLat(normalize(add(m, scale(n, step))));
    }

    @Override
    public boolean ringEncloses(final double[][] ring, final double[] p) {
        // Count the ring's edges crossed on the way from p to a point outside the ring: the antipode of the
        // ring's vertex mean, outside the smaller region the ring bounds.
        double[] sum = {0, 0, 0};
        final boolean closed = ring.length > 1 && samePoint(ring[0], ring[ring.length - 1]);
        final int vertices = closed ? ring.length - 1 : ring.length;
        for (int i = 0; i < vertices; i++) {
            sum = add(sum, vector(ring[i]));
        }
        final double[] outside = norm(sum) < 1e-9 ? new double[] {0, 0, 1} : normalize(scale(sum, -1));
        final double[] start = vector(p);
        final int crossings;
        if (dot(start, outside) > -0.5) {
            crossings = crossingsAlong(ring, start, outside);
        } else {
            // a nearly antipodal path is ill-defined as one arc: go by a point a quarter turn away
            double[] axis = cross(start, outside);
            if (norm(axis) < 1e-9) {
                axis = cross(start, Math.abs(start[0]) < 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0});
            }
            final double[] via = normalize(cross(axis, start));
            crossings = crossingsAlong(ring, start, via) + crossingsAlong(ring, via, outside);
        }
        return crossings % 2 == 1;
    }

    /** How many ring edges the arc from {@code s} to {@code t} crosses (a vertex on the arc counts half-open). */
    private static int crossingsAlong(final double[][] ring, final double[] s, final double[] t) {
        final double[] n = cross(s, t);
        int count = 0;
        for (int i = 0; i + 1 < ring.length; i++) {
            final double[] u = vector(ring[i]);
            final double[] v = vector(ring[i + 1]);
            if (dot(u, n) > 0 == dot(v, n) > 0) {
                continue;
            }
            final double[] m = cross(u, v);
            final double[] dir = cross(n, m);
            if (norm(dir) == 0) {
                continue;
            }
            double[] x = normalize(dir);
            if (dot(x, add(u, v)) < 0) {
                x = scale(x, -1);
            }
            if (dot(cross(s, x), n) >= 0 && dot(cross(x, t), n) >= 0) {
                count++;
            }
        }
        return count;
    }

    @Override
    public double distance(final double[] p, final double[] q) {
        return GeoShapes.sphereDistance(p[0], p[1], q[0], q[1]);
    }

    @Override
    public double edgeDistance(final double[] p, final double[] a, final double[] b) {
        final double[] u = vector(a);
        final double[] v = vector(b);
        final double[] w = vector(p);
        final double[] n = cross(u, v);
        final double len = norm(n);
        final double ends = Math.min(distance(p, a), distance(p, b));
        if (len < TOLERANCE) {
            return ends;
        }
        final double[] unit = scale(n, 1 / len);
        final double off = dot(w, unit);
        final double[] projected = add(w, scale(unit, -off));
        final double along = norm(projected);
        if (along < TOLERANCE || dot(cross(u, projected), n) < 0 || dot(cross(projected, v), n) < 0) {
            return ends;
        }
        return Math.min(ends, GeoShapes.EARTH_RADIUS_M * Math.atan2(Math.abs(off), along));
    }

    // ------------------------------------------------------------------ vectors

    private static double[] vector(final double[] lonLat) {
        final double lon = Math.toRadians(lonLat[0]);
        final double lat = Math.toRadians(lonLat[1]);
        final double c = Math.cos(lat);
        return new double[] {c * Math.cos(lon), c * Math.sin(lon), Math.sin(lat)};
    }

    private static double[] lonLat(final double[] v) {
        return new double[] {Math.toDegrees(Math.atan2(v[1], v[0])),
            Math.toDegrees(Math.atan2(v[2], Math.hypot(v[0], v[1])))};
    }

    private static double[] cross(final double[] a, final double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(final double[] a, final double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] add(final double[] a, final double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] scale(final double[] a, final double k) {
        return new double[] {a[0] * k, a[1] * k, a[2] * k};
    }

    private static double norm(final double[] a) {
        return Math.sqrt(dot(a, a));
    }

    private static double[] normalize(final double[] a) {
        return scale(a, 1 / norm(a));
    }
}
