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
import static java.time.temporal.ChronoField.HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.MICRO_OF_DAY;
import static java.time.temporal.ChronoField.MICRO_OF_SECOND;
import static java.time.temporal.ChronoField.MILLI_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_DAY;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;
import static java.time.temporal.ChronoField.SECOND_OF_DAY;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.Chrono;
import java.time.temporal.ChronoField;
import java.time.temporal.Queries;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * Builder that can holds date and time fields and related date and time objects.
 * <p>
 * <b>This class still needs major revision before JDK1.8 ships.</b>
 * <p>
 * The builder is used to hold onto different elements of date and time.
 * It is designed as two separate maps:
 * <p><ul>
 * <li>from {@link java.time.temporal.TemporalField} to {@code long} value, where the value may be
 * outside the valid range for the field
 * <li>from {@code Class} to {@link java.time.temporal.TemporalAccessor}, holding larger scale objects
 * like {@code LocalDateTime}.
 * </ul><p>
 *
 * <h3>Specification for implementors</h3>
 * This class is mutable and not thread-safe.
 * It should only be used from a single thread.
 *
 * @since 1.8
 */
public final class DateTimeBuilder
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
     * The list of complete date-time objects.
     */
    private final List<Object> objects = new ArrayList<>(2);

    //-----------------------------------------------------------------------
    /**
     * Creates an empty instance of the builder.
     */
    public DateTimeBuilder() {
    }

    /**
     * Creates a new instance of the builder with a single field-value.
     * <p>
     * This is equivalent to using {@link #addFieldValue(TemporalField, long)} on an empty builder.
     *
     * @param field  the field to add, not null
     * @param value  the value to add, not null
     */
    public DateTimeBuilder(TemporalField field, long value) {
        addFieldValue(field, value);
    }

    /**
     * Creates a new instance of the builder.
     *
     * @param zone  the zone, may be null
     * @param chrono  the chronology, may be null
     */
    public DateTimeBuilder(ZoneId zone, Chrono<?> chrono) {
        if (zone != null) {
            objects.add(zone);
        }
        if (chrono != null) {
            objects.add(chrono);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the map of field-value pairs in the builder.
     *
     * @return a modifiable copy of the field-value map, not null
     */
    public Map<TemporalField, Long> getFieldValueMap() {
        Map<TemporalField, Long> map = new HashMap<TemporalField, Long>(standardFields);
        if (otherFields != null) {
            map.putAll(otherFields);
        }
        return map;
    }

    /**
     * Checks whether the specified field is present in the builder.
     *
     * @param field  the field to find in the field-value map, not null
     * @return true if the field is present
     */
    public boolean containsFieldValue(TemporalField field) {
        Objects.requireNonNull(field, "field");
        return standardFields.containsKey(field) || (otherFields != null && otherFields.containsKey(field));
    }

    /**
     * Gets the value of the specified field from the builder.
     *
     * @param field  the field to query in the field-value map, not null
     * @return the value of the field, may be out of range
     * @throws DateTimeException if the field is not present
     */
    public long getFieldValue(TemporalField field) {
        Objects.requireNonNull(field, "field");
        Long value = getFieldValue0(field);
        if (value == null) {
            throw new DateTimeException("Field not found: " + field);
        }
        return value;
    }

    private Long getFieldValue0(TemporalField field) {
        if (field instanceof ChronoField) {
            return standardFields.get(field);
        } else if (otherFields != null) {
            return otherFields.get(field);
        }
        return null;
    }

    /**
     * Gets the value of the specified field from the builder ensuring it is valid.
     *
     * @param field  the field to query in the field-value map, not null
     * @return the value of the field, may be out of range
     * @throws DateTimeException if the field is not present
     */
    public long getValidFieldValue(TemporalField field) {
        long value = getFieldValue(field);
        return field.range().checkValidValue(value, field);
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
    public DateTimeBuilder addFieldValue(TemporalField field, long value) {
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

    /**
     * Removes a field-value pair from the builder.
     * <p>
     * This removes a field, which must exist, from the builder.
     * See {@link #removeFieldValues(TemporalField...)} for a version which does not throw an exception
     *
     * @param field  the field to remove, not null
     * @return the previous value of the field
     * @throws DateTimeException if the field is not found
     */
    public long removeFieldValue(TemporalField field) {
        Objects.requireNonNull(field, "field");
        Long value = null;
        if (field instanceof ChronoField) {
            value = standardFields.remove(field);
        } else if (otherFields != null) {
            value = otherFields.remove(field);
        }
        if (value == null) {
            throw new DateTimeException("Field not found: " + field);
        }
        return value;
    }

    //-----------------------------------------------------------------------
    /**
     * Removes a list of fields from the builder.
     * <p>
     * This removes the specified fields from the builder.
     * No exception is thrown if the fields are not present.
     *
     * @param fields  the fields to remove, not null
     */
    public void removeFieldValues(TemporalField... fields) {
        for (TemporalField field : fields) {
            if (field instanceof ChronoField) {
                standardFields.remove(field);
            } else if (otherFields != null) {
                otherFields.remove(field);
            }
        }
    }

    /**
     * Queries a list of fields from the builder.
     * <p>
     * This gets the value of the specified fields from the builder into
     * an array where the positions match the order of the fields.
     * If a field is not present, the array will contain null in that position.
     *
     * @param fields  the fields to query, not null
     * @return the array of field values, not null
     */
    public Long[] queryFieldValues(TemporalField... fields) {
        Long[] values = new Long[fields.length];
        int i = 0;
        for (TemporalField field : fields) {
            values[i++] = getFieldValue0(field);
        }
        return values;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the list of date-time objects in the builder.
     * <p>
     * This map is intended for use with {@link ZoneOffset} and {@link ZoneId}.
     * The returned map is live and may be edited.
     *
     * @return the editable list of date-time objects, not null
     */
    public List<Object> getCalendricalList() {
        return objects;
    }

    /**
     * Adds a date-time object to the builder.
     * <p>
     * This adds a date-time object to the builder.
     * If the object is a {@code DateTimeBuilder}, each field is added using {@link #addFieldValue}.
     * If the object is not already present, then the object is added.
     * If the object is already present and it is equal to that specified, no action occurs.
     * If the object is already present and it is not equal to that specified, then an exception is thrown.
     *
     * @param object  the object to add, not null
     * @return {@code this}, for method chaining
     * @throws DateTimeException if the field is already present with a different value
     */
    public DateTimeBuilder addCalendrical(Object object) {
        Objects.requireNonNull(object, "object");
        // special case
        if (object instanceof DateTimeBuilder) {
            DateTimeBuilder dtb = (DateTimeBuilder) object;
            for (TemporalField field : dtb.getFieldValueMap().keySet()) {
                addFieldValue(field, dtb.getFieldValue(field));
            }
            return this;
        }
        if (object instanceof Instant) {
            addFieldValue(INSTANT_SECONDS, ((Instant) object).getEpochSecond());
            addFieldValue(NANO_OF_SECOND, ((Instant) object).getNano());
        } else {
            objects.add(object);
        }
//      TODO
//        // preserve state of builder until validated
//        Class<?> cls = dateTime.extract(Class.class);
//        if (cls == null) {
//            throw new DateTimeException("Invalid dateTime, unable to extract Class");
//        }
//        Object obj = objects.get(cls);
//        if (obj != null) {
//            if (obj.equals(dateTime) == false) {
//                throw new DateTimeException("Conflict found: " + dateTime.getClass().getSimpleName() + " " + obj + " differs from " + dateTime + ": " + this);
//            }
//        } else {
//            objects.put(cls, dateTime);
//        }
        return this;
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
    public DateTimeBuilder resolve() {
        splitObjects();
        // handle unusual fields
        if (otherFields != null) {
            outer:
            while (true) {
                Set<Entry<TemporalField, Long>> entrySet = new HashSet<>(otherFields.entrySet());
                for (Entry<TemporalField, Long> entry : entrySet) {
                    if (entry.getKey().resolve(this, entry.getValue())) {
                        continue outer;
                    }
                }
                break;
            }
        }
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

        // normalize fields
        if (standardFields.containsKey(EPOCH_MONTH)) {
            long em = standardFields.remove(EPOCH_MONTH);
            addFieldValue(MONTH_OF_YEAR, (em % 12) + 1);
            addFieldValue(YEAR, (em / 12) + 1970);
        }

        // build date
        if (standardFields.containsKey(YEAR)) {
            if (standardFields.containsKey(MONTH_OF_YEAR)) {
                if (standardFields.containsKey(DAY_OF_MONTH)) {
                    int y = Math.toIntExact(standardFields.remove(YEAR));
                    int moy = Math.toIntExact(standardFields.remove(MONTH_OF_YEAR));
                    int dom = Math.toIntExact(standardFields.remove(DAY_OF_MONTH));
                    checkDate(LocalDate.of(y, moy, dom));
                    return;
                }
                if (standardFields.containsKey(ALIGNED_WEEK_OF_MONTH)) {
                    if (standardFields.containsKey(ALIGNED_DAY_OF_WEEK_IN_MONTH)) {
                        int y = Math.toIntExact(standardFields.remove(YEAR));
                        int moy = Math.toIntExact(standardFields.remove(MONTH_OF_YEAR));
                        int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_MONTH));
                        int ad = Math.toIntExact(standardFields.remove(ALIGNED_DAY_OF_WEEK_IN_MONTH));
                        checkDate(LocalDate.of(y, moy, 1).plusDays((aw - 1) * 7 + (ad - 1)));
                        return;
                    }
                    if (standardFields.containsKey(DAY_OF_WEEK)) {
                        int y = Math.toIntExact(standardFields.remove(YEAR));
                        int moy = Math.toIntExact(standardFields.remove(MONTH_OF_YEAR));
                        int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_MONTH));
                        int dow = Math.toIntExact(standardFields.remove(DAY_OF_WEEK));
                        checkDate(LocalDate.of(y, moy, 1).plusDays((aw - 1) * 7).with(nextOrSame(DayOfWeek.of(dow))));
                        return;
                    }
                }
            }
            if (standardFields.containsKey(DAY_OF_YEAR)) {
                int y = Math.toIntExact(standardFields.remove(YEAR));
                int doy = Math.toIntExact(standardFields.remove(DAY_OF_YEAR));
                checkDate(LocalDate.ofYearDay(y, doy));
                return;
            }
            if (standardFields.containsKey(ALIGNED_WEEK_OF_YEAR)) {
                if (standardFields.containsKey(ALIGNED_DAY_OF_WEEK_IN_YEAR)) {
                    int y = Math.toIntExact(standardFields.remove(YEAR));
                    int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_YEAR));
                    int ad = Math.toIntExact(standardFields.remove(ALIGNED_DAY_OF_WEEK_IN_YEAR));
                    checkDate(LocalDate.of(y, 1, 1).plusDays((aw - 1) * 7 + (ad - 1)));
                    return;
                }
                if (standardFields.containsKey(DAY_OF_WEEK)) {
                    int y = Math.toIntExact(standardFields.remove(YEAR));
                    int aw = Math.toIntExact(standardFields.remove(ALIGNED_WEEK_OF_YEAR));
                    int dow = Math.toIntExact(standardFields.remove(DAY_OF_WEEK));
                    checkDate(LocalDate.of(y, 1, 1).plusDays((aw - 1) * 7).with(nextOrSame(DayOfWeek.of(dow))));
                    return;
                }
            }
        }
    }

    private void checkDate(LocalDate date) {
        // TODO: this doesn't handle aligned weeks over into next month which would otherwise be valid

        addCalendrical(date);
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
                        addCalendrical(LocalTime.of(hodVal, mohVal, somVal, nosVal));
                    } else {
                        addCalendrical(LocalTime.of(hodVal, mohVal, somVal));
                    }
                } else {
                    addCalendrical(LocalTime.of(hodVal, mohVal));
                }
            } else {
                addCalendrical(LocalTime.of(hodVal, 0));
            }
        }
    }

    private void splitObjects() {
        List<Object> objectsToAdd = new ArrayList<>();
        for (Object object : objects) {
            if (object instanceof LocalDate || object instanceof LocalTime ||
                            object instanceof ZoneId || object instanceof Chrono) {
                continue;
            }
            if (object instanceof ZoneOffset || object instanceof Instant) {
                objectsToAdd.add(object);

            } else if (object instanceof TemporalAccessor) {
                // TODO
//                TemporalAccessor dt = (TemporalAccessor) object;
//                objectsToAdd.add(dt.extract(LocalDate.class));
//                objectsToAdd.add(dt.extract(LocalTime.class));
//                objectsToAdd.add(dt.extract(ZoneId.class));
//                objectsToAdd.add(dt.extract(Chrono.class));
            }
        }
        for (Object object : objectsToAdd) {
            if (object != null) {
                addCalendrical(object);
            }
        }
    }

    //-----------------------------------------------------------------------
    @Override
    public <R> R query(TemporalQuery<R> query) {
        if (query == Queries.zoneId()) {
            return (R) extract(ZoneId.class);
        }
        if (query == Queries.offset()) {
            ZoneOffset offset = extract(ZoneOffset.class);
            if (offset == null && standardFields.containsKey(OFFSET_SECONDS)) {
                offset = ZoneOffset.ofTotalSeconds(Math.toIntExact(standardFields.get(OFFSET_SECONDS)));
            }
            return (R) offset;
        }
        if (query == Queries.chrono()) {
            return extract(Chrono.class);
        }
        // incomplete, so no need to handle PRECISION
        return TemporalAccessor.super.query(query);
    }

    @SuppressWarnings("unchecked")
    public <R> R extract(Class<?> type) {
        R result = null;
        for (Object obj : objects) {
            if (type.isInstance(obj)) {
                if (result != null && result.equals(obj) == false) {
                    throw new DateTimeException("Conflict found: " + type.getSimpleName() + " differs " + result + " vs " + obj + ": " + this);
                }
                result = (R) obj;
            }
        }
        return result;
    }

    //-----------------------------------------------------------------------
    /**
     * Clones this builder, creating a new independent copy referring to the
     * same map of fields and objects.
     *
     * @return the cloned builder, not null
     */
    @Override
    public DateTimeBuilder clone() {
        DateTimeBuilder dtb = new DateTimeBuilder();
        dtb.objects.addAll(this.objects);
        dtb.standardFields.putAll(this.standardFields);
        dtb.standardFields.putAll(this.standardFields);
        if (this.otherFields != null) {
            dtb.otherFields.putAll(this.otherFields);
        }
        return dtb;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(128);
        buf.append("DateTimeBuilder[");
        Map<TemporalField, Long> fields = getFieldValueMap();
        if (fields.size() > 0) {
            buf.append("fields=").append(fields);
        }
        if (objects.size() > 0) {
            if (fields.size() > 0) {
                buf.append(", ");
            }
            buf.append("objects=").append(objects);
        }
        buf.append(']');
        return buf.toString();
    }

    //-----------------------------------------------------------------------
    @Override
    public boolean isSupported(TemporalField field) {
        return field != null && containsFieldValue(field);
    }

    @Override
    public long getLong(TemporalField field) {
        return getFieldValue(field);
    }

}
