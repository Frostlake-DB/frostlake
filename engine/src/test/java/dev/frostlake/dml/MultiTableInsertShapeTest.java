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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The order of a multi-table INSERT's cases: an unconditional INSERT FIRST, a case before the first WHEN, an ELSE
 * first and an ELSE with a case after it are compile errors placed at their word, ahead of every name the statement
 * resolves, in a block's and a procedure's body as well (live-verified).
 */
public class MultiTableInsertShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void unconditionalFirst() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE u (a INT, b STRING)");
        assertCells(new String[][] {
            {"INSERT FIRST INTO t VALUES (1) SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t VALUES (1) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''x''. | "
                + "syntax error line 1 at position 37 unexpected 'SELECT'."},
            {"INSERT FIRST INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT OVERWRITE FIRST INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 17 | Unconditional multi-table "
                + "insert must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t INTO u SELECT 1, 'a'",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO nosuch SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t (a) VALUES ($1) SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t VALUES (nosuchcol) SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t VALUES (1, 2) SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"insert first into t select 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"  INSERT FIRST INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 9 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t SELECT * FROM nosuch",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT FIRST INTO t SELECT 1 FROM t, nosuchfn()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '('."},
            {"INSERT FIRST INTO t (nosuchcol) SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"SELECT COUNT(*) FROM t",
                "0"},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT FIRST INTO t VALUES (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'."},
            {"INSERT FIRST INTO t SELECT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'."},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT FIRST INTO t SELECT 1; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 14 | Unconditional multi-table "
                + "insert must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST\nINTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t SELECT 1;",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t SELECT 'abc'",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t VALUES (1) SELECT 1 UNION SELECT 2",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t SELECT 1 x WHERE x > 0",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST ELSE INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | Invalid multi-table insert: "
                + "must provide insert case prior to ELSE."},
            {"INSERT FIRST INTO t.nosuchschema.x SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
        });
    }

    @Test
    public void caseOrder() {
        engine.execute("CREATE TABLE t (a INT)");
        assertCells(new String[][] {
            {"INSERT ALL INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 11 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT ALL ELSE INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid multi-table insert: "
                + "must provide insert case prior to ELSE."},
            {"INSERT FIRST INTO t INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT FIRST INTO t ELSE INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT ALL INTO t ELSE INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 11 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT OVERWRITE FIRST INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 23 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT FIRST INTO nosuch WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT FIRST ELSE INTO nosuch SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | Invalid multi-table insert: "
                + "must provide insert case prior to ELSE."},
            {"INSERT FIRST INTO t (a) VALUES (1) INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST WHEN 1=1 THEN INTO t ELSE INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unreachable case in multi-table"
                + " insert. ELSE case must be last case provided."},
            {"INSERT FIRST INTO t VALUES (1) ('x') SELECT 1 WHERE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''x''. | "
                + "syntax error line 1 at position 37 unexpected 'SELECT'."},
            {"SELECT COUNT(*) FROM t",
                "0"},
            {"EXECUTE IMMEDIATE $$\nBEGIN\n  INSERT FIRST INTO t SELECT 1;\nEND;\n$$",
                "REFUSED SQL compilation error: error line 3 at position 9 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT /* c */ FIRST INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 15 | Unconditional multi-table "
                + "insert must use INSERT ALL (not INSERT FIRST)."},
            {"EXPLAIN INSERT FIRST INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 15 | Unconditional multi-table "
                + "insert must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT FIRST INTO t VALUES (DEFAULT) SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
        });
    }

    @Test
    public void bodies() {
        engine.execute("CREATE TABLE t (a INT)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t VALUES (5); INSERT FIRST INTO t SELECT 1; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 40 | Unconditional multi-table "
                + "insert must use INSERT ALL (not INSERT FIRST)."},
            {"SELECT COUNT(*) FROM t",
                "0"},
            {"CREATE PROCEDURE pr() RETURNS INT LANGUAGE SQL AS $$ BEGIN INSERT INTO t VALUES (5); INSERT "
                + "FIRST INTO t SELECT 1; RETURN 1; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 40 | Unconditional multi-table "
                + "insert must use INSERT ALL (not INSERT FIRST)."},
            {"CALL pr()",
                "REFUSED SQL compilation error: | Unknown function PR."},
            {"SELECT COUNT(*) FROM t",
                "0"},
            {"EXECUTE IMMEDIATE $$ BEGIN IF (FALSE) THEN INSERT FIRST INTO t SELECT 1; END IF; RETURN 2; "
                + "END; $$",
                "REFUSED SQL compilation error: error line 1 at position 30 | Unconditional multi-table "
                + "insert must use INSERT ALL (not INSERT FIRST)."},
            {"EXECUTE IMMEDIATE 'INSERT FIRST INTO t SELECT 1'",
                "REFUSED SQL compilation error: error line 1 at position 7 | Unconditional multi-table insert"
                + " must use INSERT ALL (not INSERT FIRST)."},
            {"INSERT ALL WHEN 1=1 THEN INTO t ELSE INTO t ELSE INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 32 | Unreachable case in multi-table"
                + " insert. ELSE case must be last case provided."},
            {"INSERT ALL ELSE INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid multi-table insert: "
                + "must provide insert case prior to ELSE."},
            {"INSERT ALL INTO t ELSE INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 11 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT FIRST WHEN 1=1 THEN INTO t ELSE INTO t ELSE INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unreachable case in multi-table"
                + " insert. ELSE case must be last case provided."},
            {"INSERT FIRST WHEN 1=1 THEN INTO nosuch ELSE INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 39 | Unreachable case in multi-table"
                + " insert. ELSE case must be last case provided."},
        });
        // The cases in the orders the statement takes still route their rows.
        engine.execute("INSERT ALL WHEN 1=1 THEN INTO t INTO t SELECT 1");
        assertAnswer("2", "SELECT COUNT(*) FROM t");
        engine.execute("INSERT ALL INTO t VALUES (1) INTO t VALUES (2) SELECT 1");
        engine.execute("INSERT FIRST WHEN 1=1 THEN INTO t VALUES (7) ELSE INTO t VALUES (8) SELECT 1");
        assertAnswer("1,1,1,2,7", "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM t");
        assertCells(new String[][] {
            {"EXPLAIN INSERT ALL INTO t WHEN 1=1 THEN INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 19 | Conditional multi-table insert "
                + "must begin with a condition."},
            {"INSERT FIRST ELSE INTO t ELSE INTO t SELECT 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | Invalid multi-table insert: "
                + "must provide insert case prior to ELSE."},
        });
    }
}
