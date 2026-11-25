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

package dev.frostlake.config;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code stage.s3.fetch}: an {@code s3://} file missing below the local root is downloaded through the
 * {@link S3ObjectSource} on the classpath (here {@link InMemoryS3ObjectSource}) before it is read, once, and an existing
 * file is used as it is; with the setting off nothing is fetched.
 */
public class S3FetchThroughTest {

    @TempDir
    Path root;

    @BeforeEach
    void emptyBucket() {
        InMemoryS3ObjectSource.reset();
    }

    @AfterEach
    void emptyBucketAgain() {
        InMemoryS3ObjectSource.reset();
    }

    private EngineConfig config(final boolean fetch) {
        final Properties p = new Properties();
        p.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_ROOT, root.toString());
        p.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_MAPPINGS, "");
        p.setProperty(EngineConfig.PROP_STAGE_S3_FETCH, String.valueOf(fetch));
        return new EngineConfig(p);
    }

    /** Every regular file below {@code dir}, walked depth-first. */
    private static List<Path> filesBelow(final Path dir) throws IOException {
        final List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (final Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    files.addAll(filesBelow(entry));
                } else if (Files.isRegularFile(entry)) {
                    files.add(entry);
                }
            }
        }
        return files;
    }

    private static String read(final Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    public void offByDefault() {
        InMemoryS3ObjectSource.put("b", "jars/udf.jar", "jar");
        final Properties p = new Properties();
        p.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_ROOT, root.toString());
        final S3PathResolver resolver = new S3PathResolver(new EngineConfig(p));
        assertFalse(resolver.fetches());
        final Path local = resolver.toLocalPath("s3://b/jars/udf.jar");
        assertFalse(Files.exists(local));
        assertEquals(0, InMemoryS3ObjectSource.fetches());
    }

    @Test
    public void aMissingJarIsFetchedOnceAndReused() throws IOException {
        InMemoryS3ObjectSource.put("b", "jars/udf.jar", "jar bytes");
        final S3PathResolver resolver = new S3PathResolver(config(true));
        assertTrue(resolver.fetches());
        final Path local = resolver.toLocalPath("s3://b/jars/udf.jar");
        assertEquals(root.resolve("b").resolve("jars").resolve("udf.jar"), local);
        assertEquals("jar bytes", read(local));
        // A second engine sharing the root reads the cached file.
        assertEquals(local, new S3PathResolver(config(true)).toLocalPath("s3://b/jars/udf.jar"));
        assertEquals(1, InMemoryS3ObjectSource.fetches());
        assertEquals(1, filesBelow(local.getParent()).size(), "no partial download is left beside the file");
    }

    @Test
    public void anExistingFileIsNeverRefetched() throws IOException {
        InMemoryS3ObjectSource.put("b", "f.txt", "from s3");
        final Path local = root.resolve("b").resolve("f.txt");
        Files.createDirectories(local.getParent());
        Files.write(local, "local".getBytes(StandardCharsets.UTF_8));
        assertEquals("local", read(new S3PathResolver(config(true)).toLocalPath("s3://b/f.txt")));
        assertEquals(0, InMemoryS3ObjectSource.fetches());
    }

    @Test
    public void aPrefixIsSyncedOnce() throws IOException {
        InMemoryS3ObjectSource.put("b", "load/jars/a.jar", "a");
        InMemoryS3ObjectSource.put("b", "load/jars/sub/b.jar", "b");
        InMemoryS3ObjectSource.put("b", "load/jars/", "");          // a folder marker
        InMemoryS3ObjectSource.put("b", "load/other.txt", "not below the prefix");
        final S3PathResolver resolver = new S3PathResolver(config(true));
        final Path dir = resolver.toLocalPath("s3://b/load/jars/");
        assertEquals("a", read(dir.resolve("a.jar")));
        assertEquals("b", read(dir.resolve("sub").resolve("b.jar")));
        assertFalse(Files.exists(root.resolve("b").resolve("load").resolve("other.txt")));
        resolver.toLocalPath("s3://b/load/jars/");
        assertEquals(1, InMemoryS3ObjectSource.lists());
        assertEquals(2, InMemoryS3ObjectSource.fetches());
    }

    @Test
    public void aUrlThatNamesNoObjectIsSyncedAsAPrefix() throws IOException {
        InMemoryS3ObjectSource.put("b", "models/m1.bin", "m1");
        final Path dir = new S3PathResolver(config(true)).toLocalPath("s3://b/models");
        assertEquals("m1", read(dir.resolve("m1.bin")));
    }

    @Test
    public void aKeyCannotReachOutsideTheRoot() throws IOException {
        InMemoryS3ObjectSource.put("b", "p/../../../escaped.txt", "x");
        new S3PathResolver(config(true)).toLocalPath("s3://b/p/");
        assertFalse(Files.exists(root.getParent().resolve("escaped.txt")));
        assertFalse(Files.exists(root.resolve("escaped.txt")));
        final List<Path> files = filesBelow(root);
        for (final Path file : files) {
            assertTrue(file.normalize().startsWith(root.normalize()), file + " stays below the root");
        }
        assertEquals(1, files.size(), "the key lands below the root, its .. kept as a name");
    }

    @Test
    public void creatingAStageFetchesNothingAndListingSyncsIt() {
        InMemoryS3ObjectSource.put("b", "stage/one.csv", "1");
        InMemoryS3ObjectSource.put("b", "stage/two.csv", "2");
        final DatabaseEngine engine = new DatabaseEngine(config(true));
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("CREATE STAGE d.public.s URL = 's3://b/stage/'");
            assertEquals(0, InMemoryS3ObjectSource.lists());
            final ResultSet listed = engine.executeQuery("LIST @d.public.s");
            assertEquals(2, listed.getRowCount());
            assertEquals(2, InMemoryS3ObjectSource.fetches());
        } finally {
            engine.shutdown();
        }
    }
}
