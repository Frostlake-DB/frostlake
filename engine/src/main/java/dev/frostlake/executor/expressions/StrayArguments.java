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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A positional argument written after a named one. The account reads such a call whole and refuses it while the
 * statement compiles, before any name in it is looked up — a table that is not there, a function nobody made —
 * and after every syntax error (live-verified):
 *
 * <pre>
 *   SELECT n1(a =&gt; 1, 2)                    error line 1 at position 18
 *                                           positional argument cannot follow named argument in function call 'N1'
 *   SELECT UPPER(x =&gt; 'a', 'b')             … in function call 'UPPER'    a built-in, a user's and an unknown name alike
 *   SELECT test_schema."n1"(a =&gt; 1, 2)      … in function call 'n1'       the name's last part, as it resolves
 *   SELECT n1(a =&gt; 1, 1 + 1)                at the '+': the argument's own place (see {@link ExpressionPlace})
 *   SELECT n1(a =&gt; 1, DATE '2020-01-01')    at the string; a COLLATE at the word, a NOT IN list at the NOT and an
 *                                           IS DISTINCT FROM at the IS
 *   SELECT n1(a =&gt; 1, t1.*)                 at the T1; a bare star at line 0, position 0
 *   SELECT n1(a =&gt; UPPER(x =&gt; 'a', 'b'), 2) the inner call's: a call is judged after the calls it holds
 * </pre>
 *
 * <p>Ahead of that, and in the same pass, a name written twice among a call's named arguments, wherever the second
 * stands, is refused at its second value, under the call's bare name — a built-in's, a user's and an unknown one's
 * alike (live-verified):
 *
 * <pre>
 *   SELECT f(a =&gt; 1, a =&gt; 2)                 error line 1 at position 18   duplicate named argument A for function F
 *   SELECT f(a =&gt; 1, a =&gt; 2, 3)              the same: the name outranks the positional argument after it
 *   SELECT f(a =&gt; 1, 3, a =&gt; 2)              at the 2
 *   SELECT f(a =&gt; 1, "a" =&gt; 2)               no duplicate: the quoted name is another one
 * </pre>
 *
 * <p>Of several such calls the first in the text is the one refused, a WITH clause's queries read after the
 * statement they head. The call a {@code TABLE( … )} names is the table function's to judge, which refuses a mix
 * of the two ways in words of its own and takes a name twice, so it is passed over here.
 */
public final class StrayArguments {

    private StrayArguments() {
    }

    /**
     * Refuse the first call under {@code root} that writes a positional argument after a named one.
     *
     * @param root a parsed text; null is left alone
     */
    public static void require(final ParseTree root) {
        if (!(root instanceof ParserRuleContext)) {
            return;
        }
        final ParserRuleContext node = (ParserRuleContext) root;
        if (node.children == null) {
            return;
        }
        ParseTree heading = null;
        for (final ParseTree child : node.children) {
            if (child instanceof FrostlakeParser.WithClauseContext) {
                heading = child;
            } else {
                require(child);
            }
        }
        require(heading);
        requireCall(node);
    }

    /**
     * Refuse one call, not the calls it holds, for a name its named arguments write twice and then for a
     * positional argument after a named one. A node that is no such call, and the call a {@code TABLE( … )}
     * names, are left alone.
     *
     * @param node a node of a parsed text
     */
    public static void requireCall(final ParserRuleContext node) {
        if (namesATableFunction(node)) {
            return;
        }
        final List<FrostlakeParser.NamedArgumentContext> named = namedArguments(node);
        if (named != null) {
            final Set<String> seen = new HashSet<String>();
            for (final FrostlakeParser.NamedArgumentContext argument : named) {
                final String name = SqlIdentifiers.canonicalText(argument.variableName().getText());
                if (!seen.add(name)) {
                    throw new RuntimeException(duplicate(node, argument, name));
                }
            }
        }
        final FrostlakeParser.StrayArgumentsContext stray = of(node);
        if (stray != null) {
            throw new RuntimeException(refusal(node, stray));
        }
    }

