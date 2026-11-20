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

package dev.frostlake.stage;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A stage on S3-compatible storage: its ENDPOINT, judged against the location and the services the account knows,
 * and listed by SHOW STAGES and DESCRIBE STAGE (live-verified).
 */
public class StageEndpointTest extends MeasuredCellsTestSupport {

    @Test
    public void anEndpointIsJudgedAndListed() {
        engine.execute("CREATE STAGE m1 URL = 's3compat://bucket/path/' ENDPOINT = 'play.min.io' CREDENTIALS = "
            + "(AWS_KEY_ID = 'k' AWS_SECRET_KEY = 's') COMMENT = 'one'");
        engine.execute("CREATE STAGE m2 ENDPOINT = 'storage.example.com'");
        engine.execute("CREATE STAGE m3 ENDPOINT = 'foo.min.io' URL = 's3compat://bucket2/p/'");
        engine.execute("ALTER STAGE m3 SET ENDPOINT = 'a.b.r2.cloudflarestorage.com'");
        engine.execute("ALTER STAGE m1 SET COMMENT = 'two'");
        assertCells(new String[][] {
            {"SHOW STAGES LIKE 'M1' ->> SELECT \"endpoint\" || '|' || \"cloud\" || '|' || \"type\" || '|' "
                + "|| \"has_credentials\" || '|' || \"comment\" || '|' || \"url\" FROM $1",
                "play.min.io|AWS|EXTERNAL|Y|two|s3compat://bucket/path/"},
            {"SHOW STAGES LIKE 'M2' ->> SELECT \"endpoint\" || '|' || \"type\" FROM $1",
                "storage.example.com|INTERNAL"},
            {"SHOW STAGES LIKE 'M3' ->> SELECT \"endpoint\" || '|' || \"url\" FROM $1",
                "a.b.r2.cloudflarestorage.com|s3compat://bucket2/p/"},
            {"SHOW STAGES LIKE 'M1' ->> SELECT IFF(\"storage_integration\" IS NULL, 'none', 'set') FROM $1",
                "none"},
            {"DESCRIBE STAGE m1 ->> SELECT \"property_value\" FROM $1 WHERE \"parent_property\" = "
                + "'ENDPOINT'",
                "play.min.io"},
            {"DESCRIBE STAGE m1 ->> SELECT \"property\" || '|' || \"property_type\" || '|' || "
                + "\"property_default\" FROM $1 WHERE \"parent_property\" = 'ENDPOINT'",
                "ENDPOINT|String|"},
            {"DESCRIBE STAGE m2 ->> SELECT COUNT(*) FROM $1 WHERE \"parent_property\" = 'ENDPOINT'",
                "0"},
            {"CREATE STAGE r1 URL = 's3compat://bucket/path/' ENDPOINT = 'storage.example.com'",
                "REFUSED SQL compilation error: | Endpoint storage.example.com not allowed."},
            {"CREATE STAGE r2 URL = 's3compat://bucket/path/' CREDENTIALS = (AWS_KEY_ID = 'k' "
                + "AWS_SECRET_KEY = 's')",
                "REFUSED SQL compilation error: | Missing option(s): ENDPOINT"},
            {"CREATE STAGE r3 URL = 's3://bucket/path/' ENDPOINT = 'play.min.io'",
                "REFUSED SQL compilation error: | Endpoint cannot be used with storage type S3."},
            {"CREATE STAGE r4 URL = 'azure://acc.blob.core.windows.net/c/' ENDPOINT = 'play.min.io'",
                "REFUSED SQL compilation error: | Endpoint cannot be used with storage type AZURE."},
            {"CREATE STAGE r5 URL = 's3compat://bucket/path/' ENDPOINT = 'https://play.min.io'",
                "REFUSED SQL compilation error: Endpoint 'https://play.min.io' invalid. Expect no scheme "
                + "endpoint, for example: my.storage.com"},
            {"CREATE STAGE r6 URL = 's3compat://bucket/path/' ENDPOINT = 'a_b.min.io'",
                "REFUSED SQL compilation error: Endpoint 'a_b.min.io' invalid. Expect no scheme endpoint, for"
                + " example: my.storage.com"},
            {"CREATE STAGE r7 URL = 's3compat://bucket/path/' ENDPOINT = ''",
                "REFUSED SQL compilation error: | Missing option(s): ENDPOINT"},
            {"CREATE STAGE r8 URL = 's3compat://bucket/path/' ENDPOINT = 'min.io'",
                "REFUSED SQL compilation error: | Endpoint min.io not allowed."},
            {"CREATE STAGE r9 URL = 's3compat://bucket/path/' ENDPOINT = 's3.wasabisys.com'",
                "REFUSED SQL compilation error: | Endpoint s3.wasabisys.com not allowed."},
            {"CREATE STAGE r10 URL = 's3compat://bucket/path/' ENDPOINT = 'S3.AMAZONAWS.COM'",
                "REFUSED SQL compilation error: | Endpoint S3.AMAZONAWS.COM not allowed."},
            {"CREATE STAGE r11 URL = 's3compat://bucket/path/' ENDPOINT = 's3.wasabisys.com:443'",
                "REFUSED SQL compilation error: | Endpoint s3.wasabisys.com:443 not allowed."},
            {"CREATE STAGE r12 URL = 's3compat:/bucket/p' ENDPOINT = 'play.min.io'",
                "REFUSED SQL compilation error: | invalid URL: s3compat:/bucket/p"},
            {"CREATE STAGE r13 URL = 's3compat://bu?ck/path/' ENDPOINT = 'play.min.io'",
                "REFUSED Bucket name 'bu?ck' in the stage location is not supported. Valid bucket names must "
                + "consist of lowercase letters, digits, hyphens '-', and periods '.'."},
            {"CREATE STAGE r14 URL = 's3compat://bucket/path/' ENDPOINT = storage",
                "REFUSED SQL compilation error: | Endpoint storage not allowed."},
            {"ALTER STAGE m1 SET ENDPOINT = 'storage.example.com'",
                "REFUSED SQL compilation error: | Endpoint storage.example.com not allowed."},
            {"ALTER STAGE m1 SET ENDPOINT = 'http://play.min.io'",
                "REFUSED SQL compilation error: Endpoint 'http://play.min.io' invalid. Expect no scheme "
                + "endpoint, for example: my.storage.com"},
            {"CREATE STAGE k1 URL = 's3compat://bucket/path/' ENDPOINT = 's3.us-east-1.wasabisys.com' ->> "
                + "SELECT 1",
                "1"},
            {"CREATE STAGE k2 URL = 's3compat://bucket/path/' ENDPOINT = 's3-us-west-2.amazonaws.com' ->> "
                + "SELECT 1",
                "1"},
            {"CREATE STAGE k3 URL = 's3compat://bucket/path/' ENDPOINT = 'nyc3.digitaloceanspaces.com' ->>"
                + " SELECT 1",
                "1"},
            {"CREATE STAGE k4 URL = 's3compat://bucket/path/' ENDPOINT = 'foo.backblazeb2.com' ->> SELECT "
                + "1",
                "1"},
            {"CREATE STAGE k5 URL = 's3compat://bucket/path/' ENDPOINT = 's3.amazonaws.com' ->> SELECT 1",
                "1"},
        });
    }
}
