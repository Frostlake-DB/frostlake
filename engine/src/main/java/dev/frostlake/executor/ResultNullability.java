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

import dev.frostlake.executor.expressions.ArrayAccessExpression;
import dev.frostlake.executor.expressions.BetweenExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.InExpression;
import dev.frostlake.executor.expressions.IntervalExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LikeAnyAllExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.ObjectAccessExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.executor.expressions.WindowFunctionExpression;
import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether a select item can answer NULL, worked out from the item itself as the account works it out for a
 * JavaScript procedure's {@code isColumnNullable} (live-verified):
 *
 * <pre>
 *   a column                                   its own NOT NULL, or nullable on a side an outer join extends;
 *                                              a derived relation's as the projection that made it judged
 *   NULL, TRUE, FALSE                          nullable; a number, a string or an interval constant is not
 *   +, -, *, /, %, ||, a sign, a cast          nullable when an operand is; TRY_CAST always
 *   a comparison, AND, OR, NOT, IS [NOT] NULL,
 *   BETWEEN, LIKE, IN, EQUAL_NULL               nullable
 *   CASE, IFF, NVL2, DECODE                     nullable when a result is, or with no ELSE or default
 *   COALESCE, NVL, IFNULL                       nullable only when every argument is
 *   an aggregate or a window function           nullable, but for COUNT, LISTAGG, ARRAY_AGG, OBJECT_AGG,
 *                                              HASH_AGG, APPROX_COUNT_DISTINCT and the ranking functions
 *   PARSE_JSON, the constructors, GET, a path,
 *   TRY_*, NULLIF, the session's names, …       nullable
 *   HASH, RANDOM, UUID_STRING, SEQ4, the
 *   current date and time, ZEROIFNULL           not nullable
 *   any other function                         nullable when an argument is
 *   a key a ROLLUP, a CUBE or GROUPING SETS
 *   leaves out of a set, or a column one names  nullable (see RolledKeys)
 * </pre>
 *
 * A name no column answers to is a word of the call it is written in, such as a date part, and no value.
 *
 * <p>The same judgement spells a range refusal's type, {@code {nullable}} or {@code {not null}}: a cast of a
 * VALUES column holding no NULL, of {@code ABS(1234567.1234)} or of a literal operand is {@code {not null}}, one of
 * {@code NULLIF(…)}, a scalar subquery or an aggregate {@code {nullable}} (live-verified); see
 * {@link #judgingReferences}.
 */
public final class ResultNullability {

    /**
     * The functions that can answer NULL whatever their arguments are, a lookup that finds nothing among them:
     * ARRAY_POSITION, ARRAY_MIN, ARRAY_MAX, ARRAY_SIZE, JSON_EXTRACT_PATH_TEXT, CHECK_XML and GETVARIABLE are
     * nullable over columns that are not (live-verified).
     */
    private static final Set<String> ALWAYS = new HashSet<String>(Arrays.asList(
        "ARRAY_POSITION", "ARRAY_MIN", "ARRAY_MAX", "ARRAY_SIZE", "JSON_EXTRACT_PATH_TEXT", "CHECK_XML", "GETVARIABLE",
        "PARSE_JSON", "TRY_PARSE_JSON", "CHECK_JSON", "PARSE_XML", "XMLGET", "ARRAY_CONSTRUCT",
        "ARRAY_CONSTRUCT_COMPACT", "OBJECT_CONSTRUCT", "OBJECT_CONSTRUCT_KEEP_NULL", "GET", "GET_PATH",
        "GET_IGNORE_CASE", "TO_GEOGRAPHY", "TO_GEOMETRY", "CURRENT_USER", "CURRENT_ROLE", "CURRENT_DATABASE",
        "CURRENT_SCHEMA", "CURRENT_SESSION", "CURRENT_WAREHOUSE", "NULLIF", "NULLIFZERO", "REGEXP_SUBSTR", "STRTOK",
        "EQUAL_NULL", "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE", "SUM", "MIN", "MAX", "AVG",
        "ANY_VALUE", "MEDIAN", "MODE", "COUNT_IF", "BOOLAND_AGG", "BOOLOR_AGG", "BOOLXOR_AGG", "BITAND_AGG",
        "BITOR_AGG", "BITXOR_AGG", "MIN_BY", "MAX_BY", "STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VARIANCE",
        "VARIANCE_POP", "VARIANCE_SAMP", "VAR_POP", "VAR_SAMP", "KURTOSIS", "SKEW", "CORR", "COVAR_POP",
        "COVAR_SAMP", "PERCENTILE_CONT", "PERCENTILE_DISC", "APPROX_PERCENTILE"));

    /** The functions that never answer NULL. */
    private static final Set<String> NEVER = new HashSet<String>(Arrays.asList(
        "COUNT", "HASH", "HASH_AGG", "APPROX_COUNT_DISTINCT", "HLL", "LISTAGG", "ARRAY_AGG", "OBJECT_AGG",
        "ROW_NUMBER", "RANK", "DENSE_RANK", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "RANDOM", "UUID_STRING", "CURRENT_DATE",
        "CURRENT_TIME", "CURRENT_TIMESTAMP", "LOCALTIME", "LOCALTIMESTAMP", "SYSDATE", "GETDATE", "SYSTIMESTAMP",
        "ZEROIFNULL", "PI"));

    /** The functions that answer NULL only when every argument is NULL. */
    private static final Set<String> FIRST_NON_NULL = new HashSet<String>(Arrays.asList("COALESCE", "NVL",
        "IFNULL"));

    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final RolledKeys rolled;
    // The executor that parses a scalar subquery item, set where the judge tells what a result's metadata tells (see
    // reporting); null for a judge of the values themselves.
    private final QueryExecutor reporter;
    // How a column reference is judged when the evaluator resolves the names (see judgingReferences); null where the
    // select list's relations do.
    private final ReferenceNullability references;

    /**
     * @param table        the relation the select list reads
     * @param aliasToTable the relations a qualified name reaches, by name, or null
     */
    ResultNullability(final Table table, final Map<String, Table> aliasToTable) {
        this(table, aliasToTable, RolledKeys.NONE);
    }

    /**
     * @param table        the relation the select list reads
     * @param aliasToTable the relations a qualified name reaches, by name, or null
     * @param rolled       the keys the select's grouping sets leave out of a set
     */
    ResultNullability(final Table table, final Map<String, Table> aliasToTable, final RolledKeys rolled) {
        this(table, aliasToTable, rolled, null);
    }

    private ResultNullability(final Table table, final Map<String, Table> aliasToTable, final RolledKeys rolled,
                              final QueryExecutor reporter) {
        this(table, aliasToTable, rolled, reporter, null);
    }

    private ResultNullability(final Table table, final Map<String, Table> aliasToTable, final RolledKeys rolled,
                              final QueryExecutor reporter, final ReferenceNullability references) {
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.rolled = rolled;
        this.reporter = reporter;
        this.references = references;
    }

    /**
     * A judge of the values an expression evaluated row by row can take, for the refusals that spell the type of a
     * value they could not hold: every rule above, with a column reference judged as the evaluator resolves it.
     *
     * @param references how a column reference is judged
     * @return the judge
     */
    public static ResultNullability judgingReferences(final ReferenceNullability references) {
        return new ResultNullability(null, null, RolledKeys.NONE, null, references);
    }

    /**
     * A judge of what a result's metadata tells of a select item, which a JavaScript procedure's result set reads as
     * {@code isColumnNullable}: as {@link #of} judges the values, but a column of a VALUES clause is nullable only
     * where a row holds NULL in it, and an item that is a scalar subquery is judged as the subquery's own item is
     * (see {@link ScalarSubqueryNullability}): {@code (SELECT 1)} is not nullable (live-verified). A derived
     * relation's column made of such an item keeps the judgement of its values, as a refusal spells it.
     *
     * @param table        the relation the select list reads
     * @param aliasToTable the relations a qualified name reaches, by name, or null
     * @param rolled       the keys the select's grouping sets leave out of a set
     * @param executor     the executor that parses a subquery
     * @return the judge
     */
    static ResultNullability reporting(final Table table, final Map<String, Table> aliasToTable,
                                       final RolledKeys rolled, final QueryExecutor executor) {
        return new ResultNullability(table, aliasToTable, rolled, executor);
    }

    /**
     * Whether a select item is told nullable, as {@link #reporting} has it.
     *
     * @param item the select item
     * @return whether it is
     */
    boolean reported(final Expression item) {
        if (reporter != null && item instanceof SubqueryExpression && !((SubqueryExpression) item).isExistence()
                && !((SubqueryExpression) item).isLateral()) {
            return ScalarSubqueryNullability.of(reporter, (SubqueryExpression) item);
        }
        return of(item);
    }

    /**
     * Whether the expression can answer NULL.
     *
     * @param expression the select item
     * @return whether it can
     */
    public boolean of(final Expression expression) {
        if (rolled.holds(expression)) {
            return true;
        }
        if (expression instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) expression).getType();
            return type == LiteralType.NULL || type == LiteralType.BOOLEAN;
        }
        if (expression instanceof ColumnReferenceExpression) {
            return column((ColumnReferenceExpression) expression);
        }
        if (expression instanceof IntervalExpression) {
            // An interval is made of its text: INTERVAL '1' YEAR is a value as a number or a string is.
            final Expression text = ((IntervalExpression) expression).getValueExpression();
            return text == null || of(text);
        }
        if (expression instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expression;
            if (cast.isTryMode() && !(HeldNullReading.current() != null && keepsText(cast))) {
                return true;
            }
            return of(cast.getExpression());
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            final BinaryOperator operator = binary.getOperator();
            final boolean arithmetic = operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
                || operator == BinaryOperator.MODULO || operator == BinaryOperator.CONCAT;
            return !arithmetic || of(binary.getLeft()) || of(binary.getRight());
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() == UnaryOperator.NOT || unary.getOperator() == UnaryOperator.EXISTS
                || of(unary.getOperand());
        }
        if (expression instanceof IsNullExpression || expression instanceof BetweenExpression
                || expression instanceof InExpression || expression instanceof LikeAnyAllExpression) {
            return true;
        }
        if (expression instanceof CaseExpression) {
            final CaseExpression caseExpression = (CaseExpression) expression;
            if (caseExpression.getElseExpression() == null || of(caseExpression.getElseExpression())) {
                return true;
            }
            for (final WhenClause when : caseExpression.getWhenClauses()) {
                if (of(when.getResult())) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ObjectAccessExpression || expression instanceof ArrayAccessExpression) {
            return true;
        }
        if (expression instanceof WindowFunctionExpression) {
            final String name = ((WindowFunctionExpression) expression).getFunctionName();
            return name == null || !NEVER.contains(name.toUpperCase(Locale.ROOT));
        }
        if (expression instanceof FunctionCallExpression) {
            return call((FunctionCallExpression) expression);
        }
        return true;
    }

    /** A function's answer: by its own rule, else nullable when an argument is. */
    private boolean call(final FunctionCallExpression call) {
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> arguments = call.getArguments();
        if (ALWAYS.contains(name) || name.startsWith("TRY_") || name.startsWith("AS_")) {
            return true;
        }
        if (NEVER.contains(name)) {
            return false;
        }
        if (FIRST_NON_NULL.contains(name)) {
            for (final Expression argument : arguments) {
                if (!of(argument)) {
                    return false;
                }
            }
            return true;
        }
        if ("IFF".equals(name) || "NVL2".equals(name)) {
            return arguments.size() < 3 || of(arguments.get(1)) || of(arguments.get(2));
        }
        if ("DECODE".equals(name)) {
            // DECODE(expr, search, result, …, [default]): its results, and NULL when no default is written.
            if (arguments.size() % 2 == 0) {
                for (int i = 2; i < arguments.size(); i += 2) {
                    if (of(arguments.get(i))) {
                        return true;
                    }
                }
                return of(arguments.get(arguments.size() - 1));
            }
            return true;
        }
        for (final Expression argument : arguments) {
            if (of(argument)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a TRY_CAST converts a text to a text that holds every text, so that it never fails and answers NULL only
     * where its operand does. Read by the rows held (see {@link HeldNullReading}), {@code TRY_CAST(s AS VARCHAR)} of a
     * column holding no NULL answers none, where {@code TRY_CAST(s AS VARCHAR(5))} may (live-verified).
     */
    private boolean keepsText(final CastExpression cast) {
        return holdsEveryText(cast) && readsText(cast.getExpression());
    }

    /**
     * Whether a cast's target is a text type of the greatest width, which every text fits.
     *
     * @param cast the cast
     * @return whether it targets such a text
     */
    static boolean holdsEveryText(final CastExpression cast) {
        final DataType target = cast.getTargetDataType();
        return target instanceof StringType
            && ((StringType) target).getMaxLength() >= StringType.VARCHAR.getMaxLength();
    }

    /** Whether an operand is a text: a column declared one, or a cast to one. */
    private boolean readsText(final Expression operand) {
        if (operand instanceof CastExpression) {
            return ((CastExpression) operand).getTargetDataType() instanceof StringType;
        }
        if (!(operand instanceof ColumnReferenceExpression)) {
            return false;
        }
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) operand;
        TableColumn column = qualifiedColumn(reference, aliasToTable);
        if (column == null && !reference.isQualified() && table != null && table.hasColumn(reference.getColumnName())) {
            column = table.getColumns().get(table.getColumnIndex(reference.getColumnName()));
        }
        return column != null && column.getDataType() instanceof StringType;
    }

    /** A column's own NOT NULL, and the NULLs an outer join extends its relation with. */
    private boolean column(final ColumnReferenceExpression reference) {
        if (references != null) {
            return references.nullable(reference);
        }
        final String name = reference.getColumnName();
        if (reference.isQualified() && aliasToTable != null) {
            final Table relation = relationNamed(aliasToTable, reference.getTableName());
            if (relation != null && relation.hasColumn(name)) {
                final JoinedRelations joined = table == null ? null : table.getJoinedRelations();
                return nullable(relation, relation.getColumns().get(relation.getColumnIndex(name)))
                    || joined != null && joined.includes(relation) && joined.isNullExtended(relation);
            }
        }
        // A name no column answers to is a word of the call it stands in — DATEADD(day, …) — and no value.
        if (table == null || !table.hasColumn(name)) {
            return false;
        }
        final TableColumn column = table.getColumns().get(table.getColumnIndex(name));
        // A column judged unable to hold NULL, a derived relation's or a VALUES clause's, still may on a side an
        // outer join extends.
        return nullable(ownerOf(name), column) || table.getJoinedRelations() != null
            && table.getJoinedRelations().isNullExtendedColumn(column);
    }

    /**
     * The relation an unqualified name reads: the select's own, or of a join the one relation holding a column of
     * the name.
     */
    private Table ownerOf(final String name) {
        if (table.residentSource() != null || aliasToTable == null) {
            return table;
        }
        Table owner = null;
        for (final Table relation : aliasToTable.values()) {
            if (relation != null && relation != owner && relation.hasColumn(name)) {
                if (owner != null) {
                    return table;
                }
                owner = relation;
            }
        }
        return owner != null ? owner : table;
    }

    /**
     * A column's judgement, or where its rows are read for their NULLs (see {@link HeldNullReading}) whether a row of
     * its catalog table, or of its VALUES clause, holds one.
     */
    private boolean nullable(final Table owner, final TableColumn column) {
        final StoredColumnValues held = HeldNullReading.current();
        if (held != null && owner != null) {
            final Boolean holds = held.holdsNull(owner, column.getName());
            if (holds != null) {
                return holds.booleanValue();
            }
            if (column.getHeldNull() != null) {
                return column.getHeldNull().booleanValue();
            }
        }
        return nullable(column);
    }

    /**
     * A column's own NOT NULL, or a derived relation's column's judgement of its item: a column of
     * (SELECT 1 AS c1) cannot answer NULL (live-verified). Told as metadata, a VALUES clause's column is nullable
     * only where a row holds NULL in it.
     */
    private boolean nullable(final TableColumn column) {
        if (reporter != null && column.getHeldNull() != null) {
            return column.getHeldNull().booleanValue();
        }
        return column.getValuesNullable() != null ? column.getValuesNullable().booleanValue() : column.isNullable();
    }

    /**
     * The column a qualified reference names in the relation its qualifier reaches: {@code o.k} of
     * {@code jsn j JOIN t o} is t's {@code k}, whatever column of that name another relation of the join has.
     *
     * @param reference    the reference
     * @param aliasToTable the relations a qualified name reaches, by name, or null
     * @return the column, or null for an unqualified reference or a qualifier no relation answers to
     */
    static TableColumn qualifiedColumn(final ColumnReferenceExpression reference,
                                       final Map<String, Table> aliasToTable) {
        if (!reference.isQualified() || aliasToTable == null) {
            return null;
        }
        final Table relation = relationNamed(aliasToTable, reference.getTableName());
        final String name = reference.getColumnName();
        return relation != null && relation.hasColumn(name) ? relation.getColumns().get(relation.getColumnIndex(name))
            : null;
    }

    /** The relation a qualifier names, exactly or else by case alone. */
    static Table relationNamed(final Map<String, Table> aliasToTable, final String qualifier) {
        final Table exact = aliasToTable.get(qualifier);
        if (exact != null || qualifier == null) {
            return exact;
        }
        for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(qualifier)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
