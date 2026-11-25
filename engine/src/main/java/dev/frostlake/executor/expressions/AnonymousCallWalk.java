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
 * Whether an expression calls, anywhere in it, a procedure a CALL defines for itself (see
 * {@link AnonymousCallExpression}). It subclasses the printer for its traversal only; the printed string is discarded.
 */
public final class AnonymousCallWalk extends AstPrinterVisitor {

    private boolean found;

    /**
     * Whether the expression holds such a call.
     *
     * @param expression the expression
     * @return true when it does
     */
    public static boolean holdsOne(final Expression expression) {
        final AnonymousCallWalk walk = new AnonymousCallWalk();
        expression.accept(walk);
        return walk.found;
    }

    @Override
    public String visitExecuteImmediate(final ExecuteImmediateExpression expr) {
        if (expr instanceof AnonymousCallExpression) {
            found = true;
        }
        return super.visitExecuteImmediate(expr);
    }
}
