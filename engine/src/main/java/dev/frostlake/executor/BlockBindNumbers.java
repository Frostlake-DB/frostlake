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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The numbers a block's compilation draws for the binds of its variables (see UserCallBinds), walking the block in
 * its order from a start. Every value the block computes draws what it computes plus one: one for a name it reads or
 * a sign, one and one per argument for a call of a built-in function and three for a call of a user's, two for a cast, three for an operator between two
 * values, what its query compiles to less two for a subquery, and nothing for a literal. A declaration draws its value, then its variable's number, then two more (a LET
 * three); a declaration without a value draws its number, then two more. An assignment draws its value and three, four where it assigns a subquery alone; a
 * RETURN its value and two; a NULL, a BREAK, a CONTINUE and a bare RAISE one; a RAISE of an exception three; an inner
 * block one. An IF and each ELSEIF draw three and the condition, an ELSE one, a CASE one, its operand, one per WHEN and
 * its condition, a WHILE and a REPEAT three and the condition, a LOOP four, a FOR over a cursor seven, each before or
 * around its statements. A range FOR draws its first bound and one for its second, then its statements, then what its
 * second bound computes, then its counter's number three past that, then two more. An exception declaration draws two,
 * three with its code; a handler three, and two per exception it names past the first, one for the first, before its
 * statements (all live-verified).
 *
 * <p>A SQL statement draws what its query compiles to (live-verified): a SELECT seven, then for each item what it
 * computes and two, two more without a FROM, three and what it computes for a WHERE, a HAVING and a QUALIFY, three and
 * each key's computation, two per key past the first, for a GROUP BY and an ORDER BY, three for a LIMIT, seven per set
 * operator; a SELECT ... INTO ten more and four per target. An INSERT draws eleven, one per column it lists, and its
 * query, or three and for each value one and what it computes; an UPDATE ten, five and what it computes for each
 * assignment, and its WHERE; a DELETE ten and its WHERE. A variable a statement binds draws nothing there. A cursor
 * declared draws its query and two (a LET three), one over a RESULTSET three; a RESULTSET declared the same, two without a query; an OPEN two, and one and what it computes
 * per value it binds; a FETCH two and two per target; a CLOSE two; an EXECUTE IMMEDIATE four, and two per name it
 * binds, one per TRUE or FALSE; a CALL twelve, and one and what each argument computes beyond the variables it binds.
 * Statements of other kinds draw nothing here.
 *
 * <p>A procedure's parameters take their numbers after its body's walk: two past where it ends, each next one four
 * past the one before (live-verified).
 */
final class BlockBindNumbers {

    private static final int PER_VALUE = 1;
    private static final int PER_DECLARED = 2;
    private static final int PER_LET = 3;
    private static final int PER_ASSIGNMENT = 3;
    private static final int PER_RETURN = 2;
    private static final int PER_STATEMENT = 1;
    private static final int PER_INNER_BLOCK = 1;
    private static final int PER_BRANCH = 3;
    private static final int PER_ELSE = 1;
    private static final int PER_CASE = 1;
    private static final int PER_WHEN = 1;
    private static final int PER_CONDITIONAL_LOOP = 3;
    private static final int PER_LOOP = 4;
    private static final int COUNTER_AFTER_BODY = 3;
    private static final int PER_COUNTER = 2;
    private static final int PER_NAME = 1;
    private static final int PER_SIGN = 1;
    private static final int PER_CAST = 2;
    private static final int PER_USER_CALL = 3;
    private static final int PER_OPERATOR = 3;
    private static final int PER_RAISE_OF_AN_EXCEPTION = 3;
    private static final int PER_CURSOR_LOOP = 7;
    private static final int PER_EXCEPTION = 2;
    private static final int PER_EXCEPTION_CODE = 1;
    private static final int PER_HANDLER = 3;
    private static final int PER_NAMED_CONDITION = 2;
    private static final int PER_QUERY = 7;
    private static final int PER_ITEM = 2;
    private static final int PER_CLAUSE = 3;
    private static final int PER_KEY = 2;
    private static final int WITHOUT_FROM = 2;
    private static final int PER_SET_OPERATOR = 7;
    private static final int PER_INTO = 10;
    private static final int PER_INTO_TARGET = 4;
    private static final int PER_INSERT = 11;
    private static final int PER_VALUES = 3;
    private static final int PER_UPDATE = 10;
    private static final int PER_SET = 5;
    private static final int PER_DELETE = 10;
    private static final int PER_DECLARED_CURSOR = 2;
    private static final int PER_LET_CURSOR = 3;
    private static final int PER_CURSOR_OVER_A_RESULTSET = 1;
    private static final int PER_OPEN = 2;
    private static final int PER_FETCH = 2;
    private static final int PER_FETCH_TARGET = 2;
    private static final int PER_CLOSE = 2;
    private static final int PER_EXECUTE_IMMEDIATE = 4;
    private static final int PER_BOUND_NAME = 2;
    private static final int PER_CALL_STATEMENT = 12;
    private static final int PER_PARAMETER = 2;
    private static final int SUBQUERY_BELOW_ITS_QUERY = 2;
    private static final int PER_ASSIGNED_QUERY = 1;

