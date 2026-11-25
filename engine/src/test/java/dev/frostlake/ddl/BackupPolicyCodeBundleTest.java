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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BACKUP POLICY and CODE BUNDLE, as the account answers them (live-verified): the listings' and descriptions' columns
 * and cells, the properties each kind takes and how each value is judged, the schedule and expiration rules of a
 * backup policy, a code bundle's source location, and the refusals of each statement.
 */
public class BackupPolicyCodeBundleTest extends MeasuredCellsTestSupport {

    private static final String POLICY_COLUMNS = "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), "
        + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), "
        + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), schedule:VARCHAR(16777216), "
        + "expire_after_days:NUMBER(38,0), has_retention_lock:VARCHAR(16777216)";

    private static final String INVALID_SCHEDULE = "REFUSED Invalid schedule was specified. Please refer to the docs "
        + "on what constitutes a valid schedule.";

    @Test
    public void backupPolicyLifecycle() {
        assertCells(new String[][] {
            {"CREATE BACKUP POLICY BP SCHEDULE = '60 MINUTE' EXPIRE_AFTER_DAYS = 7 COMMENT = 'one'",
                "Backup policy BP successfully created."},
            {"SHOW BACKUP POLICIES ->> SELECT \"name\" || '|' || \"database_name\" || '|' || \"schema_name\" || '|' "
                + "|| \"owner_role_type\" || '|' || \"comment\" || '|' || \"schedule\" || '|' || "
                + "\"expire_after_days\" || '|' || \"has_retention_lock\" FROM $1",
                "BP|TEST_DB|TEST_SCHEMA|ROLE|one|60 MINUTE|7|false"},
            {"DESC BACKUP POLICY BP ->> SELECT \"name\" || '|' || \"comment\" || '|' || \"schedule\" || '|' || "
                + "\"expire_after_days\" || '|' || \"has_retention_lock\" FROM $1",
                "BP|one|60 MINUTE|7|false"},
            {"SHOW BACKUP POLICIES ->> SELECT COUNT(*) FROM $1 WHERE \"created_on\" IS NOT NULL AND \"owner\" IS NOT "
                + "NULL", "1"},
            {"CREATE BACKUP POLICY IF NOT EXISTS BP EXPIRE_AFTER_DAYS = 1", "BP already exists, statement succeeded."},
            {"CREATE BACKUP POLICY BP EXPIRE_AFTER_DAYS = 1",
                "REFUSED SQL compilation error: | Object 'BP' already exists."},
            {"CREATE BACKUP POLICY BQ EXPIRE_AFTER_DAYS = 2", "Backup policy BQ successfully created."},
            {"SHOW BACKUP POLICIES LIKE 'BQ' ->> SELECT \"name\" || '|' || COALESCE(\"schedule\", 'null') || '|' || "
                + "\"comment\" || '|' FROM $1", "BQ|null||"},
            {"ALTER BACKUP POLICY BQ SET SCHEDULE = '2 HOURS' COMMENT = 'c'", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BQ UNSET EXPIRE_AFTER_DAYS", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BQ UNSET SCHEDULE", "REFUSED SQL compilation error: Cannot unset schedule if "
                + "expire_after_days is also not set."},
            {"ALTER BACKUP POLICY BQ RENAME TO BR", "Statement executed successfully."},
            {"DESC BACKUP POLICY BR ->> SELECT \"name\" || '|' || \"schedule\" || '|' || "
                + "COALESCE(TO_CHAR(\"expire_after_days\"), 'null') || '|' || \"comment\" FROM $1",
                "BR|2 HOURS|null|c"},
            {"SHOW BACKUP POLICIES STARTS WITH 'BR' ->> SELECT LISTAGG(\"name\", ',') FROM $1", "BR"},
            {"SHOW BACKUP POLICIES LIMIT 1 ->> SELECT LISTAGG(\"name\", ',') FROM $1", "BP"},
            {"SHOW BACKUP POLICIES IN ACCOUNT ->> SELECT COUNT(*) FROM $1 WHERE \"database_name\" = "
                + "CURRENT_DATABASE()", "2"},
            {"ALTER BACKUP POLICY BR SET EXPIRE_AFTER_DAYS = 3", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BR UNSET SCHEDULE", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BR UNSET EXPIRE_AFTER_DAYS", "REFUSED SQL compilation error: Cannot unset "
                + "expire_after_days if schedule is also not set."},
            {"DROP BACKUP POLICY BR", "BR successfully dropped."},
            {"DROP BACKUP POLICY BR", "REFUSED SQL compilation error: | Backup Policy 'TEST_DB.TEST_SCHEMA.BR' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "BACKUP POLICY TEST_DB.TEST_SCHEMA.BR."},
            {"DROP BACKUP POLICY IF EXISTS BR", "Drop statement executed successfully (BR already dropped)."},
            {"ALTER BACKUP POLICY IF EXISTS BR SET COMMENT = 'x'", "Statement executed successfully."},
            {"DESC BACKUP POLICY BR", "REFUSED SQL compilation error: | Backup Policy 'TEST_DB.TEST_SCHEMA.BR' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "BACKUP POLICY TEST_DB.TEST_SCHEMA.BR."},
        });
    }

    @Test
    public void backupPolicyColumns() {
        engine.execute("CREATE BACKUP POLICY BP EXPIRE_AFTER_DAYS = 1");
        assertEquals(POLICY_COLUMNS, shape("SHOW BACKUP POLICIES"));
        assertEquals(POLICY_COLUMNS, shape("DESC BACKUP POLICY BP"));
    }

    @Test
    public void backupPolicyProperties() {
        assertCells(new String[][] {
            {"CREATE BACKUP POLICY P1 FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'Backup Policy'"},
            {"CREATE BACKUP POLICY P2 SCHEDULE = 60",
                "REFUSED SQL compilation error: | invalid value [60] for parameter 'SCHEDULE'"},
            {"CREATE BACKUP POLICY P3 EXPIRE_AFTER_DAYS = '7'",
                "REFUSED SQL compilation error: | invalid value ['7'] for parameter 'EXPIRE_AFTER_DAYS'"},
            {"CREATE BACKUP POLICY P17 EXPIRE_AFTER_DAYS = 1.5",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'EXPIRE_AFTER_DAYS'"},
            {"CREATE BACKUP POLICY P15 EXPIRE_AFTER_DAYS = 1 EXPIRE_AFTER_DAYS = 2",
                "REFUSED SQL compilation error: | duplicate property 'EXPIRE_AFTER_DAYS';"},
            {"CREATE BACKUP POLICY P16 COMMENT = 'only'", "REFUSED SQL compilation error: error line 0 at position 1 | "
                + "missing required argument [one of {schedule, expire_after_days}] for function [create backup "
                + "policy <name>]"},
            {"CREATE BACKUP POLICY P11 EXPIRE_AFTER_DAYS = -1",
                "REFUSED SQL compilation error: Backup policy expire_after_days must be > 0 days, but it is -1 days."},
            {"CREATE BACKUP POLICY P12 EXPIRE_AFTER_DAYS = 100000", "REFUSED SQL compilation error: Backup policy "
                + "cannot be created with expire_after_days over 3,653 days."},
            {"CREATE BACKUP POLICY P13 WITH RETENTION LOCK EXPIRE_AFTER_DAYS = 1", "REFUSED SQL compilation error: "
                + "Backup policy with retention lock cannot be created on accounts below Business Critical Edition."},
            {"CREATE BACKUP POLICY P4 EXPIRE_AFTER_DAYS = 7, COMMENT = 'x'", "Backup policy P4 successfully created."},
            {"ALTER BACKUP POLICY P4 SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'Backup Policy'"},
            {"ALTER BACKUP POLICY P4 UNSET FOO",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'Backup Policy'"},
            {"ALTER BACKUP POLICY P4 SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
            {"ALTER BACKUP POLICY P4 SET EXPIRE_AFTER_DAYS = 3653", "Statement executed successfully."},
            {"ALTER BACKUP POLICY P4 SET EXPIRE_AFTER_DAYS = 0",
                "REFUSED SQL compilation error: Backup policy expire_after_days must be > 0 days, but it is 0 days."},
            {"ALTER BACKUP POLICY P4 SET COMMENT = NULL", "Statement executed successfully."},
            {"DESC BACKUP POLICY P4 ->> SELECT \"comment\" || '|' || \"expire_after_days\" FROM $1", "|3653"},
        });
    }

    @Test
    public void backupPolicySchedules() {
        engine.execute("CREATE BACKUP POLICY BP EXPIRE_AFTER_DAYS = 1");
        assertCells(new String[][] {
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '30 MINUTE'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '59 MINUTE'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '0 HOUR'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '1 DAY'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '60 MIN'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = ' 60   MINUTE '", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = 'USING CRON bad UTC'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = 'USING CRON */5 * * * * UTC'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = 'USING CRON 0,30 * * * * UTC'", INVALID_SCHEDULE},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = 'USING CRON 0 0 * * * Nowhere/Zone'",
                "REFUSED Invalid schedule was specified. \"Nowhere/Zone\" is not a recognized time zone. Please "
                    + "specify time zones accepted by the TIMEZONE parameter."},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '61 minutes'", "Statement executed successfully."},
            {"DESC BACKUP POLICY BP ->> SELECT \"schedule\" FROM $1", "61 minutes"},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '+60 MINUTE'", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '100000 MINUTE'", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = '1 HOUR'", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = 'USING CRON 30 2 * * MON UTC'", "Statement executed successfully."},
            {"ALTER BACKUP POLICY BP SET SCHEDULE = 'USING CRON 0 0 * * * America/Los_Angeles'",
                "Statement executed successfully."},
            {"DESC BACKUP POLICY BP ->> SELECT \"schedule\" FROM $1", "USING CRON 0 0 * * * America/Los_Angeles"},
        });
    }

    @Test
    public void codeBundleLifecycle() {
        engine.execute("CREATE STAGE ISTG");
        assertCells(new String[][] {
            {"CREATE CODE BUNDLE CB FROM '@ISTG/bundle' COMMENT = 'one'",
                "CB successfully created. Warning: source directory '@ISTG/bundle' does not exist or contains no "
                    + "files."},
            {"SHOW CODE BUNDLES ->> SELECT \"name\" || '|' || \"database_name\" || '|' || \"schema_name\" || '|' || "
                + "\"owner_role_type\" || '|' || \"comment\" FROM $1", "CB|TEST_DB|TEST_SCHEMA|ROLE|one"},
            {"DESC CODE BUNDLE CB ->> SELECT \"name\" || '|' || \"comment\" || '|' || \"default_version\" || '|' || "
                + "\"default_version_name\" || '|' || \"default_version_location_uri\" || '|' || "
                + "\"default_version_source_location_uri\" || '|' || \"last_version_name\" || '|' || "
                + "\"last_version_location_uri\" || '|' || \"last_version_source_location_uri\" || '|' || "
                + "\"container_type\" || '|' || \"container_database\" || '|' || \"container_name\" FROM $1",
                "CB|one|LAST|VERSION$1|snow://code bundle/TEST_DB.TEST_SCHEMA.CB/versions/version$1/|@ISTG/bundle/|"
                    + "VERSION$1|snow://code bundle/TEST_DB.TEST_SCHEMA.CB/versions/version$1/|@ISTG/bundle/|Stage|"
                    + "TEST_DB|ISTG"},
            {"DESC CODE BUNDLE CB ->> SELECT COALESCE(\"default_version_alias\", 'n') || "
                + "COALESCE(\"default_version_git_commit_hash\", 'n') || COALESCE(\"last_version_alias\", 'n') || "
                + "COALESCE(\"last_version_git_commit_hash\", 'n') || COALESCE(\"live_version_location_uri\", 'n') || "
                + "COALESCE(\"container_schema\", 'n') FROM $1", "nnnnnn"},
            {"SHOW TERSE CODE BUNDLES ->> SELECT COUNT(*) FROM $1 WHERE \"updated_on\" >= \"created_on\"", "1"},
            {"CREATE CODE BUNDLE CB FROM '@NOSTAGE/bundle'", "REFUSED SQL compilation error: | Object 'CB' already "
                + "exists."},
            {"CREATE CODE BUNDLE IF NOT EXISTS CB FROM '@ISTG/x'", "CB successfully created."},
            {"ALTER CODE BUNDLE CB SET COMMENT = 'two'", "Statement executed successfully."},
            {"SHOW CODE BUNDLES LIKE 'C%' ->> SELECT \"comment\" FROM $1", "two"},
            {"ALTER CODE BUNDLE CB UNSET COMMENT", "Statement executed successfully."},
            {"SHOW CODE BUNDLES ->> SELECT COALESCE(\"comment\", 'null') FROM $1", "null"},
            {"ALTER CODE BUNDLE CB RENAME TO CB2", "REFUSED Unsupported feature 'renaming NOTEBOOK_PROJECT'."},
            {"ALTER CODE BUNDLE CB SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'NOTEBOOK_PROJECT'"},
            {"ALTER CODE BUNDLE CB SET COMMENT = 'a', COMMENT = 'b'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"DROP CODE BUNDLE CB", "CB successfully dropped."},
            {"DROP CODE BUNDLE CB", "REFUSED SQL compilation error: | Code Bundle 'TEST_DB.TEST_SCHEMA.CB' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on CODE "
                + "BUNDLE TEST_DB.TEST_SCHEMA.CB."},
            {"DROP CODE BUNDLE IF EXISTS CB", "Drop statement executed successfully (CB already dropped)."},
            {"SHOW CODE BUNDLES ->> SELECT COUNT(*) FROM $1", "0"},
        });
    }

    @Test
    public void codeBundleSources() {
        engine.execute("CREATE STAGE ISTG");
        engine.execute("CREATE TABLE T1 (a INT)");
        assertCells(new String[][] {
            {"CREATE CODE BUNDLE C1", "REFUSED Failed to validate properties required by Notebook Project: source "
                + "location is missing."},
            {"CREATE CODE BUNDLE C2 FROM '@NOSTAGE/bundle'", "REFUSED The specified stage NOSTAGE does not exist or "
                + "the current role does not have access. Owner must have at least READ on the specified stage."},
            {"CREATE CODE BUNDLE C3 FROM 'nostage'",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'nostage'"},
            {"CREATE CODE BUNDLE C4 FROM '@~/x'",
                "REFUSED SQL compilation error: Copy files not support stage kind: 'USER' for stage '~'."},
            {"CREATE CODE BUNDLE C5 FROM '@%T1/x'",
                "REFUSED SQL compilation error: Copy files not support stage kind: 'TABLE' for stage 'T1'."},
            {"CREATE CODE BUNDLE C5 FROM '@%NOTABLE/x'", "REFUSED The specified stage %NOTABLE does not exist or the "
                + "current role does not have access. Owner must have at least READ on the specified stage."},
            {"CREATE CODE BUNDLE C6 FROM '@ISTG' FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'NOTEBOOK_PROJECT'"},
            {"CREATE CODE BUNDLE C7 FROM '@ISTG/a' TAG (x = 'y')",
                "REFUSED Unsupported feature 'CODE BUNDLE WITH TAG'."},
            {"CREATE CODE BUNDLE C8 FROM @ISTG", "C8 successfully created. Warning: source directory '@ISTG' does "
                + "not exist or contains no files."},
            {"DESC CODE BUNDLE C8 ->> SELECT \"default_version_source_location_uri\" FROM $1", "@ISTG"},
            {"CREATE CODE BUNDLE \"low\" FROM '@istg/a'", "low successfully created. Warning: source directory "
                + "'@istg/a' does not exist or contains no files."},
            {"DESC CODE BUNDLE \"low\" ->> SELECT \"default_version_location_uri\" || '|' || "
                + "\"default_version_source_location_uri\" || '|' || \"container_name\" FROM $1",
                "snow://code bundle/TEST_DB.TEST_SCHEMA.\"low\"/versions/version$1/|@istg/a/|ISTG"},
        });
    }

    @Test
    public void keywordsStayNames() {
        assertCells(new String[][] {
            {"SHOW CODE BUNDLE", "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected "
                + "'BUNDLE'."},
            {"SHOW BACKUP", "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '<EOF>'."},
            {"DROP CODE X", "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'X'."},
            {"DROP BACKUP X", "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'X'."},
            {"CREATE TABLE code (backup INT, bundle INT, bundles INT)", "Table CODE successfully created."},
            {"INSERT INTO code (backup, bundle, bundles) VALUES (1, 2, 3)", "1"},
            {"SELECT backup + bundle + bundles AS code FROM code", "6"},
        });
    }

    @Test
    public void serviceParametersOfAMissingService() {
        assertCells(new String[][] {
            {"SHOW PARAMETERS IN SERVICE NOSVC", "REFUSED SQL compilation error: | Service "
                + "'TEST_DB.TEST_SCHEMA.NOSVC' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on SERVICE TEST_DB.TEST_SCHEMA.NOSVC."},
            {"SHOW PARAMETERS LIKE 'LOG%' FOR SERVICE TEST_SCHEMA.NOSVC", "REFUSED SQL compilation error: | Service "
                + "'TEST_DB.TEST_SCHEMA.NOSVC' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on SERVICE TEST_DB.TEST_SCHEMA.NOSVC."},
            {"SHOW PARAMETERS IN SERVICE", "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show "
                + "parameters of SqlIdentifier{qualifierNames=[], identifierName=SERVICE}'."},
        });
    }
}
