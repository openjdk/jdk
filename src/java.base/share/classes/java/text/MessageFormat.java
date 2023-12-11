/*
 * Copyright (c) 1996, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 *   The original version of this source code and documentation is copyrighted
 * and owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These
 * materials are provided under terms of a License Agreement between Taligent
 * and Sun. This technology is protected by multiple US and International
 * patents. This notice and attribution to Taligent may not be removed.
 *   Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.text;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;


/**
 * {@code MessageFormat} provides a means to produce concatenated
 * messages in a language-neutral way. Use this to construct messages
 * displayed for end users.
 *
 * <p>
 * {@code MessageFormat} takes a set of objects, formats them, then
 * inserts the formatted strings into the pattern at the appropriate places.
 *
 * <p>
 * <strong>Note:</strong>
 * {@code MessageFormat} differs from the other {@code Format}
 * classes in that you create a {@code MessageFormat} object with one
 * of its constructors (not with a {@code getInstance} style factory
 * method). The factory methods aren't necessary because {@code MessageFormat}
 * itself doesn't implement locale specific behavior. Any locale specific
 * behavior is defined by the pattern that you provide as well as the
 * subformats used for inserted arguments.
 *
 * <h2><a id="patterns">Patterns and Their Interpretation</a></h2>
 *
 * {@code MessageFormat} uses patterns of the following form:
 * <blockquote><pre>
 * <i>MessageFormatPattern:</i>
 *         <i>String</i>
 *         <i>MessageFormatPattern</i> <i>FormatElement</i> <i>String</i>
 *
 * <i>FormatElement:</i>
 *         { <i>ArgumentIndex</i> }
 *         { <i>ArgumentIndex</i> , <i>FormatType</i> }
 *         { <i>ArgumentIndex</i> , <i>FormatType</i> , <i>FormatStyle</i> }
 *
 * <i>FormatType: one of </i>
 *         number
 *         date
 *         j_date
 *         time
 *         j_time
 *         choice
 *         list
 *         <i>the DateTimeFormatter predefined formats</i>
 *
 * <i>FormatStyle:</i>
 *         short
 *         medium
 *         long
 *         full
 *         integer
 *         currency
 *         percent
 *         compact_short
 *         compact_long
 *         or
 *         unit
 *         <i>SubformatPattern</i>
 * </pre></blockquote>
 *
 * <p>Within a <i>String</i>, a pair of single quotes can be used to
 * quote any arbitrary characters except single quotes. For example,
 * pattern string <code>"'{0}'"</code> represents string
 * <code>"{0}"</code>, not a <i>FormatElement</i>. A single quote itself
 * must be represented by doubled single quotes {@code ''} throughout a
 * <i>String</i>.  For example, pattern string <code>"'{''}'"</code> is
 * interpreted as a sequence of <code>'{</code> (start of quoting and a
 * left curly brace), {@code ''} (a single quote), and
 * <code>}'</code> (a right curly brace and end of quoting),
 * <em>not</em> <code>'{'</code> and <code>'}'</code> (quoted left and
 * right curly braces): representing string <code>"{'}"</code>,
 * <em>not</em> <code>"{}"</code>.
 *
 * <p>A <i>SubformatPattern</i> is interpreted by its corresponding
 * subformat, and subformat-dependent pattern rules apply. For example,
 * pattern string <code>"{1,number,<u>$'#',##</u>}"</code>
 * (<i>SubformatPattern</i> with underline) will produce a number format
 * with the pound-sign quoted, with a result such as: {@code
 * "$#31,45"}. Refer to each {@code Format} subclass documentation for
 * details.
 *
 * <p>Any unmatched quote is treated as closed at the end of the given
 * pattern. For example, pattern string {@code "'{0}"} is treated as
 * pattern {@code "'{0}'"}.
 *
 * <p>Any curly braces within an unquoted pattern must be balanced. For
 * example, <code>"ab {0} de"</code> and <code>"ab '}' de"</code> are
 * valid patterns, but <code>"ab {0'}' de"</code>, <code>"ab } de"</code>
 * and <code>"''{''"</code> are not.
 *
 * <dl><dt><b>Warning:</b><dd>The rules for using quotes within message
 * format patterns unfortunately have shown to be somewhat confusing.
 * In particular, it isn't always obvious to localizers whether single
 * quotes need to be doubled or not. Make sure to inform localizers about
 * the rules, and tell them (for example, by using comments in resource
 * bundle source files) which strings will be processed by {@code MessageFormat}.
 * Note that localizers may need to use single quotes in translated
 * strings where the original version doesn't have them.
 * </dl>
 * <p>
 * The <i>ArgumentIndex</i> value is a non-negative integer written
 * using the digits {@code '0'} through {@code '9'}, and represents an index into the
 * {@code arguments} array passed to the {@code format} methods
 * or the result array returned by the {@code parse} methods.
 * <p>
 * The <i>FormatType</i> and <i>FormatStyle</i> values are used to create
 * a {@code Format} instance for the format element. The following
 * table shows how the values map to {@code Format} instances. These values
 * are case-insensitive when passed to {@link #applyPattern(String)}. Combinations
 * not shown in the table are illegal, except for the DateTimeFormatter
 * predefined formatters. A <i>SubformatPattern</i> must
 * be a valid pattern string for the {@code Format} subclass used.
 *
 * <table class="plain">
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
 *       <td>{@link NumberFormat#getCompactNumberInstance(Locale, NumberFormat.Style) NumberFormat.getCompactNumberInstance}{@code (getLocale(),} {@link NumberFormat.Style#SHORT})
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code compact_long}
 *       <td>{@link NumberFormat#getCompactNumberInstance(Locale, NumberFormat.Style) NumberFormat.getCompactNumberInstance}{@code (getLocale(),} {@link NumberFormat.Style#LONG})
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link DecimalFormat#DecimalFormat(String,DecimalFormatSymbols) DecimalFormat}{@code (subformatPattern,} {@link DecimalFormatSymbols#getInstance(Locale) DecimalFormatSymbols.getInstance}{@code (getLocale()))}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code date}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateFormat#getDateInstance(int,Locale) DateFormat.getDateInstance}{@code (}{@link DateFormat#DEFAULT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateFormat#getDateInstance(int,Locale) DateFormat.getDateInstance}{@code (}{@link DateFormat#SHORT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateFormat#getDateInstance(int,Locale) DateFormat.getDateInstance}{@code (}{@link DateFormat#MEDIUM}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateFormat#getDateInstance(int,Locale) DateFormat.getDateInstance}{@code (}{@link DateFormat#LONG}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateFormat#getDateInstance(int,Locale) DateFormat.getDateInstance}{@code (}{@link DateFormat#FULL}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link SimpleDateFormat#SimpleDateFormat(String,Locale) SimpleDateFormat}{@code (subformatPattern, getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code j_date}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#SHORT}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#LONG}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateTimeFormatter#ofLocalizedDate(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedDate(}{@link java.time.format.FormatStyle#FULL}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@link DateTimeFormatter#ofPattern(String, Locale)   DateTimeFormatter.ofPattern}{@code (subformatPattern, getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code time}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateFormat#getTimeInstance(int,Locale) DateFormat.getTimeInstance}{@code (}{@link DateFormat#DEFAULT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateFormat#getTimeInstance(int,Locale) DateFormat.getTimeInstance}{@code (}{@link DateFormat#SHORT}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateFormat#getTimeInstance(int,Locale) DateFormat.getTimeInstance}{@code (}{@link DateFormat#MEDIUM}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateFormat#getTimeInstance(int,Locale) DateFormat.getTimeInstance}{@code (}{@link DateFormat#LONG}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateFormat#getTimeInstance(int,Locale) DateFormat.getTimeInstance}{@code (}{@link DateFormat#FULL}{@code , getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link SimpleDateFormat#SimpleDateFormat(String,Locale) SimpleDateFormat}{@code (subformatPattern, getLocale())}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=6>{@code j_time}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code short}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#SHORT}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code medium}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#MEDIUM}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code long}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#LONG}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code full}
 *       <td>{@link DateTimeFormatter#ofLocalizedTime(java.time.format.FormatStyle) DateTimeFormatter.ofLocalizedTime(}{@link java.time.format.FormatStyle#FULL}{@code ).withLocale(getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@link DateTimeFormatter#ofPattern(String, Locale)   DateTimeFormatter.ofPattern}{@code (subformatPattern, getLocale()).toFormat()}
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code choice}
 *       <th scope="row" style="font-weight:normal"><i>SubformatPattern</i>
 *       <td>{@code new} {@link ChoiceFormat#ChoiceFormat(String) ChoiceFormat}{@code (subformatPattern)}
 *    <tr>
 *       <th scope="row" style="font-weight:normal" rowspan=3>{@code list}
 *       <th scope="row" style="font-weight:normal"><i>(none)</i>
 *       <td>{@link ListFormat#getInstance(Locale, ListFormat.Type, ListFormat.Style)  ListFormat.getInstance}{@code (getLocale()}, {@link ListFormat.Type#STANDARD}, {@link ListFormat.Style#FULL})
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code or}
 *       <td>{@link ListFormat#getInstance(Locale, ListFormat.Type, ListFormat.Style)  ListFormat.getInstance}{@code (getLocale()}, {@link ListFormat.Type#OR}, {@link ListFormat.Style#FULL})
 *    <tr>
 *       <th scope="row" style="font-weight:normal">{@code unit}
 *       <td>{@link ListFormat#getInstance(Locale, ListFormat.Type, ListFormat.Style)  ListFormat.getInstance}{@code (getLocale()}, {@link ListFormat.Type#UNIT}, {@link ListFormat.Style#FULL}}
 * </tbody>
 * </table>
 *
 * @apiNote For the <i>j_date</i> and <i>j_time</i> {@code FormatTypes} with a
 * <i>SubformatPattern</i> {@code FormatStyle}, either {@code FormatType} will work with a
 * time, date, or date and time <i>SubformatPattern</i>. As the same method is invoked
 * for both <i>j_date</i> and <i>j_time</i> when using a <i>SubformatPattern</i>,
 * a <i>j_date</i> with a time only <i>SubformatPattern</i> is equivalent to
 * <i>j_time</i> with the same <i>SubformatPattern</i>. This behavior applies to
 * the <i>date</i> and <i>time</i> {@code FormatTypes} as well.
 *
 * <h3>DateTimeFormatter Predefined Formatters (ISO and RFC1123)</h3>
 * Additionally, the {@link DateTimeFormatter} predefined formats are also supported
 * in MessageFormat patterns. To utilize one of these formatter constants, the
 * constant field name can be used as a {@code FormatType}. There are no associated {@code FormatStyles}
 * for these {@code FormatTypes}. For example, the {@code FormatType} <i>iso_date_time</i>
 * returns {@link DateTimeFormatter#ISO_DATE_TIME}{@code .toFormat()}. Similar
 * to <i>j_time</i> and <i>j_date</i>, these {@code FormatTypes} should not be used
 * with {@link Date} and are intended to be used with the {@link java.time} package.
 *
 * <h3>Usage Information</h3>
 *
 * <p>
 * For more sophisticated patterns, you can use a {@code ChoiceFormat}
 * to produce correct forms for singular and plural:
 * {@snippet lang=java :
 * MessageFormat form = new MessageFormat("The disk \"{1}\" contains {0}.");
 * double[] filelimits = {0,1,2};
 * String[] filepart = {"no files","one file","{0,number} files"};
 * ChoiceFormat fileform = new ChoiceFormat(filelimits, filepart);
 * form.setFormatByArgumentIndex(0, fileform);
 *
 * int fileCount = 1273;
 * String diskName = "MyDisk";
 * Object[] testArgs = {Long.valueOf(fileCount), diskName};
 *
 * System.out.println(form.format(testArgs));
 * }
 * The output with different values for {@code fileCount}:
 * <blockquote><pre>
 * The disk "MyDisk" contains no files.
 * The disk "MyDisk" contains one file.
 * The disk "MyDisk" contains 1,273 files.
 * </pre></blockquote>
 *
 * <p>
 * You can create the {@code ChoiceFormat} programmatically, as in the
 * above example, or by using a pattern. See {@link ChoiceFormat}
 * for more information.
 * {@snippet lang=java :
 * form.applyPattern(
 *    "There {0,choice,0#are no files|1#is one file|1<are {0,number,integer} files}.");
 * }
 *
 * <p>
 * <strong>Note:</strong> As we see above, the string produced
 * by a {@code ChoiceFormat} in {@code MessageFormat} is treated as special;
 * occurrences of '{' are used to indicate subformats, and cause recursion.
 * If you create both a {@code MessageFormat} and {@code ChoiceFormat}
 * programmatically (instead of using the string patterns), then be careful not to
 * produce a format that recurses on itself, which will cause an infinite loop.
 * <p>
 * When a single argument is parsed more than once in the string, the last match
 * will be the final result of the parsing.  For example,
 * {@snippet lang=java :
 * MessageFormat mf = new MessageFormat("{0,number,#.##}, {0,number,#.#}");
 * Object[] objs = {Double.valueOf(3.1415)};
 * String result = mf.format( objs );
 * // result now equals "3.14, 3.1"
 * objs = mf.parse(result, new ParsePosition(0));
 * // objs now equals {Double.valueOf(3.1)}
 * }
 *
 * <p>
 * Likewise, parsing with a {@code MessageFormat} object using patterns containing
 * multiple occurrences of the same argument would return the last match.  For
 * example,
 * {@snippet lang=java :
 * MessageFormat mf = new MessageFormat("{0}, {0}, {0}");
 * String forParsing = "x, y, z";
 * Object[] objs = mf.parse(forParsing, new ParsePosition(0));
 * // objs now equals {new String("z")}
 * }
 *
 * <h3>Formatting Time and Date</h3>
 *
 * MessageFormat provides patterns that support both the {@link java.time} package
 * and the {@link Date} type. Consider the 3 following examples,
 * with a date of 11/16/2023.
 *
 * <p>1) a <i>date</i> {@code FormatType} with a <i>full</i> {@code FormatStyle},
 * {@snippet lang=java :
 * Object[] arg = {new Date()};
 * var fmt = new MessageFormat("The date was {0,date,full}");
 * fmt.format(arg); // returns "The date was Thursday, November 16, 2023"
 * }
 *
 * <p>2) a <i>j_date</i> {@code FormatType} with a <i>full</i> {@code FormatStyle},
 * {@snippet lang=java :
 * Object[] arg = {LocalDate.now()};
 * var fmt = new MessageFormat("The date was {0,j_date,full}");
 * fmt.format(arg); // returns "The date was Thursday, November 16, 2023"
 * }
 *
 * <p>3) an <i>iso_local_date</i> {@code FormatType},
 * {@snippet lang=java :
 * Object[] arg = {LocalDate.now()};
 * var fmt = new MessageFormat("The date was {0,iso_local_date}");
 * fmt.format(arg); // returns "The date was 2023-11-16"
 * }
 *
 * <h3><a id="synchronization">Synchronization</a></h3>
 *
 * <p>
 * Message formats are not synchronized.
 * It is recommended to create separate format instances for each thread.
 * If multiple threads access a format concurrently, it must be synchronized
 * externally.
 *
 * @see          java.util.Locale
 * @see          Format
 * @see          NumberFormat
 * @see          DecimalFormat
 * @see          DecimalFormatSymbols
 * @see          ChoiceFormat
 * @see          DateFormat
 * @see          SimpleDateFormat
 * @see          DateTimeFormatter
 *
 * @author       Mark Davis
 * @since 1.1
 */

