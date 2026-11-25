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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A stream on a view reads its view by name: where the view's names come to read other tables than the ones the
 * stream is bound to while those are still there - a rename with a new table under the old name, a swap, or UNDROP
 * bringing back the original under the name of the table that replaced it - the stream's next read or
 * SYSTEM$STREAM_HAS_DATA re-binds it to the tables the names read. It then lists their changes since its offset, is
 * refused where one of them tracked no changes over that range, and SHOW STREAMS lists them; what SHOW STREAMS lists
 * before that read is unchanged. A stale stream holding changes answers SYSTEM$STREAM_HAS_DATA by whether the table
 * that replaced its own holds rows (all live-verified).
 */
public class ViewStreamReadsByNameTest extends MeasuredCellsTestSupport {

    private static final String GAP_ON_T = "REFUSED SQL compilation error: Change tracking is not enabled or has been "
        + "missing for the time range requested on table 'TEST_DB.RN.T'.";

    private void assertListed(final String expected, final String stream) {
        engine.execute("SHOW STREAMS LIKE '" + stream + "' IN SCHEMA rn");
        assertAnswer(expected, "SELECT \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    /** Re-binds the stream {@code vs} on {@code rn.v} to a new {@code rn.t}, then renames that and undrops the first. */
    private void reboundThenUndropped(final boolean insertIntoOriginal) {
        engine.execute("CREATE OR REPLACE SCHEMA rn");
        engine.execute("CREATE TABLE rn.t (a INT)");
        if (insertIntoOriginal) {
            engine.execute("INSERT INTO rn.t VALUES (1)");
        }
        engine.execute("CREATE VIEW rn.v AS SELECT a FROM rn.t");
        engine.execute("CREATE STREAM rn.vs ON VIEW rn.v");
        engine.execute("DROP TABLE rn.t");
        engine.execute("CREATE TABLE rn.t (a INT)");
        engine.execute("INSERT INTO rn.t VALUES (2)");
        assertAnswer(GAP_ON_T, "SELECT a FROM rn.vs");
        assertListed("TEST_DB.RN.T|false", "VS");
        engine.execute("ALTER TABLE rn.t RENAME TO rn.t_new");
        assertListed("TEST_DB.RN.T_NEW|false", "VS");
        engine.execute("UNDROP TABLE rn.t");
        assertListed("TEST_DB.RN.T_NEW|false", "VS");
    }

    @Test
    public void anUndroppedOriginalIsReadThroughTheView() {
        reboundThenUndropped(true);
        assertAnswer("", "SELECT a FROM rn.vs");
        assertListed("TEST_DB.RN.T|false", "VS");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('rn.vs')");
        assertAnswer("1", "INSERT INTO rn.t VALUES (3)");
        assertAnswer("3", "SELECT a FROM rn.vs");
        assertAnswer("1", "INSERT INTO rn.t_new VALUES (4)");
        assertAnswer("3|INSERT|false", "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM rn.vs");
        assertListed("TEST_DB.RN.T|false", "VS");
        engine.execute("CREATE TABLE rn.sink (a INT)");
        assertAnswer("1", "INSERT INTO rn.sink SELECT a FROM rn.vs");
        assertAnswer("", "SELECT a FROM rn.vs");
        assertAnswer("1", "INSERT INTO rn.t VALUES (5)");
        engine.execute("UPDATE rn.t SET a = 6 WHERE a = 1");
        assertAnswer("1|DELETE|true ;; 5|INSERT|false ;; 6|INSERT|true",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM rn.vs ORDER BY 2, 1");
        engine.execute("DELETE FROM rn.t_new");
        assertAnswer("1 ;; 5 ;; 6", "SELECT a FROM rn.vs ORDER BY 1");
    }

    @Test
    public void changesToTheOriginalBeforeTheReadAreListed() {
        reboundThenUndropped(true);
        assertAnswer("1", "INSERT INTO rn.t VALUES (3)");
        assertAnswer("1", "INSERT INTO rn.t_new VALUES (4)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('rn.vs')");
        assertListed("TEST_DB.RN.T|false", "VS");
        assertAnswer("3|INSERT", "SELECT a, METADATA$ACTION FROM rn.vs");
    }

    @Test
    public void hasDataRebindsOverTheOriginalAlone() {
        reboundThenUndropped(false);
        assertAnswer("1", "INSERT INTO rn.t_new VALUES (4)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('rn.vs')");
        assertListed("TEST_DB.RN.T|false", "VS");
        assertAnswer("", "SELECT a FROM rn.vs");
        assertAnswer("1", "INSERT INTO rn.t_new VALUES (5)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('rn.vs')");
        assertAnswer("", "SELECT a FROM rn.vs");
    }

    @Test
    public void aConsumingReadAfterTheUndropTakesTheOriginalsChanges() {
        reboundThenUndropped(false);
        assertAnswer("1", "INSERT INTO rn.t VALUES (3)");
        assertAnswer("1", "INSERT INTO rn.t_new VALUES (4)");
        engine.execute("CREATE TABLE rn.sink (a INT)");
        assertAnswer("1", "INSERT INTO rn.sink SELECT a FROM rn.vs");
        assertAnswer("3", "SELECT a FROM rn.sink");
        assertAnswer("", "SELECT a FROM rn.vs");
        assertAnswer("1", "INSERT INTO rn.t_new VALUES (5)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('rn.vs')");
        assertAnswer("1", "DELETE FROM rn.t");
        assertAnswer("3|DELETE", "SELECT a, METADATA$ACTION FROM rn.vs");
    }

    @Test
    public void aRenameWithANewTableUnderTheNameRebinds() {
        engine.execute("CREATE OR REPLACE SCHEMA rn");
        engine.execute("CREATE TABLE rn.t (a INT)");
        engine.execute("CREATE VIEW rn.v AS SELECT a FROM rn.t");
        engine.execute("CREATE STREAM rn.vs ON VIEW rn.v");
        engine.execute("ALTER TABLE rn.t RENAME TO rn.t_old");
        engine.execute("CREATE TABLE rn.t (a INT)");
        engine.execute("INSERT INTO rn.t VALUES (2)");
        engine.execute("INSERT INTO rn.t_old VALUES (7)");
        assertAnswer(GAP_ON_T, "SELECT a FROM rn.vs");
        assertListed("TEST_DB.RN.T|false", "VS");
        assertAnswer(GAP_ON_T, "SELECT a FROM rn.vs");
    }

    @Test
    public void aSwapRebinds() {
        engine.execute("CREATE OR REPLACE SCHEMA rn");
        engine.execute("CREATE TABLE rn.p (a INT)");
        engine.execute("CREATE TABLE rn.q (a INT)");
        engine.execute("CREATE VIEW rn.pv AS SELECT a FROM rn.p");
        engine.execute("CREATE STREAM rn.ps ON VIEW rn.pv");
        engine.execute("INSERT INTO rn.q VALUES (1)");
        engine.execute("ALTER TABLE rn.p SWAP WITH rn.q");
        final String gapOnP = "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for "
            + "the time range requested on table 'TEST_DB.RN.P'.";
        assertAnswer(gapOnP, "SELECT a FROM rn.ps");
        engine.execute("INSERT INTO rn.p VALUES (2)");
        engine.execute("INSERT INTO rn.q VALUES (3)");
        assertAnswer(gapOnP, "SELECT a FROM rn.ps");
        assertListed("TEST_DB.RN.P|false", "PS");
    }

    @Test
    public void aStreamHoldingChangesIsListedOnItsOwnTableAgainOnceRead() {
        engine.execute("CREATE OR REPLACE SCHEMA rn");
        engine.execute("CREATE TABLE rn.x (a INT)");
        engine.execute("CREATE VIEW rn.y AS SELECT a FROM rn.x");
        engine.execute("CREATE STREAM rn.ys ON VIEW rn.y");
        engine.execute("INSERT INTO rn.x VALUES (1)");
        engine.execute("DROP TABLE rn.x");
        engine.execute("CREATE TABLE rn.x (a INT)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('rn.ys')");
        engine.execute("INSERT INTO rn.x VALUES (2)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('rn.ys')");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
            + "range requested on table 'TEST_DB.RN.X'.", "SELECT a FROM rn.ys");
        engine.execute("ALTER TABLE rn.x RENAME TO rn.x_new");
        engine.execute("UNDROP TABLE rn.x");
        assertListed("TEST_DB.RN.X_NEW|false", "YS");
        // Its answer right after the UNDROP is not steady on the account; the call is what re-lists the stream.
        engine.execute("SELECT SYSTEM$STREAM_HAS_DATA('rn.ys')");
        assertListed("TEST_DB.RN.X|false", "YS");
        assertAnswer("1", "SELECT a FROM rn.ys");
        assertAnswer("1", "INSERT INTO rn.x VALUES (3)");
        assertAnswer("1", "INSERT INTO rn.x_new VALUES (4)");
        assertAnswer("1 ;; 3", "SELECT a FROM rn.ys ORDER BY 1");
        assertListed("TEST_DB.RN.X|false", "YS");
    }

    @Test
    public void aStaleStreamHoldingChangesAnswersHasDataByTheReplacementsRows() {
        engine.execute("CREATE OR REPLACE SCHEMA rn");
        engine.execute("CREATE TABLE rn.x (a INT)");
        engine.execute("CREATE VIEW rn.y AS SELECT a FROM rn.x");
        engine.execute("CREATE STREAM rn.ys ON VIEW rn.y");
        engine.execute("INSERT INTO rn.x VALUES (1)");
        engine.execute("DROP TABLE rn.x");
        engine.execute("CREATE TABLE rn.x (a INT)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('rn.ys')");
        engine.execute("INSERT INTO rn.x VALUES (2)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('rn.ys')");
        engine.execute("DELETE FROM rn.x");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('rn.ys')");
        engine.execute("CREATE TABLE rn.p (a INT)");
        engine.execute("CREATE VIEW rn.pv AS SELECT a FROM rn.p");
        engine.execute("CREATE STREAM rn.ps ON VIEW rn.pv");
        engine.execute("INSERT INTO rn.p VALUES (1)");
        engine.execute("DROP TABLE rn.p");
        engine.execute("CREATE TABLE rn.p AS SELECT 5 AS a");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('rn.ps')");
    }
}
