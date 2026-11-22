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

import dev.frostlake.executor.NumericConversionException;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DecimalText;
import dev.frostlake.values.BinaryValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** The whole number a bitwise aggregate reads from one value of its group. */
final class BitAggregateOperand {

    private BitAggregateOperand() {
    }

    /**
     * A value as the whole number its bits are taken from: a fraction is rounded half away from zero — 4.5 is read
     * as 5 — and a text is read as a number, one that spells none being refused in the words of a failed conversion
     * (live-verified).
     *
     * @param value a value that is not NULL
     * @return the whole number
     */
    static BigInteger of(final Object value) {
        // A BINARY, a date or a time is refused by its type as the statement compiles, so one met here is a VARIANT's
        // member, which converts to no number: "Failed to cast variant value "FF" to FIXED" (live-verified).
        if (value instanceof BinaryValue) {
            throw heldRefusal(((BinaryValue) value).toHex());
        }
        if (SharedFunctionHelpers.isNativeTemporal(value)) {
            throw heldRefusal(SharedFunctionHelpers.variantTemporalText(SharedFunctionHelpers.inSessionZone(value)));
        }
        final BigDecimal number;
        try {
            number = value instanceof BigDecimal ? (BigDecimal) value : DecimalText.parse(value.toString().trim());
        } catch (final NumberFormatException notANumber) {
            throw new RuntimeException("Numeric value '" + value + "' is not recognized");
        }
        // All 128 bits the aggregates compute in, so 2^64 and past it keep their bits (live-verified).
        return number.setScale(0, RoundingMode.HALF_UP).toBigInteger();
    }

    /** The refusal of a VARIANT's member that spells its text in quotes and converts to no number. */
    private static NumericConversionException heldRefusal(final String text) {
        return new NumericConversionException("Failed to cast variant value \"" + text + "\" to FIXED");
    }
}
