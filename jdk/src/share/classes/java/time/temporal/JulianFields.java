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
 * Copyright (c) 2012, Stephen Colebourne & Michael Nascimento Santos
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
package java.time.temporal;

import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.FOREVER;

import java.io.InvalidObjectException;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeBuilder;

/**
 * A set of date fields that provide access to Julian Days.
 * <p>
 * The Julian Day is a standard way of expressing date and time commonly used in the scientific community.
 * It is expressed as a decimal number of whole days where days start at midday.
 * This class represents variations on Julian Days that count whole days from midnight.
 *
 * <h3>Specification for implementors</h3>
 * This is an immutable and thread-safe class.
 *
 * @since 1.8
 */
public final class JulianFields {

    /**
     * The offset from Julian to EPOCH DAY.
     */
    private static final long JULIAN_DAY_OFFSET = 2440588L;

    /**
     * Julian Day field.
     * <p>
     * This is an integer-based version of the Julian Day Number.
     * Julian Day is a well-known system that represents the count of whole days since day 0,
     * which is defined to be January 1, 4713 BCE in the Julian calendar, and -4713-11-24 Gregorian.
     * The field  has "JulianDay" as 'name', and 'DAYS' as 'baseUnit'.
     * The field always refers to the local date-time, ignoring the offset or zone.
     * <p>
     * For date-times, 'JULIAN_DAY.doGet()' assumes the same value from
     * midnight until just before the next midnight.
     * When 'JULIAN_DAY.doWith()' is applied to a date-time, the time of day portion remains unaltered.
     * 'JULIAN_DAY.doWith()' and 'JULIAN_DAY.doGet()' only apply to {@code Temporal} objects that
     * can be converted into {@link ChronoField#EPOCH_DAY}.
     * A {@link DateTimeException} is thrown for any other type of object.
     * <p>
     * <h3>Astronomical and Scientific Notes</h3>
     * The standard astronomical definition uses a fraction to indicate the time-of-day,
     * thus 3.25 would represent the time 18:00, since days start at midday.
     * This implementation uses an integer and days starting at midnight.
     * The integer value for the Julian Day Number is the astronomical Julian Day value at midday
     * of the date in question.
     * This amounts to the astronomical Julian Day, rounded to an integer {@code JDN = floor(JD + 0.5)}.
     * <p>
     * <pre>
     *  | ISO date          |  Julian Day Number | Astronomical Julian Day |
     *  | 1970-01-01T00:00  |         2,440,588  |         2,440,587.5     |
     *  | 1970-01-01T06:00  |         2,440,588  |         2,440,587.75    |
     *  | 1970-01-01T12:00  |         2,440,588  |         2,440,588.0     |
     *  | 1970-01-01T18:00  |         2,440,588  |         2,440,588.25    |
     *  | 1970-01-02T00:00  |         2,440,589  |         2,440,588.5     |
     *  | 1970-01-02T06:00  |         2,440,589  |         2,440,588.75    |
     *  | 1970-01-02T12:00  |         2,440,589  |         2,440,589.0     |
     * </pre>
     * <p>
     * Julian Days are sometimes taken to imply Universal Time or UTC, but this
     * implementation always uses the Julian Day number for the local date,
     * regardless of the offset or time-zone.
     */
    public static final TemporalField JULIAN_DAY = new Field("JulianDay", DAYS, FOREVER, JULIAN_DAY_OFFSET);

    /**
     * Modified Julian Day field.
     * <p>
     * This is an integer-based version of the Modified Julian Day Number.
     * Modified Julian Day (MJD) is a well-known system that counts days continuously.
     * It is defined relative to astronomical Julian Day as  {@code MJD = JD - 2400000.5}.
     * Each Modified Julian Day runs from midnight to midnight.
     * The field always refers to the local date-time, ignoring the offset or zone.
     * <p>
     * For date-times, 'MODIFIED_JULIAN_DAY.doGet()' assumes the same value from
     * midnight until just before the next midnight.
     * When 'MODIFIED_JULIAN_DAY.doWith()' is applied to a date-time, the time of day portion remains unaltered.
     * 'MODIFIED_JULIAN_DAY.doWith()' and 'MODIFIED_JULIAN_DAY.doGet()' only apply to {@code Temporal} objects
     * that can be converted into {@link ChronoField#EPOCH_DAY}.
     * A {@link DateTimeException} is thrown for any other type of object.
     * <p>
     * This implementation is an integer version of MJD with the decimal part rounded to floor.
     * <p>
     * <h3>Astronomical and Scientific Notes</h3>
     * <pre>
     *  | ISO date          | Modified Julian Day |      Decimal MJD |
     *  | 1970-01-01T00:00  |             40,587  |       40,587.0   |
     *  | 1970-01-01T06:00  |             40,587  |       40,587.25  |
     *  | 1970-01-01T12:00  |             40,587  |       40,587.5   |
     *  | 1970-01-01T18:00  |             40,587  |       40,587.75  |
     *  | 1970-01-02T00:00  |             40,588  |       40,588.0   |
     *  | 1970-01-02T06:00  |             40,588  |       40,588.25  |
     *  | 1970-01-02T12:00  |             40,588  |       40,588.5   |
     * </pre>
     * <p>
     * Modified Julian Days are sometimes taken to imply Universal Time or UTC, but this
     * implementation always uses the Modified Julian Day for the local date,
     * regardless of the offset or time-zone.
     */
    public static final TemporalField MODIFIED_JULIAN_DAY = new Field("ModifiedJulianDay", DAYS, FOREVER, 40587L);

