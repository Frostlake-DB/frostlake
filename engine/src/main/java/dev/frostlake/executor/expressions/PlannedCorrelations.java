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
package dev.frostlake.executor.expressions;

import java.util.Set;

/**
 * A thread-local record of the correlated subqueries a statement's plan has already refused (see
 * {@code CorrelationPlanJudgement}). The refusal waits until the rest of the statement has been judged, as
 * live reports it after a predicate's type or a grouped select list; meanwhile a row that reaches such a
 * subquery reads it as empty rather than running a shape the statement will not keep.
 */
public final class PlannedCorrelations {
    private static final ThreadLocal<Set<String>> REFUSED = new ThreadLocal<Set<String>>();

    private PlannedCorrelations() {
    }

    /**
     * Record the subqueries refused for the statement about to run.
     *
     * @param refused the refused subqueries, each keyed by {@link #valueKey} or {@link #membershipKey}; null
     *                clears the record
     * @return the record displaced, to put back with {@link #end}
     */
    public static Set<String> begin(final Set<String> refused) {
        final Set<String> previous = REFUSED.get();
        if (refused == null) {
            REFUSED.remove();
        } else {
            REFUSED.set(refused);
        }
        return previous;
    }

    public static void end(final Set<String> previous) {
        begin(previous);
    }

    /** The key of a subquery read as a value. */
    public static String valueKey(final String subquery) {
        return "value " + subquery;
    }

    /** The key of a subquery under EXISTS or IN. */
    public static String membershipKey(final String subquery) {
        return "membership " + subquery;
    }

    /**
     * The key of a subquery read as a value that the plan judged answerable, which a row reaching it then runs
     * without judging its shape again; it stands in the same record as the refused keys.
     */
    public static String answeredKey(final String subquery) {
        return "answered " + valueKey(subquery);
    }

    /** Whether the running statement's plan judged the subquery under {@code key} answerable (see answeredKey). */
    static boolean answered(final String key) {
        final Set<String> refused = REFUSED.get();
        return refused != null && refused.contains("answered " + key);
    }

    /**
     * The key of a NOT IN's or an ALL's subquery that a table function feeds the outer row, which the plan
     * computes for every outer row; it stands in the same record as the refused keys.
     */
    public static String fedKey(final String subquery) {
        return "fed " + membershipKey(subquery);
    }

    /** Whether the running statement's plan computes the subquery for every outer row (see fedKey). */
    static boolean fed(final String subquery) {
        final Set<String> refused = REFUSED.get();
        return refused != null && refused.contains(fedKey(subquery));
    }

    /** Whether the running statement's plan refused the subquery under {@code key}. */
    static boolean refused(final String key) {
        final Set<String> refused = REFUSED.get();
        return refused != null && refused.contains(key);
    }
}
