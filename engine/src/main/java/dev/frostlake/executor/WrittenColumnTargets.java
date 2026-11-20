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
import dev.frostlake.parser.FrostlakeParser;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The columns an UPDATE's or a MERGE's SET and a MERGE's INSERT column list write, judged against the target by
 * their canonical names: an unquoted name is its upper-case spelling and a quoted one its exact text, so neither
 * {@code bee} nor {@code "bee"} reaches a column created as {@code "Bee"} (live-verified).
 *
 * <pre>
 *   UPDATE t SET bee = 5                    invalid identifier 'BEE'
 *   UPDATE t SET "bee" = 5                  invalid identifier '"bee"'
 *   UPDATE t SET x.bee = 5                  invalid identifier 'X.BEE'
 *   UPDATE t a SET t.cc = 5                 invalid identifier 'T.CC'      the alias hides the table's name
 *   UPDATE t SET "t".cc = 5                 invalid identifier '"t".CC'
 *   UPDATE t SET id = 1, x.id = 2           duplicate column name 'X.ID'
 * </pre>
 *
 * Three rules, judged at three places of the statement's compilation. A target named twice is refused first, by
 * its later spelling. A target the table does not hold is refused with the statement's other target names. A
 * target's qualifier must be the name the target answers to, its alias when it has one, and that is judged only
 * once every column the statement's values and conditions read has resolved: {@code SET x.id = nosuch} is refused
 * for NOSUCH. Each refusal names the target as written, qualifier and all, the invalid identifier at its first
 * character.
 */
final class WrittenColumnTargets {

    private WrittenColumnTargets() {
    }

    /**
     * Refuses a SET target that names no column of the target.
     *
     * @param target the table written
     * @param assign the assignment
     * @return the column's canonical name
     */
    static String requireAssigned(final Table target, final FrostlakeParser.AssignmentContext assign) {
        final String column = ParseTreeText.namePartText(assign.namePart());
        if (!target.hasColumnExactly(column)) {
            throw refused(assign.getStart(), qualifierOf(assign), column);
        }
        VirtualColumns.refuseTarget(target, column);
        return column;
    }

    /**
     * Refuses an INSERT column that names no column of the target.
     *
     * @param target the table written
     * @param column the column as written
     * @return the column's canonical name
     */
    static String requireInserted(final Table target, final FrostlakeParser.MergeInsertColumnContext column) {
        final String name = columnOf(column);
        if (!target.hasColumnExactly(name)) {
            throw refused(column.getStart(), qualifierOf(column), name);
        }
        VirtualColumns.refuseTarget(target, name);
        return name;
    }

    /**
     * Refuses a SET target qualified by a name the target does not answer to.
     *
     * @param relationName the canonical name the target answers to: its alias, else its own name
     * @param assign       the assignment
     */
    static void requireQualifier(final String relationName, final FrostlakeParser.AssignmentContext assign) {
        final String qualifier = qualifierOf(assign);
        if (qualifier != null && !qualifier.equals(relationName)) {
            throw refused(assign.getStart(), qualifier, ParseTreeText.namePartText(assign.namePart()));
        }
    }

    /**
     * Refuses an INSERT column qualified by a name the target does not answer to.
     *
     * @param relationName the canonical name the target answers to: its alias, else its own name
     * @param column       the column as written
     */
    static void requireQualifier(final String relationName, final FrostlakeParser.MergeInsertColumnContext column) {
        final String qualifier = qualifierOf(column);
        if (qualifier != null && !qualifier.equals(relationName)) {
            throw refused(column.getStart(), qualifier, columnOf(column));
        }
    }

    /**
     * A SET target as a refusal spells it, qualifier and all.
     *
     * @param assign the assignment
     * @return the spelling
     */
    static String spelled(final FrostlakeParser.AssignmentContext assign) {
        return spelled(qualifierOf(assign), ParseTreeText.namePartText(assign.namePart()));
    }

    /**
     * An INSERT column as a refusal spells it, qualifier and all.
     *
     * @param column the column as written
     * @return the spelling
     */
    static String spelled(final FrostlakeParser.MergeInsertColumnContext column) {
        return spelled(qualifierOf(column), columnOf(column));
    }

    private static String qualifierOf(final FrostlakeParser.AssignmentContext assign) {
        return assign.identifier() != null ? SqlIdentifiers.canonical(assign.identifier()) : null;
    }

    private static String qualifierOf(final FrostlakeParser.MergeInsertColumnContext column) {
        final List<FrostlakeParser.IdentifierContext> parts = column.identifier();
        return parts.size() > 1 ? SqlIdentifiers.canonical(parts.get(0)) : null;
    }

    private static String columnOf(final FrostlakeParser.MergeInsertColumnContext column) {
        final List<FrostlakeParser.IdentifierContext> parts = column.identifier();
        return SqlIdentifiers.canonical(parts.get(parts.size() - 1));
    }

    private static String spelled(final String qualifier, final String column) {
        return (qualifier != null ? SqlIdentifiers.spellCanonical(qualifier) + "." : "")
            + SqlIdentifiers.spellCanonical(column);
    }

    private static RuntimeException refused(final Token at, final String qualifier, final String column) {
        return new RuntimeException(SqlCompilationError.invalidIdentifier(at.getLine(), at.getCharPositionInLine(),
            spelled(qualifier, column)));
    }
}
