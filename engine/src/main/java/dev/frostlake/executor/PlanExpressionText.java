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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.BetweenExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.DateTimeUnitSlot;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.InExpression;
import dev.frostlake.executor.expressions.IntervalUnit;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.executor.expressions.WindowFunctionExpression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.TypeCategory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Spells an expression as an EXPLAIN plan lists it: every column resolved and named by its relation, {@code ET.A}, and
 * the expression in the form the account plans it (live-verified).
 *
 * <ul>
 *   <li>The operands of AND and OR are each in parentheses, a run of one of them listed flat — {@code (ET.A > 1) AND
 *       (ET.B = 'x') AND (ET.C IS NULL)} — and NOT encloses its operand, {@code NOT(ET.A = 1)}.</li>
 *   <li>An operand of a comparison or of arithmetic is in parentheses when it is an operation or a scalar call,
 *       {@code (ET.A + 1) > 2}, {@code (UPPER(ET.B)) = 'X'}, and when the operation converts it: a number with fewer
 *       decimals than the other operand of a sum, a difference or a comparison, {@code (ET.A) + ET.C}, both factors
 *       of a product that both carry decimals and both terms of a division. A literal never is, nor an aggregate or
 *       a window the operator below computes, {@code COUNT(*) > 1}. A division of whole numbers is
 *       {@code SCALED_ROUND_INT_DIVIDE(ET.A, 2)}, and a concatenation is listed flat, {@code 'x' || ET.B || 'y'}.</li>
 *   <li>IN is {@code ET.A IN 1 IN 2}, and with one value the equality, and a run of OR over equalities of one column
 *       with literals is that IN; BETWEEN is the two comparisons it stands for; LIKE with a pattern that is a prefix,
 *       a suffix or an infix and nothing else is STARTSWITH, ENDSWITH and CONTAINS, and with no wildcard the
 *       equality.</li>
 *   <li>A searched CASE is {@code CASE_FLATTENED(condition, result, …, otherwise)} and a simple one is written out,
 *       each with its ELSE {@code null} when it has none.</li>
 *   <li>A conversion is the function it stands for, {@code TO_CHAR(ET.A)}, {@code TRY_TO_NUMBER(ET.B)}, and none at
 *       all between two numbers, two texts or two dates; a literal converted to a date is the literal.</li>
 *   <li>AVG is the sum over the count, {@code SCALED_ROUND_INT_DIVIDE(SUM(ET.A), COUNT(ET.A))} for whole numbers;
 *       COALESCE stops at its first literal and of two values is IFNULL; LISTAGG lists its value alone; YEAR is
 *       {@code EXTRACT(year from x)}; DATEADD of a whole amount is {@code DATE_ADDDAYSTODATE(1, ET.D)} and DATEDIFF
 *       of two values of one kind {@code DATE_DIFFDATEINDAYS(ET.D, ET.E)}, each kin of theirs alike.</li>
 *   <li>A window is {@code F(x) OVER (PARTITION BY p ORDER BY k ASC NULLS LAST)}, its frame left out.</li>
 *   <li>A call of a procedure's SYSTEM$REFERENCE token is the procedure over its parameters' names,
 *       {@code PR_ARG(X)}.</li>
 *   <li>Literals are printed as values, {@code 1.5}, {@code 1000}, {@code 'it''s'}, {@code null}; function names are
 *       upper-cased.</li>
 * </ul>
 *
 * <p>The printer records what it resolves: the scan columns the expression reads, the relations its columns belong
 * to, the aggregate calls the Aggregate below computes for it and the window calls it holds.
 */
final class PlanExpressionText extends AstPrinterVisitor {

    private final PlanScope scope;
    private final FunctionRegistry functions;
    private final IdentifierCallText identifierCalls;
    private final PlanConstants constants;
    /** The arguments a SQL function's inlined body reads by its parameters' names, innermost body first. */
    private final Deque<Map<String, Expression>> boundParameters = new ArrayDeque<>();
    /** Each constant part's folded text, or the empty text for a part that is no constant, as looked up once. */
    private final Map<Expression, String> foldedParts = new IdentityHashMap<>();
    private final List<PlanRead> reads = new ArrayList<>();
    private final Set<String> owners = new LinkedHashSet<>();
    private final List<String> aggregateKeys = new ArrayList<>();
    private final List<List<String>> aggregateTerms = new ArrayList<>();
    private final List<String> windows = new ArrayList<>();
    /** The references spelled as a sequence read, which stand in parentheses as an operand. */
    private final Set<Expression> sequenceReads = Collections.newSetFromMap(new IdentityHashMap<Expression, Boolean>());
    private boolean unresolved;

    /**
     * @param scope           the names the expression is read in, or null to resolve none
     * @param functions       the functions, which tell an aggregate
     * @param identifierCalls what a call named by IDENTIFIER is written as
     */
    PlanExpressionText(final PlanScope scope, final FunctionRegistry functions,
                       final IdentifierCallText identifierCalls, final PlanConstants constants) {
        this.scope = scope;
        this.functions = functions;
        this.identifierCalls = identifierCalls;
        this.constants = constants;
    }

    /**
     * An expression as the plan spells it.
     *
     * @param expression the expression
     * @return the text
     */
    String spell(final Expression expression) {
        final String folded = folded(expression);
        return folded != null ? folded : expression.accept(this);
    }

    /** A constant part folded to its value's text (see {@link PlanConstantFold}), or null for any other part. */
    private String folded(final Expression expression) {
        if (constants == null || expression == null) {
            return null;
        }
        String folded = foldedParts.get(expression);
        if (folded == null) {
            final String computed = constants.folded(expression);
            folded = computed == null ? "" : computed;
            foldedParts.put(expression, folded);
        }
        return folded.isEmpty() ? null : folded;
    }

    /**
     * A condition's conjuncts as a Filter lists them: one as it is, several each in parentheses joined with AND.
     *
     * @param conjuncts the conjuncts' texts
     * @return the text
     */
    static String conjoined(final List<String> conjuncts) {
        if (conjuncts.size() == 1) {
            return conjuncts.get(0);
        }
        final StringBuilder text = new StringBuilder();
        for (final String conjunct : conjuncts) {
            text.append(text.length() == 0 ? "" : " AND ").append('(').append(conjunct).append(')');
        }
        return text.toString();
    }

    List<PlanRead> getReads() {
        return reads;
    }

    /** The relations the columns read belong to, upper-cased. */
    Set<String> getOwners() {
        return owners;
    }

    /** Each aggregate call met, by its text, in the order met. */
    List<String> getAggregateKeys() {
        return aggregateKeys;
    }

    /** What each aggregate call met is computed as: itself, or for AVG its sum and its count. */
    List<List<String>> getAggregateTerms() {
        return aggregateTerms;
    }

    List<String> getWindows() {
        return windows;
    }

