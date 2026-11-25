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

import dev.frostlake.executor.CodeBundleSource;
import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.DdlStatusNote;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.expressions.UnsupportedFeatureException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.BackupAndBundleObjects;
import dev.frostlake.metastore.model.BackupPolicy;
import dev.frostlake.metastore.model.CodeBundle;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.task.CronSchedule;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The BACKUP POLICY and CODE BUNDLE statements over catalog metadata (live-verified). A backup policy keeps a
 * schedule — an interval of at least an hour, or a cron expression firing at most once an hour — and the days a
 * backup is kept, at least one of the two; nothing takes a backup. A code bundle keeps the one version CREATE copied
 * from a stage location; its files are not copied.
 */
public final class BackupAndBundleHandler {

    private static final String POLICY_KIND = "Backup Policy";
    private static final String BUNDLE_KIND = "Code Bundle";
    /** The kind a code bundle's property refusals name. */
    private static final String BUNDLE_PROPERTY_KIND = "NOTEBOOK_PROJECT";
    private static final String SCHEDULE = "SCHEDULE";
    private static final String EXPIRE_AFTER_DAYS = "EXPIRE_AFTER_DAYS";
    private static final String COMMENT = "COMMENT";
    private static final long MAX_EXPIRE_DAYS = 3653L;
    private static final String VERSION_NAME = "VERSION$1";
    private static final String[] DESCRIBE_COLUMNS = {"name", "comment", "default_version", "default_version_name",
        "default_version_alias", "default_version_location_uri", "default_version_source_location_uri",
        "default_version_git_commit_hash", "last_version_name", "last_version_alias", "last_version_location_uri",
        "last_version_source_location_uri", "last_version_git_commit_hash", "live_version_location_uri", "created_on",
        "owner_role", "owner_role_type", "container_type", "container_database", "container_schema",
        "container_name"};

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    /**
     * @param catalog the catalog the objects live in
     * @param queryExecutor the executor whose stage area a code bundle's source is looked for in
     */
    public BackupAndBundleHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    // ---- backup policies ----------------------------------------------------------------------------------

