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


package dev.frostlake.task;

import dev.frostlake.metastore.model.Task;

/**
 * The task a scheduler key's timer was armed for, and the schedule it was armed with: the timer belongs to that very
 * task object, so a task dropped or replaced under the same name is told apart from the one the catalog holds now.
 */
public final class ArmedTask {

    private final Task task;
    private final String schedule;

    /**
     * @param task     the task the timer runs
     * @param schedule the schedule the timer was armed with
     */
    public ArmedTask(final Task task, final String schedule) {
        this.task = task;
        this.schedule = schedule;
    }

    /** The task the timer runs. */
    public Task getTask() {
        return task;
    }

    /** Whether the timer runs this very task on the schedule it has now. */
    public boolean arms(final Task candidate) {
        if (candidate != task) {
            return false;
        }
        final String now = candidate.getSchedule();
        return now == null ? schedule == null : now.equals(schedule);
    }
}
