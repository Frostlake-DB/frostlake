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

import dev.frostlake.executor.BoundedParseCache;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Parses texts that differ only in their literals — a SQL function's body with each parameter written out as its
 * argument — by reading the grammar once per shape.
 *
 * <p>Two texts have one shape when their tokens are of the same types, everything but their number and string
 * literals is written alike, and the same characters stand between them. The parser's decisions read token types
 * and the words of identifiers, never a literal's digits or characters, so texts of one shape have one parse
 * tree but for those literals. The tree of the first text read is kept with its tokens, and a later text of its
 * shape moves each kept token onto its own token before the tree is turned into an {@link Expression} again: the
 * expression built is the one a parse of that text builds, positions and all.
 *
 * <p>A text holding a query is read afresh each time, as its expression keeps parts of the tree; so is a text
 * whose kept tree another thread is rebuilding at the time.
 */
public final class ShapedExpressionParser {

    private static final BoundedParseCache<ShapedTree> SHAPES = new BoundedParseCache<>(512, 4096);

    private static final char LITERAL_MARK = '\u0000';

    private ShapedExpressionParser() {
    }

    /**
     * The expression a text is, as {@link AntlrExpressionParser#parse(String)} builds it.
     *
     * @param expression the expression text, trimmed
     * @return its expression
     */
    public static Expression parse(final String expression) {
        if (expression.length() > 4096 || expression.indexOf(LITERAL_MARK) >= 0) {
            return AntlrExpressionParser.parse(expression);
        }
        final List<Token> tokens;
        try {
            tokens = tokensOf(expression);
        } catch (final RuntimeException unreadable) {
            return AntlrExpressionParser.parse(expression);
        }
        final String shape = shapeOf(expression, tokens);
        if (shape == null) {
            return AntlrExpressionParser.parse(expression);
        }
        final ShapedTree kept = SHAPES.get(shape);
        if (kept != null) {
            final Expression rebuilt = kept.rebuiltFor(tokens);
            // A tree another thread is rebuilding is not waited for: the text is read afresh.
            return rebuilt != null ? rebuilt : AntlrExpressionParser.parse(expression);
        }
        final FrostlakeParser.BooleanExprContext tree;
        try {
            tree = AntlrExpressionParser.parseTree(expression, tokens);
        } catch (final RuntimeException refused) {
            // The plain parse refuses the text in its own words.
            return AntlrExpressionParser.parse(expression);
        }
        if (!holdsOnlyItsTokens(tree, tokens)) {
            return new ExpressionAstBuilder().build(tree);
        }
        final ShapedTree shaped = new ShapedTree(tree, tokens);
        final Expression built;
        try {
            built = shaped.rebuiltFor(tokens);
        } catch (final RuntimeException refused) {
            return AntlrExpressionParser.parse(expression);
        }
        SHAPES.put(shape, shaped);
        return built;
    }

    /** Every token of a text, its end of input last. */
    private static List<Token> tokensOf(final String expression) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(expression));
        lexer.removeErrorListeners();
        lexer.setTokenFactory(new ReboundTokenFactory());
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        return new ArrayList<Token>(stream.getTokens());
    }

    /**
     * The text with each number and string literal replaced by a mark of its type, or null for a text read afresh
     * each time: one holding a query, or a literal spanning lines.
     */
    private static String shapeOf(final String expression, final List<Token> tokens) {
        final StringBuilder shape = new StringBuilder(expression.length());
        int next = 0;
        for (final Token token : tokens) {
            final int type = token.getType();
            if (type == Token.EOF) {
                break;
            }
            if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WITH || token.getStartIndex() < next
                    || token.getChannel() != Token.DEFAULT_CHANNEL) {
                return null;
            }
            shape.append(expression, next, token.getStartIndex());
            final String text = token.getText();
            if (type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
                    || type == FrostlakeLexer.STRING_LITERAL) {
                if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
                    return null;
                }
                shape.append(LITERAL_MARK).append(type).append(LITERAL_MARK);
            } else {
                shape.append(text);
            }
            next = token.getStopIndex() + 1;
        }
        shape.append(expression, Math.min(next, expression.length()), expression.length());
        return shape.toString();
    }

    /** Whether every terminal and every node's bounds of a tree are tokens of the list, at their own places. */
    private static boolean holdsOnlyItsTokens(final ParseTree node, final List<Token> tokens) {
        if (node instanceof TerminalNode) {
            return isListed(((TerminalNode) node).getSymbol(), tokens);
        }
        if (node instanceof ParserRuleContext) {
            final ParserRuleContext context = (ParserRuleContext) node;
            if (context.exception != null || !isListed(context.start, tokens)
                    || (context.stop != null && !isListed(context.stop, tokens))) {
                return false;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!holdsOnlyItsTokens(node.getChild(i), tokens)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isListed(final Token token, final List<Token> tokens) {
        final int index = token == null ? -1 : token.getTokenIndex();
        return index >= 0 && index < tokens.size() && tokens.get(index) == token;
    }
}
