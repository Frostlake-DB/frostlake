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
import dev.frostlake.parser.SyntaxErrorListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The correlated subqueries a DELETE's or an UPDATE's WHERE holds, judged as the account judges them when it compiles
 * the statement, before any row is read: as the WHERE of a query over the target alone (see
 * {@link CorrelationPlanJudgement}). So a target of no row, or of one, spares only what it spares that query, and an
 * EXPLAIN of the statement is refused at the same place: {@code DELETE FROM t WHERE id IN (SELECT MAX(v) + t.id FROM g
 * WHERE FALSE)} over an empty {@code t} is refused at the subquery's SELECT, and so is
 * {@code EXPLAIN UPDATE t SET y = 1 WHERE id = (SELECT v FROM g WHERE g.id = t.id)} over two rows (live-verified).
 *
 * <p>The WHERE is read in place: the statement's text is read again with everything before the WHERE replaced by a
 * query over the target, which stands where those words stood, so every token of the WHERE keeps its line and its
 * position. A statement reading other sources through FROM or USING keeps the judgement its rows make as they reach
 * the subquery.
 */
final class DmlCorrelationJudgement {

    private DmlCorrelationJudgement() {
    }

    /**
     * Refuses the statement for the first subquery of its WHERE the account cannot evaluate, as a query's plan does.
     *
     * @param executor the executor
     * @param table    the statement's target
     * @param target   the target's name as written
     * @param alias    the target's alias as written, or null
     * @param where    the statement's WHERE, or null
     */
    static void require(final QueryExecutor executor, final Table table, final FrostlakeParser.ObjectNameContext target,
                        final FrostlakeParser.IdentifierContext alias, final FrostlakeParser.WhereClauseContext where) {
        if (where == null || !holdsSubquery(where)) {
            return;
        }
        final String text = queryInPlace(target, alias, where);
        final FrostlakeParser.SqlScriptContext parsed = text == null ? null : SyntaxErrorListener.cleanParse(text);
        final FrostlakeParser.SelectStatementContext query = parsed == null ? null : firstQuery(parsed);
        if (query == null || query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null) {
            return;
        }
        final Map<String, Table> relations = new HashMap<>();
        relations.put((alias != null ? ParseTreeText.getIdentifier(alias) : table.getName()).toUpperCase(Locale.ROOT),
            table);
        final CorrelationPlanJudgement judgement = CorrelationPlanJudgement.of(executor, query,
            query.selectOperand(0).selectClause(), table, relations, null);
        final RuntimeException refused = judgement == null ? null : judgement.forDml().refusal();
        if (refused != null) {
            throw refused;
        }
    }

    /**
     * Refuses an UPDATE for the first anti-join of its SET values the account cannot evaluate (see
     * {@link NullableAntiJoins}): {@code UPDATE t SET b = (k NOT IN (SELECT k FROM i))} over a column holding a NULL
     * is refused at the query's SELECT, whether or not the target holds a row, and so is a keyed one over a query
     * that may answer NULL (see {@link NullableAntiJoins#keyedValueRefusal}) (live-verified).
     *
     * @param executor    the executor
     * @param table       the statement's target
     * @param alias       the target's alias as written, or null
     * @param assignments the SET list
     */
    static void requireAntiJoins(final QueryExecutor executor, final Table table,
                                 final FrostlakeParser.IdentifierContext alias,
                                 final List<FrostlakeParser.AssignmentContext> assignments) {
        final List<ParserRuleContext> values = new ArrayList<>();
        for (final FrostlakeParser.AssignmentContext assignment : assignments) {
            values.add(assignment.expression());
        }
        final String name = (alias != null ? ParseTreeText.getIdentifier(alias) : table.getName())
            .toUpperCase(Locale.ROOT);
        final Set<String> outerNames = new HashSet<>();
        for (final TableColumn column : table.getColumns()) {
            final String upper = column.getName().toUpperCase(Locale.ROOT);
            outerNames.add(upper);
            outerNames.add(name + "." + upper);
        }
        final Map<String, Table> relations = new HashMap<>();
        relations.put(name, table);
        final NullableAntiJoins judged = new NullableAntiJoins(executor,
            new CorrelatedSubqueryRule(executor, outerNames, relations, null), relations);
        RuntimeException refused = judged.refusal(values);
        if (refused == null) {
            refused = judged.keyedValueRefusal(values);
        }
        if (refused != null) {
            throw refused;
        }
    }

