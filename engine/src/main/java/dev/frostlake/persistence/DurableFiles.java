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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forcing files and directories' entries onto the disk. A file created, or renamed into place, is durable only once the
 * directory naming it is: a crash before that may lose the name although the file's bytes were forced.
 */
final class DurableFiles {

    private static final Logger logger = LoggerFactory.getLogger(DurableFiles.class);

    private DurableFiles() {
    }

    /**
     * Forces a file's bytes onto the disk.
     *
     * @param file the file
     * @throws IOException when the file cannot be opened or forced
     */
    static void syncFile(final Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /**
     * Forces a directory's entries onto the disk. A platform that cannot open a directory for this (Windows) makes
     * a rename durable by itself, so a failure to open one is passed over.
     *
     * @param dir the directory
     */
    static void syncDirectory(final Path dir) {
        if (dir == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (final IOException cannotSync) {
            logger.debug("Could not force directory {} to disk: {}", dir, cannotSync.getMessage());
        }
    }
}
