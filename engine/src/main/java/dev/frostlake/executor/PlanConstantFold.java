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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.VolatileCalls;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The constant parts of an expression a plan writes, folded to their values as the account computes them while the
 * statement compiles (live-verified): arithmetic, concatenation, comparisons, AND, OR, NOT, IS NULL and a searched
 * CASE over constants, a test whether a value that is never null is null, a deterministic built-in function
 * over constants ({@code ABS(-5)} as {@code 5}, {@code UPPER('a')} as {@code 'A'}, {@code CURRENT_DATE()} as the
 * day), a context function reading the statement's clock or its session ({@code CURRENT_USER()} as the user's name in
 * quotes, {@code CURRENT_TIMESTAMP()} as its instant in UTC, {@code '2024-01-15 10:30:00.000000000Z'}), a SQL function
 * over constants, called by name or by its SYSTEM$REFERENCE token, and a FROM-less scalar subquery of a constant
 * ({@code (SELECT 'a')}). A number is written with its scale ({@code 1.5 * 2} as {@code 3.0}), a text, a date, a time
 * and a timestamp in quotes, a truth value as {@code TRUE} or {@code FALSE}. Anything reading a column, a function that answers anew each time it is computed,
 * any other context function, an aggregate or a window, a function of another language, and a value of another type
 * are left as written.
 */
final class PlanConstantFold {

    /**
     * The context functions the account folds: those reading the statement's clock, which compiling fixes, and those
     * reading the session it compiles in.
     */
    private static final List<String> FIXED_CONTEXT = Arrays.asList("CURRENT_DATE", "CURRENT_TIMESTAMP",
        "LOCALTIMESTAMP", "CURRENT_TIME", "LOCALTIME", "CURRENT_USER", "CURRENT_ROLE", "CURRENT_DATABASE",
        "CURRENT_SCHEMA", "CURRENT_SESSION", "CURRENT_STATEMENT", "CURRENT_VERSION", "CURRENT_ACCOUNT",
        "CURRENT_REGION", "CURRENT_SECONDARY_ROLES", "CURRENT_TRANSACTION", "CURRENT_WAREHOUSE", "CURRENT_CLIENT", "CURRENT_ACCOUNT_NAME",
        "CURRENT_ORGANIZATION_NAME", "LAST_QUERY_ID", "CURRENT_AVAILABLE_ROLES", "CURRENT_IP_ADDRESS",
        "IS_ROLE_IN_SESSION", "CURRENT_ROLE_TYPE", "CURRENT_SCHEMAS", "INVOKER_ROLE");

    /** A timestamp folded by a plan: its instant in UTC, to the nanosecond, marked Z. */
    private static final DateTimeFormatter INSTANT_TEXT = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.nnnnnnnnn'Z'", Locale.ROOT);

    /** The minutes a TIMESTAMP_TZ's folded offset is written past. */
    private static final int MINUTES_PER_DAY = 1440;

    /** A time of day folded by a plan, to the nanosecond. */
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("HH:mm:ss.nnnnnnnnn",
        Locale.ROOT);

    /**
     * The built-in functions the account never computes while compiling, whatever their arguments: the random family
     * and the row-place generators ({@code UNIFORM(1, 10, 5)} and {@code RANDOM(42)} stay as written).
     */
    private static final List<String> FRESH = Arrays.asList("RANDOM", "UUID_STRING", "UNIFORM", "NORMAL", "RANDSTR",
        "ZIPF", "SEQ1", "SEQ2", "SEQ4", "SEQ8");

    /** The built-in functions whose value is never null, whatever their arguments. */
    private static final List<String> NEVER_NULL = Arrays.asList("RANDOM", "SEQ1", "SEQ2", "SEQ4", "SEQ8");

    /** The operators whose value is never null where neither operand is. */
    private static final List<BinaryOperator> NEVER_NULL_OPERATORS = Arrays.asList(BinaryOperator.ADD,
        BinaryOperator.SUBTRACT, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE);

    private PlanConstantFold() {
    }

