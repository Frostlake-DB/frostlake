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

import dev.frostlake.executor.AggregateRangeRefusal;
import dev.frostlake.executor.NumericRangeRefusal;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The count, sum and sum of squares of an exact series, which the variance family uses over EXACT inputs: the
 * variance is the one division of {@code n·Σx² − (Σx)²} by {@code n·(n − 1)} (or {@code n²}), rounded half up to
 * the scale its column declares. The account works in exact numbers there, a moving window frame's rows included:
 * VARIANCE over the NUMBERs 100000000, 100000001 and 100000002 is 1.000000, and over 100000000.01 and
 * 100000000.02 it is 0.0000500000 (live-verified). Over FLOATs the account reads double sums instead, digit for digit
 * — see {@link SquaredSumMoments}.
 *
 * <p>The account plans the statistic as a formula over two sums, and every step of it is the arithmetic the same
 * expressions take on their own. A square is a product at the scale a product declares — the two scales added, up
 * to twelve or the value's own — rounded half up to it, so a series of seven or more decimals is read through
 * rounded squares. A square whose raw leaves the signed 128-bit carrier at that scale is refused naming the raw of
 * the exact product ("Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38" for
 * 20000000000000000000, "value 1.7161e+40" for a NUMBER(38,7) of 13100000000000), and so are {@code n·Σx²} and
 * {@code (Σx)²} when the statistic is read; a running sum that leaves its carrier is a SUM aggregate's overflow,
 * which a window with an ORDER BY reports in its own words. The difference is then divided: below a scale of twelve
 * by a rounding division whose quotient must fit the carrier at its own scale, and from twelve on by a plain one,
 * whose dividend must fit 38 digits and is refused in its own digits otherwise (live-verified).
 */
public final class ExactMoments {

    /** The scale a product keeps at most unless an operand's own is higher, from which a division is a plain one. */
    private static final int PLAIN_DIVISION_SCALE = 12;

    /** The digits a rounding division's scale gains over its dividend's. */
    private static final int QUOTIENT_DIGITS = 6;

    /** The digits of the widest NUMBER. */
    private static final int CARRIER_DIGITS = 38;

    private long count;
    private BigDecimal sum = BigDecimal.ZERO;
    private BigDecimal sumOfSquares = BigDecimal.ZERO;
    private long inexact;

    /**
     * Folds one value in: a value of no exact type is counted and leaves the moments inexact.
     *
     * @param value the value, not null
     */
    public void add(final Object value) {
        count++;
        final BigDecimal number = exactValue(value);
        if (number == null) {
            inexact++;
            return;
        }
        final BigDecimal square = product(number, number);
        sum = sum.add(number);
        sumOfSquares = sumOfSquares.add(square);
        requireSumsFit();
    }

    /**
     * Takes one value folded in before out again, as a row leaves a moving window frame.
     *
     * @param value the value, not null
     */
    public void remove(final Object value) {
        count--;
        final BigDecimal number = exactValue(value);
        if (number == null) {
            inexact--;
            return;
        }
        sum = sum.subtract(number);
        sumOfSquares = sumOfSquares.subtract(atProductScale(number.multiply(number), number.scale(), number.scale()));
    }

    /**
     * Folds another series' moments in.
     *
     * @param other the other series
     */
    public void merge(final ExactMoments other) {
        count += other.count;
        sum = sum.add(other.sum);
        sumOfSquares = sumOfSquares.add(other.sumOfSquares);
        inexact += other.inexact;
        requireSumsFit();
    }

    /** The values folded in. */
    public long count() {
        return count;
    }

    /** Whether every value folded in was of an exact type. */
    public boolean isExact() {
        return inexact == 0;
    }

