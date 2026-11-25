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
 * A membership whose query reads the row around it only inside conditions the outer relation's statistics settle
 * (live-verified): over ids 1 to 5, {@code k IN (SELECT k FROM w3 WHERE p.id > 0)} folds its correlation away and is
 * three-valued as its uncorrelated twin is, NULL where no row matches and the set holds a NULL — the same under
 * {@code p.id < 10}, {@code BETWEEN 1 AND 5}, {@code <> 0}, {@code IS NOT NULL}, a disjunct beside a column of its
 * own, a HAVING, an ON, a LIMIT, a DISTINCT or a UNION arm — where a condition the statistics leave open
 * ({@code p.id > 2}, {@code p.id BETWEEN 1 AND 3}, {@code p.id = 2}) or an equality with a column of its own reads
 * the row and the membership stays two-valued. Under NOT in a WHERE or a CASE condition it is the anti join written,
 * and its NOT IN passes over the NULLs of the set, FALSE for a NULL subject.
 */
public class SettledCorrelationMembershipTest extends MeasuredCellsTestSupport {

    @Test
    public void selectListAndPredicates() {
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
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id > 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id BETWEEN 1 AND 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|null ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id = 2) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.k + 1 > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND p.id < 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id IN (1, 2, 3)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY "
            + "id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND k IS NOT NULL) FROM p ORDER BY "
            + "id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id > 0 OR k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND k = p.k) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|null",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id > 0 AND k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|null",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE k < p.id) FROM p ORDER BY id");
        assertAnswer("1 ;; 4 ;; 5",
            "SELECT id FROM p WHERE (k IN (SELECT k FROM w3 WHERE p.id > 0)) IS NULL ORDER BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|1 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k IN (SELECT k FROM w3 WHERE p.id > 0) THEN 1 WHEN NOT (k IN (SELECT "
            + "k FROM w3 WHERE p.id > 0)) THEN 0 ELSE 9 END FROM p ORDER BY id");
        assertAnswer("1 ;; 4 ;; 5",
            "SELECT id FROM p WHERE (k = ANY (SELECT k FROM w3 WHERE p.id > 0)) IS NULL ORDER BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|1 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k = ANY (SELECT k FROM w3 WHERE p.id > 0) THEN 1 WHEN NOT (k = ANY "
            + "(SELECT k FROM w3 WHERE p.id > 0)) THEN 0 ELSE 9 END FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 34",
            "SELECT id FROM p WHERE (k NOT IN (SELECT k FROM w3 WHERE p.id > 0)) IS NULL ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3)) IS NULL ORDER "
            + "BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|1 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) THEN 1 WHEN NOT (k"
            + " IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3)) THEN 0 ELSE 9 END FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k = ANY (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3)) IS NULL "
            + "ORDER BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|1 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k = ANY (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) THEN 1 WHEN NOT"
            + " (k = ANY (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3)) THEN 0 ELSE 9 END FROM p ORDER BY"
            + " id");
        assertAnswer("1",
            "SELECT id FROM p WHERE (k NOT IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3)) IS NULL "
            + "ORDER BY id");
        assertAnswer("1|9 ;; 2|0 ;; 3|0 ;; 4|1 ;; 5|1",
            "SELECT id, CASE WHEN k NOT IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3) THEN 1 WHEN "
            + "NOT (k NOT IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 3)) THEN 0 ELSE 9 END FROM p "
            + "ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k IN (SELECT k FROM w3 WHERE p.id = 2)) IS NULL ORDER BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k IN (SELECT k FROM w3 WHERE p.id = 2) THEN 1 WHEN NOT (k IN (SELECT "
            + "k FROM w3 WHERE p.id = 2)) THEN 0 ELSE 9 END FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k = ANY (SELECT k FROM w3 WHERE p.id = 2)) IS NULL ORDER BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k = ANY (SELECT k FROM w3 WHERE p.id = 2) THEN 1 WHEN NOT (k = ANY "
            + "(SELECT k FROM w3 WHERE p.id = 2)) THEN 0 ELSE 9 END FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k NOT IN (SELECT k FROM w3 WHERE p.id = 2)) IS NULL ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, CASE WHEN k NOT IN (SELECT k FROM w3 WHERE p.id = 2) THEN 1 WHEN NOT (k NOT IN "
            + "(SELECT k FROM w3 WHERE p.id = 2)) THEN 0 ELSE 9 END FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k)) IS NULL ORDER "
            + "BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|1 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) THEN 1 WHEN NOT (k"
            + " IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k)) THEN 0 ELSE 9 END FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k = ANY (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k)) IS NULL "
            + "ORDER BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|1 ;; 4|0 ;; 5|0",
            "SELECT id, CASE WHEN k = ANY (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) THEN 1 WHEN NOT"
            + " (k = ANY (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k)) THEN 0 ELSE 9 END FROM p ORDER BY"
            + " id");
        assertAnswer("",
            "SELECT id FROM p WHERE (k NOT IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k)) IS NULL "
            + "ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|1 ;; 5|1",
            "SELECT id, CASE WHEN k NOT IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k) THEN 1 WHEN "
            + "NOT (k NOT IN (SELECT k FROM w3 WHERE p.id > 0 AND k = p.k)) THEN 0 ELSE 9 END FROM p "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT p.k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 GROUP BY k HAVING p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT w3.k FROM w3 JOIN nn ON p.id > 0 AND nn.k = 1) FROM p ORDER BY id");
    }

    @Test
    public void settledFilterKinds() {
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
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT()"
            + " UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION"
            + " ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE pz (id INT, k INT)");
        engine.execute("INSERT INTO pz VALUES (0,0),(1,1),(2,2),(3,3),(4,NULL)");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k <> ALL (SELECT k FROM w3) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE TRUE) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k <> ALL (SELECT k FROM an) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE TRUE) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k <> ALL (SELECT k FROM wn) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM wn WHERE TRUE) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id < 10) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id BETWEEN 1 AND 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id <> 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id IN (1,2,3,4,5)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id >= 1 AND p.id <= 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id > 10) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.k > -1) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE COALESCE(p.k, 0) >= 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id = p.id) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id = p.id) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id = p.id) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id = p.id) FROM p ORDER BY id");
        assertAnswer("0|false ;; 1|true ;; 2|true ;; 3|false ;; 4|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE pz.id > 0) FROM pz ORDER BY id");
        assertAnswer("0|false ;; 1|true ;; 2|true ;; 3|false ;; 4|false",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE pz.id > 0) FROM pz ORDER BY id");
        assertAnswer("0|true ;; 1|false ;; 2|false ;; 3|null ;; 4|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE pz.id > 0) FROM pz ORDER BY id");
        assertAnswer("0|null ;; 1|true ;; 2|true ;; 3|null ;; 4|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE pz.id >= 0) FROM pz ORDER BY id");
        assertAnswer("0|false ;; 1|false ;; 2|false ;; 3|false ;; 4|false",
            "SELECT id, k IN (SELECT k FROM an WHERE pz.id > 0) FROM pz ORDER BY id");
        assertAnswer("0|false ;; 1|false ;; 2|false ;; 3|false ;; 4|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE pz.id > 0) FROM pz ORDER BY id");
        assertAnswer("0|true ;; 1|null ;; 2|null ;; 3|null ;; 4|null",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE pz.id > 0) FROM pz ORDER BY id");
        assertAnswer("0|null ;; 1|null ;; 2|null ;; 3|null ;; 4|null",
            "SELECT id, k IN (SELECT k FROM an WHERE pz.id >= 0) FROM pz ORDER BY id");
    }

    @Test
    public void foldedShapes() {
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
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT()"
            + " UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION"
            + " ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE pz (id INT, k INT)");
        engine.execute("INSERT INTO pz VALUES (0,0),(1,1),(2,2),(3,3),(4,NULL)");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k NOT IN (SELECT NULL::INT FROM nn) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|false",
            "SELECT id, k <> ALL (SELECT k FROM an WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 21",
            "SELECT id, k NOT IN (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE k IS NULL AND p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT NULL::INT FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM wn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, k > ALL (SELECT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, (k, k) IN (SELECT k, k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, (k, k) IN (SELECT k, k FROM w3) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, (k IN (SELECT k FROM w3 WHERE p.id > 0)) IS NULL FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, (k IN (SELECT k FROM w3 WHERE p.id > 0)) OR NULL FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, (k IN (SELECT k FROM w3 WHERE p.id > 0)) AND TRUE FROM p ORDER BY id");
        assertAnswer("1 ;; 4 ;; 5",
            "SELECT id FROM p WHERE (k IN (SELECT k FROM w3 WHERE p.id > 0)) IS NULL ORDER BY id");
        assertAnswer("1 ;; 4 ;; 5",
            "SELECT id FROM p WHERE NOT (k IN (SELECT k FROM w3 WHERE p.id > 0)) ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 AND p.id > -5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 AND p.id < 4) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 AND w3.k = p.k) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 LIMIT 5) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT DISTINCT k FROM w3 WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id > 0 UNION ALL SELECT k FROM nn) FROM p ORDER "
            + "BY id");
    }

    @Test
    public void negationsAndStatements() {
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
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT()"
            + " UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION"
            + " ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE pz (id INT, k INT)");
        engine.execute("INSERT INTO pz VALUES (0,0),(1,1),(2,2),(3,3),(4,NULL)");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k NOT IN (SELECT k FROM nn WHERE p.id > 0) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|true",
            "SELECT id, NOT (k NOT IN (SELECT k FROM an WHERE p.id > 0)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, NOT (k = ANY (SELECT k FROM w3 WHERE p.id > 0)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, NOT (k IN (SELECT k FROM an WHERE p.id > 0)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, NOT (k IN (SELECT k FROM w3)) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|null",
            "SELECT id, (k IN (SELECT k FROM w3 WHERE p.id > 0)) = FALSE FROM p ORDER BY id");
        assertAnswer("1|0 ;; 2|1 ;; 3|1 ;; 4|0 ;; 5|0",
            "SELECT id, IFF(k IN (SELECT k FROM w3 WHERE p.id > 0), 1, 0) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, NOT NOT (k IN (SELECT k FROM w3 WHERE p.id > 0)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, COALESCE(k IN (SELECT k FROM w3 WHERE p.id > 0), FALSE) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, k IN (SELECT IFF(p.id > 0, k, NULL) FROM w3) FROM p ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM p WHERE NOT (k IN (SELECT k FROM w3 WHERE p.id > 0)) OR id = 2 ORDER BY id");
        assertAnswer("",
            "SELECT k FROM p GROUP BY k HAVING (k IN (SELECT k FROM w3 WHERE MAX(p.id) > 0)) IS NULL "
            + "ORDER BY k");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE (k IN (SELECT k FROM w3 WHERE q.id > 0)) IS NULL");
        assertAnswer("1|-1 ;; 2|1 ;; 3|2 ;; 4|-1 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = -1 WHERE NOT (k IN (SELECT k FROM w3 WHERE q.id > 0))");
        assertAnswer("1|-1 ;; 2|1 ;; 3|2 ;; 4|-1 ;; 5|-1",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("DELETE FROM q WHERE (k = ANY (SELECT k FROM w3 WHERE q.id > 0)) IS NULL");
        assertAnswer("2|1 ;; 3|2",
            "SELECT id, k FROM q ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE q AS SELECT * FROM p");
        engine.execute("UPDATE q SET k = IFF((k IN (SELECT k FROM w3 WHERE q.id > 0)) IS NULL, -9, k)");
        assertAnswer("1|-9 ;; 2|1 ;; 3|2 ;; 4|-9 ;; 5|-9",
            "SELECT id, k FROM q ORDER BY id");
    }
}
