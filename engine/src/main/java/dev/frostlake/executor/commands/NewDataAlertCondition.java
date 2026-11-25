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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

/**
 * The condition of an alert on new data, one written without a schedule: the alert runs when the one table or view
 * its condition reads takes new rows, so the condition is compiled as the alert is written - a name it cannot
 * resolve, a type it cannot convert, a select list that projects nothing, a procedure a CALL names that is not there
 * are refused then, at their place in the condition's own text - and its shape is judged after it, in this order
 * (live-verified):
 * <pre>
 *   SHOW …                                        Streaming alert condition must not contain a show statement
 *   WITH … SELECT …                               Streaming alert condition must not contain a common table expression
 *   CALL p(), SELECT 1, SELECT * FROM VALUES (1)  Streaming alert condition must not call a stored procedure or query
 *                                                 from a VALUES clause
 *   a join, a list, a set operation, a table      Streaming alert condition must query a single existing table or view
 *   function, a stream, a dynamic table
 * </pre>
 * Only the query's own source is judged, through the brackets and the derived tables around it: a subquery of its
 * WHERE clause may read any relation, a VALUES clause or a common table expression of its own. An alert on a schedule
 * takes any condition, and resolves its names only when it runs.
 */
final class NewDataAlertCondition {

    static final String SHOW = "Streaming alert condition must not contain a show statement";
    static final String CTE = "Streaming alert condition must not contain a common table expression";
    static final String VALUES = "Streaming alert condition must not call a stored procedure or query from a VALUES "
        + "clause";
    static final String SINGLE = "Streaming alert condition must query a single existing table or view";

    private NewDataAlertCondition() {
    }

    /**
     * Refuses a condition an alert on new data does not take.
     *
     * @param queryExecutor the executor that compiles the condition
     * @param condition     the condition written
     */
    static void require(final QueryExecutor queryExecutor, final FrostlakeParser.AlertConditionContext condition) {
        if (condition.showStatement() != null) {
            final Token start = condition.showStatement().getStart();
            queryExecutor.executeFromUnrecorded(ParseTreeText.getOriginalText(condition.showStatement()),
                new SourcePosition(start.getLine(), start.getCharPositionInLine()));
            throw new RuntimeException(SHOW);
        }
        if (condition.callStatement() != null) {
            queryExecutor.requireCalledRoutine(condition.callStatement());
            throw new RuntimeException(VALUES);
        }
        final FrostlakeParser.SelectStatementContext select = condition.selectStatement();
        queryExecutor.compileQueryOnly(ParseTreeText.getOriginalText(select));
        if (select.withClause() != null) {
            throw new RuntimeException(CTE);
        }
        final String refusal = statementRefusal(queryExecutor, select);
        if (refusal != null) {
            throw new RuntimeException(refusal);
        }
    }

    private static String statementRefusal(final QueryExecutor queryExecutor,
                                           final FrostlakeParser.SelectStatementContext select) {
        if (select.selectOperand().size() > 1) {
            return SINGLE;
        }
        final FrostlakeParser.SelectOperandContext operand = select.selectOperand(0);
        if (operand.selectStatement() != null) {
            return statementRefusal(queryExecutor, operand.selectStatement());
        }
        if (operand.selectClause() == null) {
            // TABLE <name>: the query of one relation.
            return null;
        }
        final FrostlakeParser.TableExpressionContext from = operand.selectClause().tableExpression();
        if (from == null) {
            return VALUES;
        }
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return SINGLE;
        }
        return referenceRefusal(queryExecutor, from.tableReference(0));
    }

    private static String referenceRefusal(final QueryExecutor queryExecutor,
                                           final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source.VALUES() != null) {
            return VALUES;
        }
        if (source.selectStatement() != null) {
            return statementRefusal(queryExecutor, source.selectStatement());
        }
        if (source.tableReference() != null) {
            return source.joinClause().isEmpty() ? referenceRefusal(queryExecutor, source.tableReference()) : SINGLE;
        }
        if (source.tableQualifiedName() != null) {
            return namesNoTable(queryExecutor, ParseTreeText.qualifiedNameParts(source.tableQualifiedName()))
                ? SINGLE : null;
        }
        if (source.KW_IDENTIFIER_REF() != null || source.openedIdentifierReference() != null) {
            return null;
        }
        return SINGLE;
    }

    /** Whether a name the compiled condition resolved names a stream or a dynamic table, not a table or a view. */
    private static boolean namesNoTable(final QueryExecutor queryExecutor, final String[] parts) {
        final Schema schema;
        try {
            schema = queryExecutor.getCatalog().requireOwningSchema(QualifiedName.of(parts));
        } catch (final RuntimeException unresolved) {
            return false;
        }
        final String name = parts[parts.length - 1];
        return schema.hasStreamExact(name) || schema.hasDynamicTable(name);
    }
}
