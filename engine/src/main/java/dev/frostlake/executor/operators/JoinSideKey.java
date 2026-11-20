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

package dev.frostlake.executor.operators;

import dev.frostlake.storage.Row;

/**
 * One side's half of a join key that is computed rather than read from a column: an expression over that side's
 * columns alone, as {@code COALESCE(a.k, -1)} or {@code a.k::VARCHAR} in {@code ON COALESCE(a.k, -1) = COALESCE(b.k,
 * -1)}, evaluated once per row so the hash path can bucket it.
 */
public interface JoinSideKey {

    /**
     * The key's value for one row of its side.
     *
     * @param sideRow a row of the side the expression reads
     * @return the value, or null
     * @throws RuntimeException when the expression cannot be computed for the row; the join then judges every pair
     *     by its condition instead
     */
    Object read(final Row sideRow);

    /** The expression as it is shown in a plan. */
    String describe();
}
