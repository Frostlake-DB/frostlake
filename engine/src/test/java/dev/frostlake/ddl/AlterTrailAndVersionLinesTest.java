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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER ... SET followed by a clause its list does not read, ALTER STAGE ... SET URL judged before the stage is looked
 * up, ALTER APPLICATION [PACKAGE] and ALTER DATASET ... ADD / DROP VERSION, each as the account answers them
 * (live-verified). The engine's local stage locations are switched off, as an account has none.
 */
public class AlterTrailAndVersionLinesTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void withoutLocalLocations() {
        if (!isLiveSnowflake()) {
            engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "false");
        }
    }

    @Test
    public void applicationPackageIsMissing() {
        final String[] statements = {"ALTER APPLICATION PACKAGE x SET COMMENT = 'c'",
            "ALTER APPLICATION PACKAGE x UNSET COMMENT", "ALTER APPLICATION PACKAGE x SET TAG t = 'v'",
            "ALTER APPLICATION PACKAGE x RENAME TO y", "COMMENT ON APPLICATION PACKAGE x IS 'c'"};
        for (final String sql : statements) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute(sql);
                }
            });
            assertTrue(refused.getMessage().contains("Application package 'X' does not exist or not authorized."),
                sql + ": " + refused.getMessage());
        }
    }

    @Test
    public void trailingClauses() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE STAGE st1");
        engine.execute("CREATE FUNCTION f(x INT) RETURNS INT AS 'x'");
        assertCells(new String[][] {
            {"ALTER FUNCTION f(INT) SET COMMENT = 'c' SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '<EOF>'."},
            {"ALTER MATERIALIZED VIEW x SET COMMENT = 'c' CLUSTER BY (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'COMMENT'."},
            {"ALTER TABLE x SET COMMENT = 'c' TAG t = 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'TAG'."},
            {"ALTER TABLE t1 SET COMMENT = 'c' TAG t = 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'TAG'."},
            {"ALTER DATA METRIC FUNCTION dmf(TABLE(INT)) SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'METRIC'."},
            {"ALTER HYBRID TABLE x SET DATA_RETENTION_TIME_IN_DAYS = 1 COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'HYBRID'."},
            {"ALTER VIEW x SET COMMENT = 'c' SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER VIEW x SET COMMENT = 'c' TAG t = 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER SCHEMA x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 29 unexpected ''c''."},
            {"ALTER STAGE st1 SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'."},
            {"ALTER PROCEDURE p(INT) SET COMMENT = 'c' SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'."},
            {"ALTER SEQUENCE s SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'FOO'."},
            {"ALTER FUNCTION f(INT) SET SECURE COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'COMMENT'."},
            {"ALTER TABLE x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'."},
            {"ALTER DYNAMIC TABLE x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'COMMENT'."},
            {"ALTER TASK x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '<EOF>'."},
            {"ALTER EXTERNAL TABLE x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SET'."},
            {"ALTER DATABASE x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'COMMENT'."},
            {"ALTER FILE FORMAT x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER STREAM x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'x'."},
            {"ALTER ICEBERG TABLE x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '<EOF>'."},
            {"ALTER HYBRID TABLE x SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'HYBRID'."},
            {"ALTER DATA METRIC FUNCTION dmf SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'METRIC'."},
            {"ALTER STAGE st1 SET URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE st1 SET URL = 's3://'",
                "REFUSED Bucket name '' in the stage location is not supported. Valid bucket names must "
                + "consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"ALTER STAGE st1 SET URL = 'xyz://bucket/p'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'xyz://bucket/p'"},
            {"ALTER STAGE st1 SET URL = 'azure://'",
                "REFUSED SQL compilation error: | Invalid location format or value 'azure://'."},
            {"ALTER STAGE st1 SET URL = 'gcs://'",
                "REFUSED Bucket name '' in the stage location is not supported. Valid bucket names must "
                + "consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"ALTER STAGE nosuch.nosuch.st SET URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE st1 SET URL = ''",
                "REFUSED SQL compilation error: | invalid URL prefix found in: ''"},
            {"ALTER STAGE st1 SET URL = 's3:/'",
                "REFUSED SQL compilation error: | invalid URL: s3:/"},
            {"ALTER STAGE st1 SET URL = 'azure://acct'",
                "REFUSED SQL compilation error: | Invalid location format or value 'azure://acct'."},
            {"ALTER STAGE nosuch SET URL = 's3://'",
                "REFUSED Bucket name '' in the stage location is not supported. Valid bucket names must "
                + "consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"CREATE STAGE st2 URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE st1 SET URL = 'foo' COMMENT = 'c'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET URL = 'xyz://b'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'xyz://b'"},
            {"ALTER APPLICATION PACKAGE IF EXISTS x SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'COMMENT'."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x DROP VERSION 'v'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET IF EXISTS x ADD VERSION 'v' FROM (SELECT 1)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) COMMENT = 'c'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'COMMENT'."},
            {"ALTER DATASET x ADD VERSION 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'."},
            {"ALTER DATASET x FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'FOO'."},
        });
    }

    @Test
    public void trailingClauseNeighbours() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE FUNCTION f(x INT) RETURNS INT AS 'x'");
        assertCells(new String[][] {
            {"ALTER VIEW x SET COMMENT = 'c' CLUSTER BY (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW x SET COMMENT = 'c' FOO BAR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW x SET COMMENT = 'c', FOO BAR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW x SET COMMENT = 'c' FOO = 1 BAR BAZ",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW x SET SECURE CLUSTER BY (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'CLUSTER'."},
            {"ALTER VIEW x SET COMMENT = 'c' FOO BAR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW x SET COMMENT = 'c' TAG t = 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'COMMENT'."},
            {"ALTER TABLE x SET COMMENT = 'c', TAG t = 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 't'."},
            {"ALTER TABLE x SET DATA_RETENTION_TIME_IN_DAYS = 1 TAG t = 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'TAG'."},
            {"ALTER TABLE x SET TAG t = 'v' COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'COMMENT'."},
            {"ALTER DYNAMIC TABLE x SET COMMENT = 'c' TAG t = 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'COMMENT'."},
            {"ALTER DYNAMIC TABLE x SET TARGET_LAG = '1 minute' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TARGET_LAG'."},
            {"ALTER DYNAMIC TABLE x SET COMMENT = 'c' FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'DYNAMIC_TABLE'"},
            {"ALTER EXTERNAL TABLE x SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | invalid property 'COMMENT' for 'EXTERNAL_TABLE'"},
            {"ALTER EXTERNAL TABLE x SET AUTO_REFRESH = TRUE FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SET'."},
            {"ALTER EXTERNAL TABLE x SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'EXTERNAL_TABLE'"},
            {"ALTER DATA x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'x'."},
            {"ALTER DATA",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'."},
            {"ALTER DATA METRIC",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'METRIC'."},
            {"ALTER DATA METRIC FUNCTION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'METRIC'."},
            {"ALTER DATA x SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'x'."},
            {"ALTER FUNCTION f(INT) SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '<EOF>'."},
            {"ALTER FUNCTION f(INT) SET SECURE COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'."},
            {"ALTER FUNCTION f(INT) SET COMMENT = 'c' SECURE = 1",
                "REFUSED SQL compilation error: | invalid property 'SECURE' for 'FUNCTION'"},
            {"ALTER FUNCTION f(INT) SET COMMENT = 'c' MEMOIZABLE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '<EOF>'."},
            {"ALTER FUNCTION nosuch(INT) SET COMMENT = 'c' SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '<EOF>'."},
            {"ALTER FUNCTION f(INT) SET LOG_LEVEL = 'INFO' SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '<EOF>'."},
            {"ALTER HYBRID TABLE x SET DATA_RETENTION_TIME_IN_DAYS = 1, COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'HYBRID'."},
            {"ALTER HYBRID TABLE x SET COMMENT = 'c' FOO = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'HYBRID'."},
            {"ALTER HYBRID TABLE x RENAME TO y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'HYBRID'."},
            {"ALTER PROCEDURE p(INT) SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'."},
            {"ALTER DYNAMIC TABLE x SET COMMENT = 'c' FOO BAR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'COMMENT'."},
            {"ALTER DATA METRIC FUNCTION dmf(TABLE(INT)) SET SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'METRIC'."},
        });
    }

    @Test
    public void stageLocations() {
        engine.execute("CREATE STAGE st1");
        assertCells(new String[][] {
            {"CREATE STAGE sx URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"CREATE STAGE sx URL = 's3://'",
                "REFUSED Bucket name '' in the stage location is not supported. Valid bucket names must "
                + "consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"CREATE STAGE sx URL = 'xyz://bucket/p'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'xyz://bucket/p'"},
            {"CREATE STAGE sx URL = 'azure://'",
                "REFUSED SQL compilation error: | Invalid location format or value 'azure://'."},
            {"CREATE STAGE sx URL = 'gcs://'",
                "REFUSED Bucket name '' in the stage location is not supported. Valid bucket names must "
                + "consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"CREATE STAGE sx URL = ''",
                "REFUSED SQL compilation error: | invalid URL prefix found in: ''"},
            {"CREATE STAGE sx URL = 's3:/'",
                "REFUSED SQL compilation error: | invalid URL: s3:/"},
            {"CREATE STAGE sx URL = 'file:///tmp/x'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'file:///tmp/x'"},
            {"CREATE STAGE sx URL = 'file://h/x'",
                "REFUSED SQL compilation error: | invalid URL: file://h/x"},
            {"CREATE STAGE sx URL = '@st1'",
                "REFUSED SQL compilation error: | invalid URL: @st1"},
            {"CREATE STAGE sx URL = 's3://Bad Bucket/x'",
                "REFUSED Bucket name 'Bad Bucket' in the stage location is not supported. Valid bucket names "
                + "must consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"ALTER STAGE st1 SET URL = 's3://bucket/p'",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an "
                + "internal or temporary stage."},
            {"ALTER STAGE st1 SET URL = 'file:///tmp/x'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'file:///tmp/x'"},
            {"ALTER STAGE nosuch SET URL = 'file:///tmp/x'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'file:///tmp/x'"},
            {"ALTER STAGE nosuch SET URL = 'file://h/x'",
                "REFUSED SQL compilation error: | invalid URL: file://h/x"},
            {"ALTER STAGE nosuch SET URL = '@st1'",
                "REFUSED SQL compilation error: | invalid URL: @st1"},
            {"ALTER STAGE IF EXISTS nosuch SET URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET URL = 's3://Bad Bucket/x'",
                "REFUSED Bucket name 'Bad Bucket' in the stage location is not supported. Valid bucket names "
                + "must consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"ALTER STAGE nosuch SET URL = 'azure://a.blob.core.windows.net/'",
                "REFUSED SQL compilation error: | Invalid location format or value "
                + "'azure://a.blob.core.windows.net/'."},
            {"ALTER STAGE nosuch SET COMMENT = 'c' URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET URL = 's3://b/p' URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET CREDENTIALS = (AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b') URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET URL = 'foo' FILE_FORMAT = (FORMAT_NAME = 'nosuchff')",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET FILE_FORMAT = (FORMAT_NAME = 'nosuchff') URL = 'foo'",
                "REFUSED SQL compilation error: | invalid URL: foo"},
            {"ALTER STAGE nosuch SET URL = 'azure://acct.blob.core.windows.net'",
                "REFUSED SQL compilation error: | Invalid location format or value "
                + "'azure://acct.blob.core.windows.net'."},
        });
    }

    @Test
    public void createdStageLocations() {
        engine.execute("CREATE STAGE st1");
        assertCells(new String[][] {
            {"CREATE STAGE sx3 URL = 'xyz://bucket/p'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'xyz://bucket/p'"},
            {"CREATE STAGE sx4 URL = 'azure://'",
                "REFUSED SQL compilation error: | Invalid location format or value 'azure://'."},
            {"CREATE STAGE sx5 URL = 'gcs://'",
                "REFUSED Bucket name '' in the stage location is not supported. Valid bucket names must "
                + "consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"CREATE STAGE sx6 URL = ''",
                "REFUSED SQL compilation error: | invalid URL prefix found in: ''"},
            {"CREATE STAGE sx7 URL = 's3:/'",
                "REFUSED SQL compilation error: | invalid URL: s3:/"},
            {"CREATE STAGE sx8 URL = 'file:///tmp/x'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'file:///tmp/x'"},
            {"CREATE STAGE sx9 URL = 'file://h/x'",
                "REFUSED SQL compilation error: | invalid URL: file://h/x"},
            {"CREATE STAGE sx10 URL = '@st1'",
                "REFUSED SQL compilation error: | invalid URL: @st1"},
            {"CREATE STAGE sx11 URL = 's3://Bad Bucket/x'",
                "REFUSED Bucket name 'Bad Bucket' in the stage location is not supported. Valid bucket names "
                + "must consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"CREATE STAGE sx12 URL = 'http://h/x'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'http://h/x'"},
            {"CREATE STAGE sx13 URL = 'xyz:/b'",
                "REFUSED SQL compilation error: | invalid URL: xyz:/b"},
            {"CREATE STAGE sx14 URL = '://b'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: '://b'"},
            {"CREATE STAGE sx15 URL = 'XYZ://b'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'XYZ://b'"},
            {"CREATE STAGE sx16 URL = 'xyz:b://c'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'xyz:b://c'"},
        });
    }

    @Test
    public void applicationPackagesAndDatasets() {
        assertCells(new String[][] {
            {"ALTER APPLICATION PACKAGE x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '<EOF>'."},
            {"ALTER APPLICATION PACKAGE x SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'APPLICATION_PACKAGE'"},
            {"ALTER APPLICATION PACKAGE IF EXISTS x SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'."},
            {"ALTER APPLICATION PACKAGE IF EXISTS x UNSET COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'COMMENT'."},
            {"ALTER APPLICATION PACKAGE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'."},
            {"ALTER APPLICATION PACKAGE x SET",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'."},
            {"ALTER APPLICATION PACKAGE a.b SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"ALTER APPLICATION PACKAGE x FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FOO'."},
            {"ALTER APPLICATION PACKAGE x SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) PARTITION BY a",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) METADATA = '{}'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '<EOF>'."},
            {"ALTER DATASET x ADD VERSION v FROM (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'v'."},
            {"ALTER DATASET x DROP VERSION v",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'v'."},
            {"ALTER DATASET x DROP VERSION 'v' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'FOO'."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT * FROM nosuch)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1 FROM)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ')'."},
            {"ALTER DATASET IF EXISTS x DROP VERSION 'v'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET a.b.x ADD VERSION 'v' FROM (SELECT 1)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) COMMENT = 'c' METADATA = '{}'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (WITH c AS (SELECT 1) SELECT * FROM c)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) PARTITION BY (a)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET nosuch.x ADD VERSION 'v' FROM (SELECT 1)",
                "REFUSED Dataset X does not exist."},
        });
    }

    @Test
    public void datasetVersionProperties() {
        assertCells(new String[][] {
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) FOO = 1",
                "REFUSED Invalid dataset property FOO."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) PARTITION BY a, b",
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected ','."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) COMMENT = 5",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) METADATA = '{}' COMMENT = 'c'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) COMMENT = 'c' COMMENT = 'd'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET \"x\" ADD VERSION 'v' FROM (SELECT 1)",
                "REFUSED Dataset x does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) COMMENT = 'c', METADATA = '{}'",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) COMMENT 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ''c''."},
            {"ALTER DATASET x DROP VERSION 'v' COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) METADATA = 5",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1 UNION SELECT 2)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM ((SELECT 1))",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION $$v$$ FROM (SELECT 1)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) PARTITION BY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '<EOF>'."},
            {"ALTER DATASET x ADD VERSION 'v' FROM (SELECT 1) COMMENT = 'c' FOO = 1",
                "REFUSED Invalid dataset property FOO."},
            {"ALTER DATASET IDENTIFIER('x') ADD VERSION 'v' FROM (SELECT 1)",
                "REFUSED Dataset X does not exist."},
            {"ALTER DATASET x ADD IF NOT EXISTS VERSION 'v' FROM (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'IF'."},
            {"ALTER DATASET x DROP VERSION IF EXISTS 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IF'."},
            {"ALTER APPLICATION PACKAGE x UNSET FOO",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'APPLICATION_PACKAGE'"},
            {"ALTER APPLICATION PACKAGE x SET COMMENT = 'a', COMMENT = 'b'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"ALTER APPLICATION PACKAGE IF EXISTS x SET FOO = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'FOO'."},
            {"COMMENT ON APPLICATION PACKAGE a.b IS 'c'",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"ALTER APPLICATION PACKAGE x SET DATA_RETENTION_TIME_IN_DAYS = 'a'",
                "REFUSED SQL compilation error: | invalid value ['a'] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
        });
    }

    @Test
    public void applications() {
        assertCells(new String[][] {
            {"ALTER APPLICATION x SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'APPLICATION'"},
            {"ALTER APPLICATION a.b SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"COMMENT ON APPLICATION x IS 'c'",
                "REFUSED Unsupported feature 'APPLICATION'."},
            {"ALTER APPLICATION x SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
            {"ALTER APPLICATION x FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'FOO'."},
            {"ALTER APPLICATION x SET COMMENT = 'c' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'."},
            {"ALTER APPLICATION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'."},
        });
    }
}
