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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where LIST, PUT, GET and REMOVE act (live-verified): a stage location in single quotes, which may hold any
 * character, and a bare path whose segments carry the marks dated and Hive-style folders are written with.
 */
public class QuotedStageLocationTest extends MeasuredCellsTestSupport {

    @TempDir
    Path local;

    /** The names a LIST answers, sorted. */
    private String listed(final String list) {
        engine.execute(list);
        return answer("SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    private String put(final String target) throws IOException {
        return "PUT 'file://" + CompressedFixtures.write(local, "h.csv", "NONE").toAbsolutePath() + "' " + target
            + " AUTO_COMPRESS = FALSE";
    }

    /** A bare path takes - = + % : and a decimal point inside its segments. */
    @Test
    public void aBarePathTakesTheMarksOfDatedAndHiveFolders() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE qsl");
        assertAnswer("h.csv|h.csv|8|8|NONE|NONE|UPLOADED|ENCRYPTED|", put("@qsl/2024-01-01/"));
        engine.execute(put("@qsl/x=1/"));
        engine.execute(put("@qsl/a+b/"));
        engine.execute(put("@qsl/p%c/"));
        engine.execute(put("@qsl/v1.0/"));
        engine.execute(put("@qsl/1.5/"));
        assertEquals("qsl/2024-01-01/h.csv", listed("LIST @qsl/2024-01-01/"));
        assertEquals("qsl/2024-01-01/h.csv", listed("LIST @qsl/2024-01-01/h.csv"));
        assertEquals("qsl/x=1/h.csv", listed("LIST @qsl/x=1/"));
        assertEquals("qsl/a+b/h.csv", listed("LIST @qsl/a+b/"));
        assertEquals("qsl/p%c/h.csv", listed("LIST @qsl/p%c/"));
        assertEquals("qsl/v1.0/h.csv", listed("LIST @qsl/v1.0/"));
        assertEquals("qsl/1.5/h.csv", listed("LIST @qsl/1.5/"));
        assertEquals("", listed("LIST @qsl/a:b/"));
        assertEquals("", listed("LIST @qsl/a@b/"));
        assertEquals("", listed("LIST @qsl/a*b?/"));
        assertEquals("", listed("LIST @qsl/a!b/"));
        assertEquals("", listed("LIST @qsl/-x-1"));
        assertAnswer("a ;; b", "SELECT $1 FROM @qsl/2024-01-01/");
        assertAnswer("a ;; b", "SELECT $1 FROM @qsl/x=1/");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ','.",
            "LIST @qsl/a,b/");
    }

    /** A quoted location reaches what the bare one does, and paths no bare one can write. */
    @Test
    public void aQuotedLocationTakesAnyCharacter() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE qsl");
        engine.execute(put("'@qsl/plain/'"));
        engine.execute(put("'@qsl/2024-01-01/'"));
        engine.execute(put("'@qsl/sp ace/'"));
        engine.execute(put("'@qsl/t~d/'"));
        assertEquals("qsl/plain/h.csv", listed("LIST '@qsl/plain/'"));
        assertEquals("qsl/2024-01-01/h.csv", listed("LIST '@qsl/2024-01-01/'"));
        assertEquals("qsl/sp ace/h.csv", listed("LIST '@qsl/sp ace/'"));
        assertEquals("qsl/plain/h.csv", listed("LIST '@QSL/plain/'"));
        assertEquals("qsl/plain/h.csv", listed("LIST '@\"QSL\"/plain/'"));
        assertEquals("qsl/plain/h.csv", listed("LIST '@test_schema.qsl/plain/'"));
        assertEquals("qsl/plain/h.csv", listed("LIST '@qsl/' PATTERN = '.*plain.*'"));
        assertEquals("qsl/2024-01-01/h.csv ;; qsl/plain/h.csv ;; qsl/sp ace/h.csv ;; qsl/t~d/h.csv",
            listed("LIST '@qsl'"));
        assertAnswer("plain/h.csv|8|DOWNLOADED|DECRYPTED|",
            "GET '@qsl/plain/h.csv' 'file://" + local.resolve("out").toAbsolutePath() + "/'");
        assertAnswer("qsl/sp ace/h.csv|removed", "REMOVE '@qsl/sp ace/'");
        assertAnswer("qsl/2024-01-01/h.csv|removed", "RM @qsl/2024-01-01/");
        assertEquals("qsl/plain/h.csv ;; qsl/t~d/h.csv", listed("LIST @qsl"));
    }

    /** A quoted text that does not start with @ is no location. */
    @Test
    public void aQuotedTextWithoutAtIsNoLocation() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE qsl");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: 'qsl/plain/'",
            "LIST 'qsl/plain/'");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: ''", "LIST ''");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: ' @qsl/plain/'",
            "LIST ' @qsl/plain/'");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: 'x'", "REMOVE 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: 'qsl/a'",
            "GET 'qsl/a' 'file://" + local.toAbsolutePath() + "/'");
        assertAnswer("REFUSED SQL compilation error: | invalid URL prefix found in: 'qsl/a'", put("'qsl/a'"));
    }
}
