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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The test suite's {@link S3ObjectSource}, registered for ServiceLoader in the test resources: a bucket held in memory,
 * shared by every resolver (ServiceLoader makes a new instance for each), that counts what it is asked. Only engines
 * configured with {@code stage.s3.fetch=true} consult it.
 */
public class InMemoryS3ObjectSource implements S3ObjectSource {

    private static final Map<String, byte[]> OBJECTS = new TreeMap<String, byte[]>();
    private static final AtomicInteger FETCHES = new AtomicInteger();
    private static final AtomicInteger LISTS = new AtomicInteger();

    /** Empties the bucket and the counters. */
    public static synchronized void reset() {
        OBJECTS.clear();
        FETCHES.set(0);
        LISTS.set(0);
    }

    /** Puts an object at {@code s3://bucket/key}. */
    public static synchronized void put(final String bucket, final String key, final String content) {
        OBJECTS.put(bucket + "/" + key, content.getBytes(StandardCharsets.UTF_8));
    }

    /** How many objects were downloaded. */
    public static int fetches() {
        return FETCHES.get();
    }

    /** How many listings were asked for. */
    public static int lists() {
        return LISTS.get();
    }

    @Override
    public void configure(final EngineConfig config) {
        // Nothing to configure: the bucket is in memory.
    }

    @Override
    public boolean fetch(final String bucket, final String key, final Path target) throws IOException {
        final byte[] content;
        synchronized (InMemoryS3ObjectSource.class) {
            content = OBJECTS.get(bucket + "/" + key);
        }
        if (content == null) {
            return false;
        }
        FETCHES.incrementAndGet();
        Files.write(target, content);
        return true;
    }

    @Override
    public List<String> list(final String bucket, final String prefix) {
        LISTS.incrementAndGet();
        final List<String> keys = new ArrayList<String>();
        synchronized (InMemoryS3ObjectSource.class) {
            for (final String name : OBJECTS.keySet()) {
                if (name.startsWith(bucket + "/" + prefix)) {
                    keys.add(name.substring(bucket.length() + 1));
                }
            }
        }
        return keys;
    }
}
