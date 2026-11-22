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

import dev.frostlake.metastore.SqlObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A failover group: an account-level object naming the object types, databases, shares and integration types
 * replicated to a list of accounts, and on what schedule. Frostlake keeps what was declared; nothing is replicated
 * and no failover ever happens. A secondary group remembers the primary it was declared a replica of.
 */
public class FailoverGroup extends SqlObject {

    private final List<String> objectTypes = new ArrayList<>();
    private final List<String> allowedDatabases = new ArrayList<>();
    private final List<String> allowedShares = new ArrayList<>();
    private final List<String> allowedIntegrationTypes = new ArrayList<>();
    private final List<String> allowedAccounts = new ArrayList<>();
    private String replicationSchedule;
    private String errorIntegration;
    private String primary;
    private boolean optimizedRefresh;

    /** @param name the group's canonical name */
    public FailoverGroup(final String name) {
        super(name);
        this.owner = "ACCOUNTADMIN";
    }

    @Override
    public String getObjectType() {
        return "FAILOVER GROUP";
    }

    /** The object types replicated, as their upper-case words. */
    public List<String> getObjectTypes() {
        return objectTypes;
    }

    /** The databases replicated, by canonical name. */
    public List<String> getAllowedDatabases() {
        return allowedDatabases;
    }

    /** The shares replicated, by canonical name. */
    public List<String> getAllowedShares() {
        return allowedShares;
    }

    /** The integration types replicated, as their upper-case words. */
    public List<String> getAllowedIntegrationTypes() {
        return allowedIntegrationTypes;
    }

    /** The accounts replicated to, each {@code ORGANIZATION.ACCOUNT}. */
    public List<String> getAllowedAccounts() {
        return allowedAccounts;
    }

    /** The replication schedule as written, or null. */
    public String getReplicationSchedule() {
        return replicationSchedule;
    }

    /** Sets the replication schedule; null takes it away. */
    public void setReplicationSchedule(final String replicationSchedule) {
        this.replicationSchedule = replicationSchedule;
    }

    /** The notification integration errors are sent to, or null. */
    public String getErrorIntegration() {
        return errorIntegration;
    }

    /** Sets the error integration; null takes it away. */
    public void setErrorIntegration(final String errorIntegration) {
        this.errorIntegration = errorIntegration;
    }

    /** Whether OPTIMIZED_REFRESH was set to TRUE. */
    public boolean isOptimizedRefresh() {
        return optimizedRefresh;
    }

    /** Sets OPTIMIZED_REFRESH. */
    public void setOptimizedRefresh(final boolean optimizedRefresh) {
        this.optimizedRefresh = optimizedRefresh;
    }

    /** The primary group a secondary was declared a replica of, {@code ORG.ACCOUNT.NAME}, or null for a primary. */
    public String getPrimary() {
        return primary;
    }

    /** Makes the group a secondary of that primary. */
    public void setPrimary(final String primary) {
        this.primary = primary;
    }
}
