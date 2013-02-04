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
package java.time.calendar;

import static java.time.calendar.MinguoChrono.YEARS_DIFFERENCE;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoLocalDate;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.util.Objects;

/**
 * A date in the Minguo calendar system.
 * <p>
 * This implements {@code ChronoLocalDate} for the {@link MinguoChrono Minguo calendar}.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
final class MinguoDate
        extends ChronoDateImpl<MinguoChrono>
        implements ChronoLocalDate<MinguoChrono>, Serializable {
    // this class is package-scoped so that future conversion to public
    // would not change serialization

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 1300372329181994526L;

    /**
     * The underlying date.
     */
    private final LocalDate isoDate;

    /**
     * Creates an instance from an ISO date.
     *
     * @param isoDate  the standard local date, validated not null
     */
    MinguoDate(LocalDate isoDate) {
        Objects.requireNonNull(isoDate, "isoDate");
        this.isoDate = isoDate;
    }

    //-----------------------------------------------------------------------
    @Override
    public MinguoChrono getChrono() {
        return MinguoChrono.INSTANCE;
    }

    @Override
    public int lengthOfMonth() {
        return isoDate.lengthOfMonth();
    }

    @Override
    public ValueRange range(TemporalField field) {
        if (field instanceof ChronoField) {
            if (isSupported(field)) {
                ChronoField f = (ChronoField) field;
                switch (f) {
                    case DAY_OF_MONTH:
                    case DAY_OF_YEAR:
                    case ALIGNED_WEEK_OF_MONTH:
                        return isoDate.range(field);
                    case YEAR_OF_ERA: {
                        ValueRange range = YEAR.range();
                        long max = (getProlepticYear() <= 0 ? -range.getMinimum() + 1 + YEARS_DIFFERENCE : range.getMaximum() - YEARS_DIFFERENCE);
                        return ValueRange.of(1, max);
                    }
                }
                return getChrono().range(f);
            }
            throw new DateTimeException("Unsupported field: " + field.getName());
        }
        return field.doRange(this);
    }

    @Override
    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case YEAR_OF_ERA: {
                    int prolepticYear = getProlepticYear();
                    return (prolepticYear >= 1 ? prolepticYear : 1 - prolepticYear);
                }
                case YEAR:
                    return getProlepticYear();
                case ERA:
                    return (getProlepticYear() >= 1 ? 1 : 0);
            }
            return isoDate.getLong(field);
        }
        return field.doGet(this);
    }

    private int getProlepticYear() {
        return isoDate.getYear() - YEARS_DIFFERENCE;
    }

    //-----------------------------------------------------------------------
    @Override
    public MinguoDate with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            if (getLong(f) == newValue) {
                return this;
            }
            switch (f) {
                case YEAR_OF_ERA:
                case YEAR:
                case ERA: {
                    f.checkValidValue(newValue);
                    int nvalue = (int) newValue;
                    switch (f) {
                        case YEAR_OF_ERA:
                            return with(isoDate.withYear(getProlepticYear() >= 1 ? nvalue + YEARS_DIFFERENCE : (1 - nvalue)  + YEARS_DIFFERENCE));
                        case YEAR:
                            return with(isoDate.withYear(nvalue + YEARS_DIFFERENCE));
                        case ERA:
                            return with(isoDate.withYear((1 - getProlepticYear()) + YEARS_DIFFERENCE));
                    }
                }
            }
            return with(isoDate.with(field, newValue));
        }
        return (MinguoDate) ChronoLocalDate.super.with(field, newValue);
    }

    //-----------------------------------------------------------------------
    @Override
    MinguoDate plusYears(long years) {
        return with(isoDate.plusYears(years));
    }

    @Override
    MinguoDate plusMonths(long months) {
        return with(isoDate.plusMonths(months));
    }

    @Override
    MinguoDate plusDays(long days) {
        return with(isoDate.plusDays(days));
    }

    private MinguoDate with(LocalDate newDate) {
        return (newDate.equals(isoDate) ? this : new MinguoDate(newDate));
    }

    @Override  // override for performance
    public long toEpochDay() {
        return isoDate.toEpochDay();
    }

    //-------------------------------------------------------------------------
    @Override  // override for performance
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MinguoDate) {
            MinguoDate otherDate = (MinguoDate) obj;
            return this.isoDate.equals(otherDate.isoDate);
        }
        return false;
    }

    @Override  // override for performance
    public int hashCode() {
        return getChrono().getId().hashCode() ^ isoDate.hashCode();
    }

    //-----------------------------------------------------------------------
    private Object writeReplace() {
        return new Ser(Ser.MINGUO_DATE_TYPE, this);
    }

    void writeExternal(DataOutput out) throws IOException {
        // MinguoChrono is implicit in the MINGUO_DATE_TYPE
        out.writeInt(get(YEAR));
        out.writeByte(get(MONTH_OF_YEAR));
        out.writeByte(get(DAY_OF_MONTH));

    }

    static ChronoLocalDate<MinguoChrono> readExternal(DataInput in) throws IOException {
        int year = in.readInt();
        int month = in.readByte();
        int dayOfMonth = in.readByte();
        return MinguoChrono.INSTANCE.date(year, month, dayOfMonth);
    }


}
