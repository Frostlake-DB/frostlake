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

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live stacks after the first token it refuses among a CREATE TABLE's options after its column list. Live
 * reads on past the refused token as a run of further options, each a name, its '=' and its value, and refuses again
 * wherever that reading fails (all live-verified):
 *
 * <pre>
 *   … (a INT) CLUSTER x                    'x', then the end of input where the '=' after x belongs
 *   … (a INT) CLUSTER x y                  'x', then 'y', read as x's value
 *   … (a INT) CLUSTER x y z                'x', 'y', then the end of input after the name z
 *   … (a INT) CLUSTER x (a)                'x', then the '(' no option opens
 *   … (a INT) CHANGE_TRACKING = ON x       'ON', which then opens ON COMMIT: 'x', then the end of input
 *   … (a INT) CHANGE_TRACKING = SET x      'SET', passed over: the end of input after the name x
 *   … (a INT) CALL p()                     'p', read as CALL's value, then the '('
 *   … (a INT) ON COMMIT x                  'x', then the end of input
 * </pre>
 *
 * <p>A word that misses the word its clause goes on with (CLUSTER's BY, COPY's GRANTS, ON's COMMIT) is read as an
 * option's name; a word where an option's '=' belongs is that option's value, unless an '=' follows it; a '(' is
 * refused where an option begins; and an '=' where an option begins ends the reading.
 */
final class TableTailLines {

    /** The most lines stacked after the first. */
    private static final int MOST = 3;

    private final TokenStream stream;
    private final List<Token> lines = new ArrayList<>();

    private TableTailLines(final TokenStream stream) {
        this.stream = stream;
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
    }

    /**
     * The lines stacked after the first refused token, when that token lies among the options after a CREATE TABLE's
     * column list; null otherwise, or when live stacks none.
     *
     * @param parser the parser that met the fault
     * @param first the token refused first
     * @return up to three stacked tokens, or null
     */
    static List<Token> stackedAfter(final Parser parser, final Token first) {
        if (first.getType() == Token.EOF || first.getType() == FrostlakeLexer.SEMI || first.getType() == FrostlakeLexer.EQ
                || !inTableTail(parser, first)) {
            return null;
        }
        final TableTailLines reading = new TableTailLines(parser.getInputStream());
        final Token before = reading.previous(first);
        if (before == null) {
            return null;
        }
        if (before.getType() == FrostlakeLexer.EQ) {
            reading.valueRefused(first);
        } else if (reading.awaitsWord(before)) {
            reading.clauseWordRefused(first);
        } else if (UnseparatedStatementOpener.namesProperty(before) && first.getType() != FrostlakeLexer.LPAREN) {
            // A '(' where the '=' belongs is no value, and nothing after it is read.
            reading.valueTaken(first);
        } else {
            return null;
        }
        if (reading.lines.isEmpty()) {
            return null;
        }
        return reading.lines.size() > MOST ? reading.lines.subList(0, MOST) : reading.lines;
    }

    /**
     * Whether a token stands after the column list of a CREATE TABLE the parser is reading, or of the one the
     * statement the token lies in runs straight on from.
     */
    private static boolean inTableTail(final Parser parser, final Token token) {
        FrostlakeParser.CreateStatementContext create = null;
        for (ParserRuleContext context = parser.getContext(); context != null; context = context.getParent()) {
            if (context instanceof FrostlakeParser.CreateStatementContext) {
                create = (FrostlakeParser.CreateStatementContext) context;
                break;
            }
        }
        if (create == null) {
            final FrostlakeParser.StatementContext last = UnseparatedStatementOpener.runOnStatement(parser, token);
            create = last == null || last.ddlStatement() == null ? null : last.ddlStatement().createStatement();
        }
        return create != null && create.TABLE() != null && create.columnList() != null
            && create.columnList().getStop() != null && create.DYNAMIC() == null && create.EVENT() == null
            && create.TEMPLATE() == null && create.selectStatement() == null
            && token.getTokenIndex() > create.columnList().getStop().getTokenIndex() + 1;
    }

    /** A value's place held a token no value is: ON opens its clause, a reserved word is passed over. */
    private void valueRefused(final Token refused) {
        if (refused.getType() == FrostlakeLexer.ON) {
            clauseRead(refused);
        } else if (UnseparatedStatementOpener.refusedAsPropertyName(refused)) {
            optionsFrom(next(refused));
        }
    }

    /** Reads on after a clause's keyword, which wants a given word next. */
    private void clauseRead(final Token keyword) {
        final Token word = next(keyword);
        if (wanted(keyword, word)) {
            if (word.getType() == FrostlakeLexer.GRANTS || word.getType() == FrostlakeLexer.ROWS) {
                optionsFrom(next(word));
            } else if (word.getType() != FrostlakeLexer.LPAREN) {
                clauseRead(word);
            }
            return;
        }
        if (add(word)) {
            clauseWordRefused(word);
        }
    }

    /** A clause's word was refused: a name begins an option, a '(' is passed over. */
    private void clauseWordRefused(final Token refused) {
        if (refused.getType() == FrostlakeLexer.LPAREN) {
            optionsFrom(next(refused));
        } else if (isName(refused)) {
            nameRead(refused);
        }
    }

    /** An option's name was read: what follows it must be its '='. */
    private void nameRead(final Token name) {
        final Token after = next(name);
        if (after.getType() == FrostlakeLexer.EQ || !add(after)) {
            return;
        }
        if (after.getType() != Token.EOF && after.getType() != FrostlakeLexer.SEMI
                && after.getType() != FrostlakeLexer.LPAREN) {
            valueTaken(after);
        }
    }

    /** A token refused where an option's '=' belongs is read as the option's value, unless an '=' follows it. */
    private void valueTaken(final Token value) {
        final Token after = next(value);
        if (after.getType() == FrostlakeLexer.EQ) {
            return;
        }
        if (value.getType() == FrostlakeLexer.ON) {
            clauseRead(value);
        } else {
            optionsFrom(after);
        }
    }

    /** Reads the options from where one may begin. */
    private void optionsFrom(final Token token) {
        final int type = token.getType();
        if (type == Token.EOF || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.EQ) {
            return;
        }
        if (type == FrostlakeLexer.LPAREN) {
            add(token);
        } else if (type == FrostlakeLexer.ON
                || (type == FrostlakeLexer.CLUSTER || type == FrostlakeLexer.COPY)
                    && next(token).getType() != FrostlakeLexer.EQ) {
            clauseRead(token);
        } else if (isName(token)) {
            nameRead(token);
        }
    }

    /** Whether a token is a clause's keyword that wants a given word after it. */
    private boolean awaitsWord(final Token keyword) {
        final int type = keyword.getType();
        if (type == FrostlakeLexer.CLUSTER || type == FrostlakeLexer.COPY || type == FrostlakeLexer.ON) {
            return true;
        }
        final Token before = previous(keyword);
        final int beforeType = before == null ? Token.INVALID_TYPE : before.getType();
        return type == FrostlakeLexer.BY && beforeType == FrostlakeLexer.CLUSTER
            || type == FrostlakeLexer.COMMIT && beforeType == FrostlakeLexer.ON
            || isCommitAction(keyword) && beforeType == FrostlakeLexer.COMMIT;
    }

    /** Whether {@code word} is the word the clause keyword before it wants. */
    private static boolean wanted(final Token keyword, final Token word) {
        switch (keyword.getType()) {
            case FrostlakeLexer.CLUSTER:
                return word.getType() == FrostlakeLexer.BY;
            case FrostlakeLexer.BY:
                return word.getType() == FrostlakeLexer.LPAREN;
            case FrostlakeLexer.COPY:
                return word.getType() == FrostlakeLexer.GRANTS;
            case FrostlakeLexer.ON:
                return word.getType() == FrostlakeLexer.COMMIT;
            case FrostlakeLexer.COMMIT:
                return isCommitAction(word);
            default:
                return isCommitAction(keyword) && word.getType() == FrostlakeLexer.ROWS;
        }
    }

    /** Whether a word is what an ON COMMIT does: PRESERVE or DELETE. */
    private static boolean isCommitAction(final Token word) {
        return word.getType() == FrostlakeLexer.DELETE || UnmodelledKindWords.is(word, "PRESERVE");
    }

    /** Whether live reads a token as an option's name. */
    private static boolean isName(final Token token) {
        return UnseparatedStatementOpener.namesProperty(token);
    }

    /** Adds a stacked line; false once the most a report holds is reached. */
    private boolean add(final Token token) {
        lines.add(token);
        return lines.size() < MOST + 1;
    }

    private Token next(final Token token) {
        int i = token.getTokenIndex() + 1;
        while (i < stream.size() - 1 && stream.get(i).getChannel() != Token.DEFAULT_CHANNEL) {
            i++;
        }
        return stream.get(Math.min(i, stream.size() - 1));
    }

    private Token previous(final Token token) {
        for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
