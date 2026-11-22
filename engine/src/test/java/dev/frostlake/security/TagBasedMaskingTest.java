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
 * Tag-based masking (live-verified): a tag carries masking policies, one per data type, set and unset by ALTER TAG,
 * FORCE replacing the policy a type has. Set on a column, or on the table, schema or database holding it, the tag
 * masks every column of a policy's type; a policy attached to the column itself wins over the tag's, a column the
 * tags reaching it give two policies is refused when it is read, a conditional policy reads its further arguments
 * from the columns named as its parameters, and a tag carrying a policy, or a policy a tag carries, is not dropped.
 * POLICY_REFERENCES lists a policy a tag gives a column with the tag.
 */
public class TagBasedMaskingTest extends MeasuredCellsTestSupport {

    @Test
    public void aTagMasksTheColumnsOfItsPolicysType() {
        engine.execute("CREATE TABLE t (a INT, s STRING, n NUMBER(10,0))");
        engine.execute("INSERT INTO t VALUES (1, 'secret', 5)");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm");
        assertAnswer("1|secret|5",
            "SELECT * FROM t");
        engine.execute("ALTER TABLE t MODIFY COLUMN s SET TAG pii = 'yes'");
        assertAnswer("1|MASKED|5",
            "SELECT * FROM t");
        assertAnswer("yes",
            "SELECT SYSTEM$GET_TAG('pii', 't.s', 'column')");
        assertAnswer("SM|S|PII|ACTIVE",
            "SELECT policy_name, ref_column_name, tag_name, policy_status FROM "
            + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'TEST_DB.TEST_SCHEMA.T', "
            + "REF_ENTITY_DOMAIN => 'table')) ORDER BY ref_column_name");
    }

