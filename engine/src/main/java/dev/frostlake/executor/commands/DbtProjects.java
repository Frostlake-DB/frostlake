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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StagePrefix;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledDropKind;

import java.util.Locale;

/**
 * DBT projects, which this engine never creates. A CREATE judges where the project's files are read from, in the
 * account's order: a FROM is wanted, its text must name a stage, the stage must be there, and a dbt_project.yml must
 * be where the location points (live-verified); a project that would then be created is an unsupported feature. No
 * project is ever there, so a statement that runs one finds nothing.
 */
public final class DbtProjects {

    /** What a location that holds no dbt project is told, the job's report as the account relays it. */
    static final String NO_PROJECT_FILE = """
        DBT job failed with exception: Runtime Error
          No dbt_project.yml found at expected path /tmp/dbt/dbt_project.yml
          Verify that each entry within packages.yml (and their transitive dependencies) contains a file named \
        dbt_project.yml
         \s
         See the query's error message for details.""";

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public DbtProjects(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    /**
     * CREATE DBT PROJECT: refused once its location is judged.
     *
     * @param ctx the statement
     * @return never
     */
    public Object create(final FrostlakeParser.CreateStatementContext ctx) {
        if (ctx.dbtProjectSource() == null) {
            throw new RuntimeException("DBT project creation requires 'FROM <location>' clause.");
        }
        requireProjectFiles(ParseTreeText.stringValue(ctx.dbtProjectSource().stringText()));
        throw new RuntimeException("Unsupported feature 'DBT PROJECT'.");
    }

    /**
     * EXECUTE DBT PROJECT: the name resolves the way any schema object's does, and the project is then missing.
     *
     * @param name the project's name
     * @return never
     */
    public Object execute(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        throw new RuntimeException(SqlCompilationError.doesNotExist(UnmodelledDropKind.DBT_PROJECT.noun(),
            owner.qualifiedName(parts[parts.length - 1])));
    }

    /**
     * Refuses a location that is no stage holding a dbt project. A workspace, a user's or a table's stage, and a stage
     * whose files are not held here pass, the project being refused all the same.
     */
    private void requireProjectFiles(final String from) {
        if (from.toLowerCase(Locale.ROOT).startsWith("snow://")) {
            return;
        }
        if (!from.startsWith("@")) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL prefix found in: '" + from + "'"));
        }
        final String reference = from.substring(1);
        final int slash = reference.indexOf('/');
        final String stage = slash >= 0 ? reference.substring(0, slash) : reference;
        if (stage.startsWith("~") || stage.startsWith("%")) {
            return;
        }
        final String stageName = SqlIdentifiers.canonicalText(QualifiedName.parse(stage).last());
        final Schema schema = catalog.resolveOwningSchema(SqlIdentifiers.canonicalText(stage));
        if (schema == null || !schema.hasStageExact(stageName)) {
            throw new RuntimeException("The specified stage " + stageName + " does not exist or the current role does"
                + " not have access. Owner must have at least READ on the specified stage.");
        }
        final StagePrefix files = queryExecutor.resolveStagePrefix("@" + SqlIdentifiers.canonicalText(stage)
            + (slash >= 0 ? reference.substring(slash) : ""));
        if (files == null || files.getRoot() == null) {
            return;
        }
        final String path = slash >= 0 ? reference.substring(slash + 1) : "";
        final String folder = path.isEmpty() || path.endsWith("/") ? path : path + "/";
        if (!files.occupied(folder + "dbt_project.yml")) {
            throw new RuntimeException(SqlCompilationError.of(NO_PROJECT_FILE));
        }
    }
}
