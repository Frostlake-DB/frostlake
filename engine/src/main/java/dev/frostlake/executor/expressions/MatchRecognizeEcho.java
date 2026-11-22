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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Prints a MATCH_RECOGNIZE relation the way the account rewrites it in an echo of the tree as parsed (all
 * live-verified). The clause reads {@code SELECT *} of its table, or its derived query bare, with every default
 * spelled out: each order key's direction and nulls, ONE ROW PER MATCH, SHOW EMPTY MATCHES after ALL ROWS PER MATCH,
 * AFTER MATCH SKIP PAST LAST ROW, LAST before a skip's symbol. A query over the clause selects the partition keys and
 * the measures; under ONE ROW PER MATCH a second query over that one takes ANY_VALUE of each measure, and under ALL
 * ROWS PER MATCH the one query selects every column before the measures. The relation goes by the alias written after
 * the clause, or {@code values}:
 *
 * <pre>
 *   FROM t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a &gt; 0)
 *   FROM (SELECT ANY_VALUE(M) AS "M" FROM (SELECT ?MATCH_NUMBER() AS "M" FROM SELECT * FROM T AS T
 *     MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH
 *     AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((">"(A, 0)) = TRUE, FALSE))) values) values
 * </pre>
 *
 * <p>A measure that is a call of a row pattern function is marked {@code ?}, after the RUNNING or FINAL written
 * before it. A definition is its condition under {@code RUNNING IFNULL((…) = TRUE, FALSE)}, a NEXT in it the LEAD
 * and a LAST the LAST_VALUE over the clause's partitions and order. A pattern of more than one term is in
 * parentheses, an anchor the terms beside it in parentheses of their own ({@code ^ x $} is {@code (^(X)$)}), a group
 * of one term that term, and a count quantifier both its bounds, {@code {2}} being {@code {2,2}}, {@code {,3}}
 * {@code {0,3}} and an open upper bound 0.
 */
final class MatchRecognizeEcho {

    /** The calls a measure marks {@code ?}. */
    private static final Set<String> PATTERN_FUNCTIONS = Set.of(
        "MATCH_NUMBER", "CLASSIFIER", "FIRST", "LAST", "PREV", "NEXT");

    /** Prints the queries the clause's expressions hold. */
    private final ParsedQueryEcho queries;
    /** Prints a measure: every operator a call. */
    private final ParsedEchoPrinter printer;
    /** Whether the clause holds a shape this printer does not model. */
    private boolean unmodelled;

    /**
     * @param queries prints the queries the clause's expressions hold
     * @param printer prints a measure
     */
    MatchRecognizeEcho(final ParsedQueryEcho queries, final ParsedEchoPrinter printer) {
        this.queries = queries;
        this.printer = printer;
    }

    /** Whether the relation printed last holds a shape this printer does not model. */
    boolean isUnmodelled() {
        return unmodelled;
    }

