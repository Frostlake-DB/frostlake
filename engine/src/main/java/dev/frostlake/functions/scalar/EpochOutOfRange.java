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

import dev.frostlake.executor.SessionZone;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A number of seconds since the epoch past the timestamps there are: before -999999999-01-01 00:00:00 or after
 * 999999999-12-31 23:59:59. Live refuses it naming the seconds, "Timestamp '(seconds_since_epoch=100000000000000000)'
 * is not recognized", and for a TIMESTAMP_TZ adds the session zone's offset there as minutes past -24:00,
 * "...=100000000000000000)tz=1020' ..." under Los Angeles in summer (live-verified).
 */
public final class EpochOutOfRange extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The seconds of four hundred Gregorian years, after which dates and weekdays repeat. */
    private static final BigInteger CYCLE_SECONDS = BigInteger.valueOf(12_622_780_800L);

    /** The first second past the last timestamp, and the first one there is. */
    static final long LAST_SECOND = 31_556_889_832_780_799L;
    static final long FIRST_SECOND = -31_557_014_135_596_800L;

    private static final int MINUTES_PER_DAY = 1440;

    private final BigInteger seconds;

    /**
     * The refusal of a number of seconds.
     *
     * @param seconds the whole seconds since the epoch
     */
    EpochOutOfRange(final BigInteger seconds) {
        super(sentence(seconds, ""));
        this.seconds = seconds;
    }

    private EpochOutOfRange(final BigInteger seconds, final String zone) {
        super(sentence(seconds, zone));
        this.seconds = seconds;
    }

    private static String sentence(final BigInteger seconds, final String zone) {
        return "Timestamp '(seconds_since_epoch=" + seconds + ")" + zone + "' is not recognized";
    }

    /**
     * The same refusal as a TIMESTAMP_TZ words it: with the session zone's offset at that instant, read four
     * hundred years at a time.
     *
     * @return the refusal
     */
    public EpochOutOfRange withSessionOffset() {
        final ZoneId zone = SessionZone.current();
        final long inCycle = seconds.mod(CYCLE_SECONDS).longValue() + 2 * CYCLE_SECONDS.longValue();
        final int minutes = zone.getRules().getOffset(Instant.ofEpochSecond(inCycle)).getTotalSeconds() / 60;
        return new EpochOutOfRange(seconds, "tz=" + String.format(Locale.ROOT, "%04d", MINUTES_PER_DAY + minutes));
    }
}
