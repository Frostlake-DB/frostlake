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

import dev.frostlake.config.AccountIdentity;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.SemanticView;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledDropKind;
import dev.frostlake.parser.UnmodelledKindWords;
import java.util.ArrayList;
import java.util.List;

/**
 * The comment of the kinds whose COMMENT ON the account answers exactly as their own ALTER … SET COMMENT: a missing
 * database or schema is refused, IF EXISTS or not; a missing object is refused in its kind's sentence unless IF
 * EXISTS forgives it; and one that is there takes the comment (live-verified). The kinds Frostlake models no object
 * of (a model, a Git repository, a DBT project) are always missing, for COMMENT ON and for their ALTER alike; a
 * snapshot is missing for its ALTER, and takes no COMMENT ON; a dataset takes no comment at all; a connection that is
 * there keeps the comment its ALTER or a COMMENT ON gives it. Any other word written where the kind stands, a quoted word, a qualified name or an IDENTIFIER(), is read
 * as the name of a class, and no class but the built-in ones is modelled: {@code ALTER foo bar SET COMMENT = 'c'}
 * and {@code COMMENT ON foo x IS 'c'} are {@code Object type or Class 'FOO' does not exist or not authorized.}, and
 * an instance of a built-in class is missing (live-verified).
 */
final class KindComments {

    /** The plain words that name a kind where a class's name could stand. */
    private static final String[] KIND_WORDS = {"MODEL", "SNAPSHOT", "DATASET", "CONNECTION"};

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;

    KindComments(final Catalog catalog, final QueryExecutor queryExecutor, final SQLCommandVisitor visitor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
    }

    /**
     * COMMENT ON one of the kinds {@code commentedKind} reads.
     *
     * @param kind     the kind's words
     * @param name     the object's name parts, an IDENTIFIER() reference resolved to the name it holds
     * @param comment  the comment
     * @param ifExists whether IF EXISTS forgives a missing object
     */
    void comment(final FrostlakeParser.CommentedKindContext kind, final String[] name, final String comment,
                 final boolean ifExists) {
        if (kind.unmodelledCommentKind() != null) {
            final FrostlakeParser.UnmodelledCommentKindContext unmodelled = kind.unmodelledCommentKind();
            if (!namesClass(unmodelled) && unmodelledKind(unmodelled) == UnmodelledDropKind.SNAPSHOT) {
                // A snapshot takes its comment from ALTER SNAPSHOT alone (live-verified).
                throw new RuntimeException("Unsupported feature 'TOK_SERVICE_SNAPSHOT'.");
            }
            refuseUnmodelled(unmodelled, name, comment, null, ifExists);
        } else if (kind.ALERT() != null) {
            new AlertCommandHandler(queryExecutor).comment(name, comment, ifExists);
        } else if (kind.NETWORK() != null) {
            securityObject(SecurityObjectKind.NETWORK_RULE, name, comment, ifExists);
        } else if (kind.STREAMLIT() != null) {
            new AppObjectCommandHandler(catalog, queryExecutor, visitor).comment(AppObjectKind.STREAMLIT, name,
                comment, ifExists);
        } else if (kind.NOTEBOOK() != null) {
            new AppObjectCommandHandler(catalog, queryExecutor, visitor).comment(AppObjectKind.NOTEBOOK, name,
                comment, ifExists);
        } else if (kind.AUTHENTICATION() != null) {
            securityObject(SecurityObjectKind.AUTHENTICATION_POLICY, name, comment, ifExists);
        } else if (kind.PASSWORD() != null) {
            securityObject(SecurityObjectKind.PASSWORD_POLICY, name, comment, ifExists);
        } else if (kind.SESSION() != null) {
            securityObject(SecurityObjectKind.SESSION_POLICY, name, comment, ifExists);
        } else if (kind.PACKAGES() != null) {
            securityObject(SecurityObjectKind.PACKAGES_POLICY, name, comment, ifExists);
        } else if (kind.AGGREGATION() != null) {
            final String written = QualifiedName.join(name);
            policy(catalog.findAggregationPolicyExact(written), "AGGREGATION_POLICY", "Aggregation policy", name,
                comment, ifExists);
        } else if (kind.PROJECTION() != null) {
            final String written = QualifiedName.join(name);
            policy(catalog.findProjectionPolicyExact(written), "PROJECTION_POLICY", "Projection policy", name,
                comment, ifExists);
        } else if (kind.JOIN() != null) {
            policy(catalog.findJoinPolicy(QualifiedName.join(name)), "JOIN_POLICY", "Join policy", name, comment,
                ifExists);
        } else if (kind.PRIVACY() != null) {
            policy(catalog.findPrivacyPolicy(QualifiedName.join(name)), "PRIVACY_POLICY", "Privacy policy", name,
                comment, ifExists);
        } else if (kind.IMAGE() != null) {
            new ContainerServicesHandler(catalog, queryExecutor.getEngineConfig(), queryExecutor)
                .commentImageRepository(name, comment, ifExists);
        } else if (kind.CORTEX() != null) {
            cortexSearchService(name, comment, ifExists);
        } else {
            new ContainerServicesHandler(catalog, queryExecutor.getEngineConfig(), queryExecutor)
                .commentService(name, comment, ifExists);
        }
    }

