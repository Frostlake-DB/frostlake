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
 * A whole number read from a scaled NUMBER — {@code 3.00} out of a NUMBER(10,2) — which keeps the DECIMAL
 * kind inside a VARIANT. Its text is the descaled {@code 3} every surface prints, and it equals the INTEGER
 * 3, but TYPEOF answers DECIMAL, IS_INTEGER false and AS_INTEGER NULL, as on the account: the kind follows
 * the number's own scale, not its value. Only this node class remembers it, so a variant read back from its
 * text is an INTEGER, as {@code PARSE_JSON('3.00')} is on the account too.
 */
public final class DecimalOriginNode extends DecimalNode {
    private static final long serialVersionUID = 1L;

    /** The scale of the NUMBER the value was read from, or -1 where it is not known. */
    private final int declaredScale;

    /**
     * @param whole the value, at scale 0
     */
    public DecimalOriginNode(final BigDecimal whole) {
        this(whole, -1);
    }

    /**
     * @param whole         the value, at scale 0
     * @param declaredScale the scale of the NUMBER it was read from, or -1 where it is not known
     */
    public DecimalOriginNode(final BigDecimal whole, final int declaredScale) {
        super(whole);
        this.declaredScale = declaredScale;
    }

    /** The scale of the NUMBER the value was read from, or -1 where it is not known. */
    public int declaredScale() {
        return declaredScale;
    }

    /**
     * The value written at the scale its type gave it, {@code 3.00}, as a Java handler's {@code String[]} element shows
     * it (live-verified); its descaled text where the scale is not known.
     */
    public String scaledText() {
        return declaredScale > 0 ? decimalValue().setScale(declaredScale).toPlainString()
            : decimalValue().toPlainString();
    }
}
