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

package dev.frostlake.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The stage files a committed {@code COPY … PURGE = TRUE} took off the stage, kept beside the write-ahead log until a
 * checkpoint holds the rows they loaded. The log replays a load from its text, which reads the stage's files again; a
 * purge removed them, and the checkpoint the load owes cannot be written while another session's transaction is open.
 * A crash in that window would lose the load, so the purge moves its files here instead of deleting them, and a
 * recovery puts them back where they were for the replayed load to read, which purges them again.
 *
 * <p>Each held file is kept under the generation of the last checkpoint written or attempted when it was purged: a
 * checkpoint of a later generation holds its load, so a written checkpoint lets go of every earlier generation, and a
 * recovery from a checkpoint of a later generation drops it instead of putting it back. An entry is a pair of files, the
 * file's own content ({@code <id>.data}) and the path it was purged from ({@code <id>.origin}).
 */
public final class PurgedFileHold {

    private static final Logger logger = LoggerFactory.getLogger(PurgedFileHold.class);

    private static final String DATA = ".data";
    private static final String ORIGIN = ".origin";

    private final Path root;

    /** The generation a file purged now is held under. */
    private volatile long generation;

    /** The files a recovery put back on their stages that no replayed load has purged again yet. */
    private final Set<Path> restored = new HashSet<Path>();

    /**
     * @param root the directory the held files are kept in, created when the first file is held
     */
    public PurgedFileHold(final Path root) {
        this.root = root;
    }

    /**
     * Sets the generation of the last checkpoint written or attempted.
     *
     * @param checkpoint the checkpoint's sequence
     */
    public void setGeneration(final long checkpoint) {
        this.generation = checkpoint;
    }

    /**
     * Takes a purged file off its stage into the hold.
     *
     * @param file the staged file
     * @throws IOException when the file cannot be moved; it stays on its stage then
     */
    public void hold(final Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        final Path dir = Files.createDirectories(root.resolve(Long.toString(generation)));
        final String id = UUID.randomUUID().toString();
        final Path origin = dir.resolve(id + ORIGIN);
        Files.write(origin, file.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(file, dir.resolve(id + DATA), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            Files.deleteIfExists(origin);
            throw e;
        }
    }

    /**
     * Puts the held files a recovery's replay may read back on their stages, before the replay: those of a generation
     * the checkpoint the recovery starts from does not hold. The others are dropped. A file whose place another file
     * took since stays held only.
     *
     * @param checkpoint the sequence of the checkpoint the recovery starts from, 0 for none
     * @return how many files were put back
     */
    public synchronized int restore(final long checkpoint) {
        int count = 0;
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (DirectoryStream<Path> generations = Files.newDirectoryStream(root)) {
            for (final Path dir : generations) {
                final long held = generationOf(dir);
                if (held < checkpoint) {
                    deleteGeneration(dir);
                    continue;
                }
                count += restoreGeneration(dir);
            }
        } catch (final IOException e) {
            logger.warn("Could not read the purged files held in {}", root, e);
        }
        return count;
    }

    private int restoreGeneration(final Path dir) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, "*" + ORIGIN)) {
            for (final Path origin : entries) {
                final String name = origin.getFileName().toString();
                final Path data = dir.resolve(name.substring(0, name.length() - ORIGIN.length()) + DATA);
                if (!Files.exists(data)) {
                    continue;
                }
                final Path place = Path.of(new String(Files.readAllBytes(origin), StandardCharsets.UTF_8));
                if (Files.exists(place)) {
                    continue;
                }
                try {
                    if (place.getParent() != null) {
                        Files.createDirectories(place.getParent());
                    }
                    Files.copy(data, place);
                    restored.add(place);
                    count++;
                } catch (final IOException e) {
                    logger.warn("Could not put the purged file {} back for the recovery", place, e);
                }
            }
        }
        return count;
    }

    /**
     * A replayed load purges a file again: one the recovery put back goes, any other stays where it is.
     *
     * @param file the staged file
     */
    public synchronized void purgedOnReplay(final Path file) {
        if (restored.remove(file.toAbsolutePath())) {
            try {
                Files.deleteIfExists(file);
            } catch (final IOException e) {
                logger.warn("Could not purge the replayed load's file {}", file, e);
            }
        }
    }

    /**
     * The recovery's replay is over: a file it put back that no replayed load purged stays on its stage, as a load the
     * log does not hold left it.
     */
    public synchronized void replayEnded() {
        restored.clear();
    }

    /**
     * A checkpoint was written: it holds every load whose file was held under an earlier generation.
     *
     * @param checkpoint the checkpoint's sequence
     */
    public synchronized void release(final long checkpoint) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (DirectoryStream<Path> generations = Files.newDirectoryStream(root)) {
            for (final Path dir : generations) {
                if (generationOf(dir) < checkpoint) {
                    deleteGeneration(dir);
                }
            }
        } catch (final IOException e) {
            logger.warn("Could not let go of the purged files held in {}", root, e);
        }
    }

    /** The generation a directory of the hold keeps, or the lowest one for a name that is none. */
    private static long generationOf(final Path dir) {
        try {
            return Long.parseLong(dir.getFileName().toString());
        } catch (final NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private static void deleteGeneration(final Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
                    for (final Path entry : entries) {
                        Files.deleteIfExists(entry);
                    }
                }
            }
            Files.deleteIfExists(dir);
        } catch (final IOException e) {
            logger.warn("Could not delete the held purged files in {}", dir, e);
        }
    }
}
