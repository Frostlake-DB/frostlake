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

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.functions.scalar.AutoTemporalParser;
import dev.frostlake.metastore.AccountObjectRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.ResourceMonitor;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledKindWords;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CREATE, ALTER and DROP of resource monitors and the rows of SHOW RESOURCE MONITORS. A monitor is kept as it was
 * declared - CREDIT_QUOTA, FREQUENCY, START_TIMESTAMP, END_TIMESTAMP, NOTIFY_USERS and the TRIGGERS - and nothing
 * is metered: the used credits stay at zero and no trigger fires.
 */
public final class ResourceMonitorCommandHandler {

    private static final List<String> FREQUENCIES = Arrays.asList("MONTHLY", "DAILY", "WEEKLY", "YEARLY", "NEVER");

    private final Catalog catalog;

    /** @param catalog the catalog holding the account's resource monitors */
    public ResourceMonitorCommandHandler(final Catalog catalog) {
        this.catalog = catalog;
    }

    private AccountObjectRegistry<ResourceMonitor> registry() {
        return catalog.getResourceMonitors();
    }

    /** Runs a CREATE or an ALTER of a resource monitor and answers its status. */
    public ResultSet handle(final FrostlakeParser.AccountObjectStatementContext ctx) {
        final String name = SqlIdentifiers.canonical(ctx.identifier());
        final ResourceMonitor standing = registry().find(name);
        if (ctx.ALTER() != null) {
            if (standing == null) {
                if (ctx.if_exists() != null) {
                    return StatusResults.of(StatusResults.EXECUTED);
                }
                throw missing(name);
            }
            apply(standing, ctx.resourceMonitorClause());
            return StatusResults.of(StatusResults.EXECUTED);
        }
        if (ctx.if_not_exists() != null && ctx.or_replace() != null) {
            throw new RuntimeException(SqlCompilationError.of("options IF NOT EXISTS and OR REPLACE are incompatible."));
        }
        final ResourceMonitor monitor = new ResourceMonitor(name);
        monitor.setOwner(catalog.currentRoleForOwner());
        apply(monitor, ctx.resourceMonitorClause());
        if (standing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(name);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        registry().put(monitor);
        return StatusResults.created("Resource monitor", name);
    }

    /**
     * Drops the monitor of that name when there is one.
     *
     * @return whether one was dropped
     */
    boolean drop(final String name) {
        return registry().remove(name) != null;
    }

    private void apply(final ResourceMonitor monitor, final List<FrostlakeParser.ResourceMonitorClauseContext> clauses) {
        for (final FrostlakeParser.ResourceMonitorClauseContext clause : clauses) {
            if (clause.triggersWord != null) {
                triggers(monitor, clause);
                continue;
            }
            final String key = ObjectProperties.key(clause.optionKey());
            final boolean unset = clause.NULL() != null;
            if ("CREDIT_QUOTA".equals(key)) {
                if (!unset && clause.INTEGER_LITERAL() == null && clause.FLOAT_LITERAL() == null) {
                    throw invalidValue(clause, key);
                }
                monitor.setCreditQuota(unset ? null : new BigDecimal(clause.INTEGER_LITERAL() != null
                    ? clause.INTEGER_LITERAL().getText() : clause.FLOAT_LITERAL().getText()));
            } else if ("FREQUENCY".equals(key)) {
                final String frequency = clause.identifier().isEmpty() || clause.LPAREN() != null ? null
                    : clause.identifier(0).getText().toUpperCase(Locale.ROOT);
                if (frequency == null || !FREQUENCIES.contains(frequency)) {
                    throw invalidValue(clause, key);
                }
                monitor.setFrequency(frequency);
            } else if ("START_TIMESTAMP".equals(key)) {
                monitor.setStartTime(moment(clause, key, true));
            } else if ("END_TIMESTAMP".equals(key)) {
                monitor.setEndTime(unset ? null : moment(clause, key, false));
            } else if ("NOTIFY_USERS".equals(key)) {
                if (clause.LPAREN() == null) {
                    throw invalidValue(clause, key);
                }
                final List<String> users = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext user : clause.identifier()) {
                    final String canonical = SqlIdentifiers.canonical(user);
                    if (!catalog.hasUserExact(canonical)) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("User", canonical));
                    }
                    users.add(canonical);
                }
                monitor.getNotifyUsers().clear();
                monitor.getNotifyUsers().addAll(users);
            } else {
                throw new RuntimeException(SqlCompilationError.of("invalid property '" + key
                    + "' for 'RESOURCE_MONITOR'"));
            }
        }
    }

    /** A TRIGGERS clause replaces the monitor's triggers: each is ON n PERCENT DO NOTIFY, SUSPEND or SUSPEND_IMMEDIATE. */
    private static void triggers(final ResourceMonitor monitor, final FrostlakeParser.ResourceMonitorClauseContext clause) {
        requireWord(clause.triggersWord, "TRIGGERS");
        final List<Integer> notifyAt = new ArrayList<>();
        Integer suspendAt = null;
        Integer suspendImmediatelyAt = null;
        for (final FrostlakeParser.ResourceMonitorTriggerContext trigger : clause.resourceMonitorTrigger()) {
            requireWord(trigger.percentWord, "PERCENT");
            final Integer threshold = Integer.valueOf(trigger.INTEGER_LITERAL().getText());
            if (trigger.SUSPEND() != null) {
                suspendAt = threshold;
            } else if (UnmodelledKindWords.is(trigger.actionWord, "SUSPEND_IMMEDIATE")) {
                suspendImmediatelyAt = threshold;
            } else {
                requireWord(trigger.actionWord, "NOTIFY");
                notifyAt.add(threshold);
            }
        }
        monitor.getNotifyAt().clear();
        monitor.getNotifyAt().addAll(notifyAt);
        monitor.setSuspendAt(suspendAt);
        monitor.setSuspendImmediatelyAt(suspendImmediatelyAt);
    }

    private static void requireWord(final Token word, final String expected) {
        if (!UnmodelledKindWords.is(word, expected)) {
            throw new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(word, word.getText())));
        }
    }

    /**
     * A moment a property is given: a string holding a date with or without a time, read in UTC unless it carries
     * an offset, or - for the start alone - the word IMMEDIATELY, which is the statement's own moment.
     */
    private static Instant moment(final FrostlakeParser.ResourceMonitorClauseContext clause, final String key,
                                  final boolean takesImmediately) {
        if (takesImmediately && clause.LPAREN() == null && !clause.identifier().isEmpty()
                && "IMMEDIATELY".equalsIgnoreCase(clause.identifier(0).getText())) {
            return StatementClock.instant();
        }
        if (clause.STRING_LITERAL() == null) {
            throw invalidValue(clause, key);
        }
        final String text = SqlStringLiterals.decode(clause.STRING_LITERAL().getText());
        if (takesImmediately && "IMMEDIATELY".equalsIgnoreCase(text.trim())) {
            return StatementClock.instant();
        }
        final int[] offset = new int[1];
        final LocalDateTime parsed;
        try {
            parsed = AutoTemporalParser.parseDateTime(text, offset);
        } catch (final RuntimeException e) {
            throw invalidValue(clause, key);
        }
        if (parsed == null) {
            throw invalidValue(clause, key);
        }
        final Instant utc = parsed.toInstant(ZoneOffset.UTC);
        return offset[0] == AutoTemporalParser.NO_OFFSET ? utc : utc.minusSeconds(offset[0] * 60L);
    }

    private static RuntimeException invalidValue(final FrostlakeParser.ResourceMonitorClauseContext clause,
                                                 final String key) {
        final String written = clause.getChild(clause.getChildCount() - 1).getText();
        return new RuntimeException(SqlCompilationError.invalidValueForProperty(
            clause.LPAREN() != null ? "(...)" : written, key));
    }

    private static RuntimeException missing(final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist("Resource monitor", name));
    }

    /**
     * The rows of SHOW RESOURCE MONITORS, by name: the quota and the credits to two decimals, nothing ever used;
     * the level a monitor a warehouse names holds; the thresholds as percentages, the notifying ones joined by
     * commas.
     */
    List<Row> rows() {
        final List<Row> rows = new ArrayList<>();
        for (final ResourceMonitor monitor : registry().all()) {
            final String quota = monitor.getCreditQuota() == null ? null
                : monitor.getCreditQuota().setScale(2, RoundingMode.HALF_UP).toPlainString();
            rows.add(new Row(Arrays.asList((Object) monitor.getName(), quota, "0.00", quota, level(monitor),
                monitor.getFrequency() == null ? "MONTHLY" : monitor.getFrequency(),
                ShowResultHelpers.createdOn(monitor.getStartTime() == null ? monitor.getCreatedTime()
                    : monitor.getStartTime()),
                ShowResultHelpers.createdOn(monitor.getEndTime()), percents(monitor.getNotifyAt()),
                monitor.getSuspendAt() == null ? null : monitor.getSuspendAt() + "%",
                monitor.getSuspendImmediatelyAt() == null ? null : monitor.getSuspendImmediatelyAt() + "%",
                ShowResultHelpers.createdOn(monitor.getCreatedTime()), monitor.getOwner(),
                ShowResultHelpers.text(monitor.getComment()), String.join(", ", monitor.getNotifyUsers()))));
        }
        return rows;
    }

    /** WAREHOUSE for a monitor a warehouse is assigned to, and nothing for one no warehouse names. */
    private String level(final ResourceMonitor monitor) {
        for (final Warehouse warehouse : catalog.getAllWarehouses()) {
            if (monitor.getName().equalsIgnoreCase(warehouse.getResourceMonitor())) {
                return "WAREHOUSE";
            }
        }
        return null;
    }

    private static String percents(final List<Integer> thresholds) {
        if (thresholds.isEmpty()) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        for (final Integer threshold : thresholds) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(threshold).append('%');
        }
        return out.toString();
    }
}
