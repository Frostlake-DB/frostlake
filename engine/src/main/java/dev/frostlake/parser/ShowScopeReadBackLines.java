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
package dev.frostlake.parser;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * The lines live reports for a SHOW listing it cannot finish inside its scope, {@code IN …}: in the outermost
 * block's own statement list or one of its exception handlers', in a block of another block's list, in an IF's,
 * a CASE's, a loop's or a REPEAT's body, and for an IDENTIFIER() reference on its own too (all live-verified).
 *
 * <p>★ A TOKEN THAT IS NO NAME, refused inside the scope, is named, and then live names a token back in the scope: the
 * second token after the IN, where the reading of the scope's words was still open. The scope's words between that
 * token and the refused one must not have settled the reading: a word as that token is followed straight by the refused
 * token (no LIMIT or STARTS); a dot there is followed by words and single dots only. A scope of the kind SCHEMA reads
 * its {@code db.schema} as one name, so a dotted one moves that token to the one after the name. Otherwise the parse's
 * own line stands. An {@code =} there swaps the two lines — {@code … IN nosuch x = 1; …} is 'x', then '=' — while a
 * {@code )} and a positional {@code $1} are one line. In an IF's body the rest is read on instead ({@link
 * #readOnInIf}).
 *
 * <pre>
 *   BEGIN SHOW TABLES IN nosuch x 1; END;          '1', then 'x'
 *   BEGIN SHOW TABLES IN COMPUTE POOL; END;        ';', then 'POOL'
 *   BEGIN SHOW TABLES IN a.b.c 1; END;             '1', then the first '.'
 *   BEGIN SHOW TABLES IN nosuch 1; END;            '1', then '1' again
 *   BEGIN SHOW TABLES IN SCHEMA a.b.c 1; END;      '1', then the second '.'
 *   BEGIN SHOW TABLES IN nosuch x . ; END;         ';' alone: the dot settled the reading
 *   BEGIN SHOW TABLES IN COMPUTE POOL p 1; END;    '1' alone
 *   BEGIN SHOW TABLES IN USER LIMIT; END;          ';' alone: a LIMIT or a STARTS opens the listing's modifiers
 *   BEGIN SHOW TABLES IN APPLICATION PACKAGE; END; ';' alone, where '… PACKAGE 1; …' is '1', then 'PACKAGE'
 * </pre>
 *
 * <p>★ IN A BLOCK OF ANOTHER BLOCK'S OWN LIST the scope is then read again from the token named back — past a name,
 * or a dot and a name — and the token that reading stops at is named too; a semicolon there reads on to the token
 * after the plain queries that follow, an END or a RETURN. In a loop's body (WHILE, LOOP, FOR) a token refused within the statement is
 * named, then what that reading refuses, and then the token after the plain queries past the semicolon, or, with
 * none, what an END straight after leaves once the loop is given up (but nothing for an END FOR); three lines at
 * most. In an IF's, a CASE's or a REPEAT's body the refused token is followed by that read-on alone. A refused token
 * named back itself, a refused semicolon, a swapped {@code =} and the function listing's word are read on there as
 * {@link BlockReadOn} reads the rest, a REPEAT's UNTIL straight after a refused semicolon not named.
 *
 * <pre>
 *   BEGIN BEGIN SHOW TABLES IN nosuch x 1; END; END;              '1', 'x', then '1' again
 *   BEGIN BEGIN SHOW TABLES IN a.b.c 1; END; END;                 '1', the first '.', then the second
 *   BEGIN BEGIN SHOW TABLES IN COMPUTE POOL; END; END;            ';', 'POOL', then the first END
 *   BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; END WHILE; END;            '1', '1', then the last END
 *   BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; SELECT 1; END WHILE; END; '1', '1', then the END of END WHILE
 *   BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; ELSE SELECT 1; END IF; END; '1' alone
 * </pre>
 *
 * <p>★ A WITH WRITTEN AS THE SCOPE, {@code IN WITH x y}, in a block or a construct's body, names the WITH, the word
 * after it, and then what a keyword scope stacks after its first name, the word after that ({@link
 * KeywordScopeLines}).
 *
 * <p>★ THE PLAIN FUNCTION LISTING, {@code SHOW FUNCTIONS}, refused at the token after its IN names the word after
 * FUNCTIONS next: {@code BEGIN SHOW FUNCTIONS IN 1; END;} is '1', then 'IN', and {@code … LIKE 'a' IN 1; …} is '1',
 * then 'LIKE'. A USER or TERSE listing, or another listing, is the refused token alone.
 *
 * <p>★ AN IDENTIFIER() REFERENCE AFTER A SCOPE WORD THAT READS NO NAME ({@link ShowScopeWords}) is refused at its
 * IDENTIFIER word, whatever follows the reference: {@code SHOW TABLES IN DATASET IDENTIFIER('y') z} is 'IDENTIFIER'
 * alone. In the outermost block's list the token after IDENTIFIER follows, in a block of another block's list then
 * the END after the plain queries past the semicolon; in an IF's body, the token after the
 * word that follows the reference, or, past the statement's semicolon, the END after the queries read on.
 */
