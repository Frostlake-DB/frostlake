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
import java.math.BigDecimal;

/**
 * Shared accumulator for the REGR_* linear-regression family. All nine functions are derived from the same
 * running sums over the non-null (y, x) pairs — note Snowflake's argument order {@code REGR_xxx(y, x)}, with
 * the dependent variable y first and the independent variable x second. {@link #getResult()} returns the
 * member selected by {@link RegrKind}.
 *
 * <p>Over doubles the members read {@link PairMoments}: COUNT = n; AVGX = Σx/n; AVGY = Σy/n; SXX, SYY and SXY the
 * unclamped centered sums; SLOPE the covariance over the variance of x, NULL when x does not vary; INTERCEPT =
 * AVGY − SLOPE·AVGX. Two members read an EXACT side (a NUMBER, not a FLOAT, a string or a VARIANT) exactly, as the
 * account does (live-verified): REGR_SXY over two exact sides sums the products as exact products at the scale a
 * product declares (see {@link ExactMoments}), and REGR_R2 asks whether a side varies through that side's own
 * variance — the exact VAR_POP over a NUMBER, the double one otherwise. R2 is NULL when x does not vary, 1 when y
 * does not, and else CORR squared (NULL with it).
 */
public class RegrAccumulator implements AggregateFunction.Accumulator, PairStatisticAccumulator {

    /** The pairs of a DECFLOAT argument, whose terms are DECFLOATs of 38 digits (see DecfloatPairStatistics). */
    private final DecfloatPairStatistics decfloatPairs = new DecfloatPairStatistics();
    private boolean decfloat;
    private final RegrKind kind;
    /** The pairs as doubles. */
    private final PairMoments moments = new PairMoments();
    /** Whether the Y argument is declared an exact NUMBER. */
    private boolean exactY;
    /** Whether the X argument is declared an exact NUMBER. */
    private boolean exactX;
    /** REGR_R2's exact moments of an exact X. */
    private final ExactMoments exactMomentsX = new ExactMoments();
    /** REGR_R2's exact moments of an exact Y. */
    private final ExactMoments exactMomentsY = new ExactMoments();
    /** The widest scale among the exact X values. */
    private int scaleX;
    /** The widest scale among the exact Y values. */
    private int scaleY;
    /** REGR_SXY's exact sum of products over two exact sides. */
    private BigDecimal exactProducts = BigDecimal.ZERO;
    /** Whether every product so far was of two exact values. */
    private boolean productsExact = true;

    public RegrAccumulator(final RegrKind kind) {
        this.kind = kind;
    }

    /** The member this accumulator computes, which decides which side of a pair is read at all. */
    public RegrKind kind() {
        return kind;
    }

    @Override
    public void accumulate(final Object value) {
        // The executor drives two-argument aggregates through accumulate(y, x); the single-value form is unused.
    }

    /** Accumulate one non-null pair. Snowflake order: {@code y} is the dependent (first) arg, {@code x} the second. */
    public void accumulate(final double y, final double x) {
        moments.add(y, x);
        productsExact = false;
        exactMomentsX.add(Double.valueOf(x));
        exactMomentsY.add(Double.valueOf(y));
    }

    /**
     * Tells the accumulator which sides are declared exact NUMBERs, which REGR_SXY and REGR_R2 read exactly.
     *
     * @param exactYSide whether the first argument is an exact NUMBER
     * @param exactXSide whether the second argument is an exact NUMBER
     */
    public void setExactArguments(final boolean exactYSide, final boolean exactXSide) {
        this.exactY = exactYSide;
        this.exactX = exactXSide;
    }

