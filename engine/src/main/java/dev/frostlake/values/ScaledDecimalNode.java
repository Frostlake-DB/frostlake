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

import tools.jackson.databind.node.DecimalNode;

import java.math.BigDecimal;

/**
 * A number with decimals read from a NUMBER of more scale than it needs - {@code 12.50} out of a NUMBER(10,2) - which a
 * VARIANT holds as the descaled {@code 12.5} every surface prints, compares and stores, and which remembers the scale
 * its type gave it for the readers that show it: a Java handler's {@code String[]} element and an ARRAY_TO_STRING
 * element are {@code 12.50} (live-verified). A whole number of such a type is a {@link DecimalOriginNode}.
 */
public final class ScaledDecimalNode extends DecimalNode {
    private static final long serialVersionUID = 1L;

    private final int declaredScale;

    /**
     * @param value         the value, its trailing zeros stripped
     * @param declaredScale the scale of the NUMBER it was read from
     */
    public ScaledDecimalNode(final BigDecimal value, final int declaredScale) {
        super(value);
        this.declaredScale = declaredScale;
    }

    /** The scale of the NUMBER the value was read from. */
    public int declaredScale() {
        return declaredScale;
    }

    /**
     * A number carried at a scale its value does not need - {@code 2.500}, {@code 2.000} - as the VARIANT that holds it
     * descaled and remembers the scale; null for any other value, which a VARIANT holds as it is.
     *
     * @param value the number
     * @return the VARIANT holding it, or null
     */
    public static VariantValue variantOf(final BigDecimal value) {
        if (value.scale() <= 0) {
            return null;
        }
        final BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() >= value.scale()) {
            return null;
        }
        return VariantValue.ofNode(stripped.scale() <= 0
            ? new DecimalOriginNode(stripped.setScale(0), value.scale())
            : new ScaledDecimalNode(stripped, value.scale()));
    }

    /** The value written at the scale its type gave it: {@code 12.50}. */
    public String scaledText() {
        return decimalValue().setScale(declaredScale).toPlainString();
    }
}
