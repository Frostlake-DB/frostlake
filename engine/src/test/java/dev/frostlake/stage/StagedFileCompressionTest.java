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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A staged file is read by its file format's COMPRESSION (live-verified): AUTO reads gzip and zstd by their first
 * bytes and bzip2 named .bz2, and reads anything else as it is; GZIP and DEFLATE read a gzip and a zlib stream
 * alike; BZ2, ZSTD and RAW_DEFLATE their own; NONE the bytes as they are. A file the named codec cannot read is
 * refused in the account's words.
 */
public class StagedFileCompressionTest extends MeasuredCellsTestSupport {

    private static final String ROWS = "a|1 ;; b|2";

    @TempDir
    Path local;

    /** Uploads the fixture text compressed by a codec, stored as it is under the name. */
    private void stage(final String name, final String codec) throws IOException {
        engine.execute("PUT 'file://" + CompressedFixtures.write(local, name, codec).toAbsolutePath()
            + "' @sfc AUTO_COMPRESS = FALSE SOURCE_COMPRESSION = NONE");
    }

    private String read(final String file, final String compression) {
        return answer("SELECT $1, $2 FROM @sfc/" + file + " (FILE_FORMAT => 'f_" + compression + "')");
    }

    private static String undecodable(final String file, final String codec, final String cause) {
        return "REFUSED Invalid data encountered during decompression for file: '" + file
            + "',compression type used: '" + codec + "', cause: '" + cause + "'";
    }

    @Test
    public void eachCompressionReadsItsOwnFiles() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE sfc");
        for (final String codec : new String[] {"AUTO", "GZIP", "BZ2", "ZSTD", "DEFLATE", "RAW_DEFLATE", "NONE"}) {
            engine.execute("CREATE OR REPLACE FILE FORMAT f_" + codec + " TYPE = CSV COMPRESSION = " + codec);
        }
        stage("gzf.gz", "GZIP");
        stage("bzf.bz2", "BZ2");
        stage("zsf.zst", "ZSTD");
        stage("dff.deflate", "DEFLATE");
        stage("rdf.raw_deflate", "RAW_DEFLATE");
        stage("bzn", "BZ2");
        stage("zsn", "ZSTD");
        stage("pln.gz", "NONE");
        stage("gzw.bz2", "GZIP");

        // AUTO: gzip and zstd by their bytes, bzip2 by its bytes and its name together.
        assertEquals(ROWS, read("gzf.gz", "AUTO"));
        assertEquals(ROWS, read("bzf.bz2", "AUTO"));
        assertEquals(ROWS, read("zsf.zst", "AUTO"));
        assertEquals(ROWS, read("zsn", "AUTO"));
        assertEquals(ROWS, read("gzw.bz2", "AUTO"));
        assertEquals(ROWS, read("pln.gz", "AUTO"));
        final String bzipAsText = read("bzn", "AUTO");
        assertTrue(bzipAsText.startsWith("REFUSED Invalid UTF8 detected in string 'BZh9"), bzipAsText);

        // Each named codec reads its own.
        assertEquals(ROWS, read("gzf.gz", "GZIP"));
        assertEquals(ROWS, read("dff.deflate", "GZIP"));
        assertEquals(ROWS, read("gzf.gz", "DEFLATE"));
        assertEquals(ROWS, read("dff.deflate", "DEFLATE"));
        assertEquals(ROWS, read("bzn", "BZ2"));
        assertEquals(ROWS, read("zsn", "ZSTD"));
        assertEquals(ROWS, read("rdf.raw_deflate", "RAW_DEFLATE"));
        assertEquals(ROWS, read("pln.gz", "NONE"));

        // And refuses the rest by the codec's own cause.
        assertEquals(undecodable("bzf.bz2", "GZIP", "data error"), read("bzf.bz2", "GZIP"));
        assertEquals(undecodable("pln.gz", "GZIP", "data error"), read("pln.gz", "GZIP"));
        assertEquals(undecodable("gzf.gz", "BZ2", "data error magic"), read("gzf.gz", "BZ2"));
        assertEquals(undecodable("gzf.gz", "ZSTD", "Unknown frame descriptor"), read("gzf.gz", "ZSTD"));
        assertEquals(undecodable("zsf.zst", "DEFLATE", "data error"), read("zsf.zst", "DEFLATE"));
        assertEquals(undecodable("dff.deflate", "RAW_DEFLATE", "data error"), read("dff.deflate", "RAW_DEFLATE"));
        final String gzipAsText = read("gzf.gz", "NONE");
        assertTrue(gzipAsText.startsWith("REFUSED Invalid UTF8 detected in string '"), gzipAsText);
    }

    /** A JSON file is decompressed by its format's COMPRESSION as a CSV file is. */
    @Test
    public void aJsonFileIsDecompressedAlike() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE sfc");
        engine.execute("CREATE OR REPLACE FILE FORMAT j_bz2 TYPE = JSON COMPRESSION = BZ2");
        engine.execute("CREATE OR REPLACE FILE FORMAT j_zstd TYPE = JSON COMPRESSION = 'zstd'");
        for (final String codec : new String[] {"BZ2", "ZSTD"}) {
            engine.execute("PUT 'file://" + CompressedFixtures.write(local, "j_" + codec, codec, "{\"a\":1}\n")
                .toAbsolutePath() + "' @sfc/j/ AUTO_COMPRESS = FALSE SOURCE_COMPRESSION = NONE");
        }
        assertAnswer("1", "SELECT $1:a FROM @sfc/j/j_BZ2 (FILE_FORMAT => 'j_bz2')");
        assertAnswer("1", "SELECT $1:a FROM @sfc/j/j_ZSTD (FILE_FORMAT => 'j_zstd')");
        assertAnswer(undecodable("j_ZSTD", "BZ2", "data error magic"),
            "SELECT $1:a FROM @sfc/j/j_ZSTD (FILE_FORMAT => 'j_bz2')");
        assertAnswer(undecodable("j_BZ2", "ZSTD", "Unknown frame descriptor"),
            "SELECT $1:a FROM @sfc/j/j_BZ2 (FILE_FORMAT => 'j_zstd')");
    }
}
