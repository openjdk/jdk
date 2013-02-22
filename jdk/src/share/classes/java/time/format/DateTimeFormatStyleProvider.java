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
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
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

import java.text.SimpleDateFormat;
import java.time.chrono.Chronology;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleResources;

/**
 * A provider to obtain date-time formatters for a style.
 * <p>
 *
 * <h3>Specification for implementors</h3>
 * This implementation is based on extraction of data from a {@link SimpleDateFormat}.
 * This class is immutable and thread-safe.
 * This Implementations caches the returned formatters.
 *
 * @since 1.8
 */
final class DateTimeFormatStyleProvider {
    // TODO: Better implementation based on CLDR

    /** Cache of formatters. */
    private static final ConcurrentMap<String, Object> FORMATTER_CACHE = new ConcurrentHashMap<>(16, 0.75f, 2);

    private DateTimeFormatStyleProvider() {}

    /**
     * Gets an Instance of the provider of format styles.
     *
     * @return the provider, not null
     */
    static DateTimeFormatStyleProvider getInstance() {
        return new DateTimeFormatStyleProvider();
    }

    /**
     * Gets a localized date, time or date-time formatter.
     * <p>
     * The formatter will be the most appropriate to use for the date and time style in the locale.
     * For example, some locales will use the month name while others will use the number.
     *
     * @param dateStyle  the date formatter style to obtain, null to obtain a time formatter
     * @param timeStyle  the time formatter style to obtain, null to obtain a date formatter
     * @param chrono  the chronology to use, not null
     * @param locale  the locale to use, not null
     * @return the date-time formatter, not null
     * @throws IllegalArgumentException if both format styles are null or if the locale is not recognized
     */
    public DateTimeFormatter getFormatter(
            FormatStyle dateStyle, FormatStyle timeStyle, Chronology chrono, Locale locale) {
        if (dateStyle == null && timeStyle == null) {
            throw new IllegalArgumentException("Date and Time style must not both be null");
        }
        String key = chrono.getId() + '|' + locale.toString() + '|' + dateStyle + timeStyle;
        Object cached = FORMATTER_CACHE.get(key);
        if (cached != null) {
            return (DateTimeFormatter) cached;
        }

        LocaleResources lr = LocaleProviderAdapter.getResourceBundleBased()
                                    .getLocaleResources(locale);
        String pattern = lr.getCldrDateTimePattern(convertStyle(timeStyle), convertStyle(dateStyle),
                                                   chrono.getCalendarType());
        DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(pattern).toFormatter(locale);
        FORMATTER_CACHE.putIfAbsent(key, formatter);
        return formatter;
    }

    /**
     * Converts the enum style to the java.util.Calendar style. Standalone styles
     * are not supported.
     *
     * @param style  the enum style
     * @return the int style, or -1 if style is null, indicating unrequired
     */
    private int convertStyle(FormatStyle style) {
        if (style == null) {
            return -1;
        }
        return style.ordinal();  // indices happen to align
    }

}
