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

/**
 * What one REVOKE did to the grant instances it named (see {@link DependentGrants}): how many it took back and how
 * many it skipped because grants made through their option stand and no CASCADE was written. A statement that
 * skipped any reports itself partially executed once it has revoked the rest (live-verified).
 */
final class RevokeTally {

    private int revoked;
    private int skipped;

    /** Counts instances taken back. */
    void revoked(final int count) {
        revoked += count;
    }

    /** Counts one instance skipped for its dependent grants. */
    void skip() {
        skipped++;
    }

    /**
     * Refuses the statement, after what it revoked, when it skipped an instance: {@code Revoke partially executed:
     * N grant(s) revoked, M grant(s) skipped due to dependent grants and no CASCADE is specified.}
     */
    void requireNoneSkipped() {
        if (skipped > 0) {
            throw new RuntimeException("Revoke partially executed: " + revoked + " grant(s) revoked, " + skipped
                + " grant(s) skipped due to dependent grants and no CASCADE is specified.");
        }
    }
}
