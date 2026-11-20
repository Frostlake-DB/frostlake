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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formats a temporal value with Snowflake's date/time format model (TO_CHAR / TO_VARCHAR with a date
 * format): YYYY/UUUU/YY/Y, MMMM/MON/MM/MO, DD/DY/D, HH24/HH12/H24/H12/HH/H, MI/ME, SS/S, FF[0-9], AM/PM/P,
 * TZH/TZM pairs, TZD. The scan is greedy longest-match at each position; any UNMATCHED character is literal
 * text, so separators (-, /, :, spaces) pass through unchanged. There is no MMM element: 'MMM' is MM and a
 * literal M. Snowflake has NO full-name 'DAY'/'MONTH' elements (live-verified on DATE '2020-01-15'): 'DAY'
 * tokenizes as D+A+Y — unpadded day-of-month, literal A, 2-digit year — giving "15A20", and 'MONTH' as
 * MON+T+H — "Jan", literal T, unpadded hour — giving "JanT0". Month/day names use the English (US) locale in
 * title case (e.g. "Jan", "January", "Wed"); the format element's own case is not propagated to the output.
 * TZH/TZM render the NTZ zero offset ("Z"). A TIME renders as a time on 1970-01-01. Not modeled: HH is
 * treated as 24-hour, and era / quarter / week elements.
 *
 * <p>★ ADJACENT ELEMENTS OF ONE FIELD RENDER AS ONE (live-verified). Each element stands for a field and a
 * width — DD is the day at two, D the day at one, MON the month at three, MMMM at four, DY the weekday at
 * three — and elements of one field with no text between them add their widths: 'DDD' is the day at three
 * (005), 'HH24H' the hour at three (013), 'YYYYYY' the year at six (002024), 'MONMM' the month at five, which
 * like any month of four or more is the full name (March), and 'DYDY' the full weekday (Tuesday). A run of
 * meridiem elements prints one meridiem ('AMPM' is PM), a run of fraction elements prints the last one's
 * digits ('FF9FF1' is one digit), and offset elements join only up to the width of TZH:TZM ('TZHTZHTZM' is
 * +05:30, 'TZH:TZMTZH' +05:30+05). A format holding Y, P or UUUU joins nothing: every element renders on its
 * own there ('DDD Y' is 055 24, 'MONMM Y' Mar03 24).
 */
public final class SnowflakeDateFormat {

    private SnowflakeDateFormat() {
    }

    /** The year's field. */
    private static final char YEAR = 'y';
    /** The UUUU element's field, which joins nothing. */
    private static final char PROLEPTIC_YEAR = 'u';
    /** The Y element's field: the year's last two digits unpadded; it joins nothing. */
    private static final char SHORT_YEAR = 'Y';
    private static final char MONTH = 'M';
    private static final char DAY = 'd';
    private static final char WEEKDAY = 'E';
    private static final char HOUR = 'H';
    private static final char CLOCK_HOUR = 'h';
    private static final char MINUTE = 'm';
    private static final char SECOND = 's';
    private static final char FRACTION = 'F';
    private static final char MERIDIEM = 'a';
    /** The P element's field: the meridiem's letter, A or P; it joins nothing. */
    private static final char MERIDIEM_LETTER = 'P';
    private static final char OFFSET = 'X';
    private static final char ZONE_NAME = 'Z';

    /** The widest run the offset elements join into: TZH:TZM. */
    private static final int WIDEST_OFFSET = 3;

