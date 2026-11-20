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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * {@link TableDataStore} over a checkpoint's tables directory — one serialized file per table, forced to disk.
 *
 * <p>A table's file is named by a digest of its database, schema and table names, so no two tables share a file
 * whatever their names hold: an underscore, a slash, or a case only a case-sensitive disk tells apart. Each file
 * also names the table it was written for, and a file written for another table is refused rather than restored.
 * A snapshot written before the digest names kept each table under {@code <database>_<schema>_<table>.dat}; such a
 * file is still read, when the table it names is the one restored.
 */
public final class DiskTableDataStore implements TableDataStore {

    /** The subdirectory holding hidden permanent tables' rows; every table file ends in ".dat", so no file clashes. */
    private static final String SHADOWED_DIR = "shadowed";

    private final Path tablesDir;

    public DiskTableDataStore(final Path tablesDir) {
        this.tablesDir = tablesDir;
    }

    @Override
    public void save(final String database, final String schema, final String table,
                     final TableDataSnapshot data) throws IOException {
        write(tablesDir.resolve(fileName(database, schema, table)), data);
    }

    @Override
    public TableDataSnapshot load(final String database, final String schema, final String table)
            throws IOException, ClassNotFoundException {
        return read(tablesDir, database, schema, table);
    }

    @Override
    public void saveShadowed(final String database, final String schema, final String table,
                             final TableDataSnapshot data) throws IOException {
        final Path dir = tablesDir.resolve(SHADOWED_DIR);
        Files.createDirectories(dir);
        write(dir.resolve(fileName(database, schema, table)), data);
    }

    @Override
    public TableDataSnapshot loadShadowed(final String database, final String schema, final String table)
            throws IOException, ClassNotFoundException {
        return read(tablesDir.resolve(SHADOWED_DIR), database, schema, table);
    }

    /** Forces the directories the table files went to onto the disk, once every file is written. */
    void syncDirectories() {
        DurableFiles.syncDirectory(tablesDir);
        final Path shadowed = tablesDir.resolve(SHADOWED_DIR);
        if (Files.isDirectory(shadowed)) {
            DurableFiles.syncDirectory(shadowed);
        }
    }

    private static void write(final Path tableFile, final TableDataSnapshot data) throws IOException {
        try (FileOutputStream file = new FileOutputStream(tableFile.toFile());
             ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(file))) {
            oos.writeObject(data);
            oos.flush();
            file.getFD().sync();
        }
    }

    /**
     * The saved rows of a table, from the file of its digest name or, for a snapshot written before those names, from
     * the file of its former name; null when neither holds any.
     */
    private static TableDataSnapshot read(final Path dir, final String database, final String schema,
                                          final String table) throws IOException, ClassNotFoundException {
        TableDataSnapshot data = read(dir.resolve(fileName(database, schema, table)));
        if (data == null) {
            final Path former = formerFile(dir, database, schema, table);
            data = former == null ? null : read(former);
        }
        if (data != null && data.table != null && !(Objects.equals(database, data.database)
                && Objects.equals(schema, data.schema) && Objects.equals(table, data.table))) {
            throw new IOException("The saved rows of table " + database + "." + schema + "." + table
                + " were written for table " + data.database + "." + data.schema + "." + data.table);
        }
        return data;
    }

    private static TableDataSnapshot read(final Path tableFile) throws IOException, ClassNotFoundException {
        if (!Files.isRegularFile(tableFile)) {
            return null;
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(tableFile)))) {
            return (TableDataSnapshot) ois.readObject();
        }
    }

    /**
     * The file name a table's rows are saved under: the SHA-256 digest of its database, schema and table names, each
     * preceded by its length, in lower-case hex.
     */
    static String fileName(final String database, final String schema, final String table) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        for (final String part : new String[] {database, schema, table}) {
            final byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            digest.update(new byte[] {(byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8), (byte) bytes.length});
            digest.update(bytes);
        }
        return "t-" + HexFormat.of().formatHex(digest.digest()) + ".dat";
    }

    /** The name a snapshot written before the digest names kept a table's rows under. */
    static String formerFileName(final String database, final String schema, final String table) {
        return database + "_" + schema + "_" + table + ".dat";
    }

    /** The file a snapshot written before the digest names kept a table's rows in, or null for a name no path takes. */
    private static Path formerFile(final Path dir, final String database, final String schema, final String table) {
        try {
            return dir.resolve(formerFileName(database, schema, table));
        } catch (final InvalidPathException notAPath) {
            return null;
        }
    }
}
