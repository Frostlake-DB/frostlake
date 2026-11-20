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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a stage's CREDENTIALS let their AWS keys be (live-verified): a colon is no part of an AWS_KEY_ID, an
 * AWS_SECRET_KEY or an AWS_TOKEN, at CREATE STAGE and at ALTER STAGE alike and before the stage is looked up, and an
 * S3 stage's key id and secret key are not left blank.
 */
public class StageCredentialKeysTest extends MeasuredCellsTestSupport {

    private static final String URL = "URL = 's3://fl-no-such-bucket-keys/p/'";

    private static final String SET = "ALTER STAGE key_stage SET CREDENTIALS = ";

    private static final String REQUIRED = "REFUSED Valid credentials are required for this stage location.";

    @BeforeEach
    public void createStage() {
        engine.execute("CREATE OR REPLACE STAGE key_stage " + URL);
    }

    private static String refused(final String value, final String property) {
        return "REFUSED SQL compilation error: | invalid value '" + value + "' for property '" + property + "'";
    }

    private void assertTaken(final String sql) {
        final String answer = answer(sql);
        assertFalse(answer.startsWith("REFUSED"), sql + " => " + answer);
    }

    @Test
    public void aKeyHoldingAColonIsRefusedByItsValue() {
        assertCells(new String[][] {
            {SET + "(AWS_KEY_ID = 'a:' AWS_SECRET_KEY = 'b')", refused("a:", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = ':a' AWS_SECRET_KEY = 'b')", refused(":a", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = ':' AWS_SECRET_KEY = 'b')", refused(":", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = '1:2' AWS_SECRET_KEY = 'b')", refused("1:2", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = 'a b:c' AWS_SECRET_KEY = 'b')", refused("a b:c", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = 's3://x' AWS_SECRET_KEY = 'b')", refused("s3://x", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b:')", refused("b:", "AWS_SECRET_KEY")},
            {SET + "(AWS_KEY_ID = 'a' AWS_SECRET_KEY = 's3://x/')", refused("s3://x/", "AWS_SECRET_KEY")},
            {SET + "(AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b' AWS_TOKEN = 'c:')", refused("c:", "AWS_TOKEN")},
            {SET + "(AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b' AWS_TOKEN = ':c')", refused(":c", "AWS_TOKEN")},
            // The first key written is the one refused, under its name as written.
            {SET + "(AWS_KEY_ID = 'a:' AWS_SECRET_KEY = 'b:' AWS_TOKEN = 'c:')", refused("a:", "AWS_KEY_ID")},
            {SET + "(AWS_TOKEN = 'c:' AWS_SECRET_KEY = 'b:' AWS_KEY_ID = 'a:')", refused("c:", "AWS_TOKEN")},
            {SET + "(aws_key_id = 'a:' AWS_SECRET_KEY = 'b')", refused("a:", "aws_key_id")},
            // A colon is refused before a blank key is.
            {SET + "(AWS_KEY_ID = 'a:' AWS_SECRET_KEY = '')", refused("a:", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = '' AWS_SECRET_KEY = 'b:')", refused("b:", "AWS_SECRET_KEY")},
            {SET + "(AWS_KEY_ID = 'a:')", refused("a:", "AWS_KEY_ID")},
            {SET + "(AWS_KEY_ID = 'a:' AWS_SECRET_KEY = 'b') COMMENT = 'c'", refused("a:", "AWS_KEY_ID")},
            {"ALTER STAGE key_stage SET " + URL + " CREDENTIALS = (AWS_KEY_ID = 'a:' AWS_SECRET_KEY = 'b')",
                refused("a:", "AWS_KEY_ID")},
        });
    }

    @Test
    public void aKeyIsJudgedBeforeItsStageIs() {
        engine.execute("CREATE OR REPLACE STAGE key_internal");
        engine.execute("CREATE OR REPLACE STAGE key_azure URL = 'azure://flkeys.blob.core.windows.net/c/p/'");
        final String keys = " CREDENTIALS = (AWS_KEY_ID = 'a:' AWS_SECRET_KEY = 'b')";
        assertCells(new String[][] {
            {"ALTER STAGE IF EXISTS no_such_key_stage SET" + keys, refused("a:", "AWS_KEY_ID")},
            {"ALTER STAGE no_such_key_stage SET" + keys, refused("a:", "AWS_KEY_ID")},
            {"ALTER STAGE key_internal SET" + keys, refused("a:", "AWS_KEY_ID")},
            {"ALTER STAGE key_azure SET" + keys, refused("a:", "AWS_KEY_ID")},
            {"CREATE STAGE key_new " + URL + keys, refused("a:", "AWS_KEY_ID")},
            {"CREATE STAGE key_new " + URL + " CREDENTIALS = (AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b:')",
                refused("b:", "AWS_SECRET_KEY")},
            {"CREATE STAGE key_new " + URL
                + " CREDENTIALS = (AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b' AWS_TOKEN = 'c:')",
                refused("c:", "AWS_TOKEN")},
            {"CREATE STAGE key_stage " + URL + keys, refused("a:", "AWS_KEY_ID")},
            {"CREATE STAGE IF NOT EXISTS key_stage " + URL + keys, refused("a:", "AWS_KEY_ID")},
            {"CREATE OR REPLACE STAGE key_stage " + URL + keys, refused("a:", "AWS_KEY_ID")},
            // A FILE_FORMAT group of the statement is judged before the key is.
            {"ALTER STAGE key_stage SET" + keys + " FILE_FORMAT = (TYPE = NOPE)",
                "REFUSED SQL compilation error: | invalid value [NOPE] for parameter 'TYPE'"},
            {"CREATE STAGE key_new " + URL + keys + " FILE_FORMAT = (TYPE = NOPE)",
                "REFUSED SQL compilation error: | invalid value [NOPE] for parameter 'TYPE'"},
            {"ALTER STAGE key_internal SET CREDENTIALS = (AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or"
                    + " temporary stage."},
        });
        // A replacement refused leaves the stage it would have replaced.
        assertAnswer("KEY_STAGE", "SHOW STAGES LIKE 'KEY_STAGE' ->> SELECT \"name\" FROM $1");
    }

    @Test
    public void anS3StageNeedsItsKeyIdAndItsSecretKeyFilled() {
        assertCells(new String[][] {
            {SET + "(AWS_KEY_ID = '' AWS_SECRET_KEY = 'b')", REQUIRED},
            {SET + "(AWS_KEY_ID = 'a' AWS_SECRET_KEY = '')", REQUIRED},
            {SET + "(AWS_KEY_ID = '' AWS_SECRET_KEY = '')", REQUIRED},
            {SET + "(AWS_KEY_ID = ' ' AWS_SECRET_KEY = ' ')", REQUIRED},
            {SET + "(AWS_SECRET_KEY = '' AWS_KEY_ID = 'a/')", REQUIRED},
            {"CREATE STAGE key_new " + URL + " CREDENTIALS = (AWS_KEY_ID = '' AWS_SECRET_KEY = 'b')", REQUIRED},
            {"CREATE STAGE key_new " + URL + " CREDENTIALS = (AWS_KEY_ID = 'a' AWS_SECRET_KEY = '')", REQUIRED},
        });
        assertTaken(SET + "(AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b' AWS_TOKEN = '')");
    }

    @Test
    public void anyOtherTextIsAKey() {
        for (final String key : new String[] {"a", "A", "1", "a/", "a b", "a-b", "a_b", "a.b", "a+b", "a=b", "a@b",
            "a\"b", "a''b", "a,b", "a;b", "a?b", "a#b", "a%b", "a&b", "a*b", "a(b", "a!b", "a$b", "a~b", "a|b",
            "a<b", "a[b", "a{b", "a^b", "a`b", " a"}) {
            assertTaken(SET + "(AWS_KEY_ID = '" + key + "' AWS_SECRET_KEY = 'b')");
        }
        assertTaken(SET + "(AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b c' AWS_TOKEN = 'c d')");
        engine.execute("SET fl_key = 'x/y'");
        assertTaken(SET + "(AWS_KEY_ID = $fl_key AWS_SECRET_KEY = $fl_key)");
        // An Azure stage's token is not judged.
        engine.execute("CREATE OR REPLACE STAGE key_azure URL = 'azure://flkeys.blob.core.windows.net/c/p/'");
        assertTaken("ALTER STAGE key_azure SET CREDENTIALS = (AZURE_SAS_TOKEN = 'a:')");
        assertTaken("CREATE OR REPLACE STAGE key_azure URL = 'azure://flkeys.blob.core.windows.net/c/p/'"
            + " CREDENTIALS = (AZURE_SAS_TOKEN = 's3://x/')");
    }

    @Test
    public void aSessionVariableIsJudgedAsTheTextItHolds() {
        engine.execute("SET fl_key = 's3://fl-no-such-bucket-keys/p/'");
        assertAnswer(refused("s3://fl-no-such-bucket-keys/p/", "AWS_KEY_ID"),
            SET + "(AWS_KEY_ID = $fl_key AWS_SECRET_KEY = $fl_key)");
    }
}
