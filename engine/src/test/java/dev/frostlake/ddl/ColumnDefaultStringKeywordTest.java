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
 * A column DEFAULT written as a string literal stays that string, whatever word it spells: {@code DEFAULT 'NULL'}
 * writes the text NULL and {@code DEFAULT 'current_date'} the text current_date, and GET_DDL and
 * INFORMATION_SCHEMA.COLUMNS tell each back quoted, a boolean default in capitals and an explicit DEFAULT NULL as
 * no default at all (all live-verified).
 */
public class ColumnDefaultStringKeywordTest extends MeasuredCellsTestSupport {

    @Test
    public void stringDefaultsSpellingKeywords() {
        engine.execute("CREATE OR REPLACE TABLE kd (id INT, e VARCHAR DEFAULT 'NULL', e2 VARCHAR DEFAULT 'null', f "
            + "VARCHAR DEFAULT 'n/a()', g VARCHAR DEFAULT 'true', h VARCHAR DEFAULT 'current_date', h2 "
            + "VARCHAR DEFAULT 'CURRENT_TIMESTAMP', i VARCHAR DEFAULT '', j VARCHAR DEFAULT 'it''s', k "
            + "VARCHAR DEFAULT NULL, l BOOLEAN DEFAULT TRUE, m DATE DEFAULT CURRENT_DATE(), n INT DEFAULT "
            + "1+1, p VARCHAR DEFAULT 'UUID_STRING()', q VARCHAR DEFAULT $$dol'lar$$, t BOOLEAN DEFAULT "
            + "false, u VARCHAR DEFAULT ('FALSE'))");
        engine.execute("INSERT INTO kd (id) VALUES (1)");
        engine.execute("INSERT INTO kd VALUES (2, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, "
            + "DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT)");
        engine.execute("CREATE OR REPLACE TABLE kn (c VARCHAR DEFAULT 'NULL' NOT NULL)");
        engine.execute("INSERT INTO kn VALUES (DEFAULT)");
        engine.execute("CREATE OR REPLACE TABLE ka (id INT)");
        engine.execute("INSERT INTO ka VALUES (1)");
        engine.execute("ALTER TABLE ka ADD COLUMN y VARCHAR NOT NULL DEFAULT 'NULL'");
        engine.execute("ALTER TABLE ka ADD COLUMN z VARCHAR DEFAULT 'current_date'");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM kd WHERE e = 'NULL' AND e2 = 'null' AND f = 'n/a()' AND g = 'true' AND "
                + "h = 'current_date' AND h2 = 'CURRENT_TIMESTAMP' AND i = '' AND j = 'it''s' AND k IS NULL AND"
                + " l AND m = CURRENT_DATE() AND n = 2 AND p = 'UUID_STRING()' AND q = 'dol''lar' AND NOT t AND"
                + " u = 'FALSE'",
                "2"},
            {"SELECT COUNT(*) FROM kn WHERE c = 'NULL'",
                "1"},
            {"SELECT y || '|' || z FROM ka",
                "NULL|current_date"},
            {"SELECT LISTAGG(COLUMN_NAME || '=' || NVL(COLUMN_DEFAULT, '-'), ' ') WITHIN GROUP (ORDER BY "
                + "ORDINAL_POSITION) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'KD' AND TABLE_SCHEMA ="
                + " CURRENT_SCHEMA()",
                "ID=- E='NULL' E2='null' F='n/a()' G='true' H='current_date' H2='CURRENT_TIMESTAMP' I='' "
                + "J='it''s' K=- L=TRUE M=CURRENT_DATE() N=1 + 1 P='UUID_STRING()' Q='dol''lar' T=FALSE "
                + "U='FALSE'"},
            {"SELECT LISTAGG(COLUMN_NAME || '=' || NVL(COLUMN_DEFAULT, '-'), ' ') WITHIN GROUP (ORDER BY "
                + "ORDINAL_POSITION) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'KA' AND TABLE_SCHEMA ="
                + " CURRENT_SCHEMA()",
                "ID=- Y='NULL' Z='current_date'"},
            {"SELECT GET_DDL('TABLE', 'kd')",
                "create or replace TABLE KD ( | \tID NUMBER(38,0), | "
                + "\tE VARCHAR(16777216) DEFAULT 'NULL', | "
                + "\tE2 VARCHAR(16777216) DEFAULT 'null', | "
                + "\tF VARCHAR(16777216) DEFAULT 'n/a()', | "
                + "\tG VARCHAR(16777216) DEFAULT 'true', | "
                + "\tH VARCHAR(16777216) DEFAULT 'current_date', | "
                + "\tH2 VARCHAR(16777216) DEFAULT 'CURRENT_TIMESTAMP', | "
                + "\tI VARCHAR(16777216) DEFAULT '', | \tJ VARCHAR(16777216) DEFAULT 'it''s', | "
                + "\tK VARCHAR(16777216), | \tL BOOLEAN DEFAULT TRUE, | "
                + "\tM DATE DEFAULT CURRENT_DATE(), | \tN NUMBER(38,0) DEFAULT 1 + 1, | "
                + "\tP VARCHAR(16777216) DEFAULT 'UUID_STRING()', | "
                + "\tQ VARCHAR(16777216) DEFAULT 'dol''lar', | \tT BOOLEAN DEFAULT FALSE, | "
                + "\tU VARCHAR(16777216) DEFAULT 'FALSE' | );"},
            {"SELECT GET_DDL('TABLE', 'ka')",
                "create or replace TABLE KA ( | \tID NUMBER(38,0), | "
                + "\tY VARCHAR(16777216) NOT NULL DEFAULT 'NULL', | "
                + "\tZ VARCHAR(16777216) DEFAULT 'current_date' | );"},
            {"CREATE OR REPLACE TABLE kc (s DATE DEFAULT '2024-01-02')",
                "REFUSED SQL compilation error: | Default value data type does not match data type for column"
                + " S"},
            {"ALTER TABLE ka ADD COLUMN w BOOLEAN DEFAULT 'true'",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "BOOLEAN but got VARCHAR(4) for column W"},
        });
    }
}
