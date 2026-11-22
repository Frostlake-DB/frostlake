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

import dev.frostlake.functions.scalar.JsonNumberNotation;
import dev.frostlake.functions.scalar.JsonNumberRange;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.functions.scalar.semistructured.JsonFileRecords;
import dev.frostlake.functions.scalar.semistructured.JsonRecordFault;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON staged-file reader: a file is read as a sequence of documents, one record each, whether they stand one to a
 * line, run across lines or share one, with or without a comma between them; a top-level array is one record, or
 * under STRIP_OUTER_ARRAY = TRUE one record per element (see {@link JsonFileRecords}, live-verified). An object that
 * names a key twice refuses the file unless ALLOW_DUPLICATE = TRUE keeps the key's last value. The reader's own
 * {@link #readRecords(Path)} strips the outer array, as callers that know no file format read it.
 */
public class JsonStageReader implements StageFileReader {

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        return readRecords(file, true);
    }

    /**
     * A file's records under the format's STRIP_OUTER_ARRAY.
     *
     * @param file             the file
     * @param stripOuterArray  whether a top-level array's elements are records of their own
     * @return the records
     * @throws IOException when the file cannot be read
     */
    public List<JsonNode> readRecords(final Path file, final boolean stripOuterArray) throws IOException {
        // A JSON file's byte order mark is passed over whatever SKIP_BYTE_ORDER_MARK says (live-verified).
        final String text = StagedFileIo.withoutByteOrderMark(StagedFileIo.readString(file));
        return records(text, stripOuterArray, false, String.valueOf(file.getFileName()), true, file);
    }

    /**
     * A file's records under the format's STRIP_OUTER_ARRAY, REPLACE_INVALID_CHARACTERS and ALLOW_DUPLICATE: a byte
     * that is no part of UTF-8 refuses the file in the account's words — {@code Error parsing JSON: invalid UTF-8
     * sequence: 0xE9}, the file, the line and the byte's character within it — unless the option reads each such
     * sequence as U+FFFD (live-verified); a malformed record refuses it in the account's words too, {@code Error
     * parsing JSON: unknown keyword "tru"}, the file, the line and the character, and the row (see
     * {@link JsonFileFault}).
     *
     * @param file             the file
     * @param stripOuterArray  whether a top-level array's elements are records of their own
     * @param replaceInvalid   whether REPLACE_INVALID_CHARACTERS = TRUE
     * @param shownName        the file as the refusal names it
     * @param namesColumn      whether a refusal names the column, $1 (see
     *                         {@link StagedFileIo#unreadableRefusal(String, String, int, String, boolean)})
     * @param allowDuplicate   whether ALLOW_DUPLICATE = TRUE
     * @return the records
     * @throws IOException when the file cannot be read
     */
    public List<JsonNode> readRecords(final Path file, final boolean stripOuterArray, final boolean replaceInvalid,
                                      final String shownName, final boolean namesColumn,
                                      final boolean allowDuplicate) throws IOException {
        return readRecords(file, stripOuterArray, replaceInvalid, shownName, namesColumn, allowDuplicate, null);
    }

    /**
     * A file's records as {@link #readRecords(Path, boolean, boolean, String, boolean, boolean)} reads them, the file
     * decompressed by its format's COMPRESSION (see {@link StagedFileIo}).
     *
     * @param file             the file
     * @param stripOuterArray  whether a top-level array's elements are records of their own
     * @param replaceInvalid   whether REPLACE_INVALID_CHARACTERS = TRUE
     * @param shownName        the file as the refusal names it
     * @param namesColumn      whether a refusal names the column, $1
     * @param allowDuplicate   whether ALLOW_DUPLICATE = TRUE
     * @param compression      the format's COMPRESSION, upper case; null for AUTO
     * @return the records
     * @throws IOException when the file cannot be read
     */
    public List<JsonNode> readRecords(final Path file, final boolean stripOuterArray, final boolean replaceInvalid,
                                      final String shownName, final boolean namesColumn,
                                      final boolean allowDuplicate, final String compression) throws IOException {
        final String marked = StagedFileIo.readMarked(file, StandardCharsets.UTF_8, compression);
        final int unreadable = StagedFileIo.firstUnreadable(marked);
        if (unreadable >= 0 && !replaceInvalid) {
            throw StagedFileIo.unreadableRefusal("JSON", marked, unreadable, shownName, namesColumn);
        }
        final String text = StagedFileIo.withoutByteOrderMark(StagedFileIo.unreadableReplaced(marked));
        return records(text, stripOuterArray, allowDuplicate, shownName, namesColumn, file);
    }

    private static List<JsonNode> records(final String text, final boolean stripOuterArray,
                                          final boolean allowDuplicate, final String shownName,
                                          final boolean namesColumn, final Path file) {
        final JsonFileRecords read = JsonFileRecords.of(text, stripOuterArray, allowDuplicate);
        if (read.fault() != null) {
            throw new JsonFileFault(read.fault(), shownName, namesColumn);
        }
        final List<JsonNode> records = new ArrayList<>();
        for (final String record : read.records()) {
            if (record == null) {
                records.add(NullNode.getInstance());
                continue;
            }
            try {
                records.add(readTree(record));
            } catch (final RuntimeException jsonError) {
                throw new RuntimeException("Invalid JSON in file " + file.getFileName() + ": "
                    + jsonError.getMessage(), jsonError);
            }
        }
        return records;
    }

    /**
     * A record's text read as PARSE_JSON reads it: a number written with an exponent is a DOUBLE (see
     * {@link JsonNumberNotation}), and one written plainly that no NUMBER can hold is a DOUBLE too (see
     * {@link JsonNumberRange}), so a staged file types a document as the function does.
     */
    private static JsonNode readTree(final String text) {
        final JsonNode strict;
        try {
            strict = MAPPER.readTree(text);
        } catch (final JacksonException malformed) {
            // What strict JSON refuses and the account reads - an unquoted key, a single-quoted string, a
            // trailing comma - is read as PARSE_JSON reads it.
            final JsonNode lenient = JsonTypeHelper.parseLenient(text);
            if (lenient == null || JsonRecordFault.breaksALine(text)) {
                throw malformed;
            }
            return lenient;
        }
        return JsonNumberRange.applyDoubleRange(text, JsonNumberNotation.applyExponentNotation(MAPPER, text, strict));
    }

    @Override
    public String formatType() {
        return "JSON";
    }
}
