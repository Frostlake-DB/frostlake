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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an ALTER of a class's instance, or of a kind Frostlake models no object of (a model, a snapshot, a dataset, a
 * connection, a Git repository, a semantic view, a DBT project), does beyond its comment (live-verified): it sets and
 * unsets properties, sets and unsets tags, and renames. The properties are judged while the statement compiles —
 * a name written twice, then a name that is no property of the kind, then a value the comment cannot take, then the
 * session variables — before the class or the object is looked for. A class is named by a plain or a quoted word, a
 * qualified name or an IDENTIFIER(), resolved as any schema object's name is; a built-in class's instance is missing.
 * The actions a kind does not read are syntax errors where the account places them.
 */
public class UnmodelledAlterActionTest extends MeasuredCellsTestSupport {

    private static final String REFUSED = "REFUSED SQL compilation error: | ";

    private static String missingClass(final String spelled) {
        return "REFUSED SQL compilation error: Object type or Class '" + spelled
            + "' does not exist or not authorized.";
    }

    private static String unexpected(final int position, final String token) {
        return REFUSED + "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String invalidProperty(final String name, final String kind) {
        return REFUSED + "invalid property '" + name + "' for '" + kind + "'";
    }

    private static String invalidValue(final String value, final String name) {
        return REFUSED + "invalid value [" + value + "] for parameter '" + name + "'";
    }

    private static String duplicate(final String name) {
        return REFUSED + "duplicate property '" + name + "';";
    }

    private static String missingVariable(final int position, final String name) {
        return "REFUSED SQL compilation error: error line 1 at position " + position + " | Session variable '$" + name
            + "' does not exist";
    }

    /** Asserts each statement is refused by the sentence given, which a privilege hint may follow. */
    private void assertMissing(final String[][] cells) {
        for (final String[] cell : cells) {
            final String answer = answer(cell[0]);
            assertTrue(answer.startsWith(REFUSED + cell[1] + " does not exist or not authorized."),
                cell[0] + " answered " + answer);
        }
    }

