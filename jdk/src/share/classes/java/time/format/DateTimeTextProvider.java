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
 * Copyright (c) 2011-2012, Stephen Colebourne & Michael Nascimento Santos
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

import static java.time.temporal.ChronoField.AMPM_OF_DAY;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

import java.time.temporal.Chrono;
import java.time.temporal.ISOChrono;
import java.time.calendar.JapaneseChrono;
import java.time.calendar.ThaiBuddhistChrono;
import java.time.temporal.TemporalField;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.spi.CalendarNameProvider;

import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.CalendarDataUtility;

/**
 * A provider to obtain the textual form of a date-time field.
 *
 * <h3>Specification for implementors</h3>
 * Implementations must be thread-safe.
 * Implementations should cache the textual information.
 *
 * @since 1.8
 */
class DateTimeTextProvider {

    /** Cache. */
    private static final ConcurrentMap<Entry<TemporalField, Locale>, Object> CACHE = new ConcurrentHashMap<>(16, 0.75f, 2);
    /** Comparator. */
    private static final Comparator<Entry<String, Long>> COMPARATOR = new Comparator<Entry<String, Long>>() {
        @Override
        public int compare(Entry<String, Long> obj1, Entry<String, Long> obj2) {
            return obj2.getKey().length() - obj1.getKey().length();  // longest to shortest
        }
    };

    DateTimeTextProvider() {}

    /**
     * Gets the provider of text.
     *
     * @return the provider, not null
     */
    static DateTimeTextProvider getInstance() {
        return new DateTimeTextProvider();
    }

    /**
     * Gets the text for the specified field, locale and style
     * for the purpose of printing.
     * <p>
     * The text associated with the value is returned.
     * The null return value should be used if there is no applicable text, or
     * if the text would be a numeric representation of the value.
     *
     * @param field  the field to get text for, not null
     * @param value  the field value to get text for, not null
     * @param style  the style to get text for, not null
     * @param locale  the locale to get text for, not null
     * @return the text for the field value, null if no text found
     */
    public String getText(TemporalField field, long value, TextStyle style, Locale locale) {
        Object store = findStore(field, locale);
        if (store instanceof LocaleStore) {
            return ((LocaleStore) store).getText(value, style);
        }
        return null;
    }

    private static int toStyle(TextStyle style) {
        if (style == TextStyle.FULL) {
            return Calendar.LONG_FORMAT;
        } else if (style == TextStyle.SHORT) {
            return Calendar.SHORT_FORMAT;
        }
        return Calendar.NARROW_STANDALONE;
    }

    /**
     * Gets the era text for the specified chrono, value, style and locale
     * for the purpose of printing.
     * <p>
     * The era text associated with the value is returned.
     * The null return value should be used if there is no applicable text, or
     * if the text would be a numeric representation of the value.
     *
     * @param chrono the chrono to get text for, not null
     * @param value  the field value to get text for, not null
     * @param style  the style to get text for, not null
     * @param locale  the locale to get text for, not null
     * @return the era text for the value, null if no text found
     */
    public String getEraText(Chrono chrono, long value, TextStyle style, Locale locale) {
        String type = null;
        if (chrono == ISOChrono.INSTANCE) {
            type = "gregory";
        } else if (chrono == JapaneseChrono.INSTANCE) {
            type = "japanese";
            if (value == -999) {
                value = 0;
            } else {
                value += 2;
            }
        } else if (chrono == ThaiBuddhistChrono.INSTANCE) {
            type = "buddhist";
        } else {
            return null;
        }
        return CalendarDataUtility.retrieveFieldValueName(
            type, Calendar.ERA, (int)value, toStyle(style), locale);
    }

    /**
     * Gets an iterator of text to field for the specified field, locale and style
     * for the purpose of parsing.
     * <p>
     * The iterator must be returned in order from the longest text to the shortest.
     * <p>
     * The null return value should be used if there is no applicable parsable text, or
     * if the text would be a numeric representation of the value.
     * Text can only be parsed if all the values for that field-style-locale combination are unique.
     *
     * @param field  the field to get text for, not null
     * @param style  the style to get text for, null for all parsable text
     * @param locale  the locale to get text for, not null
     * @return the iterator of text to field pairs, in order from longest text to shortest text,
     *  null if the field or style is not parsable
     */
    public Iterator<Entry<String, Long>> getTextIterator(TemporalField field, TextStyle style, Locale locale) {
        Object store = findStore(field, locale);
        if (store instanceof LocaleStore) {
            return ((LocaleStore) store).getTextIterator(style);
        }
        return null;
    }

    private Object findStore(TemporalField field, Locale locale) {
        Entry<TemporalField, Locale> key = createEntry(field, locale);
        Object store = CACHE.get(key);
        if (store == null) {
            store = createStore(field, locale);
            CACHE.putIfAbsent(key, store);
            store = CACHE.get(key);
        }
        return store;
    }

    private static int toWeekDay(int calWeekDay) {
        if (calWeekDay == Calendar.SUNDAY) {
            return 7;
        } else {
            return calWeekDay - 1;
        }
    }

