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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A relation named through {@code IDENTIFIER('…')} or a {@code TABLE('…')} literal reads as the name written in FROM
 * does: the same rows from a table, a view and a stream, and the same refusal for a name that reaches nothing - a
 * missing object, schema or database (live-verified). RESULT_SCAN and DESCRIBE RESULT of an ID that names no
 * statement are refused as not found.
 */
public class IdentifierAndTableLiteralReadTest extends MeasuredCellsTestSupport {

    /** Asserts a name reads as measured written, through IDENTIFIER() and through a TABLE() literal. */
    private void assertReadsAsWritten(final String expected, final String select, final String name,
                                      final String tail) {
        assertAnswer(expected, select + " FROM " + name + tail);
        assertAnswer(expected, select + " FROM IDENTIFIER('" + name + "')" + tail);
        assertAnswer(expected, select + " FROM TABLE('" + name + "')" + tail);
    }

    /**
     * Asserts a name that reaches nothing is refused through IDENTIFIER() and a TABLE() literal as it is written,
     * the refusal naming what it misses. Whether its hint names secondary roles depends on the session's user.
     */
    private void assertRefusedAsWritten(final String missing, final String name) {
        final String written = answer("SELECT * FROM " + name);
        assertTrue(written.startsWith("REFUSED SQL compilation error: | " + missing
            + " does not exist or not authorized."), written);
        assertAnswer(written, "SELECT * FROM IDENTIFIER('" + name + "')");
        assertAnswer(written, "SELECT * FROM TABLE('" + name + "')");
    }

    @Test
    public void aDynamicNameReadsTheRelationTheWrittenNameReads() {
        engine.execute("CREATE OR REPLACE TABLE t (id INT, s STRING)");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE VIEW v AS SELECT * FROM t");
        engine.execute("CREATE OR REPLACE STREAM st ON TABLE t");
        engine.execute("SET tn = 't'");
        assertReadsAsWritten("1|a ;; 2|b", "SELECT id, s", "t", " ORDER BY id");
        assertAnswer("1|a ;; 2|b", "SELECT id, s FROM IDENTIFIER($tn) ORDER BY id");
        assertAnswer("1|a ;; 2|b", "SELECT id, s FROM TABLE($tn) ORDER BY id");
        assertReadsAsWritten("1|a ;; 2|b", "SELECT id, s", "v", " ORDER BY id");
        assertReadsAsWritten("0", "SELECT COUNT(*)", "st", "");
    }

    @Test
    public void aDynamicNameThatReachesNothingIsRefusedAsTheWrittenName() {
        assertRefusedAsWritten("Object 'NOSUCH'", "nosuch");
        assertRefusedAsWritten("Database 'NODB'", "nodb.public.t");
        assertRefusedAsWritten("Object 'TEST_DB.TEST_SCHEMA.NOSUCH'", "test_schema.nosuch");
        assertRefusedAsWritten("Schema 'TEST_DB.NOSCHEMA'", "noschema.t");
    }

    @Test
    public void anIdThatNamesNoStatementIsNotFound() {
        assertAnswer("REFUSED Statement 01b1e2f3-0001-abcd-0000-000012345678 not found",
            "SELECT * FROM TABLE(RESULT_SCAN('01b1e2f3-0001-abcd-0000-000012345678'))");
        assertAnswer("REFUSED Statement not-a-query-id not found",
            "SELECT * FROM TABLE(RESULT_SCAN('not-a-query-id'))");
        assertAnswer("REFUSED Statement  not found", "SELECT * FROM TABLE(RESULT_SCAN(''))");
        assertAnswer("REFUSED Statement 01b1e2f3-0001-abcd-0000-000012345678 not found",
            "DESCRIBE RESULT '01b1e2f3-0001-abcd-0000-000012345678'");
    }
}
