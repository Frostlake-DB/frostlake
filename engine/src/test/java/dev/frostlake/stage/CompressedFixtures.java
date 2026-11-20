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

import io.airlift.compress.bzip2.BZip2HadoopStreams;
import io.airlift.compress.hadoop.HadoopOutputStream;
import io.airlift.compress.zstd.ZstdCompressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

/** Local files of a two-line CSV text in every compression a stage reads, for PUT to upload. */
final class CompressedFixtures {

    /** The text every fixture holds. */
    static final String TEXT = "a,1\nb,2\n";

    private CompressedFixtures() {
    }

    /**
     * Writes the text into a file, compressed.
     *
     * @param dir   the directory
     * @param name  the file's name
     * @param codec GZIP, BZ2, ZSTD, DEFLATE, RAW_DEFLATE or NONE
     * @return the file
     * @throws IOException when the file cannot be written
     */
    static Path write(final Path dir, final String name, final String codec) throws IOException {
        return write(dir, name, codec, TEXT);
    }

    /**
     * Writes a text into a file, compressed.
     *
     * @param dir   the directory
     * @param name  the file's name
     * @param codec GZIP, BZ2, ZSTD, DEFLATE, RAW_DEFLATE or NONE
     * @param text  the text
     * @return the file
     * @throws IOException when the file cannot be written
     */
    static Path write(final Path dir, final String name, final String codec, final String text) throws IOException {
        final Path file = dir.resolve(name);
        Files.write(file, bytes(codec, text));
        return file;
    }

    /**
     * A text, compressed.
     *
     * @param codec GZIP, BZ2, ZSTD, DEFLATE, RAW_DEFLATE or NONE
     * @param text  the text
     * @return the bytes
     * @throws IOException when the bytes cannot be made
     */
    static byte[] bytes(final String codec, final String text) throws IOException {
        final byte[] plain = text.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (codec) {
            case "GZIP":
                try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                    gz.write(plain);
                }
                return out.toByteArray();
            case "DEFLATE":
                try (DeflaterOutputStream zlib = new DeflaterOutputStream(out)) {
                    zlib.write(plain);
                }
                return out.toByteArray();
            case "RAW_DEFLATE":
                final Deflater raw = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                try (DeflaterOutputStream deflate = new DeflaterOutputStream(out, raw)) {
                    deflate.write(plain);
                } finally {
                    raw.end();
                }
                return out.toByteArray();
            case "BZ2":
                final HadoopOutputStream bz2 = new BZip2HadoopStreams().createOutputStream(out);
                try {
                    bz2.write(plain, 0, plain.length);
                    bz2.finish();
                } finally {
                    bz2.close();
                }
                return out.toByteArray();
            case "ZSTD":
                final ZstdCompressor zstd = new ZstdCompressor();
                final byte[] frame = new byte[zstd.maxCompressedLength(plain.length)];
                final int length = zstd.compress(plain, 0, plain.length, frame, 0, frame.length);
                return Arrays.copyOf(frame, length);
            default:
                return plain;
        }
    }
}
