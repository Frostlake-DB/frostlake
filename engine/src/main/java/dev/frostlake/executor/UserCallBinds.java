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
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * How the refusal of a miscounted call of a user's function writes the block variables the call reads: the call
 * compiles as a query, which reads each variable as a bind named by the variable and a number the block's compilation
 * drew for it, converted to the variable's type - {@code TO_NUMBER(:X_9)}, a text bare as {@code :S_9},
 * {@code CAST(:X_9 AS FLOAT)}, {@code CAST(:X_9 AS BOOLEAN)}, {@code CAST(:X_11 AS DATE)},
 * {@code CAST(:X_7 AS TIME(9))} and each timestamp so with its precision, {@code PARSE_JSON(:X_7)} for a VARIANT,
 * {@code CAST(PARSE_JSON(:X_7) AS ARRAY)} and the same AS OBJECT, {@code CAST(:X_7 AS BINARY(10))} (BINARY(67108864)
 * for a binary of the default width), a FOR counter as {@code TO_NUMBER(:I_15, 9, 0)} (live-verified). The numbers
 * start past two for every literal of the request that holds the block - two plus two for each literal the block
 * writes, anywhere in it, a NULL statement among them - and a block that a dynamic text holds starts from the text's
 * own start, four, or five where a block runs the text; from there the block's statements draw them in its order (see
 * BlockBindNumbers). A procedure's body starts where its CALL sets it (see ProcedureBindStart).
 */
final class UserCallBinds {

    private static final String CALL_OPENS = "function [";
    private static final int REQUEST_START = 2;
    private static final int TEXT_START = 4;
    private static final int TEXT_IN_A_BLOCK_START = 5;
    private static final int PER_LITERAL = 2;
    private static final int WHOLE_PRECISION = 38;

    /** The procedure bodies running on this thread, each with the names of its procedure's parameters. */
    private static final ThreadLocal<Map<ParseTree, List<String>>> BODIES =
        new ThreadLocal<Map<ParseTree, List<String>>>() {
            @Override
            protected Map<ParseTree, List<String>> initialValue() {
                return new IdentityHashMap<>();
            }
        };

    /** The dynamic texts running on this thread, each with the number its blocks start from. */
    private static final ThreadLocal<Map<ParseTree, Integer>> TEXTS = new ThreadLocal<Map<ParseTree, Integer>>() {
        @Override
        protected Map<ParseTree, Integer> initialValue() {
            return new IdentityHashMap<>();
        }
    };

    private UserCallBinds() {
    }

    /**
     * The refusal with each variable the miscounted call reads written as its bind, or null where the call is no user's
     * function, the expression stands in no block, or a variable it reads takes no known bind.
     *
     * @param message    the refusal, each variable written as its bound value (see BoundValueRendering)
     * @param expression the expression as parsed in the block
     * @param types      the types of the names in scope, keyed as the resolver folds them
     * @param values     the values of the names in scope, keyed upper-cased
     * @param functions  the built-in functions, which a user's function is none of
     * @return the refusal, or null
     */
    static String rewrite(final String message, final ParserRuleContext expression, final Map<String, DataType> types,
                          final Map<String, Object> values, final FunctionRegistry functions) {
        final int opens = message.indexOf(CALL_OPENS);
        if (opens < 0 || expression == null) {
            return null;
        }
        final int start = opens + CALL_OPENS.length();
        final int paren = message.indexOf('(', start);
        final int closes = message.lastIndexOf(']');
        if (paren < 0 || closes < paren || functions == null) {
            return null;
        }
        final String called = message.substring(start, paren);
        if (functions.hasFunction(called) || functions.hasAggregateFunction(called)) {
            return null;
        }
        final FrostlakeParser.BeginEndBlockContext block = outermostBlock(expression);
        if (block == null) {
            return null;
        }
        final Map<String, Integer> numbers = numbers(block, functions);
        final StringBuilder call = new StringBuilder(message.substring(start, closes));
        int from = 0;
        for (final String name : variablesRead(expression)) {
            final DataType type = types.get(name);
            final Integer number = numbers.get(name.toUpperCase(Locale.ROOT));
            final String held = name.toUpperCase(Locale.ROOT);
            if (type == null || number == null || !values.containsKey(held)) {
                return null;
            }
            final String bind = bindOf(type, held + "_" + number);
            final Expression valueForm = BoundValueRendering.of(type, values.get(held), false);
            if (bind == null || valueForm == null) {
                return null;
            }
            final String value = AstPrinterVisitor.print(valueForm);
            final int at = call.indexOf(value, from);
            if (at < 0) {
                return null;
            }
            call.replace(at, at + value.length(), bind);
            from = at + bind.length();
        }
        return message.substring(0, start) + call + message.substring(closes);
    }

