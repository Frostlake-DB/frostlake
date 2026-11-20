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
 * What the properties after a key come to once the statement has run (live-verified):
 *
 * <pre>
 *   ALTER TABLE … ADD CONSTRAINT … DEFERRABLE INITIALLY DEFERRED ENABLE RELY     succeeds and adds no key, for a
 *                                                                                PRIMARY KEY, a UNIQUE and a
 *                                                                                FOREIGN KEY alike: no listing
 *                                                                                shows one and a DROP CONSTRAINT
 *                                                                                then finds none
 *   ALTER TABLE … ADD COLUMN c … UNIQUE with the same properties                 adds the column and no key
 *   CREATE OR REPLACE TABLE whose key properties are refused                     leaves the standing table as it
 *                                                                                was, rows and keys
 *   ALTER TABLE … ADD COLUMN whose key properties are refused                    adds no column
 * </pre>
 */
public class KeyPropertiesRefusedBeforeReplaceTest extends MeasuredCellsTestSupport {

    private static final String TAIL = " DEFERRABLE INITIALLY DEFERRED ENABLE RELY";

    private static final String NOT_DEFERRABLE = "REFUSED SQL compilation error: | Constraint is not deferrable";

    private static final String CONFLICT = "REFUSED SQL compilation error: | Conflicting constraint properties";

    @Test
    public void theDeferrableEnabledTailAddsNoKey() {
        engine.execute("CREATE OR REPLACE TABLE kp_p (id NUMBER NOT NULL)");
        engine.execute("CREATE OR REPLACE TABLE kp_t (id NUMBER NOT NULL, t_id NUMBER, p_id NUMBER)");
        engine.execute("ALTER TABLE kp_p ADD CONSTRAINT kp_pk_p PRIMARY KEY (\"ID\")" + TAIL);
        engine.execute("ALTER TABLE kp_t ADD CONSTRAINT kp_uq UNIQUE (\"T_ID\")" + TAIL);
        engine.execute("ALTER TABLE kp_t ADD CONSTRAINT kp_pk PRIMARY KEY (\"ID\")" + TAIL);
        engine.execute("ALTER TABLE kp_t ADD CONSTRAINT kp_fk FOREIGN KEY (\"P_ID\") REFERENCES kp_p (\"ID\") "
            + "MATCH FULL ON UPDATE NO ACTION ON DELETE NO ACTION" + TAIL);
        assertCells(new String[][] {
            {"SHOW PRIMARY KEYS IN TABLE kp_t", ""},
            {"SHOW PRIMARY KEYS IN TABLE kp_p", ""},
            {"SHOW UNIQUE KEYS IN TABLE kp_t", ""},
            {"SHOW IMPORTED KEYS IN TABLE kp_t", ""},
            {"SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_name IN ('KP_T', 'KP_P')", "0"},
            {"SELECT GET_DDL('TABLE', 'kp_t')",
                "create or replace TABLE KP_T ( | \tID NUMBER(38,0) NOT NULL, | \tT_ID NUMBER(38,0), | "
                    + "\tP_ID NUMBER(38,0) | );"},
            {"ALTER TABLE kp_t DROP CONSTRAINT kp_fk",
                "REFUSED SQL compilation error: | constraint 'KP_FK' does not exist"},
            {"ALTER TABLE kp_t DROP CONSTRAINT kp_uq",
                "REFUSED SQL compilation error: | constraint 'KP_UQ' does not exist"},
            {"ALTER TABLE kp_t DROP CONSTRAINT kp_pk",
                "REFUSED SQL compilation error: | constraint 'KP_PK' does not exist"},
        });
    }

    @Test
    public void aKeyWithoutTheTailIsKeptBesideOneLeftOut() {
        engine.execute("CREATE OR REPLACE TABLE kp_a (id NUMBER NOT NULL, t_id NUMBER)");
        engine.execute("ALTER TABLE kp_a ADD COLUMN c NUMBER CONSTRAINT kp_u2 UNIQUE" + TAIL);
        engine.execute("ALTER TABLE kp_a ADD COLUMN c2 NUMBER CONSTRAINT kp_u3 UNIQUE RELY");
        engine.execute("SHOW UNIQUE KEYS IN TABLE kp_a");
        assertAnswer("C2|KP_U3|true", "SELECT \"column_name\", \"constraint_name\", \"rely\" FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("4", "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'KP_A'");
    }

    @Test
    public void aRefusedReplaceLeavesTheStandingTable() {
        engine.execute("CREATE OR REPLACE TABLE kp_r (id NUMBER NOT NULL, t_id NUMBER, "
            + "CONSTRAINT kp_ru UNIQUE (t_id))");
        engine.execute("INSERT INTO kp_r VALUES (1, 2)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE kp_r (id NUMBER UNIQUE ENABLE DISABLE)", CONFLICT},
            {"CREATE OR REPLACE TABLE kp_r (id NUMBER, UNIQUE (id) DEFERRABLE NOT DEFERRABLE)", CONFLICT},
            {"CREATE OR REPLACE TABLE kp_r (id NUMBER UNIQUE INITIALLY DEFERRED NOT DEFERRABLE)", NOT_DEFERRABLE},
            {"CREATE OR REPLACE TABLE kp_r (id NUMBER NOT NULL, CONSTRAINT kp_p5 PRIMARY KEY (id) NOT DEFERRABLE "
                + "INITIALLY DEFERRED, x NUMBER)", NOT_DEFERRABLE},
            {"ALTER TABLE kp_r ADD COLUMN c3 NUMBER CONSTRAINT kp_u4 UNIQUE NOT DEFERRABLE INITIALLY DEFERRED",
                NOT_DEFERRABLE},
            {"ALTER TABLE kp_r ADD COLUMN c4 NUMBER CONSTRAINT kp_u5 UNIQUE RELY NORELY", CONFLICT},
            {"ALTER TABLE kp_r ADD CONSTRAINT kp_u6 UNIQUE (id) RELY NORELY", CONFLICT},
            {"SELECT id || '-' || t_id FROM kp_r", "1-2"},
            {"SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'KP_R'", "2"},
        });
        engine.execute("SHOW UNIQUE KEYS IN TABLE kp_r");
        assertAnswer("T_ID|KP_RU", "SELECT \"column_name\", \"constraint_name\" FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
