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
 * The rows the CURRENT statement wrote or removed where it is no DML statement: a CREATE TABLE … AS SELECT the rows it
 * filled its table with, a TRUNCATE the rows it emptied its table of. The account hands both to its drivers beside the
 * statement's status, as the stats a DML statement's counts make (live-verified). Held in a ThreadLocal and consumed
 * per statement, as {@link DdlStatusNote} is, and carried on the status answer (see
 * {@link dev.frostlake.storage.ResultSet#markRowsChanged}).
 */
public final class DdlRowCounts {

    private static final ThreadLocal<long[]> COUNTS = new ThreadLocal<long[]>();

    private DdlRowCounts() {
    }

    /**
     * Record the rows the statement wrote and removed.
     *
     * @param inserted the rows it wrote
     * @param deleted  the rows it removed
     */
    public static void record(final long inserted, final long deleted) {
        COUNTS.set(new long[] {inserted, deleted});
    }

    /** Clear whatever an earlier statement may have left, before a statement runs. */
    public static void clear() {
        COUNTS.remove();
    }

    /**
     * The rows the statement just executed wrote and removed, consumed.
     *
     * @return the rows written and the rows removed, in that order, or null when the statement recorded none
     */
    public static long[] take() {
        final long[] counts = COUNTS.get();
        COUNTS.remove();
        return counts;
    }
}
