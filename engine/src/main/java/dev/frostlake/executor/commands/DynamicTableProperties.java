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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The properties ALTER DYNAMIC TABLE sets or unsets: its lag, warehouse and comment, and the parameters it lists
 * for itself (see {@code DynamicTableParameters}). The account refuses any other by name, as
 * {@code invalid property 'X' for 'DYNAMIC_TABLE'}, before it looks the table up and before it notices a
 * session with no current database: the refresh mode and INITIALIZE are fixed at creation, and
 * CHANGE_TRACKING or a name it does not know is no dynamic table property (live-verified).
 */
public final class DynamicTableProperties {

    private static final Set<String> TAKEN = new HashSet<>(Arrays.asList(
        "TARGET_LAG", "WAREHOUSE", "COMMENT", "DATA_RETENTION_TIME_IN_DAYS", "MAX_DATA_EXTENSION_TIME_IN_DAYS",
        "LOG_LEVEL", "TRACE_LEVEL", "LOG_EVENT_LEVEL", "DATA_METRIC_SCHEDULE", "DEFAULT_DDL_COLLATION",
        "ENABLE_DATA_COMPACTION", "QUOTED_IDENTIFIERS_IGNORE_CASE"));

    private DynamicTableProperties() {
    }

    /**
     * Refuse the first property the action names that ALTER DYNAMIC TABLE cannot set or unset, in the order
     * written.
     *
     * @param action the ALTER's action
     */
    public static void rejectUntakeable(final FrostlakeParser.DynamicTableActionContext action) {
        final List<FrostlakeParser.DynamicTablePropertyContext> named = new ArrayList<>();
        for (final FrostlakeParser.DynamicTableSettingContext setting : action.dynamicTableSetting()) {
            named.add(setting.dynamicTableProperty());
        }
        named.addAll(action.dynamicTableProperty());
        for (final FrostlakeParser.DynamicTablePropertyContext property : named) {
            if (!TAKEN.contains(property.getText().toUpperCase(Locale.ROOT))) {
                // Named as written, a reserved word among the names it reads (live-verified).
                throw new RuntimeException(
                    SqlCompilationError.of("invalid property '" + property.getText() + "' for 'DYNAMIC_TABLE'"));
            }
        }
        for (final FrostlakeParser.DynamicTableSettingContext setting : action.dynamicTableSetting()) {
            judgeList(setting);
        }
    }

    /**
     * A list a SET gives a name, judged before the table is looked up (live-verified): the comment refuses a list of
     * constants by its first element that is no text, the target lag any list as an invalid value of the property,
     * and a parameter of any type but text the list as an invalid value, named by the parser's word for it.
     */
    private static void judgeList(final FrostlakeParser.DynamicTableSettingContext setting) {
        if (setting.parenOptionList() == null && setting.constantList() == null) {
            return;
        }
        final String name = setting.dynamicTableProperty().getText().toUpperCase(Locale.ROOT);
        final String word = setting.constantList() != null || setting.parenOptionList().parenOption().isEmpty()
            ? ListValues.CONSTANT_LIST : ListValues.PROPERTY_LIST;
        if ("COMMENT".equals(name)) {
            if (setting.constantList() != null) {
                throw CreatedObjectProperties.listedTextValue(name, setting.constantList().propertyListConstant());
            }
            if (setting.parenOptionList().parenOption().isEmpty()) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(word, name));
            }
            return;
        }
        if ("TARGET_LAG".equals(name)) {
            throw new RuntimeException(SqlCompilationError.invalidValueForProperty(word, name));
        }
        final String type = CreatedObjectProperties.accountParameterType(name);
        if (type != null && !"STRING".equals(type)) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(word, name));
        }
    }
}
