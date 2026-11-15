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

package dev.frostlake.values;

/**
 * Writes a geospatial value in the forms other than its GeoJSON: the module that reads geospatial values supplies
 * one, found through {@link java.util.ServiceLoader}, so a session's output format can give a client a value's WKT or
 * WKB (see {@code dev.frostlake.executor.GeoOutput}).
 */
public interface GeoWriter {

    /**
     * The value's WKT, as ST_ASWKT writes it: {@code POINT(1 2)}, {@code LINESTRING(1 2,3 4.5)}.
     *
     * @param value the value
     * @return its text
     */
    String wkt(GeoValue value);

    /**
     * The value's WKB, little-endian, as ST_ASWKB writes it.
     *
     * @param value the value
     * @return its bytes
     */
    byte[] wkb(GeoValue value);
}
