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

package java.util.spi;

import java.util.Calendar;
import java.util.Map;
import java.util.Locale;

/**
 * An abstract class for service providers that provide localized {@link
 * Calendar} parameters and string representations (display names) of {@code
 * Calendar} field values.
 *
 * <p><a name="calendartypes"><b>Calendar Types</b></a>
 *
 * <p>Calendar types are used to specify calendar systems for which the {@link
 * #getDisplayName(String, int, int, int, Locale) getDisplayName} and {@link
 * #getDisplayNames(String, int, int, Locale) getDisplayNames} methods provide
 * calendar field value names. See {@link Calendar#getCalendarType()} for details.
 *
 * <p><b>Calendar Fields</b>
 *
 * <p>Calendar fields are specified with the constants defined in {@link
 * Calendar}. The following are calendar-common fields and their values to be
 * supported for each calendar system.
 *
 * <table style="border-bottom:1px solid" border="1" cellpadding="3" cellspacing="0" summary="Field values">
 *   <tr>
 *     <th>Field</th>
 *     <th>Value</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td valign="top">{@link Calendar#MONTH}</td>
 *     <td valign="top">{@link Calendar#JANUARY} to {@link Calendar#UNDECIMBER}</td>
 *     <td>Month numbering is 0-based (e.g., 0 - January, ..., 11 -
 *         December). Some calendar systems have 13 months. Month
 *         names need to be supported in both the formatting and
 *         stand-alone forms if required by the supported locales. If there's
 *         no distinction in the two forms, the same names should be returned
 *         in both of the forms.</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">{@link Calendar#DAY_OF_WEEK}</td>
 *     <td valign="top">{@link Calendar#SUNDAY} to {@link Calendar#SATURDAY}</td>
 *     <td>Day-of-week numbering is 1-based starting from Sunday (i.e., 1 - Sunday,
 *         ..., 7 - Saturday).</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">{@link Calendar#AM_PM}</td>
 *     <td valign="top">{@link Calendar#AM} to {@link Calendar#PM}</td>
 *     <td>0 - AM, 1 - PM</td>
 *   </tr>
 * </table>
 *
 * <p style="margin-top:20px">The following are calendar-specific fields and their values to be supported.
 *
 * <table style="border-bottom:1px solid" border="1" cellpadding="3" cellspacing="0" summary="Calendar type and field values">
 *   <tr>
 *     <th>Calendar Type</th>
 *     <th>Field</th>
 *     <th>Value</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td rowspan="2" valign="top">{@code "gregory"}</td>
 *     <td rowspan="2" valign="top">{@link Calendar#ERA}</td>
 *     <td>0</td>
 *     <td>{@link java.util.GregorianCalendar#BC} (BCE)</td>
 *   </tr>
 *   <tr>
 *     <td>1</td>
 *     <td>{@link java.util.GregorianCalendar#AD} (CE)</td>
 *   </tr>
 *   <tr>
 *     <td rowspan="2" valign="top">{@code "buddhist"}</td>
 *     <td rowspan="2" valign="top">{@link Calendar#ERA}</td>
 *     <td>0</td>
 *     <td>BC (BCE)</td>
 *   </tr>
 *   <tr>
 *     <td>1</td>
 *     <td>B.E. (Buddhist Era)</td>
 *   </tr>
 *   <tr>
 *     <td rowspan="6" valign="top">{@code "japanese"}</td>
 *     <td rowspan="5" valign="top">{@link Calendar#ERA}</td>
 *     <td>0</td>
 *     <td>Seireki (Before Meiji)</td>
 *   </tr>
 *   <tr>
 *     <td>1</td>
 *     <td>Meiji</td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>Taisho</td>
 *   </tr>
 *   <tr>
 *     <td>3</td>
 *     <td>Showa</td>
 *   </tr>
 *   <tr>
 *     <td>4</td>
 *     <td >Heisei</td>
 *   </tr>
 *   <tr>
 *     <td>{@link Calendar#YEAR}</td>
 *     <td>1</td>
 *     <td>the first year in each era. It should be returned when a long
 *     style ({@link Calendar#LONG_FORMAT} or {@link Calendar#LONG_STANDALONE}) is
 *     specified. See also the <a href="../../text/SimpleDateFormat.html#year">
 *     Year representation in {@code SimpleDateFormat}</a>.</td>
 *   </tr>
 * </table>
 *
 * <p>Calendar field value names for {@code "gregory"} must be consistent with
 * the date-time symbols provided by {@link java.text.spi.DateFormatSymbolsProvider}.
 *
 * <p>Time zone names are supported by {@link TimeZoneNameProvider}.
 *
 * @author Masayoshi Okutsu
 * @since 1.8
 * @see Locale#getUnicodeLocaleType(String)
 */
