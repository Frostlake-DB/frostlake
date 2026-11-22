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

package dev.frostlake.executor.copy;

import io.airlift.compress.bzip2.BZip2HadoopStreams;
import io.airlift.compress.zstd.ZstdInputStream;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Byte access to staged files, decompressed by the file format's COMPRESSION (live-verified):
 *
 * <ul>
 *   <li>AUTO, the default, reads gzip and zstd by their first bytes whatever the file is called — an unload written
 *       with {@code SINGLE = TRUE} is named {@code data} however it was compressed — and bzip2 when the file both
 *       starts with bzip2's mark and is named {@code .bz2}. Anything else is read as it is: a deflate stream, a
 *       {@code .gz} file holding plain text, a bzip2 file of another name;</li>
 *   <li>GZIP and DEFLATE each read a gzip and a zlib stream alike; BZ2, ZSTD and RAW_DEFLATE their own; a file the
 *       named codec cannot read refuses as {@code Invalid data encountered during decompression for file:
 *       '<name>',compression type used: 'BZ2', cause: '...'};</li>
 *   <li>NONE reads the bytes as they are, gzip included.</li>
 * </ul>
 */
public final class StagedFileIo {

    /**
     * The mark of the first byte of a sequence the file's encoding cannot read, the byte's value added to it, for a
     * reader to find it and settle it by REPLACE_INVALID_CHARACTERS (see {@link UnreadableMarkingReader}).
     */
    public static final char UNREADABLE_START = '\uF600';

    /** The byte order mark a file may start with, as its characters read it. */
    public static final char BYTE_ORDER_MARK = '\uFEFF';

    /** The mark of a later byte of the same unreadable sequence, the byte's value added to it. */
    public static final char UNREADABLE_MORE = '\uF700';

    /** Past the last mark. */
    private static final char UNREADABLE_END = '\uF800';

    /** The character sets a load's ENCODING names, by the account's names, as the JDK names them (documented). */
    private static final Map<String, String> ENCODINGS = new HashMap<String, String>();

    static {
        final String[][] pairs = {
            {"BIG5", "Big5"}, {"EUCJP", "EUC-JP"}, {"EUCKR", "EUC-KR"}, {"GB18030", "GB18030"},
            {"IBM420", "IBM420"}, {"IBM424", "IBM424"}, {"IBM949", "x-IBM949"},
            {"ISO2022CN", "ISO-2022-CN"}, {"ISO2022JP", "ISO-2022-JP"}, {"ISO2022KR", "ISO-2022-KR"},
            {"ISO88591", "ISO-8859-1"}, {"ISO88592", "ISO-8859-2"}, {"ISO88595", "ISO-8859-5"},
            {"ISO88596", "ISO-8859-6"}, {"ISO88597", "ISO-8859-7"}, {"ISO88598", "ISO-8859-8"},
            {"ISO88599", "ISO-8859-9"}, {"ISO885915", "ISO-8859-15"}, {"KOI8R", "KOI8-R"},
            {"SHIFTJIS", "Shift_JIS"}, {"UTF8", "UTF-8"}, {"UTF16", "UTF-16"}, {"UTF16BE", "UTF-16BE"},
            {"UTF16LE", "UTF-16LE"}, {"UTF32", "UTF-32"}, {"UTF32BE", "UTF-32BE"}, {"UTF32LE", "UTF-32LE"},
            {"WINDOWS874", "x-windows-874"}, {"WINDOWS949", "x-windows-949"}, {"WINDOWS1250", "windows-1250"},
            {"WINDOWS1251", "windows-1251"}, {"WINDOWS1252", "windows-1252"}, {"WINDOWS1253", "windows-1253"},
            {"WINDOWS1254", "windows-1254"}, {"WINDOWS1255", "windows-1255"}, {"WINDOWS1256", "windows-1256"},
        };
        for (final String[] pair : pairs) {
            ENCODINGS.put(pair[0], pair[1]);
        }
    }

    private StagedFileIo() {
    }