    /**
     * ALTER or COMMENT ON of a kind Frostlake models no object of, or of a class's instance: the class is missed
     * before the instance's name is read, IF EXISTS or not, unless it is a built-in class, whose instance resolves
     * the way any schema object's does and is missing; a kind's object resolves the same way and is missing, which
     * IF EXISTS forgives. A connection is an account's object: a qualified name finds nothing, IF EXISTS or not,
     * and one that is there takes the comment, or loses it, or the new name of a RENAME. A semantic view is modelled: one that is there takes the comment, or loses it, or takes the tags the ALTER sets
     * or unsets, and a relation of another kind holding the name is refused as that kind (live-verified).
     *
     * @param kind     the kind's words
     * @param parts    the object's name, its canonical parts
     * @param comment  the comment a semantic view or a connection that is there takes, or null to unset it
     * @param action   the ALTER's action, or null for a COMMENT ON
     * @param ifExists whether IF EXISTS forgives the missing object
     */
    void refuseUnmodelled(final FrostlakeParser.UnmodelledCommentKindContext kind, final String[] parts,
                          final String comment, final FrostlakeParser.UnmodelledAlterActionContext action,
                          final boolean ifExists) {
        if (namesClass(kind)) {
            final String[] classParts = classParts(kind);
            if (!BuiltInClass.isBuiltIn(classParts, catalog)) {
                if (classParts.length == 3 && catalog.databaseExact(classParts[0]) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Database", classParts[0]));
                }
                throw new RuntimeException(ShowScopeRefusal.missingClass(classParts, catalog));
            }
            final String instance = BuiltInClass.instancePath(parts, catalog);
            if (ifExists) {
                return;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("Instance", instance));
        }
        final UnmodelledDropKind unmodelled = unmodelledKind(kind);
        if (unmodelled == UnmodelledDropKind.CONNECTION) {
            new ConnectionCommandHandler(catalog, AccountIdentity.of(queryExecutor.getEngineConfig()))
                .comment(parts, comment, action == null || action.RENAME() == null ? null
                    : queryExecutor.resolveObjectNameParts(action.objectName()), ifExists);
            return;
        }
        if (!unmodelled.isSchemaObject()) {
            if (parts.length > 1) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            if (ifExists) {
                return;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist(unmodelled.noun(), parts[0]));
        }
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        if (unmodelled == UnmodelledDropKind.SEMANTIC_VIEW
                && semanticView(owner, parts[parts.length - 1], comment, action)) {
            return;
        }
        if (ifExists) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist(unmodelled.noun(),
            owner.qualifiedName(parts[parts.length - 1])));
    }

    /**
     * A semantic view's comment or tags, when one holds the name.
     *
     * @return whether a semantic view holds the name and took the change
     */
    private boolean semanticView(final Schema owner, final String name, final String comment,
                                 final FrostlakeParser.UnmodelledAlterActionContext action) {
        final RelationKind holder = owner.relationKindOf(name);
        if (holder != null && holder != RelationKind.SEMANTIC_VIEW) {
            // A relation of another kind holds the name, which IF EXISTS does not forgive (live-verified).
            throw new RuntimeException(SqlCompilationError.objectOfOtherType(holder.spelling(), "SEMANTIC_VIEW"));
        }
        if (holder == null) {
            return false;
        }
        final SemanticView view = owner.getSemanticViewExact(name);
        if (action != null && action.tagSet() != null) {
            new AlteredTags(queryExecutor, visitor).set(view, action.tagSet());
        } else if (action != null && action.tagUnset() != null) {
            new AlteredTags(queryExecutor, visitor).unset(view, action.tagUnset());
        } else {
            new SemanticViewCommandHandler(catalog, queryExecutor).comment(view, comment);
        }
        return true;
    }

    /**
     * Whether the words name a class rather than a kind: a plain word that is none of MODEL, SNAPSHOT, DATASET and
     * CONNECTION, a quoted word, a qualified name or an IDENTIFIER().
     *
     * @param kind the kind's words
     * @return whether they name a class
     */
    static boolean namesClass(final FrostlakeParser.UnmodelledCommentKindContext kind) {
        if (kind.GIT() != null || kind.SEMANTIC() != null || kind.DBT() != null) {
            return false;
        }
        if (kind.model == null) {
            return true;
        }
        for (final String word : KIND_WORDS) {
            if (UnmodelledKindWords.is(kind.model, word)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The canonical parts of the class the words name: a plain word as a name is spelled ({@code foo} is FOO, a
     * quoted {@code "foo"} keeps its case), an empty middle part the database's PUBLIC schema, and an IDENTIFIER()
     * its argument's parts.
     */
    private String[] classParts(final FrostlakeParser.UnmodelledCommentKindContext kind) {
        if (kind.identifierArgument() != null) {
            return queryExecutor.resolveIdentifierArgument(kind.identifierArgument());
        }
        final List<String> parts = new ArrayList<>();
        parts.add(SqlIdentifiers.canonicalText(kind.getStart().getText()));
        if (kind.qualifier == null) {
            return parts.toArray(new String[0]);
        }
        if (kind.DOT().size() > kind.namePart().size()) {
            parts.add("PUBLIC");
        }
        for (final FrostlakeParser.NamePartContext part : kind.namePart()) {
            parts.add(ParseTreeText.namePartText(part));
        }
        return parts.toArray(new String[0]);
    }

    /**
     * The kind an unmodelled comment kind's words name, for words that name no class.
     *
     * @param kind the kind's words
     * @return the kind
     */
    static UnmodelledDropKind unmodelledKind(final FrostlakeParser.UnmodelledCommentKindContext kind) {
        if (kind.GIT() != null) {
            return UnmodelledDropKind.GIT_REPOSITORY;
        }
        if (kind.SEMANTIC() != null) {
            return UnmodelledDropKind.SEMANTIC_VIEW;
        }
        if (kind.DBT() != null) {
            return UnmodelledDropKind.DBT_PROJECT;
        }
        final UnmodelledDropKind named = UnmodelledDropKind.of(kind.model);
        return named != null ? named : UnmodelledDropKind.MODEL;
    }

    private void securityObject(final SecurityObjectKind kind, final String[] name,
                                final String comment, final boolean ifExists) {
        new SecurityObjectCommandHandler(catalog, queryExecutor).comment(kind, name, comment, ifExists);
    }

    /**
     * A join, aggregation, projection or privacy policy's comment: the policy is named exactly as its ALTER names it,
     * a policy of another kind holding a privacy policy's name is refused as that kind, and the change asks MODIFY.
     */
    private void policy(final SqlObject found, final String kind, final String noun,
                        final String[] parts, final String comment, final boolean ifExists) {
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        if (found == null) {
            final String other = "PRIVACY_POLICY".equals(kind) ? schema.policyKindHolding(parts[parts.length - 1])
                : null;
            if (other != null && !other.equals(kind)) {
                throw new RuntimeException(SqlCompilationError.objectOfOtherType(other, kind));
            }
            if (ifExists) {
                return;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist(noun,
                catalog.qualifiedExactName(QualifiedName.join(parts))));
        }
        queryExecutor.requirePrivilege(Privilege.MODIFY, kind, parts, null);
        found.setComment(comment);
    }

    private void cortexSearchService(final String[] parts, final String comment, final boolean ifExists) {
        catalog.requireOwningSchema(QualifiedName.of(parts));
        if (ifExists && !catalog.hasCortexSearchService(parts)) {
            return;
        }
        catalog.resolveCortexSearchService(parts).setComment(comment);
    }
}
