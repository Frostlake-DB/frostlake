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
 * What a stage's CREDENTIALS may hold (live-verified): the value as written is judged first, whatever the stage is - a
 * value that is no property list, an empty list, a key of no name the list knows, a value that is no text - and then
 * the last list the statement writes against the stage's location: an S3 location takes an AWS_KEY_ID with an
 * AWS_SECRET_KEY and optionally an AWS_TOKEN, or an AWS_ROLE alone of a role's shape; an Azure location an
 * AZURE_SAS_TOKEN alone; a GCS location none. The FILE_FORMAT, DIRECTORY and ENCRYPTION lists are judged by their own
 * shape first, and the stage's name, its location's own checks and its other properties after.
 */
public class StageCredentialSetsTest extends MeasuredCellsTestSupport {

    private static final String S3 = "URL = 's3://fl-no-such-bucket-keys/p/'";

    private static final String AZURE = "URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/'";

    /** Each location takes its own set, a key written twice counting once with its last value. */
    @Test
    public void eachLocationTakesItsOwnSet() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE t1 " + S3 + " CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x')",
                "Stage area T1 successfully created."},
            {"CREATE OR REPLACE STAGE t2 " + S3 + " "
                + "CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x' AWS_TOKEN = 'x')",
                "Stage area T2 successfully created."},
            {"CREATE OR REPLACE STAGE t3 " + S3 + " CREDENTIALS = (AWS_KEY_ID = 'a/' AWS_SECRET_KEY = 'x')",
                "Stage area T3 successfully created."},
            {"CREATE OR REPLACE STAGE t4 " + S3 + " CREDENTIALS = (AWS_ROLE = 'arn:aws:iam::000000000000:role/x')",
                "Stage area T4 successfully created."},
            {"CREATE OR REPLACE STAGE t5 " + S3 + " CREDENTIALS = (AWS_ROLE = ':b:c:d:e:f')",
                "Stage area T5 successfully created."},
            {"CREATE OR REPLACE STAGE t6 " + S3 + " "
                + "CREDENTIALS = (AWS_ROLE = 'x' AWS_ROLE = 'arn:aws:iam::000000000000:role/x')",
                "Stage area T6 successfully created."},
            {"CREATE OR REPLACE STAGE t7 " + S3 + " "
                + "CREDENTIALS = (AWS_KEY_ID = 'x' AWS_KEY_ID = 'y' AWS_SECRET_KEY = 'x')",
                "Stage area T7 successfully created."},
            {"CREATE OR REPLACE STAGE t8 " + S3 + " "
                + "CREDENTIALS = (AWS_KEY_ID = 'x') CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x')",
                "Stage area T8 successfully created."},
            {"CREATE OR REPLACE STAGE t9 " + AZURE + " CREDENTIALS = (AZURE_SAS_TOKEN = 'x')",
                "Stage area T9 successfully created."},
            {"CREATE OR REPLACE STAGE t10 " + AZURE + " CREDENTIALS = (AZURE_SAS_TOKEN = '')",
                "Stage area T10 successfully created."},
            {"CREATE OR REPLACE STAGE t11 " + AZURE + " CREDENTIALS = (AZURE_SAS_TOKEN = 'a:')",
                "Stage area T11 successfully created."},
            {"ALTER STAGE t1 SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x' AWS_TOKEN = 'x')",
                "Statement executed successfully."},
            {"ALTER STAGE t1 SET CREDENTIALS = (AWS_KEY_ID = 'x') "
                + "CREDENTIALS = (AWS_KEY_ID = 'x', AWS_SECRET_KEY = 'x')",
                "Statement executed successfully."},
            {"ALTER STAGE t9 SET CREDENTIALS = (AZURE_SAS_TOKEN = '')",
                "Statement executed successfully."},
        });
    }

    @Test
    public void aCreateAndAnAlterJudgeTheKeysAtTheLocation() {
        engine.execute("CREATE OR REPLACE STAGE ks3 URL = 's3://fl-no-such-bucket-keys/p/'");
        engine.execute("CREATE OR REPLACE STAGE kaz URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/'");
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE c2 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE c3 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE c4 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_TOKEN ="
                + " 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE c5 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AZURE_SAS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE c6 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x' AWS_SECRET_KEY = 'x' AZURE_SAS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE c7 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"CREATE OR REPLACE STAGE c8 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE c10 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x' AWS_SECRET_KEY = 'x' FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE c12 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE ="
                + " 'x')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE g1 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x' AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE g2 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AZURE_SAS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE g3 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"CREATE OR REPLACE STAGE g4 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE g5 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE g6 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = '' AWS_SECRET_KEY = '')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE g7 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'a:' AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid value 'a:' for property 'AWS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE g8 URL = 'gcs://fl-no-such-bucket-keys/p/'",
                "REFUSED SQL compilation error: | Creation of stages with direct credentials, including "
                + "accessing public stages, has been forbidden for GCS stages. See your account administrator "
                + "for details."},
            {"CREATE OR REPLACE STAGE z1 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"CREATE OR REPLACE STAGE z4 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AWS_KEY_ID = '' AWS_SECRET_KEY = '')",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"CREATE OR REPLACE STAGE z5 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"CREATE OR REPLACE STAGE z6 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE z7 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"CREATE OR REPLACE STAGE z8 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AZURE_SAS_TOKEN = 'x' AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AZURE_SAS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x' AZURE_SAS_TOKEN = "
                + "'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = '' AWS_SECRET_KEY = 'x')",
                "REFUSED Valid credentials are required for this stage location."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = '')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x' FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_ROLE = 'x')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"ALTER STAGE kaz SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"ALTER STAGE kaz SET CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE kaz SET CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE kaz SET CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
        });
    }

    @Test
    public void theValueAsWrittenIsJudgedBeforeTheStage() {
        engine.execute("CREATE OR REPLACE STAGE ks3 URL = 's3://fl-no-such-bucket-keys/p/'");
        engine.execute("CREATE OR REPLACE STAGE kaz URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/'");
        engine.execute("CREATE OR REPLACE STAGE kin");
        assertCells(new String[][] {
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x' FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (FOO = 'x' AWS_KEY_ID = 'a:')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'a:' FOO = 'x')",
                "REFUSED SQL compilation error: | invalid value 'a:' for property 'AWS_KEY_ID'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AZURE_SAS_TOKEN = 'x' AWS_KEY_ID = 'a:')",
                "REFUSED SQL compilation error: | invalid value 'a:' for property 'AWS_KEY_ID'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (foo = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'foo'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 1 AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AWS_KEY_ID'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = '')",
                "REFUSED Valid credentials are required for this stage location."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = ' ' AWS_SECRET_KEY = ' ')",
                "REFUSED Valid credentials are required for this stage location."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_ROLE = 'x' AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x' AWS_ROLE = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_ROLE = '')",
                "REFUSED Valid credentials are required for this stage location."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AZURE_CLIENT_ID = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'AZURE_CLIENT_ID'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (GCS_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'GCS_KEY'"},
            {"ALTER STAGE kaz SET URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AZURE_SAS_TOKEN = "
                + "'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x') URL = "
                + "'azure://flnosuchacct.blob.core.windows.net/c/p/'",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"ALTER STAGE kin SET CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an "
                + "internal or temporary stage."},
            {"ALTER STAGE kin SET CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE kin SET CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE nosuch SET CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE nosuch SET CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x') COMMENT = 'c'",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x') FILE_FORMAT = (FOO = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x') ENCRYPTION = (TYPE = 'AZURE_CSE')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x') DIRECTORY = (FOO = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x') ENDPOINT = 'x.com'",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE k1 CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE k2 CREDENTIALS = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"CREATE OR REPLACE STAGE k3 CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | invalid stage (K3): location not specified"},
            {"CREATE OR REPLACE STAGE k4 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x') ENCRYPTION = (TYPE = 'AZURE_CSE')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE k5 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x') ENDPOINT = 'x.com'",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE k6 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x') FILE_FORMAT = (FOO = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE k7 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x') DIRECTORY = (FOO = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE k9 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x') BAR = 1",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE k10 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 'x')"
                + " ENCRYPTION = (TYPE = 'AZURE_CSE')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE k11 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_KEY_ID = 'x') ENCRYPTION = (TYPE = 'AWS_SSE_S3')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE k12 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x' AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE k13 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x' AZURE_SAS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE k14 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE "
                + "= 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE k15 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_SECRET_KEY = 'x' AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE k16 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AZURE_CLIENT_ID = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'AZURE_CLIENT_ID'"},
            {"CREATE OR REPLACE STAGE k17 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AZURE_CLIENT_ID = 'x' AZURE_CLIENT_SECRET = 'x' AZURE_TENANT_ID = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'AZURE_CLIENT_ID'"},
            {"CREATE OR REPLACE STAGE k18 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AZURE_CLIENT_ID = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'AZURE_CLIENT_ID'"},
            {"CREATE OR REPLACE STAGE k19 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AWS_ROLE = 'x')",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"CREATE OR REPLACE STAGE k20 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AZURE_CLIENT_ID = 'x' AZURE_CLIENT_SECRET = 'x' AZURE_TENANT_ID = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'AZURE_CLIENT_ID'"},
            {"CREATE OR REPLACE STAGE k21 URL = 's3gov://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE k22 URL = 's3compat://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE k23 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x' AWS_SECRET_KEY = 'x' AWS_ROLE = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE k24 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_TOKEN "
                + "= '')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE k27 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AZURE_SAS_TOKEN = 1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AZURE_SAS_TOKEN'"},
            {"CREATE OR REPLACE STAGE k28 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = TRUE AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'AWS_KEY_ID'"},
        });
    }

    @Test
    public void theOtherListsAreJudgedFirst() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE e1 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'a:') ENCRYPTION = (TYPE = 'AZURE_CSE')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE e2 URL = 's3://fl-no-such-bucket-keys/p/' ENCRYPTION = (TYPE = "
                + "'AZURE_CSE') CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE e4 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 'x') "
                + "FILE_FORMAT = (BAR = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'BAR'"},
            {"CREATE OR REPLACE STAGE e5 URL = 's3://fl-no-such-bucket-keys/p/' FILE_FORMAT = (BAR = 1) "
                + "CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'BAR'"},
            {"CREATE OR REPLACE STAGE e6 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = () "
                + "FILE_FORMAT = (BAR = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'BAR'"},
            {"CREATE OR REPLACE STAGE e7 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 1) FILE_FORMAT = (BAR = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'BAR'"},
            {"CREATE OR REPLACE STAGE e8 URL = 's3://fl-no-such-bucket-keys/p/' BAR = 1 CREDENTIALS = "
                + "(AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE e10 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 'x')"
                + " BAR = 1",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE e12 URL = 'gcs://fl-no-such-bucket-keys/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_S3') CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE e14 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x') ENCRYPTION = (TYPE = 'GCS_SSE_KMS')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE e15 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x') STORAGE_INTEGRATION = nosuch",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE e19 URL = 'azure://flnosuchacct.blob.core.windows.net/c/p/' "
                + "CREDENTIALS = (AWS_KEY_ID = 'x') ENDPOINT = 'x.com'",
                "REFUSED SQL compilation error: | Invalid Azure credentials specified. Provide only "
                + "AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID."},
            {"CREATE OR REPLACE STAGE e20 URL = 's3://A_B/p/' CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE e21 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x') DIRECTORY = (ENABLE = TRUE AUTO_REFRESH = TRUE)",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE e22 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 'x')"
                + " CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE f1 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE f2 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_TOKEN "
                + "= 'x')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE f3 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x' AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE f4 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x' AZURE_SAS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE f5 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AZURE_SAS_TOKEN = 'x' AWS_ROLE = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE f6 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_SECRET_KEY = 'x' AZURE_SAS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE f7 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x' AWS_SECRET_KEY = '')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE f8 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE ="
                + " '')",
                "REFUSED SQL compilation error: | GCS credential is invalid"},
            {"CREATE OR REPLACE STAGE f9 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AZURE_SAS_TOKEN = '')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE f10 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_KEY_ID = '')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE f11 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_SECRET_KEY = '')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE r2 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'arn:x')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE r5 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "' ')",
                "REFUSED Valid credentials are required for this stage location."},
            {"CREATE OR REPLACE STAGE r8 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'arn:aws:iam::000000000000:role/x' AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE r11 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE ="
                + " 'a:b')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE r17 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE ="
                + " 'arn:aws:iam::000000000000:role/x' AWS_ROLE = 'x')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
        });
    }

    @Test
    public void aRoleAndRepeatedKeys() {
        engine.execute("CREATE OR REPLACE STAGE ks3 URL = 's3://fl-no-such-bucket-keys/p/'");
        assertCells(new String[][] {
            {"CREATE STAGE ks3 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE STAGE IF NOT EXISTS ks3 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AWS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE STAGE ks3 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE STAGE IF NOT EXISTS ks3 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = "
                + "'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE h5 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID "
                + "= 'x' AWS_SECRET_KEY = 'x') CREDENTIALS = (AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"ALTER STAGE ks3 SET CREDENTIALS = (AWS_KEY_ID = 'x' AWS_SECRET_KEY = 'x') CREDENTIALS = "
                + "(AWS_TOKEN = 'x')",
                "REFUSED SQL compilation error: | Invalid AWS external stage credentials specified. Provide "
                + "only AWS_KEY_ID, AWS_SECRET_KEY, and optionally AWS_TOKEN."},
            {"CREATE OR REPLACE STAGE h7 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(AZURE_SAS_TOKEN = 'x' AZURE_SAS_TOKEN = 'y')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE h8 URL = 'gcs://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'x' aws_key_id = 'y')",
                "REFUSED SQL compilation error: | GCS service account key or access token is missing"},
            {"CREATE OR REPLACE STAGE h9 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (aws_key_id "
                + "= 1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'aws_key_id'"},
            {"CREATE OR REPLACE STAGE h10 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = abc)",
                "REFUSED SQL compilation error: | invalid value [abc] for parameter 'AWS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE h11 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = -1)",
                "REFUSED SQL compilation error: | invalid value [-1] for parameter 'AWS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE h14 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 1 FOO = 'x')",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AWS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE h15 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (FOO = 1 "
                + "AWS_KEY_ID = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE h16 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 1 AWS_SECRET_KEY = 'a:')",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AWS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE h17 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = 'a:' AWS_SECRET_KEY = 1)",
                "REFUSED SQL compilation error: | invalid value 'a:' for property 'AWS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE h18 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE ="
                + " 1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AWS_ROLE'"},
            {"CREATE OR REPLACE STAGE h20 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = 'x'",
                "REFUSED SQL compilation error: | invalid value [Not a property list: 'x'] for parameter "
                + "'{1}'"},
            {"CREATE OR REPLACE STAGE h21 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_KEY_ID"
                + " = ('x'))",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'AWS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE h22 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = "
                + "(\"AWS_KEY_ID\" = 'x' AWS_SECRET_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid parameter '\"AWS_KEY_ID\"'"},
            {"CREATE OR REPLACE STAGE q2 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'a:b:c:d:e')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE q3 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'a:b:c')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE q4 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'a:b:c:d:e:f:g')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE q5 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "':::::')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE q6 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'a:b:c:d')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE q11 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE ="
                + " 'a:b:c:d:e:')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE w2 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'a::::f')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE w7 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "' : : : : : ')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE w8 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE = "
                + "'a:b:c:d:e: ')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE w10 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = (AWS_ROLE ="
                + " '::::: ')",
                "REFUSED SQL compilation error: | AWS_ROLE credentials are invalid."},
            {"CREATE OR REPLACE STAGE x1 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = 1",
                "REFUSED SQL compilation error: | invalid value [Not a property list: 1] for parameter '{1}'"},
            {"CREATE OR REPLACE STAGE x2 URL = 's3://fl-no-such-bucket-keys/p/' CREDENTIALS = abc",
                "REFUSED SQL compilation error: | invalid value [Not a property list: abc] for parameter "
                + "'{1}'"},
            {"ALTER STAGE nosuch SET CREDENTIALS = 'x'",
                "REFUSED SQL compilation error: | invalid value [Not a property list: 'x'] for parameter "
                + "'{1}'"},
        });
    }
}
