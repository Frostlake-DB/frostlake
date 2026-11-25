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
 * A GeoJSON text whose {@code coordinates} do not hold the shape its {@code type} names, refused with the reader's
 * own sentence rather than the general geo-input one ({@code Invalid GeoJSON. 'coordinates' must not be empty.} for a
 * GEOMETRY, {@code GeoJSON::Polygon: 'coordinates' must not be empty.} for a GEOGRAPHY, live-verified).
 */
public class GeoJsonFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the whole sentence
     */
    public GeoJsonFormatException(final String message) {
        super(message);
    }
}
