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

package dev.frostlake.executor;

import dev.frostlake.metastore.model.Stage;

/**
 * A named stage's storage id: the folder the account keeps the stage's files under, {@code stages/<id>/}. A
 * refusal naming a staged file, a PATTERN matched against a file's internal path and a directory refresh all name
 * that folder. The id has the shape of a version-4 UUID; it is drawn the first time it is needed, a rename keeps
 * it and a replaced stage draws another (live-verified).
 */
public final class StageStorageId {

    private static final String HEX = "0123456789abcdef";
    private static final String VARIANT = "89ab";

    private StageStorageId() {
    }

    /**
     * The stage's id, drawn the first time it is asked for.
     *
     * @param stage the stage
     * @return its id
     */
    public static String of(final Stage stage) {
        if (stage.getDirectoryId() == null) {
            final String hex = StatementTokens.draw(HEX, 32);
            final String variant = StatementTokens.draw(VARIANT, 1);
            stage.setDirectoryId(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-4" + hex.substring(13, 16) + "-"
                + variant + hex.substring(17, 20) + "-" + hex.substring(20));
        }
        return stage.getDirectoryId();
    }
}
