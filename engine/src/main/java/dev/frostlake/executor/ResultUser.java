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
package dev.frostlake.executor;

/**
 * The user a statement runs for, as {@link QueryResultCache} asks it: a result belongs to the user whose statement
 * answered it, and only that user reads it back.
 */
public interface ResultUser {

    /**
     * The user the calling thread's statement runs for.
     *
     * @return the user's name, or null when the engine models no users
     */
    String currentUser();
}
