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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * COMMENT ON for the kinds whose comment the account sets as their own ALTER ... SET COMMENT does, and the ALTER
 * that sets or unsets the comment of a model, a Git repository, a semantic view or a DBT project: a missing object
 * is refused in its kind's sentence, IF EXISTS forgives it, a missing database or schema is refused, and an object
 * that is there shows the comment.
 */
public class CommentOnKindsTest extends BaseDatabaseTest {

    private static final String DONE = "Statement executed successfully.";

    /** The kinds COMMENT ON names, each with the noun its missing-object sentence opens with. */
    private static final String[][] KINDS = {
        {"ALERT", "Alert"}, {"NETWORK RULE", "Network rule"}, {"STREAMLIT", "Streamlit"}, {"NOTEBOOK", "Notebook"},
        {"AUTHENTICATION POLICY", "Authentication policy"}, {"PASSWORD POLICY", "Password policy"},
        {"SESSION POLICY", "Session policy"}, {"PACKAGES POLICY", "Packages policy"},
        {"AGGREGATION POLICY", "Aggregation policy"}, {"PROJECTION POLICY", "Projection policy"},
        {"JOIN POLICY", "Join policy"}, {"PRIVACY POLICY", "Privacy policy"},
        {"IMAGE REPOSITORY", "Image repository"}, {"SERVICE", "Service"}, {"MODEL", "Model"},
        {"GIT REPOSITORY", "Git repository"}, {"SEMANTIC VIEW", "Semantic View"},
        {"CORTEX SEARCH SERVICE", "Cortex Search Service"}, {"DBT PROJECT", "DBT PROJECT"},
    };

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

    /** The comment SHOW lists for the one object a listing finds. */
    private String listedComment(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object comment = rs.getRows().get(0).getValue(rs.getColumnIndex("comment"));
        return comment == null ? null : String.valueOf(comment);
    }

    private String missing(final String noun, final String name) {
        return hinted("SQL compilation error:\n" + noun + " '" + name + "' does not exist or not authorized.");
    }

    @Test
    public void aMissingObjectIsRefusedInItsKindsSentenceAndIfExistsForgivesIt() {
        for (final String[] kind : KINDS) {
            assertEquals(missing(kind[1], "TEST_DB.TEST_SCHEMA.NOSUCH"),
                refusalOf("COMMENT ON " + kind[0] + " nosuch IS 'c'"), kind[0]);
            if ("SEMANTIC VIEW".equals(kind[0]) && isLiveSnowflake()) {
                // The account fails this one with an internal error, a defect Frostlake does not mirror: it forgives
                // the absence as it does for every other kind.
                continue;
            }
            assertEquals(DONE, status("COMMENT IF EXISTS ON " + kind[0] + " nosuch IS 'c'"), kind[0]);
        }
    }

    @Test
    public void aMissingDatabaseOrSchemaIsRefusedFirst() {
        assertEquals(missing("Database", "NOSUCH_DB"),
            refusalOf("COMMENT ON PASSWORD POLICY nosuch_db.nosuch_s.nosuch IS 'c'"));
        assertEquals(missing("Schema", "TEST_DB.NOSUCH_S"),
            refusalOf("COMMENT ON PASSWORD POLICY nosuch_s.nosuch IS 'c'"));
        assertEquals(missing("Schema", "TEST_DB.NOSUCH_S"), refusalOf("COMMENT ON MODEL nosuch_s.nosuch IS 'c'"));
    }

