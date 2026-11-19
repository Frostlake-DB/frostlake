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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The word an {@code ALTER <word> <name> SET | UNSET COMMENT} and a {@code COMMENT ON <word> <name>} name their kind
 * by, where it is none of a kind's (live-verified): it is read as the name of a class, and the class is missing,
 * whatever the instance's name and IF EXISTS or not. A statement cut short is a syntax error where it ends.
 */
public class CommentClassNameTest extends MeasuredCellsTestSupport {

    private static String missing(final String spelled) {
        return "REFUSED SQL compilation error: Object type or Class '" + spelled
            + "' does not exist or not authorized.";
    }

    private static String unexpected(final int position, final String token) {
        return "REFUSED SQL compilation error: | syntax error line 1 at position " + position + " unexpected '"
            + token + "'.";
    }

    @Test
    public void aWordThatIsNoKindNamesAClassThatIsMissing() {
        assertCells(new String[][] {
            {"ALTER foo bar SET COMMENT = 'c'", missing("FOO")},
            {"ALTER foo bar SET comment = 'c'", missing("FOO")},
            {"ALTER foo bar SET COMMENT = $$c$$", missing("FOO")},
            {"ALTER foo bar UNSET COMMENT", missing("FOO")},
            {"ALTER foo IF EXISTS bar SET COMMENT = 'c'", missing("FOO")},
            {"ALTER foo a.b.c SET COMMENT = 'c'", missing("FOO")},
            {"ALTER foo no_such_db_cls.no_such_schema.x SET COMMENT = 'c'", missing("FOO")},
            {"ALTER anomaly_detection bar SET COMMENT = 'c'", missing("ANOMALY_DETECTION")},
            {"ALTER \"foo\" bar SET COMMENT = 'c'", missing("\"foo\"")},
            {"ALTER \"SNAPSHOT\" x SET COMMENT = 'c'", missing("SNAPSHOT")},
            {"COMMENT ON foo x IS 'c'", missing("FOO")},
            {"COMMENT ON foo x IS $$c$$", missing("FOO")},
            {"COMMENT IF EXISTS ON foo x IS 'c'", missing("FOO")},
            {"COMMENT ON foo a.b.c IS 'c'", missing("FOO")},
            {"COMMENT ON \"foo\" x IS 'c'", missing("\"foo\"")},
            {"COMMENT ON \"MODEL\" x IS 'c'", missing("MODEL")},
        });
    }

    @Test
    public void aStatementCutShortIsRefusedWhereItEnds() {
        assertCells(new String[][] {
            {"ALTER foo", unexpected(9, "<EOF>")},
            {"ALTER foo bar", unexpected(13, "<EOF>")},
            {"ALTER foo bar SET", unexpected(17, "<EOF>")},
            {"ALTER foo bar SET COMMENT", unexpected(25, "<EOF>")},
            {"ALTER foo bar SET COMMENT 'c'", unexpected(26, "'c'")},
            {"ALTER foo bar baz SET COMMENT = 'c'", unexpected(14, "baz")},
            {"COMMENT ON foo", unexpected(14, "<EOF>")},
            {"COMMENT ON foo x", unexpected(16, "<EOF>")},
            {"COMMENT ON x IS 'c'", unexpected(13, "IS")},
            {"COMMENT ON foo x IS NULL", unexpected(20, "NULL")},
            {"COMMENT ON foo bar baz IS 'c'", unexpected(19, "baz")},
            // A reserved word names no class.
            {"ALTER select bar SET COMMENT = 'c'", unexpected(6, "select")},
            {"COMMENT ON select bar IS 'c'", unexpected(11, "select")},
        });
    }

    @Test
    public void aSnapshotAndAModelAreKindsThatAreMissing() {
        final String snapshot = answer("ALTER SNAPSHOT no_such_snapshot SET COMMENT = 'c'");
        assertFalse(!snapshot.startsWith("REFUSED SQL compilation error: | Snapshot '"
            + currentSchemaPath() + ".NO_SUCH_SNAPSHOT' does not exist or not authorized."), snapshot);
        final String model = answer("ALTER model no_such_model SET COMMENT = 'c'");
        assertFalse(!model.startsWith("REFUSED SQL compilation error: | Model '"
            + currentSchemaPath() + ".NO_SUCH_MODEL' does not exist or not authorized."), model);
        final String database = answer("ALTER SNAPSHOT no_such_db_cls.s.x SET COMMENT = 'c'");
        assertFalse(!database.startsWith("REFUSED SQL compilation error: | Database 'NO_SUCH_DB_CLS' does not exist"
            + " or not authorized."), database);
        assertFalse(answer("ALTER SNAPSHOT IF EXISTS no_such_snapshot SET COMMENT = 'c'").startsWith("REFUSED"));
        assertCells(new String[][] {
            // A snapshot takes no COMMENT ON, whatever its name and IF EXISTS or not.
            {"COMMENT ON SNAPSHOT no_such_snapshot IS 'c'", "REFUSED Unsupported feature 'TOK_SERVICE_SNAPSHOT'."},
            {"COMMENT IF EXISTS ON SNAPSHOT no_such_snapshot IS 'c'",
                "REFUSED Unsupported feature 'TOK_SERVICE_SNAPSHOT'."},
            {"COMMENT ON SNAPSHOT no_such_db_cls.s.x IS 'c'", "REFUSED Unsupported feature 'TOK_SERVICE_SNAPSHOT'."},
        });
    }

    private String currentSchemaPath() {
        return answer("SELECT CURRENT_DATABASE() || '.' || CURRENT_SCHEMA()");
    }
}
