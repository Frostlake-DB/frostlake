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
 * The refusals of a file format's ALTER carry the account driver's code and state (live-verified): a change of the
 * format's type is 2134 / 42601 and an option the type does not take 2135 / 42601.
 */
public class FileFormatRefusalCodesTest extends BaseJdbcTest {

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
    public void aTypeChangeAndAnOptionOfAnotherType() throws SQLException {
        statement.execute("CREATE FILE FORMAT frc_ff TYPE = CSV");
        assertEquals("2134|42601|SQL compilation error:\nFile format type cannot be changed.",
            refusal("ALTER FILE FORMAT frc_ff SET TYPE = JSON"));
        assertEquals("2135|42601|SQL compilation error:\nOption STRIP_OUTER_ARRAY is not valid for file format type CSV.",
            refusal("ALTER FILE FORMAT IF EXISTS frc_ff SET STRIP_OUTER_ARRAY = TRUE"));
    }
}