    @Test
    public void anObjectThatIsThereShowsTheComment() {
        engine.execute("CREATE NETWORK RULE nr1 MODE = INGRESS TYPE = IPV4 VALUE_LIST = ('10.0.0.1')");
        assertEquals(DONE, status("COMMENT ON NETWORK RULE nr1 IS 'c-nr'"));
        assertEquals("c-nr", listedComment("SHOW NETWORK RULES LIKE 'NR1'"));
        engine.execute("CREATE PASSWORD POLICY pp1");
        assertEquals(DONE, status("COMMENT ON PASSWORD POLICY pp1 IS 'c-pp'"));
        assertEquals("c-pp", listedComment("SHOW PASSWORD POLICIES LIKE 'PP1'"));
        engine.execute("CREATE SESSION POLICY sp1");
        assertEquals(DONE, status("COMMENT ON SESSION POLICY sp1 IS 'c-sp'"));
        assertEquals("c-sp", listedComment("SHOW SESSION POLICIES LIKE 'SP1'"));
        engine.execute("CREATE AUTHENTICATION POLICY ap1");
        assertEquals(DONE, status("COMMENT ON AUTHENTICATION POLICY ap1 IS 'c-ap'"));
        assertEquals("c-ap", listedComment("SHOW AUTHENTICATION POLICIES LIKE 'AP1'"));
        engine.execute("CREATE PACKAGES POLICY pk1 LANGUAGE PYTHON");
        assertEquals(DONE, status("COMMENT ON PACKAGES POLICY pk1 IS 'c-pk'"));
        assertEquals("c-pk", listedComment("SHOW PACKAGES POLICIES LIKE 'PK1'"));
        engine.execute("CREATE AGGREGATION POLICY ag1 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        assertEquals(DONE, status("COMMENT ON AGGREGATION POLICY ag1 IS 'c-ag'"));
        assertEquals("c-ag", listedComment("SHOW AGGREGATION POLICIES LIKE 'AG1'"));
        engine.execute("CREATE PROJECTION POLICY pj1 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertEquals(DONE, status("COMMENT ON PROJECTION POLICY pj1 IS 'c-pj'"));
        assertEquals("c-pj", listedComment("SHOW PROJECTION POLICIES LIKE 'PJ1'"));
        engine.execute("CREATE JOIN POLICY jp1 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertEquals(DONE, status("COMMENT ON JOIN POLICY jp1 IS 'c-jp'"));
        assertEquals("c-jp", listedComment("SHOW JOIN POLICIES LIKE 'JP1'"));
        engine.execute("CREATE PRIVACY POLICY pv1 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertEquals(DONE, status("COMMENT ON PRIVACY POLICY pv1 IS 'c-pv'"));
        assertEquals("c-pv", listedComment("SHOW PRIVACY POLICIES LIKE 'PV1'"));
        engine.execute("CREATE IMAGE REPOSITORY ir1");
        assertEquals(DONE, status("COMMENT ON IMAGE REPOSITORY ir1 IS 'c-ir'"));
        assertEquals("c-ir", listedComment("SHOW IMAGE REPOSITORIES LIKE 'IR1'"));
        engine.execute("CREATE ALERT al1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertEquals(DONE, status("COMMENT ON ALERT al1 IS 'c-al'"));
        assertEquals("c-al", listedComment("SHOW ALERTS LIKE 'AL1'"));
        engine.execute("CREATE NOTEBOOK nb1");
        assertEquals(DONE, status("COMMENT ON NOTEBOOK nb1 IS 'c-nb'"));
        assertEquals("c-nb", listedComment("SHOW NOTEBOOKS LIKE 'NB1'"));
        assertEquals(DONE, status("COMMENT IF EXISTS ON PASSWORD POLICY pp1 IS 'c-pp2'"));
        assertEquals("c-pp2", listedComment("SHOW PASSWORD POLICIES LIKE 'PP1'"));
    }

    @Test
    public void theAlterOfAnUnmodelledKindsCommentFindsNothing() {
        final String model = missing("Model", "TEST_DB.TEST_SCHEMA.NOSUCH");
        assertEquals(model, refusalOf("ALTER MODEL nosuch SET COMMENT = 'c'"));
        assertEquals(model, refusalOf("ALTER MODEL nosuch UNSET COMMENT"));
        assertEquals(missing("Git repository", "TEST_DB.TEST_SCHEMA.NOSUCH"),
            refusalOf("ALTER GIT REPOSITORY nosuch SET COMMENT = 'c'"));
        assertEquals(missing("Git repository", "TEST_DB.TEST_SCHEMA.NOSUCH"),
            refusalOf("ALTER GIT REPOSITORY nosuch UNSET COMMENT"));
        assertEquals(missing("Semantic View", "TEST_DB.TEST_SCHEMA.NOSUCH"),
            refusalOf("ALTER SEMANTIC VIEW nosuch SET COMMENT = 'c'"));
        assertEquals(missing("Semantic View", "TEST_DB.TEST_SCHEMA.NOSUCH"),
            refusalOf("ALTER SEMANTIC VIEW nosuch UNSET COMMENT"));
        assertEquals(missing("DBT PROJECT", "TEST_DB.TEST_SCHEMA.NOSUCH"),
            refusalOf("ALTER DBT PROJECT nosuch SET COMMENT = 'c'"));
        assertEquals(DONE, status("ALTER MODEL IF EXISTS nosuch SET COMMENT = 'c'"));
        assertEquals(DONE, status("ALTER GIT REPOSITORY IF EXISTS nosuch UNSET COMMENT"));
        assertEquals(DONE, status("ALTER DBT PROJECT IF EXISTS nosuch SET COMMENT = 'c'"));
        assertEquals(missing("Schema", "TEST_DB.NOSUCH_S"), refusalOf("ALTER MODEL nosuch_s.m SET COMMENT = 'c'"));
    }
}
