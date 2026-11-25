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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.MaterializedViewValidity;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a materialized view's definition may not hold, refused as the view is created (live-verified). The
 * definition reads one table, and nothing but that table: a second table, a view, another materialized view or a
 * subquery with no FROM is more than one table, and no table at all is a source that is not accessible. Over its one
 * table it may not join, intersect or subtract (UNION and UNION ALL stay), nor hold a subquery, a window function,
 * HAVING, QUALIFY, a LIMIT (TOP and FETCH alike), ORDER BY, time travel, a function whose value is not deterministic,
 * GROUPING SETS, ROLLUP or CUBE, GROUP BY ALL, a DISTINCT aggregate, a GROUP BY key its select list leaves out, or an
 * aggregate the view cannot keep up to date (MEDIAN, the percentiles, LISTAGG, ARRAY_AGG, MODE, OBJECT_AGG,
 * HASH_AGG). Where several are written, the first in that order is the one reported. A select list leaving an
 * expression unnamed, with no column list to name it, is refused after time travel and before the clauses that
 * follow it (GROUP BY ALL alone excepted, which is reported ahead of it).
 *
 * <p>A materialized view reads its table with a hidden partition column, SYS_MV_SOURCE_PARTITION, which an UNPIVOT
 * keeps among the columns it does not unpivot. A star over an UNPIVOT therefore selects it: as the view's whole select
 * list it is refused as a column name a materialized view may not have, and anywhere else (beside another item, in a
 * branch of a set operation, or through a derived table or CTE that a star reads) as a duplicate column
 * (live-verified).
 */
final class MaterializedViewDefinition {

    /** The functions whose value is not deterministic, by the name the refusal gives each. */
    private static final Set<String> NOT_DETERMINISTIC = new HashSet<>(Arrays.asList(
        "RANDOM", "RANDSTR", "UNIFORM", "NORMAL", "ZIPF", "SEQ1", "SEQ2", "SEQ4", "SEQ8",
        "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "LOCALTIMESTAMP", "LOCALTIME",
        "CURRENT_USER", "CURRENT_ROLE", "CURRENT_ROLE_TYPE", "CURRENT_SECONDARY_ROLES", "CURRENT_AVAILABLE_ROLES",
        "CURRENT_DATABASE", "CURRENT_SCHEMA", "CURRENT_SCHEMAS", "CURRENT_WAREHOUSE", "CURRENT_ACCOUNT",
        "CURRENT_ACCOUNT_NAME", "CURRENT_ORGANIZATION_NAME", "CURRENT_REGION", "CURRENT_VERSION", "CURRENT_CLIENT",
        "CURRENT_SESSION", "CURRENT_STATEMENT", "CURRENT_TRANSACTION", "CURRENT_IP_ADDRESS", "LAST_QUERY_ID",
        "LAST_TRANSACTION", "IS_ROLE_IN_SESSION", "IS_DATABASE_ROLE_IN_SESSION", "INVOKER_ROLE", "INVOKER_SHARE",
        "ANY_VALUE", "MAX_BY", "MIN_BY", "APPROX_TOP_K", "APPROX_PERCENTILE_ESTIMATE"));

    /** The aggregates a materialized view cannot keep up to date. */
    private static final Set<String> UNSUPPORTED_AGGREGATES = new HashSet<>(Arrays.asList(
        "MEDIAN", "PERCENTILE_CONT", "PERCENTILE_DISC", "LISTAGG", "ARRAY_AGG", "ARRAYAGG", "MODE", "OBJECT_AGG",
        "HASH_AGG"));

    private static final String DEFINITION = "Invalid materialized view definition. ";

    /** The hidden column a materialized view reads its table's partitions by. */
    private static final String PARTITION_COLUMN = "SYS_MV_SOURCE_PARTITION";

    private final Catalog catalog;
    private final FunctionRegistry functions;
    private final FrostlakeParser.CreateStatementContext create;
    private final Set<String> cteNames = new HashSet<>();

