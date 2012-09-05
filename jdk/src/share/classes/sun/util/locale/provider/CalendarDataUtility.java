/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.locale.provider;

import java.util.Locale;
import java.util.Map;
import java.util.spi.CalendarDataProvider;

/**
 * {@code CalendarDataUtility} is a utility class for calling the
 * {@link CalendarDataProvider} methods.
 *
 * @author Masayoshi Okutsu
 * @author Naoto Sato
 */
public class CalendarDataUtility {
    public final static String FIRST_DAY_OF_WEEK = "firstDayOfWeek";
    public final static String MINIMAL_DAYS_IN_FIRST_WEEK = "minimalDaysInFirstWeek";

    // No instantiation
    private CalendarDataUtility() {
    }

    public static String retrieveFieldValueName(String id, int field, int value, int style, Locale locale) {
        LocaleServiceProviderPool pool =
                LocaleServiceProviderPool.getPool(CalendarDataProvider.class);
        return pool.getLocalizedObject(CalendarFieldValueNameGetter.INSTANCE, locale, id,
                                       field, value, style);
    }

    public static Map<String, Integer> retrieveFieldValueNames(String id, int field, int style, Locale locale) {
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(CalendarDataProvider.class);
        return pool.getLocalizedObject(CalendarFieldValueNamesMapGetter.INSTANCE, locale, id, field, style);
    }

    /**
     * Obtains a localized field value string from a CalendarDataProvider
     * implementation.
     */
    private static class CalendarFieldValueNameGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<CalendarDataProvider,
                                                                   String> {
        private static final CalendarFieldValueNameGetter INSTANCE =
            new CalendarFieldValueNameGetter();

        @Override
        public String getObject(CalendarDataProvider calendarDataProvider,
                                Locale locale,
                                String requestID, // calendarType
                                Object... params) {
            assert params.length == 3;
            int field = (int) params[0];
            int value = (int) params[1];
            int style = (int) params[2];
            return calendarDataProvider.getDisplayName(requestID, field, value, style, locale);
        }
    }

    /**
     * Obtains a localized field-value pairs from a CalendarDataProvider
     * implementation.
     */
    private static class CalendarFieldValueNamesMapGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<CalendarDataProvider,
                                                                   Map<String, Integer>> {
        private static final CalendarFieldValueNamesMapGetter INSTANCE =
            new CalendarFieldValueNamesMapGetter();

        @Override
        public Map<String, Integer> getObject(CalendarDataProvider calendarDataProvider,
                                              Locale locale,
                                              String requestID, // calendarType
                                              Object... params) {
            assert params.length == 2;
            int field = (int) params[0];
            int style = (int) params[1];
            return calendarDataProvider.getDisplayNames(requestID, field, style, locale);
        }
    }
}
