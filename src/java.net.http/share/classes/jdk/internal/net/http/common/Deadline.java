/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;

/**
 * A Deadline represents an instant on a {@linkplain TimeLine time line}.
 */
public final class Deadline implements Comparable<Deadline> {

    public static final Deadline MIN = new Deadline(Instant.MIN);
    public static final Deadline MAX = new Deadline(Instant.MAX);

    private final Instant deadline;
    private Deadline(Instant deadline) {
        this.deadline = deadline;
    }


    /**
     * Returns a copy of this deadline with the specified duration in nanoseconds added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param nanosToAdd  the nanoseconds to add, positive or negative
     * @return a {@code Deadline} based on this deadline with the specified nanoseconds added, not null
     * @throws DateTimeException if the result exceeds the maximum or minimum deadline
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Deadline plusNanos(long nanosToAdd) {
        return new Deadline(deadline.plusNanos(nanosToAdd));
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
     * Returns a copy of this deadline with the specified amount added.
     * <p>
     * This returns a {@code Deadline}, based on this one, with the amount
     * in terms of the unit added. If it is not possible to add the amount, because the
     * unit is not supported or for some other reason, an exception is thrown.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @see Instant#plus(long, TemporalUnit)
     *
     * @param amountToAdd  the amount of the unit to add to the result, may be negative
     * @param unit  the unit of the amount to add, not null
     * @return a {@code Deadline} based on this deadline with the specified amount added, not null
     * @throws DateTimeException if the addition cannot be made
     * @throws UnsupportedTemporalTypeException if the unit is not supported
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Deadline plus(long amountToAdd, TemporalUnit unit) {
        if (amountToAdd == 0) return this;
        return Deadline.of(deadline.plus(amountToAdd, unit));
    }

    /**
     * Returns a copy of this deadline with the specified duration in seconds added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param secondsToAdd  the seconds to add, positive or negative
     * @return a {@code Deadline} based on this deadline with the specified seconds added, not null
     * @throws DateTimeException if the result exceeds the maximum or minimum deadline
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Deadline plusSeconds(long secondsToAdd) {
        if (secondsToAdd == 0) return this;
        return Deadline.of(deadline.plusSeconds(secondsToAdd));
    }

    /**
     * Returns a copy of this deadline with the specified amount added.
     * <p>
     * This returns a {@code Deadline}, based on this one, with the specified amount added.
     * The amount is typically {@link Duration} but may be any other type implementing
     * the {@link TemporalAmount} interface.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param amountToAdd  the amount to add, not null
     * @return a {@code Deadline} based on this deadline with the addition made, not null
     * @throws DateTimeException if the addition cannot be made
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Deadline plus(TemporalAmount amountToAdd) {
        return Deadline.of(deadline.plus(amountToAdd));
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
     *
     * @param endExclusive  the end deadline, exclusive
     * @param unit  the unit to measure the amount in, not null
     * @return the amount of time between this deadline and the end deadline
     * @throws DateTimeException if the amount cannot be calculated
     * @throws UnsupportedTemporalTypeException if the unit is not supported
     * @throws ArithmeticException if numeric overflow occurs
     */
    public long until(Deadline endExclusive, TemporalUnit unit) {
        return deadline.until(endExclusive.deadline, unit);
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
     * The comparison is based on the time-line position of the deadines.
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

    static Deadline of(Instant instant) {
        return new Deadline(instant);
    }
}
