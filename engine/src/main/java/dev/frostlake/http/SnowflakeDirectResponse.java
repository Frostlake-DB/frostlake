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

/** A response of a {@link SnowflakeDirectEndpoint}: what the endpoint would send over HTTP. */
public final class SnowflakeDirectResponse {

    private final int status;
    private final String contentType;
    private final byte[] body;

    SnowflakeDirectResponse(final int status, final String contentType, final byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    /**
     * The HTTP status: 200 for every answer a driver reads, a refusal included; 404 for a path the endpoint does not
     * serve.
     *
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    /**
     * The body's content type.
     *
     * @return {@value SnowflakeDirectEndpoint#CONTENT_TYPE}
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * The body: a JSON document in UTF-8. The array is the response's own, not a copy.
     *
     * @return the body
     */
    public byte[] getBody() {
        return body;
    }
}
