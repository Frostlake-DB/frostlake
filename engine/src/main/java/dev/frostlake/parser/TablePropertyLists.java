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

package dev.frostlake.parser;

import org.antlr.v4.runtime.RuleContext;

/**
 * The property lists that read NULL for an option's value: the ones CREATE TABLE and ALTER TABLE … SET give a
 * property, {@code STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL)}, where the property the list is given judges it. A
 * parse predicate asks from inside the option, so the lists of every other statement read as they did.
 */
public final class TablePropertyLists {

    private TablePropertyLists() {
    }

    /**
     * Whether an option belongs to a list a table's property is given.
     *
     * @param option the option being read
     * @return whether its value may be NULL
     */
    public static boolean holds(final RuleContext option) {
        final RuleContext list = option == null ? null : option.getParent();
        final RuleContext owner = list == null ? null : list.getParent();
        return owner instanceof FrostlakeParser.TableTailOptionContext
            || owner instanceof FrostlakeParser.TableSetPropertyContext;
    }
}
