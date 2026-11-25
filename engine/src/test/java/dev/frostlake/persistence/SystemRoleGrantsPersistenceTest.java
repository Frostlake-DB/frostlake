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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The privileges the system roles hold on the account from the start: a fresh engine holds all 22, and one a REVOKE
 * took back stays revoked across a restart, whether a snapshot or a write-ahead log's checkpoint and replay brings
 * the catalog back.
 */
public class SystemRoleGrantsPersistenceTest {

    private static final String ACCOUNT_GRANTS = "SHOW GRANTS ON ACCOUNT";

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("system_role_grants_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private DatabaseEngine snapshotEngine(final Path data) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, data.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
        return new DatabaseEngine(config);
    }

    private DatabaseEngine walEngine(final Path walFile) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, walFile.toString());
        config.setProperty(EngineConfig.PROP_DURABILITY_CHECKPOINT_INTERVAL, "0");
        return new DatabaseEngine(config);
    }

    /** The system roles' grants on the account, each as {@code GRANTEE:PRIVILEGE}. */
    private static List<String> systemGrants(final DatabaseEngine engine) {
        final ResultSet rs = engine.executeQuery(ACCOUNT_GRANTS);
        final int grantee = rs.getColumnIndex("grantee_name");
        final int privilege = rs.getColumnIndex("privilege");
        final int grantor = rs.getColumnIndex("granted_by");
        final List<String> grants = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            if ("".equals(row.getValue(grantor))) {
                grants.add(row.getValue(grantee) + ":" + row.getValue(privilege));
            }
        }
        return grants;
    }

    /** Revokes two of the grants: one of SYSADMIN's and one of SECURITYADMIN's. */
    private static void revokeTwo(final DatabaseEngine engine) {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("REVOKE CREATE WAREHOUSE ON ACCOUNT FROM ROLE SYSADMIN");
        engine.execute("REVOKE APPLY SESSION POLICY ON ACCOUNT FROM ROLE SECURITYADMIN");
    }

    private static void assertRevoked(final List<String> grants) {
        assertEquals(20, grants.size(), grants.toString());
        assertFalse(grants.contains("SYSADMIN:CREATE WAREHOUSE"), grants.toString());
        assertFalse(grants.contains("SECURITYADMIN:APPLY SESSION POLICY"), grants.toString());
        assertTrue(grants.contains("SYSADMIN:CREATE DATABASE"), grants.toString());
    }

    @Test
    public void aFreshEngineHoldsAll() {
        final DatabaseEngine engine = new DatabaseEngine();
        try {
            final List<String> grants = systemGrants(engine);
            assertEquals(22, grants.size(), grants.toString());
            assertTrue(grants.contains("USERADMIN:CREATE USER"), grants.toString());
            assertTrue(grants.contains("SECURITYADMIN:MANAGE GRANTS"), grants.toString());
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aRevokeSurvivesASnapshot() {
        final Path data = dir.resolve("snapshot");
        final DatabaseEngine first = snapshotEngine(data);
        revokeTwo(first);
        assertRevoked(systemGrants(first));
        first.shutdown();
        final DatabaseEngine second = snapshotEngine(data);
        try {
            assertRevoked(systemGrants(second));
        } finally {
            second.shutdown();
        }
        final DatabaseEngine fresh = snapshotEngine(dir.resolve("fresh"));
        try {
            assertEquals(22, systemGrants(fresh).size());
        } finally {
            fresh.shutdown();
        }
    }

    @Test
    public void aRevokeSurvivesTheLogsReplay() {
        final Path walFile = dir.resolve("replay").resolve("wal.log");
        final DatabaseEngine first = walEngine(walFile);
        revokeTwo(first);
        first.shutdown();
        final DatabaseEngine second = walEngine(walFile);
        try {
            assertRevoked(systemGrants(second));
        } finally {
            second.shutdown();
        }
    }

    @Test
    public void aRevokeSurvivesTheLogsCheckpoint() {
        final Path walFile = dir.resolve("checkpoint").resolve("wal.log");
        final DatabaseEngine first = walEngine(walFile);
        revokeTwo(first);
        first.checkpoint();
        first.shutdown();
        final DatabaseEngine second = walEngine(walFile);
        try {
            assertRevoked(systemGrants(second));
        } finally {
            second.shutdown();
        }
    }
}
