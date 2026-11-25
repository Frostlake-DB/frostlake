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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Masking policies a tag carries: the type the refusal of two of one type names, and how POLICY_REFERENCES lists
 * them, by the policy (the tag as the entity) and by the table or the tag (live-verified).
 */
public class TagPolicyReferencesTest extends MeasuredCellsTestSupport {

    @Test
    public void sameTypePolicies() {
        engine.execute("CREATE TABLE t (a INT, s STRING, v VARCHAR(10), n NUMBER(10,0))");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY sm2 AS (x STRING) RETURNS STRING -> 'M2'");
        engine.execute("CREATE MASKING POLICY vp AS (x VARCHAR) RETURNS VARCHAR -> 'V'");
        engine.execute("CREATE MASKING POLICY v10 AS (x VARCHAR(10)) RETURNS VARCHAR(10) -> 'V10'");
        engine.execute("CREATE MASKING POLICY v20 AS (x VARCHAR(20)) RETURNS VARCHAR(20) -> 'V20'");
        engine.execute("CREATE MASKING POLICY tm AS (x TEXT) RETURNS TEXT -> 'T'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("CREATE MASKING POLICY nm2 AS (x NUMBER(10,2)) RETURNS NUMBER(10,2) -> -1");
        engine.execute("CREATE MASKING POLICY im AS (x INT) RETURNS INT -> -1");
        engine.execute("CREATE MASKING POLICY bm AS (x BINARY) RETURNS BINARY -> NULL");
        engine.execute("CREATE MASKING POLICY bm2 AS (x BINARY) RETURNS BINARY -> NULL");
        engine.execute("CREATE MASKING POLICY cm AS (x CHAR) RETURNS CHAR -> 'C'");
        engine.execute("CREATE MASKING POLICY tzm AS (x TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ -> NULL");
        engine.execute("CREATE MASKING POLICY tzm2 AS (x TIMESTAMP_NTZ(3)) RETURNS TIMESTAMP_NTZ(3) -> NULL");
        engine.execute("CREATE MASKING POLICY fm AS (x FLOAT) RETURNS FLOAT -> NULL");
        engine.execute("CREATE MASKING POLICY fm2 AS (x DOUBLE) RETURNS DOUBLE -> NULL");
        engine.execute("CREATE MASKING POLICY vrm AS (x VARIANT) RETURNS VARIANT -> NULL");
        engine.execute("CREATE MASKING POLICY vrm2 AS (x VARIANT) RETURNS VARIANT -> NULL");
        engine.execute("CREATE MASKING POLICY v16 AS (x VARCHAR(16777216)) RETURNS VARCHAR(16777216) -> 'X'");
        assertCells(new String[][] {
            {"ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY sm2",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "VARCHAR(134217728)."},
            {"ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY vp",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "VARCHAR(134217728)."},
            {"ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY tm",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "VARCHAR(134217728)."},
            {"ALTER TAG pii SET MASKING POLICY nm, MASKING POLICY im",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "NUMBER(38,0)."},
            {"ALTER TAG pii SET MASKING POLICY bm, MASKING POLICY bm2",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "BINARY(67108864)."},
            {"ALTER TAG pii SET MASKING POLICY fm, MASKING POLICY fm2",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "FLOAT."},
            {"ALTER TAG pii SET MASKING POLICY vrm, MASKING POLICY vrm2",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "VARIANT."},
            {"ALTER TAG pii SET MASKING POLICY im, MASKING POLICY nm",
                "REFUSED SQL compilation error: Multiple masking policies are specified with same data type "
                + "NUMBER(38,0)."},
        });
    }

    @Test
    public void policyAlreadyCarried() {
        engine.execute("CREATE TABLE t (a INT, s STRING, v VARCHAR(10), n NUMBER(10,0))");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY sm2 AS (x STRING) RETURNS STRING -> 'M2'");
        engine.execute("CREATE MASKING POLICY vp AS (x VARCHAR) RETURNS VARCHAR -> 'V'");
        engine.execute("CREATE MASKING POLICY v10 AS (x VARCHAR(10)) RETURNS VARCHAR(10) -> 'V10'");
        engine.execute("CREATE MASKING POLICY v20 AS (x VARCHAR(20)) RETURNS VARCHAR(20) -> 'V20'");
        engine.execute("CREATE MASKING POLICY tm AS (x TEXT) RETURNS TEXT -> 'T'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("CREATE MASKING POLICY nm2 AS (x NUMBER(10,2)) RETURNS NUMBER(10,2) -> -1");
        engine.execute("CREATE MASKING POLICY im AS (x INT) RETURNS INT -> -1");
        engine.execute("CREATE MASKING POLICY bm AS (x BINARY) RETURNS BINARY -> NULL");
        engine.execute("CREATE MASKING POLICY bm2 AS (x BINARY) RETURNS BINARY -> NULL");
        engine.execute("CREATE MASKING POLICY cm AS (x CHAR) RETURNS CHAR -> 'C'");
        engine.execute("CREATE MASKING POLICY tzm AS (x TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ -> NULL");
        engine.execute("CREATE MASKING POLICY tzm2 AS (x TIMESTAMP_NTZ(3)) RETURNS TIMESTAMP_NTZ(3) -> NULL");
        engine.execute("CREATE MASKING POLICY fm AS (x FLOAT) RETURNS FLOAT -> NULL");
        engine.execute("CREATE MASKING POLICY fm2 AS (x DOUBLE) RETURNS DOUBLE -> NULL");
        engine.execute("CREATE MASKING POLICY vrm AS (x VARIANT) RETURNS VARIANT -> NULL");
        engine.execute("CREATE MASKING POLICY vrm2 AS (x VARIANT) RETURNS VARIANT -> NULL");
        engine.execute("CREATE MASKING POLICY v16 AS (x VARCHAR(16777216)) RETURNS VARCHAR(16777216) -> 'X'");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm");
        assertCells(new String[][] {
            {"ALTER TAG pii SET MASKING POLICY sm2",
                "REFUSED SQl execution error: Tag is already assigned to a masking policy SM with same "
                + "data-type."},
            {"ALTER TAG pii SET MASKING POLICY v10",
                "REFUSED SQl execution error: Tag is already assigned to a masking policy SM with same "
                + "data-type."},
        });
    }

    @Test
    public void referencesByPolicyAndEntity() {
        engine.execute("CREATE TABLE t (a INT, s STRING, v VARCHAR(10), n NUMBER(10,0))");
        engine.execute("CREATE TABLE t2 (s STRING, w STRING)");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE TAG pii2");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("CREATE MASKING POLICY dm AS (x STRING) RETURNS STRING -> 'D'");
        engine.execute("CREATE MASKING POLICY um AS (x STRING) RETURNS STRING -> 'U'");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY nm");
        engine.execute("ALTER TABLE t SET TAG pii = 'tbl'");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN w SET MASKING POLICY dm");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN s SET TAG pii = 'col'");
        assertCells(new String[][] {
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'SM'))",
                "SM:PII:TAG:-:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'nm'))",
                "NM:PII:TAG:-:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'DM'))",
                "DM:T2:TABLE:W:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'UM'))",
                ""},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'T', REF_ENTITY_DOMAIN => "
                + "'table'))",
                "NM:T:TABLE:A:PII:ACTIVE NM:T:TABLE:N:PII:ACTIVE SM:T:TABLE:S:PII:ACTIVE "
                + "SM:T:TABLE:V:PII:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'T2', REF_ENTITY_DOMAIN => "
                + "'table'))",
                "SM:T2:TABLE:S:PII:ACTIVE DM:T2:TABLE:W:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'PII', REF_ENTITY_DOMAIN => "
                + "'tag'))",
                "NM:PII:TAG:-:-:ACTIVE SM:PII:TAG:-:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'PII2', REF_ENTITY_DOMAIN => "
                + "'TAG'))",
                ""},
        });
    }

    @Test
    public void referencesWithDirectPolicy() {
        engine.execute("CREATE TABLE t (a INT, s STRING, v VARCHAR(10), n NUMBER(10,0))");
        engine.execute("CREATE TABLE t2 (s STRING, w STRING)");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE TAG pii2");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("CREATE MASKING POLICY dm AS (x STRING) RETURNS STRING -> 'D'");
        engine.execute("CREATE MASKING POLICY um AS (x STRING) RETURNS STRING -> 'U'");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY nm");
        engine.execute("ALTER TABLE t SET TAG pii = 'tbl'");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN w SET MASKING POLICY dm");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN s SET TAG pii = 'col'");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN s SET MASKING POLICY um");
        assertCells(new String[][] {
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'SM'))",
                "SM:PII:TAG:-:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'UM'))",
                "UM:T2:TABLE:S:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'T2', REF_ENTITY_DOMAIN => "
                + "'table'))",
                "UM:T2:TABLE:S:-:ACTIVE DM:T2:TABLE:W:-:ACTIVE"},
        });
    }

    @Test
    public void referencesWithTwoTags() {
        engine.execute("CREATE TABLE t (a INT, s STRING, v VARCHAR(10), n NUMBER(10,0))");
        engine.execute("CREATE TABLE t2 (s STRING, w STRING)");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE TAG pii2");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("CREATE MASKING POLICY dm AS (x STRING) RETURNS STRING -> 'D'");
        engine.execute("CREATE MASKING POLICY um AS (x STRING) RETURNS STRING -> 'U'");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY nm");
        engine.execute("ALTER TABLE t SET TAG pii = 'tbl'");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN w SET MASKING POLICY dm");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN s SET TAG pii = 'col'");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN s SET MASKING POLICY um");
        engine.execute("ALTER TABLE t MODIFY COLUMN s SET TAG pii2 = 'x'");
        engine.execute("ALTER TAG pii2 SET MASKING POLICY dm");
        assertCells(new String[][] {
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'DM'))",
                "DM:T2:TABLE:W:-:ACTIVE DM:PII2:TAG:-:-:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'T', REF_ENTITY_DOMAIN => "
                + "'table'))",
                "NM:T:TABLE:A:PII:ACTIVE NM:T:TABLE:N:PII:ACTIVE "
                + "DM:T:TABLE:S:PII2:MULTIPLE_MASKING_POLICY_ASSIGNED_TO_THE_COLUMN "
                + "SM:T:TABLE:S:PII:MULTIPLE_MASKING_POLICY_ASSIGNED_TO_THE_COLUMN SM:T:TABLE:V:PII:ACTIVE"},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'PII2', REF_ENTITY_DOMAIN => "
                + "'tag'))",
                "DM:PII2:TAG:-:-:ACTIVE"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'SM')) WHERE "
                + "ref_entity_domain = 'TAG' AND ref_column_name IS NULL AND tag_name IS NULL AND "
                + "ref_arg_column_names IS NULL",
                "1"},
        });
    }

    @Test
    public void referencesAfterUnset() {
        engine.execute("CREATE TABLE t (a INT, s STRING, v VARCHAR(10), n NUMBER(10,0))");
        engine.execute("CREATE TABLE t2 (s STRING, w STRING)");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE TAG pii2");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("CREATE MASKING POLICY dm AS (x STRING) RETURNS STRING -> 'D'");
        engine.execute("CREATE MASKING POLICY um AS (x STRING) RETURNS STRING -> 'U'");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY nm");
        engine.execute("ALTER TABLE t SET TAG pii = 'tbl'");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN w SET MASKING POLICY dm");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN s SET TAG pii = 'col'");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN s SET MASKING POLICY um");
        engine.execute("ALTER TABLE t MODIFY COLUMN s SET TAG pii2 = 'x'");
        engine.execute("ALTER TAG pii2 SET MASKING POLICY dm");
        engine.execute("ALTER TAG pii UNSET MASKING POLICY sm");
        assertCells(new String[][] {
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'SM'))",
                ""},
            {"SELECT LISTAGG(policy_name || ':' || ref_entity_name || ':' || ref_entity_domain || ':' || "
                + "NVL(ref_column_name, '-') || ':' || NVL(tag_name, '-') || ':' || policy_status, ' ') WITHIN "
                + "GROUP (ORDER BY ref_entity_domain, ref_entity_name, ref_column_name, policy_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'PII', REF_ENTITY_DOMAIN => "
                + "'tag'))",
                "NM:PII:TAG:-:-:ACTIVE"},
        });
    }
}
