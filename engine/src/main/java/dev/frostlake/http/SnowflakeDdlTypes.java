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

package dev.frostlake.http;

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The statement type id the account answers a DDL statement with (live-verified, kind by kind and action by action).
 * Each family has an id of its own, which a kind or an action with none of its own answers — CREATE 0x6100, ALTER
 * 0x6200, DROP 0x6300, a rename 0x6400, UNDROP 0x6800, TRUNCATE 0x6210 — and these answer theirs:
 *
 * <pre>
 *   CREATE TABLE (temporary, OR ALTER, CLONE, LIKE) 0x6101   SEQUENCE 0x6102   STREAM 0x6107   TASK 0x6108
 *   CREATE TABLE … AS SELECT and MATERIALIZED VIEW 0x6111   MASKING POLICY 0x6112   VIEW 0x6114
 *   CREATE ROW ACCESS POLICY 0x6115   TAG 0x6116   PROJECTION POLICY 0x611d   CONTACT 0x6126   EVENT TABLE 0x6923
 *   ALTER TABLE CLUSTER BY 0x6201   ADD COLUMN 0x6202   DROP COLUMN 0x6203   ALTER / MODIFY COLUMN 0x6204
 *   ALTER TABLE DROP CLUSTERING KEY 0x6208   ADD a constraint 0x6103   COMMENT ON COLUMN 0x6204
 *   ALTER VIEW SET / UNSET SECURE 0x6209   SET TAG 0x6281   UNSET TAG 0x6282   (any kind)
 *   ALTER TAG 0x6280, its SET ALLOWED_VALUES 0x62be   ALTER CONTACT 0x62a5
 *   RENAME a TABLE 0x6401, a table's COLUMN 0x6402, a FILE FORMAT 0x6403, a DATABASE 0x6404, a SCHEMA 0x6405,
 *          a FUNCTION 0x6406, a VIEW 0x6408, a STAGE 0x6409, a SEQUENCE 0x640a
 *   DROP STREAM 0x6307   TASK 0x6308   MASKING POLICY 0x6309   ROW ACCESS POLICY 0x630a   TAG 0x630b
 *   DROP PROJECTION POLICY 0x631c   CONTACT 0x6322
 * </pre>
 *
 * <p>Snowflake's JDBC driver reads every id from 0x6000 to 0x6FFF as a DDL statement's, so none of them changes how a
 * driver reads the answer. A statement the account was not seen to answer — a GRANT, a REVOKE — keeps the generic
 * 0x6000.
 */
final class SnowflakeDdlTypes {

    private static final long DDL = 0x6000;
    private static final long CREATE = 0x6100;
    private static final long ALTER = 0x6200;
    private static final long DROP = 0x6300;
    private static final long RENAME = 0x6400;
    private static final long UNDROP = 0x6800;
    private static final long TRUNCATE = 0x6210;
    private static final long MODIFY_COLUMN = 0x6204;

    private SnowflakeDdlTypes() {
    }

    /**
     * The id of a DDL statement other than USE.
     *
     * @param ddl the statement
     * @return its id
     */
    static long of(final FrostlakeParser.DdlStatementContext ddl) {
        if (ddl.createStatement() != null) {
            return create(ddl.createStatement());
        }
        if (ddl.alterStatement() != null) {
            return alter(ddl.alterStatement());
        }
        if (ddl.dropStatement() != null) {
            return drop(ddl.dropStatement());
        }
        if (ddl.undropStatement() != null) {
            return UNDROP;
        }
        if (ddl.truncateStatement() != null) {
            return TRUNCATE;
        }
        if (ddl.commentStatement() != null) {
            return ddl.commentStatement().COLUMN() != null ? MODIFY_COLUMN : ALTER;
        }
        final int first = ddl.getStart().getType();
        return first == FrostlakeParser.CREATE ? CREATE : first == FrostlakeParser.ALTER ? ALTER
            : first == FrostlakeParser.DROP ? DROP : DDL;
    }

    /**
     * Whether a CREATE makes a table from a query's rows — a CREATE TABLE … AS SELECT, temporary, transient, replacing
     * or with a column list — where a CLONE, a LIKE, a template or a dynamic table makes none (live-verified for the
     * plain, temporary and transient kinds).
     *
     * @param create the statement
     * @return whether it is a CTAS
     */
    static boolean createsTableFromQuery(final FrostlakeParser.CreateStatementContext create) {
        return create.TABLE() != null && create.AS() != null && create.selectStatement() != null
            && create.DYNAMIC() == null && create.EVENT() == null && create.ICEBERG() == null
            && create.HYBRID() == null;
    }

    private static long create(final FrostlakeParser.CreateStatementContext create) {
        if (create.EVENT() != null) {
            return 0x6923;
        }
        if (create.TABLE() != null && create.DYNAMIC() == null) {
            return createsTableFromQuery(create) ? 0x6111 : 0x6101;
        }
        if (create.VIEW() != null) {
            return create.MATERIALIZED() != null ? 0x6111 : 0x6114;
        }
        if (create.SEQUENCE() != null) {
            return 0x6102;
        }
        if (create.STREAM() != null) {
            return 0x6107;
        }
        if (create.TASK() != null) {
            return 0x6108;
        }
        if (create.TAG() != null) {
            return 0x6116;
        }
        if (create.CONTACT() != null) {
            return 0x6126;
        }
        if (create.POLICY() != null && create.MASKING() != null) {
            return 0x6112;
        }
        if (create.POLICY() != null && create.ACCESS() != null) {
            return 0x6115;
        }
        if (create.POLICY() != null && create.PROJECTION() != null) {
            return 0x611d;
        }
        return CREATE;
    }

