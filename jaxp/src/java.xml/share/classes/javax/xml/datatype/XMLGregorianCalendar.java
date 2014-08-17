/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.datatype;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.TimeZone;
import java.util.GregorianCalendar;

/**
 * <p>Representation for W3C XML Schema 1.0 date/time datatypes.
 * Specifically, these date/time datatypes are
 * {@link DatatypeConstants#DATETIME},
 * {@link DatatypeConstants#TIME},
 * {@link DatatypeConstants#DATE},
 * {@link DatatypeConstants#GYEARMONTH},
 * {@link DatatypeConstants#GMONTHDAY},
 * {@link DatatypeConstants#GYEAR},
 * {@link DatatypeConstants#GMONTH}, and
 * {@link DatatypeConstants#GDAY}
 * defined in the XML Namespace
 * <code>"http://www.w3.org/2001/XMLSchema"</code>.
 * These datatypes are normatively defined in
 * <a href="http://www.w3.org/TR/xmlschema-2/#dateTime">W3C XML Schema 1.0 Part 2, Section 3.2.7-14</a>.</p>
 *
 * <p>The table below defines the mapping between XML Schema 1.0
 * date/time datatype fields and this class' fields. It also summarizes
 * the value constraints for the date and time fields defined in
 * <a href="http://www.w3.org/TR/xmlschema-2/#isoformats">W3C XML Schema 1.0 Part 2, Appendix D,
 * <i>ISO 8601 Date and Time Formats</i></a>.</p>
 *
 * <a name="datetimefieldmapping"/>
 * <table border="2" rules="all" cellpadding="2">
 *   <thead>
 *     <tr>
 *       <th align="center" colspan="3">
 *         Date/Time Datatype Field Mapping Between XML Schema 1.0 and Java Representation
 *       </th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <th>XML Schema 1.0<br/>
 *           datatype<br/>
 *            field</th>
 *       <th>Related<br/>XMLGregorianCalendar<br/>Accessor(s)</th>
 *       <th>Value Range</th>
 *     </tr>
 *     <tr>
 *       <td><a name="datetimefield-year"/>year</td>
 *       <td> {@link #getYear()} + {@link #getEon()} or<br/>
 *            {@link #getEonAndYear}
 *       </td>
 *       <td> <code>getYear()</code> is a value between -(10^9-1) to (10^9)-1
 *            or {@link DatatypeConstants#FIELD_UNDEFINED}.<br/>
 *            {@link #getEon()} is high order year value in billion of years.<br/>
 *            <code>getEon()</code> has values greater than or equal to (10^9) or less than or equal to -(10^9).
 *            A value of null indicates field is undefined.</br>
 *            Given that <a href="http://www.w3.org/2001/05/xmlschema-errata#e2-63">XML Schema 1.0 errata</a> states that the year zero
 *            will be a valid lexical value in a future version of XML Schema,
 *            this class allows the year field to be set to zero. Otherwise,
 *            the year field value is handled exactly as described
 *            in the errata and [ISO-8601-1988]. Note that W3C XML Schema 1.0
 *            validation does not allow for the year field to have a value of zero.
 *            </td>
 *     </tr>
 *     <tr>
 *       <td><a name="datetimefield-month"/>month</td>
 *       <td> {@link #getMonth()} </td>
 *       <td> 1 to 12 or {@link DatatypeConstants#FIELD_UNDEFINED} </td>
 *     </tr>
 *     <tr>
 *       <td><a name="datetimefield-day"/>day</td>
 *       <td> {@link #getDay()} </td>
 *       <td> Independent of month, max range is 1 to 31 or {@link DatatypeConstants#FIELD_UNDEFINED}.<br/>
 *            The normative value constraint stated relative to month
 *            field's value is in <a href="http://www.w3.org/TR/xmlschema-2/#isoformats">W3C XML Schema 1.0 Part 2, Appendix D</a>.
 *       </td>
 *     </tr>
 *     <tr>
 *       <td><a name="datetimefield-hour"/>hour</td>
 *       <td>{@link #getHour()}</td>
 *       <td>
 *         0 to 23 or {@link DatatypeConstants#FIELD_UNDEFINED}.
 *         An hour value of 24 is allowed to be set in the lexical space provided the minute and second
 *         field values are zero. However, an hour value of 24 is not allowed in value space and will be
 *         transformed to represent the value of the first instance of the following day as per
 *         <a href="http://www.w3.org/TR/xmlschema-2/#built-in-primitive-datatypes">
 *         XML Schema Part 2: Datatypes Second Edition, 3.2 Primitive datatypes</a>.
 *       </td>
 *     </tr>
 *     <tr>
 *       <td><a name="datetimefield-minute"/>minute</td>
 *       <td> {@link #getMinute()} </td>
 *       <td> 0 to 59 or {@link DatatypeConstants#FIELD_UNDEFINED} </td>
 *     </tr>
 *     <tr>
 *       <td><a name="datetimefield-second"/>second</td>
 *       <td>
 *         {@link #getSecond()} + {@link #getMillisecond()}/1000 or<br/>
 *         {@link #getSecond()} + {@link #getFractionalSecond()}
 *       </td>
 *       <td>
 *         {@link #getSecond()} from 0 to 60 or {@link DatatypeConstants#FIELD_UNDEFINED}.<br/>
 *         <i>(Note: 60 only allowable for leap second.)</i><br/>
 *         {@link #getFractionalSecond()} allows for infinite precision over the range from 0.0 to 1.0 when
 *         the {@link #getSecond()} is defined.<br/>
 *         <code>FractionalSecond</code> is optional and has a value of <code>null</code> when it is undefined.<br />
 *            {@link #getMillisecond()} is the convenience
 *            millisecond precision of value of {@link #getFractionalSecond()}.
 *       </td>
 *     </tr>
 *     <tr>
 *       <td><a name="datetimefield-timezone"/>timezone</td>
 *       <td> {@link #getTimezone()} </td>
 *       <td> Number of minutes or {@link DatatypeConstants#FIELD_UNDEFINED}.
 *         Value range from -14 hours (-14 * 60 minutes) to 14 hours (14 * 60 minutes).
 *       </td>
 *     </tr>
 *   </tbody>
 *  </table>
 *
 * <p>All maximum value space constraints listed for the fields in the table
 * above are checked by factory methods, @{link DatatypeFactory},
 * setter methods and parse methods of
 * this class. <code>IllegalArgumentException</code> is thrown when a
 * parameter's value is outside the value constraint for the field or
 * if the composite
 * values constitute an invalid XMLGregorianCalendar instance (for example, if
 * the 31st of June is specified).
 * </p>
 *
 * <p>The following operations are defined for this class:
 * <ul>
 *   <li>accessors/mutators for independent date/time fields</li>
 *   <li>conversion between this class and W3C XML Schema 1.0 lexical representation,
 *     {@link #toString()}, {@link DatatypeFactory#newXMLGregorianCalendar(String lexicalRepresentation)}</li>
 *   <li>conversion between this class and {@link GregorianCalendar},
 *     {@link #toGregorianCalendar(java.util.TimeZone timezone, java.util.Locale aLocale, XMLGregorianCalendar defaults)},
 *     {@link DatatypeFactory}</li>
 *   <li>partial order relation comparator method, {@link #compare(XMLGregorianCalendar xmlGregorianCalendar)}</li>
 *   <li>{@link #equals(Object)} defined relative to {@link #compare(XMLGregorianCalendar xmlGregorianCalendar)}.</li>
 *   <li>addition operation with {@link Duration}
 *      instance as defined in <a href="http://www.w3.org/TR/xmlschema-2/#adding-durations-to-dateTimes">
 *      W3C XML Schema 1.0 Part 2, Appendix E, <i>Adding durations to dateTimes</i></a>.
 *   </li>
 * </ul>
 * </p>
 *
 * @author <a href="mailto:Joseph.Fialli@Sun.com">Joseph Fialli</a>
 * @author <a href="mailto:Kohsuke.Kawaguchi@Sun.com">Kohsuke Kawaguchi</a>
 * @author <a href="mailto:Jeff.Suttor@Sun.com">Jeff Suttor</a>
 * @author <a href="mailto:Sunitha.Reddy@Sun.com">Sunitha Reddy</a>
 * @see Duration
 * @see DatatypeFactory
 * @since 1.5
 */

