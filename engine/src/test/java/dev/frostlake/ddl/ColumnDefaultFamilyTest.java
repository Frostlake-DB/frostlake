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
 * A column's DEFAULT judged by numeric family, the codes a column's DEFAULT and a value written to a column are
 * refused with, and a DEFAULT CREATE OR ALTER TABLE would change (live-verified): a NUMBER column takes a NUMBER
 * only, a FLOAT column a NUMBER or a FLOAT, a DECFLOAT column a DECFLOAT only, and a literal past NUMBER's range
 * is a FLOAT; a handler reads 2262, 2263, 2260, 2023 and 2200 for those refusals; a DEFAULT CREATE OR ALTER
 * respells is kept while one it changes is refused.
 */
public class ColumnDefaultFamilyTest extends MeasuredCellsTestSupport {

    @Test
    public void defaultTypesByFamily() {
        engine.execute("CREATE OR REPLACE TABLE de (id INT, a VARCHAR DEFAULT 'x', b BOOLEAN, d DATE)");
        engine.execute("INSERT INTO de (id) VALUES (1)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE f3 (c INT DEFAULT TO_DOUBLE('1'))",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f4 (c INT DEFAULT CAST(1 AS DOUBLE))",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f5 (c NUMBER(10,2) DEFAULT 2.5::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f6 (c DECFLOAT DEFAULT 2.5::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f7 (c INT DEFAULT 2.5::DECFLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f8 (c DECFLOAT DEFAULT 2.5)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f9 (c FLOAT DEFAULT 2.5::DECFLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f13 (c INT DEFAULT PI())",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f14 (c VARCHAR DEFAULT 2.5::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f15 (c BOOLEAN DEFAULT 2.5::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f16 (c VARIANT DEFAULT 2.5::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f20 (c NUMBER(38,5) DEFAULT 'inf'::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f21 (c INT DEFAULT 1e400)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f22 (c INT DEFAULT 2.5::REAL)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f24 (c INT DEFAULT COALESCE(2.5::FLOAT, 1))",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE f25 (c INT DEFAULT IFF(TRUE, 1, 2.5::FLOAT))",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"ALTER TABLE de ADD COLUMN q1 INT DEFAULT 2.5::FLOAT",
                "REFUSED SQL compilation error: | Invalid column default expression [CAST(2.5 AS FLOAT)]"},
            {"ALTER TABLE de ADD COLUMN q2 INT DEFAULT SQRT(4)",
                "REFUSED SQL compilation error: | Invalid column default expression [SQRT(CAST(4 AS FLOAT))]"},
            {"CREATE OR REPLACE TABLE f28 (c INT DEFAULT 2.5::FLOAT NOT NULL)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"INSERT INTO de (b) VALUES ('x'::DATE)",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "BOOLEAN but got DATE for column B"},
            {"INSERT INTO de (b) SELECT CURRENT_DATE()",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "BOOLEAN but got DATE for column B"},
            {"UPDATE de SET b = CURRENT_DATE()",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "BOOLEAN but got DATE for column B"},
            {"INSERT INTO de (d) SELECT TRUE",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "DATE but got BOOLEAN for column D"},
            {"UPDATE de SET id = (SELECT 1, 2)",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column ID"},
            {"MERGE INTO de USING (SELECT 1 k) s ON de.id = s.k WHEN MATCHED THEN UPDATE SET b = "
                + "CURRENT_DATE()",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "BOOLEAN but got DATE for column B"},
            {"EXECUTE IMMEDIATE $$BEGIN UPDATE de SET b = CURRENT_DATE(); RETURN 'ok'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2023/22000"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE dn20 (c BINARY DEFAULT 'ab'); RETURN 'ok';"
                + " EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2260/22000"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE f2x (c INT DEFAULT 2.5::FLOAT); RETURN "
                + "'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2262/22000"},
            {"CREATE OR REPLACE TABLE g2 (c DECFLOAT DEFAULT 1)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g3 (c DECFLOAT DEFAULT 1.5e0)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g5 (c INT DEFAULT 1e38)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g8 (c INT DEFAULT 1.5e-50)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g9 (c DECFLOAT DEFAULT SQRT(4))",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g13 (c INT DEFAULT 999999999999999999999999999999999999999)",
                "REFUSED SQL compilation error: Error line 1 at position 43 | Integer literal is out of "
                + "representable range: 999999999999999999999999999999999999999"},
            {"CREATE OR REPLACE TABLE g14 (c DECFLOAT DEFAULT -1)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g16 (c DECFLOAT DEFAULT '1')",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g17 (c INT DEFAULT 1 + 2.5::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE g18 (c INT DEFAULT ROUND(2.5::FLOAT))",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
        });
    }

    @Test
    public void refusalCodesInHandlers() {
        engine.execute("CREATE OR REPLACE TABLE de (id INT, a VARCHAR DEFAULT 'x')");
        engine.execute("INSERT INTO de (id) VALUES (1)");
        engine.execute("CREATE SEQUENCE sq1");
        engine.execute("CREATE OR REPLACE TABLE de (id INT, a VARCHAR DEFAULT 'x')");
        engine.execute("INSERT INTO de (id) VALUES (1)");
        engine.execute("CREATE OR REPLACE FUNCTION sqf() RETURNS NUMBER AS '1'");
        assertCells(new String[][] {
            {"ALTER TABLE de ALTER COLUMN a SET DEFAULT 'y'",
                "REFUSED Unsupported feature 'Alter Column Set Default'."},
            {"ALTER TABLE de MODIFY COLUMN a SET DEFAULT 'y'",
                "REFUSED Unsupported feature 'Alter Column Set Default'."},
            {"ALTER TABLE de ALTER COLUMN id SET DEFAULT sq1.nextval",
                "REFUSED Unsupported feature 'Alter Column Set Default'."},
            {"CREATE OR REPLACE TABLE dr2 (s DATE DEFAULT '2024-01-02')",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " S"},
            {"CREATE OR REPLACE TABLE dr3 (c INT DEFAULT '5')",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"ALTER TABLE de ADD COLUMN z BOOLEAN DEFAULT 'true'",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "BOOLEAN but got VARCHAR(4) for column Z"},
            {"ALTER TABLE de ADD COLUMN w VARCHAR NOT NULL DEFAULT NULL",
                "REFUSED SQL compilation error: Non-nullable column 'W' cannot be added to non-empty table "
                + "'DE' unless it has a non-null default value."},
            {"ALTER TABLE de ADD COLUMN w2 INT NOT NULL",
                "REFUSED SQL compilation error: Non-nullable column 'W2' cannot be added to non-empty table "
                + "'DE' unless it has a non-null default value."},
            {"ALTER TABLE de ADD COLUMN z2 INT DEFAULT 'abc'",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got VARCHAR(3) for column Z2"},
            {"CREATE OR REPLACE TABLE dn2 (c NUMBER DEFAULT 2.5::FLOAT)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE dn3 (c VARCHAR DEFAULT 5)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE dn4 (c VARCHAR DEFAULT 1+1)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE dn9 (c BOOLEAN DEFAULT 1)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE dn10 (c DATE DEFAULT CURRENT_TIMESTAMP())",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE dn12 (c VARIANT DEFAULT 1)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE dn13 (c VARCHAR DEFAULT CURRENT_DATE())",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"ALTER TABLE de ADD COLUMN q2 VARCHAR DEFAULT 5",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VARCHAR(16777216) but got NUMBER(1,0) for column Q2"},
            {"CREATE OR REPLACE TABLE dn20 (c BINARY DEFAULT 'ab')",
                "REFUSED SQL compilation error: Default values are not allowed on 'BINARY' columns."},
            {"CREATE OR REPLACE TABLE dn22 (c VARCHAR DEFAULT sq1.nextval)",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"CREATE OR REPLACE TABLE dn24 (c INT DEFAULT SQRT(4))",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " C"},
            {"EXECUTE IMMEDIATE $$BEGIN ALTER TABLE de ALTER COLUMN a SET DEFAULT 'y'; RETURN 'ok'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2/0A000"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE dr3 (c INT DEFAULT '5'); RETURN 'ok'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2262/22000"},
            {"EXECUTE IMMEDIATE $$BEGIN ALTER TABLE de ADD COLUMN z BOOLEAN DEFAULT 'true'; RETURN 'ok'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2023/22000"},
            {"EXECUTE IMMEDIATE $$BEGIN ALTER TABLE de ADD COLUMN w VARCHAR NOT NULL DEFAULT NULL; RETURN "
                + "'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2200/22023"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE dn1 (c INT DEFAULT 1.5e0); RETURN 'ok'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "ok"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE dn3 (c VARCHAR DEFAULT 5); RETURN 'ok'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2262/22000"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR ALTER TABLE de (id INT, a VARCHAR DEFAULT 'q'); RETURN "
                + "'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2/0A000"},
            {"ALTER TABLE de ADD COLUMN k1 INT DEFAULT id + 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DE.ID + 1]"},
            {"ALTER TABLE de ADD COLUMN k2 INT DEFAULT id",
                "REFUSED SQL compilation error: | Invalid column default expression [DE.ID]"},
            {"ALTER TABLE de ADD COLUMN k3 INT DEFAULT SQRT(4)",
                "REFUSED SQL compilation error: | Invalid column default expression [SQRT(CAST(4 AS FLOAT))]"},
            {"ALTER TABLE de ADD COLUMN k4 INT DEFAULT sqf()",
                "REFUSED SQL compilation error: | Invalid column default expression [SQF()]"},
            {"EXECUTE IMMEDIATE $$BEGIN ALTER TABLE de ADD COLUMN k1 INT DEFAULT id + 1; RETURN 'ok'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$",
                "2263/22000"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE g13 (c INT DEFAULT "
                + "999999999999999999999999999999999999999); RETURN 'ok'; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || '/' || SQLSTATE; END;$$",
                "REFUSED SQL compilation error: Error line 1 at position 49 | Integer literal is out of "
                + "representable range: 999999999999999999999999999999999999999"},
            {"SELECT 999999999999999999999999999999999999999",
                "REFUSED SQL compilation error: Error line 1 at position 7 | Integer literal is out of "
                + "representable range: 999999999999999999999999999999999999999"},
            {"INSERT INTO de (id) VALUES (999999999999999999999999999999999999999)",
                "REFUSED SQL compilation error: Error line 1 at position 28 | Integer literal is out of "
                + "representable range: 999999999999999999999999999999999999999"},
        });
    }

    @Test
    public void createOrAlterKeepsOrRefusesDefault() {
        engine.execute("CREATE OR REPLACE TABLE de (id INT DEFAULT 1, a VARCHAR DEFAULT 'x')");
        engine.execute("INSERT INTO de (id) VALUES (1)");
        assertCells(new String[][] {
            {"CREATE OR ALTER TABLE de (id INT DEFAULT 1, a VARCHAR DEFAULT 'q')",
                "REFUSED Unsupported feature 'Alter Column Set Default'."},
            {"CREATE OR ALTER TABLE de (id INT DEFAULT 0 + 1, a VARCHAR DEFAULT 'x')",
                "REFUSED Unsupported feature 'Alter Column Set Default'."},
            {"CREATE OR ALTER TABLE de (id INT DEFAULT 2, a VARCHAR DEFAULT 'x')",
                "REFUSED Unsupported feature 'Alter Column Set Default'."},
        });
    }

    /** The DEFAULTs each family takes. */
    @Test
    public void acceptedDefaults() {
        engine.execute("CREATE OR REPLACE SEQUENCE sq1");
        engine.execute("CREATE OR REPLACE TABLE dn1 (c INT DEFAULT 1.5e0)");
        engine.execute("CREATE OR REPLACE TABLE dn5 (c NUMBER(10,2) DEFAULT 1.5)");
        engine.execute("CREATE OR REPLACE TABLE dn6 (c FLOAT DEFAULT 1)");
        engine.execute("CREATE OR REPLACE TABLE dn7 (c INT DEFAULT 1.5)");
        engine.execute("CREATE OR REPLACE TABLE dn8 (c VARCHAR DEFAULT TRUE)");
        engine.execute("CREATE OR REPLACE TABLE dn11 (c TIMESTAMP_NTZ DEFAULT CURRENT_DATE())");
        engine.execute("CREATE OR REPLACE TABLE dn16 (c INT DEFAULT NULL)");
        engine.execute("CREATE OR REPLACE TABLE dn17 (c NUMBER DEFAULT sq1.nextval)");
        engine.execute("CREATE OR REPLACE TABLE dn18 (c FLOAT DEFAULT 1.5)");
        engine.execute("CREATE OR REPLACE TABLE dn19 (c VARCHAR(3) DEFAULT 'abcdef')");
        engine.execute("CREATE OR REPLACE TABLE dn21 (c INT DEFAULT CAST(1.5e0 AS INT))");
        engine.execute("CREATE OR REPLACE TABLE dn23 (c NUMBER(5,0) DEFAULT 123456)");
        engine.execute("CREATE OR REPLACE TABLE dn25 (c VARCHAR DEFAULT UUID_STRING())");
        engine.execute("CREATE OR REPLACE TABLE dn26 (c INT DEFAULT LENGTH('ab'))");
        engine.execute("CREATE OR REPLACE TABLE dn27 (c DOUBLE DEFAULT 1e0 + 1)");
        engine.execute("CREATE OR REPLACE TABLE dn28 (c TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP())");
        engine.execute("CREATE OR REPLACE TABLE dn29 (c TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP())");
        engine.execute("CREATE OR REPLACE TABLE dn30 (c NUMBER(10,2) DEFAULT 1.5e0)");
        engine.execute("CREATE OR REPLACE TABLE dn31 (c ARRAY DEFAULT ARRAY_CONSTRUCT(1))");
        engine.execute("CREATE OR REPLACE TABLE dn32 (c OBJECT DEFAULT 1)");
        engine.execute("CREATE OR REPLACE TABLE f1 (c INT DEFAULT -1.5e0)");
        engine.execute("CREATE OR REPLACE TABLE f2 (c INT DEFAULT 1e0 + 1)");
        engine.execute("CREATE OR REPLACE TABLE f10 (c INT DEFAULT RANDOM())");
        engine.execute("CREATE OR REPLACE TABLE f11 (c INT DEFAULT (1.5e0))");
        engine.execute("CREATE OR REPLACE TABLE f12 (c INT DEFAULT 1.5e0 * 2)");
        engine.execute("CREATE OR REPLACE TABLE f17 (c DATE DEFAULT 2.5::FLOAT)");
        engine.execute("CREATE OR REPLACE TABLE f18 (c FLOAT DEFAULT SQRT(4))");
        engine.execute("CREATE OR REPLACE TABLE f19 (c INT DEFAULT 1.5e0::NUMBER)");
        engine.execute("CREATE OR REPLACE TABLE f23 (c INT DEFAULT DIV0(1e0, 2))");
        engine.execute("CREATE OR REPLACE TABLE g1 (c DECFLOAT DEFAULT 2.5::DECFLOAT)");
        engine.execute("CREATE OR REPLACE TABLE g4 (c DECFLOAT DEFAULT NULL)");
        engine.execute("CREATE OR REPLACE TABLE g6 (c INT DEFAULT 1e37)");
        engine.execute("CREATE OR REPLACE TABLE g7 (c FLOAT DEFAULT 1e400)");
        engine.execute("CREATE OR REPLACE TABLE g10 (c FLOAT DEFAULT 2.5::REAL)");
        engine.execute("CREATE OR REPLACE TABLE g11 (c NUMBER(10,2) DEFAULT 2.5::NUMBER(5,1))");
        engine.execute("CREATE OR REPLACE TABLE g12 (c INT DEFAULT 99999999999999999999999999999999999999)");
        engine.execute("CREATE OR REPLACE TABLE g15 (c DECFLOAT DEFAULT TRUE)");
        engine.execute("CREATE OR REPLACE TABLE g19 (c INT DEFAULT 1.0e0 + 0.5)");
        engine.execute("CREATE OR REPLACE TABLE g20 (c INT DEFAULT 1E2)");
    }
}
