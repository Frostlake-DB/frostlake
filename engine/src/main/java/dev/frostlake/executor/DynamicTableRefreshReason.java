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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Why a dynamic table created with REFRESH_MODE AUTO settles on FULL, and why one created with REFRESH_MODE
 * INCREMENTAL is refused: the first construct of its query, in the order the query is written, that its incremental
 * refresh does not follow, through the views it reads (all live-verified):
 *
 * <pre>
 *   a LIMIT or FETCH, ahead of what its query holds  Change tracking is not supported on queries with LIMIT.
 *   EXISTS, a scalar subquery, IN (SELECT …),        Change tracking is not supported on queries with subquery
 *   a quantified comparison                          expressions.
 *   a view whose query reads no relation             Change tracking is not supported on queries with row generators.
 *   a window over the whole result (no PARTITION)    This dynamic table contains a complex query. …
 *   an aggregate with no GROUP BY                    This dynamic table contains a complex query. …
 *   a function whose value changes per run           Query contains the function 'RANDOM', but change tracking is not
 *                                                    supported on queries with non-deterministic functions. …
 * </pre>
 *
 * A plain projection, a GROUP BY, a join, UNION and UNION ALL, and FLATTEN are followed: the table stays INCREMENTAL.
 * A construct inside a view names the view it is in, {@code (inside view 'DB.SCHEMA.V')}, and a function the view the
 * query reads it through, {@code the function 'RANDOM' from view 'V'}; the row generators and the complex query name
 * none.
 *
 * <p>An INCREMENTAL table takes a complex query and a view's row generator; any other construct refuses its CREATE.
 * A function that answers a new value each call (RANDOM and the other generators, UUID_STRING without arguments,
 * CURRENT_SESSION) is refused first, the innermost first, as an invalid definition: {@code error line L at position
 * P} and {@code Non-deterministic function 'RANDOM' not allowed in definition.}. The rest are refused with the reason
 * above, placed at the function's name, at EXISTS, at a quantifier or at a scalar subquery's SELECT, and at no place
 * ({@code line 0 at position -1}) for a LIMIT and an IN; inside a view the place is counted in the view's own CREATE
 * statement.
 */
public final class DynamicTableRefreshReason {

    private static final String SUBQUERY = "Change tracking is not supported on queries with subquery expressions.";
    private static final String ROW_GENERATORS = "Change tracking is not supported on queries with row generators.";
    private static final String LIMIT = "Change tracking is not supported on queries with LIMIT.";
    private static final String COMPLEX = "This dynamic table contains a complex query. Refresh mode has been"
        + " automatically set to FULL for more predictable performance. To use INCREMENTAL, re-create the dynamic"
        + " table with REFRESH_MODE=INCREMENTAL. See"
        + " https://docs.snowflake.com/user-guide/dynamic-table-performance-guide for details.";
    private static final String MORE = " For more information, see"
        + " https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.";
    /** The kind of a reason that names a function whose value moves; its sentence is built for the function. */
    private static final String FUNCTION = "function";
    /** The functions an INCREMENTAL definition refuses outright, each answering a new value every call. */
    private static final Set<String> GENERATORS = Set.of("RANDOM", "RANDSTR", "NORMAL", "ZIPF", "UNIFORM",
        "UUID_STRING", "CURRENT_SESSION");
    /** The functions whose value moves between refreshes, by the name the reason gives them. */
    private static final Map<String, String> MOVING = new HashMap<>();
    /** How deep views reading views are followed. */
    private static final int MAX_DEPTH = 32;

    static {
        MOVING.put("CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP");
        MOVING.put("LOCALTIMESTAMP", "LOCALTIMESTAMP");
        MOVING.put("CURRENT_DATE", "CURRENT_DATE");
        MOVING.put("CURRENT_TIME", "CURRENT_TIME");
        MOVING.put("LOCALTIME", "LOCALTIME");
        MOVING.put("SYSDATE", "CURRENT_TIMESTAMP");
        MOVING.put("GETDATE", "CURRENT_TIMESTAMP");
        MOVING.put("SYSTIMESTAMP", "CURRENT_TIMESTAMP");
        MOVING.put("SEQ1", "SEQ1");
        MOVING.put("SEQ2", "SEQ2");
        MOVING.put("SEQ4", "SEQ4");
        MOVING.put("SEQ8", "SEQ8");
    }

    private final Catalog catalog;
    private final FunctionRegistry functions;
    private final String database;
    private final String schema;
    private final int depth;
    /** Whether the walk judges an INCREMENTAL definition, which takes a complex query and a row generator. */
    private final boolean incremental;
    private final Set<String> cteNames = new HashSet<>();
    /** The construct's place, or null for none. */
    private Token at;
    /** A function the reason names, or null. */
    private String function;
    /** The view the query reads the construct through, or null. */
    private String fromView;
    /** The view the construct is in, qualified, or null. */
    private String inside;
    /** A place the account reports for the function in its own words, or null for the function's name. */
    private int[] fixedPlace;

