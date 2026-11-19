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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;

/**
 * The CLOB a connection makes with {@code createClob()}: a text held in memory that starts empty and is written
 * through {@code setString} or one of its streams, then bound with {@code setClob}. Positions count from 1, as
 * {@link Clob} gives them.
 */
public final class FrostlakeClob implements NClob {

    /** The text, or null once the CLOB is freed. */
    private StringBuilder text = new StringBuilder();

    /** The text held, for the streams that write into it. */
    StringBuilder buffer() throws SQLException {
        if (text == null) {
            throw new SQLException("The CLOB has been freed.");
        }
        return text;
    }

    @Override
    public long length() throws SQLException {
        return buffer().length();
    }

    @Override
    public String getSubString(final long pos, final int length) throws SQLException {
        final StringBuilder held = buffer();
        if (pos < 1 || pos > held.length() + 1L || length < 0) {
            throw new SQLException("Invalid position " + pos + " or length " + length + " in a CLOB of "
                + held.length() + " characters.");
        }
        final int from = (int) pos - 1;
        return held.substring(from, (int) Math.min(held.length(), (long) from + length));
    }

    @Override
    public Reader getCharacterStream() throws SQLException {
        return new StringReader(buffer().toString());
    }

    @Override
    public Reader getCharacterStream(final long pos, final long length) throws SQLException {
        return new StringReader(getSubString(pos, (int) Math.min(Integer.MAX_VALUE, length)));
    }

    @Override
    public InputStream getAsciiStream() throws SQLException {
        return new ByteArrayInputStream(buffer().toString().getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public long position(final String searchstr, final long start) throws SQLException {
        if (searchstr == null || start < 1) {
            throw new SQLException("Invalid search text or start " + start + ".");
        }
        final int found = buffer().indexOf(searchstr, (int) Math.min(Integer.MAX_VALUE, start - 1));
        return found < 0 ? -1 : found + 1L;
    }

    @Override
    public long position(final Clob searchstr, final long start) throws SQLException {
        if (searchstr == null) {
            throw new SQLException("Invalid search text or start " + start + ".");
        }
        return position(searchstr.getSubString(1, (int) Math.min(Integer.MAX_VALUE, searchstr.length())), start);
    }

    @Override
    public int setString(final long pos, final String str) throws SQLException {
        if (str == null) {
            throw new SQLException("A CLOB takes no null text.");
        }
        return setString(pos, str, 0, str.length());
    }

    @Override
    public int setString(final long pos, final String str, final int offset, final int len) throws SQLException {
        final StringBuilder held = buffer();
        if (str == null || pos < 1 || pos > held.length() + 1L || offset < 0 || len < 0
                || offset + len > str.length()) {
            throw new SQLException("Invalid position " + pos + ", offset " + offset + " or length " + len
                + " for a CLOB of " + held.length() + " characters.");
        }
        final int from = (int) pos - 1;
        held.replace(from, Math.min(held.length(), from + len), str.substring(offset, offset + len));
        return len;
    }

    @Override
    public OutputStream setAsciiStream(final long pos) throws SQLException {
        return new ClobAsciiStream(this, requireWritable(pos));
    }

    @Override
    public Writer setCharacterStream(final long pos) throws SQLException {
        return new ClobWriter(this, requireWritable(pos));
    }

    @Override
    public void truncate(final long len) throws SQLException {
        final StringBuilder held = buffer();
        if (len < 0 || len > held.length()) {
            throw new SQLException("Invalid length " + len + " for a CLOB of " + held.length() + " characters.");
        }
        held.setLength((int) len);
    }

    @Override
    public void free() {
        text = null;
    }

    @Override
    public String toString() {
        return text == null ? "" : text.toString();
    }

    /** The position a stream starts writing at, refused where it lies outside the text and its end. */
    private long requireWritable(final long pos) throws SQLException {
        if (pos < 1 || pos > buffer().length() + 1L) {
            throw new SQLException("Invalid position " + pos + " for a CLOB of " + buffer().length()
                + " characters.");
        }
        return pos;
    }
}