    /**
     * The variance at the scale a series of an input scale declares; the caller checks there are two values at least
     * for a sample's, one for the population's.
     *
     * @param sample     whether the denominator is {@code n − 1} rather than {@code n}
     * @param inputScale the scale of the widest value of the series
     * @return the variance
     */
    public BigDecimal variance(final boolean sample, final int inputScale) {
        final int productScale = productScale(inputScale, inputScale);
        final BigDecimal n = BigDecimal.valueOf(count);
        // n·Σx² bounds (Σx)², so it is the product that leaves the carrier first.
        final BigDecimal scaledSquares = n.multiply(sumOfSquares);
        requireRawFits(scaledSquares, scaledSquares);
        final BigDecimal difference = scaledSquares.subtract(product(sum, sum));
        final BigDecimal spread = (difference.signum() < 0 ? BigDecimal.ZERO : difference)
            .setScale(Math.max(productScale, difference.scale()), RoundingMode.HALF_UP);
        final BigDecimal denominator = n.multiply(sample ? BigDecimal.valueOf(count - 1) : n);
        if (productScale >= PLAIN_DIVISION_SCALE) {
            if (spread.precision() > CARRIER_DIGITS) {
                throw new AggregateRangeRefusal(NumericRangeRefusal.typedText("SB16", CARRIER_DIGITS, spread.scale(),
                    true, spread.toPlainString()));
            }
            return spread.divide(denominator, spread.scale(), RoundingMode.HALF_UP);
        }
        final int scale = Math.min(productScale + QUOTIENT_DIGITS, PLAIN_DIVISION_SCALE);
        final BigDecimal variance = spread.divide(denominator, scale, RoundingMode.HALF_UP);
        if (NumericRangeRefusal.outsideSb16Window(variance.unscaledValue())) {
            throw new AggregateRangeRefusal(NumericRangeRefusal.typedText("SB16", CARRIER_DIGITS, scale, true,
                NumericRangeRefusal.gText(variance.doubleValue())));
        }
        return variance;
    }

    public void reset() {
        count = 0;
        sum = BigDecimal.ZERO;
        sumOfSquares = BigDecimal.ZERO;
        inexact = 0;
    }

    /** Refuses a running sum that leaves its carrier. */
    private void requireSumsFit() {
        SumAccumulator.requireSumFits(sum);
        SumAccumulator.requireSumFits(sumOfSquares);
    }

    /**
     * The product of two values at the scale it declares, rounded half up to it and refused when its raw leaves the
     * carrier there.
     *
     * @param left  one value
     * @param right the other
     * @return the product
     */
    private static BigDecimal product(final BigDecimal left, final BigDecimal right) {
        final BigDecimal exact = left.multiply(right);
        final BigDecimal product = atProductScale(exact, left.scale(), right.scale());
        requireRawFits(product, exact);
        return product;
    }

    /** An exact product rounded half up to the scale a product of two scales declares. */
    private static BigDecimal atProductScale(final BigDecimal exact, final int leftScale, final int rightScale) {
        final int scale = productScale(leftScale, rightScale);
        return exact.scale() > scale ? exact.setScale(scale, RoundingMode.HALF_UP) : exact;
    }

    /** The scale a product declares: its operands' scales added, up to twelve or the wider operand's own. */
    private static int productScale(final int leftScale, final int rightScale) {
        return Math.min(leftScale + rightScale, Math.max(Math.max(leftScale, rightScale), PLAIN_DIVISION_SCALE));
    }

    /**
     * Refuses a product whose raw scaled integer leaves the signed 128-bit carrier, printing the raw of the exact
     * product as a double.
     *
     * @param product the product at its declared scale
     * @param exact   the exact product
     */
    private static void requireRawFits(final BigDecimal product, final BigDecimal exact) {
        if (NumericRangeRefusal.outsideSb16Window(product.unscaledValue())) {
            throw new AggregateRangeRefusal(NumericRangeRefusal.typedText("SB16", CARRIER_DIGITS, 0, false,
                NumericRangeRefusal.gText(exact.unscaledValue().doubleValue())));
        }
    }

    /** A value of an exact type as a decimal at a scale of zero or more, or null for any other. */
    private static BigDecimal exactValue(final Object value) {
        if (value instanceof BigDecimal) {
            final BigDecimal decimal = (BigDecimal) value;
            return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        return null;
    }
}
