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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server stops as soon as the requests it is answering are answered: with none open it does not wait, and an open
 * one holds the stop until it ends or the grace runs out.
 */
public class InFlightRequestsTest {

    @Test
    public void aStopWaitsOnlyForTheRequestsInFlight() throws InterruptedException {
        final InFlightRequests inFlight = new InFlightRequests();
        assertTrue(inFlight.awaitNone(0));
        inFlight.begin();
        assertFalse(inFlight.awaitNone(1));
        final Thread answering = new Thread(new Runnable() {
            @Override
            public void run() {
                inFlight.end();
            }
        });
        answering.start();
        answering.join();
        assertTrue(inFlight.awaitNone(0));
    }
}