    private final FunctionRegistry functions;
    private final ParseTree outermost;
    private final Map<String, Integer> numbers = new HashMap<>();
    private int counter;

    private BlockBindNumbers(final FunctionRegistry functions, final ParseTree outermost, final int start) {
        this.functions = functions;
        this.outermost = outermost;
        this.counter = start;
    }

    /**
     * The numbers of the block's variables, by upper-cased name: the first declaration's.
     *
     * @param block      the block furthest out
     * @param start      the number the walk starts from
     * @param parameters the names of the parameters of the procedure whose body the block is, in their order
     * @param functions  the built-in functions, which a user's function is none of
     * @return the numbers
     */
    static Map<String, Integer> of(final FrostlakeParser.BeginEndBlockContext block, final int start,
                                   final List<String> parameters, final FunctionRegistry functions) {
        final BlockBindNumbers walk = new BlockBindNumbers(functions, block, start);
        walk.declare(block);
        for (final String parameter : parameters) {
            walk.counter += PER_PARAMETER;
            final String name = parameter.toUpperCase(Locale.ROOT);
            if (!walk.numbers.containsKey(name)) {
                walk.numbers.put(name, Integer.valueOf(walk.counter));
            }
            walk.counter += PER_PARAMETER;
        }
        return walk.numbers;
    }

