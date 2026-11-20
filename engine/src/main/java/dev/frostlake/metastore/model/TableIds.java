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

package dev.frostlake.metastore.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The numbers tables are told apart by, as INFORMATION_SCHEMA.TABLE_STORAGE_METRICS lists them in its ID column:
 * every table drawn a number no other table of the process holds.
 */
public final class TableIds {

    private static final AtomicLong LAST = new AtomicLong();

    private TableIds() {
    }

    /**
     * The next number, for a table being made.
     *
     * @return the number
     */
    public static long next() {
        return LAST.incrementAndGet();
    }

    /**
     * Makes every number drawn from now on follow {@code id}: a table a snapshot of an engine's state restores keeps
     * its number, and no table made after it draws the same one.
     *
     * @param id a number a restored table holds
     */
    public static void advancePast(final long id) {
        while (true) {
            final long last = LAST.get();
            if (last >= id || LAST.compareAndSet(last, id)) {
                return;
            }
        }
    }
}
