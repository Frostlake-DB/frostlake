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

import java.time.Instant;

/**
 * One more instance of a grant a grantee already holds, made by another grantor (see {@link GrantLedger}): when it
 * was made and whether it carries the grant option. A privilege can be granted to one grantee several times by
 * different grantors, each instance listed and revoked on its own (documented).
 */
final class GrantInstance {

    private Instant granted;
    private boolean option;

    /**
     * @param granted when the instance was made
     */
    GrantInstance(final Instant granted) {
        this.granted = granted;
    }

    Instant getGranted() {
        return granted;
    }

    void setGranted(final Instant granted) {
        this.granted = granted;
    }

    boolean hasOption() {
        return option;
    }

    void setOption(final boolean option) {
        this.option = option;
    }

    /** A copy, for a ledger that takes another's grants. */
    GrantInstance copy() {
        final GrantInstance copy = new GrantInstance(granted);
        copy.option = option;
        return copy;
    }
}
