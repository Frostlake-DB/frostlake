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

package dev.frostlake.functions.scalar.semistructured;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Names the first fault in a JSON document the way Snowflake does. It DECIDES NOTHING — the parse
 * itself is still the reader's, and this only runs once that reader has failed, to say what went
 * wrong. Frostlake used to answer "Invalid JSON: &lt;the whole document&gt;" for every fault alike.
 *
 * <p>The vocabulary, measured a fault at a time:
 *
 * <pre>
 *   unknown keyword "cdefg", pos 6                    a bare word that names no JSON value
 *   misplaced }, pos 5        misplaced ], pos 1      a closer where a value or a key belonged
 *   misplaced comma, pos 8                            two commas running, inside an object
 *   incomplete object value, pos 7                    the document ended mid-object
 *   incomplete array value, pos 5                     … or mid-array
 *   unfinished string, pos 5                          … or mid-string
 *   unterminated string, line 2, pos 0                a raw line feed inside a string (pos 3 for a carriage
 *                                                     return, which counts as a column)
 *   bad escape sequence in the string, pos 5          a backslash before a carriage return
 *   duplicate object attribute "abc", pos 14          the SAME object naming one key twice
 *   missing comma, pos 8       missing colon, pos 6   two members, or a key and its value, run on
 *   invalid character outside of a string: '#', pos 1
 *   stray minus sign, pos 2    no number after a sign, pos 2
 *   stray minus sign, pos 7    stray plus sign, pos 7 a sign with nothing to sign, {"a":-}, at what follows it
 *   missing decimal exponent digits: '1e+', pos 4
 *   garbage in the numeric literal: 1a , pos 3        the literal runs through letters, points and
 *                                                     signs (2024-01-01), then the character after
 *                                                     it, a space at the end: [1-2] reads "1-2], pos 5"
 *   garbage after valid input document                these two carry NO position
 *   more than one document in the input
 * </pre>
 *
 * <p>A STAGED FILE'S records are read by the same vocabulary ({@link #recordsOf}), placed by line and
 * character, with three sentences of their own: {@code not a number: '.'} for a point without a digit inside a
 * record, and after a stripped outer array {@code misplaced [} or {@code misplaced &#123;} for another array or
 * object, {@code misplaced comma} for a comma and {@code garbage after outer ]} for anything else. Records follow
 * one another across lines or on one line, a comma may part two of them, and a comma where a record should start
 * is a record of its own, a JSON null. Under STRIP_OUTER_ARRAY every top-level array is stripped; its records need
 * no comma between them, an element a comma leaves empty is a null record, and the file may end before its
 * closing bracket. A keyword reads in any case, and a string may be single-quoted.
 *
 * <p>THE POSITION IS 1-BASED and falls in one of two places. A fault about a TOKEN that was read
 * reports the index just PAST it — a five-character keyword is pos 6, and a document that simply ran
 * out reports the index past its last character. A fault about a CHARACTER that was not read reports
 * that character's own index. A duplicate key reports its CLOSING QUOTE, which is the same rule read
 * the first way: just past the key's content.
 *
 * <p>WHAT COMES AFTER a complete document decides between the last two sentences: another object,
 * array or string is "more than one document", and anything else is "garbage". A document that starts
 * with a comma is garbage too — an empty prefix is a valid (null) document, so the comma is already
 * after one.
 */
final class JsonFaultReader {

    /** The greatest hex integer the reader converts: 2^127 - 1. */
    private static final BigInteger LARGEST_HEX_INTEGER = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

    private final String text;
    private int at;
    /** Where the fault found stands, and the bytes of the faulty character past its first; -1 before any. */
    private int faultAt = -1;
    private int faultExtraBytes;
    /** Whether an object may name a key twice. */
    private boolean duplicatesAllowed;
    /** Whether a top-level array's elements are records of their own. */
    private boolean outerArrayStripped;
    /** Whether a staged file's records are read, which takes a keyword in any case. */
    private boolean readsRecords;
    /** The objects and arrays open around the value being read. */
    private int depth;
    /** The records read whole, and the line the next one starts on. */
    private int records;
    private int recordLine = 1;
    /** Each record's start and end in the text, or null for a record no value fills; null unless records are read. */
    private List<int[]> spans;

    private JsonFaultReader(final String document) {
        this.text = document;
    }

    /**
     * The fault of a raw line break inside a string, which Snowflake's reader refuses where every other
     * control character is kept: a line feed or a carriage return, or a backslash before a carriage return
     * (a backslash before a line feed continues the string). The lenient reader accepts all three, so a
     * document it has read is asked this before its value stands.
     *
     * @param document the document as written
     * @return the fault, or null when no string holds a raw line break
     */
    static String lineBreakFault(final String document) {
        boolean inString = false;
        for (int i = 0; i < document.length(); i++) {
            final char c = document.charAt(i);
            if (!inString) {
                inString = c == '"';
                continue;
            }
            if (c == '\\') {
                if (i + 1 < document.length() && document.charAt(i + 1) == '\r') {
                    return badEscape(document, i);
                }
                i++;
            } else if (c == '"') {
                inString = false;
            } else if (c == '\n' || c == '\r') {
                return unterminated(document, i);
            }
        }
        return null;
    }

    /** A raw line break at {@code at}: a line feed has begun the next line, a carriage return is a column. */
    private static String unterminated(final String text, final int at) {
        if (text.charAt(at) == '\n') {
            int line = 1;
            for (int i = 0; i < at; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            return "unterminated string, line " + (line + 1) + ", pos 0";
        }
        return "unterminated string, " + positionOf(text, at);
    }

    /** A backslash at {@code at} escaping a carriage return, reported just past the pair. */
    private static String badEscape(final String text, final int at) {
        return "bad escape sequence in the string, " + positionOf(text, at + 2);
    }

    /**
     * The records of a staged file's text and the first fault among them: every top-level value is a record, and
     * under STRIP_OUTER_ARRAY every element of a top-level array is.
     *
     * @param document          the file's text
     * @param stripOuterArray   whether a top-level array's elements are records of their own
     * @param duplicatesAllowed whether an object may name a key twice
     * @return the records read before any fault, and the fault where it stands
     */
    static JsonFileRecords recordsOf(final String document, final boolean stripOuterArray,
                                     final boolean duplicatesAllowed) {
        final JsonFaultReader reader = new JsonFaultReader(document);
        reader.duplicatesAllowed = duplicatesAllowed;
        reader.readsRecords = true;
        reader.spans = new ArrayList<int[]>();
        final JsonRecordFault fault = reader.readRecords(stripOuterArray);
        return new JsonFileRecords(document, reader.spans, fault);
    }

    /** The file's records, each span kept; the first fault, or null when this reader finds none. */
    private JsonRecordFault readRecords(final boolean stripOuterArray) {
        skipWhitespace();
        while (!atEnd()) {
            final char first = text.charAt(at);
            if (first == ',') {
                // A comma where a record should start parts an empty record, which is a JSON null.
                at++;
                spans.add(null);
                recordRead();
                skipWhitespace();
                continue;
            }
            final boolean stripped = stripOuterArray && first == '[';
            outerArrayStripped = stripped;
            final int start = at;
            final String fault = value();
            if (fault != null) {
                return recordFault(fault);
            }
            if (!stripped) {
                spans.add(new int[] {start, at});
                recordRead();
            }
            skipWhitespace();
            if (stripped && !atEnd()) {
                // Nothing follows a stripped outer array.
                final char after = text.charAt(at);
                if (after == ',') {
                    return recordFault("misplaced comma, " + placed(at));
                }
                return recordFault(after == '[' || after == '{' || after == ']' || after == '}'
                    ? misplaced(after) : "garbage after outer ], " + placed(at));
            }
            if (!atEnd() && text.charAt(at) == ',') {
                // A comma may part two records.
                at++;
                skipWhitespace();
            }
        }
        return null;
    }

    /** A record is read: the next one starts where it ended. */
    private void recordRead() {
        records++;
        recordLine = lineOf(at);
    }

    /** The line an offset stands on, the first being 1. */
    private int lineOf(final int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * A fault as a staged file's refusal places it: the sentence without its place, the line, and the character
     * within it - a place at a line's end is the next line's character 0.
     */
    private JsonRecordFault recordFault(final String fault) {
        final int cut = fault.lastIndexOf(", pos ");
        String sentence = cut < 0 ? fault : fault.substring(0, cut);
        if (faultAt < 0) {
            return new JsonRecordFault(sentence, -1, -1, records, recordLine);
        }
        final int line = lineOf(faultAt);
        final boolean atLineEnd = faultAt < text.length() && text.charAt(faultAt) == '\n';
        // PARSE_JSON's place names its line past the first; a string a line feed cuts names the line after.
        if (sentence.endsWith(", line " + line)) {
            sentence = sentence.substring(0, sentence.length() - (", line " + line).length());
        } else if (atLineEnd && sentence.endsWith(", line " + (line + 1))) {
            sentence = sentence.substring(0, sentence.length() - (", line " + (line + 1)).length());
        }
        if (atLineEnd) {
            return new JsonRecordFault(sentence, line + 1, 0, records, recordLine);
        }
        int bytes = 0;
        for (int i = text.lastIndexOf('\n', faultAt - 1) + 1; i < faultAt && i < text.length(); i++) {
            bytes += utf8Length(text.charAt(i));
        }
        return new JsonRecordFault(sentence, line, bytes + 1 + faultExtraBytes, records, recordLine);
    }

    /** Where a fault stands, as {@link #positionOf(String, int)} words it, the place kept for a file's refusal. */
    private String placed(final int offset) {
        return placed(offset, 0);
    }

    private String placed(final int offset, final int extraBytes) {
        faultAt = offset;
        faultExtraBytes = extraBytes;
        return positionOf(text, offset, extraBytes);
    }

    /** The fault Snowflake names in {@code document}, or null when this reader finds none. */
    static String faultOf(final String document) {
        final JsonFaultReader reader = new JsonFaultReader(document);
        reader.skipWhitespace();
        if (reader.atEnd()) {
            return null;
        }
        if (reader.text.charAt(reader.at) == ',') {
            return "garbage after valid input document";
        }
        final String fault = reader.value();
        if (fault != null) {
            return fault;
        }
        reader.skipWhitespace();
        if (reader.atEnd()) {
            return null;
        }
        final char next = reader.text.charAt(reader.at);
        return next == '{' || next == '[' || next == '"'
            ? "more than one document in the input"
            : "garbage after valid input document";
    }

    /** One value, leaving the cursor past it. */
    private String value() {
        skipWhitespace();
        if (atEnd()) {
            return null;
        }
        final char c = text.charAt(at);
        if (c == '{' || c == '[') {
            depth++;
            try {
                return c == '{' ? object() : array();
            } finally {
                depth--;
            }
        }
        if (c == '"' || c == '\'' && readsRecords) {
            return string();
        }
        if (c == '}' || c == ']') {
            return misplaced(c);
        }
        if (c == ',') {
            return "misplaced comma, " + placed(at);
        }
        if (c == '-' || c == '+' || c == '.' || c >= '0' && c <= '9') {
            return number();
        }
        if (isWordCharacter(c)) {
            return keyword();
        }
        return invalidCharacter(c);
    }

    private String object() {
        at++;
        final Set<String> keys = new HashSet<String>();
        skipWhitespace();
        if (atEnd()) {
            return incomplete("object");
        }
        if (text.charAt(at) == '}') {
            at++;
            return null;
        }
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return incomplete("object");
            }
            final char start = text.charAt(at);
            if (start == '}' || start == ']') {
                return misplaced(start);
            }
            if (start == ',') {
                return "misplaced comma, " + placed(at);
            }
            final int keyStart = at;
            final boolean quoted = start == '"' || start == '\'' && readsRecords;
            if (quoted) {
                final String unfinished = string();
                if (unfinished != null) {
                    return unfinished;
                }
            } else if (isDigit(start)) {
                // ★ A NUMBER IS NOT A NAME, and live says so in its own sentence rather than through
                // the character rule — {@code {1:1}} and {@code {1a:1}} both report it, at the digit.
                return "object attribute name cannot be a number, " + placed(at);
            } else if (isWordCharacter(start)) {
                // An UNQUOTED key is legal, so the keyword check that a value position would apply
                // must not run here: {a:1} is an object with one attribute.
                while (!atEnd() && isWordCharacter(text.charAt(at))) {
                    at++;
                }
            } else {
                return invalidCharacter(start);
            }
            final String key = quoted
                ? text.substring(keyStart + 1, at - 1) : text.substring(keyStart, at);
            if (!keys.add(key) && !duplicatesAllowed) {
                return "duplicate object attribute \"" + key + "\", " + placed(at - 1);
            }
            final String separated = afterKey();
            if (separated != null) {
                return separated;
            }
            final String member = value();
            if (member != null) {
                return member;
            }
            skipWhitespace();
            if (atEnd()) {
                return incomplete("object");
            }
            final char after = text.charAt(at);
            if (after == '}') {
                at++;
                return null;
            }
            if (after == ']') {
                return misplaced(after);
            }
            if (after != ',') {
                return "missing comma, " + placed(at);
            }
            at++;
            skipWhitespace();
            if (atEnd()) {
                return incomplete("object");
            }
            if (text.charAt(at) == ',') {
                return "misplaced comma, " + placed(at);
            }
            if (text.charAt(at) == '}') {
                at++;
                return null;
            }
        }
    }

    /** The colon between a key and its value, or the fault written in its place. */
    private String afterKey() {
        skipWhitespace();
        if (atEnd()) {
            return incomplete("object");
        }
        final char c = text.charAt(at);
        if (c == '}' || c == ']' || c == '[') {
            return misplaced(c);
        }
        if (c == ',') {
            return "misplaced comma, " + placed(at);
        }
        if (c != ':') {
            // ★ TWO CLASSES, and the split is "could this character begin a VALUE here". A sign or a
            // point could start a number, so the name simply ended and the colon is what is missing;
            // anything else is legal nowhere outside a string and is reported where it stands. The
            // whitespace case reads as the first kind because the skip above has already passed it,
            // so the report lands on the next real character rather than on the space.
            return startsAValue(c) ? "missing colon, " + placed(at)
                : invalidCharacter(c);
        }
        at++;
        skipWhitespace();
        if (atEnd()) {
            return incomplete("object");
        }
        return text.charAt(at) == '}' ? misplaced('}') : null;
    }

    private String array() {
        final boolean elementsAreRecords = outerArrayStripped && depth == 1;
        at++;
        skipWhitespace();
        if (atEnd()) {
            // A stripped outer array's records may end with the file, its bracket never closed.
            return elementsAreRecords ? null : incomplete("array");
        }
        if (text.charAt(at) == ']') {
            at++;
            return null;
        }
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return elementsAreRecords ? null : incomplete("array");
            }
            if (text.charAt(at) == '}') {
                return misplaced('}');
            }
            if (text.charAt(at) == ']') {
                at++;
                return null;
            }
            if (text.charAt(at) != ',') {
                // A comma straight away is an element live reads as `undefined`, not a fault.
                final int start = at;
                final String element = value();
                if (element != null) {
                    return element;
                }
                if (elementsAreRecords) {
                    spans.add(new int[] {start, at});
                    recordRead();
                }
            } else if (elementsAreRecords) {
                // The outer array's empty element is a record of its own, a JSON null.
                spans.add(null);
                recordRead();
            }
            skipWhitespace();
            if (atEnd()) {
                return elementsAreRecords ? null : incomplete("array");
            }
            final char after = text.charAt(at);
            if (after == ']') {
                at++;
                return null;
            }
            if (after == '}') {
                return misplaced(after);
            }
            if (after != ',' && elementsAreRecords) {
                // The outer array's records need no comma between them.
                continue;
            }
            if (after == '{') {
                // An object straight after an element is misplaced; any other value lacks its comma.
                return misplaced(after);
            }
            if (after != ',') {
                return "missing comma, " + placed(at);
            }
            at++;
        }
    }

    /** A string, closed by the quote that opens it. */
    private String string() {
        final char quote = text.charAt(at);
        at++;
        while (!atEnd()) {
            final char c = text.charAt(at);
            if (c == '\\') {
                if (at + 1 < text.length() && text.charAt(at + 1) == '\r') {
                    faultAt = at + 2;
                    return badEscape(text, at);
                }
                at += 2;
                continue;
            }
            if (c == '\n' || c == '\r') {
                faultAt = at;
                return unterminated(text, at);
            }
            at++;
            if (c == quote) {
                return null;
            }
        }
        return "unfinished string, " + placed(at);
    }

    private String number() {
        final int start = at;
        final char sign = text.charAt(at);
        if (sign == '-' || sign == '+') {
            at++;
            // Blanks may part a sign from what it signs.
            final int afterSign = at;
            skipWhitespace();
            if (atEnd()) {
                return "no number after a sign, " + placed(at);
            }
            final char next = text.charAt(at);
            if (next == '-' || next == '+') {
                return "stray minus sign, " + placed(at);
            }
            if (!isDigit(next) && next != '.' && isWordCharacter(next)) {
                // A word straight after its sign is one word with it; past a blank it is a word of its own.
                if (at == afterSign) {
                    at = start;
                }
                return keyword();
            }
            if (!isDigit(next) && next != '.') {
                // A sign with nothing to sign, reported at what stands after it.
                return "stray " + (sign == '-' ? "minus" : "plus") + " sign, " + placed(at);
            }
        }
        final String hex = hexNumber(start);
        if (hex != null) {
            return hex.isEmpty() ? null : hex;
        }
        skipDigits();
        if (!atEnd() && text.charAt(at) == '.') {
            at++;
            skipDigits();
            if (at == start + 1 && readsRecords && depth > 0) {
                // A staged file's reader takes no point without a digit, where a document's reads zero.
                return "not a number: '.', " + placed(at);
            }
        }
        if (!atEnd() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
            at++;
            if (!atEnd() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                at++;
            }
            if (atEnd() || !isDigit(text.charAt(at))) {
                return "missing decimal exponent digits: '" + text.substring(start, at)
                    + "', " + placed(at);
            }
            skipDigits();
        }
        if (!atEnd() && continuesANumber(text.charAt(at))) {
            while (!atEnd() && continuesANumber(text.charAt(at))) {
                at++;
            }
            return "garbage in the numeric literal: " + text.substring(start, at)
                + (atEnd() ? " " : String.valueOf(text.charAt(at))) + ", " + placed(at);
        }
        return null;
    }

    /**
     * A HEXADECIMAL number, which the reader converts where it can: {@code 0x} with no digits and an
     * integer past the signed 128-bit range are its own sentences, an exponent with no digits is the
     * hexadecimal twin of the decimal one, and a token running into other characters is garbage like any
     * other literal. Null when no hex token stands here, and the empty string when one stands and reads
     * (live-verified).
     *
     * @param start where the literal began, a leading sign included
     */
    private String hexNumber(final int start) {
        final int digitsAt = text.charAt(start) == '+' ? start + 1 : start;
        if (digitsAt + 1 >= text.length() || text.charAt(digitsAt) != '0'
                || text.charAt(digitsAt + 1) != 'x' && text.charAt(digitsAt + 1) != 'X') {
            return null;
        }
        at = digitsAt + 2;
        final int integerAt = at;
        while (!atEnd() && isHexDigit(text.charAt(at))) {
            at++;
        }
        final int integerEnd = at;
        boolean floating = false;
        if (!atEnd() && text.charAt(at) == '.') {
            floating = true;
            at++;
            while (!atEnd() && isHexDigit(text.charAt(at))) {
                at++;
            }
        }
        if (!atEnd() && (text.charAt(at) == 'p' || text.charAt(at) == 'P')) {
            floating = true;
            at++;
            if (!atEnd() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                at++;
            }
            if (atEnd() || !isDigit(text.charAt(at))) {
                return "missing hexadecimal exponent digits: '" + text.substring(start, at)
                    + "', " + placed(at);
            }
            skipDigits();
        }
        if (!atEnd() && continuesANumber(text.charAt(at))) {
            while (!atEnd() && continuesANumber(text.charAt(at))) {
                at++;
            }
            return "garbage in the numeric literal: " + text.substring(start, at)
                + (atEnd() ? " " : String.valueOf(text.charAt(at))) + ", " + placed(at);
        }
        if (!floating && integerEnd == integerAt) {
            return "hexadecimal integer number conversion error: " + text.substring(start, at)
                + ", " + placed(at);
        }
        if (!floating && new BigInteger(text.substring(integerAt, integerEnd), 16)
                .compareTo(LARGEST_HEX_INTEGER) > 0) {
            return "hexadecimal integer number conversion error: " + text.substring(start, at)
                + ", " + placed(at);
        }
        return "";
    }

    private static boolean isHexDigit(final char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    /** A bare word: one of the four JSON keywords, or a fault naming it. */
    private String keyword() {
        final int start = at;
        // ★ A VALUE-position word keeps a LEADING SIGN — {@code -a} is one word, reported whole —
        // where a NAME may not contain one at all. The two positions read different character sets,
        // which is why the name's predicate is not reused here.
        if (!atEnd() && (text.charAt(at) == '-' || text.charAt(at) == '+')) {
            at++;
        }
        while (!atEnd() && isWordCharacter(text.charAt(at))) {
            at++;
        }
        final String word = text.substring(start, at);
        if (word.equals("true") || word.equals("false") || word.equals("null")
                || word.equals("undefined") || readsRecords && isLenientKeyword(word)) {
            return null;
        }
        return "unknown keyword \"" + word + "\", " + placed(at);
    }

    /** A keyword in any case, or a number no JSON spells: what a file's reader takes where a document's is strict. */
    private static boolean isLenientKeyword(final String word) {
        final String lower = word.toLowerCase(Locale.ROOT);
        final String unsigned = lower.startsWith("-") || lower.startsWith("+") ? lower.substring(1) : lower;
        return lower.equals("true") || lower.equals("false") || lower.equals("null") || lower.equals("undefined")
            || unsigned.equals("nan") || unsigned.equals("inf") || unsigned.equals("infinity");
    }

    private String incomplete(final String container) {
        return "incomplete " + container + " value, " + placed(at);
    }

    /** Whether a character carries a numeric literal on: a letter, a digit, a point or a sign. */
    private static boolean continuesANumber(final char c) {
        return isWordCharacter(c) || c == '.' || c == '-' || c == '+';
    }

    /** Whether a character could begin a JSON VALUE here, which is what chooses the sentence. */
    private static boolean startsAValue(final char c) {
        return c == '-' || c == '+' || c == '.' || isDigit(c) || isWordCharacter(c) || c == '"';
    }

    /**
     * The invalid-character sentence, naming a NON-ASCII character by its CODE POINT.
     *
     * <p>Live prints {@code U+00E4} rather than the letter itself — measured, and the only place in
     * this reader's vocabulary where a character is not shown as written.
     */
    private String invalidCharacter(final char c) {
        // ★ The CODE POINT form carries no quotes, where a character shown as written does — so the
        // quoting is part of the ASCII spelling and not of the sentence.
        final String shown;
        if (c >= 0x80) {
            shown = String.format(Locale.ROOT, "U+%04X", (int) c);
            return "invalid character outside of a string: " + shown + ", "
                + placed(at, utf8Length(c) - 1);
        } else if (c == '\\') {
            // The one character the message ESCAPES rather than showing as written (live-measured).
            shown = "\\\\";
        } else {
            shown = String.valueOf(c);
        }
        // ★ Reported at the character's LAST byte. For ASCII that is simply where it stands, but a
        // multi-byte one is reported at its END: measured, U+00E4 at byte 2 reads pos 3 and U+20AC
        // at byte 2 reads pos 4 — the width of the character, not its start, decides the number.
        return "invalid character outside of a string: '" + shown + "', "
            + placed(at, utf8Length(c) - 1);
    }

    /** How many bytes a character occupies once the document is UTF-8, which is how live counts. */
    private static int utf8Length(final char c) {
        if (c < 0x80) {
            return 1;
        }
        return c < 0x800 ? 2 : 3;
    }

    private String misplaced(final char closer) {
        return "misplaced " + closer + ", " + placed(at);
    }

    private void skipWhitespace() {
        while (!atEnd() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private void skipDigits() {
        while (!atEnd() && isDigit(text.charAt(at))) {
            at++;
        }
    }

    private boolean atEnd() {
        return at >= text.length();
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * The characters an UNQUOTED name is made of — ASCII letters, digits and the underscore, and
     * nothing else. {@code -} and {@code +} are deliberately absent: they end a name, and what
     * follows is the missing-colon report rather than more name. A NON-ASCII letter is absent for the
     * same reason, and live names it by CODE POINT when it reports it.
     */
    private static boolean isWordCharacter(final char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || isDigit(c) || c == '_';
    }

    /**
     * Where a fault stands, as Snowflake words it: a 1-based column WITHIN ITS LINE, with a {@code line N,}
     * clause only once past the first line. The offset is into the ORIGINAL document, leading whitespace and
     * all — live counts what was written, so {@code PARSE_JSON('  cdefg')} is pos 8 where the same document
     * without the spaces is pos 6.
     */
    private static String positionOf(final String text, final int at) {
        return positionOf(text, at, 0);
    }

    /**
     * @param extraBytes bytes of the offending character itself to count, so a multi-byte one is
     *                   reported at its LAST byte rather than its first
     */
    private static String positionOf(final String text, final int at, final int extraBytes) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < at && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        // The column counts BYTES, not characters — every ASCII document reads the same either way,
        // and a document carrying a multi-byte character does not.
        int bytes = 0;
        for (int i = lineStart; i < at && i < text.length(); i++) {
            bytes += utf8Length(text.charAt(i));
        }
        final String column = "pos " + (bytes + 1 + extraBytes);
        return line > 1 ? "line " + line + ", " + column : column;
    }
}
