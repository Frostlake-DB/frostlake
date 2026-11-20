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

package dev.frostlake.geo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** The ways a geospatial coordinate is written as text. */
public final class GeoNumbers {

    /** Below this magnitude a coordinate is written with an exponent. */
    private static final double SMALL = 1e-4;

    private GeoNumbers() {
    }

    /**
     * A coordinate as the WKT forms write it: the shortest digits that read back as the same double, plainly, except
     * that a magnitude under {@code 1e-4} takes a two-digit exponent ({@code 1e-07}, {@code -1.23e-05}).
     */
    public static String wkt(final BigDecimal value) {
        final BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.signum() != 0 && stripped.abs().doubleValue() < SMALL) {
            return scientific(new BigDecimal(Double.toString(stripped.doubleValue())).stripTrailingZeros());
        }
        return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
    }

    /** A computed coordinate as the WKT forms write it, by the rule of {@link #wkt(BigDecimal)}. */
    public static String wkt(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return special(value);
        }
        return wkt(new BigDecimal(Double.toString(value)));
    }

    /**
     * A coordinate as a refusal quotes it: six significant digits, trailing zeros dropped, an exponent below
     * {@code 1e-4} or from {@code 1e6} on ({@code 123457}, {@code 1.23457e+06}, {@code 1e+300}), {@code nan} or
     * {@code -nan} for a value that is no number.
     */
    public static String quoted(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return special(value);
        }
        if (value == 0) {
            return 1 / value < 0 ? "-0" : "0";
        }
        final BigDecimal rounded = new BigDecimal(value).round(new MathContext(6, RoundingMode.HALF_EVEN));
        final int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent < -4 || exponent >= 6) {
            return scientific(rounded.stripTrailingZeros());
        }
        final BigDecimal stripped = rounded.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
    }

    private static String special(final double value) {
        if (Double.isNaN(value)) {
            return (Double.doubleToRawLongBits(value) < 0) ? "-nan" : "nan";
        }
        return value > 0 ? "inf" : "-inf";
    }

    /** {@code d[.ddd]e±XX} of a non-zero value with its trailing zeros stripped. */
    private static String scientific(final BigDecimal value) {
        final String digits = value.unscaledValue().abs().toString();
        final int exponent = value.precision() - value.scale() - 1;
        final StringBuilder out = new StringBuilder();
        if (value.signum() < 0) {
            out.append('-');
        }
        out.append(digits.charAt(0));
        if (digits.length() > 1) {
            out.append('.').append(digits, 1, digits.length());
        }
        out.append('e').append(exponent < 0 ? '-' : '+');
        final int magnitude = Math.abs(exponent);
        if (magnitude < 10) {
            out.append('0');
        }
        return out.append(magnitude).toString();
    }
}