    @Test
    public void propertiesAreJudgedBeforeTheClassIsLookedFor() {
        assertCells(new String[][] {
            {"ALTER foo bar SET X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET X = 'a', Y = 2", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET X = 1 Y = 2", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET X = TRUE", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET X = $v", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET X = NULL", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET X = abc", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET X = (1)", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar UNSET X", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar UNSET X, Y", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar UNSET COMMENT, X", invalidProperty("X", "INSTANCE")},
            {"ALTER \"foo\" bar SET X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET \"COMMENT\" = 'c'", invalidProperty("\"COMMENT\"", "INSTANCE")},
            {"ALTER foo bar SET X = 1, COMMENT = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET COMMENT = 1, X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET COMMENT = 'c', X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET COMMENT = 'c' X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER foo bar SET COMMENT = $v, X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER foo IF EXISTS bar SET X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER public.foo bar SET X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER a.foo bar SET X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER a.b.foo bar SET X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER snowflake.ml.anomaly_detection bar SET X = 1", invalidProperty("X", "INSTANCE")},
            {"ALTER MODEL x SET X = 1", invalidProperty("X", "MODEL")},
            {"ALTER MODEL x UNSET X", invalidProperty("X", "MODEL")},
            {"ALTER SNAPSHOT x SET X = 1", invalidProperty("X", "SERVICE_SNAPSHOT")},
            {"ALTER CONNECTION x SET X = 1", invalidProperty("X", "CONNECTION")},
            {"ALTER CONNECTION x UNSET X", invalidProperty("X", "CONNECTION")},
            {"ALTER CONNECTION x SET COMMENT = 'c', X = 1", invalidProperty("X", "CONNECTION")},
            {"ALTER GIT REPOSITORY x SET X = 1", invalidProperty("X", "GIT_REPOSITORY")},
            {"ALTER SEMANTIC VIEW x SET X = 1", invalidProperty("X", "SEMANTIC_VIEW")},
            {"ALTER SEMANTIC VIEW x SET X = 1, COMMENT = 1", invalidProperty("X", "SEMANTIC_VIEW")},
            {"ALTER DBT PROJECT x UNSET X", invalidProperty("X", "DBT_PROJECT")},
        });
    }

    @Test
    public void aNameWrittenTwiceIsRefusedFirst() {
        assertCells(new String[][] {
            {"ALTER foo bar SET COMMENT = 'c', COMMENT = 'd'", duplicate("COMMENT")},
            {"ALTER foo bar SET COMMENT = 1, COMMENT = 'd'", duplicate("COMMENT")},
            {"ALTER foo bar SET COMMENT = $v, COMMENT = 1", duplicate("COMMENT")},
            {"ALTER foo bar SET COMMENT = 'c' COMMENT = 'd'", duplicate("COMMENT")},
            {"ALTER foo bar SET COMMENT = 'a', COMMENT = 'b', X = 1", duplicate("COMMENT")},
            {"ALTER foo bar SET X = 1, X = 2", duplicate("X")},
            {"ALTER foo bar UNSET COMMENT, COMMENT", duplicate("COMMENT")},
            {"ALTER foo bar UNSET X, X", duplicate("X")},
        });
    }

    @Test
    public void aCommentTakesNoNumberAndNoBoolean() {
        engine.execute("SET n = 5");
        assertCells(new String[][] {
            {"ALTER foo bar SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER foo bar SET comment = 1", invalidValue("1", "comment")},
            {"ALTER foo bar SET COMMENT = 1.5", invalidValue("1.5", "COMMENT")},
            {"ALTER foo bar SET COMMENT = -1", invalidValue("-1", "COMMENT")},
            {"ALTER foo bar SET COMMENT = -1.5", invalidValue("-1.5", "COMMENT")},
            {"ALTER foo bar SET COMMENT = 1e5", invalidValue("1e5", "COMMENT")},
            {"ALTER foo bar SET COMMENT = (1)", invalidValue("1", "COMMENT")},
            {"ALTER foo bar SET COMMENT = TRUE", invalidValue("TRUE", "COMMENT")},
            {"ALTER foo bar SET COMMENT = FALSE", invalidValue("FALSE", "COMMENT")},
            {"ALTER foo IF EXISTS bar SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER public.foo bar SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER MODEL x SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER SNAPSHOT x SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER CONNECTION x SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER GIT REPOSITORY x SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER SEMANTIC VIEW x SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER DBT PROJECT x SET COMMENT = 1", invalidValue("1", "COMMENT")},
            {"ALTER foo bar SET COMMENT = $v", missingVariable(28, "V")},
            {"ALTER MODEL x SET COMMENT = $v", missingVariable(28, "V")},
            {"ALTER SNAPSHOT x SET COMMENT = $v", missingVariable(31, "V")},
            {"ALTER CONNECTION x SET COMMENT = $v", missingVariable(33, "V")},
            {"ALTER foo bar SET COMMENT = $n",
                "REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'"},
            {"ALTER foo bar SET TAG t = 1", invalidValue("1", "tagValue")},
            {"ALTER DATASET x SET TAG t = 1", invalidValue("1", "tagValue")},
        });
    }

    @Test
    public void theClassIsMissedWhateverTheAction() {
        assertCells(new String[][] {
            {"ALTER foo bar RENAME TO baz", missingClass("FOO")},
            {"ALTER foo bar RENAME TO a.b.c", missingClass("FOO")},
            {"ALTER foo bar RENAME TO a.b", missingClass("FOO")},
            {"ALTER foo IF EXISTS bar RENAME TO baz", missingClass("FOO")},
            {"ALTER foo bar SET TAG t = 'v'", missingClass("FOO")},
            {"ALTER foo bar SET TAG t = 'v', u = 'w'", missingClass("FOO")},
            {"ALTER foo bar SET TAG nosuch.t = 'v'", missingClass("FOO")},
            {"ALTER foo bar UNSET TAG t", missingClass("FOO")},
            {"ALTER foo bar UNSET TAG t, u", missingClass("FOO")},
            {"ALTER foo bar SET COMMENT = NULL", missingClass("FOO")},
            {"ALTER foo bar SET COMMENT = abc", missingClass("FOO")},
            {"ALTER foo bar SET COMMENT = \"abc\"", missingClass("FOO")},
            {"ALTER foo bar SET COMMENT = a.b", missingClass("FOO")},
            {"ALTER foo bar SET COMMENT = ('a')", missingClass("FOO")},
            {"ALTER foo bar SET COMMENT = 'c';", missingClass("FOO")},
            {"ALTER foo IDENTIFIER('bar') SET COMMENT = 'c'", missingClass("FOO")},
            {"ALTER foo IDENTIFIER('a.bar') SET COMMENT = 'c'", missingClass("FOO")},
            {"ALTER IDENTIFIER('foo') bar SET COMMENT = 'c'", missingClass("FOO")},
            {"COMMENT ON IDENTIFIER('foo') bar IS 'c'", missingClass("FOO")},
            {"ALTER public.foo bar SET COMMENT = 'c'", missingClass("TEST_DB.PUBLIC.FOO")},
            {"ALTER public.\"foo\" bar SET COMMENT = 'c'", missingClass("TEST_DB.PUBLIC.\"foo\"")},
            {"ALTER TEST_DB..foo bar SET COMMENT = 'c'", missingClass("TEST_DB.PUBLIC.FOO")},
            {"ALTER TEST_DB.test_schema.foo bar SET COMMENT = 'c'", missingClass("TEST_DB.TEST_SCHEMA.FOO")},
            {"COMMENT ON public.foo bar IS 'c'", missingClass("TEST_DB.PUBLIC.FOO")},
            {"COMMENT ON public.\"foo\" bar IS 'c'", missingClass("TEST_DB.PUBLIC.\"foo\"")},
        });
    }

    @Test
    public void theObjectIsMissedOnceTheNameResolves() {
        assertMissing(new String[][] {
            {"ALTER a.foo bar SET COMMENT = 'c'", "Schema 'TEST_DB.A'"},
            {"ALTER TEST_DB.foo bar SET COMMENT = 'c'", "Schema 'TEST_DB.TEST_DB'"},
            {"COMMENT ON a.foo bar IS 'c'", "Schema 'TEST_DB.A'"},
            {"ALTER a.b.foo bar SET COMMENT = 'c'", "Database 'A'"},
            {"ALTER a..foo bar SET COMMENT = 'c'", "Database 'A'"},
            {"ALTER snowflake.ml.anomaly_detection bar SET COMMENT = 'c'", "Instance 'TEST_DB.TEST_SCHEMA.BAR'"},
            {"ALTER snowflake.ml.anomaly_detection bar RENAME TO baz", "Instance 'TEST_DB.TEST_SCHEMA.BAR'"},
            {"COMMENT ON snowflake.ml.anomaly_detection bar IS 'c'", "Instance 'TEST_DB.TEST_SCHEMA.BAR'"},
            {"ALTER MODEL x SET COMMENT = NULL", "Model 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER MODEL x RENAME TO y", "Model 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER MODEL x SET TAG t = 'v'", "Model 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER MODEL x UNSET TAG t", "Model 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER MODEL x SET DEFAULT_VERSION = 'v'", "Model 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER MODEL x UNSET DEFAULT_VERSION", "Model 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER SNAPSHOT x SET COMMENT = NULL", "Snapshot 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER DATASET x SET TAG t = 'v'", "Dataset 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER DATASET x UNSET TAG t", "Dataset 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER GIT REPOSITORY x RENAME TO y", "Git repository 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER GIT REPOSITORY x SET TAG t = 'v'", "Git repository 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER GIT REPOSITORY x SET API_INTEGRATION = i", "Git repository 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER GIT REPOSITORY x SET GIT_CREDENTIALS = s", "Git repository 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER SEMANTIC VIEW x RENAME TO y", "Semantic View 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER SEMANTIC VIEW x UNSET TAG t", "Semantic View 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER DBT PROJECT x RENAME TO y", "DBT PROJECT 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER DBT PROJECT x SET DEFAULT_TARGET = 'p'", "DBT PROJECT 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER DBT PROJECT x SET EXTERNAL_ACCESS_INTEGRATIONS = (i)", "DBT PROJECT 'TEST_DB.TEST_SCHEMA.X'"},
            {"ALTER CONNECTION nosuch SET COMMENT = 'c'", "Connection 'NOSUCH'"},
            {"ALTER CONNECTION nosuch UNSET COMMENT", "Connection 'NOSUCH'"},
            {"ALTER CONNECTION x RENAME TO y", "Connection 'X'"},
            {"COMMENT ON CONNECTION nosuch IS 'c'", "Connection 'NOSUCH'"},
        });
        assertCells(new String[][] {
            {"ALTER CONNECTION a.nosuch SET COMMENT = 'c'",
                REFUSED + "Object does not exist, or operation cannot be performed."},
            {"COMMENT ON CONNECTION a.nosuch IS 'c'",
                REFUSED + "Object does not exist, or operation cannot be performed."},
            {"COMMENT ON DATASET x IS 'c'", invalidProperty("COMMENT", "DATASET")},
            {"COMMENT IF EXISTS ON DATASET x IS 'c'", invalidProperty("COMMENT", "DATASET")},
        });
    }

    /** The comment SHOW SEMANTIC VIEWS lists for the view. */
    private String semanticViewComment() {
        return answer("SHOW SEMANTIC VIEWS LIKE 'SV1'").split("\\|", -1)[4];
    }

    @Test
    public void aSemanticViewThatIsThereTakesTheCommentAndTheTags() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("CREATE OR REPLACE SEMANTIC VIEW sv1 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (t.d AS t.b)");
        engine.execute("CREATE OR REPLACE TAG tg");
        engine.execute("ALTER SEMANTIC VIEW sv1 SET COMMENT = 'a'");
        assertEquals("a", semanticViewComment());
        engine.execute("ALTER SEMANTIC VIEW sv1 SET COMMENT = abc");
        assertEquals("ABC", semanticViewComment());
        engine.execute("ALTER SEMANTIC VIEW sv1 SET COMMENT = \"aBc\"");
        assertEquals("aBc", semanticViewComment());
        engine.execute("ALTER SEMANTIC VIEW sv1 SET TAG tg = 'v'");
        engine.execute("ALTER SEMANTIC VIEW sv1 UNSET TAG tg");
        assertEquals("aBc", semanticViewComment());
        engine.execute("ALTER SEMANTIC VIEW sv1 SET COMMENT = NULL");
        assertEquals("null", semanticViewComment());
        engine.execute("ALTER SEMANTIC VIEW sv1 SET COMMENT = 'b'");
        engine.execute("ALTER SEMANTIC VIEW sv1 UNSET COMMENT");
        assertEquals("null", semanticViewComment());
        engine.execute("ALTER SEMANTIC VIEW IF EXISTS sv1 SET COMMENT = 'x'");
        assertEquals("x", semanticViewComment());
        final String[][] cells = {
            {"ALTER SEMANTIC VIEW t1 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'SEMANTIC_VIEW'."},
        };
        assertCells(cells);
        assertMissing(new String[][] {
            {"ALTER SEMANTIC VIEW sv1 SET TAG nosuch = 'v'", "Tag 'NOSUCH'"},
        });
    }

