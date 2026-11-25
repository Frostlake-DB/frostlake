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

/**
 * A thread-local mode in which a catalog column answers NULL by what its rows hold rather than by its declaration:
 * the account's statistics of the column, which tell whether any row is NULL. A NOT IN standing outside a filter is
 * planned by them (see {@link NullableAntiJoins}): a column declared nullable whose rows hold no NULL answers none,
 * and so does an expression over it that answers NULL only where it does. Every other source — a VALUES clause, a
 * derived relation — keeps the judgement its own rows or its projection make.
 */
final class HeldNullReading {
    private static final ThreadLocal<StoredColumnValues> ACTIVE = new ThreadLocal<StoredColumnValues>();

    private HeldNullReading() {
    }

    /**
     * Enters the mode.
     *
     * @param executor the executor whose storage the rows are read from
     * @return the state to put back with {@link #end}
     */
    static StoredColumnValues begin(final QueryExecutor executor) {
        final StoredColumnValues previous = ACTIVE.get();
        ACTIVE.set(new StoredColumnValues(executor));
        return previous;
    }

    /**
     * Leaves the mode.
     *
     * @param previous the state {@link #begin} handed back
     */
    static void end(final StoredColumnValues previous) {
        if (previous == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(previous);
        }
    }

    /** The reader of the rows' NULLs while the mode is in force, or null outside it. */
    static StoredColumnValues current() {
        return ACTIVE.get();
    }
}
