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

package dev.frostlake.stage;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A staged JSON file is a sequence of documents, each one record (all live-verified): a document may run across
 * lines, several may share one line, with or without a blank or a comma between them, and a comma where a record
 * should start is a record of its own, a JSON null. A top-level array is one record; under STRIP_OUTER_ARRAY every
 * top-level array is stripped into its elements, which need no comma between them, an element a comma leaves empty
 * is a null record, the file may end before the closing bracket, and nothing may follow the array. An object that
 * names a key twice refuses the file unless ALLOW_DUPLICATE = TRUE keeps the last value. INFER_SCHEMA reads the same
 * records, and reads the whole file before typing any of them.
 */
public class StagedJsonRecordSequenceTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE FILE FORMAT jf TYPE = JSON");
        engine.execute("CREATE FILE FORMAT jfs TYPE = JSON STRIP_OUTER_ARRAY = TRUE");
        engine.execute("CREATE FILE FORMAT jfd TYPE = JSON ALLOW_DUPLICATE = TRUE");
        engine.execute("CREATE FILE FORMAT jfsd TYPE = JSON ALLOW_DUPLICATE = TRUE STRIP_OUTER_ARRAY = TRUE");
    }

    /** The file's records under a format, each its row number and its JSON text, or the refusal. */
    private String records(final String name, final String content, final String format) {
        stageLocalFile("st", name + ".json", content);
        return answer("SELECT METADATA$FILE_ROW_NUMBER, TO_JSON($1) FROM @st/" + name + ".json (FILE_FORMAT => '"
            + format + "') ORDER BY 1").replaceAll("stages/[^/']+/", "stages/<id>/");
    }

    @Test
    public void aDocumentRunsAcrossLines() {
        assertEquals("1|{\"a\":1,\"b\":\"x\"} ;; 2|{\"a\":2}",
            records("pretty", "{\n \"a\": 1,\n \"b\": \"x\"\n}\n{\n \"a\": 2\n}\n", "jf"));
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2} ;; 3|{\"a\":3}",
            records("nd", "{\"a\":1}\n{\"a\":2}\n\n{\"a\":3}\n", "jf"));
        assertEquals("1|{\"a\":\"x\\ny\"} ;; 2|{\"s\":\"a}b{c\"}",
            records("braces", "{\"a\":\"x\\ny\"}\n{\"s\":\"a}b{c\"}\n", "jf"));
        assertEquals("", records("blank", "  \n\n", "jf"));
    }

    @Test
    public void documentsShareALine() {
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2}", records("spaced", "{\"a\":1} {\"a\":2}\n", "jf"));
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2}", records("adjacent", "{\"a\":1}{\"a\":2}", "jf"));
        assertEquals("1|{\"a\":1} ;; 2|[1,2] ;; 3|3 ;; 4|\"s\" ;; 5|null",
            records("mixed", "{\"a\":1} [1,2] 3 \"s\" null\n", "jf"));
        assertEquals("1|1 ;; 2|2 ;; 3|true", records("scalars", "1 2 true\n", "jf"));
        assertEquals("1|{\"a\":1} ;; 2|{\"b\":\"}\"}", records("lenient", "{a:1} {'b':'}'}\n", "jf"));
    }

    @Test
    public void aCommaPartsRecords() {
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2}", records("comma", "{\"a\":1},{\"a\":2}\n", "jf"));
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2}", records("commaline", "{\"a\":1}\n,\n{\"a\":2}\n", "jf"));
        assertEquals("1|{\"a\":1}", records("trailing", "{\"a\":1},\n", "jf"));
        assertEquals("1|{\"a\":1} ;; 2|null ;; 3|{\"a\":2}", records("twocommas", "{\"a\":1},,{\"a\":2}\n", "jf"));
        assertEquals("1|null ;; 2|{\"a\":1}", records("leading", ",{\"a\":1}\n", "jf"));
        assertEquals("NULL_VALUE", answer("SELECT TYPEOF($1) FROM @st/leading.json (FILE_FORMAT => 'jf')"
            + " WHERE METADATA$FILE_ROW_NUMBER = 1"));
    }

    @Test
    public void anArrayIsOneRecordUnlessStripped() {
        final String pretty = "[\n {\"a\": 1},\n {\"a\": 2}\n]\n";
        assertEquals("1|[{\"a\":1},{\"a\":2}]", records("prettyarr", pretty, "jf"));
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2}", records("prettyarr", pretty, "jfs"));
        assertEquals("1|[{\"a\":1},{\"a\":2}] ;; 2|[{\"a\":3}]",
            records("twoarr", "[{\"a\":1},{\"a\":2}] [{\"a\":3}]\n", "jf"));
        assertEquals("1|[1] ;; 2|[2]", records("commaarr", "[1],[2]\n", "jf"));
        assertEquals("1|[1,undefined,2]", records("hole", "[1,,2]\n", "jf"));
    }

    @Test
    public void aStrippedArraysElementsAreRecords() {
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2}", records("nocomma", "[{\"a\":1} {\"a\":2}]\n", "jfs"));
        assertEquals("1|1 ;; 2|null ;; 3|2", records("hole", "[1,,2]\n", "jfs"));
        assertEquals("1|null ;; 2|1", records("holes", "[,1,]\n", "jfs"));
        assertEquals("1|1", records("trailingcomma", "[1,]\n", "jfs"));
        assertEquals("1|{\"a\":1} ;; 2|1 ;; 3|2", records("after", "{\"a\":1}\n\n[\n1,\n2\n]\n", "jfs"));
        assertEquals("1|{\"a\":1} ;; 2|1 ;; 3|2", records("afternocomma", "{\"a\":1}\n[1 2]\n", "jfs"));
        assertEquals("1|{\"a\":1} ;; 2|{\"a\":2}", records("unclosed", "[{\"a\":1}\n{\"a\":2}\n", "jfs"));
    }

    @Test
    public void nothingFollowsAStrippedArray() {
        assertEquals("REFUSED Error parsing JSON: misplaced comma |   File 'stages/<id>/commaafter.json', line 1,"
            + " character 4 |   Row 1, column $1", records("commaafter", "[1],[2]\n", "jfs"));
        assertEquals("REFUSED Error parsing JSON: misplaced { |   File 'stages/<id>/objafter.json', line 1,"
            + " character 13 |   Row 2, column $1", records("objafter", "{\"a\":1} [1] {\"b\":2}\n", "jfs"));
        assertEquals("REFUSED Error parsing JSON: misplaced { |   File 'stages/<id>/emptyafter.json', line 1,"
            + " character 4 |   Row 0, column $1", records("emptyafter", "[] {\"a\":1}\n", "jfs"));
        assertEquals("REFUSED Error parsing JSON: misplaced [ |   File 'stages/<id>/arrafter.json', line 1,"
            + " character 19 |   Row 2, column $1", records("arrafter", "[{\"a\":1},{\"a\":2}] [{\"a\":3}]\n", "jfs"));
        assertEquals("REFUSED Error parsing JSON: garbage after outer ] |   File 'stages/<id>/garbage.json', line 1,"
            + " character 15 |   Row 3, column $1", records("garbage", "{\"a\":1} [1,2] 3 \"s\" null\n", "jfs"));
    }

    @Test
    public void aMalformedDocumentRefusesTheFile() {
        assertEquals("REFUSED Error parsing JSON: incomplete object value |   File 'stages/<id>/trunc.json', line 2,"
            + " character 7 |   Row 1 starts at line 1, column $1", records("trunc", "{\"a\":1}\n{\"a\":2", "jf"));
        assertEquals("REFUSED Error parsing JSON: incomplete object value |   File 'stages/<id>/badline.json', line 4,"
            + " character 1 |   Row 1 starts at line 1, column $1",
            records("badline", "{\"a\":1}\n{\"a\":\n{\"a\":3}\n", "jf"));
        assertEquals("REFUSED Error parsing JSON: misplaced ] |   File 'stages/<id>/stray.json', line 2,"
            + " character 8 |   Row 2, column $1", records("stray", "{\"a\":1}\n{\"a\":2}]\n", "jf"));
        assertEquals("REFUSED Error parsing JSON: misplaced { |   File 'stages/<id>/objinarr.json', line 1,"
            + " character 10 |   Row 0, column $1", records("objinarr", "[{\"a\":1} {\"a\":2}]\n", "jf"));
        assertEquals("REFUSED Error parsing JSON: misplaced { |   File 'stages/<id>/unclosedarr.json', line 2,"
            + " character 1 |   Row 0 starts at line 1, column $1",
            records("unclosedarr", "[{\"a\":1}\n{\"a\":2}\n", "jf"));
        assertEquals("REFUSED Error parsing JSON: missing comma |   File 'stages/<id>/nocommaarr.json', line 2,"
            + " character 4 |   Row 1 starts at line 1, column $1", records("nocommaarr", "{\"a\":1}\n[1 2]\n", "jf"));
        assertEquals("REFUSED Error parsing JSON: misplaced {, pos 10", answer("SELECT PARSE_JSON('[{\"a\":1} {\"a\":2}]')"));
    }

    @Test
    public void aDuplicateKeyRefusesTheFileUnlessAllowed() {
        assertEquals("REFUSED Error parsing JSON: duplicate object attribute \"a\" |   File 'stages/<id>/dup.json',"
            + " line 1, character 10 |   Row 0, column $1", records("dup", "{\"a\":1,\"a\":2}\n", "jf"));
        assertEquals("1|{\"a\":2}", records("dup", "{\"a\":1,\"a\":2}\n", "jfd"));
        final String nested = "{\"a\":1}\n{\"b\":{\"c\":1,\"c\":2}}\n{\"a\":3}\n";
        assertEquals("REFUSED Error parsing JSON: duplicate object attribute \"c\" |   File 'stages/<id>/dupnest.json',"
            + " line 2, character 15 |   Row 1 starts at line 1, column $1", records("dupnest", nested, "jf"));
        assertEquals("1|{\"a\":1} ;; 2|{\"b\":{\"c\":2}} ;; 3|{\"a\":3}", records("dupnest", nested, "jfd"));
        final String spread = "{\n \"a\":1\n}\n{\n \"b\":1,\n \"b\":2\n}\n";
        assertEquals("REFUSED Error parsing JSON: duplicate object attribute \"b\" |   File 'stages/<id>/dupspread.json',"
            + " line 6, character 4 |   Row 1 starts at line 3, column $1", records("dupspread", spread, "jf"));
        assertEquals("1|{\"a\":1} ;; 2|{\"b\":2}", records("dupspread", spread, "jfd"));
        assertEquals("REFUSED Error parsing JSON: duplicate object attribute \"a\" |   File 'stages/<id>/duparr.json',"
            + " line 1, character 11 |   Row 0, column $1", records("duparr", "[{\"a\":1,\"a\":2}]\n", "jfs"));
        assertEquals("1|{\"a\":2}", records("duparr", "[{\"a\":1,\"a\":2}]\n", "jfsd"));
    }

    @Test
    public void inferSchemaReadsTheSameRecords() {
        stageLocalFile("st", "ipretty.json", "{\n \"a\": 1,\n \"b\": \"x\"\n}\n{\n \"a\": 2\n}\n");
        stageLocalFile("st", "icomma.json", "{\"a\":1},\n{\"a\":2},\n");
        stageLocalFile("st", "ilenient.json", "{a:1} {'b':'}'}\n");
        stageLocalFile("st", "inocomma.json", "[{\"a\":1} {\"a\":2}]\n");
        stageLocalFile("st", "ihole.json", "{\"a\":1},,{\"a\":2}\n");
        stageLocalFile("st", "icommaafter.json", "[1],[2]\n");
        final String[][] cells = {
            {"ipretty", "jf", "A|NUMBER(1, 0) ;; B|TEXT"},
            {"icomma", "jf", "A|NUMBER(1, 0)"},
            {"ilenient", "jf", "A|NUMBER(1, 0) ;; B|TEXT"},
            {"inocomma", "jfs", "A|NUMBER(1, 0)"},
            {"ihole", "jf", "REFUSED Schema Inference failed: NULL_VALUE detected instead of an OBJECT. Please validate"
                + " the JSON data or use CSV file format"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[2], answer("SELECT UPPER(COLUMN_NAME), TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/"
                + cell[0] + ".json', FILE_FORMAT => '" + cell[1] + "')) ORDER BY 1"), cell[0]);
        }
        assertEquals("REFUSED Error parsing JSON: misplaced comma", answer("SELECT COLUMN_NAME FROM TABLE(INFER_SCHEMA("
            + "LOCATION => '@st/icommaafter.json', FILE_FORMAT => 'jfs'))").split(" \\| ")[0]);
    }
}
