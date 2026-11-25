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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledKindWords;

/**
 * The refusals an ALTER or a COMMENT ON meets before its object's name is read (live-verified). Some come whatever
 * the session: ALTER EVENT TABLE … SET and COMMENT ON EVENT TABLE are an unsupported feature, as are a secret's UNSET
 * and its COMMENT ON, and COMMENT ON EXTERNAL TABLE and COMMENT ON DATASET are refused as an invalid property, each
 * whatever the name, IF EXISTS or not. The rest come before a session with no current database is noticed: a
 * property ALTER DYNAMIC TABLE, ALTER EXTERNAL TABLE or ALTER TABLE does not take, by name.
 */
public final class RefusalsBeforeNames {

    private RefusalsBeforeNames() {
    }

    /**
     * Refuses what the statement is refused whatever its name and the session; any other statement passes.
     *
     * @param statement the statement
     */
    public static void rejectUnsupported(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        if (ddl == null) {
            return;
        }
        final FrostlakeParser.AlterStatementContext alter = ddl.alterStatement();
        if (alter != null && alter.EVENT() != null && alter.TABLE() != null) {
            throw unsupported("EVENT");
        }
        if (ddl.securityObjectStatement() != null) {
            refuseSecretUnset(ddl.securityObjectStatement());
        }
        final FrostlakeParser.CommentStatementContext comment = ddl.commentStatement();
        if (comment == null) {
            return;
        }
        if (comment.SECRET() != null) {
            throw unsupported("SECRET");
        }
        if (comment.EVENT() != null) {
            throw unsupported("EVENT");
        }
        if (comment.EXTERNAL() != null) {
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor("COMMENT", "EXTERNAL_TABLE"));
        }
        final FrostlakeParser.CommentedKindContext kind = comment.commentedKind();
        if (kind != null && kind.unmodelledCommentKind() != null && kind.unmodelledCommentKind().model != null
                && UnmodelledKindWords.is(kind.unmodelledCommentKind().model, "DATASET")) {
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor("COMMENT", "DATASET"));
        }
    }

    /**
     * Refuses what the statement is refused before a missing current database is noticed; any other statement
     * passes.
     *
     * @param statement the statement
     */
    public static void rejectProperties(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        final FrostlakeParser.AlterStatementContext alter = ddl == null ? null : ddl.alterStatement();
        if (alter == null) {
            return;
        }
        if (alter.dynamicTableAction() != null) {
            DynamicTableProperties.rejectUntakeable(alter.dynamicTableAction());
        } else if (alter.externalTableAction() != null) {
            ExternalTableAlteration.requireProperty(alter.externalTableAction());
        } else if (alter.TABLE() != null && alter.ICEBERG() == null && alter.tableAction() != null) {
            AlteredTableNames.rejectUnknown(alter.tableAction());
        }
    }

    /**
     * Refuses ALTER SECRET … UNSET, an unsupported feature whatever it names and whatever the secret, before the name
     * is read and IF EXISTS with it; any other statement passes.
     *
     * @param statement the statement
     */
    static void refuseSecretUnset(final FrostlakeParser.SecurityObjectStatementContext statement) {
        if (statement.ALTER() != null && statement.SECRET() != null && statement.securityPropertyChange() != null
                && statement.securityPropertyChange().UNSET() != null) {
            throw unsupported("SECRET");
        }
    }

    private static RuntimeException unsupported(final String feature) {
        return new RuntimeException("Unsupported feature '" + feature + "'.");
    }
}
