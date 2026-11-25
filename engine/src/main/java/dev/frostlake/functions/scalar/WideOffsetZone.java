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

package dev.frostlake.functions.scalar;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.time.zone.ZoneRulesException;
import java.time.zone.ZoneRulesProvider;
import java.util.Locale;

/**
 * A TIMESTAMP_TZ offset wider than a zone offset holds. A TIMESTAMP_TZ keeps any offset of two-digit hours as
 * written - {@code '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ} reads back {@code 2024-01-05 10:00:00.000 +2500}
 * (live-verified) - while a {@link ZoneOffset} spans 18 hours at most. Such a value is a {@link ZonedDateTime}
 * whose zone keeps the instant at UTC and whose name keeps the offset written, so everything that reads the
 * instant (comparison, ordering, conversion to TIMESTAMP_LTZ, epochs) reads it unchanged, and what reads the
 * wall clock or the offset asks this class for them.
 */
public final class WideOffsetZone {

    /** The narrowest offset, in minutes, a zone offset cannot hold. */
    static final int NARROWEST_WIDE_MINUTES = 18 * 60 + 1;

    /** The widest offset, in minutes, two-digit hours and minutes spell. */
    static final int WIDEST_MINUTES = 99 * 60 + 99;

    /** The family of the zone names that carry a wide offset. */
    private static final String PREFIX = "Frostlake/WideOffset";

    static {
        try {
            ZoneRulesProvider.registerProvider(new WideOffsetZoneRules());
        } catch (final ZoneRulesException alreadyRegistered) {
            // Another class loader registered the same zones; they are equally usable from here.
        }
    }

    private WideOffsetZone() {
    }

    /**
     * The zone name carrying an offset.
     *
     * @param minutes the offset, in minutes
     * @return the name
     */
    static String idOf(final int minutes) {
        final int absolute = Math.abs(minutes);
        return String.format(Locale.ROOT, "%s%s%02d%02d", PREFIX, minutes < 0 ? "-" : "+", absolute / 60,
            absolute % 60);
    }

    /**
     * Whether a zone name is one of the wide-offset zones, which no session parameter or function argument names.
     *
     * @param name the name
     * @return whether it is
     */
    public static boolean isWideName(final String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /**
     * Whether a zone carries a wide offset.
     *
     * @param zone the zone
     * @return whether it does
     */
    public static boolean isWide(final ZoneId zone) {
        return zone != null && isWideName(zone.getId());
    }

    /**
     * The offset a TIMESTAMP_TZ value was written with, in seconds.
     *
     * @param value the value
     * @return the offset
     */
    public static int offsetSeconds(final ZonedDateTime value) {
        final ZoneId zone = value.getZone();
        if (!isWide(zone)) {
            return value.getOffset().getTotalSeconds();
        }
        final String spelled = zone.getId().substring(PREFIX.length());
        final int digits = Integer.parseInt(spelled.substring(1));
        final int minutes = digits / 100 * 60 + digits % 100;
        return (spelled.charAt(0) == '-' ? -minutes : minutes) * 60;
    }

    /**
     * The wall clock a TIMESTAMP_TZ value was written with.
     *
     * @param value the value
     * @return the wall clock
     */
    public static LocalDateTime wallClock(final ZonedDateTime value) {
        if (!isWide(value.getZone())) {
            return value.toLocalDateTime();
        }
        return value.toLocalDateTime().plusSeconds(offsetSeconds(value));
    }

    /**
     * The wall clock a parsed value names: a wide-offset value's own, any other's as {@link LocalDateTime#from}
     * reads it.
     *
     * @param read the parsed value
     * @return the wall clock
     */
    public static LocalDateTime wallClock(final TemporalAccessor read) {
        if (read instanceof ZonedDateTime) {
            return wallClock((ZonedDateTime) read);
        }
        return LocalDateTime.from(read);
    }

    /**
     * A wall clock written with an offset of minutes, as the TIMESTAMP_TZ it names: within a zone offset's span at
     * that offset, past it at a wide-offset zone, and past two-digit hours at UTC with the instant kept.
     *
     * @param wall    the wall clock
     * @param minutes the offset, in minutes
     * @return the value
     */
    public static ZonedDateTime at(final LocalDateTime wall, final int minutes) {
        final int absolute = Math.abs(minutes);
        if (absolute < NARROWEST_WIDE_MINUTES) {
            return wall.atOffset(ZoneOffset.ofTotalSeconds(minutes * 60)).toZonedDateTime();
        }
        if (absolute > WIDEST_MINUTES) {
            return wall.minusMinutes(minutes).atZone(ZoneOffset.UTC);
        }
        return wall.minusMinutes(minutes).atZone(ZoneId.of(idOf(minutes)));
    }

    /**
     * A wall clock at the offset a TIMESTAMP_TZ value carries, keeping a wide offset as well as a narrow one.
     *
     * @param wall   the wall clock
     * @param source the value whose offset it takes
     * @return the value
     */
    public static ZonedDateTime atOffsetOf(final LocalDateTime wall, final ZonedDateTime source) {
        if (!isWide(source.getZone())) {
            return wall.atZone(source.getOffset());
        }
        return wall.minusSeconds(offsetSeconds(source)).atZone(source.getZone());
    }
}
