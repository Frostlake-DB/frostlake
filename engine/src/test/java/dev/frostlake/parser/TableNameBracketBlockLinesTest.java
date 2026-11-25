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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The lines of a bracket written right after a table's name inside a block, EXECUTE IMMEDIATE's included, and of an
 * IDENTIFIER() reference refused at its bracket in an EXISTS's, a set operation's or the statement's own brackets
 * (live-verified cells).
 */
public class TableNameBracketBlockLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aBlockAndAnExistsReadTheBracket() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE t1 (a INT)");
        assertCells(new String[][] {
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t', '1')); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 44 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM zz (a, b); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax"
                + " error line 1 at position 32 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM zz (a, b); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax"
                + " error line 1 at position 33 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM zz (a); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax"
                + " error line 1 at position 33 unexpected ')'."},
            {"BEGIN SELECT COUNT(*) FROM zz (a); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax"
                + " error line 1 at position 32 unexpected ')'."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t', '1')) x; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 44 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(f(a, b)); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 39 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(f(a)); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 39 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t', '1')); SELECT 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 44 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM zz (f(a, b)); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax"
                + " error line 1 at position 32 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM zz (f(a)); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax"
                + " error line 1 at position 32 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM zz (f(a), b); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax"
                + " error line 1 at position 32 unexpected '('."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER('a', 'b'); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax"
                + " error line 1 at position 46 unexpected ')'."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(t1) WHERE a = 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 40 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM IDENTIFIER(f(a)); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax"
                + " error line 1 at position 40 unexpected '('."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(t1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 51 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(UPPER('t1')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 59 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(CONCAT('t', '1')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 64 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(t1) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 52 unexpected 'x'."},
            {"SELECT 1 FROM t1 UNION (SELECT 1 FROM IDENTIFIER(t1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '('. | syntax"
                + " error line 1 at position 52 unexpected ')'."},
            {"(SELECT 1 FROM IDENTIFIER(t1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax"
                + " error line 1 at position 29 unexpected ')'."},
            {"SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM IDENTIFIER(t1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('. | syntax"
                + " error line 1 at position 55 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax"
                + " error line 1 at position 43 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM t1, IDENTIFIER(t1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('. | syntax"
                + " error line 1 at position 55 unexpected ')'."},
        });
    }

    @Test
    public void anIdentifierReferenceReadsAsATableName() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE t1 (a INT)");
        assertCells(new String[][] {
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (f('t', '1')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax"
                + " error line 1 at position 52 unexpected ')'."},
            {"SELECT 1 FROM zz (f('t', '1'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax"
                + " error line 1 at position 28 unexpected ')'."},
            {"SELECT 1 FROM zz (f(a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (f(a)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax"
                + " error line 1 at position 45 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax"
                + " error line 1 at position 44 unexpected 'x'."},
            {"SELECT 1 FROM zz (a) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 FROM IDENTIFIER(t1) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"SELECT 1 FROM IDENTIFIER(t1), t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"SELECT 1 FROM IDENTIFIER(t1) WHERE a = 1 AND",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('. | syntax"
                + " error line 1 at position 44 unexpected '<EOF>'."},
            {"SELECT 1 FROM t1 UNION (SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '('. | syntax"
                + " error line 1 at position 44 unexpected ')'."},
            {"(SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '('. | syntax"
                + " error line 1 at position 21 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(UPPER('t1')) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 59 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(t1, t2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 55 unexpected ')'."},
            {"SELECT 1 FROM IDENTIFIER(f(a)) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a, b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax"
                + " error line 1 at position 46 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(t1, b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 54 unexpected ')'."},
        });
    }

    @Test
    public void theIssueShapes() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE t1 (a INT)");
        assertCells(new String[][] {
            {"SELECT * FROM (SELECT 1 FROM zz (a)) , t (b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('."},
            {"SELECT * FROM (SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM IDENTIFIER(t1); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax"
                + " error line 1 at position 41 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM IDENTIFIER(UPPER('t1')); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax"
                + " error line 1 at position 44 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t', '1')); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax"
                + " error line 1 at position 45 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR DEFAULT 't1'; c INT; BEGIN SELECT COUNT(*) INTO :c "
                + "FROM IDENTIFIER(v); RETURN c; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 85 unexpected '('. | syntax"
                + " error line 1 at position 87 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM IDENTIFIER(t1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax"
                + " error line 1 at position 51 unexpected ')'."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(t1); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 40 unexpected ')'."},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(UPPER('t1')); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax"
                + " error line 1 at position 43 unexpected '('."},
            {"SELECT 1 FROM IDENTIFIER(t1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS TABLE (s VARCHAR) AS $$SELECT UPPER(@st), (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT @st$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'."},
        });
    }

    @Test
    public void aBracketAfterAnAliasReadsOn() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 foo (2); SELECT 3 bar (4); END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '('. | syntax"
                + " error line 1 at position 38 unexpected '('."},
            {"BEGIN SELECT 1 foo (2); SELECT 3 bar (4); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax"
                + " error line 1 at position 37 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 foo ('x') + 1; RETURN 1; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '('."},
            {"BEGIN SELECT 1 foo ('x') + 1; RETURN 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('."},
        });
    }
}