    private final List<FrostlakeParser.TableSourceContext> relations = new ArrayList<>();
    private final List<FrostlakeParser.SelectClauseContext> blocks = new ArrayList<>();
    private final List<ParserRuleContext> calls = new ArrayList<>();
    private final Set<String> joinTypes = new LinkedHashSet<>();
    private int relationsWithoutTable;
    private boolean unreadSource;
    private boolean inSubquery;
    private Token subquery;
    private Token timeTravel;
    private boolean window;
    private boolean having;
    private boolean qualify;
    private int qualifyDepth;
    private boolean limit;
    private Token orderBy;
    private Token groupingSet;
    private boolean groupByAll;
    /** Whether a star selects the hidden partition column an UNPIVOT keeps as the view's whole select list. */
    private boolean partitionColumnSelected;
    /** Whether a star selects that column where it meets another copy of it. */
    private boolean partitionColumnDuplicated;

    /** The one table the view reads, and the name its columns are spelled under. */
    private Table table;
    private String owner;

    /**
     * @param catalog   the catalog the definition's names resolve in
     * @param functions the functions, which tell an aggregate
     * @param create    the CREATE MATERIALIZED VIEW statement
     */
    MaterializedViewDefinition(final Catalog catalog, final FunctionRegistry functions,
                               final FrostlakeParser.CreateStatementContext create) {
        this.catalog = catalog;
        this.functions = functions;
        this.create = create;
    }

    /**
     * Refuses a definition that holds anything a materialized view may not. A definition that names a relation
     * the catalog does not hold is left for its compilation to refuse.
     */
    void validate() {
        final FrostlakeParser.SelectStatementContext select = create.selectStatement();
        if (select == null) {
            return;
        }
        QueryExecutor.collectCteNames(select, cteNames);
        walk(select);
        readUnpivotStars(select);
        if (unreadSource) {
            return;
        }
        final Set<String> tables = new LinkedHashSet<>();
        String viewRead = null;
        Token viewReadAt = null;
        int sources = relationsWithoutTable;
        for (final FrostlakeParser.TableSourceContext relation : relations) {
            final String[] parts = ParseTreeText.qualifiedNameParts(relation.tableQualifiedName());
            if (parts.length == 1 && isCte(parts[0])) {
                continue;
            }
            final String[] full = fullName(parts);
            final Schema schema = schemaOf(full);
            if (schema == null) {
                return;
            }
            final String name = full[2];
            final String key = full[0] + "." + full[1] + "." + name;
            if (schema.hasMaterializedView(name)) {
                // A materialized view is read together with the table it keeps.
                sources++;
            } else if (schema.hasViewExact(name) || schema.hasDynamicTable(name)) {
                if (viewRead == null) {
                    viewRead = name;
                    viewReadAt = relation.tableQualifiedName().getStart();
                }
            } else {
                final Table found = tableOf(schema, name);
                if (found == null) {
                    return;
                }
                if (table == null) {
                    table = found;
                    owner = ownerOf(relation, name);
                }
            }
            tables.add(key);
        }
        sources += tables.size();
        if (sources > 1) {
            refuse(SqlCompilationError.at(0, -1, DEFINITION + "More than one table referenced in the view definition"));
        }
        if (sources == 0) {
            refuse(SqlCompilationError.at(0, -1, MaterializedViewValidity.INACCESSIBLE));
        }
        if (!joinTypes.isEmpty()) {
            refuse(SqlCompilationError.at(0, -1, DEFINITION + "Join types [" + String.join(", ", joinTypes)
                + "] not allowed in view definition."));
        }
        if (viewRead != null) {
            refuse(at(viewReadAt, DEFINITION + "'" + SqlIdentifiers.spellCanonical(viewRead) + "' should be a table."));
        }
        if (partitionColumnDuplicated) {
            refuse(SqlCompilationError.of("duplicate column name '" + PARTITION_COLUMN + "'"));
        }
        if (timeTravel != null) {
            final int[] place = LeadingCommentOffset.rebase(timeTravel.getLine(), timeTravel.getCharPositionInLine());
            refuse(SqlCompilationError.inline("Invalid materialized view: Time travel on base table in line " + place[0]
                + " at position " + place[1] + "."));
        }
        if (unnamedColumn()) {
            refuseGroupByAll();
            refuse(SqlCompilationError.of("Missing column specification"));
        }
        final Token name = create.objectName().getStart();
        if (window) {
            refuse(at(name, DEFINITION + "window functions not allowed in view definition."));
        }
        if (having) {
            refuse(at(name, DEFINITION + "having clause not allowed in view definition."));
        }
        if (qualify) {
            refuse(at(name, DEFINITION + "qualify clause not allowed in view definition."));
        }
        if (limit) {
            refuse(at(name, DEFINITION + "limit clause not allowed in view definition."));
        }
        if (orderBy != null) {
            refuse(at(orderBy, "Invalid materialized view definition: ORDER BY not allowed in view definition."));
        }
        if (subquery != null) {
            refuse(at(subquery, DEFINITION + "sub-queries not allowed in view materialized definition."));
        }
        if (inSubquery) {
            refuse(SqlCompilationError.at(0, -1, DEFINITION + "sub-queries not allowed in view materialized definition."));
        }
        if (partitionColumnSelected) {
            refuseGroupingSet();
            refuse(SqlCompilationError.of("'" + PARTITION_COLUMN
                + "' cannot be used as a column name for materialized views."));
        }
        refuseNotDeterministic();
        refuseGroupingSet();
        refuseGroupByAll();
        refuseDistinctAggregates();
        for (final FrostlakeParser.SelectClauseContext block : blocks) {
            refuseUnselectedKeys(block);
        }
        refuseUnsupportedAggregates();
    }

