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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The line a DESCRIBE stacks for its tail after a fault inside the signature it reads after the object's name. The
 * account reads the rest of the statement on past the signature's closing parenthesis and reports that tail's first
 * fault too: {@code DESCRIBE TABLE t1 (a b) x} is 'b', then '&lt;EOF&gt;' — the x read as a property's name — and
 * {@code DESCRIBE TABLE IDENTIFIER(UPPER('t1')) x} the string, then '&lt;EOF&gt;'; a tail that reads whole adds
 * nothing ({@code DESCRIBE TABLE t1 ((a)) TYPE = COLUMNS}). A DROP stacks no such line (all live-verified). A DROP or
 * DESCRIBE of a table or a view that {@link SignatureRecovery} reads whole is refused with every line that reading
 * reports instead.
 */
public final class SignatureTailLine {

    /** The statement that stands in for one the account reads whole, while the rest of a request is read again. */
    private static final String STAND_IN = "SELECT 1";

    /** How many more statements read whole a request's text is read again for, past the ones its parse read. */
    private static final int MAX_LATER_READINGS = 8;

    private SignatureTailLine() {
    }

    /**
     * Refuse a DESCRIBE whose every reported fault lies inside its signature with the tail's first fault stacked.
     *
     * @param script   the parsed script
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final FrostlakeParser.SqlScriptContext script, final String sql,
                                     final SyntaxErrorListener listener) {
        if (listener.unknownTypes() != null) {
            // The parse's faults are words where types belong, and the text reads whole once they are types: a
            // block's CREATE TABLE among them is refused for the type, as the statement on its own is (see
            // UnknownTypeWords).
            return;
        }
        requireSignatureReading(script, sql, listener);
        if (script == null || sql == null || !listener.hasErrors() || script.flowChain().size() != 1
                || script.flowChain().get(0).statement().size() != 1) {
            return;
        }
        final FrostlakeParser.DescribeStatementContext describe =
            script.flowChain().get(0).statement().get(0).describeStatement();
        final FrostlakeParser.ObjectSignatureContext signature = describe == null ? null : describe.objectSignature();
        if (signature == null || signature.RPAREN() == null || signature.LPAREN() == null) {
            return;
        }
        final Token open = signature.LPAREN().getSymbol();
        final Token close = signature.RPAREN().getSymbol();
        final List<String> reported = listener.reportedSyntaxLines();
        if (reported.isEmpty()) {
            return;
        }
        final int[] from = LeadingCommentOffset.rebase(open.getLine(), open.getCharPositionInLine());
        final int[] to = LeadingCommentOffset.rebase(close.getLine(), close.getCharPositionInLine());
        for (final String line : reported) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at == null || before(at, from) || before(to, at)) {
                return;
            }
        }
        final StringBuilder blanked = new StringBuilder(sql);
        for (int i = open.getStartIndex(); i <= close.getStopIndex() && i < blanked.length(); i++) {
            if (blanked.charAt(i) != '\n') {
                blanked.setCharAt(i, ' ');
            }
        }
        final List<String> tail = SyntaxErrorListener.linesReportedFor(blanked.toString(), true);
        final String tailLine = tail.isEmpty() ? bareProperty(describe) : tail.get(0);
        final int[] tailAt = tailLine == null ? null : SyntaxErrorListener.sentenceCoordinates(tailLine);
        if (tailAt == null || !before(to, tailAt)) {
            return;
        }
        final List<String> lines = new ArrayList<>(reported);
        lines.add(tailLine);
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /**
     * Refuse a DROP or a DESCRIBE of a table or a view with every line the account reports for its signature and the
     * rest of the statement (see {@link SignatureRecovery}) — whether this parse refused the statement or read it
     * whole — unless a fault stands before the signature or the text holds a shape that reading does not model.
     */
    private static void requireSignatureReading(final FrostlakeParser.SqlScriptContext script, final String sql,
                                                final SyntaxErrorListener listener) {
        requireStatementReadings(script, sql, listener);
        final SignatureRecovery reading = SignatureRecovery.of(script, sql);
        final List<String> lines = reading == null ? null : reading.lines();
        if (lines == null || faultBefore(listener, reading.open())) {
            return;
        }
        if (lines.isEmpty()) {
            // The account reads the statement whole. Where this grammar does not — a stage reference of the account's
            // own spelling, a backticked name — the properties are judged as the account judges them.
            final String refused = listener.hasErrors() ? reading.propertyRefusal() : null;
            if (refused != null) {
                throw new RuntimeException(SqlCompilationError.of(refused));
            }
            return;
        }
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /**
     * Refuse a script of several statements in which a DROP's or a DESCRIBE's signature is faulty: each such
     * statement with the lines the account reports for it, read on its own, and every other statement with its own
     * lines, as if the faulty ones were not there — {@code DESCRIBE TABLE t ((a)); SELECT 1 x y} is the '(', the ')'
     * and the y (live-verified). A statement the account reads whole where this parse refused something in it — a
     * stage reference of the account's own spelling — is left out the same way: {@code DESCRIBE TABLE t1 (a) x =
     * @s!=x; SELECT 1 x y} is the y alone (live-verified).
     */
    private static void requireStatementReadings(final FrostlakeParser.SqlScriptContext script, final String sql,
                                                 final SyntaxErrorListener listener) {
        final List<String> lines = new ArrayList<>();
        // The lines of one reading keep the order the account reports them in: only statements are put in order.
        final List<int[]> places = new ArrayList<>();
        char[] text = null;
        boolean leftOut = false;
        // The first statement the account reads whole, which a request of several fails at.
        SignatureRecovery readWhole = null;
        // Where each statement the account reads whole stands: its first character and its last before its ';'.
        final List<int[]> readWholeAt = new ArrayList<>();
        for (final SignatureRecovery reading : SignatureRecovery.ofStatements(script, sql)) {
            final List<String> faults = reading.lines();
            if (faults != null && faults.isEmpty() && refusedWithin(listener, reading)) {
                if (readWhole == null) {
                    readWhole = reading;
                }
                readWholeAt.add(standingOf(reading));
                if (text == null) {
                    text = sql.toCharArray();
                }
                for (int i = reading.ownStatement().getStart().getStartIndex(); i <= reading.last().getStopIndex()
                        && i < text.length; i++) {
                    if (text[i] != '\n') {
                        text[i] = ' ';
                    }
                }
                leftOut = true;
                continue;
            }
            if (faults == null || faults.isEmpty()) {
                continue;
            }
            lines.addAll(faults);
            final int[] opens = LeadingCommentOffset.rebase(reading.open().getLine(),
                reading.open().getCharPositionInLine());
            for (int i = 0; i < faults.size(); i++) {
                places.add(opens);
            }
            if (reading.readsABlockStatement()) {
                if (reading.givesConstructUp() && reading.last().getType() == FrostlakeLexer.SEMI) {
                    final List<Token> spoken = BlockReadOn.spoken(sql);
                    final int semicolon = BlockReadOn.indexAt(spoken, reading.last().getStartIndex());
                    if (semicolon >= 0) {
                        lines.addAll(BlockReadOn.linesAfter(spoken, semicolon));
                    }
                } else if (reading.nameDueAtItsEnd()) {
                    // A name due at the ';' reads the next statement's first word as that name.
                    final List<Token> spoken = BlockReadOn.spoken(sql);
                    final int semicolon = BlockReadOn.indexAt(spoken, reading.last().getStartIndex());
                    if (semicolon >= 0) {
                        lines.addAll(SignatureRecovery.namedPastItsEnd(spoken, semicolon));
                    }
                } else if (reading.ownStatement().getStart().getType() == FrostlakeLexer.CREATE) {
                    lines.addAll(laterStatementLines(sql, reading));
                }
                // A block is refused for its first faulty statement.
                throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
            }
            if (text == null) {
                text = sql.toCharArray();
            }
            // A statement whose own end was refused takes the rest of the text with it.
            final int stop = reading.readsNoFurther() ? text.length - 1 : reading.last().getStopIndex();
            for (int i = reading.ownStatement().getStart().getStartIndex(); i <= stop && i < text.length; i++) {
                if (text[i] != '\n') {
                    text[i] = ' ';
                }
            }
            if (reading.readsNoFurther()) {
                break;
            }
        }
        if (lines.isEmpty() && !leftOut) {
            return;
        }
        final boolean readingFaults = !lines.isEmpty();
        if (SignatureRecovery.holdsAStatement(new String(text))) {
            for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
                lines.add(line);
                places.add(SyntaxErrorListener.sentenceCoordinates(line));
            }
        }
        final List<String> ordered = new ArrayList<>();
        final List<int[]> orderedAt = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final int[] at = places.get(i);
            int place = ordered.size();
            while (at != null && place > 0) {
                final int[] earlier = orderedAt.get(place - 1);
                if (earlier == null || !before(at, earlier)) {
                    break;
                }
                place--;
            }
            ordered.add(place, lines.get(i));
            orderedAt.add(place, at);
        }
        if (ordered.isEmpty() || readWhole != null && !readingFaults && restReadsWhole(sql, readWholeAt)) {
            noteWholeStatement(readWhole, text, sql, listener);
            return;
        }
        final List<String> told = DescribeBracketWordLines.withoutTheTokenAfter(BlockReadOn.spoken(sql), ordered);
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", told)), told, sql);
    }

    /**
     * The lines of a block's statements after a CREATE whose words run on: the CREATE is given up at its semicolon and
     * the statements after it read on as the block's own, their faults reported after its (live-verified: {@code BEGIN
     * CREATE TABLE t (x INT) y z; SELECT 1 x y; END} is the z, the y and the END; with {@code SELECT 1 x} in its place,
     * the z alone). A CREATE the block's END follows reads on to nothing.
     */
    private static List<String> laterStatementLines(final String sql, final SignatureRecovery reading) {
        int semicolon = -1;
        Token next = null;
        for (final Token token : BlockReadOn.spoken(sql)) {
            if (semicolon < 0 && token.getType() == FrostlakeLexer.SEMI
                    && token.getStartIndex() >= reading.last().getStartIndex()) {
                semicolon = token.getStopIndex();
            } else if (semicolon >= 0 && next == null) {
                next = token;
            }
        }
        if (semicolon < 0 || next == null || next.getType() == FrostlakeLexer.END) {
            return new ArrayList<>();
        }
        final char[] text = sql.toCharArray();
        for (int i = reading.ownStatement().getStart().getStartIndex(); i <= semicolon && i < text.length; i++) {
            if (text[i] != '\n') {
                text[i] = ' ';
            }
        }
        return SyntaxErrorListener.linesReportedFor(new String(text), true);
    }

    /**
     * Note, on the refusal the parse is to throw, a request of several statements whose only refused one is a
     * statement the account reads whole: the account runs the statements before it and refuses it as it compiles
     * (see {@link WholeStatementReading}).
     */
    private static void noteWholeStatement(final SignatureRecovery readWhole, final char[] text, final String sql,
                                           final SyntaxErrorListener listener) {
        if (readWhole == null || text == null || readWhole.last() == null
                || !SignatureRecovery.holdsAStatement(new String(text))) {
            return;
        }
        final String refused = readWhole.propertyRefusal();
        if (refused == null) {
            return;
        }
        final Token start = readWhole.ownStatement().getStart();
        listener.noteWholeStatement(new WholeStatementReading(start.getStartIndex(), readWhole.last().getStopIndex(),
            start.getLine(), start.getCharPositionInLine(), sql, SqlCompilationError.of(refused)));
    }

    /** The first character of a statement a reading reads whole and its last one before the ';' ending it. */
    private static int[] standingOf(final SignatureRecovery reading) {
        final Token last = reading.last();
        final int stop = last.getType() == FrostlakeLexer.SEMI ? last.getStartIndex() - 1 : last.getStopIndex();
        return new int[] {reading.ownStatement().getStart().getStartIndex(), stop};
    }

    /**
     * Whether every fault left in a request of several, once the statements the account reads whole stand aside, lies
     * in a later statement the account reads whole too, one this parse did not read as a statement after the first
     * one's fault: the account fails at the first of them, the later ones never compiled (live-verified: {@code
     * DESCRIBE TABLE t1 (a) x = @s!=x; DESCRIBE TABLE t1 (a) y = @s!=y} names the first DESCRIBE, with {@code invalid
     * parameter 'x'}). Each statement read whole is replaced by a statement that compiles, where it stands, and the
     * text read again.
     */
    private static boolean restReadsWhole(final String sql, final List<int[]> readWholeAt) {
        final char[] rest = sql.toCharArray();
        for (final int[] at : readWholeAt) {
            standIn(rest, at[0], at[1]);
        }
        for (int round = 0; round < readWholeAt.size() + MAX_LATER_READINGS; round++) {
            final String restText = new String(rest);
            if (SyntaxErrorListener.linesReportedFor(restText, true).isEmpty()) {
                return round > 0;
            }
            final SyntaxErrorListener restListener = new SyntaxErrorListener(restText, true);
            final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(restText));
            lexer.removeErrorListeners();
            lexer.addErrorListener(restListener);
            final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
            ColdPrediction.arm(parser);
            parser.removeErrorListeners();
            parser.addErrorListener(restListener);
            final FrostlakeParser.SqlScriptContext restScript = parser.sqlScript();
            boolean found = false;
            for (final SignatureRecovery reading : SignatureRecovery.ofStatements(restScript, restText)) {
                final List<String> faults = reading.lines();
                if (faults != null && faults.isEmpty() && refusedWithin(restListener, reading)
                        && reading.propertyRefusal() != null) {
                    final int[] at = standingOf(reading);
                    standIn(rest, at[0], at[1]);
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return false;
    }

    /** Writes a statement that compiles over a statement's characters, keeping every place after it. */
    private static void standIn(final char[] text, final int start, final int stop) {
        for (int i = start; i <= stop && i < text.length; i++) {
            final int offset = i - start;
            if (offset < STAND_IN.length()) {
                text[i] = STAND_IN.charAt(offset);
            } else if (text[i] != '\n') {
                text[i] = ' ';
            }
        }
    }

    /** Whether this parse refused a token of the statement a reading reads. */
    private static boolean refusedWithin(final SyntaxErrorListener listener, final SignatureRecovery reading) {
        if (listener == null || !listener.hasErrors() || reading.last() == null) {
            return false;
        }
        final Token start = reading.ownStatement().getStart();
        final int[] from = {start.getLine(), start.getCharPositionInLine()};
        // The reading's last token may be one this parse reads as several, a stage reference: its end counts.
        final Token last = reading.last();
        final int[] to = {last.getLine(), last.getCharPositionInLine() + last.getStopIndex() - last.getStartIndex()};
        for (final String line : listener.reportedSyntaxLines()) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at != null && !before(at, from) && !before(to, at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the parse refused something before {@code open}. A text this lexer refuses is reported by a line that
     * names no token: the reading has placed that refusal at or after the signature, so only a token refused before
     * it counts.
     */
    private static boolean faultBefore(final SyntaxErrorListener listener, final Token open) {
        final String first = listener.firstReportedLine();
        if (first == null) {
            return false;
        }
        final int[] opens = LeadingCommentOffset.rebase(open.getLine(), open.getCharPositionInLine());
        if (first.startsWith("syntax error")) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(first);
            return at == null || before(at, opens);
        }
        for (final String line : listener.reportedSyntaxLines()) {
            final int[] at = line.startsWith("syntax error") ? SyntaxErrorListener.sentenceCoordinates(line) : null;
            if (at != null && before(at, opens)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The line for the first property written without its value — a name the account reads and then wants its '='
     * after — at the token after it, or null when every property has its value.
     */
    private static String bareProperty(final FrostlakeParser.DescribeStatementContext describe) {
        for (final FrostlakeParser.DescribePropertyContext property : describe.describeProperty()) {
            final FrostlakeParser.DescribeParameterContext parameter = property.describeParameter();
            if (parameter == null || parameter.EQ() != null) {
                continue;
            }
            final int index = describe.children.indexOf(property);
            if (index + 1 < describe.getChildCount()) {
                final ParseTree next = describe.getChild(index + 1);
                final Token token = next instanceof ParserRuleContext ? ((ParserRuleContext) next).getStart()
                    : ((TerminalNode) next).getSymbol();
                return SyntaxErrorListener.sentence(token);
            }
            final Token last = describe.getStop();
            final int[] end = LeadingCommentOffset.rebase(last.getLine(),
                last.getCharPositionInLine() + last.getText().length());
            return "syntax error line " + end[0] + " at position " + end[1] + " unexpected '<EOF>'.";
        }
        return null;
    }

    private static boolean before(final int[] a, final int[] b) {
        return a[0] < b[0] || a[0] == b[0] && a[1] < b[1];
    }
}
