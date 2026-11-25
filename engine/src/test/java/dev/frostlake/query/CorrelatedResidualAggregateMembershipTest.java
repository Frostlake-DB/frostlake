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

import org.junit.jupiter.api.Test;

/**
 * A scalar aggregate and an IN over a subquery that reads the outer row through an equality and through other
 * conjuncts as well (live-verified): {@code (SELECT MAX(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w)} and
 * {@code a.w IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v)} answer as the subquery run for each outer row
 * does, over NULL keys, repeated keys, keys of two kinds, an empty inner table, several keys and an outer relation
 * that is derived or joined. The inner rows are kept once by key and each outer row aggregates, or compares with, the
 * rows of its own key whose residual holds. A fault met computing the inner rows has the subquery run for each row.
 */
public class CorrelatedResidualAggregateMembershipTest extends MeasuredCellsTestSupport {

    @Test
    public void membershipAndAggregateBesideResidual() {
        engine.execute("CREATE OR REPLACE TABLE ka(id INT, v INT, w INT, s VARCHAR) AS SELECT * FROM VALUES "
            + "(1,1,10,'1'),(2,5,20,'2'),(3,0,30,'x'),(NULL,1,10,NULL),(4,2,NULL,'4'),(5,0,30,'5.0')");
        engine.execute("CREATE OR REPLACE TABLE kb(k INT, j INT, j2 INT) AS SELECT * FROM VALUES "
            + "(1,5,10),(1,6,11),(2,3,20),(2,7,NULL),(2,NULL,20),(NULL,9,10),(5,1,30),(5,2,30),(7,1,1)");
        engine.execute("CREATE OR REPLACE TABLE kz(k INT, j INT, z INT) AS SELECT * FROM VALUES "
            + "(1,5,1),(2,25,5),(9,5,0)");
        engine.execute("CREATE OR REPLACE TABLE ke(k INT, j INT, j2 INT)");
        engine.execute("CREATE OR REPLACE TABLE kv(k VARIANT, j INT) AS SELECT PARSE_JSON(c), j FROM VALUES "
            + "('\"2\"',1),('3.0',2),('1',3),('null',4),('\"x\"',5) AS t(c,j)");
        engine.execute("CREATE OR REPLACE TABLE kt(k VARCHAR, j INT) AS SELECT * FROM VALUES "
            + "('1',1),('02',2),('2',3),('5',4)");
        engine.execute("CREATE OR REPLACE TABLE ktx(k VARCHAR, j INT) AS SELECT * FROM VALUES ('1',1),('x',2)");
        assertCells(new String[][] {
            {"SELECT id, w IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|true ;; null|false"},
            {"SELECT id, w NOT IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v) FROM ka a "
                + "ORDER BY id",
                "1|false ;; 2|null ;; 3|true ;; 4|true ;; 5|false ;; null|true"},
            {"SELECT id FROM ka a WHERE w IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j >= a.v) "
                + "ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM ka a WHERE w NOT IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j >= "
                + "a.v) ORDER BY id",
                "3 ;; 4 ;; null"},
            {"SELECT id, w IN (SELECT b.j2 FROM ke b WHERE b.k = a.id AND b.j > a.v), w NOT IN (SELECT"
                + " b.j2 FROM ke b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER BY id",
                "1|false|true ;; 2|false|true ;; 3|false|true ;; 4|false|true ;; 5|false|true ;; "
                + "null|false|true"},
            {"SELECT id, v IN (SELECT b.j - 4 FROM kb b WHERE b.k = a.id AND b.j2 > a.w - 11) FROM ka "
                + "a ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; null|false"},
            {"SELECT id, w IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v AND b.j2 IS NOT "
                + "NULL) FROM ka a ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|true ;; null|false"},
            {"SELECT id, w IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND (b.j > a.v OR b.j IS NULL)) "
                + "FROM ka a ORDER BY id",
                "1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|true ;; null|false"},
            {"SELECT id, s IN (SELECT b.j2 FROM kb b WHERE b.k = a.id AND b.j > a.v) FROM ka a WHERE "
                + "id < 3 ORDER BY id",
                "1|false ;; 2|false"},
            {"SELECT id, w IN (SELECT b.j2 FROM kb b WHERE b.k + 0 = a.id AND b.j > a.v), w IN (SELECT"
                + " b.j2 FROM kb b WHERE a.id = b.k AND a.v < b.j) FROM ka a ORDER BY id",
                "1|true|true ;; 2|false|false ;; 3|false|false ;; 4|false|false ;; 5|true|true ;; "
                + "null|false|false"},
            {"SELECT id, (w, v) IN (SELECT b.j2, b.j FROM kb b WHERE b.k = a.id AND b.j > a.v) FROM ka"
                + " a ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; null|false"},
            {"SELECT id, w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w NOT IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka "
                + "a ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w NOT IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a"
                + " ORDER BY id",
                "1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|true ;; null|true"},
            {"SELECT id FROM ka a WHERE a.id < 3 AND w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id "
                + "AND b.j > a.v)",
                "1"},
            {"SELECT id, w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a "
                + "WHERE id = 1",
                "1|false"},
            {"SELECT id, w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a "
                + "WHERE id = 99",
                ""},
            {"SELECT id, (SELECT MAX(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a ORDER "
                + "BY id",
                "1|6 ;; 2|3 ;; 3|null ;; 4|null ;; 5|2 ;; null|null"},
            {"SELECT id, (SELECT COUNT(*) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w), (SELECT "
                + "COUNT(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a ORDER BY id",
                "1|2|2 ;; 2|2|1 ;; 3|0|0 ;; 4|0|0 ;; 5|2|2 ;; null|0|0"},
            {"SELECT id, (SELECT SUM(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w), (SELECT "
                + "AVG(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a ORDER BY id",
                "1|11|5.500000 ;; 2|3|3.000000 ;; 3|null|null ;; 4|null|null ;; 5|3|1.500000 ;; "
                + "null|null|null"},
            {"SELECT id FROM ka a WHERE a.v < (SELECT MAX(b.j) FROM kb b WHERE b.k = a.id AND b.j2 >= "
                + "a.w) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id, (SELECT MAX(b.j) FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, (SELECT COUNT(*) FROM ke b WHERE b.k = a.id AND b.j2 >= a.w), (SELECT "
                + "SUM(b.j) FROM ke b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a ORDER BY id",
                "1|0|null ;; 2|0|null ;; 3|0|null ;; 4|0|null ;; 5|0|null ;; null|0|null"},
            {"SELECT id, (SELECT SUM(b.j) + 1 FROM kb b WHERE b.k = a.id AND b.j2 >= a.w) FROM ka a "
                + "ORDER BY id",
                "1|12 ;; 2|4 ;; 3|null ;; 4|null ;; 5|4 ;; null|null"},
            {"SELECT id, (SELECT MAX(kv.j) FROM kv WHERE kv.k = a.id) FROM ka a ORDER BY id",
                "1|3 ;; 2|null ;; 3|2 ;; 4|null ;; 5|null ;; null|null"},
            {"SELECT id, EXISTS (SELECT 1 FROM kv WHERE kv.k = a.id) FROM ka a ORDER BY id",
                "1|true ;; 2|false ;; 3|true ;; 4|false ;; 5|false ;; null|false"},
            {"SELECT id, (SELECT MAX(b.j) FROM kt b WHERE b.k = a.id) FROM ka a ORDER BY id",
                "1|1 ;; 2|3 ;; 3|null ;; 4|null ;; 5|4 ;; null|null"},
            {"SELECT id, (SELECT MAX(b.j) FROM ktx b WHERE b.k = a.id) FROM ka a ORDER BY id",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT id, (SELECT MAX(b.j) FROM kb b WHERE b.k = a.s) FROM ka a WHERE id <> 3 ORDER BY "
                + "id",
                "1|6 ;; 2|7 ;; 4|null ;; 5|2"},
            {"SELECT id, (SELECT MAX(b.j) FROM kb b WHERE b.k = a.s) FROM ka a ORDER BY id",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT id, w IN (SELECT kv.j FROM kv WHERE kv.k = a.id) FROM ka a ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; null|false"},
        });
    }

    @Test
    public void typesAndShapes() {
        engine.execute("CREATE OR REPLACE TABLE ta(id INT, id2 INT, v INT, w NUMBER(10,2), s VARCHAR, d DATE) AS"
            + " SELECT * FROM VALUES "
            + "(1,1,1,10,'b','2024-01-05'),(2,1,5,20.5,'a','2024-02-01'),(2,2,0,0,'z',NULL),(3,1,0,30,NULL,'2024-01-01'),(NULL,1,1,10,'b','2024-01-01'),(4,NULL,2,NULL,'c','2023-12-31'),(1,1,9,10,'b','2024-01-05')");
        engine.execute("CREATE OR REPLACE TABLE tb(k INT, k2 INT, n INT, x NUMBER(10,2), f FLOAT, s VARCHAR, d "
            + "DATE, bo BOOLEAN, df DECFLOAT) AS SELECT * FROM VALUES "
            + "(1,1,5,1.25,0.5,'q','2024-01-01',TRUE,1.5),(1,1,6,2.5,1.5,'r','2024-03-01',FALSE,2),(1,2,7,NULL,NULL,NULL,NULL,NULL,NULL),(2,1,3,10.10,2.25,'m','2023-01-01',TRUE,3.25),(2,1,3,10.10,2.25,'m','2023-01-01',TRUE,3.25),(2,2,NULL,5,-1,'n','2025-01-01',FALSE,-7),(NULL,1,9,9,9,'zz','2020-01-01',TRUE,9),(3,1,1,0.01,1e10,'a','2024-01-01',TRUE,1e30),(9,9,100,100,100,'y','2000-01-01',FALSE,100)");
        engine.execute("CREATE OR REPLACE TABLE te(k INT, k2 INT, n INT, x NUMBER(10,2), f FLOAT, s VARCHAR, d "
            + "DATE, bo BOOLEAN, df DECFLOAT)");
        assertCells(new String[][] {
            {"SELECT id, v, (SELECT COUNT(*) FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a "
                + "ORDER BY 1, 2",
                "1|1|3 ;; 1|9|0 ;; 2|0|2 ;; 2|5|0 ;; 3|0|1 ;; 4|2|0 ;; null|1|0"},
            {"SELECT id, v, (SELECT COUNT(b.n) FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT "
                + "COUNT(DISTINCT b.n) FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a ORDER BY 1, 2",
                "1|1|3|3 ;; 1|9|0|0 ;; 2|0|2|1 ;; 2|5|0|0 ;; 3|0|1|1 ;; 4|2|0|0 ;; null|1|0|0"},
            {"SELECT id, v, (SELECT MIN(b.s) FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT "
                + "MAX(b.d) FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT MAX(b.bo) FROM tb b WHERE "
                + "b.k = a.id AND b.n >= a.v) FROM ta a ORDER BY 1, 2",
                "1|1|q|2024-03-01|true ;; 1|9|null|null|null ;; 2|0|m|2023-01-01|true ;; "
                + "2|5|null|null|null ;; 3|0|a|2024-01-01|true ;; 4|2|null|null|null ;; "
                + "null|1|null|null|null"},
            {"SELECT id, v, (SELECT SUM(b.df) FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT "
                + "MAX(b.df) FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT AVG(b.df) FROM tb b WHERE "
                + "b.k = a.id AND b.n >= a.v) FROM ta a ORDER BY 1, 2",
                "1|1|3.5|2|1.75 ;; 1|9|null|null|null ;; 2|0|6.5|3.25|3.25 ;; 2|5|null|null|null ;; "
                + "3|0|1000000000000000000000000000000|1000000000000000000000000000000|1000000000000000000000000000000"
                + " ;; 4|2|null|null|null ;; null|1|null|null|null"},
            {"SELECT id, v, (SELECT SUM(b.n) + 1 FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT "
                + "MAX(b.n) - MIN(b.n) FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT SUM(b.n * 2) "
                + "FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a ORDER BY 1, 2",
                "1|1|19|2|36 ;; 1|9|null|null|null ;; 2|0|7|0|12 ;; 2|5|null|null|null ;; 3|0|2|0|2 ;; "
                + "4|2|null|null|null ;; null|1|null|null|null"},
            {"SELECT id, id2, v, (SELECT MAX(b.n) FROM tb b WHERE b.k = a.id AND b.k2 = a.id2 AND b.n "
                + "> a.v) FROM ta a ORDER BY 1, 2, 3",
                "1|1|1|6 ;; 1|1|9|null ;; 2|1|5|null ;; 2|2|0|null ;; 3|1|0|1 ;; 4|null|2|null ;; "
                + "null|1|1|null"},
            {"SELECT id, v, (SELECT SUM(b.x) FROM tb b WHERE b.k = a.id AND (b.n > a.v OR b.n IS "
                + "NULL)) FROM ta a ORDER BY 1, 2",
                "1|1|3.75 ;; 1|9|null ;; 2|0|25.20 ;; 2|5|5.00 ;; 3|0|0.01 ;; 4|2|null ;; null|1|null"},
            {"SELECT id, w, (SELECT MAX(b.n) FROM tb b WHERE b.k = a.id AND b.x < a.w) FROM ta a ORDER"
                + " BY 1, 2",
                "1|10.00|6 ;; 1|10.00|6 ;; 2|0.00|null ;; 2|20.50|3 ;; 3|30.00|1 ;; 4|null|null ;; "
                + "null|10.00|null"},
            {"SELECT id, d, (SELECT COUNT(*) FROM tb b WHERE b.k = a.id AND b.d < a.d) FROM ta a ORDER"
                + " BY 1, 2",
                "1|2024-01-05|1 ;; 1|2024-01-05|1 ;; 2|2024-02-01|2 ;; 2|null|0 ;; 3|2024-01-01|0 ;; "
                + "4|2023-12-31|0 ;; null|2024-01-01|0"},
            {"SELECT id, s, (SELECT MAX(b.n) FROM tb b WHERE b.k = a.id AND b.s > a.s) FROM ta a ORDER"
                + " BY 1, 2",
                "1|b|6 ;; 1|b|6 ;; 2|a|3 ;; 2|z|null ;; 3|null|null ;; 4|c|null ;; null|b|null"},
            {"SELECT id, v FROM ta a WHERE a.v < (SELECT MAX(b.n) FROM tb b WHERE b.k = a.id AND b.n >"
                + " a.v - 1) ORDER BY 1, 2",
                "1|1 ;; 2|0 ;; 3|0"},
            {"SELECT id, v FROM ta a WHERE (SELECT COUNT(*) FROM tb b WHERE b.k = a.id AND b.n > a.v) "
                + "= 0 ORDER BY 1, 2",
                "1|9 ;; 2|5 ;; 4|2 ;; null|1"},
            {"SELECT id, v, (SELECT COUNT(*) FROM te b WHERE b.k = a.id AND b.n >= a.v), (SELECT "
                + "SUM(b.n) FROM te b WHERE b.k = a.id AND b.n >= a.v), (SELECT AVG(b.x) FROM te b WHERE "
                + "b.k = a.id AND b.n >= a.v), (SELECT MAX(b.s) FROM te b WHERE b.k = a.id AND b.n >= a.v) "
                + "FROM ta a ORDER BY 1, 2",
                "1|1|0|null|null|null ;; 1|9|0|null|null|null ;; 2|0|0|null|null|null ;; "
                + "2|5|0|null|null|null ;; 3|0|0|null|null|null ;; 4|2|0|null|null|null ;; "
                + "null|1|0|null|null|null"},
            {"SELECT id, v, (SELECT SUM(b.n) FROM tb b WHERE b.k = a.id AND b.n >= a.v AND b.k2 = 1) "
                + "FROM ta a ORDER BY 1, 2",
                "1|1|11 ;; 1|9|null ;; 2|0|6 ;; 2|5|null ;; 3|0|1 ;; 4|2|null ;; null|1|null"},
            {"SELECT id, v, (SELECT COUNT(*) + SUM(b.n) FROM tb b WHERE a.id = b.k AND a.v <= b.n) "
                + "FROM ta a ORDER BY 1, 2",
                "1|1|21 ;; 1|9|null ;; 2|0|8 ;; 2|5|null ;; 3|0|2 ;; 4|2|null ;; null|1|null"},
            {"SELECT id, v, (SELECT MAX(b.n) FROM tb b WHERE b.k = a.id AND b.n >= a.v) + 1 FROM ta a "
                + "ORDER BY 1, 2",
                "1|1|8 ;; 1|9|null ;; 2|0|4 ;; 2|5|null ;; 3|0|2 ;; 4|2|null ;; null|1|null"},
            {"SELECT id, v, (SELECT SUM(b.n) FROM tb b WHERE b.k = a.id AND b.n >= a.v) AS t FROM ta a"
                + " ORDER BY t NULLS FIRST, 1, 2",
                "1|9|null ;; 2|5|null ;; 4|2|null ;; null|1|null ;; 3|0|1 ;; 2|0|6 ;; 1|1|18"},
            {"SELECT id, v, (SELECT MIN(b.x) FROM tb b WHERE b.k = a.id AND b.n BETWEEN a.v AND a.v + "
                + "5) FROM ta a ORDER BY 1, 2",
                "1|1|1.25 ;; 1|9|null ;; 2|0|10.10 ;; 2|5|null ;; 3|0|0.01 ;; 4|2|null ;; null|1|null"},
            {"SELECT SUM((SELECT SUM(b.n) FROM tb b WHERE b.k = a.id AND b.n >= a.v)) FROM ta a",
                "25"},
            {"SELECT id, v, (SELECT COUNT_IF(b.bo) FROM tb b WHERE b.k = a.id AND b.n >= a.v), (SELECT"
                + " LISTAGG(b.s, ',') WITHIN GROUP (ORDER BY b.s) FROM tb b WHERE b.k = a.id AND b.n >= "
                + "a.v) FROM ta a ORDER BY 1, 2",
                "1|1|1|q,r ;; 1|9|null| ;; 2|0|2|m,m ;; 2|5|null| ;; 3|0|1|a ;; 4|2|null| ;; null|1|null|"},
            {"SELECT id, v, (SELECT TYPEOF(TO_VARIANT(SUM(b.n))) FROM tb b WHERE b.k = a.id AND b.n >="
                + " a.v), (SELECT TYPEOF(TO_VARIANT(AVG(b.x))) FROM tb b WHERE b.k = a.id AND b.n >= a.v) "
                + "FROM ta a WHERE id = 1 ORDER BY 1, 2",
                "1|1|INTEGER|DECIMAL ;; 1|9|null|null"},
            {"SELECT id, v, SYSTEM$TYPEOF((SELECT SUM(b.n) FROM tb b WHERE b.k = a.id AND b.n >= "
                + "a.v)), SYSTEM$TYPEOF((SELECT AVG(b.x) FROM tb b WHERE b.k = a.id AND b.n >= a.v)) FROM "
                + "ta a WHERE id = 1 ORDER BY 1, 2",
                "1|1|NUMBER(38,0)[SB8]|NUMBER(28,8)[SB16] ;; 1|9|NUMBER(38,0)[SB8]|NUMBER(28,8)[SB16]"},
            {"SELECT id, v, s IN (SELECT b.s FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a "
                + "ORDER BY 1, 2",
                "1|1|false ;; 1|9|false ;; 2|0|false ;; 2|5|false ;; 3|0|false ;; 4|2|false ;; "
                + "null|1|false"},
            {"SELECT id, v, d NOT IN (SELECT b.d FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a "
                + "ORDER BY 1, 2",
                "1|1|null ;; 1|9|true ;; 2|0|null ;; 2|5|true ;; 3|0|false ;; 4|2|true ;; null|1|true"},
            {"SELECT id, id2, v, w IN (SELECT b.x * 2 FROM tb b WHERE b.k = a.id AND b.k2 = a.id2 AND "
                + "b.n >= a.v) FROM ta a ORDER BY 1, 2, 3",
                "1|1|1|false ;; 1|1|9|false ;; 2|1|5|false ;; 2|2|0|false ;; 3|1|0|false ;; "
                + "4|null|2|false ;; null|1|1|false"},
            {"SELECT id, v, v NOT IN (SELECT b.n - 4 FROM tb b WHERE b.k = a.id AND b.f > a.v - 2) "
                + "FROM ta a ORDER BY 1, 2",
                "1|1|false ;; 1|9|true ;; 2|0|null ;; 2|5|true ;; 3|0|true ;; 4|2|true ;; null|1|true"},
            {"SELECT id, v FROM ta a WHERE v IN (SELECT b.n - 4 FROM tb b WHERE b.k = a.id AND b.f > "
                + "a.v - 2) ORDER BY 1, 2",
                "1|1"},
            {"SELECT id, v, w IN (SELECT b.df FROM tb b WHERE b.k = a.id AND b.n >= a.v), w IN (SELECT"
                + " b.f FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a ORDER BY 1, 2",
                "1|1|false|false ;; 1|9|false|false ;; 2|0|false|false ;; 2|5|false|false ;; "
                + "3|0|false|false ;; 4|2|false|false ;; null|1|false|false"},
            {"SELECT id, v, NULL IN (SELECT b.n FROM tb b WHERE b.k = a.id AND b.n >= a.v), NULL NOT "
                + "IN (SELECT b.n FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a ORDER BY 1, 2",
                "1|1|null|null ;; 1|9|null|null ;; 2|0|null|null ;; 2|5|null|null ;; 3|0|null|null ;; "
                + "4|2|null|null ;; null|1|null|null"},
            {"SELECT id, v, s IN (SELECT b.n FROM tb b WHERE b.k = a.id AND b.n >= a.v) FROM ta a "
                + "WHERE s <> 'c' ORDER BY 1, 2",
                "REFUSED Numeric value 'b' is not recognized"},
            {"SELECT id, v, CAST(NULL AS INT) NOT IN (SELECT b.n FROM tb b WHERE b.k = a.id AND b.n > "
                + "a.v) FROM ta a ORDER BY 1, 2",
                "1|1|null ;; 1|9|null ;; 2|0|null ;; 2|5|null ;; 3|0|null ;; 4|2|null ;; null|1|null"},
        });
    }

    @Test
    public void largerRelationsAgreeWithJoins() {
        engine.execute("CREATE OR REPLACE TABLE ga(id INT, v INT, w INT) AS SELECT SEQ4(), MOD(SEQ4(), 7), "
            + "MOD(SEQ4(), 13) FROM TABLE(GENERATOR(ROWCOUNT => 200))");
        engine.execute("CREATE OR REPLACE TABLE gb(k INT, j INT, j2 INT) AS SELECT IFF(MOD(SEQ4(), 17) = 0, "
            + "NULL, MOD(SEQ4(), 250)), IFF(MOD(SEQ4(), 23) = 0, NULL, MOD(SEQ4(), 11)), MOD(SEQ4(), "
            + "13) FROM TABLE(GENERATOR(ROWCOUNT => 2000))");
        engine.execute("CREATE OR REPLACE TABLE xa(id NUMBER(10,2), v INT, s VARCHAR) AS SELECT * FROM VALUES "
            + "(1.00, 0, 'a'), (2.50, 0, 'b'), (2, 1, 'c'), (NULL, 0, 'd')");
        engine.execute("CREATE OR REPLACE TABLE xb(k INT, j INT, s VARCHAR) AS SELECT * FROM VALUES (1, 1, 'a'),"
            + " (1, 2, 'z'), (2, 3, 'c'), (2, 3, 'c'), (3, 4, 'q')");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM (SELECT a.id, (SELECT SUM(b.j) FROM gb b WHERE b.k = a.id AND b.j2 "
                + ">= a.w) s FROM ga a) x JOIN (SELECT a.id, SUM(b.j) s FROM ga a LEFT JOIN gb b ON b.k = "
                + "a.id AND b.j2 >= a.w GROUP BY a.id) y ON x.id = y.id WHERE EQUAL_NULL(x.s, y.s)",
                "200"},
            {"SELECT COUNT(*) FROM (SELECT a.id, (SELECT COUNT(b.j) FROM gb b WHERE b.k = a.id AND "
                + "b.j2 >= a.w) s FROM ga a) x JOIN (SELECT a.id, COUNT(b.j) s FROM ga a LEFT JOIN gb b ON "
                + "b.k = a.id AND b.j2 >= a.w GROUP BY a.id) y ON x.id = y.id WHERE EQUAL_NULL(x.s, y.s)",
                "200"},
            {"SELECT COUNT(*) FROM (SELECT a.id, (SELECT MAX(b.j) - MIN(b.j2) FROM gb b WHERE b.k = "
                + "a.id AND b.j > a.v) s FROM ga a) x JOIN (SELECT a.id, MAX(b.j) - MIN(b.j2) s FROM ga a "
                + "LEFT JOIN gb b ON b.k = a.id AND b.j > a.v GROUP BY a.id) y ON x.id = y.id WHERE "
                + "EQUAL_NULL(x.s, y.s)",
                "200"},
            {"SELECT COUNT(*), COUNT_IF(w IN (SELECT b.j2 FROM gb b WHERE b.k = a.id AND b.j > a.v)), "
                + "COUNT_IF(w NOT IN (SELECT b.j2 FROM gb b WHERE b.k = a.id AND b.j > a.v)), COUNT_IF(v IN"
                + " (SELECT b.j FROM gb b WHERE b.k = a.id AND b.j2 > a.w)), COUNT_IF(v NOT IN (SELECT b.j "
                + "FROM gb b WHERE b.k = a.id AND b.j2 > a.w)) FROM ga a",
                "200|112|88|63|116"},
            {"SELECT SUM((SELECT AVG(b.j) FROM gb b WHERE b.k = a.id AND b.j2 >= a.w)), SUM((SELECT "
                + "COUNT(*) FROM gb b WHERE b.k = a.id AND b.j2 >= a.w)), SUM((SELECT COUNT(DISTINCT b.j) "
                + "FROM gb b WHERE b.k = a.id AND b.j2 >= a.w)) FROM ga a",
                "988.016667|866|826"},
            {"SELECT id, v, (SELECT SUM(b.j) FROM xb b WHERE b.k = a.id AND b.j > a.v), (SELECT "
                + "COUNT(*) FROM xb b WHERE b.k = a.id AND b.j > a.v), s IN (SELECT b.s FROM xb b WHERE b.k"
                + " = a.id AND b.j > a.v) FROM xa a ORDER BY 1, 2",
                "1.00|0|3|2|true ;; 2.00|1|6|2|true ;; 2.50|0|null|0|false ;; null|0|null|0|false"},
            {"SELECT a.id, (SELECT MAX(b.s) FROM xb b WHERE b.k = a.id AND b.s >= a.s) FROM (SELECT "
                + "id, s FROM xa WHERE v = 0) a ORDER BY 1",
                "1.00|z ;; 2.50|null ;; null|null"},
            {"SELECT a.id, c.n, (SELECT SUM(b.j) FROM xb b WHERE b.k = a.id AND b.j <= c.n) FROM xa a "
                + "JOIN (SELECT 1 n UNION ALL SELECT 3) c ON TRUE ORDER BY 1, 2",
                "1.00|1|1 ;; 1.00|3|3 ;; 2.00|1|null ;; 2.00|3|6 ;; 2.50|1|null ;; 2.50|3|null ;; "
                + "null|1|null ;; null|3|null"},
            {"SELECT a.id, (SELECT COUNT(*) FROM xb b WHERE b.k = a.id AND b.j > a.v AND b.s <> 'z') "
                + "FROM xa a ORDER BY 1",
                "1.00|1 ;; 2.00|2 ;; 2.50|0 ;; null|0"},
            {"SELECT a.id, a.v FROM xa a WHERE a.v NOT IN (SELECT b.j - 3 FROM xb b WHERE b.k = a.id "
                + "AND b.s >= a.s) ORDER BY 1, 2",
                "1.00|0 ;; 2.00|1 ;; 2.50|0 ;; null|0"},
        });
    }
}
