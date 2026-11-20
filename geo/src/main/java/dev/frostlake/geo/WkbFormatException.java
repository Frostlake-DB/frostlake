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
 * A BINARY that is no well-formed WKB, refused with the reader's own sentence ({@code Error parsing WKB input:
 * Trailing bytes not recognized.}); the same bytes written as hex text are refused with the general geo-input
 * sentence instead (live-verified).
 */
public class WkbFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param detail what is wrong with the bytes, a whole sentence
     */
    public WkbFormatException(final String detail) {
        super("Error parsing WKB input: " + detail);
    }
}
