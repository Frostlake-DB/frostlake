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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The grants a REVOKE reaches beside the one it names (documented). A role holding a privilege WITH GRANT OPTION
 * that passes it on makes a grant recorded in its own name, which depends on the grantee holding the option. When a
 * REVOKE takes the last instance of the option away - with the privilege, or alone through GRANT OPTION FOR - the
 * grants made through it are left without it: RESTRICT, the default, skips the instances that carry the option and
 * revokes the rest, then reports the statement partially executed; CASCADE takes those grants back too, and the
 * grants made through theirs after them, each counted as a grant the statement took back (live-verified). Grants an
 * owner makes stand on its ownership, so revoking a privilege from the object's owner leaves them.
 */
final class DependentGrants {

    private final Catalog catalog;

    /**
     * @param catalog the catalog whose roles and users hold the grants
     */
    DependentGrants(final Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Whether a grant made through the grantee's option stands: one instance of the privilege, held by any role or
     * user, recorded in the grantee's name.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name as privileges record it
     * @param privilege  the privilege revoked, or whose grant option is
     * @param grantee    the account role losing the option
     * @return whether such a grant stands
     */
    boolean exist(final String objectType, final String objectName, final Privilege privilege, final Role grantee) {
        for (final Role dependent : roles()) {
            if (madeBy(dependent.privilegeGrantors(objectType, objectName, privilege), grantee) != null) {
                return true;
            }
        }
        for (final User dependent : catalog.getAllUsers()) {
            if (madeBy(dependent.privilegeGrantors(objectType, objectName, privilege), grantee) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the grantee holds the grant option a revoke takes away, and grants can have been made through it:
     * the object's owner grants on its ownership instead.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name as privileges record it
     * @param privilege  the privilege revoked, or whose grant option is
     * @param grantee    the role losing the option
     * @param owner      the object's owner
     * @return whether grants made in the grantee's name depend on the option
     */
    boolean losesOption(final String objectType, final String objectName, final Privilege privilege,
                        final Role grantee, final String owner) {
        return !(grantee instanceof DatabaseRole) && grantee.hasGrantOption(objectType, objectName, privilege)
            && (owner == null || !catalog.resolveRoleName(owner).equals(grantee.getName()));
    }

    /**
     * Takes back the grants made through an option the grantee lost, and the grants made through theirs: each
     * instance recorded in a grantor's name that no longer holds the option.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name as privileges record it
     * @param privilege  the privilege whose grants are taken back
     * @param grantee    the account role that lost the option
     * @return how many instances were taken back
     */
    int revokeAll(final String objectType, final String objectName, final Privilege privilege, final Role grantee) {
        int taken = 0;
        final Set<String> visited = new HashSet<>();
        visited.add(grantee.getName());
        final List<Role> pending = new ArrayList<>();
        pending.add(grantee);
        while (!pending.isEmpty()) {
            final Role grantor = pending.remove(pending.size() - 1);
            for (final Role dependent : roles()) {
                final String recorded = madeBy(dependent.privilegeGrantors(objectType, objectName, privilege), grantor);
                if (recorded == null) {
                    continue;
                }
                final boolean heldOption = !(dependent instanceof DatabaseRole)
                    && dependent.hasGrantOption(objectType, objectName, privilege);
                dependent.revokePrivilegeGrantedBy(objectType, objectName, privilege, recorded);
                taken++;
                if (heldOption && !dependent.hasGrantOption(objectType, objectName, privilege)
                        && visited.add(dependent.getName())) {
                    pending.add(dependent);
                }
            }
            for (final User dependent : catalog.getAllUsers()) {
                final String recorded = madeBy(dependent.privilegeGrantors(objectType, objectName, privilege), grantor);
                if (recorded != null) {
                    dependent.revokePrivilegeGrantedBy(objectType, objectName, privilege, recorded);
                    taken++;
                }
            }
        }
        return taken;
    }

    /**
     * The grantor, as recorded, of the instance a role made among a grantee's instances of one privilege; null when
     * it made none. Grants with no grantor recorded depend on no option.
     */
    private String madeBy(final List<String> recorded, final Role grantor) {
        for (final String each : recorded) {
            if (catalog.resolveRoleName(each).equals(grantor.getName())) {
                return each;
            }
        }
        return null;
    }

    /** The account roles and every database's database roles: whatever a grant on an object can be made to. */
    private List<Role> roles() {
        final List<Role> roles = new ArrayList<>(catalog.getAllRoles());
        for (final Database database : catalog.getAllDatabases()) {
            roles.addAll(database.getDatabaseRoles());
        }
        return roles;
    }
}
