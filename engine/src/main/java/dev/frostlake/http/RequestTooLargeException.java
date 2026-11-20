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

import java.io.IOException;

/**
 * A request body larger than the server takes ({@code http.maxRequestBytes}), as sent or once unzipped: answered 413,
 * nothing of it run.
 */
public final class RequestTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * @param limit the most bytes a body may hold
     */
    public RequestTooLargeException(final long limit) {
        super("The request body exceeds the " + limit + " bytes the server takes (http.maxRequestBytes).");
    }
}
