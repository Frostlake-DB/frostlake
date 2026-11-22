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

package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An object created with COPY GRANTS takes the privileges of the object it replaces or copies, all but OWNERSHIP, and
 * none of the future grants defined for its kind in the schema; without COPY GRANTS it takes the future grants and
 * none of the old privileges (documented, for tables, views and materialized views alike). A materialized view
 * replaced with COPY GRANTS keeps its grants as a view does. Engine only: the live account allows no CREATE ROLE or
 * GRANT.
 */
public class CopyGrantsWithoutFutureGrantsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("USE SCHEMA d1.s1");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE ROLE r2");
        engine.execute("CREATE TABLE base (x INT)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** The grants on an object, as "privilege grantee" pairs in alphabetical order. */
    private String grantsOn(final String kind, final String name) {
        final ResultSet rs = engine.executeQuery("SHOW GRANTS ON " + kind + " " + name);
        final List<String> pairs = new ArrayList<>();
        while (rs.next()) {
            pairs.add(rs.getValue("privilege") + " " + rs.getValue("grantee_name"));
        }
        Collections.sort(pairs);
        return String.join(" | ", pairs);
    }

    @Test
    public void aReplacedTableWithCopyGrantsTakesNoFutureGrant() {
        engine.execute("CREATE TABLE t (x INT)");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE r2");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA d1.s1 TO ROLE r1");
        engine.execute("CREATE OR REPLACE TABLE t (x INT, y INT) COPY GRANTS");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R2", grantsOn("TABLE", "t"));
        // Without COPY GRANTS the future grant applies and the old privilege is gone.
        engine.execute("CREATE OR REPLACE TABLE t (x INT)");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R1", grantsOn("TABLE", "t"));
    }

    @Test
    public void aCloneLikeOrCtasWithCopyGrantsTakesNoFutureGrant() {
        engine.execute("GRANT SELECT ON TABLE base TO ROLE r2");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA d1.s1 TO ROLE r1");
        engine.execute("CREATE TABLE c1 CLONE base COPY GRANTS");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R2", grantsOn("TABLE", "c1"));
        engine.execute("CREATE TABLE l1 LIKE base COPY GRANTS");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R2", grantsOn("TABLE", "l1"));
        engine.execute("CREATE TABLE a1 (x INT)");
        engine.execute("GRANT INSERT ON TABLE a1 TO ROLE r2");
        engine.execute("CREATE OR REPLACE TABLE a1 COPY GRANTS AS SELECT 1 AS x");
        // The table being replaced took the future grant when it was created, and passes it on as its own.
        assertEquals("INSERT R2 | OWNERSHIP SYSADMIN | SELECT R1", grantsOn("TABLE", "a1"));
    }

    @Test
    public void aReplacedViewWithCopyGrantsTakesNoFutureGrant() {
        engine.execute("CREATE VIEW v AS SELECT x FROM base");
        engine.execute("GRANT SELECT ON VIEW v TO ROLE r2");
        engine.execute("GRANT SELECT ON FUTURE VIEWS IN SCHEMA d1.s1 TO ROLE r1");
        engine.execute("CREATE OR REPLACE VIEW v COPY GRANTS AS SELECT x FROM base");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R2", grantsOn("VIEW", "v"));
        engine.execute("CREATE OR REPLACE VIEW v AS SELECT x FROM base");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R1", grantsOn("VIEW", "v"));
    }

    @Test
    public void aReplacedMaterializedViewKeepsItsGrantsWithCopyGrants() {
        engine.execute("CREATE MATERIALIZED VIEW mv AS SELECT x FROM base");
        engine.execute("GRANT SELECT ON MATERIALIZED VIEW mv TO ROLE r1");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv COPY GRANTS AS SELECT x FROM base");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R1", grantsOn("MATERIALIZED VIEW", "mv"));
        // A future grant does not reach the copy either.
        engine.execute("GRANT SELECT ON FUTURE MATERIALIZED VIEWS IN SCHEMA d1.s1 TO ROLE r2");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv COPY GRANTS AS SELECT x FROM base");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R1", grantsOn("MATERIALIZED VIEW", "mv"));
        // Without COPY GRANTS the old privileges go and the future grant applies.
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv AS SELECT x FROM base");
        assertEquals("OWNERSHIP SYSADMIN | SELECT R2", grantsOn("MATERIALIZED VIEW", "mv"));
    }

    @Test
    public void aRefusedReplacementLeavesTheMaterializedViewsGrants() {
        engine.execute("CREATE MATERIALIZED VIEW mv AS SELECT x FROM base");
        engine.execute("GRANT SELECT ON MATERIALIZED VIEW mv TO ROLE r1");
        try {
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv COPY GRANTS AS SELECT nosuch FROM base");
        } catch (final RuntimeException refused) {
            // the replacement does not compile
        }
        assertEquals("OWNERSHIP SYSADMIN | SELECT R1", grantsOn("MATERIALIZED VIEW", "mv"));
    }
}