    /**
     * The relation.
     *
     * @param clause the clause
     * @param input  what the clause reads, as the echo prints it: {@code SELECT * FROM T AS T}, or a derived query
     * @param alias  the alias written after the clause, or null
     * @return its text
     */
    String relation(final FrostlakeParser.MatchRecognizeClauseContext clause, final String input,
                    final String alias) {
        final List<String> keys = keys(clause);
        final List<String> order = order(clause);
        final List<String> names = new ArrayList<>();
        final List<String> measures = measures(clause, names);
        final FrostlakeParser.RowsPerMatchContext rows = clause.rowsPerMatch();
        final boolean allRows = rows != null && rows.ALL() != null;
        final StringBuilder text = new StringBuilder("MATCH_RECOGNIZE (");
        if (!keys.isEmpty()) {
            text.append("PARTITION BY ").append(String.join(", ", keys)).append(' ');
        }
        if (!order.isEmpty()) {
            text.append("ORDER BY ").append(String.join(", ", order)).append(' ');
        }
        if (!measures.isEmpty()) {
            text.append("MEASURES ");
            for (int i = 0; i < measures.size(); i++) {
                text.append(i > 0 ? ", " : "").append(measures.get(i)).append(" AS ").append(names.get(i));
            }
            text.append(' ');
        }
        text.append(rowsPerMatch(rows)).append(' ').append(afterMatch(clause.afterMatchSkip()))
            .append(" PATTERN (").append(pattern(clause.rowPattern())).append(") DEFINE ")
            .append(definitions(clause, window(keys, order))).append(')');
        final String read = input + " " + text;
        final String named = alias != null ? alias : "values";
        if (allRows) {
            final StringBuilder query = new StringBuilder("(SELECT *");
            for (int i = 0; i < measures.size(); i++) {
                query.append(", ").append(measures.get(i)).append(" AS \"").append(names.get(i)).append('"');
            }
            return query.append(" FROM ").append(read).append(") ").append(named).toString();
        }
        // A match with no partition key and no measure answers no column: the account's text does not read back.
        unmodelled = unmodelled || keys.isEmpty() && measures.isEmpty();
        final List<String> inner = new ArrayList<>();
        final List<String> outer = new ArrayList<>();
        for (final String key : keys) {
            inner.add(key + " AS \"" + key + "\"");
            outer.add(SqlIdentifiers.spellCanonical(key) + " AS \"" + key + "\"");
        }
        for (int i = 0; i < measures.size(); i++) {
            inner.add(measures.get(i) + " AS \"" + names.get(i) + "\"");
            outer.add("ANY_VALUE(" + SqlIdentifiers.spellCanonical(names.get(i)) + ") AS \"" + names.get(i) + "\"");
        }
        return "(SELECT " + String.join(", ", outer) + " FROM (SELECT " + String.join(", ", inner) + " FROM " + read
            + ") values) " + named;
    }

