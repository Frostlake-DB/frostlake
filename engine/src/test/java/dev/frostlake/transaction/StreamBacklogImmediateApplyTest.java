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

package dev.frostlake.transaction;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.features.StreamBacklogStatementVersionsTest;

import org.junit.jupiter.api.Assumptions;

/**
 * The cells of StreamBacklogStatementVersionsTest where DML applies at once
 * ({@code transaction.deferredApply=false}): each statement writes its versions as it ends.
 */
public class StreamBacklogImmediateApplyTest extends StreamBacklogStatementVersionsTest {

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the mode is an engine setting");
        engine.shutdown();
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_TRANSACTION_DEFERRED_APPLY, "false");
        engine = new DatabaseEngine(config);
        enableLocalStageUrls(engine);
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
    }
}