public final class ShowScopeReadBackLines {

    /** The most lines a read-back in a construct's body names. */
    private static final int MOST_LINES = 3;

    private ShowScopeReadBackLines() {
    }

    /**
     * The lines of the parse's first fault when it is one of the three shapes, or null.
     *
     * @param recognizer the parser
     * @param e          the fault, or null
     * @param refused    the token the fault names
     * @return the lines, or null for any other fault
     */
    static List<String> lines(final Recognizer<?, ?> recognizer, final RecognitionException e, final Token refused) {
        if (!(recognizer instanceof Parser) || refused == null || refused.getType() == Token.EOF) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final List<Token> spoken = new ArrayList<>();
        int at = -1;
        for (int i = 0; i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                if (token.getTokenIndex() == refused.getTokenIndex()) {
                    at = spoken.size();
                }
                spoken.add(token);
            }
        }
        if (at < 0) {
            return null;
        }
        final int show = statementShow(spoken, at);
        if (show < 0) {
            return null;
        }
        int in = -1;
        for (int i = show + 1; i < at; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.IN) {
                in = i;
            }
        }
        if (in < 0) {
            return null;
        }
        final ParserRuleContext ctx = e != null && e.getCtx() instanceof ParserRuleContext
            ? (ParserRuleContext) e.getCtx() : parser.getContext();
        final ShowStatementPlacement placement = placement(ctx);
        if (placement != ShowStatementPlacement.STANDALONE && placement != ShowStatementPlacement.OTHER
                && at == in + 1 && refused.getType() == FrostlakeLexer.WITH
                && functionListingWord(spoken, show, in) == null) {
            return scopeWith(stream, spoken, at, ctx);
        }
        final List<String> reference = identifierReference(stream, spoken, in, at, placement);
        if (reference != null) {
            return reference;
        }
        return readBack(spoken, at, placement);
    }

    /**
     * The lines of a SHOW listing refused inside its scope at a token that is no name, by where the statement
     * stands: the refused token and the token named back in a block's own list — in a block of another block's own
     * list, then what reading the scope again from that token refuses; in a loop's body the refused token, what that
     * reading refuses, and what the body reads on past plain queries; in an IF's, a CASE's or a REPEAT's body the
     * refused token and what is read on (all live-verified). Null when no token is named back.
     *
     * @param spoken    the text's default-channel tokens
     * @param at        the index of the refused token among them
     * @param placement where the statement stands
     * @return the lines, or null
     */
    public static List<String> readBack(final List<Token> spoken, final int at, final ShowStatementPlacement placement) {
        final Token back = placement == ShowStatementPlacement.STANDALONE || placement == ShowStatementPlacement.OTHER
            ? null : tokenBack(spoken, at);
        if (back == null) {
            return null;
        }
        final Token refused = spoken.get(at);
        final int show = statementShow(spoken, at);
        int in = -1;
        for (int i = show + 1; i < at; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.IN) {
                in = i;
            }
        }
        final int b = indexOf(spoken, back);
        final boolean swapped = refused.getType() == FrostlakeLexer.EQ && back != refused;
        // The scope read again from the token named back: a name, or a dot and a name, before the refused token.
        final boolean readAgain = back != refused && !swapped && b > in;
        final int semicolon = semicolonFrom(spoken, at);
        final List<String> lines = new ArrayList<>();
        if (placement == ShowStatementPlacement.BLOCK || placement == ShowStatementPlacement.NESTED) {
            if (swapped) {
                lines.add(SyntaxErrorListener.sentence(back));
                lines.add(SyntaxErrorListener.sentence(refused));
            } else {
                lines.add(SyntaxErrorListener.sentence(refused));
                lines.add(SyntaxErrorListener.sentence(back));
            }
            if (placement == ShowStatementPlacement.NESTED && readAgain) {
                final Token next = readAgainFrom(spoken, b);
                if (next != null && next.getType() == FrostlakeLexer.SEMI) {
                    final Token end = afterQueries(spoken, semicolon + 1, 0);
                    if (end != null) {
                        lines.add(SyntaxErrorListener.sentence(end));
                    }
                } else if (next != null) {
                    lines.add(SyntaxErrorListener.sentence(next));
                }
            }
            return lines;
        }
        final boolean withinStatement = readAgain && refused.getType() != FrostlakeLexer.SEMI;
        if (withinStatement) {
            lines.add(SyntaxErrorListener.sentence(refused));
            if (placement == ShowStatementPlacement.LOOP) {
                // ★ A LOOP'S BODY reads the scope again from the token named back and names what it refuses then.
                int next = readAgainIndex(spoken, b);
                while (next >= 0 && lines.size() < MOST_LINES) {
                    final Token again = spoken.get(next);
                    if (again.getType() == FrostlakeLexer.SEMI || again.getType() == Token.EOF) {
                        break;
                    }
                    lines.add(SyntaxErrorListener.sentence(again));
                    next = again.getType() == FrostlakeLexer.DOT ? readAgainIndex(spoken, next) : -1;
                }
            }
            if (lines.size() < MOST_LINES && spoken.get(semicolon).getType() == FrostlakeLexer.SEMI) {
                lines.addAll(readOnPastQueries(spoken, semicolon));
            }
            return lines.size() > MOST_LINES ? lines.subList(0, MOST_LINES) : lines;
        }
        if (placement == ShowStatementPlacement.CONSTRUCT) {
            lines.add(SyntaxErrorListener.sentence(refused));
            lines.addAll(readOnInIf(spoken, at));
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(swapped ? back : refused));
        if (spoken.get(semicolon).getType() == FrostlakeLexer.SEMI) {
            final List<String> rest = BlockReadOn.linesAfter(spoken, semicolon);
            // What the rest refuses straight after a refused semicolon is not named.
            final boolean straightAfter = refused.getType() == FrostlakeLexer.SEMI && !rest.isEmpty()
                && semicolon + 1 < spoken.size()
                && rest.get(0).equals(SyntaxErrorListener.sentence(spoken.get(semicolon + 1)));
            if (!straightAfter) {
                lines.addAll(rest);
            }
        }
        return lines;
    }

    /**
     * The token live names back in a SHOW listing's scope after the token the listing was refused at, when the
     * statement stands in the outermost block's own list; null when the refused token is a name or stands anywhere
     * else, or when the scope's words settled the reading.
     *
     * @param spoken  the text's default-channel tokens
     * @param at      the index of the refused token among them
     * @return the token, or null
     */
    public static Token tokenBack(final List<Token> spoken, final int at) {
        final Token refused = spoken.get(at);
        if (refused.getType() == Token.EOF || SyntaxErrorListener.isNameLike(refused)
                || refused.getType() == FrostlakeLexer.POSITIONAL_PARAMETER
                || refused.getType() == FrostlakeLexer.RPAREN) {
            return null;
        }
        final int show = statementShow(spoken, at);
        if (show < 0) {
            return null;
        }
        int in = -1;
        for (int i = show + 1; i < at; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.IN) {
                in = i;
            }
        }
        if (in < 0) {
            return null;
        }
        return at == in + 1 ? functionListingWord(spoken, show, in) : scopeToken(spoken, in, at);
    }

    /**
     * The lines live stacks after a token refused in the scope of a SHOW in an IF's body, when {@link #tokenBack}
     * names one: the rest is read once the IF is given up ({@link BlockReadOn}), except that plain queries after the
     * statement are read on and the END after them refused — {@code … x 1; SELECT 1; END IF; END;} is '1', then the
     * END of END IF, {@code … x 1; END IF; END;} '1', then the last END (live-verified).
     *
     * @param spoken the text's default-channel tokens
     * @param at     the index of the refused token among them
     * @return the lines
     */
    public static List<String> readOnInIf(final List<Token> spoken, final int at) {
        final int semicolon = semicolonFrom(spoken, at);
        final Token end = endPastQueries(spoken, semicolon + 1, 1);
        if (end != null) {
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(end));
            return lines;
        }
        return spoken.get(semicolon).getType() == FrostlakeLexer.SEMI ? BlockReadOn.linesAfter(spoken, semicolon)
            : new ArrayList<String>();
    }

    /**
     * The lines of a WITH written as a SHOW listing's scope, {@code IN WITH x y}, in a block or a construct's body:
     * the WITH, the word after it, and then what a keyword scope's refused word stacks after the name that follows
     * ({@link KeywordScopeLines#afterName}); null when no second word follows.
     */
    private static List<String> scopeWith(final TokenStream stream, final List<Token> spoken, final int at,
                                          final ParserRuleContext ctx) {
        if (at + 2 >= spoken.size() || !SyntaxErrorListener.isNameLike(spoken.get(at + 1))
                || !SyntaxErrorListener.isNameLike(spoken.get(at + 2))) {
            return null;
        }
        final List<String> rest = KeywordScopeLines.afterName(stream, ctx, spoken.get(at + 2));
        if (rest == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
        lines.addAll(rest);
        return lines;
    }

    /** The index of a token among the spoken ones, or -1. */
    private static int indexOf(final List<Token> spoken, final Token token) {
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == token.getTokenIndex()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The index of the token the scope's reading stops at when it is read again from the token at {@code from}: past
     * a name, or past a dot and the name after it; -1 from any other token.
     */
    private static int readAgainIndex(final List<Token> spoken, final int from) {
        final Token token = spoken.get(from);
        if (token.getType() == FrostlakeLexer.DOT) {
            return from + 2 < spoken.size() && SyntaxErrorListener.isNameLike(spoken.get(from + 1)) ? from + 2 : -1;
        }
        return SyntaxErrorListener.isNameLike(token) && from + 1 < spoken.size() ? from + 1 : -1;
    }

    /** The token at {@link #readAgainIndex}, or null. */
    private static Token readAgainFrom(final List<Token> spoken, final int from) {
        final int next = readAgainIndex(spoken, from);
        return next < 0 ? null : spoken.get(next);
    }

    /**
     * What a construct's body reads on past the semicolon of a statement refused inside its scope: the token after
     * the plain queries that follow, when one query or more does — an END, or a REPEAT's UNTIL; with none, an END
     * straight after gives the construct up and the rest is read as {@link BlockReadOn} reads it, but for an END
     * FOR; anything else is no line.
     */
    private static List<String> readOnPastQueries(final List<Token> spoken, final int semicolon) {
        final List<String> lines = new ArrayList<>();
        final Token after = afterQueries(spoken, semicolon + 1, 1);
        if (after != null) {
            lines.add(SyntaxErrorListener.sentence(after));
        } else if (semicolon + 2 < spoken.size() && spoken.get(semicolon + 1).getType() == FrostlakeLexer.END
                && spoken.get(semicolon + 2).getType() != FrostlakeLexer.FOR) {
            lines.addAll(BlockReadOn.linesAfter(spoken, semicolon));
        }
        return lines;
    }

    /** The index of the SHOW opening the statement that holds the token at {@code at}, or -1. */
    private static int statementShow(final List<Token> spoken, final int at) {
        int start = at;
        while (start > 0) {
            final int type = spoken.get(start - 1).getType();
            if (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.THEN
                    || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO || type == FrostlakeLexer.LOOP
                    || type == FrostlakeLexer.REPEAT) {
                break;
            }
            start--;
        }
        if (start == at && spoken.get(at).getType() == FrostlakeLexer.SEMI) {
            return -1;
        }
        return spoken.get(start).getType() == FrostlakeLexer.SHOW ? start : -1;
    }

    /** The word after FUNCTIONS in a plain {@code SHOW FUNCTIONS} refused at the token after its IN; or null. */
    private static Token functionListingWord(final List<Token> spoken, final int show, final int in) {
        if (spoken.get(show + 1).getType() != FrostlakeLexer.FUNCTIONS || show + 2 > in) {
            return null;
        }
        return spoken.get(show + 2);
    }

    /** The token back in the scope live names after the refused one, or null when the reading was settled. */
    private static Token scopeToken(final List<Token> spoken, final int in, final int at) {
        int open = in + 2;
        if (spoken.get(in + 1).getType() == FrostlakeLexer.SCHEMA && in + 3 < at
                && spoken.get(in + 3).getType() == FrostlakeLexer.DOT) {
            open = in + 5;
        }
        if (open > at || spoken.get(at).getType() == FrostlakeLexer.SEMI
                && spoken.get(in + 1).getType() == FrostlakeLexer.APPLICATION) {
            return null;
        }
        final Token token = spoken.get(open);
        if (open == at) {
            return token;
        }
        if (token.getType() == FrostlakeLexer.LIMIT || token.getType() == FrostlakeLexer.STARTS) {
            return null;
        }
        if (token.getType() != FrostlakeLexer.DOT) {
            return open + 1 == at ? token : null;
        }
        boolean afterDot = true;
        for (int i = open + 1; i < at; i++) {
            final Token between = spoken.get(i);
            if (between.getType() == FrostlakeLexer.DOT) {
                if (afterDot) {
                    return null;
                }
                afterDot = true;
            } else if (SyntaxErrorListener.isNameLike(between)) {
                afterDot = false;
            } else {
                return null;
            }
        }
        return token;
    }

    /**
     * The lines of an IDENTIFIER() reference written after a scope word that reads no name, when the parse's fault
     * lies past it; or null.
     */
    private static List<String> identifierReference(final TokenStream stream, final List<Token> spoken, final int in,
                                                    final int at, final ShowStatementPlacement placement) {
        if (in + 2 >= at || placement == ShowStatementPlacement.OTHER || placement == ShowStatementPlacement.LOOP
                || placement == ShowStatementPlacement.BRANCH) {
            return null;
        }
        final Token reference = spoken.get(in + 2);
        final int kind = spoken.get(in + 1).getType();
        if (kind == FrostlakeLexer.DATABASE || kind == FrostlakeLexer.SCHEMA || kind == FrostlakeLexer.TABLE
                || kind == FrostlakeLexer.ACCOUNT) {
            return null;
        }
        if (reference.getType() != FrostlakeParser.KW_IDENTIFIER_REF
                && reference.getType() != FrostlakeParser.KW_IDENTIFIER_OPEN
                || !ShowScopeWords.refusesReference(stream, reference)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(reference));
        if (placement == ShowStatementPlacement.BLOCK || placement == ShowStatementPlacement.NESTED) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(in + 3)));
            final Token end = placement == ShowStatementPlacement.NESTED
                ? afterQueries(spoken, semicolonFrom(spoken, at) + 1, 0) : null;
            if (end != null) {
                lines.add(SyntaxErrorListener.sentence(end));
            }
        } else if (placement == ShowStatementPlacement.CONSTRUCT) {
            final Token next = afterTrailingWord(spoken, in + 3);
            if (next == null) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(next));
        }
        return lines;
    }

    /**
     * The token live refuses after an IDENTIFIER() reference refused in an IF's body when a word follows the
     * reference: the token after that word, or, past the statement's semicolon, the END after the queries read on
     * ({@code … IDENTIFIER('y') z; SELECT 1; END IF; …} is the END of END IF); null for any other shape.
     */
    private static Token afterTrailingWord(final List<Token> spoken, final int open) {
        if (spoken.get(open).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        int depth = 0;
        int k = open;
        for (; k < spoken.size(); k++) {
            final int type = spoken.get(k).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                break;
            } else if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return null;
            }
        }
        if (k + 2 >= spoken.size() || !SyntaxErrorListener.isNameLike(spoken.get(k + 1))) {
            return null;
        }
        k += 2;
        if (spoken.get(k).getType() != FrostlakeLexer.SEMI) {
            return spoken.get(k).getType() == Token.EOF ? null : spoken.get(k);
        }
        return endPastQueries(spoken, k + 1, 0);
    }

    /** The index of the semicolon ending the statement the token at {@code from} stands in, or the last index. */
    private static int semicolonFrom(final List<Token> spoken, final int from) {
        int k = from;
        while (k < spoken.size() - 1 && spoken.get(k).getType() != FrostlakeLexer.SEMI) {
            k++;
        }
        return k;
    }

    /**
     * The END that follows at least {@code least} plain SELECT statements read on from {@code from}, or null when
     * anything else stands there.
     */
    private static Token endPastQueries(final List<Token> spoken, final int from, final int least) {
        int queries = 0;
        int k = from;
        while (k < spoken.size() && spoken.get(k).getType() == FrostlakeLexer.SELECT) {
            while (k < spoken.size() && spoken.get(k).getType() != FrostlakeLexer.SEMI) {
                final int type = spoken.get(k).getType();
                if (type == Token.EOF || type == FrostlakeLexer.END || type == FrostlakeLexer.BEGIN
                        || type == FrostlakeLexer.CASE) {
                    return null;
                }
                k++;
            }
            queries++;
            k++;
        }
        return queries >= least && k < spoken.size() && spoken.get(k).getType() == FrostlakeLexer.END
            ? spoken.get(k) : null;
    }

    /**
     * The token after the plain SELECT statements read on from {@code from}, when at least {@code least} of them
     * stand there; null when the text ends first or a query holds a block's word.
     *
     * @param spoken the text's default-channel tokens
     * @param from   the index to read on from
     * @param least  how many queries must stand there
     * @return the token, or null
     */
    static Token afterQueries(final List<Token> spoken, final int from, final int least) {
        int queries = 0;
        int k = from;
        while (k < spoken.size() && spoken.get(k).getType() == FrostlakeLexer.SELECT) {
            while (k < spoken.size() && spoken.get(k).getType() != FrostlakeLexer.SEMI) {
                final int type = spoken.get(k).getType();
                if (type == Token.EOF || type == FrostlakeLexer.END || type == FrostlakeLexer.BEGIN
                        || type == FrostlakeLexer.CASE) {
                    return null;
                }
                k++;
            }
            queries++;
            k++;
        }
        return queries >= least && k < spoken.size() && spoken.get(k).getType() != Token.EOF ? spoken.get(k) : null;
    }

    /**
     * Where the statement the fault was met in stands.
     *
     * @param ctx the context of the statement, or of a rule inside it
     * @return the placement
     */
    public static ShowStatementPlacement placement(final ParserRuleContext ctx) {
        ParserRuleContext list = null;
        for (ParserRuleContext up = ctx; up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                list = up;
                break;
            }
        }
        if (list == null) {
            for (ParserRuleContext up = ctx; up != null; up = up.getParent()) {
                if (up instanceof FrostlakeParser.BeginEndBlockContext
                        || up instanceof FrostlakeParser.ExceptionHandlerContext) {
                    return ShowStatementPlacement.OTHER;
                }
            }
            return ShowStatementPlacement.STANDALONE;
        }
        final ParserRuleContext owner = list.getParent();
        ParserRuleContext block = owner;
        if (owner instanceof FrostlakeParser.ExceptionHandlerContext) {
            block = owner.getParent();
            while (block != null && !(block instanceof FrostlakeParser.BeginEndBlockContext)) {
                block = block.getParent();
            }
        }
        if (block instanceof FrostlakeParser.BeginEndBlockContext) {
            for (ParserRuleContext up = block.getParent(); up != null; up = up.getParent()) {
                if (up instanceof FrostlakeParser.StatementListContext) {
                    // A block of another block's own list; in a handler or in a construct's body it is read alone.
                    return owner == block && up.getParent() instanceof FrostlakeParser.BeginEndBlockContext
                        ? ShowStatementPlacement.NESTED : ShowStatementPlacement.OTHER;
                }
            }
            return ShowStatementPlacement.BLOCK;
        }
        if (owner instanceof FrostlakeParser.IfStatementContext) {
            return ShowStatementPlacement.CONSTRUCT;
        }
        if (owner instanceof FrostlakeParser.WhileStatementContext || owner instanceof FrostlakeParser.LoopStatementContext
                || owner instanceof FrostlakeParser.ForStatementContext) {
            return ShowStatementPlacement.LOOP;
        }
        if (owner instanceof FrostlakeParser.CaseStatementContext
                || owner instanceof FrostlakeParser.RepeatStatementContext) {
            return ShowStatementPlacement.BRANCH;
        }
        return ShowStatementPlacement.OTHER;
    }
}
