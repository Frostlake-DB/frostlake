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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A quantified comparison's truth (live-verified): {@code = ANY} is membership, three-valued (two-valued for a query
 * that reads the row around it, see {@link CorrelatedMembershipTruthTest}); any other operator
 * under ANY or SOME asks whether some row satisfies it, so a NULL subject or a NULL row leaves it FALSE; ALL is
 * FALSE when a row fails it, TRUE over no row, and for a NULL subject FALSE against a column that cannot answer NULL
 * (a NOT NULL column, COALESCE over one, a VALUES list of several rows without NULL) and UNKNOWN against any other.
 * A NULL subject that reads no column stays UNKNOWN whatever the query answers.
 * Each cell runs in a select list, under NOT in a WHERE, in a CASE, and as the condition of an UPDATE or a DELETE.
 */
public class QuantifiedComparisonTruthTest extends MeasuredCellsTestSupport {

    @Test
    public void matrixOfOperatorsAndProbes() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        assertAnswer("1|false|false|false|false|false|false|false ;; "
            + "2|false|false|false|false|false|false|false ;; "
            + "3|false|false|false|false|false|false|false ;; "
            + "4|false|false|false|false|false|false|false ;; "
            + "5|false|false|false|false|false|false|false",
            "SELECT id, k = ANY (SELECT k FROM e1), k <> ANY (SELECT k FROM e1), k != ANY (SELECT k "
            + "FROM e1), k < ANY (SELECT k FROM e1), k <= ANY (SELECT k FROM e1), k > ANY (SELECT k "
            + "FROM e1), k >= ANY (SELECT k FROM e1) FROM p ORDER BY id");
        assertAnswer("1|false|false|false|false|false|false|false ;; "
            + "2|false|false|false|false|false|false|false ;; "
            + "3|false|false|false|false|false|false|false ;; "
            + "4|false|false|false|false|false|false|false ;; "
            + "5|false|false|false|false|false|false|false",
            "SELECT id, k = ANY (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k <> ANY (SELECT k"
            + " FROM e1 WHERE e1.k IS NULL OR p.id > 0), k != ANY (SELECT k FROM e1 WHERE e1.k IS NULL "
            + "OR p.id > 0), k < ANY (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k <= ANY "
            + "(SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k > ANY (SELECT k FROM e1 WHERE e1.k "
            + "IS NULL OR p.id > 0), k >= ANY (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0) FROM p "
            + "ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ANY (SELECT k FROM e1), NULL <> ANY (SELECT k FROM e1), NULL != ANY "
            + "(SELECT k FROM e1), NULL < ANY (SELECT k FROM e1), NULL <= ANY (SELECT k FROM e1), NULL "
            + "> ANY (SELECT k FROM e1), NULL >= ANY (SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ANY (SELECT k FROM e1), CAST(NULL AS INT) <> ANY (SELECT k "
            + "FROM e1), CAST(NULL AS INT) != ANY (SELECT k FROM e1), CAST(NULL AS INT) < ANY (SELECT k"
            + " FROM e1), CAST(NULL AS INT) <= ANY (SELECT k FROM e1), CAST(NULL AS INT) > ANY (SELECT "
            + "k FROM e1), CAST(NULL AS INT) >= ANY (SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ANY (SELECT k FROM e1), NULL::INT <> ANY (SELECT k FROM e1), "
            + "NULL::INT != ANY (SELECT k FROM e1), NULL::INT < ANY (SELECT k FROM e1), NULL::INT <= "
            + "ANY (SELECT k FROM e1), NULL::INT > ANY (SELECT k FROM e1), NULL::INT >= ANY (SELECT k "
            + "FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ANY (SELECT k FROM e1), NULLIF(1,1) <> ANY (SELECT k FROM e1), "
            + "NULLIF(1,1) != ANY (SELECT k FROM e1), NULLIF(1,1) < ANY (SELECT k FROM e1), NULLIF(1,1)"
            + " <= ANY (SELECT k FROM e1), NULLIF(1,1) > ANY (SELECT k FROM e1), NULLIF(1,1) >= ANY "
            + "(SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ANY (SELECT k FROM e1), 1 + NULL <> ANY (SELECT k FROM e1), 1 + NULL "
            + "!= ANY (SELECT k FROM e1), 1 + NULL < ANY (SELECT k FROM e1), 1 + NULL <= ANY (SELECT k "
            + "FROM e1), 1 + NULL > ANY (SELECT k FROM e1), 1 + NULL >= ANY (SELECT k FROM e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 0 = ANY (SELECT k FROM e1), 0 <> ANY (SELECT k FROM e1), 0 != ANY (SELECT k FROM "
            + "e1), 0 < ANY (SELECT k FROM e1), 0 <= ANY (SELECT k FROM e1), 0 > ANY (SELECT k FROM "
            + "e1), 0 >= ANY (SELECT k FROM e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 1 = ANY (SELECT k FROM e1), 1 <> ANY (SELECT k FROM e1), 1 != ANY (SELECT k FROM "
            + "e1), 1 < ANY (SELECT k FROM e1), 1 <= ANY (SELECT k FROM e1), 1 > ANY (SELECT k FROM "
            + "e1), 1 >= ANY (SELECT k FROM e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 2 = ANY (SELECT k FROM e1), 2 <> ANY (SELECT k FROM e1), 2 != ANY (SELECT k FROM "
            + "e1), 2 < ANY (SELECT k FROM e1), 2 <= ANY (SELECT k FROM e1), 2 > ANY (SELECT k FROM "
            + "e1), 2 >= ANY (SELECT k FROM e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 3 = ANY (SELECT k FROM e1), 3 <> ANY (SELECT k FROM e1), 3 != ANY (SELECT k FROM "
            + "e1), 3 < ANY (SELECT k FROM e1), 3 <= ANY (SELECT k FROM e1), 3 > ANY (SELECT k FROM "
            + "e1), 3 >= ANY (SELECT k FROM e1)");
        assertAnswer("1|false|false|false|false|false|false|false ;; "
            + "2|false|false|false|false|false|false|false ;; "
            + "3|false|false|false|false|false|false|false ;; "
            + "4|false|false|false|false|false|false|false ;; "
            + "5|false|false|false|false|false|false|false",
            "SELECT id, (k + 0) = ANY (SELECT k FROM e1), (k + 0) <> ANY (SELECT k FROM e1), (k + 0) "
            + "!= ANY (SELECT k FROM e1), (k + 0) < ANY (SELECT k FROM e1), (k + 0) <= ANY (SELECT k "
            + "FROM e1), (k + 0) > ANY (SELECT k FROM e1), (k + 0) >= ANY (SELECT k FROM e1) FROM p "
            + "ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ANY (SELECT k FROM e1), CAST(NULL AS INT) <> ANY (SELECT "
            + "k FROM e1), CAST(NULL AS INT) != ANY (SELECT k FROM e1), CAST(NULL AS INT) < ANY (SELECT"
            + " k FROM e1), CAST(NULL AS INT) <= ANY (SELECT k FROM e1), CAST(NULL AS INT) > ANY "
            + "(SELECT k FROM e1), CAST(NULL AS INT) >= ANY (SELECT k FROM e1) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ANY (SELECT k FROM e1), IFF(id > 100, 1, NULL) <> "
            + "ANY (SELECT k FROM e1), IFF(id > 100, 1, NULL) != ANY (SELECT k FROM e1), IFF(id > 100, "
            + "1, NULL) < ANY (SELECT k FROM e1), IFF(id > 100, 1, NULL) <= ANY (SELECT k FROM e1), "
            + "IFF(id > 100, 1, NULL) > ANY (SELECT k FROM e1), IFF(id > 100, 1, NULL) >= ANY (SELECT k"
            + " FROM e1) FROM p ORDER BY id");
        assertAnswer("1|false|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|false|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, k = ANY (SELECT k FROM nn), k <> ANY (SELECT k FROM nn), k != ANY (SELECT k "
            + "FROM nn), k < ANY (SELECT k FROM nn), k <= ANY (SELECT k FROM nn), k > ANY (SELECT k "
            + "FROM nn), k >= ANY (SELECT k FROM nn) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ANY (SELECT k FROM nn), NULL <> ANY (SELECT k FROM nn), NULL != ANY "
            + "(SELECT k FROM nn), NULL < ANY (SELECT k FROM nn), NULL <= ANY (SELECT k FROM nn), NULL "
            + "> ANY (SELECT k FROM nn), NULL >= ANY (SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ANY (SELECT k FROM nn), CAST(NULL AS INT) <> ANY (SELECT k "
            + "FROM nn), CAST(NULL AS INT) != ANY (SELECT k FROM nn), CAST(NULL AS INT) < ANY (SELECT k"
            + " FROM nn), CAST(NULL AS INT) <= ANY (SELECT k FROM nn), CAST(NULL AS INT) > ANY (SELECT "
            + "k FROM nn), CAST(NULL AS INT) >= ANY (SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ANY (SELECT k FROM nn), NULL::INT <> ANY (SELECT k FROM nn), "
            + "NULL::INT != ANY (SELECT k FROM nn), NULL::INT < ANY (SELECT k FROM nn), NULL::INT <= "
            + "ANY (SELECT k FROM nn), NULL::INT > ANY (SELECT k FROM nn), NULL::INT >= ANY (SELECT k "
            + "FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ANY (SELECT k FROM nn), NULLIF(1,1) <> ANY (SELECT k FROM nn), "
            + "NULLIF(1,1) != ANY (SELECT k FROM nn), NULLIF(1,1) < ANY (SELECT k FROM nn), NULLIF(1,1)"
            + " <= ANY (SELECT k FROM nn), NULLIF(1,1) > ANY (SELECT k FROM nn), NULLIF(1,1) >= ANY "
            + "(SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ANY (SELECT k FROM nn), 1 + NULL <> ANY (SELECT k FROM nn), 1 + NULL "
            + "!= ANY (SELECT k FROM nn), 1 + NULL < ANY (SELECT k FROM nn), 1 + NULL <= ANY (SELECT k "
            + "FROM nn), 1 + NULL > ANY (SELECT k FROM nn), 1 + NULL >= ANY (SELECT k FROM nn)");
        assertAnswer("false|true|true|true|true|false|false",
            "SELECT 0 = ANY (SELECT k FROM nn), 0 <> ANY (SELECT k FROM nn), 0 != ANY (SELECT k FROM "
            + "nn), 0 < ANY (SELECT k FROM nn), 0 <= ANY (SELECT k FROM nn), 0 > ANY (SELECT k FROM "
            + "nn), 0 >= ANY (SELECT k FROM nn)");
        assertAnswer("true|true|true|true|true|false|true",
            "SELECT 1 = ANY (SELECT k FROM nn), 1 <> ANY (SELECT k FROM nn), 1 != ANY (SELECT k FROM "
            + "nn), 1 < ANY (SELECT k FROM nn), 1 <= ANY (SELECT k FROM nn), 1 > ANY (SELECT k FROM "
            + "nn), 1 >= ANY (SELECT k FROM nn)");
        assertAnswer("true|true|true|false|true|true|true",
            "SELECT 2 = ANY (SELECT k FROM nn), 2 <> ANY (SELECT k FROM nn), 2 != ANY (SELECT k FROM "
            + "nn), 2 < ANY (SELECT k FROM nn), 2 <= ANY (SELECT k FROM nn), 2 > ANY (SELECT k FROM "
            + "nn), 2 >= ANY (SELECT k FROM nn)");
        assertAnswer("false|true|true|false|false|true|true",
            "SELECT 3 = ANY (SELECT k FROM nn), 3 <> ANY (SELECT k FROM nn), 3 != ANY (SELECT k FROM "
            + "nn), 3 < ANY (SELECT k FROM nn), 3 <= ANY (SELECT k FROM nn), 3 > ANY (SELECT k FROM "
            + "nn), 3 >= ANY (SELECT k FROM nn)");
        assertAnswer("1|false|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|false|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, (k + 0) = ANY (SELECT k FROM nn), (k + 0) <> ANY (SELECT k FROM nn), (k + 0) "
            + "!= ANY (SELECT k FROM nn), (k + 0) < ANY (SELECT k FROM nn), (k + 0) <= ANY (SELECT k "
            + "FROM nn), (k + 0) > ANY (SELECT k FROM nn), (k + 0) >= ANY (SELECT k FROM nn) FROM p "
            + "ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ANY (SELECT k FROM nn), CAST(NULL AS INT) <> ANY (SELECT "
            + "k FROM nn), CAST(NULL AS INT) != ANY (SELECT k FROM nn), CAST(NULL AS INT) < ANY (SELECT"
            + " k FROM nn), CAST(NULL AS INT) <= ANY (SELECT k FROM nn), CAST(NULL AS INT) > ANY "
            + "(SELECT k FROM nn), CAST(NULL AS INT) >= ANY (SELECT k FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ANY (SELECT k FROM nn), IFF(id > 100, 1, NULL) <> "
            + "ANY (SELECT k FROM nn), IFF(id > 100, 1, NULL) != ANY (SELECT k FROM nn), IFF(id > 100, "
            + "1, NULL) < ANY (SELECT k FROM nn), IFF(id > 100, 1, NULL) <= ANY (SELECT k FROM nn), "
            + "IFF(id > 100, 1, NULL) > ANY (SELECT k FROM nn), IFF(id > 100, 1, NULL) >= ANY (SELECT k"
            + " FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|false|false|false|true|false|true ;; "
            + "3|null|true|true|false|false|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, k = ANY (SELECT k FROM wn), k <> ANY (SELECT k FROM wn), k != ANY (SELECT k "
            + "FROM wn), k < ANY (SELECT k FROM wn), k <= ANY (SELECT k FROM wn), k > ANY (SELECT k "
            + "FROM wn), k >= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ANY (SELECT k FROM wn), NULL <> ANY (SELECT k FROM wn), NULL != ANY "
            + "(SELECT k FROM wn), NULL < ANY (SELECT k FROM wn), NULL <= ANY (SELECT k FROM wn), NULL "
            + "> ANY (SELECT k FROM wn), NULL >= ANY (SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ANY (SELECT k FROM wn), CAST(NULL AS INT) <> ANY (SELECT k "
            + "FROM wn), CAST(NULL AS INT) != ANY (SELECT k FROM wn), CAST(NULL AS INT) < ANY (SELECT k"
            + " FROM wn), CAST(NULL AS INT) <= ANY (SELECT k FROM wn), CAST(NULL AS INT) > ANY (SELECT "
            + "k FROM wn), CAST(NULL AS INT) >= ANY (SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ANY (SELECT k FROM wn), NULL::INT <> ANY (SELECT k FROM wn), "
            + "NULL::INT != ANY (SELECT k FROM wn), NULL::INT < ANY (SELECT k FROM wn), NULL::INT <= "
            + "ANY (SELECT k FROM wn), NULL::INT > ANY (SELECT k FROM wn), NULL::INT >= ANY (SELECT k "
            + "FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ANY (SELECT k FROM wn), NULLIF(1,1) <> ANY (SELECT k FROM wn), "
            + "NULLIF(1,1) != ANY (SELECT k FROM wn), NULLIF(1,1) < ANY (SELECT k FROM wn), NULLIF(1,1)"
            + " <= ANY (SELECT k FROM wn), NULLIF(1,1) > ANY (SELECT k FROM wn), NULLIF(1,1) >= ANY "
            + "(SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ANY (SELECT k FROM wn), 1 + NULL <> ANY (SELECT k FROM wn), 1 + NULL "
            + "!= ANY (SELECT k FROM wn), 1 + NULL < ANY (SELECT k FROM wn), 1 + NULL <= ANY (SELECT k "
            + "FROM wn), 1 + NULL > ANY (SELECT k FROM wn), 1 + NULL >= ANY (SELECT k FROM wn)");
        assertAnswer("null|true|true|true|true|false|false",
            "SELECT 0 = ANY (SELECT k FROM wn), 0 <> ANY (SELECT k FROM wn), 0 != ANY (SELECT k FROM "
            + "wn), 0 < ANY (SELECT k FROM wn), 0 <= ANY (SELECT k FROM wn), 0 > ANY (SELECT k FROM "
            + "wn), 0 >= ANY (SELECT k FROM wn)");
        assertAnswer("true|false|false|false|true|false|true",
            "SELECT 1 = ANY (SELECT k FROM wn), 1 <> ANY (SELECT k FROM wn), 1 != ANY (SELECT k FROM "
            + "wn), 1 < ANY (SELECT k FROM wn), 1 <= ANY (SELECT k FROM wn), 1 > ANY (SELECT k FROM "
            + "wn), 1 >= ANY (SELECT k FROM wn)");
        assertAnswer("null|true|true|false|false|true|true",
            "SELECT 2 = ANY (SELECT k FROM wn), 2 <> ANY (SELECT k FROM wn), 2 != ANY (SELECT k FROM "
            + "wn), 2 < ANY (SELECT k FROM wn), 2 <= ANY (SELECT k FROM wn), 2 > ANY (SELECT k FROM "
            + "wn), 2 >= ANY (SELECT k FROM wn)");
        assertAnswer("null|true|true|false|false|true|true",
            "SELECT 3 = ANY (SELECT k FROM wn), 3 <> ANY (SELECT k FROM wn), 3 != ANY (SELECT k FROM "
            + "wn), 3 < ANY (SELECT k FROM wn), 3 <= ANY (SELECT k FROM wn), 3 > ANY (SELECT k FROM "
            + "wn), 3 >= ANY (SELECT k FROM wn)");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|false|false|false|true|false|true ;; "
            + "3|null|true|true|false|false|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, (k + 0) = ANY (SELECT k FROM wn), (k + 0) <> ANY (SELECT k FROM wn), (k + 0) "
            + "!= ANY (SELECT k FROM wn), (k + 0) < ANY (SELECT k FROM wn), (k + 0) <= ANY (SELECT k "
            + "FROM wn), (k + 0) > ANY (SELECT k FROM wn), (k + 0) >= ANY (SELECT k FROM wn) FROM p "
            + "ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ANY (SELECT k FROM wn), CAST(NULL AS INT) <> ANY (SELECT "
            + "k FROM wn), CAST(NULL AS INT) != ANY (SELECT k FROM wn), CAST(NULL AS INT) < ANY (SELECT"
            + " k FROM wn), CAST(NULL AS INT) <= ANY (SELECT k FROM wn), CAST(NULL AS INT) > ANY "
            + "(SELECT k FROM wn), CAST(NULL AS INT) >= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ANY (SELECT k FROM wn), IFF(id > 100, 1, NULL) <> "
            + "ANY (SELECT k FROM wn), IFF(id > 100, 1, NULL) != ANY (SELECT k FROM wn), IFF(id > 100, "
            + "1, NULL) < ANY (SELECT k FROM wn), IFF(id > 100, 1, NULL) <= ANY (SELECT k FROM wn), "
            + "IFF(id > 100, 1, NULL) > ANY (SELECT k FROM wn), IFF(id > 100, 1, NULL) >= ANY (SELECT k"
            + " FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null|false|false|false|false|false|false ;; 2|null|false|false|false|false|false|false"
            + " ;; 3|null|false|false|false|false|false|false ;; "
            + "4|null|false|false|false|false|false|false ;; 5|null|false|false|false|false|false|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE an.k IS NULL OR p.id > 0), k <> ANY (SELECT k"
            + " FROM an WHERE an.k IS NULL OR p.id > 0), k != ANY (SELECT k FROM an WHERE an.k IS NULL "
            + "OR p.id > 0), k < ANY (SELECT k FROM an WHERE an.k IS NULL OR p.id > 0), k <= ANY "
            + "(SELECT k FROM an WHERE an.k IS NULL OR p.id > 0), k > ANY (SELECT k FROM an WHERE an.k "
            + "IS NULL OR p.id > 0), k >= ANY (SELECT k FROM an WHERE an.k IS NULL OR p.id > 0) FROM p "
            + "ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ANY (SELECT k FROM an), NULL <> ANY (SELECT k FROM an), NULL != ANY "
            + "(SELECT k FROM an), NULL < ANY (SELECT k FROM an), NULL <= ANY (SELECT k FROM an), NULL "
            + "> ANY (SELECT k FROM an), NULL >= ANY (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ANY (SELECT k FROM an), CAST(NULL AS INT) <> ANY (SELECT k "
            + "FROM an), CAST(NULL AS INT) != ANY (SELECT k FROM an), CAST(NULL AS INT) < ANY (SELECT k"
            + " FROM an), CAST(NULL AS INT) <= ANY (SELECT k FROM an), CAST(NULL AS INT) > ANY (SELECT "
            + "k FROM an), CAST(NULL AS INT) >= ANY (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ANY (SELECT k FROM an), NULL::INT <> ANY (SELECT k FROM an), "
            + "NULL::INT != ANY (SELECT k FROM an), NULL::INT < ANY (SELECT k FROM an), NULL::INT <= "
            + "ANY (SELECT k FROM an), NULL::INT > ANY (SELECT k FROM an), NULL::INT >= ANY (SELECT k "
            + "FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ANY (SELECT k FROM an), NULLIF(1,1) <> ANY (SELECT k FROM an), "
            + "NULLIF(1,1) != ANY (SELECT k FROM an), NULLIF(1,1) < ANY (SELECT k FROM an), NULLIF(1,1)"
            + " <= ANY (SELECT k FROM an), NULLIF(1,1) > ANY (SELECT k FROM an), NULLIF(1,1) >= ANY "
            + "(SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ANY (SELECT k FROM an), 1 + NULL <> ANY (SELECT k FROM an), 1 + NULL "
            + "!= ANY (SELECT k FROM an), 1 + NULL < ANY (SELECT k FROM an), 1 + NULL <= ANY (SELECT k "
            + "FROM an), 1 + NULL > ANY (SELECT k FROM an), 1 + NULL >= ANY (SELECT k FROM an)");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ANY (SELECT k FROM an), CAST(NULL AS INT) <> ANY (SELECT "
            + "k FROM an), CAST(NULL AS INT) != ANY (SELECT k FROM an), CAST(NULL AS INT) < ANY (SELECT"
            + " k FROM an), CAST(NULL AS INT) <= ANY (SELECT k FROM an), CAST(NULL AS INT) > ANY "
            + "(SELECT k FROM an), CAST(NULL AS INT) >= ANY (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ANY (SELECT k FROM an), IFF(id > 100, 1, NULL) <> "
            + "ANY (SELECT k FROM an), IFF(id > 100, 1, NULL) != ANY (SELECT k FROM an), IFF(id > 100, "
            + "1, NULL) < ANY (SELECT k FROM an), IFF(id > 100, 1, NULL) <= ANY (SELECT k FROM an), "
            + "IFF(id > 100, 1, NULL) > ANY (SELECT k FROM an), IFF(id > 100, 1, NULL) >= ANY (SELECT k"
            + " FROM an) FROM p ORDER BY id");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, k = ANY (SELECT k FROM w3), k <> ANY (SELECT k FROM w3), k != ANY (SELECT k "
            + "FROM w3), k < ANY (SELECT k FROM w3), k <= ANY (SELECT k FROM w3), k > ANY (SELECT k "
            + "FROM w3), k >= ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ANY (SELECT k FROM w3), NULL <> ANY (SELECT k FROM w3), NULL != ANY "
            + "(SELECT k FROM w3), NULL < ANY (SELECT k FROM w3), NULL <= ANY (SELECT k FROM w3), NULL "
            + "> ANY (SELECT k FROM w3), NULL >= ANY (SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ANY (SELECT k FROM w3), CAST(NULL AS INT) <> ANY (SELECT k "
            + "FROM w3), CAST(NULL AS INT) != ANY (SELECT k FROM w3), CAST(NULL AS INT) < ANY (SELECT k"
            + " FROM w3), CAST(NULL AS INT) <= ANY (SELECT k FROM w3), CAST(NULL AS INT) > ANY (SELECT "
            + "k FROM w3), CAST(NULL AS INT) >= ANY (SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ANY (SELECT k FROM w3), NULL::INT <> ANY (SELECT k FROM w3), "
            + "NULL::INT != ANY (SELECT k FROM w3), NULL::INT < ANY (SELECT k FROM w3), NULL::INT <= "
            + "ANY (SELECT k FROM w3), NULL::INT > ANY (SELECT k FROM w3), NULL::INT >= ANY (SELECT k "
            + "FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ANY (SELECT k FROM w3), NULLIF(1,1) <> ANY (SELECT k FROM w3), "
            + "NULLIF(1,1) != ANY (SELECT k FROM w3), NULLIF(1,1) < ANY (SELECT k FROM w3), NULLIF(1,1)"
            + " <= ANY (SELECT k FROM w3), NULLIF(1,1) > ANY (SELECT k FROM w3), NULLIF(1,1) >= ANY "
            + "(SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ANY (SELECT k FROM w3), 1 + NULL <> ANY (SELECT k FROM w3), 1 + NULL "
            + "!= ANY (SELECT k FROM w3), 1 + NULL < ANY (SELECT k FROM w3), 1 + NULL <= ANY (SELECT k "
            + "FROM w3), 1 + NULL > ANY (SELECT k FROM w3), 1 + NULL >= ANY (SELECT k FROM w3)");
        assertAnswer("null|true|true|true|true|false|false",
            "SELECT 0 = ANY (SELECT k FROM w3), 0 <> ANY (SELECT k FROM w3), 0 != ANY (SELECT k FROM "
            + "w3), 0 < ANY (SELECT k FROM w3), 0 <= ANY (SELECT k FROM w3), 0 > ANY (SELECT k FROM "
            + "w3), 0 >= ANY (SELECT k FROM w3)");
        assertAnswer("true|true|true|true|true|false|true",
            "SELECT 1 = ANY (SELECT k FROM w3), 1 <> ANY (SELECT k FROM w3), 1 != ANY (SELECT k FROM "
            + "w3), 1 < ANY (SELECT k FROM w3), 1 <= ANY (SELECT k FROM w3), 1 > ANY (SELECT k FROM "
            + "w3), 1 >= ANY (SELECT k FROM w3)");
        assertAnswer("true|true|true|false|true|true|true",
            "SELECT 2 = ANY (SELECT k FROM w3), 2 <> ANY (SELECT k FROM w3), 2 != ANY (SELECT k FROM "
            + "w3), 2 < ANY (SELECT k FROM w3), 2 <= ANY (SELECT k FROM w3), 2 > ANY (SELECT k FROM "
            + "w3), 2 >= ANY (SELECT k FROM w3)");
        assertAnswer("null|true|true|false|false|true|true",
            "SELECT 3 = ANY (SELECT k FROM w3), 3 <> ANY (SELECT k FROM w3), 3 != ANY (SELECT k FROM "
            + "w3), 3 < ANY (SELECT k FROM w3), 3 <= ANY (SELECT k FROM w3), 3 > ANY (SELECT k FROM "
            + "w3), 3 >= ANY (SELECT k FROM w3)");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, (k + 0) = ANY (SELECT k FROM w3), (k + 0) <> ANY (SELECT k FROM w3), (k + 0) "
            + "!= ANY (SELECT k FROM w3), (k + 0) < ANY (SELECT k FROM w3), (k + 0) <= ANY (SELECT k "
            + "FROM w3), (k + 0) > ANY (SELECT k FROM w3), (k + 0) >= ANY (SELECT k FROM w3) FROM p "
            + "ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ANY (SELECT k FROM w3), CAST(NULL AS INT) <> ANY (SELECT "
            + "k FROM w3), CAST(NULL AS INT) != ANY (SELECT k FROM w3), CAST(NULL AS INT) < ANY (SELECT"
            + " k FROM w3), CAST(NULL AS INT) <= ANY (SELECT k FROM w3), CAST(NULL AS INT) > ANY "
            + "(SELECT k FROM w3), CAST(NULL AS INT) >= ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ANY (SELECT k FROM w3), IFF(id > 100, 1, NULL) <> "
            + "ANY (SELECT k FROM w3), IFF(id > 100, 1, NULL) != ANY (SELECT k FROM w3), IFF(id > 100, "
            + "1, NULL) < ANY (SELECT k FROM w3), IFF(id > 100, 1, NULL) <= ANY (SELECT k FROM w3), "
            + "IFF(id > 100, 1, NULL) > ANY (SELECT k FROM w3), IFF(id > 100, 1, NULL) >= ANY (SELECT k"
            + " FROM w3) FROM p ORDER BY id");
        assertAnswer("1|true|true|true|true|true|true|true ;; 2|true|true|true|true|true|true|true ;; "
            + "3|true|true|true|true|true|true|true ;; 4|true|true|true|true|true|true|true ;; "
            + "5|true|true|true|true|true|true|true",
            "SELECT id, k = ALL (SELECT k FROM e1), k <> ALL (SELECT k FROM e1), k != ALL (SELECT k "
            + "FROM e1), k < ALL (SELECT k FROM e1), k <= ALL (SELECT k FROM e1), k > ALL (SELECT k "
            + "FROM e1), k >= ALL (SELECT k FROM e1) FROM p ORDER BY id");
        assertAnswer("1|true|true|true|true|true|true|true ;; 2|true|true|true|true|true|true|true ;; "
            + "3|true|true|true|true|true|true|true ;; 4|true|true|true|true|true|true|true ;; "
            + "5|true|true|true|true|true|true|true",
            "SELECT id, k = ALL (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k <> ALL (SELECT k"
            + " FROM e1 WHERE e1.k IS NULL OR p.id > 0), k != ALL (SELECT k FROM e1 WHERE e1.k IS NULL "
            + "OR p.id > 0), k < ALL (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k <= ALL "
            + "(SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k > ALL (SELECT k FROM e1 WHERE e1.k "
            + "IS NULL OR p.id > 0), k >= ALL (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0) FROM p "
            + "ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ALL (SELECT k FROM e1), NULL <> ALL (SELECT k FROM e1), NULL != ALL "
            + "(SELECT k FROM e1), NULL < ALL (SELECT k FROM e1), NULL <= ALL (SELECT k FROM e1), NULL "
            + "> ALL (SELECT k FROM e1), NULL >= ALL (SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ALL (SELECT k FROM e1), CAST(NULL AS INT) <> ALL (SELECT k "
            + "FROM e1), CAST(NULL AS INT) != ALL (SELECT k FROM e1), CAST(NULL AS INT) < ALL (SELECT k"
            + " FROM e1), CAST(NULL AS INT) <= ALL (SELECT k FROM e1), CAST(NULL AS INT) > ALL (SELECT "
            + "k FROM e1), CAST(NULL AS INT) >= ALL (SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ALL (SELECT k FROM e1), NULL::INT <> ALL (SELECT k FROM e1), "
            + "NULL::INT != ALL (SELECT k FROM e1), NULL::INT < ALL (SELECT k FROM e1), NULL::INT <= "
            + "ALL (SELECT k FROM e1), NULL::INT > ALL (SELECT k FROM e1), NULL::INT >= ALL (SELECT k "
            + "FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ALL (SELECT k FROM e1), NULLIF(1,1) <> ALL (SELECT k FROM e1), "
            + "NULLIF(1,1) != ALL (SELECT k FROM e1), NULLIF(1,1) < ALL (SELECT k FROM e1), NULLIF(1,1)"
            + " <= ALL (SELECT k FROM e1), NULLIF(1,1) > ALL (SELECT k FROM e1), NULLIF(1,1) >= ALL "
            + "(SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ALL (SELECT k FROM e1), 1 + NULL <> ALL (SELECT k FROM e1), 1 + NULL "
            + "!= ALL (SELECT k FROM e1), 1 + NULL < ALL (SELECT k FROM e1), 1 + NULL <= ALL (SELECT k "
            + "FROM e1), 1 + NULL > ALL (SELECT k FROM e1), 1 + NULL >= ALL (SELECT k FROM e1)");
        assertAnswer("true|true|true|true|true|true|true",
            "SELECT 0 = ALL (SELECT k FROM e1), 0 <> ALL (SELECT k FROM e1), 0 != ALL (SELECT k FROM "
            + "e1), 0 < ALL (SELECT k FROM e1), 0 <= ALL (SELECT k FROM e1), 0 > ALL (SELECT k FROM "
            + "e1), 0 >= ALL (SELECT k FROM e1)");
        assertAnswer("true|true|true|true|true|true|true",
            "SELECT 1 = ALL (SELECT k FROM e1), 1 <> ALL (SELECT k FROM e1), 1 != ALL (SELECT k FROM "
            + "e1), 1 < ALL (SELECT k FROM e1), 1 <= ALL (SELECT k FROM e1), 1 > ALL (SELECT k FROM "
            + "e1), 1 >= ALL (SELECT k FROM e1)");
        assertAnswer("true|true|true|true|true|true|true",
            "SELECT 2 = ALL (SELECT k FROM e1), 2 <> ALL (SELECT k FROM e1), 2 != ALL (SELECT k FROM "
            + "e1), 2 < ALL (SELECT k FROM e1), 2 <= ALL (SELECT k FROM e1), 2 > ALL (SELECT k FROM "
            + "e1), 2 >= ALL (SELECT k FROM e1)");
        assertAnswer("true|true|true|true|true|true|true",
            "SELECT 3 = ALL (SELECT k FROM e1), 3 <> ALL (SELECT k FROM e1), 3 != ALL (SELECT k FROM "
            + "e1), 3 < ALL (SELECT k FROM e1), 3 <= ALL (SELECT k FROM e1), 3 > ALL (SELECT k FROM "
            + "e1), 3 >= ALL (SELECT k FROM e1)");
        assertAnswer("1|true|true|true|true|true|true|true ;; 2|true|true|true|true|true|true|true ;; "
            + "3|true|true|true|true|true|true|true ;; 4|true|true|true|true|true|true|true ;; "
            + "5|true|true|true|true|true|true|true",
            "SELECT id, (k + 0) = ALL (SELECT k FROM e1), (k + 0) <> ALL (SELECT k FROM e1), (k + 0) "
            + "!= ALL (SELECT k FROM e1), (k + 0) < ALL (SELECT k FROM e1), (k + 0) <= ALL (SELECT k "
            + "FROM e1), (k + 0) > ALL (SELECT k FROM e1), (k + 0) >= ALL (SELECT k FROM e1) FROM p "
            + "ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ALL (SELECT k FROM e1), CAST(NULL AS INT) <> ALL (SELECT "
            + "k FROM e1), CAST(NULL AS INT) != ALL (SELECT k FROM e1), CAST(NULL AS INT) < ALL (SELECT"
            + " k FROM e1), CAST(NULL AS INT) <= ALL (SELECT k FROM e1), CAST(NULL AS INT) > ALL "
            + "(SELECT k FROM e1), CAST(NULL AS INT) >= ALL (SELECT k FROM e1) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ALL (SELECT k FROM e1), IFF(id > 100, 1, NULL) <> "
            + "ALL (SELECT k FROM e1), IFF(id > 100, 1, NULL) != ALL (SELECT k FROM e1), IFF(id > 100, "
            + "1, NULL) < ALL (SELECT k FROM e1), IFF(id > 100, 1, NULL) <= ALL (SELECT k FROM e1), "
            + "IFF(id > 100, 1, NULL) > ALL (SELECT k FROM e1), IFF(id > 100, 1, NULL) >= ALL (SELECT k"
            + " FROM e1) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ALL (SELECT k FROM nn), NULL <> ALL (SELECT k FROM nn), NULL != ALL "
            + "(SELECT k FROM nn), NULL < ALL (SELECT k FROM nn), NULL <= ALL (SELECT k FROM nn), NULL "
            + "> ALL (SELECT k FROM nn), NULL >= ALL (SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ALL (SELECT k FROM nn), CAST(NULL AS INT) <> ALL (SELECT k "
            + "FROM nn), CAST(NULL AS INT) != ALL (SELECT k FROM nn), CAST(NULL AS INT) < ALL (SELECT k"
            + " FROM nn), CAST(NULL AS INT) <= ALL (SELECT k FROM nn), CAST(NULL AS INT) > ALL (SELECT "
            + "k FROM nn), CAST(NULL AS INT) >= ALL (SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ALL (SELECT k FROM nn), NULL::INT <> ALL (SELECT k FROM nn), "
            + "NULL::INT != ALL (SELECT k FROM nn), NULL::INT < ALL (SELECT k FROM nn), NULL::INT <= "
            + "ALL (SELECT k FROM nn), NULL::INT > ALL (SELECT k FROM nn), NULL::INT >= ALL (SELECT k "
            + "FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ALL (SELECT k FROM nn), NULLIF(1,1) <> ALL (SELECT k FROM nn), "
            + "NULLIF(1,1) != ALL (SELECT k FROM nn), NULLIF(1,1) < ALL (SELECT k FROM nn), NULLIF(1,1)"
            + " <= ALL (SELECT k FROM nn), NULLIF(1,1) > ALL (SELECT k FROM nn), NULLIF(1,1) >= ALL "
            + "(SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ALL (SELECT k FROM nn), 1 + NULL <> ALL (SELECT k FROM nn), 1 + NULL "
            + "!= ALL (SELECT k FROM nn), 1 + NULL < ALL (SELECT k FROM nn), 1 + NULL <= ALL (SELECT k "
            + "FROM nn), 1 + NULL > ALL (SELECT k FROM nn), 1 + NULL >= ALL (SELECT k FROM nn)");
        assertAnswer("false|true|true|true|true|false|false",
            "SELECT 0 = ALL (SELECT k FROM nn), 0 <> ALL (SELECT k FROM nn), 0 != ALL (SELECT k FROM "
            + "nn), 0 < ALL (SELECT k FROM nn), 0 <= ALL (SELECT k FROM nn), 0 > ALL (SELECT k FROM "
            + "nn), 0 >= ALL (SELECT k FROM nn)");
        assertAnswer("false|false|false|false|true|false|false",
            "SELECT 1 = ALL (SELECT k FROM nn), 1 <> ALL (SELECT k FROM nn), 1 != ALL (SELECT k FROM "
            + "nn), 1 < ALL (SELECT k FROM nn), 1 <= ALL (SELECT k FROM nn), 1 > ALL (SELECT k FROM "
            + "nn), 1 >= ALL (SELECT k FROM nn)");
        assertAnswer("false|false|false|false|false|false|true",
            "SELECT 2 = ALL (SELECT k FROM nn), 2 <> ALL (SELECT k FROM nn), 2 != ALL (SELECT k FROM "
            + "nn), 2 < ALL (SELECT k FROM nn), 2 <= ALL (SELECT k FROM nn), 2 > ALL (SELECT k FROM "
            + "nn), 2 >= ALL (SELECT k FROM nn)");
        assertAnswer("false|true|true|false|false|true|true",
            "SELECT 3 = ALL (SELECT k FROM nn), 3 <> ALL (SELECT k FROM nn), 3 != ALL (SELECT k FROM "
            + "nn), 3 < ALL (SELECT k FROM nn), 3 <= ALL (SELECT k FROM nn), 3 > ALL (SELECT k FROM "
            + "nn), 3 >= ALL (SELECT k FROM nn)");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ALL (SELECT k FROM nn), CAST(NULL AS INT) <> ALL (SELECT "
            + "k FROM nn), CAST(NULL AS INT) != ALL (SELECT k FROM nn), CAST(NULL AS INT) < ALL (SELECT"
            + " k FROM nn), CAST(NULL AS INT) <= ALL (SELECT k FROM nn), CAST(NULL AS INT) > ALL "
            + "(SELECT k FROM nn), CAST(NULL AS INT) >= ALL (SELECT k FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ALL (SELECT k FROM nn), IFF(id > 100, 1, NULL) <> "
            + "ALL (SELECT k FROM nn), IFF(id > 100, 1, NULL) != ALL (SELECT k FROM nn), IFF(id > 100, "
            + "1, NULL) < ALL (SELECT k FROM nn), IFF(id > 100, 1, NULL) <= ALL (SELECT k FROM nn), "
            + "IFF(id > 100, 1, NULL) > ALL (SELECT k FROM nn), IFF(id > 100, 1, NULL) >= ALL (SELECT k"
            + " FROM nn) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ALL (SELECT k FROM wn), NULL <> ALL (SELECT k FROM wn), NULL != ALL "
            + "(SELECT k FROM wn), NULL < ALL (SELECT k FROM wn), NULL <= ALL (SELECT k FROM wn), NULL "
            + "> ALL (SELECT k FROM wn), NULL >= ALL (SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ALL (SELECT k FROM wn), CAST(NULL AS INT) <> ALL (SELECT k "
            + "FROM wn), CAST(NULL AS INT) != ALL (SELECT k FROM wn), CAST(NULL AS INT) < ALL (SELECT k"
            + " FROM wn), CAST(NULL AS INT) <= ALL (SELECT k FROM wn), CAST(NULL AS INT) > ALL (SELECT "
            + "k FROM wn), CAST(NULL AS INT) >= ALL (SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ALL (SELECT k FROM wn), NULL::INT <> ALL (SELECT k FROM wn), "
            + "NULL::INT != ALL (SELECT k FROM wn), NULL::INT < ALL (SELECT k FROM wn), NULL::INT <= "
            + "ALL (SELECT k FROM wn), NULL::INT > ALL (SELECT k FROM wn), NULL::INT >= ALL (SELECT k "
            + "FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ALL (SELECT k FROM wn), NULLIF(1,1) <> ALL (SELECT k FROM wn), "
            + "NULLIF(1,1) != ALL (SELECT k FROM wn), NULLIF(1,1) < ALL (SELECT k FROM wn), NULLIF(1,1)"
            + " <= ALL (SELECT k FROM wn), NULLIF(1,1) > ALL (SELECT k FROM wn), NULLIF(1,1) >= ALL "
            + "(SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ALL (SELECT k FROM wn), 1 + NULL <> ALL (SELECT k FROM wn), 1 + NULL "
            + "!= ALL (SELECT k FROM wn), 1 + NULL < ALL (SELECT k FROM wn), 1 + NULL <= ALL (SELECT k "
            + "FROM wn), 1 + NULL > ALL (SELECT k FROM wn), 1 + NULL >= ALL (SELECT k FROM wn)");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ALL (SELECT k FROM wn), CAST(NULL AS INT) <> ALL (SELECT "
            + "k FROM wn), CAST(NULL AS INT) != ALL (SELECT k FROM wn), CAST(NULL AS INT) < ALL (SELECT"
            + " k FROM wn), CAST(NULL AS INT) <= ALL (SELECT k FROM wn), CAST(NULL AS INT) > ALL "
            + "(SELECT k FROM wn), CAST(NULL AS INT) >= ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, k = ALL (SELECT k FROM an), k <> ALL (SELECT k FROM an), k != ALL (SELECT k "
            + "FROM an), k < ALL (SELECT k FROM an), k <= ALL (SELECT k FROM an), k > ALL (SELECT k "
            + "FROM an), k >= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ALL (SELECT k FROM an), NULL <> ALL (SELECT k FROM an), NULL != ALL "
            + "(SELECT k FROM an), NULL < ALL (SELECT k FROM an), NULL <= ALL (SELECT k FROM an), NULL "
            + "> ALL (SELECT k FROM an), NULL >= ALL (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ALL (SELECT k FROM an), CAST(NULL AS INT) <> ALL (SELECT k "
            + "FROM an), CAST(NULL AS INT) != ALL (SELECT k FROM an), CAST(NULL AS INT) < ALL (SELECT k"
            + " FROM an), CAST(NULL AS INT) <= ALL (SELECT k FROM an), CAST(NULL AS INT) > ALL (SELECT "
            + "k FROM an), CAST(NULL AS INT) >= ALL (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ALL (SELECT k FROM an), NULL::INT <> ALL (SELECT k FROM an), "
            + "NULL::INT != ALL (SELECT k FROM an), NULL::INT < ALL (SELECT k FROM an), NULL::INT <= "
            + "ALL (SELECT k FROM an), NULL::INT > ALL (SELECT k FROM an), NULL::INT >= ALL (SELECT k "
            + "FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ALL (SELECT k FROM an), NULLIF(1,1) <> ALL (SELECT k FROM an), "
            + "NULLIF(1,1) != ALL (SELECT k FROM an), NULLIF(1,1) < ALL (SELECT k FROM an), NULLIF(1,1)"
            + " <= ALL (SELECT k FROM an), NULLIF(1,1) > ALL (SELECT k FROM an), NULLIF(1,1) >= ALL "
            + "(SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ALL (SELECT k FROM an), 1 + NULL <> ALL (SELECT k FROM an), 1 + NULL "
            + "!= ALL (SELECT k FROM an), 1 + NULL < ALL (SELECT k FROM an), 1 + NULL <= ALL (SELECT k "
            + "FROM an), 1 + NULL > ALL (SELECT k FROM an), 1 + NULL >= ALL (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 0 = ALL (SELECT k FROM an), 0 <> ALL (SELECT k FROM an), 0 != ALL (SELECT k FROM "
            + "an), 0 < ALL (SELECT k FROM an), 0 <= ALL (SELECT k FROM an), 0 > ALL (SELECT k FROM "
            + "an), 0 >= ALL (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 = ALL (SELECT k FROM an), 1 <> ALL (SELECT k FROM an), 1 != ALL (SELECT k FROM "
            + "an), 1 < ALL (SELECT k FROM an), 1 <= ALL (SELECT k FROM an), 1 > ALL (SELECT k FROM "
            + "an), 1 >= ALL (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 2 = ALL (SELECT k FROM an), 2 <> ALL (SELECT k FROM an), 2 != ALL (SELECT k FROM "
            + "an), 2 < ALL (SELECT k FROM an), 2 <= ALL (SELECT k FROM an), 2 > ALL (SELECT k FROM "
            + "an), 2 >= ALL (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 3 = ALL (SELECT k FROM an), 3 <> ALL (SELECT k FROM an), 3 != ALL (SELECT k FROM "
            + "an), 3 < ALL (SELECT k FROM an), 3 <= ALL (SELECT k FROM an), 3 > ALL (SELECT k FROM "
            + "an), 3 >= ALL (SELECT k FROM an)");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, (k + 0) = ALL (SELECT k FROM an), (k + 0) <> ALL (SELECT k FROM an), (k + 0) "
            + "!= ALL (SELECT k FROM an), (k + 0) < ALL (SELECT k FROM an), (k + 0) <= ALL (SELECT k "
            + "FROM an), (k + 0) > ALL (SELECT k FROM an), (k + 0) >= ALL (SELECT k FROM an) FROM p "
            + "ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ALL (SELECT k FROM an), CAST(NULL AS INT) <> ALL (SELECT "
            + "k FROM an), CAST(NULL AS INT) != ALL (SELECT k FROM an), CAST(NULL AS INT) < ALL (SELECT"
            + " k FROM an), CAST(NULL AS INT) <= ALL (SELECT k FROM an), CAST(NULL AS INT) > ALL "
            + "(SELECT k FROM an), CAST(NULL AS INT) >= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = ALL (SELECT k FROM an), IFF(id > 100, 1, NULL) <> "
            + "ALL (SELECT k FROM an), IFF(id > 100, 1, NULL) != ALL (SELECT k FROM an), IFF(id > 100, "
            + "1, NULL) < ALL (SELECT k FROM an), IFF(id > 100, 1, NULL) <= ALL (SELECT k FROM an), "
            + "IFF(id > 100, 1, NULL) > ALL (SELECT k FROM an), IFF(id > 100, 1, NULL) >= ALL (SELECT k"
            + " FROM an) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = ALL (SELECT k FROM w3), NULL <> ALL (SELECT k FROM w3), NULL != ALL "
            + "(SELECT k FROM w3), NULL < ALL (SELECT k FROM w3), NULL <= ALL (SELECT k FROM w3), NULL "
            + "> ALL (SELECT k FROM w3), NULL >= ALL (SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = ALL (SELECT k FROM w3), CAST(NULL AS INT) <> ALL (SELECT k "
            + "FROM w3), CAST(NULL AS INT) != ALL (SELECT k FROM w3), CAST(NULL AS INT) < ALL (SELECT k"
            + " FROM w3), CAST(NULL AS INT) <= ALL (SELECT k FROM w3), CAST(NULL AS INT) > ALL (SELECT "
            + "k FROM w3), CAST(NULL AS INT) >= ALL (SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = ALL (SELECT k FROM w3), NULL::INT <> ALL (SELECT k FROM w3), "
            + "NULL::INT != ALL (SELECT k FROM w3), NULL::INT < ALL (SELECT k FROM w3), NULL::INT <= "
            + "ALL (SELECT k FROM w3), NULL::INT > ALL (SELECT k FROM w3), NULL::INT >= ALL (SELECT k "
            + "FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = ALL (SELECT k FROM w3), NULLIF(1,1) <> ALL (SELECT k FROM w3), "
            + "NULLIF(1,1) != ALL (SELECT k FROM w3), NULLIF(1,1) < ALL (SELECT k FROM w3), NULLIF(1,1)"
            + " <= ALL (SELECT k FROM w3), NULLIF(1,1) > ALL (SELECT k FROM w3), NULLIF(1,1) >= ALL "
            + "(SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = ALL (SELECT k FROM w3), 1 + NULL <> ALL (SELECT k FROM w3), 1 + NULL "
            + "!= ALL (SELECT k FROM w3), 1 + NULL < ALL (SELECT k FROM w3), 1 + NULL <= ALL (SELECT k "
            + "FROM w3), 1 + NULL > ALL (SELECT k FROM w3), 1 + NULL >= ALL (SELECT k FROM w3)");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = ALL (SELECT k FROM w3), CAST(NULL AS INT) <> ALL (SELECT "
            + "k FROM w3), CAST(NULL AS INT) != ALL (SELECT k FROM w3), CAST(NULL AS INT) < ALL (SELECT"
            + " k FROM w3), CAST(NULL AS INT) <= ALL (SELECT k FROM w3), CAST(NULL AS INT) > ALL "
            + "(SELECT k FROM w3), CAST(NULL AS INT) >= ALL (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|false|false|false|false|false|false|false ;; "
            + "2|false|false|false|false|false|false|false ;; "
            + "3|false|false|false|false|false|false|false ;; "
            + "4|false|false|false|false|false|false|false ;; "
            + "5|false|false|false|false|false|false|false",
            "SELECT id, k = SOME (SELECT k FROM e1), k <> SOME (SELECT k FROM e1), k != SOME (SELECT "
            + "k FROM e1), k < SOME (SELECT k FROM e1), k <= SOME (SELECT k FROM e1), k > SOME (SELECT "
            + "k FROM e1), k >= SOME (SELECT k FROM e1) FROM p ORDER BY id");
        assertAnswer("1|false|false|false|false|false|false|false ;; "
            + "2|false|false|false|false|false|false|false ;; "
            + "3|false|false|false|false|false|false|false ;; "
            + "4|false|false|false|false|false|false|false ;; "
            + "5|false|false|false|false|false|false|false",
            "SELECT id, k = SOME (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k <> SOME (SELECT"
            + " k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k != SOME (SELECT k FROM e1 WHERE e1.k IS "
            + "NULL OR p.id > 0), k < SOME (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k <= SOME"
            + " (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0), k > SOME (SELECT k FROM e1 WHERE "
            + "e1.k IS NULL OR p.id > 0), k >= SOME (SELECT k FROM e1 WHERE e1.k IS NULL OR p.id > 0) "
            + "FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = SOME (SELECT k FROM e1), NULL <> SOME (SELECT k FROM e1), NULL != SOME "
            + "(SELECT k FROM e1), NULL < SOME (SELECT k FROM e1), NULL <= SOME (SELECT k FROM e1), "
            + "NULL > SOME (SELECT k FROM e1), NULL >= SOME (SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = SOME (SELECT k FROM e1), CAST(NULL AS INT) <> SOME (SELECT k "
            + "FROM e1), CAST(NULL AS INT) != SOME (SELECT k FROM e1), CAST(NULL AS INT) < SOME (SELECT"
            + " k FROM e1), CAST(NULL AS INT) <= SOME (SELECT k FROM e1), CAST(NULL AS INT) > SOME "
            + "(SELECT k FROM e1), CAST(NULL AS INT) >= SOME (SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = SOME (SELECT k FROM e1), NULL::INT <> SOME (SELECT k FROM e1), "
            + "NULL::INT != SOME (SELECT k FROM e1), NULL::INT < SOME (SELECT k FROM e1), NULL::INT <= "
            + "SOME (SELECT k FROM e1), NULL::INT > SOME (SELECT k FROM e1), NULL::INT >= SOME (SELECT "
            + "k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = SOME (SELECT k FROM e1), NULLIF(1,1) <> SOME (SELECT k FROM e1), "
            + "NULLIF(1,1) != SOME (SELECT k FROM e1), NULLIF(1,1) < SOME (SELECT k FROM e1), "
            + "NULLIF(1,1) <= SOME (SELECT k FROM e1), NULLIF(1,1) > SOME (SELECT k FROM e1), "
            + "NULLIF(1,1) >= SOME (SELECT k FROM e1)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = SOME (SELECT k FROM e1), 1 + NULL <> SOME (SELECT k FROM e1), 1 + NULL"
            + " != SOME (SELECT k FROM e1), 1 + NULL < SOME (SELECT k FROM e1), 1 + NULL <= SOME "
            + "(SELECT k FROM e1), 1 + NULL > SOME (SELECT k FROM e1), 1 + NULL >= SOME (SELECT k FROM "
            + "e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 0 = SOME (SELECT k FROM e1), 0 <> SOME (SELECT k FROM e1), 0 != SOME (SELECT k "
            + "FROM e1), 0 < SOME (SELECT k FROM e1), 0 <= SOME (SELECT k FROM e1), 0 > SOME (SELECT k "
            + "FROM e1), 0 >= SOME (SELECT k FROM e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 1 = SOME (SELECT k FROM e1), 1 <> SOME (SELECT k FROM e1), 1 != SOME (SELECT k "
            + "FROM e1), 1 < SOME (SELECT k FROM e1), 1 <= SOME (SELECT k FROM e1), 1 > SOME (SELECT k "
            + "FROM e1), 1 >= SOME (SELECT k FROM e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 2 = SOME (SELECT k FROM e1), 2 <> SOME (SELECT k FROM e1), 2 != SOME (SELECT k "
            + "FROM e1), 2 < SOME (SELECT k FROM e1), 2 <= SOME (SELECT k FROM e1), 2 > SOME (SELECT k "
            + "FROM e1), 2 >= SOME (SELECT k FROM e1)");
        assertAnswer("false|false|false|false|false|false|false",
            "SELECT 3 = SOME (SELECT k FROM e1), 3 <> SOME (SELECT k FROM e1), 3 != SOME (SELECT k "
            + "FROM e1), 3 < SOME (SELECT k FROM e1), 3 <= SOME (SELECT k FROM e1), 3 > SOME (SELECT k "
            + "FROM e1), 3 >= SOME (SELECT k FROM e1)");
        assertAnswer("1|false|false|false|false|false|false|false ;; "
            + "2|false|false|false|false|false|false|false ;; "
            + "3|false|false|false|false|false|false|false ;; "
            + "4|false|false|false|false|false|false|false ;; "
            + "5|false|false|false|false|false|false|false",
            "SELECT id, (k + 0) = SOME (SELECT k FROM e1), (k + 0) <> SOME (SELECT k FROM e1), (k + "
            + "0) != SOME (SELECT k FROM e1), (k + 0) < SOME (SELECT k FROM e1), (k + 0) <= SOME "
            + "(SELECT k FROM e1), (k + 0) > SOME (SELECT k FROM e1), (k + 0) >= SOME (SELECT k FROM "
            + "e1) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = SOME (SELECT k FROM e1), CAST(NULL AS INT) <> SOME "
            + "(SELECT k FROM e1), CAST(NULL AS INT) != SOME (SELECT k FROM e1), CAST(NULL AS INT) < "
            + "SOME (SELECT k FROM e1), CAST(NULL AS INT) <= SOME (SELECT k FROM e1), CAST(NULL AS INT)"
            + " > SOME (SELECT k FROM e1), CAST(NULL AS INT) >= SOME (SELECT k FROM e1) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = SOME (SELECT k FROM e1), IFF(id > 100, 1, NULL) <> "
            + "SOME (SELECT k FROM e1), IFF(id > 100, 1, NULL) != SOME (SELECT k FROM e1), IFF(id > "
            + "100, 1, NULL) < SOME (SELECT k FROM e1), IFF(id > 100, 1, NULL) <= SOME (SELECT k FROM "
            + "e1), IFF(id > 100, 1, NULL) > SOME (SELECT k FROM e1), IFF(id > 100, 1, NULL) >= SOME "
            + "(SELECT k FROM e1) FROM p ORDER BY id");
        assertAnswer("1|false|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|false|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, k = SOME (SELECT k FROM nn), k <> SOME (SELECT k FROM nn), k != SOME (SELECT "
            + "k FROM nn), k < SOME (SELECT k FROM nn), k <= SOME (SELECT k FROM nn), k > SOME (SELECT "
            + "k FROM nn), k >= SOME (SELECT k FROM nn) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = SOME (SELECT k FROM nn), NULL <> SOME (SELECT k FROM nn), NULL != SOME "
            + "(SELECT k FROM nn), NULL < SOME (SELECT k FROM nn), NULL <= SOME (SELECT k FROM nn), "
            + "NULL > SOME (SELECT k FROM nn), NULL >= SOME (SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = SOME (SELECT k FROM nn), CAST(NULL AS INT) <> SOME (SELECT k "
            + "FROM nn), CAST(NULL AS INT) != SOME (SELECT k FROM nn), CAST(NULL AS INT) < SOME (SELECT"
            + " k FROM nn), CAST(NULL AS INT) <= SOME (SELECT k FROM nn), CAST(NULL AS INT) > SOME "
            + "(SELECT k FROM nn), CAST(NULL AS INT) >= SOME (SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = SOME (SELECT k FROM nn), NULL::INT <> SOME (SELECT k FROM nn), "
            + "NULL::INT != SOME (SELECT k FROM nn), NULL::INT < SOME (SELECT k FROM nn), NULL::INT <= "
            + "SOME (SELECT k FROM nn), NULL::INT > SOME (SELECT k FROM nn), NULL::INT >= SOME (SELECT "
            + "k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = SOME (SELECT k FROM nn), NULLIF(1,1) <> SOME (SELECT k FROM nn), "
            + "NULLIF(1,1) != SOME (SELECT k FROM nn), NULLIF(1,1) < SOME (SELECT k FROM nn), "
            + "NULLIF(1,1) <= SOME (SELECT k FROM nn), NULLIF(1,1) > SOME (SELECT k FROM nn), "
            + "NULLIF(1,1) >= SOME (SELECT k FROM nn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = SOME (SELECT k FROM nn), 1 + NULL <> SOME (SELECT k FROM nn), 1 + NULL"
            + " != SOME (SELECT k FROM nn), 1 + NULL < SOME (SELECT k FROM nn), 1 + NULL <= SOME "
            + "(SELECT k FROM nn), 1 + NULL > SOME (SELECT k FROM nn), 1 + NULL >= SOME (SELECT k FROM "
            + "nn)");
        assertAnswer("false|true|true|true|true|false|false",
            "SELECT 0 = SOME (SELECT k FROM nn), 0 <> SOME (SELECT k FROM nn), 0 != SOME (SELECT k "
            + "FROM nn), 0 < SOME (SELECT k FROM nn), 0 <= SOME (SELECT k FROM nn), 0 > SOME (SELECT k "
            + "FROM nn), 0 >= SOME (SELECT k FROM nn)");
        assertAnswer("true|true|true|true|true|false|true",
            "SELECT 1 = SOME (SELECT k FROM nn), 1 <> SOME (SELECT k FROM nn), 1 != SOME (SELECT k "
            + "FROM nn), 1 < SOME (SELECT k FROM nn), 1 <= SOME (SELECT k FROM nn), 1 > SOME (SELECT k "
            + "FROM nn), 1 >= SOME (SELECT k FROM nn)");
        assertAnswer("true|true|true|false|true|true|true",
            "SELECT 2 = SOME (SELECT k FROM nn), 2 <> SOME (SELECT k FROM nn), 2 != SOME (SELECT k "
            + "FROM nn), 2 < SOME (SELECT k FROM nn), 2 <= SOME (SELECT k FROM nn), 2 > SOME (SELECT k "
            + "FROM nn), 2 >= SOME (SELECT k FROM nn)");
        assertAnswer("false|true|true|false|false|true|true",
            "SELECT 3 = SOME (SELECT k FROM nn), 3 <> SOME (SELECT k FROM nn), 3 != SOME (SELECT k "
            + "FROM nn), 3 < SOME (SELECT k FROM nn), 3 <= SOME (SELECT k FROM nn), 3 > SOME (SELECT k "
            + "FROM nn), 3 >= SOME (SELECT k FROM nn)");
        assertAnswer("1|false|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|false|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, (k + 0) = SOME (SELECT k FROM nn), (k + 0) <> SOME (SELECT k FROM nn), (k + "
            + "0) != SOME (SELECT k FROM nn), (k + 0) < SOME (SELECT k FROM nn), (k + 0) <= SOME "
            + "(SELECT k FROM nn), (k + 0) > SOME (SELECT k FROM nn), (k + 0) >= SOME (SELECT k FROM "
            + "nn) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = SOME (SELECT k FROM nn), CAST(NULL AS INT) <> SOME "
            + "(SELECT k FROM nn), CAST(NULL AS INT) != SOME (SELECT k FROM nn), CAST(NULL AS INT) < "
            + "SOME (SELECT k FROM nn), CAST(NULL AS INT) <= SOME (SELECT k FROM nn), CAST(NULL AS INT)"
            + " > SOME (SELECT k FROM nn), CAST(NULL AS INT) >= SOME (SELECT k FROM nn) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = SOME (SELECT k FROM nn), IFF(id > 100, 1, NULL) <> "
            + "SOME (SELECT k FROM nn), IFF(id > 100, 1, NULL) != SOME (SELECT k FROM nn), IFF(id > "
            + "100, 1, NULL) < SOME (SELECT k FROM nn), IFF(id > 100, 1, NULL) <= SOME (SELECT k FROM "
            + "nn), IFF(id > 100, 1, NULL) > SOME (SELECT k FROM nn), IFF(id > 100, 1, NULL) >= SOME "
            + "(SELECT k FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|false|false|false|true|false|true ;; "
            + "3|null|true|true|false|false|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, k = SOME (SELECT k FROM wn), k <> SOME (SELECT k FROM wn), k != SOME (SELECT "
            + "k FROM wn), k < SOME (SELECT k FROM wn), k <= SOME (SELECT k FROM wn), k > SOME (SELECT "
            + "k FROM wn), k >= SOME (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = SOME (SELECT k FROM wn), NULL <> SOME (SELECT k FROM wn), NULL != SOME "
            + "(SELECT k FROM wn), NULL < SOME (SELECT k FROM wn), NULL <= SOME (SELECT k FROM wn), "
            + "NULL > SOME (SELECT k FROM wn), NULL >= SOME (SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = SOME (SELECT k FROM wn), CAST(NULL AS INT) <> SOME (SELECT k "
            + "FROM wn), CAST(NULL AS INT) != SOME (SELECT k FROM wn), CAST(NULL AS INT) < SOME (SELECT"
            + " k FROM wn), CAST(NULL AS INT) <= SOME (SELECT k FROM wn), CAST(NULL AS INT) > SOME "
            + "(SELECT k FROM wn), CAST(NULL AS INT) >= SOME (SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = SOME (SELECT k FROM wn), NULL::INT <> SOME (SELECT k FROM wn), "
            + "NULL::INT != SOME (SELECT k FROM wn), NULL::INT < SOME (SELECT k FROM wn), NULL::INT <= "
            + "SOME (SELECT k FROM wn), NULL::INT > SOME (SELECT k FROM wn), NULL::INT >= SOME (SELECT "
            + "k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = SOME (SELECT k FROM wn), NULLIF(1,1) <> SOME (SELECT k FROM wn), "
            + "NULLIF(1,1) != SOME (SELECT k FROM wn), NULLIF(1,1) < SOME (SELECT k FROM wn), "
            + "NULLIF(1,1) <= SOME (SELECT k FROM wn), NULLIF(1,1) > SOME (SELECT k FROM wn), "
            + "NULLIF(1,1) >= SOME (SELECT k FROM wn)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = SOME (SELECT k FROM wn), 1 + NULL <> SOME (SELECT k FROM wn), 1 + NULL"
            + " != SOME (SELECT k FROM wn), 1 + NULL < SOME (SELECT k FROM wn), 1 + NULL <= SOME "
            + "(SELECT k FROM wn), 1 + NULL > SOME (SELECT k FROM wn), 1 + NULL >= SOME (SELECT k FROM "
            + "wn)");
        assertAnswer("null|true|true|true|true|false|false",
            "SELECT 0 = SOME (SELECT k FROM wn), 0 <> SOME (SELECT k FROM wn), 0 != SOME (SELECT k "
            + "FROM wn), 0 < SOME (SELECT k FROM wn), 0 <= SOME (SELECT k FROM wn), 0 > SOME (SELECT k "
            + "FROM wn), 0 >= SOME (SELECT k FROM wn)");
        assertAnswer("true|false|false|false|true|false|true",
            "SELECT 1 = SOME (SELECT k FROM wn), 1 <> SOME (SELECT k FROM wn), 1 != SOME (SELECT k "
            + "FROM wn), 1 < SOME (SELECT k FROM wn), 1 <= SOME (SELECT k FROM wn), 1 > SOME (SELECT k "
            + "FROM wn), 1 >= SOME (SELECT k FROM wn)");
        assertAnswer("null|true|true|false|false|true|true",
            "SELECT 2 = SOME (SELECT k FROM wn), 2 <> SOME (SELECT k FROM wn), 2 != SOME (SELECT k "
            + "FROM wn), 2 < SOME (SELECT k FROM wn), 2 <= SOME (SELECT k FROM wn), 2 > SOME (SELECT k "
            + "FROM wn), 2 >= SOME (SELECT k FROM wn)");
        assertAnswer("null|true|true|false|false|true|true",
            "SELECT 3 = SOME (SELECT k FROM wn), 3 <> SOME (SELECT k FROM wn), 3 != SOME (SELECT k "
            + "FROM wn), 3 < SOME (SELECT k FROM wn), 3 <= SOME (SELECT k FROM wn), 3 > SOME (SELECT k "
            + "FROM wn), 3 >= SOME (SELECT k FROM wn)");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|false|false|false|true|false|true ;; "
            + "3|null|true|true|false|false|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, (k + 0) = SOME (SELECT k FROM wn), (k + 0) <> SOME (SELECT k FROM wn), (k + "
            + "0) != SOME (SELECT k FROM wn), (k + 0) < SOME (SELECT k FROM wn), (k + 0) <= SOME "
            + "(SELECT k FROM wn), (k + 0) > SOME (SELECT k FROM wn), (k + 0) >= SOME (SELECT k FROM "
            + "wn) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = SOME (SELECT k FROM wn), CAST(NULL AS INT) <> SOME "
            + "(SELECT k FROM wn), CAST(NULL AS INT) != SOME (SELECT k FROM wn), CAST(NULL AS INT) < "
            + "SOME (SELECT k FROM wn), CAST(NULL AS INT) <= SOME (SELECT k FROM wn), CAST(NULL AS INT)"
            + " > SOME (SELECT k FROM wn), CAST(NULL AS INT) >= SOME (SELECT k FROM wn) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = SOME (SELECT k FROM wn), IFF(id > 100, 1, NULL) <> "
            + "SOME (SELECT k FROM wn), IFF(id > 100, 1, NULL) != SOME (SELECT k FROM wn), IFF(id > "
            + "100, 1, NULL) < SOME (SELECT k FROM wn), IFF(id > 100, 1, NULL) <= SOME (SELECT k FROM "
            + "wn), IFF(id > 100, 1, NULL) > SOME (SELECT k FROM wn), IFF(id > 100, 1, NULL) >= SOME "
            + "(SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null|false|false|false|false|false|false ;; 2|null|false|false|false|false|false|false"
            + " ;; 3|null|false|false|false|false|false|false ;; "
            + "4|null|false|false|false|false|false|false ;; 5|null|false|false|false|false|false|false",
            "SELECT id, k = SOME (SELECT k FROM an WHERE an.k IS NULL OR p.id > 0), k <> SOME (SELECT"
            + " k FROM an WHERE an.k IS NULL OR p.id > 0), k != SOME (SELECT k FROM an WHERE an.k IS "
            + "NULL OR p.id > 0), k < SOME (SELECT k FROM an WHERE an.k IS NULL OR p.id > 0), k <= SOME"
            + " (SELECT k FROM an WHERE an.k IS NULL OR p.id > 0), k > SOME (SELECT k FROM an WHERE "
            + "an.k IS NULL OR p.id > 0), k >= SOME (SELECT k FROM an WHERE an.k IS NULL OR p.id > 0) "
            + "FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = SOME (SELECT k FROM an), NULL <> SOME (SELECT k FROM an), NULL != SOME "
            + "(SELECT k FROM an), NULL < SOME (SELECT k FROM an), NULL <= SOME (SELECT k FROM an), "
            + "NULL > SOME (SELECT k FROM an), NULL >= SOME (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = SOME (SELECT k FROM an), CAST(NULL AS INT) <> SOME (SELECT k "
            + "FROM an), CAST(NULL AS INT) != SOME (SELECT k FROM an), CAST(NULL AS INT) < SOME (SELECT"
            + " k FROM an), CAST(NULL AS INT) <= SOME (SELECT k FROM an), CAST(NULL AS INT) > SOME "
            + "(SELECT k FROM an), CAST(NULL AS INT) >= SOME (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = SOME (SELECT k FROM an), NULL::INT <> SOME (SELECT k FROM an), "
            + "NULL::INT != SOME (SELECT k FROM an), NULL::INT < SOME (SELECT k FROM an), NULL::INT <= "
            + "SOME (SELECT k FROM an), NULL::INT > SOME (SELECT k FROM an), NULL::INT >= SOME (SELECT "
            + "k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = SOME (SELECT k FROM an), NULLIF(1,1) <> SOME (SELECT k FROM an), "
            + "NULLIF(1,1) != SOME (SELECT k FROM an), NULLIF(1,1) < SOME (SELECT k FROM an), "
            + "NULLIF(1,1) <= SOME (SELECT k FROM an), NULLIF(1,1) > SOME (SELECT k FROM an), "
            + "NULLIF(1,1) >= SOME (SELECT k FROM an)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = SOME (SELECT k FROM an), 1 + NULL <> SOME (SELECT k FROM an), 1 + NULL"
            + " != SOME (SELECT k FROM an), 1 + NULL < SOME (SELECT k FROM an), 1 + NULL <= SOME "
            + "(SELECT k FROM an), 1 + NULL > SOME (SELECT k FROM an), 1 + NULL >= SOME (SELECT k FROM "
            + "an)");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = SOME (SELECT k FROM an), CAST(NULL AS INT) <> SOME "
            + "(SELECT k FROM an), CAST(NULL AS INT) != SOME (SELECT k FROM an), CAST(NULL AS INT) < "
            + "SOME (SELECT k FROM an), CAST(NULL AS INT) <= SOME (SELECT k FROM an), CAST(NULL AS INT)"
            + " > SOME (SELECT k FROM an), CAST(NULL AS INT) >= SOME (SELECT k FROM an) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = SOME (SELECT k FROM an), IFF(id > 100, 1, NULL) <> "
            + "SOME (SELECT k FROM an), IFF(id > 100, 1, NULL) != SOME (SELECT k FROM an), IFF(id > "
            + "100, 1, NULL) < SOME (SELECT k FROM an), IFF(id > 100, 1, NULL) <= SOME (SELECT k FROM "
            + "an), IFF(id > 100, 1, NULL) > SOME (SELECT k FROM an), IFF(id > 100, 1, NULL) >= SOME "
            + "(SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, k = SOME (SELECT k FROM w3), k <> SOME (SELECT k FROM w3), k != SOME (SELECT "
            + "k FROM w3), k < SOME (SELECT k FROM w3), k <= SOME (SELECT k FROM w3), k > SOME (SELECT "
            + "k FROM w3), k >= SOME (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL = SOME (SELECT k FROM w3), NULL <> SOME (SELECT k FROM w3), NULL != SOME "
            + "(SELECT k FROM w3), NULL < SOME (SELECT k FROM w3), NULL <= SOME (SELECT k FROM w3), "
            + "NULL > SOME (SELECT k FROM w3), NULL >= SOME (SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT CAST(NULL AS INT) = SOME (SELECT k FROM w3), CAST(NULL AS INT) <> SOME (SELECT k "
            + "FROM w3), CAST(NULL AS INT) != SOME (SELECT k FROM w3), CAST(NULL AS INT) < SOME (SELECT"
            + " k FROM w3), CAST(NULL AS INT) <= SOME (SELECT k FROM w3), CAST(NULL AS INT) > SOME "
            + "(SELECT k FROM w3), CAST(NULL AS INT) >= SOME (SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULL::INT = SOME (SELECT k FROM w3), NULL::INT <> SOME (SELECT k FROM w3), "
            + "NULL::INT != SOME (SELECT k FROM w3), NULL::INT < SOME (SELECT k FROM w3), NULL::INT <= "
            + "SOME (SELECT k FROM w3), NULL::INT > SOME (SELECT k FROM w3), NULL::INT >= SOME (SELECT "
            + "k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT NULLIF(1,1) = SOME (SELECT k FROM w3), NULLIF(1,1) <> SOME (SELECT k FROM w3), "
            + "NULLIF(1,1) != SOME (SELECT k FROM w3), NULLIF(1,1) < SOME (SELECT k FROM w3), "
            + "NULLIF(1,1) <= SOME (SELECT k FROM w3), NULLIF(1,1) > SOME (SELECT k FROM w3), "
            + "NULLIF(1,1) >= SOME (SELECT k FROM w3)");
        assertAnswer("null|null|null|null|null|null|null",
            "SELECT 1 + NULL = SOME (SELECT k FROM w3), 1 + NULL <> SOME (SELECT k FROM w3), 1 + NULL"
            + " != SOME (SELECT k FROM w3), 1 + NULL < SOME (SELECT k FROM w3), 1 + NULL <= SOME "
            + "(SELECT k FROM w3), 1 + NULL > SOME (SELECT k FROM w3), 1 + NULL >= SOME (SELECT k FROM "
            + "w3)");
        assertAnswer("null|true|true|true|true|false|false",
            "SELECT 0 = SOME (SELECT k FROM w3), 0 <> SOME (SELECT k FROM w3), 0 != SOME (SELECT k "
            + "FROM w3), 0 < SOME (SELECT k FROM w3), 0 <= SOME (SELECT k FROM w3), 0 > SOME (SELECT k "
            + "FROM w3), 0 >= SOME (SELECT k FROM w3)");
        assertAnswer("true|true|true|true|true|false|true",
            "SELECT 1 = SOME (SELECT k FROM w3), 1 <> SOME (SELECT k FROM w3), 1 != SOME (SELECT k "
            + "FROM w3), 1 < SOME (SELECT k FROM w3), 1 <= SOME (SELECT k FROM w3), 1 > SOME (SELECT k "
            + "FROM w3), 1 >= SOME (SELECT k FROM w3)");
        assertAnswer("true|true|true|false|true|true|true",
            "SELECT 2 = SOME (SELECT k FROM w3), 2 <> SOME (SELECT k FROM w3), 2 != SOME (SELECT k "
            + "FROM w3), 2 < SOME (SELECT k FROM w3), 2 <= SOME (SELECT k FROM w3), 2 > SOME (SELECT k "
            + "FROM w3), 2 >= SOME (SELECT k FROM w3)");
        assertAnswer("null|true|true|false|false|true|true",
            "SELECT 3 = SOME (SELECT k FROM w3), 3 <> SOME (SELECT k FROM w3), 3 != SOME (SELECT k "
            + "FROM w3), 3 < SOME (SELECT k FROM w3), 3 <= SOME (SELECT k FROM w3), 3 > SOME (SELECT k "
            + "FROM w3), 3 >= SOME (SELECT k FROM w3)");
        assertAnswer("1|null|true|true|true|true|false|false ;; 2|true|true|true|true|true|false|true ;; "
            + "3|true|true|true|false|true|true|true ;; 4|null|true|true|false|false|true|true ;; "
            + "5|null|false|false|false|false|false|false",
            "SELECT id, (k + 0) = SOME (SELECT k FROM w3), (k + 0) <> SOME (SELECT k FROM w3), (k + "
            + "0) != SOME (SELECT k FROM w3), (k + 0) < SOME (SELECT k FROM w3), (k + 0) <= SOME "
            + "(SELECT k FROM w3), (k + 0) > SOME (SELECT k FROM w3), (k + 0) >= SOME (SELECT k FROM "
            + "w3) FROM p ORDER BY id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, CAST(NULL AS INT) = SOME (SELECT k FROM w3), CAST(NULL AS INT) <> SOME "
            + "(SELECT k FROM w3), CAST(NULL AS INT) != SOME (SELECT k FROM w3), CAST(NULL AS INT) < "
            + "SOME (SELECT k FROM w3), CAST(NULL AS INT) <= SOME (SELECT k FROM w3), CAST(NULL AS INT)"
            + " > SOME (SELECT k FROM w3), CAST(NULL AS INT) >= SOME (SELECT k FROM w3) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
            + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
            + "5|null|null|null|null|null|null|null",
            "SELECT id, IFF(id > 100, 1, NULL) = SOME (SELECT k FROM w3), IFF(id > 100, 1, NULL) <> "
            + "SOME (SELECT k FROM w3), IFF(id > 100, 1, NULL) != SOME (SELECT k FROM w3), IFF(id > "
            + "100, 1, NULL) < SOME (SELECT k FROM w3), IFF(id > 100, 1, NULL) <= SOME (SELECT k FROM "
            + "w3), IFF(id > 100, 1, NULL) > SOME (SELECT k FROM w3), IFF(id > 100, 1, NULL) >= SOME "
            + "(SELECT k FROM w3) FROM p ORDER BY id");
    }

    @Test
    public void eachOperatorAlone() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        assertAnswer("1|null ;; 2|true ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) = ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END = ANY (SELECT k FROM wn) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) = ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) <> ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <> ANY (SELECT k FROM wn) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) <> ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) < ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END < ANY (SELECT k FROM wn) FROM p ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) < ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) <= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <= ANY (SELECT k FROM wn) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) <= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) > ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END > ANY (SELECT k FROM wn) FROM p ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) > ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) >= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END >= ANY (SELECT k FROM wn) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) >= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) = ANY (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END = ANY (SELECT k FROM an) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) = ANY (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER BY"
            + " id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0)"
            + " FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER BY"
            + " id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0) "
            + "FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0)"
            + " FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER BY"
            + " id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0) "
            + "FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0)"
            + " FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER"
            + " BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER "
            + "BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) = ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END = ANY (SELECT column1 FROM VALUES (1),"
            + " (2)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) = ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|null",
            "SELECT id, k = ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) <> ANY (SELECT column1 FROM VALUES (1), (2)) FROM p "
            + "ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <> ANY (SELECT column1 FROM VALUES "
            + "(1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) <> ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ANY (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY"
            + " id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) < ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER"
            + " BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END < ANY (SELECT column1 FROM VALUES (1),"
            + " (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) < ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY "
            + "id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) <= ANY (SELECT column1 FROM VALUES (1), (2)) FROM p "
            + "ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <= ANY (SELECT column1 FROM VALUES "
            + "(1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) <= ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY"
            + " id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) > ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END > ANY (SELECT column1 FROM VALUES (1),"
            + " (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) > ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k > ANY (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY "
            + "id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) >= ANY (SELECT column1 FROM VALUES (1), (2)) FROM p "
            + "ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END >= ANY (SELECT column1 FROM VALUES "
            + "(1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) >= ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY"
            + " id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <> ANY (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ANY (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k > ANY (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) = ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END = ALL (SELECT k FROM wn) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) = ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) < ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END < ALL (SELECT k FROM wn) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) < ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) <= ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <= ALL (SELECT k FROM wn) FROM p ORDER"
            + " BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) <= ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) > ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END > ALL (SELECT k FROM wn) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) > ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) >= ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END >= ALL (SELECT k FROM wn) FROM p ORDER"
            + " BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) >= ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) = ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END = ALL (SELECT k FROM an) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) = ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) <> ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <> ALL (SELECT k FROM an) FROM p ORDER"
            + " BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) <> ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <> ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) < ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END < ALL (SELECT k FROM an) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) < ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) <= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <= ALL (SELECT k FROM an) FROM p ORDER"
            + " BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) <= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) > ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END > ALL (SELECT k FROM an) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) > ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, IFF(id > 3, NULL, k) >= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END >= ALL (SELECT k FROM an) FROM p ORDER"
            + " BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NULLIF(k, k) >= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <> ALL (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0) "
            + "FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0) "
            + "FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <= ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0)"
            + " FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER BY"
            + " id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0) "
            + "FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT)) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT column1 FROM VALUES (NULL::INT), (NULL::INT) WHERE p.id > 0)"
            + " FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER "
            + "BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k <= ALL (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k <= ALL (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT column1 FROM VALUES (1), (NULL)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k >= ALL (SELECT column1 FROM VALUES (1), (NULL) WHERE p.id > 0) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) = ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END = ALL (SELECT column1 FROM VALUES (1),"
            + " (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) = ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ALL (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY "
            + "id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) <> ALL (SELECT column1 FROM VALUES (1), (2)) FROM p "
            + "ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <> ALL (SELECT column1 FROM VALUES "
            + "(1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) <> ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k <> ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k <> ALL (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY"
            + " id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) < ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER"
            + " BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END < ALL (SELECT column1 FROM VALUES (1),"
            + " (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) < ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY "
            + "id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) <= ALL (SELECT column1 FROM VALUES (1), (2)) FROM p "
            + "ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END <= ALL (SELECT column1 FROM VALUES "
            + "(1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) <= ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k <= ALL (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY"
            + " id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) > ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER"
            + " BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END > ALL (SELECT column1 FROM VALUES (1),"
            + " (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) > ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k > ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k > ALL (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY "
            + "id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, IFF(id > 3, NULL, k) >= ALL (SELECT column1 FROM VALUES (1), (2)) FROM p "
            + "ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, CASE WHEN id >= 4 THEN NULL ELSE k END >= ALL (SELECT column1 FROM VALUES "
            + "(1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NULLIF(k, k) >= ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k >= ALL (SELECT column1 FROM VALUES (1), (2) WHERE p.id > 0) FROM p ORDER BY"
            + " id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k = ALL (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k = ALL (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k < ALL (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k < ALL (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k <= ALL (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k <= ALL (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k > ALL (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k > ALL (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k >= ALL (SELECT k FROM nn WHERE k > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k >= ALL (SELECT k FROM nn WHERE k > 10 AND p.id > 0) FROM p ORDER BY id");
    }

    @Test
    public void eachPlace() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE p2 (id INT, n NUMBER(10,2), s VARCHAR)");
        engine.execute("INSERT INTO p2 VALUES (1, 1.5, 'a'), (2, NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE i2 (n NUMBER(10,2), s VARCHAR)");
        engine.execute("INSERT INTO i2 VALUES (1.0, 'b'), (NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE i3 (n NUMBER(10,2), s VARCHAR)");
        engine.execute("INSERT INTO i3 VALUES (2.0, 'b'), (3.0, 'c')");
        assertAnswer("1",
            "SELECT id FROM p WHERE k < ANY (SELECT k FROM wn) ORDER BY id");
        assertAnswer("2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM p WHERE NOT (k < ANY (SELECT k FROM wn)) ORDER BY id");
        assertAnswer("1|T ;; 2|F ;; 3|F ;; 4|F ;; 5|F",
            "SELECT id, CASE WHEN k < ANY (SELECT k FROM wn) THEN 'T' WHEN NOT (k < ANY (SELECT k "
            + "FROM wn)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        assertAnswer("1|false|true|true ;; 2|false|null|false ;; 3|false|null|false ;; 4|false|null|false ;; "
            + "5|false|null|false",
            "SELECT id, (k < ANY (SELECT k FROM wn)) IS NULL, (k < ANY (SELECT k FROM wn)) OR NULL, "
            + "(k < ANY (SELECT k FROM wn)) AND TRUE FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k < ANY (SELECT k FROM wn))");
        assertAnswer("1|0 ;; 2|-1 ;; 3|-1 ;; 4|-1 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k < ANY (SELECT k FROM wn))");
        assertAnswer("1",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k < ANY (SELECT k FROM wn)) IS NULL ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4",
            "SELECT id FROM p WHERE k <> ANY (SELECT k FROM nn) ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM p WHERE NOT (k <> ANY (SELECT k FROM nn)) ORDER BY id");
        assertAnswer("1|T ;; 2|T ;; 3|T ;; 4|T ;; 5|F",
            "SELECT id, CASE WHEN k <> ANY (SELECT k FROM nn) THEN 'T' WHEN NOT (k <> ANY (SELECT k "
            + "FROM nn)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        assertAnswer("1|false|true|true ;; 2|false|true|true ;; 3|false|true|true ;; 4|false|true|true ;; "
            + "5|false|null|false",
            "SELECT id, (k <> ANY (SELECT k FROM nn)) IS NULL, (k <> ANY (SELECT k FROM nn)) OR NULL,"
            + " (k <> ANY (SELECT k FROM nn)) AND TRUE FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k <> ANY (SELECT k FROM nn))");
        assertAnswer("1|0 ;; 2|1 ;; 3|2 ;; 4|3 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k <> ANY (SELECT k FROM nn))");
        assertAnswer("1 ;; 2 ;; 3 ;; 4",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k <> ANY (SELECT k FROM nn)) IS NULL ORDER BY id");
        assertAnswer("2",
            "SELECT id FROM p WHERE k = ANY (SELECT k FROM wn) ORDER BY id");
        assertAnswer("1|true|null|null ;; 2|false|true|true ;; 3|true|null|null ;; 4|true|null|null ;; "
            + "5|true|null|null",
            "SELECT id, (k = ANY (SELECT k FROM wn)) IS NULL, (k = ANY (SELECT k FROM wn)) OR NULL, "
            + "(k = ANY (SELECT k FROM wn)) AND TRUE FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k = ANY (SELECT k FROM wn))");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k = ANY (SELECT k FROM wn))");
        assertAnswer("1 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM p WHERE (k = ANY (SELECT k FROM wn)) IS NULL ORDER BY id");
        assertAnswer("2 ;; 3 ;; 4",
            "SELECT id FROM p WHERE k >= ANY (SELECT k FROM w3 WHERE p.id > 0) ORDER BY id");
        assertAnswer("1 ;; 5",
            "SELECT id FROM p WHERE NOT (k >= ANY (SELECT k FROM w3 WHERE p.id > 0)) ORDER BY id");
        assertAnswer("1|F ;; 2|T ;; 3|T ;; 4|T ;; 5|F",
            "SELECT id, CASE WHEN k >= ANY (SELECT k FROM w3 WHERE p.id > 0) THEN 'T' WHEN NOT (k >= "
            + "ANY (SELECT k FROM w3 WHERE p.id > 0)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        assertAnswer("1|false|null|false ;; 2|false|true|true ;; 3|false|true|true ;; 4|false|true|true ;; "
            + "5|false|null|false",
            "SELECT id, (k >= ANY (SELECT k FROM w3 WHERE p.id > 0)) IS NULL, (k >= ANY (SELECT k "
            + "FROM w3 WHERE p.id > 0)) OR NULL, (k >= ANY (SELECT k FROM w3 WHERE p.id > 0)) AND TRUE "
            + "FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k >= ANY (SELECT k FROM w3 WHERE q.id > 0))");
        assertAnswer("1|-1 ;; 2|1 ;; 3|2 ;; 4|3 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k >= ANY (SELECT k FROM w3 WHERE q.id > 0))");
        assertAnswer("2 ;; 3 ;; 4",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k >= ANY (SELECT k FROM w3 WHERE p.id > 0)) IS NULL ORDER BY id");
        assertAnswer("2 ;; 3",
            "SELECT id FROM p WHERE k = ANY (SELECT k FROM w3 WHERE p.id > 0) ORDER BY id");
        assertAnswer("1 ;; 4 ;; 5",
            "SELECT id FROM p WHERE NOT (k = ANY (SELECT k FROM w3 WHERE p.id > 0)) ORDER BY id");
        assertAnswer("1|F ;; 2|T ;; 3|T ;; 4|F ;; 5|F",
            "SELECT id, CASE WHEN k = ANY (SELECT k FROM w3 WHERE p.id > 0) THEN 'T' WHEN NOT (k = "
            + "ANY (SELECT k FROM w3 WHERE p.id > 0)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k = ANY (SELECT k FROM w3 WHERE q.id > 0))");
        assertAnswer("1|-1 ;; 2|1 ;; 3|2 ;; 4|-1 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k = ANY (SELECT k FROM w3 WHERE q.id > 0))");
        assertAnswer("2 ;; 3",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM p WHERE k < ALL (SELECT k FROM nn) ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k < ALL (SELECT k FROM nn))");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k < ALL (SELECT k FROM nn))");
        assertAnswer("",
            "SELECT id FROM p WHERE k = ALL (SELECT k FROM nn) ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k = ALL (SELECT k FROM nn))");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k = ALL (SELECT k FROM nn))");
        assertAnswer("",
            "SELECT id FROM p WHERE k > ALL (SELECT k FROM wn) ORDER BY id");
        assertAnswer("1 ;; 2",
            "SELECT id FROM p WHERE NOT (k > ALL (SELECT k FROM wn)) ORDER BY id");
        assertAnswer("1|F ;; 2|F ;; 3|N ;; 4|N ;; 5|N",
            "SELECT id, CASE WHEN k > ALL (SELECT k FROM wn) THEN 'T' WHEN NOT (k > ALL (SELECT k "
            + "FROM wn)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        assertAnswer("1|false|null|false ;; 2|false|null|false ;; 3|true|null|null ;; 4|true|null|null ;; "
            + "5|true|null|null",
            "SELECT id, (k > ALL (SELECT k FROM wn)) IS NULL, (k > ALL (SELECT k FROM wn)) OR NULL, "
            + "(k > ALL (SELECT k FROM wn)) AND TRUE FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k > ALL (SELECT k FROM wn))");
        assertAnswer("1|-1 ;; 2|-1 ;; 3|2 ;; 4|3 ;; 5|null",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k > ALL (SELECT k FROM wn))");
        assertAnswer("3 ;; 4 ;; 5",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("3 ;; 4 ;; 5",
            "SELECT id FROM p WHERE (k > ALL (SELECT k FROM wn)) IS NULL ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE k <= ALL (SELECT k FROM w3 WHERE p.id > 0) ORDER BY id");
        assertAnswer("3 ;; 4",
            "SELECT id FROM p WHERE NOT (k <= ALL (SELECT k FROM w3 WHERE p.id > 0)) ORDER BY id");
        assertAnswer("1|N ;; 2|N ;; 3|F ;; 4|F ;; 5|N",
            "SELECT id, CASE WHEN k <= ALL (SELECT k FROM w3 WHERE p.id > 0) THEN 'T' WHEN NOT (k <= "
            + "ALL (SELECT k FROM w3 WHERE p.id > 0)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        assertAnswer("1|true|null|null ;; 2|true|null|null ;; 3|false|null|false ;; 4|false|null|false ;; "
            + "5|true|null|null",
            "SELECT id, (k <= ALL (SELECT k FROM w3 WHERE p.id > 0)) IS NULL, (k <= ALL (SELECT k "
            + "FROM w3 WHERE p.id > 0)) OR NULL, (k <= ALL (SELECT k FROM w3 WHERE p.id > 0)) AND TRUE "
            + "FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k <= ALL (SELECT k FROM w3 WHERE q.id > 0))");
        assertAnswer("1|0 ;; 2|1 ;; 3|-1 ;; 4|-1 ;; 5|null",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k <= ALL (SELECT k FROM w3 WHERE q.id > 0))");
        assertAnswer("1 ;; 2 ;; 5",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("1 ;; 2 ;; 5",
            "SELECT id FROM p WHERE (k <= ALL (SELECT k FROM w3 WHERE p.id > 0)) IS NULL ORDER BY id");
        assertAnswer("3 ;; 4",
            "SELECT id FROM p WHERE k > SOME (SELECT column1 FROM VALUES (1), (NULL)) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 5",
            "SELECT id FROM p WHERE NOT (k > SOME (SELECT column1 FROM VALUES (1), (NULL))) ORDER BY "
            + "id");
        assertAnswer("1|F ;; 2|F ;; 3|T ;; 4|T ;; 5|F",
            "SELECT id, CASE WHEN k > SOME (SELECT column1 FROM VALUES (1), (NULL)) THEN 'T' WHEN NOT"
            + " (k > SOME (SELECT column1 FROM VALUES (1), (NULL))) THEN 'F' ELSE 'N' END FROM p ORDER "
            + "BY id");
        assertAnswer("1|false|null|false ;; 2|false|null|false ;; 3|false|true|true ;; 4|false|true|true ;; "
            + "5|false|null|false",
            "SELECT id, (k > SOME (SELECT column1 FROM VALUES (1), (NULL))) IS NULL, (k > SOME "
            + "(SELECT column1 FROM VALUES (1), (NULL))) OR NULL, (k > SOME (SELECT column1 FROM VALUES"
            + " (1), (NULL))) AND TRUE FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k > SOME (SELECT column1 FROM VALUES (1), (NULL)))");
        assertAnswer("1|-1 ;; 2|-1 ;; 3|2 ;; 4|3 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k > SOME (SELECT column1 FROM VALUES (1), (NULL)))");
        assertAnswer("3 ;; 4",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k > SOME (SELECT column1 FROM VALUES (1), (NULL))) IS NULL ORDER"
            + " BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM p WHERE k <> ALL (SELECT k FROM e1) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE NOT (k <> ALL (SELECT k FROM e1)) ORDER BY id");
        assertAnswer("1|T ;; 2|T ;; 3|T ;; 4|T ;; 5|T",
            "SELECT id, CASE WHEN k <> ALL (SELECT k FROM e1) THEN 'T' WHEN NOT (k <> ALL (SELECT k "
            + "FROM e1)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        assertAnswer("1|false|true|true ;; 2|false|true|true ;; 3|false|true|true ;; 4|false|true|true ;; "
            + "5|false|true|true",
            "SELECT id, (k <> ALL (SELECT k FROM e1)) IS NULL, (k <> ALL (SELECT k FROM e1)) OR NULL,"
            + " (k <> ALL (SELECT k FROM e1)) AND TRUE FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k <> ALL (SELECT k FROM e1))");
        assertAnswer("1|0 ;; 2|1 ;; 3|2 ;; 4|3 ;; 5|null",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k <> ALL (SELECT k FROM e1))");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k <> ALL (SELECT k FROM e1)) IS NULL ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE k < ANY (SELECT k FROM e1) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM p WHERE NOT (k < ANY (SELECT k FROM e1)) ORDER BY id");
        assertAnswer("1|F ;; 2|F ;; 3|F ;; 4|F ;; 5|F",
            "SELECT id, CASE WHEN k < ANY (SELECT k FROM e1) THEN 'T' WHEN NOT (k < ANY (SELECT k "
            + "FROM e1)) THEN 'F' ELSE 'N' END FROM p ORDER BY id");
        assertAnswer("1|false|null|false ;; 2|false|null|false ;; 3|false|null|false ;; 4|false|null|false ;; "
            + "5|false|null|false",
            "SELECT id, (k < ANY (SELECT k FROM e1)) IS NULL, (k < ANY (SELECT k FROM e1)) OR NULL, "
            + "(k < ANY (SELECT k FROM e1)) AND TRUE FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k < ANY (SELECT k FROM e1))");
        assertAnswer("1|-1 ;; 2|-1 ;; 3|-1 ;; 4|-1 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE NOT (k < ANY (SELECT k FROM e1))");
        assertAnswer("",
            "SELECT id FROM q ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k < ANY (SELECT k FROM e1)) IS NULL ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false",
            "SELECT p.id, p.k > ANY (SELECT k FROM wn WHERE wn.k IS NULL OR wn.k < p.id) FROM p JOIN "
            + "nn ON nn.k = 1 ORDER BY p.id");
        assertAnswer("true|null|null|null|null",
            "SELECT 2 > ANY (SELECT k FROM wn), NULL > ANY (SELECT k FROM wn), NULL < ALL (SELECT k "
            + "FROM nn), NULL < ALL (SELECT k FROM e1), NULL::INT = ANY (SELECT k FROM e1)");
    }

    @Test
    public void nullRowsOfTheQuery() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE nk (k INT NOT NULL)");
        engine.execute("INSERT INTO nk VALUES (1), (2)");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM w3 WHERE k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT column1 FROM VALUES (1), (NULL) WHERE column1 IS NOT NULL) "
            + "FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ALL (SELECT COALESCE(k, 0) FROM wn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ALL (SELECT k FROM nk) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE k IS NOT NULL) FROM p ORDER BY id");
    }

    @Test
    public void nullableColumnsOfTheQuery() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE nk (k INT NOT NULL)");
        engine.execute("INSERT INTO nk VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE nd (k INT)");
        engine.execute("INSERT INTO nd VALUES (1), (2), (NULL)");
        engine.execute("DELETE FROM nd WHERE k IS NULL");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ALL (SELECT k FROM nk) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT k FROM w3 WHERE k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k = ALL (SELECT k + 0 FROM w3 WHERE k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ALL (SELECT COALESCE(k, 0) FROM w3 WHERE k IS NOT NULL) FROM p ORDER BY "
            + "id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (1), (2)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT k FROM nk WHERE k > 1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k < ALL (SELECT 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT k FROM w3 WHERE k = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT MAX(k) FROM nn) FROM p ORDER BY id");
    }

    @Test
    public void oneValueColumnsOfTheQuery() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE nk (k INT NOT NULL)");
        engine.execute("INSERT INTO nk VALUES (1), (2)");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT k FROM nk WHERE k = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (7)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|null",
            "SELECT id, k < ALL (SELECT COUNT(*) FROM nk) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k < ALL (SELECT 5 UNION ALL SELECT 6) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT k FROM nk LIMIT 1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT k FROM nk) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT COALESCE(k, 0) FROM wn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT k FROM nk WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ALL (SELECT k + 1 FROM nk) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k < ALL (SELECT 5 FROM nk) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k < ALL (SELECT 5 FROM nn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k < ALL (SELECT k FROM nk WHERE k > 5) FROM p ORDER BY id");
    }

    /**
     * A VALUES clause of one row answers a column of one value, read directly, through an alias, a star, an
     * expression or a derived table, so a NULL subject is UNKNOWN against it; one of two rows, a NOT NULL column of
     * one row and an expression over one are FALSE (live-verified).
     */
    @Test
    public void oneRowValuesColumnsAreOneValue() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k VARCHAR)");
        engine.execute("INSERT INTO p VALUES (1,'a'),(2,NULL)");
        engine.execute("CREATE OR REPLACE TABLE tn (s VARCHAR NOT NULL)");
        engine.execute("INSERT INTO tn VALUES ('b'),('c')");
        engine.execute("CREATE OR REPLACE TABLE pn (id INT, k INT)");
        engine.execute("INSERT INTO pn VALUES (1,0),(2,NULL)");
        assertAnswer("1|true ;; 2|false", "SELECT id, k < ALL (SELECT s FROM tn WHERE s = 'b') FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|null", "SELECT id, k < ALL (SELECT column1 FROM VALUES ('z')) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false", "SELECT id, k < ALL (SELECT UPPER(s) FROM tn LIMIT 1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES ('y'), ('z')) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false", "SELECT id, k < ALL (SELECT s FROM tn LIMIT 1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|null", "SELECT id, k < ALL (SELECT 7) FROM pn ORDER BY id");
        assertAnswer("1|true ;; 2|null", "SELECT id, k < ALL (SELECT column1 FROM VALUES (7)) FROM pn ORDER BY id");
        assertAnswer("1|true ;; 2|false",
            "SELECT id, k < ALL (SELECT column1 FROM VALUES (7), (8)) FROM pn ORDER BY id");
        assertAnswer("1|true ;; 2|null", "SELECT id, k < ALL (SELECT x FROM VALUES (7) t(x)) FROM pn ORDER BY id");
        assertAnswer("1|true ;; 2|null", "SELECT id, k < ALL (SELECT * FROM VALUES (7)) FROM pn ORDER BY id");
        assertAnswer("1|true ;; 2|null",
            "SELECT id, k < ALL (SELECT column1 + 1 FROM VALUES (7)) FROM pn ORDER BY id");
        assertAnswer("1|true ;; 2|null", "SELECT id, k NOT IN (SELECT column1 FROM VALUES (7)) FROM pn ORDER BY id");
        assertAnswer("1|true ;; 2|null",
            "SELECT id, k < ALL (SELECT column1 FROM (SELECT column1 FROM VALUES (7))) FROM pn ORDER BY id");
    }

    /**
     * Where the account's answer hangs on what it knows of a table before it runs the query, or on the place a
     * membership stands, this engine keeps the rule above: a table whose column holds only NULLs answers FALSE under
     * {@code < ANY} (the account folds it to NULL), {@code = ANY} keeps its three values under a NOT in a WHERE (the account drops them to FALSE), and
     * {@code <> ALL} over rows that hold a NULL is answered (the account refuses it as an unsupported subquery).
     */
    @Test
    public void answersTheAccountFoldsAreKeptToTheRule() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account answers these cells from its own plan");
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("", "SELECT id FROM p WHERE NOT (k = ANY (SELECT k FROM wn)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 16",
            "SELECT id, k <> ALL (SELECT k FROM wn) FROM p ORDER BY id");
    }
}
