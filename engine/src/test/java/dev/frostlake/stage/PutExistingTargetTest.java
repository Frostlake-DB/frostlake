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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A PUT whose target name the stage already holds answers SKIPPED whatever the file holds, a gzip file named as the
 * earlier upload's target, a file of other content or of another compression alike; OVERWRITE = TRUE uploads it.
 * Two files of one PUT bound for one target name collide: the one the account's driver takes first - in the order
 * of a hash map of their paths, filled from a hash set - is reported COLLISION (live-verified).
 */
public class PutExistingTargetTest extends MeasuredCellsTestSupport {

    private static final String SAME = "File with same destination name and checksum already exists: ";

    @TempDir
    Path local;

    /** A PUT of a local file: its text as the statement writes it. */
    private String put(final Path file, final String target) {
        return "PUT 'file://" + file.toAbsolutePath() + "' " + target;
    }

    @Test
    public void anExistingTargetNameIsSkippedWhateverTheFileHolds() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE pet");
        final Path plain = CompressedFixtures.write(local, "h.csv", "NONE");
        final Path gzip = CompressedFixtures.write(local, "h.csv.gz", "GZIP");
        final Path other = local.resolve("d2");
        Files.createDirectories(other);
        final Path changed = CompressedFixtures.write(other, "h.csv", "NONE", "z,9\ny,8\nq,7\n");
        final Path otherCodec = CompressedFixtures.write(other, "h.csv.bz2", "ZSTD");
        final long zstSize = Files.size(otherCodec);
        assertAnswer("h.csv|h.csv.gz|8|28|NONE|GZIP|UPLOADED|ENCRYPTED|", put(plain, "@pet/r/"));
        assertAnswer("h.csv|h.csv.gz|8|0|NONE|GZIP|SKIPPED||" + SAME + "h.csv.gz", put(plain, "@pet/r/"));
        assertAnswer("h.csv.gz|h.csv.gz|28|0|GZIP|GZIP|SKIPPED||" + SAME + "h.csv.gz", put(gzip, "@pet/r/"));
        assertAnswer("h.csv|h.csv.gz|12|0|NONE|GZIP|SKIPPED||" + SAME + "h.csv.gz", put(changed, "@pet/r/"));
        assertAnswer("h.csv|h.csv.gz|12|0|NONE|GZIP|SKIPPED||" + SAME + "h.csv.gz",
            put(changed, "@pet/r/ OVERWRITE = FALSE"));
        assertAnswer("h.csv|h.csv.gz|12|32|NONE|GZIP|UPLOADED|ENCRYPTED|", put(changed, "@pet/r/ OVERWRITE = TRUE"));
        // The other way round: a gzip file first, then the plain file that compresses to its name.
        assertAnswer("h.csv.gz|h.csv.gz|28|28|GZIP|GZIP|UPLOADED|ENCRYPTED|", put(gzip, "@pet/u/"));
        assertAnswer("h.csv|h.csv.gz|8|0|NONE|GZIP|SKIPPED||" + SAME + "h.csv.gz", put(plain, "@pet/u/"));
        // Without AUTO_COMPRESS the name is the file's own; another compression under that name is skipped too.
        assertAnswer("h.csv|h.csv|8|8|NONE|NONE|UPLOADED|ENCRYPTED|", put(plain, "@pet/s/ AUTO_COMPRESS = FALSE"));
        assertAnswer("h.csv|h.csv|12|0|NONE|NONE|SKIPPED||" + SAME + "h.csv",
            put(changed, "@pet/s/ AUTO_COMPRESS = FALSE"));
        assertAnswer("h.csv.bz2|h.csv.bz2|48|48|BZIP2|BZIP2|UPLOADED|ENCRYPTED|",
            put(CompressedFixtures.write(local, "h.csv.bz2", "BZ2"), "@pet/s/"));
        assertAnswer("h.csv.bz2|h.csv.bz2|" + zstSize + "|0|ZSTD|ZSTD|SKIPPED||" + SAME + "h.csv.bz2",
            put(otherCodec, "@pet/s/"));
    }

    @Test
    public void twoFilesOfOneTargetNameCollide() throws IOException {
        engine.execute("CREATE OR REPLACE STAGE pec");
        final Path dir = local.resolve("g");
        Files.createDirectories(dir);
        final Path plain = CompressedFixtures.write(dir, "h.csv", "NONE");
        final Path gzip = CompressedFixtures.write(dir, "h.csv.gz", "GZIP");
        final Path bz2 = CompressedFixtures.write(dir, "h.csv.bz2", "BZ2");
        // Listed as the local directory lists them, sorted by name, before the hash set takes them.
        final List<String> order = driverOrder(plain, bz2, gzip);
        final boolean gzipFirst = order.indexOf(gzip.toString()) < order.indexOf(plain.toString());
        final Map<String, String> fresh = new HashMap<>();
        final Map<String, String> again = new HashMap<>();
        final String collision = gzipFirst ? gzip + " has same name as " + plain : plain + " has same name as " + gzip;
        fresh.put(plain.toString(), gzipFirst ? "h.csv|h.csv.gz|8|28|NONE|GZIP|UPLOADED|ENCRYPTED|"
            : "h.csv|h.csv.gz|8|0|NONE|GZIP|COLLISION||" + collision);
        fresh.put(gzip.toString(), gzipFirst ? "h.csv.gz|h.csv.gz|28|0|GZIP|GZIP|COLLISION||" + collision
            : "h.csv.gz|h.csv.gz|28|28|GZIP|GZIP|UPLOADED|ENCRYPTED|");
        fresh.put(bz2.toString(), "h.csv.bz2|h.csv.bz2|48|48|BZIP2|BZIP2|UPLOADED|ENCRYPTED|");
        again.put(plain.toString(), gzipFirst ? "h.csv|h.csv.gz|8|0|NONE|GZIP|SKIPPED||" + SAME + "h.csv.gz"
            : "h.csv|h.csv.gz|8|0|NONE|GZIP|COLLISION||" + collision);
        again.put(gzip.toString(), gzipFirst ? "h.csv.gz|h.csv.gz|28|0|GZIP|GZIP|COLLISION||" + collision
            : "h.csv.gz|h.csv.gz|28|0|GZIP|GZIP|SKIPPED||" + SAME + "h.csv.gz");
        again.put(bz2.toString(), "h.csv.bz2|h.csv.bz2|48|0|BZIP2|BZIP2|SKIPPED||" + SAME + "h.csv.bz2");
        assertAnswer(joined(order, fresh), put(dir.resolve("h*"), "@pec/v/"));
        assertAnswer(joined(order, again), put(dir.resolve("h*"), "@pec/v/"));
    }

    /** The paths in the order the account's driver takes them: a hash map's, filled from a hash set. */
    private static List<String> driverOrder(final Path... files) {
        final Set<String> paths = new HashSet<>();
        for (final Path file : files) {
            paths.add(file.toString());
        }
        final Map<String, String> byPath = new HashMap<>(files.length);
        for (final String path : paths) {
            byPath.put(path, path);
        }
        return new ArrayList<>(byPath.keySet());
    }

    private static String joined(final List<String> order, final Map<String, String> rows) {
        final List<String> out = new ArrayList<>();
        for (final String path : order) {
            out.add(rows.get(path));
        }
        return String.join(" ;; ", out);
    }
}
