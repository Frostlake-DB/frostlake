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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A staged JSON file's records are read as PARSE_JSON reads a document (live-verified): a keyword in any case, NaN
 * and an infinity, an invalid escape, an array hole, a hexadecimal number, a sign parted from its number by blanks
 * and a point with no digit after a minus. INFER_SCHEMA types NaN and an infinity as REAL and {@code undefined} as
 * a null, and passes over a record that is {@code undefined}; a record that is no object refuses it by the kind it
 * is. PARSE_JSON reads a point with no digit as zero and passes over the blanks after a sign, where a minus parted
 * from a word still leads it and a plus before a keyword is refused; CHECK_JSON refuses a point no digit stands
 * beside unless a minus leads it or an exponent follows it.
 */
public class StagedJsonLenientRecordTest extends MeasuredCellsTestSupport {

    /** Each file: its name, its file format, its text, then what INFER_SCHEMA answers and what a query reads. */
    private static final String[][] FILES = {
            {"f1", "fj", "{a:1}\n",
                "a|NUMBER(1, 0)", null},
            {"f2", "fj", "{\"a\":\"x\\qy\"}\n",
                "a|TEXT", null},
            {"f3", "fj", "TRUE\n",
                "REFUSED Schema Inference failed: BOOLEAN detected instead of an OBJECT. Please validate the JSON data or use CSV file format", null},
            {"f4", "fj", "Null\n",
                "REFUSED Schema Inference failed: NULL_VALUE detected instead of an OBJECT. Please validate the JSON data or use CSV file format", null},
            {"f5", "fj", "undefined\n",
                "", null},
            {"f6", "fj", "NaN\n",
                "REFUSED Schema Inference failed: REAL detected instead of an OBJECT. Please validate the JSON data or use CSV file format", null},
            {"f7", "fj", "{\"a\":1},{\"a\":2}\n",
                "a|NUMBER(1, 0)", null},
            {"f8", "fjs", "[{\"a\":1}{\"a\":2}]\n",
                "a|NUMBER(1, 0)", null},
            {"f11", "fj", "{\"a\":TRUE}\n",
                "a|BOOLEAN", null},
            {"f12", "fj", "{\"a\":Null}\n",
                "a|TEXT", null},
            {"f13", "fj", "{\"a\":NaN}\n",
                "a|REAL", null},
            {"f14", "fj", "{\"a\":undefined}\n",
                "a|TEXT", null},
            {"f15", "fj", "{'a':1}\n",
                "a|NUMBER(1, 0)", null},
            {"f16", "fj", "{\"a\":'x'}\n",
                "a|TEXT", null},
            {"f17", "fj", "{\"a\":1}\n{a:2,b:\"x\"}\n",
                "a|NUMBER(1, 0) ;; b|TEXT", null},
            {"f18", "fj", "{\"a\":-Infinity}\n",
                "a|REAL", null},
            {"f19", "fjs", "[{\"a\":1},{\"a\":2},]\n",
                "a|NUMBER(1, 0)", null},
            {"f20", "fj", "{\"a\":1}\n,\n{\"a\":2}\n",
                "a|NUMBER(1, 0)", null},
            {"f21", "fj", "{\"a\":1},\n{\"a\":2},\n",
                "a|NUMBER(1, 0)", null},
            {"f22", "fj", "{\"a\":\"x\\qy\",\"b\":1}\n{\"a\":\"\\z\"}\n",
                "a|TEXT ;; b|NUMBER(1, 0)", null},
            {"f23", "fjs", "[{\"a\":1} {\"a\":2}]\n",
                "a|NUMBER(1, 0)", null},
            {"f24", "fj", "{\"a\":inf}\n",
                "a|REAL", null},
            {"f25", "fj", "{\"a\":True}\n",
                "a|BOOLEAN", null},
            {"g1", "fj", "{\"a\":0x10}\n",
                "a|NUMBER(2, 0)", "{\"a\":16}|OBJECT"},
            {"g2", "fj", "{\"a\":0x1.8}\n",
                "a|REAL", "{\"a\":1.500000000000000e+00}|OBJECT"},
            {"g4", "fjs", "[{\"a\":1},undefined,{\"b\":2}]\n",
                "a|NUMBER(1, 0) ;; b|NUMBER(1, 0)", null},
            {"g5", "fj", "{\"a\":1}\nundefined\n{\"b\":2}\n",
                "a|NUMBER(1, 0) ;; b|NUMBER(1, 0)", null},
            {"g6", "fj", "nan\n",
                "REFUSED Schema Inference failed: REAL detected instead of an OBJECT. Please validate the JSON data or use CSV file format", "NaN|DOUBLE"},
            {"g7", "fj", "{\"a\":NaN,\"b\":inf}\n{\"a\":1.5,\"b\":2}\n",
                "a|TEXT ;; b|TEXT", "{\"a\":NaN,\"b\":Infinity}|OBJECT ;; {\"a\":1.5,\"b\":2}|OBJECT"},
            {"g8", "fj", "{\"a\":1}\n{\"a\":NaN}\n",
                "a|TEXT", "{\"a\":1}|OBJECT ;; {\"a\":NaN}|OBJECT"},
            {"g9", "fj", "{\"a\":\"x\"}\n{\"a\":undefined}\n",
                "a|TEXT", "{\"a\":\"x\"}|OBJECT ;; {\"a\":null}|OBJECT"},
            {"g10", "fj", "{\"a\":FALSE,\"b\":NULL}\n",
                "a|BOOLEAN ;; b|TEXT", "{\"a\":false,\"b\":null}|OBJECT"},
            {"g11", "fj", "{\"a\":\"\\q\"}\n{\"a\":5}\n",
                "a|TEXT", "{\"a\":\"q\"}|OBJECT ;; {\"a\":5}|OBJECT"},
            {"g12", "fj", "{\"a\":+NaN}\n",
                "a|REAL", "{\"a\":NaN}|OBJECT"},
            {"g14", "fj", "Infinity\n",
                "REFUSED Schema Inference failed: REAL detected instead of an OBJECT. Please validate the JSON data or use CSV file format", "Infinity|DOUBLE"},
            {"g15", "fj", "{\"a\":[1,,2]}\n",
                "a|ARRAY", "{\"a\":[1,undefined,2]}|OBJECT"},
            {"g16", "fj", "{\"a\":- 1}\n",
                "a|NUMBER(1, 0)", "{\"a\":-1}|OBJECT"},
            {"g17", "fj", "{\"a\":-.}\n",
                "a|NUMBER(1, 0)", "{\"a\":0}|OBJECT"},
            {"h6", "fj", "{\"a\":1}\n{\"a\":1.5e0}\n",
                "a|TEXT", "{\"a\":1}|OBJECT ;; {\"a\":1.500000000000000e+00}|OBJECT"},
            {"h7", "fj", "{\"a\":1.5e0}\n{\"a\":NaN}\n",
                "a|REAL", "{\"a\":1.500000000000000e+00}|OBJECT ;; {\"a\":NaN}|OBJECT"},
            {"h8", "fj", "{\"a\":NaN}\n{\"a\":1.5e0}\n",
                "a|REAL", "{\"a\":NaN}|OBJECT ;; {\"a\":1.500000000000000e+00}|OBJECT"},
            {"h9", "fj", "{\"a\":NaN}\n{\"a\":\"x\"}\n",
                "a|TEXT", "{\"a\":NaN}|OBJECT ;; {\"a\":\"x\"}|OBJECT"},
            {"h10", "fj", "{\"a\":inf}\n{\"a\":-inf}\n",
                "a|REAL", "{\"a\":Infinity}|OBJECT ;; {\"a\":-Infinity}|OBJECT"},
            {"h11", "fj", "{\"a\":NaN}\n{\"a\":1}\n",
                "a|TEXT", "{\"a\":NaN}|OBJECT ;; {\"a\":1}|OBJECT"},
            {"h12", "fj", "{\"a\":1}\nundefined\n{\"b\":2}\n{\"c\":3}\n",
                "a|NUMBER(1, 0) ;; b|NUMBER(1, 0) ;; c|NUMBER(1, 0)", null},
            {"h13", "fj", "undefined\n{\"a\":1}\n",
                "a|NUMBER(1, 0)", null},
            {"h14", "fj", "{\"a\":\"2020-01-01\"}\n{\"a\":NaN}\n",
                "a|TEXT", "{\"a\":\"2020-01-01\"}|OBJECT ;; {\"a\":NaN}|OBJECT"},
            {"h15", "fj", "{\"a\":NaN}\n{\"a\":null}\n",
                "a|REAL", "{\"a\":NaN}|OBJECT ;; {\"a\":null}|OBJECT"},
            {"h16", "fj", "{\"a\":1}\nTRUE\n",
                "REFUSED Schema Inference failed: BOOLEAN detected instead of an OBJECT. Please validate the JSON data or use CSV file format", "{\"a\":1}|OBJECT ;; true|BOOLEAN"},
            {"i1", "fj", "{\"a\":.5}\n",
                "a|NUMBER(2, 1)", "{\"a\":0.5}|OBJECT"},
            {"i2", "fj", "{\"a\":1.}\n",
                "a|NUMBER(1, 0)", "{\"a\":1}|OBJECT"},
            {"i3", "fj", "{\"a\":+1}\n",
                "a|NUMBER(1, 0)", "{\"a\":1}|OBJECT"},
            {"i4", "fj", "{\"a\":- .5}\n",
                "a|NUMBER(2, 1)", "{\"a\":-0.5}|OBJECT"},
            {"i5", "fj", "{\"a\":-.e1}\n",
                "a|REAL", "{\"a\":0.000000000000000e+00}|OBJECT"},
            {"i9", "fj", "{\"a\":\"x\\q\"}\n",
                "a|TEXT", "{\"a\":\"xq\"}|OBJECT"},
            {"i10", "fj", "- 1\n",
                "REFUSED Schema Inference failed: FIXED detected instead of an OBJECT. Please validate the JSON data or use CSV file format", "-1|INTEGER"},
            {"i11", "fj", "{\"a\":1.5}\n{\"a\":.5}\n",
                "a|NUMBER(2, 1)", "{\"a\":1.5}|OBJECT ;; {\"a\":0.5}|OBJECT"},
    };

