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

import dev.frostlake.values.Decfloats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The DECFLOAT variance, standard deviation, skewness and kurtosis, each the formula the account plans over the sums
 * of {@link DecfloatPowerSums}, every step held to 38 digits. The variance is {@code (n·Σx² - Σx·Σx) / (n(n - 1))}
 * (over {@code n²} for a population) and the deviation its held root: STDDEV over 1, 2 and 4 is
 * 1.527525231651946668862682397909336163. The skewness is {@code k · centered cubes / ((s · s) · s)} and the
 * kurtosis {@code k · centered fourth powers / (v · v) - c}, with the small-sample coefficients
 * {@code n / ((n - 1)(n - 2))} and {@code n(n + 1) / ((n - 1)(n - 2)(n - 3))} rounded to twelve fractional digits
 * and {@code c = 3(n - 1)² / ((n - 2)(n - 3))} to six: SKEW over 1, 2, 4 and 8 is
 * 1.1376243669582576648487648196737920811 and KURTOSIS 0.757655954629954196597353497164461248, and SKEW over 1, 3,
 * 4, 6, 7, 9 and 10 is -0.091437453209622087109061711274049517062 (live-verified). A series that does not vary
 * divides by its zero deviation.
 */
final class DecfloatMoments {

    /** The digits a root is computed to before its one rounding to 38. */
    private static final MathContext WORKING = new MathContext(Decfloats.DIGITS + 22, RoundingMode.HALF_EVEN);

    private final List<BigDecimal> values = new ArrayList<>();

    /** Adds a value, a NULL skipped. */
    void add(final Object value) {
        if (value instanceof Number) {
            values.add(Decfloats.of((Number) value, "VARIANCE"));
        }
    }

    void merge(final DecfloatMoments other) {
        values.addAll(other.values);
    }

    void reset() {
        values.clear();
    }

    private BigDecimal variance(final DecfloatPowerSums sums, final boolean sample) {
        final long n = values.size();
        return Decfloats.divide(sums.spread(), BigDecimal.valueOf(sample ? n * (n - 1) : n * n));
    }

    /**
     * The variance, held as a DECFLOAT: the spread of the sums over {@code n(n - 1)} for a sample, over {@code n²}
     * for a population.
     *
     * @param sample whether the sample variance is asked
     * @return the variance, or null for too few values
     */
    BigDecimal heldVariance(final boolean sample) {
        final long n = values.size();
        if (n == 0 || sample && n < 2) {
            return null;
        }
        return variance(new DecfloatPowerSums(values), sample);
    }

    /**
     * The sample skewness, held as a DECFLOAT.
     *
     * @return the skewness, or null under three values
     */
    BigDecimal heldSkewness() {
        final int n = values.size();
        if (n < 3) {
            return null;
        }
        final DecfloatPowerSums sums = new DecfloatPowerSums(values);
        final BigDecimal deviation = root(variance(sums, true));
        final BigDecimal cubed = Decfloats.multiply(Decfloats.multiply(deviation, deviation), deviation);
        return Decfloats.divide(Decfloats.multiply(coefficient(n, (long) (n - 1) * (n - 2), 12),
            sums.centeredCubes()), cubed);
    }

    /**
     * The sample excess kurtosis, held as a DECFLOAT.
     *
     * @return the kurtosis, or null under four values
     */
    BigDecimal heldKurtosis() {
        final int n = values.size();
        if (n < 4) {
            return null;
        }
        final DecfloatPowerSums sums = new DecfloatPowerSums(values);
        final BigDecimal variance = variance(sums, true);
        final BigDecimal scaled = Decfloats.divide(Decfloats.multiply(
            coefficient((long) n * (n + 1), (long) (n - 1) * (n - 2) * (n - 3), 12), sums.centeredFourthPowers()),
            Decfloats.multiply(variance, variance));
        return Decfloats.subtract(scaled, coefficient(3L * (n - 1) * (n - 1), (long) (n - 2) * (n - 3), 6));
    }

    /** A small-sample coefficient, a ratio of whole numbers rounded half up to its fractional digits. */
    private static BigDecimal coefficient(final long dividend, final long divisor, final int scale) {
        return BigDecimal.valueOf(dividend).divide(BigDecimal.valueOf(divisor), scale, RoundingMode.HALF_UP);
    }

    /**
     * The standard deviation, the root of the variance, held as a DECFLOAT.
     *
     * @param sample whether the sample deviation is asked
     * @return the deviation, or null for too few values
     */
    BigDecimal heldDeviation(final boolean sample) {
        final BigDecimal variance = heldVariance(sample);
        return variance == null ? null : root(variance);
    }

    /** The square root of a held DECFLOAT, correctly rounded to 38 digits. */
    private static BigDecimal root(final BigDecimal value) {
        return Decfloats.held(value.sqrt(WORKING));
    }
}
