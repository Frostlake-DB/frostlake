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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One field of an Arrow record batch and its column of values (see {@link ArrowStream}): a leaf with its buffers -
 * the validity bitmap first, then the offsets and the data its type takes - or a struct or a fixed-size list, whose
 * buffer is its bitmap and whose children carry the values: a struct's one value per row in each child, a fixed-size
 * list's {@code listSize} values per row in its one child. The values are added row by row through the {@code add}
 * methods.
 */
final class ArrowField {

    /** Arrow's Type union ids this writer uses. */
    static final int INT = 2;
    static final int FLOATING_POINT = 3;
    static final int BINARY = 4;
    static final int UTF8 = 5;
    static final int BOOL = 6;
    static final int DECIMAL = 7;
    static final int DATE = 8;
    static final int STRUCT = 13;
    static final int FIXED_SIZE_LIST = 16;

    private final String name;
    private final int typeId;
    private final int bitWidth;
    private final int precision;
    private final int scale;
    private int listSize;
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final List<ArrowField> children = new ArrayList<>();
    private final ByteArrayOutputStream validity = new ByteArrayOutputStream();
    private final ByteArrayOutputStream offsets = new ByteArrayOutputStream();
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private int rows;
    private int nulls;
    private int validityByte;
    private int dataBits;
    private int dataByte;

    private ArrowField(final String name, final int typeId, final int bitWidth, final int precision, final int scale) {
        this.name = name;
        this.typeId = typeId;
        this.bitWidth = bitWidth;
        this.precision = precision;
        this.scale = scale;
        if (typeId == UTF8 || typeId == BINARY) {
            writeInt(offsets, 0);
        }
    }

    /** A signed integer field of {@code bitWidth} bits. */
    static ArrowField integer(final String name, final int bitWidth) {
        return new ArrowField(name, INT, bitWidth, 0, 0);
    }

    static ArrowField float64(final String name) {
        return new ArrowField(name, FLOATING_POINT, 64, 0, 0);
    }

    static ArrowField float32(final String name) {
        return new ArrowField(name, FLOATING_POINT, 32, 0, 0);
    }

    static ArrowField utf8(final String name) {
        return new ArrowField(name, UTF8, 0, 0, 0);
    }

    static ArrowField binary(final String name) {
        return new ArrowField(name, BINARY, 0, 0, 0);
    }

    static ArrowField bool(final String name) {
        return new ArrowField(name, BOOL, 0, 0, 0);
    }

    /** A 128-bit decimal of {@code precision} digits and {@code scale}. */
    static ArrowField decimal(final String name, final int precision, final int scale) {
        return new ArrowField(name, DECIMAL, 128, precision, scale);
    }

    /** A date as days since the epoch. */
    static ArrowField dateDays(final String name) {
        return new ArrowField(name, DATE, 32, 0, 0);
    }

    static ArrowField struct(final String name) {
        return new ArrowField(name, STRUCT, 0, 0, 0);
    }

    /** A list of {@code size} values per row, held by the one child it is given. */
    static ArrowField fixedSizeList(final String name, final int size) {
        final ArrowField list = new ArrowField(name, FIXED_SIZE_LIST, 0, 0, 0);
        list.listSize = size;
        return list;
    }

    ArrowField child(final ArrowField child) {
        children.add(child);
        return child;
    }

    ArrowField meta(final String key, final String value) {
        metadata.put(key, value);
        return this;
    }

    String name() {
        return name;
    }

    int typeId() {
        return typeId;
    }

    int bitWidth() {
        return bitWidth;
    }

    int precision() {
        return precision;
    }

    int scale() {
        return scale;
    }

    int listSize() {
        return listSize;
    }

    Map<String, String> metadata() {
        return metadata;
    }

    List<ArrowField> children() {
        return children;
    }

    int rows() {
        return rows;
    }

    int nulls() {
        return nulls;
    }

    /** This field's own buffers, in Arrow's order for its type. */
    List<byte[]> buffers() {
        final List<byte[]> out = new ArrayList<>();
        out.add(bits(validity, validityByte, rows));
        if (typeId == UTF8 || typeId == BINARY) {
            out.add(offsets.toByteArray());
            out.add(data.toByteArray());
        } else if (typeId == BOOL) {
            out.add(bits(data, dataByte, dataBits));
        } else if (!isNested()) {
            out.add(data.toByteArray());
        }
        return out;
    }

    /** A NULL in this field, its children NULL too: a fixed-size list's child for each of the row's slots. */
    void addNull() {
        mark(false);
        if (typeId == UTF8 || typeId == BINARY) {
            writeInt(offsets, data.size());
        } else if (typeId == BOOL) {
            addBit(false);
        } else if (!isNested()) {
            writeZeros(width());
        }
        final int slots = typeId == FIXED_SIZE_LIST ? listSize : 1;
        for (final ArrowField child : children) {
            for (int i = 0; i < slots; i++) {
                child.addNull();
            }
        }
    }

    /** A present struct or fixed-size list row: its children take their values next. */
    void addPresent() {
        mark(true);
    }

    void addLong(final long value) {
        mark(true);
        writeLittleEndian(value, width());
    }

    void addDouble(final double value) {
        mark(true);
        writeLittleEndian(Double.doubleToRawLongBits(value), 8);
    }

    void addFloat(final float value) {
        mark(true);
        writeLittleEndian(Float.floatToRawIntBits(value), 4);
    }

    void addBoolean(final boolean value) {
        mark(true);
        addBit(value);
    }

    void addText(final String value) {
        addBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    void addBytes(final byte[] value) {
        mark(true);
        data.write(value, 0, value.length);
        writeInt(offsets, data.size());
    }

    /** A 128-bit two's complement value, its big-endian bytes as BigInteger#toByteArray gives them. */
    void addDecimal(final byte[] bigEndian) {
        mark(true);
        final byte fill = bigEndian.length > 0 && bigEndian[0] < 0 ? (byte) -1 : 0;
        for (int i = 0; i < 16; i++) {
            final int from = bigEndian.length - 1 - i;
            data.write(from >= 0 ? bigEndian[from] : fill);
        }
    }

    private int width() {
        return typeId == DECIMAL ? 16 : bitWidth / 8;
    }

    /** Whether the values live in the children, the field's own buffer its bitmap alone. */
    private boolean isNested() {
        return typeId == STRUCT || typeId == FIXED_SIZE_LIST;
    }

    private void mark(final boolean present) {
        if (present) {
            validityByte |= 1 << (rows % 8);
        } else {
            nulls++;
        }
        rows++;
        if (rows % 8 == 0) {
            validity.write(validityByte);
            validityByte = 0;
        }
    }

    private void addBit(final boolean value) {
        if (value) {
            dataByte |= 1 << (dataBits % 8);
        }
        dataBits++;
        if (dataBits % 8 == 0) {
            data.write(dataByte);
            dataByte = 0;
        }
    }

    private static byte[] bits(final ByteArrayOutputStream full, final int partial, final int count) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] whole = full.toByteArray();
        out.write(whole, 0, whole.length);
        if (count % 8 != 0) {
            out.write(partial);
        }
        return out.toByteArray();
    }

    private void writeZeros(final int count) {
        for (int i = 0; i < count; i++) {
            data.write(0);
        }
    }

    private void writeLittleEndian(final long value, final int bytes) {
        for (int i = 0; i < bytes; i++) {
            data.write((int) (value >> (8 * i)));
        }
    }

    private static void writeInt(final ByteArrayOutputStream out, final int value) {
        out.write(value);
        out.write(value >> 8);
        out.write(value >> 16);
        out.write(value >> 24);
    }
}
