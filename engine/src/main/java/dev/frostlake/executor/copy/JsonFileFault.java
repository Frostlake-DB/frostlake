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

import dev.frostlake.functions.scalar.semistructured.JsonRecordFault;

/**
 * A staged JSON file refused for a malformed record, in the words of the account's reader: {@code Error parsing
 * JSON: incomplete object value}, the file with the fault's line and character, and the row being read with the
 * line it starts on. The fault itself stays at hand for a load that reports it as a result row.
 */
public class JsonFileFault extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient JsonRecordFault fault;

    /**
     * @param fault       the first fault among the file's records
     * @param shownName   the file, as the refusal names it
     * @param namesColumn whether the refusal names the column, $1
     */
    public JsonFileFault(final JsonRecordFault fault, final String shownName, final boolean namesColumn) {
        super("Error parsing JSON: " + fault.sentence() + fault.placeLines(shownName, namesColumn));
        this.fault = fault;
    }

    /** @return the fault, as the account's reader names and places it */
    public JsonRecordFault fault() {
        return fault;
    }

    /** @return the refusal's first line: the sentence, without the file and the row */
    public String sentence() {
        return "Error parsing JSON: " + fault.sentence();
    }
}