    /** A document and what PARSE_JSON, TRY_PARSE_JSON or CHECK_JSON makes of it. */
    private static final String[][] DOCUMENTS = {
            {"SELECT PARSE_JSON('[.e]')",
                "REFUSED Error parsing JSON: missing decimal exponent digits: '.e', pos 4"},
            {"SELECT TO_JSON(PARSE_JSON('+ 1')), TYPEOF(PARSE_JSON('+ 1'))",
                "1|INTEGER"},
            {"SELECT TO_JSON(PARSE_JSON('[+.]')), TYPEOF(PARSE_JSON('[+.]'))",
                "[0]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[- .5]')), TYPEOF(PARSE_JSON('[- .5]'))",
                "[-0.5]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[+ .5]')), TYPEOF(PARSE_JSON('[+ .5]'))",
                "[0.5]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('-0')), TYPEOF(PARSE_JSON('-0'))",
                "0|INTEGER"},
            {"SELECT TO_JSON(PARSE_JSON('[-.]')), TYPEOF(PARSE_JSON('[-.]'))",
                "[0]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[-.e1]')), TYPEOF(PARSE_JSON('[-.e1]'))",
                "[0.000000000000000e+00]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[.e1]')), TYPEOF(PARSE_JSON('[.e1]'))",
                "[0.000000000000000e+00]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[-0e1]')), TYPEOF(PARSE_JSON('[-0e1]'))",
                "[-0.000000000000000e+00]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[- 1e2]')), TYPEOF(PARSE_JSON('[- 1e2]'))",
                "[-1.000000000000000e+02]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('- x')), TYPEOF(PARSE_JSON('- x'))",
                "REFUSED Error parsing JSON: unknown keyword \"-x\", pos 4"},
            {"SELECT TO_JSON(PARSE_JSON('[-\n1]')), TYPEOF(PARSE_JSON('[-\n1]'))",
                "[-1]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[1,- 2]')), TYPEOF(PARSE_JSON('[1,- 2]'))",
                "[1,-2]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('{\"a\":-  .}')), TYPEOF(PARSE_JSON('{\"a\":-  .}'))",
                "{\"a\":0}|OBJECT"},
            {"SELECT TO_JSON(PARSE_JSON('-.')), TYPEOF(PARSE_JSON('-.'))",
                "0|INTEGER"},
            {"SELECT TO_JSON(PARSE_JSON('.e1')), TYPEOF(PARSE_JSON('.e1'))",
                "0.000000000000000e+00|DOUBLE"},
            {"SELECT TO_JSON(PARSE_JSON('[1.e1]')), TYPEOF(PARSE_JSON('[1.e1]'))",
                "[1.000000000000000e+01]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[-1.]')), TYPEOF(PARSE_JSON('[-1.]'))",
                "[-1]|ARRAY"},
            {"SELECT TO_JSON(PARSE_JSON('[- .]')), TYPEOF(PARSE_JSON('[- .]'))",
                "[0]|ARRAY"},
            {"SELECT TO_JSON(TRY_PARSE_JSON('- 1'))",
                "-1"},
            {"SELECT CHECK_JSON('- 1')",
                "null"},
            {"SELECT TO_JSON(TRY_PARSE_JSON('[.]'))",
                "[0]"},
            {"SELECT CHECK_JSON('[.]')",
                "not a number: '.', pos 3"},
            {"SELECT TO_JSON(TRY_PARSE_JSON('[- 1]'))",
                "[-1]"},
            {"SELECT CHECK_JSON('[- 1]')",
                "null"},
            {"SELECT TO_JSON(PARSE_JSON('[- x]'))",
                "REFUSED Error parsing JSON: unknown keyword \"-x\", pos 5"},
            {"SELECT TO_JSON(PARSE_JSON('+ x'))",
                "REFUSED Error parsing JSON: unknown keyword \"x\", pos 4"},
            {"SELECT TO_JSON(PARSE_JSON('- a'))",
                "REFUSED Error parsing JSON: unknown keyword \"-a\", pos 4"},
            {"SELECT TO_JSON(PARSE_JSON('[+ a]'))",
                "REFUSED Error parsing JSON: unknown keyword \"a\", pos 5"},
            {"SELECT TO_JSON(PARSE_JSON('[- a]'))",
                "REFUSED Error parsing JSON: unknown keyword \"-a\", pos 5"},
            {"SELECT TO_JSON(PARSE_JSON('{\"a\":- b}'))",
                "REFUSED Error parsing JSON: unknown keyword \"-b\", pos 9"},
            {"SELECT TO_JSON(PARSE_JSON('-  x'))",
                "REFUSED Error parsing JSON: unknown keyword \"-x\", pos 5"},
            {"SELECT TO_JSON(PARSE_JSON('- -1'))",
                "REFUSED Error parsing JSON: stray minus sign, pos 3"},
            {"SELECT TO_JSON(PARSE_JSON('-x'))",
                "REFUSED Error parsing JSON: unknown keyword \"-x\", pos 3"},
            {"SELECT CHECK_JSON('.')",
                "not a number: '.', pos 2"},
            {"SELECT CHECK_JSON('-.')",
                "null"},
            {"SELECT CHECK_JSON('[-.]')",
                "null"},
            {"SELECT CHECK_JSON('.e1')",
                "null"},
            {"SELECT CHECK_JSON('{\"a\":.}')",
                "not a number: '.', pos 7"},
            {"SELECT CHECK_JSON('[.5]')",
                "null"},
            {"SELECT CHECK_JSON('[- .5]')",
                "null"},
            {"SELECT CHECK_JSON('[1.]')",
                "null"},
            {"SELECT CHECK_JSON('-.5')",
                "null"},
            {"SELECT CHECK_JSON('[+.]')",
                "not a number: '.', pos 4"},
            {"SELECT CHECK_JSON('[.e1]')",
                "null"},
            {"SELECT TO_JSON(PARSE_JSON('- true'))",
                "REFUSED Error parsing JSON: unknown keyword \"-true\", pos 7"},
            {"SELECT TO_JSON(PARSE_JSON('[- nan]'))",
                "REFUSED Error parsing JSON: unknown keyword \"-nan\", pos 7"},
            {"SELECT TO_JSON(PARSE_JSON('[- null]'))",
                "REFUSED Error parsing JSON: unknown keyword \"-null\", pos 8"},
            {"SELECT TO_JSON(PARSE_JSON('+ true'))",
                "REFUSED Error parsing JSON: invalid sign before keyword \"true\", pos 7"},
            {"SELECT TO_JSON(PARSE_JSON('- 0x10'))",
                "REFUSED Error parsing JSON: garbage in the numeric literal: -0x10 , pos 7"},
            {"SELECT TO_JSON(PARSE_JSON('[1,- .]'))",
                "[1,0]"},
            {"SELECT CHECK_JSON('- x')",
                "unknown keyword \"-x\", pos 4"},
            {"SELECT CHECK_JSON('[+.e1]')",
                "null"},
            {"SELECT CHECK_JSON('+.')",
                "not a number: '.', pos 3"},
            {"SELECT CHECK_JSON('[ . ]')",
                "not a number: '.', pos 4"},
            {"SELECT CHECK_JSON('{\"a\":+ .}')",
                "not a number: '.', pos 9"},
            {"SELECT CHECK_JSON('[1,.]')",
                "not a number: '.', pos 5"},
            {"SELECT CHECK_JSON('\".\"')",
                "null"},
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE FILE FORMAT fj TYPE = JSON");
        engine.execute("CREATE FILE FORMAT fjs TYPE = JSON STRIP_OUTER_ARRAY = TRUE");
    }

    @Test
    public void inferSchemaAndAStagedFileQueryReadWhatParseJsonReads() {
        for (final String[] file : FILES) {
            stageLocalFile("st/" + file[0] + "/", file[0] + ".json", file[2]);
            assertEquals(file[3], answer("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/" + file[0]
                + "/', FILE_FORMAT => '" + file[1] + "')) ORDER BY 1"), file[0]);
            if (file[4] != null) {
                assertEquals(file[4], answer("SELECT TO_JSON($1), TYPEOF($1) FROM @st/" + file[0]
                    + "/ (FILE_FORMAT => '" + file[1] + "')"), file[0]);
            }
        }
    }

    @Test
    public void aLooselyWrittenNumberReadsAsTheFunctionsReadIt() {
        assertCells(DOCUMENTS);
    }
}
