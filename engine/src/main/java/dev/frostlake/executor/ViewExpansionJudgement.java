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
package dev.frostlake.executor;

import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A statement that reads no row of a view - its WHERE or HAVING holds a constant FALSE or NULL, or it is limited to no row - is
 * still refused for a subquery of the view's body the account cannot evaluate, where it reads the subquery's
 * column: the account judges the view as it expands it, and neither that WHERE nor that LIMIT spares it
 * (live-verified). {@code SELECT * FROM v WHERE FALSE} and {@code SELECT y FROM v LIMIT 0} are refused as
 * {@code SELECT * FROM v} is, while {@code SELECT id FROM v WHERE FALSE} answers no row.
 *
 * <p>The statement is judged as the same statement without that WHERE or that LIMIT is: what it would read is read,
 * and a refusal naming a view is the statement's.
 */
public final class ViewExpansionJudgement {
    private static final ThreadLocal<Boolean> JUDGING = new ThreadLocal<Boolean>();

    private ViewExpansionJudgement() {
    }

    /**
     * Refuses such a statement.
     *
     * @param executor  the executor
     * @param statement the statement about to run
     */
    public static void require(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext statement) {
        if (Boolean.TRUE.equals(JUDGING.get()) || RelationBody.isActive() || statement.selectOperand().size() != 1
                || statement.selectOperand(0).selectClause() == null || statement.start == null
                || statement.stop == null) {
            return;
        }
        final FrostlakeParser.WhereClauseContext where =
            ParseTreeText.getWhereClause(statement.selectOperand(0).selectClause());
        final boolean pruned = where != null && foldsFalse(executor, where.booleanExpr());
        final FrostlakeParser.HavingClauseContext having = statement.selectOperand(0).selectClause().havingClause();
        final boolean ungrouped = having != null && foldsFalse(executor, having.booleanExpr());
        final FrostlakeParser.LimitClauseContext limit = statement.limitClause();
        final boolean noRow = limit != null && readsNoRow(limit);
        if (!pruned && !noRow && !ungrouped || !ExplainPlan.readsViewHoldingSubquery(executor.getCatalog(), statement, 0)) {
            return;
        }
        final String text = ParseTreeText.getOriginalText(statement);
        final int origin = statement.start.getStartIndex();
        if (text.length() != statement.stop.getStopIndex() - origin + 1) {
            return;
        }
        final char[] judged = text.toCharArray();
        if (pruned) {
            blank(judged, where, origin);
        }
        if (noRow) {
            blank(judged, limit, origin);
        }
        if (ungrouped) {
            blank(judged, having, origin);
        }
        JUDGING.set(Boolean.TRUE);
        try {
            executor.readForJudgement(new String(judged));
        } catch (final UnsupportedSubqueryException refused) {
            if (refused.namesView()) {
                throw refused;
            }
        } catch (final RuntimeException unrelated) {
            // The statement itself raises whatever else is wrong with it, in its own words.
            return;
        } finally {
            JUDGING.remove();
        }
    }

    /** Whether a condition holds a constant conjunct that folds to FALSE or NULL. */
    private static boolean foldsFalse(final QueryExecutor executor, final FrostlakeParser.BooleanExprContext condition) {
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        DerivedRelationJudgement.collectConjuncts(condition, conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final String text = ParseTreeText.getOriginalText(conjunct);
            try {
                if (!ConstantHaving.isConstant(ExpressionEvaluator.parse(text), executor.getFunctionRegistry())) {
                    continue;
                }
                final Object value = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
                    executor.getCatalog(), executor).evaluate(text, null);
                if (value == null || Boolean.FALSE.equals(value)) {
                    return true;
                }
            } catch (final RuntimeException notConstant) {
                continue;
            }
        }
        return false;
    }

    /** Whether a LIMIT's count is the literal 0. */
    private static boolean readsNoRow(final FrostlakeParser.LimitClauseContext limit) {
        final TerminalNode count = limit.INTEGER_LITERAL().isEmpty() ? null : limit.INTEGER_LITERAL(0);
        return count != null && limit.getChild(1) == count && "0".equals(count.getText());
    }

    /** Writes blanks over a clause's characters, keeping every other character's place. */
    private static void blank(final char[] text, final ParserRuleContext clause, final int origin) {
        for (int i = clause.start.getStartIndex() - origin; i <= clause.stop.getStopIndex() - origin; i++) {
            if (i >= 0 && i < text.length && text[i] != '\n') {
                text[i] = ' ';
            }
        }
    }
}
