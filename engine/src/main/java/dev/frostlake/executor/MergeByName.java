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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * A MERGE's {@code UPDATE ALL BY NAME} and {@code INSERT ALL BY NAME}: every target column is written from the
 * source column of the same canonical name, in whatever order the source holds them (live-verified):
 *
 * <pre>
 *   a source of another column count     Column count mismatch in MERGE statement with ALL BY NAME subclause,
 *                                         source table has (2) columns but target table has (4).
 *   a target column the source lacks      Missing column in MERGE statement with ALL BY NAME subclause, the
 *                                         column Mx is in the target table but not the source.
 * </pre>
 *
 * The count is judged first, then the target's columns in their order, each by its exact name: a source column
 * {@code mx} or {@code "MX"} is no column {@code "Mx"}, and a source naming one column twice misses another. Both
 * refusals are compiled before any row is read, placed at the statement's first character.
 */
final class MergeByName {

    private MergeByName() {
    }

    /**
     * Whether a WHEN clause writes by name.
     *
     * @param clause the clause
     * @return true for UPDATE ALL BY NAME and INSERT ALL BY NAME
     */
    static boolean isByName(final FrostlakeParser.MergeClauseContext clause) {
        return clause.NAME() != null;
    }

    /**
     * Refuses a statement whose by-name clauses cannot pair the source's columns with the target's.
     *
     * @param ctx    the MERGE
     * @param target the target table
     * @param source the source relation's shape
     */
    static void requirePairable(final FrostlakeParser.MergeStatementContext ctx, final Table target,
                                final Table source) {
        boolean byName = false;
        for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
            byName |= isByName(clause);
        }
        if (!byName || source == null) {
            return;
        }
        final Token at = ctx.getStart();
        final int written = VirtualColumns.writtenCount(target);
        if (source.columnCount() != written) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "Column count mismatch in MERGE statement with ALL BY NAME subclause, source table has ("
                    + source.columnCount() + ") columns but target table has (" + written + ")."));
        }
        for (final TableColumn column : target.columnsView()) {
            if (!column.isVirtual() && !source.hasColumnExactly(column.getName())) {
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "Missing column in MERGE statement with ALL BY NAME subclause, the column " + column.getName()
                        + " is in the target table but not the source."));
            }
        }
    }

    /**
     * The source position each target column reads by name, -1 for a virtual one, which is never written, and for one
     * the source does not hold, which a statement writing by name has already been refused for.
     *
     * @param target the target table
     * @param source the source relation's shape
     * @return the source position by target position
     */
    static int[] sourcePositions(final Table target, final Table source) {
        final List<TableColumn> columns = target.columnsView();
        final int[] positions = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            final String name = columns.get(i).getName();
            positions[i] = columns.get(i).isVirtual() || !source.hasColumnExactly(name) ? -1
                : source.getColumnIndex(name);
        }
        return positions;
    }
}