    private DynamicTableRefreshReason(final Catalog catalog, final FunctionRegistry functions, final String database,
                                      final String schema, final int depth, final boolean incremental,
                                      final ParseTree query) {
        this.catalog = catalog;
        this.functions = functions;
        this.database = database;
        this.schema = schema;
        this.depth = depth;
        this.incremental = incremental;
        QueryExecutor.collectCteNames(query, cteNames);
    }

    /**
     * Why a dynamic table's query settles an AUTO refresh mode on FULL.
     *
     * @param catalog   the catalog
     * @param functions the functions, which say what aggregates
     * @param database  the dynamic table's database
     * @param schema    the dynamic table's schema
     * @param query     the dynamic table's query
     * @return the reason, or null when the query is refreshed incrementally
     */
    public static String of(final Catalog catalog, final FunctionRegistry functions, final String database,
                            final String schema, final FrostlakeParser.SelectStatementContext query) {
        final DynamicTableRefreshReason judged = new DynamicTableRefreshReason(catalog, functions, database, schema, 0,
            false, query);
        final String kind = judged.walk(query);
        return kind == null ? null : judged.sentence(kind);
    }

    /**
     * Why a dynamic table's query cannot be created with REFRESH_MODE INCREMENTAL, as the CREATE is refused.
     *
     * @param catalog   the catalog
     * @param functions the functions
     * @param database  the dynamic table's database
     * @param schema    the dynamic table's schema
     * @param query     the dynamic table's query
     * @return the refusal, or null when the query refreshes incrementally
     */
    public static String incrementalRefusal(final Catalog catalog, final FunctionRegistry functions,
                                            final String database, final String schema,
                                            final FrostlakeParser.SelectStatementContext query) {
        final DynamicTableRefreshReason generator = new DynamicTableRefreshReason(catalog, functions, database, schema,
            0, true, query);
        if (generator.generator(query)) {
            final String place = generator.at == null ? "line 0 at position -1"
                : "line " + generator.at.getLine() + " at position " + generator.at.getCharPositionInLine();
            return SqlCompilationError.PREFIX + " error " + place + "\nInvalid Dynamic Table definition."
                + " Non-deterministic function '" + generator.function + "'"
                + (generator.fromView == null ? "" : " from view '" + generator.fromView + "'")
                + " not allowed in definition." + MORE;
        }
        final DynamicTableRefreshReason judged = new DynamicTableRefreshReason(catalog, functions, database, schema, 0,
            true, query);
        final String kind = judged.walk(query);
        if (kind == null) {
            return null;
        }
        final String place = judged.fixedPlace != null
            ? "line " + judged.fixedPlace[0] + " at position " + judged.fixedPlace[1]
            : judged.at == null ? "line 0 at position -1"
            : "line " + judged.at.getLine() + " at position " + judged.at.getCharPositionInLine();
        return SqlCompilationError.PREFIX + " " + place + ":\n" + judged.sentence(kind);
    }

    /** The reason's sentence for the construct found, naming the view it is in. */
    private String sentence(final String kind) {
        if (COMPLEX.equals(kind) || ROW_GENERATORS.equals(kind)) {
            return kind;
        }
        final String within = inside == null ? "" : " (inside view '" + inside + "').";
        if (FUNCTION.equals(kind)) {
            return "Query contains the function '" + function + "'"
                + (fromView == null ? "" : " from view '" + fromView + "'")
                + ", but change tracking is not supported on queries with non-deterministic functions." + MORE + within;
        }
        return kind + within;
    }

