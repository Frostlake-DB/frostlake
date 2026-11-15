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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledDropKind;

/**
 * The comment of the kinds whose COMMENT ON the account answers exactly as their own ALTER … SET COMMENT: a missing
 * database or schema is refused, IF EXISTS or not; a missing object is refused in its kind's sentence unless IF
 * EXISTS forgives it; and one that is there takes the comment (live-verified). The kinds Frostlake models no object
 * of (a model, a Git repository, a semantic view, a DBT project) are always missing, for COMMENT ON and for the
 * ALTER that sets or unsets their comment alike.
 */
final class KindComments {

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
     * @param name     the object's name
     * @param comment  the comment
     * @param ifExists whether IF EXISTS forgives a missing object
     */
    void comment(final FrostlakeParser.CommentedKindContext kind, final FrostlakeParser.QualifiedNameContext name,
                 final String comment, final boolean ifExists) {
        if (kind.unmodelledCommentKind() != null) {
            refuseUnmodelled(kind.unmodelledCommentKind(), name, ifExists);
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
            final String written = visitor.getText(name);
            policy(catalog.findAggregationPolicyExact(written), "AGGREGATION_POLICY", "Aggregation policy", name,
                comment, ifExists);
        } else if (kind.PROJECTION() != null) {
            final String written = visitor.getText(name);
            policy(catalog.findProjectionPolicyExact(written), "PROJECTION_POLICY", "Projection policy", name,
                comment, ifExists);
        } else if (kind.JOIN() != null) {
            policy(catalog.findJoinPolicy(visitor.getText(name)), "JOIN_POLICY", "Join policy", name, comment,
                ifExists);
        } else if (kind.PRIVACY() != null) {
            policy(catalog.findPrivacyPolicy(visitor.getText(name)), "PRIVACY_POLICY", "Privacy policy", name,
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
     * ALTER of a kind Frostlake models no object of that sets or unsets its comment: the name resolves the way any
     * schema object's does, and the object is then missing, which IF EXISTS forgives.
     *
     * @param kind     the kind's words
     * @param name     the object's name
     * @param ifExists whether IF EXISTS forgives the missing object
     */
    void refuseUnmodelled(final FrostlakeParser.UnmodelledCommentKindContext kind,
                          final FrostlakeParser.QualifiedNameContext name, final boolean ifExists) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        if (ifExists) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist(unmodelledKind(kind).noun(),
            owner.qualifiedName(parts[parts.length - 1])));
    }

    /**
     * The kind an unmodelled comment kind's words name.
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
        return kind.DBT() != null ? UnmodelledDropKind.DBT_PROJECT : UnmodelledDropKind.MODEL;
    }

    private void securityObject(final SecurityObjectKind kind, final FrostlakeParser.QualifiedNameContext name,
                                final String comment, final boolean ifExists) {
        new SecurityObjectCommandHandler(catalog, queryExecutor).comment(kind, name, comment, ifExists);
    }

    /**
     * A join, aggregation, projection or privacy policy's comment: the policy is named exactly as its ALTER names it,
     * a policy of another kind holding a privacy policy's name is refused as that kind, and the change asks MODIFY.
     */
    private void policy(final SqlObject found, final String kind, final String noun,
                        final FrostlakeParser.QualifiedNameContext name, final String comment,
                        final boolean ifExists) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
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
                catalog.qualifiedExactName(visitor.getText(name))));
        }
        queryExecutor.requirePrivilege(Privilege.MODIFY, kind, parts, null);
        found.setComment(comment);
    }

    private void cortexSearchService(final FrostlakeParser.QualifiedNameContext name, final String comment,
                                     final boolean ifExists) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        catalog.requireOwningSchema(QualifiedName.of(parts));
        if (ifExists && !catalog.hasCortexSearchService(parts)) {
            return;
        }
        catalog.resolveCortexSearchService(parts).setComment(comment);
    }
}
