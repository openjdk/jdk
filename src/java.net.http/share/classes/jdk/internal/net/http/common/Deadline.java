/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.common;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;

/**
 * An instantaneous point on the {@linkplain TimeLine time-line}.
 * <p>
 * This class is immutable and thread-safe.
 * <p id="overflow">
 * Operations that add or subtract durations to a {@code Deadline}, whether
 * represented as a {@link Duration} or as a {@code long} time increment (such
 * as seconds or nanoseconds) do not throw on numeric overflow if the resulting
 * {@code Deadline} would exceed {@link #MAX} or be less than {@link #MIN}.
 * Instead, {@code MAX} or {@code MIN} is returned, respectively. Similarly,
 * methods that return a duration as a {@code long} will either return
 * {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE} if the returned quantity
 * would exceed the capacity of a {@code long}.
 */
public final class Deadline implements Comparable<Deadline> {

    public static final Deadline MIN = new Deadline(Instant.MIN);
    public static final Deadline MAX = new Deadline(Instant.MAX);

    private final Instant deadline;
    private Deadline(Instant deadline) {
        this.deadline = deadline;
    }


    /**
     * {@return a deadline with the specified duration in nanoseconds added}
     * <p>
     * This instance is immutable and unaffected by this method call.
     * <p>
     * On {@linkplain ##overflow numeric overflows}, this method will return
     * {@link Deadline#MAX} if the provided duration is positive,
     * {@link Deadline#MIN} otherwise.
     *
     * @param nanosToAdd  the nanoseconds to add, positive or negative
     */
    public Deadline plusNanos(long nanosToAdd) {
        if (nanosToAdd == 0) return this;
        try {
            return new Deadline(deadline.plusNanos(nanosToAdd));
        } catch (DateTimeException |        // "Instant exceeds minimum or maximum instant"
                 ArithmeticException _) {   // "long overflow"
            return nanosToAdd > 0 ? Deadline.MAX : Deadline.MIN;
        }
    }

    /**
     * Returns a copy of this {@code Deadline} truncated to the specified unit.
     * <p>
     * Truncating the deadline returns a copy of the original with fields
     * smaller than the specified unit set to zero.
     * The fields are calculated on the basis of using a UTC offset as seen
     * in {@code Instant.toString}.
     * For example, truncating with the {@link ChronoUnit#MINUTES MINUTES} unit will
     * round down to the nearest minute, setting the seconds and nanoseconds to zero.
     * <p>
     * The unit must have a {@linkplain TemporalUnit#getDuration() duration}
     * that divides into the length of a standard day without remainder.
     * This includes all supplied time units on {@link ChronoUnit} and
     * {@link ChronoUnit#DAYS DAYS}. Other units throw an exception.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param unit  the unit to truncate to, not null
     * @return a {@code Deadline} based on this deadline with the time truncated, not null
     * @throws DateTimeException if the unit is invalid for truncation
     * @throws UnsupportedTemporalTypeException if the unit is not supported
     */
    public Deadline truncatedTo(ChronoUnit unit) {
        return of(deadline.truncatedTo(unit));
    }

    /**
     * {@return a deadline with the specified amount subtracted from this deadline}
     * <p>
     * This instance is immutable and unaffected by this method call.
     * <p>
     * On {@linkplain ##overflow numeric overflows}, this method will return
     * {@link Deadline#MIN} if the provided duration is positive,
     * {@link Deadline#MAX} otherwise.
     *
     * @param duration the amount to subtract, not null
     */
    public Deadline minus(Duration duration) {
        if (duration.isZero()) return this;
        try {
            return Deadline.of(deadline.minus(duration));
        } catch (DateTimeException |        // "Instant exceeds minimum or maximum instant"
                 ArithmeticException _) {   // "long overflow"
            return duration.isPositive() ? Deadline.MIN : Deadline.MAX;
        }
    }

    /**
     * {@return a deadline with the specified amount added to this deadline}
     * <p>
     * This returns a {@code Deadline}, based on this one, with the amount
     * in terms of the unit added. If it is not possible to add the amount, because the
     * unit is not supported or for some other reason, an exception is thrown.
     * <p>
     * This instance is immutable and unaffected by this method call.
     * <p>
     * On {@linkplain ##overflow numeric overflows}, this method will return
     * {@link Deadline#MAX} if the provided amount is positive,
     * {@link Deadline#MIN} otherwise.
     *
     * @see Instant#plus(long, TemporalUnit)
     *
     * @param amountToAdd  the amount of the unit to add to the result, may be negative
     * @param unit  the unit of the amount to add, not null
     * @throws UnsupportedTemporalTypeException if the unit is not supported
     */
    public Deadline plus(long amountToAdd, TemporalUnit unit) {
        if (amountToAdd == 0) return this;
        try {
            return Deadline.of(deadline.plus(amountToAdd, unit));
        } catch (DateTimeException |        // "Instant exceeds minimum or maximum instant"
                 ArithmeticException _) {   // "long overflow"
            return amountToAdd > 0 ? Deadline.MAX : Deadline.MIN;
        }
    }

