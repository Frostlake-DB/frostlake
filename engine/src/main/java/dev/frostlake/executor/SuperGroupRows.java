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

import dev.frostlake.executor.expressions.GroupingKeys;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The source rows of one group of a ROLLUP, CUBE or GROUPING SETS query, as the clauses after the grouping read
 * them: HAVING, ORDER BY, QUALIFY and window calls. The list holds the rows with the dimensions the group's set
 * aggregates over blanked, so whatever reads a rolled-up key outside an aggregate reads NULL; an aggregate reads
 * {@link #sourceRows() the rows as stored} instead, and GROUPING and GROUPING_ID read the set's
 * {@link #groupingKeys() keys}, never whether a value is NULL. A dimension written as an expression has no column
 * to blank; the ones the set rolls up are {@link #rolledUp() read as NULL by their form}.
 */
final class SuperGroupRows extends ArrayList<Row> {

    private static final long serialVersionUID = 1L;

    private final transient List<Row> sourceRows;

    private final transient GroupingKeys groupingKeys;

    private final transient Map<String, Object> rolledUp;

    /**
     * @param readRows     the rows with the rolled-up dimensions blanked
     * @param sourceRows   the same rows as stored
     * @param groupingKeys the keys of the group's grouping set
     * @param rolledUp     the rolled-up dimensions written as expressions, by their canonical form, each bound to
     *                     NULL; null for none
     */
    SuperGroupRows(final List<Row> readRows, final List<Row> sourceRows, final GroupingKeys groupingKeys,
                   final Map<String, Object> rolledUp) {
        super(readRows);
        this.sourceRows = sourceRows;
        this.groupingKeys = groupingKeys;
        this.rolledUp = rolledUp;
    }

    /** The group's rows as stored, which its aggregates read. */
    List<Row> sourceRows() {
        return sourceRows;
    }

    /** The keys of the group's grouping set, which GROUPING reads. */
    GroupingKeys groupingKeys() {
        return groupingKeys;
    }

    /** The rolled-up dimensions written as expressions, by their canonical form, each bound to NULL; null for none. */
    Map<String, Object> rolledUp() {
        return rolledUp;
    }

    /**
     * The rows an aggregate over a group reads: a super-group's as stored, any other group's as they are.
     *
     * @param groupRows a group's rows
     * @return the rows its aggregates read
     */
    static List<Row> aggregated(final List<Row> groupRows) {
        return groupRows instanceof SuperGroupRows ? ((SuperGroupRows) groupRows).sourceRows() : groupRows;
    }

    /**
     * The grouping set a group was computed for, or null for a group of a plain GROUP BY.
     *
     * @param groupRows a group's rows
     * @return its grouping set's keys, or null
     */
    static GroupingKeys keysOf(final List<Row> groupRows) {
        return groupRows instanceof SuperGroupRows ? ((SuperGroupRows) groupRows).groupingKeys() : null;
    }

    /**
     * The dimensions written as expressions a group rolled up, by their canonical form, each bound to NULL.
     *
     * @param groupRows a group's rows
     * @return those dimensions, or null for none
     */
    static Map<String, Object> rolledUpOf(final List<Row> groupRows) {
        return groupRows instanceof SuperGroupRows ? ((SuperGroupRows) groupRows).rolledUp() : null;
    }
}