    private static long alter(final FrostlakeParser.AlterStatementContext alter) {
        final int kind = wordAt(alter, 1);
        final ParserRuleContext action = actionOf(alter);
        if (action == null) {
            return ALTER;
        }
        if (action.getStart().getType() == FrostlakeParser.RENAME && wordAt(action, 1) == FrostlakeParser.TO) {
            return renamed(kind);
        }
        if (holds(action, FrostlakeParser.TagSetContext.class)) {
            return 0x6281;
        }
        if (holds(action, FrostlakeParser.TagUnsetContext.class)) {
            return 0x6282;
        }
        if (kind == FrostlakeParser.TABLE && action instanceof FrostlakeParser.TableActionContext) {
            return tableAction((FrostlakeParser.TableActionContext) action);
        }
        if (kind == FrostlakeParser.VIEW && action.getToken(FrostlakeParser.SECURE, 0) != null) {
            return 0x6209;
        }
        if (kind == FrostlakeParser.TAG) {
            return action.getToken(FrostlakeParser.SET, 0) != null
                && action.getToken(FrostlakeParser.ALLOWED_VALUES, 0) != null ? 0x62be : 0x6280;
        }
        return kind == FrostlakeParser.CONTACT ? 0x62a5 : ALTER;
    }

    private static long tableAction(final FrostlakeParser.TableActionContext action) {
        final int first = action.getStart().getType();
        if (first == FrostlakeParser.RENAME && action.COLUMN() != null) {
            return 0x6402;
        }
        if (action.ADD() != null && action.columnDef() != null) {
            return 0x6202;
        }
        if (action.ADD() != null && action.tableConstraint() != null) {
            return 0x6103;
        }
        if (first == FrostlakeParser.DROP && !action.identifier().isEmpty() && action.CONSTRAINT() == null) {
            return 0x6203;
        }
        if (first == FrostlakeParser.CLUSTER) {
            return 0x6201;
        }
        if (first == FrostlakeParser.DROP && action.CLUSTERING() != null) {
            return 0x6208;
        }
        return (first == FrostlakeParser.ALTER || first == FrostlakeParser.MODIFY)
            && (action.COLUMN() != null || !action.alterColumnItem().isEmpty()) ? MODIFY_COLUMN : ALTER;
    }

    private static long renamed(final int kind) {
        switch (kind) {
            case FrostlakeParser.TABLE:
                return 0x6401;
            case FrostlakeParser.FILE:
                return 0x6403;
            case FrostlakeParser.DATABASE:
                return 0x6404;
            case FrostlakeParser.SCHEMA:
                return 0x6405;
            case FrostlakeParser.FUNCTION:
                return 0x6406;
            case FrostlakeParser.VIEW:
                return 0x6408;
            case FrostlakeParser.STAGE:
                return 0x6409;
            case FrostlakeParser.SEQUENCE:
                return 0x640a;
            default:
                return RENAME;
        }
    }

    private static long drop(final FrostlakeParser.DropStatementContext drop) {
        if (drop.STREAM() != null) {
            return 0x6307;
        }
        if (drop.TASK() != null) {
            return 0x6308;
        }
        if (drop.MASKING() != null) {
            return 0x6309;
        }
        if (drop.ACCESS() != null) {
            return 0x630a;
        }
        if (drop.TAG() != null) {
            return 0x630b;
        }
        if (drop.PROJECTION() != null) {
            return 0x631c;
        }
        return drop.CONTACT() != null ? 0x6322 : DROP;
    }

    /** The action an ALTER applies: its last rule after the object's name, or null when it writes none. */
    private static ParserRuleContext actionOf(final FrostlakeParser.AlterStatementContext alter) {
        for (int i = alter.getChildCount() - 1; i > 0; i--) {
            final ParseTree child = alter.getChild(i);
            if (child instanceof ParserRuleContext) {
                return child instanceof FrostlakeParser.QualifiedNameContext
                    || child instanceof FrostlakeParser.ObjectNameContext
                    || child instanceof FrostlakeParser.ContainerNameContext
                    || child instanceof FrostlakeParser.IdentifierContext
                    || child instanceof FrostlakeParser.If_existsContext ? null : (ParserRuleContext) child;
            }
        }
        return null;
    }

    /** Whether a node is, or opens with, a rule of the class. */
    private static boolean holds(final ParserRuleContext node, final Class<? extends ParserRuleContext> kind) {
        return kind.isInstance(node) || node.getChildCount() > 0 && kind.isInstance(node.getChild(0));
    }

    /** The token type of a node's child at an index, or -1 where that child is no token. */
    private static int wordAt(final ParserRuleContext node, final int index) {
        if (node.getChildCount() <= index || !(node.getChild(index) instanceof TerminalNode)) {
            return -1;
        }
        return ((TerminalNode) node.getChild(index)).getSymbol().getType();
    }
}
