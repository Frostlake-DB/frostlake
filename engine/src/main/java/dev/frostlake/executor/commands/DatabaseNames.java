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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;

/**
 * The database a statement names, read from its name's parts: a name of one part, or of two whose first is this
 * account's locator. The account refuses any other name of two or three parts with its generic sentence, and one of
 * four or more as a database identifier with too many qualifiers — CREATE DATABASE, a database CLONE's source, DROP,
 * UNDROP, ALTER and COMMENT ON DATABASE alike, written or given through IDENTIFIER(), IF EXISTS forgiving neither
 * (live-verified). A quoted name holding a dot is one part, and names the database it spells.
 */
final class DatabaseNames {

    private DatabaseNames() {
    }

    /**
     * The database's own name.
     *
     * @param parts   the name's canonical parts
     * @param catalog the catalog that knows this account's locator
     * @return the database's name
     */
    static String of(final String[] parts, final Catalog catalog) {
        if (parts.length > 3) {
            throw new RuntimeException(SqlCompilationError.inline("database identifier '"
                + SqlIdentifiers.spellAlreadyCanonicalPath(QualifiedName.join(parts)) + "' has too many qualifiers"));
        }
        return catalog.withoutAccount(parts, 1)[0];
    }
}
