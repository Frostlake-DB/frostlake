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

package dev.frostlake.executor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The names a lateral item reads: the row to its left, over the row of the query its own query is written in.
 *
 * <p>{@code SELECT … FROM o WHERE o.n NOT IN (SELECT f.value FROM i, TABLE(FLATTEN(INPUT => o.arr)) f)} calls
 * FLATTEN for every row of {@code i} with a name of {@code o}'s. A name of the row to the left hides the
 * enclosing row's of the same spelling, as a query's own column hides an outer one. A read of an enclosing
 * name makes the query one that is run again for every enclosing row, where a read of the left row leaves it
 * computed once.
 */
public final class LeftRowNames extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    /** The names the enclosing row gave, none of them one of the left row's. */
    private final Set<String> enclosing = new HashSet<>();

    /**
     * Adds the enclosing row's names beneath the ones held.
     *
     * @param enclosingRow the names of the row around the query, or null where it stands in none
     */
    void underlay(final Map<String, Object> enclosingRow) {
        if (enclosingRow == null) {
            return;
        }
        for (final Map.Entry<String, Object> name : enclosingRow.entrySet()) {
            if (!containsKey(name.getKey())) {
                put(name.getKey(), name.getValue());
                enclosing.add(name.getKey());
            }
        }
    }

    /**
     * Whether the enclosing row gave a name.
     *
     * @param name the name as it is held
     * @return whether it reads the row around the query
     */
    boolean fromEnclosing(final String name) {
        return enclosing.contains(name);
    }

    /**
     * Whether a name read from a row's names reads the row around the query: every name of names that are no
     * left row's is.
     *
     * @param names the names an argument is evaluated with
     * @param name  the name read, as the names hold it
     * @return whether the read is one of the enclosing row
     */
    public static boolean readsEnclosing(final Map<String, Object> names, final String name) {
        return !(names instanceof LeftRowNames) || ((LeftRowNames) names).fromEnclosing(name);
    }
}