    /** Whether a column was met that the names do not resolve, so the columns read are not all known. */
    boolean isUnresolved() {
        return unresolved;
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        final Expression bound = boundParameters.isEmpty() || expr.isQualified() ? null
            : boundParameters.peek().get(expr.getColumnName().toUpperCase(Locale.ROOT));
        if (bound != null) {
            // The argument is written as the call around the body reads it.
            final Map<String, Expression> body = boundParameters.pop();
            try {
                return spell(bound);
            } finally {
                boundParameters.push(body);
            }
        }
        final PlanColumn column = scope == null ? null : scope.resolve(expr);
        if (column != null) {
            reads.addAll(column.getReads());
            windows.addAll(column.getWindows());
            if (column.getOwner() != null) {
                owners.add(column.getOwner());
            }
            return column.getText();
        }
        if (scope != null && expr.isQualified() && "NEXTVAL".equalsIgnoreCase(expr.getColumnName())) {
            // A sequence read is named by the Getnextval that answers it (see PlanScope.readSequences).
            final String[] qualifier = QualifiedName.parse(expr.getTableName()).parts();
            final String read = scope.nextSequenceRead(qualifier[qualifier.length - 1]);
            if (read != null) {
                sequenceReads.add(expr);
                return "\"" + read + "\".NEXTVAL";
            }
        }
        unresolved = true;
        final String name = SqlIdentifiers.spellCanonical(expr.getColumnName());
        if (!expr.isQualified()) {
            return name;
        }
        final String qualifier = expr.getTableName();
        final int dot = qualifier.lastIndexOf('.');
        return SqlIdentifiers.spellCanonical(dot < 0 ? qualifier : qualifier.substring(dot + 1)) + "." + name;
    }

