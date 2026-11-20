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

package dev.frostlake.functions.scalar.string;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Reads a regular expression the way the account's matcher reads one, which is not the way
 * {@code java.util.regex} does. The account takes POSIX extended syntax with the Perl classes {@code \d \s \w},
 * word boundaries and lazy repetition, and nothing else of Perl: no {@code (?…)} group of any kind, no
 * backreference, no {@code \p{…}}, {@code \A}, {@code \z} or {@code \Q…\E}. A pattern it cannot read is refused
 * as {@code Invalid regular expression: '<pattern>', <reason>: <fragment>}, with the reason and the fragment the
 * account gives (live-verified pattern by pattern):
 *
 * <pre>
 *   a\            trailing \
 *   (a            missing ): (a                        the whole pattern
 *   a)            unexpected ): a)                     the whole pattern
 *   [a            missing ]: [a                        from the bracket on
 *   [z-a]         invalid character class range: z-a
 *   [\d-z]        invalid character class range: -z    a '-' that neither opens nor closes the class
 *   [[:foo:]]     invalid character class range: [:foo:]
 *   *  a|*  (*)   no argument for repetition operator: *
 *   (?:a) (?i)a   no argument for repetition operator: ?
 *   {2}           no argument for repetition operator: {2}
 *   a{2,1}        invalid repetition size: {2,1}       and a{1001}: a count past 1000
 *   \1  \p{L}     invalid escape sequence: \1, \p
 *   \x{110000}    invalid escape sequence: \x{110000
 * </pre>
 *
 * <p>What it does read is written out again for {@code java.util.regex}, where the two differ: a brace that opens
 * no count is a literal brace ({@code a{,2}}, {@code a{x}}), a repetition of a repetition repeats the whole
 * ({@code a**}), {@code \0} is the NUL character, {@code \v} a vertical tab, and a POSIX class keeps its meaning
 * inside brackets.
 *
 * <p>The account's lines end at {@code \n} alone, so its anchors are written out by that rule: {@code $} is the
 * end of the text (not before a final newline), and with the {@code m} parameter {@code ^} and {@code $} stand
 * after and before each {@code \n} and at either end of the text, a final newline included. {@code \s} is
 * {@code [\t\n\f\r ]} (no vertical tab) and a word boundary is judged by the ASCII word characters.
 */
final class Re2PatternReader {

    private static final int LARGEST_REPEAT = 1000;
    private static final int LARGEST_CHARACTER = 0x10FFFF;
    private static final int A_CLASS = -1;
    private static final int NO_VALUE = -2;
    private static final String[][] POSIX_CLASSES = {
        {"alnum", "\\p{Alnum}"}, {"alpha", "\\p{Alpha}"}, {"ascii", "\\p{ASCII}"}, {"blank", "\\p{Blank}"},
        {"cntrl", "\\p{Cntrl}"}, {"digit", "\\p{Digit}"}, {"graph", "\\p{Graph}"}, {"lower", "\\p{Lower}"},
        {"print", "\\p{Print}"}, {"punct", "\\p{Punct}"}, {"space", "\\p{Space}"}, {"upper", "\\p{Upper}"},
        {"word", "\\w"}, {"xdigit", "\\p{XDigit}"},
    };

    private static final String SPACE = "\\t\\n\\f\\r\\x{20}";
    private static final String WORD_BOUNDARY = "(?:(?<=\\w)(?!\\w)|(?<!\\w)(?=\\w))";
    private static final String NOT_WORD_BOUNDARY = "(?:(?<=\\w)(?=\\w)|(?<!\\w)(?!\\w))";

    private final String pattern;
    private final boolean multiLine;
    private final boolean caseless;
    private final StringBuilder java = new StringBuilder();
    private String fault;
    private int at;

    /**
     * Reads a pattern.
     *
     * @param pattern   the pattern, as written
     * @param multiLine whether the {@code m} parameter is given
     * @param caseless  whether the pattern folds case (the {@code i} parameter)
     */
    Re2PatternReader(final String pattern, final boolean multiLine, final boolean caseless) {
        this.pattern = pattern;
        this.multiLine = multiLine;
        this.caseless = caseless;
        read();
    }

    /** The refusal in the account's sentence, or null when the pattern reads. */
    String refusal() {
        return fault == null ? null : "Invalid regular expression: '" + pattern + "', " + fault;
    }

    /** The pattern as {@code java.util.regex} reads it to the same meaning. */
    String javaPattern() {
        return java.toString();
    }

    private void read() {
        final List<Integer> groups = new ArrayList<>();
        int atom = 0;
        boolean operand = false;
        boolean repeated = false;
        boolean lazyAllowed = false;
        while (at < pattern.length() && fault == null) {
            final char c = pattern.charAt(at);
            if (c == '*' || c == '+' || c == '?' || c == '{' && countEnd() > 0) {
                final String operator = c == '{' ? pattern.substring(at, countEnd()) : String.valueOf(c);
                if (!operand) {
                    fault = "no argument for repetition operator: " + operator;
                } else if (c == '{' && !countFits(operator)) {
                    fault = "invalid repetition size: " + operator;
                } else if (repeated && c == '?' && lazyAllowed) {
                    java.append('?');
                    lazyAllowed = false;
                } else {
                    if (repeated) {
                        // A repetition of a repetition repeats the whole of it.
                        java.insert(atom, "(?:").append(')');
                    }
                    java.append(operator);
                    repeated = true;
                    lazyAllowed = true;
                }
                at += operator.length();
                continue;
            }
            repeated = false;
            if (c == '(') {
                if (at + 1 < pattern.length() && pattern.charAt(at + 1) == '?') {
                    fault = "no argument for repetition operator: ?";
                } else {
                    groups.add(Integer.valueOf(java.length()));
                    java.append('(');
                    at++;
                    operand = false;
                }
            } else if (c == ')') {
                if (groups.isEmpty()) {
                    fault = "unexpected ): " + pattern;
                } else {
                    atom = groups.remove(groups.size() - 1).intValue();
                    java.append(')');
                    at++;
                    operand = true;
                }
            } else if (c == '|') {
                java.append('|');
                at++;
                operand = false;
            } else {
                atom = java.length();
                operand = true;
                if (c == '\\') {
                    escape(java, false);
                } else if (c == '[') {
                    characterClass();
                } else if (c == '{') {
                    java.append("\\{");
                    at++;
                } else if (c == '^') {
                    java.append(multiLine ? "(?:(?<![^\\n]))" : "(?:^)");
                    at++;
                } else if (c == '$') {
                    java.append(multiLine ? "(?:(?![^\\n]))" : "(?:\\z)");
                    at++;
                } else if (caseless && CaseFoldOrbits.orbit(pattern.codePointAt(at)).length > 1) {
                    final int point = pattern.codePointAt(at);
                    at += Character.charCount(point);
                    character(java, point, false);
                } else {
                    java.append(c);
                    at++;
                }
            }
        }
        if (fault == null && !groups.isEmpty()) {
            fault = "missing ): " + pattern;
        }
    }

    /** The index after a count written at the current brace, {@code {n}}, {@code {n,}} or {@code {n,m}}, or -1. */
    private int countEnd() {
        int i = at + 1;
        final int digits = i;
        while (i < pattern.length() && isDigit(pattern.charAt(i))) {
            i++;
        }
        if (i == digits) {
            return -1;
        }
        if (i < pattern.length() && pattern.charAt(i) == ',') {
            i++;
            while (i < pattern.length() && isDigit(pattern.charAt(i))) {
                i++;
            }
        }
        return i < pattern.length() && pattern.charAt(i) == '}' ? i + 1 : -1;
    }

    /** Whether a count stays within what the account repeats: neither bound past 1000, the first no larger. */
    private static boolean countFits(final String count) {
        final String body = count.substring(1, count.length() - 1);
        final int comma = body.indexOf(',');
        final int low = bounded(comma < 0 ? body : body.substring(0, comma));
        if (comma < 0 || comma == body.length() - 1) {
            return low <= LARGEST_REPEAT;
        }
        final int high = bounded(body.substring(comma + 1));
        return low <= LARGEST_REPEAT && high <= LARGEST_REPEAT && low <= high;
    }

    /** A count's number, or one past the largest when it is longer than any count the account takes. */
    private static int bounded(final String digits) {
        return digits.length() > 4 ? LARGEST_REPEAT + 1 : Integer.parseInt(digits);
    }

    /**
     * Reads the escape at the current backslash and writes it out.
     *
     * @param out     where the escape is written for {@code java.util.regex}
     * @param inClass whether the escape stands inside brackets
     * @return the character the escape names, {@link #A_CLASS} for a class such as {@code \d}, or
     *         {@link #NO_VALUE} when the escape is refused
     */
    private int escape(final StringBuilder out, final boolean inClass) {
        final int start = at;
        if (at + 1 >= pattern.length()) {
            fault = "trailing \\";
            return NO_VALUE;
        }
        final char d = pattern.charAt(at + 1);
        if (caseless && "dDwW".indexOf(d) >= 0) {
            out.append(foldedNamed(Character.toLowerCase(d) == 'd' ? "\\d" : "\\w", Character.isUpperCase(d),
                inClass));
            at += 2;
            return A_CLASS;
        }
        if (d == 's' || d == 'S') {
            if (d == 's') {
                out.append(inClass ? SPACE : "[" + SPACE + "]");
            } else {
                out.append("[^").append(SPACE).append(']');
            }
            at += 2;
            return A_CLASS;
        }
        if (!inClass && (d == 'b' || d == 'B')) {
            out.append(d == 'b' ? WORD_BOUNDARY : NOT_WORD_BOUNDARY);
            at += 2;
            return A_CLASS;
        }
        if ("dDwW".indexOf(d) >= 0) {
            out.append('\\').append(d);
            at += 2;
            return A_CLASS;
        }
        final int control = "afnrtv".indexOf(d);
        if (control >= 0) {
            at += 2;
            return character(out, new int[] {7, 12, 10, 13, 9, 11}[control], inClass);
        }
        if (d >= '0' && d <= '7') {
            final boolean more = at + 2 < pattern.length() && isOctal(pattern.charAt(at + 2));
            if (d != '0' && !more) {
                fault = "invalid escape sequence: \\" + d;
                return NO_VALUE;
            }
            int value = d - '0';
            at += 2;
            for (int digits = 0; digits < 2 && at < pattern.length() && isOctal(pattern.charAt(at)); digits++) {
                value = value * 8 + (pattern.charAt(at) - '0');
                at++;
            }
            return character(out, value, inClass);
        }
        if (d == 'x') {
            return hexadecimal(out, start, inClass);
        }
        if (d < 0x80 && !Character.isLetterOrDigit(d)) {
            out.append('\\').append(d);
            at += 2;
            return d;
        }
        if (d >= 0x80) {
            final int point = pattern.codePointAt(at + 1);
            at += 1 + Character.charCount(point);
            return character(out, point, inClass);
        }
        fault = "invalid escape sequence: \\" + d;
        return NO_VALUE;
    }

    /** {@code \xHH} or {@code \x{H…}}; a value past the last character is refused where its digits pass it. */
    private int hexadecimal(final StringBuilder out, final int start, final boolean inClass) {
        int i = at + 2;
        int value = 0;
        if (i < pattern.length() && pattern.charAt(i) == '{') {
            i++;
            int digits = 0;
            while (i < pattern.length() && isHex(pattern.charAt(i))) {
                value = value * 16 + Character.digit(pattern.charAt(i), 16);
                digits++;
                i++;
                if (value > LARGEST_CHARACTER) {
                    fault = "invalid escape sequence: " + pattern.substring(start, i);
                    return NO_VALUE;
                }
            }
            if (digits == 0 || i >= pattern.length() || pattern.charAt(i) != '}') {
                fault = "invalid escape sequence: " + pattern.substring(start, Math.min(i + 1, pattern.length()));
                return NO_VALUE;
            }
            at = i + 1;
            return character(out, value, inClass);
        }
        for (int digits = 0; digits < 2; digits++) {
            if (i >= pattern.length() || !isHex(pattern.charAt(i))) {
                fault = "invalid escape sequence: " + pattern.substring(start, Math.min(i + 1, pattern.length()));
                return NO_VALUE;
            }
            value = value * 16 + Character.digit(pattern.charAt(i), 16);
            i++;
        }
        at = i;
        return character(out, value, inClass);
    }

    /**
     * Writes one character the pattern names; outside brackets a caseless pattern writes the characters it folds
     * with beside it (inside them the class adds those, see {@link #characterClass}).
     *
     * @param out       where it is written
     * @param character the character
     * @param inClass   whether it stands inside brackets
     * @return the character
     */
    private int character(final StringBuilder out, final int character, final boolean inClass) {
        final int[] orbit = CaseFoldOrbits.orbit(character);
        if (inClass || !caseless || orbit.length < 2) {
            return written(out, character);
        }
        out.append('[');
        for (final int member : orbit) {
            written(out, member);
        }
        out.append(']');
        return character;
    }

    /**
     * A named class of a caseless pattern: the ASCII characters it holds, each with the characters it folds with,
     * or every other character when it is negated ({@code \W}, {@code [:^lower:]}) — the account folds the class
     * before negating it.
     *
     * @param javaClass the class as {@code java.util.regex} writes it, unnegated
     * @param negated   whether the class is negated
     * @param inClass   whether it stands inside brackets
     * @return the class written out
     */
    private static String foldedNamed(final String javaClass, final boolean negated, final boolean inClass) {
        final Pattern named = Pattern.compile("[" + javaClass + "]");
        final TreeSet<Integer> members = new TreeSet<>();
        for (int c = 0; c < 0x80; c++) {
            if (named.matcher(String.valueOf((char) c)).matches()) {
                for (final int member : CaseFoldOrbits.orbit(c)) {
                    members.add(Integer.valueOf(member));
                }
            }
        }
        final StringBuilder out = new StringBuilder();
        for (final Integer member : members) {
            written(out, member.intValue());
        }
        if (negated) {
            return "[^" + out + "]";
        }
        return inClass ? out.toString() : "[" + out + "]";
    }

    /** Writes one character by its number, which {@code java.util.regex} reads the same anywhere. */
    private static int written(final StringBuilder out, final int character) {
        out.append("\\x{").append(Integer.toHexString(character)).append('}');
        return character;
    }

    /** Reads the bracketed class at the current bracket and writes it out. */
    private void characterClass() {
        final int open = at;
        final StringBuilder written = new StringBuilder("[");
        at++;
        if (at < pattern.length() && pattern.charAt(at) == '^') {
            written.append('^');
            at++;
        }
        boolean first = true;
        while (fault == null) {
            if (at >= pattern.length()) {
                fault = "missing ]: " + pattern.substring(open);
                return;
            }
            final char c = pattern.charAt(at);
            if (c == ']' && !first) {
                at++;
                java.append(written).append(']');
                return;
            }
            if (c == '-' && !first && at + 1 < pattern.length() && pattern.charAt(at + 1) != ']') {
                // A '-' that neither opens nor closes the class, and continues no range.
                final int next = pattern.codePointAt(at + 1);
                fault = "invalid character class range: " + pattern.substring(at, at + 1 + Character.charCount(next));
                return;
            }
            first = false;
            if (c == '[' && at + 1 < pattern.length() && pattern.charAt(at + 1) == ':'
                    && pattern.indexOf(":]", at + 2) > 0) {
                final int end = pattern.indexOf(":]", at + 2) + 2;
                final String named = caseless ? foldedPosixClass(pattern.substring(at + 2, end - 2))
                    : posixClass(pattern.substring(at + 2, end - 2));
                if (named == null) {
                    fault = "invalid character class range: " + pattern.substring(at, end);
                    return;
                }
                written.append(named);
                at = end;
                continue;
            }
            final int item = at;
            final int low = member(written);
            if (low == NO_VALUE || low == A_CLASS) {
                continue;
            }
            int high = low;
            if (at + 1 < pattern.length() && pattern.charAt(at) == '-' && pattern.charAt(at + 1) != ']') {
                at++;
                written.append('-');
                high = member(written);
                if (high != NO_VALUE && high < low) {
                    fault = "invalid character class range: " + pattern.substring(item, at);
                }
            }
            if (caseless && high >= low) {
                for (final Integer folded : CaseFoldOrbits.foldedOutside(low, high)) {
                    written(written, folded.intValue());
                }
            }
        }
    }

    /** Reads one member of a class, a character or an escape, and writes it out; its number, or a marker. */
    private int member(final StringBuilder written) {
        if (pattern.charAt(at) == '\\') {
            return escape(written, true);
        }
        final int point = pattern.codePointAt(at);
        at += Character.charCount(point);
        return written(written, point);
    }

    /** A POSIX class name, with its {@code ^} when it has one, as {@code java.util.regex} writes it; or null. */
    private static String posixClass(final String name) {
        final boolean negated = name.startsWith("^");
        final String word = negated ? name.substring(1) : name;
        for (final String[] known : POSIX_CLASSES) {
            if (known[0].equals(word)) {
                if (!negated) {
                    return known[1];
                }
                return "\\w".equals(known[1]) ? "\\W" : "\\P" + known[1].substring(2);
            }
        }
        return null;
    }

    /** A POSIX class of a caseless pattern, folded as {@link #foldedNamed} folds one; or null. */
    private static String foldedPosixClass(final String name) {
        final boolean negated = name.startsWith("^");
        final String plain = posixClass(negated ? name.substring(1) : name);
        return plain == null ? null : foldedNamed(plain, negated, true);
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isOctal(final char c) {
        return c >= '0' && c <= '7';
    }

    private static boolean isHex(final char c) {
        return Character.digit(c, 16) >= 0 && c < 0x80;
    }
}
