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

package dev.frostlake.functions.file;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stage that encrypts its files on the client — a named internal stage with no {@code ENCRYPTION}, and a table
 * stage — reports each file as the object it stores: {@code FL_GET_SIZE}, {@code LIST}'s size and a directory
 * table's {@code SIZE} are the content padded to the cipher's 16-byte block (a whole block added when the content
 * fills one), and {@code FL_GET_ETAG}, {@code LIST}'s md5, a directory table's {@code MD5} and {@code ETAG} and
 * {@code METADATA$FILE_CONTENT_KEY} are one digest of that upload — never the content's MD5, different for two files
 * of the same bytes, the same for every reader. A server-side encrypted stage reports the content's size and MD5.
 * Every expectation is live-verified, and the suite runs on both sides: its files arrive by PUT.
 */
public class ClientEncryptedStageObjectTest extends BaseDatabaseTest {

    private static final String HELLO = "hello world\nsecond line\n";

    private static final String HELLO_MD5 = "MD5('hello world\\nsecond line\\n')";

    /** Every column of the first row, as text. */
    private List<String> row(final String sql) {
        final Row first = engine.executeQuery(sql).getRows().get(0);
        final List<String> cells = new ArrayList<>();
        for (final Object value : first.getValues()) {
            cells.add(value == null ? null : value.toString());
        }
        return cells;
    }

    /** Every row's cells, as text, joined. */
    private String rows(final String sql) {
        final StringBuilder text = new StringBuilder();
        for (final Row each : engine.executeQuery(sql).getRows()) {
            if (text.length() > 0) {
                text.append(" ; ");
            }
            for (int i = 0; i < each.getValues().size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(each.getValues().get(i));
            }
        }
        return text.toString();
    }

    @Test
    public void aDefaultStagesFileSizeIsItsPaddedStoredSize() {
        engine.execute("CREATE STAGE st");
        stageLocalFile("st", "hello.txt", HELLO);
        stageLocalFile("st", "b32.txt", "0123456789abcdef0123456789abcdef");
        stageLocalFile("st", "one.txt", "x");
        stageLocalFile("st", "empty.txt", "");

        assertEquals(List.of("32", "48", "16", "16"), row("SELECT FL_GET_SIZE(TO_FILE('@st/hello.txt'))::VARCHAR,"
            + " FL_GET_SIZE(TO_FILE('@st/b32.txt'))::VARCHAR, FL_GET_SIZE(TO_FILE('@st/one.txt'))::VARCHAR,"
            + " FL_GET_SIZE(TO_FILE('@st/empty.txt'))::VARCHAR"));
    }

    @Test
    public void aDefaultStagesEtagIsAPerUploadDigest() {
        engine.execute("CREATE STAGE st");
        stageLocalFile("st", "hello.txt", HELLO);
        stageLocalFile("st", "twin.txt", HELLO);
        stageLocalFile("st", "empty.txt", "");

        assertEquals(List.of("false", "false", "32", "true", "true"), row("SELECT"
            + " (FL_GET_ETAG(TO_FILE('@st/hello.txt')) = " + HELLO_MD5 + ")::VARCHAR,"
            + " (FL_GET_ETAG(TO_FILE('@st/hello.txt')) = FL_GET_ETAG(TO_FILE('@st/twin.txt')))::VARCHAR,"
            + " LENGTH(FL_GET_ETAG(TO_FILE('@st/hello.txt')))::VARCHAR,"
            + " REGEXP_LIKE(FL_GET_ETAG(TO_FILE('@st/hello.txt')), '[0-9a-f]{32}')::VARCHAR,"
            + " (FL_GET_ETAG(TO_FILE('@st/hello.txt')) = FL_GET_ETAG(TO_FILE('@st/hello.txt')))::VARCHAR"));
        assertEquals(List.of("false"), row("SELECT (FL_GET_ETAG(TO_FILE('@st/empty.txt')) = MD5(''))::VARCHAR"));
    }

