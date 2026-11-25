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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A started alert follows its schema: dropping the schema takes its alerts out of SHOW ALERTS, UNDROP SCHEMA brings
 * them back still started, CREATE OR REPLACE SCHEMA drops them with the schema it replaces (once the replacement is
 * renamed away, UNDROP SCHEMA brings them back started), and CREATE OR REPLACE ALERT leaves the new alert suspended.
 * Live-verified.
 */
public class AlertStateAcrossSchemaChangesTest extends MeasuredCellsTestSupport {

    private static final String STATES = """
        SELECT LISTAGG("schema_name" || '.' || "name" || ':' || "state", '/') WITHIN GROUP (ORDER BY "schema_name")
        FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""";

    @Test
    public void startedAlertsFollowTheirSchema() {
        engine.execute("CREATE SCHEMA s1");
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE SCHEMA s3");
        engine.execute("CREATE ALERT s1.a1 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN"
            + " SELECT 1");
        engine.execute("CREATE ALERT s2.a2 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN"
            + " SELECT 1");
        engine.execute("CREATE ALERT s3.a3 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN"
            + " SELECT 1");
        engine.execute("ALTER ALERT s1.a1 RESUME");
        engine.execute("ALTER ALERT s2.a2 RESUME");
        engine.execute("ALTER ALERT s3.a3 RESUME");
        engine.execute("ALTER ALERT s3.a3 RESUME");
        assertEquals("S1.A1:started/S2.A2:started/S3.A3:started", states());
        engine.execute("DROP SCHEMA s1");
        assertEquals("S2.A2:started/S3.A3:started", states());
        engine.execute("UNDROP SCHEMA s1");
        assertEquals("S1.A1:started/S2.A2:started/S3.A3:started", states());
        engine.execute("CREATE OR REPLACE SCHEMA s2");
        assertEquals("S1.A1:started/S3.A3:started", states());
        engine.execute("ALTER SCHEMA s2 RENAME TO s2new");
        engine.execute("UNDROP SCHEMA s2");
        assertEquals("S1.A1:started/S2.A2:started/S3.A3:started", states());
        engine.execute("CREATE OR REPLACE ALERT s3.a3 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 2");
        assertEquals("S1.A1:started/S2.A2:started/S3.A3:suspended", states());
        engine.execute("ALTER ALERT s1.a1 SUSPEND");
        engine.execute("ALTER ALERT s2.a2 SUSPEND");
        assertEquals("S1.A1:suspended/S2.A2:suspended/S3.A3:suspended", states());
    }

    /** Every alert of the current database, as {@code SCHEMA.NAME:state}, by schema. */
    private String states() {
        engine.execute("SHOW ALERTS IN DATABASE");
        return answer(STATES);
    }
}
