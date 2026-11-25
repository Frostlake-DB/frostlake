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
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for an ALTER TABLE, VIEW, SCHEMA or DATABASE whose SET TAG or UNSET TAG does not read
 * whole (all live-verified).
 *
 * <p>A table and a view read the tag list as items {@code name = 'value'} joined by commas, and tell one line. A
 * fault in a later item's name or '=' is told at the comma before the item, and a token after a list that reads whole
 * is told where it stands. A table tells any other fault where it stands; a view tells the word TAG for a fault in
 * its first item and for a value missing anywhere:
 *
 * <pre>
 *   ALTER TABLE t1 SET TAG tg1 = 'x' COMMENT = 'c'     'COMMENT'
 *   ALTER TABLE t1 SET TAG tg1 = 'x', tg1              ','
 *   ALTER TABLE t1 SET TAG tg1 = 'x', tg1 =            '&lt;EOF&gt;'
 *   ALTER VIEW v1 SET TAG tg1 'x'                      'TAG'
 *   ALTER VIEW v1 SET TAG tg1 = 'x', tg1 =             'TAG'
 * </pre>
 *
 * <p>A database tells the word TAG alone. A schema reads TAG as the name of a property it does not have: it tells the
 * TAG and the word after it, and an UNSET nothing more. A SET then reads past the first item's value and goes on as
 * its property list does — each later value is told, a comma that no name follows tells the token after it, a later
 * name no '=' follows tells the token after it, and a property no comma follows ends the reading:
 *
 * <pre>
 *   ALTER SCHEMA s SET TAG tg1 = 'x' COMMENT = 'c'            'TAG', 'tg1'
 *   ALTER SCHEMA s SET TAG tg1 'x'                            'TAG', 'tg1', ''x''
 *   ALTER SCHEMA s SET TAG tg1 = 'x', tg2 = 'y',              'TAG', 'tg1', ''y'', '&lt;EOF&gt;'
 *   ALTER SCHEMA s SET TAG tg1 = 'x', tg2                     'TAG', 'tg1', '&lt;EOF&gt;'
 * </pre>
 *
 * <p>A table's or a view's value other than a string is left to the parse's own lines.
 */
public final class TagActionLines {

    private TagActionLines() {
    }

    /**
     * The lines for a text whose first fault stands after the TAG of such an action, or null for any other text.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the first fault, negative for the end of input
     * @param firstOnly  whether only the first line is wanted
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final boolean firstOnly) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            final Token token = spoken.get(i);
            if (faultIndex < 0 ? token.getType() == Token.EOF : token.getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        // A refusal of the whole action may stand at its SET or UNSET.
        int tag = fault >= 0 && fault + 1 < spoken.size() && spoken.get(fault + 1).getType() == FrostlakeLexer.TAG
            ? fault + 1 : -1;
        for (int i = fault; i >= 0 && tag < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.TAG) {
                tag = i;
            }
        }
        if (tag < 4) {
            return null;
        }
        final int kind = alteredKind(spoken, tag);
        final boolean unset = spoken.get(tag - 1).getType() == FrostlakeLexer.UNSET;
        final List<String> told;
        if (kind == FrostlakeLexer.DATABASE) {
            told = Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(tag)));
        } else if (kind == FrostlakeLexer.SCHEMA) {
            told = schemaLines(spoken, tag, unset);
        } else if ((kind == FrostlakeLexer.TABLE || kind == FrostlakeLexer.VIEW) && !unset) {
            final Token line = listLine(spoken, tag, kind == FrostlakeLexer.VIEW);
            told = line == null ? null : Collections.singletonList(SyntaxErrorListener.sentence(line));
        } else {
            told = null;
        }
        if (told == null || told.isEmpty()) {
            return null;
        }
        return firstOnly ? Collections.singletonList(told.get(0)) : told;
    }

    /**
     * The token a view's SET TAG is refused at when it parses as a list of the view's properties, or null for a list
     * this reading leaves alone.
     *
     * @param written the action's tokens from its TAG on, the end of the statement left out
     * @return the token, or null
     */
    public static Token viewListFault(final List<Token> written) {
        final List<Token> spoken = new ArrayList<>(written);
        spoken.add(new CommonToken(Token.EOF));
        return listLine(spoken, 0, true);
    }

