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
package com.snowflake.snowpark;

/**
 * The Scala Snowpark session a LANGUAGE SCALA procedure's handler receives when it declares its first parameter as
 * {@code com.snowflake.snowpark.Session}: the statements it runs go through the session of the Java API
 * ({@code com.snowflake.snowpark_java.Session}) the procedure was called with.
 */
public final class Session {

    private final com.snowflake.snowpark_java.Session session;

    Session(final com.snowflake.snowpark_java.Session session) {
        this.session = session;
    }

    /**
     * A DataFrame over a statement's rows.
     *
     * @param query the statement's text
     * @return the DataFrame
     */
    public DataFrame sql(final String query) {
        return new DataFrame(session.sql(query));
    }

    /**
     * A DataFrame over a table's rows.
     *
     * @param name the table's name
     * @return the DataFrame
     */
    public DataFrame table(final String name) {
        return new DataFrame(session.table(name));
    }
}
