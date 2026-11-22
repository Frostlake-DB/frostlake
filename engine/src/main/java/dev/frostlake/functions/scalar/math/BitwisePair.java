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

import java.math.BigInteger;
import java.util.List;

/**
 * BITAND, BITOR and BITXOR: two whole numbers combined in 128 bits, or two BINARY values combined byte by byte
 * with an optional third argument naming the side a shorter one is padded on (see {@link BinaryBitwise}). The
 * BINARY operands and the padding side are judged as a whole signature while the statement compiles (see the
 * evaluator's bitwise signature rule); the padding side takes a text, a number, a BOOLEAN, a date or time and a
 * VARIANT alike, read as its text when a row reaches it (live-verified).
 */
public abstract class BitwisePair extends NumericArgumentFunction {

    private final BitwiseOperation operation;

    protected BitwisePair(final String name, final BitwiseOperation operation) {
        super(name, IntegerResultWidths.WIDEST);
        this.operation = operation;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        if (BinaryBitwise.holdsBinary(args.get(0), args.get(1))) {
            if (args.size() > 2 && args.get(2) == null) {
                return null;
            }
            return BinaryBitwise.combined(operation, (BinaryValue) args.get(0), (BinaryValue) args.get(1),
                args.size() > 2 ? args.get(2) : null);
        }
        return BitwiseOperand.result(numeric(BitwiseOperand.wide(args.get(0)), BitwiseOperand.wide(args.get(1))));
    }

    /**
     * The operation over two whole numbers.
     *
     * @param first  the first number
     * @param second the second number
     * @return the combined number
     */
    protected abstract BigInteger numeric(BigInteger first, BigInteger second);

    /** The padding side is read as text, never as a number. */
    @Override
    public boolean readsNumberAt(final int position) {
        return position < 2;
    }

    /** A VARIANT is cast to a whole number here. */
    @Override
    public String variantCastTarget() {
        return "FIXED";
    }

    /** A BINARY operand is taken; a BINARY padding side is refused with the whole signature. */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return position < 2 ? SemiStructuredRejection.NONE : super.binaryRejection(position);
    }

    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position < 2 ? super.booleanRejection(position) : SemiStructuredRejection.NONE;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return position < 2 ? super.temporalRejection(position) : SemiStructuredRejection.NONE;
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 3;
    }
}
