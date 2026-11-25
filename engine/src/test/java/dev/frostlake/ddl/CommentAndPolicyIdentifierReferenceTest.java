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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * COMMENT ON a warehouse, a user, a role, an alert, a Streamlit app or any other kind a COMMENT ON names by its own
 * words, an ALTER TABLE's or ALTER VIEW's ADD and DROP ROW ACCESS POLICY, and a column's SET PROJECTION POLICY all take
 * an {@code IDENTIFIER()} reference to the name, held in a string or a session variable (live-verified).
 */
public class CommentAndPolicyIdentifierReferenceTest extends BaseDatabaseTest {

    /** Each row's cells joined by commas, rows joined by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private void assertRefused(final String expected, final String sql) {
        final String message = refusal(sql);
        assertTrue(message.contains(expected), sql + " => " + message);
    }

    private String policyReferences() {
        return rows("SELECT policy_name, ref_column_name, ARRAY_TO_STRING(PARSE_JSON(ref_arg_column_names), ',')"
            + " FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.pt1',"
            + " REF_ENTITY_DOMAIN => 'table')) ORDER BY 1");
    }

    @Test
    public void accountObjectsAreCommentedThroughAReference() {
        engine.execute("SET wn = 'nosuch_wh_zq'");
        assertRefused("Warehouse 'NOSUCH_WH_ZQ' does not exist or not authorized.",
            "COMMENT ON WAREHOUSE IDENTIFIER('nosuch_wh_zq') IS 'x'");
        assertRefused("Warehouse 'NOSUCH_WH_ZQ' does not exist or not authorized.",
            "COMMENT ON WAREHOUSE IDENTIFIER($wn) IS 'x'");
        assertRefused("User 'NOSUCH_USER_ZQ' does not exist or not authorized.",
            "COMMENT ON USER IDENTIFIER('nosuch_user_zq') IS 'x'");
        assertRefused("Role 'NOSUCH_ROLE_ZQ' does not exist or not authorized.",
            "COMMENT ON ROLE IDENTIFIER('nosuch_role_zq') IS 'x'");
        assertRefused("Role '\"nosuch Role\"' does not exist or not authorized.",
            "COMMENT ON ROLE IDENTIFIER('\"nosuch Role\"') IS 'x'");
        // IF EXISTS forgives the missing object.
        engine.execute("COMMENT IF EXISTS ON WAREHOUSE IDENTIFIER('nosuch_wh_zq') IS 'x'");
        engine.execute("COMMENT IF EXISTS ON USER IDENTIFIER('nosuch_user_zq') IS 'x'");
        engine.execute("COMMENT IF EXISTS ON ROLE IDENTIFIER('nosuch_role_zq') IS 'x'");
    }

    @Test
    public void anAccountObjectsNameOfSeveralPartsIsNoObject() {
        assertRefused("Object does not exist, or operation cannot be performed.",
            "COMMENT ON WAREHOUSE IDENTIFIER('a.nosuch_wh_zq') IS 'x'");
        assertRefused("Object does not exist, or operation cannot be performed.",
            "COMMENT ON WAREHOUSE a.nosuch_wh_zq IS 'x'");
        assertRefused("Object does not exist, or operation cannot be performed.",
            "COMMENT ON USER a.nosuch_user_zq IS 'x'");
        assertRefused("Object does not exist, or operation cannot be performed.",
            "COMMENT ON ROLE IDENTIFIER('a.nosuch_role_zq') IS 'x'");
        // IF EXISTS forgives no such name.
        assertRefused("Object does not exist, or operation cannot be performed.",
            "COMMENT IF EXISTS ON WAREHOUSE IDENTIFIER('a.nosuch_wh_zq') IS 'x'");
        assertRefused("Object does not exist, or operation cannot be performed.",
            "COMMENT IF EXISTS ON ROLE a.b.nosuch_role_zq IS 'x'");
    }

    @Test
    public void aBrokenReferenceIsRefusedWhereItStopsBeingOne() {
        assertRefused("syntax error line 1 at position 36 unexpected '||'",
            "COMMENT ON WAREHOUSE IDENTIFIER('a' || 'b') IS 'x'");
        assertRefused("syntax error line 1 at position 32 unexpected '||'",
            "COMMENT ON ALERT IDENTIFIER('a' || 'b') IS 'x'");
        assertRefused("syntax error line 1 at position 55 unexpected '||'",
            "ALTER TABLE pt1 DROP ROW ACCESS POLICY IDENTIFIER('ra' || 'p1')");
        assertRefused("syntax error line 1 at position 69 unexpected '||'",
            "ALTER TABLE pt1 MODIFY COLUMN b SET PROJECTION POLICY IDENTIFIER('p' || 'p1')");
        assertRefused("syntax error line 1 at position 65 unexpected 'UPPER'",
            "ALTER TABLE pt1 MODIFY COLUMN b SET PROJECTION POLICY IDENTIFIER(UPPER('pp1'))");
        // ADD ROW ACCESS POLICY reads the word IDENTIFIER as the policy's name, refusing the parenthesis after it
        // and the first token inside.
        final String added = refusal("ALTER TABLE pt1 ADD ROW ACCESS POLICY IDENTIFIER('ra' || 'p1') ON (a)");
        assertTrue(added.contains("syntax error line 1 at position 48 unexpected '('"), added);
        assertTrue(added.contains("syntax error line 1 at position 49 unexpected ''ra''"), added);
    }

    @Test
    public void schemaObjectsAreCommentedThroughAReference() {
        engine.execute("SET an = 'al1'");
        assertRefused("Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_AL' does not exist or not authorized.",
            "COMMENT ON ALERT IDENTIFIER('nosuch_al') IS 'x'");
        assertRefused("Streamlit 'TEST_DB.TEST_SCHEMA.NOSUCH_ST' does not exist or not authorized.",
            "COMMENT ON STREAMLIT IDENTIFIER('nosuch_st') IS 'x'");
        assertRefused("Notebook 'TEST_DB.TEST_SCHEMA.NOSUCH_NB' does not exist or not authorized.",
            "COMMENT ON NOTEBOOK IDENTIFIER('nosuch_nb') IS 'x'");
        assertRefused("Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PP' does not exist or not authorized.",
            "COMMENT ON PROJECTION POLICY IDENTIFIER('nosuch_pp') IS 'x'");
        assertRefused("Model 'TEST_DB.TEST_SCHEMA.NOSUCH_M' does not exist or not authorized.",
            "COMMENT ON MODEL IDENTIFIER('nosuch_m') IS 'x'");
        engine.execute("COMMENT IF EXISTS ON ALERT IDENTIFIER('nosuch_al') IS 'x'");
        engine.execute("COMMENT IF EXISTS ON STREAMLIT IDENTIFIER('nosuch_st') IS 'x'");
        engine.execute("CREATE OR REPLACE ALERT al1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("COMMENT ON ALERT IDENTIFIER($an) IS 'alc'");
        assertEquals("alc", rows("SHOW ALERTS LIKE 'AL1' ->> SELECT \"comment\" FROM $1"));
        engine.execute("COMMENT ON ALERT IDENTIFIER('test_schema.al1') IS 'alc2'");
        assertEquals("alc2", rows("SHOW ALERTS LIKE 'AL1' ->> SELECT \"comment\" FROM $1"));
        engine.execute("DROP ALERT al1");
    }

    @Test
    public void rowAccessAndProjectionPoliciesAttachThroughAReference() {
        engine.execute("CREATE OR REPLACE TABLE pt1 (a INT, b STRING)");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap1 AS (x INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp1 AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => true)");
        engine.execute("SET rn = 'rap1'");
        engine.execute("SET pn = 'pp1'");
        engine.execute("ALTER TABLE pt1 ADD ROW ACCESS POLICY IDENTIFIER('rap1') ON (a)");
        assertEquals("RAP1, null, A", policyReferences());
        engine.execute("ALTER TABLE pt1 DROP ROW ACCESS POLICY IDENTIFIER($rn)");
        assertEquals("", policyReferences());
        engine.execute("ALTER TABLE pt1 ADD ROW ACCESS POLICY IDENTIFIER('test_schema.rap1') ON (a)");
        engine.execute("ALTER TABLE pt1 DROP ROW ACCESS POLICY IDENTIFIER('test_db.test_schema.rap1')");
        assertEquals("", policyReferences());
        assertRefused("Row access policy 'TEST_DB.TEST_SCHEMA.NOSUCH_RAP' does not exist or not authorized.",
            "ALTER TABLE pt1 ADD ROW ACCESS POLICY IDENTIFIER('nosuch_rap') ON (a)");

        engine.execute("ALTER TABLE pt1 MODIFY COLUMN b SET PROJECTION POLICY IDENTIFIER('pp1')");
        assertEquals("PP1, B, null", policyReferences());
        engine.execute("ALTER TABLE pt1 MODIFY COLUMN b UNSET PROJECTION POLICY");
        engine.execute("ALTER TABLE pt1 ALTER COLUMN b SET PROJECTION POLICY IDENTIFIER($pn)");
        engine.execute("ALTER TABLE pt1 MODIFY b SET PROJECTION POLICY IDENTIFIER($pn) FORCE");
        assertEquals("PP1, B, null", policyReferences());
        engine.execute("ALTER TABLE pt1 MODIFY COLUMN b UNSET PROJECTION POLICY");
        assertRefused("Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PP' does not exist or not authorized.",
            "ALTER TABLE pt1 MODIFY COLUMN b SET PROJECTION POLICY IDENTIFIER('nosuch_pp')");

        engine.execute("CREATE OR REPLACE VIEW pv1 AS SELECT a, b FROM pt1");
        engine.execute("ALTER VIEW pv1 ADD ROW ACCESS POLICY IDENTIFIER('rap1') ON (a)");
        engine.execute("ALTER VIEW pv1 DROP ROW ACCESS POLICY IDENTIFIER($rn)");
        engine.execute("ALTER VIEW pv1 MODIFY COLUMN b SET PROJECTION POLICY IDENTIFIER('pp1')");
        engine.execute("ALTER VIEW pv1 MODIFY COLUMN b UNSET PROJECTION POLICY");
    }
}
