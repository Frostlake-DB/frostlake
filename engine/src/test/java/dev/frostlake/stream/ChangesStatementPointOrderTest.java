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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A CHANGES clause's {@code STATEMENT} point is the statement's place among the table's changes, not its
 * millisecond: a row inserted right after the statement is a change since it, however fast the two ran.
 */
public class ChangesStatementPointOrderTest extends BaseDatabaseTest {

    /** Many rounds on the engine, where statements share a millisecond; a few against an account. */
    private int rounds() {
        return isLiveSnowflake() ? 2 : 150;
    }

    private String only(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.next() ? String.valueOf(rs.getValue(0)) : "(0 rows)";
    }

    @Test
    public void aChangeRightAfterTheStatementIsReadSinceIt() {
        engine.execute("CREATE OR REPLACE TABLE cs (id INT) CHANGE_TRACKING = TRUE");
        for (int round = 0; round < rounds(); round++) {
            engine.execute("INSERT INTO cs VALUES (" + (2 * round) + ")");
            engine.execute("SET q1 = LAST_QUERY_ID()");
            engine.execute("INSERT INTO cs VALUES (" + (2 * round + 1) + ")");
            assertEquals(String.valueOf(2 * round + 1),
                only("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM cs CHANGES(INFORMATION => DEFAULT) "
                    + "AT(STATEMENT => $q1)"), "round " + round);
        }
    }

    @Test
    public void aStatementReadBeforeItselfKeepsItsOwnChange() {
        engine.execute("CREATE OR REPLACE TABLE cb (id INT) CHANGE_TRACKING = TRUE");
        for (int round = 0; round < rounds(); round++) {
            engine.execute("INSERT INTO cb VALUES (" + (2 * round) + ")");
            engine.execute("INSERT INTO cb VALUES (" + (2 * round + 1) + ")");
            engine.execute("SET q2 = LAST_QUERY_ID()");
            assertEquals(String.valueOf(2 * round + 1),
                only("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM cb CHANGES(INFORMATION => DEFAULT) "
                    + "BEFORE(STATEMENT => $q2)"), "round " + round);
        }
    }

    @Test
    public void anEndStatementClosesTheWindowAtItsPlace() {
        engine.execute("CREATE OR REPLACE TABLE ce (id INT) CHANGE_TRACKING = TRUE");
        for (int round = 0; round < rounds(); round++) {
            engine.execute("INSERT INTO ce VALUES (" + (3 * round) + ")");
            engine.execute("SET q1 = LAST_QUERY_ID()");
            engine.execute("INSERT INTO ce VALUES (" + (3 * round + 1) + ")");
            engine.execute("SET q2 = LAST_QUERY_ID()");
            engine.execute("INSERT INTO ce VALUES (" + (3 * round + 2) + ")");
            assertEquals(String.valueOf(3 * round + 1),
                only("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM ce CHANGES(INFORMATION => DEFAULT) "
                    + "AT(STATEMENT => $q1) END(STATEMENT => $q2)"), "round " + round);
        }
    }
}
