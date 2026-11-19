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

package dev.frostlake.executor.commands;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;

/**
 * The hold a network policy has on the network rules its lists name (live-verified): a rule on a policy's allowed or
 * blocked list can be neither dropped nor replaced, and neither can the schema holding it.
 *
 * <pre>
 *   DROP NETWORK RULE r                Cannot drop Network rule R as it is associated with some network policies.
 *   CREATE OR REPLACE NETWORK RULE r   the same
 *   DROP SCHEMA s                      Cannot drop schema S as it includes network rule - policy associations.
 *   CREATE OR REPLACE SCHEMA s         the same
 * </pre>
 */
final class NetworkRuleAssociations {

    private static final String[] LISTS = {"ALLOWED_NETWORK_RULE_LIST", "BLOCKED_NETWORK_RULE_LIST"};

    private NetworkRuleAssociations() {
    }

    /**
     * Refuses to drop or replace a rule some network policy lists.
     *
     * @param catalog the catalog holding the account's network policies
     * @param schema the rule's schema
     * @param rule the rule's name
     */
    static void requireFree(final Catalog catalog, final Schema schema, final String rule) {
        if (listed(catalog, schema.qualifiedName(rule))) {
            throw new NetworkRuleAssociationRefusal("Cannot drop Network rule " + rule
                + " as it is associated with some network policies.");
        }
    }

    /**
     * Refuses to drop or replace a schema holding a rule some network policy lists.
     *
     * @param catalog the catalog holding the account's network policies
     * @param schema the schema, or null when the statement names none that exists
     */
    static void requireFree(final Catalog catalog, final Schema schema) {
        if (schema == null) {
            return;
        }
        for (final SecurityObject rule : schema.getSecurityObjects().list(SecurityObjectKind.NETWORK_RULE)) {
            if (listed(catalog, schema.qualifiedName(rule.getName()))) {
                throw new NetworkRuleAssociationRefusal("Cannot drop schema " + schema.getName()
                    + " as it includes network rule - policy associations.");
            }
        }
    }

    private static boolean listed(final Catalog catalog, final String qualifiedRule) {
        for (final SecurityObject policy : catalog.getSecurityObjects().list(SecurityObjectKind.NETWORK_POLICY)) {
            for (final String list : LISTS) {
                if (policy.list(list).contains(qualifiedRule)) {
                    return true;
                }
            }
        }
        return false;
    }
}
