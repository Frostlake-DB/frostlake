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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where a code bundle's version is copied from: a named stage and the path below it. The location is read as LIST
 * reads one; a user stage, a table's stage and a stage that is not there are refused with the copy's own sentences
 * (live-verified).
 */
public final class CodeBundleSource {

    private final String written;
    private final String location;
    private final String stageDatabase;
    private final String stageName;
    private final String stageSpelled;
    private final String path;

    private CodeBundleSource(final String written, final String location, final String stageDatabase,
                             final String stageName, final String stageSpelled, final String path) {
        this.written = written;
        this.location = location;
        this.stageDatabase = stageDatabase;
        this.stageName = stageName;
        this.stageSpelled = stageSpelled;
        this.path = path;
    }

    /**
     * The source a CREATE CODE BUNDLE names.
     *
     * @param ctx the location as written
     * @param catalog the catalog its stage resolves in
     * @return the source
     */
    public static CodeBundleSource of(final FrostlakeParser.StageLocationContext ctx, final Catalog catalog) {
        final String written = ctx.STRING_LITERAL() != null ? SqlStringLiterals.decode(ctx.STRING_LITERAL().getText())
            : textOf(ctx);
        if (ctx.STRING_LITERAL() != null && written.regionMatches(true, 0, "snow://", 0, "snow://".length())) {
            throw new RuntimeException("Invalid domain to copy files: 'Code Bundle'.");
        }
        final StageLocation parsed = StageLocation.of(ctx);
        final FrostlakeParser.StageRefContext stage = parsed.stage();
        if (stage.TILDE() != null) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Copy files not support stage kind: 'USER' for stage '~'."));
        }
        final String stageWritten = StageReferenceShape.writtenName(stage);
        final List<String> parts = StageReferenceShape.nameParts(stage);
        final String name = parts.get(parts.size() - 1);
        if (stage.PERCENT() != null) {
            if (tableExists(catalog, parts)) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Copy files not support stage kind: 'TABLE' for stage '" + name + "'."));
            }
            throw missingStage(stageWritten);
        }
        final String database = parts.size() >= 3 ? parts.get(parts.size() - 3) : catalog.getCurrentDatabase();
        final Schema owner;
        try {
            owner = catalog.requireOwningSchema(QualifiedName.of(parts.toArray(new String[0])));
        } catch (final RuntimeException missing) {
            throw missingStage(stageWritten);
        }
        if (!owner.hasStageExact(name)) {
            throw missingStage(stageWritten);
        }
        final String path = parsed.path();
        final String location = path.isEmpty() || path.endsWith("/") ? written : written + "/";
        return new CodeBundleSource(written, location, database, name,
            QualifiedName.join(parts.toArray(new String[0])), path);
    }

    private static boolean tableExists(final Catalog catalog, final List<String> parts) {
        try {
            return catalog.requireOwningSchema(QualifiedName.of(parts.toArray(new String[0])))
                .hasTable(parts.get(parts.size() - 1));
        } catch (final RuntimeException missing) {
            return false;
        }
    }

    private static RuntimeException missingStage(final String written) {
        return new RuntimeException("The specified stage " + written + " does not exist or the current role does not"
            + " have access. Owner must have at least READ on the specified stage.");
    }

    private static String textOf(final FrostlakeParser.StageLocationContext ctx) {
        final Token start = ctx.getStart();
        return start.getInputStream().getText(Interval.of(start.getStartIndex(), ctx.getStop().getStopIndex()));
    }

    /** The location as written. */
    public String written() {
        return written;
    }

    /** The location as the bundle's version reports its source: a path closed by a {@code /}. */
    public String location() {
        return location;
    }

    /** The database of the stage. */
    public String stageDatabase() {
        return stageDatabase;
    }

    /** The stage's canonical name. */
    public String stageName() {
        return stageName;
    }

    /**
     * Whether any file is staged below the location's path.
     *
     * @param executor the executor whose stage area holds the files
     * @return whether one is
     */
    public boolean holdsFiles(final QueryExecutor executor) {
        final Path root;
        try {
            root = executor.resolveCopyBaseDir("@" + stageSpelled);
        } catch (final RuntimeException unresolved) {
            return false;
        }
        if (root == null) {
            return false;
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        while (relative.endsWith("/")) {
            relative = relative.substring(0, relative.length() - 1);
        }
        final Path directory = relative.isEmpty() ? root : root.resolve(relative);
        return Files.isDirectory(directory) && holdsFile(directory);
    }

    private static boolean holdsFile(final Path directory) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (final Path entry : entries) {
                if (Files.isRegularFile(entry) || Files.isDirectory(entry) && holdsFile(entry)) {
                    return true;
                }
            }
        } catch (final IOException unreadable) {
            return false;
        }
        return false;
    }
}
