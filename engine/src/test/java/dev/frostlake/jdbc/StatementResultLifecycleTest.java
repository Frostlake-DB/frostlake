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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a statement's result sets close, per the JDBC contract: {@code getMoreResults()} and
 * {@code getMoreResults(CLOSE_CURRENT_RESULT)} close the current one, {@code KEEP_CURRENT_RESULT} leaves it
 * readable, {@code CLOSE_ALL_RESULTS} closes every one kept so far, and closing the statement closes its result
 * set (live-verified through the account's driver).
 */
public class StatementResultLifecycleTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
    }

    @Override
    protected void teardownTest() throws SQLException {
        try (Statement reset = connection.createStatement()) {
            reset.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }

    /** The first value of a result set's first row. */
    private static int first(final ResultSet rs) throws SQLException {
        assertTrue(rs.next());
        return rs.getInt(1);
    }

    /** Moving on closes the current result set. */
    @Test
    public void getMoreResultsClosesTheCurrentResultSet() throws SQLException {
        try (Statement st = connection.createStatement()) {
            assertTrue(st.execute("SELECT 1; SELECT 2"));
            final ResultSet a = st.getResultSet();
            assertTrue(st.getMoreResults());
            assertTrue(a.isClosed());
            assertEquals(2, first(st.getResultSet()));
        }
    }

    /** CLOSE_CURRENT_RESULT is what the argument-less call does. */
    @Test
    public void closeCurrentResultClosesIt() throws SQLException {
        try (Statement st = connection.createStatement()) {
            assertTrue(st.execute("SELECT 1; SELECT 2"));
            final ResultSet a = st.getResultSet();
            assertTrue(st.getMoreResults(Statement.CLOSE_CURRENT_RESULT));
            assertTrue(a.isClosed());
        }
    }

    /** KEEP_CURRENT_RESULT leaves the current result set open and readable. */
    @Test
    public void keepCurrentResultLeavesItReadable() throws SQLException {
        try (Statement st = connection.createStatement()) {
            assertTrue(st.execute("SELECT 1; SELECT 2"));
            final ResultSet a = st.getResultSet();
            assertTrue(st.getMoreResults(Statement.KEEP_CURRENT_RESULT));
            assertFalse(a.isClosed());
            assertEquals(1, first(a));
            assertEquals(2, first(st.getResultSet()));
        }
    }

    /**
     * CLOSE_ALL_RESULTS closes the current result set and every one kept before it, as JDBC defines it. The
     * account's driver closes only the current one and leaves a kept one open until the statement closes.
     */
    @Test
    public void closeAllResultsClosesTheKeptOnesToo() throws SQLException {
        try (Statement st = connection.createStatement()) {
            assertTrue(st.execute("SELECT 1; SELECT 2; SELECT 3"));
            final ResultSet a = st.getResultSet();
            assertTrue(st.getMoreResults(Statement.KEEP_CURRENT_RESULT));
            final ResultSet b = st.getResultSet();
            assertTrue(st.getMoreResults(Statement.CLOSE_ALL_RESULTS));
            assertEquals(!isSnowflakeDriver(), a.isClosed());
            assertTrue(b.isClosed());
            assertEquals(3, first(st.getResultSet()));
        }
    }

    /** Closing the statement closes its result set. */
    @Test
    public void closingTheStatementClosesItsResultSet() throws SQLException {
        final Statement st = connection.createStatement();
        final ResultSet rs = st.executeQuery("SELECT 1");
        st.close();
        assertTrue(rs.isClosed());
    }

    /** Closing the statement closes a result set kept by KEEP_CURRENT_RESULT as well. */
    @Test
    public void closingTheStatementClosesAKeptResultSet() throws SQLException {
        final Statement st = connection.createStatement();
        assertTrue(st.execute("SELECT 1; SELECT 2"));
        final ResultSet a = st.getResultSet();
        assertTrue(st.getMoreResults(Statement.KEEP_CURRENT_RESULT));
        final ResultSet b = st.getResultSet();
        st.close();
        assertTrue(a.isClosed());
        assertTrue(b.isClosed());
    }
}
