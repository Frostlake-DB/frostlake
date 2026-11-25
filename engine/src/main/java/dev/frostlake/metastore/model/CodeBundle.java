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
 * A code bundle: a schema object holding one version of the files below a stage location. Frostlake keeps its
 * metadata only — where the version was copied from and the stage that held it.
 */
public final class CodeBundle {

    private final String name;
    private final Instant createdOn;
    private Instant updatedOn;
    private final String sourceLocation;
    private final String stageDatabase;
    private final String stageName;
    private String owner;
    private String comment;

    /**
     * @param name the canonical name
     * @param sourceLocation the stage location its version was copied from, as the account spells it
     * @param stageDatabase the database of the stage that holds the files
     * @param stageName the canonical name of that stage
     */
    public CodeBundle(final String name, final String sourceLocation, final String stageDatabase,
                      final String stageName) {
        this.name = name;
        this.sourceLocation = sourceLocation;
        this.stageDatabase = stageDatabase;
        this.stageName = stageName;
        this.createdOn = StatementClock.instant();
        this.updatedOn = createdOn;
    }

    /** The canonical name. */
    public String getName() {
        return name;
    }

    /** When it was created. */
    public Instant getCreatedOn() {
        return createdOn;
    }

    /** When it was last changed. */
    public Instant getUpdatedOn() {
        return updatedOn;
    }

    /** Marks it changed now. */
    public void touch() {
        this.updatedOn = StatementClock.instant();
    }

    /** The stage location its version was copied from. */
    public String getSourceLocation() {
        return sourceLocation;
    }

    /** The database of the stage that holds the files. */
    public String getStageDatabase() {
        return stageDatabase;
    }

    /** The canonical name of the stage that holds the files. */
    public String getStageName() {
        return stageName;
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
