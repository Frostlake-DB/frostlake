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

/** The plane of GEOMETRY: straight edges and Euclidean distance in coordinate units. */
public final class PlanarSpace implements GeoSpace {

    /** The relative tolerance under which a point counts as lying on a line. */
    private static final double TOLERANCE = 1e-12;
    /** The step {@link #offsetFromMiddle} takes, relative to the edge's length. */
    private static final double OFFSET = 1e-7;

    @Override
    public boolean spherical() {
        return false;
    }

    @Override
    public boolean samePoint(final double[] p, final double[] q) {
        return p[0] == q[0] && p[1] == q[1];
    }

    /** The sign of {@code p} against the line through {@code a} and {@code b}: 1 left, -1 right, 0 on it. */
    private int side(final double[] a, final double[] b, final double[] p) {
        final double dx1 = b[0] - a[0];
        final double dy1 = b[1] - a[1];
        final double dx2 = p[0] - a[0];
        final double dy2 = p[1] - a[1];
        final double cross = dx1 * dy2 - dy1 * dx2;
        final double scale = (Math.abs(dx1) + Math.abs(dy1)) * (Math.abs(dx2) + Math.abs(dy2));
        if (Math.abs(cross) <= TOLERANCE * scale) {
            return 0;
        }
        return cross > 0 ? 1 : -1;
    }

    @Override
    public boolean onEdge(final double[] p, final double[] a, final double[] b) {
        if (samePoint(p, a) || samePoint(p, b)) {
            return true;
        }
        if (samePoint(a, b) || side(a, b, p) != 0) {
            return false;
        }
        return p[0] >= Math.min(a[0], b[0]) && p[0] <= Math.max(a[0], b[0])
            && p[1] >= Math.min(a[1], b[1]) && p[1] <= Math.max(a[1], b[1]);
    }

    @Override
    public double[] crossing(final double[] a, final double[] b, final double[] c, final double[] d) {
        final int sc = side(a, b, c);
        final int sd = side(a, b, d);
        final int sa = side(c, d, a);
        final int sb = side(c, d, b);
        if (sc * sd >= 0 || sa * sb >= 0) {
            return null;
        }
        final double rx = b[0] - a[0];
        final double ry = b[1] - a[1];
        final double qx = d[0] - c[0];
        final double qy = d[1] - c[1];
        final double t = ((c[0] - a[0]) * qy - (c[1] - a[1]) * qx) / (rx * qy - ry * qx);
        return new double[] {a[0] + t * rx, a[1] + t * ry};
    }

    @Override
    public double position(final double[] a, final double[] b, final double[] p) {
        return (p[0] - a[0]) * (b[0] - a[0]) + (p[1] - a[1]) * (b[1] - a[1]);
    }

    @Override
    public double[] midpoint(final double[] a, final double[] b) {
        return new double[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
    }

    @Override
    public double[] offsetFromMiddle(final double[] a, final double[] b, final int side) {
        final double[] m = midpoint(a, b);
        // the left normal of a->b is (-dy, dx)
        return new double[] {m[0] - side * OFFSET * (b[1] - a[1]), m[1] + side * OFFSET * (b[0] - a[0])};
    }

    @Override
    public boolean ringEncloses(final double[][] ring, final double[] p) {
        boolean inside = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            final double xi = ring[i][0];
            final double yi = ring[i][1];
            final double xj = ring[j][0];
            final double yj = ring[j][1];
            if ((yi > p[1]) != (yj > p[1]) && p[0] < (xj - xi) * (p[1] - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public double distance(final double[] p, final double[] q) {
        return Math.hypot(p[0] - q[0], p[1] - q[1]);
    }

    @Override
    public double edgeDistance(final double[] p, final double[] a, final double[] b) {
        final double dx = b[0] - a[0];
        final double dy = b[1] - a[1];
        final double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        return distance(p, new double[] {a[0] + t * dx, a[1] + t * dy});
    }
}
