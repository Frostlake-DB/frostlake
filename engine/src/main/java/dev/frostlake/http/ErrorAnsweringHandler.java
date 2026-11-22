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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers a request whose handler failed without answering it. A fault that escapes a handler, whether a
 * RuntimeException it did not expect or an Error such as a class that cannot be loaded, would otherwise
 * end the server's pool thread with the exchange still open. The client would then wait out its own
 * timeout for an answer that never comes. Here it gets a 500 at once or, when the handler had already
 * begun its response, the end of the exchange.
 */
public final class ErrorAnsweringHandler implements HttpHandler {

    /** The 500's body: a fixed text, so answering needs nothing that could itself fail to load. */
    static final String FAULT_BODY = "{\"success\":false,\"errorMessage\":\"Internal server error\"}";

    private static final Logger logger = LoggerFactory.getLogger(ErrorAnsweringHandler.class);

    private final HttpHandler delegate;
    private final RequestGuard guard;
    private final boolean jsonBody;
    /** The most bytes a request body may hold, or 0 for no bound. */
    private final long maxRequestBytes;

    public ErrorAnsweringHandler(final HttpHandler delegate) {
        this(delegate, null, false, 0L);
    }

    /**
     * @param delegate the handler
     * @param guard what a request is held to before the handler sees it, or null for nothing
     * @param jsonBody whether the handler takes a JSON body and no other
     * @param maxRequestBytes the most bytes a request body may hold, or 0 for no bound: a larger one is answered 413
     */
    ErrorAnsweringHandler(final HttpHandler delegate, final RequestGuard guard, final boolean jsonBody,
                          final long maxRequestBytes) {
        this.delegate = delegate;
        this.guard = guard;
        this.jsonBody = jsonBody;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        try {
            if (guard != null && guard.answered(exchange, jsonBody)) {
                return;
            }
            if (maxRequestBytes > 0) {
                // A body declared larger than the bound is refused before it is read; one that turns out larger as it
                // is read stops being read there (see BoundedRequestStream).
                exchange.setStreams(new BoundedRequestStream(exchange.getRequestBody(), maxRequestBytes), null);
                if (declaredLength(exchange) > maxRequestBytes) {
                    tooLarge(exchange, new RequestTooLargeException(maxRequestBytes));
                    return;
                }
            }
            delegate.handle(exchange);
        } catch (final RequestTooLargeException tooLarge) {
            if (exchange.getResponseCode() == -1) {
                tooLarge(exchange, tooLarge);
            } else {
                exchange.close();
            }
        } catch (final RuntimeException | Error fault) {
            logger.error("Unanswered fault handling {} {}", exchange.getRequestMethod(), exchange.getRequestURI(),
                fault);
            if (exchange.getResponseCode() == -1) {
                HttpResponses.send(exchange, 500, FAULT_BODY);
            } else {
                exchange.close();
            }
        }
    }

    /** The body length a request declares, or -1 where it declares none or one that cannot be read. */
    private static long declaredLength(final HttpExchange exchange) {
        final String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length == null) {
            return -1L;
        }
        try {
            return Long.parseLong(length.trim());
        } catch (final NumberFormatException unreadable) {
            return -1L;
        }
    }

    /** Answers 413: the body is larger than the server takes. */
    private static void tooLarge(final HttpExchange exchange, final RequestTooLargeException tooLarge)
            throws IOException {
        HttpResponses.tooLarge(exchange, 413, "{\"success\":false,\"errorMessage\":\""
            + tooLarge.getMessage() + "\"}");
    }
}
