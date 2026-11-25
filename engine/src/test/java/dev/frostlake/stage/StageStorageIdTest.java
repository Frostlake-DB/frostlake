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

/**
 * A named stage's files are stored under {@code stages/<id>/}, where the id is the stage's own, in the shape of a
 * version-4 UUID: a refusal naming a staged file names it there, the same id for every file of the stage and a
 * different one for another stage, kept by a rename. A query's PATTERN and a named stage's LIST PATTERN match that
 * stored path; a table stage's query matches {@code tables/<number>/<path>}, its LIST the stage-relative path
 * alone (live-verified).
 */
public class StageStorageIdTest extends MeasuredCellsTestSupport {

    /** A block running the statement and answering its refusal, each stage id in it written {@code <uuid>}. */
    private static String caught(final String statement) {
        return "EXECUTE IMMEDIATE $$ BEGIN " + statement + "; RETURN 'ok'; EXCEPTION WHEN OTHER THEN RETURN "
            + "REGEXP_REPLACE(SQLERRM, '[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}', "
            + "'<uuid>'); END; $$";
    }

    @Test
    public void storedPaths() {
        engine.execute("CREATE FILE FORMAT jf TYPE = JSON");
        engine.execute("CREATE FILE FORMAT cf TYPE = CSV");
        engine.execute("CREATE STAGE s");
        engine.execute("CREATE STAGE s2");
        engine.execute("CREATE TABLE tc (a INT, b STRING)");
        stageLocalFile("s", "bad.json", "{\"a\":1}\n{\"a\":2");
        stageLocalFile("s/sub", "bad.json", "{\"a\":1}\n{\"a\":2");
        stageLocalFile("s", "good.json", "{\"a\":1}\n");
        stageLocalFile("s2", "bad.json", "{\"a\":1}\n{\"a\":2");
        stageLocalFile("%tc/sub", "c.csv", "1,x\n");
        stageLocalFile("%tc", "c.csv", "1,x\n");
        assertCells(new String[][] {
            {caught("SELECT * FROM TABLE(INFER_SCHEMA(LOCATION => '@s/bad.json', FILE_FORMAT => 'jf'))"),
                "Error parsing JSON: incomplete object value |   File 'stages/<uuid>/bad.json', "
                + "line 2, character 7 |   "
                + "Row 1 starts at line 1, column $1 |   "
                + "File 'bad.json', line 2, character 7 |   Row 0 starts at line 0, column $1"},
            {caught("SELECT * FROM TABLE(INFER_SCHEMA(LOCATION => '@s/sub/', FILE_FORMAT => 'jf'))"),
                "Error parsing JSON: incomplete object value |   File 'stages/<uuid>/sub/bad.json', "
                + "line 2, character 7 |   "
                + "Row 1 starts at line 1, column $1 |   "
                + "File 'sub/bad.json', line 2, character 7 |   Row 0 starts at line 0, column $1"},
            {caught("SELECT * FROM TABLE(INFER_SCHEMA(LOCATION => '@s', FILES => 'sub/bad.json', "
                + "FILE_FORMAT => 'jf'))"),
                "Error parsing JSON: incomplete object value |   File 'stages/<uuid>/sub/bad.json', "
                + "line 2, character 7 |   "
                + "Row 1 starts at line 1, column $1 |   "
                + "File 'sub/bad.json', line 2, character 7 |   Row 0 starts at line 0, column $1"},
            {caught("SELECT $1 FROM @s/bad.json (FILE_FORMAT => 'jf')"),
                "Error parsing JSON: incomplete object value |   File 'stages/<uuid>/bad.json', "
                + "line 2, character 7 |   "
                + "Row 1 starts at line 1, column $1"},
            {"EXECUTE IMMEDIATE $$ DECLARE a STRING; b STRING; c STRING; d STRING; BEGIN BEGIN SELECT * "
                + "FROM TABLE(INFER_SCHEMA(LOCATION => '@s/bad.json', FILE_FORMAT => 'jf')); EXCEPTION WHEN "
                + "OTHER THEN a := REGEXP_SUBSTR(SQLERRM, 'stages/([^/]+)/', 1, 1, 'e'); END; BEGIN SELECT $1 "
                + "FROM @s/sub/bad.json (FILE_FORMAT => 'jf'); EXCEPTION WHEN OTHER THEN b := "
                + "REGEXP_SUBSTR(SQLERRM, 'stages/([^/]+)/', 1, 1, 'e'); END; BEGIN SELECT $1 FROM @s2/bad.json"
                + " (FILE_FORMAT => 'jf'); EXCEPTION WHEN OTHER THEN c := REGEXP_SUBSTR(SQLERRM, "
                + "'stages/([^/]+)/', 1, 1, 'e'); END; ALTER STAGE s RENAME TO s9; BEGIN SELECT $1 FROM "
                + "@s9/bad.json (FILE_FORMAT => 'jf'); EXCEPTION WHEN OTHER THEN d := REGEXP_SUBSTR(SQLERRM, "
                + "'stages/([^/]+)/', 1, 1, 'e'); END; ALTER STAGE s9 RENAME TO s; RETURN (a = b)::STRING || "
                + "',' || (a = c)::STRING || ',' || (a = d)::STRING; END; $$",
                "true,false,true"},
            {"SELECT METADATA$FILENAME FROM @s (FILE_FORMAT => 'jf', PATTERN => "
                + "'stages/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/good[.]json')",
                "good.json"},
            {"SELECT METADATA$FILENAME FROM @s (FILE_FORMAT => 'jf', PATTERN => 'stages/s/good[.]json')",
                ""},
            {"SELECT METADATA$FILENAME FROM @s (FILE_FORMAT => 'jf', PATTERN => 'good[.]json')",
                ""},
            {"EXECUTE IMMEDIATE $$ BEGIN LIST @s PATTERN = "
                + "'stages/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/good[.]json'; "
                + "RETURN (SELECT LISTAGG(\"name\", ',') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))); END; $$",
                "s/good.json"},
            {"LIST @s PATTERN = 'stages/s/good[.]json'",
                ""},
            {"LIST @s PATTERN = 'good[.]json'",
                ""},
            {"SELECT METADATA$FILENAME FROM @%tc (FILE_FORMAT => 'cf', PATTERN => "
                + "'tables/[0-9]+/sub/c[.]csv')",
                "@TC/sub/c.csv"},
            {"SELECT METADATA$FILENAME FROM @%tc (FILE_FORMAT => 'cf', PATTERN => 'tables/tc/sub/c[.]csv')",
                ""},
            {"SELECT METADATA$FILENAME FROM @%tc (FILE_FORMAT => 'cf', PATTERN => 'sub/c[.]csv')",
                ""},
            {"EXECUTE IMMEDIATE $$ BEGIN LIST @%tc PATTERN = 'sub/c[.]csv'; RETURN (SELECT "
                + "LISTAGG(\"name\", ',') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))); END; $$",
                "sub/c.csv"},
            {"LIST @%tc PATTERN = 'tables/.*'",
                ""},
        });
    }
}