    // Ordered longest-first so the greedy scan matches MMMM before MON before MM, HH24 before HH
    // before H, DD/DY before D, YYYY before YY before Y, etc. Each element names its field and width.
    private static final String[][] TOKENS = {
        // The offset elements, which render the value's OWN offset — a zero one as the ISO "Z", which is
        // what a TIMESTAMP_NTZ always shows. Live: TZH:TZM over an LTZ under America/Los_Angeles is
        // -08:00, over a TIMESTAMP_TZ written +0530 it is +05:30, and over an NTZ it is Z. TZH alone is the
        // hours only: +05 for +05:30.
        //
        // ★ A BARE TZM IS NOT AN ELEMENT. Live renders the format 'TZM' as the literal text TZM, so it
        // is absent from this table on purpose and falls through to the literal path; only the pair
        // (with or without the colon) and TZH alone are read.
        {"TZH:TZM", "X", "3"}, {"TZHTZM", "X", "2"}, {"TZH", "X", "1"},
        // ★ TZD IS THE STANDARD-TIME ABBREVIATION, NOT THE INSTANT'S (live-verified across eleven
        // session zones in January and July): an LTZ under America/Los_Angeles renders PST in July
        // too — never PDT, never BST, never a daylight name of any zone. A TIMESTAMP_TZ has no zone,
        // only its written offset, and spells it GMT±HH:MM — the zero offset included, GMT+00:00 —
        // while a value with no zone at all (NTZ, DATE) says the bare GMT. A session zone with no
        // English abbreviation (Etc/GMT+5) uses the spelled-offset form as well.
        {"TZD", "Z", "0"},
        // ★ YYYY PRINTS THE DIGITS OF A YEAR PAST 9999 (20201), never a sign.
        {"YYYY", "y", "4"}, {"UUUU", "u", "4"}, {"HH24", "H", "2"}, {"HH12", "h", "2"},
        // The unpadded elements (live-verified on 2005-04-05 17:08:09): H24:ME:S is 17:8:9, H12 is 5, MO is 4.
        {"H24", "H", "1"}, {"H12", "h", "1"}, {"MMMM", "M", "4"},
        {"FF9", "F", "9"}, {"FF8", "F", "8"}, {"FF7", "F", "7"}, {"FF6", "F", "6"},
        {"FF5", "F", "5"}, {"FF4", "F", "4"}, {"FF3", "F", "3"}, {"FF2", "F", "2"}, {"FF1", "F", "1"},
        // FF0 asks for no fraction digits: SS.FF0 is 09. (live-verified).
        {"FF0", "F", "0"},
        {"MON", "M", "3"},
        {"YY", "y", "2"}, {"MM", "M", "2"}, {"DD", "d", "2"}, {"DY", "E", "3"}, {"HH", "H", "2"},
        // A bare FF prints the value's scale, nine digits when it is not known.
        {"MI", "m", "2"}, {"SS", "s", "2"}, {"FF", "F", "-1"}, {"AM", "a", "1"}, {"PM", "a", "1"},
        {"MO", "M", "1"}, {"ME", "m", "1"},
        // Single-letter elements (live-verified): Y = the year's last two digits UNPADDED ('Y' on 2020-01-15 →
        // "20", on 2005-04-05 → "5", so 'YYY' is YY then Y, "055"), D = day-of-month UNPADDED ('D' → "15"),
        // H = hour UNPADDED (the H in 'MONTH' rendered "0" for a DATE), S = second UNPADDED, and P = the
        // meridiem's letter, A or P. These are what make 'DAY' and 'MONTH' come out as "15A20" / "JanT0".
        {"Y", "Y", "1"}, {"D", "d", "1"}, {"H", "H", "1"}, {"S", "s", "1"}, {"P", "P", "1"},
    };

    /** The Y element: the year's last two digits, unpadded. */
    private static final DateTimeFormatter SHORT_YEAR_FORMAT = new DateTimeFormatterBuilder()
        .appendValueReduced(ChronoField.YEAR, 1, 2, 2000).toFormatter(Locale.US);

    /** The UUUU element: the proleptic year, four digits at least and signed past them. */
    private static final DateTimeFormatter PROLEPTIC_YEAR_FORMAT = DateTimeFormatter.ofPattern("uuuu", Locale.US);

    // Format strings are query constants but format runs per ROW; the pieces are immutable.
    private static final int PIECES_CACHE_CAPACITY = 256;
    private static final Map<String, List<FormatPiece>> PIECES_CACHE = new ConcurrentHashMap<>();

