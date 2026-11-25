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
import java.util.List;

/**
 * The sums the account plans the DECFLOAT variance, skewness and kurtosis over: {@code SUM(x)}, {@code SUM(x * x)},
 * {@code SUM((x * x) * x)} and {@code SUM(((x * x) * x) * x)}. Each product is held to 38 digits, and each sum is
 * taken exactly and held once, as a DECFLOAT SUM is: the sum of 1e38, 1 and -1e38 is 1 (live-verified). The mean is
 * the held sum over the count, held.
 */
final class DecfloatPowerSums {

    private final BigDecimal count;
    private final BigDecimal first;
    private final BigDecimal squares;
    private final BigDecimal cubes;
    private final BigDecimal fourthPowers;
    private final BigDecimal mean;

    /**
     * @param values the DECFLOAT values, at least one
     */
    DecfloatPowerSums(final List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal sumOfSquares = BigDecimal.ZERO;
        BigDecimal sumOfCubes = BigDecimal.ZERO;
        BigDecimal sumOfFourthPowers = BigDecimal.ZERO;
        for (final BigDecimal value : values) {
            final BigDecimal square = Decfloats.multiply(value, value);
            final BigDecimal cube = Decfloats.multiply(square, value);
            sum = DecfloatAggregates.added(sum, value);
            sumOfSquares = DecfloatAggregates.added(sumOfSquares, square);
            sumOfCubes = DecfloatAggregates.added(sumOfCubes, cube);
            sumOfFourthPowers = DecfloatAggregates.added(sumOfFourthPowers, Decfloats.multiply(cube, value));
        }
        count = BigDecimal.valueOf(values.size());
        first = DecfloatAggregates.held(sum);
        squares = DecfloatAggregates.held(sumOfSquares);
        cubes = DecfloatAggregates.held(sumOfCubes);
        fourthPowers = DecfloatAggregates.held(sumOfFourthPowers);
        mean = Decfloats.divide(first, count);
    }

    /**
     * {@code n·Σx² - Σx·Σx}, each step held to 38 digits, a negative difference taken as 0: the variance's dividend.
     *
     * @return the dividend
     */
    BigDecimal spread() {
        final BigDecimal spread = Decfloats.subtract(Decfloats.multiply(count, squares),
            Decfloats.multiply(first, first));
        return spread.signum() < 0 ? BigDecimal.ZERO : spread;
    }

    /** {@code ((Σx³ - (3·mean)·Σx²) + ((3·mean)·mean)·Σx) - ((n·mean)·mean)·mean}, each step held to 38 digits. */
    BigDecimal centeredCubes() {
        final BigDecimal threeMeans = Decfloats.multiply(BigDecimal.valueOf(3), mean);
        return Decfloats.subtract(Decfloats.add(
            Decfloats.subtract(cubes, Decfloats.multiply(threeMeans, squares)),
            Decfloats.multiply(Decfloats.multiply(threeMeans, mean), first)),
            Decfloats.multiply(Decfloats.multiply(Decfloats.multiply(count, mean), mean), mean));
    }

    /**
     * {@code (((Σx⁴ - (4·mean)·Σx³) + ((6·mean)·mean)·Σx²) - (((4·mean)·mean)·mean)·Σx) + (((n·mean)·mean)·mean)·mean},
     * each step held to 38 digits.
     */
    BigDecimal centeredFourthPowers() {
        final BigDecimal fourMeans = Decfloats.multiply(BigDecimal.valueOf(4), mean);
        final BigDecimal sixMeans = Decfloats.multiply(BigDecimal.valueOf(6), mean);
        final BigDecimal nMeans = Decfloats.multiply(count, mean);
        return Decfloats.add(Decfloats.subtract(Decfloats.add(
            Decfloats.subtract(fourthPowers, Decfloats.multiply(fourMeans, cubes)),
            Decfloats.multiply(Decfloats.multiply(sixMeans, mean), squares)),
            Decfloats.multiply(Decfloats.multiply(Decfloats.multiply(fourMeans, mean), mean), first)),
            Decfloats.multiply(Decfloats.multiply(Decfloats.multiply(nMeans, mean), mean), mean));
    }
}