    /** The bind a variable of {@code type} is read as, or null for a type whose form is not known. */
    private static String bindOf(final DataType type, final String bind) {
        final String read = ":" + bind;
        if (type instanceof StringType) {
            return read;
        }
        if (type instanceof NumericType && !NumericType.isApproximate(type)) {
            final NumericType number = (NumericType) type;
            return number.getPrecision() == WHOLE_PRECISION && number.getScale() == 0 ? "TO_NUMBER(" + read + ")"
                : "TO_NUMBER(" + read + ", " + number.getPrecision() + ", " + number.getScale() + ")";
        }
        if (type instanceof NumericType) {
            return "CAST(" + read + " AS FLOAT)";
        }
        if (type instanceof BooleanType) {
            return "CAST(" + read + " AS BOOLEAN)";
        }
        if (type instanceof DateTimeType && "DATE".equals(type.getName())) {
            return "CAST(" + read + " AS DATE)";
        }
        if (type instanceof DateTimeType && (type.getName().startsWith("TIME"))) {
            return "CAST(" + read + " AS " + type.getName() + "(" + ((DateTimeType) type).getPrecision() + "))";
        }
        if (type.getClass() == VariantType.class) {
            return "PARSE_JSON(" + read + ")";
        }
        if (type.getClass() == ArrayType.class) {
            return "CAST(PARSE_JSON(" + read + ") AS ARRAY)";
        }
        if (type.getClass() == ObjectType.class) {
            return "CAST(PARSE_JSON(" + read + ") AS OBJECT)";
        }
        if (type instanceof BinaryType) {
            final int width = ((BinaryType) type).getMaxLength();
            return "CAST(" + read + " AS BINARY(" + (width == BinaryType.DEFAULT_WIDTH ? BinaryType.NOMINAL_MAXIMUM
                : width) + "))";
        }
        return null;
    }

    /** The block furthest out that holds the expression, or null. */
    private static FrostlakeParser.BeginEndBlockContext outermostBlock(final ParserRuleContext expression) {
        FrostlakeParser.BeginEndBlockContext found = null;
        for (ParseTree at = expression; at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.BeginEndBlockContext) {
                found = (FrostlakeParser.BeginEndBlockContext) at;
            }
        }
        return found;
    }

    /**
     * A dynamic text starts running its statement: a block in it draws its numbers from the text's own start, which
     * the request's literals do not move.
     *
     * @param text     the text as parsed
     * @param inABlock whether a block runs the text
     */
    static void textRuns(final ParseTree text, final boolean inABlock) {
        TEXTS.get().put(text, Integer.valueOf(inABlock ? TEXT_IN_A_BLOCK_START : TEXT_START));
    }

    /**
     * A procedure's body starts running: a block in it draws its numbers from {@code start}, which the CALL's
     * arguments set (see ProcedureBindStart), and its parameters take theirs after the body's.
     *
     * @param body       the body as parsed
     * @param start      the number its blocks start from
     * @param parameters the names of the procedure's parameters, in their order
     */
    static void bodyRuns(final ParseTree body, final int start, final List<String> parameters) {
        TEXTS.get().put(body, Integer.valueOf(start));
        BODIES.get().put(body, parameters);
    }

    /**
     * Whether the statement is a dynamic text's own statement, run as the text.
     *
     * @param statement the statement as parsed
     * @return whether a text holds it
     */
    static boolean runsAsAText(final ParseTree statement) {
        ParseTree root = statement;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return TEXTS.get().containsKey(root) && !BODIES.get().containsKey(root);
    }

    /**
     * The dynamic text {@link #textRuns} named, or the body {@link #bodyRuns} named, has run.
     *
     * @param text the text as parsed
     */
    static void textRan(final ParseTree text) {
        TEXTS.get().remove(text);
        BODIES.get().remove(text);
    }

    /** The numbers the block's compilation draws for its variables, by upper-cased name: the first declaration's. */
    private static Map<String, Integer> numbers(final FrostlakeParser.BeginEndBlockContext block,
                                                final FunctionRegistry functions) {
        ParseTree root = block;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        final Integer textStart = TEXTS.get().get(root);
        final List<String> parameters = BODIES.get().get(root);
        return BlockBindNumbers.of(block, textStart != null ? textStart.intValue()
            : REQUEST_START + PER_LITERAL * literals(block),
            parameters != null ? parameters : new ArrayList<String>(), functions);
    }

    /** The literals the block writes, a NULL statement among them. */
    private static int literals(final ParseTree node) {
        if (node instanceof FrostlakeParser.LiteralExprContext || node instanceof FrostlakeParser.NullStatementContext) {
            return 1;
        }
        int count = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += literals(node.getChild(i));
        }
        return count;
    }

    /** The names an expression reads as variables, by name or as a bind, in the order written. */
    private static List<String> variablesRead(final ParseTree expression) {
        final List<String> names = new ArrayList<>();
        collect(expression, names);
        return names;
    }

    private static void collect(final ParseTree node, final List<String> names) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name = ((FrostlakeParser.QualifiedNameExprContext) node)
                .qualifiedName();
            if (name.nameStartPart() != null && name.namePart().isEmpty()) {
                names.add(SqlIdentifiers.canonicalText(name.getText()));
            }
            return;
        }
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            if (bind.variableName() != null) {
                names.add(SqlIdentifiers.canonicalText(bind.variableName().getText()));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), names);
        }
    }
}
