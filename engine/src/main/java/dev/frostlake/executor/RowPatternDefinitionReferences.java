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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The names a MATCH_RECOGNIZE definition may qualify a column with, told where a routine's body is compiled
 * (all live-verified). A definition reads the row it judges: a column qualified by the definition's own symbol is that
 * column, and any other qualifier — another symbol, the table or its alias, a word naming nothing — refuses the
 * statement for {@link #CORRELATED}. Inside a NEXT or a LAST, which read another row, any qualifier refuses it for
 * {@link #WINDOWED} instead. The first qualified name met, reading the definitions in their order and each from left
 * to right, tells which. A definition of a symbol the pattern does not name is not read at all.
 *
 * <p>The symbols a pattern names are its words upper-cased, a quoted one too, and a definition is the symbol's when
 * its name, read as any identifier is, is one of them. A definition holding an aggregate call, or a name of more than
 * two parts, is left for the account's other readings of the body.
 */
public final class RowPatternDefinitionReferences {

    /** The refusal of a definition that reads a column of another symbol, or of the table. */
    public static final String CORRELATED = "Unsupported feature 'Correlated pattern definitions'.";
    /** The refusal of a definition that reads a qualified column inside a NEXT or a LAST. */
    public static final String WINDOWED =
        "Unsupported feature 'Window functions with pattern variable predicate in DEFINE'.";

    private RowPatternDefinitionReferences() {
    }

    /**
     * Refuses the first MATCH_RECOGNIZE under {@code node} whose definitions qualify a column they may not.
     *
     * @param node      the tree walked
     * @param functions the registry that tells an aggregate, or null
     */
    public static void reject(final ParseTree node, final FunctionRegistry functions) {
        if (node instanceof FrostlakeParser.MatchRecognizeClauseContext) {
            rejectClause((FrostlakeParser.MatchRecognizeClauseContext) node, functions);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            reject(node.getChild(i), functions);
        }
    }

    /**
     * The symbols a pattern names, each its word upper-cased.
     *
     * @param pattern the pattern
     * @param symbols where the symbols are added
     */
    public static void collectSymbols(final ParseTree pattern, final Set<String> symbols) {
        if (pattern instanceof FrostlakeParser.RowPatternPrimaryContext) {
            final FrostlakeParser.RowPatternPrimaryContext primary = (FrostlakeParser.RowPatternPrimaryContext) pattern;
            if (primary.identifier() != null && primary.LPAREN() == null) {
                symbols.add(SqlIdentifiers.canonical(primary.identifier()).toUpperCase(Locale.ROOT));
                return;
            }
        }
        for (int i = 0; i < pattern.getChildCount(); i++) {
            collectSymbols(pattern.getChild(i), symbols);
        }
    }

    private static void rejectClause(final FrostlakeParser.MatchRecognizeClauseContext clause,
                                     final FunctionRegistry functions) {
        final Set<String> symbols = new HashSet<>();
        collectSymbols(clause.rowPattern(), symbols);
        final List<FrostlakeParser.RowPatternDefinitionContext> definitions = clause.rowPatternDefinition();
        for (final FrostlakeParser.RowPatternDefinitionContext definition : definitions) {
            if (symbols.contains(SqlIdentifiers.canonical(definition.identifier()))
                    && readsOtherwise(definition.booleanExpr(), functions)) {
                return;
            }
        }
        for (final FrostlakeParser.RowPatternDefinitionContext definition : definitions) {
            final String symbol = SqlIdentifiers.canonical(definition.identifier());
            if (symbols.contains(symbol)) {
                walk(definition.booleanExpr(), symbol, false);
            }
        }
    }

    /** Whether a definition holds an aggregate call or a name of more than two parts. */
    private static boolean readsOtherwise(final ParseTree node, final FunctionRegistry functions) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName()).length > 2;
        }
        final FrostlakeParser.FunctionNameContext name = node instanceof FrostlakeParser.FunctionCallExprContext
            ? ((FrostlakeParser.FunctionCallExprContext) node).functionName()
            : node instanceof FrostlakeParser.FunctionCallStarExprContext
            ? ((FrostlakeParser.FunctionCallStarExprContext) node).functionName() : null;
        if (name != null && functions != null
                && functions.hasAggregateFunction(name.getText().toUpperCase(Locale.ROOT))) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsOtherwise(node.getChild(i), functions)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param node     the part of the definition walked
     * @param symbol   the symbol the definition defines
     * @param neighbor whether the part stands inside a NEXT or a LAST
     */
    private static void walk(final ParseTree node, final String symbol, final boolean neighbor) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            if (parts.length > 1 && neighbor) {
                throw new RuntimeException(WINDOWED);
            }
            if (parts.length > 1 && !parts[0].equals(symbol)) {
                throw new RuntimeException(CORRELATED);
            }
            return;
        }
        boolean inside = neighbor;
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String name = ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText()
                .toUpperCase(Locale.ROOT);
            inside = inside || "NEXT".equals(name) || "LAST".equals(name);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), symbol, inside);
        }
    }
}
