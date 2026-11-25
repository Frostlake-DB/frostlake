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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The marks a stage path takes unquoted, and the ones that end it (live-verified): a '#' and an '&amp;' are part of
 * the path wherever it stands; a '~' after the first character ends the stage reference and is refused where it
 * stands; a location of no known URL prefix is refused with code 1011 and state 42601.
 */
public class StagePathMarkTest extends MeasuredCellsTestSupport {

    @TempDir
    Path local;

    /** A PUT of a local file: its text as the statement writes it. */
    private String put(final Path file, final String target) {
        return "PUT 'file://" + file.toAbsolutePath() + "' " + target + " AUTO_COMPRESS = FALSE";
    }

    /** A statement refused with one syntax error at a position of line 1. */
    private void assertRefusedAt(final int position, final String token, final String sql) {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position " + position + " unexpected '"
            + token + "'.", sql);
    }

    @Test
    public void hashAndAmpersandArePartOfThePath() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE st");
        final Path file = CompressedFixtures.write(local, "h.csv", "NONE");
        engine.execute(put(file, "'@st/a#b/'"));
        engine.execute(put(file, "@st/x&y/z#/"));
        engine.execute("LIST @st/a#b/");
        assertAnswer("st/a#b/h.csv", "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("LIST @st/x&y/z#/h.csv");
        assertAnswer("st/x&y/z#/h.csv", "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("LIST @st/a#");
        assertAnswer("st/a#b/h.csv", "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("LIST @st/x&");
        assertAnswer("1", "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("2", "SELECT COUNT(*) FROM @st/a#b/");
        assertAnswer("2", "SELECT COUNT(x.$1) FROM @st/a#b/h.csv x");
        // Only inside a path: after a blank the '#' is a character no token opens with.
        assertRefusedAt(11, "#b", "LIST @st/a #b/");
        assertRefusedAt(9, "# ", "SELECT 1 # 2");
        assertRefusedAt(8, "#2", "SELECT 1#2");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 & 2");
    }

    @Test
    public void aTildeInThePathIsRefusedWhereItStands() {
        engine.execute("CREATE OR REPLACE STAGE st");
        assertRefusedAt(10, "~", "LIST @st/t~d/");
        assertRefusedAt(10, "~", "LIST @st/t~");
        assertRefusedAt(9, "~", "LIST @st/~x");
        assertRefusedAt(11, "~", "LIST @st/a/~b");
        assertRefusedAt(11, "~", "LIST @st/a#~");
        assertRefusedAt(12, "~", "LIST @st/a#b~c/");
        assertRefusedAt(10, "~", "LIST @st/a~ PATTERN = 'x'");
        assertRefusedAt(9, "~", "LIST @~/a~b");
        assertRefusedAt(20, "~", "SELECT $1 FROM @st/t~d/");
        assertRefusedAt(22, "~", "SELECT $1 FROM @st/a/b~");
    }

    @Test
    public void aLocationOfNoKnownPrefixIsCode1011() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), "reads the code the engine's own driver reports");
        try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:stage_path_mark");
             Statement statement = direct.createStatement()) {
            final String[] refused = {"LIST 'st/x/'", "LIST ' @st/plain/'", "GET 'st/x' file:///tmp/",
                "SELECT $1 FROM 'st/x'", "CREATE OR REPLACE STAGE s9 URL = 'file:///dir'"};
            for (final String sql : refused) {
                final SQLException e = assertThrows(SQLException.class, new Executable() {
                    @Override
                    public void execute() throws SQLException {
                        statement.execute(sql);
                    }
                });
                assertEquals(1011, e.getErrorCode(), sql);
                assertEquals("42601", e.getSQLState(), sql);
            }
        }
    }
}
