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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stage path's segment holding a digit run no expression reads - 1e, 12e, 1.5., 0x1f, 1.2.3, or a query ID such as
 * data_8ded3c96-91eb-8e9e-5d5f-576cdeb8fc4c - is path text and names the folder it spells, while the same run after
 * the path's blank is still refused (live-verified).
 */
public class StagePathNumberWordsTest extends MeasuredCellsTestSupport {

    @TempDir
    Path local;

    @BeforeEach
    public void stageFiles() throws Exception {
        final Path file = Files.writeString(local.resolve("f.csv"), "a\n");
        engine.execute("CREATE STAGE s");
        final String[] folders = {"data_01c8-98e754-x", "1e", "0x1f", "x-1e-5", "1.5.", "12e"};
        for (final String folder : folders) {
            engine.execute("PUT 'file://" + file.toAbsolutePath() + "' '@s/" + folder + "/' AUTO_COMPRESS = FALSE");
        }
    }

    /** The names a LIST answers, sorted. */
    private String listed(final String list) {
        engine.execute(list);
        return answer("SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    @Test
    public void aDigitRunInAPathSegmentNamesTheFolderItSpells() {
        assertEquals("s/data_01c8-98e754-x/f.csv", listed("LIST @s/data_01c8-98e754-x/"));
        assertEquals("s/x-1e-5/f.csv", listed("LIST @s/x-1e-5/"));
        assertEquals("s/1e/f.csv", listed("LIST @s/1e/"));
        assertEquals("s/12e/f.csv", listed("LIST @s/12e/"));
        assertEquals("s/1.5./f.csv", listed("LIST @s/1.5./"));
        assertEquals("s/0x1f/f.csv", listed("LIST @s/0x1f/"));
    }

    @Test
    public void pathSegmentsReadDigitRunsAsText() {
        assertCells(new String[][] {
            {"LIST @s/1e5/",
                ""},
            {"LIST @s/a-1e5b/",
                ""},
            {"LIST @s/data_01c8-98e754/",
                ""},
            {"LIST @s/x-1e+5/",
                ""},
            {"LIST @s/x-12e/",
                ""},
            {"LIST @s/1e+/",
                ""},
            {"LIST @s/1.e/",
                ""},
            {"LIST @s/.5e/",
                ""},
            {"LIST @s/1.5e3/",
                ""},
            {"LIST @s/01c0a1b2-0000-98e7-0000-0001abcd1234/",
                ""},
            {"LIST @s/data_01c0a1b2-0000-98e7-0000-98e754abcd12",
                ""},
            {"LIST @s/9e9e9/",
                ""},
            {"LIST @s/a1e5/",
                ""},
            {"LIST @s/2024-01-01/",
                ""},
            {"LIST @s/v1.5.6/",
                ""},
            {"LIST @s/1.2.3/",
                ""},
            {"LIST @s/0xff/",
                ""},
            {"LIST @s/0X/",
                ""},
            {"LIST @s/a-0x/",
                ""},
            {"LIST @s/12abc/",
                ""},
            {"LIST @s/1e5abc/",
                ""},
            {"LIST @s/1e5.csv",
                ""},
            {"LIST @s/x/1e",
                ""},
            {"LIST @s/x/1e PATTERN = '.*'",
                ""},
            {"SELECT $1 FROM @s/1e/",
                "a"},
            {"SELECT t.$1 FROM @s/1e/ t",
                "a"},
            {"LIST @s/data_7311d8a3-c2ce-6f44-7ed4-d57b1e2feb89",
                ""},
            {"LIST @s/data_8ded3c96-91eb-8e9e-5d5f-576cdeb8fc4c",
                ""},
            {"LIST @s/data_8a9a021e-a648-a7dd-0683-9eb905b6e6e3",
                ""},
            {"SELECT $1 FROM @s/data_8ded3c96-91eb-8e9e-5d5f-576cdeb8fc4c/",
                ""},
        });
    }

    @Test
    public void aDigitRunAfterThePathIsStillRefused() {
        assertCells(new String[][] {
            {"SELECT 1e",
                "REFUSED SQL compilation error: | parse error line 1 at position 9 near '<EOF>'. | syntax "
                + "error line 1 at position 9 unexpected '<EOF>'."},
            {"SELECT 12e+ AS v",
                "REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'. | syntax error"
                + " line 1 at position 12 unexpected 'AS'."},
            {"SELECT 0x1f",
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x1f'."},
            {"SELECT 1.2.3",
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1.2.3'."},
            {"SELECT 1.5.",
                "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'. | syntax "
                + "error line 1 at position 11 unexpected '<EOF>'."},
            {"LIST @s/x 1e",
                "REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'."},
            {"LIST @s/x/ 0x1f",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '0x1f'."},
            {"LIST @s/x/ 1.2.3",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1.2.3'."},
            {"SELECT $1 FROM @s/1e/ 1e",
                "REFUSED SQL compilation error: | parse error line 1 at position 24 near '<EOF>'."},
            {"SELECT $1 FROM @s/1e/ t WHERE 1e = 1",
                "REFUSED SQL compilation error: | parse error line 1 at position 32 near '32'. | syntax error"
                + " line 1 at position 33 unexpected '='."},
        });
    }
}
