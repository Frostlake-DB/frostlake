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

import dev.frostlake.functions.table.Getnextval;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * The sequence a {@code TABLE(GETNEXTVAL(<name>))} call draws from, judged while the statement is planned
 * (live-verified):
 *
 * <pre>
 *   GETNEXTVAL(seq)  GETNEXTVAL(db.sch.seq)  GETNEXTVAL("SEQ")  GETNEXTVAL(IDENTIFIER('seq'))   the sequence
 *   GETNEXTVAL(any_name =&gt; seq)                                                          the same, by any name
 *   GETNEXTVAL(nosuch), a table's or a view's name           invalid identifier 'NOSUCH', at the name
 *   GETNEXTVAL(IDENTIFIER('nosuch'))                         invalid identifier 'NOSUCH', at the text
 *   GETNEXTVAL(), GETNEXTVAL(a, b)                           invalid number of arguments for [GETNEXTVAL],
 *                                                            expected 1, got N, at the function's name
 * </pre>
 *
 * The refusal names the sequence as written, folded: an unquoted name upper-cased, a quoted one in its quotes, a
 * qualified one with its parts.
 */
final class GetnextvalSequence {

    private GetnextvalSequence() {
    }

    /**
     * Refuses a call that does not write exactly one argument.
     *
     * @param nameToken the call's name, where the refusal stands
     * @param given     the number of arguments written
     */
    static void requireOneArgument(final Token nameToken, final int given) {
        if (given != 1) {
            throw new RuntimeException(SqlCompilationError.at(nameToken.getLine(), nameToken.getCharPositionInLine(),
                "invalid number of arguments for [" + Getnextval.NAME + "], expected 1, got " + given));
        }
    }

    /**
     * The sequence the call's argument names, or a refusal at the argument.
     *
     * @param executor the executor, whose session's search path an unqualified name is looked for along
     * @param written  the call's one argument: an expression, or a positional argument as the call lists it
     * @return the sequence
     */
    static Sequence resolve(final QueryExecutor executor, final ParserRuleContext written) {
        ParserRuleContext argument = written;
        if (written instanceof FrostlakeParser.FunctionArgContext) {
            final FrostlakeParser.BooleanExprContext value = ((FrostlakeParser.FunctionArgContext) written).booleanExpr();
            if (value instanceof FrostlakeParser.ValueExprContext) {
                argument = ((FrostlakeParser.ValueExprContext) value).expression();
            }
        }
        Token at = argument.getStart();
        String[] parts = null;
        if (argument instanceof FrostlakeParser.QualifiedNameExprContext) {
            parts = ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) argument)
                .qualifiedName());
        } else if (argument instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) argument;
            if ("IDENTIFIER".equalsIgnoreCase(call.functionName().getText()) && call.functionArgList() != null
                    && call.functionArgList().functionArg().size() == 1) {
                final FrostlakeParser.FunctionArgContext text = call.functionArgList().functionArg(0);
                at = text.getStart();
                final Object value = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
                    executor.getCatalog(), executor).evaluate(ParseTreeText.getOriginalText(text), null);
                if (value != null && SqlIdentifiers.isIdentifierReference(value.toString())) {
                    parts = SqlIdentifiers.identifierReferenceParts(value.toString().trim());
                }
            }
        }
        if (parts == null) {
            throw invalidIdentifier(at, ParseTreeText.getOriginalText(argument).toUpperCase(Locale.ROOT));
        }
        final Sequence sequence = find(executor, parts);
        if (sequence == null) {
            throw invalidIdentifier(at, spelled(parts));
        }
        return sequence;
    }

    private static Sequence find(final QueryExecutor executor, final String[] parts) {
        final Catalog catalog = executor.getCatalog();
        try {
            if (parts.length == 1) {
                // An unqualified sequence is looked for along the session's SEARCH_PATH.
                final List<Schema> path = executor.searchPathSchemas();
                for (final Schema schema : path) {
                    if (schema.hasSequenceExact(parts[0])) {
                        return schema.getSequenceExact(parts[0]);
                    }
                }
                return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema())
                    .getSequenceExact(parts[0]);
            }
            if (parts.length == 2) {
                return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                    .getSequenceExact(parts[1]);
            }
            if (parts.length == 3) {
                return catalog.getDatabase(parts[0]).getSchema(parts[1]).getSequenceExact(parts[2]);
            }
            return null;
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    private static String spelled(final String[] parts) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                text.append('.');
            }
            text.append(SqlIdentifiers.spellCanonical(parts[i]));
        }
        return text.toString();
    }

    private static RuntimeException invalidIdentifier(final Token at, final String name) {
        return new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            "invalid identifier '" + name + "'"));
    }
}
