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

package dev.frostlake.functions.aggregate;

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.values.FloatOriginNode;

/** Accumulator for {@link Max}. */
public class MaxAccumulator implements AggregateFunction.Accumulator {
    private Comparable max = null;

    @Override
    @SuppressWarnings("unchecked")
    public void accumulate(final Object value) {
        if (value != null && value instanceof Comparable) {
            if (max == null || ValueComparisons.compareForExtreme(value, max) > 0) {
                max = (Comparable) value;
            }
        }
    }

    @Override
    /** The value kept; a folded FLOAT in a VARIANT leaves the aggregate as a computed double (live-verified). */
    public Object getResult() { return FloatOriginNode.withoutOrigin(max); }

    @Override
    public void reset() { max = null; }

    @Override
    @SuppressWarnings("unchecked")
    public void merge(final AggregateFunction.Accumulator other) {
        final Comparable otherMax = ((MaxAccumulator) other).max;
        if (otherMax != null && (max == null || ValueComparisons.compareForExtreme(otherMax, max) > 0)) {
            max = otherMax;
        }
    }
}
