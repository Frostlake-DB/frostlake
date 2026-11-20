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

import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.ExternalTableMetadata;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.VariantType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CREATE and DROP of external tables and their rows in SHOW EXTERNAL TABLES. An external table is kept as a table
 * of its declared columns - VALUE, and each column computed from it - beside the stage location and the file
 * format it was declared over. Frostlake reads no file through it: the table holds no rows, a refresh finds
 * nothing, and a query over it answers none.
 */
public final class ExternalTableCommandHandler {

    private static final List<String> FORMAT_TYPES = Arrays.asList("CSV", "JSON", "AVRO", "ORC", "PARQUET");

    private static final List<String> FLAGS = Arrays.asList("REFRESH_ON_CREATE", "AUTO_REFRESH");

    private static final List<String> TEXTS = Arrays.asList("PATTERN", "AWS_SNS_TOPIC", "INTEGRATION",
        "PARTITION_TYPE", "TABLE_FORMAT");

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor types;

    /**
     * @param catalog       the catalog the table is made in
     * @param queryExecutor the executor names and privileges resolve through
     * @param types         the reader of a column's declared type
     */
    public ExternalTableCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                                       final SQLCommandVisitor types) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.types = types;
    }

    /**
     * CREATE [OR REPLACE] EXTERNAL TABLE [IF NOT EXISTS]. The statement is refused, in this order, for IF NOT
     * EXISTS beside OR REPLACE, a missing LOCATION, a missing FILE_FORMAT, a property or a format type that is
     * none, a stage that does not exist and a stage that is internal (live-verified).
     */
    public ResultSet create(final FrostlakeParser.AccountObjectStatementContext ctx) {
        if (ctx.if_not_exists() != null && ctx.or_replace() != null) {
            throw new RuntimeException(SqlCompilationError.of("options IF NOT EXISTS and OR REPLACE are incompatible."));
        }
        FrostlakeParser.StageRefContext location = null;
        FrostlakeParser.ParenOptionListContext format = null;
        String comment = null;
        final ExternalTableMetadata metadata = new ExternalTableMetadata();
        final List<String> invalid = new ArrayList<>();
        for (final FrostlakeParser.ExternalTableOptionContext option : ctx.externalTableOption()) {
            if (option.commentClause() != null) {
                comment = CommentClauses.text(option.commentClause());
            } else if (option.optionKey() != null) {
                final String key = ObjectProperties.key(option.optionKey());
                if ("LOCATION".equals(key) && option.stageRef() != null) {
                    location = option.stageRef();
                } else if ("FILE_FORMAT".equals(key) && option.parenOptionList() != null) {
                    format = option.parenOptionList();
                } else if (FLAGS.contains(key) && option.booleanValue() != null) {
                    continue;
                } else if (TEXTS.contains(key) && (option.STRING_LITERAL() != null || option.valueWord != null)) {
                    if ("TABLE_FORMAT".equals(key)) {
                        metadata.setTableFormat(option.valueWord != null
                            ? option.valueWord.getText().toUpperCase(Locale.ROOT)
                            : SqlStringLiterals.decode(option.STRING_LITERAL().getText()).toUpperCase(Locale.ROOT));
                    }
                } else {
                    invalid.add(key);
                }
            }
        }
        if (location == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): location"));
        }
        if (format == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): file_format"));
        }
        if (!invalid.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("invalid property '" + invalid.get(0) + "' for 'TABLE'"));
        }
        readFormat(format, metadata);
        final Stage stage = stage(location, metadata);
        if (stage.getUrl() == null) {
            throw new RuntimeException("Cannot use internal stage " + stage.getName()
                + " as the location for an external table.");
        }
        metadata.setCloud(cloud(stage.getUrl()));
        final String[] parts = catalog.withoutAccount(queryExecutor.resolveObjectNameParts(ctx.objectName()), 3);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        schema.rejectNameHeldByOtherKind(name, RelationKind.TABLE, false);
        final boolean exists = schema.hasTable(name);
        if (exists && ctx.if_not_exists() != null) {
            ConditionalDdlOutcome.createSkipped();
            return StatusResults.alreadyExists(name);
        }
        if (exists && ctx.or_replace() == null) {
            throw new RuntimeException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellAlreadyCanonicalPath(QualifiedName.join(parts)) + "' already exists."));
        }
        queryExecutor.requireCreatePrivilege(Privilege.CREATE_EXTERNAL_TABLE, schema);
        final List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("VALUE", VariantType.VARIANT, true, null, false, false, false));
        for (final FrostlakeParser.ExternalTableColumnContext written : ctx.externalTableColumn()) {
            final TableColumn column = new TableColumn(SqlIdentifiers.canonicalText(written.columnDefName().getText()),
                types.parseDataType(written.dataTypeName(), written.typeParameters()), written.NOT() == null, null,
                false, false, false);
            if (written.expression() != null) {
                final String expression = ParseTreeText.getOriginalText(written.expression());
                column.setVirtualExpression(expression, expression);
            }
            columns.add(column);
        }
        final String key = QualifiedName.key(schema.getDatabaseName(), schema.getName(), name);
        if (exists) {
            remove(schema, name, queryExecutor);
        }
        final Table table = new Table(name, columns, false, false);
        table.setOwner(catalog.currentRoleForOwner());
        table.setComment(comment);
        table.setExternalMetadata(metadata);
        schema.addTable(table);
        queryExecutor.getStorageEngine().createTable(key, table);
        return StatusResults.of("Table " + name + " successfully created.");
    }

    /** FILE_FORMAT = ( FORMAT_NAME = '...' | TYPE = ... [ options ] ): the format's name or its type is kept. */
    private static void readFormat(final FrostlakeParser.ParenOptionListContext format,
                                   final ExternalTableMetadata metadata) {
        for (final FrostlakeParser.ParenOptionContext option : format.parenOption()) {
            final String key = ObjectProperties.key(option.optionKey());
            final FrostlakeParser.CopyOptionValueContext value = option.copyOptionValue();
            if (value == null) {
                continue;
            }
            final String text = value.STRING_LITERAL() != null
                ? SqlStringLiterals.decode(value.STRING_LITERAL().getText()) : value.getText();
            if ("TYPE".equals(key)) {
                if (!FORMAT_TYPES.contains(text.toUpperCase(Locale.ROOT))) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, "TYPE"));
                }
                metadata.setFileFormatType(text.toUpperCase(Locale.ROOT));
            } else if ("FORMAT_NAME".equals(key)) {
                metadata.setFileFormatName(text);
            }
        }
    }

    /** The named stage a location reads, its name and the location as written kept in the metadata. */
    private Stage stage(final FrostlakeParser.StageRefContext location, final ExternalTableMetadata metadata) {
        if (location.TILDE() != null || location.PERCENT() != null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): location"));
        }
        queryExecutor.requireNamedStageExact(location);
        final String[] parts = new String[location.identifier().size()];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = ParseTreeText.getIdentifier(location.identifier(i));
        }
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final Stage stage = owner.getStageExact(parts[parts.length - 1]);
        final String qualified = owner.qualifiedName(stage.getName());
        metadata.setStage(qualified);
        metadata.setLocation("@" + qualified + (location.stagePath() == null ? "/" : location.stagePath().getText()));
        return stage;
    }

    /**
     * Drops the external table of that name in a schema when there is one.
     *
     * @return whether one was dropped
     */
    static boolean drop(final Schema schema, final String name, final QueryExecutor queryExecutor) {
        if (find(schema, name) == null) {
            return false;
        }
        remove(schema, name, queryExecutor);
        return true;
    }

    private static void remove(final Schema schema, final String name, final QueryExecutor queryExecutor) {
        final String key = QualifiedName.key(schema.getDatabaseName(), schema.getName(), schema.getTable(name).getName());
        schema.dropTable(name);
        queryExecutor.getStorageEngine().dropTable(key);
        queryExecutor.getTransactionManager().discardBufferedWritesFor(key);
    }

    /**
     * The external table a name finds.
     *
     * @return the table, or null when the name finds none or finds a table that is no external table
     */
    static Table find(final Schema schema, final String name) {
        if (!schema.hasTable(name)) {
            return null;
        }
        final Table table = schema.getTable(name);
        return table.getExternalMetadata() == null ? null : table;
    }

    /** The rows of SHOW EXTERNAL TABLES for the external tables of some schemas, never refreshed. */
    static List<Row> rows(final List<Schema> schemas) {
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : schemas) {
            for (final Table table : schema.getTables()) {
                final ExternalTableMetadata metadata = table.getExternalMetadata();
                if (metadata == null) {
                    continue;
                }
                rows.add(new Row(Arrays.asList((Object) ShowResultHelpers.createdOn(table.getCreatedTime()),
                    table.getName(), schema.getDatabaseName(), schema.getName(), "false", null, table.getOwner(),
                    ShowResultHelpers.text(table.getComment()), metadata.getStage(), metadata.getLocation(),
                    ShowResultHelpers.text(metadata.getFileFormatName()),
                    ShowResultHelpers.text(metadata.getFileFormatType()), metadata.getCloud(), "", null, null,
                    metadata.getTableFormat() == null ? "UNSPECIFIED" : metadata.getTableFormat(), null,
                    ShowResultHelpers.OWNER_ROLE_TYPE)));
            }
        }
        return rows;
    }

    /** The cloud a stage's URL names by its scheme. */
    private static String cloud(final String url) {
        final String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("s3")) {
            return "AWS";
        }
        return lower.startsWith("gcs") ? "GCP" : lower.startsWith("azure") ? "AZURE" : "";
    }
}
