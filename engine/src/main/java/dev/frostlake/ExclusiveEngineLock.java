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

/**
 * The lock a wrapper serves an engine's requests under, as one that runs alone takes it: work the engine starts on a
 * thread of its own - a scheduled task run, an alert's evaluation, a dynamic table's refresh, a checkpoint - takes it
 * too, before the engine's monitor, so that it never runs beside a request (see
 * {@link DatabaseEngine#setExclusiveLock}).
 */
public interface ExclusiveEngineLock {

    /**
     * Takes the lock for the calling thread, reentrantly. A thread that holds the lock shared only cannot take it
     * alone; it goes on under what it holds.
     *
     * @return whether the lock was taken, to be given back with {@link #release}
     */
    boolean acquire();

    /** Gives back a lock {@link #acquire} took. */
    void release();
}
