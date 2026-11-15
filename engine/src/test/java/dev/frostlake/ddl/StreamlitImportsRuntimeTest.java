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
 * A new Streamlit app created FROM a location runs on the container runtime unless RUNTIME_NAME names the warehouse
 * one, and only an app running on warehouses takes IMPORTS: the statement's query warehouse is judged first, then the
 * runtime its IMPORTS need, then its main file. An app replacing one of its name, and one created with ROOT_LOCATION,
 * run on warehouses and take IMPORTS (all live-verified).
 */
public class StreamlitImportsRuntimeTest extends MeasuredCellsTestSupport {

    private static final String IMPORTS_REFUSED = "REFUSED This has IMPORTS set - but IMPORTS are only supported by "
        + "Streamlit applications running on warehouses.";
    private static final String NO_MAIN_FILE = "REFUSED SQL compilation error: | Missing MAIN_FILE.";
    private static final String FROM = "CREATE OR REPLACE STREAMLIT app40 FROM '@stg40/app' MAIN_FILE = "
        + "'streamlit_app.py' TITLE = 'My app' ";

    @Test
    public void importsNeedTheWarehouseRuntime() {
        engine.execute("CREATE OR REPLACE STAGE stg40");
        assertCells(new String[][] {
            {FROM + "IMPORTS = ('@stg40/lib.py')", IMPORTS_REFUSED},
            {FROM + "RUNTIME_NAME = 'SYSTEM$WAREHOUSE_RUNTIME' IMPORTS = ('@stg40/lib.py')", NO_MAIN_FILE},
            {FROM + "QUERY_WAREHOUSE = flq_wh40 IMPORTS = ('@stg40/lib.py')",
                "REFUSED The specified warehouse FLQ_WH40 does not exist or the current role does not have access. "
                    + "Owner of the Streamlit must have at least USAGE on the specified warehouse."},
            {FROM + "RUNTIME_NAME = 'SYSTEM$ST_CONTAINER_RUNTIME_PY3_11' IMPORTS = ('@stg40/lib.py')", IMPORTS_REFUSED},
            {FROM.trim(), NO_MAIN_FILE},
            {"CREATE OR REPLACE STREAMLIT app40 FROM '@stg40/app' MAIN_FILE = 'nosuch.py' IMPORTS = ('@stg40/lib.py')",
                IMPORTS_REFUSED},
            {"CREATE STREAMLIT IF NOT EXISTS app41 FROM '@stg40/app' MAIN_FILE = 'streamlit_app.py' "
                + "IMPORTS = ('@stg40/lib.py')", IMPORTS_REFUSED},
            {"CREATE OR REPLACE STREAMLIT app40 ROOT_LOCATION = '@stg40/app' MAIN_FILE = 'streamlit_app.py' "
                + "IMPORTS = ('@stg40/lib.py')", "Streamlit APP40 successfully created."},
        });
    }

    @Test
    public void aMainFileThereLeavesTheRuntimeRule() {
        engine.execute("CREATE OR REPLACE STAGE stg50");
        engine.execute("COPY INTO @stg50/app/streamlit_app.py FROM (SELECT 'import streamlit') FILE_FORMAT = "
            + "(TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE HEADER = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE STREAMLIT app51 FROM '@stg50/app' MAIN_FILE = 'streamlit_app.py' "
                + "IMPORTS = ('@stg50/lib.py')", IMPORTS_REFUSED},
            {"CREATE STREAMLIT app50 FROM '@stg50/app' MAIN_FILE = 'streamlit_app.py'",
                "Streamlit APP50 successfully created."},
            // The app it replaces makes the new one an app on warehouses.
            {"CREATE OR REPLACE STREAMLIT app50 FROM '@stg50/app' MAIN_FILE = 'streamlit_app.py' "
                + "IMPORTS = ('@stg50/lib.py')", "Streamlit APP50 successfully created."},
            {"CREATE STREAMLIT app52 FROM '@stg50/app' MAIN_FILE = 'streamlit_app.py' "
                + "RUNTIME_NAME = 'SYSTEM$WAREHOUSE_RUNTIME' IMPORTS = ('@stg50/lib.py')",
                "Streamlit APP52 successfully created."},
        });
    }
}