    @Override
    public String visitLiteral(final LiteralExpression expr) {
        final Object value = expr.getValue();
        switch (expr.getType()) {
            case NULL:
                return "null";
            case STRING:
                return quoted(String.valueOf(value));
            case BOOLEAN:
                return String.valueOf(value).toUpperCase(Locale.ROOT);
            case INTEGER:
            case DECIMAL:
                return numberText(value);
            default:
                return super.visitLiteral(expr);
        }
    }

    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        final BinaryOperator operator = expr.getOperator();
        final Expression left = expr.getLeft();
        final Expression right = expr.getRight();
        switch (operator) {
            case AND:
            case OR:
                return chain(expr, operator);
            case CONCAT:
                return concatenation(expr);
            case LIKE:
            case ILIKE:
            case NOT_LIKE:
            case NOT_ILIKE:
                return like(expr);
            case DIVIDE:
                if (isWhole(left) && isWhole(right) && !isCountingWindow(left) && !isCountingWindow(right)) {
                    return "SCALED_ROUND_INT_DIVIDE(" + spell(left) + ", " + spell(right) + ")";
                }
                return operand(left, true) + " / " + operand(right, true);
            case MULTIPLY: {
                final boolean converted = !isLiteral(left) && !isLiteral(right) && positive(scaleOf(left))
                    && positive(scaleOf(right));
                return term(left, converted || widened(left, right, '*', false), '*', right) + " * "
                    + term(right, converted || widened(right, left, '*', true), '*', left);
            }
            case ADD:
            case SUBTRACT:
            case MODULO:
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
                return aligned(left, symbolOf(expr), right);
            default:
                return operand(left, false) + " " + symbolOf(expr) + " " + operand(right, false);
        }
    }

    @Override
    public String visitUnaryOperation(final UnaryOperationExpression expr) {
        final Expression operand = expr.getOperand();
        if (expr.getOperator() == UnaryOperator.NOT) {
            return "NOT(" + spell(operand) + ")";
        }
        if (expr.getOperator() == UnaryOperator.NEGATE) {
            return isNumber(operand) ? "-" + spell(operand) : "NEGATE(" + spell(operand) + ")";
        }
        if (expr.getOperator() == UnaryOperator.PLUS) {
            return spell(operand);
        }
        return super.visitUnaryOperation(expr);
    }

    @Override
    public String visitIsNull(final IsNullExpression expr) {
        return operand(expr.getOperand(), false) + (expr.isNot() ? " IS NOT NULL" : " IS NULL");
    }

    @Override
    public String visitIn(final InExpression expr) {
        final String text;
        if (expr.hasSubquery()) {
            text = operand(expr.getValue(), false) + " IN " + spell(expr.getSubquery());
        } else if (expr.getValues().size() == 1) {
            text = aligned(expr.getValue(), "=", expr.getValues().get(0));
        } else {
            final StringBuilder listed = new StringBuilder(operand(expr.getValue(), false));
            for (final Expression value : expr.getValues()) {
                listed.append(" IN ").append(operand(value, false));
            }
            text = listed.toString();
        }
        return expr.isNot() ? "NOT(" + text + ")" : text;
    }

    @Override
    public String visitBetween(final BetweenExpression expr) {
        final String text = "(" + aligned(expr.getValue(), ">=", expr.getLower()) + ") AND ("
            + aligned(expr.getValue(), "<=", expr.getUpper()) + ")";
        return expr.isNot() ? "NOT(" + text + ")" : text;
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String identified = expr.getNameExpression() == null ? null : identifierCalls.spell(expr);
        if (identified != null) {
            // The arguments are read all the same, their columns the scan's.
            final List<String> argumentTexts = new ArrayList<>();
            for (final Expression argument : expr.getArguments()) {
                argumentTexts.add(spell(argument));
            }
            identifierCalls.requireArguments(expr, argumentTexts);
            return identified;
        }
        if (expr.getFunctionName() == null || expr.getNameExpression() != null) {
            return super.visitFunctionCall(expr);
        }
        final InlinedFunctionBody inlined = constants == null ? null : constants.inlined(expr);
        if (inlined != null) {
            // A SQL function's call is written as its body, each parameter read as its argument (see
            // PlanConstantFold).
            final Map<String, Expression> bound = new HashMap<>();
            final List<String> parameters = inlined.getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                bound.put(parameters.get(i), expr.getArguments().get(i));
            }
            boundParameters.push(bound);
            try {
                return spell(inlined.getBody());
            } finally {
                boundParameters.pop();
            }
        }
        final String name = expr.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = expr.getArguments();
        final boolean aggregate = isAggregate(expr);
        if (aggregate && "AVG".equals(name) && !expr.isDistinct() && args.size() == 1) {
            final String value = spell(args.get(0));
            final String sum = "SUM(" + value + ")";
            final String count = "COUNT(" + value + ")";
            final List<String> terms = new ArrayList<>();
            terms.add(sum);
            terms.add(count);
            aggregateKeys.add("AVG(" + value + ")");
            aggregateTerms.add(terms);
            return isWhole(args.get(0)) ? "SCALED_ROUND_INT_DIVIDE(" + sum + ", " + count + ")"
                : "(" + sum + ") / (" + count + ")";
        }
        final String planned = "DATEADD".equals(name) || "TIMEADD".equals(name) || "TIMESTAMPADD".equals(name)
            ? dateAdd(args) : "DATEDIFF".equals(name) || "TIMEDIFF".equals(name) || "TIMESTAMPDIFF".equals(name)
                ? dateDiff(args) : null;
        final String text;
        if (planned != null) {
            text = planned;
        } else if ("COALESCE".equals(name) && !expr.isStar()) {
            text = coalesced(args);
        } else if ("LISTAGG".equals(name) && !args.isEmpty()) {
            text = "LISTAGG(" + (expr.isDistinct() ? "DISTINCT " : "") + spell(args.get(0)) + ")";
        } else if ("YEAR".equals(name) && args.size() == 1) {
            text = "EXTRACT(year from " + spell(args.get(0)) + ")";
        } else if ("COUNT_IF".equals(name) && args.size() == 1 && !expr.isDistinct()) {
            text = countedIf(args.get(0));
        } else {
            final StringBuilder called = new StringBuilder(name).append('(');
            if (expr.isDistinct()) {
                called.append("DISTINCT ");
            }
            if (expr.isStar()) {
                called.append('*');
            } else {
                final List<String> names = expr.getArgumentNames();
                for (int i = 0; i < args.size(); i++) {
                    called.append(i > 0 ? ", " : "");
                    if (names != null && names.get(i) != null) {
                        called.append(names.get(i)).append(" => ");
                    }
                    called.append(spell(args.get(i)));
                }
            }
            text = called.append(')').toString();
        }
        if (aggregate) {
            final List<String> terms = new ArrayList<>();
            terms.add(text);
            aggregateKeys.add(text);
            aggregateTerms.add(terms);
        }
        return text;
    }

    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        if (expr.getFunctionName() == null || expr.getPartitionKeys() == null || expr.getArguments() == null) {
            final String written = expr.getCallText();
            windows.add(written);
            return written;
        }
        final String name = expr.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> order = expr.getOrderKeys();
        // A ROWS frame of the whole partition is read in no order (see WindowFunctionExpression#isWholeRowsFramed).
        final boolean ordered = order != null && !order.isEmpty() && !expr.isWholeRowsFramed();
        final StringBuilder text = new StringBuilder();
        if ("COUNT_IF".equals(name) && expr.getArguments().size() == 1 && !expr.isDistinct()) {
            text.append(countedIf(expr.getArguments().get(0)));
        } else {
            text.append(name).append('(');
            if (expr.isDistinct()) {
                text.append("DISTINCT ");
            }
            if (expr.getStarCall() != null) {
                // A count of rows over an ordered window counts a constant (live-verified).
                text.append("COUNT".equals(name) && ordered ? "1" : "*");
            } else {
                for (int i = 0; i < expr.getArguments().size(); i++) {
                    text.append(i > 0 ? ", " : "").append(spell(expr.getArguments().get(i)));
                }
            }
            text.append(')');
        }
        text.append(" OVER (");
        final List<Expression> partition = expr.getPartitionKeys();
        if (!partition.isEmpty()) {
            text.append("PARTITION BY ");
            for (int i = 0; i < partition.size(); i++) {
                text.append(i > 0 ? ", " : "").append(spell(partition.get(i)));
            }
        }
        if (ordered) {
            text.append(partition.isEmpty() ? "" : " ").append("ORDER BY ");
            // A frame of the whole partition prints its order and reads its rows, but lists no column for it, and a
            // frame the plan turns about prints it reversed (live-verified; see
            // WindowFunctionExpression#isOrderReversedInPlan).
            final int readsBefore = reads.size();
            final boolean reversed = expr.isOrderReversedInPlan();
            for (int i = 0; i < order.size(); i++) {
                final boolean ascending = expr.getOrderAscending() == null
                    || !Boolean.FALSE.equals(expr.getOrderAscending().get(i));
                final Boolean nullsFirst = expr.getOrderNullsFirst() == null ? null : expr.getOrderNullsFirst().get(i);
                text.append(i > 0 ? ", " : "").append(sortKey(spell(order.get(i)), reversed != ascending,
                    nullsFirst == null || !reversed ? nullsFirst : Boolean.valueOf(!nullsFirst.booleanValue())));
            }
            for (int i = readsBefore; expr.isWholeFramed() && i < reads.size(); i++) {
                reads.set(i, reads.get(i).unlisted());
            }
        }
        final String spelled = text.append(')').toString();
        windows.add(spelled);
        return spelled;
    }

    /** A COUNT_IF as the plan counts it, a sum of one for each row its condition holds for (live-verified). */
    private String countedIf(final Expression condition) {
        return "SUM(IFF(" + spell(condition) + ", 1, 0))";
    }

    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        final List<WhenClause> whens = expr.getWhenClauses();
        final String otherwise = expr.getElseExpression() == null ? "null" : spell(expr.getElseExpression());
        if (isSimpleCase(whens)) {
            final StringBuilder text = new StringBuilder("CASE ")
                .append(spell(((BinaryOperationExpression) whens.get(0).getCondition()).getLeft()));
            for (final WhenClause when : whens) {
                text.append(" WHEN ").append(spell(((BinaryOperationExpression) when.getCondition()).getRight()))
                    .append(" THEN ").append(spell(when.getResult()));
            }
            return text.append(" ELSE ").append(otherwise).append(" END").toString();
        }
        final StringBuilder text = new StringBuilder("CASE_FLATTENED(");
        for (final WhenClause when : whens) {
            text.append(spell(when.getCondition())).append(", ").append(spell(when.getResult())).append(", ");
        }
        return text.append(otherwise).append(')').toString();
    }

    @Override
    public String visitCast(final CastExpression expr) {
        final Expression value = expr.getExpression();
        final String target = familyOf(expr.getTargetDataType());
        if (folds(value, target, expr)) {
            return spell(value);
        }
        if (target != null && target.equals(familyOf(value))) {
            return spell(value);
        }
        final String conversion = conversionOf(expr, target);
        if (conversion == null) {
            return "CAST(" + spell(value) + " AS " + expr.getTargetType() + ")";
        }
        return (expr.isTryMode() ? "TRY_" : "") + conversion + "(" + spell(value) + ")";
    }

    /**
     * A condition's conjuncts as a Filter lists them: a BETWEEN as the two comparisons it stands for, any other
     * condition as itself.
     *
     * @param condition the condition
     * @return the conjuncts' texts
     */
    List<String> conjuncts(final Expression condition) {
        final List<String> texts = new ArrayList<>();
        if (condition instanceof BetweenExpression && !((BetweenExpression) condition).isNot()) {
            final BetweenExpression between = (BetweenExpression) condition;
            texts.add(aligned(between.getValue(), ">=", between.getLower()));
            texts.add(aligned(between.getValue(), "<=", between.getUpper()));
        } else {
            texts.add(spell(condition));
        }
        return texts;
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        final Expression item = constants == null ? null : constants.inlinedItem(expr);
        return item != null ? spell(item) : "(" + expr.getSubquery() + ")";
    }

    /** A run of AND, or of OR, listed flat with each operand in parentheses. */
    private String chain(final BinaryOperationExpression expr, final BinaryOperator operator) {
        final List<Expression> operands = new ArrayList<>();
        flatten(expr, operator, operands);
        final String listed = operator == BinaryOperator.OR ? inList(operands) : null;
        if (listed != null) {
            return listed;
        }
        final StringBuilder text = new StringBuilder();
        for (final Expression operand : operands) {
            text.append(text.length() == 0 ? "" : " " + operator.name() + " ").append('(').append(spell(operand))
                .append(')');
        }
        return text.toString();
    }

    /** A run of OR over equalities of one column with literals, as the IN the plan holds it as; null otherwise. */
    private String inList(final List<Expression> operands) {
        String subject = null;
        final StringBuilder values = new StringBuilder();
        for (final Expression operand : operands) {
            if (!(operand instanceof BinaryOperationExpression)
                    || ((BinaryOperationExpression) operand).getOperator() != BinaryOperator.EQUAL) {
                return null;
            }
            final BinaryOperationExpression equality = (BinaryOperationExpression) operand;
            if (!(equality.getLeft() instanceof ColumnReferenceExpression) || !isLiteral(equality.getRight())) {
                return null;
            }
            final String column = spell(equality.getLeft());
            if (subject != null && !subject.equals(column)) {
                return null;
            }
            subject = column;
            values.append(" IN ").append(spell(equality.getRight()));
        }
        return subject + values;
    }

    /**
     * DATEADD, TIMEADD and TIMESTAMPADD of a whole amount as the plan names them,
     * {@code DATE_ADD<UNITS>TO<KIND>(amount, target)}: a DATE moved by days or longer, a TIME by its own units, any
     * timestamp by any unit. Null for another shape, which keeps its written name.
     */
    private String dateAdd(final List<Expression> args) {
        if (args.size() != 3) {
            return null;
        }
        final IntervalUnit unit = unitOf(args.get(0));
        final String kind = temporalKind(typeOf(args.get(2)));
        if (unit == null || kind == null || !isWhole(args.get(1))
                || "DATE".equals(kind) && !unit.isWholeDay() || "TIME".equals(kind) && unit.isWholeDay()) {
            return null;
        }
        return "DATE_ADD" + unit.shiftName() + "TO" + kind + "(" + spell(args.get(1)) + ", " + spell(args.get(2))
            + ")";
    }

    /**
     * DATEDIFF, TIMEDIFF and TIMESTAMPDIFF of two values of one type as the plan names them,
     * {@code DATE_DIFF<KIND>IN<UNITS>(from, to)}. Null for another shape, which keeps its written name.
     */
    private String dateDiff(final List<Expression> args) {
        if (args.size() != 3) {
            return null;
        }
        final IntervalUnit unit = unitOf(args.get(0));
        final DataType from = typeOf(args.get(1));
        final DataType to = typeOf(args.get(2));
        final String kind = temporalKind(from);
        if (unit == null || kind == null || to == null || !from.getName().equals(to.getName())) {
            return null;
        }
        return "DATE_DIFF" + kind + "IN" + unit.name() + "S(" + spell(args.get(1)) + ", " + spell(args.get(2)) + ")";
    }

    /** The unit a date function's first argument names; null for one it does not name. */
    private static IntervalUnit unitOf(final Expression argument) {
        final String written = DateTimeUnitSlot.unitTextOf(argument);
        if (written == null || SharedFunctionHelpers.isComponentOnlyUnit(written)) {
            return null;
        }
        return IntervalUnit.fromSpelling(SharedFunctionHelpers.canonicalDateUnit(written));
    }

    /** The kind of a date and time type a planned shift names, DATE, TIME or TIMESTAMP; null for any other. */
    private static String temporalKind(final DataType type) {
        if (type == null || type.getCategory() != TypeCategory.DATE_TIME) {
            return null;
        }
        return "DATE".equals(type.getName()) || "TIME".equals(type.getName()) ? type.getName() : "TIMESTAMP";
    }

    /** The type an expression answers where the plan knows it: a column's and a conversion's; null otherwise. */
    private DataType typeOf(final Expression expression) {
        if (expression instanceof ColumnReferenceExpression) {
            final PlanColumn column = scope == null ? null : scope.resolve((ColumnReferenceExpression) expression);
            return column == null ? null : column.getType();
        }
        if (expression instanceof WindowFunctionExpression) {
            return windowType((WindowFunctionExpression) expression);
        }
        if (expression instanceof FunctionCallExpression && isAggregate((FunctionCallExpression) expression)) {
            // A sum, a mean or an extreme of a FLOAT is a FLOAT, as the window is.
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            final DataType argument = call.getArguments().size() == 1 && call.getQuantifier() == null
                && ("SUM".equals(name) || "AVG".equals(name) || "MIN".equals(name) || "MAX".equals(name))
                ? typeOf(call.getArguments().get(0)) : null;
            return "FLOAT".equals(familyOf(argument)) ? argument : null;
        }
        return expression instanceof CastExpression ? ((CastExpression) expression).getTargetDataType() : null;
    }

    /**
     * The type of a window's value where the plan's spelling turns on it: a sum, a mean or an extreme of a FLOAT is a
     * FLOAT, so a number written beside it is a FLOAT literal, SUM(T.D) OVER () + 1.0 (live-verified); null for any
     * other window.
     */
    private DataType windowType(final WindowFunctionExpression window) {
        final String name = window.getFunctionName() == null ? "" : window.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> arguments = window.getArguments();
        if (arguments == null || arguments.size() != 1 || window.isDistinct()
                || !("SUM".equals(name) || "AVG".equals(name) || "MIN".equals(name) || "MAX".equals(name))) {
            return null;
        }
        final DataType argument = typeOf(arguments.get(0));
        return "FLOAT".equals(familyOf(argument)) ? argument : null;
    }

    private static void flatten(final Expression expression, final BinaryOperator operator,
                                final List<Expression> into) {
        if (expression instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) expression).getOperator() == operator) {
            flatten(((BinaryOperationExpression) expression).getLeft(), operator, into);
            flatten(((BinaryOperationExpression) expression).getRight(), operator, into);
            return;
        }
        into.add(expression);
    }

    private String concatenation(final BinaryOperationExpression expr) {
        final List<Expression> operands = new ArrayList<>();
        flatten(expr, BinaryOperator.CONCAT, operands);
        final StringBuilder text = new StringBuilder();
        for (final Expression operand : operands) {
            // An aggregate of the query's own Aggregate stands in parentheses here as in arithmetic.
            final boolean aggregate = operand instanceof FunctionCallExpression
                && isAggregate((FunctionCallExpression) operand) && !isCompound(operand);
            text.append(text.length() == 0 ? "" : " || ").append(operand(operand, aggregate));
        }
        return text.toString();
    }

    private String like(final BinaryOperationExpression expr) {
        final BinaryOperator operator = expr.getOperator();
        final boolean negated = operator == BinaryOperator.NOT_LIKE || operator == BinaryOperator.NOT_ILIKE;
        final boolean caseless = operator == BinaryOperator.ILIKE || operator == BinaryOperator.NOT_ILIKE;
        final Expression pattern = expr.getRight();
        String text = null;
        if (!caseless && expr.getEscape() == null && pattern instanceof LiteralExpression
                && ((LiteralExpression) pattern).getType() == LiteralType.STRING) {
            text = likeFunction(expr.getLeft(), String.valueOf(((LiteralExpression) pattern).getValue()));
        }
        if (text == null) {
            text = operand(expr.getLeft(), false) + (caseless ? " ILIKE " : " LIKE ") + spell(pattern)
                + (expr.getEscape() == null ? "" : " ESCAPE " + spell(expr.getEscape()));
        }
        return negated ? "NOT(" + text + ")" : text;
    }

    /** What a LIKE of a literal pattern is planned as, or null for a pattern it keeps. */
    private String likeFunction(final Expression subject, final String pattern) {
        if (pattern.indexOf('\\') >= 0) {
            return null;
        }
        final String inner = pattern.length() < 2 ? "" : pattern.substring(1, pattern.length() - 1);
        if (!pattern.contains("%") && !pattern.contains("_")) {
            return operand(subject, false) + " = " + quoted(pattern);
        }
        if (pattern.length() < 2 || pattern.contains("_") || inner.contains("%")) {
            return null;
        }
        final boolean leading = pattern.charAt(0) == '%';
        final boolean trailing = pattern.charAt(pattern.length() - 1) == '%';
        if (leading && trailing) {
            return "CONTAINS(" + spell(subject) + ", " + quoted(inner) + ")";
        }
        if (trailing) {
            return "STARTSWITH(" + spell(subject) + ", " + quoted(pattern.substring(0, pattern.length() - 1)) + ")";
        }
        if (leading) {
            return "ENDSWITH(" + spell(subject) + ", " + quoted(pattern.substring(1)) + ")";
        }
        return null;
    }

    /**
     * A sum, a difference, a remainder or a comparison, the operand with fewer decimals converted to the other's.
     */
    private String aligned(final Expression left, final String symbol, final Expression right) {
        // A number written beside a FLOAT in a sum or a difference is the FLOAT literal it becomes: FZF.F + 1.0
        // (live-verified).
        if (("+".equals(symbol) || "-".equals(symbol)) && "FLOAT".equals(familyOf(typeOf(left)))
                && floatLiteral(right) != null) {
            return term(left, false, symbol.charAt(0), right) + " " + symbol + " " + floatLiteral(right);
        }
        if (("+".equals(symbol) || "-".equals(symbol)) && "FLOAT".equals(familyOf(typeOf(right)))
                && floatLiteral(left) != null) {
            return floatLiteral(left) + " " + symbol + " " + term(right, false, symbol.charAt(0), left);
        }
        final Integer leftScale = scaleOf(left);
        final Integer rightScale = scaleOf(right);
        final boolean known = leftScale != null && rightScale != null;
        final boolean arithmetic = "+".equals(symbol) || "-".equals(symbol);
        return term(left, known && leftScale.intValue() < rightScale.intValue()
                || arithmetic && widened(left, right, symbol.charAt(0), false), symbol.charAt(0), right)
            + " " + symbol + " "
            + term(right, known && rightScale.intValue() < leftScale.intValue()
                || arithmetic && widened(right, left, symbol.charAt(0), true), symbol.charAt(0), left);
    }

    /**
     * An operand of a sum, a difference, a product, a remainder or a comparison, in parentheses where the account
     * converts it to the width the operation is worked in (live-verified). The account stores whole numbers in the
     * fewest bytes their values need - one, two, four, eight or sixteen - and:
     * <ul>
     *   <li>an aggregate of the query's own Aggregate always stands in parentheses in arithmetic, and in a
     *       comparison unless it is a count: {@code (SUM(T.A)) + 1}, {@code (COUNT(*)) * 2}, {@code COUNT(*) = 1};</li>
     *   <li>a counting or ranking window - COUNT, ROW_NUMBER, RANK, DENSE_RANK, NTILE - is converted in a sum, a
     *       difference and a product, and so is what stands beside it: {@code (COUNT(*) OVER ()) + (T.N)};</li>
     *   <li>a SUM or an AVG window is eight bytes wide, a MAX, a MIN, a LAG, a LEAD, a FIRST_VALUE or a LAST_VALUE
     *       window as wide as the column it reads, and the narrower operand is converted: {@code (T.A) + SUM(T.A)
     *       OVER ()}, {@code (MAX(T.A) OVER ()) + T.N5};</li>
     *   <li>two columns of whole numbers are compared by their stored widths, and both are converted where the
     *       result needs more than either: {@code (T.N5) + T.N18}, {@code (T.N5) * (T.N5)}.</li>
     * </ul>
     * A column of a table holding no value is one byte wide.
     *
     * @param operand   the operand
     * @param converted whether another rule converts it already
     * @param operator  the operation's first character: '+', '-', '*', '%' or a comparison's
     * @param other     the operand beside it
     * @return the operand's text
     */
    private String term(final Expression operand, final boolean converted, final char operator,
                        final Expression other) {
        if (converted || isLiteral(operand)) {
            return operand(operand, converted);
        }
        final boolean arithmetic = operator == '+' || operator == '-' || operator == '*';
        final boolean comparison = !arithmetic && operator != '%';
        if (operand instanceof FunctionCallExpression && isAggregate((FunctionCallExpression) operand)
                && !isCompound(operand)) {
            return comparison && isCount((FunctionCallExpression) operand) ? operand(operand, false)
                : "(" + spell(operand) + ")";
        }
        if (arithmetic && (isCountingWindow(operand) || isCountingWindow(other) && !isFloat(operand))) {
            return operand(operand, true);
        }
        final int mine = widthOf(operand);
        final int theirs = widthOf(other);
        if (mine == 0 || theirs == 0) {
            return operand(operand, false);
        }
        int needed = Math.max(mine, theirs);
        final BigInteger[] range = rangeOf(operand);
        final BigInteger[] beside = rangeOf(other);
        if (arithmetic && range != null && beside != null) {
            needed = Math.max(needed, resultBytes(range, beside, operator));
        }
        return operand(operand, mine < needed);
    }

    /** Whether an aggregate is a count, which a comparison leaves as it stands. */
    private static boolean isCount(final FunctionCallExpression call) {
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        return "COUNT".equals(name) || "COUNT_IF".equals(name);
    }

    private boolean isFloat(final Expression expression) {
        return "FLOAT".equals(familyOf(typeOf(expression)));
    }

    /** Whether an expression is a counting or a ranking window. */
    private static boolean isCountingWindow(final Expression expression) {
        if (!(expression instanceof WindowFunctionExpression)) {
            return false;
        }
        final String name = windowName((WindowFunctionExpression) expression);
        return "COUNT".equals(name) || "ROW_NUMBER".equals(name) || "RANK".equals(name)
            || "DENSE_RANK".equals(name) || "NTILE".equals(name);
    }

    private static String windowName(final WindowFunctionExpression window) {
        return window.getFunctionName() == null ? "" : window.getFunctionName().toUpperCase(Locale.ROOT);
    }

    /** The window's one argument where it is a window that answers a value of the column it reads; null otherwise. */
    private static Expression carriedArgument(final Expression expression) {
        if (!(expression instanceof WindowFunctionExpression)) {
            return null;
        }
        final WindowFunctionExpression window = (WindowFunctionExpression) expression;
        final String name = windowName(window);
        final List<Expression> arguments = window.getArguments();
        return arguments != null && !arguments.isEmpty() && ("MAX".equals(name) || "MIN".equals(name)
            || "LAG".equals(name) || "LEAD".equals(name) || "FIRST_VALUE".equals(name) || "LAST_VALUE".equals(name)
            || "ANY_VALUE".equals(name)) ? arguments.get(0) : null;
    }

    /**
     * The bytes an operand is worked in: a whole literal's, a stored whole-number column's, eight for a SUM or an AVG
     * window and for a sum or a count of the query's Aggregate, a carrying window's or aggregate's argument's; a
     * column of exact numbers with decimals counts as narrower than eight. Zero where the width is not known.
     */
    private int widthOf(final Expression expression) {
        if (expression instanceof WindowFunctionExpression) {
            final String name = windowName((WindowFunctionExpression) expression);
            if ("SUM".equals(name) || "AVG".equals(name)) {
                return isFloat(expression) ? 0 : 8;
            }
            final Expression carried = carriedArgument(expression);
            return carried == null ? 0 : widthOf(carried);
        }
        if (expression instanceof FunctionCallExpression && isAggregate((FunctionCallExpression) expression)) {
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            if ("SUM".equals(name) || "COUNT".equals(name) || "COUNT_IF".equals(name)) {
                return 8;
            }
            return ("MAX".equals(name) || "MIN".equals(name)) && call.getArguments().size() == 1
                ? widthOf(call.getArguments().get(0)) : 0;
        }
        final BigInteger[] range = rangeOf(expression);
        if (range != null) {
            return Math.max(storageBytes(range[0]), storageBytes(range[1]));
        }
        if (constants != null && expression instanceof ColumnReferenceExpression) {
            final PlanColumn column = scope == null ? null : scope.resolve((ColumnReferenceExpression) expression);
            if (column != null && column.getStoredTable() != null && positive(scaleOf(column.getType()))) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * The least and the greatest whole number an operand holds: a whole literal's value, a stored whole-number
     * column's values - zero alone where it holds none - and a carrying window's argument's; null otherwise.
     */
    private BigInteger[] rangeOf(final Expression expression) {
        if (isWholeLiteral(expression)) {
            final BigInteger value = new BigDecimal(((LiteralExpression) expression).getValue().toString())
                .toBigInteger();
            return new BigInteger[] {value, value};
        }
        final Expression carried = carriedArgument(expression);
        if (carried != null) {
            return rangeOf(carried);
        }
        if (constants == null || !(expression instanceof ColumnReferenceExpression)) {
            return null;
        }
        final PlanColumn column = scope == null ? null : scope.resolve((ColumnReferenceExpression) expression);
        final Integer scale = column == null ? null : scaleOf(column.getType());
        if (scale == null || scale.intValue() != 0 || column.getStoredTable() == null || column.isCompound()) {
            return null;
        }
        final BigInteger[] stored = constants.storedRange(column);
        return stored != null ? stored : new BigInteger[] {BigInteger.ZERO, BigInteger.ZERO};
    }

    /** The bytes the result of a sum, a difference or a product over two ranges needs. */
    private static int resultBytes(final BigInteger[] left, final BigInteger[] right, final char operator) {
        final BigInteger[] ends;
        if (operator == '*') {
            ends = new BigInteger[] {left[0].multiply(right[0]), left[0].multiply(right[1]),
                left[1].multiply(right[0]), left[1].multiply(right[1])};
        } else if (operator == '+') {
            ends = new BigInteger[] {left[0].add(right[0]), left[1].add(right[1])};
        } else {
            ends = new BigInteger[] {left[0].subtract(right[1]), left[1].subtract(right[0])};
        }
        int bytes = 1;
        for (final BigInteger end : ends) {
            bytes = Math.max(bytes, storageBytes(end));
        }
        return bytes;
    }

    /**
     * Whether a whole-number column beside a whole-number literal in a sum, a difference or a product is converted to
     * a wider integer, and so stands in parentheses: the account stores a column of whole numbers in the fewest bytes
     * its values need - one for -128 to 127, then two, four, eight and sixteen - and converts it where the literal,
     * or the operation's result over the values, needs more. Over 5, 7 and 9, {@code (FZ.ID) + 119} and
     * {@code (FZ.ID) - 133} but {@code FZ.ID + 118}, and {@code (FZ.ID) * 15} but {@code FZ.ID * 14}
     * (live-verified).
     *
     * @param column       the operand that may be the column
     * @param literal      the other operand, which may be the literal
     * @param operator     '+', '-' or '*'
     * @param literalFirst whether the literal is written first
     * @return true where the column is converted
     */
    private boolean widened(final Expression column, final Expression literal, final char operator,
                            final boolean literalFirst) {
        if (constants == null || !(column instanceof ColumnReferenceExpression) || !isWholeLiteral(literal)) {
            return false;
        }
        final PlanColumn resolved = scope == null ? null : scope.resolve((ColumnReferenceExpression) column);
        final Integer scale = resolved == null ? null : scaleOf(resolved.getType());
        final BigInteger[] range = scale == null || scale.intValue() != 0 ? null : constants.storedRange(resolved);
        if (range == null) {
            return false;
        }
        final BigInteger value = new BigDecimal(((LiteralExpression) literal).getValue().toString()).toBigInteger();
        final BigInteger least;
        final BigInteger greatest;
        if (operator == '*') {
            least = range[0].multiply(value).min(range[1].multiply(value));
            greatest = range[0].multiply(value).max(range[1].multiply(value));
        } else if (operator == '+') {
            least = range[0].add(value);
            greatest = range[1].add(value);
        } else if (literalFirst) {
            least = value.subtract(range[1]);
            greatest = value.subtract(range[0]);
        } else {
            least = range[0].subtract(value);
            greatest = range[1].subtract(value);
        }
        final int stored = Math.max(storageBytes(range[0]), storageBytes(range[1]));
        return Math.max(storageBytes(value), Math.max(storageBytes(least), storageBytes(greatest))) > stored;
    }

    /** Whether an expression is a literal whole number written with no point, a long's or a wider one. */
    private static boolean isWholeLiteral(final Expression expression) {
        if (!(expression instanceof LiteralExpression)) {
            return false;
        }
        final LiteralExpression literal = (LiteralExpression) expression;
        return literal.getType() == LiteralType.INTEGER && literal.getValue() instanceof Number
            || literal.getType() == LiteralType.DECIMAL && literal.getValue() instanceof BigDecimal
                && ((BigDecimal) literal.getValue()).scale() == 0;
    }

    /** The bytes a whole number is stored in: 1 for -128 to 127, then 2, 4, 8 and 16. */
    private static int storageBytes(final BigInteger value) {
        final int bits = value.bitLength() + 1;
        return bits <= 8 ? 1 : bits <= 16 ? 2 : bits <= 32 ? 4 : bits <= 64 ? 8 : 16;
    }

    /** An operand of an infix operator, in parentheses where it is an operation, a scalar call or converted. */
    private String operand(final Expression operand, final boolean converted) {
        final String text = spell(operand);
        if (isLiteral(operand)) {
            return text;
        }
        return converted || isCompound(operand) ? "(" + text + ")" : text;
    }

    /**
     * Whether an expression is an operation or a computed call, which stands in parentheses as an operand: a column
     * that stands for one, a view's or a derived table's, too.
     *
     * @param expression the expression
     * @return true where it is
     */
    boolean isCompound(final Expression expression) {
        if (sequenceReads.contains(expression)) {
            return true;
        }
        if (expression instanceof ColumnReferenceExpression) {
            final PlanColumn column = scope == null ? null : scope.resolve((ColumnReferenceExpression) expression);
            return column != null && column.isCompound();
        }
        if (expression instanceof WindowFunctionExpression || expression instanceof SubqueryExpression) {
            return false;
        }
        if (expression instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            return !isAggregate(call) || "AVG".equalsIgnoreCase(call.getFunctionName());
        }
        if (expression instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expression;
            final String target = familyOf(cast.getTargetDataType());
            if (folds(cast.getExpression(), target, cast)) {
                return false;
            }
            return target == null || !target.equals(familyOf(cast.getExpression()))
                || isCompound(cast.getExpression());
        }
        return true;
    }

    private boolean isAggregate(final FunctionCallExpression call) {
        return call.getFunctionName() != null && call.getNameExpression() == null
            && functions.hasAggregateFunction(call.getFunctionName().toUpperCase(Locale.ROOT));
    }

    /** Whether an expression is a value the plan holds as it is: a literal, a negative number, a folded literal. */
    private boolean isLiteral(final Expression expression) {
        if (expression instanceof LiteralExpression || folded(expression) != null) {
            return true;
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() == UnaryOperator.NEGATE && isNumber(unary.getOperand());
        }
        if (expression instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expression;
            return folds(cast.getExpression(), familyOf(cast.getTargetDataType()), cast);
        }
        return false;
    }

    private static boolean isNumber(final Expression expression) {
        return expression instanceof LiteralExpression && (((LiteralExpression) expression).getType()
            == LiteralType.INTEGER || ((LiteralExpression) expression).getType() == LiteralType.DECIMAL);
    }

    /** Whether a conversion is folded into the literal it converts: a typed literal, a text to a date, a number. */
    private static boolean folds(final Expression value, final String target, final CastExpression cast) {
        if (!(value instanceof LiteralExpression)) {
            return false;
        }
        final LiteralType type = ((LiteralExpression) value).getType();
        if (cast.isTypedLiteral()) {
            return true;
        }
        if (type == LiteralType.STRING) {
            return target != null && !"NUMBER".equals(target) && !"FLOAT".equals(target)
                && !"DECFLOAT".equals(target) && !"BOOLEAN".equals(target);
        }
        return (type == LiteralType.INTEGER || type == LiteralType.DECIMAL) && "NUMBER".equals(target);
    }

    private boolean isWhole(final Expression expression) {
        final Integer scale = scaleOf(expression);
        return scale != null && scale.intValue() == 0;
    }

    private static boolean positive(final Integer scale) {
        return scale != null && scale.intValue() > 0;
    }

    /**
     * The decimals of the exact number an expression answers, where the plan can tell them.
     *
     * @param expression the expression
     * @return the decimals, or null for any other value or where they are not known
     */
    Integer decimalsOf(final Expression expression) {
        return scaleOf(expression);
    }

    /** The decimals of an exact number an expression answers; null for any other value or where it is not known. */
    private Integer scaleOf(final Expression expression) {
        if (expression instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) expression;
            if (literal.getType() == LiteralType.INTEGER) {
                return Integer.valueOf(0);
            }
            if (literal.getType() == LiteralType.DECIMAL && literal.getValue() instanceof BigDecimal) {
                return Integer.valueOf(Math.max(0, ((BigDecimal) literal.getValue()).scale()));
            }
            return null;
        }
        if (expression instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) expression).getOperator() == UnaryOperator.NEGATE) {
            return scaleOf(((UnaryOperationExpression) expression).getOperand());
        }
        if (expression instanceof ColumnReferenceExpression) {
            final PlanColumn column = scope == null ? null : scope.resolve((ColumnReferenceExpression) expression);
            return column == null ? null : scaleOf(column.getType());
        }
        if (expression instanceof WindowFunctionExpression) {
            // A window that sums or carries its argument has the argument's decimals; a counting one has none.
            final WindowFunctionExpression window = (WindowFunctionExpression) expression;
            if (isCountingWindow(window)) {
                return Integer.valueOf(0);
            }
            final List<Expression> arguments = window.getArguments();
            return arguments != null && arguments.size() == 1 && !window.isDistinct()
                && ("SUM".equals(windowName(window)) || carriedArgument(window) != null)
                ? scaleOf(arguments.get(0)) : null;
        }
        if (expression instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            if (!isAggregate(call)) {
                return null;
            }
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            if ("COUNT".equals(name) || "COUNT_IF".equals(name)) {
                return Integer.valueOf(0);
            }
            if (("SUM".equals(name) || "MIN".equals(name) || "MAX".equals(name) || "ANY_VALUE".equals(name))
                    && call.getArguments().size() == 1) {
                return scaleOf(call.getArguments().get(0));
            }
            return null;
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression operation = (BinaryOperationExpression) expression;
            final Integer left = scaleOf(operation.getLeft());
            final Integer right = scaleOf(operation.getRight());
            if (left == null || right == null) {
                return null;
            }
            switch (operation.getOperator()) {
                case ADD:
                case SUBTRACT:
                case MODULO:
                    return Integer.valueOf(Math.max(left.intValue(), right.intValue()));
                case MULTIPLY:
                    return Integer.valueOf(left.intValue() + right.intValue());
                default:
                    return null;
            }
        }
        if (expression instanceof CastExpression) {
            return scaleOf(((CastExpression) expression).getTargetDataType());
        }
        return null;
    }

    private static Integer scaleOf(final DataType type) {
        if (type instanceof NumericType && !NumericType.isApproximate(type)
                && !"DECFLOAT".equals(type.getName())) {
            return Integer.valueOf(((NumericType) type).getScale());
        }
        return null;
    }

    /** The family a conversion's operand belongs to, as {@link #familyOf(DataType)} names it; null where unknown. */
    private String familyOf(final Expression expression) {
        if (expression instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) expression).getType();
            return type == LiteralType.STRING ? "TEXT" : type == LiteralType.BOOLEAN ? "BOOLEAN"
                : type == LiteralType.INTEGER || type == LiteralType.DECIMAL && isExact(expression) ? "NUMBER" : null;
        }
        if (expression instanceof ColumnReferenceExpression) {
            final PlanColumn column = scope == null ? null : scope.resolve((ColumnReferenceExpression) expression);
            return column == null ? null : familyOf(column.getType());
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperator operator = ((BinaryOperationExpression) expression).getOperator();
            if (operator == BinaryOperator.CONCAT) {
                return "TEXT";
            }
            return scaleOf(expression) != null ? "NUMBER" : null;
        }
        return null;
    }

    private static boolean isExact(final Expression literal) {
        return ((LiteralExpression) literal).getValue() instanceof BigDecimal;
    }

    /**
     * The family a value of a type belongs to, which a conversion within leaves out: NUMBER, FLOAT, DECFLOAT, TEXT,
     * BOOLEAN, DATE, TIME and each timestamp by its name; null for any other type and where it is not known.
     */
    private static String familyOf(final DataType type) {
        if (type == null) {
            return null;
        }
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? "FLOAT" : "DECFLOAT".equals(type.getName()) ? "DECFLOAT"
                : "NUMBER";
        }
        switch (type.getCategory()) {
            case STRING:
                return "TEXT";
            case BOOLEAN:
                return "BOOLEAN";
            case DATE_TIME:
                return type.getName();
            default:
                return null;
        }
    }

    /** The function a conversion is planned as, or null for one that stays a CAST. */
    private static String conversionOf(final CastExpression cast, final String family) {
        if (family == null) {
            final DataType target = cast.getTargetDataType();
            if (target == null) {
                return null;
            }
            if (target.getCategory() == TypeCategory.SEMI_STRUCTURED) {
                return "TO_" + target.getName();
            }
            return target.getCategory() == TypeCategory.BINARY ? "TO_BINARY" : null;
        }
        switch (family) {
            case "NUMBER":
                return "TO_NUMBER";
            case "FLOAT":
                return "TO_DOUBLE";
            case "DECFLOAT":
                return "TO_DECFLOAT";
            case "TEXT":
                return "TO_CHAR";
            case "BOOLEAN":
                return "TO_BOOLEAN";
            case "DATE":
                return "TO_DATE";
            case "TIME":
                return "TO_TIME";
            default:
                // The bare word keeps its name, TO_TIMESTAMP, whatever flavour it stands for (live-verified).
                return "TIMESTAMP".equals(cast.getTargetBaseName()) ? "TO_TIMESTAMP" : "TO_" + family;
        }
    }

    /** COALESCE with what follows its first literal left out, IFNULL for two values. */
    private String coalesced(final List<Expression> args) {
        final List<Expression> kept = new ArrayList<>();
        for (final Expression arg : args) {
            kept.add(arg);
            if (arg instanceof LiteralExpression && ((LiteralExpression) arg).getType() != LiteralType.NULL) {
                break;
            }
        }
        final StringBuilder text = new StringBuilder(kept.size() == 2 ? "IFNULL(" : "COALESCE(");
        for (int i = 0; i < kept.size(); i++) {
            text.append(i > 0 ? ", " : "").append(spell(kept.get(i)));
        }
        return text.append(')').toString();
    }

    private static boolean isSimpleCase(final List<WhenClause> whens) {
        if (whens.isEmpty()) {
            return false;
        }
        for (final WhenClause when : whens) {
            if (!when.isOperandMatch() || !(when.getCondition() instanceof BinaryOperationExpression)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A sort key as the plan lists it: its direction, and the NULLS placement written or the one the session's
     * DEFAULT_NULL_ORDERING gives the direction (see {@link SessionNullOrdering}).
     *
     * @param key        the key's text
     * @param ascending  whether it sorts ascending
     * @param nullsFirst the NULLS placement written, or null
     * @return the text
     */
    static String sortKey(final String key, final boolean ascending, final Boolean nullsFirst) {
        final boolean first = SessionNullOrdering.nullsFirst(ascending, nullsFirst);
        return key + (ascending ? " ASC" : " DESC") + (first ? " NULLS FIRST" : " NULLS LAST");
    }

    private static String symbolOf(final BinaryOperationExpression expr) {
        switch (expr.getOperator()) {
            case ADD:
                return "+";
            case SUBTRACT:
                return "-";
            case MULTIPLY:
                return "*";
            case DIVIDE:
                return "/";
            case MODULO:
                return "%";
            case EQUAL:
                return "=";
            case NOT_EQUAL:
                return expr.isBangSpelled() ? "!=" : "<>";
            case LESS_THAN:
                return "<";
            case LESS_THAN_OR_EQUAL:
                return "<=";
            case GREATER_THAN:
                return ">";
            case GREATER_THAN_OR_EQUAL:
                return ">=";
            default:
                return expr.getOperator().name();
        }
    }

    private static String quoted(final String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /** A number as the plan prints a value: its digits in place, with no trailing zero after the point. */
    /** A number literal as the FLOAT literal it becomes, {@code 1.0}, {@code 2.5}; null for any other expression. */
    private static String floatLiteral(final Expression expression) {
        if (!(expression instanceof LiteralExpression)) {
            return null;
        }
        final LiteralExpression literal = (LiteralExpression) expression;
        if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL
                || !(literal.getValue() instanceof Number)) {
            return null;
        }
        final BigDecimal value = DecimalText.parse(literal.getValue().toString()).stripTrailingZeros();
        return value.scale() <= 0 ? value.setScale(1).toPlainString() : value.toPlainString();
    }

    private static String numberText(final Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            final double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return String.valueOf(value);
            }
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigInteger || value instanceof Number) {
            return value.toString();
        }
        return String.valueOf(value);
    }
}
