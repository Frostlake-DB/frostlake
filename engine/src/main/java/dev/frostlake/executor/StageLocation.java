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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

/**
 * Where LIST, PUT, GET and REMOVE act: the stage a location names, and the path below it. A bare reference is read
 * as written; a quoted one ({@code '@st/2024-01-01/'}, {@code '@st/sp ace/'}) names its stage up to the first
 * {@code /} the stage's name does not quote, read as a bare reference's name is, and its path is the rest of the
 * text, every character kept. A quoted text that does not start with {@code @}, or whose stage part names no stage
 * reference, is no location: {@code invalid URL prefix found in: '<text>'} (live-verified).
 */
public final class StageLocation {

    /** The stage reference, its path included for a bare reference and absent for a quoted one. */
    private final FrostlakeParser.StageRefContext stage;

    /** The quoted location's path from its first {@code /}, or the empty text; null for a bare reference. */
    private final String quotedPath;

    private StageLocation(final FrostlakeParser.StageRefContext stage, final String quotedPath) {
        this.stage = stage;
        this.quotedPath = quotedPath;
    }

    /**
     * The location a statement names.
     *
     * @param ctx the location as written
     * @return the location
     */
    public static StageLocation of(final FrostlakeParser.StageLocationContext ctx) {
        if (ctx.stageRef() != null) {
            return new StageLocation(ctx.stageRef(), null);
        }
        final String text = SqlStringLiterals.decode(ctx.STRING_LITERAL().getText());
        if (!text.startsWith("@")) {
            throw invalidPrefix(text);
        }
        // The stage's name runs to the first '/' token: a quoted name part keeps its own slashes.
        final FrostlakeLexer scanner = new FrostlakeLexer(SqlCharStream.of(text));
        scanner.removeErrorListeners();
        int nameEnd = text.length();
        Token token = scanner.nextToken();
        while (token.getType() != Token.EOF) {
            if (token.getType() == FrostlakeLexer.SLASH) {
                nameEnd = token.getStartIndex();
                break;
            }
            token = scanner.nextToken();
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text.substring(0, nameEnd)));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        final FrostlakeParser.StageRefContext name;
        try {
            name = parser.stageRef();
        } catch (final ParseCancellationException notAStage) {
            throw invalidPrefix(text);
        }
        if (parser.getCurrentToken().getType() != Token.EOF || name.stagePath() != null) {
            throw invalidPrefix(text);
        }
        return new StageLocation(name, text.substring(nameEnd));
    }

    private static RuntimeException invalidPrefix(final String text) {
        return new RuntimeException(SqlCompilationError.of("invalid URL prefix found in: '" + text + "'"));
    }

    /**
     * The stage reference: for a quoted location, its stage alone.
     *
     * @return the reference
     */
    public FrostlakeParser.StageRefContext stage() {
        return stage;
    }

    /**
     * The path below the stage as written, from its first {@code /}; empty when there is none.
     *
     * @return the path
     */
    public String path() {
        if (quotedPath != null) {
            return quotedPath;
        }
        return stage.stagePath() != null ? stage.stagePath().getText() : "";
    }
}
