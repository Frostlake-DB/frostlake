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

/**
 * The requests a server is answering, so it stops as soon as they are answered rather than after a fixed grace: a
 * listener's own stop waits out its whole delay on some JDKs even with no exchange open.
 */
final class InFlightRequests {

    private int open;

    /** A request has begun. */
    synchronized void begin() {
        open++;
    }

    /** A request has been answered. */
    synchronized void end() {
        open--;
        if (open == 0) {
            notifyAll();
        }
    }

    /**
     * Wait until no request is open, for at most {@code millis}.
     *
     * @param millis the longest wait
     * @return true when none is open
     */
    synchronized boolean awaitNone(final long millis) {
        final long deadline = System.currentTimeMillis() + millis;
        while (open > 0) {
            final long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return false;
            }
            try {
                wait(left);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return open == 0;
            }
        }
        return true;
    }
}