    private void declare(final ParseTree node) {
        if (node instanceof FrostlakeParser.DeclarationItemContext) {
            final FrostlakeParser.DeclarationItemContext item = (FrostlakeParser.DeclarationItemContext) node;
            if (item.dataTypeName() != null) {
                variable(item.identifier().getText(), item.booleanExpr(), PER_DECLARED);
            } else if (item.EXCEPTION() != null) {
                counter += PER_EXCEPTION + (item.INTEGER_LITERAL() != null ? PER_EXCEPTION_CODE : 0);
            } else if (item.CURSOR() != null) {
                counter += cursor(item.cursorSource()) + PER_DECLARED_CURSOR;
            } else if (item.RESULTSET() != null) {
                counter += (item.resultSetSource() != null ? source(item.resultSetSource()) : 0) + PER_DECLARED_CURSOR;
            }
            return;
        }
        if (node instanceof FrostlakeParser.UntypedDeclarationItemContext) {
            final FrostlakeParser.UntypedDeclarationItemContext item =
                (FrostlakeParser.UntypedDeclarationItemContext) node;
            if (item.booleanExpr() != null) {
                variable(item.identifier().getText(), item.booleanExpr(), PER_DECLARED);
            }
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            if (let.CURSOR() != null) {
                counter += cursor(let.cursorSource()) + PER_LET_CURSOR;
            } else if (let.RESULTSET() != null) {
                counter += (let.resultSetSource() != null ? source(let.resultSetSource()) : 0) + PER_LET_CURSOR;
            } else {
                variable(let.variableName().getText(), let.booleanExpr(), PER_LET);
            }
            return;
        }
        if (node instanceof FrostlakeParser.AssignmentStatementContext) {
            final FrostlakeParser.AssignmentStatementContext assignment =
                (FrostlakeParser.AssignmentStatementContext) node;
            if (assignment.booleanExpr() != null) {
                counter += computed(assignment.booleanExpr()) + PER_ASSIGNMENT
                    + (subqueryAlone(assignment.booleanExpr()) ? PER_ASSIGNED_QUERY : 0);
            }
            return;
        }
        if (node instanceof FrostlakeParser.ReturnStatementContext) {
            final FrostlakeParser.ReturnStatementContext ret = (FrostlakeParser.ReturnStatementContext) node;
            if (ret.TABLE() == null && ret.booleanExpr() != null) {
                counter += computed(ret.booleanExpr()) + PER_RETURN;
            }
            return;
        }
        if (node instanceof FrostlakeParser.NullStatementContext
                || node instanceof FrostlakeParser.BreakStatementContext
                || node instanceof FrostlakeParser.ContinueStatementContext) {
            counter += PER_STATEMENT;
            return;
        }
        if (node instanceof FrostlakeParser.RaiseStatementContext) {
            counter += ((FrostlakeParser.RaiseStatementContext) node).variableName() != null
                ? PER_RAISE_OF_AN_EXCEPTION : PER_STATEMENT;
            return;
        }
        if (node instanceof FrostlakeParser.ExceptionHandlerContext) {
            handler((FrostlakeParser.ExceptionHandlerContext) node);
            return;
        }
        if (statement(node)) {
            return;
        }
        if (node instanceof FrostlakeParser.IfStatementContext) {
            branches((FrostlakeParser.IfStatementContext) node);
            return;
        }
        if (node instanceof FrostlakeParser.CaseStatementContext) {
            cases((FrostlakeParser.CaseStatementContext) node);
            return;
        }
        if (node instanceof FrostlakeParser.WhileStatementContext) {
            final FrostlakeParser.WhileStatementContext loop = (FrostlakeParser.WhileStatementContext) node;
            counter += PER_CONDITIONAL_LOOP + value(loop.booleanExpr());
            declare(loop.statementList());
            return;
        }
        if (node instanceof FrostlakeParser.RepeatStatementContext) {
            final FrostlakeParser.RepeatStatementContext loop = (FrostlakeParser.RepeatStatementContext) node;
            counter += PER_CONDITIONAL_LOOP;
            declare(loop.statementList());
            counter += value(loop.booleanExpr());
            return;
        }
        if (node instanceof FrostlakeParser.LoopStatementContext) {
            counter += PER_LOOP;
            declare(((FrostlakeParser.LoopStatementContext) node).statementList());
            return;
        }
        if (node instanceof FrostlakeParser.ForStatementContext) {
            range((FrostlakeParser.ForStatementContext) node);
            return;
        }
        if (node instanceof FrostlakeParser.BeginEndBlockContext && node != outermost) {
            counter += PER_INNER_BLOCK;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            declare(node.getChild(i));
        }
    }

    /** An IF: three and the condition for it and for each ELSEIF, one for an ELSE, each before its statements. */
    private void branches(final FrostlakeParser.IfStatementContext branch) {
        for (int i = 0; i < branch.statementList().size(); i++) {
            if (i < branch.booleanExpr().size()) {
                counter += PER_BRANCH + value(branch.booleanExpr(i));
            } else {
                counter += PER_ELSE;
            }
            declare(branch.statementList(i));
        }
    }

    /** A CASE: one, its operand, then one and the condition for each WHEN, one for an ELSE, before its statements. */
    private void cases(final FrostlakeParser.CaseStatementContext choice) {
        counter += PER_CASE;
        final int whens = choice.WHEN().size();
        final int operands = choice.booleanExpr().size() - whens;
        for (int i = 0; i < operands; i++) {
            counter += value(choice.booleanExpr(i));
        }
        for (int i = 0; i < choice.statementList().size(); i++) {
            if (i < whens) {
                counter += PER_WHEN + value(choice.booleanExpr(operands + i));
            } else {
                counter += PER_ELSE;
            }
            declare(choice.statementList(i));
        }
    }

