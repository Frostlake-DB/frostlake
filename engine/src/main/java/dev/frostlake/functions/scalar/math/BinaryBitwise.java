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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.math.BigInteger;
import java.util.Locale;

/**
 * The bitwise functions over BINARY values, which work byte by byte and answer a BINARY (live-verified):
 *
 * <ul>
 *   <li>BITAND, BITOR and BITXOR combine two values of one length; two lengths are refused row by row, "The lengths
 *       of two variable-sized fields do not match: first length 2, second length 1", unless a third argument
 *       names the side the shorter value is padded with zero bytes on, LEFT or RIGHT in any case and written
 *       exactly: {@code BITAND(X'FF00', X'0F', 'LEFT')} is 0000, with RIGHT 0F00. Any other padding side is
 *       refused, even for two values of one length, in its upper-cased text: "Invalid parameter value: MIDDLE.
 *       Reason: Only [left | right] is allowed." A NULL operand or padding side answers NULL first;</li>
 *   <li>BITNOT inverts every byte;</li>
 *   <li>BITSHIFTLEFT and BITSHIFTRIGHT move the value's bits within its own length, the bits moved out dropped and
 *       zeros moved in: {@code BITSHIFTLEFT(X'8001', 1)} is 0002 and {@code BITSHIFTRIGHT(X'8001', 1)} 4000. The
 *       count is a 32-bit whole number, rounded half away from zero: 2147483648 is "Numeric value '2147483648' is
 *       out of range", and a negative count is refused as for a number.</li>
 * </ul>
 */
public final class BinaryBitwise {

    private BinaryBitwise() {
    }

    /**
     * Whether a call's operands hold a BINARY, which makes it the BINARY form.
     *
     * @param first  the first operand
     * @param second the second operand, or null
     * @return whether either is a BINARY
     */
    static boolean holdsBinary(final Object first, final Object second) {
        return first instanceof BinaryValue || second instanceof BinaryValue;
    }

    /**
     * Two BINARY values combined byte by byte.
     *
     * @param operation the byte-wise operation
     * @param first     the first value
     * @param second    the second value
     * @param padSide   the padding side as handed, or null where the call names none
     * @return the combined value
     */
    static BinaryValue combined(final BitwiseOperation operation, final BinaryValue first, final BinaryValue second,
                                final Object padSide) {
        final boolean padLeft = padSide != null && padsLeft(padSide);
        final byte[] a = first.bytes();
        final byte[] b = second.bytes();
        if (a.length != b.length && padSide == null) {
            throw new RuntimeException("The lengths of two variable-sized fields do not match: first length "
                + a.length + ", second length " + b.length);
        }
        final int length = Math.max(a.length, b.length);
        final byte[] wideA = padded(a, length, padLeft);
        final byte[] wideB = padded(b, length, padLeft);
        final byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            out[i] = operation.apply(wideA[i], wideB[i]);
        }
        return BinaryValue.of(out);
    }

    /**
     * A BINARY value with every bit inverted.
     *
     * @param value the value
     * @return its negation
     */
    static BinaryValue inverted(final BinaryValue value) {
        final byte[] bytes = value.bytes();
        final byte[] out = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (byte) ~bytes[i];
        }
        return BinaryValue.of(out);
    }

    /**
     * A BINARY value's bits moved within its own length.
     *
     * @param value the value
     * @param count the count as handed (a number)
     * @param left  whether to the left
     * @return the shifted value
     */
    static BinaryValue shifted(final BinaryValue value, final Object count, final boolean left) {
        final BigInteger whole = BitwiseOperand.wide(count);
        if (whole.bitLength() >= Integer.SIZE) {
            throw new RuntimeException("Numeric value '" + whole + "' is out of range");
        }
        final int bits = whole.intValue();
        if (bits < 0) {
            throw new RuntimeException("Invalid parameter value: " + bits
                + ". Reason: shift parameter must be greater or equal to zero");
        }
        final byte[] bytes = value.bytes();
        final int width = bytes.length * Byte.SIZE;
        if (bits >= width) {
            return BinaryValue.of(new byte[bytes.length]);
        }
        final BigInteger held = new BigInteger(1, bytes);
        final BigInteger moved = left
            ? held.shiftLeft(bits).and(BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE))
            : held.shiftRight(bits);
        final byte[] magnitude = moved.toByteArray();
        final byte[] out = new byte[bytes.length];
        final int copied = Math.min(magnitude.length, out.length);
        System.arraycopy(magnitude, magnitude.length - copied, out, out.length - copied, copied);
        return BinaryValue.of(out);
    }

    /** Whether the padding side names LEFT, refusing anything but LEFT or RIGHT. */
    private static boolean padsLeft(final Object padSide) {
        final String side = sideText(padSide).toUpperCase(Locale.ROOT);
        if (side.equals("LEFT")) {
            return true;
        }
        if (side.equals("RIGHT")) {
            return false;
        }
        throw new RuntimeException("Invalid parameter value: " + side + ". Reason: Only [left | right] is allowed.");
    }

    /** The padding side as text, a VARIANT string by its own characters. */
    private static String sideText(final Object padSide) {
        if (padSide instanceof VariantValue) {
            return ((VariantValue) padSide).node().asText();
        }
        return SharedFunctionHelpers.textOf(padSide);
    }

    /** The bytes widened to a length with zero bytes on one side. */
    private static byte[] padded(final byte[] bytes, final int length, final boolean left) {
        if (bytes.length == length) {
            return bytes;
        }
        final byte[] out = new byte[length];
        System.arraycopy(bytes, 0, out, left ? length - bytes.length : 0, bytes.length);
        return out;
    }
}