public abstract class CalendarDataProvider extends LocaleServiceProvider {

    /**
     * Sole constructor. (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected CalendarDataProvider() {
    }

    /**
     * Returns the first day of a week in the given {@code locale}. This
     * information is required by {@link Calendar} to support operations on the
     * week-related calendar fields.
     *
     * @param locale
     *        the desired locale
     * @return the first day of a week; one of {@link Calendar#SUNDAY} ..
     *         {@link Calendar#SATURDAY},
     *         or 0 if the value isn't available for the {@code locale}
     * @throws NullPointerException
     *         if {@code locale} is {@code null}.
     * @see java.util.Calendar#getFirstDayOfWeek()
     * @see <a href="../Calendar.html#first_week">First Week</a>
     */
    public abstract int getFirstDayOfWeek(Locale locale);

    /**
     * Returns the minimal number of days required in the first week of a
     * year. This information is required by {@link Calendar} to determine the
     * first week of a year. Refer to the description of <a
     * href="../Calendar.html#first_week"> how {@code Calendar} determines
     * the first week</a>.
     *
     * @param locale
     *        the desired locale
     * @return the minimal number of days of the first week,
     *         or 0 if the value isn't available for the {@code locale}
     * @throws NullPointerException
     *         if {@code locale} is {@code null}.
     * @see java.util.Calendar#getMinimalDaysInFirstWeek()
     */
    public abstract int getMinimalDaysInFirstWeek(Locale locale);

    /**
     * Returns the string representation (display name) of the calendar
     * <code>field value</code> in the given <code>style</code> and
     * <code>locale</code>.  If no string representation is
     * applicable, <code>null</code> is returned.
     *
     * <p>{@code field} is a {@code Calendar} field index, such as {@link
     * Calendar#MONTH}. The time zone fields, {@link Calendar#ZONE_OFFSET} and
     * {@link Calendar#DST_OFFSET}, are <em>not</em> supported by this
     * method. {@code null} must be returned if any time zone fields are
     * specified.
     *
     * <p>{@code value} is the numeric representation of the {@code field} value.
     * For example, if {@code field} is {@link Calendar#DAY_OF_WEEK}, the valid
     * values are {@link Calendar#SUNDAY} to {@link Calendar#SATURDAY}
     * (inclusive).
     *
     * <p>{@code style} gives the style of the string representation. It is one
     * of {@link Calendar#SHORT_FORMAT} ({@link Calendar#SHORT SHORT}),
     * {@link Calendar#SHORT_STANDALONE}, {@link Calendar#LONG_FORMAT}
     * ({@link Calendar#LONG LONG}), or {@link Calendar#LONG_STANDALONE}.
     *
     * <p>For example, the following call will return {@code "Sunday"}.
     * <pre>
     * getDisplayName("gregory", Calendar.DAY_OF_WEEK, Calendar.SUNDAY,
     *                Calendar.LONG_STANDALONE, Locale.ENGLISH);
     * </pre>
     *
     * @param calendarType
     *              the calendar type. (Any calendar type given by {@code locale}
     *              is ignored.)
     * @param field
     *              the {@code Calendar} field index,
     *              such as {@link Calendar#DAY_OF_WEEK}
     * @param value
     *              the value of the {@code Calendar field},
     *              such as {@link Calendar#MONDAY}
     * @param style
     *              the string representation style: one of {@link
     *              Calendar#SHORT_FORMAT} ({@link Calendar#SHORT SHORT}),
     *              {@link Calendar#SHORT_STANDALONE}, {@link
     *              Calendar#LONG_FORMAT} ({@link Calendar#LONG LONG}), or
     *              {@link Calendar#LONG_STANDALONE}
     * @param locale
     *              the desired locale
     * @return the string representation of the {@code field value}, or {@code
     *         null} if the string representation is not applicable or
     *         the given calendar type is unknown
     * @throws IllegalArgumentException
     *         if {@code field} or {@code style} is invalid
     * @throws NullPointerException if {@code locale} is {@code null}
     * @see TimeZoneNameProvider
     * @see java.util.Calendar#get(int)
     * @see java.util.Calendar#getDisplayName(int, int, Locale)
     */
    public abstract String getDisplayName(String calendarType,
                                          int field, int value,
                                          int style, Locale locale);

