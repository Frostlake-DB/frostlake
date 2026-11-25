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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A fact, a dimension or a metric of a semantic view: a name on a logical table (none for a derived metric), the SQL
 * expression as written, and the type that expression answers.
 */
public final class SemanticExpression implements Serializable {

    private static final long serialVersionUID = 1L;

    private final SemanticExpressionKind kind;
    private final String table;
    private final String name;
    private final String expression;
    private final boolean privateAccess;
    private final List<String> synonyms;
    private final String comment;
    private String dataType;

    /**
     * @param kind          what the expression is
     * @param table         the logical table, or null for a derived metric
     * @param name          the expression's name
     * @param expression    the SQL expression, as written
     * @param privateAccess whether it was declared PRIVATE
     * @param synonyms      the synonyms, as written
     * @param comment       the comment, or null
     */
    public SemanticExpression(final SemanticExpressionKind kind, final String table, final String name,
                              final String expression, final boolean privateAccess, final List<String> synonyms,
                              final String comment) {
        this.kind = kind;
        this.table = table;
        this.name = name;
        this.expression = expression;
        this.privateAccess = privateAccess;
        this.synonyms = new ArrayList<String>(synonyms);
        this.comment = comment;
    }

    public SemanticExpressionKind getKind() {
        return kind;
    }

    public String getTable() {
        return table;
    }

    public String getName() {
        return name;
    }

    public String getExpression() {
        return expression;
    }

    public boolean isPrivateAccess() {
        return privateAccess;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    public String getComment() {
        return comment;
    }

    /** The type the expression answers, as DESCRIBE spells it. */
    public String getDataType() {
        return dataType;
    }

    public void setDataType(final String dataType) {
        this.dataType = dataType;
    }
}