    /** The named arguments a call writes, in the order written, its trailing ones included; null for no call. */
    private static List<FrostlakeParser.NamedArgumentContext> namedArguments(final ParserRuleContext node) {
        final List<FrostlakeParser.NamedArgumentContext> named = new ArrayList<FrostlakeParser.NamedArgumentContext>();
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            named.addAll(((FrostlakeParser.FunctionCallMixedArgsExprContext) node).namedArgument());
        } else if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            named.addAll(((FrostlakeParser.FunctionCallNamedArgsExprContext) node).namedArgumentList().namedArgument());
        } else {
            return null;
        }
        final FrostlakeParser.StrayArgumentsContext stray = of(node);
        if (stray != null) {
            named.addAll(stray.namedArgument());
        }
        return named;
    }

    /** The refusal of a call for a name written twice, placed at the second writing's value. */
    private static String duplicate(final ParserRuleContext call, final FrostlakeParser.NamedArgumentContext argument,
                                    final String name) {
        final ParseTree value = argument.getChild(argument.getChildCount() - 1);
        SourcePosition at = null;
        if (value instanceof FrostlakeParser.BooleanExprContext) {
            at = windowPlace((FrostlakeParser.BooleanExprContext) value);
            if (at == null) {
                at = operatorPlace((FrostlakeParser.BooleanExprContext) value);
            }
        } else if (value instanceof FrostlakeParser.ExpressionContext) {
            at = windowPlace((FrostlakeParser.ExpressionContext) value);
            if (at == null) {
                at = operatorPlace((FrostlakeParser.ExpressionContext) value);
            }
        }
        if (at == null && !(value instanceof FrostlakeParser.ArgumentRowContext)) {
            try {
                at = ExpressionPlace.of(new ExpressionAstBuilder().visit(value));
            } catch (final RuntimeException unread) {
                // A value this builder does not read keeps the place of its first token.
                at = null;
            }
        }
        final Token first = ((ParserRuleContext) value).getStart();
        final SourcePosition within = at != null ? at
            : new SourcePosition(first.getLine(), first.getCharPositionInLine());
        final SourcePosition placed = ExpressionSource.place(within.getLine(), within.getCharPositionInLine());
        return SqlCompilationError.at(placed.getLine(), placed.getCharPositionInLine(),
            "duplicate named argument " + name + " for function " + calledName(call));
    }

    /**
     * What a call writes after its named arguments.
     *
     * @param call a call as parsed
     * @return its trailing arguments, or null when it writes none or is no call
     */
    public static FrostlakeParser.StrayArgumentsContext of(final ParseTree call) {
        if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).strayArguments();
        }
        if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).strayArguments();
        }
        return null;
    }

    /**
     * The refusal of a call for its first positional argument after a named one, placed at that argument.
     *
     * @param call  the call
     * @param stray what it writes after its named arguments
     * @return the refusal
     */
    public static String refusal(final ParserRuleContext call, final FrostlakeParser.StrayArgumentsContext stray) {
        final String sentence = "positional argument cannot follow named argument in function call '"
            + calledName(call) + "'";
        if (stray.getChild(0) instanceof FrostlakeParser.MixedStarArgumentContext) {
            final FrostlakeParser.MixedStarArgumentContext star =
                (FrostlakeParser.MixedStarArgumentContext) stray.getChild(0);
            if (star.starQualifiedName() == null) {
                // A bare star stands nowhere the account tells.
                return SqlCompilationError.at(0, 0, sentence);
            }
            final Token qualifier = star.getStart();
            final SourcePosition placed = ExpressionSource.place(qualifier.getLine(),
                qualifier.getCharPositionInLine());
            return SqlCompilationError.at(placed.getLine(), placed.getCharPositionInLine(), sentence);
        }
        final FrostlakeParser.BooleanExprContext argument = stray.booleanExpr(0);
        SourcePosition at = windowPlace(argument);
        if (at == null) {
            at = operatorPlace(argument);
        }
        if (at == null) {
            try {
                at = ExpressionPlace.of(new ExpressionAstBuilder().visit(argument));
            } catch (final RuntimeException unread) {
                // An argument this builder does not read keeps the place of its first token.
                at = null;
            }
        }
        final Token first = argument.getStart();
        final SourcePosition within = at != null ? at
            : new SourcePosition(first.getLine(), first.getCharPositionInLine());
        final SourcePosition placed = ExpressionSource.place(within.getLine(), within.getCharPositionInLine());
        return SqlCompilationError.at(placed.getLine(), placed.getCharPositionInLine(), sentence);
    }

    /**
     * Where an argument of four shapes stands, which the account tells by a token of its own: a typed literal at
     * its string, a COLLATE at the word, a NOT IN list at the NOT and an IS DISTINCT FROM at the IS; null for any
     * other argument.
     */
    private static SourcePosition operatorPlace(final FrostlakeParser.BooleanExprContext argument) {
        if (!(argument instanceof FrostlakeParser.ValueExprContext)) {
            return null;
        }
        return operatorPlace(((FrostlakeParser.ValueExprContext) argument).expression());
    }

    /** The place {@link #operatorPlace(FrostlakeParser.BooleanExprContext)} tells, of a value read as an expression. */
    private static SourcePosition operatorPlace(final FrostlakeParser.ExpressionContext value) {
        final TerminalNode token;
        if (value instanceof FrostlakeParser.TypedDateTimeLiteralExprContext) {
            token = ((FrostlakeParser.TypedDateTimeLiteralExprContext) value).STRING_LITERAL();
        } else if (value instanceof FrostlakeParser.CollateExprContext) {
            token = ((FrostlakeParser.CollateExprContext) value).COLLATE();
        } else if (value instanceof FrostlakeParser.InListExprContext) {
            token = ((FrostlakeParser.InListExprContext) value).NOT();
        } else if (value instanceof FrostlakeParser.IsDistinctExprContext) {
            token = ((FrostlakeParser.IsDistinctExprContext) value).IS();
        } else {
            token = null;
        }
        return token == null ? null
            : new SourcePosition(token.getSymbol().getLine(), token.getSymbol().getCharPositionInLine());
    }

    /** The OVER of an argument that is a call over a window, which is where such a call stands; null for any other. */
    private static SourcePosition windowPlace(final FrostlakeParser.BooleanExprContext argument) {
        if (!(argument instanceof FrostlakeParser.ValueExprContext)) {
            return null;
        }
        return windowPlace(((FrostlakeParser.ValueExprContext) argument).expression());
    }

    /** The place {@link #windowPlace(FrostlakeParser.BooleanExprContext)} tells, of a value read as an expression. */
    private static SourcePosition windowPlace(final FrostlakeParser.ExpressionContext value) {
        if (!(value instanceof FrostlakeParser.FunctionCallExprContext)
                || ((FrostlakeParser.FunctionCallExprContext) value).overClause() == null) {
            return null;
        }
        final Token over = ((FrostlakeParser.FunctionCallExprContext) value).overClause().getStart();
        return new SourcePosition(over.getLine(), over.getCharPositionInLine());
    }

    /** The last part of the name a call writes, as it resolves: upper-cased unless it is quoted. */
    private static String calledName(final ParserRuleContext call) {
        final FrostlakeParser.FunctionNameContext name = call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
            ? ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).functionName()
            : ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).functionName();
        final Token last = name.getStop();
        return SqlIdentifiers.canonicalText(last.getText());
    }

    /** Whether a call is the whole operand of a {@code TABLE( … )} in a FROM clause. */
    private static boolean namesATableFunction(final ParserRuleContext call) {
        final ParserRuleContext parent = call.getParent();
        return parent instanceof FrostlakeParser.TableSourceContext
            && ((FrostlakeParser.TableSourceContext) parent).TABLE() != null
            && ((FrostlakeParser.TableSourceContext) parent).expression() == call;
    }

    /**
     * The arguments a call writes after its named ones, in the order written.
     *
     * @param stray the trailing arguments, or null
     * @param out   the list each is added to: a {@code NamedArgumentContext}, a {@code BooleanExprContext} or a
     *              {@code MixedStarArgumentContext}
     */
    public static void collect(final FrostlakeParser.StrayArgumentsContext stray, final List<ParserRuleContext> out) {
        if (stray == null || stray.children == null) {
            return;
        }
        for (final ParseTree child : stray.children) {
            if (child instanceof FrostlakeParser.NamedArgumentContext
                    || child instanceof FrostlakeParser.BooleanExprContext
                    || child instanceof FrostlakeParser.MixedStarArgumentContext) {
                out.add((ParserRuleContext) child);
            }
        }
    }
}