public class MessageFormat extends Format {

    @java.io.Serial
    private static final long serialVersionUID = 6479157306784022952L;

    /**
     * Constructs a MessageFormat for the default
     * {@link java.util.Locale.Category#FORMAT FORMAT} locale and the
     * specified pattern.
     * The constructor first sets the locale, then parses the pattern and
     * creates a list of subformats for the format elements contained in it.
     * Patterns and their interpretation are specified in the
     * {@linkplain ##patterns class description}.
     *
     * @param pattern the pattern for this message format
     * @throws    IllegalArgumentException if the pattern is invalid
     * @throws    NullPointerException if {@code pattern} is
     *            {@code null}
     */
    public MessageFormat(String pattern) {
        this.locale = Locale.getDefault(Locale.Category.FORMAT);
        applyPatternImpl(pattern);
    }

    /**
     * Constructs a MessageFormat for the specified locale and
     * pattern.
     * The constructor first sets the locale, then parses the pattern and
     * creates a list of subformats for the format elements contained in it.
     * Patterns and their interpretation are specified in the
     * {@linkplain ##patterns class description}.
     *
     * @implSpec The default implementation throws a
     * {@code NullPointerException} if {@code locale} is {@code null}
     * either during the creation of the {@code MessageFormat} object or later
     * when {@code format()} is called by a {@code MessageFormat}
     * instance with a null locale and the implementation utilizes a
     * locale-dependent subformat.
     *
     * @param pattern the pattern for this message format
     * @param locale the locale for this message format
     * @throws    IllegalArgumentException if the pattern is invalid
     * @throws    NullPointerException if {@code pattern} is
     *            {@code null} or {@code locale} is {@code null} and the
     *            implementation uses a locale-dependent subformat.
     * @since 1.4
     */
    public MessageFormat(String pattern, Locale locale) {
        this.locale = locale;
        applyPatternImpl(pattern);
    }