    /**
     * Rata Die field.
     * <p>
     * Rata Die counts whole days continuously starting day 1 at midnight at the beginning of 0001-01-01 (ISO).
     * The field always refers to the local date-time, ignoring the offset or zone.
     * <p>
     * For date-times, 'RATA_DIE.doGet()' assumes the same value from
     * midnight until just before the next midnight.
     * When 'RATA_DIE.doWith()' is applied to a date-time, the time of day portion remains unaltered.
     * 'MODIFIED_JULIAN_DAY.doWith()' and 'RATA_DIE.doGet()' only apply to {@code Temporal} objects
     * that can be converted into {@link ChronoField#EPOCH_DAY}.
     * A {@link DateTimeException} is thrown for any other type of object.
     */
    public static final TemporalField RATA_DIE = new Field("RataDie", DAYS, FOREVER, 719163L);

    /**
     * Restricted constructor.
     */
    private JulianFields() {
        throw new AssertionError("Not instantiable");
    }

    /**
     * implementation of JulianFields.  Each instance is a singleton.
     */
    private static class Field implements TemporalField, Serializable {

        private static final long serialVersionUID = -7501623920830201812L;

        private final String name;
        private final transient TemporalUnit baseUnit;
        private final transient TemporalUnit rangeUnit;
        private final transient ValueRange range;
        private final transient long offset;

        private Field(String name, TemporalUnit baseUnit, TemporalUnit rangeUnit, long offset) {
            this.name = name;
            this.baseUnit = baseUnit;
            this.rangeUnit = rangeUnit;
            this.range = ValueRange.of(-365243219162L + offset, 365241780471L + offset);
            this.offset = offset;
        }


        /**
         * Resolve the object from the stream to the appropriate singleton.
         * @return one of the singleton objects {@link #JULIAN_DAY},
         *     {@link #MODIFIED_JULIAN_DAY}, or {@link #RATA_DIE}.
         * @throws InvalidObjectException if the object in the stream is not one of the singletons.
         */
        private Object readResolve() throws InvalidObjectException {
            if (JULIAN_DAY.getName().equals(name)) {
                return JULIAN_DAY;
            } else if (MODIFIED_JULIAN_DAY.getName().equals(name)) {
                return MODIFIED_JULIAN_DAY;
            } else if (RATA_DIE.getName().equals(name)) {
                return RATA_DIE;
            } else {
                throw new InvalidObjectException("Not one of the singletons");
            }
        }

        //-----------------------------------------------------------------------
        @Override
        public String getName() {
            return name;
        }

        @Override
        public TemporalUnit getBaseUnit() {
            return baseUnit;
        }

        @Override
        public TemporalUnit getRangeUnit() {
            return rangeUnit;
        }

        @Override
        public ValueRange range() {
            return range;
        }

        //-----------------------------------------------------------------------
        @Override
        public boolean doIsSupported(TemporalAccessor temporal) {
            return temporal.isSupported(EPOCH_DAY);
        }

        @Override
        public ValueRange doRange(TemporalAccessor temporal) {
            if (doIsSupported(temporal) == false) {
                throw new DateTimeException("Unsupported field: " + this);
            }
            return range();
        }

        @Override
        public long doGet(TemporalAccessor temporal) {
            return temporal.getLong(EPOCH_DAY) + offset;
        }

        @Override
        public <R extends Temporal> R doWith(R temporal, long newValue) {
            if (range().isValidValue(newValue) == false) {
                throw new DateTimeException("Invalid value: " + name + " " + newValue);
            }
            return (R) temporal.with(EPOCH_DAY, Math.subtractExact(newValue, offset));
        }

        //-----------------------------------------------------------------------
        @Override
        public boolean resolve(DateTimeBuilder builder, long value) {
            boolean changed = false;
            changed = resolve0(JULIAN_DAY, builder, changed);
            changed = resolve0(MODIFIED_JULIAN_DAY, builder, changed);
            changed = resolve0(RATA_DIE, builder, changed);
            return changed;
        }

        private boolean resolve0(TemporalField field, DateTimeBuilder builder, boolean changed) {
            if (builder.containsFieldValue(field)) {
                builder.addCalendrical(LocalDate.ofEpochDay(Math.subtractExact(builder.getFieldValue(JULIAN_DAY), JULIAN_DAY_OFFSET)));
                builder.removeFieldValue(JULIAN_DAY);
                changed = true;
            }
            return changed;
        }

        //-----------------------------------------------------------------------
        @Override
        public String toString() {
            return getName();
        }
    }
}