    /**
     * Accumulate one non-null pair, with the values as they were evaluated beside their doubles.
     *
     * @param y the dependent value as a double
     * @param x the independent value as a double
     * @param rawY the dependent value as evaluated
     * @param rawX the independent value as evaluated
     */
    public void accumulate(final double y, final double x, final Object rawY, final Object rawX) {
        moments.add(y, x);
        if (kind == RegrKind.R2) {
            if (exactX) {
                exactMomentsX.add(rawX);
                scaleX = Math.max(scaleX, AggregateNumerics.scaleOfInput(rawX));
            }
            if (exactY) {
                exactMomentsY.add(rawY);
                scaleY = Math.max(scaleY, AggregateNumerics.scaleOfInput(rawY));
            }
        } else if (kind == RegrKind.SXY && productsExact) {
            final BigDecimal product = exactX && exactY ? ExactMoments.exactProduct(rawY, rawX) : null;
            if (product == null) {
                productsExact = false;
            } else {
                exactProducts = exactProducts.add(product);
                SumAccumulator.requireSumFits(exactProducts);
            }
        }
    }

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloat = decfloat;
    }

    @Override
    public boolean isDecfloat() {
        return decfloat;
    }

    /** Reads only the sides the member reads, as the double path converts only those (see {@link #kind}). */
    @Override
    public void accumulateDecfloat(final Object y, final Object x) {
        final boolean readsY = kind != RegrKind.AVGX && kind != RegrKind.SXX && kind != RegrKind.COUNT;
        final boolean readsX = kind != RegrKind.AVGY;
        decfloatPairs.add(readsY ? y : null, readsX ? x : null, "REGR_" + kind.name());
    }

    @Override
    public Object getResult() {
        if (kind == RegrKind.COUNT) {
            return decfloat ? Long.valueOf(decfloatPairs.count()) : Long.valueOf(moments.count());
        }
        if (decfloat) {
            return decfloatResult();
        }
        if (moments.count() == 0) {
            return null;
        }
        switch (kind) {
            case AVGX:
                return moments.meanOfX();
            case AVGY:
                return moments.meanOfY();
            case SXX:
                return moments.centeredSquaresOfX();
            case SYY:
                return moments.centeredSquaresOfY();
            case SXY:
                return productsExact ? moments.centeredProducts(exactProducts.doubleValue())
                    : moments.centeredProducts();
            case SLOPE:
                return moments.slope();
            case INTERCEPT: {
                final Double slope = moments.slope();
                return slope == null ? null : moments.meanOfY() - slope.doubleValue() * moments.meanOfX();
            }
            case R2:
                return determination();
            default:
                return null;
        }
    }

    /** REGR_R2: NULL when x does not vary, 1 when y does not, else CORR squared. */
    private Double determination() {
        if (!varies(exactX, exactMomentsX, scaleX, moments.populationVarianceOfX())) {
            return null;
        }
        if (!varies(exactY, exactMomentsY, scaleY, moments.populationVarianceOfY())) {
            return Double.valueOf(1.0);
        }
        final Double correlation = moments.correlation();
        return correlation == null ? null : Double.valueOf(correlation.doubleValue() * correlation.doubleValue());
    }

    /** Whether a side varies: by its exact VAR_POP when it is an exact NUMBER, else by the double one. */
    private static boolean varies(final boolean exact, final ExactMoments exactMoments, final int scale,
                                  final double doubleVariance) {
        if (exact && exactMoments.isExact()) {
            return exactMoments.variance(false, scale).signum() != 0;
        }
        return doubleVariance != 0.0;
    }

    /** The member over DECFLOAT pairs. */
    private Object decfloatResult() {
        switch (kind) {
            case AVGX:
                return decfloatPairs.averageOfX();
            case AVGY:
                return decfloatPairs.averageOfY();
            case SXX:
                return decfloatPairs.centeredSquaresOfX();
            case SYY:
                return decfloatPairs.centeredSquaresOfY();
            case SXY:
                return decfloatPairs.centeredProducts();
            case SLOPE:
                return decfloatPairs.slope();
            case INTERCEPT:
                return decfloatPairs.intercept();
            case R2:
                return decfloatPairs.determination();
            default:
                return null;
        }
    }

    @Override
    public void reset() {
        decfloatPairs.reset();
        moments.reset();
        exactMomentsX.reset();
        exactMomentsY.reset();
        scaleX = 0;
        scaleY = 0;
        exactProducts = BigDecimal.ZERO;
        productsExact = true;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final RegrAccumulator o = (RegrAccumulator) other;
        decfloatPairs.merge(o.decfloatPairs);
        moments.merge(o.moments);
        exactMomentsX.merge(o.exactMomentsX);
        exactMomentsY.merge(o.exactMomentsY);
        scaleX = Math.max(scaleX, o.scaleX);
        scaleY = Math.max(scaleY, o.scaleY);
        exactProducts = exactProducts.add(o.exactProducts);
        productsExact = productsExact && o.productsExact;
    }
}
