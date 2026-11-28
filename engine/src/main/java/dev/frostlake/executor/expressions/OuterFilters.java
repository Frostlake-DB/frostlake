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

import java.util.Collections;
import java.util.List;

/**
 * The WHERE condition a filter is evaluating on this thread, for a correlated subquery written among its conjuncts:
 * an outer filter on the key a subquery is correlated by bounds the inner rows the account reads for it (see
 * {@link SubqueryEvaluator}). Each filter names its condition for the rows it reads and gives the one before it back
 * after them, so a nested query's filter holds its own condition only while it runs.
 *
 * <p>A select list being computed names its query's WHERE the same way, for a correlated subquery among its items:
 * the query's filters on the key bound the inner rows read for it as well.
 */
public final class OuterFilters {

    private static final ThreadLocal<Expression> CURRENT = new ThreadLocal<Expression>();
    /** The WHERE of the query whose select list is computing, as a one-element list holding null for none. */
    private static final ThreadLocal<List<Expression>> SELECTED = new ThreadLocal<List<Expression>>();

    private OuterFilters() {
    }

    /**
     * Names the condition a filter is evaluating.
     *
     * @param condition the condition
     * @return the condition named before it, to give back to {@link #end}
     */
    public static Expression begin(final Expression condition) {
        final Expression displaced = CURRENT.get();
        CURRENT.set(condition);
        return displaced;
    }

    /**
     * Gives back the condition named before a filter's.
     *
     * @param displaced what {@link #begin} answered
     */
    public static void end(final Expression displaced) {
        if (displaced == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(displaced);
        }
    }

    /** @return the condition the innermost filter on this thread is evaluating, or null outside any */
    static Expression current() {
        return CURRENT.get();
    }

    /**
     * Names the WHERE of a query whose select list is computing, no filter being evaluated meanwhile.
     *
     * @param where the query's WHERE condition, or null where it has none
     * @return what was named before, to give back to {@link #endSelectList}
     */
    public static Object[] beginSelectList(final Expression where) {
        final Object[] displaced = {CURRENT.get(), SELECTED.get()};
        CURRENT.remove();
        SELECTED.set(Collections.singletonList(where));
        return displaced;
    }

    /**
     * Names nothing while a subquery runs: its own filters and select list name their conditions.
     *
     * @return what was named before, to give back to {@link #endSelectList}
     */
    public static Object[] beginSubquery() {
        final Object[] displaced = {CURRENT.get(), SELECTED.get()};
        CURRENT.remove();
        SELECTED.remove();
        return displaced;
    }

    /**
     * Gives back what was named before a select list's or a subquery's.
     *
     * @param displaced what {@link #beginSelectList} or {@link #beginSubquery} answered
     */
    @SuppressWarnings("unchecked")
    public static void endSelectList(final Object[] displaced) {
        end((Expression) displaced[0]);
        if (displaced[1] == null) {
            SELECTED.remove();
        } else {
            SELECTED.set((List<Expression>) displaced[1]);
        }
    }

    /**
     * @return the WHERE of the query whose select list is computing on this thread, as a one-element list holding
     *         null where the query has none; null outside a select list
     */
    static List<Expression> selectList() {
        return SELECTED.get();
    }
}