    /**
     * A range FOR: its first bound and one for its second, its statements, what its second bound computes, then its
     * counter; a FOR over a cursor seven and its statements.
     */
    private void range(final FrostlakeParser.ForStatementContext loop) {
        if (loop.TO() == null) {
            counter += PER_CURSOR_LOOP;
            declare(loop.statementList());
            return;
        }
        counter += value(loop.expression(0)) + PER_VALUE;
        declare(loop.statementList());
        counter += computed(loop.expression(1)) + COUNTER_AFTER_BODY;
        final String name = SqlIdentifiers.canonicalText(loop.variableName().getText()).toUpperCase(Locale.ROOT);
        if (!numbers.containsKey(name)) {
            numbers.put(name, Integer.valueOf(counter));
        }
        counter += PER_COUNTER;
    }

    private void variable(final String written, final ParseTree initial, final int step) {
        if (initial != null) {
            counter += value(initial);
        }
        final String name = SqlIdentifiers.canonicalText(written).toUpperCase(Locale.ROOT);
        if (!numbers.containsKey(name)) {
            numbers.put(name, Integer.valueOf(counter));
        }
        counter += initial != null ? step : PER_DECLARED;
    }

    /** What a value computes beyond its literals: names and signs, calls and casts, operators. */
    private int computed(final ParseTree node) {
        return weighed(node, PER_NAME, PER_NAME);
    }

    /** What a value a SQL statement holds computes: a variable it binds draws nothing there. */
    private int sql(final ParseTree node) {
        return weighed(node, PER_NAME, 0);
    }