    /**
     * @param executor   the executor that computes the value
     * @param expression a part of an expression the plan writes
     * @return the text the plan writes for its value, or null where it writes the part as written
     */
    static String folded(final QueryExecutor executor, final Expression expression) {
        if (expression instanceof LiteralExpression || !foldable(executor, expression)) {
            return null;
        }
        final Object value;
        try {
            value = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(), executor)
                .evaluate(expression, new Row(new ArrayList<Object>()));
        } catch (final RuntimeException unfolded) {
            return null;
        }
        if (value == null && expression instanceof FunctionCallExpression) {
            // A call that answers nothing is folded to null: NULLIF(1, 1), CURRENT_TRANSACTION() outside a
            // transaction.
            return "null";
        }
        if ((value instanceof OffsetDateTime || value instanceof ZonedDateTime)
                && DateTimeType.TIMESTAMP_TZ.getName().equals(typeName(executor, expression))) {
            // A TIMESTAMP_TZ carries its offset too, as minutes past a day behind UTC: tz=1560 for +02:00.
            final OffsetDateTime zoned = value instanceof OffsetDateTime ? (OffsetDateTime) value
                : ((ZonedDateTime) value).toOffsetDateTime();
            return "'" + INSTANT_TEXT.format(zoned.atZoneSameInstant(ZoneOffset.UTC)) + "tz="
                + (zoned.getOffset().getTotalSeconds() / 60 + MINUTES_PER_DAY) + "'";
        }
        return textOf(value);
    }

    /** The name of the type an expression's outermost conversion or built-in call declares, or null. */
    private static String typeName(final QueryExecutor executor, final Expression expression) {
        DataType type = null;
        if (expression instanceof CastExpression) {
            type = ((CastExpression) expression).getTargetDataType();
        } else if (expression instanceof FunctionCallExpression
                && ((FunctionCallExpression) expression).getFunctionName() != null) {
            final String name = ((FunctionCallExpression) expression).getFunctionName().toUpperCase(Locale.ROOT);
            if ("CONVERT_TIMEZONE".equals(name)) {
                return ((FunctionCallExpression) expression).getArguments().size() == 2
                    ? DateTimeType.TIMESTAMP_TZ.getName() : null;
            }
            final BuiltInFunction builtIn = executor.getFunctionRegistry().hasFunction(name)
                ? executor.getFunctionRegistry().getFunction(name) : null;
            type = builtIn == null ? null : builtIn.getReturnType();
        }
        return type == null ? null : type.getName();
    }

    /**
     * Whether an expression computed from constants alone, by the parts the account folds, is NULL: {@code
     * NULLIF(1, 1)}.
     *
     * @param executor   the executor that computes the value
     * @param expression the expression
     * @return whether it folds to NULL; false for one reading anything but constants or failing to compute
     */
    static boolean foldsToNull(final QueryExecutor executor, final Expression expression) {
        if (expression instanceof LiteralExpression) {
            return ((LiteralExpression) expression).getValue() == null;
        }
        if (!foldable(executor, expression)) {
            return false;
        }
        try {
            return new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(), executor)
                .evaluate(expression, new Row(new ArrayList<Object>())) == null;
        } catch (final RuntimeException unfolded) {
            return false;
        }
    }

    /** The plan's text of a folded value, or null for a value of a type it does not fold. */
    private static String textOf(final Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }
        if (value instanceof OffsetDateTime) {
            return "'" + INSTANT_TEXT.format(((OffsetDateTime) value).atZoneSameInstant(ZoneOffset.UTC)) + "'";
        }
        if (value instanceof ZonedDateTime) {
            return "'" + INSTANT_TEXT.format(((ZonedDateTime) value).withZoneSameInstant(ZoneOffset.UTC)) + "'";
        }
        if (value instanceof LocalDateTime) {
            return "'" + INSTANT_TEXT.format((LocalDateTime) value) + "'";
        }
        if (value instanceof LocalTime) {
            return "'" + TIME_TEXT.format((LocalTime) value) + "'";
        }
        return value instanceof LocalDate ? "'" + value + "'" : null;
    }

    /** Whether an expression is computed from constants alone, by the parts the account folds. */
    private static boolean foldable(final QueryExecutor executor, final Expression expression) {
        if (expression instanceof LiteralExpression) {
            return true;
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return (unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS
                || unary.getOperator() == UnaryOperator.NOT) && foldable(executor, unary.getOperand());
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            switch (binary.getOperator()) {
                case ADD:
                case SUBTRACT:
                case MULTIPLY:
                case DIVIDE:
                case MODULO:
                case CONCAT:
                case EQUAL:
                case NOT_EQUAL:
                case LESS_THAN:
                case LESS_THAN_OR_EQUAL:
                case GREATER_THAN:
                case GREATER_THAN_OR_EQUAL:
                case AND:
                case OR:
                    return foldable(executor, binary.getLeft()) && foldable(executor, binary.getRight());
                default:
                    return false;
            }
        }
        if (expression instanceof CastExpression) {
            return foldable(executor, ((CastExpression) expression).getExpression());
        }
        if (expression instanceof CaseExpression) {
            final CaseExpression choice = (CaseExpression) expression;
            for (final WhenClause when : choice.getWhenClauses()) {
                if (when.isOperandMatch() || !foldable(executor, when.getCondition())
                        || !foldable(executor, when.getResult())) {
                    return false;
                }
            }
            return choice.getElseExpression() == null || foldable(executor, choice.getElseExpression());
        }
        if (expression instanceof IsNullExpression) {
            final Expression operand = ((IsNullExpression) expression).getOperand();
            return neverNull(operand) || foldable(executor, operand);
        }
        if (expression instanceof FunctionCallExpression) {
            return foldableCall(executor, (FunctionCallExpression) expression);
        }
        if (expression instanceof SubqueryExpression) {
            final Expression item = constantItem(executor, (SubqueryExpression) expression);
            return item != null && foldable(executor, item);
        }
        return false;
    }

    /**
     * Whether a value is never null whatever computes it, so that a test whether it is null folds (live-verified):
     * RANDOM and the row-place generators, their negation and absolute value, and a sum, a difference, a product or
     * a quotient of such values or of constants that are not null. UNIFORM, NORMAL, ZIPF, RANDSTR and UUID_STRING are tested as written.
     *
     * @param expression the value tested
     * @return true for such a value
     */
    static boolean neverNull(final Expression expression) {
        if (expression instanceof LiteralExpression) {
            return ((LiteralExpression) expression).getValue() != null;
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            return NEVER_NULL_OPERATORS.contains(binary.getOperator()) && neverNull(binary.getLeft())
                && neverNull(binary.getRight()) && (fresh(binary.getLeft()) || fresh(binary.getRight()));
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return (unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS)
                && fresh(unary.getOperand());
        }
        if (!(expression instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expression;
        if (call.getNameExpression() != null || call.getFunctionName() == null || call.isStar()
                || call.getNameParts() != null && call.getNameParts().size() != 1) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (NEVER_NULL.contains(name)) {
            return true;
        }
        return "ABS".equals(name) && call.getArguments().size() == 1 && fresh(call.getArguments().get(0))
            && neverNull(call.getArguments().get(0));
    }

    /** Whether a value that is never null is so because it calls RANDOM or a row-place generator. */
    private static boolean fresh(final Expression expression) {
        return !(expression instanceof LiteralExpression) && neverNull(expression);
    }

    /** Whether a call is one the account computes while compiling, its arguments constants. */
    private static boolean foldableCall(final QueryExecutor executor, final FunctionCallExpression call) {
        if (call.isStar() || VolatileCalls.writes(call)) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!foldable(executor, argument)) {
                return false;
            }
        }
        final List<String> parts;
        if (call.getNameExpression() != null) {
            final Object resolved;
            try {
                resolved = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                    executor).evaluate(call.getNameExpression(), null);
            } catch (final RuntimeException unresolved) {
                return false;
            }
            if (resolved == null || ProcedureTokenCalls.namesProcedure(resolved.toString())) {
                return false;
            }
            parts = Arrays.asList(SqlIdentifiers.identifierReferenceParts(resolved.toString()));
            if (!SqlIdentifiers.isIssuedReference(resolved.toString()) && parts.size() == 1
                    && executor.getFunctionRegistry().hasFunction(parts.get(0).toUpperCase(Locale.ROOT))) {
                return false;
            }
        } else {
            if (call.getFunctionName() == null) {
                return false;
            }
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            final FunctionRegistry functions = executor.getFunctionRegistry();
            if (functions.hasAggregateFunction(name)) {
                return false;
            }
            final List<String> written = call.getNameParts();
            if (functions.hasFunction(name) && (written == null || written.size() == 1)) {
                if (FRESH.contains(name)) {
                    return false;
                }
                final BuiltInFunction builtIn = functions.getFunction(name);
                return builtIn != null && builtIn.getClass().getPackage() != null
                    && (!builtIn.getClass().getPackage().getName().endsWith(".context")
                        || FIXED_CONTEXT.contains(name))
                    && !builtIn.getClass().getPackage().getName().endsWith(".crypto");
            }
            parts = written;
        }
        return sqlFunctions(executor, parts);
    }

    /**
     * Whether a call is one the account computes anew wherever it stands rather than while compiling
     * (live-verified): a built-in of the random family or a row-place generator, whatever its arguments, or a call
     * by name of a user-defined function written in another language than SQL or whose SQL body answers anew. A
     * query with no FROM that writes one reads an empty ValuesClause rather than a Generator of one row.
     *
     * @param executor the executor whose catalog holds the functions
     * @param call     the call
     * @return true for such a call
     */
    static boolean answersAnew(final QueryExecutor executor, final FunctionCallExpression call) {
        if (call.getNameExpression() != null || call.getFunctionName() == null) {
            return false;
        }
        final List<String> parts = call.getNameParts();
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (parts == null || parts.size() == 1) {
            if (FRESH.contains(name)) {
                return true;
            }
            if (executor.getFunctionRegistry().hasFunction(name)
                    || executor.getFunctionRegistry().hasAggregateFunction(name)) {
                return false;
            }
        }
        try {
            final List<Function> overloads = overloadsOf(executor, parts == null ? Arrays.asList(name) : parts);
            if (overloads == null) {
                return false;
            }
            for (final Function function : overloads) {
                if (function.isTableFunction()) {
                    continue;
                }
                if (!"SQL".equals(function.getLanguage()) || answersAnew(function.getBody())) {
                    return true;
                }
            }
            return false;
        } catch (final RuntimeException unresolved) {
            return false;
        }
    }

    /**
     * The body a plan writes for a call of a SQL function over columns or constants, as the account inlines it
     * (live-verified): {@code fn(n)} of {@code x + 1} as {@code E.N + 1}. Only a function of one overload, not secure,
     * whose body is an expression rather than a query, called with one argument for each parameter, each a column or
     * a constant; null for any other call.
     *
     * @param executor the executor whose catalog holds the function
     * @param call     the call
     * @return the body and its parameters, or null
     */
    static InlinedFunctionBody inlined(final QueryExecutor executor, final FunctionCallExpression call) {
        if (call.getNameExpression() != null || call.getFunctionName() == null || call.isStar()
                || call.getArgumentNames() != null) {
            return null;
        }
        final List<String> parts = call.getNameParts();
        if (parts == null || parts.size() == 1
                && executor.getFunctionRegistry().hasFunction(call.getFunctionName().toUpperCase(Locale.ROOT))) {
            return null;
        }
        for (final Expression argument : call.getArguments()) {
            if (!(argument instanceof LiteralExpression || argument instanceof ColumnReferenceExpression)) {
                return null;
            }
        }
        try {
            final List<Function> overloads = overloadsOf(executor, parts);
            if (overloads == null || overloads.size() != 1) {
                return null;
            }
            final Function function = overloads.get(0);
            final String body = function.getBody() == null ? null : function.getBody().trim();
            if (!"SQL".equals(function.getLanguage()) || function.isTableFunction() || function.isSecure()
                    || body == null || function.getParameters().size() != call.getArguments().size()) {
                return null;
            }
            // A query body is inlined as its one item where it reads no FROM and its item answers anew.
            final Expression inlinedBody = body.regionMatches(true, 0, "SELECT", 0, 6)
                ? freshItem(executor, queryItem(executor, body)) : ExpressionEvaluator.parse(body);
            if (inlinedBody == null) {
                return null;
            }
            final List<String> names = new ArrayList<>();
            for (final Parameter parameter : function.getParameters()) {
                names.add(parameter.getName().toUpperCase(Locale.ROOT));
            }
            return new InlinedFunctionBody(inlinedBody, names);
        } catch (final RuntimeException unread) {
            return null;
        }
    }

    /**
     * A call by name of a user-defined function written in JavaScript, as the plan writes it (live-verified): its name
     * and its parameters, each with its type, {@code FNJ(X FLOAT, S VARCHAR)}, whatever the arguments. A DOUBLE is
     * named FLOAT, a text type VARCHAR and a TIMESTAMP TIMESTAMP_NTZ. Only a function of one overload; null for any
     * other call.
     *
     * @param executor the executor whose catalog holds the function
     * @param call     the call
     * @return the call as the plan writes it, or null
     */
    static ExternalFunctionCall external(final QueryExecutor executor, final FunctionCallExpression call) {
        if (call.getNameExpression() != null || call.getFunctionName() == null || call.isStar()) {
            return null;
        }
        final List<String> parts = call.getNameParts();
        if (parts == null || parts.size() == 1
                && executor.getFunctionRegistry().hasFunction(call.getFunctionName().toUpperCase(Locale.ROOT))) {
            return null;
        }
        try {
            final List<Function> overloads = overloadsOf(executor, parts);
            if (overloads == null || overloads.size() != 1 || overloads.get(0).isTableFunction()
                    || !"JAVASCRIPT".equalsIgnoreCase(overloads.get(0).getLanguage())
                    || overloads.get(0).getParameters().size() != call.getArguments().size()) {
                return null;
            }
            final Function function = overloads.get(0);
            final StringBuilder text = new StringBuilder(SqlIdentifiers.spellCanonical(function.getName()))
                .append('(');
            final List<Parameter> parameters = function.getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                text.append(i > 0 ? ", " : "").append(SqlIdentifiers.spellCanonical(parameters.get(i).getName()))
                    .append(' ').append(externalTypeName(parameters.get(i).getDataType()));
            }
            return new ExternalFunctionCall(text.append(')').toString(), function.getReturnType());
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /** A parameter's type as a plan names it in a JavaScript function's call. */
    private static String externalTypeName(final DataType type) {
        final String name = type == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        switch (name) {
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "FLOAT":
            case "FLOAT4":
            case "FLOAT8":
            case "REAL":
                return "FLOAT";
            case "STRING":
            case "TEXT":
            case "VARCHAR":
                return "VARCHAR";
            case "TIMESTAMP":
                return "TIMESTAMP_NTZ";
            default:
                return name;
        }
    }

    /** The user-defined functions a name reaches, or null where it reaches none. */
    private static List<Function> overloadsOf(final QueryExecutor executor, final List<String> parts) {
        if (parts == null || parts.isEmpty() || parts.size() > 3) {
            return null;
        }
        final String databaseName = parts.size() == 3 ? parts.get(0) : executor.getCatalog().getCurrentDatabase();
        final String schemaName = parts.size() >= 2 ? parts.get(parts.size() - 2)
            : executor.getCatalog().getCurrentSchema();
        final Database database = executor.getCatalog().databaseExact(databaseName);
        final Schema schema = database.schemaExact(schemaName);
        final List<Function> overloads = schema.getFunctionOverloads(parts.get(parts.size() - 1));
        return overloads == null || overloads.isEmpty() ? null : overloads;
    }

    /** Whether a name reaches user-defined functions, every overload of them written in SQL. */
    private static boolean sqlFunctions(final QueryExecutor executor, final List<String> parts) {
        if (parts == null || parts.isEmpty() || parts.size() > 3) {
            return false;
        }
        try {
            final List<Function> overloads = overloadsOf(executor, parts);
            if (overloads == null) {
                return false;
            }
            for (final Function function : overloads) {
                if (!"SQL".equals(function.getLanguage()) || function.isTableFunction()
                        || answersAnew(function.getBody())) {
                    return false;
                }
            }
            return true;
        } catch (final RuntimeException unresolved) {
            return false;
        }
    }

    /** Whether a SQL function's body calls a function that answers anew each time, or cannot be read. */
    private static boolean answersAnew(final String body) {
        if (body == null) {
            return true;
        }
        try {
            final String text = body.trim();
            return VolatileCalls.writes(ExpressionEvaluator.parse(text.regionMatches(true, 0, "SELECT", 0, 6)
                ? text.substring(6) : text));
        } catch (final RuntimeException unread) {
            return true;
        }
    }

    /**
     * The item a plan writes in place of a FROM-less scalar subquery of one SELECT whose item calls a procedure's
     * token or a function that answers anew, as the account inlines it (live-verified): {@code (SELECT
     * IDENTIFIER($tok)(4))} as {@code PR(X)}, {@code (SELECT RANDOM() + 1)} as {@code (RANDOM()) + 1}.
     *
     * @param executor the executor that resolves the call
     * @param subquery the subquery
     * @return the item, or null for any other subquery
     */
    static Expression inlinedItem(final QueryExecutor executor, final SubqueryExpression subquery) {
        final Expression item = constantItem(executor, subquery);
        if (item == null) {
            return null;
        }
        if (freshItem(executor, item) != null) {
            return item;
        }
        try {
            final FrostlakeParser.SelectStatementContext statement = executor.subqueryStatement(
                subquery.getSubquery());
            return ProcedureTokenCalls.holdsPlannedCall(executor, statement) ? item : null;
        } catch (final RuntimeException unread) {
            return null;
        }
    }

    /** An item that calls a function answering anew, or null for any other item (or none). */
    private static Expression freshItem(final QueryExecutor executor, final Expression item) {
        if (item == null) {
            return null;
        }
        final PlanFreshCallWalk walk = new PlanFreshCallWalk(executor);
        item.accept(walk);
        return walk.fresh() ? item : null;
    }

    /** The one item of a FROM-less scalar subquery of one SELECT, or null for any other subquery. */
    private static Expression constantItem(final QueryExecutor executor, final SubqueryExpression subquery) {
        if (subquery.isExistence() || subquery.isLateral()) {
            return null;
        }
        return queryItem(executor, subquery.getSubquery());
    }

    /** The one item of a query of one FROM-less SELECT, or null for any other query. */
    private static Expression queryItem(final QueryExecutor executor, final String query) {
        try {
            final FrostlakeParser.SelectStatementContext statement = executor.subqueryStatement(query);
            if (statement == null || !statement.setOperator().isEmpty() || statement.selectOperand().size() != 1
                    || statement.orderByClause() != null || statement.limitClause() != null) {
                return null;
            }
            final FrostlakeParser.SelectClauseContext clause = statement.selectOperand(0).selectClause();
            if (clause == null || clause.tableExpression() != null || clause.whereClause() != null
                    || clause.selectList().selectItem().size() != 1
                    || !(clause.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final ParseTree value = SelectItemAccessors.getItemValueExpr(clause.selectList().selectItem(0));
            return value == null ? null : ExpressionEvaluator.parse(ParseTreeText.getOriginalText(
                SelectItemAccessors.getItemExpression(clause.selectList().selectItem(0))));
        } catch (final RuntimeException unread) {
            return null;
        }
    }
}
