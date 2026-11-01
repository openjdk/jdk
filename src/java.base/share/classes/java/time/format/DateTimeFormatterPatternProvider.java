/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.time.format;

import java.time.DateTimeException;
import java.time.chrono.Chronology;
import java.util.Locale;
import java.util.spi.LocaleServiceProvider;

/**
 * Service Provider Interface for retrieving localized patterns used by
 * {@code DateTimeFormatter} and {@code DateTimeFormatterBuilder} classes.
 * The methods in this class provide localized format pattern strings for use
 * with methods such as {@link DateTimeFormatter#ofLocalizedDateTime(FormatStyle)
 * ofLocalizedDateTime(FormatStyle)}
 * and {@link DateTimeFormatterBuilder#appendLocalized(FormatStyle, FormatStyle)
 * appendLocalized(FormatStyle, FormatStyle)}.
 * For details on using the Locale Sensitive SPI, see
 * {@link LocaleServiceProvider}.
 *
 * @since 27
 */

public abstract class DateTimeFormatterPatternProvider extends LocaleServiceProvider {

    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected DateTimeFormatterPatternProvider() {}

    /**
     * {@return the localized pattern string for the date style,
     * time style, calendar type, and locale} Either {@code dateStyle} or
     * {@code timeStyle} may be {@code null}. In such cases, the returned
     * pattern represents only the time or only the date, respectively. If
     * both are {@code null}, an {@code IllegalArgumentException} is thrown.
     *
     * @param dateStyle {@code FormatStyle} representing date style. {@code null}
     *     for time-only pattern
     * @param timeStyle {@code FormatStyle} representing time style. {@code null}
     *     for date-only pattern
     * @param locale {@code Locale} used to obtain the localized pattern.
     *     Non-null.
     * @param calType Non-null {@code String} representing a CLDR/LDML
     *     calendar type, such as "japanese", "iso8601".
     * @throws IllegalArgumentException if both {@code dateStyle} and
     *     {@code timeStyle} are {@code null}.
     * @throws DateTimeException if no formatting pattern is available for the
     *     specified arguments.
     * @throws NullPointerException if {@code calType} or {@code locale} is
     *     {@code null}.
     * @see Chronology#getCalendarType()
     */
    public abstract String getDateTimeFormatterPattern(FormatStyle dateStyle,
        FormatStyle timeStyle, String calType, Locale locale);

    /**
     * {@return the localized pattern string for the requested template,
     * calendar type, and locale} The {@code requestedTemplate} must match
     * the regular expression described in
     * {@link DateTimeFormatterBuilder#appendLocalized(String)}; otherwise,
     * an {@code IllegalArgumentException} is thrown.
     *
     * @param requestedTemplate requested template, Non-null.
     * @param calType Non-null {@code String} representing a CLDR/LDML
     *     calendar type, such as "japanese", "iso8601".
     * @param locale the {@code Locale} used to obtain the localized pattern.
     *     Non-null.
     * @throws IllegalArgumentException if {@code requestedTemplate} does not match
     *     the regular expression syntax described in
     *     {@link DateTimeFormatterBuilder#appendLocalized(String)
     *     appendLocalized(String)}.
     * @throws DateTimeException if no formatting pattern is available for the
     *     specified arguments.
     * @throws NullPointerException if {@code requestedTemplate}, {@code calType},
     *     or {@code locale} is {@code null}.
     * @see DateTimeFormatterBuilder#appendLocalized(String)
     * @see Chronology#getCalendarType()
     */
    public abstract String getDateTimeFormatterPattern(String requestedTemplate,
        String calType, Locale locale);
}
