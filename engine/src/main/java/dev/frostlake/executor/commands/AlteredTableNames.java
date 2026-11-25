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

import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The names an ALTER TABLE … SET or UNSET gives: a table's own properties and the parameters of its level. A name no
 * table takes is refused by name, as {@code invalid property 'FOO' for 'TABLE'}, before the table is looked for,
 * before IF EXISTS is read and before a session with no current database is noticed; so is BASE_LOCATION, which only
 * an Iceberg table's CREATE writes, as an unsupported feature. An UNSET names the stage file format and copy options
 * as invalid properties, and a quoted name there is none either (live-verified).
 */
public final class AlteredTableNames {

    /** The location an Iceberg table's CREATE writes, which ALTER TABLE … SET does not take. */
    private static final String BASE_LOCATION = "BASE_LOCATION";

    /** What an ALTER TABLE … UNSET takes besides the table's parameters. */
    private static final Set<String> UNSET_PROPERTIES = new HashSet<>(Arrays.asList("CHANGE_TRACKING",
        "DATA_RETENTION_TIME_IN_DAYS", "MAX_DATA_EXTENSION_TIME_IN_DAYS", "DEFAULT_DDL_COLLATION",
        "ENABLE_SCHEMA_EVOLUTION", "DATA_METRIC_SCHEDULE", "COMMENT"));

    /** A table's stage file format and copy options: its SET writes them, and its UNSET names either as invalid. */
    private static final Set<String> STAGE_PROPERTIES = new HashSet<>(Arrays.asList("STAGE_FILE_FORMAT",
        "STAGE_COPY_OPTIONS"));

    private AlteredTableNames() {
    }

    /**
     * Refuses the first name the action sets or unsets that no table takes, of several the first of a table hashed by
     * name; any other action passes.
     *
     * @param action the ALTER TABLE's action
     */
    public static void rejectUnknown(final FrostlakeParser.TableActionContext action) {
        if (action.SET() != null && !action.tableSetProperty().isEmpty()) {
            rejectUnknownSet(action.tableSetProperty());
        } else if (action.tableUnsetProperties() != null) {
            final List<FrostlakeParser.PropertyNameContext> keys = action.tableUnsetProperties().propertyName();
            ParameterLevels.rejectUnknownUnsetNames(writtenNames(keys), unsetTaken(keys), "TABLE");
        }
    }

    private static void rejectUnknownSet(final List<FrostlakeParser.TableSetPropertyContext> properties) {
        final List<String> names = new ArrayList<>();
        final List<Boolean> taken = new ArrayList<>();
        boolean baseLocation = false;
        for (final FrostlakeParser.TableSetPropertyContext property : properties) {
            final boolean isBaseLocation = BASE_LOCATION.equals(
                ParameterRegistry.canonical(property.propertyName().getText()));
            baseLocation |= isBaseLocation;
            names.add(property.propertyName().getText());
            taken.add(Boolean.valueOf(isBaseLocation || takes(property)));
        }
        ParameterLevels.rejectUnknownNames(names, taken, "TABLE");
        if (baseLocation) {
            throw new RuntimeException("Unsupported feature '" + BASE_LOCATION + "'.");
        }
    }

    /**
     * Whether an ALTER TABLE … SET's property is one a table takes: its own, or a parameter of its level.
     *
     * @param property the property set
     * @return whether a table takes it
     */
    static boolean takes(final FrostlakeParser.TableSetPropertyContext property) {
        final String raw = property.propertyName().getText();
        return "CHANGE_TRACKING".equalsIgnoreCase(raw) || "DATA_METRIC_SCHEDULE".equalsIgnoreCase(raw)
            || "ENABLE_SCHEMA_EVOLUTION".equalsIgnoreCase(raw) || "COMMENT".equalsIgnoreCase(raw)
            || "STAGE_COPY_OPTIONS".equalsIgnoreCase(raw) && property.stageCopyOptionList() != null
            || ParameterRegistry.isTableParameter(ParameterRegistry.canonical(raw));
    }

    /**
     * The names an UNSET gives, as written.
     *
     * @param keys the UNSET's names
     * @return their texts
     */
    static List<String> writtenNames(final List<FrostlakeParser.PropertyNameContext> keys) {
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.PropertyNameContext key : keys) {
            names.add(key.getText());
        }
        return names;
    }

    /**
     * Per UNSET name, whether a table takes it: a bare name of its own property or of a parameter of its level, the
     * stage file format and copy options excepted.
     *
     * @param keys the UNSET's names
     * @return per name, whether it is taken
     */
    static List<Boolean> unsetTaken(final List<FrostlakeParser.PropertyNameContext> keys) {
        final List<Boolean> taken = new ArrayList<>();
        for (final FrostlakeParser.PropertyNameContext key : keys) {
            final String written = key.getText();
            final String canonical = ParameterRegistry.canonical(written);
            taken.add(Boolean.valueOf(!written.startsWith("\"") && !STAGE_PROPERTIES.contains(canonical)
                && (UNSET_PROPERTIES.contains(canonical) || ParameterRegistry.isTableParameter(canonical))));
        }
        return taken;
    }
}