    private void refuseGroupByAll() {
        if (groupByAll) {
            refuse(SqlCompilationError.inline(DEFINITION + "GROUP BY ALL not allowed in MV definition."));
        }
    }

    /**
     * Whether the view's columns go unnamed: it declares no column list, and its one query block selects an
     * expression with no name and no star.
     */
    private boolean unnamedColumn() {
        if (create.viewColumnList() != null || create.trailingViewColumns() != null) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext select = create.selectStatement();
        if (select.selectOperand().size() != 1) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext block = select.selectOperand(0).selectClause();
        if (block == null || block.selectList() == null || hasStar(block)) {
            return false;
        }
        for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
            if (SelectItemAccessors.isUnnamedExpressionItem(item)) {
                return true;
            }
        }
        return false;
    }

    private void refuseGroupingSet() {
        if (groupingSet != null) {
            final int[] place = LeadingCommentOffset.rebase(groupingSet.getLine(), groupingSet.getCharPositionInLine());
            refuse(SqlCompilationError.inline("Invalid materialized view: Grouping sets, cube, rollup are not supported:"
                + "  line '" + place[0] + "' at position '" + place[1] + "'."));
        }
    }

    /** Where a star of the definition selects the partition column an UNPIVOT keeps (see the class comment). */
    private void readUnpivotStars(final FrostlakeParser.SelectStatementContext select) {
        final List<FrostlakeParser.SelectOperandContext> operands = select.selectOperand();
        if (operands.size() > 1) {
            for (final FrostlakeParser.SelectOperandContext operand : operands) {
                final FrostlakeParser.SelectClauseContext block = operand.selectClause();
                if (block != null && (starOverUnpivot(block) || starOverUnpivotStar(block, select))) {
                    partitionColumnDuplicated = true;
                }
            }
            return;
        }
        final FrostlakeParser.SelectClauseContext block = operands.get(0).selectClause();
        if (block == null) {
            return;
        }
        if (starOverUnpivot(block)) {
            if (onlyStars(block)) {
                partitionColumnSelected = true;
            } else {
                partitionColumnDuplicated = true;
            }
        } else if (starOverUnpivotStar(block, select)) {
            partitionColumnDuplicated = true;
        }
    }

    /** Whether a query block selects a star FROM a relation an UNPIVOT is applied to. */
    private static boolean starOverUnpivot(final FrostlakeParser.SelectClauseContext block) {
        if (!hasStar(block) || block.tableExpression() == null) {
            return false;
        }
        for (final FrostlakeParser.TableReferenceContext reference : block.tableExpression().tableReference()) {
            if (reference.unpivotClause() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a query block selects a star FROM a derived table or a CTE one of whose query blocks selects a star over
     * an UNPIVOT, directly or through such a relation of its own.
     */
    private static boolean starOverUnpivotStar(final FrostlakeParser.SelectClauseContext block,
                                               final FrostlakeParser.SelectStatementContext statement) {
        if (!hasStar(block) || block.tableExpression() == null) {
            return false;
        }
        for (final FrostlakeParser.TableReferenceContext reference : block.tableExpression().tableReference()) {
            final FrostlakeParser.SelectStatementContext body = bodyRead(reference.tableSource(), statement);
            if (body == null) {
                continue;
            }
            for (final FrostlakeParser.SelectOperandContext operand : body.selectOperand()) {
                final FrostlakeParser.SelectClauseContext inner = operand.selectClause();
                if (inner != null && (starOverUnpivot(inner) || starOverUnpivotStar(inner, body))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The query a relation reads: a derived table's own, or that of the CTE of the statement its name is. */
    private static FrostlakeParser.SelectStatementContext bodyRead(final FrostlakeParser.TableSourceContext source,
                                                                   final FrostlakeParser.SelectStatementContext
                                                                       statement) {
        if (source == null) {
            return null;
        }
        if (source.selectStatement() != null) {
            return source.selectStatement();
        }
        if (source.tableQualifiedName() == null || statement.withClause() == null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        if (parts.length != 1) {
            return null;
        }
        for (final FrostlakeParser.CteDefinitionContext cte : statement.withClause().cteDefinition()) {
            if (ParseTreeText.namePartText(cte.nameStartPart()).equalsIgnoreCase(parts[0])) {
                return cte.selectStatement();
            }
        }
        return null;
    }

    private static boolean hasStar(final FrostlakeParser.SelectClauseContext block) {
        for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean onlyStars(final FrostlakeParser.SelectClauseContext block) {
        for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
            if (!SelectItemAccessors.isStarItem(item) && !SelectItemAccessors.isQualifiedStarItem(item)) {
                return false;
            }
        }
        return true;
    }

    /** Collects what the definition holds, in the order it is written. */
    private void walk(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            final FrostlakeParser.SelectStatementContext statement = (FrostlakeParser.SelectStatementContext) node;
            for (final FrostlakeParser.SetOperatorContext operator : statement.setOperator()) {
                if (operator.MINUS_KW() != null || operator.EXCEPT() != null) {
                    joinTypes.add("MINUS");
                } else if (operator.INTERSECT() != null) {
                    joinTypes.add("INTERSECT");
                }
            }
            if (statement.orderByClause() != null && orderBy == null) {
                orderBy = statement.orderByClause().getStart();
            }
            if (statement.limitClause() != null || statement.fetchClause() != null) {
                limit = true;
            }
        } else if (node instanceof FrostlakeParser.SelectClauseContext) {
            readBlock((FrostlakeParser.SelectClauseContext) node);
        } else if (node instanceof FrostlakeParser.TableExpressionContext) {
            readJoins((FrostlakeParser.TableExpressionContext) node);
        } else if (node instanceof FrostlakeParser.TableSourceContext) {
            readSource((FrostlakeParser.TableSourceContext) node);
        } else if (node instanceof FrostlakeParser.InSubqueryExprContext
                || node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            inSubquery = true;
        } else if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            final FrostlakeParser.QuantifiedComparisonExprContext quantified =
                (FrostlakeParser.QuantifiedComparisonExprContext) node;
            if (quantified.selectStatement() != null) {
                readSubquery(quantified.quantifier().getStart());
            }
        } else if (node instanceof FrostlakeParser.ExistsExprContext) {
            readSubquery(((ParserRuleContext) node).getStart());
        } else if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            readSubquery(((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement().getStart());
        } else if (node instanceof FrostlakeParser.QualifyClauseContext) {
            // A window function QUALIFY filters by is QUALIFY's: the clause is what is refused.
            qualifyDepth++;
        } else if (node instanceof FrostlakeParser.WindowedOperandExprContext) {
            window = window || qualifyDepth == 0;
        } else if (node instanceof FrostlakeParser.FunctionCallExprContext
                || node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                || node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
                || node instanceof FrostlakeParser.FunctionCallStarExprContext
                || node instanceof FrostlakeParser.CurrentTimestampExprContext
                || node instanceof FrostlakeParser.CurrentDateExprContext
                || node instanceof FrostlakeParser.CurrentTimeExprContext
                || node instanceof FrostlakeParser.CurrentUserExprContext) {
            if (((ParserRuleContext) node).getChildCount() > 0
                    && ((ParserRuleContext) node).getRuleContext(FrostlakeParser.OverClauseContext.class, 0) != null) {
                window = window || qualifyDepth == 0;
            }
            calls.add((ParserRuleContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i));
        }
        if (node instanceof FrostlakeParser.QualifyClauseContext) {
            qualifyDepth--;
        }
    }

    private void readBlock(final FrostlakeParser.SelectClauseContext block) {
        blocks.add(block);
        if (block.FROM() == null) {
            // A query block with no FROM reads a VALUES table of its own.
            relationsWithoutTable++;
        }
        if (block.topClause() != null) {
            limit = true;
        }
        if (block.havingClause() != null) {
            having = true;
        }
        if (block.qualifyClause() != null) {
            qualify = true;
        }
        final FrostlakeParser.GroupByClauseContext groupBy = block.groupByClause();
        if (groupBy != null) {
            if (groupBy.ALL() != null) {
                groupByAll = true;
            }
            for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
                // One grouping set is a plain grouping by its keys.
                final boolean oneSet = element.GROUPING() != null && element.groupingSetList().groupingSet().size() == 1;
                if (element.booleanExpr() == null && !oneSet && groupingSet == null) {
                    groupingSet = groupBy.getStart();
                }
            }
        }
    }

    /** Every comma and every join between two relations joins them; a table function read beside one does not. */
    private void readJoins(final FrostlakeParser.TableExpressionContext expression) {
        boolean afterComma = false;
        for (int i = 0; i < expression.getChildCount(); i++) {
            final ParseTree child = expression.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                if (afterComma && !isTableFunction((FrostlakeParser.TableReferenceContext) child)) {
                    joinTypes.add("INNER_JOIN");
                }
                afterComma = false;
            } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) child;
                if (!isTableFunction(join.tableReference())) {
                    joinTypes.add(joinType(join));
                }
            } else {
                afterComma = ",".equals(child.getText());
            }
        }
    }

    private static String joinType(final FrostlakeParser.JoinClauseContext join) {
        final FrostlakeParser.JoinTypeContext type = join.joinType();
        if (type == null || type.INNER() != null) {
            return join.ASOF() != null ? "ASOF_JOIN" : "INNER_JOIN";
        }
        if (type.LEFT() != null) {
            return "LEFT_OUTER_JOIN";
        }
        if (type.RIGHT() != null) {
            return "RIGHT_OUTER_JOIN";
        }
        return "FULL_OUTER_JOIN";
    }

    private static boolean isTableFunction(final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        return source != null && (source.FLATTEN() != null || source.tableFunctionExpr() != null
            || source.TABLE() != null);
    }

    private void readSource(final FrostlakeParser.TableSourceContext source) {
        if (source.tableQualifiedName() != null) {
            relations.add(source);
            final FrostlakeParser.TimeTravelClauseContext travel = source.timeTravelClause();
            if (travel != null && travel.changesClause() == null && timeTravel == null) {
                timeTravel = source.tableQualifiedName().getStart();
            }
            if (travel != null && travel.changesClause() != null) {
                unreadSource = true;
            }
        } else if (source.VALUES() != null) {
            relationsWithoutTable++;
        } else if (source.selectStatement() == null && source.tableReference() == null && source.FLATTEN() == null
                && source.tableFunctionExpr() == null && source.TABLE() == null) {
            // A stage, a directory, a name given by IDENTIFIER: its compilation answers for it.
            unreadSource = true;
        }
    }

    private void readSubquery(final Token at) {
        if (subquery == null) {
            subquery = at;
        }
    }

    private void refuseNotDeterministic() {
        for (final ParserRuleContext call : calls) {
            final String name = calledName(call);
            if (name == null) {
                continue;
            }
            final String reported = "SYSDATE".equals(name) || "GETDATE".equals(name) || "SYSTIMESTAMP".equals(name)
                ? "CURRENT_TIMESTAMP" : "APPROX_PERCENTILE".equals(name) ? "APPROX_PERCENTILE_ESTIMATE" : name;
            // UUID_STRING over a namespace and a name answers the same value each time; alone it does not.
            final boolean refused = "UUID_STRING".equals(reported) ? argumentCount(call) == 0
                : NOT_DETERMINISTIC.contains(reported);
            if (refused) {
                refuse(notDeterministicAt(call, name, DEFINITION + "non deterministic function '" + reported
                    + "' not allowed in view definition."));
            }
        }
    }

    /**
     * The place a non-deterministic call is refused at: the call's own, but SYSDATE is reported at line 1 position 35
     * and GETDATE and SYSTIMESTAMP at line 1 position 1 wherever they are written, as the CURRENT_TIMESTAMP each is
     * read as (live-verified).
     */
    private static String notDeterministicAt(final ParserRuleContext call, final String name, final String detail) {
        if ("SYSDATE".equals(name)) {
            return SqlCompilationError.PREFIX + " error line 1 at position 35\n" + detail;
        }
        if ("GETDATE".equals(name) || "SYSTIMESTAMP".equals(name)) {
            return SqlCompilationError.PREFIX + " error line 1 at position 1\n" + detail;
        }
        return at(call.getStart(), detail);
    }

    private void refuseDistinctAggregates() {
        for (final ParserRuleContext call : calls) {
            final String name = calledName(call);
            if (name == null || !isDistinct(call) || "APPROX_COUNT_DISTINCT".equals(name)
                    || !functions.hasAggregateFunction(name)) {
                continue;
            }
            refuse(at(call.getStart(), DEFINITION + "distinct aggregate '" + aggregateText(call, name)
                + "' not allowed in view definition."));
        }
    }

    private void refuseUnsupportedAggregates() {
        for (final ParserRuleContext call : calls) {
            final String name = calledName(call);
            if (name != null && UNSUPPORTED_AGGREGATES.contains(name)) {
                refuse(SqlCompilationError.inline("Invalid materialized view: Unsupported aggregate expression "
                    + aggregateText(call, name) + "."));
            }
        }
    }

    /** A GROUP BY key must be among the columns the view selects: by its expression, its alias or its ordinal. */
    private void refuseUnselectedKeys(final FrostlakeParser.SelectClauseContext block) {
        final FrostlakeParser.GroupByClauseContext groupBy = block.groupByClause();
        if (groupBy == null || groupBy.ALL() != null) {
            return;
        }
        final MaterializedViewExpressionText printer = new MaterializedViewExpressionText(owner);
        final Set<String> selected = new HashSet<>();
        final Set<String> aliases = new HashSet<>();
        boolean star = false;
        for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                star = true;
                continue;
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null) {
                aliases.add(alias.toUpperCase(Locale.ROOT));
            }
            final Expression expression = parsed(SelectItemAccessors.getItemExpression(item));
            if (expression != null) {
                selected.add(printer.spell(expression));
            }
        }
        final List<FrostlakeParser.BooleanExprContext> keys = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            if (element.booleanExpr() != null) {
                keys.add(element.booleanExpr());
            } else if (element.GROUPING() != null && element.groupingSetList().groupingSet().size() == 1) {
                keys.addAll(element.groupingSetList().groupingSet(0).booleanExpr());
            }
        }
        for (final FrostlakeParser.BooleanExprContext written : keys) {
            final Expression key = parsed(written);
            if (key == null || key instanceof LiteralExpression && ((LiteralExpression) key).getType() != LiteralType.STRING) {
                continue;
            }
            if (key instanceof ColumnReferenceExpression && (star || !((ColumnReferenceExpression) key).isQualified()
                    && aliases.contains(((ColumnReferenceExpression) key).getColumnName().toUpperCase(Locale.ROOT)))) {
                continue;
            }
            final String text = printer.spell(key);
            if (!selected.contains(text)) {
                refuse(SqlCompilationError.inline("Invalid materialized view definition: GROUP BY key '" + text
                    + "' is not in the view definition's select list."));
            }
        }
    }

    /** An aggregate call as the refusal names it: MEDIAN and PERCENTILE_CONT as the percentile they compute. */
    private String aggregateText(final ParserRuleContext call, final String name) {
        final MaterializedViewExpressionText printer = new MaterializedViewExpressionText(owner);
        final List<String> arguments = new ArrayList<>();
        final List<Expression> parsedArguments = new ArrayList<>();
        if (call instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) call).functionArgList() != null) {
            for (final FrostlakeParser.FunctionArgContext argument
                    : ((FrostlakeParser.FunctionCallExprContext) call).functionArgList().functionArg()) {
                final Expression expression = parsed(argument);
                parsedArguments.add(expression);
                arguments.add(expression == null ? ParseTreeText.getOriginalText(argument).toUpperCase(Locale.ROOT)
                    : printer.spell(expression));
            }
        }
        final Expression ordered = withinGroupKey(call);
        if ("MEDIAN".equals(name) && !parsedArguments.isEmpty()) {
            return "PERCENTILE_CONT(" + converted(parsedArguments.get(0), arguments.get(0)) + ", 0.5)";
        }
        if (("PERCENTILE_CONT".equals(name) || "PERCENTILE_DISC".equals(name)) && ordered != null
                && !arguments.isEmpty()) {
            final String key = printer.spell(ordered);
            return name + "(" + ("PERCENTILE_CONT".equals(name) ? converted(ordered, key) : key) + ", "
                + arguments.get(0) + ")";
        }
        final String called = "ARRAYAGG".equals(name) ? "ARRAY_AGG" : name;
        final StringBuilder text = new StringBuilder(called).append('(');
        if (isDistinct(call)) {
            text.append("DISTINCT ");
        }
        if (call instanceof FrostlakeParser.FunctionCallStarExprContext) {
            text.append('*');
        }
        text.append(String.join(", ", arguments));
        return text.append(')').toString();
    }

    /** A continuous percentile's value, converted to the number the percentile is computed over. */
    private String converted(final Expression value, final String text) {
        DataType type = null;
        if (value instanceof ColumnReferenceExpression && table != null) {
            for (final TableColumn column : table.getColumns()) {
                if (column.getName().equalsIgnoreCase(((ColumnReferenceExpression) value).getColumnName())) {
                    type = column.getDataType();
                }
            }
        }
        if (type != null && NumericType.isApproximate(type)) {
            return text;
        }
        int precision = 38;
        int scale = 0;
        if (type != null && NumericType.isExactNumber(type)) {
            precision = ((NumericType) type).getPrecision();
            scale = ((NumericType) type).getScale();
        }
        return "CAST(" + text + " AS NUMBER(" + Math.min(38, precision + 3) + "," + Math.min(37, scale + 3) + "))";
    }

    private Expression withinGroupKey(final ParserRuleContext call) {
        final FrostlakeParser.WithinGroupClauseContext within =
            call.getRuleContext(FrostlakeParser.WithinGroupClauseContext.class, 0);
        if (within == null || within.orderByClause().orderItem().isEmpty()) {
            return null;
        }
        return parsed(within.orderByClause().orderItem(0).booleanExpr());
    }

    /** The name a call is made by, upper-cased; null for a function named by a qualifier, a quote or IDENTIFIER. */
    private static String calledName(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.CurrentTimestampExprContext
                || call instanceof FrostlakeParser.CurrentDateExprContext
                || call instanceof FrostlakeParser.CurrentTimeExprContext
                || call instanceof FrostlakeParser.CurrentUserExprContext) {
            return call.getStart().getText().toUpperCase(Locale.ROOT);
        }
        final FrostlakeParser.FunctionNameContext name =
            call.getRuleContext(FrostlakeParser.FunctionNameContext.class, 0);
        if (name == null || name.identifier().size() != 1 || name.KW_IDENTIFIER_REF() != null) {
            return null;
        }
        final String written = name.getText();
        return written.startsWith("\"") ? null : written.toUpperCase(Locale.ROOT);
    }

    private static boolean isDistinct(final ParserRuleContext call) {
        return call.getToken(FrostlakeParser.DISTINCT, 0) != null;
    }

    private static int argumentCount(final ParserRuleContext call) {
        final FrostlakeParser.FunctionArgListContext list =
            call.getRuleContext(FrostlakeParser.FunctionArgListContext.class, 0);
        return list == null ? 0 : list.functionArg().size();
    }

    private boolean isCte(final String name) {
        for (final String cte : cteNames) {
            if (cte.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** A name's database, schema and object, the parts it leaves out taken from the session. */
    private String[] fullName(final String[] written) {
        final String[] parts = catalog.withoutAccount(written, 3);
        return parts.length == 3 ? parts
            : parts.length == 2 ? new String[]{catalog.getCurrentDatabase(), parts[0], parts[1]}
            : new String[]{catalog.getCurrentDatabase(), catalog.getCurrentSchema(), parts[0]};
    }

    private Schema schemaOf(final String[] full) {
        if (full[0] == null || full[1] == null) {
            return null;
        }
        try {
            final Database database = catalog.databaseExact(full[0]);
            return database.schemaExact(full[1]);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    private static Table tableOf(final Schema schema, final String name) {
        try {
            return schema.tableExact(name);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /** The name a relation's columns are spelled under: its alias, else its own name. */
    private static String ownerOf(final FrostlakeParser.TableSourceContext relation, final String name) {
        if (relation.getParent() instanceof FrostlakeParser.TableReferenceContext) {
            final FrostlakeParser.TableReferenceContext reference =
                (FrostlakeParser.TableReferenceContext) relation.getParent();
            final ParserRuleContext alias = reference.aliasName() != null ? reference.aliasName()
                : reference.nonJoinKeywordIdentifier();
            if (alias != null) {
                return SqlIdentifiers.spellCanonical(SqlIdentifiers.canonicalText(alias.getText()));
            }
        }
        return SqlIdentifiers.spellCanonical(name);
    }

    private static Expression parsed(final ParserRuleContext written) {
        if (written == null) {
            return null;
        }
        try {
            return ExpressionEvaluator.parse(ParseTreeText.getOriginalText(written));
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    private static String at(final Token token, final String detail) {
        return SqlCompilationError.at(token.getLine(), token.getCharPositionInLine(), detail);
    }

    private static void refuse(final String message) {
        throw new RuntimeException(message);
    }
}
