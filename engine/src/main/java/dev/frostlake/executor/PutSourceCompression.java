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

package dev.frostlake.executor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * How a PUT reads the compression of a file it uploads, as its SOURCE_COMPRESSION and source_compression column
 * name it (live-verified):
 *
 * <ul>
 *   <li>AUTO_DETECT, the default, reads the file's first bytes first — gzip, bzip2, zstd and zlib (DEFLATE) are
 *       known by them whatever the file is called, so {@code hgz.txt} holding gzip is GZIP and {@code x.gz} holding
 *       bzip2 is BZIP2 — and its extension second, in any case: {@code .gz}, {@code .bz2}, {@code .zst},
 *       {@code .deflate}, {@code .raw_deflate}, {@code .br}, {@code .parquet} and {@code .orc}. A raw deflate
 *       stream, a Parquet and an ORC file have no mark the detection reads, and {@code .Z} and {@code .zip} are not
 *       known at all;</li>
 *   <li>xz, lzip and lzma are known and not supported: the file's row answers ERROR, {@code Copy command does not
 *       support compression type XZ.};</li>
 *   <li>a named SOURCE_COMPRESSION is taken as written, without reading the file: {@code BZ2} and {@code BZIP2}
 *       both name BZIP2, and NONE gzips even a gzip file under AUTO_COMPRESS. AUTO is no such name ({@code Copy
 *       command does not recognize compression type AUTO.}).</li>
 * </ul>
 */
public final class PutSourceCompression {

    /** The name of an uncompressed source. */
    public static final String NONE = "NONE";

    /** SOURCE_COMPRESSION's default: read the file. */
    public static final String AUTO_DETECT = "AUTO_DETECT";

    /** The compressions a PUT uploads as they are. */
    private static final Set<String> SUPPORTED = Set.of("GZIP", "BZIP2", "ZSTD", "DEFLATE", "RAW_DEFLATE", "BROTLI",
        "PARQUET", "ORC");

    /** The compressions a PUT knows and refuses. */
    private static final Set<String> UNSUPPORTED = Set.of("XZ", "LZIP", "LZMA", "LZO", "COMPRESS");

    /** How many first bytes the detection reads. */
    private static final int HEAD = 6;

    private PutSourceCompression() {
    }

    /**
     * The compression a SOURCE_COMPRESSION value names.
     *
     * @param written the value as written, quotes removed
     * @return {@link #AUTO_DETECT}, {@link #NONE} or a supported compression's name
     * @throws RuntimeException for a compression the PUT refuses or does not know
     */
    public static String named(final String written) {
        final String name = written.toUpperCase(Locale.ROOT);
        if (AUTO_DETECT.equals(name) || NONE.equals(name)) {
            return name;
        }
        final String type = "BZ2".equals(name) ? "BZIP2" : name;
        if (SUPPORTED.contains(type)) {
            return type;
        }
        if (UNSUPPORTED.contains(type)) {
            throw new RuntimeException(unsupported(type));
        }
        throw new RuntimeException("Copy command does not recognize compression type " + written + ".");
    }

    /**
     * The message of a file whose compression a PUT does not upload.
     *
     * @param type the compression
     * @return the message
     */
    public static String unsupported(final String type) {
        return "Copy command does not support compression type " + type + ".";
    }

    /**
     * Whether a PUT uploads files of a compression it detected.
     *
     * @param type the compression
     * @return false for one it refuses
     */
    public static boolean isSupported(final String type) {
        return !UNSUPPORTED.contains(type);
    }

    /**
     * The compression AUTO_DETECT reads off a file.
     *
     * @param file the local file
     * @return the compression's name, or {@link #NONE}
     * @throws IOException when the file cannot be read
     */
    public static String detect(final Path file) throws IOException {
        final byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(HEAD);
        }
        final String byContent = byContent(head);
        if (byContent != null) {
            return byContent;
        }
        final String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        final int dot = name.lastIndexOf('.');
        final String extension = dot < 0 ? "" : name.substring(dot + 1);
        switch (extension) {
            case "gz":
                return "GZIP";
            case "bz2":
                return "BZIP2";
            case "zst":
                return "ZSTD";
            case "deflate":
                return "DEFLATE";
            case "raw_deflate":
                return "RAW_DEFLATE";
            case "br":
                return "BROTLI";
            case "parquet":
                return "PARQUET";
            case "orc":
                return "ORC";
            case "xz":
                return "XZ";
            case "lz":
                return "LZIP";
            case "lzma":
                return "LZMA";
            default:
                return NONE;
        }
    }

    /** The compression a file's first bytes mark it with, or null where they mark none. */
    private static String byContent(final byte[] head) {
        if (head.length >= 2 && (head[0] & 0xff) == 0x1f && (head[1] & 0xff) == 0x8b) {
            return "GZIP";
        }
        if (head.length >= 3 && head[0] == 'B' && head[1] == 'Z' && head[2] == 'h') {
            return "BZIP2";
        }
        if (head.length >= 4 && (head[0] & 0xff) == 0x28 && (head[1] & 0xff) == 0xb5 && (head[2] & 0xff) == 0x2f
                && (head[3] & 0xff) == 0xfd) {
            return "ZSTD";
        }
        if (head.length >= 6 && (head[0] & 0xff) == 0xfd && head[1] == '7' && head[2] == 'z' && head[3] == 'X'
                && head[4] == 'Z' && head[5] == 0) {
            return "XZ";
        }
        if (head.length >= 4 && head[0] == 'L' && head[1] == 'Z' && head[2] == 'I' && head[3] == 'P') {
            return "LZIP";
        }
        if (head.length >= 2 && isZlibHeader(head[0] & 0xff, head[1] & 0xff)) {
            return "DEFLATE";
        }
        return null;
    }

    /** Whether two bytes open a zlib stream: method 8 with a window of at most 32K, the pair a multiple of 31. */
    private static boolean isZlibHeader(final int cmf, final int flg) {
        return (cmf & 0x0f) == 8 && (cmf >> 4) <= 7 && ((cmf << 8) | flg) % 31 == 0;
    }
}
