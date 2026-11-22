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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What an unload names its files and writes in them, and how a load decompresses what it reads, from the account's
 * documentation (COPY INTO runs on the embedded engine only): a partition key's {@code /} nests folders, partitioned
 * and INCLUDE_QUERY_ID files carry the statement's query ID, a VARIANT DOUBLE is written as TO_JSON spells it, a
 * TYPE = JSON unload judges its column the same whatever order its rows come in, and a load reads its files by the
 * format's COMPRESSION as a staged-file query does.
 */
public class UnloadNamingAndValuesTest extends MeasuredCellsTestSupport {

    @TempDir
    Path local;

    @BeforeEach
    public void embeddedOnly() {
        Assumptions.assumeFalse(isLiveSnowflake(), "COPY INTO is not run against the live account");
        engine.execute("CREATE OR REPLACE STAGE unv");
    }

    /** The names a LIST answers, the statement's query ID written Q, sorted. */
    private String listed(final String list, final String queryId) {
        engine.execute(list);
        return answer("SELECT REPLACE(\"name\", '" + queryId + "', 'Q') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " ORDER BY 1");
    }

    private String lastQueryId() {
        return answer("SELECT LAST_QUERY_ID()");
    }

    /** A / in a partition key separates nested folders, and the files carry the unload's query ID. */
    @Test
    public void aPartitionKeyNestsFoldersAndFilesCarryTheQueryId() {
        engine.execute("CREATE OR REPLACE TABLE upt (dt DATE, h INT) AS SELECT TO_DATE($1), $2 FROM VALUES "
            + "('2020-01-28', 18), ('2020-01-28', 22), ('2020-01-29', 2)");
        engine.execute("COPY INTO @unv/p/ FROM upt PARTITION BY ('date=' || TO_VARCHAR(dt, 'YYYY-MM-DD') || '/hour=' "
            + "|| h) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        final String queryId = lastQueryId();
        assertEquals("unv/p/date=2020-01-28/hour=18/data_Q_0_0_0.csv ;; unv/p/date=2020-01-28/hour=22/data_Q_0_0_0.csv"
            + " ;; unv/p/date=2020-01-29/hour=2/data_Q_0_0_0.csv", listed("LIST @unv/p/", queryId));
        assertEquals("unv/p/date=2020-01-28/hour=18/data_Q_0_0_0.csv",
            listed("LIST @unv/p/date=2020-01-28/hour=18/", queryId));
        final String detailed = firstOf("COPY INTO @unv/q/ FROM (SELECT 1 AS v) PARTITION BY "
            + "('date=2020-01-28/hour=18') FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) DETAILED_OUTPUT = TRUE");
        assertAnswer("date=2020-01-28/hour=18|date=2020-01-28/hour=18/data_Q_0_0_0.csv", "SELECT PARTITION_NAME, "
            + "REPLACE(FILE_NAME, '" + detailed + "', 'Q') FROM TABLE(RESULT_SCAN('" + detailed + "'))");
    }

    /** The NULL partition's folder keeps its backslash in a listing, a file name and a PATTERN. */
    @Test
    public void theNullPartitionKeepsItsBackslash() {
        engine.execute("COPY INTO @unv/n/ FROM (SELECT 1 AS v, NULL::VARCHAR AS k) PARTITION BY k "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        final String queryId = lastQueryId();
        assertEquals("unv/n/\\N/data_Q_0_0_0.csv", listed("LIST @unv/n/", queryId));
        assertEquals("unv/n/\\N/data_Q_0_0_0.csv", listed("LIST @unv PATTERN = '.*\\\\\\\\N.*'", queryId));
        assertAnswer("n/\\N/data_" + queryId + "_0_0_0.csv", "SELECT DISTINCT METADATA$FILENAME FROM @unv/n/");
    }

    /** INCLUDE_QUERY_ID = TRUE names the file after the statement, so a rerun writes a second file. */
    @Test
    public void includeQueryIdNamesTheFilesAfterTheStatement() {
        engine.execute("COPY INTO @unv/i/ FROM (SELECT 1 AS v) INCLUDE_QUERY_ID = TRUE");
        final String first = lastQueryId();
        engine.execute("COPY INTO @unv/i/ FROM (SELECT 1 AS v) INCLUDE_QUERY_ID = TRUE");
        final String second = lastQueryId();
        assertEquals("unv/i/data_Q_0_0_0.csv.gz", listed("LIST '@unv/i/data_" + first + "'", first));
        assertEquals("unv/i/data_Q_0_0_0.csv.gz", listed("LIST '@unv/i/data_" + second + "'", second));
        engine.execute("LIST @unv/i/");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED Files already existing at the unload destination: @unv/i/. Use overwrite option to "
            + "force unloading.", "COPY INTO @unv/i/ FROM (SELECT 1 AS v)");
        final String detailed = firstOf("COPY INTO @unv/d/ FROM (SELECT 1 AS v) FILE_FORMAT = (TYPE = CSV "
            + "COMPRESSION = NONE) INCLUDE_QUERY_ID = TRUE DETAILED_OUTPUT = TRUE");
        assertAnswer("data_Q_0_0_0.csv|2|1", "SELECT REPLACE(FILE_NAME, '" + detailed + "', 'Q'), FILE_SIZE, "
            + "ROW_COUNT FROM TABLE(RESULT_SCAN('" + detailed + "'))");
    }

    /** Runs a statement and answers its query ID. */
    private String firstOf(final String sql) {
        engine.execute(sql);
        return lastQueryId();
    }

    /** A VARIANT DOUBLE is written as TO_JSON spells it, in a JSON and in a CSV unload alike. */
    @Test
    public void aVariantDoubleIsWrittenAsToJsonSpellsIt() {
        engine.execute("CREATE OR REPLACE FILE FORMAT unv_lines TYPE = CSV FIELD_DELIMITER = NONE");
        engine.execute("COPY INTO @unv/j/ FROM (SELECT OBJECT_CONSTRUCT('c', 1e20::FLOAT, 'f', 1::FLOAT/3, 'h', "
            + "1e-7::FLOAT)) FILE_FORMAT = (TYPE = JSON COMPRESSION = NONE)");
        assertAnswer("{\"c\":1.000000000000000e+20,\"f\":3.333333333333333e-01,\"h\":1.000000000000000e-07}",
            "SELECT $1 FROM @unv/j/ (FILE_FORMAT => 'unv_lines')");
        engine.execute("COPY INTO @unv/c/ FROM (SELECT 1 AS a, (1::FLOAT/3)::VARIANT AS v) "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        assertAnswer("1,3.333333333333333e-01", "SELECT $1 FROM @unv/c/ (FILE_FORMAT => 'unv_lines')");
    }

    /** A JSON unload of one VARIANT column takes its scalars whatever row comes first; a text column refuses. */
    @Test
    public void aJsonUnloadJudgesTheColumnWhateverTheRowOrder() {
        engine.execute("CREATE OR REPLACE FILE FORMAT unv_lines TYPE = CSV FIELD_DELIMITER = NONE");
        engine.execute("COPY INTO @unv/d1/ FROM (SELECT PARSE_JSON(column1) FROM VALUES ('[1,2]'), ('\"str\"'), ('5'))"
            + " FILE_FORMAT = (TYPE = JSON COMPRESSION = NONE)");
        engine.execute("COPY INTO @unv/d2/ FROM (SELECT PARSE_JSON(column1) FROM VALUES ('\"str\"'), ('{\"a\":1}'))"
            + " FILE_FORMAT = (TYPE = JSON COMPRESSION = NONE)");
        assertAnswer("\"str\" ;; {\"a\":1}", "SELECT $1 FROM @unv/d2/ (FILE_FORMAT => 'unv_lines')");
        final String refusal = "REFUSED Unsupported feature 'unloading of more than one column or non-json values'.";
        assertAnswer(refusal, "COPY INTO @unv/d3/ FROM (SELECT column1 FROM VALUES ('{\"a\":1}'), ('x')) "
            + "FILE_FORMAT = (TYPE = JSON)");
        assertAnswer(refusal, "COPY INTO @unv/d4/ FROM (SELECT column1 FROM VALUES ('x'), ('{\"a\":1}')) "
            + "FILE_FORMAT = (TYPE = JSON)");
    }

    /** A load reads its files by the format's COMPRESSION, as a staged-file query reads them. */
    @Test
    public void aLoadDecompressesByTheFormatsCompression() throws IOException {
        engine.execute("CREATE OR REPLACE TABLE ulc (s VARCHAR, n INT)");
        for (final String codec : new String[] {"BZ2", "ZSTD", "DEFLATE", "RAW_DEFLATE"}) {
            engine.execute("PUT 'file://" + CompressedFixtures.write(local, "h_" + codec, codec).toAbsolutePath()
                + "' @unv/l/ AUTO_COMPRESS = FALSE SOURCE_COMPRESSION = NONE");
            engine.execute("COPY INTO ulc FROM @unv/l/h_" + codec + " FILE_FORMAT = (TYPE = CSV COMPRESSION = "
                + codec + ")");
        }
        assertAnswer("8", "SELECT COUNT(*) FROM ulc");
        engine.execute("PUT 'file://" + CompressedFixtures.write(local, "h.csv.bz2", "BZ2").toAbsolutePath()
            + "' @unv/a/ AUTO_COMPRESS = FALSE");
        engine.execute("COPY INTO ulc FROM @unv/a/ FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("10", "SELECT COUNT(*) FROM ulc");
        assertAnswer("REFUSED Invalid data encountered during decompression for file: 'h_BZ2',compression type used: "
            + "'ZSTD', cause: 'Unknown frame descriptor'",
            "COPY INTO ulc FROM @unv/l/h_BZ2 FILE_FORMAT = (TYPE = CSV COMPRESSION = ZSTD) FORCE = TRUE");
    }
}
