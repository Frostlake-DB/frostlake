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
package dev.frostlake.functions.scalar.math;

/** The byte-wise operation a two-operand bitwise function applies to two BINARY values. */
public enum BitwiseOperation {
    AND,
    OR,
    XOR;

    /**
     * The operation over one byte of each operand.
     *
     * @param left  the first operand's byte
     * @param right the second operand's byte
     * @return the combined byte
     */
    byte apply(final byte left, final byte right) {
        switch (this) {
            case AND:
                return (byte) (left & right);
            case OR:
                return (byte) (left | right);
            default:
                return (byte) (left ^ right);
        }
    }
}
