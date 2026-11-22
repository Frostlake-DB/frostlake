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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * An {@code IDENTIFIER()} reference names the object of a MERGE (target and source), a COPY INTO, an ALTER or EXECUTE
 * TASK, a COMMENT ON, each INTO of a multi-table INSERT, an UNDROP, the masking policy a column takes, and a FROM item
 * read at a time travel point, as a name written there does, from a literal, a session variable or a block's variable
 * alike; a reference the account cannot read whole is refused at the token live names (live-verified).
 */
public class IdentifierObjectNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void mergeAndMultiInsert() {
        engine.execute("CREATE OR REPLACE TABLE tm (a INT, b INT)");
        engine.execute("INSERT INTO tm VALUES (1, 1), (2, 2), (3, 3)");
        engine.execute("CREATE OR REPLACE TABLE ts (a INT, b INT)");
        engine.execute("INSERT INTO ts VALUES (3, 30), (4, 40)");
        engine.execute("SET tv = 'tm'");
        engine.execute("SET sv = 'ts'");
        engine.execute("MERGE INTO IDENTIFIER('tm') t USING (SELECT 1 a) s ON t.a = s.a WHEN MATCHED THEN UPDATE SET"
            + " b = 10");
        engine.execute("MERGE INTO IDENTIFIER($tv) t USING IDENTIFIER($sv) s ON t.a = s.a WHEN MATCHED THEN UPDATE "
            + "SET b = s.b WHEN NOT MATCHED THEN INSERT VALUES (s.a, s.b)");
        engine.execute("MERGE INTO tm USING IDENTIFIER('ts') ON tm.a = ts.a - 2 WHEN MATCHED THEN DELETE");
        engine.execute("INSERT ALL INTO IDENTIFIER('tm') VALUES (5, 50) INTO IDENTIFIER($sv) (a) VALUES (6) SELECT 1");
        engine.execute("INSERT FIRST WHEN 1 = 1 THEN INTO IDENTIFIER($tv) VALUES (7, 70) SELECT 1");
        assertCells(new String[][] {
            {"SELECT LISTAGG(a || ':' || b, ',') WITHIN GROUP (ORDER BY a) FROM tm",
                "3:30,4:40,5:50,7:70"},
            {"SELECT LISTAGG(a || ':' || COALESCE(b, 0), ',') WITHIN GROUP (ORDER BY a) FROM ts",
                "3:30,4:40,6:0"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR DEFAULT 'tm'; BEGIN MERGE INTO IDENTIFIER(:v) t USING"
                + " (SELECT 999 a) s ON t.a = s.a WHEN MATCHED THEN DELETE; RETURN 1; END; $$",
                "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR DEFAULT 'tm'; BEGIN INSERT ALL INTO IDENTIFIER(:v) "
                + "VALUES (8, 80) SELECT 1; RETURN 1; END; $$",
                "1"},
            {"SELECT COUNT(*) FROM tm",
                "5"},
            {"MERGE INTO tm t USING IDENTIFIER('t' || 's') s ON t.a = s.a WHEN MATCHED THEN DELETE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '||'."},
            {"MERGE INTO IDENTIFIER('my table') t USING (SELECT 1 a) s ON t.a = s.a WHEN MATCHED THEN "
                + "DELETE",
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier ''my table''"},
            {"INSERT ALL INTO IDENTIFIER('t' || 'm') VALUES (1, 2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''t''. | "
                + "syntax error line 1 at position 39 unexpected 'VALUES'."},
            {"COPY INTO IDENTIFIER('t' || 'm') FROM @nosuchstg",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '||'."},
        });
    }

    @Test
    public void tasksCommentsUndrop() {
        engine.execute("CREATE OR REPLACE TABLE tc (a INT)");
        engine.execute("CREATE OR REPLACE VIEW vc AS SELECT 1 x");
        engine.execute("CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("SET kv = 'tk'");
        engine.execute("SET cv = 'tc'");
        engine.execute("ALTER TASK IDENTIFIER('tk') SET COMMENT = 'k1'");
        engine.execute("ALTER TASK IDENTIFIER($kv) SUSPEND");
        engine.execute("COMMENT ON TABLE IDENTIFIER($cv) IS 'tcomment'");
        engine.execute("COMMENT ON COLUMN IDENTIFIER('tc.a') IS 'colc'");
        engine.execute("COMMENT ON VIEW IDENTIFIER('vc') IS 'vcomment'");
        engine.execute("COMMENT IF EXISTS ON TABLE IDENTIFIER('nosuch') IS 'c'");
        engine.execute("CREATE OR REPLACE TABLE tu (a INT)");
        engine.execute("INSERT INTO tu VALUES (1)");
        engine.execute("DROP TABLE tu");
        engine.execute("UNDROP TABLE IDENTIFIER('tu')");
        engine.execute("CREATE OR REPLACE SCHEMA su");
        engine.execute("DROP SCHEMA su");
        engine.execute("SET uv = 'su'");
        engine.execute("UNDROP SCHEMA IDENTIFIER($uv)");
        // CREATE SCHEMA made the new schema the current one.
        engine.execute("USE SCHEMA test_schema");
        engine.execute("SHOW TASKS LIKE 'TK'");
        assertCells(new String[][] {
            {"SELECT \"comment\" || '/' || \"state\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "k1/suspended"},
            {"SELECT comment FROM information_schema.tables WHERE table_name = 'TC'",
                "tcomment"},
            {"SELECT comment FROM information_schema.columns WHERE table_name = 'TC' AND column_name = 'A'",
                "colc"},
            {"SELECT comment FROM information_schema.views WHERE table_name = 'VC'",
                "vcomment"},
            {"SELECT COUNT(*) FROM tu",
                "1"},
            {"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'SU'",
                "1"},
            {"EXECUTE TASK IDENTIFIER('t' || 'k')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('."},
            {"ALTER TASK IDENTIFIER('t' || 'k') SUSPEND",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '||'."},
            {"COMMENT ON COLUMN IDENTIFIER('tc').a IS 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '.'."},
            {"COMMENT ON TABLE IDENTIFIER('t' || 'c') IS 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '||'."},
            {"COMMENT ON TABLE IDENTIFIER('tc') IS 'c' 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'."},
            {"UNDROP TABLE IDENTIFIER('t' || 'u')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('."},
            {"UNDROP TABLE IDENTIFIER('nosuchu')",
                "REFUSED Table NOSUCHU did not exist or was purged."},
        });
    }

    @Test
    public void maskingAndTimeTravel() {
        engine.execute("CREATE OR REPLACE TABLE tp (a INT, b INT)");
        engine.execute("INSERT INTO tp VALUES (1, 5)");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp AS (v INT) RETURNS INT -> 0");
        engine.execute("SET pv = 'mp'");
        engine.execute("ALTER TABLE tp MODIFY COLUMN b SET MASKING POLICY IDENTIFIER($pv)");
        engine.execute("CREATE OR REPLACE TABLE tq (a INT MASKING POLICY IDENTIFIER('mp'))");
        engine.execute("INSERT INTO tq VALUES (9)");
        engine.execute("CREATE OR REPLACE VIEW vp (x WITH MASKING POLICY IDENTIFIER('mp')) AS SELECT a FROM tp");
        engine.execute("INSERT INTO tp VALUES (2, 6)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM IDENTIFIER('tp') BEFORE(STATEMENT => LAST_QUERY_ID())",
                "1"},
            {"SELECT COUNT(*) FROM IDENTIFIER('tp')",
                "2"},
            {"SELECT SUM(b) FROM tp",
                "0"},
            {"SELECT a FROM tq",
                "0"},
            {"SELECT MAX(x) FROM vp",
                "0"},
            {"ALTER TABLE tp MODIFY COLUMN b SET MASKING POLICY IDENTIFIER('m' || 'p')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '('."},
            {"SELECT COUNT(*) FROM IDENTIFIER('t' || 'p') AT(OFFSET => -1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '||'."},
        });
    }
}
