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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/** A handler whose requests the server counts while they run (see {@link InFlightRequests}). */
final class CountedHandler implements HttpHandler {

    private final HttpHandler delegate;
    private final InFlightRequests inFlight;

    /**
     * @param delegate the handler
     * @param inFlight the server's count
     */
    CountedHandler(final HttpHandler delegate, final InFlightRequests inFlight) {
        this.delegate = delegate;
        this.inFlight = inFlight;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        inFlight.begin();
        try {
            delegate.handle(exchange);
        } finally {
            inFlight.end();
        }
    }
}