    /**
     * Refuses a DELETE or an UPDATE for the first NOT IN or {@code <> ALL} of its WHERE that the account cannot
     * evaluate over a target of more than one row (see {@link NullableAntiJoins#refusesKeyedInDml}): {@code UPDATE t
     * SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k)} over a {@code val} holding a NULL is refused
     * at the query's SELECT, with or without a FROM or a USING, where the same filter of a query is answered
     * (live-verified).
     *
     * @param executor the executor
     * @param table    the statement's target
     * @param alias    the target's alias as written, or null
     * @param where    the statement's WHERE, or null
     */
    static void requireKeyedAntiJoins(final QueryExecutor executor, final Table table,
                                      final FrostlakeParser.IdentifierContext alias,
                                      final FrostlakeParser.WhereClauseContext where) {
        if (where == null || !holdsSubquery(where)) {
            return;
        }
        final long rows = executor.storedRowCount(table.getQualifiedName());
        if (rows >= 0 && rows <= 1) {
            return;   // a target of one row at most is read row by row
        }
        final List<ParserRuleContext> antiJoins = new ArrayList<>();
        NullableAntiJoins.collect(where.booleanExpr(), antiJoins);
        if (antiJoins.isEmpty()) {
            return;
        }
        final String name = (alias != null ? ParseTreeText.getIdentifier(alias) : table.getName())
            .toUpperCase(Locale.ROOT);
        final Set<String> outerNames = new HashSet<>();
        for (final TableColumn column : table.getColumns()) {
            final String upper = column.getName().toUpperCase(Locale.ROOT);
            outerNames.add(upper);
            outerNames.add(name + "." + upper);
        }
        final Map<String, Table> relations = new HashMap<>();
        relations.put(name, table);
        final NullableAntiJoins judged = new NullableAntiJoins(executor,
            new CorrelatedSubqueryRule(executor, outerNames, relations, null), relations);
        if (neverTrue(where.booleanExpr(), judged)) {
            return;   // the WHERE folds to FALSE, and nothing of it is planned
        }
        for (final ParserRuleContext antiJoin : antiJoins) {
            if (judged.refusesKeyedInDml(antiJoin)) {
                throw NullableAntiJoins.placed(NullableAntiJoins.anchor(antiJoin));
            }
        }
    }

    /**
     * Whether a conjunct of the WHERE, through AND and parentheses, is an anti-join no row passes (see
     * {@link NullableAntiJoins#neverTrue}): beside {@code AND t.id NOT IN (SELECT val FROM i)} over a {@code val}
     * holding a NULL, a keyed NOT IN is answered (live-verified).
     */
    private static boolean neverTrue(final FrostlakeParser.BooleanExprContext condition,
                                     final NullableAntiJoins judged) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) condition;
            return neverTrue(and.booleanExpr(0), judged) || neverTrue(and.booleanExpr(1), judged);
        }
        if (!(condition instanceof FrostlakeParser.ValueExprContext)) {
            return false;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) condition).expression();
        if (value instanceof FrostlakeParser.ParenExprContext) {
            return neverTrue(((FrostlakeParser.ParenExprContext) value).booleanExpr(), judged);
        }
        return judged.neverTrue(value);
    }

    /** Whether the WHERE holds a query at all, the only thing the judgement reads. */
    private static boolean holdsSubquery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsSubquery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The statement's text up to its WHERE's end, everything before the WHERE blanked but its line breaks and a query
     * over the target written into the first stretch that holds it; null where no stretch does.
     */
    private static String queryInPlace(final FrostlakeParser.ObjectNameContext target,
                                       final FrostlakeParser.IdentifierContext alias,
                                       final FrostlakeParser.WhereClauseContext where) {
        final CharStream input = where.getStart().getInputStream();
        final int whereStart = where.getStart().getStartIndex();
        final int whereStop = where.getStop().getStopIndex();
        if (input == null || whereStart < 0 || whereStop < whereStart) {
            return null;
        }
        final String standIn = "SELECT*FROM " + written(target) + (alias != null ? " " + written(alias) : "");
        final char[] text = input.getText(Interval.of(0, whereStop)).toCharArray();
        int run = 0;
        int placedAt = -1;
        for (int i = 0; i < whereStart; i++) {
            if (text[i] == '\n' || text[i] == '\r') {
                run = 0;
                continue;
            }
            text[i] = ' ';
            run++;
            if (placedAt < 0 && run == standIn.length() + 1) {
                placedAt = i - standIn.length();
            }
        }
        if (placedAt < 0) {
            return null;
        }
        standIn.getChars(0, standIn.length(), text, placedAt);
        return new String(text);
    }

    private static String written(final ParserRuleContext node) {
        return node.getStart().getInputStream().getText(
            Interval.of(node.getStart().getStartIndex(), node.getStop().getStopIndex()));
    }

    private static FrostlakeParser.SelectStatementContext firstQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return (FrostlakeParser.SelectStatementContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.SelectStatementContext found = firstQuery(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
