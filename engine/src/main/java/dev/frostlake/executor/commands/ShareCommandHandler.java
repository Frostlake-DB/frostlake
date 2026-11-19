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
import dev.frostlake.metastore.model.Share;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CREATE, ALTER, DROP and DESCRIBE of outbound shares and their rows in SHOW SHARES. A share keeps its comment and
 * the accounts it was given; no other account reads through it, and nothing is granted to it, so DESCRIBE answers
 * its columns and no rows.
 */
public final class ShareCommandHandler {

    private final Catalog catalog;
    private final AccountIdentity identity;

    /**
     * @param catalog  the catalog holding the account's shares
     * @param identity the account the shares belong to
     */
    public ShareCommandHandler(final Catalog catalog, final AccountIdentity identity) {
        this.catalog = catalog;
        this.identity = identity;
    }

    private AccountObjectRegistry<Share> registry() {
        return catalog.getShares();
    }

    /** Runs a CREATE or an ALTER of a share and answers its status. */
    public ResultSet handle(final FrostlakeParser.AccountObjectStatementContext ctx) {
        final String name = SqlIdentifiers.canonical(ctx.identifier());
        final Share standing = registry().find(name);
        if (ctx.ALTER() != null) {
            if (standing == null) {
                if (ctx.if_exists() != null) {
                    return StatusResults.of(StatusResults.EXECUTED);
                }
                throw missing(name);
            }
            alter(standing, ctx.shareAction());
            return StatusResults.of(StatusResults.EXECUTED);
        }
        if (ctx.if_not_exists() != null && ctx.or_replace() != null) {
            throw new RuntimeException(SqlCompilationError.of("options IF NOT EXISTS and OR REPLACE are incompatible."));
        }
        if (standing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(name);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        final Share share = new Share(name);
        share.setOwner(catalog.currentRoleForOwner());
        share.setComment(CommentClauses.text(ctx.commentClause()));
        registry().put(share);
        return StatusResults.created("Share", name);
    }

    private static void alter(final Share share, final FrostlakeParser.ShareActionContext action) {
        if (action.UNSET() != null) {
            share.setComment(null);
        } else if (action.ADD() != null) {
            for (final String account : accounts(action.qualifiedName())) {
                if (!share.getAccounts().contains(account)) {
                    share.getAccounts().add(account);
                }
            }
        } else if (action.REMOVE() != null) {
            share.getAccounts().removeAll(accounts(action.qualifiedName()));
        } else {
            for (final FrostlakeParser.ShareSetPropertyContext property : action.shareSetProperty()) {
                if (property.commentClause() != null) {
                    share.setComment(CommentClauses.text(property.commentClause()));
                } else {
                    share.getAccounts().clear();
                    share.getAccounts().addAll(accounts(property.qualifiedName()));
                }
            }
        }
    }

    private static List<String> accounts(final List<FrostlakeParser.QualifiedNameContext> written) {
        final List<String> accounts = new ArrayList<>();
        for (final FrostlakeParser.QualifiedNameContext account : written) {
            accounts.add(QualifiedName.join(ParseTreeText.qualifiedNameParts(account)));
        }
        return accounts;
    }

    /**
     * Drops the share of that name when there is one.
     *
     * @return whether one was dropped
     */
    boolean drop(final String name) {
        return registry().remove(name) != null;
    }

    private RuntimeException missing(final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("Share",
            DropCommandHandler.shareName(catalog.getAccountLocator(), name)));
    }

    /**
     * The rows of SHOW SHARES for the shares created here, by name: each OUTBOUND, owned by this account, with no
     * database until one is granted to it, and the accounts it reaches joined by commas.
     */
    List<Row> rows() {
        final List<Row> rows = new ArrayList<>();
        for (final Share share : registry().all()) {
            rows.add(new Row(Arrays.asList((Object) ShowResultHelpers.createdOn(share.getCreatedTime()), "OUTBOUND",
                identity.getOrganization() + "." + identity.getAccountName(), share.getName(), "",
                String.join(", ", share.getAccounts()), share.getOwner(), ShowResultHelpers.text(share.getComment()),
                "", "")));
        }
        return rows;
    }

    /**
     * DESCRIBE SHARE of a share created here: the kind, the name and the moment of each object granted to it - none.
     *
     * @param name the share's canonical name
     * @return the columns and no rows, or null when no such share was created here
     */
    ResultSet describe(final String name) {
        if (registry().find(name) == null) {
            return null;
        }
        return new ResultSet(Arrays.asList(new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("shared_on", ShowResultHelpers.CREATED_ON)), new ArrayList<Row>());
    }
}
