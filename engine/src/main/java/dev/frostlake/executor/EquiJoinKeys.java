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

import dev.frostlake.executor.operators.JoinOperator;
import dev.frostlake.executor.operators.JoinSideKey;

/**
 * The keys a join's hash path buckets its two sides by: per position, a column of each side, or an expression of
 * one side's columns where the equality compares computed values.
 */
final class EquiJoinKeys {

    private final int[] left;
    private final int[] right;
    private final JoinSideKey[] leftReaders;
    private final JoinSideKey[] rightReaders;

    /**
     * @param left         per position, the left column, or -1 where the left reader computes it
     * @param right        per position, the right column, or -1 where the right reader computes it
     * @param leftReaders  per position, the left expression or null; the array itself is null when no key is computed
     * @param rightReaders per position, the right expression or null, alike
     */
    EquiJoinKeys(final int[] left, final int[] right, final JoinSideKey[] leftReaders,
                 final JoinSideKey[] rightReaders) {
        this.left = left;
        this.right = right;
        this.leftReaders = leftReaders;
        this.rightReaders = rightReaders;
    }

    /** Sets the keys on a join. */
    void applyTo(final JoinOperator join) {
        join.withEquiKeys(left, right);
        if (leftReaders != null) {
            join.withKeyExpressions(leftReaders, rightReaders);
        }
    }
}
