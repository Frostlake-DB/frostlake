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

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.SqlObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A semantic view: logical tables over existing tables, the relationships between them, and the facts, dimensions
 * and metrics defined over them, each keeping its SQL expression as written. It shares the namespace of a schema's
 * tables and views.
 */
public class SemanticView extends SqlObject {

    private final List<SemanticTable> tables;
    private final List<SemanticRelationship> relationships;
    private final List<SemanticExpression> expressions;

    /**
     * @param name          the view's canonical name
     * @param tables        the logical tables, in the definition's order
     * @param relationships the relationships, in the definition's order
     * @param expressions   the facts, dimensions and metrics, in the definition's order
     * @param comment       the comment, or null
     */
    public SemanticView(final String name, final List<SemanticTable> tables,
                        final List<SemanticRelationship> relationships, final List<SemanticExpression> expressions,
                        final String comment) {
        super(name);
        this.tables = new ArrayList<SemanticTable>(tables);
        this.relationships = new ArrayList<SemanticRelationship>(relationships);
        this.expressions = new ArrayList<SemanticExpression>(expressions);
        this.comment = comment;
    }

    public List<SemanticTable> getTables() {
        return tables;
    }

    public List<SemanticRelationship> getRelationships() {
        return relationships;
    }

    public List<SemanticExpression> getExpressions() {
        return expressions;
    }

    @Override
    public String getObjectType() {
        return "SEMANTIC_VIEW";
    }

    /**
     * The statement that creates the view again, as GET_DDL writes it: the clauses and their items in the
     * definition's order, every expression as written, and every table by its full name.
     *
     * @param spelledName the view's name as the call spells it
     * @return the statement
     */
    public String ddl(final String spelledName) {
        final StringBuilder out = new StringBuilder("create or replace semantic view ").append(spelledName);
        out.append("\n\ttables (");
        boolean first = true;
        for (final SemanticTable table : tables) {
            out.append(first ? "\n\t\t" : ",\n\t\t");
            first = false;
            if (table.isAliasWritten()) {
                out.append(SqlIdentifiers.spellCanonical(table.getAlias())).append(" as ");
            }
            out.append(SqlIdentifiers.spellCanonical(table.getDatabaseName())).append('.')
                .append(SqlIdentifiers.spellCanonical(table.getSchemaName())).append('.')
                .append(SqlIdentifiers.spellCanonical(table.getTableName()));
            if (!table.getPrimaryKey().isEmpty()) {
                out.append(" primary key (").append(names(table.getPrimaryKey())).append(')');
            }
            for (final List<String> unique : table.getUniqueKeys()) {
                out.append(" unique (").append(names(unique)).append(')');
            }
            appendSynonymsAndComment(out, table.getSynonyms(), table.getComment());
        }
        out.append("\n\t)");
        if (!relationships.isEmpty()) {
            out.append("\n\trelationships (");
            first = true;
            for (final SemanticRelationship relationship : relationships) {
                out.append(first ? "\n\t\t" : ",\n\t\t");
                first = false;
                if (relationship.isNameWritten()) {
                    out.append(SqlIdentifiers.spellCanonical(relationship.getName())).append(" as ");
                }
                out.append(SqlIdentifiers.spellCanonical(relationship.getTable())).append('(')
                    .append(names(relationship.getForeignKey())).append(") references ")
                    .append(SqlIdentifiers.spellCanonical(relationship.getRefTable())).append('(')
                    .append(names(relationship.getRefKey())).append(')');
            }
            out.append("\n\t)");
        }
        appendClause(out, "facts", SemanticExpressionKind.FACT, null);
        appendClause(out, "dimensions", SemanticExpressionKind.DIMENSION, null);
        appendClause(out, "metrics", SemanticExpressionKind.METRIC, SemanticExpressionKind.DERIVED_METRIC);
        if (comment != null) {
            out.append("\n\tcomment=").append(quoted(comment));
        }
        return out.append(';').toString();
    }

    /**
     * One clause's items: the facts and the dimensions table by table, in the order of the logical tables, the
     * metrics in the definition's order (live-verified).
     */
    private void appendClause(final StringBuilder out, final String word, final SemanticExpressionKind kind,
                              final SemanticExpressionKind alsoKind) {
        final List<SemanticExpression> items = new ArrayList<SemanticExpression>();
        if (alsoKind != null) {
            for (final SemanticExpression expression : expressions) {
                if (expression.getKind() == kind || expression.getKind() == alsoKind) {
                    items.add(expression);
                }
            }
        } else {
            for (final SemanticTable table : tables) {
                for (final SemanticExpression expression : expressions) {
                    if (expression.getKind() == kind && table.getAlias().equals(expression.getTable())) {
                        items.add(expression);
                    }
                }
            }
        }
        boolean first = true;
        for (final SemanticExpression expression : items) {
            out.append(first ? "\n\t" + word + " (\n\t\t" : ",\n\t\t");
            first = false;
            if (expression.isPrivateAccess()) {
                out.append("PRIVATE ");
            }
            if (expression.getTable() != null) {
                out.append(SqlIdentifiers.spellCanonical(expression.getTable())).append('.');
            }
            out.append(SqlIdentifiers.spellCanonical(expression.getName())).append(" as ")
                .append(expression.getExpression());
            appendSynonymsAndComment(out, expression.getSynonyms(), expression.getComment());
        }
        if (!first) {
            out.append("\n\t)");
        }
    }

    private static void appendSynonymsAndComment(final StringBuilder out, final List<String> synonyms,
                                                 final String comment) {
        if (!synonyms.isEmpty()) {
            out.append(" with synonyms=(");
            for (int i = 0; i < synonyms.size(); i++) {
                out.append(i == 0 ? "" : ",").append(quoted(synonyms.get(i)));
            }
            out.append(')');
        }
        if (comment != null) {
            out.append(" comment=").append(quoted(comment));
        }
    }

    private static String names(final List<String> names) {
        final StringBuilder out = new StringBuilder();
        for (final String name : names) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(SqlIdentifiers.spellCanonical(name));
        }
        return out.toString();
    }

    private static String quoted(final String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
