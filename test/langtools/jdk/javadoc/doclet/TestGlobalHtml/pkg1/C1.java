/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package pkg1;

import java.io.IOException;
import java.io.Serializable;
import java.text.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A class comment for testing.
 *
 * <p accesskey="C" autocapitalize="sentences" dir="ltr" lang="en" data-purpose="documentation">
 * </p>
 * <table class="plain" id="format-table" data-description="FormatType to FormatStyle mapping">
 * <caption style="display:none">Shows how FormatType and FormatStyle values map to Format instances</caption>
 * <thead>
 *    <tr>
 *       <th scope="col" class="TableHeadingColor">FormatType
 *       <th scope="col" class="TableHeadingColor">FormatStyle
 *       <th scope="col" class="TableHeadingColor">Subformat Created
 * </thead>
 * <tbody>
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@code null}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=7>{@code number}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link NumberFormat#getInstance(Locale) NumberFormat.getInstance}{@code (getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code integer}
 *       <td>{@link NumberFormat#getIntegerInstance(Locale) NumberFormat.getIntegerInstance}{@code (getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code currency}
 *       <td>{@link NumberFormat#getCurrencyInstance(Locale) NumberFormat.getCurrencyInstance}{@code (getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code percent}
 *       <td>{@link NumberFormat#getPercentInstance(Locale) NumberFormat.getPercentInstance}{@code (getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code compact_short}
 *       <td>{@link NumberFormat#getCompactNumberInstance(Locale, NumberFormat.Style)
 *       NumberFormat.getCompactNumberInstance}{@code (getLocale(),} {@link NumberFormat.Style#SHORT})
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code compact_long}
 *       <td>{@link NumberFormat#getCompactNumberInstance(Locale, NumberFormat.Style)
 *       NumberFormat.getCompactNumberInstance}{@code (getLocale(),} {@link NumberFormat.Style#LONG})
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link DecimalFormat#DecimalFormat(String, DecimalFormatSymbols)
 *       DecimalFormat}{@code (subformatPattern,} {@link DecimalFormatSymbols#getInstance(Locale)
 *       DecimalFormatSymbols.getInstance}{@code (getLocale()))}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code dtf_date}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#SHORT}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#LONG}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#FULL}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@link DateTimeFormatter#ofPattern(String, Locale)
 *       DateTimeFormatter.ofPattern}{@code (subformatPattern, getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code dtf_time}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#SHORT}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#LONG}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#FULL}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@link DateTimeFormatter#ofPattern(String, Locale)   DateTimeFormatter.ofPattern}{@code (subformatPattern, getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code dtf_datetime}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateTimeFormatter#ofLocalizedDateTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDateTime(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateTimeFormatter#ofLocalizedDateTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDateTime(}{@link java.time.format.FormatStyle#SHORT}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateTimeFormatter#ofLocalizedDateTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDateTime(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateTimeFormatter#ofLocalizedDateTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDateTime(}{@link java.time.format.FormatStyle#LONG}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateTimeFormatter#ofLocalizedDateTime(java.time.format.FormatStyle)
 *       DateTimeFormatter.ofLocalizedDateTime(}{@link java.time.format.FormatStyle#FULL}{@code ).withLocale(getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@link DateTimeFormatter#ofPattern(String, Locale)
 *       DateTimeFormatter.ofPattern}{@code (subformatPattern, getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=1>{@code pre-defined DateTimeFormatter(s)}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>The {@code pre-defined DateTimeFormatter(s)} are used as a {@code FormatType} :
 *       {@link DateTimeFormatter#BASIC_ISO_DATE BASIC_ISO_DATE},
 *       {@link DateTimeFormatter#ISO_LOCAL_DATE ISO_LOCAL_DATE},
 *       {@link DateTimeFormatter#ISO_OFFSET_DATE ISO_OFFSET_DATE},
 *       {@link DateTimeFormatter#ISO_DATE ISO_DATE},
 *       {@link DateTimeFormatter#ISO_LOCAL_TIME ISO_LOCAL_TIME},
 *       {@link DateTimeFormatter#ISO_OFFSET_TIME ISO_OFFSET_TIME},
 *       {@link DateTimeFormatter#ISO_TIME ISO_TIME},
 *       {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME ISO_LOCAL_DATE_TIME},
 *       {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME ISO_OFFSET_DATE_TIME},
 *       {@link DateTimeFormatter#ISO_ZONED_DATE_TIME ISO_ZONED_DATE_TIME},
 *       {@link DateTimeFormatter#ISO_DATE_TIME ISO_DATE_TIME},
 *       {@link DateTimeFormatter#ISO_ORDINAL_DATE ISO_ORDINAL_DATE},
 *       {@link DateTimeFormatter#ISO_WEEK_DATE ISO_WEEK_DATE},
 *       {@link DateTimeFormatter#ISO_INSTANT ISO_INSTANT},
 *       {@link DateTimeFormatter#RFC_1123_DATE_TIME RFC_1123_DATE_TIME}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code date}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateFormat#getDateInstance(int, Locale)
 *       DateFormat.getDateInstance}{@code (}{@link DateFormat#DEFAULT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateFormat#getDateInstance(int, Locale)
 *       DateFormat.getDateInstance}{@code (}{@link DateFormat#SHORT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateFormat#getDateInstance(int, Locale)
 *       DateFormat.getDateInstance}{@code (}{@link DateFormat#MEDIUM}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateFormat#getDateInstance(int, Locale)
 *       DateFormat.getDateInstance}{@code (}{@link DateFormat#LONG}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateFormat#getDateInstance(int, Locale)
 *       DateFormat.getDateInstance}{@code (}{@link DateFormat#FULL}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link SimpleDateFormat#SimpleDateFormat(String, Locale)
 *       SimpleDateFormat}{@code (subformatPattern, getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code time}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateFormat#getTimeInstance(int, Locale)
 *       DateFormat.getTimeInstance}{@code (}{@link DateFormat#DEFAULT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateFormat#getTimeInstance(int, Locale)
 *       DateFormat.getTimeInstance}{@code (}{@link DateFormat#SHORT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateFormat#getTimeInstance(int, Locale)
 *       DateFormat.getTimeInstance}{@code (}{@link DateFormat#MEDIUM}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateFormat#getTimeInstance(int, Locale)
 *       DateFormat.getTimeInstance}{@code (}{@link DateFormat#LONG}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateFormat#getTimeInstance(int, Locale)
 *       DateFormat.getTimeInstance}{@code (}{@link DateFormat#FULL}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link SimpleDateFormat#SimpleDateFormat(String, Locale)
 *       SimpleDateFormat}{@code (subformatPattern, getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code choice}
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link ChoiceFormat#ChoiceFormat(String) ChoiceFormat}{@code (subformatPattern)}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=3>{@code list}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link ListFormat#getInstance(Locale, ListFormat.Type, ListFormat.Style)
 *       ListFormat.getInstance}{@code (getLocale()}, {@link ListFormat.Type#STANDARD}, {@link ListFormat.Style#FULL})
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code or}
 *       <td>{@link ListFormat#getInstance(Locale, ListFormat.Type, ListFormat.Style)
 *       ListFormat.getInstance}{@code (getLocale()}, {@link ListFormat.Type#OR}, {@link ListFormat.Style#FULL})
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code unit}
 *       <td>{@link ListFormat#getInstance(Locale, ListFormat.Type, ListFormat.Style)
 *       ListFormat.getInstance}{@code (getLocale()}, {@link ListFormat.Type#UNIT}, {@link ListFormat.Style#FULL}}
 * </tbody>
 * </table>
 *
 * @since JDK1.0
 */
public class C1 implements Serializable {

    /**
     * This field indicates whether the C1 is undecorated.
     *
     * <p contenteditable="true" draggable="true" spellcheck="true" data-status="deprecated">
     * Use this field to check if the window is undecorated.
     * </p>
     *
     * @serial
     * @see #setUndecorated(boolean)
     * @since 1.4
     * @deprecated As of JDK version 1.5, replaced by
     * {@link C1#setUndecorated(boolean) setUndecorated(boolean)}.
     */
    @Deprecated
    public boolean undecorated = false;

    /**
     * Constructor.
     *
     * <p style="color:blue;" itemprop="constructor" itemtype="http://schema.org/Person">
     * Initializes a new instance of the C1 class with the specified title and test flag.
     * </p>
     *
     * @param title the title
     * @param test  boolean value
     * @throws IllegalArgumentException if the <code>owner</code>'s
     *                                  <code>GraphicsConfiguration</code> is not from a screen device
     */
    public C1(String title, boolean test) {
    }


    /**
     * <p lang="en"> This is a Constructor</p>
     *
     * @param title title
     */

    public C1(String title) {
    }

    /**
     * Method comments.
     *
     * <p dir="rtl" enterkeyhint="next" data-method="setter">
     * Sets the undecorated property of the window.
     * </p>
     *
     * @param undecorated <code>true</code> if no decorations are
     *                    to be enabled;
     *                    <code>false</code> if decorations are to be enabled.
     * @see #readObject()
     * @since 1.4
     */
    public void setUndecorated(boolean undecorated) {
        /* Make sure we don't run in the middle of peer creation.*/
    }

    /**
     * Can throw an exception
     *
     * @throws java.io.IOException on error
     * @see #setUndecorated(boolean)
     *
     * <p itemid="#readObject" tabindex="0" inputmode="text" data-method="deserializer">
     * Reads the object from a stream.
     * </p>
     */
    public void readObject() throws IOException {
    }

    /**
     * This enum specifies the possible modal exclusion types.
     * <div inert>
     * Modal exclusion types determine how the application handles modal dialogs.
     * </div>
     *
     * @since 1.6
     */
    public static enum ModalExclusionType {

        /**
         * No modal exclusion.
         * <span title="No exclusion" data-tooltip="No exclusion applied">No modal exclusion is applied.</span>
         */
        NO_EXCLUDE,
        /**
         * <code>APPLICATION_EXCLUDE</code> indicates that a top-level window
         * won't be blocked by any application-modal dialogs. Also, it isn't
         * blocked by document-modal dialogs from outside of its child hierarchy.
         * <p>
         * <span hidden>This exclusion type ensures the top-level window remains interactive.</span>
         */
        APPLICATION_EXCLUDE
    }
}
