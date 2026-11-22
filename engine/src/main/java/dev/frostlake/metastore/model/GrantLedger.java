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

package dev.frostlake.metastore.model;

import dev.frostlake.executor.StatementClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a grantee holds, kept once for a role and a user alike: the privileges granted on objects and on their
 * columns, with the role each was granted by, when, and whether WITH GRANT OPTION; the roles and the database roles
 * granted to it, with their grantors and moments. A privilege is keyed {@code KIND:NAME}, the object's kind and its
 * name exactly as recorded — a quoted {@code "t"} and an unquoted {@code T} are two objects with two sets of grants
 * (live-verified); the callers hand in canonical names — and what is recorded about one privilege under
 * {@code KIND:NAME:PRIVILEGE}. A grant dates from when it was first made; granting it again keeps that. A revoke
 * takes back everything recorded about the grant, so a later grant starts afresh.
 *
 * <p>A privilege can be granted to one grantee several times by different grantors, and each instance is listed and
 * revoked on its own (documented, live-verified): the first instance is recorded as above, and each other grantor's
 * instance beside it, with its own moment and grant option. When the first instance is revoked the earliest of the
 * others takes its place. The grantee holds the grant option while any instance carries it.
 */
final class GrantLedger {

    /** The grantor a grant made with none recorded is reported as. */
    private static final String DEFAULT_GRANTOR = "SYSADMIN";

    private final Map<String, Set<Privilege>> objectPrivileges = new HashMap<>();
    private final Map<String, Map<String, Set<Privilege>>> columnPrivileges = new HashMap<>();
    private final Map<String, String> privilegeGrantors = new HashMap<>();
    private final Map<String, Instant> privilegeGrantTimes = new HashMap<>();
    private final Set<String> grantOptions = new HashSet<>();
    /** The instances of a privilege granted by grantors other than the first, by grantor, in the order made. */
    private final Map<String, Map<String, GrantInstance>> otherGrants = new HashMap<>();
    private final Set<String> grantedRoles = new HashSet<>();
    private final Map<String, String> roleGrantors = new HashMap<>();
    private final Map<String, Instant> roleGrantTimes = new HashMap<>();
    /** The database roles granted, by qualified name, each with the role that granted it, in the order granted. */
    private final Map<String, String> databaseRoleGrantors = new LinkedHashMap<>();
    private final Map<String, Instant> databaseRoleGrantTimes = new HashMap<>();

    /** The key a grant on an object is kept under. */
    static String key(final String objectType, final String objectName) {
        return objectType.toUpperCase(Locale.ROOT) + ":" + objectName;
    }

    private static String key(final String objectType, final String objectName, final Privilege privilege) {
        return key(objectType, objectName) + ":" + privilege.name();
    }

    // ── privileges on objects ──

