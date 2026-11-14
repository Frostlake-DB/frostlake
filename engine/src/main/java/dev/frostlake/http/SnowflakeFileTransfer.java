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
package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.SessionWork;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.parser.FrostlakeParser;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * PUT and GET as the Snowflake driver carries them out. The driver moves the files itself, so the statement is
 * answered with the transfer it asks for - the files, the stage's location and the options - and is not run here. The
 * location is the stage's own directory on this machine, the driver's {@code LOCAL_FS} stage type: a PUT's files are
 * written into the directory the engine stages them in, compressed as the driver compresses them, and a GET's are
 * copied out of it, and the driver builds the statement's result rows itself, as it does against the account. The
 * server and the client share one file system.
 */
final class SnowflakeFileTransfer {

    /** The size past which the driver splits a file's upload. */
    private static final int THRESHOLD = 200 * 1024 * 1024;
    private static final int DEFAULT_PARALLEL = 4;

    private SnowflakeFileTransfer() {
    }

    /**
     * Whether a statement is a PUT or a GET.
     *
     * @param statement the statement's parse tree
     * @return true for a file transfer
     */
    static boolean isTransfer(final FrostlakeParser.StatementContext statement) {
        return statement.getChildCount() > 0 && (statement.getChild(0) instanceof FrostlakeParser.PutStatementContext
            || statement.getChild(0) instanceof FrostlakeParser.GetStatementContext);
    }

    /**
     * The transfer a PUT or a GET asks of the driver.
     *
     * @param engine    the engine
     * @param session   the session the statement runs in
     * @param statement the statement's parse tree
     * @param queryId   the query ID the transfer runs under
     * @return the answer's {@code data}
     * @throws IOException where the stage's directory cannot be made
     */
    static ObjectNode describe(final ConcurrentDatabaseEngine engine, final SessionContext session,
                               final FrostlakeParser.StatementContext statement, final String queryId)
            throws IOException {
        final ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("queryId", queryId);
        data.put("threshold", THRESHOLD);
        data.put("clientShowEncryptionParameter", false);
        data.putNull("encryptionMaterial");
        if (statement.getChild(0) instanceof FrostlakeParser.PutStatementContext) {
            put(engine, session, (FrostlakeParser.PutStatementContext) statement.getChild(0), data);
        } else {
            get(engine, session, (FrostlakeParser.GetStatementContext) statement.getChild(0), data);
        }
        return data;
    }

    private static void put(final ConcurrentDatabaseEngine engine, final SessionContext session,
                            final FrostlakeParser.PutStatementContext put, final ObjectNode data) throws IOException {
        boolean autoCompress = true;
        boolean overwrite = false;
        int parallel = DEFAULT_PARALLEL;
        String sourceCompression = "auto_detect";
        for (final FrostlakeParser.StageFileOptionContext option : put.stageFileOption()) {
            final String key = option.optionKey().getText().toUpperCase(Locale.ROOT);
            final String value = valueOf(option.copyOptionValue());
            if ("AUTO_COMPRESS".equals(key)) {
                autoCompress = "TRUE".equalsIgnoreCase(value);
            } else if ("OVERWRITE".equals(key)) {
                overwrite = "TRUE".equalsIgnoreCase(value);
            } else if ("PARALLEL".equals(key)) {
                parallel = Integer.parseInt(value);
            } else if ("SOURCE_COMPRESSION".equals(key)) {
                sourceCompression = value.toLowerCase(Locale.ROOT);
            }
        }
        final String[] source = new String[1];
        final Path directory = engine.inSession(session, new SessionWork<Path>() {
            @Override
            public Path run(final DatabaseEngine bound) throws IOException {
                final QueryExecutor executor = bound.getExecutor();
                source[0] = executor.putSource(put);
                final Path named = Paths.get(source[0]).getFileName();
                return executor.putDirectory(put, named == null ? "" : named.toString());
            }
        });
        data.put("command", "UPLOAD");
        data.putArray("src_locations").add(source[0]);
        data.put("autoCompress", autoCompress);
        data.put("sourceCompression", sourceCompression);
        data.put("parallel", parallel);
        data.put("overwrite", overwrite);
        stageInfo(data, directory);
    }

    private static void get(final ConcurrentDatabaseEngine engine, final SessionContext session,
                            final FrostlakeParser.GetStatementContext get, final ObjectNode data) throws IOException {
        final String[] target = new String[1];
        final Path[] root = new Path[1];
        final List<Path> files = engine.inSession(session, new SessionWork<List<Path>>() {
            @Override
            public List<Path> run(final DatabaseEngine bound) {
                final QueryExecutor executor = bound.getExecutor();
                target[0] = executor.getTarget(get);
                root[0] = executor.getRoot(get);
                return executor.getFiles(get);
            }
        });
        data.put("command", "DOWNLOAD");
        final ArrayNode sources = data.putArray("src_locations");
        for (final Path file : files) {
            sources.add(root[0].relativize(file).toString().replace('\\', '/'));
        }
        data.put("localLocation", target[0]);
        data.putArray("presignedUrls");
        int parallel = DEFAULT_PARALLEL;
        for (final FrostlakeParser.StageFileOptionContext option : get.stageFileOption()) {
            if ("PARALLEL".equalsIgnoreCase(option.optionKey().getText())) {
                parallel = Integer.parseInt(valueOf(option.copyOptionValue()));
            }
        }
        data.put("parallel", parallel);
        stageInfo(data, root[0] == null ? Paths.get("") : root[0]);
    }

    /** The stage's location as the driver's {@code LOCAL_FS} stage type takes it. */
    private static void stageInfo(final ObjectNode data, final Path directory) {
        final ObjectNode stage = data.putObject("stageInfo");
        stage.put("locationType", "LOCAL_FS");
        stage.put("location", directory.toAbsolutePath().toString());
        stage.put("path", "");
        stage.putNull("region");
        stage.putObject("creds");
        stage.put("isClientSideEncrypted", false);
    }

    /** An option's value: a quoted text's content, any other value as written. */
    private static String valueOf(final FrostlakeParser.CopyOptionValueContext value) {
        return value.STRING_LITERAL() != null ? ParseTreeText.extractStringLiteral(value.STRING_LITERAL())
            : value.getText();
    }
}
