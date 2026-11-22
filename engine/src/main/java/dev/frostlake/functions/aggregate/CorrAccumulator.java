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

import dev.frostlake.functions.AggregateFunction;

/** Accumulator for {@link Corr}. */
public class CorrAccumulator implements AggregateFunction.Accumulator, PairStatisticAccumulator {
    /** The pairs of a DECFLOAT argument, whose correlation is a DECFLOAT of 38 digits (see DecfloatPairStatistics). */
    private final DecfloatPairStatistics decfloatPairs = new DecfloatPairStatistics();
    private boolean decfloat;
    /** The pairs as doubles, whose correlation is NULL when either side does not vary (see PairMoments). */
    private final PairMoments moments = new PairMoments();

    @Override
    public void accumulate(final Object v) {}

    /**
     * Accumulate one non-null pair.
     *
     * @param y the first argument's value
     * @param x the second argument's value
     */
    public void accumulate(final double y, final double x) {
        moments.add(y, x);
    }

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloat = decfloat;
    }

    @Override
    public boolean isDecfloat() {
        return decfloat;
    }

    @Override
    public void accumulateDecfloat(final Object y, final Object x) {
        decfloatPairs.add(y, x, "CORR");
    }

    @Override
    public Object getResult() {
        if (decfloat) {
            return decfloatPairs.correlation();
        }
        return moments.correlation();
    }

    @Override
    public void reset() {
        decfloatPairs.reset();
        moments.reset();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final CorrAccumulator o = (CorrAccumulator) other;
        decfloatPairs.merge(o.decfloatPairs);
        moments.merge(o.moments);
    }
}