    /** Runs one backup policy statement: a result set for a SHOW or DESCRIBE, null for the others. */
    public Object handleBackupPolicy(final FrostlakeParser.BackupPolicyStatementContext ctx) {
        final int verb = ctx.getStart().getType();
        if (verb == FrostlakeParser.SHOW) {
            final List<Row> rows = new ArrayList<>();
            for (final Schema schema : scope(ctx.kindListingScope())) {
                for (final BackupPolicy policy : schema.getBackupAndBundleObjects().getBackupPolicies()) {
                    if (listed(policy.getName(), ctx.LIKE() != null ? ctx.stringText() : null)) {
                        rows.add(policyRow(schema, policy));
                    }
                }
            }
            return page(new ResultSet(policyColumns(), rows), ctx.kindListingTail());
        }
        final FrostlakeParser.QualifiedNameContext written = ctx.qualifiedName(0);
        final Schema schema = schemaOf(written);
        final String name = last(written);
        final BackupAndBundleObjects objects = schema.getBackupAndBundleObjects();
        final BackupPolicy existing = objects.getBackupPolicy(name);
        if (verb == FrostlakeParser.CREATE) {
            return createPolicy(ctx, objects, name, existing);
        }
        if (existing == null) {
            if (ctx.if_exists() != null) {
                if (verb == FrostlakeParser.DROP) {
                    ConditionalDdlOutcome.dropSkipped();
                }
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist(POLICY_KIND, fullName(schema, name)));
        }
        if (verb == FrostlakeParser.DROP) {
            objects.removeBackupPolicy(name);
            return null;
        }
        if (verb == FrostlakeParser.ALTER) {
            alterPolicy(ctx, objects, existing);
            return null;
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(policyRow(schema, existing));
        return new ResultSet(policyColumns(), rows);
    }

    private Object createPolicy(final FrostlakeParser.BackupPolicyStatementContext ctx,
                                final BackupAndBundleObjects objects, final String name, final BackupPolicy existing) {
        final Map<String, FrostlakeParser.KindPropertyValueContext> properties =
            properties(ctx.kindProperty(), POLICY_KIND, true);
        if (ctx.RETENTION() != null) {
            throw new RuntimeException(SqlCompilationError.inline("Backup policy with retention lock cannot be"
                + " created on accounts below Business Critical Edition."));
        }
        final FrostlakeParser.KindPropertyValueContext schedule = properties.get(SCHEDULE);
        final FrostlakeParser.KindPropertyValueContext expire = properties.get(EXPIRE_AFTER_DAYS);
        if (schedule == null && expire == null) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " error line 0 at position 1\nmissing required"
                + " argument [one of {schedule, expire_after_days}] for function [create backup policy <name>]");
        }
        final String scheduleText = schedule == null ? null : requireSchedule(schedule);
        final Long days = expire == null ? null : requireExpireDays(expire);
        if (existing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
        }
        final BackupPolicy policy = new BackupPolicy(name);
        policy.setSchedule(scheduleText);
        policy.setExpireAfterDays(days);
        policy.setComment(properties.containsKey(COMMENT) ? comment(properties.get(COMMENT)) : null);
        policy.setOwner(catalog.currentRoleForOwner());
        objects.putBackupPolicy(policy);
        return null;
    }

    private void alterPolicy(final FrostlakeParser.BackupPolicyStatementContext ctx,
                             final BackupAndBundleObjects objects, final BackupPolicy existing) {
        if (ctx.RENAME() != null) {
            final FrostlakeParser.QualifiedNameContext target = ctx.qualifiedName(1);
            final Schema targetSchema = schemaOf(target);
            final String newName = last(target);
            final BackupAndBundleObjects targetObjects = targetSchema.getBackupAndBundleObjects();
            if (targetObjects.getBackupPolicy(newName) != null) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + newName + "' already exists."));
            }
            objects.removeBackupPolicy(existing.getName());
            targetObjects.putBackupPolicy(existing.renamed(newName));
            return;
        }
        if (ctx.SET() != null) {
            final Map<String, FrostlakeParser.KindPropertyValueContext> properties =
                properties(ctx.kindProperty(), POLICY_KIND, true);
            final String scheduleText = properties.containsKey(SCHEDULE) ? requireSchedule(properties.get(SCHEDULE))
                : null;
            final Long days = properties.containsKey(EXPIRE_AFTER_DAYS)
                ? requireExpireDays(properties.get(EXPIRE_AFTER_DAYS)) : null;
            if (scheduleText != null) {
                existing.setSchedule(scheduleText);
            }
            if (days != null) {
                existing.setExpireAfterDays(days);
            }
            if (properties.containsKey(COMMENT)) {
                existing.setComment(comment(properties.get(COMMENT)));
            }
            return;
        }
        final List<String> unset = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext id : ctx.identifier()) {
            final String key = SqlIdentifiers.canonical(id).toUpperCase(Locale.ROOT);
            if (!SCHEDULE.equals(key) && !EXPIRE_AFTER_DAYS.equals(key) && !COMMENT.equals(key)) {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(SqlIdentifiers.canonical(id),
                    POLICY_KIND));
            }
            unset.add(key);
        }
        for (final String key : unset) {
            if (SCHEDULE.equals(key) && (existing.getExpireAfterDays() == null || unset.contains(EXPIRE_AFTER_DAYS))) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Cannot unset schedule if expire_after_days is also not set."));
            }
            if (EXPIRE_AFTER_DAYS.equals(key) && (existing.getSchedule() == null || unset.contains(SCHEDULE))) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Cannot unset expire_after_days if schedule is also not set."));
            }
        }
        for (final String key : unset) {
            if (SCHEDULE.equals(key)) {
                existing.setSchedule(null);
            } else if (EXPIRE_AFTER_DAYS.equals(key)) {
                existing.setExpireAfterDays(null);
            } else {
                existing.setComment(null);
            }
        }
    }

    /**
     * A schedule's text, kept as written: {@code <n> MINUTE[S]} of at least 60, {@code <n> HOUR[S]} of at least one,
     * the number and the word one space apart, or a {@code USING CRON} expression whose minute field names a single
     * minute, so that it fires at most once an hour.
     */
    private static String requireSchedule(final FrostlakeParser.KindPropertyValueContext value) {
        if (value.STRING_LITERAL() == null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(), SCHEDULE));
        }
        final String text = SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        if (text.trim().regionMatches(true, 0, "USING", 0, "USING".length())) {
            if (CronSchedule.parse(text).minutesPerHour() != 1) {
                throw new RuntimeException(CronSchedule.INVALID_SCHEDULE_MESSAGE);
            }
            return text;
        }
        final int space = text.indexOf(' ');
        if (space <= 0 || space != text.lastIndexOf(' ')) {
            throw new RuntimeException(CronSchedule.INVALID_SCHEDULE_MESSAGE);
        }
        final String number = text.startsWith("+") ? text.substring(1, space) : text.substring(0, space);
        final String unit = text.substring(space + 1).toUpperCase(Locale.ROOT);
        long amount;
        try {
            amount = number.isEmpty() || number.charAt(0) == '-' || number.charAt(0) == '+' ? -1L
                : Long.parseLong(number);
        } catch (final NumberFormatException notANumber) {
            amount = -1L;
        }
        final boolean minutes = "MINUTE".equals(unit) || "MINUTES".equals(unit);
        final boolean hours = "HOUR".equals(unit) || "HOURS".equals(unit);
        if (!(minutes && amount >= 60L || hours && amount >= 1L)) {
            throw new RuntimeException(CronSchedule.INVALID_SCHEDULE_MESSAGE);
        }
        return text;
    }

    private static Long requireExpireDays(final FrostlakeParser.KindPropertyValueContext value) {
        if (value.INTEGER_LITERAL() == null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(),
                EXPIRE_AFTER_DAYS));
        }
        final long days;
        try {
            days = Long.parseLong(value.getText());
        } catch (final NumberFormatException tooLarge) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(),
                EXPIRE_AFTER_DAYS));
        }
        if (days <= 0L) {
            throw new RuntimeException(SqlCompilationError.inline("Backup policy expire_after_days must be > 0 days,"
                + " but it is " + days + " days."));
        }
        if (days > MAX_EXPIRE_DAYS) {
            throw new RuntimeException(SqlCompilationError.inline("Backup policy cannot be created with"
                + " expire_after_days over 3,653 days."));
        }
        return days;
    }

    private static List<ResultSetColumn> policyColumns() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON));
        for (final String name : new String[] {"name", "database_name", "schema_name", "owner", "owner_role_type",
            "comment", "schedule"}) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        columns.add(new ResultSetColumn("expire_after_days", NumericType.NUMBER));
        columns.add(new ResultSetColumn("has_retention_lock", StringType.VARCHAR));
        return columns;
    }

    private Row policyRow(final Schema schema, final BackupPolicy policy) {
        return new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(policy.getCreatedOn()), policy.getName(),
            databaseOf(schema), schema.getName(), policy.getOwner(), ShowResultHelpers.ownerRoleType(policy.getOwner()),
            policy.getComment() == null ? "" : policy.getComment(), policy.getSchedule(), policy.getExpireAfterDays(),
            "false"));
    }

    // ---- code bundles -------------------------------------------------------------------------------------

    /** Runs one code bundle statement: a result set for a SHOW or DESCRIBE, null for the others. */
    public Object handleCodeBundle(final FrostlakeParser.CodeBundleStatementContext ctx) {
        final int verb = ctx.getStart().getType();
        if (verb == FrostlakeParser.SHOW) {
            final boolean terse = ctx.TERSE() != null;
            final List<Row> rows = new ArrayList<>();
            for (final Schema schema : scope(ctx.kindListingScope())) {
                for (final CodeBundle bundle : schema.getBackupAndBundleObjects().getCodeBundles()) {
                    if (listed(bundle.getName(), ctx.LIKE() != null ? ctx.stringText() : null)) {
                        rows.add(bundleRow(schema, bundle, terse));
                    }
                }
            }
            return page(new ResultSet(bundleColumns(terse), rows), ctx.kindListingTail());
        }
        final FrostlakeParser.QualifiedNameContext written = ctx.qualifiedName(0);
        final Schema schema = schemaOf(written);
        final String name = last(written);
        final BackupAndBundleObjects objects = schema.getBackupAndBundleObjects();
        final CodeBundle existing = objects.getCodeBundle(name);
        if (verb == FrostlakeParser.CREATE) {
            return createBundle(ctx, objects, name, existing);
        }
        if (existing == null) {
            if (ctx.if_exists() != null) {
                if (verb == FrostlakeParser.DROP) {
                    ConditionalDdlOutcome.dropSkipped();
                }
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist(BUNDLE_KIND, fullName(schema, name)));
        }
        if (verb == FrostlakeParser.DROP) {
            objects.removeCodeBundle(name);
            return null;
        }
        if (verb == FrostlakeParser.ALTER) {
            if (ctx.RENAME() != null) {
                throw new UnsupportedFeatureException("renaming " + BUNDLE_PROPERTY_KIND);
            }
            if (ctx.SET() != null) {
                final Map<String, FrostlakeParser.KindPropertyValueContext> properties =
                    properties(ctx.kindProperty(), BUNDLE_PROPERTY_KIND, false);
                existing.setComment(comment(properties.get(COMMENT)));
            } else {
                for (final FrostlakeParser.IdentifierContext id : ctx.identifier()) {
                    if (!COMMENT.equals(SqlIdentifiers.canonical(id).toUpperCase(Locale.ROOT))) {
                        throw new RuntimeException(SqlCompilationError.invalidPropertyFor(
                            SqlIdentifiers.canonical(id), BUNDLE_PROPERTY_KIND));
                    }
                }
                existing.setComment(null);
            }
            existing.touch();
            return null;
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(describeRow(schema, existing));
        return new ResultSet(columns(DESCRIBE_COLUMNS), rows);
    }

    private Object createBundle(final FrostlakeParser.CodeBundleStatementContext ctx,
                                final BackupAndBundleObjects objects, final String name, final CodeBundle existing) {
        if (existing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                return null;
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
        }
        if (ctx.tagList() != null) {
            throw new UnsupportedFeatureException("CODE BUNDLE WITH TAG");
        }
        final Map<String, FrostlakeParser.KindPropertyValueContext> properties =
            properties(ctx.kindProperty(), BUNDLE_PROPERTY_KIND, false);
        if (ctx.stageLocation() == null) {
            throw new RuntimeException(
                "Failed to validate properties required by Notebook Project: source location is missing.");
        }
        final CodeBundleSource source = CodeBundleSource.of(ctx.stageLocation(), catalog);
        final CodeBundle bundle = new CodeBundle(name, source.location(), source.stageDatabase(),
            source.stageName());
        bundle.setComment(properties.containsKey(COMMENT) ? comment(properties.get(COMMENT)) : null);
        bundle.setOwner(catalog.currentRoleForOwner());
        objects.putCodeBundle(bundle);
        if (!source.holdsFiles(queryExecutor)) {
            DdlStatusNote.add("Warning: source directory '" + source.written()
                + "' does not exist or contains no files.");
        }
        return null;
    }

    private static List<ResultSetColumn> bundleColumns(final boolean terse) {
        return terse ? columns("name", "database_name", "schema_name", "created_on", "updated_on", "owner_role")
            : columns("name", "database_name", "schema_name", "created_on", "updated_on", "owner_role",
                "owner_role_type", "comment");
    }

    private Row bundleRow(final Schema schema, final CodeBundle bundle, final boolean terse) {
        final List<Object> cells = new ArrayList<>(Arrays.<Object>asList(bundle.getName(), databaseOf(schema),
            schema.getName(), ShowResultHelpers.createdOn(bundle.getCreatedOn()),
            ShowResultHelpers.createdOn(bundle.getUpdatedOn()), bundle.getOwner()));
        if (!terse) {
            cells.add(ShowResultHelpers.ownerRoleType(bundle.getOwner()));
            cells.add(bundle.getComment());
        }
        return new Row(cells);
    }

    private Row describeRow(final Schema schema, final CodeBundle bundle) {
        final String uri = "snow://code bundle/" + SqlIdentifiers.spellAlreadyCanonicalPath(
            QualifiedName.join(databaseOf(schema), schema.getName(), bundle.getName())) + "/versions/version$1/";
        return new Row(Arrays.<Object>asList(bundle.getName(), bundle.getComment(), "LAST", VERSION_NAME, null, uri,
            bundle.getSourceLocation(), null, VERSION_NAME, null, uri, bundle.getSourceLocation(), null, null,
            ShowResultHelpers.createdOn(bundle.getCreatedOn()), bundle.getOwner(),
            ShowResultHelpers.ownerRoleType(bundle.getOwner()), "Stage", bundle.getStageDatabase(), null,
            bundle.getStageName()));
    }

    // ---- shared -------------------------------------------------------------------------------------------

    /**
     * The properties written, by upper-case name: one written twice is refused, and so is a name the kind does not
     * take, and — for a code bundle — a comment that is no text.
     */
    private static Map<String, FrostlakeParser.KindPropertyValueContext> properties(
            final List<FrostlakeParser.KindPropertyContext> written, final String kind, final boolean policy) {
        final Map<String, FrostlakeParser.KindPropertyValueContext> properties = new LinkedHashMap<>();
        for (final FrostlakeParser.KindPropertyContext property : written) {
            final String key = SqlIdentifiers.canonical(property.identifier()).toUpperCase(Locale.ROOT);
            final boolean known = COMMENT.equals(key) || policy && (SCHEDULE.equals(key)
                || EXPIRE_AFTER_DAYS.equals(key));
            if (!known) {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(
                    SqlIdentifiers.canonical(property.identifier()), kind));
            }
            if (properties.containsKey(key)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(key));
            }
            properties.put(key, property.kindPropertyValue());
        }
        final FrostlakeParser.KindPropertyValueContext comment = properties.get(COMMENT);
        if (comment != null && comment.STRING_LITERAL() == null && comment.NULL() == null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(comment.getText(), COMMENT));
        }
        return properties;
    }

    /** A COMMENT's text; NULL is no comment. */
    private static String comment(final FrostlakeParser.KindPropertyValueContext value) {
        return value == null || value.STRING_LITERAL() == null ? null
            : SqlStringLiterals.decode(value.STRING_LITERAL().getText());
    }

    private Schema schemaOf(final FrostlakeParser.QualifiedNameContext name) {
        return catalog.requireOwningSchema(QualifiedName.of(ParseTreeText.qualifiedNameParts(name)));
    }

    private static String last(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        return parts[parts.length - 1];
    }

    private String fullName(final Schema schema, final String name) {
        return QualifiedName.join(databaseOf(schema), schema.getName(), name);
    }

    private String databaseOf(final Schema schema) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema candidate : database.getAllSchemas()) {
                if (candidate == schema) {
                    return database.getName();
                }
            }
        }
        return catalog.getCurrentDatabase();
    }

    private static boolean listed(final String name, final FrostlakeParser.StringTextContext like) {
        return like == null || ShowResultHelpers.matchesLike(name, ParseTreeText.stringValue(like));
    }

    /** The schemas a listing's IN clause covers; the current database's without a clause. */
    private List<Schema> scope(final FrostlakeParser.KindListingScopeContext scope) {
        final List<Schema> schemas = new ArrayList<>();
        if (scope != null && (scope.SCHEMA() != null || scope.qualifiedName() != null && scope.DATABASE() == null)) {
            if (scope.qualifiedName() == null) {
                schemas.add(catalog.requireOwningSchema(QualifiedName.of("X")));
                return schemas;
            }
            final String[] written = ParseTreeText.qualifiedNameParts(scope.qualifiedName());
            final String database = written.length >= 2 ? written[written.length - 2] : catalog.getCurrentDatabase();
            schemas.add(catalog.databaseExact(database).schemaExact(written[written.length - 1]));
            return schemas;
        }
        String database = null;
        if (scope != null && scope.DATABASE() != null) {
            database = scope.identifier() != null
                ? catalog.databaseExact(SqlIdentifiers.canonical(scope.identifier())).getName()
                : catalog.getCurrentDatabase();
        } else if (scope == null) {
            database = catalog.getCurrentDatabase();
        }
        for (final Database candidate : catalog.getAllDatabases()) {
            if (database == null || database.equals(candidate.getName())) {
                schemas.addAll(candidate.getAllSchemas());
            }
        }
        return schemas;
    }

    /** A listing's page: the rows from the STARTS WITH's prefix on, at most the LIMIT's, from the FROM's name. */
    private static ResultSet page(final ResultSet set, final FrostlakeParser.KindListingTailContext tail) {
        if (tail == null || tail.STARTS() == null && tail.LIMIT() == null) {
            return set;
        }
        final int named = set.getColumns().get(0).getName().equals("name") ? 0 : 1;
        final String prefix = tail.STARTS() != null ? ParseTreeText.stringValue(tail.stringText(0)) : null;
        final String from = tail.FROM() != null ? ParseTreeText.stringValue(tail.stringText(tail.STARTS() != null
            ? 1 : 0)) : null;
        final int max = tail.LIMIT() != null ? ShowPageSize.require(tail.INTEGER_LITERAL().getText())
            : Integer.MAX_VALUE;
        final List<Row> kept = new ArrayList<>();
        for (final Row row : set.getRows()) {
            final String name = String.valueOf(row.getValue(named));
            if (prefix != null && !name.startsWith(prefix) || from != null && name.compareTo(from) < 0) {
                continue;
            }
            if (kept.size() < max) {
                kept.add(row);
            }
        }
        return new ResultSet(set.getColumns(), kept);
    }

    private static List<ResultSetColumn> columns(final String... names) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : names) {
            final boolean stamp = "created_on".equals(name) || "updated_on".equals(name);
            columns.add(new ResultSetColumn(name, stamp ? ShowResultHelpers.CREATED_ON : StringType.VARCHAR));
        }
        return columns;
    }
}
