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
 * The metric space a geospatial relation is decided in: the plane of GEOMETRY, or the sphere of GEOGRAPHY where an
 * edge between two vertices is the shorter great-circle arc. Points are {@code {x, y}} (longitude, latitude in
 * degrees on the sphere); an edge is the pair of its end points.
 */
public interface GeoSpace {

    /** Whether this is the sphere of GEOGRAPHY. */
    boolean spherical();

    /** Whether two points are the same place. */
    boolean samePoint(double[] p, double[] q);

    /** Whether {@code p} lies on the closed edge {@code a}–{@code b}. */
    boolean onEdge(double[] p, double[] a, double[] b);

    /**
     * The single point where the two edges cross with each passing from one side of the other to its other side, or
     * null when they do not cross that way (they are apart, touch at an end point, or run along each other).
     */
    double[] crossing(double[] a, double[] b, double[] c, double[] d);

    /** A value that grows monotonically with the position of {@code p} along the edge from {@code a} to {@code b}. */
    double position(double[] a, double[] b, double[] p);

    /** The point halfway along the edge {@code a}–{@code b}. */
    double[] midpoint(double[] a, double[] b);

    /**
     * A point a tiny step off the middle of the edge {@code a}–{@code b}, to its left for a positive {@code side} and
     * to its right for a negative one.
     */
    double[] offsetFromMiddle(double[] a, double[] b, int side);

    /** Whether the closed ring encloses {@code p}, a point that is known not to lie on the ring. */
    boolean ringEncloses(double[][] ring, double[] p);

    /** The distance between two points: meters on the sphere, coordinate units in the plane. */
    double distance(double[] p, double[] q);

    /** The distance from {@code p} to the closest point of the edge {@code a}–{@code b}. */
    double edgeDistance(double[] p, double[] a, double[] b);
}
