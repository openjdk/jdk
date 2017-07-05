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

import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.Chrono;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoLocalDate;
import java.time.temporal.Era;
import java.time.temporal.ISOChrono;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.ValueRange;
import java.time.temporal.Year;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sun.util.calendar.CalendarSystem;
import sun.util.calendar.LocalGregorianCalendar;

/**
 * The Japanese Imperial calendar system.
 * <p>
 * This chronology defines the rules of the Japanese Imperial calendar system.
 * This calendar system is primarily used in Japan.
 * The Japanese Imperial calendar system is the same as the ISO calendar system
 * apart from the era-based year numbering.
 * <p>
 * Only Meiji (1865-04-07 - 1868-09-07) and later eras are supported.
 * Older eras are handled as an unknown era where the year-of-era is the ISO year.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
public final class JapaneseChrono extends Chrono<JapaneseChrono> implements Serializable {
    // TODO: definition for unknown era may break requirement that year-of-era >= 1

    static final LocalGregorianCalendar JCAL =
        (LocalGregorianCalendar) CalendarSystem.forName("japanese");

    // Locale for creating a JapaneseImpericalCalendar.
    static final Locale LOCALE = Locale.forLanguageTag("ja-JP-u-ca-japanese");

    /**
     * Singleton instance for Japanese chronology.
     */
    public static final JapaneseChrono INSTANCE = new JapaneseChrono();

    /**
     * The singleton instance for the before Meiji era ( - 1868-09-07)
     * which has the value -999.
     */
    public static final Era<JapaneseChrono> ERA_SEIREKI = JapaneseEra.SEIREKI;
    /**
     * The singleton instance for the Meiji era (1868-09-08 - 1912-07-29)
     * which has the value -1.
     */
    public static final Era<JapaneseChrono> ERA_MEIJI = JapaneseEra.MEIJI;
    /**
     * The singleton instance for the Taisho era (1912-07-30 - 1926-12-24)
     * which has the value 0.
     */
    public static final Era<JapaneseChrono> ERA_TAISHO = JapaneseEra.TAISHO;
    /**
     * The singleton instance for the Showa era (1926-12-25 - 1989-01-07)
     * which has the value 1.
     */
    public static final Era<JapaneseChrono> ERA_SHOWA = JapaneseEra.SHOWA;
    /**
     * The singleton instance for the Heisei era (1989-01-08 - current)
     * which has the value 2.
     */
    public static final Era<JapaneseChrono> ERA_HEISEI = JapaneseEra.HEISEI;
    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 459996390165777884L;

    //-----------------------------------------------------------------------
    /**
     * Restricted constructor.
     */
    private JapaneseChrono() {
    }

    /**
     * Resolve singleton.
     *
     * @return the singleton instance, not null
     */
    private Object readResolve() {
        return INSTANCE;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the ID of the chronology - 'Japanese'.
     * <p>
     * The ID uniquely identifies the {@code Chrono}.
     * It can be used to lookup the {@code Chrono} using {@link #of(String)}.
     *
     * @return the chronology ID - 'Japanese'
     * @see #getCalendarType()
     */
    @Override
    public String getId() {
        return "Japanese";
    }

    /**
     * Gets the calendar type of the underlying calendar system - 'japanese'.
     * <p>
     * The calendar type is an identifier defined by the
     * <em>Unicode Locale Data Markup Language (LDML)</em> specification.
     * It can be used to lookup the {@code Chrono} using {@link #of(String)}.
     * It can also be used as part of a locale, accessible via
     * {@link Locale#getUnicodeLocaleType(String)} with the key 'ca'.
     *
     * @return the calendar system type - 'japanese'
     * @see #getId()
     */
    @Override
    public String getCalendarType() {
        return "japanese";
    }

    //-----------------------------------------------------------------------
    @Override
    public ChronoLocalDate<JapaneseChrono> date(Era<JapaneseChrono> era, int yearOfEra, int month, int dayOfMonth) {
        if (era instanceof JapaneseEra == false) {
            throw new DateTimeException("Era must be JapaneseEra");
        }
        return JapaneseDate.of((JapaneseEra) era, yearOfEra, month, dayOfMonth);
    }

    @Override
    public ChronoLocalDate<JapaneseChrono> date(int prolepticYear, int month, int dayOfMonth) {
        return new JapaneseDate(LocalDate.of(prolepticYear, month, dayOfMonth));
    }

    @Override
    public ChronoLocalDate<JapaneseChrono> dateYearDay(int prolepticYear, int dayOfYear) {
        LocalDate date = LocalDate.ofYearDay(prolepticYear, dayOfYear);
        return date(prolepticYear, date.getMonthValue(), date.getDayOfMonth());
    }

    @Override
    public ChronoLocalDate<JapaneseChrono> date(TemporalAccessor temporal) {
        if (temporal instanceof JapaneseDate) {
            return (JapaneseDate) temporal;
        }
        return new JapaneseDate(LocalDate.from(temporal));
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the specified year is a leap year.
     * <p>
     * Japanese calendar leap years occur exactly in line with ISO leap years.
     * This method does not validate the year passed in, and only has a
     * well-defined result for years in the supported range.
     *
     * @param prolepticYear  the proleptic-year to check, not validated for range
     * @return true if the year is a leap year
     */
    @Override
    public boolean isLeapYear(long prolepticYear) {
        return ISOChrono.INSTANCE.isLeapYear(prolepticYear);
    }

    @Override
    public int prolepticYear(Era<JapaneseChrono> era, int yearOfEra) {
        if (era instanceof JapaneseEra == false) {
            throw new DateTimeException("Era must be JapaneseEra");
        }
        JapaneseEra jera = (JapaneseEra) era;
        int gregorianYear = jera.getPrivateEra().getSinceDate().getYear() + yearOfEra - 1;
        if (yearOfEra == 1) {
            return gregorianYear;
        }
        LocalGregorianCalendar.Date jdate = JCAL.newCalendarDate(null);
        jdate.setEra(jera.getPrivateEra()).setDate(yearOfEra, 1, 1);
        JCAL.normalize(jdate);
        if (jdate.getNormalizedYear() == gregorianYear) {
            return gregorianYear;
        }
        throw new DateTimeException("invalid yearOfEra value");
    }

    /**
     * Returns the calendar system era object from the given numeric value.
     *
     * See the description of each Era for the numeric values of:
     * {@link #ERA_HEISEI}, {@link #ERA_SHOWA},{@link #ERA_TAISHO},
     * {@link #ERA_MEIJI}), only Meiji and later eras are supported.
     * Prior to Meiji {@link #ERA_SEIREKI} is used.
     *
     * @param eraValue  the era value
     * @return the Japanese {@code Era} for the given numeric era value
     * @throws DateTimeException if {@code eraValue} is invalid
     */
    @Override
    public Era<JapaneseChrono> eraOf(int eraValue) {
        return JapaneseEra.of(eraValue);
    }

    @Override
    public List<Era<JapaneseChrono>> eras() {
        return Arrays.<Era<JapaneseChrono>>asList(JapaneseEra.values());
    }

    //-----------------------------------------------------------------------
    @Override
    public ValueRange range(ChronoField field) {
        switch (field) {
            case DAY_OF_MONTH:
            case DAY_OF_WEEK:
            case MICRO_OF_DAY:
            case MICRO_OF_SECOND:
            case HOUR_OF_DAY:
            case HOUR_OF_AMPM:
            case MINUTE_OF_DAY:
            case MINUTE_OF_HOUR:
            case SECOND_OF_DAY:
            case SECOND_OF_MINUTE:
            case MILLI_OF_DAY:
            case MILLI_OF_SECOND:
            case NANO_OF_DAY:
            case NANO_OF_SECOND:
            case CLOCK_HOUR_OF_DAY:
            case CLOCK_HOUR_OF_AMPM:
            case EPOCH_DAY:
            case EPOCH_MONTH:
                return field.range();
        }
        Calendar jcal = Calendar.getInstance(LOCALE);
        int fieldIndex;
        switch (field) {
            case ERA:
                return ValueRange.of(jcal.getMinimum(Calendar.ERA) - JapaneseEra.ERA_OFFSET,
                        jcal.getMaximum(Calendar.ERA) - JapaneseEra.ERA_OFFSET);
            case YEAR:
            case YEAR_OF_ERA:
                return ValueRange.of(Year.MIN_VALUE, jcal.getGreatestMinimum(Calendar.YEAR),
                        jcal.getLeastMaximum(Calendar.YEAR), Year.MAX_VALUE);
            case MONTH_OF_YEAR:
                return ValueRange.of(jcal.getMinimum(Calendar.MONTH) + 1, jcal.getGreatestMinimum(Calendar.MONTH) + 1,
                        jcal.getLeastMaximum(Calendar.MONTH) + 1, jcal.getMaximum(Calendar.MONTH) + 1);
            case DAY_OF_YEAR:
                fieldIndex = Calendar.DAY_OF_YEAR;
                break;
            default:
                 // TODO: review the remaining fields
                throw new UnsupportedOperationException("Unimplementable field: " + field);
        }
        return ValueRange.of(jcal.getMinimum(fieldIndex), jcal.getGreatestMinimum(fieldIndex),
                jcal.getLeastMaximum(fieldIndex), jcal.getMaximum(fieldIndex));
    }

}
