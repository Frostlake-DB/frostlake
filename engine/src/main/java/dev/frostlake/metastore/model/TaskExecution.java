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

package dev.frostlake.metastore.model;

import dev.frostlake.task.TaskTrigger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * One run of a task. Its stamps are instants, so a run in the repeated hour of a fall-back night keeps its own offset;
 * their views on the host's wall clock are what a snapshot also holds, for a reader that predates the instants.
 */
public class TaskExecution {
    private final Instant scheduledAt;
    private final Instant startedAt;
    private final Instant endedAt;
    private final TaskExecutionState state;
    private final String errorMessage;
    private final int rowsAffected;
    /** What started the run, as TASK_HISTORY's SCHEDULED_FROM reports it. */
    private final String scheduledFrom;
    /** Which attempt of its graph run the run belongs to: 1, or one more for each retry of the graph run. */
    private final int attempt;
    /** The id of the query that ran the task's statement, or null when none is known. */
    private String queryId;
    /** The code of the error the run failed with, as text, or null. */
    private String errorCode;
    /** The user the run was scheduled by: who ran EXECUTE TASK, or who resumed the task. */
    private String scheduledByUser;

    /**
     * A run stamped by instants.
     *
     * @param scheduledAt  the instant the run was scheduled for
     * @param startedAt    when it began
     * @param endedAt      when it ended, or null while it runs
     * @param state        its outcome
     * @param errorMessage the failure's message, or null
     * @param rowsAffected the rows its body wrote
     * @param trigger      what started it
     * @param attempt      the attempt of its graph run: 1 for the run itself, one more for each retry
     */
    public TaskExecution(final Instant scheduledAt, final Instant startedAt, final Instant endedAt,
                         final TaskExecutionState state, final String errorMessage, final int rowsAffected,
                         final TaskTrigger trigger, final int attempt) {
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.state = state;
        this.errorMessage = errorMessage;
        this.rowsAffected = rowsAffected;
        this.scheduledFrom = trigger.reported();
        this.attempt = attempt;
    }

    /**
     * Records the query that ran the task's statement, as TASK_HISTORY reports it: its id, the code of the error it
     * failed with, and the user the run was scheduled by.
     *
     * @param ranQueryId    the query's id, or null
     * @param failureCode   the error's code as text, or null for a run that succeeded
     * @param scheduledBy   the user the run was scheduled by, or null
     * @return this run
     */
    public TaskExecution ranAs(final String ranQueryId, final String failureCode, final String scheduledBy) {
        this.queryId = ranQueryId;
        this.errorCode = failureCode;
        this.scheduledByUser = scheduledBy;
        return this;
    }

    /**
     * The id of the query that ran the task's statement.
     *
     * @return the id, or null when none is known
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * The code of the error the run failed with, as TASK_HISTORY's ERROR_CODE tells it.
     *
     * @return the code as text, or null
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * The user the run was scheduled by, as SCHEDULED_BY_USER tells it.
     *
     * @return the user's name, or null
     */
    public String getScheduledByUser() {
        return scheduledByUser;
    }

    /**
     * Which attempt of its graph run this run belongs to, as TASK_HISTORY's and COMPLETE_TASK_GRAPHS'
     * ATTEMPT_NUMBER report it.
     *
     * @return 1 for a run of the graph run itself, one more for each EXECUTE TASK … RETRY of it
     */
    public int getAttempt() {
        return attempt;
    }

    /**
     * What started this run, as TASK_HISTORY prints it.
     *
     * @return SCHEDULE or EXECUTE TASK
     */
    public String getScheduledFrom() {
        return scheduledFrom;
    }

    /** The instant the run was scheduled for, on the host's wall clock. */
    public LocalDateTime getScheduledTime() {
        return wallClock(scheduledAt);
    }

    /** When the run began, on the host's wall clock. */
    public LocalDateTime getStartTime() {
        return wallClock(startedAt);
    }

    /** When the run ended, on the host's wall clock, or null while it runs. */
    public LocalDateTime getEndTime() {
        return wallClock(endedAt);
    }

    /** The instant the run was scheduled for. */
    public Instant getScheduledInstant() {
        return scheduledAt;
    }

    /** The instant the run began. */
    public Instant getStartInstant() {
        return startedAt;
    }

    /** The instant the run ended, or null while it runs. */
    public Instant getEndInstant() {
        return endedAt;
    }

    private static LocalDateTime wallClock(final Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public String getState() {
        return state.name();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRowsAffected() {
        return rowsAffected;
    }
}
