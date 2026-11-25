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

import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The number a called procedure's body starts drawing the binds of its variables from (see UserCallBinds), set by its
 * CALL (live-verified): fifteen, seventeen for a CALL a dynamic text holds, one more where the CALL names the
 * procedure without a schema while the session's current schema is PUBLIC, then for each argument one and what it
 * computes - a literal three where the CALL is the request, two where a block runs it, none in a text; a name the
 * first time it is read one, a sign one, a call one and one per argument, a cast two, an operator three - and two
 * for a conversion to its parameter's type, which a NULL always takes, a number every parameter but a NUMBER(38, 0)
 * one, and a text every parameter but a text one. Each parameter with a default draws two more.
 */
final class ProcedureBindStart {

    private static final int START = 15;
    private static final int TEXT_START = 17;
    private static final int NAMED_FROM_PUBLIC = 1;
    private static final String PUBLIC = "PUBLIC";
    private static final int PER_ARGUMENT = 1;
    private static final int PER_NAME = 1;
    private static final int PER_SIGN = 1;
    private static final int PER_CALL = 1;
    private static final int PER_CAST = 2;
    private static final int PER_OPERATOR = 3;
    private static final int PER_DEFAULT = 2;
    private static final int PER_CONVERSION = 2;
    private static final int WHOLE_PRECISION = 38;

    private final CallPlacement placement;
    private final Set<String> namesRead = new HashSet<>();

    private ProcedureBindStart(final CallPlacement placement) {
        this.placement = placement;
    }

    /**
     * The number the body of the procedure the CALL calls starts from.
     *
     * @param call       the CALL as parsed
     * @param parameters the procedure's parameters
     * @param placement  where the CALL stands
     * @param bareName   whether the CALL names the procedure without a schema
     * @param current    the session's current schema, or null
     * @return the number
     */
    static int of(final FrostlakeParser.CallStatementContext call, final List<Parameter> parameters,
                  final CallPlacement placement, final boolean bareName, final String current) {
        final ProcedureBindStart walk = new ProcedureBindStart(placement);
        int start = (placement == CallPlacement.TEXT ? TEXT_START : START)
            + (bareName && PUBLIC.equals(current) ? NAMED_FROM_PUBLIC : 0);
        for (final Parameter parameter : parameters) {
            if (parameter.hasDefault()) {
                start += PER_DEFAULT;
            }
        }
        if (call.callArguments() == null) {
            return start;
        }
        int position = 0;
        for (final FrostlakeParser.CallArgumentContext argument : call.callArguments().callArgument()) {
            final ParseTree value;
            Parameter parameter = null;
            if (argument.namedArgument() != null) {
                final FrostlakeParser.NamedArgumentContext named = argument.namedArgument();
                value = named.expression() != null ? named.expression() : named.booleanExpr();
                final String name = SqlIdentifiers.canonicalText(named.variableName().getText());
                for (final Parameter candidate : parameters) {
                    if (candidate.getName().equalsIgnoreCase(name)) {
                        parameter = candidate;
                    }
                }
            } else {
                value = argument.expression() != null ? argument.expression() : argument.booleanExpr();
                parameter = position < parameters.size() ? parameters.get(position) : null;
                position++;
            }
            start += PER_ARGUMENT;
            if (value != null) {
                start += walk.cost(value);
                if (parameter != null && converts(value, parameter.getDataType())) {
                    start += PER_CONVERSION;
                }
            }
        }
        return start;
    }

    /**
     * The names of the parameters, in their order.
     *
     * @param parameters the procedure's parameters
     * @return the names
     */
    static List<String> names(final List<Parameter> parameters) {
        final List<String> names = new ArrayList<>();
        for (final Parameter parameter : parameters) {
            names.add(parameter.getName());
        }
        return names;
    }

    private int literal() {
        return placement == CallPlacement.REQUEST ? 3 : placement == CallPlacement.BLOCK ? 2 : 0;
    }