    @Test
    public void policiesOnATagAndHowTheyMeet() {
        engine.execute("CREATE TABLE t (a INT, s STRING, v VARCHAR(10), n NUMBER(10,0))");
        engine.execute("INSERT INTO t VALUES (1, 'secret', 'vv', 5)");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE TAG pii2");
        engine.execute("CREATE MASKING POLICY sm AS (x STRING) RETURNS STRING -> 'MASKED'");
        engine.execute("CREATE MASKING POLICY sm2 AS (x STRING) RETURNS STRING -> 'M2'");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("CREATE MASKING POLICY dm AS (x STRING) RETURNS STRING -> 'DIRECT'");
        engine.execute("CREATE MASKING POLICY cm AS (x STRING, y INT) RETURNS STRING -> CASE WHEN y > 0 THEN 'C'"
            + " ELSE x END");
        engine.execute("CREATE TABLE t2 (s STRING)");
        engine.execute("INSERT INTO t2 VALUES ('two')");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm, MASKING POLICY nm");
        assertAnswer("1|secret|vv|5",
            "SELECT * FROM t");
        engine.execute("ALTER TABLE t SET TAG pii = 'tbl'");
        assertAnswer("-1|MASKED|MASKED|-1",
            "SELECT * FROM t");
        assertAnswer("REFUSED SQl execution error: Tag is already assigned to a masking policy SM with same "
            + "data-type.",
            "ALTER TAG pii SET MASKING POLICY sm2");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm2 FORCE");
        assertAnswer("-1|M2|M2|-1",
            "SELECT * FROM t");
        engine.execute("ALTER TAG pii UNSET MASKING POLICY sm");
        engine.execute("ALTER TAG pii UNSET MASKING POLICY sm2");
        assertAnswer("-1|secret|vv|-1",
            "SELECT * FROM t");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm");
        engine.execute("ALTER TABLE t MODIFY COLUMN s SET MASKING POLICY dm");
        assertAnswer("-1|DIRECT|MASKED|-1",
            "SELECT * FROM t");
        engine.execute("ALTER TABLE t MODIFY COLUMN s UNSET MASKING POLICY");
        engine.execute("ALTER TAG pii2 SET MASKING POLICY sm2");
        engine.execute("ALTER TABLE t MODIFY COLUMN v SET TAG pii2 = 'col'");
        assertAnswer("REFUSED SQL execution error: Column TEST_DB.TEST_SCHEMA.T.V is mapped to multiple "
            + "masking policies by tags.Please contact your local administrator to fix the issue.",
            "SELECT * FROM t");
        engine.execute("ALTER TABLE t MODIFY COLUMN s SET TAG pii = 'col'");
        assertAnswer("REFUSED SQL execution error: Column TEST_DB.TEST_SCHEMA.T.V is mapped to multiple "
            + "masking policies by tags.Please contact your local administrator to fix the issue.",
            "SELECT * FROM t");
        engine.execute("ALTER TABLE t MODIFY COLUMN s SET TAG pii2 = 'col'");
        assertAnswer("REFUSED SQL execution error: Column TEST_DB.TEST_SCHEMA.T.S is mapped to multiple "
            + "masking policies by tags.Please contact your local administrator to fix the issue.",
            "SELECT * FROM t");
        assertAnswer("REFUSED SQL execution error: Column TEST_DB.TEST_SCHEMA.T.S is mapped to multiple "
            + "masking policies by tags.Please contact your local administrator to fix the issue.",
            "SELECT s FROM t");
        assertAnswer("-1",
            "SELECT a FROM t");
        engine.execute("ALTER TABLE t MODIFY COLUMN s UNSET TAG pii2");
        assertAnswer("REFUSED SQL compilation error: Policy SM cannot be dropped/replaced as it is associated "
            + "with one or more entities.",
            "DROP MASKING POLICY sm");
        assertAnswer("REFUSED SQL compilation error: Tag PII2 cannot be dropped/replaced as it is associated "
            + "with one or more policies.",
            "DROP TAG pii2");
        assertAnswer("REFUSED SQl execution error: Tag is already assigned to a masking policy SM with same "
            + "data-type.",
            "ALTER TAG pii SET MASKING POLICY cm");
        engine.execute("ALTER SCHEMA test_schema SET TAG pii2 = 'sch'");
        assertAnswer("M2",
            "SELECT * FROM t2");
        engine.execute("ALTER TAG pii2 UNSET MASKING POLICY sm");
        engine.execute("ALTER TAG pii2 UNSET MASKING POLICY sm2, MASKING POLICY sm2");
        assertAnswer("two",
            "SELECT * FROM t2");
        assertAnswer("NM|A|PII|ACTIVE ;; NM|N|PII|ACTIVE ;; SM|S|PII|ACTIVE ;; SM|V|PII|ACTIVE",
            "SELECT policy_name, ref_column_name, tag_name, policy_status FROM "
            + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'TEST_DB.TEST_SCHEMA.T', "
            + "REF_ENTITY_DOMAIN => 'table')) ORDER BY ref_column_name, policy_name");
        engine.execute("CREATE VIEW vw AS SELECT s, n FROM t");
        assertAnswer("MASKED|-1",
            "SELECT * FROM vw");
        engine.execute("ALTER TAG pii SET MASKING POLICY sm");
        engine.execute("ALTER TAG pii SET MASKING POLICY nm FORCE");
    }

    @Test
    public void conditionalAndDatabaseLevelTags() {
        engine.execute("CREATE TABLE t (s STRING, flag INT, f FLOAT)");
        engine.execute("INSERT INTO t VALUES ('one', 1, 1.5), ('two', 0, 2.5)");
        engine.execute("CREATE TAG tc");
        engine.execute("CREATE TAG td");
        engine.execute("CREATE MASKING POLICY cm AS (x STRING, flag INT) RETURNS STRING -> CASE WHEN flag = 1 "
            + "THEN 'C' ELSE x END");
        engine.execute("CREATE MASKING POLICY nm AS (x NUMBER) RETURNS NUMBER -> -1");
        engine.execute("ALTER TAG tc SET MASKING POLICY cm");
        engine.execute("ALTER TABLE t MODIFY COLUMN s SET TAG tc = 'x'");
        assertAnswer("two ;; C",
            "SELECT s FROM t ORDER BY flag");
        engine.execute("ALTER TAG td SET MASKING POLICY nm");
        engine.execute("ALTER DATABASE test_db SET TAG td = 'db'");
        assertAnswer("-1|1.5 ;; -1|2.5",
            "SELECT flag, f FROM t ORDER BY flag");
        assertAnswer("-1|1.5 ;; -1|2.5",
            "SELECT flag, f FROM t ORDER BY flag");
        engine.execute("ALTER DATABASE test_db UNSET TAG td");
        assertAnswer("0|2.5 ;; 1|1.5",
            "SELECT flag, f FROM t ORDER BY flag");
        engine.execute("ALTER TAG tc UNSET MASKING POLICY cm");
        engine.execute("DROP TAG tc");
    }
}