    /**
     * Sets the locale to be used when creating or comparing subformats.
     * This affects subsequent calls
     * <ul>
     * <li>to the {@link #applyPattern applyPattern}
     *     and {@link #toPattern toPattern} methods if format elements specify
     *     a format type and therefore have the subformats created in the
     *     {@code applyPattern} method, as well as
     * <li>to the {@code format} and
     *     {@link #formatToCharacterIterator formatToCharacterIterator} methods
     *     if format elements do not specify a format type and therefore have
     *     the subformats created in the formatting methods.
     * </ul>
     * Subformats that have already been created are not affected.
     *
     * @param locale the locale to be used when creating or comparing subformats
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * Gets the locale that's used when creating or comparing subformats.
     *
     * @return the locale used when creating or comparing subformats
     */
    public Locale getLocale() {
        return locale;
    }


    /**
     * Sets the pattern used by this message format.
     * The method parses the pattern and creates a list of subformats
     * for the format elements contained in it.
     * Patterns and their interpretation are specified in the
     * {@linkplain ##patterns class description}.
     *
     * @param pattern the pattern for this message format
     * @throws    IllegalArgumentException if the pattern is invalid
     * @throws    NullPointerException if {@code pattern} is
     *            {@code null}
     */
    public void applyPattern(String pattern) {
        applyPatternImpl(pattern);
    }

    /**
     * Implementation of applying a pattern to this MessageFormat.
     * This method processes a String pattern in accordance with the MessageFormat
     * pattern syntax and sets the internal {@code pattern} variable as well as
     * populating the {@code formats} array with the subformats defined in the
     * pattern. See the {@linkplain ##patterns} section for further understanding
     * of certain special characters: "{", "}", ",". See {@linkplain
     * ##makeFormat(int, int, StringBuilder[])} for the implementation of setting
     * a subformat.
     */
    @SuppressWarnings("fallthrough") // fallthrough in switch is expected, suppress it
    private void applyPatternImpl(String pattern) {
            StringBuilder[] segments = new StringBuilder[4];
            // Allocate only segments[SEG_RAW] here. The rest are
            // allocated on demand.
            segments[SEG_RAW] = new StringBuilder();

            int part = SEG_RAW;
            int formatNumber = 0;
            boolean inQuote = false;
            int braceStack = 0;
            maxOffset = -1;
            for (int i = 0; i < pattern.length(); ++i) {
                char ch = pattern.charAt(i);
                if (part == SEG_RAW) {
                    if (ch == '\'') {
                        if (i + 1 < pattern.length()
                            && pattern.charAt(i+1) == '\'') {
                            segments[part].append(ch);  // handle doubles
                            ++i;
                        } else {
                            inQuote = !inQuote;
                        }
                    } else if (ch == '{' && !inQuote) {
                        part = SEG_INDEX;
                        if (segments[SEG_INDEX] == null) {
                            segments[SEG_INDEX] = new StringBuilder();
                        }
                    } else {
                        segments[part].append(ch);
                    }
                } else  {
                    if (inQuote) {              // just copy quotes in parts
                        segments[part].append(ch);
                        if (ch == '\'') {
                            inQuote = false;
                        }
                    } else {
                        switch (ch) {
                        case ',':
                            if (part < SEG_MODIFIER) {
                                if (segments[++part] == null) {
                                    segments[part] = new StringBuilder();
                                }
                            } else {
                                segments[part].append(ch);
                            }
                            break;
                        case '{':
                            ++braceStack;
                            segments[part].append(ch);
                            break;
                        case '}':
                            if (braceStack == 0) {
                                part = SEG_RAW;
                                // Set the subformat
                                setFormatFromPattern(i, formatNumber, segments);
                                formatNumber++;
                                // throw away other segments
                                segments[SEG_INDEX] = null;
                                segments[SEG_TYPE] = null;
                                segments[SEG_MODIFIER] = null;
                            } else {
                                --braceStack;
                                segments[part].append(ch);
                            }
                            break;
                        case ' ':
                            // Skip any leading space chars for SEG_TYPE.
                            if (part != SEG_TYPE || segments[SEG_TYPE].length() > 0) {
                                segments[part].append(ch);
                            }
                            break;
                        case '\'':
                            inQuote = true;
                            // fall through, so we keep quotes in other parts
                        default:
                            segments[part].append(ch);
                            break;
                        }
                    }
                }
            }
            if (braceStack == 0 && part != 0) {
                maxOffset = -1;
                throw new IllegalArgumentException("Unmatched braces in the pattern.");
            }
            this.pattern = segments[0].toString();
    }


