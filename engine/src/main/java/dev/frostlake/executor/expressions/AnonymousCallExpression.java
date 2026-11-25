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

import java.util.List;

/**
 * A CALL of a procedure the CALL defines for itself standing as a value, {@code (WITH p AS PROCEDURE … CALL p(…))}.
 * The account calls the procedure as a function of a query: the value is the wrapper the procedure's answer travels
 * in, {@code {"type":"SCALAR","scalarValue":"7",…}}, a text, so that an operator reading it as a number refuses it,
 * {@code Numeric value '{…}' is not recognized}, while a value a statement answers or a variable takes is the
 * answer itself once more (see {@link dev.frostlake.executor.AnonymousCallAnswers}; all live-verified). It is the
 * statement an {@link ExecuteImmediateExpression} runs, the CALL's arguments its bound values, read where the value
 * stands; a CALL naming another routine than the one it defines is refused by that name.
 */
public class AnonymousCallExpression extends ExecuteImmediateExpression {

    private final String unknownRoutine;

    /**
     * @param callText       the CALL statement, a {@code ?} where each argument's value goes
     * @param arguments      the arguments' values in order
     * @param unknownRoutine the refusal of a call of another routine, or null
     */
    public AnonymousCallExpression(final String callText, final List<Expression> arguments,
                                   final String unknownRoutine) {
        super(new LiteralExpression(callText, LiteralType.STRING), arguments);
        this.unknownRoutine = unknownRoutine;
    }

    /**
     * The refusal of a CALL naming another routine than the procedure it defines, or null when it calls that one.
     *
     * @return the refusal's message, or null
     */
    public String getUnknownRoutine() {
        return unknownRoutine;
    }
}