    private String walk(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                && (((FrostlakeParser.SelectStatementContext) node).limitClause() != null
                    || ((FrostlakeParser.SelectStatementContext) node).fetchClause() != null)) {
            return LIMIT;
        }
        if (!incremental && node instanceof FrostlakeParser.SelectClauseContext
                && ((FrostlakeParser.SelectClauseContext) node).groupByClause() == null
                && UnreadColumnPruning.containsAggregate(((FrostlakeParser.SelectClauseContext) node).selectList(),
                    functions)) {
            return COMPLEX;
        }
        if (!incremental && node instanceof FrostlakeParser.SelectClauseContext && depth > 0
                && ((FrostlakeParser.SelectClauseContext) node).tableExpression() == null) {
            return ROW_GENERATORS;
        }
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            at = ((FrostlakeParser.ExistsExprContext) node).getStart();
            return SUBQUERY;
        }
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            at = ((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement().getStart();
            return SUBQUERY;
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext
                && ((FrostlakeParser.QuantifiedComparisonExprContext) node).selectStatement() != null) {
            at = ((FrostlakeParser.QuantifiedComparisonExprContext) node).quantifier().getStart();
            return SUBQUERY;
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext
                || node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return SUBQUERY;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String reason = callReason((FrostlakeParser.FunctionCallExprContext) node);
            if (reason != null) {
                return reason;
            }
        }
        if (node instanceof FrostlakeParser.CurrentTimestampExprContext
                || node instanceof FrostlakeParser.CurrentDateExprContext
                || node instanceof FrostlakeParser.CurrentTimeExprContext) {
            final Token word = ((ParserRuleContext) node).getStart();
            at = word;
            function = MOVING.get(word.getText().toUpperCase(Locale.ROOT));
            return function == null ? null : FUNCTION;
        }
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            final String within = viewReason(((FrostlakeParser.TableSourceContext) node).tableQualifiedName(), false);
            if (within != null) {
                return within;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final String reason = walk(node.getChild(i));
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    /** The reason a call gives: a window over the whole result, or a function whose value changes per run. */
    private String callReason(final FrostlakeParser.FunctionCallExprContext call) {
        if (!incremental && call.overClause() != null && call.overClause().partitionByClause() == null) {
            return COMPLEX;
        }
        final String name = calledName(call);
        final String moving = MOVING.get(name);
        if (moving != null || !incremental && isGenerator(call, name)) {
            at = call.functionName().getStart();
            function = moving != null ? moving : name;
            if ("SYSDATE".equals(name)) {
                fixedPlace = new int[] {1, 35};
            } else if ("GETDATE".equals(name) || "SYSTIMESTAMP".equals(name)) {
                fixedPlace = new int[] {1, 1};
            }
            return FUNCTION;
        }
        return null;
    }

    /**
     * Whether the query holds a function that answers a new value each call, the innermost first, kept when it does.
     */
    private boolean generator(final ParseTree node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            if (generator(node.getChild(i))) {
                return true;
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String name = calledName(call);
            if (isGenerator(call, name)) {
                at = call.functionName().getStart();
                function = name;
                return true;
            }
        }
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            return viewReason(((FrostlakeParser.TableSourceContext) node).tableQualifiedName(), true) != null;
        }
        return false;
    }

    private static String calledName(final FrostlakeParser.FunctionCallExprContext call) {
        final String[] parts = ParseTreeText.functionNameParts(call.functionName());
        return parts[parts.length - 1].toUpperCase(Locale.ROOT);
    }

    /** Whether a call answers a new value each time: a generator, UUID_STRING with no arguments. */
    private static boolean isGenerator(final FrostlakeParser.FunctionCallExprContext call, final String name) {
        return GENERATORS.contains(name) && (!"UUID_STRING".equals(name) || call.functionArgList() == null);
    }

    /**
     * The reason a view the query reads gives, judged by its own query, or null; what it found is kept, the view
     * named as the one it is read through and, where no view inside it holds it, as the one it is in.
     */
    private String viewReason(final FrostlakeParser.TableQualifiedNameContext name, final boolean generators) {
        if (depth >= MAX_DEPTH || cteNames.contains(ParseTreeText.getQualifiedName(name).toUpperCase(Locale.ROOT))) {
            return null;
        }
        final String[] parts = catalog.withoutAccount(ParseTreeText.qualifiedNameParts(name), 3);
        final String viewDatabase = parts.length == 3 ? parts[0] : database;
        final String viewSchema = parts.length >= 2 ? parts[parts.length - 2] : schema;
        final View view = viewNamed(viewDatabase, viewSchema, parts[parts.length - 1]);
        final FrostlakeParser.SelectStatementContext body = view == null ? null : ChangeTrackedQuery.body(view);
        if (body == null) {
            return null;
        }
        final DynamicTableRefreshReason within = new DynamicTableRefreshReason(catalog, functions, viewDatabase,
            viewSchema, depth + 1, incremental, body);
        final String reason;
        if (generators) {
            reason = within.generator(body) ? FUNCTION : null;
        } else {
            reason = within.walk(body);
        }
        if (reason == null) {
            return null;
        }
        at = within.at;
        function = within.function;
        fixedPlace = within.fixedPlace;
        fromView = view.getName();
        inside = within.inside != null ? within.inside : viewDatabase + "." + viewSchema + "." + view.getName();
        return reason;
    }

    private View viewNamed(final String viewDatabase, final String viewSchema, final String viewName) {
        if (viewDatabase == null || viewSchema == null) {
            return null;
        }
        try {
            final Database holder = catalog.databaseExact(viewDatabase);
            final Schema container = holder == null ? null : holder.schemaExact(viewSchema);
            return container != null && container.hasViewExact(viewName) ? container.getView(viewName) : null;
        } catch (final RuntimeException missing) {
            return null;
        }
    }
}