    /** The token a table's or a view's tag list is refused at, or null for a list this reading leaves alone. */
    private static Token listLine(final List<Token> spoken, final int tag, final boolean view) {
        int i = tag + 1;
        Token comma = null;
        while (i < spoken.size()) {
            if (!SyntaxErrorListener.isIdentifierToken(spoken.get(i))) {
                if (isWord(spoken.get(i))) {
                    return null;
                }
                return itemFault(spoken, tag, comma, i, view);
            }
            i++;
            while (i + 1 < spoken.size() && spoken.get(i).getType() == FrostlakeLexer.DOT) {
                if (!SyntaxErrorListener.isIdentifierToken(spoken.get(i + 1))) {
                    return null;
                }
                i += 2;
            }
            if (spoken.get(i).getType() != FrostlakeLexer.EQ) {
                return itemFault(spoken, tag, comma, i, view);
            }
            final Token value = spoken.get(i + 1);
            if (ends(value)) {
                return view ? spoken.get(tag) : value;
            }
            if (value.getType() != FrostlakeLexer.STRING_LITERAL) {
                return null;
            }
            final Token after = spoken.get(i + 2);
            if (after.getType() != FrostlakeLexer.COMMA) {
                return ends(after) ? null : after;
            }
            comma = after;
            i += 3;
        }
        return null;
    }

    /** The token a fault in an item's name or '=' is told at. */
    private static Token itemFault(final List<Token> spoken, final int tag, final Token comma, final int at,
                                   final boolean view) {
        if (comma != null) {
            return comma;
        }
        return view ? spoken.get(tag) : spoken.get(at);
    }

    /** A schema's lines, or null for a shape this reading leaves alone. */
    private static List<String> schemaLines(final List<Token> spoken, final int tag, final boolean unset) {
        final Token word = spoken.get(tag + 1);
        if (ends(word) || spoken.get(tag + 2).getType() == FrostlakeLexer.DOT) {
            return null;
        }
        final List<String> told = new ArrayList<>();
        told.add(SyntaxErrorListener.sentence(spoken.get(tag)));
        told.add(SyntaxErrorListener.sentence(word));
        if (unset) {
            return told;
        }
        int i = tag + 2;
        boolean first = true;
        while (i < spoken.size()) {
            final Token eq = spoken.get(i);
            if (eq.getType() != FrostlakeLexer.EQ) {
                if (!first || !ends(eq)) {
                    told.add(SyntaxErrorListener.sentence(eq));
                }
                return told;
            }
            final Token value = spoken.get(i + 1);
            if (ends(value)) {
                if (!first) {
                    told.add(SyntaxErrorListener.sentence(value));
                }
                return told;
            }
            if (!first) {
                told.add(SyntaxErrorListener.sentence(value));
            }
            final int comma = i + 2;
            if (comma >= spoken.size() || spoken.get(comma).getType() != FrostlakeLexer.COMMA) {
                return told;
            }
            final Token next = spoken.get(comma + 1);
            if (ends(next) || !isWord(next)) {
                told.add(SyntaxErrorListener.sentence(next));
                return told;
            }
            first = false;
            i = comma + 2;
        }
        return told;
    }

    /**
     * The kind of object ALTER [IF EXISTS] name SET|UNSET TAG alters when the TAG at {@code tag} stands so, else -1.
     */
    private static int alteredKind(final List<Token> spoken, final int tag) {
        final int action = spoken.get(tag - 1).getType();
        if (action != FrostlakeLexer.SET && action != FrostlakeLexer.UNSET) {
            return -1;
        }
        int i = tag - 2;
        if (!SyntaxErrorListener.isIdentifierToken(spoken.get(i))) {
            return -1;
        }
        while (i >= 2 && spoken.get(i - 1).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isIdentifierToken(spoken.get(i - 2))) {
            i -= 2;
        }
        i--;
        if (i >= 1 && spoken.get(i).getType() == FrostlakeLexer.EXISTS
                && spoken.get(i - 1).getType() == FrostlakeLexer.IF) {
            i -= 2;
        }
        if (i < 1 || spoken.get(i - 1).getType() != FrostlakeLexer.ALTER) {
            return -1;
        }
        return spoken.get(i).getType();
    }

    private static boolean ends(final Token token) {
        return token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI;
    }

    /** Whether the token is a word: a plain or a quoted identifier, or a keyword. */
    private static boolean isWord(final Token token) {
        if (SyntaxErrorListener.isIdentifierToken(token)) {
            return true;
        }
        final String text = token.getText();
        return token.getType() != Token.EOF && text != null && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
    }
}