public abstract class XMLGregorianCalendar
        implements Cloneable {

        /**
         * Default no-arg constructor.
         *
         * <p>Note: Always use the {@link DatatypeFactory} to
         * construct an instance of <code>XMLGregorianCalendar</code>.
         * The constructor on this class cannot be guaranteed to
         * produce an object with a consistent state and may be
         * removed in the future.</p>
         */
         public XMLGregorianCalendar() {
         }

        /**
         * <p>Unset all fields to undefined.</p>
         *
         * <p>Set all int fields to {@link DatatypeConstants#FIELD_UNDEFINED} and reference fields
         * to null.</p>
         */
        public abstract void clear();

        /**
         * <p>Reset this <code>XMLGregorianCalendar</code> to its original values.</p>
         *
         * <p><code>XMLGregorianCalendar</code> is reset to the same values as when it was created with
         * {@link DatatypeFactory#newXMLGregorianCalendar()},
         * {@link DatatypeFactory#newXMLGregorianCalendar(String lexicalRepresentation)},
         * {@link DatatypeFactory#newXMLGregorianCalendar(
         *   BigInteger year,
         *   int month,
         *   int day,
         *   int hour,
         *   int minute,
         *   int second,
         *   BigDecimal fractionalSecond,
         *   int timezone)},
         * {@link DatatypeFactory#newXMLGregorianCalendar(
         *   int year,
         *   int month,
         *   int day,
         *   int hour,
         *   int minute,
         *   int second,
         *   int millisecond,
         *   int timezone)},
         * {@link DatatypeFactory#newXMLGregorianCalendar(GregorianCalendar cal)},
         * {@link DatatypeFactory#newXMLGregorianCalendarDate(
         *   int year,
         *   int month,
         *   int day,
         *   int timezone)},
         * {@link DatatypeFactory#newXMLGregorianCalendarTime(
         *   int hours,
         *   int minutes,
         *   int seconds,
         *   int timezone)},
         * {@link DatatypeFactory#newXMLGregorianCalendarTime(
         *   int hours,
         *   int minutes,
         *   int seconds,
         *   BigDecimal fractionalSecond,
         *   int timezone)} or
         * {@link DatatypeFactory#newXMLGregorianCalendarTime(
         *   int hours,
         *   int minutes,
         *   int seconds,
         *   int milliseconds,
         *   int timezone)}.
         * </p>
         *
         * <p><code>reset()</code> is designed to allow the reuse of existing <code>XMLGregorianCalendar</code>s
         * thus saving resources associated with the creation of new <code>XMLGregorianCalendar</code>s.</p>
         */
        public abstract void reset();

    /**
     * <p>Set low and high order component of XSD <code>dateTime</code> year field.</p>
     *
     * <p>Unset this field by invoking the setter with a parameter value of <code>null</code>.</p>
     *
     * @param year value constraints summarized in <a href="#datetimefield-year">year field of date/time field mapping table</a>.
     *
     * @throws IllegalArgumentException if <code>year</code> parameter is
     * outside value constraints for the field as specified in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.
     */
    public abstract void setYear(BigInteger year);

    /**
     * <p>Set year of XSD <code>dateTime</code> year field.</p>
     *
     * <p>Unset this field by invoking the setter with a parameter value of
     * {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
     *
     * <p>Note: if the absolute value of the <code>year</code> parameter
     * is less than 10^9, the eon component of the XSD year field is set to
     * <code>null</code> by this method.</p>
     *
     * @param year value constraints are summarized in <a href="#datetimefield-year">year field of date/time field mapping table</a>.
     *   If year is {@link DatatypeConstants#FIELD_UNDEFINED}, then eon is set to <code>null</code>.
     */
    public abstract void setYear(int year);

    /**
     * <p>Set month.</p>
     *
     * <p>Unset this field by invoking the setter with a parameter value of {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
     *
     * @param month value constraints summarized in <a href="#datetimefield-month">month field of date/time field mapping table</a>.
     *
     * @throws IllegalArgumentException if <code>month</code> parameter is
     * outside value constraints for the field as specified in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.
     */
    public abstract void setMonth(int month);

    /**
     * <p>Set days in month.</p>
     *
     * <p>Unset this field by invoking the setter with a parameter value of {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
     *
     * @param day value constraints summarized in <a href="#datetimefield-day">day field of date/time field mapping table</a>.
     *
     * @throws IllegalArgumentException if <code>day</code> parameter is
     * outside value constraints for the field as specified in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.
     */
    public abstract void setDay(int day);

    /**
     * <p>Set the number of minutes in the timezone offset.</p>
     *
     * <p>Unset this field by invoking the setter with a parameter value of {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
     *
     * @param offset value constraints summarized in <a href="#datetimefield-timezone">
     *   timezone field of date/time field mapping table</a>.
     *
     * @throws IllegalArgumentException if <code>offset</code> parameter is
     * outside value constraints for the field as specified in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.
     */
    public abstract void setTimezone(int offset);

    /**
     * <p>Set time as one unit.</p>
     *
     * @param hour value constraints are summarized in
     * <a href="#datetimefield-hour">hour field of date/time field mapping table</a>.
     * @param minute value constraints are summarized in
     * <a href="#datetimefield-minute">minute field of date/time field mapping table</a>.
     * @param second value constraints are summarized in
     * <a href="#datetimefield-second">second field of date/time field mapping table</a>.
     *
     * @see #setTime(int, int, int, BigDecimal)
     *
     * @throws IllegalArgumentException if any parameter is
     * outside value constraints for the field as specified in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.
     */
    public void setTime(int hour, int minute, int second) {

                setTime(
                        hour,
                        minute,
                        second,
                        null // fractional
                );
    }

        /**
         * <p>Set hours.</p>
         *
         * <p>Unset this field by invoking the setter with a parameter value of {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * @param hour value constraints summarized in <a href="#datetimefield-hour">hour field of date/time field mapping table</a>.
         *
         * @throws IllegalArgumentException if <code>hour</code> parameter is outside value constraints for the field as specified in
         *   <a href="#datetimefieldmapping">date/time field mapping table</a>.
        */
        public abstract void setHour(int hour);

        /**
         * <p>Set minutes.</p>
         *
         * <p>Unset this field by invoking the setter with a parameter value of {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * @param minute value constraints summarized in <a href="#datetimefield-minute">minute field of date/time field mapping table</a>.
         *
         * @throws IllegalArgumentException if <code>minute</code> parameter is outside value constraints for the field as specified in
         *   <a href="#datetimefieldmapping">date/time field mapping table</a>.
        */
        public abstract void setMinute(int minute);

        /**
         * <p>Set seconds.</p>
         *
         * <p>Unset this field by invoking the setter with a parameter value of {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * @param second value constraints summarized in <a href="#datetimefield-second">second field of date/time field mapping table</a>.
         *
         * @throws IllegalArgumentException if <code>second</code> parameter is outside value constraints for the field as specified in
         *   <a href="#datetimefieldmapping">date/time field mapping table</a>.
        */
        public abstract void setSecond(int second);

        /**
         * <p>Set milliseconds.</p>
         *
         * <p>Unset this field by invoking the setter with a parameter value of {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * @param millisecond value constraints summarized in
         *   <a href="#datetimefield-second">second field of date/time field mapping table</a>.
         *
         * @throws IllegalArgumentException if <code>millisecond</code> parameter is outside value constraints for the field as specified
         *   in <a href="#datetimefieldmapping">date/time field mapping table</a>.
        */
        public abstract void setMillisecond(int millisecond);

        /**
         * <p>Set fractional seconds.</p>
         *
         * <p>Unset this field by invoking the setter with a parameter value of <code>null</code>.</p>
         *
         * @param fractional value constraints summarized in
         *   <a href="#datetimefield-second">second field of date/time field mapping table</a>.
         *
         * @throws IllegalArgumentException if <code>fractional</code> parameter is outside value constraints for the field as specified
         *   in <a href="#datetimefieldmapping">date/time field mapping table</a>.
        */
        public abstract void setFractionalSecond(BigDecimal fractional);


    /**
     * <p>Set time as one unit, including the optional infinite precision
     * fractional seconds.</p>
     *
     * @param hour value constraints are summarized in
     * <a href="#datetimefield-hour">hour field of date/time field mapping table</a>.
     * @param minute value constraints are summarized in
     * <a href="#datetimefield-minute">minute field of date/time field mapping table</a>.
     * @param second value constraints are summarized in
     * <a href="#datetimefield-second">second field of date/time field mapping table</a>.
     * @param fractional value of <code>null</code> indicates this optional
     *   field is not set.
     *
     * @throws IllegalArgumentException if any parameter is
     * outside value constraints for the field as specified in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.
     */
    public void setTime(
        int hour,
        int minute,
        int second,
        BigDecimal fractional) {

                setHour(hour);
        setMinute(minute);
        setSecond(second);
        setFractionalSecond(fractional);
    }


    /**
     * <p>Set time as one unit, including optional milliseconds.</p>
     *
     * @param hour value constraints are summarized in
     * <a href="#datetimefield-hour">hour field of date/time field mapping table</a>.
     * @param minute value constraints are summarized in
     * <a href="#datetimefield-minute">minute field of date/time field mapping table</a>.
     * @param second value constraints are summarized in
     * <a href="#datetimefield-second">second field of date/time field mapping table</a>.
     * @param millisecond value of {@link DatatypeConstants#FIELD_UNDEFINED} indicates this
     *                    optional field is not set.
     *
     * @throws IllegalArgumentException if any parameter is
     * outside value constraints for the field as specified in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.
     */
    public void setTime(int hour, int minute, int second, int millisecond) {

        setHour(hour);
        setMinute(minute);
        setSecond(second);
        setMillisecond(millisecond);
    }

        /**
         * <p>Return high order component for XML Schema 1.0 dateTime datatype field for
         * <code>year</code>.
         * <code>null</code> if this optional part of the year field is not defined.</p>
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-year">year field of date/time field mapping table</a>.</p>
         * @return eon of this <code>XMLGregorianCalendar</code>. The value
         * returned is an integer multiple of 10^9.
         *
         * @see #getYear()
         * @see #getEonAndYear()
         */
        public abstract BigInteger getEon();

        /**
         * <p>Return low order component for XML Schema 1.0 dateTime datatype field for
         * <code>year</code> or {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-year">year field of date/time field mapping table</a>.</p>
         *
         * @return year  of this <code>XMLGregorianCalendar</code>.
         *
         * @see #getEon()
         * @see #getEonAndYear()
         */
        public abstract int getYear();

        /**
         * <p>Return XML Schema 1.0 dateTime datatype field for
         * <code>year</code>.</p>
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-year">year field of date/time field mapping table</a>.</p>
         *
         * @return sum of <code>eon</code> and <code>BigInteger.valueOf(year)</code>
         * when both fields are defined. When only <code>year</code> is defined,
         * return it. When both <code>eon</code> and <code>year</code> are not
         * defined, return <code>null</code>.
         *
         * @see #getEon()
         * @see #getYear()
         */
        public abstract BigInteger getEonAndYear();

        /**
         * <p>Return number of month or {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-month">month field of date/time field mapping table</a>.</p>
         *
         * @return year  of this <code>XMLGregorianCalendar</code>.
         *
         */
        public abstract int getMonth();

        /**
         * Return day in month or {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-day">day field of date/time field mapping table</a>.</p>
         *
         * @see #setDay(int)
         */
        public abstract int getDay();

        /**
         * Return timezone offset in minutes or
         * {@link DatatypeConstants#FIELD_UNDEFINED} if this optional field is not defined.
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-timezone">timezone field of date/time field mapping table</a>.</p>
         *
         * @see #setTimezone(int)
         */
        public abstract int getTimezone();

        /**
         * Return hours or {@link DatatypeConstants#FIELD_UNDEFINED}.
         * Returns {@link DatatypeConstants#FIELD_UNDEFINED} if this field is not defined.
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-hour">hour field of date/time field mapping table</a>.</p>
         * @see #setTime(int, int, int)
         */
        public abstract int getHour();

        /**
         * Return minutes or {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         * Returns {@link DatatypeConstants#FIELD_UNDEFINED} if this field is not defined.
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-minute">minute field of date/time field mapping table</a>.</p>
         * @see #setTime(int, int, int)
         */
        public abstract int getMinute();

        /**
         * <p>Return seconds or {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * <p>Returns {@link DatatypeConstants#FIELD_UNDEFINED} if this field is not defined.
         * When this field is not defined, the optional xs:dateTime
         * fractional seconds field, represented by
         * {@link #getFractionalSecond()} and {@link #getMillisecond()},
         * must not be defined.</p>
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-second">second field of date/time field mapping table</a>.</p>
         *
         * @return Second  of this <code>XMLGregorianCalendar</code>.
         *
         * @see #getFractionalSecond()
         * @see #getMillisecond()
         * @see #setTime(int, int, int)
         */
        public abstract int getSecond();

        /**
         * <p>Return millisecond precision of {@link #getFractionalSecond()}.</p>
         *
         * <p>This method represents a convenience accessor to infinite
         * precision fractional second value returned by
         * {@link #getFractionalSecond()}. The returned value is the rounded
         * down to milliseconds value of
         * {@link #getFractionalSecond()}. When {@link #getFractionalSecond()}
         * returns <code>null</code>, this method must return
         * {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * <p>Value constraints for this value are summarized in
         * <a href="#datetimefield-second">second field of date/time field mapping table</a>.</p>
         *
         * @return Millisecond  of this <code>XMLGregorianCalendar</code>.
         *
         * @see #getFractionalSecond()
         * @see #setTime(int, int, int)
         */
        public int getMillisecond() {

                BigDecimal fractionalSeconds = getFractionalSecond();

                // is field undefined?
                if (fractionalSeconds == null) {
                        return DatatypeConstants.FIELD_UNDEFINED;
                }

                return getFractionalSecond().movePointRight(3).intValue();
        }

        /**
         * <p>Return fractional seconds.</p>
         *
         * <p><code>null</code> is returned when this optional field is not defined.</p>
         *
         * <p>Value constraints are detailed in
         * <a href="#datetimefield-second">second field of date/time field mapping table</a>.</p>
         *
         * <p>This optional field can only have a defined value when the
         * xs:dateTime second field, represented by {@link #getSecond()},
         * does not return {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
         *
         * @return fractional seconds  of this <code>XMLGregorianCalendar</code>.
         *
         * @see #getSecond()
         * @see #setTime(int, int, int, BigDecimal)
         */
        public abstract BigDecimal getFractionalSecond();

    // comparisons
    /**
     * <p>Compare two instances of W3C XML Schema 1.0 date/time datatypes
     * according to partial order relation defined in
     * <a href="http://www.w3.org/TR/xmlschema-2/#dateTime-order">W3C XML Schema 1.0 Part 2, Section 3.2.7.3,
     * <i>Order relation on dateTime</i></a>.</p>
     *
     * <p><code>xsd:dateTime</code> datatype field mapping to accessors of
     * this class are defined in
     * <a href="#datetimefieldmapping">date/time field mapping table</a>.</p>
     *
     * @param xmlGregorianCalendar Instance of <code>XMLGregorianCalendar</code> to compare
     *
     * @return The relationship between <code>this</code> <code>XMLGregorianCalendar</code> and
     *   the specified <code>xmlGregorianCalendar</code> as
     *   {@link DatatypeConstants#LESSER},
     *   {@link DatatypeConstants#EQUAL},
     *   {@link DatatypeConstants#GREATER} or
     *   {@link DatatypeConstants#INDETERMINATE}.
     *
     * @throws NullPointerException if <code>xmlGregorianCalendar</code> is null.
     */
    public abstract int compare(XMLGregorianCalendar xmlGregorianCalendar);

    /**
     * <p>Normalize this instance to UTC.</p>
     *
     * <p>2000-03-04T23:00:00+03:00 normalizes to 2000-03-04T20:00:00Z</p>
     * <p>Implements W3C XML Schema Part 2, Section 3.2.7.3 (A).</p>
     *
     * @return <code>this</code> <code>XMLGregorianCalendar</code> normalized to UTC.
     */
    public abstract XMLGregorianCalendar normalize();

    /**
     * <p>Compares this calendar to the specified object. The result is
     * <code>true</code> if and only if the argument is not null and is an
     * <code>XMLGregorianCalendar</code> object that represents the same
     * instant in time as this object.</p>
     *
     * @param obj to compare.
     *
     * @return <code>true</code> when <code>obj</code> is an instance of
     * <code>XMLGregorianCalendar</code> and
     * {@link #compare(XMLGregorianCalendar obj)}
     * returns {@link DatatypeConstants#EQUAL},
     * otherwise <code>false</code>.
     */
    public boolean equals(Object obj) {

        if (obj == null || !(obj instanceof XMLGregorianCalendar)) {
               return false;
        }
        return compare((XMLGregorianCalendar) obj) == DatatypeConstants.EQUAL;
    }

    /**
     * <p>Returns a hash code consistent with the definition of the equals method.</p>
     *
     * @return hash code of this object.
     */
    public int hashCode() {

        // Following two dates compare to EQUALS since in different timezones.
        // 2000-01-15T12:00:00-05:00 == 2000-01-15T13:00:00-04:00
        //
        // Must ensure both instances generate same hashcode by normalizing
        // this to UTC timezone.
        int timezone = getTimezone();
        if (timezone == DatatypeConstants.FIELD_UNDEFINED) {
            timezone = 0;
        }
        XMLGregorianCalendar gc = this;
        if (timezone != 0) {
            gc = this.normalize();
        }
        return gc.getYear()
                + gc.getMonth()
                + gc.getDay()
                + gc.getHour()
                + gc.getMinute()
                + gc.getSecond();
    }

    /**
     * <p>Return the lexical representation of <code>this</code> instance.
     * The format is specified in
     * <a href="http://www.w3.org/TR/xmlschema-2/#dateTime-order">XML Schema 1.0 Part 2, Section 3.2.[7-14].1,
     * <i>Lexical Representation</i>".</a></p>
     *
     * <p>Specific target lexical representation format is determined by
     * {@link #getXMLSchemaType()}.</p>
     *
     * @return XML, as <code>String</code>, representation of this <code>XMLGregorianCalendar</code>
     *
     * @throws IllegalStateException if the combination of set fields
     *    does not match one of the eight defined XML Schema builtin date/time datatypes.
     */
    public abstract String toXMLFormat();

    /**
     * <p>Return the name of the XML Schema date/time type that this instance
     * maps to. Type is computed based on fields that are set.</p>
     *
     * <table border="2" rules="all" cellpadding="2">
     *   <thead>
     *     <tr>
     *       <th align="center" colspan="7">
     *         Required fields for XML Schema 1.0 Date/Time Datatypes.<br/>
     *         <i>(timezone is optional for all date/time datatypes)</i>
     *       </th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td>Datatype</td>
     *       <td>year</td>
     *       <td>month</td>
     *       <td>day</td>
     *       <td>hour</td>
     *       <td>minute</td>
     *       <td>second</td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#DATETIME}</td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td>X</td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#DATE}</td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#TIME}</td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td>X</td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#GYEARMONTH}</td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#GMONTHDAY}</td>
     *       <td></td>
     *       <td>X</td>
     *       <td>X</td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#GYEAR}</td>
     *       <td>X</td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#GMONTH}</td>
     *       <td></td>
     *       <td>X</td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *     </tr>
     *     <tr>
     *       <td>{@link DatatypeConstants#GDAY}</td>
     *       <td></td>
     *       <td></td>
     *       <td>X</td>
     *       <td></td>
     *       <td></td>
     *       <td></td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * @throws java.lang.IllegalStateException if the combination of set fields
     *    does not match one of the eight defined XML Schema builtin
     *    date/time datatypes.
     * @return One of the following class constants:
     *   {@link DatatypeConstants#DATETIME},
     *   {@link DatatypeConstants#TIME},
     *   {@link DatatypeConstants#DATE},
     *   {@link DatatypeConstants#GYEARMONTH},
     *   {@link DatatypeConstants#GMONTHDAY},
     *   {@link DatatypeConstants#GYEAR},
     *   {@link DatatypeConstants#GMONTH} or
     *   {@link DatatypeConstants#GDAY}.
     */
    public abstract QName getXMLSchemaType();

        /**
         * <p>Returns a <code>String</code> representation of this <code>XMLGregorianCalendar</code> <code>Object</code>.</p>
         *
         * <p>The result is a lexical representation generated by {@link #toXMLFormat()}.</p>
         *
         * @return A non-<code>null</code> valid <code>String</code> representation of this <code>XMLGregorianCalendar</code>.
         *
     * @throws IllegalStateException if the combination of set fields
     *    does not match one of the eight defined XML Schema builtin date/time datatypes.
     *
     * @see #toXMLFormat()
         */
    public String toString() {

        return toXMLFormat();
    }

    /**
     * Validate instance by <code>getXMLSchemaType()</code> constraints.
     * @return true if data values are valid.
     */
    public abstract boolean isValid();

    /**
     * <p>Add <code>duration</code> to this instance.</p>
     *
     * <p>The computation is specified in
     * <a href="http://www.w3.org/TR/xmlschema-2/#adding-durations-to-dateTimes">XML Schema 1.0 Part 2, Appendix E,
     * <i>Adding durations to dateTimes</i>></a>.
     * <a href="#datetimefieldmapping">date/time field mapping table</a>
     * defines the mapping from XML Schema 1.0 <code>dateTime</code> fields
     * to this class' representation of those fields.</p>
     *
     * @param duration Duration to add to this <code>XMLGregorianCalendar</code>.
     *
     * @throws NullPointerException  when <code>duration</code> parameter is <code>null</code>.
     */
    public abstract void add(Duration duration);

    /**
     * <p>Convert this <code>XMLGregorianCalendar</code> to a {@link GregorianCalendar}.</p>
     *
     * <p>When <code>this</code> instance has an undefined field, this
     * conversion relies on the <code>java.util.GregorianCalendar</code> default
     * for its corresponding field. A notable difference between
     * XML Schema 1.0 date/time datatypes and <code>java.util.GregorianCalendar</code>
     * is that Timezone value is optional for date/time datatypes and it is
     * a required field for <code>java.util.GregorianCalendar</code>. See javadoc
     * for <code>java.util.TimeZone.getDefault()</code> on how the default
     * is determined. To explicitly specify the <code>TimeZone</code>
     * instance, see
     * {@link #toGregorianCalendar(TimeZone, Locale, XMLGregorianCalendar)}.</p>
     *
     * <table border="2" rules="all" cellpadding="2">
     *   <thead>
     *     <tr>
     *       <th align="center" colspan="2">
     *          Field by Field Conversion from this class to
     *          <code>java.util.GregorianCalendar</code>
     *       </th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *        <td><code>java.util.GregorianCalendar</code> field</td>
     *        <td><code>javax.xml.datatype.XMLGregorianCalendar</code> field</td>
     *     </tr>
     *     <tr>
     *       <td><code>ERA</code></td>
     *       <td>{@link #getEonAndYear()}<code>.signum() < 0 ? GregorianCalendar.BC : GregorianCalendar.AD</code></td>
     *     </tr>
     *     <tr>
     *       <td><code>YEAR</code></td>
     *       <td>{@link #getEonAndYear()}<code>.abs().intValue()</code><i>*</i></td>
     *     </tr>
     *     <tr>
     *       <td><code>MONTH</code></td>
     *       <td>{@link #getMonth()} - {@link DatatypeConstants#JANUARY} + {@link GregorianCalendar#JANUARY}</td>
     *     </tr>
     *     <tr>
     *       <td><code>DAY_OF_MONTH</code></td>
     *       <td>{@link #getDay()}</td>
     *     </tr>
     *     <tr>
     *       <td><code>HOUR_OF_DAY</code></td>
     *       <td>{@link #getHour()}</td>
     *     </tr>
     *     <tr>
     *       <td><code>MINUTE</code></td>
     *       <td>{@link #getMinute()}</td>
     *     </tr>
     *     <tr>
     *       <td><code>SECOND</code></td>
     *       <td>{@link #getSecond()}</td>
     *     </tr>
     *     <tr>
     *       <td><code>MILLISECOND</code></td>
     *       <td>get millisecond order from {@link #getFractionalSecond()}<i>*</i> </td>
     *     </tr>
     *     <tr>
     *       <td><code>GregorianCalendar.setTimeZone(TimeZone)</code></td>
     *       <td>{@link #getTimezone()} formatted into Custom timezone id</td>
     *     </tr>
     *   </tbody>
     * </table>
     * <i>*</i> designates possible loss of precision during the conversion due
     * to source datatype having higher precision than target datatype.
     *
     * <p>To ensure consistency in conversion implementations, the new
     * <code>GregorianCalendar</code> should be instantiated in following
     * manner.
     * <ul>
     *   <li>Using <code>timeZone</code> value as defined above, create a new
     * <code>java.util.GregorianCalendar(timeZone,Locale.getDefault())</code>.
     *   </li>
     *   <li>Initialize all GregorianCalendar fields by calling {@link java.util.GregorianCalendar#clear()}.</li>
     *   <li>Obtain a pure Gregorian Calendar by invoking
     *   <code>GregorianCalendar.setGregorianChange(
     *   new Date(Long.MIN_VALUE))</code>.</li>
     *   <li>Its fields ERA, YEAR, MONTH, DAY_OF_MONTH, HOUR_OF_DAY,
     *       MINUTE, SECOND and MILLISECOND are set using the method
     *       <code>Calendar.set(int,int)</code></li>
     * </ul>
     * </p>
     *
     * @see #toGregorianCalendar(java.util.TimeZone, java.util.Locale, XMLGregorianCalendar)
     */
    public abstract GregorianCalendar toGregorianCalendar();

    /**
     * <p>Convert this <code>XMLGregorianCalendar</code> along with provided parameters
     * to a {@link GregorianCalendar} instance.</p>
     *
     * <p> Since XML Schema 1.0 date/time datetypes has no concept of
     * timezone ids or daylight savings timezone ids, this conversion operation
     * allows the user to explicitly specify one with
     * <code>timezone</code> parameter.</p>
     *
     * <p>To compute the return value's <code>TimeZone</code> field,
     * <ul>
     * <li>when parameter <code>timeZone</code> is non-null,
     * it is the timezone field.</li>
     * <li>else when <code>this.getTimezone() != FIELD_UNDEFINED</code>,
     * create a <code>java.util.TimeZone</code> with a custom timezone id
     * using the <code>this.getTimezone()</code>.</li>
     * <li>else when <code>defaults.getTimezone() != FIELD_UNDEFINED</code>,
     * create a <code>java.util.TimeZone</code> with a custom timezone id
     * using <code>defaults.getTimezone()</code>.</li>
     * <li>else use the <code>GregorianCalendar</code> default timezone value
     * for the host is defined as specified by
     * <code>java.util.TimeZone.getDefault()</code>.</li></p>
     *
     * <p>To ensure consistency in conversion implementations, the new
     * <code>GregorianCalendar</code> should be instantiated in following
     * manner.
     * <ul>
     *   <li>Create a new <code>java.util.GregorianCalendar(TimeZone,
     *       Locale)</code> with TimeZone set as specified above and the
     *       <code>Locale</code> parameter.
     *   </li>
     *   <li>Initialize all GregorianCalendar fields by calling {@link GregorianCalendar#clear()}</li>
     *   <li>Obtain a pure Gregorian Calendar by invoking
     *   <code>GregorianCalendar.setGregorianChange(
     *   new Date(Long.MIN_VALUE))</code>.</li>
     *   <li>Its fields ERA, YEAR, MONTH, DAY_OF_MONTH, HOUR_OF_DAY,
     *       MINUTE, SECOND and MILLISECOND are set using the method
     *       <code>Calendar.set(int,int)</code></li>
     * </ul>
     *
     * @param timezone provide Timezone. <code>null</code> is a legal value.
     * @param aLocale  provide explicit Locale. Use default GregorianCalendar locale if
     *                 value is <code>null</code>.
     * @param defaults provide default field values to use when corresponding
     *                 field for this instance is FIELD_UNDEFINED or null.
     *                 If <code>defaults</code>is <code>null</code> or a field
     *                 within the specified <code>defaults</code> is undefined,
     *                 just use <code>java.util.GregorianCalendar</code> defaults.
     * @return a java.util.GregorianCalendar conversion of this instance.
     */
    public abstract GregorianCalendar toGregorianCalendar(
        java.util.TimeZone timezone,
                java.util.Locale aLocale,
                XMLGregorianCalendar defaults);

    /**
     * <p>Returns a <code>java.util.TimeZone</code> for this class.</p>
     *
     * <p>If timezone field is defined for this instance,
     * returns TimeZone initialized with custom timezone id
     * of zoneoffset. If timezone field is undefined,
     * try the defaultZoneoffset that was passed in.
     * If defaultZoneoffset is FIELD_UNDEFINED, return
     * default timezone for this host.
     * (Same default as java.util.GregorianCalendar).</p>
     *
     * @param defaultZoneoffset default zoneoffset if this zoneoffset is
     * {@link DatatypeConstants#FIELD_UNDEFINED}.
     *
     * @return TimeZone for this.
     */
    public abstract TimeZone getTimeZone(int defaultZoneoffset);



    /**
     * <p>Creates and returns a copy of this object.</p>
     *
     * @return copy of this <code>Object</code>
     */
   public abstract Object clone();
}
