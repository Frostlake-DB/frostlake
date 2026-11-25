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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The subqueries standing in an EXISTS body's select items whose value no row of the body reads (see
 * {@link CorrelatedSubqueryRule#unreadItemSubqueries}): while the body runs they are evaluated as written but meet
 * no correlation judgement, since the account never plans them. They are named by their text; the waiver nests, an
 * EXISTS inside such an item adding its own.
 */
public final class UnreadExistsSubqueries {

    private static final ThreadLocal<Set<String>> WAIVED = new ThreadLocal<Set<String>>();

    private UnreadExistsSubqueries() {
    }

    /**
     * Waives the correlation judgement of the given subqueries, beside those already waived.
     *
     * @param texts the subqueries' texts
     * @return the waiver to put back with {@link #end}
     */
    public static Set<String> begin(final Set<String> texts) {
        final Set<String> previous = WAIVED.get();
        final Set<String> waived = new HashSet<>(texts);
        if (previous != null) {
            waived.addAll(previous);
        }
        WAIVED.set(Collections.unmodifiableSet(waived));
        return previous;
    }

    /**
     * Whether a subquery's correlation judgement is waived.
     *
     * @param text the subquery's text
     * @return true where it stands in an unread select item of an EXISTS body being run
     */
    public static boolean waives(final String text) {
        final Set<String> waived = WAIVED.get();
        return waived != null && text != null && waived.contains(text);
    }

    /**
     * Puts back the waiver {@link #begin} found.
     *
     * @param previous what {@link #begin} handed back
     */
    public static void end(final Set<String> previous) {
        if (previous == null) {
            WAIVED.remove();
        } else {
            WAIVED.set(previous);
        }
    }
}
