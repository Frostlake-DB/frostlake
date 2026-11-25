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

package dev.frostlake.metastore;

/**
 * The current database of the session whose statement runs on this thread when that statement's roles do not see
 * it and hold nothing in it: to the statement the session has no current database and no current schema, so CURRENT_DATABASE() and
 * CURRENT_SCHEMA() answer NULL and an unqualified name finds nothing, while the session keeps the database for a
 * later statement whose roles see it again (live-verified). Each statement names its own; a statement it runs in
 * turn names the one current there.
 */
public final class UnseenCurrentDatabase {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<String>();

    private UnseenCurrentDatabase() {
    }

    /**
     * Names the current database the running statement does not see.
     *
     * @param name the database's stored name, or null when the statement sees its current database
     * @return the name named before, to give back to {@link #end}
     */
    public static String begin(final String name) {
        final String displaced = CURRENT.get();
        if (name == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(name);
        }
        return displaced;
    }

    /**
     * Gives back the name named before a statement's.
     *
     * @param displaced what {@link #begin} answered
     */
    public static void end(final String displaced) {
        if (displaced == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(displaced);
        }
    }

    /** Whether the running statement does not see the current database of this stored name. */
    static boolean hides(final String name) {
        return name != null && name.equals(CURRENT.get());
    }
}