    /** What a value computes, each name it reads drawing {@code perName} and each variable it binds {@code perBind}. */
    private int weighed(final ParseTree node, final int perName, final int perBind) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return query(((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement())
                - SUBQUERY_BELOW_ITS_QUERY;
        }
        int cost = 0;
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            cost += perName;
        } else if (node instanceof FrostlakeParser.BindVarExprContext) {
            cost += perBind;
        } else if (node instanceof FrostlakeParser.UnaryExprContext
                || node instanceof FrostlakeParser.NotExprContext) {
            cost += PER_SIGN;
        } else if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            cost += userCall(call.functionName()) ? PER_USER_CALL
                : PER_VALUE + (call.functionArgList() == null ? 0 : call.functionArgList().functionArg().size());
        } else if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            cost += PER_VALUE + ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).namedArgumentList()
                .namedArgument().size();
        } else if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext call =
                (FrostlakeParser.FunctionCallMixedArgsExprContext) node;
            cost += PER_VALUE + call.expression().size() + call.namedArgument().size()
                + (call.mixedStarArgument() != null ? 1 : 0);
        } else if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            cost += PER_VALUE;
        } else if (node instanceof FrostlakeParser.CastExprContext
                || node instanceof FrostlakeParser.CastExpr2Context) {
            cost += PER_CAST;
        } else if (node instanceof FrostlakeParser.AdditiveExprContext
                || node instanceof FrostlakeParser.MultiplicativeExprContext
                || node instanceof FrostlakeParser.ComparisonExprContext
                || node instanceof FrostlakeParser.AndExprContext
                || node instanceof FrostlakeParser.OrExprContext) {
            cost += PER_OPERATOR;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            cost += weighed(node.getChild(i), perName, perBind);
        }
        return cost;
    }

    /** Whether a value is a subquery and nothing more. */
    private static boolean subqueryAlone(final ParseTree value) {
        ParseTree at = value;
        while (at.getChildCount() == 1 && !(at instanceof FrostlakeParser.ScalarSubqueryExprContext)) {
            at = at.getChild(0);
        }
        return at instanceof FrostlakeParser.ScalarSubqueryExprContext;
    }

    /** Whether a call is of a user's function: a plain name no built-in function holds. */
    private boolean userCall(final FrostlakeParser.FunctionNameContext name) {
        final String[] parts = ParseTreeText.functionNameParts(name);
        if (parts == null || parts.length != 1 || functions == null) {
            return false;
        }
        final String called = parts[0].toUpperCase(Locale.ROOT);
        return !functions.hasFunction(called) && !functions.hasAggregateFunction(called);
    }

    /** What a value draws: what it computes, and one for the value. */
    private int value(final ParseTree node) {
        return computed(node) + PER_VALUE;
    }

    /** A SQL statement the walk counts: true where it drew what the statement compiles to. */
    private boolean statement(final ParseTree node) {
        if (node instanceof FrostlakeParser.QueryStatementContext) {
            counter += query(((FrostlakeParser.QueryStatementContext) node).selectStatement());
        } else if (node instanceof FrostlakeParser.SelectIntoStatementContext) {
            counter += selectInto((FrostlakeParser.SelectIntoStatementContext) node);
        } else if (node instanceof FrostlakeParser.InsertStatementContext) {
            counter += insert((FrostlakeParser.InsertStatementContext) node);
        } else if (node instanceof FrostlakeParser.UpdateStatementContext) {
            final FrostlakeParser.UpdateStatementContext update = (FrostlakeParser.UpdateStatementContext) node;
            int cost = PER_UPDATE + where(update.whereClause());
            for (final FrostlakeParser.AssignmentContext set : update.assignmentList().assignment()) {
                cost += PER_SET + sql(set.expression());
            }
            counter += cost;
        } else if (node instanceof FrostlakeParser.DeleteStatementContext) {
            counter += PER_DELETE + where(((FrostlakeParser.DeleteStatementContext) node).whereClause());
        } else if (node instanceof FrostlakeParser.OpenStatementContext) {
            final FrostlakeParser.OpenStatementContext open = (FrostlakeParser.OpenStatementContext) node;
            counter += PER_OPEN;
            if (open.expressionList() != null) {
                for (final FrostlakeParser.ExpressionContext bound : open.expressionList().expression()) {
                    counter += sql(bound) + PER_VALUE;
                }
            }
        } else if (node instanceof FrostlakeParser.FetchStatementContext) {
            counter += PER_FETCH + PER_FETCH_TARGET
                * ((FrostlakeParser.FetchStatementContext) node).identifierList().identifier().size();
        } else if (node instanceof FrostlakeParser.CloseStatementContext) {
            counter += PER_CLOSE;
        } else if (node instanceof FrostlakeParser.ExecuteImmediateStatementContext) {
            counter += PER_EXECUTE_IMMEDIATE;
            for (final FrostlakeParser.UsingArgumentContext bound
                    : ((FrostlakeParser.ExecuteImmediateStatementContext) node).usingArgument()) {
                counter += bound.identifier() != null ? PER_BOUND_NAME : PER_VALUE;
            }
        } else if (node instanceof FrostlakeParser.CallStatementContext) {
            final FrostlakeParser.CallStatementContext call = (FrostlakeParser.CallStatementContext) node;
            counter += PER_CALL_STATEMENT;
            if (call.callArguments() != null) {
                for (final FrostlakeParser.CallArgumentContext argument : call.callArguments().callArgument()) {
                    counter += PER_VALUE + weighed(argument, 0, 0);
                }
            }
        } else {
            return false;
        }
        return true;
    }

    /** A handler: three, two per exception it names past the first and one for the first, then its statements. */
    private void handler(final FrostlakeParser.ExceptionHandlerContext handler) {
        int named = 0;
        for (final FrostlakeParser.ExceptionConditionContext condition : handler.exceptionCondition()) {
            if (condition.OTHER() == null) {
                named++;
            }
        }
        counter += PER_HANDLER + (named > 0 ? PER_NAMED_CONDITION * named - 1 : 0);
        declare(handler.statementList());
    }

    /** What a cursor's source compiles to: its query, or three more where it reads a RESULTSET. */
    private int cursor(final FrostlakeParser.CursorSourceContext source) {
        return source.selectStatement() != null ? query(source.selectStatement()) : PER_CURSOR_OVER_A_RESULTSET;
    }

    /** What a RESULTSET's source compiles to: its query; a source of another kind draws nothing here. */
    private int source(final FrostlakeParser.ResultSetSourceContext source) {
        return source.selectStatement() != null ? query(source.selectStatement()) : 0;
    }

    /** What a query compiles to: its operands, seven per set operator, its ORDER BY and its LIMIT. */
    private int query(final FrostlakeParser.SelectStatementContext query) {
        int cost = PER_SET_OPERATOR * query.setOperator().size() + order(query.orderByClause())
            + (query.limitClause() != null || query.fetchClause() != null ? PER_CLAUSE : 0);
        for (final FrostlakeParser.SelectOperandContext operand : query.selectOperand()) {
            if (operand.selectClause() != null) {
                final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
                cost += select(clause.selectList(), clause.FROM() != null, clause.whereClause(),
                    clause.groupByClause(), clause.havingClause(), clause.qualifyClause());
            } else if (operand.selectStatement() != null) {
                cost += query(operand.selectStatement());
            }
        }
        return cost;
    }

    /** A SELECT ... INTO: the query it holds, ten more and four per target. */
    private int selectInto(final FrostlakeParser.SelectIntoStatementContext into) {
        return select(into.selectList(), into.FROM() != null, into.whereClause(), into.groupByClause(),
            into.havingClause(), into.qualifyClause()) + order(into.orderByClause())
            + (into.limitClause() != null || into.fetchClause() != null ? PER_CLAUSE : 0)
            + PER_INTO + PER_INTO_TARGET * into.intoTargetList().intoTarget().size();
    }

    /** A SELECT's own clauses: seven, its items, two more without a FROM, its WHERE, GROUP BY, HAVING, QUALIFY. */
    private int select(final FrostlakeParser.SelectListContext items, final boolean from,
                              final FrostlakeParser.WhereClauseContext where,
                              final FrostlakeParser.GroupByClauseContext groups,
                              final FrostlakeParser.HavingClauseContext having,
                              final FrostlakeParser.QualifyClauseContext qualify) {
        int cost = PER_QUERY + (from ? 0 : WITHOUT_FROM) + where(where);
        for (final FrostlakeParser.SelectItemContext item : items.selectItem()) {
            cost += sql(item) + PER_ITEM;
        }
        if (groups != null) {
            cost += PER_CLAUSE + keys(groups.groupByElement());
        }
        if (having != null) {
            cost += PER_CLAUSE + sql(having);
        }
        if (qualify != null) {
            cost += PER_CLAUSE + sql(qualify);
        }
        return cost;
    }

    /** A WHERE: three and what its condition computes; none draws nothing. */
    private int where(final FrostlakeParser.WhereClauseContext where) {
        return where == null ? 0 : PER_CLAUSE + sql(where.booleanExpr());
    }

    /** An ORDER BY: three, each key's computation, two per key past the first; none draws nothing. */
    private int order(final FrostlakeParser.OrderByClauseContext order) {
        return order == null ? 0 : PER_CLAUSE + keys(order.orderItem());
    }

    /** Keys of a GROUP BY or an ORDER BY: what each computes, two per key past the first. */
    private int keys(final List<? extends ParseTree> keys) {
        int cost = keys.isEmpty() ? 0 : -PER_KEY;
        for (final ParseTree key : keys) {
            cost += sql(key) + PER_KEY;
        }
        return cost;
    }

    /** An INSERT: eleven, one per column it lists, and its query, or three and each value's draw. */
    private int insert(final FrostlakeParser.InsertStatementContext insert) {
        int cost = PER_INSERT + (insert.insertColumnList() == null ? 0
            : insert.insertColumnList().insertColumnItem().size());
        if (insert.selectStatement() != null) {
            return cost + query(insert.selectStatement());
        }
        cost += PER_VALUES;
        for (final FrostlakeParser.ValueTupleContext row : insert.valueTupleList().valueTuple()) {
            for (final FrostlakeParser.BooleanExprContext value : row.valueList().booleanExpr()) {
                cost += sql(value) + PER_VALUE;
            }
        }
        return cost;
    }
}
