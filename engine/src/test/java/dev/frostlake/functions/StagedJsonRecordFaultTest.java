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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A malformed record of a staged JSON file is refused in the words of the account's reader (live-verified):
 * {@code Error parsing JSON:} and the sentence PARSE_JSON gives the fault, then the stored file with the line and
 * the character, and the row being read with the line it starts on. The character is the place just past what was
 * read, counted in bytes, and a fault found at a line's end stands at the next line's character 0. INFER_SCHEMA
 * names the file a second time as the stage lists it; a staged-file query does not. A sign with nothing to sign is
 * a stray sign, in a file and in PARSE_JSON alike. A record that is a number or a keyword ends one character past
 * its last, so a line feed straight after it starts the next record's line.
 */
public class StagedJsonRecordFaultTest extends BaseDatabaseTest {

    /** Each file: its name, its file format, its text, then the fault's sentence, its place and the row read. */
    private static final String[][] FILES = {
            {"k1", "fj", "{\"a\":1}\n{\"a\":2.5}\n{\"a\":tru\n",
                "unknown keyword \"tru\"", "line 4, character 0", "Row 2 starts at line 2"},
            {"k2", "fj", "{\"a\":1}\n{\"a\":tru, \"b\":1}\n{\"a\":3}\n",
                "unknown keyword \"tru\"", "line 2, character 9", "Row 1 starts at line 1"},
            {"k3", "fj", "{\"a\":tru}",
                "unknown keyword \"tru\"", "line 1, character 9", "Row 0"},
            {"k4", "fj", "{\"a\":tru",
                "unknown keyword \"tru\"", "line 1, character 9", "Row 0"},
            {"k5", "fj", "{\"a\":\"abc\n{\"a\":1}\n",
                "unterminated string", "line 2, character 0", "Row 0 starts at line 1"},
            {"k6", "fj", "{\"a\" 1}\n",
                "missing colon", "line 1, character 6", "Row 0"},
            {"k7", "fj", "{\"a\":1 \"b\":2}\n",
                "missing comma", "line 1, character 8", "Row 0"},
            {"k8", "fj", "{\"a\":1}x\n{\"a\":2}\n",
                "unknown keyword \"x\"", "line 2, character 0", "Row 1 starts at line 1"},
            {"k9", "fj", "{\"a\":1\n",
                "incomplete object value", "line 2, character 1", "Row 0 starts at line 1"},
            {"k11", "fj", "{\"a\":1a}\n",
                "garbage in the numeric literal: 1a}", "line 1, character 8", "Row 0"},
            {"k12", "fj", "{\"a\":-}\n",
                "stray minus sign", "line 1, character 7", "Row 0"},
            {"k13", "fj", "{\"a\":1e+}\n",
                "missing decimal exponent digits: '1e+'", "line 1, character 9", "Row 0"},
            {"k14", "fj", "{\"a\":1,,\"b\":2}\n",
                "misplaced comma", "line 1, character 8", "Row 0"},
            {"k15", "fj", "{\"a\":1]}\n",
                "misplaced ]", "line 1, character 7", "Row 0"},
            {"k16", "fj", "{\"a\":[1,2}\n",
                "misplaced }", "line 1, character 10", "Row 0"},
            {"k17", "fj", "{\"a\":1}\n\n\n{\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 4, character 9", "Row 1 starts at line 1"},
            {"k18", "fj", "{\"a\":1} {\"a\":tru} {\"a\":2}\n",
                "unknown keyword \"tru\"", "line 1, character 17", "Row 1"},
            {"k19", "fj", "{\n \"a\": 1,\n \"b\": tru\n}\n",
                "unknown keyword \"tru\"", "line 4, character 0", "Row 0 starts at line 1"},
            {"k20", "fj", "{\"a\":#}\n",
                "invalid character outside of a string: '#'", "line 1, character 6", "Row 0"},
            {"k21", "fj", "[{\"a\":1},{\"a\":tru}]\n",
                "unknown keyword \"tru\"", "line 1, character 18", "Row 0"},
            {"k22", "fj", "{\"a\":\"unfinished",
                "unfinished string", "line 1, character 17", "Row 0"},
            {"k23", "fj", "{\"a\":1}\n}\n",
                "misplaced }", "line 2, character 1", "Row 1 starts at line 1"},
            {"k24", "fj", "{\"a\":tru\r\n{\"a\":1}\r\n",
                "unknown keyword \"tru\"", "line 1, character 9", "Row 0"},
            {"k28", "fj", "{\"a\":1}\n{\"a\":nul}\n{\"a\":3}\n",
                "unknown keyword \"nul\"", "line 2, character 9", "Row 1 starts at line 1"},
            {"k29", "fj", "{\"\u00e9\":tru}\n",
                "unknown keyword \"tru\"", "line 1, character 10", "Row 0"},
            {"k31", "fj", "{\"a\":1}\n{\"a\":2}\n{\"b\":{\"c\":1,\"d\":tru}}\n",
                "unknown keyword \"tru\"", "line 3, character 20", "Row 2 starts at line 2"},
            {"k32", "fj", "{\"a\":1}\n{\"a\":[1,\n2,\ntru]}\n",
                "unknown keyword \"tru\"", "line 4, character 4", "Row 1 starts at line 1"},
            {"m1", "fj", "{\"a\":tru \n{\"a\":1}\n",
                "unknown keyword \"tru\"", "line 1, character 9", "Row 0"},
            {"m2", "fj", "{\"a\":1a\n}\n",
                "garbage in the numeric literal: 1a\n", "line 2, character 0", "Row 0 starts at line 1"},
            {"m4", "fj", "{\"a\":1}\n1x\n",
                "garbage in the numeric literal: 1x\n", "line 3, character 0", "Row 1 starts at line 1"},
            {"m7", "fj", "{\"a\":-1}\n{\"a\":+}\n",
                "stray plus sign", "line 2, character 7", "Row 1 starts at line 1"},
            {"m8", "fj", "{\"a\":--1}\n",
                "stray minus sign", "line 1, character 7", "Row 0"},
            {"m9", "fj", "{\"a\":1}\n{\"a\":\"x\\\ry\"}\n",
                "bad escape sequence in the string", "line 2, character 10", "Row 1 starts at line 1"},
            {"m10", "fj", "{\"a\":1}\n{\"a\":\"x\ry\"}\n",
                "unterminated string", "line 2, character 8", "Row 1 starts at line 1"},
            {"m11", "fj", "{\"a\":1}\n{\"a\":0x}\n",
                "hexadecimal integer number conversion error: 0x", "line 2, character 8", "Row 1 starts at line 1"},
            {"m12", "fj", "{1:1}\n",
                "object attribute name cannot be a number", "line 1, character 2", "Row 0"},
            {"m13", "fj", "{\"a\":1}\n{\"a\":[1,2\n",
                "incomplete array value", "line 3, character 1", "Row 1 starts at line 1"},
            {"m14", "fj", "{\"a\":1}\n{\"a\":1 2}\n",
                "missing comma", "line 2, character 8", "Row 1 starts at line 1"},
            {"m15", "fj", "{\"a\":1}\n\n{\"a\" [1]}\n",
                "misplaced [", "line 3, character 6", "Row 1 starts at line 1"},
            {"m16", "fj", "{\"a\":1}\n{\"\u00e4b\":1, \u00e4}\n",
                "invalid character outside of a string: U+00E4", "line 2, character 12", "Row 1 starts at line 1"},
            {"m17", "fj", "{\"a\":1}\n{\"a\":.}\n",
                "not a number: '.'", "line 2, character 7", "Row 1 starts at line 1"},
            {"m18", "fj", "{\"a\":1}\n{\"a\":1e}\n",
                "missing decimal exponent digits: '1e'", "line 2, character 8", "Row 1 starts at line 1"},
            {"n1", "fj", "{\"a\":-NaN}\n",
                "unknown keyword \"-NaN\"", "line 1, character 10", "Row 0"},
            {"n2", "fj", "{\"a\":+.}\n",
                "not a number: '.'", "line 1, character 8", "Row 0"},
            {"n3", "fj", "{\"a\":1}\nundefined\n{\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 3, character 9", "Row 2"},
            {"n4", "fj", "true\n{\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 2, character 9", "Row 1"},
            {"n5", "fj", "1\n{\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 2, character 9", "Row 1"},
            {"n6", "fj", "\"s\"\n{\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 2, character 9", "Row 1 starts at line 1"},
            {"n7", "fj", "undefined {\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 1, character 19", "Row 1"},
            {"n8", "fj", "null\n\n{\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 3, character 9", "Row 1 starts at line 2"},
            {"n9", "fj", "1\r\n{\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 2, character 9", "Row 1 starts at line 1"},
            {"n10", "fjs", "[1,\n{\"a\":tru}]\n",
                "unknown keyword \"tru\"", "line 2, character 9", "Row 1 starts at line 1"},
            {"n11", "fj", "true {\"a\":tru}\n",
                "unknown keyword \"tru\"", "line 1, character 14", "Row 1"},
            {"s1", "fjs", "[{\"a\":1},\n{\"a\":tru},\n{\"a\":3}]\n",
                "unknown keyword \"tru\"", "line 2, character 9", "Row 1 starts at line 1"},
            {"s2", "fjs", "[{\"a\":1},{\"a\":2},{\"a\":tru\n",
                "unknown keyword \"tru\"", "line 2, character 0", "Row 2 starts at line 1"},
            {"s4", "fjs", "[{\"a\":1},{\"a\":2}]\n[{\"a\":3},{\"a\":tru}]\n",
                "misplaced [", "line 2, character 1", "Row 2 starts at line 1"},
            {"s5", "fjs", "[{\"a\":1},{\"a\":2}]x\n",
                "garbage after outer ]", "line 1, character 18", "Row 2"},
    };

