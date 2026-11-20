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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.ArrayArgumentKind;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.VariantAccessorFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * ARRAY_APPEND(array, value) — appends value to array and returns the new array.
 */
public class ArrayAppend extends VariantAccessorFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ArrayAppend() {
        super("ARRAY_APPEND", ArrayType.ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // A VARIANT that holds no array is read as a one-element array of it (see parseArrayOrSingle).
        final ArrayNode single = ArrayFunctionHelper.singleOf(args.get(0));
        final String arrayStr = single != null ? single.toString() : args.get(0).toString().trim();
        final Object value = args.get(1);

        try {
            // The source array may already hold a VARIANT `undefined` element, whose canonical text is the
            // bare token no JSON parser accepts on its own — see VariantUndefined.
            final ArrayNode array = (ArrayNode) VariantUndefined.readTree(MAPPER, arrayStr);
            // The appended value converts exactly as an ARRAY_CONSTRUCT element does (live-verified): a SQL NULL
            // is `undefined` (ARRAY_APPEND([1], NULL) is [1,undefined]), a VARIANT keeps its own type (a JSON
            // number, boolean or null stays one), a DATE or BINARY keeps its type, and a VARCHAR stays a STRING
            // member however much its text looks like JSON (ARRAY_APPEND([1], '[9]') is [1,"[9]"]).
            array.add(ArrayFunctionHelper.toElementNode(MAPPER, value));
            return VariantValue.ofNode(array);
        } catch (final Exception e) {
            throw new RuntimeException("ARRAY_APPEND: invalid array: " + args.get(0));
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /** A VECTOR is no ARRAY: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public ArrayArgumentKind arrayArgumentKind(final int position) {
        if (position == 0) {
            return ArrayArgumentKind.ARRAY;
        }
        return null;
    }
}