    @Test
    public void anActionTheKindDoesNotReadIsRefusedWhereTheAccountPlacesIt() {
        assertCells(new String[][] {
            {"ALTER DATASET x SET COMMENT = 'c'", unexpected(20, "COMMENT")},
            {"ALTER DATASET x UNSET COMMENT", unexpected(22, "COMMENT")},
            {"ALTER DATASET IF EXISTS x SET COMMENT = 'c'", unexpected(30, "COMMENT")},
            {"ALTER DATASET x SET X = 1", unexpected(20, "X")},
            {"ALTER DATASET x UNSET X", unexpected(22, "X")},
            {"ALTER DATASET x RENAME TO y", unexpected(16, "RENAME")},
            {"ALTER DATASET x ADD x", unexpected(20, "x")},
            {"ALTER DATASET x", unexpected(15, "<EOF>")},
            {"COMMENT ON DATASET x", unexpected(20, "<EOF>")},
            {"ALTER SNAPSHOT x SET TAG t = 'v'", unexpected(25, "t")},
            {"ALTER CONNECTION x SET TAG t = 'v'", unexpected(27, "t")},
            {"ALTER CONNECTION x UNSET TAG t", unexpected(29, "t")},
            {"ALTER CONNECTION x ADD x", unexpected(19, "ADD")},
            {"ALTER DBT PROJECT x SET TAG t = 'v'", unexpected(28, "t")},
            {"ALTER DBT PROJECT x UNSET TAG t", unexpected(30, "t")},
            {"ALTER DBT PROJECT x ADD x", unexpected(24, "x")},
            {"ALTER MODEL x ADD x", unexpected(18, "x")},
            {"ALTER GIT REPOSITORY x ADD x", unexpected(27, "x")},
            {"ALTER foo bar ADD COLUMN x INT", unexpected(18, "COLUMN")},
            {"ALTER tabel t ADD COLUMN x INT", unexpected(18, "COLUMN")},
            {"ALTER tabel t ADD x INT", unexpected(18, "x")},
            {"ALTER foo bar ADD ROW ACCESS POLICY p ON (c)", unexpected(18, "ROW")},
            {"ALTER foo bar ADD SEARCH OPTIMIZATION", unexpected(18, "SEARCH")},
            {"ALTER foo bar ADD TAG t", unexpected(18, "TAG")},
            {"ALTER foo bar ADD", unexpected(17, "<EOF>")},
            {"ALTER foo bar DROP COLUMN x", unexpected(19, "COLUMN")},
            {"ALTER foo bar DROP ROW ACCESS POLICY p", unexpected(19, "ROW")},
            {"ALTER foo bar DROP x", unexpected(19, "x")},
            {"ALTER foo bar SUSPEND", unexpected(14, "SUSPEND")},
            {"ALTER foo bar RENAME TO", unexpected(23, "<EOF>")},
            {"ALTER foo bar RENAME TO 1", unexpected(24, "1")},
            {"ALTER foo bar SET COMMENT = 'c' TAG t = 'v'", unexpected(32, "TAG")},
            {"ALTER foo bar SET X = 1 TAG t = 'v'", unexpected(24, "TAG")},
            {"ALTER foo bar UNSET X TAG t", unexpected(22, "TAG")},
            {"ALTER foo bar UNSET COMMENT X", unexpected(28, "X")},
            {"ALTER foo bar SET COMMENT = 'a' 'b'", unexpected(32, "'b'")},
            {"ALTER foo bar SET COMMENT = 'c' X", unexpected(33, "<EOF>")},
            {"ALTER foo bar SET 'COMMENT' = 'c'", unexpected(18, "'COMMENT'")},
            {"ALTER foo bar SET COMMENT = 0x1F", unexpected(28, "0x1F")},
            {"ALTER foo bar SET COMMENT = +1", unexpected(28, "+")},
            {"ALTER foo bar SET X = 'a' || 'b'", unexpected(26, "||")},
            {"ALTER foo bar SET X = f(1)", unexpected(23, "(")},
            {"ALTER foo bar SET X =", unexpected(21, "<EOF>")},
            {"ALTER foo bar SET X", unexpected(19, "<EOF>")},
            {"ALTER foo bar UNSET", unexpected(19, "<EOF>")},
        });
    }
}
