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

package dev.frostlake.persistence;

/**
 * How an autosave takes the engine: it writes the snapshot alone, as a checkpoint does, so that no statement changes
 * the catalog or the tables while the snapshot walks them.
 */
public interface AutoSaveGuard {

    /**
     * Runs the save holding the engine, and returns once it ran.
     *
     * @param save the save
     */
    void runAlone(Runnable save);
}
