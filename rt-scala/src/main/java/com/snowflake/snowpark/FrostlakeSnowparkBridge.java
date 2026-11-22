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
 * How Frostlake's Scala runtime hands a handler the Scala API's objects and takes them back: the Scala
 * {@link Session} over the Java API's session a procedure is called with, and the Java API's DataFrame under the
 * Scala {@link DataFrame} a table procedure's handler returns. Frostlake's own; no account has it.
 */
public final class FrostlakeSnowparkBridge {

    private FrostlakeSnowparkBridge() {
    }

    /**
     * @param session the Java API's session
     * @return the Scala API's session over it
     */
    public static Session session(final com.snowflake.snowpark_java.Session session) {
        return new Session(session);
    }

    /**
     * @param returned what a handler returned
     * @return the Java API's DataFrame under a Scala DataFrame, anything else as it is
     */
    public static Object javaResult(final Object returned) {
        return returned instanceof DataFrame ? ((DataFrame) returned).frame() : returned;
    }
}
