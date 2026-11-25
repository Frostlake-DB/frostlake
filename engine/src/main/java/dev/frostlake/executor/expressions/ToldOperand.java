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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An exact-number operand of an operation as a column's default or a virtual column's expression is told back
 * (live-verified). The plan stores every exact number in the narrowest of 1, 2, 4, 8 or 16 bytes its width
 * takes, and an operation first widens each operand to the storage of the widest of its operands and, for
 * {@code +}, {@code -}, {@code *} and {@code /}, of its result; the told text spells that widening as one
 * more CAST to the operand's own type: {@code (CAST(T.H9 AS NUMBER(9,0))) + 1},
 * {@code T.B + (CAST(CAST(1 AS NUMBER(10,2)) AS NUMBER(10,2)))}. A constant is stored in whatever its value
 * needs: a number written out bare is never widened; any other constant, and a rescaled one, takes the storage
 * of the scaled integer it holds, so the 12 of {@code z + 12} over a NUMBER(2,1) (120) keeps one byte where the
 * 13 of {@code z + 13} (130) takes two, and {@code b + (100 * 100)} over a NUMBER(10,2) holds 1000000 in four;
 * a rescaled zero is widened nowhere, {@code T.B + (CAST(0 AS NUMBER(10,2)))}.
 */
final class ToldOperand {

    /** The widest storage an exact number takes, in bytes. */
    static final int WIDEST_STORAGE = 16;

    private final String inner;
    private final String operand;
    private final int precision;
    private final int scale;
    private final BigDecimal constant;
    private final boolean written;
    private final int storage;

    /**
     * @param inner     the operand printed bare, as a function argument or a cast's source is
     * @param operand   the operand printed beside an infix operator, bracketed where it is not bare
     * @param precision the precision of the operand's type
     * @param scale     the scale of the operand's type
     * @param constant  the value of a constant operand, null for anything else
     * @param written   whether the operand is a number written out
     * @param rescaled  whether the operand is rescaled from the type it had
     */
    ToldOperand(final String inner, final String operand, final int precision, final int scale,
                final BigDecimal constant, final boolean written, final boolean rescaled) {
        this.inner = inner;
        this.operand = operand;
        this.precision = precision;
        this.scale = scale;
        this.constant = constant;
        this.written = written;
        if (constant == null) {
            this.storage = storageBytes(precision);
        } else if (rescaled) {
            this.storage = constant.signum() == 0 ? 0 : valueStorageBytes(constant, scale);
        } else {
            this.storage = written ? 0 : valueStorageBytes(constant, scale);
        }
    }

    /** This operand rescaled to another width, printed as {@code rescale}. */
    ToldOperand rescaledTo(final String rescale, final int newPrecision, final int newScale) {
        return new ToldOperand(rescale, "(" + rescale + ")", newPrecision, newScale, constant, written, true);
    }

    String inner() {
        return inner;
    }

    int precision() {
        return precision;
    }

    int scale() {
        return scale;
    }

    /** The storage this operand is held in, in bytes; 0 for one never widened. */
    int storage() {
        return storage;
    }

    /** The operand beside an infix operator, widened to {@code common} bytes where it is held in fewer. */
    String asOperand(final int common) {
        return widens(common) ? "(" + widened() + ")" : operand;
    }

    /** The operand as a function argument, widened to {@code common} bytes where it is held in fewer. */
    String asArgument(final int common) {
        return widens(common) ? widened() : inner;
    }

    private boolean widens(final int common) {
        return storage != 0 && storage < common;
    }

    private String widened() {
        return "CAST(" + inner + " AS NUMBER(" + precision + "," + scale + "))";
    }

    /** The narrowest storage holding every number of a precision: 1, 2, 4, 8 or 16 bytes. */
    static int storageBytes(final int precision) {
        if (precision <= 2) {
            return 1;
        }
        if (precision <= 4) {
            return 2;
        }
        if (precision <= 9) {
            return 4;
        }
        return precision <= 18 ? 8 : WIDEST_STORAGE;
    }

    /** The narrowest signed storage holding a value scaled to an integer at a scale. */
    private static int valueStorageBytes(final BigDecimal value, final int atScale) {
        final int bits = value.setScale(atScale, RoundingMode.HALF_UP).unscaledValue().bitLength();
        if (bits <= 7) {
            return 1;
        }
        if (bits <= 15) {
            return 2;
        }
        if (bits <= 31) {
            return 4;
        }
        return bits <= 63 ? 8 : WIDEST_STORAGE;
    }
}
