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
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a scripting block whose faults are object constants with a key that is no text.
 *
 * <p>A plain assignment, {@code m := <value>}, is refused at the first token of its value. The account then reads on
 * from there: a ';' ends the statement soundly; a word that may be a name is taken, and the token after it - past any
 * ';' - is refused as well, which ends the report (all live-verified):
 *
 * <pre>
 *   m := {1: 'a'};                           '{'
 *   m := {1: 'a'}::MAP(NUMBER, VARCHAR);     '{', then the '(' after MAP
 *   m := {1: 'a'}::VARIANT; RETURN m;        '{', then 'RETURN'
 *   m := {TRUE: 'a'};                        '{', then the ':' after TRUE
 *   m := {1: x};                             '{', then the '}' after x
 *   m := 1 + {1: 'a'};                       '1'
 *   m := {1: 'a'}; m := {2: 'b'};            '{', then the second '{'
 * </pre>
 *
 * <p>A reserved word is no such word: {@code m := {1: 'a'} FROM t; RETURN m;} names the RETURN after {@code t;}. A
 * CALL that names no procedure is one: {@code m := {1: 1} CALL; RETURN m;} names the RETURN, {@code CALL (1)} its
 * '('. A value that opens with a parenthesis is read by {@link BlockParenthesizedConstantLines}.
 *
 * <p>Anywhere else in the block - a LET, a DEFAULT, a RETURN, a statement of its own - the constant is read as a
 * query reads it (see {@link ObjectConstantKeyLines}). A query in parentheses that is a LET's, a DEFAULT's or a
 * RETURN's value is refused at its SELECT first, the constant's values told after it: {@code RETURN (SELECT {1:
 * 'a'})} is the SELECT, then ''a''.
 *
 * <p>Once a statement holding such a constant has ended soundly, the block's other statements are read on and
 * their own faults told in their places: {@code m := {1: 'a'}; RETURN 1 +;} is the '{', then the ';' after the
 * '+'.
 */
public final class BlockObjectConstantLines {

    private BlockObjectConstantLines() {
    }

    /**
     * The lines of a block's text whose first fault is such a constant's key, for a text read outside a parse.
     *
     * @param sql the block's text
     * @return the lines, or null when the text is no block or its first fault is no such key
     */
    public static List<String> linesOf(final String sql) {
        final String first = sql == null ? null : SyntaxErrorListener.firstLineReportedFor(sql);
        if (first == null) {
            return null;
        }
        final List<Token> spoken = BlockReadOn.spoken(sql);
        final int fault = nextFault(spoken, 0);
        final List<String> lines = fault < 0 ? null : read(sql, spoken, fault);
        // The parse's first line is the key's, or already the one read here.
        return lines == null || !first.equals(SyntaxErrorListener.sentence(spoken.get(fault)))
            && !first.equals(lines.get(0)) ? null : lines;
    }

