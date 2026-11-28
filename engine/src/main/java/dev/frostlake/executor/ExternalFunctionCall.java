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

import dev.frostlake.types.DataType;

/**
 * A call of a user-defined function written in JavaScript as a plan writes it: the function's name and its parameters,
 * each with its type, {@code FN(X FLOAT)}, in place of the arguments (live-verified), and the type it answers.
 */
final class ExternalFunctionCall {

    private final String text;
    private final DataType returnType;

    /**
     * @param text       the call as the plan writes it
     * @param returnType the type the function answers, or null where it is not known
     */
    ExternalFunctionCall(final String text, final DataType returnType) {
        this.text = text;
        this.returnType = returnType;
    }

    String getText() {
        return text;
    }

    DataType getReturnType() {
        return returnType;
    }
}
