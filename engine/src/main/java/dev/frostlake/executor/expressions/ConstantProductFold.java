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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.values.ValueRange;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A product the account computes while the statement compiles, its two operands constants: literals and casts of
 * them, or columns the table's statistics pin to one value and no NULL. The planner multiplies the operands' values
 * exactly — each at its own digits, trailing zeros dropped — and, where that product has no more than 38 digits,
 * converts its text to the type the product declares, as a text cast would: a raw the signed 128-bit carrier cannot
 * hold is "Numeric value '171610000000000000000000000000000' is not recognized", one it holds past 38 digits
 * "Numeric value '100000000000000000000000000000000' is out of range". A product of more digits is left to the row,
 * and so is one that declares no fraction, which the carrier multiplies (all live-verified).
 */
public final class ConstantProductFold {

    /** The digits a folded product's text holds at most. */
    private static final int FOLDED_DIGITS = 38;

    private ConstantProductFold() {
    }

    /**
     * Whether an operand's interval pins it to one value that is not NULL, as a constant or a pinned column is.
     *
     * @param range the operand's interval, or null where none is known
     * @return whether the planner reads the operand as a constant
     */
    public static boolean pins(final ValueRange range) {
        return range != null && !range.isEmpty() && !range.isNullable() && !range.isOpaqueToComputation()
            && range.getMin() != null && range.getMax() != null && range.getMin().compareTo(range.getMax()) == 0;
    }

    /**
     * Refuses a folded product whose text the declared type cannot hold; answers nothing for every other.
     *
     * @param left          one operand's value
     * @param right         the other operand's value
     * @param declaredScale the scale the product declares
     */
    public static void requireFits(final BigDecimal left, final BigDecimal right, final int declaredScale) {
        final String refusal = refusal(left, right, declaredScale);
        if (refusal != null) {
            throw new RuntimeException(refusal);
        }
    }

    /**
     * The sentence a folded product is refused in where its text does not fit the declared type.
     *
     * @param left          one operand's value
     * @param right         the other operand's value
     * @param declaredScale the scale the product declares
     * @return the sentence, or null where the fold answers or the product is left to the row
     */
    public static String refusal(final BigDecimal left, final BigDecimal right, final int declaredScale) {
        if (declaredScale <= 0) {
            return null;
        }
        final BigDecimal exact = ownDigits(left).multiply(ownDigits(right));
        if (exact.precision() > FOLDED_DIGITS) {
            return null;
        }
        final BigDecimal converted = exact.setScale(declaredScale, RoundingMode.HALF_UP);
        if (NumericRangeRefusal.outsideSb16Window(converted.unscaledValue())) {
            return "Numeric value '" + exact.toPlainString() + "' is not recognized";
        }
        if (converted.precision() > FOLDED_DIGITS) {
            return "Numeric value '" + exact.toPlainString() + "' is out of range";
        }
        return null;
    }

    /** A value at its own digits: trailing zeros of its fraction dropped, never a negative scale. */
    private static BigDecimal ownDigits(final BigDecimal value) {
        final BigDecimal stripped = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
