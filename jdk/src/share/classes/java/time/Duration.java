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
package java.time;

import static java.time.LocalTime.SECONDS_PER_DAY;
import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoUnit.DAYS;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdder;
import java.time.temporal.TemporalSubtractor;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

/**
 * A duration between two instants on the time-line.
 * <p>
 * This class models a duration of time and is not tied to any instant.
 * The model is of a directed duration, meaning that the duration may be negative.
 * <p>
 * A physical duration could be of infinite length.
 * For practicality, the duration is stored with constraints similar to {@link Instant}.
 * The duration uses nanosecond resolution with a maximum value of the seconds that can
 * be held in a {@code long}. This is greater than the current estimated age of the universe.
 * <p>
 * The range of a duration requires the storage of a number larger than a {@code long}.
 * To achieve this, the class stores a {@code long} representing seconds and an {@code int}
 * representing nanosecond-of-second, which will always be between 0 and 999,999,999.
 * <p>
 * The duration is measured in "seconds", but these are not necessarily identical to
 * the scientific "SI second" definition based on atomic clocks.
 * This difference only impacts durations measured near a leap-second and should not affect
 * most applications.
 * See {@link Instant} for a discussion as to the meaning of the second and time-scales.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
public final class Duration
        implements TemporalAdder, TemporalSubtractor, Comparable<Duration>, Serializable {

    /**
     * Constant for a duration of zero.
     */
    public static final Duration ZERO = new Duration(0, 0);
    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 3078945930695997490L;
    /**
     * Constant for nanos per second.
     */
    private static final int NANOS_PER_SECOND = 1000_000_000;
    /**
     * Constant for nanos per second.
     */
    private static final BigInteger BI_NANOS_PER_SECOND = BigInteger.valueOf(NANOS_PER_SECOND);

    /**
     * The number of seconds in the duration.
     */
    private final long seconds;
    /**
     * The number of nanoseconds in the duration, expressed as a fraction of the
     * number of seconds. This is always positive, and never exceeds 999,999,999.
     */
    private final int nanos;

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} from a number of seconds.
     * <p>
     * The nanosecond in second field is set to zero.
     *
     * @param seconds  the number of seconds, positive or negative
     * @return a {@code Duration}, not null
     */
    public static Duration ofSeconds(long seconds) {
        return create(seconds, 0);
    }

    /**
     * Obtains an instance of {@code Duration} from a number of seconds
     * and an adjustment in nanoseconds.
     * <p>
     * This method allows an arbitrary number of nanoseconds to be passed in.
     * The factory will alter the values of the second and nanosecond in order
     * to ensure that the stored nanosecond is in the range 0 to 999,999,999.
     * For example, the following will result in the exactly the same duration:
     * <pre>
     *  Duration.ofSeconds(3, 1);
     *  Duration.ofSeconds(4, -999_999_999);
     *  Duration.ofSeconds(2, 1000_000_001);
     * </pre>
     *
     * @param seconds  the number of seconds, positive or negative
     * @param nanoAdjustment  the nanosecond adjustment to the number of seconds, positive or negative
     * @return a {@code Duration}, not null
     * @throws ArithmeticException if the adjustment causes the seconds to exceed the capacity of {@code Duration}
     */
    public static Duration ofSeconds(long seconds, long nanoAdjustment) {
        long secs = Math.addExact(seconds, Math.floorDiv(nanoAdjustment, NANOS_PER_SECOND));
        int nos = (int)Math.floorMod(nanoAdjustment, NANOS_PER_SECOND);
        return create(secs, nos);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} from a number of milliseconds.
     * <p>
     * The seconds and nanoseconds are extracted from the specified milliseconds.
     *
     * @param millis  the number of milliseconds, positive or negative
     * @return a {@code Duration}, not null
     */
    public static Duration ofMillis(long millis) {
        long secs = millis / 1000;
        int mos = (int) (millis % 1000);
        if (mos < 0) {
            mos += 1000;
            secs--;
        }
        return create(secs, mos * 1000_000);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} from a number of nanoseconds.
     * <p>
     * The seconds and nanoseconds are extracted from the specified nanoseconds.
     *
     * @param nanos  the number of nanoseconds, positive or negative
     * @return a {@code Duration}, not null
     */
    public static Duration ofNanos(long nanos) {
        long secs = nanos / NANOS_PER_SECOND;
        int nos = (int) (nanos % NANOS_PER_SECOND);
        if (nos < 0) {
            nos += NANOS_PER_SECOND;
            secs--;
        }
        return create(secs, nos);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} from a number of standard length minutes.
     * <p>
     * The seconds are calculated based on the standard definition of a minute,
     * where each minute is 60 seconds.
     * The nanosecond in second field is set to zero.
     *
     * @param minutes  the number of minutes, positive or negative
     * @return a {@code Duration}, not null
     * @throws ArithmeticException if the input minutes exceeds the capacity of {@code Duration}
     */
    public static Duration ofMinutes(long minutes) {
        return create(Math.multiplyExact(minutes, 60), 0);
    }

    /**
     * Obtains an instance of {@code Duration} from a number of standard length hours.
     * <p>
     * The seconds are calculated based on the standard definition of an hour,
     * where each hour is 3600 seconds.
     * The nanosecond in second field is set to zero.
     *
     * @param hours  the number of hours, positive or negative
     * @return a {@code Duration}, not null
     * @throws ArithmeticException if the input hours exceeds the capacity of {@code Duration}
     */
    public static Duration ofHours(long hours) {
        return create(Math.multiplyExact(hours, 3600), 0);
    }

    /**
     * Obtains an instance of {@code Duration} from a number of standard 24 hour days.
     * <p>
     * The seconds are calculated based on the standard definition of a day,
     * where each day is 86400 seconds which implies a 24 hour day.
     * The nanosecond in second field is set to zero.
     *
     * @param days  the number of days, positive or negative
     * @return a {@code Duration}, not null
     * @throws ArithmeticException if the input days exceeds the capacity of {@code Duration}
     */
    public static Duration ofDays(long days) {
        return create(Math.multiplyExact(days, 86400), 0);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} from a duration in the specified unit.
     * <p>
     * The parameters represent the two parts of a phrase like '6 Hours'. For example:
     * <pre>
     *  Duration.of(3, SECONDS);
     *  Duration.of(465, HOURS);
     * </pre>
     * Only a subset of units are accepted by this method.
     * The unit must either have an {@linkplain TemporalUnit#isDurationEstimated() exact duration} or
     * be {@link ChronoUnit#DAYS} which is treated as 24 hours. Other units throw an exception.
     *
     * @param amount  the amount of the duration, measured in terms of the unit, positive or negative
     * @param unit  the unit that the duration is measured in, must have an exact duration, not null
     * @return a {@code Duration}, not null
     * @throws DateTimeException if the period unit has an estimated duration
     * @throws ArithmeticException if a numeric overflow occurs
     */
    public static Duration of(long amount, TemporalUnit unit) {
        return ZERO.plus(amount, unit);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} representing the duration between two instants.
     * <p>
     * A {@code Duration} represents a directed distance between two points on the time-line.
     * As such, this method will return a negative duration if the end is before the start.
     * To guarantee to obtain a positive duration call {@link #abs()} on the result of this factory.
     *
     * @param startInclusive  the start instant, inclusive, not null
     * @param endExclusive  the end instant, exclusive, not null
     * @return a {@code Duration}, not null
     * @throws ArithmeticException if the calculation exceeds the capacity of {@code Duration}
     */
    public static Duration between(TemporalAccessor startInclusive, TemporalAccessor endExclusive) {
        long secs = Math.subtractExact(endExclusive.getLong(INSTANT_SECONDS), startInclusive.getLong(INSTANT_SECONDS));
        long nanos = endExclusive.getLong(NANO_OF_SECOND) - startInclusive.getLong(NANO_OF_SECOND);
        secs = Math.addExact(secs, Math.floorDiv(nanos, NANOS_PER_SECOND));
        nanos = Math.floorMod(nanos, NANOS_PER_SECOND);
        return create(secs, (int) nanos);  // safe from overflow
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} by parsing a text string.
     * <p>
     * This will parse the string produced by {@link #toString()} which is
     * the ISO-8601 format {@code PTnS} where {@code n} is
     * the number of seconds with optional decimal part.
     * The number must consist of ASCII numerals.
     * There must only be a negative sign at the start of the number and it can
     * only be present if the value is less than zero.
     * There must be at least one digit before any decimal point.
     * There must be between 1 and 9 inclusive digits after any decimal point.
     * The letters (P, T and S) will be accepted in upper or lower case.
     * The decimal point may be either a dot or a comma.
     *
     * @param text  the text to parse, not null
     * @return a {@code Duration}, not null
     * @throws DateTimeParseException if the text cannot be parsed to a {@code Duration}
     */
    public static Duration parse(final CharSequence text) {
        Objects.requireNonNull(text, "text");
        int len = text.length();
        if (len < 4 ||
                (text.charAt(0) != 'P' && text.charAt(0) != 'p') ||
                (text.charAt(1) != 'T' && text.charAt(1) != 't') ||
                (text.charAt(len - 1) != 'S' && text.charAt(len - 1) != 's') ||
                (len == 5 && text.charAt(2) == '-' && text.charAt(3) == '0')) {
            throw new DateTimeParseException("Duration could not be parsed: " + text, text, 0);
        }
        String numberText = text.subSequence(2, len - 1).toString().replace(',', '.');
        if (numberText.charAt(0) == '+') {
            throw new DateTimeParseException("Duration could not be parsed: " + text, text, 2);
        }
        int dot = numberText.indexOf('.');
        try {
            if (dot == -1) {
                // no decimal places
                if (numberText.startsWith("-0")) {
                    throw new DateTimeParseException("Duration could not be parsed: " + text, text, 2);
                }
                return create(Long.parseLong(numberText), 0);
            }
            // decimal places
            boolean negative = false;
            if (numberText.charAt(0) == '-') {
                negative = true;
            }
            long secs = Long.parseLong(numberText.substring(0, dot));
            numberText = numberText.substring(dot + 1);
            len = numberText.length();
            if (len == 0 || len > 9 || numberText.charAt(0) == '-' || numberText.charAt(0) == '+') {
                throw new DateTimeParseException("Duration could not be parsed: " + text, text, 2);
            }
            int nanos = Integer.parseInt(numberText);
            switch (len) {
                case 1:
                    nanos *= 100000000;
                    break;
                case 2:
                    nanos *= 10000000;
                    break;
                case 3:
                    nanos *= 1000000;
                    break;
                case 4:
                    nanos *= 100000;
                    break;
                case 5:
                    nanos *= 10000;
                    break;
                case 6:
                    nanos *= 1000;
                    break;
                case 7:
                    nanos *= 100;
                    break;
                case 8:
                    nanos *= 10;
                    break;
            }
            return negative ? ofSeconds(secs, -nanos) : create(secs, nanos);

        } catch (ArithmeticException | NumberFormatException ex) {
            throw new DateTimeParseException("Duration could not be parsed: " + text, text, 2, ex);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Duration} using seconds and nanoseconds.
     *
     * @param seconds  the length of the duration in seconds, positive or negative
     * @param nanoAdjustment  the nanosecond adjustment within the second, from 0 to 999,999,999
     */
    private static Duration create(long seconds, int nanoAdjustment) {
        if ((seconds | nanoAdjustment) == 0) {
            return ZERO;
        }
        return new Duration(seconds, nanoAdjustment);
    }

    /**
     * Constructs an instance of {@code Duration} using seconds and nanoseconds.
     *
     * @param seconds  the length of the duration in seconds, positive or negative
     * @param nanos  the nanoseconds within the second, from 0 to 999,999,999
     */
    private Duration(long seconds, int nanos) {
        super();
        this.seconds = seconds;
        this.nanos = nanos;
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if this duration is zero length.
     * <p>
     * A {@code Duration} represents a directed distance between two points on
     * the time-line and can therefore be positive, zero or negative.
     * This method checks whether the length is zero.
     *
     * @return true if this duration has a total length equal to zero
     */
    public boolean isZero() {
        return (seconds | nanos) == 0;
    }

    /**
     * Checks if this duration is positive, excluding zero.
     * <p>
     * A {@code Duration} represents a directed distance between two points on
     * the time-line and can therefore be positive, zero or negative.
     * This method checks whether the length is greater than zero.
     *
     * @return true if this duration has a total length greater than zero
     */
    public boolean isPositive() {
        return seconds >= 0 && ((seconds | nanos) != 0);
    }

    /**
     * Checks if this duration is negative, excluding zero.
     * <p>
     * A {@code Duration} represents a directed distance between two points on
     * the time-line and can therefore be positive, zero or negative.
     * This method checks whether the length is less than zero.
     *
     * @return true if this duration has a total length less than zero
     */
    public boolean isNegative() {
        return seconds < 0;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the number of seconds in this duration.
     * <p>
     * The length of the duration is stored using two fields - seconds and nanoseconds.
     * The nanoseconds part is a value from 0 to 999,999,999 that is an adjustment to
     * the length in seconds.
     * The total duration is defined by calling this method and {@link #getNano()}.
     * <p>
     * A {@code Duration} represents a directed distance between two points on the time-line.
     * A negative duration is expressed by the negative sign of the seconds part.
     * A duration of -1 nanosecond is stored as -1 seconds plus 999,999,999 nanoseconds.
     *
     * @return the whole seconds part of the length of the duration, positive or negative
     */
    public long getSeconds() {
        return seconds;
    }

    /**
     * Gets the number of nanoseconds within the second in this duration.
     * <p>
     * The length of the duration is stored using two fields - seconds and nanoseconds.
     * The nanoseconds part is a value from 0 to 999,999,999 that is an adjustment to
     * the length in seconds.
     * The total duration is defined by calling this method and {@link #getSeconds()}.
     * <p>
     * A {@code Duration} represents a directed distance between two points on the time-line.
     * A negative duration is expressed by the negative sign of the seconds part.
     * A duration of -1 nanosecond is stored as -1 seconds plus 999,999,999 nanoseconds.
     *
     * @return the nanoseconds within the second part of the length of the duration, from 0 to 999,999,999
     */
    public int getNano() {
        return nanos;
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this duration with the specified duration added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param duration  the duration to add, positive or negative, not null
     * @return a {@code Duration} based on this duration with the specified duration added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration plus(Duration duration) {
        return plus(duration.getSeconds(), duration.getNano());
     }

    /**
     * Returns a copy of this duration with the specified duration added.
     * <p>
     * The duration amount is measured in terms of the specified unit.
     * Only a subset of units are accepted by this method.
     * The unit must either have an {@linkplain TemporalUnit#isDurationEstimated() exact duration} or
     * be {@link ChronoUnit#DAYS} which is treated as 24 hours. Other units throw an exception.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param amountToAdd  the amount of the period, measured in terms of the unit, positive or negative
     * @param unit  the unit that the period is measured in, must have an exact duration, not null
     * @return a {@code Duration} based on this duration with the specified duration added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration plus(long amountToAdd, TemporalUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (unit == DAYS) {
            return plus(Math.multiplyExact(amountToAdd, SECONDS_PER_DAY), 0);
        }
        if (unit.isDurationEstimated()) {
            throw new DateTimeException("Unit must not have an estimated duration");
        }
        if (amountToAdd == 0) {
            return this;
        }
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case NANOS: return plusNanos(amountToAdd);
                case MICROS: return plusSeconds((amountToAdd / (1000_000L * 1000)) * 1000).plusNanos((amountToAdd % (1000_000L * 1000)) * 1000);
                case MILLIS: return plusMillis(amountToAdd);
                case SECONDS: return plusSeconds(amountToAdd);
            }
            return plusSeconds(Math.multiplyExact(unit.getDuration().seconds, amountToAdd));
        }
        Duration duration = unit.getDuration().multipliedBy(amountToAdd);
        return plusSeconds(duration.getSeconds()).plusNanos(duration.getNano());
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this duration with the specified duration in seconds added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param secondsToAdd  the seconds to add, positive or negative
     * @return a {@code Duration} based on this duration with the specified seconds added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration plusSeconds(long secondsToAdd) {
        return plus(secondsToAdd, 0);
    }

    /**
     * Returns a copy of this duration with the specified duration in milliseconds added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param millisToAdd  the milliseconds to add, positive or negative
     * @return a {@code Duration} based on this duration with the specified milliseconds added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration plusMillis(long millisToAdd) {
        return plus(millisToAdd / 1000, (millisToAdd % 1000) * 1000_000);
    }

    /**
     * Returns a copy of this duration with the specified duration in nanoseconds added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param nanosToAdd  the nanoseconds to add, positive or negative
     * @return a {@code Duration} based on this duration with the specified nanoseconds added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration plusNanos(long nanosToAdd) {
        return plus(0, nanosToAdd);
    }

    /**
     * Returns a copy of this duration with the specified duration added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param secondsToAdd  the seconds to add, positive or negative
     * @param nanosToAdd  the nanos to add, positive or negative
     * @return a {@code Duration} based on this duration with the specified seconds added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    private Duration plus(long secondsToAdd, long nanosToAdd) {
        if ((secondsToAdd | nanosToAdd) == 0) {
            return this;
        }
        long epochSec = Math.addExact(seconds, secondsToAdd);
        epochSec = Math.addExact(epochSec, nanosToAdd / NANOS_PER_SECOND);
        nanosToAdd = nanosToAdd % NANOS_PER_SECOND;
        long nanoAdjustment = nanos + nanosToAdd;  // safe int+NANOS_PER_SECOND
        return ofSeconds(epochSec, nanoAdjustment);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this duration with the specified duration subtracted.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param duration  the duration to subtract, positive or negative, not null
     * @return a {@code Duration} based on this duration with the specified duration subtracted, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration minus(Duration duration) {
        long secsToSubtract = duration.getSeconds();
        int nanosToSubtract = duration.getNano();
        if (secsToSubtract == Long.MIN_VALUE) {
            return plus(Long.MAX_VALUE, -nanosToSubtract).plus(1, 0);
        }
        return plus(-secsToSubtract, -nanosToSubtract);
     }

    /**
     * Returns a copy of this duration with the specified duration subtracted.
     * <p>
     * The duration amount is measured in terms of the specified unit.
     * Only a subset of units are accepted by this method.
     * The unit must either have an {@linkplain TemporalUnit#isDurationEstimated() exact duration} or
     * be {@link ChronoUnit#DAYS} which is treated as 24 hours. Other units throw an exception.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param amountToSubtract  the amount of the period, measured in terms of the unit, positive or negative
     * @param unit  the unit that the period is measured in, must have an exact duration, not null
     * @return a {@code Duration} based on this duration with the specified duration subtracted, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration minus(long amountToSubtract, TemporalUnit unit) {
        return (amountToSubtract == Long.MIN_VALUE ? plus(Long.MAX_VALUE, unit).plus(1, unit) : plus(-amountToSubtract, unit));
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this duration with the specified duration in seconds subtracted.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param secondsToSubtract  the seconds to subtract, positive or negative
     * @return a {@code Duration} based on this duration with the specified seconds subtracted, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration minusSeconds(long secondsToSubtract) {
        return (secondsToSubtract == Long.MIN_VALUE ? plusSeconds(Long.MAX_VALUE).plusSeconds(1) : plusSeconds(-secondsToSubtract));
    }

    /**
     * Returns a copy of this duration with the specified duration in milliseconds subtracted.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param millisToSubtract  the milliseconds to subtract, positive or negative
     * @return a {@code Duration} based on this duration with the specified milliseconds subtracted, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration minusMillis(long millisToSubtract) {
        return (millisToSubtract == Long.MIN_VALUE ? plusMillis(Long.MAX_VALUE).plusMillis(1) : plusMillis(-millisToSubtract));
    }

    /**
     * Returns a copy of this duration with the specified duration in nanoseconds subtracted.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param nanosToSubtract  the nanoseconds to subtract, positive or negative
     * @return a {@code Duration} based on this duration with the specified nanoseconds subtracted, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration minusNanos(long nanosToSubtract) {
        return (nanosToSubtract == Long.MIN_VALUE ? plusNanos(Long.MAX_VALUE).plusNanos(1) : plusNanos(-nanosToSubtract));
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this duration multiplied by the scalar.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param multiplicand  the value to multiply the duration by, positive or negative
     * @return a {@code Duration} based on this duration multiplied by the specified scalar, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration multipliedBy(long multiplicand) {
        if (multiplicand == 0) {
            return ZERO;
        }
        if (multiplicand == 1) {
            return this;
        }
        return create(toSeconds().multiply(BigDecimal.valueOf(multiplicand)));
     }

    /**
     * Returns a copy of this duration divided by the specified value.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param divisor  the value to divide the duration by, positive or negative, not zero
     * @return a {@code Duration} based on this duration divided by the specified divisor, not null
     * @throws ArithmeticException if the divisor is zero
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration dividedBy(long divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        if (divisor == 1) {
            return this;
        }
        return create(toSeconds().divide(BigDecimal.valueOf(divisor), RoundingMode.DOWN));
     }

    /**
     * Converts this duration to the total length in seconds and
     * fractional nanoseconds expressed as a {@code BigDecimal}.
     *
     * @return the total length of the duration in seconds, with a scale of 9, not null
     */
    private BigDecimal toSeconds() {
        return BigDecimal.valueOf(seconds).add(BigDecimal.valueOf(nanos, 9));
    }

    /**
     * Creates an instance of {@code Duration} from a number of seconds.
     *
     * @param seconds  the number of seconds, up to scale 9, positive or negative
     * @return a {@code Duration}, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    private static Duration create(BigDecimal seconds) {
        BigInteger nanos = seconds.movePointRight(9).toBigIntegerExact();
        BigInteger[] divRem = nanos.divideAndRemainder(BI_NANOS_PER_SECOND);
        if (divRem[0].bitLength() > 63) {
            throw new ArithmeticException("Exceeds capacity of Duration: " + nanos);
        }
        return ofSeconds(divRem[0].longValue(), divRem[1].intValue());
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this duration with the length negated.
     * <p>
     * This method swaps the sign of the total length of this duration.
     * For example, {@code PT1.3S} will be returned as {@code PT-1.3S}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @return a {@code Duration} based on this duration with the amount negated, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration negated() {
        return multipliedBy(-1);
    }

    /**
     * Returns a copy of this duration with a positive length.
     * <p>
     * This method returns a positive duration by effectively removing the sign from any negative total length.
     * For example, {@code PT-1.3S} will be returned as {@code PT1.3S}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @return a {@code Duration} based on this duration with an absolute length, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Duration abs() {
        return isNegative() ? negated() : this;
    }

    //-------------------------------------------------------------------------
    /**
     * Adds this duration to the specified temporal object.
     * <p>
     * This returns a temporal object of the same observable type as the input
     * with this duration added.
     * <p>
     * In most cases, it is clearer to reverse the calling pattern by using
     * {@link Temporal#plus(TemporalAdder)}.
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   dateTime = thisDuration.addTo(dateTime);
     *   dateTime = dateTime.plus(thisDuration);
     * </pre>
     * <p>
     * A {@code Duration} can only be added to a {@code Temporal} that
     * represents an instant and can supply {@link ChronoField#INSTANT_SECONDS}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return an object of the same type with the adjustment made, not null
     * @throws DateTimeException if unable to add
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public Temporal addTo(Temporal temporal) {
        long instantSecs = temporal.getLong(INSTANT_SECONDS);
        long instantNanos = temporal.getLong(NANO_OF_SECOND);
        instantSecs = Math.addExact(instantSecs, seconds);
        instantNanos = Math.addExact(instantNanos, nanos);
        instantSecs = Math.addExact(instantSecs, Math.floorDiv(instantNanos, NANOS_PER_SECOND));
        instantNanos = Math.floorMod(instantNanos, NANOS_PER_SECOND);
        return temporal.with(INSTANT_SECONDS, instantSecs).with(NANO_OF_SECOND, instantNanos);
    }

    /**
     * Subtracts this duration from the specified temporal object.
     * <p>
     * This returns a temporal object of the same observable type as the input
     * with this duration subtracted.
     * <p>
     * In most cases, it is clearer to reverse the calling pattern by using
     * {@link Temporal#minus(TemporalSubtractor)}.
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   dateTime = thisDuration.subtractFrom(dateTime);
     *   dateTime = dateTime.minus(thisDuration);
     * </pre>
     * <p>
     * A {@code Duration} can only be subtracted from a {@code Temporal} that
     * represents an instant and can supply {@link ChronoField#INSTANT_SECONDS}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return an object of the same type with the adjustment made, not null
     * @throws DateTimeException if unable to subtract
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public Temporal subtractFrom(Temporal temporal) {
        long instantSecs = temporal.getLong(INSTANT_SECONDS);
        long instantNanos = temporal.getLong(NANO_OF_SECOND);
        instantSecs = Math.subtractExact(instantSecs, seconds);
        instantNanos = Math.subtractExact(instantNanos, nanos);
        instantSecs = Math.addExact(instantSecs, Math.floorDiv(instantNanos, NANOS_PER_SECOND));
        instantNanos = Math.floorMod(instantNanos, NANOS_PER_SECOND);
        return temporal.with(INSTANT_SECONDS, instantSecs).with(NANO_OF_SECOND, instantNanos);
    }

    //-----------------------------------------------------------------------
    /**
     * Converts this duration to the total length in milliseconds.
     * <p>
     * If this duration is too large to fit in a {@code long} milliseconds, then an
     * exception is thrown.
     * <p>
     * If this duration has greater than millisecond precision, then the conversion
     * will drop any excess precision information as though the amount in nanoseconds
     * was subject to integer division by one million.
     *
     * @return the total length of the duration in milliseconds
     * @throws ArithmeticException if numeric overflow occurs
     */
    public long toMillis() {
        long millis = Math.multiplyExact(seconds, 1000);
        millis = Math.addExact(millis, nanos / 1000_000);
        return millis;
    }

    /**
     * Converts this duration to the total length in nanoseconds expressed as a {@code long}.
     * <p>
     * If this duration is too large to fit in a {@code long} nanoseconds, then an
     * exception is thrown.
     *
     * @return the total length of the duration in nanoseconds
     * @throws ArithmeticException if numeric overflow occurs
     */
    public long toNanos() {
        long millis = Math.multiplyExact(seconds, 1000_000_000);
        millis = Math.addExact(millis, nanos);
        return millis;
    }

    //-----------------------------------------------------------------------
    /**
     * Compares this duration to the specified {@code Duration}.
     * <p>
     * The comparison is based on the total length of the durations.
     * It is "consistent with equals", as defined by {@link Comparable}.
     *
     * @param otherDuration  the other duration to compare to, not null
     * @return the comparator value, negative if less, positive if greater
     */
    @Override
    public int compareTo(Duration otherDuration) {
        int cmp = Long.compare(seconds, otherDuration.seconds);
        if (cmp != 0) {
            return cmp;
        }
        return nanos - otherDuration.nanos;
    }

    /**
     * Checks if this duration is greater than the specified {@code Duration}.
     * <p>
     * The comparison is based on the total length of the durations.
     *
     * @param otherDuration  the other duration to compare to, not null
     * @return true if this duration is greater than the specified duration
     */
    public boolean isGreaterThan(Duration otherDuration) {
        return compareTo(otherDuration) > 0;
    }

    /**
     * Checks if this duration is less than the specified {@code Duration}.
     * <p>
     * The comparison is based on the total length of the durations.
     *
     * @param otherDuration  the other duration to compare to, not null
     * @return true if this duration is less than the specified duration
     */
    public boolean isLessThan(Duration otherDuration) {
        return compareTo(otherDuration) < 0;
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if this duration is equal to the specified {@code Duration}.
     * <p>
     * The comparison is based on the total length of the durations.
     *
     * @param otherDuration  the other duration, null returns false
     * @return true if the other duration is equal to this one
     */
    @Override
    public boolean equals(Object otherDuration) {
        if (this == otherDuration) {
            return true;
        }
        if (otherDuration instanceof Duration) {
            Duration other = (Duration) otherDuration;
            return this.seconds == other.seconds &&
                   this.nanos == other.nanos;
        }
        return false;
    }

    /**
     * A hash code for this duration.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        return ((int) (seconds ^ (seconds >>> 32))) + (51 * nanos);
    }

    //-----------------------------------------------------------------------
    /**
     * A string representation of this duration using ISO-8601 seconds
     * based representation, such as {@code PT12.345S}.
     * <p>
     * The format of the returned string will be {@code PTnS} where n is
     * the seconds and fractional seconds of the duration.
     *
     * @return an ISO-8601 representation of this duration, not null
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(24);
        buf.append("PT");
        if (seconds < 0 && nanos > 0) {
            if (seconds == -1) {
                buf.append("-0");
            } else {
                buf.append(seconds + 1);
            }
        } else {
            buf.append(seconds);
        }
        if (nanos > 0) {
            int pos = buf.length();
            if (seconds < 0) {
                buf.append(2 * NANOS_PER_SECOND - nanos);
            } else {
                buf.append(nanos + NANOS_PER_SECOND);
            }
            while (buf.charAt(buf.length() - 1) == '0') {
                buf.setLength(buf.length() - 1);
            }
            buf.setCharAt(pos, '.');
        }
        buf.append('S');
        return buf.toString();
    }

    //-----------------------------------------------------------------------
    /**
     * Writes the object using a
     * <a href="../../serialized-form.html#java.time.Ser">dedicated serialized form</a>.
     * <pre>
     *  out.writeByte(1);  // identifies this as a Duration
     *  out.writeLong(seconds);
     *  out.writeInt(nanos);
     * </pre>
     *
     * @return the instance of {@code Ser}, not null
     */
    private Object writeReplace() {
        return new Ser(Ser.DURATION_TYPE, this);
    }

    /**
     * Defend against malicious streams.
     * @return never
     * @throws InvalidObjectException always
     */
    private Object readResolve() throws ObjectStreamException {
        throw new InvalidObjectException("Deserialization via serialization delegate");
    }

    void writeExternal(DataOutput out) throws IOException {
        out.writeLong(seconds);
        out.writeInt(nanos);
    }

    static Duration readExternal(DataInput in) throws IOException {
        long seconds = in.readLong();
        int nanos = in.readInt();
        return Duration.ofSeconds(seconds, nanos);
    }

}
