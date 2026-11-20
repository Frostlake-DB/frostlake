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

package dev.frostlake.functions.scalar.semistructured;

import java.util.ArrayList;
import java.util.List;

/**
 * A staged JSON file's text read into its records, as the account's reader splits it: documents follow one another
 * on one line or across lines, with or without a comma between them, and under STRIP_OUTER_ARRAY a top-level
 * array's elements are records of their own (live-verified). The first fault, if any, ends the reading.
 */
public final class JsonFileRecords {

    private final String text;
    private final List<int[]> spans;
    private final JsonRecordFault fault;

    JsonFileRecords(final String text, final List<int[]> spans, final JsonRecordFault fault) {
        this.text = text;
        this.spans = spans;
        this.fault = fault;
    }

    /**
     * A file's records.
     *
     * @param text              the file's text, past its byte order mark
     * @param stripOuterArray   whether a top-level array's elements are records of their own
     * @param duplicatesAllowed whether an object may name a key twice (ALLOW_DUPLICATE)
     * @return the records and the first fault
     */
    public static JsonFileRecords of(final String text, final boolean stripOuterArray,
                                     final boolean duplicatesAllowed) {
        return JsonFaultReader.recordsOf(text, stripOuterArray, duplicatesAllowed);
    }

    /** @return the first fault among the records, or null when the account's reader finds none */
    public JsonRecordFault fault() {
        return fault;
    }

    /** @return each record's text as written, in order; null for a record no value fills, which is a JSON null */
    public List<String> records() {
        final List<String> records = new ArrayList<String>(spans.size());
        for (final int[] span : spans) {
            records.add(span == null ? null : text.substring(span[0], span[1]));
        }
        return records;
    }
}
