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
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The refusals of a statement's own text and of an unsupported feature carry the account driver's code and state
 * (live-verified), and a block handler reads the same pair: a property written twice 1424 / 22023, a NOORDER
 * sequence altered to ORDER 2008 / 55000, a block variable declared twice 92210 / P0000, named arguments that match
 * no signature 944, one named twice 942, a positional argument after a named one 953 and named arguments to a
 * function that takes none 945 (all 42601), a clustering function over a table without clustering keys 5 / XX000,
 * and an unsupported feature 2 / 0A000, or 4 / 0A000 where the sentence names the form to use instead.
 */
public class CompileRefusalCodesTest extends BaseJdbcTest {

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

    /** The SQLCODE and SQLSTATE a block's handler reads for a statement it runs. */
    private String handled(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery("EXECUTE IMMEDIATE $$BEGIN " + sql
                + "; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END;$$")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void aPropertyWrittenTwiceAndASequencesOrder() throws SQLException {
        assertEquals("1424|22023|SQL compilation error:\nduplicate property 'SEQUENCE_INCREMENT';",
            refusal("CREATE SEQUENCE cr_s2 INCREMENT = 2 INCREMENT = 3"));
        assertEquals("1424|22023|SQL compilation error:\nduplicate property 'COMMENT';",
            refusal("CREATE TABLE cr_t2 (x INT) COMMENT = 'a' COMMENT = 'b'"));
        statement.execute("CREATE SEQUENCE cr_s1");
        assertEquals("1424|22023|SQL compilation error:\nduplicate property 'SEQUENCE_INCREMENT';",
            refusal("ALTER SEQUENCE cr_s1 SET INCREMENT = 2 INCREMENT = 3"));
        assertEquals("2008|55000|SQL compilation error:\nInvalid operation ALTER NOORDER SEQUENCE TO ORDER",
            refusal("ALTER SEQUENCE cr_s1 SET ORDER"));
        assertEquals("2008/55000", handled("ALTER SEQUENCE cr_s1 SET ORDER"));
    }

    @Test
    public void aVariableDeclaredTwice() throws SQLException {
        assertEquals("92210|P0000|SQL compilation error: error line 1 at position 15\n Variable with name 'X' "
            + "declared twice.", refusal("EXECUTE IMMEDIATE $$DECLARE x INT; x INT; BEGIN RETURN 1; END;$$"));
        assertEquals("92210|P0000|SQL compilation error: error line 1 at position 21\n Variable with name 'X' "
            + "declared twice.", refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN LET x := 1; LET x := 2; RETURN x;"
            + " END;$$"));
        assertEquals("92210/P0000",
            handled("EXECUTE IMMEDIATE 'DECLARE x INT; x INT; BEGIN RETURN 1; END;'"));
    }

    @Test
    public void namedArguments() throws SQLException {
        statement.execute("CREATE FUNCTION cr_f(a INT, b INT) RETURNS INT AS 'a + b'");
        assertEquals("944|42601|SQL compilation error: error line 1 at position 7\nnamed arguments [A, C] do not match "
            + "any signature for function CR_F", refusal("SELECT cr_f(a => 1, c => 2)"));
        assertEquals("944|42601|SQL compilation error: error line 1 at position 7\nnamed arguments [A] do not match "
            + "any signature for function CR_F", refusal("SELECT cr_f(a => 1)"));
        assertEquals("942|42601|SQL compilation error: error line 1 at position 25\nduplicate named argument A for "
            + "function CR_F", refusal("SELECT cr_f(a => 1, a => 2)"));
        assertEquals("953|42601|SQL compilation error: error line 1 at position 20\npositional argument cannot follow "
            + "named argument in function call 'CR_F'", refusal("SELECT cr_f(a => 1, 2)"));
        assertEquals("945|42601|SQL compilation error: error line 1 at position 7\nfunction UPPER does not support "
            + "named arguments", refusal("SELECT UPPER(str => 'a')"));
        assertEquals("944/42601", handled("SELECT cr_f(a => 1, c => 2)"));
    }

    @Test
    public void unsupportedFeaturesAndClustering() throws SQLException {
        statement.execute("CREATE TABLE cr_t (x INT)");
        assertEquals("2|0A000|Unsupported feature 'VARIANCE(DISTINCT)'.",
            refusal("SELECT VARIANCE(DISTINCT x) FROM cr_t"));
        assertEquals("2|0A000|Unsupported feature 'TOK_STAR'.", refusal("SELECT COUNT(DISTINCT *) FROM cr_t"));
        assertEquals("2|0A000|Unsupported feature 'MINUS ALL'.", refusal("SELECT 1 MINUS ALL SELECT 1"));
        assertEquals("2|0A000|Unsupported feature 'Alter Column Set Default'.",
            refusal("ALTER TABLE cr_t ALTER COLUMN x SET DEFAULT 1"));
        assertEquals("2|0A000|Unsupported feature 'TAG'.", refusal("DESCRIBE TAG cr_tg"));
        assertEquals("4|0A000|Unsupported feature 'SHOW REGIONS IN ACCOUNT LIMIT 1'; use 'SHOW REGIONS IN DATA "
            + "EXCHANGE <exchange_name>' instead.", refusal("SHOW REGIONS IN ACCOUNT LIMIT 1"));
        assertEquals("5|XX000|Invalid clustering keys or table CR_T is not clustered",
            refusal("SELECT SYSTEM$CLUSTERING_RATIO('cr_t')"));
        assertEquals("2/0A000", handled("SELECT VARIANCE(DISTINCT x) FROM cr_t"));
    }
}
