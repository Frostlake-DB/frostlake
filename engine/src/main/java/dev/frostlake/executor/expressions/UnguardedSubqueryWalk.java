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

package dev.frostlake.executor.expressions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects the subqueries an expression may evaluate as the statement stands, in the order they are written: a branch
 * of a CASE, an IFF, an NVL2 or a DECODE is passed by only where its condition is settled against it — by a constant,
 * or by what the table's statistics prove of every row — and a COALESCE, NVL or IFNULL argument after the first, and
 * a lambda's body, are judged as a row reaches them. Live refuses a subquery opening with the outer query's aggregate
 * in such a place as the statement compiles: {@code IFF(m.i = 5, s, NULL)} over a column holding 1 and 10 is refused,
 * {@code IFF(m.i > 50, s, NULL)} answered, and over an empty input, or under WHERE FALSE, only a constant settles a
 * condition (all live-verified).
 */
final class UnguardedSubqueryWalk extends AstPrinterVisitor {

    private final List<SubqueryExpression> subqueries = new ArrayList<>();

    /** What settles a condition from the statistics, or null where only a constant does. */
    private final ExpressionEvaluatorVisitor statistics;

    /** What settles a condition the statistics leave open, from the query around's own, or null. */
    private final ExpressionEvaluatorVisitor fallback;

    /** Whether an ANY or SOME comparison's body is passed by. */
    private final boolean scalarOnly;

    /** Whether the expression filters rows, so that an IN's body is passed by under {@link #scalarOnly}. */
    private final boolean filter;

    /**
     * @param statistics what settles a condition from the table's statistics, or null where the input is open: empty,
     *                   or filtered by a WHERE that keeps no row
     */
    UnguardedSubqueryWalk(final ExpressionEvaluatorVisitor statistics) {
        this(statistics, null);
    }

    /**
     * @param statistics what settles a condition from the table's statistics, or null where the input is open
     * @param fallback   what settles a condition the first leaves open, from the statistics of the query around, or
     *                   null
     */
    UnguardedSubqueryWalk(final ExpressionEvaluatorVisitor statistics, final ExpressionEvaluatorVisitor fallback) {
        this(statistics, fallback, false, false);
    }

    /**
     * @param statistics what settles a condition from the table's statistics, or null where the input is open
     * @param fallback   what settles a condition the first leaves open, from the statistics of the query around, or
     *                   null
     * @param scalarOnly whether the body of an ANY or SOME comparison is passed by, as no value the select plans
     * @param filter     whether the expression filters a select's rows, where the body of an IN is a membership
     *                   passed by too; a NOT IN's is collected wherever it stands
     */
    UnguardedSubqueryWalk(final ExpressionEvaluatorVisitor statistics, final ExpressionEvaluatorVisitor fallback,
                          final boolean scalarOnly, final boolean filter) {
        this.statistics = statistics;
        this.fallback = fallback;
        this.scalarOnly = scalarOnly;
        this.filter = filter;
    }

    @Override
    public String visitQuantifiedComparison(final QuantifiedComparisonExpression expr) {
        if (scalarOnly && expr.getQuantifier() != Quantifier.ALL) {
            expr.getLeft().accept(this);
            return "";
        }
        return super.visitQuantifiedComparison(expr);
    }

    @Override
    public String visitIn(final InExpression expr) {
        if (scalarOnly && filter && expr.hasSubquery() && !expr.isNot()) {
            expr.getValue().accept(this);
            return "";
        }
        return super.visitIn(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        subqueries.add(expr);
        return "";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";
    }

    /**
     * An AND one side of which is settled FALSE, or an OR one side of which is settled TRUE, is settled whatever the
     * other side holds, and neither side is evaluated: over g holding 7 and 8, {@code g.v > 100 AND g.v = (SELECT
     * MAX(o.i) FROM g g2)} is answered where {@code g.v > 100 OR …} is refused (live-verified).
     */
    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        if (expr.getOperator() == BinaryOperator.AND || expr.getOperator() == BinaryOperator.OR) {
            final Boolean decisive = expr.getOperator() == BinaryOperator.AND ? Boolean.FALSE : Boolean.TRUE;
            if (decisive.equals(settled(expr.getLeft())) || decisive.equals(settled(expr.getRight()))) {
                return "";
            }
        }
        return super.visitBinaryOperation(expr);
    }

    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        for (final WhenClause when : expr.getWhenClauses()) {
            when.getCondition().accept(this);
            final Boolean settled = settled(when.getCondition());
            if (!Boolean.FALSE.equals(settled)) {
                when.getResult().accept(this);
            }
            if (Boolean.TRUE.equals(settled)) {
                return "";
            }
        }
        if (expr.getElseExpression() != null) {
            expr.getElseExpression().accept(this);
        }
        return "";
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String name = expr.getFunctionName() == null ? "" : expr.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = expr.getArguments();
        if (expr.isStar()) {
            return super.visitFunctionCall(expr);
        }
        switch (name) {
            case "IFF":
                if (args.size() == 3) {
                    return branches(args.get(0), args.get(0), args.get(1), args.get(2));
                }
                break;
            case "NVL2":
                if (args.size() == 3) {
                    return branches(args.get(0), new IsNullExpression(args.get(0), true), args.get(1), args.get(2));
                }
                break;
            case "DECODE":
                if (args.size() >= 3) {
                    return decode(args);
                }
                break;
            case "COALESCE":
            case "NVL":
            case "IFNULL":
                if (!args.isEmpty()) {
                    args.get(0).accept(this);
                    return "";
                }
                break;
            default:
                break;
        }
        return super.visitFunctionCall(expr);
    }

    /** A two-way branch: the tested value always, then the branches its condition leaves open. */
    private String branches(final Expression tested, final Expression condition, final Expression whenTrue,
                            final Expression otherwise) {
        tested.accept(this);
        final Boolean settled = settled(condition);
        if (!Boolean.FALSE.equals(settled)) {
            whenTrue.accept(this);
        }
        if (!Boolean.TRUE.equals(settled)) {
            otherwise.accept(this);
        }
        return "";
    }

    /** DECODE(x, s1, r1, …, default): x and each search value in turn, and the results the matches leave open. */
    private String decode(final List<Expression> args) {
        final Expression tested = args.get(0);
        tested.accept(this);
        int i = 1;
        for (; i + 1 < args.size(); i += 2) {
            args.get(i).accept(this);
            final Boolean settled = settled(new BinaryOperationExpression(tested, BinaryOperator.EQUAL, args.get(i)));
            if (!Boolean.FALSE.equals(settled)) {
                args.get(i + 1).accept(this);
            }
            if (Boolean.TRUE.equals(settled)) {
                return "";
            }
        }
        if (i < args.size()) {
            args.get(i).accept(this);
        }
        return "";
    }

    /** Whether a condition is settled TRUE or FALSE for every row, or null where it is left open. */
    private Boolean settled(final Expression condition) {
        if (condition instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) condition).getValue();
            return value instanceof Boolean ? (Boolean) value : null;
        }
        if (statistics == null) {
            return null;
        }
        Boolean settled;
        try {
            settled = statistics.settledCondition(condition);
        } catch (final RuntimeException unsettled) {
            settled = null;
        }
        if (settled == null && fallback != null) {
            try {
                settled = fallback.settledCondition(condition);
            } catch (final RuntimeException unsettled) {
                settled = null;
            }
        }
        return settled;
    }

    /** The subqueries found, in the order written. */
    List<SubqueryExpression> subqueries() {
        return subqueries;
    }
}
