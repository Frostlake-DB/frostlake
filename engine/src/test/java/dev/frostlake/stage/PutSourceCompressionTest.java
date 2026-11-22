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

package dev.frostlake.stage;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How a PUT reads the compression of the file it uploads (live-verified): by the file's first bytes, then by its
 * extension, a compressed file uploaded as it is under its own name; a named SOURCE_COMPRESSION taken as written; an
 * uncompressed file gzipped under AUTO_COMPRESS; xz refused file by file.
 */
public class PutSourceCompressionTest extends MeasuredCellsTestSupport {

    @TempDir
    Path local;

    /** A PUT of a local file: its text as the statement writes it. */
    private String put(final Path file, final String target) {
        return "PUT 'file://" + file.toAbsolutePath() + "' " + target;
    }

    /** Detection reads the first bytes before the name, and the name where the bytes mark nothing. */
    @Test
    public void autoDetectReadsTheBytesThenTheExtension() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE psc");
        assertAnswer("h.csv|h.csv.gz|8|28|NONE|GZIP|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "h.csv", "NONE"), "@psc/a/"));
        final Path bz2 = CompressedFixtures.write(local, "h.csv.bz2", "BZ2");
        final long bz2Size = Files.size(bz2);
        assertAnswer("h.csv.bz2|h.csv.bz2|" + bz2Size + "|" + bz2Size + "|BZIP2|BZIP2|UPLOADED|ENCRYPTED|",
            put(bz2, "@psc/a/"));
        final Path zst = CompressedFixtures.write(local, "hzst_noext", "ZSTD");
        final long zstSize = Files.size(zst);
        assertAnswer("hzst_noext|hzst_noext|" + zstSize + "|" + zstSize + "|ZSTD|ZSTD|UPLOADED|ENCRYPTED|",
            put(zst, "@psc/a/"));
        assertAnswer("hdef.txt|hdef.txt|16|16|DEFLATE|DEFLATE|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "hdef.txt", "DEFLATE"), "@psc/a/"));
        assertAnswer("hgz_noext|hgz_noext|28|28|GZIP|GZIP|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "hgz_noext", "GZIP"), "@psc/a/"));
        // A file named .gz that holds bzip2 is BZIP2; a raw deflate stream is known by its name alone.
        assertAnswer("bz.gz|bz.gz|" + bz2Size + "|" + bz2Size + "|BZIP2|BZIP2|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "bz.gz", "BZ2"), "@psc/a/"));
        final Path raw = CompressedFixtures.write(local, "h.csv.raw_deflate", "RAW_DEFLATE");
        final long rawSize = Files.size(raw);
        assertAnswer("h.csv.raw_deflate|h.csv.raw_deflate|" + rawSize + "|" + rawSize
            + "|RAW_DEFLATE|RAW_DEFLATE|UPLOADED|ENCRYPTED|", put(raw, "@psc/a/"));
        assertAnswer("hraw_noext|hraw_noext.gz|" + rawSize + "|30|NONE|GZIP|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "hraw_noext", "RAW_DEFLATE"), "@psc/a/"));
        // The extension alone, in any case, whatever the file holds.
        assertAnswer("x.BR|x.BR|8|8|BROTLI|BROTLI|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "x.BR", "NONE"), "@psc/a/"));
        assertAnswer("plain.gz|plain.gz|8|8|GZIP|GZIP|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "plain.gz", "NONE"), "@psc/a/"));
        Files.write(local.resolve("fake.parquet"), "PAR1xxxxPAR1".getBytes(StandardCharsets.US_ASCII));
        assertAnswer("fake.parquet|fake.parquet|12|12|PARQUET|PARQUET|UPLOADED|ENCRYPTED|",
            put(local.resolve("fake.parquet"), "@psc/a/"));
        assertAnswer("x.zip|x.zip.gz|8|28|NONE|GZIP|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "x.zip", "NONE"), "@psc/a/"));
        engine.execute("LIST @psc/a/");
        assertAnswer("psc/a/bz.gz ;; psc/a/fake.parquet ;; psc/a/h.csv.bz2 ;; psc/a/h.csv.gz ;; psc/a/h.csv.raw_deflate"
                + " ;; psc/a/hdef.txt ;; psc/a/hgz_noext ;; psc/a/hraw_noext.gz ;; psc/a/hzst_noext ;; psc/a/plain.gz"
                + " ;; psc/a/x.BR ;; psc/a/x.zip.gz",
            "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    /** A known compression the PUT does not upload answers an ERROR row for its file. */
    @Test
    public void xzIsKnownAndNotUploaded() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE psx");
        assertAnswer("x.xz|null|8|0|XZ|NONE|ERROR||Copy command does not support compression type XZ.",
            put(CompressedFixtures.write(local, "x.xz", "NONE"), "@psx/"));
        assertAnswer("x.lz|null|8|0|LZIP|NONE|ERROR||Copy command does not support compression type LZIP.",
            put(CompressedFixtures.write(local, "x.lz", "NONE"), "@psx/"));
    }

    /** A named SOURCE_COMPRESSION is taken as written, and only a known name is taken. */
    @Test
    public void aNamedSourceCompressionIsTakenAsWritten() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE psn");
        final Path plain = CompressedFixtures.write(local, "h.csv", "NONE");
        final Path gzip = CompressedFixtures.write(local, "h.csv.gz", "GZIP");
        assertAnswer("hgz_noext|hgz_noext|28|28|GZIP|GZIP|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "hgz_noext", "GZIP"), "@psn/a/ SOURCE_COMPRESSION = GZIP"));
        assertAnswer("h.csv|h.csv|8|8|BZIP2|BZIP2|UPLOADED|ENCRYPTED|", put(plain, "@psn/b/ SOURCE_COMPRESSION = bz2"));
        assertAnswer("h.csv|h.csv|8|8|ZSTD|ZSTD|UPLOADED|ENCRYPTED|", put(plain, "@psn/c/ SOURCE_COMPRESSION = 'zstd'"));
        assertAnswer("h.csv|h.csv|8|8|BROTLI|BROTLI|UPLOADED|ENCRYPTED|",
            put(plain, "@psn/d/ SOURCE_COMPRESSION = BROTLI"));
        // NONE gzips a gzip file again under AUTO_COMPRESS, and leaves it as it is without.
        assertAnswer("h.csv.gz|h.csv.gz.gz|28|43|NONE|GZIP|UPLOADED|ENCRYPTED|",
            put(gzip, "@psn/e/ SOURCE_COMPRESSION = NONE"));
        assertAnswer("h.csv.gz|h.csv.gz|28|28|NONE|NONE|UPLOADED|ENCRYPTED|",
            put(gzip, "@psn/f/ AUTO_COMPRESS = FALSE SOURCE_COMPRESSION = NONE"));
        assertAnswer("h.csv.gz|h.csv.gz|28|28|GZIP|GZIP|UPLOADED|ENCRYPTED|",
            put(gzip, "@psn/g/ AUTO_COMPRESS = FALSE"));
        assertAnswer("REFUSED Copy command does not recognize compression type AUTO.",
            put(plain, "@psn/h/ SOURCE_COMPRESSION = AUTO"));
        assertAnswer("REFUSED Copy command does not recognize compression type FOO.",
            put(plain, "@psn/h/ SOURCE_COMPRESSION = FOO"));
        assertAnswer("REFUSED Copy command does not support compression type XZ.",
            put(plain, "@psn/h/ SOURCE_COMPRESSION = XZ"));
    }
}
