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


package dev.frostlake.functions;

import dev.frostlake.executor.SessionZone;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.WideOffsetZone;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The carrier of a TIMESTAMP_TZ offset wider than a zone offset holds: it keeps the instant, the wall clock and the
 * offset written through Java serialization, which the write-ahead log and the table files use, and none of its
 * zones is a name a session parameter takes.
 */
public class WideOffsetZoneTest {

    @Test
    public void keepsWallClockOffsetAndInstant() {
        final ZonedDateTime wide = WideOffsetZone.at(LocalDateTime.of(2024, 1, 5, 10, 0), 25 * 60 + 30);
        assertEquals(LocalDateTime.of(2024, 1, 5, 10, 0), WideOffsetZone.wallClock(wide));
        assertEquals((25 * 60 + 30) * 60, WideOffsetZone.offsetSeconds(wide));
        assertEquals(LocalDateTime.of(2024, 1, 4, 8, 30), wide.toLocalDateTime());
        assertEquals("2024-01-05 10:00:00.000 +2530", SharedFunctionHelpers.writtenOffsetTimestampText(wide));
        final ZonedDateTime negative = WideOffsetZone.at(LocalDateTime.of(2024, 1, 5, 10, 0), -(19 * 60));
        assertEquals("2024-01-05 10:00:00.000 -1900", SharedFunctionHelpers.writtenOffsetTimestampText(negative));
    }

    @Test
    public void survivesSerialization() throws IOException, ClassNotFoundException {
        final ZonedDateTime wide = WideOffsetZone.at(LocalDateTime.of(2024, 1, 5, 10, 0, 0, 123_000_000), 99 * 60 + 59);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(wide);
        }
        final Object read;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = in.readObject();
        }
        assertEquals(wide, read);
        assertEquals("2024-01-05 10:00:00.123 +9959",
            SharedFunctionHelpers.writtenOffsetTimestampText((ZonedDateTime) read));
    }

    @Test
    public void zonesAreNoSessionZoneNames() {
        final String name = WideOffsetZone.at(LocalDateTime.of(2024, 1, 5, 10, 0), 25 * 60).getZone().getId();
        assertTrue(WideOffsetZone.isWideName(name));
        assertFalse(SessionZone.knows(name));
        assertTrue(SessionZone.knows("Europe/Warsaw"));
    }
}
