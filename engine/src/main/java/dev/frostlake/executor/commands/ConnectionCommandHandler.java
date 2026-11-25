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
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.AccountObjectRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Connection;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CREATE, ALTER and DROP of connections, their comments, and their rows in SHOW CONNECTIONS. A connection keeps what
 * was declared - its comment, the primary a secondary was declared a replica of and the accounts failover was enabled
 * to; no client is redirected and nothing fails over.
 */
public final class ConnectionCommandHandler {

    private final Catalog catalog;
    private final AccountIdentity identity;

    /**
     * @param catalog  the catalog holding the account's connections
     * @param identity the account the connections belong to
     */
    public ConnectionCommandHandler(final Catalog catalog, final AccountIdentity identity) {
        this.catalog = catalog;
        this.identity = identity;
    }

    private AccountObjectRegistry<Connection> registry() {
        return catalog.getConnections();
    }

    /** Runs a CREATE or an ALTER of a connection and answers its status. */
    public ResultSet handle(final FrostlakeParser.AccountObjectStatementContext ctx) {
        final String name = SqlIdentifiers.canonical(ctx.identifier());
        final Connection standing = registry().find(name);
        if (ctx.ALTER() != null) {
            if (standing == null) {
                if (ctx.if_exists() != null) {
                    return StatusResults.of(StatusResults.EXECUTED);
                }
                throw missing(name);
            }
            alter(standing, ctx.connectionAction());
            return StatusResults.of(StatusResults.EXECUTED);
        }
        if (standing != null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(name);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        final Connection connection = new Connection(name);
        connection.setOwner(catalog.currentRoleForOwner());
        if (ctx.qualifiedName() != null) {
            connection.setPrimary(QualifiedName.join(ParseTreeText.qualifiedNameParts(ctx.qualifiedName())));
        }
        if (ctx.commentClause() != null) {
            connection.setComment(CommentClauses.text(ctx.commentClause()));
        }
        registry().put(connection);
        return StatusResults.created("Connection", name);
    }

    private static void alter(final Connection connection, final FrostlakeParser.ConnectionActionContext action) {
        if (action.PRIMARY() != null) {
            connection.setPrimary(null);
            return;
        }
        final List<String> accounts = connection.getFailoverAccounts();
        if (action.DISABLE() != null && action.qualifiedName().isEmpty()) {
            accounts.clear();
            return;
        }
        for (final FrostlakeParser.QualifiedNameContext written : action.qualifiedName()) {
            final String account = QualifiedName.join(ParseTreeText.qualifiedNameParts(written));
            if (action.DISABLE() != null) {
                accounts.remove(account);
            } else if (!accounts.contains(account)) {
                accounts.add(account);
            }
        }
    }

    /**
     * Sets or unsets the comment of the connection a name of one part names, or renames it. A name of more parts
     * names no connection, and neither does a name nothing holds, which IF EXISTS forgives.
     *
     * @param parts    the name's canonical parts
     * @param comment  the comment, or null to unset it
     * @param renamed  the new name's canonical parts of a RENAME, or null to set or unset the comment
     * @param ifExists whether IF EXISTS forgives a missing connection
     */
    void comment(final String[] parts, final String comment, final String[] renamed, final boolean ifExists) {
        if (parts.length > 1) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final Connection standing = registry().find(parts[0]);
        if (standing == null) {
            if (ifExists) {
                return;
            }
            throw missing(parts[0]);
        }
        if (renamed == null) {
            standing.setComment(comment);
            return;
        }
        if (renamed.length > 1) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final String target = renamed[0];
        if (registry().find(target) != null) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(target)
                + "' already exists."));
        }
        registry().remove(standing.getName());
        standing.setName(target);
        registry().put(standing);
    }

    /**
     * Drops the connection of that name when there is one.
     *
     * @return whether one was dropped
     */
    boolean drop(final String name) {
        return registry().remove(name) != null;
    }

    private static RuntimeException missing(final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist("Connection", name));
    }

    /**
     * The rows of SHOW CONNECTIONS, by name: each a connection of this account, its primary its own full name unless
     * it was declared a replica, reached at the organization's URL for it.
     */
    List<Row> rows() {
        final List<Row> rows = new ArrayList<>();
        final String account = identity.getOrganization() + "." + identity.getAccountName();
        for (final Connection connection : registry().all()) {
            final String primary = connection.getPrimary() == null ? account + "." + connection.getName()
                : connection.getPrimary();
            final String url = (identity.getOrganization() + "-" + connection.getName()).toLowerCase(Locale.ROOT)
                + ".snowflakecomputing.com";
            rows.add(new Row(Arrays.asList((Object) identity.getRegion(),
                ShowResultHelpers.createdOn(connection.getCreatedTime()), identity.getAccountName(),
                connection.getName(), ShowResultHelpers.text(connection.getComment()), primary, null, null, url)));
        }
        return rows;
    }
}