    /** The files only INFER_SCHEMA is asked about. */
    private static final String[][] INFERRED_ONLY = {
            {"m5", "fj", "{\"a\":1,\"a\":2}\n",
                "duplicate object attribute \"a\"", "line 1, character 10", "Row 0"},
            {"m6", "fj", "{\"a\":1}\n{\"b\":{\"c\":1,\"c\":2}}\n",
                "duplicate object attribute \"c\"", "line 2, character 15", "Row 1 starts at line 1"},
    };

    /** The files only a staged-file query is asked about. */
    private static final String[][] QUERIED_ONLY = {

    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE FILE FORMAT fj TYPE = JSON");
        engine.execute("CREATE FILE FORMAT fjs TYPE = JSON STRIP_OUTER_ARRAY = TRUE");
    }

    @Test
    public void inferSchemaNamesTheFaultAndItsPlace() {
        assertInferred(FILES);
        assertInferred(INFERRED_ONLY);
    }

    @Test
    public void aStagedFileQueryNamesTheFaultAndItsPlace() {
        assertQueried(FILES);
        assertQueried(QUERIED_ONLY);
    }

    @Test
    public void aSignWithNothingToSignIsAStraySign() {
        final String[][] cells = {
            {"{\"a\":-}", "stray minus sign, pos 7"},
            {"-", "no number after a sign, pos 2"},
            {"-a", "unknown keyword \"-a\", pos 3"},
            {"{\"a\":-a}", "unknown keyword \"-a\", pos 8"},
            {"[-]", "stray minus sign, pos 3"},
            {"--1", "stray minus sign, pos 2"},
            {"{\"a\":+}", "stray plus sign, pos 7"},
            {"[+]", "stray plus sign, pos 3"},
            {"{\"a\":- }", "stray minus sign, pos 8"},
            {"{\"a\":-,\"b\":1}", "stray minus sign, pos 7"},
            {"[-1,-]", "stray minus sign, pos 6"},
            {"[+ a]", "unknown keyword \"a\", pos 5"},
            {"[-", "no number after a sign, pos 3"},
            {"[- ", "no number after a sign, pos 4"},
            {"[-\"a\"]", "stray minus sign, pos 3"},
        };
        for (final String[] cell : cells) {
            assertEquals("Error parsing JSON: " + cell[1], refusalOf("SELECT PARSE_JSON('" + cell[0] + "')"),
                cell[0]);
        }
    }