    @Test
    public void aDefaultStagesListingCarriesTheSameStoredObject() {
        engine.execute("CREATE STAGE st");
        stageLocalFile("st", "hello.txt", HELLO);
        stageLocalFile("st", "twin.txt", HELLO);

        engine.execute("LIST @st");
        assertEquals("st/hello.txt, 32, false, 32 ; st/twin.txt, 32, false, 32", rows("SELECT \"name\", \"size\","
            + " (\"md5\" = " + HELLO_MD5 + ")::VARCHAR, LENGTH(\"md5\") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " ORDER BY 1"));
        engine.execute("LIST @st PATTERN = '.*hello.*'");
        assertEquals(List.of("true"), row("SELECT (\"md5\" = FL_GET_ETAG(TO_FILE('@st/hello.txt')))::VARCHAR"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    @Test
    public void aTableStageReadsAsItsStoredObjects() {
        engine.execute("CREATE TABLE t (a INT)");
        stageLocalFile("%t", "hello.txt", HELLO);
        stageLocalFile("%t", "twin.txt", HELLO);

        assertEquals(List.of("32", "false", "false"), row("SELECT FL_GET_SIZE(TO_FILE('@%t/hello.txt'))::VARCHAR,"
            + " (FL_GET_ETAG(TO_FILE('@%t/hello.txt')) = " + HELLO_MD5 + ")::VARCHAR,"
            + " (FL_GET_ETAG(TO_FILE('@%t/hello.txt')) = FL_GET_ETAG(TO_FILE('@%t/twin.txt')))::VARCHAR"));
    }

    @Test
    public void aStagedFilesContentKeyIsItsStoredObjectsDigest() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE STAGE sse ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')");
        stageLocalFile("st", "twin.txt", HELLO);
        stageLocalFile("sse", "twin.txt", HELLO);

        assertEquals(List.of("false", "true"), row("SELECT (METADATA$FILE_CONTENT_KEY = " + HELLO_MD5 + ")::VARCHAR,"
            + " (METADATA$FILE_CONTENT_KEY = FL_GET_ETAG(TO_FILE('@st/twin.txt')))::VARCHAR FROM @st/twin.txt LIMIT 1"));
        assertEquals(List.of("true"), row("SELECT (METADATA$FILE_CONTENT_KEY = " + HELLO_MD5 + ")::VARCHAR"
            + " FROM @sse/twin.txt LIMIT 1"));
    }

    @Test
    public void aDefaultStagesDirectoryListsTheStoredObjects() {
        engine.execute("CREATE STAGE dst DIRECTORY = (ENABLE = TRUE)");
        stageLocalFile("dst", "hello.txt", HELLO);
        stageLocalFile("dst", "twin.txt", HELLO);
        engine.execute("ALTER STAGE dst REFRESH");

        assertEquals("hello.txt, 32, false, true ; twin.txt, 32, false, true", rows("SELECT RELATIVE_PATH, SIZE,"
            + " (MD5 = " + HELLO_MD5 + ")::VARCHAR, (MD5 = ETAG)::VARCHAR FROM DIRECTORY(@dst) ORDER BY 1"));
        assertEquals(List.of("2", "2"), row("SELECT COUNT(DISTINCT MD5), COUNT(DISTINCT ETAG) FROM DIRECTORY(@dst)"));
        assertEquals(List.of("true", "true"), row("SELECT (d.ETAG = FL_GET_ETAG(TO_FILE('@dst/hello.txt')))::VARCHAR,"
            + " (d.SIZE = FL_GET_SIZE(TO_FILE('@dst/hello.txt')))::VARCHAR FROM DIRECTORY(@dst) d"
            + " WHERE RELATIVE_PATH = 'hello.txt'"));
    }

    @Test
    public void aServerSideEncryptedStageReadsAsTheContent() {
        engine.execute("CREATE STAGE sse ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE') DIRECTORY = (ENABLE = TRUE)");
        stageLocalFile("sse", "hello.txt", HELLO);
        stageLocalFile("sse", "twin.txt", HELLO);
        engine.execute("ALTER STAGE sse REFRESH");

        assertEquals(List.of("24", "true", "true"), row("SELECT FL_GET_SIZE(TO_FILE('@sse/hello.txt'))::VARCHAR,"
            + " (FL_GET_ETAG(TO_FILE('@sse/hello.txt')) = " + HELLO_MD5 + ")::VARCHAR,"
            + " (FL_GET_ETAG(TO_FILE('@sse/hello.txt')) = FL_GET_ETAG(TO_FILE('@sse/twin.txt')))::VARCHAR"));
        engine.execute("LIST @sse");
        assertEquals("sse/hello.txt, 24, true ; sse/twin.txt, 24, true", rows("SELECT \"name\", \"size\","
            + " (\"md5\" = " + HELLO_MD5 + ")::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1"));
        assertEquals(List.of("24", "true", "true"), row("SELECT SIZE::VARCHAR, (MD5 = " + HELLO_MD5 + ")::VARCHAR,"
            + " (ETAG = " + HELLO_MD5 + ")::VARCHAR FROM DIRECTORY(@sse) WHERE RELATIVE_PATH = 'hello.txt'"));
    }
}
