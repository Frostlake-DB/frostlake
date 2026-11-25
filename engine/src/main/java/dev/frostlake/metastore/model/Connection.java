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
 * A connection: an account-level object a client connects through, which client redirect would point at the
 * account serving it. Frostlake keeps what was declared - its comment, the primary a secondary was declared a
 * replica of, the accounts failover was enabled to; nothing is redirected and no failover ever happens.
 */
public class Connection extends SqlObject {

    private final List<String> failoverAccounts = new ArrayList<>();
    private String primary;

    /** @param name the connection's canonical name */
    public Connection(final String name) {
        super(name);
        this.owner = "ACCOUNTADMIN";
    }

    @Override
    public String getObjectType() {
        return "CONNECTION";
    }

    /** The accounts failover was enabled to, each {@code ORGANIZATION.ACCOUNT}. */
    public List<String> getFailoverAccounts() {
        return failoverAccounts;
    }

    /** The primary connection a secondary was declared a replica of, {@code ORG.ACCOUNT.NAME}, or null. */
    public String getPrimary() {
        return primary;
    }

    /** Makes the connection a secondary of that primary; null makes it a primary. */
    public void setPrimary(final String primary) {
        this.primary = primary;
    }
}
