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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.TagAssignments;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;

/**
 * An ALTER's SET TAG and UNSET TAG over one object, judged whole before any tag changes (live-verified): every tag
 * the action names has to exist and be one the session may apply, then each value has to be one its tag allows, and
 * only then are the tags set or taken off, so a refused action leaves the object's tags as they were. A tag set
 * twice takes the value written last.
 */
final class AlteredTags {

    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;
    private final Catalog catalog;

    AlteredTags(final QueryExecutor queryExecutor, final SQLCommandVisitor visitor) {
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
        this.catalog = queryExecutor.getCatalog();
    }

    /**
     * Set each tag of a SET TAG action on an object.
     *
     * @param target the object
     * @param set    the action
     */
    void set(final Taggable target, final FrostlakeParser.TagSetContext set) {
        final List<Tag> tags = new ArrayList<>();
        for (final FrostlakeParser.TagAssignContext assign : set.tagAssign()) {
            tags.add(applicable(assign.qualifiedName()));
        }
        final List<String> values = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            final FrostlakeParser.TagAssignContext assign = set.tagAssign(i);
            final String value = TagValues.text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
            TagValues.requireAllowed(tags.get(i), value);
            values.add(value);
        }
        for (int i = 0; i < tags.size(); i++) {
            TagAssignments.set(target, tags.get(i), values.get(i));
        }
    }

    /**
     * Take each tag of an UNSET TAG action off an object.
     *
     * @param target the object
     * @param unset  the action
     */
    void unset(final Taggable target, final FrostlakeParser.TagUnsetContext unset) {
        final List<Tag> tags = new ArrayList<>();
        for (final FrostlakeParser.QualifiedNameContext name : unset.qualifiedName()) {
            tags.add(applicable(name));
        }
        for (final Tag tag : tags) {
            TagAssignments.unset(target, tag);
        }
    }

    /**
     * The tag an action names, which must exist (live-verified on every kind that takes tags) and be one the session
     * may apply: APPLY TAG on the account, or APPLY on the tag beside the privilege on the object the statement already
     * asks (documented, "Tag privileges"; see ApplyPrivileges). A missing tag named bare is refused by that name alone
     * - Tag 'NOSUCH', Tag '"nosuch"' - and one named by a path by its full path.
     */
    private Tag applicable(final FrostlakeParser.QualifiedNameContext name) {
        final String tagName = visitor.getText(name);
        final QualifiedName written = QualifiedName.parse(tagName);
        if (written.size() == 1 && catalog.getCurrentDatabase() != null && catalog.getCurrentSchema() != null
                && !catalog.hasTagExact(tagName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", written.last()));
        }
        final Tag tag = catalog.getTagExact(tagName);
        ApplyPrivileges.require(queryExecutor, Privilege.APPLY_TAG, "TAG", catalog.qualifiedExactName(tagName),
            tag.getOwner());
        return tag;
    }
}
