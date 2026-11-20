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
package dev.frostlake.jdbc;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;

/** The stream of characters {@link FrostlakeClob#setCharacterStream} hands out: each write lands in the CLOB. */
final class ClobWriter extends Writer {

    private final FrostlakeClob clob;
    private long position;

    ClobWriter(final FrostlakeClob clob, final long position) {
        this.clob = clob;
        this.position = position;
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        try {
            position += clob.setString(position, new String(cbuf, off, len));
        } catch (final SQLException refused) {
            throw new IOException(refused.getMessage(), refused);
        }
    }

    @Override
    public void flush() {
        // Every write is in the CLOB already.
    }

    @Override
    public void close() {
        // Nothing is held open.
    }
}
