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

package dev.frostlake.functions.scalar.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * How a staged file reads as the object its stage stores.
 *
 * <p>An internal stage with no server-side encryption (the user stage, a table stage, and a named internal stage
 * without {@code ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')}) keeps every file encrypted by the client, and what
 * {@code LIST}, {@code DIRECTORY(@stage)}, {@code FL_GET_SIZE} and {@code FL_GET_ETAG} report is that stored
 * object, not the file's content: its size is the content padded to the cipher's 16-byte block (a whole block added
 * when the content already fills one), and its digest is the encrypted bytes' MD5, which is fresh for every upload —
 * two files of identical content never share it, a file put again gets a new one, and it is never the content's
 * MD5. Every reader of one upload agrees on it (live-verified). Any other stage reports the content's size and MD5.
 */
public final class StoredStageObject {

    /** The block an internal stage's cipher pads a file to. */
    private static final long CIPHER_BLOCK = 16L;

    /** What sets an encrypted object's digest apart from the digest of any content. */
    private static final byte[] STORED_OBJECT_SALT = "frostlake-stored-object".getBytes(StandardCharsets.UTF_8);

    private StoredStageObject() {
    }

    /**
     * The size a stage reports for a file.
     *
     * @param contentBytes    the file's own length
     * @param clientEncrypted whether the stage encrypts its files on the client
     * @return the stored size: the content padded to the cipher block on a client-encrypted stage, else the length
     */
    public static long size(final long contentBytes, final boolean clientEncrypted) {
        return clientEncrypted ? (contentBytes / CIPHER_BLOCK + 1) * CIPHER_BLOCK : contentBytes;
    }

    /**
     * The digest a stage reports for a file, as LIST's {@code md5}, a directory table's {@code MD5} and
     * {@code ETAG}, and a FILE's {@code ETAG} carry it.
     *
     * @param file            the staged file
     * @param clientEncrypted whether the stage encrypts its files on the client
     * @return the lower-case hex MD5: of the content, or on a client-encrypted stage of this upload of it
     * @throws IOException when the file cannot be read
     */
    public static String digest(final Path file, final boolean clientEncrypted) throws IOException {
        final MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
        if (clientEncrypted) {
            // One upload is one file written under one name at one instant: the digest keys off both, to the
            // millisecond a copy of the stage keeps, so it holds for every read of the upload and of such a copy, and
            // changes with the next upload.
            md5.update(STORED_OBJECT_SALT);
            md5.update(String.valueOf(file.getFileName()).getBytes(StandardCharsets.UTF_8));
            md5.update(Long.toString(Files.getLastModifiedTime(file).toMillis()).getBytes(StandardCharsets.UTF_8));
        }
        final byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.read(buffer);
            while (read >= 0) {
                md5.update(buffer, 0, read);
                read = in.read(buffer);
            }
        }
        return HexFormat.of().formatHex(md5.digest());
    }
}
