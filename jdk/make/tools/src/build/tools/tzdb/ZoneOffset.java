/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package build.tools.tzdb;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A time-zone offset from Greenwich/UTC, such as {@code +02:00}.
 * <p>
 * A time-zone offset is the period of time that a time-zone differs from Greenwich/UTC.
 * This is usually a fixed number of hours and minutes.
 *
 * @since 1.8
 */
final class ZoneOffset implements Comparable<ZoneOffset> {

    /** Cache of time-zone offset by offset in seconds. */
    private static final ConcurrentMap<Integer, ZoneOffset> SECONDS_CACHE = new ConcurrentHashMap<>(16, 0.75f, 4);
    /** Cache of time-zone offset by ID. */
    private static final ConcurrentMap<String, ZoneOffset> ID_CACHE = new ConcurrentHashMap<>(16, 0.75f, 4);

    /**
     * The number of seconds per hour.
     */
    private static final int SECONDS_PER_HOUR = 60 * 60;
    /**
     * The number of seconds per minute.
     */
    private static final int SECONDS_PER_MINUTE = 60;
    /**
     * The number of minutes per hour.
     */
    private static final int MINUTES_PER_HOUR = 60;
    /**
     * The abs maximum seconds.
     */
    private static final int MAX_SECONDS = 18 * SECONDS_PER_HOUR;
    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 2357656521762053153L;

    /**
     * The time-zone offset for UTC, with an ID of 'Z'.
     */
    public static final ZoneOffset UTC = ZoneOffset.ofTotalSeconds(0);
    /**
     * Constant for the maximum supported offset.
     */
    public static final ZoneOffset MIN = ZoneOffset.ofTotalSeconds(-MAX_SECONDS);
    /**
     * Constant for the maximum supported offset.
     */
    public static final ZoneOffset MAX = ZoneOffset.ofTotalSeconds(MAX_SECONDS);

