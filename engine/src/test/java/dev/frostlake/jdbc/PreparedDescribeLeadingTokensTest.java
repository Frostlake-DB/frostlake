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

import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code PreparedStatement.getMetaData()} describes a query whatever comes before its first keyword: a line
 * comment, a block comment or an opening parenthesis (live-verified through the account's driver).
 */
public class PreparedDescribeLeadingTokensTest extends BaseJdbcTest {

    /** The described columns of a prepared query, their labels joined with commas. */
    private String described(final String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            final ResultSetMetaData md = ps.getMetaData();
            assertNotNull(md, sql);
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.append(i == 1 ? "" : ",").append(md.getColumnLabel(i));
            }
            return out.toString();
        }
    }

    /** A plain query is described. */
    @Test
    public void aPlainQueryIsDescribed() throws SQLException {
        assertEquals("X", described("SELECT ? AS x"));
    }

    /** A leading line comment does not hide the query. */
    @Test
    public void aLeadingLineCommentIsSkipped() throws SQLException {
        assertEquals("X", described("-- c\nSELECT ? AS x"));
    }

    /** A leading block comment does not hide the query. */
    @Test
    public void aLeadingBlockCommentIsSkipped() throws SQLException {
        assertEquals("X", described("/* c */ SELECT ? AS x"));
    }

    /** A parenthesized query is described. */
    @Test
    public void aParenthesizedQueryIsDescribed() throws SQLException {
        assertEquals("X", described("(SELECT ? AS x)"));
    }

    /** A leading comment before WITH does not hide the query either. */
    @Test
    public void aCommentBeforeWithIsSkipped() throws SQLException {
        assertEquals("Y", described("/* c */ WITH t AS (SELECT 1 AS y) SELECT y FROM t WHERE y = ?"));
    }
}
