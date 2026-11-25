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

import dev.frostlake.executor.StagePathSegments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves Snowflake-style S3 URLs ({@code s3://bucket/key}) to a local filesystem path, so files that
 * live in S3 in production — most usefully the JAR(s) named in a UDF/procedure {@code IMPORTS} clause —
 * can be served from local disk in the local/CI testing the engine targets. Driven by two engine settings:
 *
 * <ul>
 *   <li>{@code stage.s3.localMappings} — explicit prefix overrides, {@code ';'}-separated {@code s3prefix=localdir}
 *       pairs, e.g. {@code "s3://prod-bucket/lib/=/opt/jars; s3://data/=/srv/data"}. The longest matching
 *       prefix wins, and the remainder of the URL is appended to the mapped directory.</li>
 *   <li>{@code stage.s3.localRoot} — the fallback base; an unmapped {@code s3://bucket/key} resolves to
 *       {@code <localRoot>/bucket/key}. Defaults to {@code ~/.frostlake_stages/s3}.</li>
 * </ul>
 *
 * <p>With {@code stage.s3.fetch=true} and an {@link S3ObjectSource} on the classpath, the local tree is a cache of the
 * bucket rather than its stand-in: {@link #toLocalPath} downloads a file that is not there yet before answering it.
 * A URL ending in {@code /} names a prefix, synced once per JVM; any other URL is fetched as an object, and when the
 * bucket holds none under that key it is synced as a prefix. A file already present is used as it is and never
 * refetched. Every downloaded key goes through the same mapping and path-safety checks as a resolved URL, and
 * concurrent engines in one JVM, which share the local root, fetch each file once. {@link #mappedPath} maps without
 * fetching, for callers that only need the location (a stage's directory when the stage is created).</p>
 */
public class S3PathResolver {

    private static final Logger logger = LoggerFactory.getLogger(S3PathResolver.class);

    private static final String S3_SCHEME = "s3://";

    /** A lock per local file or directory, shared by every resolver in the JVM, so one download serves them all. */
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<String, Object>();

    /** The prefixes synced in this JVM, by local directory and URL. */
    private static final Set<String> SYNCED = ConcurrentHashMap.newKeySet();

    /** Whether the missing-source warning was logged, so it is logged once. */
    private static final AtomicBoolean WARNED_NO_SOURCE = new AtomicBoolean();

    private final Path localRoot;
    private final Map<String, Path> mappings;   // s3:// prefix -> local dir, in configuration order
    private final S3ObjectSource source;        // null unless stage.s3.fetch is on and a source is on the classpath

    public S3PathResolver(final EngineConfig config) {
        this.localRoot = Paths.get(config.getStageS3LocalRoot());
        this.mappings = parseMappings(config.getStageS3LocalMappings());
        this.source = config.isStageS3Fetch() ? loadSource(config) : null;
    }

    /**
     * The first {@link S3ObjectSource} {@link ServiceLoader} finds, configured for the engine, or null (with a warning
     * logged once per JVM) when there is none.
     */
    private static S3ObjectSource loadSource(final EngineConfig config) {
        final Iterator<S3ObjectSource> found =
            ServiceLoader.load(S3ObjectSource.class, S3PathResolver.class.getClassLoader()).iterator();
        if (!found.hasNext()) {
            if (WARNED_NO_SOURCE.compareAndSet(false, true)) {
                logger.warn("stage.s3.fetch is on but no S3ObjectSource is on the classpath (add frostlake-cloud-aws); "
                    + "s3:// files are read from the local root only");
            }
            return null;
        }
        final S3ObjectSource first = found.next();
        first.configure(config);
        return first;
    }

    /** Whether {@link #toLocalPath} downloads missing files: {@code stage.s3.fetch} is on and a source was found. */
    public boolean fetches() {
        return source != null;
    }

    /** True if {@code url} is an {@code s3://…} reference. */
    public static boolean isS3Url(final String url) {
        return url != null && url.startsWith(S3_SCHEME);
    }

    /**
     * Map an {@code s3://bucket/key} URL to its local filesystem path, and with {@code stage.s3.fetch} on download
     * what the path should hold first (see the class description). Without fetching this is {@link #mappedPath}.
     *
     * @throws IllegalArgumentException if {@code s3Url} is not an {@code s3://} URL
     * @throws IllegalStateException    if a download is refused or fails
     */
    public Path toLocalPath(final String s3Url) {
        final Path local = mappedPath(s3Url);
        if (source == null) {
            return local;
        }
        if (s3Url.endsWith("/")) {
            syncPrefix(s3Url, local);
            return local;
        }
        if (Files.isRegularFile(local)) {
            return local;
        }
        if (!Files.isDirectory(local) && fetchObject(s3Url, local)) {
            return local;
        }
        syncPrefix(s3Url + "/", local);
        return local;
    }

    /**
     * Map an {@code s3://bucket/key} URL to its local filesystem path, never downloading. An explicit
     * {@code stage.s3.localMappings} entry wins (longest matching prefix); with no match the key is placed under the
     * configured local root ({@code s3://bucket/key} → {@code <localRoot>/bucket/key}).
     *
     * @throws IllegalArgumentException if {@code s3Url} is not an {@code s3://} URL
     */
    public Path mappedPath(final String s3Url) {
        if (!isS3Url(s3Url)) {
            throw new IllegalArgumentException("Not an s3:// URL: " + s3Url);
        }

        String bestPrefix = null;
        Path bestDir = null;
        for (final Map.Entry<String, Path> entry : mappings.entrySet()) {
            final String prefix = entry.getKey();
            if (s3Url.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
                bestDir = entry.getValue();
            }
        }

        if (bestPrefix != null) {
            final String rest = stripLeadingSlash(s3Url.substring(bestPrefix.length()));
            return rest.isEmpty() ? bestDir : within(bestDir, rest);
        }

        final String rest = s3Url.substring(S3_SCHEME.length());   // bucket/key
        return rest.isEmpty() ? localRoot : within(localRoot, rest);
    }

    /**
     * An S3 key under its local stand-in. A key is a flat name: {@code ..} is two characters of it and a leading
     * slash an empty segment, never a step up or an absolute path, so every segment is escaped as an internal stage
     * path's is and nothing the URL spells reaches outside {@code base}.
     *
     * @param base the root or mapping directory
     * @param key  the bucket and key, slash-separated
     * @return the local path, inside {@code base}
     */
    private static Path within(final Path base, final String key) {
        final Path resolved = StagePathSegments.resolve(base, key);
        if (!resolved.normalize().startsWith(base.normalize())) {
            throw new IllegalArgumentException("S3 location reaches outside its local root: " + key);
        }
        return resolved;
    }

    /**
     * Downloads the object a URL names into {@code local}, unless a file is there already. The object is written to a
     * sibling first and moved into place, so a reader never sees half a file.
     *
     * @return true when {@code local} now holds the object, false when the bucket holds none under that key
     */
    private boolean fetchObject(final String s3Url, final Path local) {
        final String[] bucketKey = bucketAndKey(s3Url);
        if (bucketKey[1].isEmpty()) {
            return false;
        }
        synchronized (lockFor(local)) {
            if (Files.isRegularFile(local)) {
                return true;
            }
            final Path part = local.resolveSibling(local.getFileName() + ".part-" + UUID.randomUUID());
            try {
                Files.createDirectories(local.getParent());
                if (!source.fetch(bucketKey[0], bucketKey[1], part)) {
                    return false;
                }
                try {
                    Files.move(part, local, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (final AtomicMoveNotSupportedException notAtomic) {
                    Files.move(part, local, StandardCopyOption.REPLACE_EXISTING);
                }
                logger.info("Fetched {} to {}", s3Url, local);
                return true;
            } catch (final IOException e) {
                throw new IllegalStateException("Cannot fetch " + s3Url + ": " + e.getMessage(), e);
            } finally {
                try {
                    Files.deleteIfExists(part);
                } catch (final IOException ignored) {
                    logger.debug("Could not remove {}", part);
                }
            }
        }
    }

    /**
     * Downloads every object below a prefix that is missing locally, once per JVM for each prefix and directory. Each
     * key is placed by {@link #mappedPath}, so its mapping and path-safety checks apply; folder markers (keys ending
     * in {@code /}) are skipped.
     */
    private void syncPrefix(final String prefixUrl, final Path localDir) {
        final String marker = localDir.toAbsolutePath().normalize() + "|" + prefixUrl;
        if (SYNCED.contains(marker)) {
            return;
        }
        synchronized (lockFor(localDir)) {
            if (SYNCED.contains(marker)) {
                return;
            }
            final String[] bucketKey = bucketAndKey(prefixUrl);
            final List<String> keys;
            try {
                keys = source.list(bucketKey[0], bucketKey[1]);
            } catch (final IOException e) {
                throw new IllegalStateException("Cannot list " + prefixUrl + ": " + e.getMessage(), e);
            }
            int fetched = 0;
            for (final String key : keys) {
                if (key.endsWith("/")) {
                    continue;
                }
                final String objectUrl = S3_SCHEME + bucketKey[0] + "/" + key;
                final Path target;
                try {
                    target = mappedPath(objectUrl);
                } catch (final IllegalArgumentException outside) {
                    logger.warn("Skipping {} while syncing {}: {}", objectUrl, prefixUrl, outside.getMessage());
                    continue;
                }
                if (!Files.isRegularFile(target) && fetchObject(objectUrl, target)) {
                    fetched++;
                }
            }
            SYNCED.add(marker);
            logger.info("Synced {} ({} of {} objects fetched) to {}", prefixUrl, fetched, keys.size(), localDir);
        }
    }

    /** {@code s3://bucket/key} as its bucket and its key (the key empty for a bare bucket). */
    private static String[] bucketAndKey(final String s3Url) {
        final String rest = s3Url.substring(S3_SCHEME.length());
        final int slash = rest.indexOf('/');
        return slash < 0 ? new String[] {rest, ""} : new String[] {rest.substring(0, slash), rest.substring(slash + 1)};
    }

    private static Object lockFor(final Path local) {
        final String name = local.toAbsolutePath().normalize().toString();
        final Object fresh = new Object();
        final Object held = LOCKS.putIfAbsent(name, fresh);
        return held != null ? held : fresh;
    }

    private static Map<String, Path> parseMappings(final String raw) {
        final Map<String, Path> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (final String token : raw.split(";")) {
            final String entry = token.trim();
            if (entry.isEmpty()) {
                continue;
            }
            final int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                logger.warn("Ignoring malformed stage.s3.localMappings entry (expected 's3prefix=localdir'): {}", entry);
                continue;
            }
            final String prefix = entry.substring(0, eq).trim();
            final String dir = entry.substring(eq + 1).trim();
            if (!isS3Url(prefix)) {
                logger.warn("Ignoring stage.s3.localMappings entry whose key is not an s3:// prefix: {}", entry);
                continue;
            }
            result.put(prefix, Paths.get(dir));
        }
        return result;
    }

    private static String stripLeadingSlash(final String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
