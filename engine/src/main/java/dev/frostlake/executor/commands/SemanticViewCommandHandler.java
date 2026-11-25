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
import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SemanticExpression;
import dev.frostlake.metastore.model.SemanticExpressionKind;
import dev.frostlake.metastore.model.SemanticRelationship;
import dev.frostlake.metastore.model.SemanticTable;
import dev.frostlake.metastore.model.SemanticView;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Semantic views: CREATE, DESCRIBE, the SHOW rows, a comment, a rename and DROP (live-verified).
 *
 * <p>A definition is judged as the account judges it: the logical tables must exist and their keys name their
 * columns, a relationship's tables are logical tables and it references a key, a fact or a dimension is named on a
 * logical table and aggregates nothing, a metric on a logical table aggregates, a metric named alone is a derived
 * metric, and no two expressions share a name. The type of every expression is the type the engine answers for it
 * over its table, a metric over the table's rows, a derived metric over the metrics it names. A fault in an
 * expression is reported at its place in the statement.
 */
public final class SemanticViewCommandHandler {

    private static final String[] CLAUSES = {"FACTS", "DIMENSIONS", "METRICS"};

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public SemanticViewCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    /**
     * CREATE [OR REPLACE] SEMANTIC VIEW [IF NOT EXISTS].
     *
     * @param ctx         the statement
     * @param ifNotExists whether IF NOT EXISTS is written
     * @param ddl         the DDL handler, for the privilege check and the comment's text
     * @return null
     */
    public Object create(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists,
                         final DDLCommandHandler ddl) {
        final String[] parts = queryExecutor.createdNameParts(ctx);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        ddl.checkCreatePrivilege(Privilege.CREATE_SEMANTIC_VIEW, schema);
        // Another kind holding the name is refused before OR REPLACE or IF NOT EXISTS is weighed.
        schema.rejectNameHeldByOtherKind(name, RelationKind.SEMANTIC_VIEW, false);
        final boolean exists = schema.getSemanticViewExact(name) != null;
        if (exists && ctx.or_replace() == null) {
            if (ifNotExists) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        final FrostlakeParser.SemanticViewBodyContext body = ctx.semanticViewBody();
        final Map<String, SemanticTable> tables = new LinkedHashMap<String, SemanticTable>();
        final Map<String, List<String>> columns = new LinkedHashMap<String, List<String>>();
        for (final FrostlakeParser.SemanticTableContext written : body.semanticTable()) {
            final SemanticTable table = table(written, ddl, columns);
            if (tables.containsKey(table.getAlias())) {
                throw new RuntimeException(SqlCompilationError.of("duplicate alias '"
                    + SqlIdentifiers.spellCanonical(table.getAlias()) + "'"));
            }
            tables.put(table.getAlias(), table);
        }
        final List<SemanticRelationship> relationships = new ArrayList<SemanticRelationship>();
        if (body.semanticRelationships() != null) {
            for (final FrostlakeParser.SemanticRelationshipContext written
                    : body.semanticRelationships().semanticRelationship()) {
                relationships.add(relationship(written, tables, columns));
            }
        }
        final List<SemanticExpression> expressions = new ArrayList<SemanticExpression>();
        final List<FrostlakeParser.SemanticExpressionContext> writtenExpressions =
            new ArrayList<FrostlakeParser.SemanticExpressionContext>();
        final Set<String> names = new LinkedHashSet<String>();
        int last = -1;
        for (final FrostlakeParser.SemanticExpressionsContext clause : body.semanticExpressions()) {
            final Token word = clause.identifier().getStart();
            final int index = Arrays.asList(CLAUSES).indexOf(word.getText().toUpperCase(Locale.ROOT));
            if (index <= last) {
                // A clause stands once, and after the clauses before it.
                throw new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(word,
                    word.getText())));
            }
            last = index;
            for (final FrostlakeParser.SemanticExpressionContext item : clause.semanticExpression()) {
                expressions.add(expression(item, index, tables, names, ddl));
                writtenExpressions.add(item);
            }
        }
        if (expressions.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(
                "No queryable expression is defined in the semantic view."));
        }
        new SemanticViewTypes(queryExecutor).type(tables, expressions, writtenExpressions);
        if (exists) {
            schema.dropSemanticViewExact(name);
        }
        final SemanticView view = new SemanticView(name, new ArrayList<SemanticTable>(tables.values()),
            relationships, expressions, body.commentClause() != null ? ddl.extractComment(body.commentClause())
                : null);
        view.setOwner(catalog.currentRoleForOwner());
        schema.addSemanticView(view);
        return null;
    }

    private SemanticTable table(final FrostlakeParser.SemanticTableContext written, final DDLCommandHandler ddl,
                                final Map<String, List<String>> columns) {
        final String[] parts = queryExecutor.resolveObjectNameParts(written.objectName());
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String tableName = parts[parts.length - 1];
        final RelationKind kind = owner.relationKindOf(tableName);
        if (kind == null || kind == RelationKind.STREAM || kind == RelationKind.SEMANTIC_VIEW) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", QualifiedName.join(parts)));
        }
        final String alias = written.identifier() != null ? SqlIdentifiers.canonical(written.identifier())
            : tableName;
        final String path = SqlIdentifiers.spellCanonical(owner.getDatabaseName()) + "."
            + SqlIdentifiers.spellCanonical(owner.getName()) + "." + SqlIdentifiers.spellCanonical(tableName);
        final List<String> tableColumns = new ArrayList<String>();
        for (final ResultSetColumn column
                : queryExecutor.executeUnrecorded("SELECT * FROM " + path + " LIMIT 0").get(0).getColumns()) {
            tableColumns.add(column.getName());
        }
        columns.put(alias, tableColumns);
        final List<String> primaryKey = new ArrayList<String>();
        final List<List<String>> uniqueKeys = new ArrayList<List<String>>();
        for (final FrostlakeParser.IdentifierListContext list : written.identifierList()) {
            final List<String> key = keyColumns(list, tableColumns);
            if (written.PRIMARY() != null && list == written.identifierList(0)) {
                primaryKey.addAll(key);
            } else {
                uniqueKeys.add(key);
            }
        }
        return new SemanticTable(alias, written.identifier() != null, owner.getDatabaseName(), owner.getName(),
            tableName, primaryKey, uniqueKeys, synonyms(written.semanticSynonyms()),
            written.commentClause() != null ? ddl.extractComment(written.commentClause()) : null);
    }

    /** A key's columns, each one the table has, or an invalid identifier at its place. */
    private static List<String> keyColumns(final FrostlakeParser.IdentifierListContext list,
                                           final List<String> tableColumns) {
        final List<String> key = new ArrayList<String>();
        for (final FrostlakeParser.IdentifierContext column : list.identifier()) {
            final String name = SqlIdentifiers.canonical(column);
            if (!tableColumns.contains(name)) {
                throw invalidIdentifierAt(column.getStart(), SqlIdentifiers.spellCanonical(name));
            }
            key.add(name);
        }
        return key;
    }

    private static RuntimeException invalidIdentifierAt(final Token token, final String spelled) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return new RuntimeException(SqlCompilationError.invalidIdentifier(shown[0], shown[1], spelled));
    }

    private static List<String> synonyms(final FrostlakeParser.SemanticSynonymsContext written) {
        final List<String> synonyms = new ArrayList<String>();
        if (written != null) {
            for (final FrostlakeParser.StringTextContext synonym : written.stringText()) {
                synonyms.add(ParseTreeText.stringValue(synonym));
            }
        }
        return synonyms;
    }

    private static SemanticRelationship relationship(final FrostlakeParser.SemanticRelationshipContext written,
                                                     final Map<String, SemanticTable> tables,
                                                     final Map<String, List<String>> columns) {
        final boolean named = written.AS() != null;
        final List<FrostlakeParser.IdentifierContext> words = written.identifier();
        final String table = logicalTable(words.get(named ? 1 : 0), tables, "RELATIONSHIPS");
        final String refTable = logicalTable(words.get(named ? 2 : 1), tables, "RELATIONSHIPS");
        final List<String> foreignKey = keyColumns(written.identifierList(0), columns.get(table));
        final List<String> refKey = written.identifierList().size() > 1
            ? keyColumns(written.identifierList(1), columns.get(refTable)) : tables.get(refTable).getPrimaryKey();
        if (refKey.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("The relationship '"
                + SqlIdentifiers.spellCanonical(table) + " REFERENCES " + SqlIdentifiers.spellCanonical(refTable)
                + "' does not specify foreign keys or referenced keys."));
        }
        final String name = named ? SqlIdentifiers.canonical(words.get(0))
            : "SYS_RELATIONSHIP_" + UUID.randomUUID();
        return new SemanticRelationship(name, named, table, foreignKey, refTable, refKey);
    }

    private static String logicalTable(final FrostlakeParser.IdentifierContext written,
                                       final Map<String, SemanticTable> tables, final String clause) {
        final String alias = SqlIdentifiers.canonical(written);
        return requireLogicalTable(alias, tables, clause);
    }

    private static String requireLogicalTable(final String alias, final Map<String, SemanticTable> tables,
                                              final String clause) {
        if (!tables.containsKey(alias)) {
            throw new RuntimeException(SqlCompilationError.of("Invalid table name '"
                + SqlIdentifiers.spellCanonical(alias) + "' in the " + clause + " definition."));
        }
        return alias;
    }

    private SemanticExpression expression(final FrostlakeParser.SemanticExpressionContext written, final int clause,
                                          final Map<String, SemanticTable> tables, final Set<String> names,
                                          final DDLCommandHandler ddl) {
        boolean privateAccess = false;
        if (written.accessModifier != null) {
            final String modifier = written.accessModifier.getText().toUpperCase(Locale.ROOT);
            if (!"PRIVATE".equals(modifier) && !"PUBLIC".equals(modifier)) {
                // Any other word there leaves the name it stands before unexpected (live-verified).
                final Token next = written.qualifiedName().getStart();
                throw new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(next,
                    next.getText())));
            }
            privateAccess = "PRIVATE".equals(modifier);
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(written.qualifiedName());
        final String name = parts[parts.length - 1];
        final String table = parts.length > 1 ? parts[parts.length - 2] : null;
        final SemanticExpressionKind kind;
        if (table == null) {
            if (clause != 2) {
                final Token start = written.qualifiedName().getStart();
                final int[] shown = LeadingCommentOffset.rebase(start.getLine(), start.getCharPositionInLine());
                throw new RuntimeException(SqlCompilationError.at(shown[0], shown[1], "Invalid definition for '"
                    + SqlIdentifiers.spellCanonical(name) + "': Row-level expressions must be qualified unless they"
                    + " are LOD. An LOD expression must specify FIXED, INCLUDE, or EXCLUDE DIMENSIONS."));
            }
            kind = SemanticExpressionKind.DERIVED_METRIC;
        } else {
            requireLogicalTable(table, tables, CLAUSES[clause].toLowerCase(Locale.ROOT));
            kind = clause == 0 ? SemanticExpressionKind.FACT
                : clause == 1 ? SemanticExpressionKind.DIMENSION : SemanticExpressionKind.METRIC;
        }
        final String spelled = (table != null ? SqlIdentifiers.spellCanonical(table) + "." : "")
            + SqlIdentifiers.spellCanonical(name);
        if (!names.add((table != null ? table : "") + "\u0000" + name)) {
            throw new RuntimeException(SqlCompilationError.of("Duplicate expression name '" + spelled + "'."));
        }
        final boolean aggregates = queryExecutor.hasAggregateFunctionInExpression(written.expression());
        if (kind == SemanticExpressionKind.DIMENSION && aggregates) {
            throw new RuntimeException(SqlCompilationError.of("Invalid dimension definition for '" + spelled
                + "': A dimension must directly refer to a column from the same table without an aggregate."));
        }
        if (kind == SemanticExpressionKind.FACT && aggregates) {
            throw new RuntimeException(SqlCompilationError.of("Invalid fact definition for '" + spelled
                + "': A fact must directly refer to a column from the same table without an aggregate."));
        }
        if (kind == SemanticExpressionKind.METRIC && !aggregates
                && SemanticViewTypes.references(written.expression()).isEmpty()) {
            // A metric over other metrics aggregates through them (live-verified).
            throw new RuntimeException(SqlCompilationError.of("Invalid metric definition for '" + spelled
                + "': A metric must have a single aggregate over a column from the same table."));
        }
        return new SemanticExpression(kind, table, name, ParseTreeText.getOriginalText(written.expression()),
            privateAccess, synonyms(written.semanticSynonyms()),
            written.commentClause() != null ? ddl.extractComment(written.commentClause()) : null);
    }

    /**
     * The view a statement names, or the refusal of a missing one.
     *
     * @param parts the name's parts, as written
     * @return the view
     */
    public SemanticView require(final String[] parts) {
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final SemanticView view = owner.getSemanticViewExact(parts[parts.length - 1]);
        if (view == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Semantic View",
                owner.qualifiedName(parts[parts.length - 1])));
        }
        return view;
    }

    /**
     * The view a statement names, or null when there is none.
     *
     * @param parts the name's parts, as written
     * @return the view, or null
     */
    public SemanticView find(final String[] parts) {
        return catalog.requireOwningSchema(QualifiedName.of(parts)).getSemanticViewExact(parts[parts.length - 1]);
    }

    /**
     * DESCRIBE SEMANTIC VIEW: the view's comment, then its derived metrics, then each logical table in the
     * definition's order with its relationships, dimensions, facts and metrics, each kind by name.
     *
     * @param parts the name's parts, as written
     * @return one row per property
     */
    public ResultSet describe(final String[] parts) {
        final RelationKind holder = catalog.requireOwningSchema(QualifiedName.of(parts))
            .relationKindOf(parts[parts.length - 1]);
        if (holder != null && holder != RelationKind.SEMANTIC_VIEW) {
            // The account answers a relation of another kind with its invalid-identifier template, its placeholders
            // unfilled (live-verified).
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " error line {0} at position {1}\ninvalid identifier '{2}'");
        }
        final SemanticView view = require(parts);
        final List<Row> rows = new ArrayList<Row>();
        if (view.getComment() != null) {
            rows.add(row(null, null, null, "COMMENT", view.getComment()));
        }
        for (final SemanticExpression derived : sorted(view, null, SemanticExpressionKind.DERIVED_METRIC)) {
            expressionRows(derived, rows);
        }
        for (final SemanticTable table : view.getTables()) {
            final String alias = table.getAlias();
            rows.add(row("TABLE", alias, null, "BASE_TABLE_DATABASE_NAME", table.getDatabaseName()));
            rows.add(row("TABLE", alias, null, "BASE_TABLE_SCHEMA_NAME", table.getSchemaName()));
            rows.add(row("TABLE", alias, null, "BASE_TABLE_NAME", table.getTableName()));
            if (!table.getSynonyms().isEmpty()) {
                rows.add(row("TABLE", alias, null, "SYNONYMS", textArray(table.getSynonyms())));
            }
            if (!table.getPrimaryKey().isEmpty()) {
                rows.add(row("TABLE", alias, null, "PRIMARY_KEY", textArray(table.getPrimaryKey())));
            }
            if (!table.getUniqueKeys().isEmpty()) {
                final StringBuilder keys = new StringBuilder("[");
                for (final List<String> key : table.getUniqueKeys()) {
                    keys.append(keys.length() > 1 ? "," : "").append(textArray(key));
                }
                rows.add(row("TABLE", alias, null, "UNIQUE_KEY", keys.append(']').toString()));
            }
            if (table.getComment() != null) {
                rows.add(row("TABLE", alias, null, "COMMENT", table.getComment()));
            }
            final List<SemanticRelationship> relationships = new ArrayList<SemanticRelationship>();
            for (final SemanticRelationship relationship : view.getRelationships()) {
                if (relationship.getTable().equals(alias)) {
                    relationships.add(relationship);
                }
            }
            Collections.sort(relationships, new Comparator<SemanticRelationship>() {
                @Override
                public int compare(final SemanticRelationship a, final SemanticRelationship b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            for (final SemanticRelationship relationship : relationships) {
                final String name = relationship.getName();
                rows.add(row("RELATIONSHIP", name, alias, "TABLE", alias));
                rows.add(row("RELATIONSHIP", name, alias, "REF_TABLE", relationship.getRefTable()));
                rows.add(row("RELATIONSHIP", name, alias, "FOREIGN_KEY", textArray(relationship.getForeignKey())));
                rows.add(row("RELATIONSHIP", name, alias, "REF_KEY", textArray(relationship.getRefKey())));
            }
            for (final SemanticExpressionKind kind : new SemanticExpressionKind[] {SemanticExpressionKind.DIMENSION,
                    SemanticExpressionKind.FACT, SemanticExpressionKind.METRIC}) {
                for (final SemanticExpression expression : sorted(view, alias, kind)) {
                    expressionRows(expression, rows);
                }
            }
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String column : new String[] {"object_kind", "object_name", "parent_entity", "property",
                "property_value"}) {
            columns.add(new ResultSetColumn(column, StringType.VARCHAR));
        }
        return new ResultSet(columns, rows);
    }

    private static List<SemanticExpression> sorted(final SemanticView view, final String table,
                                                   final SemanticExpressionKind kind) {
        final List<SemanticExpression> found = new ArrayList<SemanticExpression>();
        for (final SemanticExpression expression : view.getExpressions()) {
            if (expression.getKind() == kind && (table == null || table.equals(expression.getTable()))) {
                found.add(expression);
            }
        }
        Collections.sort(found, new Comparator<SemanticExpression>() {
            @Override
            public int compare(final SemanticExpression a, final SemanticExpression b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return found;
    }

    private static void expressionRows(final SemanticExpression expression, final List<Row> rows) {
        final String kind = expression.getKind().name();
        final String name = expression.getName();
        final String table = expression.getTable();
        if (table != null) {
            rows.add(row(kind, name, table, "TABLE", table));
        }
        rows.add(row(kind, name, table, "EXPRESSION", expression.getExpression()));
        rows.add(row(kind, name, table, "DATA_TYPE", expression.getDataType()));
        if (!expression.getSynonyms().isEmpty()) {
            rows.add(row(kind, name, table, "SYNONYMS", textArray(expression.getSynonyms())));
        }
        if (expression.getComment() != null) {
            rows.add(row(kind, name, table, "COMMENT", expression.getComment()));
        }
        rows.add(row(kind, name, table, "ACCESS_MODIFIER", expression.isPrivateAccess() ? "PRIVATE" : "PUBLIC"));
    }

    private static Row row(final String kind, final String name, final String parent, final String property,
                           final String value) {
        return new Row(Arrays.<Object>asList(kind, name, parent, property, value));
    }

    /** Texts as the JSON array DESCRIBE shows: {@code ["A","B"]}. */
    private static String textArray(final List<String> texts) {
        final StringBuilder out = new StringBuilder("[");
        for (final String text : texts) {
            out.append(out.length() > 1 ? "," : "").append('"')
                .append(text.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return out.append(']').toString();
    }

    /**
     * The SHOW SEMANTIC VIEWS rows of the given schemas, each schema's by name.
     *
     * @param schemas the schemas listed
     * @param terse   whether the statement was written SHOW TERSE
     * @return the rows
     */
    public static List<Row> showRows(final List<Schema> schemas, final boolean terse) {
        final List<Row> rows = new ArrayList<Row>();
        for (final Schema schema : schemas) {
            final List<SemanticView> views = new ArrayList<SemanticView>(schema.getSemanticViews());
            Collections.sort(views, new Comparator<SemanticView>() {
                @Override
                public int compare(final SemanticView a, final SemanticView b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            for (final SemanticView view : views) {
                final Object created = ShowResultHelpers.createdOn(view.getCreatedTime());
                if (terse) {
                    rows.add(new Row(Arrays.<Object>asList(created, view.getName(), "SEMANTIC_VIEW",
                        schema.getDatabaseName(), schema.getName())));
                } else {
                    rows.add(new Row(Arrays.<Object>asList(created, view.getName(), schema.getDatabaseName(),
                        schema.getName(), view.getComment(), view.getOwner(),
                        ShowResultHelpers.ownerRoleType(view.getOwner()), null, null,
                        ShowResultHelpers.createdOn(view.getLastDdlTime() != null ? view.getLastDdlTime()
                            : view.getCreatedTime()))));
                }
            }
        }
        return rows;
    }

    /**
     * ALTER SEMANTIC VIEW ... SET COMMENT or UNSET COMMENT, and COMMENT ON SEMANTIC VIEW.
     *
     * @param view    the view
     * @param comment the comment, or null to unset it
     */
    public void comment(final SemanticView view, final String comment) {
        view.setComment(comment);
        view.markDdl(null);
    }

    /**
     * ALTER SEMANTIC VIEW ... RENAME TO: the new name may move the view to another schema, and is refused when any
     * relation there holds it.
     *
     * @param ctx the statement
     * @return null
     */
    public Object rename(final FrostlakeParser.AlterStatementContext ctx) {
        final String[] parts = ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
        final SemanticView view = ctx.if_exists() != null ? find(parts) : require(parts);
        if (view == null) {
            return null;
        }
        final Schema from = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String[] target = queryExecutor.resolveObjectNameParts(ctx.objectName());
        final Schema to = target.length > 1 ? catalog.requireOwningSchema(QualifiedName.of(target)) : from;
        final String newName = target[target.length - 1];
        final RelationKind holder = to.relationKindOf(newName);
        if (holder != null && holder != RelationKind.SEMANTIC_VIEW) {
            // A rename names no kind (live-verified).
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(newName)
                + "' already exists."));
        }
        if (to == from) {
            from.renameSemanticView(view.getName(), newName);
        } else {
            if (to.getSemanticViewExact(newName) != null) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(newName)
                    + "' already exists."));
            }
            from.dropSemanticViewExact(view.getName());
            view.rename(newName);
            to.addSemanticView(view);
        }
        view.markDdl(null);
        return null;
    }
}
