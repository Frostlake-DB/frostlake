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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A quoted variable and an unquoted one whose names differ only by letter case are two variables (live-verified):
 * {@code "low"} and {@code low} each hold their own value wherever the block reads or writes them, as a procedure's
 * parameters do, while {@code "LOW"} and {@code low} are one name. A named argument of a CALL fills the parameter
 * of exactly its name.
 */
public class QuotedVariableTwinsTest extends MeasuredCellsTestSupport {

    /** Declarations, assignments and a block's own expressions. */
    @Test
    public void eachTwinHoldsItsOwnValue() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN RETURN \"low\" + "
                + "low * 10; END; $$", "21"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"Low\" INT DEFAULT 1; \"LOW\" INT DEFAULT 2; BEGIN RETURN "
                + "\"Low\" + \"LOW\" * 10 + low * 100; END; $$", "221"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; BEGIN LET low := 7; RETURN \"low\" + low * "
                + "10; END; $$", "71"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN \"low\" := 5; low "
                + ":= 6; RETURN \"low\" + low * 10; END; $$", "65"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" VARCHAR DEFAULT 'a'; low VARCHAR DEFAULT 'b'; BEGIN "
                + "RETURN \"low\" || low; END; $$", "ab"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"a b\" INT DEFAULT 3; \"A B\" INT DEFAULT 4; BEGIN RETURN \"a "
                + "b\" * 10 + \"A B\"; END; $$", "34"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT; low INT DEFAULT 2; BEGIN \"low\" := low + 1; low := "
                + "\"low\" * 10; RETURN \"low\" || '/' || low; END; $$", "3/30"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN LET \"x\" := "
                + "\"low\" + low; LET x := \"x\" * 2; RETURN \"x\" || '/' || x; END; $$", "3/6"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" VARCHAR DEFAULT 'a'; low INT DEFAULT 2; BEGIN RETURN "
                + "SYSTEM$TYPEOF(\"low\") || '/' || SYSTEM$TYPEOF(low); END; $$", "VARCHAR[LOB]/NUMBER(38,0)[SB16]"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN RETURN "
                + "ABS(\"low\") || '-' || ABS(low) || '-' || IFF(\"low\" < low, 'y', 'n'); END; $$", "1-2-y"},
        });
    }

    /** Conditions, loops, nested blocks and handlers. */
    @Test
    public void eachTwinIsReadAndWrittenInControlFlow() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN IF (\"low\" < "
                + "low) THEN RETURN 'lt'; END IF; RETURN 'ge'; END; $$", "lt"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN CASE WHEN \"low\" "
                + "< low THEN RETURN 'lt'; ELSE RETURN 'ge'; END CASE; END; $$", "lt"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 5; n INT DEFAULT 0; BEGIN "
                + "WHILE (\"low\" < low) DO \"low\" := \"low\" + 1; n := n + 1; END WHILE; RETURN n; END; $$", "4"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN FOR \"i\" IN 1 TO "
                + "2 DO low := low + \"i\"; END FOR; RETURN \"low\" + low * 10; END; $$", "51"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN FOR low IN 5 TO 5 "
                + "DO \"low\" := \"low\" + low; END FOR; RETURN \"low\" || '/' || low; END; $$", "6/2"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; c CURSOR FOR SELECT 7 "
                + "AS v; BEGIN FOR \"rec\" IN c DO low := \"rec\".v; END FOR; RETURN \"low\" || '/' || low; END; "
                + "$$", "1/7"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN BEGIN LET \"low\" "
                + ":= 8; low := 9; END; RETURN \"low\" + low * 10; END; $$", "91"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN BEGIN \"low\" := "
                + "1 / 0; EXCEPTION WHEN OTHER THEN low := 9; END; RETURN \"low\" || '/' || low; END; $$", "1/9"},
        });
    }

    /** Binds of the statements a block runs, SELECT ... INTO targets and USING lists. */
    @Test
    public void eachTwinBindsItsOwnValue() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; r INT; BEGIN r := "
                + "(SELECT :low * 10); RETURN r + \"low\"; END; $$", "21"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN SELECT 3 INTO "
                + "\"low\"; SELECT 4 INTO low; RETURN \"low\" || '/' || low; END; $$", "3/4"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; c CURSOR FOR SELECT ? + "
                + "? * 10; r INT; BEGIN OPEN c USING (\"low\", low); FETCH c INTO r; RETURN r; END; $$", "21"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; c CURSOR FOR SELECT ? "
                + "|| '/' || ?; r VARCHAR; BEGIN OPEN c USING (low, \"low\"); FETCH c INTO r; RETURN r; END; $$", "2/1"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; low INT DEFAULT 2; BEGIN EXECUTE IMMEDIATE "
                + "'SELECT ? + ? * 10' USING (\"low\", low); RETURN (SELECT $1 FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))); END; $$", "21"},
        });
    }

    /** A quoted name that folds to itself is the unquoted name; one of another case is another name. */
    @Test
    public void aQuotedNameOfAnotherCaseIsAnotherName() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; BEGIN RETURN \"low\"; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE low INT DEFAULT 1; BEGIN RETURN \"LOW\"; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"LOW\" INT DEFAULT 1; BEGIN RETURN low; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; BEGIN RETURN low; END; $$", "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'LOW'"},
            {"EXECUTE IMMEDIATE $$ DECLARE low INT DEFAULT 1; BEGIN RETURN \"low\"; END; $$", "REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier '\"low\"'"},
            {"EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; \"low\" INT DEFAULT 2; BEGIN RETURN "
                + "\"low\"; END; $$", "REFUSED SQL compilation error: error line 1 at position 30 |  Variable with name 'low' declared twice."},
            {"EXECUTE IMMEDIATE $$ DECLARE low INT DEFAULT 1; \"LOW\" INT DEFAULT 2; BEGIN RETURN low; END; "
                + "$$", "REFUSED SQL compilation error: error line 1 at position 28 |  Variable with name 'LOW' declared twice."},
        });
    }

    /** A procedure's parameters, bound by position and by name. */
    @Test
    public void parametersAreTwinsToo() {
        engine.execute("CREATE OR REPLACE PROCEDURE pq_twins(\"low\" INT, low INT) RETURNS INT LANGUAGE SQL AS "
            + "$$ BEGIN RETURN \"low\" + low * 10; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pq_cased(\"Low\" INT, \"LOW\" INT) RETURNS INT LANGUAGE SQL AS "
            + "$$ BEGIN RETURN \"Low\" + low * 10; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pq_bare(low INT) RETURNS INT LANGUAGE SQL AS "
            + "$$ BEGIN RETURN low; END $$");
        assertCells(new String[][] {
            {"CALL pq_twins(1, 2)", "21"},
            {"CALL pq_cased(1, 2)", "21"},
            {"CALL pq_cased(\"Low\" => 1, low => 2)", "21"},
            {"CALL pq_cased(LOW => 2, \"Low\" => 1)", "21"},
            {"CALL pq_cased(\"LOW\" => 2, \"Low\" => 1)", "21"},
            {"CALL pq_cased(\"low\" => 1, low => 2)", "REFUSED SQL compilation error: error line 0 at position -1 | "
                + "named arguments [low, LOW] do not match any signature for function PQ_CASED"},
            {"CALL pq_bare(\"LOW\" => 1)", "1"},
            {"CALL pq_bare(Low => 1)", "1"},
            {"CALL pq_bare(\"low\" => 1)", "REFUSED SQL compilation error: error line 0 at position -1 | "
                + "named arguments [low] do not match any signature for function PQ_BARE"},
        });
    }
}