    /**
     * The character set an ENCODING names, by the account's name for it (ISO88591) or the set's own (ISO-8859-1),
     * in any case.
     *
     * @param encoding the name as written
     * @return the set, or null for a name the account does not list
     */
    public static Charset charsetFor(final String encoding) {
        final String name = ENCODINGS.get(encodingKey(encoding));
        if (name == null) {
            return null;
        }
        try {
            return Charset.forName(name);
        } catch (final UnsupportedCharsetException absent) {
            return null;
        }
    }

    /**
     * A staged file's bytes as COMPRESSION AUTO reads them.
     *
     * @param file the staged file
     * @return its bytes, decompressed
     * @throws IOException when the file cannot be read
     */
    public static InputStream inputStream(final Path file) throws IOException {
        return inputStream(file, null);
    }

    /**
     * A staged file's bytes as a file format's COMPRESSION reads them.
     *
     * @param file        the staged file
     * @param compression the format's COMPRESSION, upper case; null for AUTO
     * @return its bytes, decompressed
     * @throws IOException when the file cannot be read
     */
    public static InputStream inputStream(final Path file, final String compression) throws IOException {
        final String codec = compression == null ? "AUTO" : compression;
        if ("NONE".equals(codec)) {
            return Files.newInputStream(file);
        }
        final byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(4);
        }
        final String used;
        if ("AUTO".equals(codec)) {
            if (isGzip(head)) {
                used = "GZIP";
            } else if (isZstd(head)) {
                used = "ZSTD";
            } else if (isBzip2(head) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bz2")) {
                used = "BZ2";
            } else {
                return Files.newInputStream(file);
            }
        } else {
            used = codec;
        }
        return new ByteArrayInputStream(decompressed(file, used, head));
    }

    /** A staged file's bytes decompressed by one codec, or the account's refusal where that codec cannot read it. */
    private static byte[] decompressed(final Path file, final String codec, final byte[] head) throws IOException {
        final String name = file.getFileName().toString();
        switch (codec) {
            case "GZIP":
            case "DEFLATE":
                try (InputStream in = isGzip(head) ? new GZIPInputStream(Files.newInputStream(file))
                        : new InflaterInputStream(Files.newInputStream(file))) {
                    return in.readAllBytes();
                } catch (final IOException unreadable) {
                    throw undecodable(name, codec, "data error");
                }
            case "RAW_DEFLATE":
                final Inflater raw = new Inflater(true);
                try (InputStream in = new InflaterInputStream(Files.newInputStream(file), raw)) {
                    return in.readAllBytes();
                } catch (final IOException unreadable) {
                    throw undecodable(name, codec, "data error");
                } finally {
                    raw.end();
                }
            case "BZ2":
                if (!isBzip2(head)) {
                    throw undecodable(name, codec, "data error magic");
                }
                try (InputStream in = new BZip2HadoopStreams().createInputStream(Files.newInputStream(file))) {
                    return in.readAllBytes();
                } catch (final IOException | RuntimeException unreadable) {
                    throw undecodable(name, codec, "data error");
                }
            case "ZSTD":
                if (!isZstd(head)) {
                    throw undecodable(name, codec, "Unknown frame descriptor");
                }
                try (InputStream in = new ZstdInputStream(Files.newInputStream(file))) {
                    return in.readAllBytes();
                } catch (final IOException | RuntimeException unreadable) {
                    throw undecodable(name, codec, "data error");
                }
            default:
                throw new RuntimeException("Loading " + codec + "-compressed files is not supported by this engine"
                    + " (supported: AUTO, GZIP, BZ2, ZSTD, DEFLATE, RAW_DEFLATE, NONE)");
        }
    }

    /** The account's refusal of a file the named codec cannot read, the file named by its bare name. */
    private static RuntimeException undecodable(final String name, final String codec, final String cause) {
        return new RuntimeException("Invalid data encountered during decompression for file: '" + name
            + "',compression type used: '" + codec + "', cause: '" + cause + "'");
    }

    private static boolean isGzip(final byte[] head) {
        return head.length >= 2 && (head[0] & 0xff) == 0x1f && (head[1] & 0xff) == 0x8b;
    }

    private static boolean isZstd(final byte[] head) {
        return head.length >= 4 && (head[0] & 0xff) == 0x28 && (head[1] & 0xff) == 0xb5
            && (head[2] & 0xff) == 0x2f && (head[3] & 0xff) == 0xfd;
    }

    private static boolean isBzip2(final byte[] head) {
        return head.length >= 3 && head[0] == 'B' && head[1] == 'Z' && head[2] == 'h';
    }

    /**
     * Whether an ENCODING names one of the account's character sets, by the account's name or the set's own.
     *
     * @param encoding the name as written
     * @return whether the account lists it
     */
    public static boolean isEncodingName(final String encoding) {
        return encoding != null && ENCODINGS.containsKey(encodingKey(encoding));
    }

    /** A character set's name as the table keys it: upper case, without hyphens and underscores. */
    private static String encodingKey(final String encoding) {
        return encoding.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    public static BufferedReader reader(final Path file) throws IOException {
        return reader(file, (String) null);
    }

    /**
     * A staged file's text as UTF-8, decompressed by a file format's COMPRESSION.
     *
     * @param file        the staged file
     * @param compression the format's COMPRESSION, upper case; null for AUTO
     * @return the reader
     * @throws IOException when the file cannot be opened
     */
    public static BufferedReader reader(final Path file, final String compression) throws IOException {
        return new BufferedReader(new InputStreamReader(inputStream(file, compression), StandardCharsets.UTF_8));
    }

    /**
     * A staged file's text in a character set, every byte the set cannot read kept as a mark of it (see
     * {@link #UNREADABLE_START}).
     *
     * @param file    the staged file
     * @param charset its character set
     * @return the reader
     * @throws IOException when the file cannot be opened
     */
    public static BufferedReader reader(final Path file, final Charset charset) throws IOException {
        return reader(file, charset, null);
    }

    /**
     * A staged file's text in a character set, decompressed by a file format's COMPRESSION, every byte the set
     * cannot read kept as a mark of it.
     *
     * @param file        the staged file
     * @param charset     its character set
     * @param compression the format's COMPRESSION, upper case; null for AUTO
     * @return the reader
     * @throws IOException when the file cannot be opened
     */
    public static BufferedReader reader(final Path file, final Charset charset, final String compression)
            throws IOException {
        return new BufferedReader(new UnreadableMarkingReader(inputStream(file, compression), charset));
    }

    /**
     * A staged file's whole text in a character set, every byte the set cannot read kept as a mark of it.
     *
     * @param file    the staged file
     * @param charset its character set
     * @return the text
     * @throws IOException when the file cannot be read
     */
    public static String readMarked(final Path file, final Charset charset) throws IOException {
        return readMarked(file, charset, null);
    }

    /**
     * A staged file's whole text in a character set, decompressed by a file format's COMPRESSION, every byte the set
     * cannot read kept as a mark of it.
     *
     * @param file        the staged file
     * @param charset     its character set
     * @param compression the format's COMPRESSION, upper case; null for AUTO
     * @return the text
     * @throws IOException when the file cannot be read
     */
    public static String readMarked(final Path file, final Charset charset, final String compression)
            throws IOException {
        final StringBuilder text = new StringBuilder();
        try (BufferedReader reader = reader(file, charset, compression)) {
            final char[] buffer = new char[8192];
            int read = reader.read(buffer);
            while (read >= 0) {
                text.append(buffer, 0, read);
                read = reader.read(buffer);
            }
        }
        return text.toString();
    }

    /**
     * Bytes read as UTF-8, every byte that is no part of it kept as a mark of it, as {@link #readMarked} keeps them.
     *
     * @param bytes the bytes
     * @return the text
     */
    public static String markedUtf8(final byte[] bytes) {
        final StringBuilder text = new StringBuilder(bytes.length);
        try (Reader reader = new UnreadableMarkingReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            final char[] buffer = new char[1024];
            int read = reader.read(buffer);
            while (read >= 0) {
                text.append(buffer, 0, read);
                read = reader.read(buffer);
            }
        } catch (final IOException unread) {
            throw new UncheckedIOException(unread);
        }
        return text.toString();
    }

    /** Whether a character is the mark of a byte its file's encoding could not read. */
    public static boolean isUnreadable(final char c) {
        return c >= UNREADABLE_START && c < UNREADABLE_END;
    }

    /**
     * Where a text holds the first mark of an unreadable byte.
     *
     * @param text the text
     * @return its index, or -1 when it holds none
     */
    public static int firstUnreadable(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isUnreadable(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The byte a mark keeps, as the account's refusals spell it: {@code 0xE9}.
     *
     * @param mark the mark
     * @return its byte in upper-case hex
     */
    public static String unreadableByte(final char mark) {
        return String.format(Locale.ROOT, "0x%02X", Integer.valueOf(mark & 0xFF));
    }

    /**
     * A text as a refusal quotes it, each unreadable byte spelled as its value: {@code caf0xE9}.
     *
     * @param text the text
     * @return the text quoted
     */
    public static String unreadableShown(final String text) {
        final StringBuilder shown = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (isUnreadable(c)) {
                shown.append(unreadableByte(c));
            } else {
                shown.append(c);
            }
        }
        return shown.toString();
    }

    /**
     * A record format's refusal of a file holding a byte that is no part of UTF-8, in the account's words: {@code Error
     * parsing JSON: invalid UTF-8 sequence: 0xE9}, then the file, the line and the byte's character within it, and the
     * row and column being read (live-verified, for JSON and XML).
     *
     * @param format     the format, as the refusal names it: JSON, XML
     * @param marked     the file's text, each unreadable byte kept as its mark
     * @param unreadable where the text holds the first mark
     * @param shownName  the file, as the refusal names it
     * @return the refusal
     */
    public static RuntimeException unreadableRefusal(final String format, final String marked, final int unreadable,
                                                     final String shownName) {
        return unreadableRefusal(format, marked, unreadable, shownName, true);
    }

    /**
     * {@link #unreadableRefusal(String, String, int, String)}, the column named only where the reading query reads
     * one: under a query that reads none of the file's positional columns the refusal ends {@code Row 0, column }
     * (live-verified).
     *
     * @param format      the format, as the refusal names it: JSON, XML
     * @param marked      the file's text, each unreadable byte kept as its mark
     * @param unreadable  where the text holds the first mark
     * @param shownName   the file, as the refusal names it
     * @param namesColumn whether the refusal names the column, $1
     * @return the refusal
     */
    public static RuntimeException unreadableRefusal(final String format, final String marked, final int unreadable,
                                                     final String shownName, final boolean namesColumn) {
        final int lineStart = marked.lastIndexOf('\n', unreadable - 1) + 1;
        int line = 1;
        for (int i = 0; i < lineStart; i++) {
            line += marked.charAt(i) == '\n' ? 1 : 0;
        }
        return new RuntimeException("Error parsing " + format + ": invalid UTF-8 sequence: "
            + unreadableByte(marked.charAt(unreadable)) + "\n  File '" + shownName + "', line " + line
            + ", character " + (unreadable - lineStart + 1) + "\n  Row 0, column " + (namesColumn ? "$1" : ""));
    }

    /**
     * A text with each unreadable byte sequence replaced by U+FFFD, as REPLACE_INVALID_CHARACTERS = TRUE reads it.
     *
     * @param text the text
     * @return the text replaced
     */
    public static String unreadableReplaced(final String text) {
        if (firstUnreadable(text) < 0) {
            return text;
        }
        final StringBuilder replaced = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c >= UNREADABLE_START && c < UNREADABLE_MORE) {
                replaced.append('\uFFFD');
            } else if (!isUnreadable(c)) {
                replaced.append(c);
            }
        }
        return replaced.toString();
    }

    /**
     * A file's text without the byte order mark it starts with, if it starts with one.
     *
     * @param text the text
     * @return the text
     */
    public static String withoutByteOrderMark(final String text) {
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }

    /**
     * Moves a reader past the byte order mark its file starts with, if it starts with one.
     *
     * @param reader the reader, at the file's start
     * @return the reader
     * @throws IOException when the file cannot be read
     */
    public static BufferedReader pastByteOrderMark(final BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
        return reader;
    }

    public static String readString(final Path file) throws IOException {
        try (final InputStream in = inputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
