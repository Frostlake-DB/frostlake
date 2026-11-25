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

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The answers of the procedures CALLed as values in the running statement (see
 * {@link dev.frostlake.executor.expressions.AnonymousCallExpression}). Such a value is the wrapper the answer travels
 * in; a text that begins with a wrapper is that answer again where the statement hands it out - a cell the outermost
 * statement answers, a value a block's variable takes - so {@code SELECT (WITH p … CALL p()) || 'x'} answers the
 * procedure's 7, and so does a query reading it from a derived table (live-verified). The wrappers are remembered for
 * the outermost statement of the thread, which forgets them as the next one begins.
 */
public final class AnonymousCallAnswers {

    private static final ThreadLocal<Map<String, String>> ANSWERS = new ThreadLocal<Map<String, String>>() {
        @Override
        protected Map<String, String> initialValue() {
            return new HashMap<>();
        }
    };

    private AnonymousCallAnswers() {
    }

    /** Forgets the answers of an earlier statement. */
    public static void forget() {
        final Map<String, String> answers = ANSWERS.get();
        if (!answers.isEmpty()) {
            answers.clear();
        }
    }

    /**
     * The value a CALL of a procedure the CALL defines for itself stands for: the wrapper of what the procedure
     * answered, remembered with the answer's text.
     *
     * @param results what the CALL answered
     * @return the wrapper's text
     */
    public static String wrapped(final List<ResultSet> results) {
        final Object answer = ProceduralExecutor.procedureReturnValue(results);
        final DataType type = ProceduralExecutor.procedureAnswerType(results);
        final String wrapper = AnonymousCallValues.wrapper(answer, type);
        ANSWERS.get().put(wrapper, AnonymousCallValues.answerText(answer, type));
        return wrapper;
    }

    /**
     * A value as it is handed out: the answer of a text that begins with a remembered wrapper, else the value.
     *
     * @param value the value
     * @return the value handed out
     */
    public static Object unwrapped(final Object value) {
        final Map<String, String> answers = ANSWERS.get();
        if (answers.isEmpty() || !(value instanceof String)) {
            return value;
        }
        final String text = (String) value;
        for (final Map.Entry<String, String> answer : answers.entrySet()) {
            if (text.startsWith(answer.getKey())) {
                return answer.getValue();
            }
        }
        return value;
    }

    /**
     * The outermost statement's results handed out: one value, its first row's first, handed out (see
     * {@link #unwrapped}).
     *
     * @param results the results
     * @return the same results
     */
    public static List<ResultSet> handedOut(final List<ResultSet> results) {
        if (ANSWERS.get().isEmpty()) {
            return results;
        }
        // The statement answers as the CALL it is turned into does: one value, its first row's first.
        final List<ResultSet> answered = new ArrayList<>(results.size());
        for (final ResultSet rs : results) {
            if (rs != null && (rs.getColumns().size() > 1 || rs.getRowCount() > 1)) {
                final List<Row> first = new ArrayList<>();
                if (rs.getRowCount() > 0) {
                    final List<Object> value = new ArrayList<>();
                    value.add(rs.getRows().get(0).getValue(0));
                    first.add(new Row(value));
                }
                answered.add(new ResultSet(rs.getColumns().subList(0, 1), first));
            } else {
                answered.add(rs);
            }
        }
        for (final ResultSet rs : answered) {
            if (rs == null) {
                continue;
            }
            final List<Row> rows = rs.getRows();
            for (int r = 0; r < rows.size(); r++) {
                final Row row = rows.get(r);
                Row given = row;
                for (int c = 0; c < row.size(); c++) {
                    final Object value = row.getValue(c);
                    final Object answer = unwrapped(value);
                    if (answer != value) {
                        if (given == row) {
                            given = row.copy();
                        }
                        given.setValue(c, answer);
                    }
                }
                if (given != row) {
                    rows.set(r, given);
                }
            }
        }
        return answered;
    }
}
