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
package dev.frostlake;

/**
 * What the engine did with one statement a {@link SnowflakeJdbcFlEngine}'s endpoint ran for the driver, recorded on
 * the server's thread: the text it ran, its result or its failure, and the session's context after it — the database,
 * the schema, the role and AUTOCOMMIT — which the test's thread then reads in place of the driver's own view.
 */
final class ServedStatement {

    private final String sql;
    private final ExecutionResult result;
    private final RuntimeException failure;
    private final String database;
    private final String schema;
    private final String role;
    private final boolean autoCommit;

    ServedStatement(final String sql, final ExecutionResult result, final RuntimeException failure,
                    final String database, final String schema, final String role, final boolean autoCommit) {
        this.sql = sql;
        this.result = result;
        this.failure = failure;
        this.database = database;
        this.schema = schema;
        this.role = role;
        this.autoCommit = autoCommit;
    }

    /** @return the text the engine ran */
    String sql() {
        return sql;
    }

    /** @return the engine's result, or null when it refused the statement */
    ExecutionResult result() {
        return result;
    }

    /** @return the engine's refusal, or null */
    RuntimeException failure() {
        return failure;
    }

    /** @return the session's database after the statement */
    String database() {
        return database;
    }

    /** @return the session's schema after the statement */
    String schema() {
        return schema;
    }

    /** @return the session's primary role after the statement */
    String role() {
        return role;
    }

    /** @return the session's AUTOCOMMIT after the statement */
    boolean autoCommit() {
        return autoCommit;
    }
}
