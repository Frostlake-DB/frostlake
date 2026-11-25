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

package dev.frostlake.functions.scalar;

/**
 * The loosely written numbers Snowflake's JSON reader accepts, rewritten to what a JSON parser reads
 * (live-verified):
 *
 * <pre>
 *   - 1, -\n1, [+ .5]    blanks between a sign and its number are passed over: -1, -1, [0.5]
 *   ., [.], -., [+.]     a point with no digit on either side is the integer zero, whatever its sign
 *   .e1, [-.e1]          … and with an exponent after it the DOUBLE zero, positive whatever its sign
 * </pre>
 *
 * <p>Only a number that starts a value is rewritten: at the start of the document or after an opening bracket, a
 * comma or a colon. Nothing inside a string is touched, nor a sign that leads a word ({@code - x} keeps its fault)
 * or a hexadecimal number ({@code - 0x10} is refused).
 */
public final class JsonLenientNumbers {

    private JsonLenientNumbers() {
    }

    /**
     * The document with every loosely written number outside its strings rewritten.
     *
     * @param text the document as written, or null
     * @return the rewritten document, or the same text when it holds no such number
     */
    public static String normalize(final String text) {
        if (text == null || text.indexOf('.') < 0 && !hasBlankAfterSign(text)) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length());
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
                continue;
            }
            if ((c == '-' || c == '+') && startsAValue(text, i)) {
                int signed = i + 1;
                while (signed < text.length() && Character.isWhitespace(text.charAt(signed))) {
                    signed++;
                }
                if (signed < text.length() && (isDigit(text.charAt(signed)) || text.charAt(signed) == '.')
                        && !isHex(text, signed)) {
                    if (isBarePoint(text, signed)) {
                        // The sign of a zero with no digits is dropped.
                        out.append('0');
                        i = signed;
                    } else {
                        out.append(c);
                        i = signed - 1;
                    }
                    continue;
                }
            }
            if (c == '.' && startsAValue(text, i) && isBarePoint(text, i)) {
                out.append('0');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Whether some sign is followed by a blank, the other thing this rewrites. */
    private static boolean hasBlankAfterSign(final String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            final char c = text.charAt(i);
            if ((c == '-' || c == '+') && Character.isWhitespace(text.charAt(i + 1))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the character at {@code at} starts a value: nothing but blanks before it since a [, a comma or a colon. */
    private static boolean startsAValue(final String text, final int at) {
        int before = at - 1;
        while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
            before--;
        }
        if (before < 0) {
            return true;
        }
        final char c = text.charAt(before);
        return c == '[' || c == ',' || c == ':';
    }

    /** Whether the point at {@code at} has no digit after it (the caller knows none stands before it). */
    private static boolean isBarePoint(final String text, final int at) {
        return text.charAt(at) == '.' && (at + 1 >= text.length() || !isDigit(text.charAt(at + 1)));
    }

    /** Whether a hexadecimal number starts at {@code at}, which a sign parted from it does not lead. */
    private static boolean isHex(final String text, final int at) {
        return text.charAt(at) == '0' && at + 1 < text.length()
            && (text.charAt(at + 1) == 'x' || text.charAt(at + 1) == 'X');
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }
}
