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

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoLocalDate;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.util.Calendar;
import java.util.Objects;

import sun.util.calendar.LocalGregorianCalendar;

/**
 * A date in the Japanese Imperial calendar system.
 * <p>
 * This implements {@code ChronoLocalDate} for the
 * {@linkplain JapaneseChrono Japanese Imperial calendar}.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
final class JapaneseDate
        extends ChronoDateImpl<JapaneseChrono>
        implements ChronoLocalDate<JapaneseChrono>, Serializable {
    // this class is package-scoped so that future conversion to public
    // would not change serialization

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -305327627230580483L;

    /**
     * The underlying ISO local date.
     */
    private transient final LocalDate isoDate;
    /**
     * The JapaneseEra of this date.
     */
    private transient JapaneseEra era;
    /**
     * The Japanese imperial calendar year of this date.
     */
    private transient int yearOfEra;

    /**
     * Obtains an instance of {@code JapaneseDate} from the era, year-of-era,
     * month-of-year and day-of-month.
     *
     * @param era  the era to represent, not null
     * @param yearOfEra  the year-of-era to represent
     * @param month  the month-of-year to represent
     * @param dayOfMonth  the day-of-month to represent, from 1 to 31
     * @return the Japanese date, never null
     * @throws DateTimeException if the value of any field is out of range, or
     *                           if the day-of-month is invalid for the month-year
     */
    static JapaneseDate of(JapaneseEra era, int yearOfEra, int month, int dayOfMonth) {
        Objects.requireNonNull(era, "era");
        LocalGregorianCalendar.Date jdate = JapaneseChrono.JCAL.newCalendarDate(null);
        jdate.setEra(era.getPrivateEra()).setDate(yearOfEra, month, dayOfMonth);
        if (!JapaneseChrono.JCAL.validate(jdate)) {
            throw new IllegalArgumentException();
        }
        LocalDate date = LocalDate.of(jdate.getNormalizedYear(), month, dayOfMonth);
        return new JapaneseDate(era, yearOfEra, date);
    }

    //-----------------------------------------------------------------------
    /**
     * Creates an instance from an ISO date.
     *
     * @param isoDate  the standard local date, validated not null
     */
    JapaneseDate(LocalDate isoDate) {
        LocalGregorianCalendar.Date jdate = toPrivateJapaneseDate(isoDate);
        this.era = JapaneseEra.toJapaneseEra(jdate.getEra());
        this.yearOfEra = jdate.getYear();
        this.isoDate = isoDate;
    }

    /**
     * Constructs a {@code JapaneseDate}. This constructor does NOT validate the given parameters,
     * and {@code era} and {@code year} must agree with {@code isoDate}.
     *
     * @param era  the era, validated not null
     * @param year  the year-of-era, validated
     * @param isoDate  the standard local date, validated not null
     */
    JapaneseDate(JapaneseEra era, int year, LocalDate isoDate) {
        this.era = era;
        this.yearOfEra = year;
        this.isoDate = isoDate;
    }

    //-----------------------------------------------------------------------
    @Override
    public JapaneseChrono getChrono() {
        return JapaneseChrono.INSTANCE;
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
                    case DAY_OF_YEAR:
                        return actualRange(Calendar.DAY_OF_YEAR);
                    case YEAR_OF_ERA:
                        return actualRange(Calendar.YEAR);
                }
                return getChrono().range(f);
            }
            throw new DateTimeException("Unsupported field: " + field.getName());
        }
        return field.doRange(this);
    }

    private ValueRange actualRange(int calendarField) {
        Calendar jcal = Calendar.getInstance(JapaneseChrono.LOCALE);
        jcal.set(Calendar.ERA, era.getValue() + JapaneseEra.ERA_OFFSET);
        jcal.set(yearOfEra, isoDate.getMonthValue() - 1, isoDate.getDayOfMonth());
        return ValueRange.of(jcal.getActualMinimum(calendarField),
                jcal.getActualMaximum(calendarField));
    }

    @Override
    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case YEAR_OF_ERA:
                    return yearOfEra;
                case ERA:
                    return era.getValue();
                case DAY_OF_YEAR: {
                    LocalGregorianCalendar.Date jdate = toPrivateJapaneseDate(isoDate);
                    return JapaneseChrono.JCAL.getDayOfYear(jdate);
                }
            }
            // TODO: review other fields
            return isoDate.getLong(field);
        }
        return field.doGet(this);
    }

    /**
     * Returns a {@code LocalGregorianCalendar.Date} converted from the given {@code isoDate}.
     *
     * @param isoDate  the local date, not null
     * @return a {@code LocalGregorianCalendar.Date}, not null
     */
    private static LocalGregorianCalendar.Date toPrivateJapaneseDate(LocalDate isoDate) {
        LocalGregorianCalendar.Date jdate = JapaneseChrono.JCAL.newCalendarDate(null);
        sun.util.calendar.Era sunEra = JapaneseEra.privateEraFrom(isoDate);
        int year = isoDate.getYear();
        if (sunEra != null) {
            year -= sunEra.getSinceDate().getYear() - 1;
        }
        jdate.setEra(sunEra).setYear(year).setMonth(isoDate.getMonthValue()).setDayOfMonth(isoDate.getDayOfMonth());
        JapaneseChrono.JCAL.normalize(jdate);
        return jdate;
    }

    //-----------------------------------------------------------------------
    @Override
    public JapaneseDate with(TemporalField field, long newValue) {
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
                            return this.withYear(nvalue);
                        case YEAR:
                            return with(isoDate.withYear(nvalue));
                        case ERA: {
                            return this.withYear(JapaneseEra.of(nvalue), yearOfEra);
                        }
                    }
                }
            }
            // TODO: review other fields, such as WEEK_OF_YEAR
            return with(isoDate.with(field, newValue));
        }
        return (JapaneseDate) ChronoLocalDate.super.with(field, newValue);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this date with the year altered.
     * <p>
     * This method changes the year of the date.
     * If the month-day is invalid for the year, then the previous valid day
     * will be selected instead.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param era  the era to set in the result, not null
     * @param yearOfEra  the year-of-era to set in the returned date
     * @return a {@code JapaneseDate} based on this date with the requested year, never null
     * @throws DateTimeException if {@code year} is invalid
     */
    private JapaneseDate withYear(JapaneseEra era, int yearOfEra) {
        int year = JapaneseChrono.INSTANCE.prolepticYear(era, yearOfEra);
        return with(isoDate.withYear(year));
    }

    /**
     * Returns a copy of this date with the year-of-era altered.
     * <p>
     * This method changes the year-of-era of the date.
     * If the month-day is invalid for the year, then the previous valid day
     * will be selected instead.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param year  the year to set in the returned date
     * @return a {@code JapaneseDate} based on this date with the requested year-of-era, never null
     * @throws DateTimeException if {@code year} is invalid
     */
    private JapaneseDate withYear(int year) {
        return withYear((JapaneseEra) getEra(), year);
    }

    //-----------------------------------------------------------------------
    @Override
    JapaneseDate plusYears(long years) {
        return with(isoDate.plusYears(years));
    }

    @Override
    JapaneseDate plusMonths(long months) {
        return with(isoDate.plusMonths(months));
    }

    @Override
    JapaneseDate plusDays(long days) {
        return with(isoDate.plusDays(days));
    }

    private JapaneseDate with(LocalDate newDate) {
        return (newDate.equals(isoDate) ? this : new JapaneseDate(newDate));
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
        if (obj instanceof JapaneseDate) {
            JapaneseDate otherDate = (JapaneseDate) obj;
            return this.isoDate.equals(otherDate.isoDate);
        }
        return false;
    }

    @Override  // override for performance
    public int hashCode() {
        return getChrono().getId().hashCode() ^ isoDate.hashCode();
    }

    @Override
    public String toString() {
        if (era == JapaneseEra.SEIREKI) {
            return getChrono().getId() + " " + isoDate.toString();
        }
        return super.toString();
    }

    //-----------------------------------------------------------------------
    private Object writeReplace() {
        return new Ser(Ser.JAPANESE_DATE_TYPE, this);
    }

    void writeExternal(DataOutput out) throws IOException {
        // JapaneseChrono is implicit in the JAPANESE_DATE_TYPE
        out.writeInt(get(YEAR));
        out.writeByte(get(MONTH_OF_YEAR));
        out.writeByte(get(DAY_OF_MONTH));
    }

    static ChronoLocalDate<JapaneseChrono> readExternal(DataInput in) throws IOException {
        int year = in.readInt();
        int month = in.readByte();
        int dayOfMonth = in.readByte();
        return JapaneseChrono.INSTANCE.date(year, month, dayOfMonth);
    }


}
