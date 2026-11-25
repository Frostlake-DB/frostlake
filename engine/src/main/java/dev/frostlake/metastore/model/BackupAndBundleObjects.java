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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The backup policies and code bundles a schema holds, each keyed by canonical name. They drop with their schema.
 */
public final class BackupAndBundleObjects {

    private final Map<String, BackupPolicy> backupPolicies = new TreeMap<>();
    private final Map<String, CodeBundle> codeBundles = new TreeMap<>();

    /** The backup policy of that name, or null. */
    public synchronized BackupPolicy getBackupPolicy(final String name) {
        return backupPolicies.get(name);
    }

    /** Adds or replaces a backup policy. */
    public synchronized void putBackupPolicy(final BackupPolicy policy) {
        backupPolicies.put(policy.getName(), policy);
    }

    /** Removes a backup policy; whether it existed. */
    public synchronized boolean removeBackupPolicy(final String name) {
        return backupPolicies.remove(name) != null;
    }

    /** Every backup policy, by name. */
    public synchronized List<BackupPolicy> getBackupPolicies() {
        return new ArrayList<>(backupPolicies.values());
    }

    /** The code bundle of that name, or null. */
    public synchronized CodeBundle getCodeBundle(final String name) {
        return codeBundles.get(name);
    }

    /** Adds or replaces a code bundle. */
    public synchronized void putCodeBundle(final CodeBundle bundle) {
        codeBundles.put(bundle.getName(), bundle);
    }

    /** Removes a code bundle; whether it existed. */
    public synchronized boolean removeCodeBundle(final String name) {
        return codeBundles.remove(name) != null;
    }

    /** Every code bundle, by name. */
    public synchronized List<CodeBundle> getCodeBundles() {
        return new ArrayList<>(codeBundles.values());
    }
}
