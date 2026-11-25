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

/**
 * How much of the SNOWFLAKE database the statement running on this thread sees. A session whose roles reach
 * ACCOUNTADMIN, or hold IMPORTED PRIVILEGES on the database, sees all of it; any other sees only the schemas every
 * role sees ({@link SystemSchema#isSeenByEveryRole}) and the database roles PUBLIC holds
 * ({@link SystemDatabase#publicRoles}) — to its SQL references the rest do not exist, and its listings leave them out
 * (live-verified). The outermost statement names the sight for its whole run; a statement it runs in turn keeps it.
 * Where no statement has named one, as for the engine's own bookkeeping, everything is seen.
 */
public final class SystemDatabaseSight {

    private static final ThreadLocal<Boolean> PARTIAL = new ThreadLocal<Boolean>();

    private SystemDatabaseSight() {
    }

    /** @return whether a statement on this thread has named its sight */
    public static boolean installed() {
        return PARTIAL.get() != null;
    }

    /**
     * Names the running statement's sight.
     *
     * @param partial whether the statement sees only the part of the database every role sees
     * @return the sight named before, to give back to {@link #end}
     */
    public static Boolean begin(final boolean partial) {
        final Boolean displaced = PARTIAL.get();
        PARTIAL.set(Boolean.valueOf(partial));
        return displaced;
    }

    /**
     * Gives back the sight named before a statement's.
     *
     * @param displaced what {@link #begin} answered
     */
    public static void end(final Boolean displaced) {
        if (displaced == null) {
            PARTIAL.remove();
        } else {
            PARTIAL.set(displaced);
        }
    }

    /**
     * Whether the running statement does not see a schema of the SNOWFLAKE database.
     *
     * @param schemaName the schema's stored name
     * @return whether it is a system schema the statement's roles do not see
     */
    public static boolean hidesSchema(final String schemaName) {
        if (!Boolean.TRUE.equals(PARTIAL.get())) {
            return false;
        }
        final SystemSchema system = SystemSchema.of(SystemDatabase.NAME, schemaName);
        return system != null && !system.isSeenByEveryRole();
    }

    /**
     * Whether the running statement does not see a database role of the SNOWFLAKE database.
     *
     * @param roleName the database role's stored name
     * @return whether it is one of the database's own roles that PUBLIC does not hold
     */
    public static boolean hidesRole(final String roleName) {
        return Boolean.TRUE.equals(PARTIAL.get()) && SystemDatabase.roles().contains(roleName)
            && !SystemDatabase.publicRoles().contains(roleName);
    }
}