    private Object createStore(TemporalField field, Locale locale) {
        CalendarNameProvider provider = LocaleProviderAdapter.getAdapter(CalendarNameProvider.class, locale)
                                                             .getCalendarNameProvider();
        Map<TextStyle, Map<Long, String>> styleMap = new HashMap<>();
        if (field == MONTH_OF_YEAR) {
            Map<Long, String> map = new HashMap<>();
            for (Entry<String, Integer> entry :
                 provider.getDisplayNames("gregory", Calendar.MONTH, Calendar.LONG_FORMAT, locale).entrySet()) {
                map.put((long)(entry.getValue() + 1), entry.getKey());
            }
            styleMap.put(TextStyle.FULL, map);

            map = new HashMap<>();
            for (Entry<String, Integer> entry :
                 provider.getDisplayNames("gregory", Calendar.MONTH, Calendar.SHORT_FORMAT, locale).entrySet()) {
                map.put((long)(entry.getValue() + 1), entry.getKey());
            }
            styleMap.put(TextStyle.SHORT, map);

            map = new HashMap<>();
            for (int month = Calendar.JANUARY; month <= Calendar.DECEMBER; month++) {
                String name = provider.getDisplayName("gregory", Calendar.MONTH, month, Calendar.NARROW_STANDALONE, locale);
                if (name != null) {
                    map.put((long)(month + 1), name);
                }
            }
            if (map.size() != 0) {
                styleMap.put(TextStyle.NARROW, map);
            }
            return new LocaleStore(styleMap);
        }
        if (field == DAY_OF_WEEK) {
            Map<Long, String> map = new HashMap<>();
            for (Entry<String, Integer> entry :
                 provider.getDisplayNames("gregory", Calendar.DAY_OF_WEEK, Calendar.LONG_FORMAT, locale).entrySet()) {
                map.put((long)toWeekDay(entry.getValue()), entry.getKey());
            }
            styleMap.put(TextStyle.FULL, map);
            map = new HashMap<>();
            for (Entry<String, Integer> entry :
                 provider.getDisplayNames("gregory", Calendar.DAY_OF_WEEK, Calendar.SHORT_FORMAT, locale).entrySet()) {
                map.put((long)toWeekDay(entry.getValue()), entry.getKey());
            }
            styleMap.put(TextStyle.SHORT, map);
            map = new HashMap<>();
            for (int wday = Calendar.SUNDAY; wday <= Calendar.SATURDAY; wday++) {
                map.put((long)toWeekDay(wday),
                        provider.getDisplayName("gregory", Calendar.DAY_OF_WEEK, wday, Calendar.NARROW_FORMAT, locale));
            }
            styleMap.put(TextStyle.NARROW, map);
            return new LocaleStore(styleMap);
        }
        if (field == AMPM_OF_DAY) {
            Map<Long, String> map = new HashMap<>();
            for (Entry<String, Integer> entry :
                 provider.getDisplayNames("gregory", Calendar.AM_PM, Calendar.LONG_FORMAT, locale).entrySet()) {
                map.put((long)entry.getValue(), entry.getKey());
            }
            styleMap.put(TextStyle.FULL, map);
            styleMap.put(TextStyle.SHORT, map);  // re-use, as we don't have different data
            return new LocaleStore(styleMap);
        }
        return "";  // null marker for map
    }

    /**
     * Helper method to create an immutable entry.
     *
     * @param text  the text, not null
     * @param field  the field, not null
     * @return the entry, not null
     */
    private static <A, B> Entry<A, B> createEntry(A text, B field) {
        return new SimpleImmutableEntry<>(text, field);
    }

    /**
     * Stores the text for a single locale.
     * <p>
     * Some fields have a textual representation, such as day-of-week or month-of-year.
     * These textual representations can be captured in this class for printing
     * and parsing.
     * <p>
     * This class is immutable and thread-safe.
     */
    static final class LocaleStore {
        /**
         * Map of value to text.
         */
        private final Map<TextStyle, Map<Long, String>> valueTextMap;
        /**
         * Parsable data.
         */
        private final Map<TextStyle, List<Entry<String, Long>>> parsable;

        /**
         * Constructor.
         *
         * @param valueTextMap  the map of values to text to store, assigned and not altered, not null
         */
        LocaleStore(Map<TextStyle, Map<Long, String>> valueTextMap) {
            this.valueTextMap = valueTextMap;
            Map<TextStyle, List<Entry<String, Long>>> map = new HashMap<>();
            List<Entry<String, Long>> allList = new ArrayList<>();
            for (TextStyle style : valueTextMap.keySet()) {
                Map<String, Entry<String, Long>> reverse = new HashMap<>();
                for (Map.Entry<Long, String> entry : valueTextMap.get(style).entrySet()) {
                    if (reverse.put(entry.getValue(), createEntry(entry.getValue(), entry.getKey())) != null) {
                        // TODO: BUG: this has no effect
                        continue;  // not parsable, try next style
                    }
                }
                List<Entry<String, Long>> list = new ArrayList<>(reverse.values());
                Collections.sort(list, COMPARATOR);
                map.put(style, list);
                allList.addAll(list);
                map.put(null, allList);
            }
            Collections.sort(allList, COMPARATOR);
            this.parsable = map;
        }

        /**
         * Gets the text for the specified field value, locale and style
         * for the purpose of printing.
         *
         * @param value  the value to get text for, not null
         * @param style  the style to get text for, not null
         * @return the text for the field value, null if no text found
         */
        String getText(long value, TextStyle style) {
            Map<Long, String> map = valueTextMap.get(style);
            return map != null ? map.get(value) : null;
        }

        /**
         * Gets an iterator of text to field for the specified style for the purpose of parsing.
         * <p>
         * The iterator must be returned in order from the longest text to the shortest.
         *
         * @param style  the style to get text for, null for all parsable text
         * @return the iterator of text to field pairs, in order from longest text to shortest text,
         *  null if the style is not parsable
         */
        Iterator<Entry<String, Long>> getTextIterator(TextStyle style) {
            List<Entry<String, Long>> list = parsable.get(style);
            return list != null ? list.iterator() : null;
        }
    }
}
