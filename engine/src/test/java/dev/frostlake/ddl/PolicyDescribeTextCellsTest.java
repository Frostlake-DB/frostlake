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

/**
 * DESCRIBE of a password, an authentication and a session policy holds no NULL cell: a property that holds nothing
 * and a property with no default show the text {@code null}, which a driver reads as a string (live-verified). A
 * Streamlit app made over a stage location describes its default packages, an empty text for its own, and a compute
 * pool and a runtime that are NULL.
 */
public class PolicyDescribeTextCellsTest extends MeasuredCellsTestSupport {

    private static final String NULL_DEFAULTS = " ->> SELECT LISTAGG(\"property\", ' ') WITHIN GROUP (ORDER BY "
        + "\"property\") FROM $1 WHERE \"default\" = 'null'";

    private static final String NULL_VALUES = " ->> SELECT LISTAGG(\"property\", ' ') WITHIN GROUP (ORDER BY "
        + "\"property\") FROM $1 WHERE \"value\" = 'null'";

    private static final String COUNTS = " ->> SELECT COUNT(*), COUNT(\"property\"), COUNT(\"value\"), "
        + "COUNT(\"default\"), COUNT(\"description\") FROM $1";

    private static final String STREAMLIT_CELLS = "DESCRIBE STREAMLIT sl ->> SELECT \"name\" || '|' || "
        + "IFNULL(\"title\", '<NULL>') || '|' || \"root_location\" || '|' || \"main_file\" || '|' || "
        + "IFNULL(\"query_warehouse\", '<NULL>') || '|' || \"default_packages\" || '|[' || \"user_packages\" || "
        + "']|' || \"import_urls\" || '|' || \"external_access_integrations\" || '|' || "
        + "\"external_access_secrets\" || '|' || IFNULL(\"compute_pool\", '<NULL>') || '|' || "
        + "IFNULL(\"runtime_name\", '<NULL>') FROM $1";

    @Test
    public void aPasswordPolicyDescribesNoNullCell() {
        engine.execute("CREATE PASSWORD POLICY pp PASSWORD_MIN_LENGTH = 10 COMMENT = 'one'");
        engine.execute("CREATE PASSWORD POLICY pp0");
        assertCells(new String[][] {
            {"DESCRIBE PASSWORD POLICY pp" + NULL_DEFAULTS, "COMMENT NAME OWNER"},
            {"DESCRIBE PASSWORD POLICY pp" + NULL_VALUES, ""},
            {"DESCRIBE PASSWORD POLICY pp0" + NULL_VALUES, "COMMENT"},
            {"DESCRIBE PASSWORD POLICY pp0" + COUNTS, "14|14|14|14|14"},
            {"DESCRIBE PASSWORD POLICY pp ->> SELECT \"value\" || '|' || \"default\" FROM $1 "
                + "WHERE \"property\" = 'PASSWORD_MIN_LENGTH'", "10|14"},
        });
    }

    @Test
    public void anAuthenticationPolicyDescribesNoNullCell() {
        engine.execute("CREATE AUTHENTICATION POLICY ap AUTHENTICATION_METHODS = ('PASSWORD') COMMENT = 'one'");
        engine.execute("CREATE AUTHENTICATION POLICY ap0");
        assertCells(new String[][] {
            {"DESCRIBE AUTHENTICATION POLICY ap" + NULL_DEFAULTS,
                "CLIENT_POLICY COMMENT NAME OWNER PAT_POLICY WORKLOAD_IDENTITY_POLICY"},
            {"DESCRIBE AUTHENTICATION POLICY ap" + NULL_VALUES, ""},
            {"DESCRIBE AUTHENTICATION POLICY ap0" + NULL_VALUES, "COMMENT"},
            {"DESCRIBE AUTHENTICATION POLICY ap0" + COUNTS, "11|11|11|11|11"},
        });
    }

    @Test
    public void aSessionPolicyDescribesNoNullCell() {
        engine.execute("CREATE SESSION POLICY sp SESSION_IDLE_TIMEOUT_MINS = 30 COMMENT = 'one'");
        engine.execute("CREATE SESSION POLICY sp0");
        assertCells(new String[][] {
            {"DESCRIBE SESSION POLICY sp" + NULL_DEFAULTS, "AGENT_RESTRICTED_SESSION_SCOPE ALLOWED_SECONDARY_ROLES "
                + "BLOCKED_SECONDARY_ROLES COMMENT CREATED_ON NAME OWNER OWNER_ROLE_TYPE"},
            {"DESCRIBE SESSION POLICY sp" + NULL_VALUES, "AGENT_RESTRICTED_SESSION_SCOPE"},
            {"DESCRIBE SESSION POLICY sp0" + NULL_VALUES, "AGENT_RESTRICTED_SESSION_SCOPE COMMENT"},
            {"DESCRIBE SESSION POLICY sp0" + COUNTS, "12|12|12|12|12"},
        });
    }

    @Test
    public void aStreamlitOverAStageLocationDescribesItsPackages() {
        engine.execute("CREATE STAGE stg");
        engine.execute("CREATE STREAMLIT sl ROOT_LOCATION = '@TEST_DB.TEST_SCHEMA.STG' "
            + "MAIN_FILE = 'streamlit_app.py' COMMENT = 'one'");
        engine.execute("DESCRIBE STREAMLIT sl ->> CREATE TABLE tcols AS SELECT * FROM $1");
        assertCells(new String[][] {
            {"SELECT LISTAGG(column_name, ' ') WITHIN GROUP (ORDER BY ordinal_position) "
                + "FROM information_schema.columns WHERE table_name = 'TCOLS'",
                "name title root_location main_file query_warehouse url_id default_packages user_packages "
                    + "import_urls external_access_integrations external_access_secrets compute_pool runtime_name"},
            {STREAMLIT_CELLS, "SL|<NULL>|@TEST_DB.TEST_SCHEMA.STG|streamlit_app.py|<NULL>|"
                + "python==3.11.*,snowflake-snowpark-python,streamlit|[]|[]|[]|{}|<NULL>|<NULL>"},
        });
        engine.execute("ALTER STREAMLIT sl SET TITLE = 'tt'");
        assertAnswer("SL|tt|@TEST_DB.TEST_SCHEMA.STG|streamlit_app.py|<NULL>|"
            + "python==3.11.*,snowflake-snowpark-python,streamlit|[]|[]|[]|{}|<NULL>|<NULL>", STREAMLIT_CELLS);
    }
}
