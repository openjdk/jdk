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
package java.time.format;

import static java.time.temporal.Adjusters.nextOrSame;
import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR;
import static java.time.temporal.ChronoField.AMPM_OF_DAY;
import static java.time.temporal.ChronoField.CLOCK_HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.CLOCK_HOUR_OF_DAY;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoField.EPOCH_MONTH;
import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MICRO_OF_DAY;
import static java.time.temporal.ChronoField.MICRO_OF_SECOND;
import static java.time.temporal.ChronoField.MILLI_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_DAY;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_DAY;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.Era;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseChronology;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Queries;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builder that can holds date and time fields and related date and time objects.
 * <p>
 * <b>This class still needs major revision before JDK1.8 ships.</b>
 * <p>
 * The builder is used to hold onto different elements of date and time.
 * It holds two kinds of object:
 * <p><ul>
 * <li>a {@code Map} from {@link TemporalField} to {@code long} value, where the
 *  value may be outside the valid range for the field
 * <li>a list of objects, such as {@code Chronology} or {@code ZoneId}
 * </ul><p>
 *
 * <h3>Specification for implementors</h3>
 * This class is mutable and not thread-safe.
 * It should only be used from a single thread.
 *
 * @since 1.8
 */
final class DateTimeBuilder
        implements TemporalAccessor, Cloneable {

    /**
     * The map of other fields.
     */
    private Map<TemporalField, Long> otherFields;
    /**
     * The map of date-time fields.
     */
    private final EnumMap<ChronoField, Long> standardFields = new EnumMap<ChronoField, Long>(ChronoField.class);
    /**
     * The chronology.
     */
    private Chronology chrono;
    /**
     * The zone.
     */
    private ZoneId zone;
    /**
     * The date.
     */
    private LocalDate date;
    /**
     * The time.
     */
    private LocalTime time;

    //-----------------------------------------------------------------------
    /**
     * Creates an empty instance of the builder.
     */
    public DateTimeBuilder() {
    }

    //-----------------------------------------------------------------------
    private Long getFieldValue0(TemporalField field) {
        if (field instanceof ChronoField) {
            return standardFields.get(field);
        } else if (otherFields != null) {
            return otherFields.get(field);
        }
        return null;
    }

    /**
     * Adds a field-value pair to the builder.
     * <p>
     * This adds a field to the builder.
     * If the field is not already present, then the field-value pair is added to the map.
     * If the field is already present and it has the same value as that specified, no action occurs.
     * If the field is already present and it has a different value to that specified, then
     * an exception is thrown.
     *
     * @param field  the field to add, not null
     * @param value  the value to add, not null
     * @return {@code this}, for method chaining
     * @throws DateTimeException if the field is already present with a different value
     */
    DateTimeBuilder addFieldValue(TemporalField field, long value) {
        Objects.requireNonNull(field, "field");
        Long old = getFieldValue0(field);  // check first for better error message
        if (old != null && old.longValue() != value) {
            throw new DateTimeException("Conflict found: " + field + " " + old + " differs from " + field + " " + value + ": " + this);
        }
        return putFieldValue0(field, value);
    }

    private DateTimeBuilder putFieldValue0(TemporalField field, long value) {
        if (field instanceof ChronoField) {
            standardFields.put((ChronoField) field, value);
        } else {
            if (otherFields == null) {
                otherFields = new LinkedHashMap<TemporalField, Long>();
            }
            otherFields.put(field, value);
        }
        return this;
    }

    //-----------------------------------------------------------------------
    void addObject(Chronology chrono) {
        this.chrono = chrono;
    }

    void addObject(ZoneId zone) {
        this.zone = zone;
    }

    void addObject(LocalDate date) {
        this.date = date;
    }

    void addObject(LocalTime time) {
        this.time = time;
    }

    //-----------------------------------------------------------------------
    /**
     * Resolves the builder, evaluating the date and time.
     * <p>
     * This examines the contents of the builder and resolves it to produce the best
     * available date and time, throwing an exception if a problem occurs.
     * Calling this method changes the state of the builder.
     *
     * @return {@code this}, for method chaining
     */
    DateTimeBuilder resolve() {
        // handle standard fields
        mergeDate();
        mergeTime();
        // TODO: cross validate remaining fields?
        return this;
    }

    private void mergeDate() {
        if (standardFields.containsKey(EPOCH_DAY)) {
            checkDate(LocalDate.ofEpochDay(standardFields.remove(EPOCH_DAY)));
            return;
        }

        Era era = null;
        if (chrono == IsoChronology.INSTANCE) {
            // normalize fields
            if (standardFields.containsKey(EPOCH_MONTH)) {
                long em = standardFields.remove(EPOCH_MONTH);
                addFieldValue(MONTH_OF_YEAR, (em % 12) + 1);
                addFieldValue(YEAR, (em / 12) + 1970);
            }
        } else {
            // TODO: revisit EPOCH_MONTH calculation in non-ISO chronology
            // Handle EPOCH_MONTH here for non-ISO Chronology
            if (standardFields.containsKey(EPOCH_MONTH)) {
                long em = standardFields.remove(EPOCH_MONTH);
                ChronoLocalDate<?> chronoDate = chrono.date(LocalDate.ofEpochDay(0L));
                chronoDate = chronoDate.plus(em, ChronoUnit.MONTHS);
                LocalDate date = LocalDate.ofEpochDay(chronoDate.toEpochDay());
                checkDate(date);
                return;
            }
            List<Era> eras = chrono.eras();
            if (!eras.isEmpty()) {
                if (standardFields.containsKey(ERA)) {
                    long index = standardFields.remove(ERA);
                    era = chrono.eraOf((int) index);
                } else {
                    era = eras.get(eras.size() - 1); // current Era
                }
                if (standardFields.containsKey(YEAR_OF_ERA)) {
                    Long y = standardFields.remove(YEAR_OF_ERA);
                    putFieldValue0(YEAR, y);
                }
            }

        }

        // build date
        if (standardFields.containsKey(YEAR)) {
            if (standardFields.containsKey(MONTH_OF_YEAR)) {
                if (standardFields.containsKey(DAY_OF_MONTH)) {
                    int y = Math.toIntExact(standardFields.remove(YEAR));
                    int moy = Math.toIntExact(standardFields.remove(MONTH_OF_YEAR));
                    int dom = Math.toIntExact(standardFields.remove(DAY_OF_MONTH));
                    LocalDate date;
                    if (chrono == IsoChronology.INSTANCE) {
                        date = LocalDate.of(y, moy, dom);
                    } else {
                        ChronoLocalDate<?> chronoDate;
                        if (era == null) {
                            chronoDate = chrono.date(y, moy, dom);
                        } else {
                            chronoDate = era.date(y, moy, dom);
                        }
                        date = LocalDate.ofEpochDay(chronoDate.toEpochDay());
                    }
                    checkDate(date);
                    return;
                }
                if (standardFields.containsKey(ALIGNED_WEEK_OF_MONTH)) {
                    if (standardFields.containsKey(ALIGNED_DAY_OF_WEEK_IN_MONTH)) {
                        int y = Math.toIntExact(standardFields.remove(YEAR));
                        int moy = Math.toIntExact(standardFields.remove(MONTH_OF_YEAR));
                        int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_MONTH));
                        int ad = Math.toIntExact(standardFields.remove(ALIGNED_DAY_OF_WEEK_IN_MONTH));
                        LocalDate date;
                        if (chrono == IsoChronology.INSTANCE) {
                            date = LocalDate.of(y, moy, 1).plusDays((aw - 1) * 7 + (ad - 1));
                        } else {
                            ChronoLocalDate<?> chronoDate;
                            if (era == null) {
                                chronoDate = chrono.date(y, moy, 1);
                            } else {
                                chronoDate = era.date(y, moy, 1);
                            }
                            chronoDate = chronoDate.plus((aw - 1) * 7 + (ad - 1), ChronoUnit.DAYS);
                            date = LocalDate.ofEpochDay(chronoDate.toEpochDay());
                        }
                        checkDate(date);
                        return;
                    }
                    if (standardFields.containsKey(DAY_OF_WEEK)) {
                        int y = Math.toIntExact(standardFields.remove(YEAR));
                        int moy = Math.toIntExact(standardFields.remove(MONTH_OF_YEAR));
                        int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_MONTH));
                        int dow = Math.toIntExact(standardFields.remove(DAY_OF_WEEK));
                        LocalDate date;
                        if (chrono == IsoChronology.INSTANCE) {
                            date = LocalDate.of(y, moy, 1).plusDays((aw - 1) * 7).with(nextOrSame(DayOfWeek.of(dow)));
                        } else {
                            ChronoLocalDate<?> chronoDate;
                            if (era == null) {
                                chronoDate = chrono.date(y, moy, 1);
                            } else {
                                chronoDate = era.date(y, moy, 1);
                            }
                            chronoDate = chronoDate.plus((aw - 1) * 7, ChronoUnit.DAYS).with(nextOrSame(DayOfWeek.of(dow)));
                            date = LocalDate.ofEpochDay(chronoDate.toEpochDay());
                        }
                        checkDate(date);
                        return;
                    }
                }
            }
            if (standardFields.containsKey(DAY_OF_YEAR)) {
                int y = Math.toIntExact(standardFields.remove(YEAR));
                int doy = Math.toIntExact(standardFields.remove(DAY_OF_YEAR));
                LocalDate date;
                if (chrono == IsoChronology.INSTANCE) {
                    date = LocalDate.ofYearDay(y, doy);
                } else {
                    ChronoLocalDate<?> chronoDate;
                    if (era == null) {
                        chronoDate = chrono.dateYearDay(y, doy);
                    } else {
                        chronoDate = era.dateYearDay(y, doy);
                    }
                    date = LocalDate.ofEpochDay(chronoDate.toEpochDay());
                }
                checkDate(date);
                return;
            }
            if (standardFields.containsKey(ALIGNED_WEEK_OF_YEAR)) {
                if (standardFields.containsKey(ALIGNED_DAY_OF_WEEK_IN_YEAR)) {
                    int y = Math.toIntExact(standardFields.remove(YEAR));
                    int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_YEAR));
                    int ad = Math.toIntExact(standardFields.remove(ALIGNED_DAY_OF_WEEK_IN_YEAR));
                    LocalDate date;
                    if (chrono == IsoChronology.INSTANCE) {
                        date = LocalDate.of(y, 1, 1).plusDays((aw - 1) * 7 + (ad - 1));
                    } else {
                        ChronoLocalDate<?> chronoDate;
                        if (era == null) {
                            chronoDate = chrono.dateYearDay(y, 1);
                        } else {
                            chronoDate = era.dateYearDay(y, 1);
                        }
                        chronoDate = chronoDate.plus((aw - 1) * 7 + (ad - 1), ChronoUnit.DAYS);
                        date = LocalDate.ofEpochDay(chronoDate.toEpochDay());
                    }
                    checkDate(date);
                    return;
                }
                if (standardFields.containsKey(DAY_OF_WEEK)) {
                    int y = Math.toIntExact(standardFields.remove(YEAR));
                    int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_YEAR));
                    int dow = Math.toIntExact(standardFields.remove(DAY_OF_WEEK));
                    LocalDate date;
                    if (chrono == IsoChronology.INSTANCE) {
                        date = LocalDate.of(y, 1, 1).plusDays((aw - 1) * 7).with(nextOrSame(DayOfWeek.of(dow)));
                    } else {
                        ChronoLocalDate<?> chronoDate;
                        if (era == null) {
                            chronoDate = chrono.dateYearDay(y, 1);
                        } else {
                            chronoDate = era.dateYearDay(y, 1);
                        }
                        chronoDate = chronoDate.plus((aw - 1) * 7, ChronoUnit.DAYS).with(nextOrSame(DayOfWeek.of(dow)));
                        date = LocalDate.ofEpochDay(chronoDate.toEpochDay());
                    }
                    checkDate(date);
                    return;
                }
            }
        }
    }

    private void checkDate(LocalDate date) {
        addObject(date);
        for (ChronoField field : standardFields.keySet()) {
            long val1;
            try {
                val1 = date.getLong(field);
            } catch (DateTimeException ex) {
                continue;
            }
            Long val2 = standardFields.get(field);
            if (val1 != val2) {
                throw new DateTimeException("Conflict found: Field " + field + " " + val1 + " differs from " + field + " " + val2 + " derived from " + date);
            }
        }
    }

    private void mergeTime() {
        if (standardFields.containsKey(CLOCK_HOUR_OF_DAY)) {
            long ch = standardFields.remove(CLOCK_HOUR_OF_DAY);
            addFieldValue(HOUR_OF_DAY, ch == 24 ? 0 : ch);
        }
        if (standardFields.containsKey(CLOCK_HOUR_OF_AMPM)) {
            long ch = standardFields.remove(CLOCK_HOUR_OF_AMPM);
            addFieldValue(HOUR_OF_AMPM, ch == 12 ? 0 : ch);
        }
        if (standardFields.containsKey(AMPM_OF_DAY) && standardFields.containsKey(HOUR_OF_AMPM)) {
            long ap = standardFields.remove(AMPM_OF_DAY);
            long hap = standardFields.remove(HOUR_OF_AMPM);
            addFieldValue(HOUR_OF_DAY, ap * 12 + hap);
        }
//        if (timeFields.containsKey(HOUR_OF_DAY) && timeFields.containsKey(MINUTE_OF_HOUR)) {
//            long hod = timeFields.remove(HOUR_OF_DAY);
//            long moh = timeFields.remove(MINUTE_OF_HOUR);
//            addFieldValue(MINUTE_OF_DAY, hod * 60 + moh);
//        }
//        if (timeFields.containsKey(MINUTE_OF_DAY) && timeFields.containsKey(SECOND_OF_MINUTE)) {
//            long mod = timeFields.remove(MINUTE_OF_DAY);
//            long som = timeFields.remove(SECOND_OF_MINUTE);
//            addFieldValue(SECOND_OF_DAY, mod * 60 + som);
//        }
        if (standardFields.containsKey(NANO_OF_DAY)) {
            long nod = standardFields.remove(NANO_OF_DAY);
            addFieldValue(SECOND_OF_DAY, nod / 1000_000_000L);
            addFieldValue(NANO_OF_SECOND, nod % 1000_000_000L);
        }
        if (standardFields.containsKey(MICRO_OF_DAY)) {
            long cod = standardFields.remove(MICRO_OF_DAY);
            addFieldValue(SECOND_OF_DAY, cod / 1000_000L);
            addFieldValue(MICRO_OF_SECOND, cod % 1000_000L);
        }
        if (standardFields.containsKey(MILLI_OF_DAY)) {
            long lod = standardFields.remove(MILLI_OF_DAY);
            addFieldValue(SECOND_OF_DAY, lod / 1000);
            addFieldValue(MILLI_OF_SECOND, lod % 1000);
        }
        if (standardFields.containsKey(SECOND_OF_DAY)) {
            long sod = standardFields.remove(SECOND_OF_DAY);
            addFieldValue(HOUR_OF_DAY, sod / 3600);
            addFieldValue(MINUTE_OF_HOUR, (sod / 60) % 60);
            addFieldValue(SECOND_OF_MINUTE, sod % 60);
        }
        if (standardFields.containsKey(MINUTE_OF_DAY)) {
            long mod = standardFields.remove(MINUTE_OF_DAY);
            addFieldValue(HOUR_OF_DAY, mod / 60);
            addFieldValue(MINUTE_OF_HOUR, mod % 60);
        }

//            long sod = nod / 1000_000_000L;
//            addFieldValue(HOUR_OF_DAY, sod / 3600);
//            addFieldValue(MINUTE_OF_HOUR, (sod / 60) % 60);
//            addFieldValue(SECOND_OF_MINUTE, sod % 60);
//            addFieldValue(NANO_OF_SECOND, nod % 1000_000_000L);
        if (standardFields.containsKey(MILLI_OF_SECOND) && standardFields.containsKey(MICRO_OF_SECOND)) {
            long los = standardFields.remove(MILLI_OF_SECOND);
            long cos = standardFields.get(MICRO_OF_SECOND);
            addFieldValue(MICRO_OF_SECOND, los * 1000 + (cos % 1000));
        }

        Long hod = standardFields.get(HOUR_OF_DAY);
        Long moh = standardFields.get(MINUTE_OF_HOUR);
        Long som = standardFields.get(SECOND_OF_MINUTE);
        Long nos = standardFields.get(NANO_OF_SECOND);
        if (hod != null) {
            int hodVal = Math.toIntExact(hod);
            if (moh != null) {
                int mohVal = Math.toIntExact(moh);
                if (som != null) {
                    int somVal = Math.toIntExact(som);
                    if (nos != null) {
                        int nosVal = Math.toIntExact(nos);
                        addObject(LocalTime.of(hodVal, mohVal, somVal, nosVal));
                    } else {
                        addObject(LocalTime.of(hodVal, mohVal, somVal));
                    }
                } else {
                    addObject(LocalTime.of(hodVal, mohVal));
                }
            } else {
                addObject(LocalTime.of(hodVal, 0));
            }
        }
    }

    //-----------------------------------------------------------------------
    @Override
    public boolean isSupported(TemporalField field) {
        if (field == null) {
            return false;
        }
        return standardFields.containsKey(field) ||
                (otherFields != null && otherFields.containsKey(field)) ||
                (date != null && date.isSupported(field)) ||
                (time != null && time.isSupported(field));
    }

    @Override
    public long getLong(TemporalField field) {
        Objects.requireNonNull(field, "field");
        Long value = getFieldValue0(field);
        if (value == null) {
            if (date != null && date.isSupported(field)) {
                return date.getLong(field);
            }
            if (time != null && time.isSupported(field)) {
                return time.getLong(field);
            }
            throw new DateTimeException("Field not found: " + field);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> R query(TemporalQuery<R> query) {
        if (query == Queries.zoneId()) {
            return (R) zone;
        } else if (query == Queries.chronology()) {
            return (R) chrono;
        } else if (query == Queries.localDate()) {
            return (R) date;
        } else if (query == Queries.localTime()) {
            return (R) time;
        } else if (query == Queries.zone() || query == Queries.offset()) {
            return query.queryFrom(this);
        } else if (query == Queries.precision()) {
            return null;  // not a complete date/time
        }
        // inline TemporalAccessor.super.query(query) as an optimization
        // non-JDK classes are not permitted to make this optimization
        return query.queryFrom(this);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(128);
        buf.append("DateTimeBuilder[");
        Map<TemporalField, Long> fields = new HashMap<>();
        fields.putAll(standardFields);
        if (otherFields != null) {
            fields.putAll(otherFields);
        }
        if (fields.size() > 0) {
            buf.append("fields=").append(fields);
        }
        buf.append(", ").append(chrono);
        buf.append(", ").append(zone);
        buf.append(", ").append(date);
        buf.append(", ").append(time);
        buf.append(']');
        return buf.toString();
    }

}
