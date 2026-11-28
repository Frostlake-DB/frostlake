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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;

/**
 * Walks an expression a plan writes for the calls the account never computes while the statement compiles (see
 * {@link PlanConstantFold#answersAnew(QueryExecutor, FunctionCallExpression)}), and notes whether it reads a
 * subquery or a column. Subqueries are not entered, save a FROM-less one the plan writes as its item; nor is a test whether a
 * value that is never null is null, which the plan folds.
 */
final class PlanFreshCallWalk extends AstPrinterVisitor {

    private final QueryExecutor executor;
    private boolean fresh;
    private boolean readsQuery;
    private boolean readsColumn;

    PlanFreshCallWalk(final QueryExecutor executor) {
        this.executor = executor;
    }

    /** @return whether the expression calls a function the account never folds */
    boolean fresh() {
        return fresh;
    }

    /** @return whether the expression reads a subquery */
    boolean readsQuery() {
        return readsQuery;
    }

    /** @return whether the expression reads a column, or a sequence's next value */
    boolean readsColumn() {
        return readsColumn;
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        readsColumn = true;
        return super.visitColumnReference(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        // A FROM-less subquery the plan writes as its item is read as that item.
        final Expression item = PlanConstantFold.inlinedItem(executor, expr);
        if (item != null) {
            return item.accept(this);
        }
        readsQuery = true;
        return "";
    }

    @Override
    public String visitIsNull(final IsNullExpression expr) {
        // A test of a value that is never null is folded, whatever computes the value.
        return PlanConstantFold.neverNull(expr.getOperand()) ? "" : super.visitIsNull(expr);
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        if (PlanConstantFold.answersAnew(executor, expr)) {
            fresh = true;
        }
        return super.visitFunctionCall(expr);
    }
}
