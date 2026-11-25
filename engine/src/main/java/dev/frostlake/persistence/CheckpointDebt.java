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

package dev.frostlake.persistence;

/**
 * What a statement read that its write-ahead log record cannot read again. The log replays a statement from its text,
 * so a restart would read the stage's files as they are then, which a purge or an overwrite has changed, and call a
 * function written in a handler language again, which may answer otherwise. A statement that read either owes a
 * checkpoint once it is logged, which keeps what it wrote as it wrote it (see DatabaseEngine). The debt is noted on
 * the calling thread, where the statement runs.
 */
public final class CheckpointDebt {

    private static final ThreadLocal<Boolean> OWED = new ThreadLocal<Boolean>();

    private static final ThreadLocal<Boolean> OWED_UNLOGGED = new ThreadLocal<Boolean>();

    private CheckpointDebt() {
    }

    /** Notes that the statement running on this thread read what a replay cannot read again. */
    public static void owe() {
        OWED.set(Boolean.TRUE);
    }

    /**
     * Whether the statement running on this thread owes a checkpoint, the debt cleared.
     *
     * @return whether it read what a replay cannot read again
     */
    public static boolean settle() {
        final boolean owed = Boolean.TRUE.equals(OWED.get());
        OWED.remove();
        return owed;
    }

    /**
     * Notes that the statement running on this thread changed what no log record carries, whether the log keeps the
     * statement or not: a read of a view stream, or SYSTEM$STREAM_HAS_DATA, that re-bound it to the tables its view's
     * names read now. A replay would leave the stream bound as it was, so the statement owes a checkpoint, which keeps
     * the stream as the re-bind left it.
     */
    public static void oweUnlogged() {
        OWED_UNLOGGED.set(Boolean.TRUE);
    }

    /**
     * Whether the statement running on this thread owes a checkpoint for what it changed that no log record carries,
     * the debt cleared (see {@link #oweUnlogged}).
     *
     * @return whether it changed such state
     */
    public static boolean settleUnlogged() {
        final boolean owed = Boolean.TRUE.equals(OWED_UNLOGGED.get());
        OWED_UNLOGGED.remove();
        return owed;
    }
}
