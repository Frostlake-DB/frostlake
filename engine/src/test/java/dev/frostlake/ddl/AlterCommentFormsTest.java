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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The comment forms of ALTER VIEW's columns and ALTER FILE FORMAT's UNSET: a view column's comment is set or unset,
 * several in one statement, a missing column refused where it stands; a file format's UNSET is an unsupported
 * feature once the format is there or IF EXISTS forgives its absence.
 */
public class AlterCommentFormsTest extends BaseDatabaseTest {

    private static final String DONE = "Statement executed successfully.";

    private String refusalOf(final String sql) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return refusal.getMessage();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String listedComment(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object comment = rs.getRows().get(0).getValue(rs.getColumnIndex("comment"));
        return comment == null ? null : String.valueOf(comment);
    }

    private String viewColumnComment(final String column) {
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW v1");
        final Object comment = soleRowWhere(rs, "name", column).getValue(rs.getColumnIndex("comment"));
        return comment == null ? null : String.valueOf(comment);
    }

    @Test
    public void aViewColumnsCommentIsSetAndUnset() {
        engine.execute("CREATE TABLE t1 (a INT, b VARCHAR)");
        engine.execute("CREATE VIEW v1 AS SELECT a, b FROM t1");
        assertEquals(DONE, status("ALTER VIEW v1 ALTER COLUMN a COMMENT 'ca'"));
        assertEquals(DONE, status("ALTER VIEW v1 MODIFY b COMMENT 'cb'"));
        assertEquals("ca", viewColumnComment("A"));
        assertEquals("cb", viewColumnComment("B"));
        assertEquals(DONE, status("ALTER VIEW v1 ALTER a UNSET COMMENT"));
        assertEquals(DONE, status("ALTER VIEW v1 MODIFY COLUMN b UNSET COMMENT"));
        assertNull(viewColumnComment("A"));
        assertNull(viewColumnComment("B"));
        assertEquals(DONE, status("ALTER VIEW v1 ALTER COLUMN a COMMENT 'x', COLUMN b COMMENT 'y'"));
        assertEquals("x", viewColumnComment("A"));
        assertEquals("y", viewColumnComment("B"));
        assertEquals(DONE, status("ALTER VIEW v1 ALTER COLUMN a COMMENT 'x2', b UNSET COMMENT"));
        assertEquals("x2", viewColumnComment("A"));
        assertNull(viewColumnComment("B"));
    }

    @Test
    public void aMissingColumnIsRefusedWhereItStands() {
        engine.execute("CREATE TABLE t1 (a INT, b VARCHAR)");
        engine.execute("CREATE VIEW v1 AS SELECT a, b FROM t1");
        assertEquals("SQL compilation error: error line 1 at position 27\ninvalid identifier 'NOSUCH'",
            refusalOf("ALTER VIEW v1 ALTER COLUMN nosuch COMMENT 'x'"));
        assertEquals("SQL compilation error: error line 1 at position 21\ninvalid identifier 'NOSUCH'",
            refusalOf("ALTER VIEW v1 MODIFY nosuch COMMENT 'x'"));
        assertEquals("SQL compilation error: error line 1 at position 49\ninvalid identifier 'NOSUCH'",
            refusalOf("ALTER VIEW v1 ALTER COLUMN a COMMENT 'x', COLUMN nosuch COMMENT 'y'"));
    }

    @Test
    public void aMissingViewIsRefusedIfExistsOrNot() {
        final String missing = hinted("SQL compilation error:\nView 'TEST_DB.TEST_SCHEMA.NOSUCH_V' does not exist or "
            + "not authorized.");
        assertEquals(missing, refusalOf("ALTER VIEW nosuch_v ALTER COLUMN a COMMENT 'x'"));
        assertEquals(missing, refusalOf("ALTER VIEW IF EXISTS nosuch_v ALTER COLUMN a COMMENT 'x'"));
    }

    @Test
    public void aFileFormatsUnsetIsAnUnsupportedFeature() {
        engine.execute("CREATE FILE FORMAT ff1 TYPE = CSV COMMENT = 'c-ff'");
        assertEquals("Unsupported feature 'UNSET'.", refusalOf("ALTER FILE FORMAT ff1 UNSET COMMENT"));
        assertEquals("Unsupported feature 'UNSET'.", refusalOf("ALTER FILE FORMAT ff1 UNSET COMMENT, TYPE"));
        assertEquals("c-ff", listedComment("SHOW FILE FORMATS LIKE 'FF1'"));
        assertEquals(hinted("SQL compilation error:\nFile format 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
            + "authorized."), refusalOf("ALTER FILE FORMAT nosuch UNSET COMMENT"));
        assertEquals("Unsupported feature 'UNSET'.", refusalOf("ALTER FILE FORMAT IF EXISTS nosuch UNSET COMMENT"));
    }
}
