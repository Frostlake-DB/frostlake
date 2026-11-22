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
 * One window value function's argument over one sorted partition, evaluated once per row, as FIRST_VALUE, LAST_VALUE,
 * NTH_VALUE, LAG and LEAD read it. Every frame and every step is then a read by position: under IGNORE NULLS the
 * non-NULL cells are indexed once (a running count before each position and the positions in order), so the n-th
 * non-NULL value of a frame, or the n-th met walking away from a row, is found without a walk. Only a SQL NULL is
 * skipped; a VARIANT holding a JSON null is a value.
 */
final class WindowValueCells {

    private final Object[] cells;
    /** How many non-NULL cells lie before each position, one entry past the last; built on first use. */
    private int[] nonNullBefore;
    /** The positions of the non-NULL cells, in order; built with {@link #nonNullBefore}. */
    private int[] nonNullPositions;

    /**
     * @param cells the argument's value on each row of the partition, in its sorted order
     */
    WindowValueCells(final Object[] cells) {
        this.cells = cells;
    }

    /**
     * @param position a position in the partition
     * @return the argument's value there
     */
    Object at(final int position) {
        return cells[position];
    }

    /**
     * The first non-NULL value among positions {@code [from, to]}, or null when there is none.
     *
     * @param from the frame's first position
     * @param to the frame's last position
     * @return the value, or null
     */
    Object firstNonNull(final int from, final int to) {
        return nthNonNull(from, to, 1, false);
    }

    /**
     * The last non-NULL value among positions {@code [from, to]}, or null when there is none.
     *
     * @param from the frame's first position
     * @param to the frame's last position
     * @return the value, or null
     */
    Object lastNonNull(final int from, final int to) {
        return nthNonNull(from, to, 1, true);
    }

    /**
     * The {@code n}-th non-NULL value among positions {@code [from, to]}, counted from the frame's start or from its
     * end, or null when the frame holds fewer.
     *
     * @param from the frame's first position
     * @param to the frame's last position
     * @param n which one, from 1
     * @param fromLast whether to count from the frame's end
     * @return the value, or null
     */
    Object nthNonNull(final int from, final int to, final int n, final boolean fromLast) {
        if (n <= 0 || from > to) {
            return null;
        }
        index();
        final int first = nonNullBefore[from];
        final int pastLast = nonNullBefore[to + 1];
        final int k = fromLast ? pastLast - n : first + n - 1;
        return k >= first && k < pastLast ? cells[nonNullPositions[k]] : null;
    }

    /**
     * The {@code count}-th non-NULL value met walking away from a position, the position itself not counted, or null
     * when the partition runs out first.
     *
     * @param from the position walked from
     * @param direction -1 towards the partition's start, 1 towards its end
     * @param count how many non-NULL values to pass, the last one being the answer
     * @return the value, or null
     */
    Object nthNonNullAway(final int from, final int direction, final int count) {
        if (count <= 0) {
            return null;
        }
        index();
        final int k = direction < 0 ? nonNullBefore[from] - count : nonNullBefore[from + 1] + count - 1;
        return k >= 0 && k < nonNullPositions.length ? cells[nonNullPositions[k]] : null;
    }

    private void index() {
        if (nonNullBefore != null) {
            return;
        }
        final int[] before = new int[cells.length + 1];
        int nonNull = 0;
        for (int i = 0; i < cells.length; i++) {
            before[i] = nonNull;
            if (cells[i] != null) {
                nonNull++;
            }
        }
        before[cells.length] = nonNull;
        final int[] positions = new int[nonNull];
        int next = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null) {
                positions[next++] = i;
            }
        }
        nonNullPositions = positions;
        nonNullBefore = before;
    }
}
