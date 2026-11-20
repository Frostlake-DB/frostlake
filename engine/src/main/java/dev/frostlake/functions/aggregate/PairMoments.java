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

/**
 * The running sums of a series of (y, x) pairs over doubles - the count and the sums of x, y, x², y² and x·y - from
 * which CORR, COVAR_POP, COVAR_SAMP and the REGR family read their double answers. Each sum is added the way SUM adds
 * a FLOAT column (see {@link CompensatedSum}), as the variance family's moments are (see {@link SquaredSumMoments}).
 *
 * <p>The members read them as the account does, digit for digit (live-verified):
 * <ul>
 *   <li>a population variance is {@code (n·Σx² − (Σx)²) / n²}, never below zero;</li>
 *   <li>the population covariance is {@code (Σxy − Σx·Σy / n) / n}, the sample one over {@code n − 1};</li>
 *   <li>the centered sums REGR_SXX, REGR_SYY and REGR_SXY are {@code Σx² − (Σx)² / n} and its kin, unclamped, so a
 *       constant side can come out a hair below zero (seven copies of 0.1 give -1.3877787807814457e-17);</li>
 *   <li>CORR is the covariance over the product of the two deviations, NULL when either variance is zero;</li>
 *   <li>REGR_SLOPE is the covariance over the variance of x, NULL when that is zero.</li>
 * </ul>
 */
public final class PairMoments {

    private long count;
    private final CompensatedSum sumX = new CompensatedSum();
    private final CompensatedSum sumY = new CompensatedSum();
    private final CompensatedSum sumXX = new CompensatedSum();
    private final CompensatedSum sumYY = new CompensatedSum();
    private final CompensatedSum sumXY = new CompensatedSum();

    /**
     * Fold one pair in, neither side NULL.
     *
     * @param y the dependent value
     * @param x the independent value
     */
    public void add(final double y, final double x) {
        count++;
        sumX.add(x);
        sumY.add(y);
        sumXX.add(x * x);
        sumYY.add(y * y);
        sumXY.add(x * y);
    }

    /**
     * Fold another series' sums in, each added as a term.
     *
     * @param other the sums to absorb
     */
    public void merge(final PairMoments other) {
        if (other.count == 0) {
            return;
        }
        count += other.count;
        sumX.add(other.sumX.correctedValue());
        sumY.add(other.sumY.correctedValue());
        sumXX.add(other.sumXX.correctedValue());
        sumYY.add(other.sumYY.correctedValue());
        sumXY.add(other.sumXY.correctedValue());
    }

    /** Forget every pair. */
    public void reset() {
        count = 0;
        sumX.reset();
        sumY.reset();
        sumXX.reset();
        sumYY.reset();
        sumXY.reset();
    }

    /** @return how many pairs were folded in */
    public long count() {
        return count;
    }

    /** @return Σx */
    public double sumOfX() {
        return sumX.correctedValue();
    }

    /** @return Σy */
    public double sumOfY() {
        return sumY.correctedValue();
    }

    /** @return the mean of x; the caller checks there is a pair */
    public double meanOfX() {
        return sumOfX() / count;
    }

    /** @return the mean of y; the caller checks there is a pair */
    public double meanOfY() {
        return sumOfY() / count;
    }

    /** @return {@code Σx² − (Σx)² / n}; the caller checks there is a pair */
    public double centeredSquaresOfX() {
        final double total = sumOfX();
        return sumXX.correctedValue() - total * total / count;
    }

    /** @return {@code Σy² − (Σy)² / n}; the caller checks there is a pair */
    public double centeredSquaresOfY() {
        final double total = sumOfY();
        return sumYY.correctedValue() - total * total / count;
    }

    /**
     * {@code Σxy − Σx·Σy / n}, with Σxy given by the caller (an exact sum of products where both sides are exact).
     *
     * @param sumOfProducts Σxy
     * @return the centered sum of products; the caller checks there is a pair
     */
    public double centeredProducts(final double sumOfProducts) {
        return sumOfProducts - sumOfX() * sumOfY() / count;
    }

    /** @return {@code Σxy − Σx·Σy / n} over the double products; the caller checks there is a pair */
    public double centeredProducts() {
        return centeredProducts(sumXY.correctedValue());
    }

    /** @return the population variance of x, never negative; the caller checks there is a pair */
    public double populationVarianceOfX() {
        return populationVariance(sumX.correctedValue(), sumXX.correctedValue());
    }

    /** @return the population variance of y, never negative; the caller checks there is a pair */
    public double populationVarianceOfY() {
        return populationVariance(sumY.correctedValue(), sumYY.correctedValue());
    }

    /** @return the population covariance; the caller checks there is a pair */
    public double populationCovariance() {
        return centeredProducts() / count;
    }

    /**
     * The correlation, or null when there is no pair or either side does not vary.
     *
     * @return CORR
     */
    public Double correlation() {
        if (count == 0) {
            return null;
        }
        final double varianceX = populationVarianceOfX();
        final double varianceY = populationVarianceOfY();
        if (varianceX == 0.0 || varianceY == 0.0) {
            return null;
        }
        return Double.valueOf(populationCovariance() / (Math.sqrt(varianceX) * Math.sqrt(varianceY)));
    }

    /**
     * The slope of the regression line, or null when there is no pair or x does not vary.
     *
     * @return REGR_SLOPE
     */
    public Double slope() {
        if (count == 0) {
            return null;
        }
        final double varianceX = populationVarianceOfX();
        if (varianceX == 0.0) {
            return null;
        }
        return Double.valueOf(populationCovariance() / varianceX);
    }

    private double populationVariance(final double total, final double squares) {
        final double n = count;
        return Math.max(0.0, (n * squares - total * total) / (n * n));
    }
}
