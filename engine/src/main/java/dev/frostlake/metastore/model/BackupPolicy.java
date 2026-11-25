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

import dev.frostlake.executor.StatementClock;

import java.time.Instant;

/**
 * A backup policy: a schema object holding the schedule backups of a backup set are taken on and the days each is
 * kept. Frostlake keeps its metadata only; nothing takes a backup.
 */
public final class BackupPolicy {

    private final String name;
    private final Instant createdOn;
    private String schedule;
    private Long expireAfterDays;
    private String owner;
    private String comment;

    /**
     * @param name the canonical name
     */
    public BackupPolicy(final String name) {
        this(name, StatementClock.instant());
    }

    /**
     * A policy created at a given moment, as a rename keeps it.
     *
     * @param name the canonical name
     * @param createdOn when it was created
     */
    public BackupPolicy(final String name, final Instant createdOn) {
        this.name = name;
        this.createdOn = createdOn;
    }

    /**
     * The same policy under another name, its creation time and properties kept.
     *
     * @param newName the canonical name it takes
     * @return the renamed copy
     */
    public BackupPolicy renamed(final String newName) {
        final BackupPolicy copy = new BackupPolicy(newName, createdOn);
        copy.schedule = schedule;
        copy.expireAfterDays = expireAfterDays;
        copy.owner = owner;
        copy.comment = comment;
        return copy;
    }

    /** The canonical name. */
    public String getName() {
        return name;
    }

    /** When it was created. */
    public Instant getCreatedOn() {
        return createdOn;
    }

    /** The schedule as written, or null. */
    public String getSchedule() {
        return schedule;
    }

    /** Sets the schedule; null clears it. */
    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }

    /** The days a backup is kept, or null. */
    public Long getExpireAfterDays() {
        return expireAfterDays;
    }

    /** Sets the days a backup is kept; null clears them. */
    public void setExpireAfterDays(final Long expireAfterDays) {
        this.expireAfterDays = expireAfterDays;
    }

    /** The owning role, or null. */
    public String getOwner() {
        return owner;
    }

    /** Sets the owning role. */
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    /** The comment, or null. */
    public String getComment() {
        return comment;
    }

    /** Sets the comment; null clears it. */
    public void setComment(final String comment) {
        this.comment = comment;
    }
}
