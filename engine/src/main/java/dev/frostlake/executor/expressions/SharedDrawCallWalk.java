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

/**
 * Whether an expression holds a call whose equal calls share one drawn value in a row (see {@link SharedRowDraws}),
 * outside any subquery or lambda of its own. It subclasses the printer for its traversal only; the printed string is
 * discarded.
 */
final class SharedDrawCallWalk extends AstPrinterVisitor {

    private boolean found;

    private SharedDrawCallWalk() {
    }

    /**
     * @param expression an expression
     * @return true when it holds such a call
     */
    static boolean holdsOne(final Expression expression) {
        final SharedDrawCallWalk walk = new SharedDrawCallWalk();
        expression.accept(walk);
        return walk.found;
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        found = found || SharedRowDraws.shares(expr);
        return super.visitFunctionCall(expr);
    }
}
