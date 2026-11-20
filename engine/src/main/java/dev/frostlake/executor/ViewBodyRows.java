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

import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.storage.Row;

import java.util.List;

/**
 * The view whose body's rows are being computed on this thread, if any. A correlated subquery of the body that the
 * account cannot evaluate is judged where the reading statement reaches the row's value, as every other item of a
 * relation's body is (see {@link RelationBody}): a row the statement's own filter drops and a column it does not
 * read raise nothing, and a value it reads is refused in the view's name (live-verified).
 */
public final class ViewBodyRows {
    private static final ThreadLocal<String> VIEW = new ThreadLocal<String>();

    private static final ThreadLocal<Long> HELD = new ThreadLocal<Long>();

    private ViewBodyRows() {
    }

    /**
     * The number of refusals this thread's view bodies have left in their cells so far.
     *
     * @return the count
     */
    public static long held() {
        final Long count = HELD.get();
        return count == null ? 0L : count.longValue();
    }

    /**
     * The refusal a view's cell keeps for a subquery of its body: it names the view, or the function a refusal
     * names already.
     *
     * @param refused the refusal the subquery raised
     * @return the cell
     */
    public static DeferredFault hold(final UnsupportedSubqueryException refused) {
        HELD.set(Long.valueOf(held() + 1));
        return new DeferredFault(refused.namesFunction() ? refused
            : UnsupportedSubqueryException.insideView(current()));
    }

    /**
     * Names a view in the refusals its rows carry from a view its body reads: a statement is told of the view it
     * reads itself.
     *
     * @param rows     the view's rows
     * @param viewName the view's name
     */
    public static void rename(final List<Row> rows, final String viewName) {
        for (final Row row : rows) {
            final List<Object> values = row.getValues();
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) instanceof DeferredFault
                        && ((DeferredFault) values.get(i)).getFault() instanceof UnsupportedSubqueryException) {
                    final UnsupportedSubqueryException held =
                        (UnsupportedSubqueryException) ((DeferredFault) values.get(i)).getFault();
                    if (held.namesView() && !held.getMessage().endsWith(": " + viewName)) {
                        values.set(i, new DeferredFault(UnsupportedSubqueryException.insideView(viewName)));
                    }
                }
            }
        }
    }

    /**
     * The view being expanded.
     *
     * @return its name, or null outside every view's body
     */
    public static String current() {
        return VIEW.get();
    }

    /**
     * Enters a view's body.
     *
     * @param viewName the view's name
     * @return the state to put back with {@link #end}
     */
    public static String begin(final String viewName) {
        final String previous = VIEW.get();
        VIEW.set(viewName);
        return previous;
    }

    /**
     * Leaves a view's body.
     *
     * @param previous what {@link #begin} handed back
     */
    public static void end(final String previous) {
        if (previous == null) {
            VIEW.remove();
        } else {
            VIEW.set(previous);
        }
    }
}