    /**
     * {@return a deadline with the specified duration in seconds added to this deadline}
     * <p>
     * This instance is immutable and unaffected by this method call.
     * <p>
     * On {@linkplain ##overflow numeric overflows}, this method will return
     * {@link Deadline#MAX} if the provided duration is positive,
     * {@link Deadline#MIN} otherwise.
     *
     * @param secondsToAdd  the seconds to add, positive or negative
     */
    public Deadline plusSeconds(long secondsToAdd) {
        if (secondsToAdd == 0) return this;
        try {
            return Deadline.of(deadline.plusSeconds(secondsToAdd));
        } catch (DateTimeException |        // "Instant exceeds minimum or maximum instant"
                 ArithmeticException _) {   // "long overflow"
            return secondsToAdd > 0 ? Deadline.MAX : Deadline.MIN;
        }
    }

    /**
     * {@return a deadline with the specified duration in milliseconds added to this deadline}
     * <p>
     * This instance is immutable and unaffected by this method call.
     * <p>
     * On {@linkplain ##overflow numeric overflows}, this method will return
     * {@link Deadline#MAX} if the provided duration is positive,
     * {@link Deadline#MIN} otherwise.
     *
     * @param millisToAdd  the milliseconds to add, positive or negative
     */
    public Deadline plusMillis(long millisToAdd) {
        if (millisToAdd ==  0) return this;
        try {
            return Deadline.of(deadline.plusMillis(millisToAdd));
        } catch (DateTimeException |        // "Instant exceeds minimum or maximum instant"
                 ArithmeticException _) {   // "long overflow"
            return millisToAdd > 0 ? Deadline.MAX : Deadline.MIN;
        }
    }

    /**
     * {@return a deadline with the specified duration added to this deadline}
     * <p>
     * This instance is immutable and unaffected by this method call.
     * <p>
     * On {@linkplain ##overflow numeric overflows}, this method will return
     * {@link Deadline#MAX} if the provided duration is positive,
     * {@link Deadline#MIN} otherwise.
     *
     * @param duration the duration to add, not null
     */
    public Deadline plus(Duration duration) {
        if (duration.isZero()) return this;
        try {
            return Deadline.of(deadline.plus(duration));
        } catch (DateTimeException |        // "Instant exceeds minimum or maximum instant"
                 ArithmeticException _) {   // "long overflow"
            return duration.isPositive() ? Deadline.MAX : Deadline.MIN;
        }
    }

    /**
     * Calculates the amount of time until another deadline in terms of the specified unit.
     * <p>
     * This calculates the amount of time between two {@code Deadline}
     * objects in terms of a single {@code TemporalUnit}.
     * The start and end points are {@code this} and the specified deadline.
     * The result will be negative if the end is before the start.
     * The calculation returns a whole number, representing the number of
     * complete units between the two deadlines.
     * <p>
     * This instance is immutable and unaffected by this method call.
     * <p>
     * On {@linkplain ##overflow numeric overflows}, this method will return
     * {@link Long#MAX_VALUE} if the current deadline is before the provided end
     * deadline, {@link Long#MIN_VALUE} otherwise.
     *
     * @param endExclusive  the end deadline, exclusive
     * @param unit  the unit to measure the amount in, not null
     * @return the amount of time between this deadline and the end deadline
     * @throws UnsupportedTemporalTypeException if the unit is not supported
     */
    public long until(Deadline endExclusive, TemporalUnit unit) {
        try {
            return deadline.until(endExclusive.deadline, unit);
        } catch (DateTimeException |        // "Instant exceeds minimum or maximum instant"
                 ArithmeticException _) {   // "long overflow"
            int delta = compareTo(endExclusive);
            return delta < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    /**
     * Checks if this deadline is after the specified deadline.
     * <p>
     * The comparison is based on the time-line position of the deadlines.
     *
     * @param otherDeadline  the other deadline to compare to, not null
     * @return true if this deadline is after the specified deadline
     * @throws NullPointerException if otherDeadline is null
     */
    public boolean isAfter(Deadline otherDeadline) {
        return compareTo(otherDeadline) > 0;
    }

    /**
     * Checks if this deadline is before the specified deadline.
     * <p>
     * The comparison is based on the time-line position of the deadlines.
     *
     * @param otherDeadline  the other deadine to compare to, not null
     * @return true if this deadline is before the specified deadine
     * @throws NullPointerException if otherDeadline is null
     */
    public boolean isBefore(Deadline otherDeadline) {
        return compareTo(otherDeadline) < 0;
    }

    @Override
    public int compareTo(Deadline o) {
        return deadline.compareTo(o.deadline);
    }

    @Override
    public String toString() {
        return "Deadline(" + deadline + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Deadline d) {
            return deadline.equals(d.deadline);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return deadline.hashCode();
    }

    Instant asInstant() {
        return deadline;
    }

    static Deadline of(Instant instant) {
        return new Deadline(instant);
    }

    /**
     * Obtains a {@code Duration} representing the duration between two deadlines.
     * <p>
     * The result of this method can be a negative period if the end is before the start.
     *
     * @param startInclusive  the start deadline, inclusive, not null
     * @param endExclusive  the end deadline, exclusive, not null
     * @return a {@code Duration}, not null
     */
    public static Duration between(Deadline startInclusive, Deadline endExclusive) {
        if (startInclusive.equals(endExclusive)) return Duration.ZERO;
        // `Deadline` works with `Instant` under the hood.
        // Delta between `Instant.MIN` and `Instant.MAX` fits in a `Duration`.
        // Hence, we should never receive a numeric overflow while calculating the delta between two deadlines.
        return Duration.between(startInclusive.deadline, endExclusive.deadline);
    }

}
