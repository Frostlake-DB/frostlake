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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The value the planner folds a constant arithmetic tree, or one of its constant leaves, to: the exact number the
 * tree computes (NULL where a leaf is NULL) and the scale its type declares (see {@link ConstantArithmeticFold}).
 */
final class FoldedArithmetic {

    /** The digits a converted value holds at most. */
    private static final int FOLDED_DIGITS = 38;

    private final BigDecimal exact;
    private final int declaredScale;
    private final boolean leaf;

    FoldedArithmetic(final BigDecimal exact, final int declaredScale, final boolean leaf) {
        this.exact = exact;
        this.declaredScale = declaredScale;
        this.leaf = leaf;
    }

    /** The exact number, or null for NULL. */
    BigDecimal exact() {
        return exact;
    }

    /** Whether the tree folds to NULL. */
    boolean isNull() {
        return exact == null;
    }

    /** Whether this is a constant leaf rather than an operation over two. */
    boolean isLeaf() {
        return leaf;
    }

    /** The value at the declared scale, unconverted: it may hold more digits than the type does. */
    BigDecimal value() {
        return exact.setScale(declaredScale, RoundingMode.HALF_UP);
    }

    /**
     * The sentence the conversion of the exact text to the declared type is refused in: a raw the signed 128-bit
     * carrier cannot hold is "not recognized", one it holds past 38 digits "out of range".
     *
     * @return the sentence, or null where the value fits
     */
    String refusal() {
        if (exact == null) {
            return null;
        }
        final BigDecimal converted = value();
        if (NumericRangeRefusal.outsideSb16Window(converted.unscaledValue())) {
            return "Numeric value '" + exact.toPlainString() + "' is not recognized";
        }
        if (converted.precision() > FOLDED_DIGITS) {
            return "Numeric value '" + exact.toPlainString() + "' is out of range";
        }
        return null;
    }

    /** Refuses the conversion where the value does not fit the declared type. */
    void requireFits() {
        final String refusal = refusal();
        if (refusal != null) {
            throw new RuntimeException(refusal);
        }
    }
}
