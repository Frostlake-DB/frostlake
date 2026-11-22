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

/**
 * The first fault among a staged JSON file's records, as the account's reader names and places it: the sentence
 * PARSE_JSON gives the fault, the file's line and the character within it, and the row being read with the line it
 * starts on. A fault found at a line's end stands at the next line's character 0, and a row starts where the row
 * before it ended, the first on line 1 (live-verified).
 */
public final class JsonRecordFault {

    private final String sentence;
    private final int line;
    private final int character;
    private final int row;
    private final int rowLine;

    JsonRecordFault(final String sentence, final int line, final int character, final int row, final int rowLine) {
        this.sentence = sentence;
        this.line = line;
        this.character = character;
        this.row = row;
        this.rowLine = rowLine;
    }

    /**
     * The first fault among a file's records.
     *
     * @param text              the file's text, past its byte order mark
     * @param stripOuterArray   whether a top-level array's elements are records of their own
     * @param duplicatesAllowed whether an object may name a key twice
     * @return the fault, or null when the account's reader finds none
     */
    public static JsonRecordFault first(final String text, final boolean stripOuterArray,
                                        final boolean duplicatesAllowed) {
        return JsonFaultReader.recordsOf(text, stripOuterArray, duplicatesAllowed).fault();
    }

    /**
     * Whether a string of a record holds a raw line break, or a backslash before a carriage return, which the
     * account's reader refuses where a lenient one reads on.
     *
     * @param record the record's text
     * @return true when the account's reader refuses the record for it
     */
    public static boolean breaksALine(final String record) {
        return JsonFaultReader.lineBreakFault(record) != null;
    }

    /**
     * The refusal's lines after its first: the file and the place, then the row and the column being read.
     *
     * @param shownName   the file, as the refusal names it
     * @param namesColumn whether the refusal names the column, $1
     * @return the lines, each led by a line break
     */
    public String placeLines(final String shownName, final boolean namesColumn) {
        return fileLine(shownName) + "\n  " + (line < 0 || line == rowLine ? "Row " + row
            : "Row " + row + " starts at line " + rowLine) + ", column " + (namesColumn ? "$1" : "");
    }

    /**
     * @param shownName the file, as the refusal names it
     * @return the line naming the file and the fault's place in it, led by a line break
     */
    public String fileLine(final String shownName) {
        return "\n  File '" + shownName + "'" + (line < 0 ? "" : ", line " + line + ", character " + character);
    }

    /** @return the fault, as PARSE_JSON words it without its place */
    public String sentence() {
        return sentence;
    }

    /** @return the file's line the fault stands on, the first being 1; -1 when the fault has no place */
    public int line() {
        return line;
    }

    /** @return the fault's character within its line, counted in bytes from 1; -1 when it has no place */
    public int character() {
        return character;
    }

    /** @return the row being read, the first being 0 */
    public int row() {
        return row;
    }

    /** @return the line the row being read starts on */
    public int rowLine() {
        return rowLine;
    }
}
