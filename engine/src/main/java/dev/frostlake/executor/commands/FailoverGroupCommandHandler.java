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

import dev.frostlake.config.AccountIdentity;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.AccountObjectRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.FailoverGroup;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CREATE, ALTER and DROP of failover and replication groups, their rows in SHOW FAILOVER GROUPS and SHOW REPLICATION
 * GROUPS, and the databases and shares a group lists. The two kinds share the account's names, and each statement
 * acts on a group of the kind it names. A group keeps what was declared - its object types, the databases, shares and integration types it
 * allows, the accounts it replicates to and its schedule; nothing is replicated, refreshed or failed over.
 */
public final class FailoverGroupCommandHandler {

    private final Catalog catalog;
    private final AccountIdentity identity;

    /**
     * @param catalog  the catalog holding the account's failover groups
     * @param identity the account the groups belong to
     */
    public FailoverGroupCommandHandler(final Catalog catalog, final AccountIdentity identity) {
        this.catalog = catalog;
        this.identity = identity;
    }

    private AccountObjectRegistry<FailoverGroup> registry() {
        return catalog.getFailoverGroups();
    }

    /** Runs a CREATE or an ALTER of a failover or a replication group and answers its status. */
    public ResultSet handle(final FrostlakeParser.AccountObjectStatementContext ctx) {
        final String name = SqlIdentifiers.canonical(ctx.identifier());
        final String groupType = ctx.failoverWord.getText().toUpperCase(Locale.ROOT);
        final FailoverGroup standing = registry().find(name);
        if (ctx.ALTER() != null) {
            if (standing == null || !standing.getGroupType().equals(groupType)) {
                if (ctx.if_exists() != null) {
                    return StatusResults.of(StatusResults.EXECUTED);
                }
                throw missing(name);
            }
            alter(standing, ctx.failoverGroupAction());
            return StatusResults.of(StatusResults.EXECUTED);
        }
        if (standing != null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(name);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        final FailoverGroup group = new FailoverGroup(name, groupType);
        group.setOwner(catalog.currentRoleForOwner());
        if (ctx.qualifiedName() != null) {
            group.setPrimary(QualifiedName.join(ParseTreeText.qualifiedNameParts(ctx.qualifiedName())));
        } else {
            set(group, ctx.failoverGroupProperty());
            if (group.getObjectTypes().isEmpty()) {
                throw new RuntimeException(SqlCompilationError.of("Missing option(s): [OBJECT_TYPES]"));
            }
            if (group.getAllowedAccounts().isEmpty()) {
                throw new RuntimeException(SqlCompilationError.of("Missing option(s): [ALLOWED_ACCOUNTS]"));
            }
        }
        registry().put(group);
        return StatusResults.created(group.isFailover() ? "Failover group" : "Replication group", name);
    }

    /**
     * Drops the group of that name when there is one of the kind the statement names.
     *
     * @param name     the group's canonical name
     * @param failover whether the statement names a failover group, rather than a replication group
     * @return whether one was dropped
     */
    boolean drop(final String name, final boolean failover) {
        final FailoverGroup standing = registry().find(name);
        if (standing == null || standing.isFailover() != failover) {
            return false;
        }
        return registry().remove(name) != null;
    }

    private void alter(final FailoverGroup group, final FrostlakeParser.FailoverGroupActionContext action) {
        if (action.RENAME() != null) {
            final String renamed = SqlIdentifiers.canonical(action.identifier());
            if (registry().find(renamed) != null) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(renamed)
                    + "' already exists."));
            }
            registry().remove(group.getName());
            group.setName(renamed);
            registry().put(group);
        } else if (action.SET() != null) {
            set(group, action.failoverGroupProperty());
        } else if (action.UNSET() != null) {
            for (final FrostlakeParser.OptionKeyContext written : action.optionKey()) {
                final String key = ObjectProperties.key(written);
                if ("COMMENT".equals(key)) {
                    group.setComment(null);
                } else if ("REPLICATION_SCHEDULE".equals(key)) {
                    group.setReplicationSchedule(null);
                } else if ("ERROR_INTEGRATION".equals(key)) {
                    group.setErrorIntegration(null);
                } else if ("OPTIMIZED_REFRESH".equals(key)) {
                    group.setOptimizedRefresh(false);
                } else {
                    throw invalidProperty(group, key);
                }
            }
        } else if (action.ADD() != null || action.REMOVE() != null) {
            final String key = ObjectProperties.key(action.optionKey(0));
            final List<String> list = list(group, key);
            if (list == null || list == group.getObjectTypes()) {
                throw invalidProperty(group, key);
            }
            for (final FrostlakeParser.QualifiedNameContext written : action.qualifiedName()) {
                final String value = QualifiedName.join(ParseTreeText.qualifiedNameParts(written));
                if (action.REMOVE() != null) {
                    list.remove(value);
                } else if (!list.contains(value)) {
                    list.add(value);
                }
            }
        }
        // REFRESH, PRIMARY, SUSPEND and RESUME have nothing to act on: no replication runs.
    }

    private static void set(final FailoverGroup group,
                            final List<FrostlakeParser.FailoverGroupPropertyContext> properties) {
        for (final FrostlakeParser.FailoverGroupPropertyContext property : properties) {
            if (property.IGNORE() != null) {
                continue;
            }
            final String key = ObjectProperties.key(property.optionKey());
            final List<String> values = new ArrayList<>();
            for (final FrostlakeParser.FailoverGroupValueContext value : property.failoverGroupValue()) {
                values.add(text(value));
            }
            final List<String> list = list(group, key);
            if (list != null) {
                list.clear();
                for (final String value : values) {
                    list.add(key.endsWith("TYPES") ? value.toUpperCase(Locale.ROOT) : value);
                }
            } else if ("COMMENT".equals(key) && values.size() == 1
                    && property.failoverGroupValue(0).STRING_LITERAL() != null) {
                group.setComment(values.get(0));
            } else if ("REPLICATION_SCHEDULE".equals(key) && values.size() == 1) {
                group.setReplicationSchedule(values.get(0));
            } else if ("ERROR_INTEGRATION".equals(key) && values.size() == 1) {
                group.setErrorIntegration(values.get(0));
            } else if ("OPTIMIZED_REFRESH".equals(key) && values.size() == 1
                    && property.failoverGroupValue(0).booleanValue() != null) {
                group.setOptimizedRefresh(property.failoverGroupValue(0).booleanValue().TRUE() != null);
            } else if (!"ALLOWED_EXTERNAL_VOLUMES".equals(key)) {
                throw invalidProperty(group, key);
            }
        }
    }

    /** The list a property names, or null for a property that is no list. */
    private static List<String> list(final FailoverGroup group, final String key) {
        if ("OBJECT_TYPES".equals(key)) {
            return group.getObjectTypes();
        }
        if ("ALLOWED_DATABASES".equals(key)) {
            return group.getAllowedDatabases();
        }
        if ("ALLOWED_SHARES".equals(key)) {
            return group.getAllowedShares();
        }
        if ("ALLOWED_INTEGRATION_TYPES".equals(key)) {
            return group.getAllowedIntegrationTypes();
        }
        return "ALLOWED_ACCOUNTS".equals(key) ? group.getAllowedAccounts() : null;
    }

    /** A value as it is kept: a string's text, a name's canonical parts, or a kind's words apart by one blank. */
    private static String text(final FrostlakeParser.FailoverGroupValueContext value) {
        if (value.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        }
        if (value.qualifiedName() != null) {
            return QualifiedName.join(ParseTreeText.qualifiedNameParts(value.qualifiedName()));
        }
        final StringBuilder words = new StringBuilder();
        for (int i = 0; i < value.getChildCount(); i++) {
            if (words.length() > 0) {
                words.append(' ');
            }
            words.append(value.getChild(i).getText().toUpperCase(Locale.ROOT));
        }
        return words.toString();
    }

    private static RuntimeException invalidProperty(final FailoverGroup group, final String key) {
        return new RuntimeException(SqlCompilationError.of("invalid property '" + key + "' for '"
            + group.getGroupType() + "_GROUP'"));
    }

    private static RuntimeException missing(final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist("Replication group", name));
    }

    /**
     * The rows of SHOW FAILOVER GROUPS or SHOW REPLICATION GROUPS, by name: each a group of this account, of its
     * kind, primary unless it was declared a replica, never refreshed. The failover groups list only failover
     * groups, the replication groups both kinds.
     *
     * @param failoverOnly whether the listing is of the failover groups alone
     */
    List<Row> rows(final boolean failoverOnly) {
        final List<Row> rows = new ArrayList<>();
        final String account = identity.getOrganization() + "." + identity.getAccountName();
        for (final FailoverGroup group : registry().all()) {
            if (failoverOnly && !group.isFailover()) {
                continue;
            }
            final boolean primary = group.getPrimary() == null;
            rows.add(new Row(Arrays.asList((Object) identity.getRegion(),
                ShowResultHelpers.createdOn(group.getCreatedTime()), identity.getAccountName(), group.getName(),
                group.getGroupType(), ShowResultHelpers.text(group.getComment()), primary ? "true" : "false",
                primary ? account + "." + group.getName() : group.getPrimary(),
                String.join(", ", group.getObjectTypes()), String.join(", ", group.getAllowedIntegrationTypes()),
                String.join(", ", group.getAllowedAccounts()), identity.getOrganization(),
                identity.getAccountLocator(), group.getReplicationSchedule(), null, null, group.getOwner(), "false",
                group.getErrorIntegration(), "false", group.isOptimizedRefresh() ? "true" : "false")));
        }
        return rows;
    }

    /**
     * SHOW DATABASES or SHOW SHARES IN FAILOVER GROUP g: the databases or the shares the group allows, as their own
     * listing shows them. A group that does not exist is refused, the database listing by the sentence that names
     * nothing and the share listing by the group's name (live-verified).
     *
     * @return the listing, or null when the statement is no such listing
     */
    ResultSet listingInGroup(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                             final QueryExecutor queryExecutor) {
        if (listing != ShowListing.DATABASES && listing != ShowListing.SHARES) {
            return null;
        }
        final ShowScopeKind kind = ShowScopeKindRefusal.scopeKind(ctx);
        if (kind != ShowScopeKind.FAILOVER_GROUP && kind != ShowScopeKind.REPLICATION_GROUP) {
            return null;
        }
        final String[] parts = ShowScopeKindRefusal.instanceParts(ctx, queryExecutor);
        final FailoverGroup group = parts.length == 1 ? registry().find(parts[0]) : null;
        if (group == null) {
            throw new RuntimeException(listing == ShowListing.DATABASES || parts.length > 1
                ? SqlCompilationError.objectDoesNotExist()
                : SqlCompilationError.doesNotExist("Replication group", parts[0]));
        }
        final boolean databases = listing == ShowListing.DATABASES;
        final ResultSet all = databases ? queryExecutor.getShowExecutor().showDatabases()
            : new ResultSet(UnmodelledListing.SHARES.columns(false),
                new ShareCommandHandler(catalog, identity).rows());
        final List<String> allowed = databases ? group.getAllowedDatabases() : group.getAllowedShares();
        final int name = all.getColumnIndex("name");
        final List<Row> rows = new ArrayList<>();
        for (final Row row : all.getRows()) {
            if (allowed.contains(String.valueOf(row.getValue(name)))) {
                rows.add(row);
            }
        }
        return new ResultSet(all.getColumns(), rows);
    }
}
