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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An Arrow IPC stream of one record batch, as a Snowflake driver reads a result's {@code rowsetBase64}: the schema
 * message, the batch's message and body, then the end-of-stream marker. Each message is the continuation marker, the
 * size of its FlatBuffers metadata padded to 8 bytes, the metadata (see {@link FlatBufferBuilder}) and its body, each
 * buffer of the body 8-byte aligned. Metadata version V5.
 */
final class ArrowStream {

    private static final int CONTINUATION = 0xFFFFFFFF;
    private static final int METADATA_V5 = 4;
    private static final int HEADER_SCHEMA = 1;
    private static final int HEADER_RECORD_BATCH = 3;
    private static final int PRECISION_SINGLE = 1;
    private static final int PRECISION_DOUBLE = 2;
    private static final int DATE_UNIT_DAY = 0;

    private ArrowStream() {
    }

    /**
     * The stream of a batch.
     *
     * @param fields the batch's fields, each holding its column
     * @param rows   the batch's row count
     * @return the stream's bytes
     */
    static byte[] of(final List<ArrowField> fields, final int rows) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeMessage(out, schema(fields), new byte[0]);
        final List<long[]> nodes = new ArrayList<>();
        final List<byte[]> buffers = new ArrayList<>();
        for (final ArrowField field : fields) {
            collect(field, nodes, buffers);
        }
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final long[] bufferPairs = new long[buffers.size() * 2];
        for (int i = 0; i < buffers.size(); i++) {
            final byte[] buffer = buffers.get(i);
            bufferPairs[2 * i] = body.size();
            bufferPairs[2 * i + 1] = buffer.length;
            body.write(buffer, 0, buffer.length);
            pad(body, body.size());
        }
        final long[] nodePairs = new long[nodes.size() * 2];
        for (int i = 0; i < nodes.size(); i++) {
            nodePairs[2 * i] = nodes.get(i)[0];
            nodePairs[2 * i + 1] = nodes.get(i)[1];
        }
        writeMessage(out, recordBatch(rows, nodePairs, bufferPairs, body.size()), body.toByteArray());
        writeInt(out, CONTINUATION);
        writeInt(out, 0);
        return out.toByteArray();
    }

    /** A field's node and buffers, then its children's, depth first. */
    private static void collect(final ArrowField field, final List<long[]> nodes, final List<byte[]> buffers) {
        nodes.add(new long[] {field.rows(), field.nulls()});
        buffers.addAll(field.buffers());
        for (final ArrowField child : field.children()) {
            collect(child, nodes, buffers);
        }
    }

    private static byte[] schema(final List<ArrowField> fields) {
        final FlatBufferBuilder fb = new FlatBufferBuilder(1024);
        final int[] written = new int[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            written[i] = field(fb, fields.get(i));
        }
        final int vector = fb.createOffsetVector(written);
        fb.startTable(4);
        fb.addOffsetField(1, vector);
        final int schema = fb.endTable();
        fb.startTable(5);
        fb.addOffsetField(2, schema);
        fb.addShortField(0, METADATA_V5);
        fb.addByteField(1, HEADER_SCHEMA);
        return fb.finish(fb.endTable());
    }

    /** An Arrow Field table: name, nullable, type, children and custom metadata. */
    private static int field(final FlatBufferBuilder fb, final ArrowField field) {
        final int name = fb.createString(field.name());
        final int type = type(fb, field);
        final int[] children = new int[field.children().size()];
        for (int i = 0; i < children.length; i++) {
            children[i] = field(fb, field.children().get(i));
        }
        final int childVector = fb.createOffsetVector(children);
        final int[] pairs = new int[field.metadata().size()];
        int n = 0;
        for (final Map.Entry<String, String> entry : field.metadata().entrySet()) {
            final int key = fb.createString(entry.getKey());
            final int value = fb.createString(entry.getValue());
            fb.startTable(2);
            fb.addOffsetField(0, key);
            fb.addOffsetField(1, value);
            pairs[n++] = fb.endTable();
        }
        final int metadata = fb.createOffsetVector(pairs);
        fb.startTable(7);
        fb.addOffsetField(0, name);
        fb.addOffsetField(3, type);
        fb.addOffsetField(5, childVector);
        fb.addOffsetField(6, metadata);
        fb.addByteField(1, 1);
        fb.addByteField(2, field.typeId());
        return fb.endTable();
    }

    /** The table of a field's type, as its Type union member. */
    private static int type(final FlatBufferBuilder fb, final ArrowField field) {
        switch (field.typeId()) {
            case ArrowField.INT:
                fb.startTable(2);
                fb.addIntField(0, field.bitWidth());
                fb.addByteField(1, 1);
                return fb.endTable();
            case ArrowField.FLOATING_POINT:
                fb.startTable(1);
                fb.addShortField(0, field.bitWidth() == 32 ? PRECISION_SINGLE : PRECISION_DOUBLE);
                return fb.endTable();
            case ArrowField.DECIMAL:
                fb.startTable(3);
                fb.addIntField(0, field.precision());
                fb.addIntField(1, field.scale());
                fb.addIntField(2, 128);
                return fb.endTable();
            case ArrowField.DATE:
                fb.startTable(1);
                fb.addShortField(0, DATE_UNIT_DAY);
                return fb.endTable();
            case ArrowField.FIXED_SIZE_LIST:
                fb.startTable(1);
                fb.addIntField(0, field.listSize());
                return fb.endTable();
            default:
                fb.startTable(0);
                return fb.endTable();
        }
    }

    private static byte[] recordBatch(final int rows, final long[] nodes, final long[] buffers, final long bodyLength) {
        final FlatBufferBuilder fb = new FlatBufferBuilder(1024);
        final int nodeVector = fb.createLongPairVector(nodes);
        final int bufferVector = fb.createLongPairVector(buffers);
        fb.startTable(5);
        fb.addLongField(0, rows);
        fb.addOffsetField(1, nodeVector);
        fb.addOffsetField(2, bufferVector);
        final int batch = fb.endTable();
        fb.startTable(5);
        fb.addLongField(3, bodyLength);
        fb.addOffsetField(2, batch);
        fb.addShortField(0, METADATA_V5);
        fb.addByteField(1, HEADER_RECORD_BATCH);
        return fb.finish(fb.endTable());
    }

    private static void writeMessage(final ByteArrayOutputStream out, final byte[] metadata, final byte[] body) {
        final int padded = (metadata.length + 7) & ~7;
        writeInt(out, CONTINUATION);
        writeInt(out, padded);
        out.write(metadata, 0, metadata.length);
        for (int i = metadata.length; i < padded; i++) {
            out.write(0);
        }
        out.write(body, 0, body.length);
    }

    private static void pad(final ByteArrayOutputStream out, final int size) {
        for (int i = size; i % 8 != 0; i++) {
            out.write(0);
        }
    }

    private static void writeInt(final ByteArrayOutputStream out, final int value) {
        out.write(value);
        out.write(value >> 8);
        out.write(value >> 16);
        out.write(value >> 24);
    }
}
