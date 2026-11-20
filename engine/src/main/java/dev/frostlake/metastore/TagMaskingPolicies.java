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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.types.SqlTypeNames;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tag-based masking: a tag carries masking policies, one per data type, and masks every column of a policy's type the
 * tag is set on, on the column itself or on the table, the schema or the database holding it (live-verified). A
 * policy attached to the column directly is the column's own and the tags' are not read. A column the tags reaching it
 * give two different policies of its type is masked by neither: every read of it is refused.
 */
public final class TagMaskingPolicies {

    private TagMaskingPolicies() {
    }

    /**
     * The policies the tags reaching a column give it for its type, each named in full: none, one, or several when
     * the tags disagree.
     *
     * @param catalog the catalog
     * @param table   the table holding the column, which names where it lives
     * @param column  the column
     * @return the policies' names, in the order the column, the table, the schema and the database give them
     */
    public static List<String> of(final Catalog catalog, final Table table, final TableColumn column) {
        final List<String> names = new ArrayList<>();
        for (final String[] source : sources(catalog, table, column)) {
            names.add(source[0]);
        }
        return names;
    }

    /**
     * The policies the tags reaching a column give it for its type, each with the tag that gives it first: none,
     * one, or several when the tags disagree.
     *
     * @param catalog the catalog
     * @param table   the table holding the column, which names where it lives
     * @param column  the column
     * @return per policy, its name in full and the tag's database, schema and name, in the order the column, the
     *         table, the schema and the database give them
     */
    public static List<String[]> sources(final Catalog catalog, final Table table, final TableColumn column) {
        final Map<String, String[]> found = new LinkedHashMap<>();
        if (table == null || column == null || table.getQualifiedName() == null) {
            return new ArrayList<>(found.values());
        }
        final String[] where = QualifiedName.parse(table.getQualifiedName()).parts();
        if (where.length < 3) {
            return new ArrayList<>(found.values());
        }
        final Database database = catalog.findDatabase(where[0]);
        final Schema schema = database != null && database.hasSchema(where[1]) ? database.getSchema(where[1]) : null;
        collect(catalog, TagAssignments.on(catalog, column, where[0], where[1]), column, found);
        collect(catalog, TagAssignments.on(catalog, table, where[0], where[1]), column, found);
        if (schema != null) {
            collect(catalog, TagAssignments.on(catalog, schema, where[0], null), column, found);
        }
        if (database != null) {
            collect(catalog, TagAssignments.on(catalog, database, null, null), column, found);
        }
        return new ArrayList<>(found.values());
    }

    /**
     * Whether a masking policy is carried by a tag.
     *
     * @param catalog    the catalog
     * @param home       the policy's schema
     * @param policyName the policy's own name
     * @return whether a tag carries it
     */
    public static boolean carried(final Catalog catalog, final Schema home, final String policyName) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Tag tag : schema.getTags()) {
                    for (final String policy : tag.getMaskingPolicies()) {
                        if (PolicyReferences.names(catalog, policy, schema, home, policyName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * The refusal of a read of a column the tags reaching it give several masking policies (live-verified).
     *
     * @param table  the table holding the column
     * @param column the column
     * @return the refusal
     */
    public static RuntimeException conflict(final Table table, final TableColumn column) {
        return new RuntimeException("SQL execution error: Column " + table.getQualifiedName() + "."
            + column.getName() + " is mapped to multiple masking policies by tags."
            + "Please contact your local administrator to fix the issue.");
    }

    private static void collect(final Catalog catalog, final List<TagAssignment> assignments,
                                final TableColumn column, final Map<String, String[]> found) {
        for (final TagAssignment assignment : assignments) {
            for (final String name : assignment.getTag().getMaskingPolicies()) {
                final MaskingPolicy policy = policyNamed(catalog, name);
                if (policy != null && !policy.getParameters().isEmpty() && !found.containsKey(name)
                        && SqlTypeNames.sameFamily(column.getDataType(), policy.getParameters().get(0).getDataType())) {
                    found.put(name, new String[] {name, assignment.getDatabase(), assignment.getSchema(),
                        assignment.getTag().getName()});
                }
            }
        }
    }

    private static MaskingPolicy policyNamed(final Catalog catalog, final String name) {
        try {
            return catalog.findMaskingPolicyExact(name);
        } catch (final RuntimeException gone) {
            return null;
        }
    }
}
