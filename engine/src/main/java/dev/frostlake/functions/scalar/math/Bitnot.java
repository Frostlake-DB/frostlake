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

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.values.BinaryValue;

import java.util.List;

public class Bitnot extends NumericArgumentFunction {
    public Bitnot() { super("BITNOT", IntegerResultWidths.WIDEST); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.get(0) instanceof BinaryValue) return BinaryBitwise.inverted((BinaryValue) args.get(0));
        return BitwiseOperand.result(BitwiseOperand.wide(args.get(0)).not());
    }

    /** A VARIANT is cast to a whole number here. */
    @Override
    public String variantCastTarget() {
        return "FIXED";
    }

    /** A BINARY value is taken in first place, the BINARY form (see BinaryBitwise). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.NONE : super.binaryRejection(position);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