    /**
     * The lines of a block's parse whose first fault is such a constant's key.
     *
     * @param sql          the text parsed
     * @param spoken       its default-channel tokens, end of input included
     * @param faultIndexes the token indexes of the tokens the parse's lines name, in order
     * @return the lines, or null when the text is no block or the first fault is no key of an object constant
     */
    static List<String> lines(final String sql, final List<Token> spoken, final List<Integer> faultIndexes) {
        if (faultIndexes.isEmpty()) {
            return null;
        }
        // The faults after the first are this parse's own way on from it: the account's are read from the tokens.
        final int faultIndex = faultIndexes.get(0).intValue();
        int first = -1;
        for (int i = 0; i < spoken.size() && first < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                first = i;
            }
        }
        if (first < 0) {
            return null;
        }
        return read(sql, spoken, keyOf(spoken, first) >= 0 ? first : faultInValueQuery(spoken, first));
    }

    /**
     * The constant's fault a parse that stops earlier in a query in parentheses - a LET's, a DEFAULT's or a RETURN's
     * value - leads to: the first constant's fault when it stands in that query and the parse's own first fault in
     * the query's bracket or after it, else {@code first}.
     */
    private static int faultInValueQuery(final List<Token> spoken, final int first) {
        final int constant = nextFault(spoken, 0);
        if (constant < first) {
            return first;
        }
        final int paren = valueQuery(spoken, openingBrace(spoken, keyOf(spoken, constant)));
        return paren >= 0 && paren <= first ? constant : first;
    }

    /**
     * The index of the '(' of the query the brace at {@code open} stands directly in, where that query is a LET's
     * value - of a type other than RESULTSET - a DEFAULT's or a RETURN's; -1 for any other brace.
     */
    private static int valueQuery(final List<Token> spoken, final int open) {
        final int paren = parenOf(spoken, open);
        if (paren < 2 || spoken.get(paren + 1).getType() != FrostlakeLexer.SELECT) {
            return -1;
        }
        final int before = spoken.get(paren - 1).getType();
        return before == FrostlakeLexer.COLON_EQ && spoken.get(paren - 2).getType() != FrostlakeLexer.RESULTSET
            || before == FrostlakeLexer.DEFAULT || before == FrostlakeLexer.RETURN ? paren : -1;
    }

    /** The lines from the fault at {@code first} on, or null when the text is no block or the fault no key's. */
    private static List<String> read(final String sql, final List<Token> spoken, final int first) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.BEGIN
                && spoken.get(0).getType() != FrostlakeLexer.DECLARE || keyOf(spoken, first) < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        // The constants read, each a span of tokens, the first line it tells and the end of its lines; the rest of
        // the text is read with each written as NULL.
        final List<int[]> read = new ArrayList<>();
        final char[] patched = sql.toCharArray();
        int fault = first;
        int resume = -1;
        while (fault >= 0) {
            final int open = openingBrace(spoken, keyOf(spoken, fault));
            final int assigned = BlockAssignmentValueLines.assignmentBefore(spoken, open);
            final int told = lines.size();
            final int from;
            final int until;
            if (assigned >= 0 && spoken.get(assigned + 1).getType() == FrostlakeLexer.LPAREN) {
                from = assigned + 1;
                resume = BlockParenthesizedConstantLines.read(sql, spoken, assigned + 1, open, fault, lines);
                if (resume == BlockParenthesizedConstantLines.UNMODELLED) {
                    // A value in parentheses of another shape is left to the parse's own lines.
                    return lines.isEmpty() ? null : withLaterFaults(sql, spoken, patched, read, lines, false);
                }
                until = resume < 0 ? spoken.size() - 1 : resume - 1;
            } else if (assigned >= 0) {
                from = assigned + 1;
                lines.add(SyntaxErrorListener.sentence(spoken.get(assigned + 1)));
                resume = BlockAssignmentValueLines.readOn(spoken, assigned + 2, lines, true);
                until = resume < 0 ? spoken.size() - 1 : resume - 1;
            } else {
                final List<String> asQuery = subqueryValueLines(sql, spoken, open, fault);
                if (asQuery == null) {
                    return lines.isEmpty() ? null : withLaterFaults(sql, spoken, patched, read, lines, false);
                }
                lines.addAll(asQuery);
                from = open;
                until = closing(spoken, open) + 1;
                resume = until;
            }
            read.add(new int[] {from, until, told, lines.size()});
            if (resume >= 0) {
                writeNull(spoken, patched, from, until);
            }
            fault = resume < 0 ? -1 : nextFault(spoken, resume);
        }
        return withLaterFaults(sql, spoken, patched, read, lines, resume >= 0);
    }

    /**
     * The lines of a constant read as a query reads it: in a query in parentheses that is a value (see
     * {@link #valueQuery}) the account refuses the query's SELECT first, and tells the constant's values from its first
     * pair when it is the query's first item, its key and values from the faulty pair otherwise.
     */
    private static List<String> subqueryValueLines(final String sql, final List<Token> spoken, final int open,
                                                   final int fault) {
        final int paren = valueQuery(spoken, open);
        if (paren < 0) {
            return ObjectConstantKeyLines.lines(sql, spoken, spoken.get(fault).getTokenIndex());
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(paren + 1)));
        if (open == paren + 2) {
            ObjectConstantValueLines.addValues(spoken, open, open, lines);
            return lines;
        }
        final List<String> asQuery = ObjectConstantKeyLines.lines(sql, spoken, spoken.get(fault).getTokenIndex());
        if (asQuery == null) {
            return null;
        }
        lines.addAll(asQuery);
        return lines;
    }

    /** The index of the '(' the token at {@code at} stands directly in, or -1 when another bracket is between. */
    private static int parenOf(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.LBRACE) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** Writes the tokens from {@code from} up to {@code until} as NULL, every other character a space. */
    private static void writeNull(final List<Token> spoken, final char[] patched, final int from, final int until) {
        final int start = spoken.get(from).getStartIndex();
        final int stop = spoken.get(until - 1).getStopIndex();
        if (start < 0 || stop >= patched.length || stop - start < 3) {
            return;
        }
        for (int i = start; i <= stop; i++) {
            if (patched[i] != '\n' && patched[i] != '\r') {
                patched[i] = ' ';
            }
        }
        patched[start] = 'N';
        patched[start + 1] = 'U';
        patched[start + 2] = 'L';
        patched[start + 3] = 'L';
    }

    /**
     * The constants' lines with the faults of the statements around them: the text with each constant read written
     * as NULL is parsed, and its lines that stand between two constants' statements - after the last one too while
     * its statement ended soundly - are told in their place.
     */
    private static List<String> withLaterFaults(final String sql, final List<Token> spoken, final char[] patched,
                                                final List<int[]> read, final List<String> lines,
                                                final boolean readsOn) {
        if (read.isEmpty() || sql.length() != sql.codePointCount(0, sql.length())
                || read.size() == 1 && !readsOn) {
            return lines;
        }
        final List<String> later = SyntaxErrorListener.linesReportedForBlock(new String(patched));
        if (later.isEmpty()) {
            return lines;
        }
        final List<String> merged = new ArrayList<>();
        for (int g = 0; g < read.size(); g++) {
            final int[] constant = read.get(g);
            if (g > 0) {
                addBetween(later, spoken.get(read.get(g - 1)[1] - 1), spoken.get(constant[0]), merged);
            }
            merged.addAll(lines.subList(constant[2], constant[3]));
        }
        if (readsOn) {
            addBetween(later, spoken.get(read.get(read.size() - 1)[1] - 1), null, merged);
        }
        return merged;
    }

    /** Adds the lines that stand after the token {@code after} and before the token {@code before}, if any. */
    private static void addBetween(final List<String> later, final Token after, final Token before,
                                   final List<String> merged) {
        final int[] low = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(after));
        final int[] high = before == null ? null
            : SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(before));
        for (final String line : later) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at != null && low != null && compare(at, low) > 0 && (high == null || compare(at, high) < 0)) {
                merged.add(line);
            }
        }
    }

    private static int compare(final int[] a, final int[] b) {
        return a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
    }

    /**
     * The key the fault at {@code fault} belongs to - the fault itself, or the token before a ':' the fault is - or -1
     * when the fault is no key of an object constant.
     */
    private static int keyOf(final List<Token> spoken, final int fault) {
        final int key = fault > 0 && spoken.get(fault).getType() == FrostlakeLexer.COLON ? fault - 1 : fault;
        // A signed number is a key of two tokens.
        final int last = spoken.get(key).getType() == FrostlakeLexer.MINUS ? key + 1 : key;
        if (key < 1 || last + 1 >= spoken.size() || spoken.get(last + 1).getType() != FrostlakeLexer.COLON
                || spoken.get(key - 1).getType() != FrostlakeLexer.LBRACE
                    && spoken.get(key - 1).getType() != FrostlakeLexer.COMMA
                || directlyInBrace(spoken, key) < 0) {
            return -1;
        }
        return key;
    }

    /** The index of the brace the token at {@code at} stands directly inside, no other bracket between, or -1. */
    private static int directlyInBrace(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (type == FrostlakeLexer.LBRACE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The index of the innermost brace open at the token at {@code at}, whatever else is open, or -1. */
    private static int openingBrace(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.LBRACE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The index of the brace that closes the one at {@code open}, or the last token's when none does. */
    private static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.RBRACE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return spoken.size() - 1;
    }

    /**
     * The fault of the first object constant from {@code from} on that has a key which is no text: the key, or the
     * ':' after a key that is a word. -1 when there is none.
     */
    private static int nextFault(final List<Token> spoken, final int from) {
        for (int i = Math.max(from, 1); i + 1 < spoken.size(); i++) {
            final int before = spoken.get(i - 1).getType();
            final int last = spoken.get(i).getType() == FrostlakeLexer.MINUS ? i + 1 : i;
            if (before != FrostlakeLexer.LBRACE && before != FrostlakeLexer.COMMA
                    || last + 1 >= spoken.size() || spoken.get(last + 1).getType() != FrostlakeLexer.COLON
                    || spoken.get(i).getType() == FrostlakeLexer.STRING_LITERAL
                    || directlyInBrace(spoken, i) < 0) {
                continue;
            }
            return BlockAssignmentValueLines.takenForName(spoken.get(i)) ? i + 1 : i;
        }
        return -1;
    }
}
