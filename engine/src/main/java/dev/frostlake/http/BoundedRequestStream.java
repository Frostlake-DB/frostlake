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
package dev.frostlake.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A request body read at most so far: reading past the bound fails with {@link RequestTooLargeException} instead of
 * holding the rest, so neither a long body nor a small one that unzips to a large one is ever held whole.
 */
final class BoundedRequestStream extends FilterInputStream {

    /** The fewest bytes of an oversized body read and dropped before it is refused (see {@link #discardRest}). */
    private static final long DISCARDED_AT_LEAST = 16L * 1024 * 1024;

    private final long limit;
    private long read;

    /**
     * @param in    the body
     * @param limit the most bytes it may hold; 0 or less for no bound
     */
    BoundedRequestStream(final InputStream in, final long limit) {
        super(in);
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        final int b = super.read();
        if (b >= 0) {
            count(1);
        }
        return b;
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        final int n = super.read(buffer, offset, length);
        if (n > 0) {
            count(n);
        }
        return n;
    }

    @Override
    public long skip(final long n) throws IOException {
        final long skipped = super.skip(n);
        if (skipped > 0) {
            count(skipped);
        }
        return skipped;
    }

    /**
     * Reads what is left of the body and drops it, at most as many bytes again as the bound and at least
     * {@value #DISCARDED_AT_LEAST}, so that a refusal sent before the client finished sending reaches it: a connection
     * closed on bytes still arriving is reset, and the answer with it. A body longer still is left, its connection
     * closed.
     */
    void discardRest() {
        final byte[] buffer = new byte[8192];
        long left = Math.max(DISCARDED_AT_LEAST, limit);
        try {
            while (left > 0) {
                final int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (n < 0) {
                    return;
                }
                left -= n;
            }
        } catch (final IOException unreadable) {
            // Nothing more to read: the answer goes as it is.
            return;
        }
    }

    private void count(final long n) throws RequestTooLargeException {
        read += n;
        if (limit > 0 && read > limit) {
            throw new RequestTooLargeException(limit);
        }
    }
}
