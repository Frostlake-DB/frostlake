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

package dev.frostlake.dml;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A COPY reads a JSON file's records as a staged-file query does: documents across lines or several on one line, an
 * array one record unless STRIP_OUTER_ARRAY strips it, and an object naming a key twice refused unless
 * ALLOW_DUPLICATE = TRUE keeps the last value. A file that is no sequence of JSON records is one error of the file,
 * named in the reader's words: the default aborts the statement, ON_ERROR = CONTINUE reports the file LOAD_FAILED
 * with the fault's sentence, line and character, and VALIDATION_MODE = RETURN_ERRORS answers it as an error row.
 * Uses a {@code file://} stage over a temp directory.
 */
public class CopyJsonRecordSequenceTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_json_sequence_");
        Files.writeString(stageDir.resolve("pretty.json"), "{\n \"a\": 1,\n \"b\": \"x\"\n}\n{\n \"a\": 2\n}\n");
        Files.writeString(stageDir.resolve("spaced.json"), "{\"a\":3} {\"a\":4}{\"a\":5},{\"a\":6}");
        Files.writeString(stageDir.resolve("array.json"), "[\n {\"a\": 7},\n {\"a\": 8}\n]\n");
        Files.writeString(stageDir.resolve("dup.json"), "{\"a\":1,\"a\":2}\n");
        Files.writeString(stageDir.resolve("badline.json"), "{\"a\":1}\n{\"a\":\n{\"a\":3}\n");
        engine = new DatabaseEngine();
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
        engine.execute("CREATE TABLE docs (doc VARIANT)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** Each row of an answer, its cells parted by '|', the rows by " ;; ". */
    private static String rowsOf(final ResultSet result, final int width) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : result.getRows()) {
            rows.append(rows.length() > 0 ? " ;; " : "");
            for (int i = 0; i < width; i++) {
                rows.append(i > 0 ? "|" : "").append(String.valueOf(row.getValue(i)).split("\n")[0]);
            }
        }
        return rows.toString();
    }

    private String loaded() {
        return rowsOf(engine.executeQuery("SELECT TO_JSON(doc) FROM docs ORDER BY doc:a, doc:b"), 1);
    }

    /**
     * Each file's row of a COPY's answer: file, status, rows parsed, rows loaded, errors seen, first error, its line
     * and its character.
     */
    private String copied(final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            rows.append(rows.length() > 0 ? " ;; " : "");
            final List<Object> cells = row.getValues();
            final int[] shown = {0, 1, 2, 3, 5, 6, 7, 8};
            for (int i = 0; i < shown.length; i++) {
                rows.append(i > 0 ? "|" : "").append(String.valueOf(cells.get(shown[i])).split("\n")[0]);
            }
        }
        return rows.toString();
    }

    @Test
    public void documentsAcrossLinesAndOnOneLineLoadARowEach() {
        assertEquals("st/pretty.json|LOADED|2|2|0|null|null|null",
            copied("COPY INTO docs FROM @st FILES = ('pretty.json') FILE_FORMAT = (TYPE = JSON)"));
        assertEquals("st/spaced.json|LOADED|4|4|0|null|null|null",
            copied("COPY INTO docs FROM @st FILES = ('spaced.json') FILE_FORMAT = (TYPE = JSON)"));
        assertEquals("{\"a\":1,\"b\":\"x\"} ;; {\"a\":2} ;; {\"a\":3} ;; {\"a\":4} ;; {\"a\":5} ;; {\"a\":6}", loaded());
    }

    @Test
    public void anArrayIsOneRowUnlessStripped() {
        engine.execute("COPY INTO docs FROM @st FILES = ('array.json') FILE_FORMAT = (TYPE = JSON)");
        assertEquals("[{\"a\":7},{\"a\":8}]", loaded());
        engine.execute("TRUNCATE TABLE docs");
        engine.execute("COPY INTO docs FROM @st FILES = ('array.json') FILE_FORMAT = (TYPE = JSON"
            + " STRIP_OUTER_ARRAY = TRUE) FORCE = TRUE");
        assertEquals("{\"a\":7} ;; {\"a\":8}", loaded());
    }

    @Test
    public void aDuplicateKeyFailsTheFileUnlessAllowed() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @st FILES = ('dup.json') FILE_FORMAT = (TYPE = JSON)");
            }
        });
        assertEquals("Error parsing JSON: duplicate object attribute \"a\"", refused.getMessage().split("\n")[0]);
        assertEquals("st/dup.json|LOAD_FAILED|0|0|1|Error parsing JSON: duplicate object attribute \"a\"|1|10",
            copied("COPY INTO docs FROM @st FILES = ('dup.json') FILE_FORMAT = (TYPE = JSON) ON_ERROR = CONTINUE"));
        assertEquals("", loaded());
        assertEquals("st/dup.json|LOADED|1|1|0|null|null|null", copied("COPY INTO docs FROM @st FILES = ('dup.json')"
            + " FILE_FORMAT = (TYPE = JSON ALLOW_DUPLICATE = TRUE)"));
        assertEquals("{\"a\":2}", loaded());
    }

    @Test
    public void aMalformedFileIsOneErrorOfTheFile() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO docs FROM @st FILES = ('badline.json') FILE_FORMAT = (TYPE = JSON)");
            }
        });
        assertEquals("Error parsing JSON: incomplete object value", refused.getMessage().split("\n")[0]);
        assertEquals("st/badline.json|LOAD_FAILED|0|0|1|Error parsing JSON: incomplete object value|4|1",
            copied("COPY INTO docs FROM @st FILES = ('badline.json') FILE_FORMAT = (TYPE = JSON)"
                + " ON_ERROR = CONTINUE"));
    }

    @Test
    public void returnErrorsAnswersAMalformedFileAsAnErrorRow() {
        assertEquals("Error parsing JSON: incomplete object value|badline.json|4|1|null|parsing|100069|22P02|null|2|1"
            + "|null", rowsOf(engine.executeQuery("COPY INTO docs FROM @st FILES = ('badline.json')"
                + " FILE_FORMAT = (TYPE = JSON) VALIDATION_MODE = RETURN_ERRORS"), 12));
        assertEquals("", rowsOf(engine.executeQuery("COPY INTO docs FROM @st FILES = ('pretty.json')"
            + " FILE_FORMAT = (TYPE = JSON) VALIDATION_MODE = RETURN_ERRORS"), 12));
        assertEquals(2, engine.executeQuery("COPY INTO docs FROM @st FILES = ('pretty.json')"
            + " FILE_FORMAT = (TYPE = JSON) VALIDATION_MODE = RETURN_2_ROWS").getRows().size());
    }
}
