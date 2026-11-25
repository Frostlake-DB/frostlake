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

import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.storage.Row;

import java.util.List;

/**
 * The statistics of one column of a table: its least and greatest value and how many NULLs it holds, the whole
 * table's (see InsertSourcePruning).
 */
final class ColumnExtremes {

    private Object least;
    private Object greatest;
    private boolean mixed;
    /** The NULLs the column holds. */
    final long nulls;

    /**
     * @param rows   the table's rows
     * @param column the column's position
     */
    ColumnExtremes(final List<Row> rows, final int column) {
        long held = 0;
        for (final Row row : rows) {
            final Object value = column < row.getValues().size() ? row.getValue(column) : null;
            if (value == null) {
                held++;
                continue;
            }
            if (least == null) {
                least = value;
                greatest = value;
                continue;
            }
            if (!InsertSourcePruning.comparable(least, value)) {
                mixed = true;
                continue;
            }
            if (ValueComparisons.compareValues(value, least) < 0) {
                least = value;
            }
            if (ValueComparisons.compareValues(value, greatest) > 0) {
                greatest = value;
            }
        }
        this.nulls = held;
    }

    /**
     * Whether {@code column LIKE pattern} holds on no row: the texts the pattern's fixed prefix starts lie outside
     * the column's least and greatest text.
     *
     * @param pattern the pattern, with no escape
     * @return true where no row can match it
     */
    boolean rulesOutLike(final String pattern) {
        if (least == null) {
            return true;
        }
        if (mixed || !(least instanceof String) || !(greatest instanceof String)) {
            return false;
        }
        int prefixEnd = -1;
        for (int i = 0; i < pattern.length() && prefixEnd < 0; i++) {
            final char c = pattern.charAt(i);
            if (c == '%' || c == '_') {
                prefixEnd = i;
            } else if (c == '\\') {
                return false;
            }
        }
        final String prefix = prefixEnd < 0 ? pattern : pattern.substring(0, prefixEnd);
        if (prefix.isEmpty()) {
            return false;
        }
        final String low = (String) least;
        final String high = (String) greatest;
        return high.compareTo(prefix) < 0 && !high.startsWith(prefix)
            || low.compareTo(prefix) > 0 && !low.startsWith(prefix);
    }

    /**
     * Whether {@code column operator value} holds on no row by the column's least and greatest value: a NULL value
     * meets no row, and a value of another kind than the column's settles nothing.
     *
     * @param value    the value compared with
     * @param operator =, &lt;, &lt;=, &gt; or &gt;=, the column on its left
     * @return true where no row can hold it
     */
    boolean rulesOut(final Object value, final BinaryOperator operator) {
        if (value == null) {
            return true;
        }
        if (least == null) {
            return true;
        }
        if (mixed || !InsertSourcePruning.comparable(least, value)) {
            return false;
        }
        final int belowLeast = ValueComparisons.compareValues(value, least);
        final int aboveGreatest = ValueComparisons.compareValues(value, greatest);
        switch (operator) {
            case EQUAL:
                return belowLeast < 0 || aboveGreatest > 0;
            case LESS_THAN:
                return belowLeast <= 0;
            case LESS_THAN_OR_EQUAL:
                return belowLeast < 0;
            case GREATER_THAN:
                return aboveGreatest >= 0;
            case GREATER_THAN_OR_EQUAL:
                return aboveGreatest > 0;
            default:
                return false;
        }
    }
}
