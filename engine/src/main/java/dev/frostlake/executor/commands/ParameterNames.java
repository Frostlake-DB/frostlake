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

/**
 * Whether a name a statement sets is a parameter, for readers outside the command handlers (see
 * {@link ParameterRegistry}).
 */
public final class ParameterNames {

    private ParameterNames() {
    }

    /**
     * Whether a name, as written, is a parameter in the list of some level - a database's, a schema's, a table's, a
     * session's and the like - rather than an object's own property.
     *
     * @param spelled the name as written, quoted or not
     * @return whether it is a parameter
     */
    public static boolean isParameter(final String spelled) {
        return ParameterRegistry.isSomeLevelParameter(ParameterRegistry.canonical(spelled));
    }
}
