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
package dev.frostlake.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The order in which EXPLAIN joins three or more tables keyed by equalities of their columns, and the expressions an
 * UPDATE's and a DELETE's plans spell (live-verified). Each plan is listed as its operators below the Result: id,
 * parents, operation, objects, expressions and partitions assigned.
 */
public class ExplainKeyedJoinOrderTest extends MeasuredCellsTestSupport {

    /** The operators of a plan below its Result, one line each. */
    private static final String OPERATORS =
        " ->> SELECT LISTAGG(\"id\" || '|' || \"parentOperators\" || '|' || \"operation\" || '|' || "
        + "NVL(\"objects\", '-') || '|' || NVL(\"expressions\", '-') || '|' || "
        + "NVL(TO_VARCHAR(\"partitionsAssigned\"), '-'), ' ;; ') WITHIN GROUP (ORDER BY \"id\") FROM "
        + "$1 WHERE \"id\" IS NOT NULL";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, b INT)");
        engine.execute("INSERT INTO tt SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 3))");
        engine.execute("CREATE OR REPLACE TABLE zz (a INT, b INT)");
        engine.execute("INSERT INTO zz SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 2))");
        engine.execute("CREATE OR REPLACE TABLE aa (a INT, b INT)");
        engine.execute("INSERT INTO aa SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 2))");
        engine.execute("CREATE OR REPLACE TABLE mm (a INT, b INT)");
        engine.execute("INSERT INTO mm SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 4))");
        engine.execute("CREATE OR REPLACE TABLE qa (a INT, b INT)");
        engine.execute("INSERT INTO qa SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 6))");
        engine.execute("CREATE OR REPLACE TABLE qb (a INT, b INT)");
        engine.execute("INSERT INTO qb SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 4))");
        engine.execute("CREATE OR REPLACE TABLE qc (a INT, b INT)");
        engine.execute("INSERT INTO qc SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 2))");
        engine.execute("CREATE OR REPLACE TABLE qd (a INT, b INT)");
        engine.execute("INSERT INTO qd SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 8))");
        engine.execute("CREATE OR REPLACE TABLE qt (a INT, b INT)");
        engine.execute("INSERT INTO qt SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 6))");
        engine.execute("CREATE OR REPLACE TABLE qx (a INT, b INT)");
        engine.execute("INSERT INTO qx SELECT SEQ4() + 1, SEQ4() + 1 FROM TABLE(GENERATOR(ROWCOUNT => 4))");
    }

    private void assertPlan(final String operators, final String statement) {
        assertAnswer(operators, "EXPLAIN " + statement + OPERATORS);
    }

    /**
     * Equalities of one column class imply the keys they do not write: the table that holds the fewest rows stands
     * first and joins first a table it is keyed to only by such a key, the one that holds the most rows first, each
     * table keyed once, on the column of the first table.
     */
    @Test
    public void impliedKeys() {
        assertPlan("1|[0]|InnerJoin|-|joinKey: (AA.A = ZZ.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (AA.A "
                + "= TT.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.AA|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (AA.A = TT.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (AA.A"
                + " = ZZ.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A|1",
            "SELECT 1 FROM tt, zz, aa WHERE tt.a = zz.a AND zz.a = aa.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (ZZ.A = TT.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.A "
                + "= MM.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (ZZ.A = MM.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.MM|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (ZZ.A"
                + " = TT.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "SELECT 1 FROM mm, tt, zz WHERE mm.a = tt.a AND tt.a = zz.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QA.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QB.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.A = QB.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QA.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1",
            "SELECT 1 FROM qa, qb, qc WHERE qa.a = qb.a AND qa.a = qc.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QB.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QA.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.A = QA.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QB.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1",
            "SELECT 1 FROM qc, qb, qa WHERE qb.a = qc.a AND qa.a = qb.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QB.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QA.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.A = QA.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QB.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1",
            "SELECT 1 FROM qa, qb, qc WHERE qa.a = qc.a AND qc.a = qb.a");
    }

    /**
     * A tie for the most rows goes to the name that sorts last, one for the fewest to the name that sorts first; the
     * second table stands first where it holds as many rows as the first.
     */
    @Test
    public void tiesForTheLargestAndTheSmallest() {
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QA.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QT.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.A = QT.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QT|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QA.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1",
            "SELECT 1 FROM qa, qt, qc WHERE qa.a = qc.a AND qc.a = qt.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QA.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QT.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.A = QT.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QT|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QA.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1",
            "SELECT 1 FROM qt, qa, qc WHERE qa.a = qc.a AND qa.a = qt.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QB.A = QA.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QX.A "
                + "= QB.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QX|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QX.A = QB.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (QB.A"
                + " = QA.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1",
            "SELECT 1 FROM qa, qb, qx WHERE qa.a = qb.a AND qa.a = qx.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (AA.A = ZZ.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (AA.A "
                + "= TT.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.AA|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (AA.A = TT.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (AA.A"
                + " = ZZ.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A|1",
            "SELECT 1 FROM tt, zz, aa WHERE tt.a = aa.a AND aa.a = zz.a");
    }

    /**
     * Inner joins ON equalities are ordered as tables written with commas and the same conditions in a WHERE are.
     */
    @Test
    public void innerJoinsOn() {
        assertPlan("1|[0]|InnerJoin|-|joinKey: (AA.A = TT.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.A "
                + "= AA.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (ZZ.A = AA.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.AA|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (AA.A"
                + " = TT.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "SELECT 1 FROM tt JOIN zz ON tt.a = zz.a JOIN aa ON tt.a = aa.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QA.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QB.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.A = QB.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1 ;; 6|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QA.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1",
            "SELECT 1 FROM qa JOIN qb ON qa.a = qb.a JOIN qc ON qa.a = qc.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QB.B = QA.B)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QB.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.A = QB.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QB|A, B|1 ;; 6|[1]|JoinFilter|-|joinKey: "
                + "(QB.B = QA.B)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QA|B|1",
            "SELECT 1 FROM qc INNER JOIN qb ON qc.a = qb.a INNER JOIN qa ON qb.b = qa.b");
    }

    /**
     * Four and five tables keyed in one class join one at a time in the same order.
     */
    @Test
    public void moreThanThreeTables() {
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QA.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QB.A)|- ;; 3|[2]|InnerJoin|-|joinKey: (QC.A = QD.A)|- ;; "
                + "4|[3]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; 5|[3]|JoinFilter|-|joinKey: (QC.A"
                + " = QD.A)|- ;; 6|[5]|TableScan|TEST_DB.TEST_SCHEMA.QD|A|1 ;; "
                + "7|[2]|JoinFilter|-|joinKey: (QC.A = QB.A)|- ;; "
                + "8|[7]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1 ;; 9|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QA.A)|- ;; 10|[9]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1",
            "SELECT 1 FROM qd, qb, qa, qc WHERE qd.a = qb.a AND qb.a = qa.a AND qa.a = qc.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QD.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= QB.A)|- ;; 3|[2]|InnerJoin|-|joinKey: (QC.A = QA.A)|- ;; "
                + "4|[3]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; 5|[3]|JoinFilter|-|joinKey: (QC.A"
                + " = QA.A)|- ;; 6|[5]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1 ;; "
                + "7|[2]|JoinFilter|-|joinKey: (QC.A = QB.A)|- ;; "
                + "8|[7]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1 ;; 9|[1]|JoinFilter|-|joinKey: (QC.A"
                + " = QD.A)|- ;; 10|[9]|TableScan|TEST_DB.TEST_SCHEMA.QD|A|1",
            "SELECT 1 FROM qd, qa, qb, qc WHERE qd.a = qa.a AND qd.a = qb.a AND qd.a = qc.a");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QC.A = QB.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.A "
                + "= MM.A)|- ;; 3|[2]|InnerJoin|-|joinKey: (QC.A = QA.A)|- ;; "
                + "4|[3]|InnerJoin|-|joinKey: (QC.A = QD.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QC|A|1 ;; 6|[4]|JoinFilter|-|joinKey: (QC.A"
                + " = QD.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QD|A|1 ;; "
                + "8|[3]|JoinFilter|-|joinKey: (QC.A = QA.A)|- ;; "
                + "9|[8]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1 ;; 10|[2]|JoinFilter|-|joinKey: "
                + "(QC.A = MM.A)|- ;; 11|[10]|TableScan|TEST_DB.TEST_SCHEMA.MM|A|1 ;; "
                + "12|[1]|JoinFilter|-|joinKey: (QC.A = QB.A)|- ;; "
                + "13|[12]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1",
            "SELECT 1 FROM qd, qa, mm, qb, qc WHERE qd.a = qa.a AND qa.a = mm.a AND mm.a = "
                + "qb.a AND qb.a = qc.a");
    }

    /**
     * Three tables keyed by columns of two classes: the table that holds the fewest rows first, then each table keyed
     * to those joined, the one that holds the most rows first, keyed on the column of the table that joined first.
     */
    @Test
    public void keysOfSeveralClasses() {
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QB.A = QA.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (QC.B "
                + "= QB.B)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.QC|B|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (QC.B = QB.B)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.QB|A, B|1 ;; 6|[1]|JoinFilter|-|joinKey: "
                + "(QB.A = QA.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.QA|A|1",
            "SELECT 1 FROM qa, qb, qc WHERE qa.a = qb.a AND qb.b = qc.b");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (TT.A = MM.A)|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.B "
                + "= TT.B)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|B|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (ZZ.B = TT.B)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A, B|1 ;; 6|[1]|JoinFilter|-|joinKey: "
                + "(TT.A = MM.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.MM|A|1",
            "SELECT 1 FROM mm, tt, zz WHERE mm.a = tt.a AND tt.b = zz.b");
    }

    /**
     * Where the two tables that hold the fewest rows hold as many and the keys are of two classes, the one keyed to the
     * third joins it first and the other joins that pair, standing first, its JoinFilter over the tables of the pair
     * that hold a column of its key.
     */
    @Test
    public void tiedTablesKeyedBySeveralClasses() {
        assertPlan("1|[0]|InnerJoin|-|joinKey: (AA.B = ZZ.B)|- ;; "
                + "2|[1]|TableScan|TEST_DB.TEST_SCHEMA.AA|B|1 ;; 3|[1]|InnerJoin|-|joinKey: (ZZ.A "
                + "= TT.A)|- ;; 4|[3]|JoinFilter|-|joinKey: (AA.B = ZZ.B)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A, B|1 ;; 6|[3]|JoinFilter|-|joinKey: "
                + "(ZZ.A = TT.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "SELECT 1 FROM tt, zz, aa WHERE tt.a = zz.a AND zz.b = aa.b");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (ZZ.B = AA.B)|- ;; "
                + "2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|B|1 ;; 3|[1]|InnerJoin|-|joinKey: (AA.A "
                + "= TT.A)|- ;; 4|[3]|JoinFilter|-|joinKey: (ZZ.B = AA.B)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.AA|A, B|1 ;; 6|[3]|JoinFilter|-|joinKey: "
                + "(AA.A = TT.A)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "SELECT 1 FROM tt, zz, aa WHERE tt.a = aa.a AND aa.b = zz.b");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (QB.A = QA.A)|- ;; "
                + "2|[1]|TableScan|TEST_DB.TEST_SCHEMA.QB|A|1 ;; 3|[1]|InnerJoin|-|joinKey: (QX.B "
                + "= QA.B)|- ;; 4|[3]|TableScan|TEST_DB.TEST_SCHEMA.QX|B|1 ;; "
                + "5|[3]|JoinFilter|-|joinKey: (QB.A = QA.A)|- ;; "
                + "6|[5]|TableScan|TEST_DB.TEST_SCHEMA.QA|A, B|1",
            "SELECT 1 FROM qa, qb, qx WHERE qa.a = qb.a AND qa.b = qx.b");
        assertPlan("1|[0]|InnerJoin|-|joinKey: (AA.A = ZZ.A)|- ;; "
                + "2|[1]|TableScan|TEST_DB.TEST_SCHEMA.AA|A|1 ;; 3|[1]|InnerJoin|-|joinKey: (ZZ.B "
                + "= TT.B)|- ;; 4|[3]|JoinFilter|-|joinKey: (AA.A = ZZ.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A, B|1 ;; 6|[3]|JoinFilter|-|joinKey: "
                + "(ZZ.B = TT.B)|- ;; 7|[6]|TableScan|TEST_DB.TEST_SCHEMA.TT|B|1",
            "SELECT 1 FROM tt JOIN zz ON tt.b = zz.b JOIN aa ON zz.a = aa.a");
    }

    /**
     * An UPDATE or a DELETE spells its keys, its filters and the columns it reads as a SELECT does, and reads its
     * target whatever columns it lists.
     */
    @Test
    public void updatesAndDeletes() {
        assertPlan("1|[0]|Delete|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.A = "
                + "TT.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (ZZ.A = TT.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "DELETE FROM tt USING zz WHERE tt.a = zz.a");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.A = "
                + "TT.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A, B|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (ZZ.A = TT.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "UPDATE tt SET b = zz.b FROM zz WHERE tt.a = zz.a");
        assertPlan("1|[0]|Delete|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.A = "
                + "TT.A)|- ;; 3|[2]|InnerJoin|-|joinKey: (ZZ.A = MM.A)|- ;; "
                + "4|[3]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A|1 ;; 5|[3]|JoinFilter|-|joinKey: (ZZ.A"
                + " = MM.A)|- ;; 6|[5]|TableScan|TEST_DB.TEST_SCHEMA.MM|A|1 ;; "
                + "7|[2]|JoinFilter|-|joinKey: (ZZ.A = TT.A)|- ;; "
                + "8|[7]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "DELETE FROM tt USING zz, mm WHERE tt.a = zz.a AND zz.a = mm.a");
        assertPlan("1|[0]|Delete|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (AA.B = "
                + "ZZ.B)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.AA|B|1 ;; "
                + "4|[2]|InnerJoin|-|joinKey: (ZZ.A = TT.A)|- ;; 5|[4]|JoinFilter|-|joinKey: (AA.B"
                + " = ZZ.B)|- ;; 6|[5]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A, B|1 ;; "
                + "7|[4]|JoinFilter|-|joinKey: (ZZ.A = TT.A)|- ;; "
                + "8|[7]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "DELETE FROM tt USING zz, aa WHERE tt.a = zz.a AND zz.b = aa.b");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (AA.A = "
                + "ZZ.A)|- ;; 3|[2]|InnerJoin|-|joinKey: (AA.A = TT.A)|- ;; "
                + "4|[3]|TableScan|TEST_DB.TEST_SCHEMA.AA|A|1 ;; 5|[3]|JoinFilter|-|joinKey: (AA.A"
                + " = TT.A)|- ;; 6|[5]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1 ;; "
                + "7|[2]|JoinFilter|-|joinKey: (AA.A = ZZ.A)|- ;; "
                + "8|[7]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A|1",
            "UPDATE tt SET b = 1 FROM zz JOIN aa ON zz.a = aa.a WHERE tt.a = zz.a");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; "
                + "2|[1]|TableScan|TEST_DB.TEST_SCHEMA.TT|-|1",
            "UPDATE tt SET b = 5");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; "
                + "2|[1]|TableScan|TEST_DB.TEST_SCHEMA.TT|B|1",
            "UPDATE tt SET b = b + 1");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|Filter|-|TT.B > 1|- ;; "
                + "3|[2]|TableScan|TEST_DB.TEST_SCHEMA.TT|A, B|1",
            "UPDATE tt SET b = a WHERE b > 1");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (Z.A = "
                + "T.A)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A, B|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (Z.A = T.A)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "UPDATE tt t SET b = z.b FROM zz z WHERE t.a = z.a");
        assertPlan("1|[0]|Delete|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|CartesianJoin|-|-|- ;; "
                + "3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|-|0 ;; "
                + "4|[2]|TableScan|TEST_DB.TEST_SCHEMA.TT|-|1",
            "DELETE FROM tt USING zz");
        assertPlan("1|[0]|Delete|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.A = "
                + "TT.A), joinFilter: TT.B > ZZ.B|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A, "
                + "B|1 ;; 4|[2]|JoinFilter|-|joinKey: (ZZ.A = TT.A), joinFilter: TT.B > ZZ.B|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A, B|1",
            "DELETE FROM tt USING zz WHERE tt.a = zz.a AND tt.b > zz.b");
        assertPlan("1|[0]|Delete|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|Filter|-|(T.A = 1) OR (T.B = "
                + "2)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.TT|A, B|1",
            "DELETE FROM tt t WHERE t.a = 1 OR t.b = 2");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|Filter|-|TT.A = 3|- ;; "
                + "3|[2]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "UPDATE tt SET a = 1, b = 2 WHERE a = 3");
        assertPlan("1|[0]|Update|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|InnerJoin|-|joinKey: (ZZ.A = "
                + "TT.A) AND (ZZ.B = TT.B)|- ;; 3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ZZ|A, B|1 ;; "
                + "4|[2]|JoinFilter|-|joinKey: (ZZ.A = TT.A) AND (ZZ.B = TT.B)|- ;; "
                + "5|[4]|TableScan|TEST_DB.TEST_SCHEMA.TT|A, B|1",
            "UPDATE tt SET b = zz.b FROM zz WHERE tt.a = zz.a AND tt.b = zz.b");
        assertPlan("1|[0]|Delete|TEST_DB.TEST_SCHEMA.TT|-|- ;; 2|[1]|Filter|-|TT.A > 1|- ;; "
                + "3|[2]|TableScan|TEST_DB.TEST_SCHEMA.TT|A|1",
            "DELETE FROM tt WHERE a > 1");
    }
}
