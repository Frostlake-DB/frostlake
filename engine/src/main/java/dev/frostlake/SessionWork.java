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
package dev.frostlake;

import java.io.IOException;

/**
 * Work done on the engine in a session's scope - its current database, schema, role and settings - with no statement
 * run (see {@link ConcurrentDatabaseEngine#inSession}).
 *
 * @param <T> what the work answers
 */
public interface SessionWork<T> {

    /**
     * Does the work.
     *
     * @param engine the engine, bound to the session
     * @return what the work answers
     * @throws IOException where it reads or writes a file and fails
     */
    T run(DatabaseEngine engine) throws IOException;
}
