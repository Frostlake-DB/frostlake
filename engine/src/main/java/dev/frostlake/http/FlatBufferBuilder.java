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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The part of a FlatBuffers builder Arrow's IPC messages take (see {@link ArrowStream}): tables, strings, vectors of
 * offsets and of 16-byte structs, written back to front as the FlatBuffers builder writes them, little-endian. A child
 * is written before its parent, so every offset points forward; a table's vtable is written just before it and is
 * never shared.
 */
final class FlatBufferBuilder {

    private static final int INT_SIZE = 4;
    private static final int SHORT_SIZE = 2;
    private static final int VTABLE_HEADER_FIELDS = 2;

    /** The bytes written so far occupy {@code buf[space..buf.length)}. */
    private byte[] buf;
    private int space;
    private int minAlign = 1;
    private int[] vtable = new int[0];
    private int objectStart;
    private int vectorElements;

    FlatBufferBuilder(final int initialSize) {
        buf = new byte[Math.max(initialSize, 16)];
        space = buf.length;
    }

    /** How many bytes are written: an object's position, counted from the end. */
    int offset() {
        return buf.length - space;
    }

    /** Pads so that {@code size} bytes written after {@code additional} more are aligned to {@code size}. */
    private void prep(final int size, final int additional) {
        if (size > minAlign) {
            minAlign = size;
        }
        final int alignSize = (~(buf.length - space + additional) + 1) & (size - 1);
        while (space < alignSize + size + additional) {
            final int oldLength = buf.length;
            final byte[] grown = new byte[oldLength * 2];
            System.arraycopy(buf, 0, grown, oldLength, oldLength);
            space += oldLength;
            buf = grown;
        }
        for (int i = 0; i < alignSize; i++) {
            buf[--space] = 0;
        }
    }

    private void putByte(final int value) {
        buf[--space] = (byte) value;
    }

    private void putShort(final int value) {
        space -= SHORT_SIZE;
        buf[space] = (byte) value;
        buf[space + 1] = (byte) (value >> 8);
    }

    private void putInt(final int value) {
        space -= INT_SIZE;
        writeInt(space, value);
    }

    private void putLong(final long value) {
        space -= 8;
        for (int i = 0; i < 8; i++) {
            buf[space + i] = (byte) (value >> (8 * i));
        }
    }

    private void writeInt(final int at, final int value) {
        buf[at] = (byte) value;
        buf[at + 1] = (byte) (value >> 8);
        buf[at + 2] = (byte) (value >> 16);
        buf[at + 3] = (byte) (value >> 24);
    }

    private void addShort(final int value) {
        prep(SHORT_SIZE, 0);
        putShort(value);
    }

    private void addInt(final int value) {
        prep(INT_SIZE, 0);
        putInt(value);
    }

    private void addOffset(final int target) {
        prep(INT_SIZE, 0);
        putInt(offset() - target + INT_SIZE);
    }

    /**
     * Writes a string, NUL-terminated.
     *
     * @param text the text
     * @return its offset
     */
    int createString(final String text) {
        final byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        prep(1, 0);
        putByte(0);
        startVector(1, utf8.length, 1);
        space -= utf8.length;
        System.arraycopy(utf8, 0, buf, space, utf8.length);
        return endVector();
    }

    /**
     * Starts a vector, whose elements are then added last to first.
     *
     * @param elementSize each element's size
     * @param count       how many elements
     * @param alignment   the elements' alignment
     */
    void startVector(final int elementSize, final int count, final int alignment) {
        vectorElements = count;
        prep(INT_SIZE, elementSize * count);
        prep(alignment, elementSize * count);
    }

    /**
     * Ends a vector.
     *
     * @return its offset
     */
    int endVector() {
        putInt(vectorElements);
        return offset();
    }

    /**
     * A vector of offsets, each to an object written before.
     *
     * @param offsets the objects' offsets, in order
     * @return the vector's offset
     */
    int createOffsetVector(final int[] offsets) {
        startVector(INT_SIZE, offsets.length, INT_SIZE);
        for (int i = offsets.length - 1; i >= 0; i--) {
            addOffset(offsets[i]);
        }
        return endVector();
    }

    /**
     * A vector of structs of two longs each - Arrow's FieldNode and Buffer.
     *
     * @param pairs the structs' fields, two longs each, in order
     * @return the vector's offset
     */
    int createLongPairVector(final long[] pairs) {
        final int count = pairs.length / 2;
        startVector(16, count, 8);
        for (int i = count - 1; i >= 0; i--) {
            prep(8, 16);
            putLong(pairs[2 * i + 1]);
            putLong(pairs[2 * i]);
        }
        return endVector();
    }

    /**
     * Starts a table of {@code fields} slots.
     *
     * @param fields how many slots its schema declares
     */
    void startTable(final int fields) {
        vtable = new int[fields];
        objectStart = offset();
    }

    void addByteField(final int slot, final int value) {
        prep(1, 0);
        putByte(value);
        vtable[slot] = offset();
    }

    void addShortField(final int slot, final int value) {
        addShort(value);
        vtable[slot] = offset();
    }

    void addIntField(final int slot, final int value) {
        addInt(value);
        vtable[slot] = offset();
    }

    void addLongField(final int slot, final long value) {
        prep(8, 0);
        putLong(value);
        vtable[slot] = offset();
    }

    void addOffsetField(final int slot, final int target) {
        addOffset(target);
        vtable[slot] = offset();
    }

    /**
     * Ends a table, writing its vtable before it.
     *
     * @return the table's offset
     */
    int endTable() {
        addInt(0);
        final int tableAt = offset();
        int last = vtable.length - 1;
        while (last >= 0 && vtable[last] == 0) {
            last--;
        }
        for (int i = last; i >= 0; i--) {
            addShort(vtable[i] != 0 ? tableAt - vtable[i] : 0);
        }
        addShort(tableAt - objectStart);
        addShort((last + 1 + VTABLE_HEADER_FIELDS) * SHORT_SIZE);
        writeInt(buf.length - tableAt, offset() - tableAt);
        vtable = new int[0];
        return tableAt;
    }

    /**
     * Ends the buffer with its root table.
     *
     * @param root the root table's offset
     * @return the buffer's bytes
     */
    byte[] finish(final int root) {
        prep(minAlign, INT_SIZE);
        addOffset(root);
        return Arrays.copyOfRange(buf, space, buf.length);
    }
}
