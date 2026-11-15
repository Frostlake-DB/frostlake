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

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;

import java.util.HashSet;
import java.util.Set;

/**
 * An argument a routine or a policy declares with no type. The account parses one and refuses the statement for it
 * as {@code Missing data type for argument 'A'}, naming the first such argument as it resolves - an unquoted name
 * folded, a quoted one as written - without the compilation-error prefix (live-verified, every routine and policy
 * kind alike). What the statement says of itself comes first: a required argument after an optional one, a width out
 * of range, an unknown language, a missing schema, an unknown type anywhere in the signature; then a repeated name, an
 * argument's before a returned column's; a policy's taken name; an external function's integration. A returned
 * column with no type is refused after it (all live-verified).
 */
final class UntypedArguments {

    private UntypedArguments() {
    }

    /**
     * Whether an argument of the list has no type.
     *
     * @param list the arguments, or null for none
     * @return whether one is untyped
     */
    static boolean any(final FrostlakeParser.ParameterListContext list) {
        return first(list) != null;
    }

    /**
     * Refuse the list's first argument written with no type, if any.
     *
     * @param list the arguments, or null for none
     */
    static void reject(final FrostlakeParser.ParameterListContext list) {
        final FrostlakeParser.ParameterDefContext untyped = first(list);
        if (untyped != null) {
            throw new RuntimeException("Missing data type for argument '"
                + SqlIdentifiers.canonical(untyped.variableName()) + "'");
        }
    }

    /**
     * Refuse a policy's arguments that repeat a name - {@code Argument 'V' repeats in the function signature.}, in a
     * routine's words, names compared as they resolve - or leave a type out, in that order: {@code (v STRING, v)} is
     * refused for the name (all live-verified).
     *
     * @param list the policy's arguments, or null for none
     */
    static void rejectInPolicy(final FrostlakeParser.ParameterListContext list) {
        if (list == null) {
            return;
        }
        final Set<String> names = new HashSet<>();
        for (final FrostlakeParser.ParameterDefContext parameter : list.parameterDef()) {
            final String name = SqlIdentifiers.canonical(parameter.variableName());
            if (!names.add(name)) {
                throw new RuntimeException("Argument '" + name + "' repeats in the function signature.");
            }
        }
        reject(list);
    }

    private static FrostlakeParser.ParameterDefContext first(final FrostlakeParser.ParameterListContext list) {
        if (list == null) {
            return null;
        }
        for (final FrostlakeParser.ParameterDefContext parameter : list.parameterDef()) {
            if (parameter.dataTypeName() == null) {
                return parameter;
            }
        }
        return null;
    }
}