    /**
     * The total offset in seconds.
     */
    private final int totalSeconds;
    /**
     * The string form of the time-zone offset.
     */
    private final transient String id;

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code ZoneOffset} using the ID.
     * <p>
     * This method parses the string ID of a {@code ZoneOffset} to
     * return an instance. The parsing accepts all the formats generated by
     * {@link #getId()}, plus some additional formats:
     * <p><ul>
     * <li>{@code Z} - for UTC
     * <li>{@code +h}
     * <li>{@code +hh}
     * <li>{@code +hh:mm}
     * <li>{@code -hh:mm}
     * <li>{@code +hhmm}
     * <li>{@code -hhmm}
     * <li>{@code +hh:mm:ss}
     * <li>{@code -hh:mm:ss}
     * <li>{@code +hhmmss}
     * <li>{@code -hhmmss}
     * </ul><p>
     * Note that &plusmn; means either the plus or minus symbol.
     * <p>
     * The ID of the returned offset will be normalized to one of the formats
     * described by {@link #getId()}.
     * <p>
     * The maximum supported range is from +18:00 to -18:00 inclusive.
     *
     * @param offsetId  the offset ID, not null
     * @return the zone-offset, not null
     * @throws DateTimeException if the offset ID is invalid
     */
    @SuppressWarnings("fallthrough")
    public static ZoneOffset of(String offsetId) {
        Objects.requireNonNull(offsetId, "offsetId");
        // "Z" is always in the cache
        ZoneOffset offset = ID_CACHE.get(offsetId);
        if (offset != null) {
            return offset;
        }

        // parse - +h, +hh, +hhmm, +hh:mm, +hhmmss, +hh:mm:ss
        final int hours, minutes, seconds;
        switch (offsetId.length()) {
            case 2:
                offsetId = offsetId.charAt(0) + "0" + offsetId.charAt(1);  // fallthru
            case 3:
                hours = parseNumber(offsetId, 1, false);
                minutes = 0;
                seconds = 0;
                break;
            case 5:
                hours = parseNumber(offsetId, 1, false);
                minutes = parseNumber(offsetId, 3, false);
                seconds = 0;
                break;
            case 6:
                hours = parseNumber(offsetId, 1, false);
                minutes = parseNumber(offsetId, 4, true);
                seconds = 0;
                break;
            case 7:
                hours = parseNumber(offsetId, 1, false);
                minutes = parseNumber(offsetId, 3, false);
                seconds = parseNumber(offsetId, 5, false);
                break;
            case 9:
                hours = parseNumber(offsetId, 1, false);
                minutes = parseNumber(offsetId, 4, true);
                seconds = parseNumber(offsetId, 7, true);
                break;
            default:
                throw new DateTimeException("Zone offset ID '" + offsetId + "' is invalid");
        }
        char first = offsetId.charAt(0);
        if (first != '+' && first != '-') {
            throw new DateTimeException("Zone offset ID '" + offsetId + "' is invalid: Plus/minus not found when expected");
        }
        if (first == '-') {
            return ofHoursMinutesSeconds(-hours, -minutes, -seconds);
        } else {
            return ofHoursMinutesSeconds(hours, minutes, seconds);
        }
    }

    /**
     * Parse a two digit zero-prefixed number.
     *
     * @param offsetId  the offset ID, not null
     * @param pos  the position to parse, valid
     * @param precededByColon  should this number be prefixed by a precededByColon
     * @return the parsed number, from 0 to 99
     */
    private static int parseNumber(CharSequence offsetId, int pos, boolean precededByColon) {
        if (precededByColon && offsetId.charAt(pos - 1) != ':') {
            throw new DateTimeException("Zone offset ID '" + offsetId + "' is invalid: Colon not found when expected");
        }
        char ch1 = offsetId.charAt(pos);
        char ch2 = offsetId.charAt(pos + 1);
        if (ch1 < '0' || ch1 > '9' || ch2 < '0' || ch2 > '9') {
            throw new DateTimeException("Zone offset ID '" + offsetId + "' is invalid: Non numeric characters found");
        }
        return (ch1 - 48) * 10 + (ch2 - 48);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code ZoneOffset} using an offset in hours.
     *
     * @param hours  the time-zone offset in hours, from -18 to +18
     * @return the zone-offset, not null
     * @throws DateTimeException if the offset is not in the required range
     */
    public static ZoneOffset ofHours(int hours) {
        return ofHoursMinutesSeconds(hours, 0, 0);
    }

    /**
     * Obtains an instance of {@code ZoneOffset} using an offset in
     * hours and minutes.
     * <p>
     * The sign of the hours and minutes components must match.
     * Thus, if the hours is negative, the minutes must be negative or zero.
     * If the hours is zero, the minutes may be positive, negative or zero.
     *
     * @param hours  the time-zone offset in hours, from -18 to +18
     * @param minutes  the time-zone offset in minutes, from 0 to &plusmn;59, sign matches hours
     * @return the zone-offset, not null
     * @throws DateTimeException if the offset is not in the required range
     */
    public static ZoneOffset ofHoursMinutes(int hours, int minutes) {
        return ofHoursMinutesSeconds(hours, minutes, 0);
    }

    /**
     * Obtains an instance of {@code ZoneOffset} using an offset in
     * hours, minutes and seconds.
     * <p>
     * The sign of the hours, minutes and seconds components must match.
     * Thus, if the hours is negative, the minutes and seconds must be negative or zero.
     *
     * @param hours  the time-zone offset in hours, from -18 to +18
     * @param minutes  the time-zone offset in minutes, from 0 to &plusmn;59, sign matches hours and seconds
     * @param seconds  the time-zone offset in seconds, from 0 to &plusmn;59, sign matches hours and minutes
     * @return the zone-offset, not null
     * @throws DateTimeException if the offset is not in the required range
     */
    public static ZoneOffset ofHoursMinutesSeconds(int hours, int minutes, int seconds) {
        validate(hours, minutes, seconds);
        int totalSeconds = totalSeconds(hours, minutes, seconds);
        return ofTotalSeconds(totalSeconds);
    }

    /**
     * Validates the offset fields.
     *
     * @param hours  the time-zone offset in hours, from -18 to +18
     * @param minutes  the time-zone offset in minutes, from 0 to &plusmn;59
     * @param seconds  the time-zone offset in seconds, from 0 to &plusmn;59
     * @throws DateTimeException if the offset is not in the required range
     */
    private static void validate(int hours, int minutes, int seconds) {
        if (hours < -18 || hours > 18) {
            throw new DateTimeException("Zone offset hours not in valid range: value " + hours +
                    " is not in the range -18 to 18");
        }
        if (hours > 0) {
            if (minutes < 0 || seconds < 0) {
                throw new DateTimeException("Zone offset minutes and seconds must be positive because hours is positive");
            }
        } else if (hours < 0) {
            if (minutes > 0 || seconds > 0) {
                throw new DateTimeException("Zone offset minutes and seconds must be negative because hours is negative");
            }
        } else if ((minutes > 0 && seconds < 0) || (minutes < 0 && seconds > 0)) {
            throw new DateTimeException("Zone offset minutes and seconds must have the same sign");
        }
        if (Math.abs(minutes) > 59) {
            throw new DateTimeException("Zone offset minutes not in valid range: abs(value) " +
                    Math.abs(minutes) + " is not in the range 0 to 59");
        }
        if (Math.abs(seconds) > 59) {
            throw new DateTimeException("Zone offset seconds not in valid range: abs(value) " +
                    Math.abs(seconds) + " is not in the range 0 to 59");
        }
        if (Math.abs(hours) == 18 && (Math.abs(minutes) > 0 || Math.abs(seconds) > 0)) {
            throw new DateTimeException("Zone offset not in valid range: -18:00 to +18:00");
        }
    }

    /**
     * Calculates the total offset in seconds.
     *
     * @param hours  the time-zone offset in hours, from -18 to +18
     * @param minutes  the time-zone offset in minutes, from 0 to &plusmn;59, sign matches hours and seconds
     * @param seconds  the time-zone offset in seconds, from 0 to &plusmn;59, sign matches hours and minutes
     * @return the total in seconds
     */
    private static int totalSeconds(int hours, int minutes, int seconds) {
        return hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds;
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code ZoneOffset} specifying the total offset in seconds
     * <p>
     * The offset must be in the range {@code -18:00} to {@code +18:00}, which corresponds to -64800 to +64800.
     *
     * @param totalSeconds  the total time-zone offset in seconds, from -64800 to +64800
     * @return the ZoneOffset, not null
     * @throws DateTimeException if the offset is not in the required range
     */
    public static ZoneOffset ofTotalSeconds(int totalSeconds) {
        if (Math.abs(totalSeconds) > MAX_SECONDS) {
            throw new DateTimeException("Zone offset not in valid range: -18:00 to +18:00");
        }
        if (totalSeconds % (15 * SECONDS_PER_MINUTE) == 0) {
            Integer totalSecs = totalSeconds;
            ZoneOffset result = SECONDS_CACHE.get(totalSecs);
            if (result == null) {
                result = new ZoneOffset(totalSeconds);
                SECONDS_CACHE.putIfAbsent(totalSecs, result);
                result = SECONDS_CACHE.get(totalSecs);
                ID_CACHE.putIfAbsent(result.getId(), result);
            }
            return result;
        } else {
            return new ZoneOffset(totalSeconds);
        }
    }

    /**
     * Constructor.
     *
     * @param totalSeconds  the total time-zone offset in seconds, from -64800 to +64800
     */
    private ZoneOffset(int totalSeconds) {
        super();
        this.totalSeconds = totalSeconds;
        id = buildId(totalSeconds);
    }

    private static String buildId(int totalSeconds) {
        if (totalSeconds == 0) {
            return "Z";
        } else {
            int absTotalSeconds = Math.abs(totalSeconds);
            StringBuilder buf = new StringBuilder();
            int absHours = absTotalSeconds / SECONDS_PER_HOUR;
            int absMinutes = (absTotalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR;
            buf.append(totalSeconds < 0 ? "-" : "+")
                .append(absHours < 10 ? "0" : "").append(absHours)
                .append(absMinutes < 10 ? ":0" : ":").append(absMinutes);
            int absSeconds = absTotalSeconds % SECONDS_PER_MINUTE;
            if (absSeconds != 0) {
                buf.append(absSeconds < 10 ? ":0" : ":").append(absSeconds);
            }
            return buf.toString();
        }
    }

    /**
     * Gets the total zone offset in seconds.
     * <p>
     * This is the primary way to access the offset amount.
     * It returns the total of the hours, minutes and seconds fields as a
     * single offset that can be added to a time.
     *
     * @return the total zone offset amount in seconds
     */
    public int getTotalSeconds() {
        return totalSeconds;
    }

    /**
     * Gets the normalized zone offset ID.
     * <p>
     * The ID is minor variation to the standard ISO-8601 formatted string
     * for the offset. There are three formats:
     * <p><ul>
     * <li>{@code Z} - for UTC (ISO-8601)
     * <li>{@code +hh:mm} or {@code -hh:mm} - if the seconds are zero (ISO-8601)
     * <li>{@code +hh:mm:ss} or {@code -hh:mm:ss} - if the seconds are non-zero (not ISO-8601)
     * </ul><p>
     *
     * @return the zone offset ID, not null
     */
    public String getId() {
        return id;
    }

    /**
     * Compares this offset to another offset in descending order.
     * <p>
     * The offsets are compared in the order that they occur for the same time
     * of day around the world. Thus, an offset of {@code +10:00} comes before an
     * offset of {@code +09:00} and so on down to {@code -18:00}.
     * <p>
     * The comparison is "consistent with equals", as defined by {@link Comparable}.
     *
     * @param other  the other date to compare to, not null
     * @return the comparator value, negative if less, postive if greater
     * @throws NullPointerException if {@code other} is null
     */
    @Override
    public int compareTo(ZoneOffset other) {
        return other.totalSeconds - totalSeconds;
    }

    /**
     * Checks if this offset is equal to another offset.
     * <p>
     * The comparison is based on the amount of the offset in seconds.
     * This is equivalent to a comparison by ID.
     *
     * @param obj  the object to check, null returns false
     * @return true if this is equal to the other offset
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
           return true;
        }
        if (obj instanceof ZoneOffset) {
            return totalSeconds == ((ZoneOffset) obj).totalSeconds;
        }
        return false;
    }

    /**
     * A hash code for this offset.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        return totalSeconds;
    }

    /**
     * Outputs this offset as a {@code String}, using the normalized ID.
     *
     * @return a string representation of this offset, not null
     */
    @Override
    public String toString() {
        return id;
    }

}