    void grantPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final String key = key(objectType, objectName);
        Set<Privilege> granted = objectPrivileges.get(key);
        if (granted == null) {
            granted = new HashSet<>();
            objectPrivileges.put(key, granted);
        }
        granted.add(privilege);
    }

    void grantPrivilege(final String objectType, final String objectName, final Privilege privilege,
                        final String grantor) {
        final String key = key(objectType, objectName, privilege);
        final String first = privilegeGrantors.get(key);
        if (first != null && grantor != null && !first.equals(grantor)
                && hasPrivilege(objectType, objectName, privilege)) {
            // Another grantor's grant of a privilege already held is an instance of its own.
            Map<String, GrantInstance> others = otherGrants.get(key);
            if (others == null) {
                others = new LinkedHashMap<>();
                otherGrants.put(key, others);
            }
            if (!others.containsKey(grantor)) {
                others.put(grantor, new GrantInstance(StatementClock.instant()));
            }
            return;
        }
        grantPrivilege(objectType, objectName, privilege);
        privilegeGrantors.put(key, grantor);
        privilegeGrantTimes.putIfAbsent(key, StatementClock.instant());
    }

    void revokePrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final String recorded = key(objectType, objectName, privilege);
        otherGrants.remove(recorded);
        grantOptions.remove(recorded);
        privilegeGrantTimes.remove(recorded);
        privilegeGrantors.remove(recorded);
        final String key = key(objectType, objectName);
        final Set<Privilege> privileges = objectPrivileges.get(key);
        if (privileges != null) {
            privileges.remove(privilege);
            if (privileges.isEmpty()) {
                objectPrivileges.remove(key);
            }
        }
    }

    /**
     * Takes back the instance of a privilege one grantor made, leaving the instances other grantors made: the
     * earliest of those takes the first one's place when the first is taken.
     *
     * @return whether the grantor had made an instance
     */
    boolean revokePrivilegeGrantedBy(final String objectType, final String objectName, final Privilege privilege,
                                     final String grantor) {
        if (!hasPrivilege(objectType, objectName, privilege)) {
            return false;
        }
        final String key = key(objectType, objectName, privilege);
        final Map<String, GrantInstance> others = otherGrants.get(key);
        if (isFirst(objectType, objectName, privilege, grantor)) {
            if (others == null || others.isEmpty()) {
                revokePrivilege(objectType, objectName, privilege);
                return true;
            }
            String earliest = null;
            for (final Map.Entry<String, GrantInstance> other : others.entrySet()) {
                if (earliest == null || other.getValue().getGranted().isBefore(others.get(earliest).getGranted())) {
                    earliest = other.getKey();
                }
            }
            final GrantInstance promoted = others.remove(earliest);
            if (others.isEmpty()) {
                otherGrants.remove(key);
            }
            privilegeGrantors.put(key, earliest);
            privilegeGrantTimes.put(key, promoted.getGranted());
            if (promoted.hasOption()) {
                grantOptions.add(key);
            } else {
                grantOptions.remove(key);
            }
            return true;
        }
        if (others == null || others.remove(grantor) == null) {
            return false;
        }
        if (others.isEmpty()) {
            otherGrants.remove(key);
        }
        return true;
    }

    /**
     * Records a privilege held as one grant in a grantor's name, whoever made its instances: how a grant copied
     * with an object's ownership is kept (COPY CURRENT GRANTS). It keeps the first instance's moment, and the grant
     * option when any instance carried it.
     */
    void regrantAs(final String objectType, final String objectName, final Privilege privilege, final String grantor) {
        if (!hasPrivilege(objectType, objectName, privilege)) {
            return;
        }
        final String key = key(objectType, objectName, privilege);
        if (hasGrantOption(objectType, objectName, privilege)) {
            grantOptions.add(key);
        }
        otherGrants.remove(key);
        privilegeGrantors.put(key, grantor);
        privilegeGrantTimes.putIfAbsent(key, StatementClock.instant());
    }

    /**
     * The grantors of the instances of a privilege the grantee holds, in name order; none when it holds none.
     */
    List<String> privilegeGrantors(final String objectType, final String objectName, final Privilege privilege) {
        final List<String> grantors = new ArrayList<>();
        if (!hasPrivilege(objectType, objectName, privilege)) {
            return grantors;
        }
        grantors.add(getPrivilegeGrantor(objectType, objectName, privilege));
        final Map<String, GrantInstance> others = otherGrants.get(key(objectType, objectName, privilege));
        if (others != null) {
            grantors.addAll(others.keySet());
        }
        Collections.sort(grantors);
        return grantors;
    }

    /** When one grantor's instance of a privilege was made; null when it made none or no moment is recorded. */
    Instant getPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege,
                                  final String grantor) {
        final GrantInstance other = otherInstance(objectType, objectName, privilege, grantor);
        return other != null ? other.getGranted() : getPrivilegeGrantTime(objectType, objectName, privilege);
    }

    /** Puts back the moment one grantor's instance of a privilege was made, as a snapshot recorded it. */
    void setPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege,
                               final String grantor, final Instant granted) {
        final GrantInstance other = otherInstance(objectType, objectName, privilege, grantor);
        if (other != null) {
            other.setGranted(granted);
        } else {
            setPrivilegeGrantTime(objectType, objectName, privilege, granted);
        }
    }

    /** Whether one grantor's instance of a privilege carries the grant option. */
    boolean hasGrantOption(final String objectType, final String objectName, final Privilege privilege,
                           final String grantor) {
        final GrantInstance other = otherInstance(objectType, objectName, privilege, grantor);
        return other != null ? other.hasOption()
            : isFirst(objectType, objectName, privilege, grantor)
                && grantOptions.contains(key(objectType, objectName, privilege));
    }

    /** Gives or takes the grant option of one grantor's instance of a privilege. */
    void setGrantOption(final String objectType, final String objectName, final Privilege privilege,
                        final String grantor, final boolean holds) {
        final GrantInstance other = otherInstance(objectType, objectName, privilege, grantor);
        if (other != null) {
            other.setOption(holds);
        } else if (isFirst(objectType, objectName, privilege, grantor)) {
            final String key = key(objectType, objectName, privilege);
            if (holds) {
                grantOptions.add(key);
            } else {
                grantOptions.remove(key);
            }
        }
    }

    /** Whether a grantor names the first instance of a privilege: null names it too, as a grant with none recorded. */
    private boolean isFirst(final String objectType, final String objectName, final Privilege privilege,
                            final String grantor) {
        return grantor == null || grantor.equals(getPrivilegeGrantor(objectType, objectName, privilege));
    }

    /** The instance of a privilege a grantor other than the first made; null when there is none. */
    private GrantInstance otherInstance(final String objectType, final String objectName, final Privilege privilege,
                                        final String grantor) {
        final Map<String, GrantInstance> others = otherGrants.get(key(objectType, objectName, privilege));
        return others == null ? null : others.get(grantor);
    }

    boolean hasPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final Set<Privilege> privileges = objectPrivileges.get(key(objectType, objectName));
        return privileges != null && privileges.contains(privilege);
    }

    Set<Privilege> getPrivileges(final String objectType, final String objectName) {
        return objectPrivileges.getOrDefault(key(objectType, objectName), new HashSet<>());
    }

    Map<String, Set<Privilege>> getAllPrivileges() {
        return new HashMap<>(objectPrivileges);
    }

    Instant getPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege) {
        return privilegeGrantTimes.get(key(objectType, objectName, privilege));
    }

    void setPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege,
                               final Instant granted) {
        privilegeGrantTimes.put(key(objectType, objectName, privilege), granted);
    }

    String getPrivilegeGrantor(final String objectType, final String objectName, final Privilege privilege) {
        return privilegeGrantors.getOrDefault(key(objectType, objectName, privilege), DEFAULT_GRANTOR);
    }

    String recordedPrivilegeGrantor(final String objectType, final String objectName, final Privilege privilege) {
        return privilegeGrantors.get(key(objectType, objectName, privilege));
    }

    /** Gives the first instance of a privilege the grant option, or takes it from every instance. */
    void setGrantOption(final String objectType, final String objectName, final Privilege privilege,
                        final boolean holds) {
        final String key = key(objectType, objectName, privilege);
        if (holds) {
            grantOptions.add(key);
            return;
        }
        grantOptions.remove(key);
        final Map<String, GrantInstance> others = otherGrants.get(key);
        if (others != null) {
            for (final GrantInstance other : others.values()) {
                other.setOption(false);
            }
        }
    }

    /** Whether the grantee holds the grant option of a privilege: any instance of it carries the option. */
    boolean hasGrantOption(final String objectType, final String objectName, final Privilege privilege) {
        final String key = key(objectType, objectName, privilege);
        if (grantOptions.contains(key)) {
            return true;
        }
        final Map<String, GrantInstance> others = otherGrants.get(key);
        if (others != null) {
            for (final GrantInstance other : others.values()) {
                if (other.hasOption()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── privileges on columns ──

    void grantColumnPrivilege(final String objectType, final String objectName, final String columnName,
                              final Privilege privilege) {
        final String key = key(objectType, objectName);
        Map<String, Set<Privilege>> byColumn = columnPrivileges.get(key);
        if (byColumn == null) {
            byColumn = new HashMap<>();
            columnPrivileges.put(key, byColumn);
        }
        final String upperColumn = columnName.toUpperCase(Locale.ROOT);
        Set<Privilege> granted = byColumn.get(upperColumn);
        if (granted == null) {
            granted = new HashSet<>();
            byColumn.put(upperColumn, granted);
        }
        granted.add(privilege);
    }

    void revokeColumnPrivilege(final String objectType, final String objectName, final String columnName,
                               final Privilege privilege) {
        final String key = key(objectType, objectName);
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns == null) {
            return;
        }
        final String upperColumn = columnName.toUpperCase(Locale.ROOT);
        final Set<Privilege> privileges = columns.get(upperColumn);
        if (privileges != null) {
            privileges.remove(privilege);
            if (privileges.isEmpty()) {
                columns.remove(upperColumn);
            }
            if (columns.isEmpty()) {
                columnPrivileges.remove(key);
            }
        }
    }

    /** Whether a column is held: a privilege on the whole object reaches every column of it. */
    boolean hasColumnPrivilege(final String objectType, final String objectName, final String columnName,
                               final Privilege privilege) {
        if (hasPrivilege(objectType, objectName, privilege)) {
            return true;
        }
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key(objectType, objectName));
        if (columns == null) {
            return false;
        }
        final Set<Privilege> privileges = columns.get(columnName.toUpperCase(Locale.ROOT));
        return privileges != null && privileges.contains(privilege);
    }

    Set<String> getColumnsWithPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final Set<String> result = new HashSet<>();
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key(objectType, objectName));
        if (columns != null) {
            for (final Map.Entry<String, Set<Privilege>> entry : columns.entrySet()) {
                if (entry.getValue().contains(privilege)) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    Map<String, Map<String, Set<Privilege>>> getAllColumnPrivileges() {
        final Map<String, Map<String, Set<Privilege>>> result = new HashMap<>();
        for (final Map.Entry<String, Map<String, Set<Privilege>>> entry : columnPrivileges.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }

    // ── a change of the names the privileges are recorded under ──

    /** Applies a change of recorded names to the privileges held on objects (see {@link GrantRemap}). */
    void remapGrants(final GrantRemap remap) {
        GrantKeys.remap(objectPrivileges, columnPrivileges, privilegeGrantors, privilegeGrantTimes, grantOptions,
            otherGrants, remap);
    }

    /**
     * Takes back the privileges held on one object.
     *
     * @param objectType    the kind the privileges are recorded under
     * @param objectName    the name they are recorded under
     * @param ownershipOnly true to take back only OWNERSHIP
     */
    void purgeGrants(final String objectType, final String objectName, final boolean ownershipOnly) {
        GrantKeys.purge(objectPrivileges, columnPrivileges, privilegeGrantors, privilegeGrantTimes, grantOptions,
            otherGrants, key(objectType, objectName), ownershipOnly);
    }

    // ── roles granted ──

    void grantRole(final String roleName) {
        grantedRoles.add(roleName);
    }

    void grantRole(final String roleName, final String grantor) {
        grantedRoles.add(roleName);
        roleGrantors.put(roleName, grantor);
        // The grant is stamped with the statement's own instant, which a replay restores.
        roleGrantTimes.put(roleName, StatementClock.instant());
    }

    void revokeRole(final String roleName) {
        grantedRoles.remove(roleName);
        roleGrantors.remove(roleName);
        roleGrantTimes.remove(roleName);
    }

    boolean hasRole(final String roleName) {
        return grantedRoles.contains(roleName);
    }

    Set<String> getGrantedRoles() {
        return new HashSet<>(grantedRoles);
    }

    Instant getRoleGrantTime(final String roleName) {
        return roleGrantTimes.get(roleName);
    }

    /** Puts back the moment a restored grant was made at; none where the snapshot recorded no moment for it. */
    void restoreRoleGrantTime(final String roleName, final Instant granted) {
        if (granted != null) {
            roleGrantTimes.put(roleName, granted);
        } else {
            roleGrantTimes.remove(roleName);
        }
    }

    String getRoleGrantor(final String roleName) {
        return roleGrantors.getOrDefault(roleName, DEFAULT_GRANTOR);
    }

    /** Whether a role reached the grantee through a GRANT ROLE, which records its grantor, rather than implicitly. */
    boolean hasExplicitRoleGrant(final String roleName) {
        return roleGrantors.containsKey(roleName);
    }

    // ── database roles granted ──

    void grantDatabaseRole(final String qualifiedName, final String grantor) {
        databaseRoleGrantors.put(qualifiedName, grantor);
        databaseRoleGrantTimes.putIfAbsent(qualifiedName, StatementClock.instant());
    }

    void revokeDatabaseRole(final String qualifiedName) {
        databaseRoleGrantors.remove(qualifiedName);
        databaseRoleGrantTimes.remove(qualifiedName);
    }

    Map<String, String> getDatabaseRoleGrants() {
        return new LinkedHashMap<>(databaseRoleGrantors);
    }

    void setDatabaseRoleGrantTime(final String qualifiedName, final Instant granted) {
        databaseRoleGrantTimes.put(qualifiedName, granted);
    }

    Instant getDatabaseRoleGrantTime(final String qualifiedName) {
        return databaseRoleGrantTimes.get(qualifiedName);
    }

    // ── a grantor's drop ──

    /**
     * Takes back what one role granted, as that role's drop does (documented): the instances of privileges
     * recorded in its name but OWNERSHIP, which stays with its holder, and the database roles it granted.
     *
     * @param grantor the role's canonical name, as grants record it
     */
    void revokeGrantedBy(final String grantor) {
        for (final Map.Entry<String, Set<Privilege>> held : new ArrayList<>(objectPrivileges.entrySet())) {
            final String objectKey = held.getKey();
            final int split = objectKey.indexOf(':');
            for (final Privilege privilege : new ArrayList<>(held.getValue())) {
                final String recorded = objectKey + ":" + privilege.name();
                final Map<String, GrantInstance> others = otherGrants.get(recorded);
                if (privilege != Privilege.OWNERSHIP && (grantor.equals(privilegeGrantors.get(recorded))
                        || others != null && others.containsKey(grantor))) {
                    revokePrivilegeGrantedBy(objectKey.substring(0, split), objectKey.substring(split + 1),
                        privilege, grantor);
                }
            }
        }
        for (final Map.Entry<String, String> granted : new ArrayList<>(databaseRoleGrantors.entrySet())) {
            if (grantor.equals(granted.getValue())) {
                revokeDatabaseRole(granted.getKey());
            }
        }
    }

    /**
     * Takes what another ledger holds, with the names under one database recorded under another's: how a database
     * clone's database roles start (documented: a database role is cloned with its database). The privileges keep
     * their grantors, grant times and grant options; a database role granted from the source's own database is
     * granted from the clone.
     *
     * @param source       the ledger to copy
     * @param fromDatabase the source's database, canonical
     * @param toDatabase   the clone's database, canonical
     */
    void copyFrom(final GrantLedger source, final String fromDatabase, final String toDatabase) {
        final Set<String> kinds = new HashSet<>();
        for (final Map.Entry<String, Set<Privilege>> held : source.objectPrivileges.entrySet()) {
            objectPrivileges.put(held.getKey(), new HashSet<>(held.getValue()));
            kinds.add(held.getKey().substring(0, held.getKey().indexOf(':')));
        }
        for (final Map.Entry<String, Map<String, Set<Privilege>>> held : source.columnPrivileges.entrySet()) {
            final Map<String, Set<Privilege>> columns = new HashMap<>();
            for (final Map.Entry<String, Set<Privilege>> column : held.getValue().entrySet()) {
                columns.put(column.getKey(), new HashSet<>(column.getValue()));
            }
            columnPrivileges.put(held.getKey(), columns);
            kinds.add(held.getKey().substring(0, held.getKey().indexOf(':')));
        }
        privilegeGrantors.putAll(source.privilegeGrantors);
        privilegeGrantTimes.putAll(source.privilegeGrantTimes);
        grantOptions.addAll(source.grantOptions);
        for (final Map.Entry<String, Map<String, GrantInstance>> held : source.otherGrants.entrySet()) {
            final Map<String, GrantInstance> others = new LinkedHashMap<>();
            for (final Map.Entry<String, GrantInstance> other : held.getValue().entrySet()) {
                others.put(other.getKey(), other.getValue().copy());
            }
            otherGrants.put(held.getKey(), others);
        }
        grantedRoles.addAll(source.grantedRoles);
        roleGrantors.putAll(source.roleGrantors);
        roleGrantTimes.putAll(source.roleGrantTimes);
        remapGrants(new GrantRemap(kinds, new String[] {fromDatabase}, new String[] {toDatabase}, false, false,
            GrantRemap.ALL));
        final String prefix = fromDatabase + ".";
        for (final Map.Entry<String, String> granted : source.databaseRoleGrantors.entrySet()) {
            final String name = granted.getKey().startsWith(prefix)
                ? toDatabase + "." + granted.getKey().substring(prefix.length()) : granted.getKey();
            databaseRoleGrantors.put(name, granted.getValue());
            final Instant when = source.databaseRoleGrantTimes.get(granted.getKey());
            if (when != null) {
                databaseRoleGrantTimes.put(name, when);
            }
        }
    }
}