    private int cost(final ParseTree node) {
        int cost = 0;
        if (node instanceof FrostlakeParser.LiteralExprContext) {
            cost += literal();
        } else if (node instanceof FrostlakeParser.QualifiedNameExprContext
                || node instanceof FrostlakeParser.BindVarExprContext) {
            cost += namesRead.add(node.getText().toUpperCase(Locale.ROOT)) ? PER_NAME : 0;
        } else if (node instanceof FrostlakeParser.UnaryExprContext
                || node instanceof FrostlakeParser.NotExprContext) {
            cost += PER_SIGN;
        } else if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionArgListContext arguments =
                ((FrostlakeParser.FunctionCallExprContext) node).functionArgList();
            cost += PER_CALL + (arguments == null ? 0 : arguments.functionArg().size());
        } else if (node instanceof FrostlakeParser.CastExprContext
                || node instanceof FrostlakeParser.CastExpr2Context) {
            cost += PER_CAST;
        } else if (node instanceof FrostlakeParser.AdditiveExprContext
                || node instanceof FrostlakeParser.MultiplicativeExprContext
                || node instanceof FrostlakeParser.ComparisonExprContext
                || node instanceof FrostlakeParser.AndExprContext
                || node instanceof FrostlakeParser.OrExprContext) {
            cost += PER_OPERATOR;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            cost += cost(node.getChild(i));
        }
        return cost;
    }

    /** Whether an argument written as a literal, or as signs and arithmetic over number literals, is converted. */
    private static boolean converts(final ParseTree value, final DataType type) {
        final LiteralArgumentKind kind = kind(value);
        if (kind == null) {
            return false;
        }
        if (kind == LiteralArgumentKind.NULL) {
            return true;
        }
        if (kind == LiteralArgumentKind.NUMBER) {
            return !(type instanceof NumericType && !NumericType.isApproximate(type)
                && ((NumericType) type).getPrecision() == WHOLE_PRECISION && ((NumericType) type).getScale() == 0);
        }
        if (kind == LiteralArgumentKind.TEXT) {
            return !(type instanceof StringType);
        }
        return false;
    }

    /** What a literal, or arithmetic over number literals, is written as; null for anything else. */
    private static LiteralArgumentKind kind(final ParseTree node) {
        if (node instanceof FrostlakeParser.LiteralExprContext) {
            final ParseTree token = ((FrostlakeParser.LiteralExprContext) node).literal().getChild(0);
            final int type = token instanceof TerminalNode ? ((TerminalNode) token).getSymbol().getType() : -1;
            if (type == FrostlakeParser.NULL) {
                return LiteralArgumentKind.NULL;
            }
            if (type == FrostlakeParser.INTEGER_LITERAL || type == FrostlakeParser.FLOAT_LITERAL) {
                return LiteralArgumentKind.NUMBER;
            }
            if (type == FrostlakeParser.STRING_LITERAL || type == FrostlakeParser.DOLLAR_QUOTED_STRING) {
                return LiteralArgumentKind.TEXT;
            }
            return null;
        }
        if (node instanceof FrostlakeParser.UnaryExprContext || node instanceof FrostlakeParser.AdditiveExprContext
                || node instanceof FrostlakeParser.MultiplicativeExprContext) {
            for (int i = 0; i < node.getChildCount(); i++) {
                final ParseTree child = node.getChild(i);
                if (!(child instanceof TerminalNode) && kind(child) != LiteralArgumentKind.NUMBER) {
                    return null;
                }
            }
            return LiteralArgumentKind.NUMBER;
        }
        if (node.getChildCount() == 1 && !(node instanceof TerminalNode)) {
            return kind(node.getChild(0));
        }
        if (node.getChildCount() == 3 && "(".equals(node.getChild(0).getText())) {
            return kind(node.getChild(1));
        }
        return null;
    }
}
