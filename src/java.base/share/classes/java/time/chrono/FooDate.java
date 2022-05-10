/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.time.chrono;

import java.time.temporal.*;

/**
 * Super interface for experimentation.
 */
public interface FooDate {
    /**
     * {@return the chronology}
     */
    Chronology getChronology();

    /**
     * {@return the era}
     */
    Era getEra();

    /**
     * {@return the length of month}
     */
    int lengthOfMonth();

    /**
     * {@return an object of the same type as this object with the
     * specified period subtracted}
     * @param amountToAdd the amount of the specified unit to
     * subtract, may be negative
     * @param unit the unit of the amount to subtract, not null
     */
    FooDate minus(long amountToAdd, TemporalUnit unit);

    /**
     * {@return an object of the same type as this object with an
     * amount subtracted}
     * @param amount the amount to subtract, not null
     */
    FooDate minus(TemporalAmount amount);

    /**
     * {@return an object of the same type as this object with the
     * specified period added}
     * @param amountToAdd the amount of the specified unit to
     * add, may be negative
     * @param unit the unit of the amount to add, not null
     */
    FooDate plus(long amountToAdd, TemporalUnit unit);

    /**
     * {@return an object of the same type as this object with an
     * amount added}
     * @param amount the amount to add, not null
     */
    FooDate plus(TemporalAmount amount);

    /**
     * {@return Calculates the period between this date and another
     * date as a ChronoPeriod}
     * @param endDate the end date, exclusive, which may be in any
     * chronology, not null
     */
    ChronoPeriod until(ChronoLocalDate endDate);

    /**
     * {@return Calculates the amount of time until another date in
     * terms of the specified unit}
     * @param endExclusive the end date, exclusive, which is converted
     * to a ChronoLocalDate in the same chronology, not null
     * @param unit the unit to measure the amount in, not null
     */
    long until(Temporal endExclusive, TemporalUnit unit);

    /**
     * {@return an adjusted object of the same type as this object
     * with the adjustment made}
     * @param adjuster the adjuster to use, not null
     */
    FooDate with(TemporalAdjuster adjuster);

    /**
     * {@return an object of the same type as this object with the
     * specified field altered}
     * @param field the field to set in the result, not null
     * @param newValue the new value of the field in the result
     */
    FooDate with(TemporalField field, long newValue);
}