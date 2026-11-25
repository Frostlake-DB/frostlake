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

package dev.frostlake.functions.scalar.math;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * DIV(a, b) — a name the account knows and lists, and refuses to run: a call of two or more arguments is
 * {@code Unsupported feature 'DIV'.} (SQLSTATE 0A000), whatever the arguments' types, while fewer than two is
 * the usual not-enough-arguments refusal. An argument the account folds to NULL before it plans the call — a NULL
 * literal, a cast or an arithmetic over one — folds the whole call to NULL instead: {@code DIV(NULL, 3)} and
 * {@code DIV(a, NULL)} are NULL, where {@code DIV(n, n)} over a NULL column is refused (live-verified).
 *
 * <p>A statement is refused for such a call before it reads a row (see {@code DivCalls}); a value reaches this
 * function only where a call is evaluated on its own, in a block's expression, and there a NULL argument is what
 * the block bound for a variable, which the account folds too.
 */
public class Div extends BuiltInFunction {

    /** The refusal a call that does not fold gets. */
    public static final String UNSUPPORTED = "Unsupported feature 'DIV'.";

    public Div() {
        super("DIV", NumericType.NUMBER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        for (final Object arg : args) {
            if (arg == null) {
                return null;
            }
        }
        throw new RuntimeException(UNSUPPORTED);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return Integer.MAX_VALUE;
    }
}
