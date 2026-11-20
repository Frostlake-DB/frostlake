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

package dev.frostlake.executor.operators;

import dev.frostlake.executor.DeferredFault;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.StatementCancellation;
import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;
import dev.frostlake.values.VariantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JOIN operator - joins two sets of rows based on a condition.
 *
 * This operator supports multiple join types:
 * - INNER JOIN: Returns rows when there's a match in both tables
 * - LEFT JOIN: Returns all rows from left table, matched rows from right
 * - RIGHT JOIN: Returns all rows from right table, matched rows from left
 * - FULL JOIN: Returns all rows when there's a match in either table
 * - CROSS JOIN: Returns Cartesian product of both tables
 */
public class JoinOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(JoinOperator.class);

    private final Table leftTable;
    private final Table rightTable;
    private final RowsProvider rightSide;
    private List<Row> rightRows;
    private final JoinType joinType;
    private final String joinCondition;
    private final JoinConditionEvaluator conditionEvaluator;
    // Equi-join key column indices (left-table-relative, right-table-relative). When set, the join
    // hash-partitions the right side by these keys and probes per left row, re-confirming each
    // candidate pair with conditionEvaluator. Null => nested-loop.
    private int[] leftKeyIdx;
    private int[] rightKeyIdx;
    // Per key position, the expression that computes it for each side, or null where the key is a column.
    private JoinSideKey[] leftKeyReaders;
    private JoinSideKey[] rightKeyReaders;
    private static final Object NULL_KEY = new Object();
    // The kinds of value a computed key position may hold (see computedKeysCompareAlike).
    private static final int KIND_NONE = 0;
    private static final int KIND_MIXED = -1;
    private static final int KIND_NUMBER = 1;
    private static final int KIND_TEXT = 2;
    private static final int KIND_BOOLEAN = 3;
    private static final int KIND_MOMENT = 4;
    private static final int KIND_TIME = 5;
    private static final int KIND_VARIANT = 6;

    /**
     * Create a JOIN operator with a condition evaluator.
     *
     * @param leftTable Left table metadata
     * @param rightTable Right table metadata
     * @param rightRows Rows from the right table
     * @param joinType Type of join to perform
     * @param joinCondition Join condition expression (null for CROSS JOIN)
     * @param conditionEvaluator Evaluator to evaluate join condition on pair of rows (null for CROSS JOIN)
     */
    public JoinOperator(final Table leftTable, final Table rightTable, final List<Row> rightRows,
                       final JoinType joinType, final String joinCondition,
                       final JoinConditionEvaluator conditionEvaluator) {
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.rightSide = RowsProvider.of(rightRows);
        this.joinType = joinType;
        this.joinCondition = joinCondition;
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * Create a CROSS JOIN operator (no condition).
     */
    /**
     * A join whose right side is read when the join runs — a relation planned as a pipeline of its own.
     *
     * @param leftTable          the left relation
     * @param rightTable         the right relation's shape
     * @param rightSide          the right relation's rows, read when the join runs
     * @param joinType           the join type
     * @param joinCondition      the condition's text, or null
     * @param conditionEvaluator the condition, or null for a cross join
     */
    public JoinOperator(final Table leftTable, final Table rightTable, final RowsProvider rightSide,
                        final JoinType joinType, final String joinCondition,
                        final JoinConditionEvaluator conditionEvaluator) {
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.rightSide = rightSide;
        this.joinType = joinType;
        this.joinCondition = joinCondition;
        this.conditionEvaluator = conditionEvaluator;
    }

    public static JoinOperator cross(final Table leftTable, final Table rightTable, final List<Row> rightRows) {
        return new JoinOperator(leftTable, rightTable, rightRows, JoinType.CROSS, null, null);
    }

    /** A cross join whose right side is read when the join runs. */
    public static JoinOperator cross(final Table leftTable, final Table rightTable, final RowsProvider rightSide) {
        return new JoinOperator(leftTable, rightTable, rightSide, JoinType.CROSS, null, null);
    }

    /** Enable hash-join candidate filtering using the given equi-join key column indices. */
    public JoinOperator withEquiKeys(final int[] leftKeyIdx, final int[] rightKeyIdx) {
        this.leftKeyIdx = leftKeyIdx;
        this.rightKeyIdx = rightKeyIdx;
        return this;
    }

    /**
     * Compute some of the keys {@link #withEquiKeys} sets up from an expression of each side rather than from a
     * column: the positions whose reader is not null ignore their column index.
     *
     * @param leftReaders  per key position, the left side's expression or null
     * @param rightReaders per key position, the right side's expression or null
     * @return this operator
     */
    public JoinOperator withKeyExpressions(final JoinSideKey[] leftReaders, final JoinSideKey[] rightReaders) {
        this.leftKeyReaders = leftReaders;
        this.rightKeyReaders = rightReaders;
        return this;
    }

    @Override
    public List<Row> execute(final List<Row> leftRows, final OperatorContext context) {
        rightRows = rightSide.rows();
        logger.debug("Executing {} JOIN: {} rows (left) x {} rows (right)",
            joinType, leftRows.size(), rightRows.size());

        if (joinType == JoinType.CROSS || (joinCondition == null && conditionEvaluator == null)) {
            // A comma join whose equality was pushed down from WHERE arrives here WITH keys and
            // WITHOUT a condition: the keys ARE the condition, so the hash path applies and the
            // cartesian product is never built.
            if (leftKeyIdx != null && rightKeyIdx != null) {
                return executeHashJoin(leftRows);
            }
            return executeCrossJoin(leftRows);
        }

        if (leftKeyIdx != null && rightKeyIdx != null) {
            return executeHashJoin(leftRows);
        }

        return executeConditionalJoin(leftRows);
    }

    @Override
    public String getDescription() {
        // A USING or NATURAL join matches by key through its evaluator and carries no condition text.
        final String rightSideRead = rightSide.describe() == null ? "" : "{" + rightSide.describe() + "}";
        if (joinType == JoinType.CROSS || joinCondition == null) {
            return String.format(Locale.ROOT, "%s JOIN[%s x %s%s]%s",
                joinType, leftTable.getName(), rightTable.getName(), keysRead(), rightSideRead);
        }

        final String conditionPreview = joinCondition.length() > 30
            ? joinCondition.substring(0, 27) + "..."
            : joinCondition;

        return String.format(Locale.ROOT, "%s JOIN[%s x %s ON %s%s]%s",
            joinType, leftTable.getName(), rightTable.getName(), conditionPreview, keysRead(), rightSideRead);
    }

    /** The columns the hash path buckets the two sides by, as {@code " | HASH L=R, …"}; nothing without keys. */
    private String keysRead() {
        if (leftKeyIdx == null || rightKeyIdx == null) {
            return "";
        }
        final StringBuilder keys = new StringBuilder(" | HASH ");
        for (int i = 0; i < leftKeyIdx.length; i++) {
            keys.append(i > 0 ? ", " : "").append(keyName(leftTable, leftKeyIdx, leftKeyReaders, i)).append('=')
                .append(keyName(rightTable, rightKeyIdx, rightKeyReaders, i));
        }
        return keys.toString();
    }

    private static String keyName(final Table table, final int[] keyIdx, final JoinSideKey[] readers, final int i) {
        if (readers != null && readers[i] != null) {
            return readers[i].describe();
        }
        return columnName(table, keyIdx[i]);
    }

    private static String columnName(final Table table, final int index) {
        return index >= 0 && index < table.columnCount() ? table.columnAt(index).getName() : "$" + (index + 1);
    }

    /**
     * Execute CROSS JOIN - Cartesian product of left and right rows.
     */
    private List<Row> executeCrossJoin(final List<Row> leftRows) {
        final List<Row> resultRows = new ArrayList<>();

        for (final Row leftRow : leftRows) {
            // Each outer row is where the statement's timeout or cancel ends a join.
            StatementCancellation.check();
            for (final Row rightRow : rightRows) {
                final Row combinedRow = combineRows(leftRow, rightRow);
                resultRows.add(combinedRow);
            }
        }

        logger.debug("CROSS JOIN: {} x {} -> {} rows",
            leftRows.size(), rightRows.size(), resultRows.size());

        return resultRows;
    }

    /**
     * Execute conditional JOIN (INNER, LEFT, RIGHT, FULL).
     */
    private List<Row> executeConditionalJoin(final List<Row> leftRows) {
        final List<Row> resultRows = new ArrayList<>();
        final Set<Integer> matchedRightRows = new HashSet<>();

        // Process each left row
        for (final Row leftRow : leftRows) {
            // Each outer row is where the statement's timeout or cancel ends a join.
            StatementCancellation.check();
            boolean leftMatchFound = false;

            // Try to match with each right row
            for (int rightIdx = 0; rightIdx < rightRows.size(); rightIdx++) {
                final Row rightRow = rightRows.get(rightIdx);

                // Evaluate join condition
                final boolean matches = evaluateCondition(leftRow, rightRow);

                if (matches) {
                    resultRows.add(combineRows(leftRow, rightRow));
                    leftMatchFound = true;
                    matchedRightRows.add(rightIdx);
                }
            }

            // Handle LEFT JOIN or FULL JOIN - include left row even if no match
            if (!leftMatchFound && (joinType == JoinType.LEFT || joinType == JoinType.FULL)) {
                resultRows.add(combineRowsWithNullRight(leftRow));
            }
        }

        // Handle RIGHT JOIN or FULL JOIN - include unmatched right rows
        if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
            for (int rightIdx = 0; rightIdx < rightRows.size(); rightIdx++) {
                if (!matchedRightRows.contains(rightIdx)) {
                    resultRows.add(combineRowsWithNullLeft(rightRows.get(rightIdx)));
                }
            }
        }

        logger.debug("{} JOIN: {} rows (left) x {} rows (right) -> {} rows",
            joinType, leftRows.size(), rightRows.size(), resultRows.size());

        return resultRows;
    }

    /**
     * Hash join for equi-join conditions: partition the right rows by their join-key value(s), then
     * probe per left row. The extracted keys are a CANDIDATE FILTER only — every candidate pair is
     * still re-confirmed with the full condition evaluator, so results match the nested-loop path
     * regardless of key normalization. Handles all join types like {@link #executeConditionalJoin}.
     */
    private List<Row> executeHashJoin(final List<Row> leftRows) {
        final List<List<Object>> rightKeys = computedKeys(rightRows, rightKeyIdx, rightKeyReaders);
        final List<List<Object>> leftKeys = rightKeys == null ? null
            : computedKeys(leftRows, leftKeyIdx, leftKeyReaders);
        if (rightKeys == null || leftKeys == null || !computedKeysCompareAlike(leftKeys, rightKeys)) {
            // A computed key that cannot be read for every row, or whose two sides do not bucket alike, leaves
            // every pair to the condition, as though no key had been found.
            if (joinCondition == null && conditionEvaluator == null) {
                return executeCrossJoin(leftRows);
            }
            return executeConditionalJoin(leftRows);
        }
        normalizeKeys(rightKeys);
        normalizeKeys(leftKeys);
        final Map<List<Object>, List<Integer>> rightIndex = new HashMap<>();
        for (int r = 0; r < rightRows.size(); r++) {
            final List<Object> key = rightKeys.get(r);
            List<Integer> bucket = rightIndex.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                rightIndex.put(key, bucket);
            }
            bucket.add(r);
        }

        final List<Row> resultRows = new ArrayList<>();
        final boolean[] matchedRight = new boolean[rightRows.size()];

        for (int l = 0; l < leftRows.size(); l++) {
            final Row leftRow = leftRows.get(l);
            // Each outer row is where the statement's timeout or cancel ends a join.
            StatementCancellation.check();
            boolean leftMatchFound = false;
            final List<Integer> candidates = rightIndex.get(leftKeys.get(l));
            if (candidates != null) {
                for (final int idx : candidates) {
                    final Row rightRow = rightRows.get(idx);
                    if (matchesBeyondKeys(leftRow, rightRow)) {
                        resultRows.add(combineRows(leftRow, rightRow));
                        leftMatchFound = true;
                        matchedRight[idx] = true;
                    }
                }
            }
            if (!leftMatchFound && (joinType == JoinType.LEFT || joinType == JoinType.FULL)) {
                resultRows.add(combineRowsWithNullRight(leftRow));
            }
        }

        if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
            for (int rightIdx = 0; rightIdx < rightRows.size(); rightIdx++) {
                if (!matchedRight[rightIdx]) {
                    resultRows.add(combineRowsWithNullLeft(rightRows.get(rightIdx)));
                }
            }
        }

        logger.debug("{} HASH JOIN: {} (left) x {} (right) -> {} rows",
            joinType, leftRows.size(), rightRows.size(), resultRows.size());
        return resultRows;
    }

    /**
     * Every row's key, or null when a computed key cannot be read for some row: its refusal, if the pair it belongs
     * to is ever judged, is then the condition's to raise.
     */
    private static List<List<Object>> computedKeys(final List<Row> rows, final int[] keyIdx,
                                                   final JoinSideKey[] readers) {
        final List<List<Object>> keys = new ArrayList<>(rows.size());
        if (readers == null) {
            for (final Row row : rows) {
                keys.add(buildKey(row, keyIdx, null));
            }
            return keys;
        }
        try {
            for (final Row row : rows) {
                keys.add(buildKey(row, keyIdx, readers));
            }
        } catch (final RuntimeException notComputable) {
            return null;
        }
        return keys;
    }

    /**
     * Whether every computed key position holds values of one kind on both sides, so the buckets part no pair the
     * condition would keep: numbers beside numbers, texts beside texts, dates and timestamps beside each other, and
     * so on. A text beside a number, or a VARIANT beside anything else, is converted by the comparison and is left
     * to it, as is a value of a kind the buckets do not know.
     */
    private boolean computedKeysCompareAlike(final List<List<Object>> leftKeys, final List<List<Object>> rightKeys) {
        if (leftKeyReaders == null && rightKeyReaders == null) {
            return true;
        }
        for (int i = 0; i < leftKeyIdx.length; i++) {
            final boolean computed = leftKeyReaders != null && leftKeyReaders[i] != null
                || rightKeyReaders != null && rightKeyReaders[i] != null;
            if (!computed) {
                continue;
            }
            int kind = KIND_NONE;
            for (final List<Object> key : leftKeys) {
                kind = joinKind(kind, key.get(i));
            }
            for (final List<Object> key : rightKeys) {
                kind = joinKind(kind, key.get(i));
            }
            if (kind == KIND_MIXED) {
                return false;
            }
        }
        return true;
    }

    /** Replaces every key value by the bucket it falls in. */
    private static void normalizeKeys(final List<List<Object>> keys) {
        for (final List<Object> key : keys) {
            for (int i = 0; i < key.size(); i++) {
                key.set(i, normalizeKey(key.get(i)));
            }
        }
    }

    /** The kind a key position holds so far, given one more of its raw values. */
    private static int joinKind(final int kind, final Object raw) {
        if (raw == null || kind == KIND_MIXED) {
            return kind;
        }
        final int next = kindOf(raw);
        if (next == KIND_MIXED || kind != KIND_NONE && kind != next) {
            return KIND_MIXED;
        }
        return next;
    }

    private static int kindOf(final Object raw) {
        if (raw instanceof VariantValue) {
            return KIND_VARIANT;
        }
        if (raw instanceof Number) {
            return KIND_NUMBER;
        }
        if (raw instanceof String) {
            return KIND_TEXT;
        }
        if (raw instanceof Boolean) {
            return KIND_BOOLEAN;
        }
        if (raw instanceof LocalDate || raw instanceof LocalDateTime || raw instanceof OffsetDateTime
                || raw instanceof ZonedDateTime) {
            return KIND_MOMENT;
        }
        if (raw instanceof LocalTime) {
            return KIND_TIME;
        }
        return KIND_MIXED;
    }

    /** One row's key, its values as read: {@link #normalizeKeys} buckets them once their kinds are known. */
    private static List<Object> buildKey(final Row row, final int[] keyIdx, final JoinSideKey[] readers) {
        final List<Object> key = new ArrayList<>(keyIdx.length);
        for (int i = 0; i < keyIdx.length; i++) {
            if (readers != null && readers[i] != null) {
                key.add(readers[i].read(row));
            } else {
                // Joining reads each key, so a cell a relation deferred raises its fault here.
                key.add(DeferredFault.read(row.getValue(keyIdx[i])));
            }
        }
        return key;
    }

    /**
     * Normalize a key value so values the condition treats as equal share a bucket (case-insensitive
     * strings, cross-type numerics). False collisions are harmless — the full condition is re-checked.
     */
    private static Object normalizeKey(final Object value) {
        if (value == null) {
            return NULL_KEY;
        }
        if (value instanceof VariantValue) {
            // A semi-structured value buckets as = compares it: a number by its value whatever its notation
            // or carrier, so PARSE_JSON('1') meets TO_VARIANT(1::FLOAT) and a number a VARIANT column carries
            // unwrapped; a string as a string; an array or an object by the VARIANT order's equality, whatever
            // its key order and its numbers' notation (live-verified).
            final VariantValue variant = (VariantValue) value;
            final Number number = ValueComparisons.variantScalarNumber(variant);
            if (number != null) {
                return normalizeKey(number);
            }
            if (!variant.isContainerValue() && variant.node().isTextual()) {
                return normalizeKey(variant.node().asText());
            }
            return variant;
        }
        if (value instanceof String) {
            // A numeric-looking string buckets like the number it denotes: Snowflake implicitly
            // coerces VARCHAR vs NUMBER equality, so 999001 and '999001' must meet in one bucket.
            // Over-grouping is safe — every candidate pair is re-confirmed by the real condition
            // evaluator — while under-grouping silently loses matches.
            final Double numeric = parseAsDouble((String) value);
            if (numeric != null) {
                return numeric;
            }
            return ((String) value).toUpperCase(Locale.ROOT);
        }
        if (value instanceof Number) {
            // -0.0 and 0 are one value to =, so they share a bucket (live-verified).
            final double numeric = ((Number) value).doubleValue();
            return numeric == 0.0 ? 0.0 : numeric;
        }
        // Every date and timestamp buckets by the moment it names: a zoned one whatever its offset, a naive one
        // and a date's midnight read in the session's zone, as = compares a TIMESTAMP_NTZ with a TIMESTAMP_TZ
        // (live-verified). Two naive values a zone change folds together share a bucket, which is safe.
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toInstant();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(SessionZone.current()).toInstant();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay(SessionZone.current()).toInstant();
        }
        return value;
    }

    /** The string's numeric value, or null when it does not parse as a number. */
    private static Double parseAsDouble(final String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (final NumberFormatException notNumeric) {
            return null;
        }
    }

    /**
     * Evaluate join condition on a pair of rows.
     */
    /**
     * Whether a candidate pair that already AGREES ON THE KEYS also satisfies the join condition. With
     * no condition at all the keys are the whole of it — the shape a comma join takes once its WHERE
     * equality has been pushed down — and the pair matches. Without this, evaluateCondition's
     * no-evaluator answer of FALSE would make such a join return nothing.
     */
    private boolean matchesBeyondKeys(final Row leftRow, final Row rightRow) {
        return joinCondition == null && conditionEvaluator == null
            || evaluateCondition(leftRow, rightRow);
    }

    private boolean evaluateCondition(final Row leftRow, final Row rightRow) {
        if (conditionEvaluator == null) {
            logger.warn("No condition evaluator provided for conditional join");
            return false;
        }

        // A refusal is the statement's rather than this pair's: two collations that disagree in ON are refused
        // however the rows compare, and so is a value the condition cannot compute (live-verified).
        return conditionEvaluator.matches(leftRow, rightRow);
    }

    /**
     * Combine left and right rows.
     */
    private Row combineRows(final Row leftRow, final Row rightRow) {
        final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
        combinedValues.addAll(rightRow.getValues());
        return Row.of(combinedValues);
    }

    /**
     * Combine left row with NULLs for right columns (for LEFT JOIN unmatched rows).
     */
    private Row combineRowsWithNullRight(final Row leftRow) {
        final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
        final int rightWidth = rightTable.columnCount();
        for (int i = 0; i < rightWidth; i++) {
            combinedValues.add(null);
        }
        return Row.of(combinedValues);
    }

    /**
     * Combine right row with NULLs for left columns (for RIGHT JOIN unmatched rows).
     */
    private Row combineRowsWithNullLeft(final Row rightRow) {
        final List<Object> combinedValues = new ArrayList<>();
        final int leftWidth = leftTable.columnCount();
        for (int i = 0; i < leftWidth; i++) {
            combinedValues.add(null);
        }
        combinedValues.addAll(rightRow.getValues());
        return Row.of(combinedValues);
    }
}