    /** The partition keys, each as written in capitals; one holding a quote is not modelled. */
    private List<String> keys(final FrostlakeParser.MatchRecognizeClauseContext clause) {
        final List<String> keys = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext key : clause.booleanExpr()) {
            keys.add(written(key));
        }
        return keys;
    }

    /** The order keys, each a plain name with its direction and where its nulls sort. */
    private List<String> order(final FrostlakeParser.MatchRecognizeClauseContext clause) {
        final List<String> order = new ArrayList<>();
        for (final FrostlakeParser.RowPatternOrderItemContext item : clause.rowPatternOrderItem()) {
            unmodelled = unmodelled || !(item.expression() instanceof FrostlakeParser.QualifiedNameExprContext)
                || item.expression().getText().indexOf('.') >= 0;
            order.add(written(item.expression()) + ParsedEchoPrinter.sortSuffix(item.DESC() == null,
                item.NULLS() == null ? null : Boolean.valueOf(item.FIRST() != null)));
        }
        return order;
    }

    /** A key's text as written, in capitals. */
    private String written(final ParserRuleContext key) {
        final String text = ParseTreeText.getOriginalText(key);
        unmodelled = unmodelled || text.indexOf('"') >= 0 || text.indexOf('\'') >= 0 || text.indexOf('$') >= 0;
        return text.toUpperCase(Locale.ROOT);
    }

    /** The measures, their names added to {@code names}. */
    private List<String> measures(final FrostlakeParser.MatchRecognizeClauseContext clause, final List<String> names) {
        final List<String> measures = new ArrayList<>();
        for (final FrostlakeParser.RowPatternMeasureContext measure : clause.rowPatternMeasure()) {
            names.add(ParseTreeText.getIdentifier(measure.aliasName()));
            FrostlakeParser.ExpressionContext value = SelectItemAccessors.unwrapValue(measure.booleanExpr());
            String semantics = "";
            if (value instanceof FrostlakeParser.RowPatternSemanticsExprContext) {
                final FrostlakeParser.RowPatternSemanticsExprContext prefixed =
                    (FrostlakeParser.RowPatternSemanticsExprContext) value;
                semantics = prefixed.ROW_PATTERN_SEMANTICS().getText().toUpperCase(Locale.ROOT) + " ";
                value = prefixed.expression();
            }
            if (value == null) {
                measures.add(new ExpressionAstBuilder().build(measure.booleanExpr()).accept(printer));
                continue;
            }
            final Expression built = new ExpressionAstBuilder().visit(value);
            final boolean marked = built instanceof FunctionCallExpression
                && isUnqualified((FunctionCallExpression) built)
                && PATTERN_FUNCTIONS.contains(((FunctionCallExpression) built).getFunctionName()
                    .toUpperCase(Locale.ROOT));
            measures.add(semantics + (marked ? "?" : "") + built.accept(printer));
        }
        return measures;
    }

    /** Whether a call names its function alone, with no schema or database before it. */
    private static boolean isUnqualified(final FunctionCallExpression call) {
        return call.getNameParts() == null || call.getNameParts().size() <= 1;
    }

    private static String rowsPerMatch(final FrostlakeParser.RowsPerMatchContext rows) {
        if (rows == null || rows.ALL() == null) {
            return "ONE ROW PER MATCH";
        }
        final FrostlakeParser.RowPatternEmptyMatchesContext empty = rows.rowPatternEmptyMatches();
        if (empty == null || empty.SHOW() != null) {
            return "ALL ROWS PER MATCH SHOW EMPTY MATCHES";
        }
        return "ALL ROWS PER MATCH " + (empty.WITH() != null ? "WITH UNMATCHED ROWS" : "OMIT EMPTY MATCHES");
    }

    private String afterMatch(final FrostlakeParser.AfterMatchSkipContext skip) {
        if (skip == null) {
            return "AFTER MATCH SKIP PAST LAST ROW";
        }
        final FrostlakeParser.RowPatternSkipTargetContext target = skip.rowPatternSkipTarget();
        if (target.NEXT() != null) {
            return "AFTER MATCH SKIP TO NEXT ROW";
        }
        if (target.TO() == null) {
            return "AFTER MATCH SKIP PAST LAST ROW";
        }
        return "AFTER MATCH SKIP TO " + (target.FIRST() != null ? "FIRST " : "LAST ") + symbol(target.identifier());
    }

    /** A pattern symbol's name; a quoted one is not modelled. */
    private String symbol(final FrostlakeParser.IdentifierContext name) {
        unmodelled = unmodelled || name.getText().startsWith("\"");
        return ParseTreeText.getIdentifier(name);
    }

    /** A pattern: its alternatives side by side in parentheses, a lone one as its sequence. */
    private String pattern(final FrostlakeParser.RowPatternContext ctx) {
        final List<FrostlakeParser.RowPatternSequenceContext> alternatives = ctx.rowPatternSequence();
        if (alternatives.size() == 1) {
            return sequence(alternatives.get(0));
        }
        final StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < alternatives.size(); i++) {
            text.append(i > 0 ? " | " : "").append(sequence(alternatives.get(i)));
        }
        return text.append(')').toString();
    }

    /**
     * A sequence: a lone term as itself, more in parentheses; an anchor at either end stands outside parentheses of
     * the terms between, the whole in parentheses again.
     */
    private String sequence(final FrostlakeParser.RowPatternSequenceContext ctx) {
        final List<FrostlakeParser.RowPatternTermContext> terms = ctx.rowPatternTerm();
        final boolean start = anchor(terms.get(0)) == FrostlakeParser.CARET;
        final boolean end = terms.size() > 1 && anchor(terms.get(terms.size() - 1)) == FrostlakeParser.DOLLAR;
        final int first = start ? 1 : 0;
        final int last = end ? terms.size() - 1 : terms.size();
        final StringBuilder core = new StringBuilder();
        for (int i = first; i < last; i++) {
            // An anchor anywhere else, or one alone, is not modelled.
            unmodelled = unmodelled || anchor(terms.get(i)) != 0;
            core.append(i > first ? " " : "").append(term(terms.get(i)));
        }
        unmodelled = unmodelled || first >= last;
        if (!start && !end) {
            return terms.size() == 1 ? core.toString() : "(" + core + ")";
        }
        return "(" + (start ? "^" : "") + "(" + core + ")" + (end ? "$" : "") + ")";
    }

    /** The token type of a term that is an anchor without a quantifier, or 0. */
    private static int anchor(final FrostlakeParser.RowPatternTermContext term) {
        final FrostlakeParser.RowPatternPrimaryContext primary = term.rowPatternPrimary();
        if (primary.CARET() != null || primary.DOLLAR() != null) {
            return term.rowPatternQuantifier() == null
                ? (primary.CARET() != null ? FrostlakeParser.CARET : FrostlakeParser.DOLLAR) : -1;
        }
        return 0;
    }

    private String term(final FrostlakeParser.RowPatternTermContext ctx) {
        return primary(ctx.rowPatternPrimary()) + quantifier(ctx.rowPatternQuantifier());
    }

    private String primary(final FrostlakeParser.RowPatternPrimaryContext ctx) {
        if (ctx.LBRACE() != null) {
            return "{-" + pattern(ctx.rowPattern(0)) + "-}";
        }
        if (ctx.LPAREN() != null && ctx.identifier() != null) {
            final StringBuilder text = new StringBuilder("PERMUTE(");
            for (int i = 0; i < ctx.rowPattern().size(); i++) {
                text.append(i > 0 ? ", " : "").append(pattern(ctx.rowPattern(i)));
            }
            return text.append(')').toString();
        }
        if (ctx.LPAREN() != null) {
            return pattern(ctx.rowPattern(0));
        }
        if (ctx.identifier() != null) {
            return symbol(ctx.identifier());
        }
        unmodelled = true;
        return "";
    }

    /** A quantifier: a count's bounds both written, an open upper one as 0; a reluctant one keeps its mark. */
    private String quantifier(final FrostlakeParser.RowPatternQuantifierContext ctx) {
        if (ctx == null) {
            return "";
        }
        final boolean reluctant = ctx.QUESTION().size() > (ctx.LBRACE() == null && ctx.STAR() == null
            && ctx.PLUS() == null ? 1 : 0);
        final String mark = reluctant ? "?" : "";
        if (ctx.LBRACE() == null) {
            return ctx.getChild(0).getText() + mark;
        }
        final List<TerminalNode> counts = ctx.INTEGER_LITERAL();
        if (ctx.COMMA() == null) {
            return "{" + counts.get(0).getText() + "," + counts.get(0).getText() + "}" + mark;
        }
        final int comma = ctx.COMMA().getSymbol().getTokenIndex();
        final boolean lower = counts.size() == 2
            || counts.size() == 1 && counts.get(0).getSymbol().getTokenIndex() < comma;
        final boolean upper = counts.size() == 2 || counts.size() == 1 && !lower;
        unmodelled = unmodelled || counts.isEmpty();
        final String from = lower ? counts.get(0).getText() : "0";
        final String to = upper ? counts.get(counts.size() - 1).getText() : "0";
        return "{" + from + "," + to + "}" + mark;
    }

    /** The window a definition's NEXT and LAST read: the clause's partitions and order, or null for none. */
    private static String window(final List<String> keys, final List<String> order) {
        if (order.isEmpty()) {
            return null;
        }
        return (keys.isEmpty() ? "" : "PARTITION BY " + String.join(", ", keys) + " ") + "ORDER BY "
            + String.join(", ", order);
    }

    private String definitions(final FrostlakeParser.MatchRecognizeClauseContext clause, final String window) {
        final ParsedEchoPrinter defining = ParsedEchoPrinter.inQuery(queries, new CallEcho() {
            @Override
            public String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
                final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
                if (!isUnqualified(call) || !"NEXT".equals(name) && !"LAST".equals(name)) {
                    return null;
                }
                if (call.getArguments().size() != 1 || window == null) {
                    unmodelled = true;
                    return null;
                }
                return ("NEXT".equals(name) ? "LEAD(" : "LAST_VALUE(") + printer.printed(call.getArguments().get(0))
                    + ") OVER (" + window + ")";
            }
        });
        final StringBuilder text = new StringBuilder();
        final List<FrostlakeParser.RowPatternDefinitionContext> definitions = clause.rowPatternDefinition();
        for (int i = 0; i < definitions.size(); i++) {
            final FrostlakeParser.RowPatternDefinitionContext definition = definitions.get(i);
            text.append(i > 0 ? ", " : "").append(symbol(definition.identifier())).append(" AS RUNNING IFNULL((")
                .append(new ExpressionAstBuilder().build(definition.booleanExpr()).accept(defining))
                .append(") = TRUE, FALSE)");
        }
        return text.toString();
    }
}