    /**
     * Returns a {@code Map} containing all string representations (display
     * names) of the {@code Calendar} {@code field} in the given {@code style}
     * and {@code locale} and their corresponding field values.
     *
     * <p>{@code field} is a {@code Calendar} field index, such as {@link
     * Calendar#MONTH}. The time zone fields, {@link Calendar#ZONE_OFFSET} and
     * {@link Calendar#DST_OFFSET}, are <em>not</em> supported by this
     * method. {@code null} must be returned if any time zone fields are specified.
     *
     * <p>{@code style} gives the style of the string representation. It must be
     * one of {@link Calendar#ALL_STYLES}, {@link Calendar#SHORT_FORMAT} ({@link
     * Calendar#SHORT SHORT}), {@link Calendar#SHORT_STANDALONE}, {@link
     * Calendar#LONG_FORMAT} ({@link Calendar#LONG LONG}), or {@link
     * Calendar#LONG_STANDALONE}.
     *
     * <p>For example, the following call will return a {@code Map} containing
     * {@code "January"} to {@link Calendar#JANUARY}, {@code "Jan"} to {@link
     * Calendar#JANUARY}, {@code "February"} to {@link Calendar#FEBRUARY},
     * {@code "Feb"} to {@link Calendar#FEBRUARY}, and so on.
     * <pre>
     * getDisplayNames("gregory", Calendar.MONTH, Calendar.ALL_STYLES, Locale.ENGLISH);
     * </pre>
     *
     * @param calendarType
     *              the calendar type. (Any calendar type given by {@code locale}
     *              is ignored.)
     * @param field
     *              the calendar field for which the display names are returned
     * @param style
     *              the style applied to the display names; one of
     *              {@link Calendar#ALL_STYLES}, {@link Calendar#SHORT_FORMAT}
     *              ({@link Calendar#SHORT SHORT}), {@link
     *              Calendar#SHORT_STANDALONE}, {@link Calendar#LONG_FORMAT}
     *              ({@link Calendar#LONG LONG}), or {@link
     *              Calendar#LONG_STANDALONE}.
     * @param locale
     *              the desired locale
     * @return a {@code Map} containing all display names of {@code field} in
     *         {@code style} and {@code locale} and their {@code field} values,
     *         or {@code null} if no display names are defined for {@code field}
     * @throws NullPointerException
     *         if {@code locale} is {@code null}
     * @see Calendar#getDisplayNames(int, int, Locale)
     */
    public abstract Map<String, Integer> getDisplayNames(String calendarType,
                                                         int field, int style,
                                                         Locale locale);
}
