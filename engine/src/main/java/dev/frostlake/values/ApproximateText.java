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

package dev.frostlake.values;

/**
 * A text read as a FLOAT: trimmed, a non-finite word ({@code 'inf'}, {@code '-Infinity'}, {@code 'NaN'}) taken
 * for its value, a hexadecimal number with no binary exponent read as though {@code p0} followed (see
 * {@link HexDoubleText}), and anything else that spells no number refused.
 *
 * <p>★ A FINITE TEXT PAST THE DOUBLE'S RANGE IS NO NUMBER WHILE THE STATEMENT RUNS. A column write, a cast,
 * TO_DOUBLE and the arithmetic or comparison of a row's text beside a FLOAT all refuse it with the sentence of a
 * text that spells no number, while one the plan folds to a constant becomes the infinity (all live-verified):
 *
 * <pre>
 *   INSERT INTO t (f) VALUES ('2e308')      Numeric value '2e308' is not recognized   a DML envelope around it
 *   INSERT INTO t (f) VALUES ('1e-400')     0                                          an underflow reads
 *   INSERT INTO t (f) VALUES ('-inf')       -inf                                       the words read
 *   SELECT s::FLOAT, TO_DOUBLE(s)           Numeric value '5e308' is not recognized   s a column
 *   SELECT TRY_TO_DOUBLE(s)                 NULL
 *   SELECT '2e308'::FLOAT                   inf                                        folded
 *   SELECT TO_DOUBLE(UPPER('2e308'))        inf                                        folded
 * </pre>
 *
 * <p>The refusal echoes the text trimmed and without one leading plus sign: {@code ' +5e308 '} is "Numeric value
 * '5e308' is not recognized", {@code '++1'} "Numeric value '+1' is not recognized".
 */
public final class ApproximateText {

    private ApproximateText() {
    }

    /**
     * The double a text spells while the statement runs, refusing one that spells none or one past the double's
     * range.
     *
     * @param raw the text as written
     * @return the double
     */
    public static Double read(final String raw) {
        final Double value = folded(raw);
        if (value == null || overflows(raw)) {
            throw new RuntimeException(unreadable(raw));
        }
        return value;
    }

    /**
     * Whether a text spells a finite number past the double's range, which only a folded constant reads (as the
     * infinity of its sign).
     *
     * @param raw the text as written
     * @return whether it does
     */
    public static boolean overflows(final String raw) {
        final Double value = folded(raw);
        return value != null && Double.isInfinite(value.doubleValue()) && NonFiniteDoubles.parseForCast(raw) == null;
    }

    /**
     * The double a text spells where the plan folds it to a constant: a finite text past the double's range is the
     * infinity of its sign.
     *
     * @param raw the text as written
     * @return the double, or null where the text spells no number
     */
    public static Double folded(final String raw) {
        final String text = raw.trim();
        final Double nonFinite = NonFiniteDoubles.parseForCast(text);
        if (nonFinite != null) {
            return nonFinite;
        }
        try {
            return Double.valueOf(Double.parseDouble(text));
        } catch (final NumberFormatException notNumeric) {
            return HexDoubleText.withoutExponent(text, true);
        }
    }

    /**
     * The sentence a text earns that spells no FLOAT, echoing it trimmed and without one leading plus sign.
     *
     * @param raw the text as written
     * @return the sentence
     */
    public static String unreadable(final String raw) {
        final String text = raw.trim();
        return "Numeric value '" + (text.startsWith("+") ? text.substring(1) : text) + "' is not recognized";
    }
}
