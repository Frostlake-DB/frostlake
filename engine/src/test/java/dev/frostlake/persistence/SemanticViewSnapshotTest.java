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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A semantic view survives a snapshot: an engine's copy describes it, writes its DDL and lists it as the engine did,
 * its comment and its owner included.
 */
public class SemanticViewSnapshotTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE svs_db");
        engine.execute("USE SCHEMA svs_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private static String rows(final DatabaseEngine on, final String sql) {
        final ResultSet rs = on.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ");
            for (int c = 0; c < rs.getColumns().size(); c++) {
                out.append(c == 0 ? "" : "|").append(row.getValue(c));
            }
        }
        return out.toString();
    }

    @Test
    public void aSemanticViewSurvivesACopy() throws Exception {
        engine.execute("CREATE TABLE t1 (a INT, b VARCHAR(20), c NUMBER(10,2))");
        engine.execute("CREATE TABLE t2 (x INT, a INT)");
        engine.execute("CREATE SEMANTIC VIEW sv TABLES (t1 PRIMARY KEY (a) WITH SYNONYMS = ('s'), t2 PRIMARY KEY (x)) "
            + "RELATIONSHIPS (r AS t2 (a) REFERENCES t1) FACTS (t1.f AS c * 2) DIMENSIONS (t1.d AS b COMMENT = 'dc') "
            + "METRICS (t1.m AS SUM(t1.f), t2.n AS COUNT(x), dm AS t1.m + t2.n) COMMENT = 'top'");
        engine.execute("ALTER SEMANTIC VIEW sv SET COMMENT = 'kept'");
        final String described = rows(engine, "DESCRIBE SEMANTIC VIEW sv");
        final String ddl = rows(engine, "SELECT GET_DDL('SEMANTIC_VIEW', 'sv')");
        engine.execute("SHOW SEMANTIC VIEWS");
        final String listed = rows(engine, "SELECT \"created_on\", \"name\", \"comment\", \"owner\" "
            + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        final DatabaseEngine copy = engine.cloneInstance();
        try {
            copy.execute("USE SCHEMA svs_db.PUBLIC");
            assertEquals(described, rows(copy, "DESCRIBE SEMANTIC VIEW sv"));
            assertEquals(ddl, rows(copy, "SELECT GET_DDL('SEMANTIC_VIEW', 'sv')"));
            copy.execute("SHOW SEMANTIC VIEWS");
            assertEquals(listed, rows(copy, "SELECT \"created_on\", \"name\", \"comment\", \"owner\" "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        } finally {
            copy.shutdown();
        }
    }
}
