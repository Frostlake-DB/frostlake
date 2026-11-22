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
package dev.frostlake.http;

/** The answer of {@link SnowflakeProtocolHandler} to a request: an HTTP status and a JSON document. */
final class SnowflakeReply {

    private final int status;
    private final String json;

    SnowflakeReply(final int status, final String json) {
        this.status = status;
        this.json = json;
    }

    int status() {
        return status;
    }

    String json() {
        return json;
    }
}