    /**
     * Renders a temporal value under a format model.
     *
     * @param value           the value: a date, time or timestamp, or the text of one
     * @param snowflakeFormat the format model
     * @return the text
     */
    public static String format(final Object value, final String snowflakeFormat) {
        final TemporalAccessor temporal = toTemporal(value);
        final List<FormatPiece> scanned = cachedPieces(snowflakeFormat);
        final boolean joins = joinsElements(scanned);
        final StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < scanned.size()) {
            final FormatPiece piece = scanned.get(i);
            if (!piece.isElement()) {
                out.append(piece.literal());
                i++;
                continue;
            }
            final char field = fieldOf(piece.element());
            int end = i + 1;
            int width = widthOf(piece.element());
            if (joins && field != ZONE_NAME) {
                while (end < scanned.size() && scanned.get(end).isElement()
                        && fieldOf(scanned.get(end).element()) == field) {
                    width += widthOf(scanned.get(end).element());
                    end++;
                }
                if (field == OFFSET && width > WIDEST_OFFSET) {
                    end = i + 1;
                    width = widthOf(piece.element());
                }
            }
            if (field == FRACTION) {
                width = widthOf(scanned.get(end - 1).element());
            }
            out.append(render(field, width, temporal, value));
            i = end;
        }
        return out.toString();
    }

    /** Whether a format's adjacent elements of one field join: not when it holds Y, P or UUUU. */
    private static boolean joinsElements(final List<FormatPiece> scanned) {
        for (final FormatPiece piece : scanned) {
            if (piece.isElement()) {
                final char field = fieldOf(piece.element());
                if (field == SHORT_YEAR || field == MERIDIEM_LETTER || field == PROLEPTIC_YEAR) {
                    return false;
                }
            }
        }
        return true;
    }

    /** One field at one width. */
    private static String render(final char field, final int width, final TemporalAccessor temporal,
                                 final Object value) {
        switch (field) {
            case YEAR:
                final int year = temporal.get(ChronoField.YEAR_OF_ERA);
                return width == 2 ? padded(year % 100, 2) : padded(year, width);
            case PROLEPTIC_YEAR:
                return PROLEPTIC_YEAR_FORMAT.format(temporal);
            case SHORT_YEAR:
                return SHORT_YEAR_FORMAT.format(temporal);
            case MONTH:
                final int month = temporal.get(ChronoField.MONTH_OF_YEAR);
                if (width >= 4) {
                    return Month.of(month).getDisplayName(TextStyle.FULL, Locale.US);
                }
                return width == 3 ? Month.of(month).getDisplayName(TextStyle.SHORT, Locale.US) : padded(month, width);
            case DAY:
                return padded(temporal.get(ChronoField.DAY_OF_MONTH), width);
            case WEEKDAY:
                return DayOfWeek.of(temporal.get(ChronoField.DAY_OF_WEEK))
                    .getDisplayName(width >= 4 ? TextStyle.FULL : TextStyle.SHORT, Locale.US);
            case HOUR:
                return padded(temporal.get(ChronoField.HOUR_OF_DAY), width);
            case CLOCK_HOUR:
                return padded(temporal.get(ChronoField.CLOCK_HOUR_OF_AMPM), width);
            case MINUTE:
                return padded(temporal.get(ChronoField.MINUTE_OF_HOUR), width);
            case SECOND:
                return padded(temporal.get(ChronoField.SECOND_OF_MINUTE), width);
            case FRACTION:
                return fraction(temporal.get(ChronoField.NANO_OF_SECOND), width);
            case MERIDIEM:
                return temporal.get(ChronoField.AMPM_OF_DAY) == 0 ? "AM" : "PM";
            case MERIDIEM_LETTER:
                return temporal.get(ChronoField.AMPM_OF_DAY) == 0 ? "A" : "P";
            case OFFSET:
                return offset(temporal.get(ChronoField.OFFSET_SECONDS), width);
            default:
                return tzdText(value);
        }
    }

    /** A number zero-padded to a width. */
    private static String padded(final int number, final int width) {
        final String digits = Integer.toString(number);
        if (digits.length() >= width) {
            return digits;
        }
        final StringBuilder out = new StringBuilder(width);
        for (int i = digits.length(); i < width; i++) {
            out.append('0');
        }
        return out.append(digits).toString();
    }

    /** The fraction of a second at a number of digits, cut rather than rounded; a bare FF the value's scale. */
    private static String fraction(final int nanos, final int digits) {
        final int wanted = digits >= 0 ? digits : FractionScale.current() >= 0 ? FractionScale.current() : 9;
        return padded(nanos, 9).substring(0, Math.min(wanted, 9));
    }

    /** An offset: Z when zero, else the hours (TZH), hours and minutes (TZHTZM), or both with a colon. */
    private static String offset(final int seconds, final int width) {
        if (seconds == 0) {
            return "Z";
        }
        final int magnitude = Math.abs(seconds);
        final String hours = (seconds < 0 ? "-" : "+") + padded(magnitude / 3600, 2);
        if (width <= 1) {
            return hours;
        }
        return hours + (width == 2 ? "" : ":") + padded((magnitude % 3600) / 60, 2);
    }

    private static char fieldOf(final String element) {
        return tokenOf(element)[1].charAt(0);
    }

    private static int widthOf(final String element) {
        return Integer.parseInt(tokenOf(element)[2]);
    }

    private static String[] tokenOf(final String element) {
        for (final String[] token : TOKENS) {
            if (token[0].equals(element)) {
                return token;
            }
        }
        throw new IllegalArgumentException("Not a format element: " + element);
    }

    private static List<FormatPiece> cachedPieces(final String format) {
        final List<FormatPiece> existing = PIECES_CACHE.get(format);
        if (existing != null) {
            return existing;
        }
        final List<FormatPiece> scanned = pieces(format);
        if (PIECES_CACHE.size() >= PIECES_CACHE_CAPACITY) {
            final Iterator<String> it = PIECES_CACHE.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        PIECES_CACHE.put(format, scanned);
        return scanned;
    }

    /**
     * The TZD element's text for this value: the SESSION zone's standard abbreviation for an LTZ,
     * the value's own spelled offset for a TIMESTAMP_TZ, and GMT for a value that carries no zone.
     */
    private static String tzdText(final Object value) {
        if (value instanceof OffsetDateTime) {
            return standardShortName(SessionZone.current());
        }
        if (value instanceof ZonedDateTime) {
            final ZoneId zone = ((ZonedDateTime) value).getZone();
            if (zone instanceof ZoneOffset) {
                return spelledGmtOffset((ZoneOffset) zone);
            }
            return standardShortName(zone);
        }
        return "GMT";
    }

    /**
     * The zone's English standard-time abbreviation (PST, CET, JST — whatever the instant), with the
     * spelled GMT offset for a zone that has none, and UTC for the zero-offset zone itself.
     */
    private static String standardShortName(final ZoneId zone) {
        if (zone.normalized().equals(ZoneOffset.UTC)) {
            return "UTC";
        }
        return TimeZone.getTimeZone(zone).getDisplayName(false, TimeZone.SHORT, Locale.ENGLISH);
    }

    /** The offset spelled in the localized-GMT form, sign and minutes always written: GMT+05:30. */
    private static String spelledGmtOffset(final ZoneOffset offset) {
        final int total = offset.getTotalSeconds();
        final int magnitude = Math.abs(total);
        return String.format(Locale.ROOT, "GMT%s%02d:%02d", total < 0 ? "-" : "+",
            magnitude / 3600, (magnitude % 3600) / 60);
    }

    /**
     * Whether the format string contains at least one recognized date/time element — used to decide
     * whether a plain string value should be interpreted as a temporal (a DATE/TIMESTAMP value can
     * reach a function as its ISO string) rather than a numeric or literal value.
     */
    public static boolean isDateFormat(final String format) {
        int i = 0;
        while (i < format.length()) {
            if (matchToken(format, i) != null) {
                return true;
            }
            i++;
        }
        return false;
    }

    private static TemporalAccessor toTemporal(final Object v) {
        // The zoned values keep their own offset for the TZH / TZM elements; a naive one is widened to
        // a ZERO offset rather than left without one, because the offset elements have to render
        // SOMETHING for it and live renders Z. A TIME is a time on 1970-01-01 (live-verified: 'YYYY-MM-DD'
        // over a TIME is 1970-01-01).
        if (v instanceof ZonedDateTime) return (ZonedDateTime) v;
        if (v instanceof OffsetDateTime) return (OffsetDateTime) v;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).atOffset(ZoneOffset.UTC);
        if (v instanceof LocalDate) return ((LocalDate) v).atStartOfDay().atOffset(ZoneOffset.UTC);
        if (v instanceof LocalTime) return ((LocalTime) v).atDate(LocalDate.EPOCH).atOffset(ZoneOffset.UTC);
        if (v instanceof Instant) return ((Instant) v).atOffset(ZoneOffset.UTC);
        return SharedFunctionHelpers.toLocalDateTime(v).atOffset(ZoneOffset.UTC);
    }

    /**
     * The format model scanned into its pieces: each recognised element as its canonical spelling,
     * every other character merged into literal runs. The parsing side ({@code SnowflakeDateParser})
     * reads the same pieces, so the two directions can never disagree about what an element is.
     *
     * <p>★ DOUBLE-QUOTED TEXT IS LITERAL AND THE QUOTES ARE CONSUMED (live-verified):
     * 'YYYY-MM-DD"T"HH24:MI' emits a bare T, elements never match inside a run ('"YYYY"' is the four
     * characters YYYY), an UNCLOSED run is literal to the end ('YYYY"T' emits ...T), and a DOUBLED
     * quote at top level is one literal quote ('YYYY""MM' emits ..."...) while adjacent runs just
     * concatenate ('"a""b"' is ab). A quoted run must match literally when parsing, too —
     * TO_DATE('2020T01T15', 'YYYY"T"MM"T"DD') runs on both engines.
     */
    static List<FormatPiece> pieces(final String format) {
        final List<FormatPiece> out = new ArrayList<>();
        final StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < format.length()) {
            if (format.charAt(i) == '"') {
                if (i + 1 < format.length() && format.charAt(i + 1) == '"') {
                    literal.append('"');
                    i += 2;
                    continue;
                }
                final int close = format.indexOf('"', i + 1);
                literal.append(close < 0 ? format.substring(i + 1) : format.substring(i + 1, close));
                i = close < 0 ? format.length() : close + 1;
                continue;
            }
            final String[] token = matchToken(format, i);
            if (token != null) {
                if (literal.length() > 0) {
                    out.add(FormatPiece.literal(literal.toString()));
                    literal.setLength(0);
                }
                out.add(FormatPiece.element(token[0]));
                i += token[0].length();
            } else {
                literal.append(format.charAt(i));
                i++;
            }
        }
        if (literal.length() > 0) {
            out.add(FormatPiece.literal(literal.toString()));
        }
        return out;
    }

    private static String[] matchToken(final String format, final int pos) {
        for (final String[] token : TOKENS) {
            if (format.regionMatches(true, pos, token[0], 0, token[0].length())) {
                return token;
            }
        }
        return null;
    }
}