    private void assertInferred(final String[][] files) {
        for (final String[] file : files) {
            stageLocalFile("st/" + file[0] + "/", file[0] + ".json", file[2]);
            final String listed = file[0] + "/" + file[0] + ".json";
            assertEquals("Error parsing JSON: " + file[3] + "\n  File 'stages/<id>/" + listed + "', " + file[4]
                + "\n  " + file[5] + ", column $1\n  File '" + listed + "', " + file[4]
                + "\n  Row 0 starts at line 0, column $1",
                refusalOf("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/" + file[0]
                    + "/', FILE_FORMAT => '" + file[1] + "'))"), file[0]);
        }
    }

    private void assertQueried(final String[][] files) {
        for (final String[] file : files) {
            stageLocalFile("st/" + file[0] + "/", file[0] + ".json", file[2]);
            assertEquals("Error parsing JSON: " + file[3] + "\n  File 'stages/<id>/" + file[0] + "/" + file[0]
                + ".json', " + file[4] + "\n  " + file[5] + ", column $1",
                refusalOf("SELECT $1 FROM @st/" + file[0] + "/ (FILE_FORMAT => '" + file[1] + "')"), file[0]);
        }
    }

    /** A statement's refusal, the stage's storage folder masked: no two engines name it alike. */
    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage().replaceAll("stages/[^/']+/", "stages/<id>/");
    }
}