    /**
     * {@return a String pattern adhering to the {@link ##patterns patterns section} that
     * represents the current state of this {@code MessageFormat}}
     *
     * The string is constructed from internal information and therefore
     * does not necessarily equal the previously applied pattern.
     * @implSpec This method does not always guarantee a conversion of a subformat to
     * a pattern. If a subformat cannot be converted to a String pattern, the {@code
     * FormatType} and {@code FormatStyle} will be omitted from the {@code
     * FormatElement}. To check a subformat, it is recommended to use either
     * {@link #getFormats()} or {@link #getFormatsByArgumentIndex()}.
     */
    public String toPattern() {
        // later, make this more extensible
        int lastOffset = 0;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i <= maxOffset; ++i) {
            copyAndFixQuotes(pattern, lastOffset, offsets[i], result);
            lastOffset = offsets[i];
            result.append('{')
                    .append(argumentNumbers[i])
                    .append(patternFromFormat(formats[i]))
                    .append('}');
        }
        copyAndFixQuotes(pattern, lastOffset, pattern.length(), result);
        return result.toString();
    }

    /**
     * This method converts a Format into a {@code FormatType} and {@code
     * FormatStyle}, if applicable. For each Format, this method will
     * first check against the pre-defined styles established in the
     * {@link ##patterns patterns section}. If a Format does not match to a
     * pre-defined style, it will provide the {@code SubformatPattern}, if the Format
     * class can provide one. The following subformats do not provide a {@code
     * SubformatPattern}: {@link CompactNumberFormat}, {@link ListFormat}, and {@link
     * java.time.format.DateTimeFormatter}. In addition, {@code DateTimeFormatter}
     * does not implement {@code equals()}, and thus cannot be synthesized as a
     * pattern. Any "default"/"medium" styles are omitted per the specification.
     */
    private String patternFromFormat(Format fmt) {
        if (fmt instanceof NumberFormat) {
            // Add any instances returned from the NumberFormat factory methods
            if (fmt.equals(NumberFormat.getInstance(locale))) {
                return ",number";
            } else if (fmt.equals(NumberFormat.getCurrencyInstance(locale))) {
                return ",number,currency";
            } else if (fmt.equals(NumberFormat.getPercentInstance(locale))) {
                return ",number,percent";
            } else if (fmt.equals(NumberFormat.getIntegerInstance(locale))) {
                return ",number,integer";
            } else if (fmt.equals(NumberFormat.getCompactNumberInstance(locale,
                    NumberFormat.Style.SHORT))) {
                return ",number,compact_short";
            } else if (fmt.equals(NumberFormat.getCompactNumberInstance(locale,
                    NumberFormat.Style.LONG))) {
                return ",number,compact_long";
            } else {
                // No pre-defined styles match, return the SubformatPattern
                if (fmt instanceof DecimalFormat dFmt) {
                    return ",number,"+dFmt.toPattern();
                } else if (fmt instanceof ChoiceFormat cFmt) {
                    return ",choice,"+cFmt.toPattern();
                }
            }
        } else if (fmt instanceof DateFormat) {
            // Match to any pre-defined DateFormat styles
            for (DateFormat.Style style : DateFormat.Style.values()) {
                if (fmt.equals(DateFormat.getDateInstance(style.getValue(), locale))) {
                    return ",date"+((style.getValue() != DateFormat.DEFAULT)
                            ? ","+style.name().toLowerCase(Locale.ROOT) : "");
                }
                if (fmt.equals(DateFormat.getTimeInstance(style.getValue(), locale))) {
                    return ",time"+((style.getValue() != DateFormat.DEFAULT)
                            ? ","+style.name().toLowerCase(Locale.ROOT) : "");
                }
            }
            // If no styles match, return the SubformatPattern
            if (fmt instanceof SimpleDateFormat sdFmt) {
                return ",date,"+sdFmt.toPattern();
            }
        } else if (fmt instanceof ListFormat) {
            for (ListFormat.Type type : ListFormat.Type.values()) {
                if (fmt.equals(ListFormat.getInstance(locale, type, ListFormat.Style.FULL))) {
                    return ",list"+((type != ListFormat.Type.STANDARD)
                            ? ","+type.name().toLowerCase(Locale.ROOT) : "");
                }
            }
        }
        else if (fmt != null) {
            // By here, this means that it is a JDK Format class that cannot
            // provide a SubformatPattern or a user-defined Format subclass
        }
        return "";
    }

    /**
     * Sets the formats to use for the values passed into
     * {@code format} methods or returned from {@code parse}
     * methods. The indices of elements in {@code newFormats}
     * correspond to the argument indices used in the previously set
     * pattern string.
     * The order of formats in {@code newFormats} thus corresponds to
     * the order of elements in the {@code arguments} array passed
     * to the {@code format} methods or the result array returned
     * by the {@code parse} methods.
     * <p>
     * If an argument index is used for more than one format element
     * in the pattern string, then the corresponding new format is used
     * for all such format elements. If an argument index is not used
     * for any format element in the pattern string, then the
     * corresponding new format is ignored. If fewer formats are provided
     * than needed, then only the formats for argument indices less
     * than {@code newFormats.length} are replaced.
     *
     * @param newFormats the new formats to use
     * @throws    NullPointerException if {@code newFormats} is null
     * @since 1.4
     */
    public void setFormatsByArgumentIndex(Format[] newFormats) {
        for (int i = 0; i <= maxOffset; i++) {
            int j = argumentNumbers[i];
            if (j < newFormats.length) {
                formats[i] = newFormats[j];
            }
        }
    }

    /**
     * Sets the formats to use for the format elements in the
     * previously set pattern string.
     * The order of formats in {@code newFormats} corresponds to
     * the order of format elements in the pattern string.
     * <p>
     * If more formats are provided than needed by the pattern string,
     * the remaining ones are ignored. If fewer formats are provided
     * than needed, then only the first {@code newFormats.length}
     * formats are replaced.
     * <p>
     * Since the order of format elements in a pattern string often
     * changes during localization, it is generally better to use the
     * {@link #setFormatsByArgumentIndex setFormatsByArgumentIndex}
     * method, which assumes an order of formats corresponding to the
     * order of elements in the {@code arguments} array passed to
     * the {@code format} methods or the result array returned by
     * the {@code parse} methods.
     *
     * @param newFormats the new formats to use
     * @throws    NullPointerException if {@code newFormats} is null
     */
    public void setFormats(Format[] newFormats) {
        int runsToCopy = newFormats.length;
        if (runsToCopy > maxOffset + 1) {
            runsToCopy = maxOffset + 1;
        }
        if (runsToCopy >= 0)
            System.arraycopy(newFormats, 0, formats, 0, runsToCopy);
    }

    /**
     * Sets the format to use for the format elements within the
     * previously set pattern string that use the given argument
     * index.
     * The argument index is part of the format element definition and
     * represents an index into the {@code arguments} array passed
     * to the {@code format} methods or the result array returned
     * by the {@code parse} methods.
     * <p>
     * If the argument index is used for more than one format element
     * in the pattern string, then the new format is used for all such
     * format elements. If the argument index is not used for any format
     * element in the pattern string, then the new format is ignored.
     *
     * @param argumentIndex the argument index for which to use the new format
     * @param newFormat the new format to use
     * @since 1.4
     */
    public void setFormatByArgumentIndex(int argumentIndex, Format newFormat) {
        for (int j = 0; j <= maxOffset; j++) {
            if (argumentNumbers[j] == argumentIndex) {
                formats[j] = newFormat;
            }
        }
    }

    /**
     * Sets the format to use for the format element with the given
     * format element index within the previously set pattern string.
     * The format element index is the zero-based number of the format
     * element counting from the start of the pattern string.
     * <p>
     * Since the order of format elements in a pattern string often
     * changes during localization, it is generally better to use the
     * {@link #setFormatByArgumentIndex setFormatByArgumentIndex}
     * method, which accesses format elements based on the argument
     * index they specify.
     *
     * @param formatElementIndex the index of a format element within the pattern
     * @param newFormat the format to use for the specified format element
     * @throws    ArrayIndexOutOfBoundsException if {@code formatElementIndex} is equal to or
     *            larger than the number of format elements in the pattern string
     */
    public void setFormat(int formatElementIndex, Format newFormat) {

        if (formatElementIndex > maxOffset) {
            throw new ArrayIndexOutOfBoundsException(formatElementIndex);
        }
        formats[formatElementIndex] = newFormat;
    }

    /**
     * Gets the formats used for the values passed into
     * {@code format} methods or returned from {@code parse}
     * methods. The indices of elements in the returned array
     * correspond to the argument indices used in the previously set
     * pattern string.
     * The order of formats in the returned array thus corresponds to
     * the order of elements in the {@code arguments} array passed
     * to the {@code format} methods or the result array returned
     * by the {@code parse} methods.
     * <p>
     * If an argument index is used for more than one format element
     * in the pattern string, then the format used for the last such
     * format element is returned in the array. If an argument index
     * is not used for any format element in the pattern string, then
     * null is returned in the array.
     *
     * @return the formats used for the arguments within the pattern
     * @since 1.4
     */
    public Format[] getFormatsByArgumentIndex() {
        int maximumArgumentNumber = -1;
        for (int i = 0; i <= maxOffset; i++) {
            if (argumentNumbers[i] > maximumArgumentNumber) {
                maximumArgumentNumber = argumentNumbers[i];
            }
        }
        Format[] resultArray = new Format[maximumArgumentNumber + 1];
        for (int i = 0; i <= maxOffset; i++) {
            resultArray[argumentNumbers[i]] = formats[i];
        }
        return resultArray;
    }

    /**
     * Gets the formats used for the format elements in the
     * previously set pattern string.
     * The order of formats in the returned array corresponds to
     * the order of format elements in the pattern string.
     * <p>
     * Since the order of format elements in a pattern string often
     * changes during localization, it's generally better to use the
     * {@link #getFormatsByArgumentIndex getFormatsByArgumentIndex}
     * method, which assumes an order of formats corresponding to the
     * order of elements in the {@code arguments} array passed to
     * the {@code format} methods or the result array returned by
     * the {@code parse} methods.
     *
     * @return the formats used for the format elements in the pattern
     */
    public Format[] getFormats() {
        Format[] resultArray = new Format[maxOffset + 1];
        System.arraycopy(formats, 0, resultArray, 0, maxOffset + 1);
        return resultArray;
    }

    /**
     * Formats an array of objects and appends the {@code MessageFormat}'s
     * pattern, with format elements replaced by the formatted objects, to the
     * provided {@code StringBuffer}.
     * <p>
     * The text substituted for the individual format elements is derived from
     * the current subformat of the format element and the
     * {@code arguments} element at the format element's argument index
     * as indicated by the first matching line of the following table. An
     * argument is <i>unavailable</i> if {@code arguments} is
     * {@code null} or has fewer than argumentIndex+1 elements.
     *
     * <table class="plain">
     * <caption style="display:none">Examples of subformat, argument, and formatted text</caption>
     * <thead>
     *    <tr>
     *       <th scope="col">Subformat
     *       <th scope="col">Argument
     *       <th scope="col">Formatted Text
     * </thead>
     * <tbody>
     *    <tr>
     *       <th scope="row" style="font-weight:normal" rowspan=2><i>any</i>
     *       <th scope="row" style="font-weight:normal"><i>unavailable</i>
     *       <td><code>"{" + argumentIndex + "}"</code>
     *    <tr>
     *       <th scope="row" style="font-weight:normal">{@code null}
     *       <td>{@code "null"}
     *    <tr>
     *       <th scope="row" style="font-weight:normal">{@code instanceof ChoiceFormat}
     *       <th scope="row" style="font-weight:normal"><i>any</i>
     *       <td><code>subformat.format(argument).indexOf('{') &gt;= 0 ?<br>
     *           (new MessageFormat(subformat.format(argument), getLocale())).format(argument) :
     *           subformat.format(argument)</code>
     *    <tr>
     *       <th scope="row" style="font-weight:normal">{@code != null}
     *       <th scope="row" style="font-weight:normal"><i>any</i>
     *       <td>{@code subformat.format(argument)}
     *    <tr>
     *       <th scope="row" style="font-weight:normal" rowspan=4>{@code null}
     *       <th scope="row" style="font-weight:normal">{@code instanceof Number}
     *       <td>{@code NumberFormat.getInstance(getLocale()).format(argument)}
     *    <tr>
     *       <th scope="row" style="font-weight:normal">{@code instanceof Date}
     *       <td>{@code DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, getLocale()).format(argument)}
     *    <tr>
     *       <th scope="row" style="font-weight:normal">{@code instanceof String}
     *       <td>{@code argument}
     *    <tr>
     *       <th scope="row" style="font-weight:normal"><i>any</i>
     *       <td>{@code argument.toString()}
     * </tbody>
     * </table>
     * <p>
     * If {@code pos} is non-null, and refers to
     * {@code Field.ARGUMENT}, the location of the first formatted
     * string will be returned.
     *
     * @param arguments an array of objects to be formatted and substituted.
     * @param result where text is appended.
     * @param pos keeps track on the position of the first replaced argument
     *            in the output string.
     * @return the string buffer passed in as {@code result}, with formatted
     * text appended
     * @throws    IllegalArgumentException if an argument in the
     *            {@code arguments} array is not of the type
     *            expected by the format element(s) that use it.
     * @throws    NullPointerException if {@code result} is {@code null} or
     *            if the {@code MessageFormat} instance that calls this method
     *            has locale set to null, and the implementation
     *            uses a locale-dependent subformat.
     */
    public final StringBuffer format(Object[] arguments, StringBuffer result,
                                     FieldPosition pos)
    {
        return subformat(arguments, result, pos, null);
    }

    /**
     * Creates a MessageFormat with the given pattern and uses it
     * to format the given arguments. This is equivalent to
     * <blockquote>
     *     <code>(new {@link #MessageFormat(String) MessageFormat}(pattern)).{@link #format(java.lang.Object[], java.lang.StringBuffer, java.text.FieldPosition) format}(arguments, new StringBuffer(), null).toString()</code>
     * </blockquote>
     *
     * @param pattern   the pattern string
     * @param arguments object(s) to format
     * @return the formatted string
     * @throws    IllegalArgumentException if the pattern is invalid,
     *            or if an argument in the {@code arguments} array
     *            is not of the type expected by the format element(s)
     *            that use it.
     * @throws    NullPointerException if {@code pattern} is {@code null}
     */
    public static String format(String pattern, Object ... arguments) {
        MessageFormat temp = new MessageFormat(pattern);
        return temp.format(arguments);
    }

    // Overrides
    /**
     * Formats an array of objects and appends the {@code MessageFormat}'s
     * pattern, with format elements replaced by the formatted objects, to the
     * provided {@code StringBuffer}.
     * This is equivalent to
     * <blockquote>
     *     <code>{@link #format(java.lang.Object[], java.lang.StringBuffer, java.text.FieldPosition) format}((Object[]) arguments, result, pos)</code>
     * </blockquote>
     *
     * @param arguments an array of objects to be formatted and substituted.
     * @param result where text is appended.
     * @param pos keeps track on the position of the first replaced argument
     *            in the output string.
     * @throws    IllegalArgumentException if an argument in the
     *            {@code arguments} array is not of the type
     *            expected by the format element(s) that use it.
     * @throws    NullPointerException if {@code result} is {@code null} or
     *            if the {@code MessageFormat} instance that calls this method
     *            has locale set to null, and the implementation
     *            uses a locale-dependent subformat.
     */
    public final StringBuffer format(Object arguments, StringBuffer result,
                                     FieldPosition pos)
    {
        return subformat((Object[]) arguments, result, pos, null);
    }

    /**
     * Formats an array of objects and inserts them into the
     * {@code MessageFormat}'s pattern, producing an
     * {@code AttributedCharacterIterator}.
     * You can use the returned {@code AttributedCharacterIterator}
     * to build the resulting String, as well as to determine information
     * about the resulting String.
     * <p>
     * The text of the returned {@code AttributedCharacterIterator} is
     * the same that would be returned by
     * <blockquote>
     *     <code>{@link #format(java.lang.Object[], java.lang.StringBuffer, java.text.FieldPosition) format}(arguments, new StringBuffer(), null).toString()</code>
     * </blockquote>
     * <p>
     * In addition, the {@code AttributedCharacterIterator} contains at
     * least attributes indicating where text was generated from an
     * argument in the {@code arguments} array. The keys of these attributes are of
     * type {@code MessageFormat.Field}, their values are
     * {@code Integer} objects indicating the index in the {@code arguments}
     * array of the argument from which the text was generated.
     * <p>
     * The attributes/value from the underlying {@code Format}
     * instances that {@code MessageFormat} uses will also be
     * placed in the resulting {@code AttributedCharacterIterator}.
     * This allows you to not only find where an argument is placed in the
     * resulting String, but also which fields it contains in turn.
     *
     * @param arguments an array of objects to be formatted and substituted.
     * @return AttributedCharacterIterator describing the formatted value.
     * @throws    NullPointerException if {@code arguments} is null.
     * @throws    IllegalArgumentException if an argument in the
     *            {@code arguments} array is not of the type
     *            expected by the format element(s) that use it.
     * @since 1.4
     */
    public AttributedCharacterIterator formatToCharacterIterator(Object arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        StringBuffer result = new StringBuffer();
        ArrayList<AttributedCharacterIterator> iterators = new ArrayList<>();

        subformat((Object[]) arguments, result, null, iterators);
        if (iterators.size() == 0) {
            return createAttributedCharacterIterator("");
        }
        return createAttributedCharacterIterator(
                iterators.toArray(new AttributedCharacterIterator[0]));
    }

    /**
     * Parses the string.
     *
     * <p>Caveats: The parse may fail in a number of circumstances.
     * For example:
     * <ul>
     * <li>If one of the arguments does not occur in the pattern.
     * <li>If the format of an argument loses information, such as
     *     with a choice format where a large number formats to "many".
     * <li>Does not yet handle recursion (where
     *     the substituted strings contain {n} references.)
     * <li>Will not always find a match (or the correct match)
     *     if some part of the parse is ambiguous.
     *     For example, if the pattern "{1},{2}" is used with the
     *     string arguments {"a,b", "c"}, it will format as "a,b,c".
     *     When the result is parsed, it will return {"a", "b,c"}.
     * <li>If a single argument is parsed more than once in the string,
     *     then the later parse wins.
     * </ul>
     * When the parse fails, use ParsePosition.getErrorIndex() to find out
     * where in the string the parsing failed.  The returned error
     * index is the starting offset of the sub-patterns that the string
     * is comparing with.  For example, if the parsing string "AAA {0} BBB"
     * is comparing against the pattern "AAD {0} BBB", the error index is
     * 0. When an error occurs, the call to this method will return null.
     * If the source is null, return an empty array.
     *
     * @param source the string to parse
     * @param pos    the parse position
     * @return an array of parsed objects
     * @throws    NullPointerException if {@code pos} is {@code null}
     *            for a non-null {@code source} string.
     */
    public Object[] parse(String source, ParsePosition pos) {
        if (source == null) {
            Object[] empty = {};
            return empty;
        }

        int maximumArgumentNumber = -1;
        for (int i = 0; i <= maxOffset; i++) {
            if (argumentNumbers[i] > maximumArgumentNumber) {
                maximumArgumentNumber = argumentNumbers[i];
            }
        }
        Object[] resultArray = new Object[maximumArgumentNumber + 1];

        int patternOffset = 0;
        int sourceOffset = pos.index;
        ParsePosition tempStatus = new ParsePosition(0);
        for (int i = 0; i <= maxOffset; ++i) {
            // match up to format
            int len = offsets[i] - patternOffset;
            if (len == 0 || pattern.regionMatches(patternOffset,
                                                  source, sourceOffset, len)) {
                sourceOffset += len;
                patternOffset += len;
            } else {
                pos.errorIndex = sourceOffset;
                return null; // leave index as is to signal error
            }

            // now use format
            if (formats[i] == null) {   // string format
                // if at end, use longest possible match
                // otherwise uses first match to intervening string
                // does NOT recursively try all possibilities
                int tempLength = (i != maxOffset) ? offsets[i+1] : pattern.length();

                int next;
                if (patternOffset >= tempLength) {
                    next = source.length();
                }else{
                    next = source.indexOf(pattern.substring(patternOffset, tempLength),
                                          sourceOffset);
                }

                if (next < 0) {
                    pos.errorIndex = sourceOffset;
                    return null; // leave index as is to signal error
                } else {
                    String strValue= source.substring(sourceOffset,next);
                    if (!strValue.equals("{"+argumentNumbers[i]+"}"))
                        resultArray[argumentNumbers[i]]
                            = source.substring(sourceOffset,next);
                    sourceOffset = next;
                }
            } else {
                tempStatus.index = sourceOffset;
                resultArray[argumentNumbers[i]]
                    = formats[i].parseObject(source,tempStatus);
                if (tempStatus.index == sourceOffset) {
                    pos.errorIndex = sourceOffset;
                    return null; // leave index as is to signal error
                }
                sourceOffset = tempStatus.index; // update
            }
        }
        int len = pattern.length() - patternOffset;
        if (len == 0 || pattern.regionMatches(patternOffset,
                                              source, sourceOffset, len)) {
            pos.index = sourceOffset + len;
        } else {
            pos.errorIndex = sourceOffset;
            return null; // leave index as is to signal error
        }
        return resultArray;
    }

    /**
     * Parses text from the beginning of the given string to produce an object
     * array.
     * The method may not use the entire text of the given string.
     * <p>
     * See the {@link #parse(String, ParsePosition)} method for more information
     * on message parsing.
     *
     * @param source A {@code String} whose beginning should be parsed.
     * @return An {@code Object} array parsed from the string.
     * @throws    ParseException if the beginning of the specified string
     *            cannot be parsed.
     */
    public Object[] parse(String source) throws ParseException {
        ParsePosition pos  = new ParsePosition(0);
        Object[] result = parse(source, pos);
        if (pos.index == 0)  // unchanged, returned object is null
            throw new ParseException("MessageFormat parse error!", pos.errorIndex);

        return result;
    }

    /**
     * Parses text from a string to produce an object array.
     * <p>
     * The method attempts to parse text starting at the index given by
     * {@code pos}.
     * If parsing succeeds, then the index of {@code pos} is updated
     * to the index after the last character used (parsing does not necessarily
     * use all characters up to the end of the string), and the parsed
     * object array is returned. The updated {@code pos} can be used to
     * indicate the starting point for the next call to this method.
     * If an error occurs, then the index of {@code pos} is not
     * changed, the error index of {@code pos} is set to the index of
     * the character where the error occurred, and null is returned.
     * <p>
     * See the {@link #parse(String, ParsePosition)} method for more information
     * on message parsing.
     *
     * @param source A {@code String}, part of which should be parsed.
     * @param pos A {@code ParsePosition} object with index and error
     *            index information as described above.
     * @return An {@code Object} array parsed from the string. In case of
     *         error, returns null.
     * @throws NullPointerException if {@code pos} is null.
     */
    public Object parseObject(String source, ParsePosition pos) {
        return parse(source, pos);
    }

    /**
     * Creates and returns a copy of this object.
     *
     * @return a clone of this instance.
     */
    public Object clone() {
        MessageFormat other = (MessageFormat) super.clone();

        // clone arrays. Can't do with utility because of bug in Cloneable
        other.formats = formats.clone(); // shallow clone
        for (int i = 0; i < formats.length; ++i) {
            if (formats[i] != null)
                other.formats[i] = (Format)formats[i].clone();
        }
        // for primitives or immutables, shallow clone is enough
        other.offsets = offsets.clone();
        other.argumentNumbers = argumentNumbers.clone();

        return other;
    }

    /**
     * Compares the specified object with this {@code MessageFormat} for equality.
     * Returns true if the object is also a {@code MessageFormat} and the
     * two formats would format any value the same.
     *
     * @implSpec This method performs an equality check with a notion of class
     * identity based on {@code getClass()}, rather than {@code instanceof}.
     * Therefore, in the equals methods in subclasses, no instance of this class
     * should compare as equal to an instance of a subclass.
     * @param  obj object to be compared for equality
     * @return {@code true} if the specified object is equal to this {@code MessageFormat}
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)                      // quick check
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        MessageFormat other = (MessageFormat) obj;
        return (maxOffset == other.maxOffset
                && pattern.equals(other.pattern)
                && Objects.equals(locale,other.locale)
                && Arrays.equals(offsets,other.offsets)
                && Arrays.equals(argumentNumbers,other.argumentNumbers)
                && Arrays.equals(formats,other.formats));
    }

    /**
     * {@return the hash code value for this {@code MessageFormat}}
     *
     * @implSpec This method calculates the hash code value using the value returned by
     * {@link #toPattern()}.
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return pattern.hashCode(); // enough for reasonable distribution
    }


    /**
     * Defines constants that are used as attribute keys in the
     * {@code AttributedCharacterIterator} returned
     * from {@code MessageFormat.formatToCharacterIterator}.
     *
     * @since 1.4
     */
    public static class Field extends Format.Field {

        // Proclaim serial compatibility with 1.4 FCS
        @java.io.Serial
        private static final long serialVersionUID = 7899943957617360810L;

        /**
         * Creates a Field with the specified name.
         *
         * @param name Name of the attribute
         */
        protected Field(String name) {
            super(name);
        }

        /**
         * Resolves instances being deserialized to the predefined constants.
         *
         * @throws InvalidObjectException if the constant could not be
         *         resolved.
         * @return resolved MessageFormat.Field constant
         */
        @java.io.Serial
        protected Object readResolve() throws InvalidObjectException {
            if (this.getClass() != MessageFormat.Field.class) {
                throw new InvalidObjectException("subclass didn't correctly implement readResolve");
            }

            return ARGUMENT;
        }

        //
        // The constants
        //

        /**
         * Constant identifying a portion of a message that was generated
         * from an argument passed into {@code formatToCharacterIterator}.
         * The value associated with the key will be an {@code Integer}
         * indicating the index in the {@code arguments} array of the
         * argument from which the text was generated.
         */
        public static final Field ARGUMENT =
                           new Field("message argument field");
    }

    // ===========================privates============================

    /**
     * The locale to use for formatting numbers and dates.
     * @serial
     */
    private Locale locale;

    /**
     * The string that the formatted values are to be plugged into.  In other words, this
     * is the pattern supplied on construction with all of the {} expressions taken out.
     * @serial
     */
    private String pattern = "";

    /** The initially expected number of subformats in the format */
    private static final int INITIAL_FORMATS = 10;

    /**
     * An array of formatters, which are used to format the arguments.
     * @serial
     */
    private Format[] formats = new Format[INITIAL_FORMATS];

    /**
     * The positions where the results of formatting each argument are to be inserted
     * into the pattern.
     * @serial
     */
    private int[] offsets = new int[INITIAL_FORMATS];

    /**
     * The argument numbers corresponding to each formatter.  (The formatters are stored
     * in the order they occur in the pattern, not in the order in which the arguments
     * are specified.)
     * @serial
     */
    private int[] argumentNumbers = new int[INITIAL_FORMATS];

    /**
     * One less than the number of entries in {@code offsets}.  Can also be thought of
     * as the index of the highest-numbered element in {@code offsets} that is being used.
     * All of these arrays should have the same number of elements being used as {@code offsets}
     * does, and so this variable suffices to tell us how many entries are in all of them.
     * @serial
     */
    private int maxOffset = -1;

    /**
     * Internal routine used by format. If {@code characterIterators} is
     * {@code non-null}, AttributedCharacterIterator will be created from the
     * subformats as necessary. If {@code characterIterators} is {@code null}
     * and {@code fp} is {@code non-null} and identifies
     * {@code Field.ARGUMENT} as the field attribute, the location of
     * the first replaced argument will be set in it.
     *
     * @throws    IllegalArgumentException if an argument in the
     *            {@code arguments} array is not of the type
     *            expected by the format element(s) that use it.
     */
    private StringBuffer subformat(Object[] arguments, StringBuffer result,
                                   FieldPosition fp, List<AttributedCharacterIterator> characterIterators) {
        // note: this implementation assumes a fast substring & index.
        // if this is not true, would be better to append chars one by one.
        int lastOffset = 0;
        int last = result.length();
        for (int i = 0; i <= maxOffset; ++i) {
            result.append(pattern, lastOffset, offsets[i]);
            lastOffset = offsets[i];
            int argumentNumber = argumentNumbers[i];
            if (arguments == null || argumentNumber >= arguments.length) {
                result.append('{').append(argumentNumber).append('}');
                continue;
            }
            // int argRecursion = ((recursionProtection >> (argumentNumber*2)) & 0x3);
            if (false) { // if (argRecursion == 3){
                // prevent loop!!!
                result.append('\uFFFD');
            } else {
                Object obj = arguments[argumentNumber];
                String arg = null;
                Format subFormatter = null;
                if (obj == null) {
                    arg = "null";
                } else if (formats[i] != null) {
                    subFormatter = formats[i];
                    if (subFormatter instanceof ChoiceFormat) {
                        arg = formats[i].format(obj);
                        if (arg.indexOf('{') >= 0) {
                            subFormatter = new MessageFormat(arg, locale);
                            obj = arguments;
                            arg = null;
                        }
                    }
                } else if (obj instanceof Number) {
                    // format number if can
                    subFormatter = NumberFormat.getInstance(locale);
                } else if (obj instanceof Date) {
                    // format a Date if can
                    subFormatter = DateFormat.getDateTimeInstance(
                             DateFormat.SHORT, DateFormat.SHORT, locale);//fix
                } else if (obj instanceof String) {
                    arg = (String) obj;

                } else {
                    arg = obj.toString();
                    if (arg == null) arg = "null";
                }

                // At this point we are in two states, either subFormatter
                // is non-null indicating we should format obj using it,
                // or arg is non-null and we should use it as the value.

                if (characterIterators != null) {
                    // If characterIterators is non-null, it indicates we need
                    // to get the CharacterIterator from the child formatter.
                    if (last != result.length()) {
                        characterIterators.add(
                            createAttributedCharacterIterator(result.substring
                                                              (last)));
                        last = result.length();
                    }
                    if (subFormatter != null) {
                        AttributedCharacterIterator subIterator =
                                   subFormatter.formatToCharacterIterator(obj);

                        append(result, subIterator);
                        if (last != result.length()) {
                            characterIterators.add(
                                         createAttributedCharacterIterator(
                                         subIterator, Field.ARGUMENT,
                                         Integer.valueOf(argumentNumber)));
                            last = result.length();
                        }
                        arg = null;
                    }
                    if (arg != null && !arg.isEmpty()) {
                        result.append(arg);
                        characterIterators.add(
                                 createAttributedCharacterIterator(
                                 arg, Field.ARGUMENT,
                                 Integer.valueOf(argumentNumber)));
                        last = result.length();
                    }
                }
                else {
                    if (subFormatter != null) {
                        arg = subFormatter.format(obj);
                    }
                    last = result.length();
                    result.append(arg);
                    if (i == 0 && fp != null && Field.ARGUMENT.equals(
                                  fp.getFieldAttribute())) {
                        fp.setBeginIndex(last);
                        fp.setEndIndex(result.length());
                    }
                    last = result.length();
                }
            }
        }
        result.append(pattern, lastOffset, pattern.length());
        if (characterIterators != null && last != result.length()) {
            characterIterators.add(createAttributedCharacterIterator(
                                   result.substring(last)));
        }
        return result;
    }

    /**
     * Convenience method to append all the characters in
     * {@code iterator} to the StringBuffer {@code result}.
     */
    private void append(StringBuffer result, CharacterIterator iterator) {
        if (iterator.first() != CharacterIterator.DONE) {
            char aChar;

            result.append(iterator.first());
            while ((aChar = iterator.next()) != CharacterIterator.DONE) {
                result.append(aChar);
            }
        }
    }

    // Indices for segments
    private static final int SEG_RAW      = 0; // String in MessageFormatPattern
    private static final int SEG_INDEX    = 1; // ArgumentIndex
    private static final int SEG_TYPE     = 2; // FormatType
    private static final int SEG_MODIFIER = 3; // FormatStyle

    /**
     * This method sets a Format in the {@code formats} array for the
     * corresponding {@code argumentNumber} based on the pattern supplied.
     * If the pattern supplied does not contain a {@code FormatType}, null
     * is stored in the {@code formats} array.
     */
    private void setFormatFromPattern(int position, int offsetNumber,
                            StringBuilder[] textSegments) {

        // Convert any null values in textSegments to empty string
        String[] segments = new String[textSegments.length];
        for (int i = 0; i < textSegments.length; i++) {
            StringBuilder oneseg = textSegments[i];
            segments[i] = (oneseg != null) ? oneseg.toString() : "";
        }

        // get the argument number
        int argumentNumber;
        try {
            argumentNumber = Integer.parseInt(segments[SEG_INDEX]); // always unlocalized!
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("can't parse argument number: "
                                               + segments[SEG_INDEX], e);
        }
        if (argumentNumber < 0) {
            throw new IllegalArgumentException("negative argument number: "
                                               + argumentNumber);
        }

        // resize format information arrays if necessary
        if (offsetNumber >= formats.length) {
            int newLength = formats.length * 2;
            Format[] newFormats = new Format[newLength];
            int[] newOffsets = new int[newLength];
            int[] newArgumentNumbers = new int[newLength];
            System.arraycopy(formats, 0, newFormats, 0, maxOffset + 1);
            System.arraycopy(offsets, 0, newOffsets, 0, maxOffset + 1);
            System.arraycopy(argumentNumbers, 0, newArgumentNumbers, 0, maxOffset + 1);
            formats = newFormats;
            offsets = newOffsets;
            argumentNumbers = newArgumentNumbers;
        }

        int oldMaxOffset = maxOffset;
        maxOffset = offsetNumber;
        offsets[offsetNumber] = segments[SEG_RAW].length();
        argumentNumbers[offsetNumber] = argumentNumber;

        // Only search for corresponding type/style if type is not empty
        if (!segments[SEG_TYPE].isEmpty()) {
            try {
                formats[offsetNumber] = formatFromPattern(segments[SEG_TYPE], segments[SEG_MODIFIER]);
            } catch (Exception e) {
                // Catch to reset maxOffset
                maxOffset = oldMaxOffset;
                throw e;
            }
        } else {
            // Type "" is allowed. e.g., "{0,}", "{0,,}", and "{0,,#}"
            // are treated as "{0}".
            formats[offsetNumber] = null;
        }
    }

    /**
     * This method converts a {@code FormatType} and {@code FormatStyle} to a
     * {@code Format} value. The String parameters are converted
     * to their corresponding enum values FormatType and FormatStyle which are used
     * to return a {@code Format}. See the patterns section in the class
     * description for further detail on a MessageFormat pattern.
     *
     * @param type the {@code FormatType} in {@code FormatElement}
     * @param style the {@code FormatStyle} in {@code FormatElement}
     * @return a Format that corresponds to the corresponding {@code formatType}
     *         and {@code formatStyle}
     * @throws IllegalArgumentException if a Format cannot be produced from the
     *         type and style provided
     */
    private Format formatFromPattern(String type, String style) {
        // Get the type, if it's valid
        FormatType fType;
        try {
            fType = FormatType.fromString(type);
        } catch (IllegalArgumentException iae) {
            // Invalid type throws exception
            throw new IllegalArgumentException("unknown format type: " + type);
        }
        // Get the style if recognized, otherwise treat style as a SubformatPattern
        FormatStyle fStyle;
        try {
            fStyle = FormatStyle.fromString(style);
        } catch (IllegalArgumentException iae) {
            fStyle = FormatStyle.SUBFORMATPATTERN;
        }
        return switch (fType) {
            case NUMBER -> switch (fStyle) {
                case DEFAULT -> NumberFormat.getInstance(locale);
                case CURRENCY ->
                        NumberFormat.getCurrencyInstance(locale);
                case PERCENT ->
                        NumberFormat.getPercentInstance(locale);
                case INTEGER ->
                        NumberFormat.getIntegerInstance(locale);
                case COMPACT_SHORT ->
                        NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT);
                case COMPACT_LONG ->
                        NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.LONG);
                default -> formatFromSubformatPattern(fType, style);
            };
            case DATE -> switch (fStyle) {
                case DEFAULT ->
                        DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
                case SHORT ->
                        DateFormat.getDateInstance(DateFormat.SHORT, locale);
                case MEDIUM ->
                        DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
                case LONG ->
                        DateFormat.getDateInstance(DateFormat.LONG, locale);
                case FULL ->
                        DateFormat.getDateInstance(DateFormat.FULL, locale);
                default -> formatFromSubformatPattern(fType, style);
            };
            case TIME -> switch (fStyle) {
                case DEFAULT ->
                        DateFormat.getTimeInstance(DateFormat.DEFAULT, locale);
                case SHORT ->
                        DateFormat.getTimeInstance(DateFormat.SHORT, locale);
                case MEDIUM ->
                        DateFormat.getTimeInstance(DateFormat.MEDIUM, locale);
                case LONG ->
                        DateFormat.getTimeInstance(DateFormat.LONG, locale);
                case FULL ->
                        DateFormat.getTimeInstance(DateFormat.FULL, locale);
                default -> formatFromSubformatPattern(fType, style);
            };
            case J_DATE -> switch (fStyle) {
                case DEFAULT, MEDIUM ->
                        DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale).toFormat();
                case SHORT ->
                        DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(locale).toFormat();
                case LONG ->
                        DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale).toFormat();
                case FULL ->
                        DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale).toFormat();
                default -> formatFromSubformatPattern(fType, style);
            };
            case J_TIME -> switch (fStyle) {
                case DEFAULT, MEDIUM ->
                        DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.MEDIUM).withLocale(locale).toFormat();
                case SHORT ->
                        DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale).toFormat();
                case LONG ->
                        DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.LONG).withLocale(locale).toFormat();
                case FULL ->
                        DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.FULL).withLocale(locale).toFormat();
                default -> formatFromSubformatPattern(fType, style);
            };
            case CHOICE -> formatFromSubformatPattern(fType, style);
            case LIST -> switch (fStyle) {
                case DEFAULT ->
                        ListFormat.getInstance(locale, ListFormat.Type.STANDARD, ListFormat.Style.FULL);
                case OR ->
                        ListFormat.getInstance(locale, ListFormat.Type.OR, ListFormat.Style.FULL);
                case UNIT ->
                        ListFormat.getInstance(locale, ListFormat.Type.UNIT, ListFormat.Style.FULL);
                // ListFormat does not provide a String pattern method/constructor
                default -> formatFromSubformatPattern(fType, style);
            };
            // The DateTimeFormatter constants are only given as a type
            // Regardless of style, return the corresponding DTF constant
            case BASIC_ISO_DATE -> DateTimeFormatter.BASIC_ISO_DATE.toFormat();
            case ISO_LOCAL_DATE -> DateTimeFormatter.ISO_LOCAL_DATE.toFormat();
            case ISO_OFFSET_DATE -> DateTimeFormatter.ISO_OFFSET_DATE.toFormat();
            case ISO_DATE -> DateTimeFormatter.ISO_DATE.toFormat();
            case ISO_LOCAL_TIME -> DateTimeFormatter.ISO_LOCAL_TIME.toFormat();
            case ISO_OFFSET_TIME -> DateTimeFormatter.ISO_OFFSET_TIME.toFormat();
            case ISO_TIME -> DateTimeFormatter.ISO_TIME.toFormat();
            case ISO_LOCAL_DATE_TIME -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.toFormat();
            case ISO_OFFSET_DATE_TIME -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.toFormat();
            case ISO_ZONED_DATE_TIME -> DateTimeFormatter.ISO_ZONED_DATE_TIME.toFormat();
            case ISO_DATE_TIME -> DateTimeFormatter.ISO_DATE_TIME.toFormat();
            case ISO_ORDINAL_DATE -> DateTimeFormatter.ISO_ORDINAL_DATE.toFormat();
            case ISO_WEEK_DATE -> DateTimeFormatter.ISO_WEEK_DATE.toFormat();
            case ISO_INSTANT -> DateTimeFormatter.ISO_INSTANT.toFormat();
            case RFC_1123_DATE_TIME -> DateTimeFormatter.RFC_1123_DATE_TIME.toFormat();
        };
    }

    /**
     * This method will attempt to return a subformat produced with the provided
     * SubformatPattern applied. If the subformat does not support SubformatPatterns
     * or the SubformatPattern is illegal to the subformat, an IllegalArgumentException
     * is thrown. To adhere to the specification, this method ensures if an underlying
     * exception is thrown, it is rethrown as an IllegalArgumentException unless
     * the underlying exception is itself an IAE, or an NPE.
     *
     * @param fType the enum type of the subformat
     * @param pattern the SubformatPattern to be applied
     * @return a Format that corresponds to the corresponding {@code fType}
     *         and {@code pattern}
     * @throws IllegalArgumentException if a Format cannot be produced from the
     *         type and SubformatPattern provided
     */
    private Format formatFromSubformatPattern(FormatType fType, String pattern) {
        // Modified for neater exception value if needed
        String type = fType.text.substring(0,1).toUpperCase(Locale.ROOT)+fType.text.substring(1);
        try {
            return switch(fType) {
                case NUMBER -> new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
                case DATE, TIME -> new SimpleDateFormat(pattern, locale);
                case J_DATE, J_TIME -> DateTimeFormatter.ofPattern(pattern).toFormat();
                case CHOICE -> new ChoiceFormat(pattern);
                default ->  throw new IllegalArgumentException(String.format(
                            "Unexpected modifier for %s: %s", type, pattern));
            };
        } catch (Exception e) {
            // getClass check over separate catch block to not catch the IAE subclasses
            // For example, ChoiceFormat can throw a NumberFormatException
            if (e.getClass() == IllegalArgumentException.class
                    || e.getClass() == NullPointerException.class) {
                // If IAE no need to wrap with another IAE
                // If NPE, it should be thrown as is (as specified)
                throw e;
            } else {
                throw new IllegalArgumentException(String.format(
                        "%s pattern incorrect: %s", type, pattern), e);
            }
        }
    }

    private static void copyAndFixQuotes(String source, int start, int end,
                                         StringBuilder target) {
        boolean quoted = false;

        for (int i = start; i < end; ++i) {
            char ch = source.charAt(i);
            if (ch == '{') {
                if (!quoted) {
                    target.append('\'');
                    quoted = true;
                }
                target.append(ch);
            } else if (ch == '\'') {
                target.append("''");
            } else {
                if (quoted) {
                    target.append('\'');
                    quoted = false;
                }
                target.append(ch);
            }
        }
        if (quoted) {
            target.append('\'');
        }
    }

    // Corresponding to the FormatType pattern
    private enum FormatType {
        NUMBER("number"),
        DATE("date"),
        TIME("time"),
        J_DATE("j_date"),
        J_TIME("j_time"),
        CHOICE("choice"),
        LIST("list"),

        // Pre-defined DateTimeFormatter types
        BASIC_ISO_DATE("basic_iso_date"),
        ISO_LOCAL_DATE("iso_local_date"),
        ISO_OFFSET_DATE ("iso_offset_date"),
        ISO_DATE("iso_date"),
        ISO_LOCAL_TIME("iso_local_time"),
        ISO_OFFSET_TIME("iso_offset_time"),
        ISO_TIME("iso_time"),
        ISO_LOCAL_DATE_TIME("iso_local_date_time"),
        ISO_OFFSET_DATE_TIME("iso_offset_date_time"),
        ISO_ZONED_DATE_TIME("iso_zoned_date_time"),
        ISO_DATE_TIME("iso_date_time"),
        ISO_ORDINAL_DATE("iso_ordinal_date"),
        ISO_WEEK_DATE("iso_week_date"),
        ISO_INSTANT("iso_instant"),
        RFC_1123_DATE_TIME("rfc_1123_date_time");

        private final String text;

        FormatType(String text){
            this.text = text;
        }

        // This method returns a FormatType that matches the passed String.
        // If no matching FormatType is found, an IllegalArgumentException is thrown.
        private static FormatType fromString(String text) {
            for (FormatType type : values()) {
                // Also check trimmed lower case for historical reasons
                if (text.equals(type.text)
                        || text.trim().toLowerCase(Locale.ROOT).equals(type.text)) {
                    return type;
                }
            }
            throw new IllegalArgumentException();
        }
    }

    // Corresponding to the FormatStyle pattern
    private enum FormatStyle {
        DEFAULT(""),
        SHORT("short"),
        MEDIUM("medium"),
        LONG("long"),
        FULL("full"),
        INTEGER("integer"),
        CURRENCY("currency"),
        PERCENT("percent"),
        COMPACT_SHORT("compact_short"),
        COMPACT_LONG("compact_long"),
        OR("or"),
        UNIT("unit"),
        SUBFORMATPATTERN(null);

        private final String text;
        FormatStyle(String text){
            this.text = text;
        }

        // This method returns a FormatStyle that matches the passed String.
        // If no FormatStyle is found, IllegalArgumentException is thrown
        private static FormatStyle fromString(String text) {
            for (FormatStyle style : values()) {
                // Also check trimmed lower case for historical reasons
                if (text.equals(style.text)
                        || text.trim().toLowerCase(Locale.ROOT).equals(style.text)) {
                    return style;
                }
            }
            throw new IllegalArgumentException();
        }
    }

    /**
     * After reading an object from the input stream, do a simple verification
     * to maintain class invariants.
     * @throws InvalidObjectException if the objects read from the stream is invalid.
     */
    @java.io.Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        boolean isValid = maxOffset >= -1
                && formats.length > maxOffset
                && offsets.length > maxOffset
                && argumentNumbers.length > maxOffset;
        if (isValid) {
            int lastOffset = pattern.length() + 1;
            for (int i = maxOffset; i >= 0; --i) {
                if ((offsets[i] < 0) || (offsets[i] > lastOffset)) {
                    isValid = false;
                    break;
                } else {
                    lastOffset = offsets[i];
                }
            }
        }
        if (!isValid) {
            throw new InvalidObjectException("Could not reconstruct MessageFormat from corrupt stream.");
        }
    }
}
