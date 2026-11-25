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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseJdbcTest;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The refusals of a column's DEFAULT and of a value written to a column carry the account driver's code and state
 * (live-verified): a DEFAULT of a type its column does not take 2262 / 22000, a DEFAULT an added column cannot be
 * given 2263 / 22000, a DEFAULT on a BINARY column 2260 / 22000, an expression of a type its column does not take
 * 2023 / 22000, a NOT NULL column added to a table holding rows 2200 / 22023, an integer literal past NUMBER's range
 * 1067 / 42601 and a DEFAULT an existing column would be given 2 / 0A000.
 */
public class ColumnDefaultRefusalCodesTest extends BaseJdbcTest {

    /** The code, the state and the sentence a statement is refused with. */
    private String refusal(final String sql) {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
        return refused.getErrorCode() + "|" + refused.getSQLState() + "|" + refused.getMessage();
    }

    @Test
    public void aDefaultsType() {
        assertEquals("2262|22000|SQL compilation error:\nDefault value data type does not match data type for column C",
            refusal("CREATE TABLE cd_a (c INT DEFAULT '5')"));
        assertEquals("2262|22000|SQL compilation error:\nDefault value data type does not match data type for column C",
            refusal("CREATE TABLE cd_b (c INT DEFAULT 2.5::FLOAT)"));
        assertEquals("2260|22000|SQL compilation error: Default values are not allowed on 'BINARY' columns.",
            refusal("CREATE TABLE cd_c (c BINARY DEFAULT 'ab')"));
        assertEquals("1067|42601|SQL compilation error: Error line 1 at position 33\n"
            + "Integer literal is out of representable range: 999999999999999999999999999999999999999",
            refusal("CREATE TABLE cd_d (c INT DEFAULT 999999999999999999999999999999999999999)"));
    }

    @Test
    public void anAddedColumnAndAWrittenValue() throws SQLException {
        statement.execute("CREATE TABLE cd_t (id INT, a VARCHAR DEFAULT 'x', b BOOLEAN)");
        statement.execute("INSERT INTO cd_t (id) VALUES (1)");
        assertEquals("2023|22000|SQL compilation error:\nExpression type does not match column data type, expecting "
            + "BOOLEAN but got VARCHAR(4) for column Z", refusal("ALTER TABLE cd_t ADD COLUMN z BOOLEAN DEFAULT 'true'"));
        assertEquals("2263|22000|SQL compilation error:\nInvalid column default expression [CAST(2.5 AS FLOAT)]",
            refusal("ALTER TABLE cd_t ADD COLUMN q INT DEFAULT 2.5::FLOAT"));
        assertEquals("2200|22023|SQL compilation error: Non-nullable column 'W' cannot be added to non-empty table "
            + "'CD_T' unless it has a non-null default value.", refusal("ALTER TABLE cd_t ADD COLUMN w INT NOT NULL"));
        assertEquals("2023|22000|SQL compilation error:\nExpression type does not match column data type, expecting "
            + "BOOLEAN but got DATE for column B", refusal("UPDATE cd_t SET b = CURRENT_DATE()"));
        assertEquals("2|0A000|Unsupported feature 'Alter Column Set Default'.",
            refusal("CREATE OR ALTER TABLE cd_t (id INT, a VARCHAR DEFAULT 'q', b BOOLEAN)"));
    }
}
