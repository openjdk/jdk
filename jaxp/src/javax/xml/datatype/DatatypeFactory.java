/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Factory that creates new <code>javax.xml.datatype</code> <code>Object</code>s that map XML to/from Java <code>Object</code>s.</p>
 *
 * <p>A new instance of the <code>DatatypeFactory</code> is created through the {@link #newInstance()} method
 * that uses the following implementation resolution mechanisms to determine an implementation:</p>
 * <ol>
 *    <li>
 *      If the system property specified by {@link #DATATYPEFACTORY_PROPERTY}, "<code>javax.xml.datatype.DatatypeFactory</code>",
 *      exists, a class with the name of the property value is instantiated.
 *      Any Exception thrown during the instantiation process is wrapped as a {@link DatatypeConfigurationException}.
 *    </li>
 *    <li>
 *      If the file ${JAVA_HOME}/lib/jaxp.properties exists, it is loaded in a {@link java.util.Properties} <code>Object</code>.
 *      The <code>Properties</code> <code>Object </code> is then queried for the property as documented in the prior step
 *      and processed as documented in the prior step.
 *    </li>
 *    <li>
 *     Uses the service-provider loading facilities, defined by the {@link java.util.ServiceLoader} class, to attempt
 *     to locate and load an implementation of the service using the {@linkplain
 *     java.util.ServiceLoader#load(java.lang.Class) default loading mechanism}:
 *     the service-provider loading facility will use the {@linkplain
 *     java.lang.Thread#getContextClassLoader() current thread's context class loader}
 *     to attempt to load the service. If the context class
 *     loader is null, the {@linkplain
 *     ClassLoader#getSystemClassLoader() system class loader} will be used.
 *     <br>
 *     In case of {@link java.util.ServiceConfigurationError service
 *     configuration error} a {@link javax.xml.datatype.DatatypeConfigurationException}
 *     will be thrown.
 *    </li>
 *    <li>
 *      The final mechanism is to attempt to instantiate the <code>Class</code> specified by
 *      {@link #DATATYPEFACTORY_IMPLEMENTATION_CLASS}.
 *      Any Exception thrown during the instantiation process is wrapped as a {@link DatatypeConfigurationException}.
 *    </li>
 * </ol>
 *
 * @author <a href="mailto:Joseph.Fialli@Sun.COM">Joseph Fialli</a>
 * @author <a href="mailto:Jeff.Suttor@Sun.com">Jeff Suttor</a>
 * @author <a href="mailto:Neeraj.Bajaj@sun.com">Neeraj Bajaj</a>
 *
 * @version $Revision: 1.13 $, $Date: 2010/03/11 23:10:53 $
 * @since 1.5
 */
public abstract class DatatypeFactory {

    /**
     * <p>Default property name as defined in JSR 206: Java(TM) API for XML Processing (JAXP) 1.3.</p>
     *
     * <p>Default value is <code>javax.xml.datatype.DatatypeFactory</code>.</p>
     */
    public static final String DATATYPEFACTORY_PROPERTY =
            // We use a String constant here, rather than calling
            // DatatypeFactory.class.getName() - in order to make javadoc
            // generate a See Also: Constant Field Value link.
            "javax.xml.datatype.DatatypeFactory";

    /**
     * <p>Default implementation class name as defined in
     * <em>JSR 206: Java(TM) API for XML Processing (JAXP) 1.3</em>.</p>
     *
     * <p>Implementers should specify the name of an appropriate class
     * to be instantiated if no other implementation resolution mechanism
     * succeeds.</p>
     *
     * <p>Users should not refer to this field; it is intended only to
     * document a factory implementation detail.
     * </p>
     */
    public static final String DATATYPEFACTORY_IMPLEMENTATION_CLASS =
        // We use new String() here to prevent javadoc from generating
        // a See Also: Constant Field Value link.
        new String("com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl");

    /**
     * http://www.w3.org/TR/xpath-datamodel/#xdtschema defines two regexps
     * to constrain the value space of dayTimeDuration ([^YM]*[DT].*)
     * and yearMonthDuration ([^DT]*). Note that these expressions rely on
     * the fact that the value must be an xs:Duration, they simply exclude
     * some Durations.
     */
    private static final Pattern XDTSCHEMA_YMD =
        Pattern.compile("[^DT]*");

    private static final Pattern XDTSCHEMA_DTD =
        Pattern.compile("[^YM]*[DT].*");

    /**
     * <p>Protected constructor to prevent instaniation outside of package.</p>
     *
     * <p>Use {@link #newInstance()} to create a <code>DatatypeFactory</code>.</p>
     */
    protected DatatypeFactory() {
    }

    /**
     * <p>Obtain a new instance of a <code>DatatypeFactory</code>.</p>
     *
     * <p>The implementation resolution mechanisms are <a href="#DatatypeFactory.newInstance">defined</a> in this
     * <code>Class</code>'s documentation.</p>
     *
     * @return New instance of a <code>DatatypeFactory</code>
     *
     * @throws DatatypeConfigurationException If the implementation is not
     *   available or cannot be instantiated.
     *
     * @see #newInstance(String factoryClassName, ClassLoader classLoader)
     */
    public static DatatypeFactory newInstance()
            throws DatatypeConfigurationException {

            return FactoryFinder.find(
                    /* The default property name according to the JAXP spec */
                    DatatypeFactory.class,
                    /* The fallback implementation class name */
                    DATATYPEFACTORY_IMPLEMENTATION_CLASS);
    }

    /**
     * <p>Obtain a new instance of a <code>DatatypeFactory</code> from class name.
     * This function is useful when there are multiple providers in the classpath.
     * It gives more control to the application as it can specify which provider
     * should be loaded.</p>
     *
     * <p>Once an application has obtained a reference to a <code>DatatypeFactory</code>
     * it can use the factory to configure and obtain datatype instances.</P>
     *
     *
     * <h2>Tip for Trouble-shooting</h2>
     * <p>Setting the <code>jaxp.debug</code> system property will cause
     * this method to print a lot of debug messages
     * to <code>System.err</code> about what it is doing and where it is looking at.</p>
     *
     * <p> If you have problems try:</p>
     * <pre>
     * java -Djaxp.debug=1 YourProgram ....
     * </pre>
     *
     * @param factoryClassName fully qualified factory class name that provides implementation of <code>javax.xml.datatype.DatatypeFactory</code>.
     *
     * @param classLoader <code>ClassLoader</code> used to load the factory class. If <code>null</code>
     *                     current <code>Thread</code>'s context classLoader is used to load the factory class.
     *
     * @return New instance of a <code>DatatypeFactory</code>
     *
     * @throws DatatypeConfigurationException if <code>factoryClassName</code> is <code>null</code>, or
     *                                   the factory class cannot be loaded, instantiated.
     *
     * @see #newInstance()
     *
     * @since 1.6
     */
    public static DatatypeFactory newInstance(String factoryClassName, ClassLoader classLoader)
        throws DatatypeConfigurationException {
        return FactoryFinder.newInstance(DatatypeFactory.class,
                    factoryClassName, classLoader, false);
     }

    /**
     * <p>Obtain a new instance of a <code>Duration</code>
     * specifying the <code>Duration</code> as its string representation, "PnYnMnDTnHnMnS",
     * as defined in XML Schema 1.0 section 3.2.6.1.</p>
     *
     * <p>XML Schema Part 2: Datatypes, 3.2.6 duration, defines <code>duration</code> as:</p>
     * <blockquote>
     * duration represents a duration of time.
     * The value space of duration is a six-dimensional space where the coordinates designate the
     * Gregorian year, month, day, hour, minute, and second components defined in Section 5.5.3.2 of [ISO 8601], respectively.
     * These components are ordered in their significance by their order of appearance i.e. as
     * year, month, day, hour, minute, and second.
     * </blockquote>
     * <p>All six values are set and available from the created {@link Duration}</p>
     *
     * <p>The XML Schema specification states that values can be of an arbitrary size.
     * Implementations may chose not to or be incapable of supporting arbitrarily large and/or small values.
     * An {@link UnsupportedOperationException} will be thrown with a message indicating implementation limits
     * if implementation capacities are exceeded.</p>
     *
     * @param lexicalRepresentation <code>String</code> representation of a <code>Duration</code>.
     *
     * @return New <code>Duration</code> created from parsing the <code>lexicalRepresentation</code>.
     *
     * @throws IllegalArgumentException If <code>lexicalRepresentation</code> is not a valid representation of a <code>Duration</code>.
     * @throws UnsupportedOperationException If implementation cannot support requested values.
     * @throws NullPointerException if <code>lexicalRepresentation</code> is <code>null</code>.
     */
    public abstract Duration newDuration(final String lexicalRepresentation);

    /**
     * <p>Obtain a new instance of a <code>Duration</code>
     * specifying the <code>Duration</code> as milliseconds.</p>
     *
     * <p>XML Schema Part 2: Datatypes, 3.2.6 duration, defines <code>duration</code> as:</p>
     * <blockquote>
     * duration represents a duration of time.
     * The value space of duration is a six-dimensional space where the coordinates designate the
     * Gregorian year, month, day, hour, minute, and second components defined in Section 5.5.3.2 of [ISO 8601], respectively.
     * These components are ordered in their significance by their order of appearance i.e. as
     * year, month, day, hour, minute, and second.
     * </blockquote>
     * <p>All six values are set by computing their values from the specified milliseconds
     * and are available using the <code>get</code> methods of  the created {@link Duration}.
     * The values conform to and are defined by:</p>
     * <ul>
     *   <li>ISO 8601:2000(E) Section 5.5.3.2 Alternative format</li>
     *   <li><a href="http://www.w3.org/TR/xmlschema-2/#isoformats">
     *     W3C XML Schema 1.0 Part 2, Appendix D, ISO 8601 Date and Time Formats</a>
     *   </li>
     *   <li>{@link XMLGregorianCalendar}  Date/Time Datatype Field Mapping Between XML Schema 1.0 and Java Representation</li>
     * </ul>
     *
     * <p>The default start instance is defined by {@link GregorianCalendar}'s use of the start of the epoch: i.e.,
     * {@link java.util.Calendar#YEAR} = 1970,
     * {@link java.util.Calendar#MONTH} = {@link java.util.Calendar#JANUARY},
     * {@link java.util.Calendar#DATE} = 1, etc.
     * This is important as there are variations in the Gregorian Calendar,
     * e.g. leap years have different days in the month = {@link java.util.Calendar#FEBRUARY}
     * so the result of {@link Duration#getMonths()} and {@link Duration#getDays()} can be influenced.</p>
     *
     * @param durationInMilliSeconds Duration in milliseconds to create.
     *
     * @return New <code>Duration</code> representing <code>durationInMilliSeconds</code>.
     */
    public abstract Duration newDuration(final long durationInMilliSeconds);

    /**
     * <p>Obtain a new instance of a <code>Duration</code>
     * specifying the <code>Duration</code> as isPositive, years, months, days, hours, minutes, seconds.</p>
     *
     * <p>The XML Schema specification states that values can be of an arbitrary size.
     * Implementations may chose not to or be incapable of supporting arbitrarily large and/or small values.
     * An {@link UnsupportedOperationException} will be thrown with a message indicating implementation limits
     * if implementation capacities are exceeded.</p>
     *
     * <p>A <code>null</code> value indicates that field is not set.</p>
     *
     * @param isPositive Set to <code>false</code> to create a negative duration. When the length
     *   of the duration is zero, this parameter will be ignored.
     * @param years of this <code>Duration</code>
     * @param months of this <code>Duration</code>
     * @param days of this <code>Duration</code>
     * @param hours of this <code>Duration</code>
     * @param minutes of this <code>Duration</code>
     * @param seconds of this <code>Duration</code>
     *
     * @return New <code>Duration</code> created from the specified values.
     *
     * @throws IllegalArgumentException If the values are not a valid representation of a
     * <code>Duration</code>: if all the fields (years, months, ...) are null or
     * if any of the fields is negative.
     * @throws UnsupportedOperationException If implementation cannot support requested values.
     */
    public abstract Duration newDuration(
            final boolean isPositive,
            final BigInteger years,
            final BigInteger months,
            final BigInteger days,
            final BigInteger hours,
            final BigInteger minutes,
            final BigDecimal seconds);

    /**
     * <p>Obtain a new instance of a <code>Duration</code>
     * specifying the <code>Duration</code> as isPositive, years, months, days, hours, minutes, seconds.</p>
     *
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param isPositive Set to <code>false</code> to create a negative duration. When the length
     *   of the duration is zero, this parameter will be ignored.
     * @param years of this <code>Duration</code>
     * @param months of this <code>Duration</code>
     * @param days of this <code>Duration</code>
     * @param hours of this <code>Duration</code>
     * @param minutes of this <code>Duration</code>
     * @param seconds of this <code>Duration</code>
     *
     * @return New <code>Duration</code> created from the specified values.
     *
     * @throws IllegalArgumentException If the values are not a valid representation of a
     * <code>Duration</code>: if any of the fields is negative.
     *
     * @see #newDuration(
     *   boolean isPositive,
     *   BigInteger years,
     *   BigInteger months,
     *   BigInteger days,
     *   BigInteger hours,
     *   BigInteger minutes,
     *   BigDecimal seconds)
     */
    public Duration newDuration(
            final boolean isPositive,
            final int years,
            final int months,
            final int days,
            final int hours,
            final int minutes,
            final int seconds) {

            // years may not be set
            BigInteger realYears = (years != DatatypeConstants.FIELD_UNDEFINED) ? BigInteger.valueOf((long) years) : null;

            // months may not be set
            BigInteger realMonths = (months != DatatypeConstants.FIELD_UNDEFINED) ? BigInteger.valueOf((long) months) : null;

            // days may not be set
            BigInteger realDays = (days != DatatypeConstants.FIELD_UNDEFINED) ? BigInteger.valueOf((long) days) : null;

            // hours may not be set
            BigInteger realHours = (hours != DatatypeConstants.FIELD_UNDEFINED) ? BigInteger.valueOf((long) hours) : null;

            // minutes may not be set
            BigInteger realMinutes = (minutes != DatatypeConstants.FIELD_UNDEFINED) ? BigInteger.valueOf((long) minutes) : null;

            // seconds may not be set
            BigDecimal realSeconds = (seconds != DatatypeConstants.FIELD_UNDEFINED) ? BigDecimal.valueOf((long) seconds) : null;

                    return newDuration(
                            isPositive,
                            realYears,
                            realMonths,
                            realDays,
                            realHours,
                            realMinutes,
                            realSeconds
                    );
            }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:dayTimeDuration</code> by parsing its <code>String</code> representation,
     * "<em>PnDTnHnMnS</em>", <a href="http://www.w3.org/TR/xpath-datamodel#dayTimeDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:dayTimeDuration</a>.</p>
     *
     * <p>The datatype <code>xdt:dayTimeDuration</code> is a subtype of <code>xs:duration</code>
     * whose lexical representation contains only day, hour, minute, and second components.
     * This datatype resides in the namespace <code>http://www.w3.org/2003/11/xpath-datatypes</code>.</p>
     *
     * <p>All four values are set and available from the created {@link Duration}</p>
     *
     * <p>The XML Schema specification states that values can be of an arbitrary size.
     * Implementations may chose not to or be incapable of supporting arbitrarily large and/or small values.
     * An {@link UnsupportedOperationException} will be thrown with a message indicating implementation limits
     * if implementation capacities are exceeded.</p>
     *
     * @param lexicalRepresentation Lexical representation of a duration.
     *
     * @return New <code>Duration</code> created using the specified <code>lexicalRepresentation</code>.
     *
     * @throws IllegalArgumentException If <code>lexicalRepresentation</code> is not a valid representation of a <code>Duration</code> expressed only in terms of days and time.
     * @throws UnsupportedOperationException If implementation cannot support requested values.
     * @throws NullPointerException If <code>lexicalRepresentation</code> is <code>null</code>.
     */
    public Duration newDurationDayTime(final String lexicalRepresentation) {
        // lexicalRepresentation must be non-null
        if (lexicalRepresentation == null) {
            throw new NullPointerException(
                "Trying to create an xdt:dayTimeDuration with an invalid"
                + " lexical representation of \"null\"");
        }

        // test lexicalRepresentation against spec regex
        Matcher matcher = XDTSCHEMA_DTD.matcher(lexicalRepresentation);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Trying to create an xdt:dayTimeDuration with an invalid"
                + " lexical representation of \"" + lexicalRepresentation
                + "\", data model requires years and months only.");
        }

        return newDuration(lexicalRepresentation);
    }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:dayTimeDuration</code> using the specified milliseconds as defined in
     * <a href="http://www.w3.org/TR/xpath-datamodel#dayTimeDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:dayTimeDuration</a>.</p>
     *
     * <p>The datatype <code>xdt:dayTimeDuration</code> is a subtype of <code>xs:duration</code>
     * whose lexical representation contains only day, hour, minute, and second components.
     * This datatype resides in the namespace <code>http://www.w3.org/2003/11/xpath-datatypes</code>.</p>
     *
     * <p>All four values are set by computing their values from the specified milliseconds
     * and are available using the <code>get</code> methods of  the created {@link Duration}.
     * The values conform to and are defined by:</p>
     * <ul>
     *   <li>ISO 8601:2000(E) Section 5.5.3.2 Alternative format</li>
     *   <li><a href="http://www.w3.org/TR/xmlschema-2/#isoformats">
     *     W3C XML Schema 1.0 Part 2, Appendix D, ISO 8601 Date and Time Formats</a>
     *   </li>
     *   <li>{@link XMLGregorianCalendar}  Date/Time Datatype Field Mapping Between XML Schema 1.0 and Java Representation</li>
     * </ul>
     *
     * <p>The default start instance is defined by {@link GregorianCalendar}'s use of the start of the epoch: i.e.,
     * {@link java.util.Calendar#YEAR} = 1970,
     * {@link java.util.Calendar#MONTH} = {@link java.util.Calendar#JANUARY},
     * {@link java.util.Calendar#DATE} = 1, etc.
     * This is important as there are variations in the Gregorian Calendar,
     * e.g. leap years have different days in the month = {@link java.util.Calendar#FEBRUARY}
     * so the result of {@link Duration#getDays()} can be influenced.</p>
     *
     * <p>Any remaining milliseconds after determining the day, hour, minute and second are discarded.</p>
     *
     * @param durationInMilliseconds Milliseconds of <code>Duration</code> to create.
     *
     * @return New <code>Duration</code> created with the specified <code>durationInMilliseconds</code>.
     *
     * @see <a href="http://www.w3.org/TR/xpath-datamodel#dayTimeDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:dayTimeDuration</a>
     */
    public Duration newDurationDayTime(final long durationInMilliseconds) {

            return newDuration(durationInMilliseconds);
    }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:dayTimeDuration</code> using the specified
     * <code>day</code>, <code>hour</code>, <code>minute</code> and <code>second</code> as defined in
     * <a href="http://www.w3.org/TR/xpath-datamodel#dayTimeDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:dayTimeDuration</a>.</p>
     *
     * <p>The datatype <code>xdt:dayTimeDuration</code> is a subtype of <code>xs:duration</code>
     * whose lexical representation contains only day, hour, minute, and second components.
     * This datatype resides in the namespace <code>http://www.w3.org/2003/11/xpath-datatypes</code>.</p>
     *
     * <p>The XML Schema specification states that values can be of an arbitrary size.
     * Implementations may chose not to or be incapable of supporting arbitrarily large and/or small values.
     * An {@link UnsupportedOperationException} will be thrown with a message indicating implementation limits
     * if implementation capacities are exceeded.</p>
     *
     * <p>A <code>null</code> value indicates that field is not set.</p>
     *
     * @param isPositive Set to <code>false</code> to create a negative duration. When the length
     *   of the duration is zero, this parameter will be ignored.
     * @param day Day of <code>Duration</code>.
     * @param hour Hour of <code>Duration</code>.
     * @param minute Minute of <code>Duration</code>.
     * @param second Second of <code>Duration</code>.
     *
     * @return New <code>Duration</code> created with the specified <code>day</code>, <code>hour</code>, <code>minute</code>
     * and <code>second</code>.
     *
     * @throws IllegalArgumentException If the values are not a valid representation of a
     * <code>Duration</code>: if all the fields (day, hour, ...) are null or
     * if any of the fields is negative.
     * @throws UnsupportedOperationException If implementation cannot support requested values.
     */
    public Duration newDurationDayTime(
            final boolean isPositive,
            final BigInteger day,
            final BigInteger hour,
            final BigInteger minute,
            final BigInteger second) {

            return newDuration(
                    isPositive,
                    null,  // years
                    null, // months
                    day,
                    hour,
                    minute,
                    (second != null)? new BigDecimal(second):null
            );
    }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:dayTimeDuration</code> using the specified
     * <code>day</code>, <code>hour</code>, <code>minute</code> and <code>second</code> as defined in
     * <a href="http://www.w3.org/TR/xpath-datamodel#dayTimeDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:dayTimeDuration</a>.</p>
     *
     * <p>The datatype <code>xdt:dayTimeDuration</code> is a subtype of <code>xs:duration</code>
     * whose lexical representation contains only day, hour, minute, and second components.
     * This datatype resides in the namespace <code>http://www.w3.org/2003/11/xpath-datatypes</code>.</p>
     *
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param isPositive Set to <code>false</code> to create a negative duration. When the length
     *   of the duration is zero, this parameter will be ignored.
     * @param day Day of <code>Duration</code>.
     * @param hour Hour of <code>Duration</code>.
     * @param minute Minute of <code>Duration</code>.
     * @param second Second of <code>Duration</code>.
     *
     * @return New <code>Duration</code> created with the specified <code>day</code>, <code>hour</code>, <code>minute</code>
     * and <code>second</code>.
     *
     * @throws IllegalArgumentException If the values are not a valid representation of a
     * <code>Duration</code>: if any of the fields (day, hour, ...) is negative.
     */
    public Duration newDurationDayTime(
            final boolean isPositive,
            final int day,
            final int hour,
            final int minute,
            final int second) {

                    return newDurationDayTime(
                            isPositive,
                            BigInteger.valueOf((long) day),
                            BigInteger.valueOf((long) hour),
                            BigInteger.valueOf((long) minute),
                            BigInteger.valueOf((long) second)
                            );
            }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:yearMonthDuration</code> by parsing its <code>String</code> representation,
     * "<em>PnYnM</em>", <a href="http://www.w3.org/TR/xpath-datamodel#yearMonthDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:yearMonthDuration</a>.</p>
     *
     * <p>The datatype <code>xdt:yearMonthDuration</code> is a subtype of <code>xs:duration</code>
     * whose lexical representation contains only year and month components.
     * This datatype resides in the namespace {@link javax.xml.XMLConstants#W3C_XPATH_DATATYPE_NS_URI}.</p>
     *
     * <p>Both values are set and available from the created {@link Duration}</p>
     *
     * <p>The XML Schema specification states that values can be of an arbitrary size.
     * Implementations may chose not to or be incapable of supporting arbitrarily large and/or small values.
     * An {@link UnsupportedOperationException} will be thrown with a message indicating implementation limits
     * if implementation capacities are exceeded.</p>
     *
     * @param lexicalRepresentation Lexical representation of a duration.
     *
     * @return New <code>Duration</code> created using the specified <code>lexicalRepresentation</code>.
     *
     * @throws IllegalArgumentException If <code>lexicalRepresentation</code> is not a valid representation of a <code>Duration</code> expressed only in terms of years and months.
     * @throws UnsupportedOperationException If implementation cannot support requested values.
     * @throws NullPointerException If <code>lexicalRepresentation</code> is <code>null</code>.
     */
    public Duration newDurationYearMonth(
            final String lexicalRepresentation) {

        // lexicalRepresentation must be non-null
        if (lexicalRepresentation == null) {
            throw new NullPointerException(
                    "Trying to create an xdt:yearMonthDuration with an invalid"
                    + " lexical representation of \"null\"");
        }

        // test lexicalRepresentation against spec regex
        Matcher matcher = XDTSCHEMA_YMD.matcher(lexicalRepresentation);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Trying to create an xdt:yearMonthDuration with an invalid"
                    + " lexical representation of \"" + lexicalRepresentation
                    + "\", data model requires days and times only.");
        }

        return newDuration(lexicalRepresentation);
    }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:yearMonthDuration</code> using the specified milliseconds as defined in
     * <a href="http://www.w3.org/TR/xpath-datamodel#yearMonthDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:yearMonthDuration</a>.</p>
     *
     * <p>The datatype <code>xdt:yearMonthDuration</code> is a subtype of <code>xs:duration</code>
     * whose lexical representation contains only year and month components.
     * This datatype resides in the namespace {@link javax.xml.XMLConstants#W3C_XPATH_DATATYPE_NS_URI}.</p>
     *
     * <p>Both values are set by computing their values from the specified milliseconds
     * and are available using the <code>get</code> methods of  the created {@link Duration}.
     * The values conform to and are defined by:</p>
     * <ul>
     *   <li>ISO 8601:2000(E) Section 5.5.3.2 Alternative format</li>
     *   <li><a href="http://www.w3.org/TR/xmlschema-2/#isoformats">
     *     W3C XML Schema 1.0 Part 2, Appendix D, ISO 8601 Date and Time Formats</a>
     *   </li>
     *   <li>{@link XMLGregorianCalendar}  Date/Time Datatype Field Mapping Between XML Schema 1.0 and Java Representation</li>
     * </ul>
     *
     * <p>The default start instance is defined by {@link GregorianCalendar}'s use of the start of the epoch: i.e.,
     * {@link java.util.Calendar#YEAR} = 1970,
     * {@link java.util.Calendar#MONTH} = {@link java.util.Calendar#JANUARY},
     * {@link java.util.Calendar#DATE} = 1, etc.
     * This is important as there are variations in the Gregorian Calendar,
     * e.g. leap years have different days in the month = {@link java.util.Calendar#FEBRUARY}
     * so the result of {@link Duration#getMonths()} can be influenced.</p>
     *
     * <p>Any remaining milliseconds after determining the year and month are discarded.</p>
     *
     * @param durationInMilliseconds Milliseconds of <code>Duration</code> to create.
     *
     * @return New <code>Duration</code> created using the specified <code>durationInMilliseconds</code>.
     */
    public Duration newDurationYearMonth(
            final long durationInMilliseconds) {

        // create a Duration that only has sign, year & month
        // Duration is immutable, so need to create a new Duration
        // implementations may override this method in a more efficient way
        Duration fullDuration = newDuration(durationInMilliseconds);
        boolean isPositive = (fullDuration.getSign() == -1) ? false : true;
        BigInteger years =
            (BigInteger) fullDuration.getField(DatatypeConstants.YEARS);
        if (years == null) { years = BigInteger.ZERO; }
        BigInteger months =
            (BigInteger) fullDuration.getField(DatatypeConstants.MONTHS);
        if (months == null) { months = BigInteger.ZERO; }

        return newDurationYearMonth(isPositive, years, months);
    }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:yearMonthDuration</code> using the specified
     * <code>year</code> and <code>month</code> as defined in
     * <a href="http://www.w3.org/TR/xpath-datamodel#yearMonthDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:yearMonthDuration</a>.</p>
     *
     * <p>The XML Schema specification states that values can be of an arbitrary size.
     * Implementations may chose not to or be incapable of supporting arbitrarily large and/or small values.
     * An {@link UnsupportedOperationException} will be thrown with a message indicating implementation limits
     * if implementation capacities are exceeded.</p>
     *
     * <p>A <code>null</code> value indicates that field is not set.</p>
     *
     * @param isPositive Set to <code>false</code> to create a negative duration. When the length
     *   of the duration is zero, this parameter will be ignored.
     * @param year Year of <code>Duration</code>.
     * @param month Month of <code>Duration</code>.
     *
     * @return New <code>Duration</code> created using the specified <code>year</code> and <code>month</code>.
     *
     * @throws IllegalArgumentException If the values are not a valid representation of a
     * <code>Duration</code>: if all of the fields (year, month) are null or
     * if any of the fields is negative.
     * @throws UnsupportedOperationException If implementation cannot support requested values.
     */
    public Duration newDurationYearMonth(
            final boolean isPositive,
            final BigInteger year,
            final BigInteger month) {

            return newDuration(
                    isPositive,
                    year,
                    month,
                    null, // days
                    null, // hours
                    null, // minutes
                    null  // seconds
            );
    }

    /**
     * <p>Create a <code>Duration</code> of type <code>xdt:yearMonthDuration</code> using the specified
     * <code>year</code> and <code>month</code> as defined in
     * <a href="http://www.w3.org/TR/xpath-datamodel#yearMonthDuration">
     *   XQuery 1.0 and XPath 2.0 Data Model, xdt:yearMonthDuration</a>.</p>
     *
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param isPositive Set to <code>false</code> to create a negative duration. When the length
     *   of the duration is zero, this parameter will be ignored.
     * @param year Year of <code>Duration</code>.
     * @param month Month of <code>Duration</code>.
     *
     * @return New <code>Duration</code> created using the specified <code>year</code> and <code>month</code>.
     *
     * @throws IllegalArgumentException If the values are not a valid representation of a
     * <code>Duration</code>: if any of the fields (year, month) is negative.
     */
    public Duration newDurationYearMonth(
            final boolean isPositive,
            final int year,
            final int month) {

            return newDurationYearMonth(
                    isPositive,
                    BigInteger.valueOf((long) year),
                    BigInteger.valueOf((long) month));
            }

    /**
     * <p>Create a new instance of an <code>XMLGregorianCalendar</code>.</p>
     *
     * <p>All date/time datatype fields set to {@link DatatypeConstants#FIELD_UNDEFINED} or null.</p>
     *
     * @return New <code>XMLGregorianCalendar</code> with all date/time datatype fields set to
     *   {@link DatatypeConstants#FIELD_UNDEFINED} or null.
     */
    public abstract XMLGregorianCalendar newXMLGregorianCalendar();

    /**
     * <p>Create a new XMLGregorianCalendar by parsing the String as a lexical representation.</p>
     *
     * <p>Parsing the lexical string representation is defined in
     * <a href="http://www.w3.org/TR/xmlschema-2/#dateTime-order">XML Schema 1.0 Part 2, Section 3.2.[7-14].1,
     * <em>Lexical Representation</em>.</a></p>
     *
     * <p>The string representation may not have any leading and trailing whitespaces.</p>
     *
     * <p>The parsing is done field by field so that
     * the following holds for any lexically correct String x:</p>
     * <pre>
     * newXMLGregorianCalendar(x).toXMLFormat().equals(x)
     * </pre>
     * <p>Except for the noted lexical/canonical representation mismatches
     * listed in <a href="http://www.w3.org/2001/05/xmlschema-errata#e2-45">
     * XML Schema 1.0 errata, Section 3.2.7.2</a>.</p>
     *
     * @param lexicalRepresentation Lexical representation of one the eight XML Schema date/time datatypes.
     *
     * @return <code>XMLGregorianCalendar</code> created from the <code>lexicalRepresentation</code>.
     *
     * @throws IllegalArgumentException If the <code>lexicalRepresentation</code> is not a valid <code>XMLGregorianCalendar</code>.
     * @throws NullPointerException If <code>lexicalRepresentation</code> is <code>null</code>.
     */
    public abstract XMLGregorianCalendar newXMLGregorianCalendar(final String lexicalRepresentation);

    /**
     * <p>Create an <code>XMLGregorianCalendar</code> from a {@link GregorianCalendar}.</p>
     *
     * <table border="2" rules="all" cellpadding="2">
     *   <thead>
     *     <tr>
     *       <th align="center" colspan="2">
     *          Field by Field Conversion from
     *          {@link GregorianCalendar} to an {@link XMLGregorianCalendar}
     *       </th>
     *     </tr>
     *     <tr>
     *        <th><code>java.util.GregorianCalendar</code> field</th>
     *        <th><code>javax.xml.datatype.XMLGregorianCalendar</code> field</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td><code>ERA == GregorianCalendar.BC ? -YEAR : YEAR</code></td>
     *       <td>{@link XMLGregorianCalendar#setYear(int year)}</td>
     *     </tr>
     *     <tr>
     *       <td><code>MONTH + 1</code></td>
     *       <td>{@link XMLGregorianCalendar#setMonth(int month)}</td>
     *     </tr>
     *     <tr>
     *       <td><code>DAY_OF_MONTH</code></td>
     *       <td>{@link XMLGregorianCalendar#setDay(int day)}</td>
     *     </tr>
     *     <tr>
     *       <td><code>HOUR_OF_DAY, MINUTE, SECOND, MILLISECOND</code></td>
     *       <td>{@link XMLGregorianCalendar#setTime(int hour, int minute, int second, BigDecimal fractional)}</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <code>(ZONE_OFFSET + DST_OFFSET) / (60*1000)</code><br/>
     *         <em>(in minutes)</em>
     *       </td>
     *       <td>{@link XMLGregorianCalendar#setTimezone(int offset)}<sup><em>*</em></sup>
     *       </td>
     *     </tr>
     *   </tbody>
     * </table>
     * <p><em>*</em>conversion loss of information. It is not possible to represent
     * a <code>java.util.GregorianCalendar</code> daylight savings timezone id in the
     * XML Schema 1.0 date/time datatype representation.</p>
     *
     * <p>To compute the return value's <code>TimeZone</code> field,
     * <ul>
     * <li>when <code>this.getTimezone() != FIELD_UNDEFINED</code>,
     * create a <code>java.util.TimeZone</code> with a custom timezone id
     * using the <code>this.getTimezone()</code>.</li>
     * <li>else use the <code>GregorianCalendar</code> default timezone value
     * for the host is defined as specified by
     * <code>java.util.TimeZone.getDefault()</code>.</li></p>
     *
     * @param cal <code>java.util.GregorianCalendar</code> used to create <code>XMLGregorianCalendar</code>
     *
     * @return <code>XMLGregorianCalendar</code> created from <code>java.util.GregorianCalendar</code>
     *
     * @throws NullPointerException If <code>cal</code> is <code>null</code>.
     */
    public abstract XMLGregorianCalendar newXMLGregorianCalendar(final GregorianCalendar cal);

    /**
     * <p>Constructor allowing for complete value spaces allowed by
     * W3C XML Schema 1.0 recommendation for xsd:dateTime and related
     * builtin datatypes. Note that <code>year</code> parameter supports
     * arbitrarily large numbers and fractionalSecond has infinite
     * precision.</p>
     *
     * <p>A <code>null</code> value indicates that field is not set.</p>
     *
     * @param year of <code>XMLGregorianCalendar</code> to be created.
     * @param month of <code>XMLGregorianCalendar</code> to be created.
     * @param day of <code>XMLGregorianCalendar</code> to be created.
     * @param hour of <code>XMLGregorianCalendar</code> to be created.
     * @param minute of <code>XMLGregorianCalendar</code> to be created.
     * @param second of <code>XMLGregorianCalendar</code> to be created.
     * @param fractionalSecond of <code>XMLGregorianCalendar</code> to be created.
     * @param timezone of <code>XMLGregorianCalendar</code> to be created.
     *
     * @return <code>XMLGregorianCalendar</code> created from specified values.
     *
     * @throws IllegalArgumentException If any individual parameter's value is outside the maximum value constraint for the field
     *   as determined by the Date/Time Data Mapping table in {@link XMLGregorianCalendar}
     *   or if the composite values constitute an invalid <code>XMLGregorianCalendar</code> instance
     *   as determined by {@link XMLGregorianCalendar#isValid()}.
     */
    public abstract XMLGregorianCalendar newXMLGregorianCalendar(
            final BigInteger year,
            final int month,
            final int day,
            final int hour,
            final int minute,
            final int second,
            final BigDecimal fractionalSecond,
            final int timezone);

    /**
     * <p>Constructor of value spaces that a
     * <code>java.util.GregorianCalendar</code> instance would need to convert to an
     * <code>XMLGregorianCalendar</code> instance.</p>
     *
     * <p><code>XMLGregorianCalendar eon</code> and
     * <code>fractionalSecond</code> are set to <code>null</code></p>
     *
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param year of <code>XMLGregorianCalendar</code> to be created.
     * @param month of <code>XMLGregorianCalendar</code> to be created.
     * @param day of <code>XMLGregorianCalendar</code> to be created.
     * @param hour of <code>XMLGregorianCalendar</code> to be created.
     * @param minute of <code>XMLGregorianCalendar</code> to be created.
     * @param second of <code>XMLGregorianCalendar</code> to be created.
     * @param millisecond of <code>XMLGregorianCalendar</code> to be created.
     * @param timezone of <code>XMLGregorianCalendar</code> to be created.
     *
     * @return <code>XMLGregorianCalendar</code> created from specified values.
     *
     * @throws IllegalArgumentException If any individual parameter's value is outside the maximum value constraint for the field
     *   as determined by the Date/Time Data Mapping table in {@link XMLGregorianCalendar}
     *   or if the composite values constitute an invalid <code>XMLGregorianCalendar</code> instance
     *   as determined by {@link XMLGregorianCalendar#isValid()}.
     */
    public XMLGregorianCalendar newXMLGregorianCalendar(
            final int year,
            final int month,
            final int day,
            final int hour,
            final int minute,
            final int second,
            final int millisecond,
            final int timezone) {

            // year may be undefined
            BigInteger realYear = (year != DatatypeConstants.FIELD_UNDEFINED) ? BigInteger.valueOf((long) year) : null;

            // millisecond may be undefined
            // millisecond must be >= 0 millisecond <= 1000
            BigDecimal realMillisecond = null; // undefined value
            if (millisecond != DatatypeConstants.FIELD_UNDEFINED) {
                    if (millisecond < 0 || millisecond > 1000) {
                            throw new IllegalArgumentException(
                                                    "javax.xml.datatype.DatatypeFactory#newXMLGregorianCalendar("
                                                    + "int year, int month, int day, int hour, int minute, int second, int millisecond, int timezone)"
                                                    + "with invalid millisecond: " + millisecond
                                                    );
                    }

                    realMillisecond = BigDecimal.valueOf((long) millisecond).movePointLeft(3);
            }

            return newXMLGregorianCalendar(
                    realYear,
                    month,
                    day,
                    hour,
                    minute,
                    second,
                    realMillisecond,
                    timezone
            );
    }

    /**
     * <p>Create a Java representation of XML Schema builtin datatype <code>date</code> or <code>g*</code>.</p>
     *
     * <p>For example, an instance of <code>gYear</code> can be created invoking this factory
     * with <code>month</code> and <code>day</code> parameters set to
     * {@link DatatypeConstants#FIELD_UNDEFINED}.</p>
     *
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param year of <code>XMLGregorianCalendar</code> to be created.
     * @param month of <code>XMLGregorianCalendar</code> to be created.
     * @param day of <code>XMLGregorianCalendar</code> to be created.
     * @param timezone offset in minutes. {@link DatatypeConstants#FIELD_UNDEFINED} indicates optional field is not set.
     *
     * @return <code>XMLGregorianCalendar</code> created from parameter values.
     *
     * @see DatatypeConstants#FIELD_UNDEFINED
     *
     * @throws IllegalArgumentException If any individual parameter's value is outside the maximum value constraint for the field
     *   as determined by the Date/Time Data Mapping table in {@link XMLGregorianCalendar}
     *   or if the composite values constitute an invalid <code>XMLGregorianCalendar</code> instance
     *   as determined by {@link XMLGregorianCalendar#isValid()}.
     */
    public XMLGregorianCalendar newXMLGregorianCalendarDate(
            final int year,
            final int month,
            final int day,
            final int timezone) {

            return newXMLGregorianCalendar(
                    year,
                    month,
                    day,
                    DatatypeConstants.FIELD_UNDEFINED, // hour
                    DatatypeConstants.FIELD_UNDEFINED, // minute
                    DatatypeConstants.FIELD_UNDEFINED, // second
                    DatatypeConstants.FIELD_UNDEFINED, // millisecond
                    timezone);
            }

    /**
     * <p>Create a Java instance of XML Schema builtin datatype <code>time</code>.</p>
     *
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param hours number of hours
     * @param minutes number of minutes
     * @param seconds number of seconds
     * @param timezone offset in minutes. {@link DatatypeConstants#FIELD_UNDEFINED} indicates optional field is not set.
     *
     * @return <code>XMLGregorianCalendar</code> created from parameter values.
     *
     * @throws IllegalArgumentException If any individual parameter's value is outside the maximum value constraint for the field
     *   as determined by the Date/Time Data Mapping table in {@link XMLGregorianCalendar}
     *   or if the composite values constitute an invalid <code>XMLGregorianCalendar</code> instance
     *   as determined by {@link XMLGregorianCalendar#isValid()}.
     *
     * @see DatatypeConstants#FIELD_UNDEFINED
     */
    public XMLGregorianCalendar newXMLGregorianCalendarTime(
            final int hours,
            final int minutes,
            final int seconds,
            final int timezone) {

            return newXMLGregorianCalendar(
                    DatatypeConstants.FIELD_UNDEFINED, // Year
                    DatatypeConstants.FIELD_UNDEFINED, // Month
                    DatatypeConstants.FIELD_UNDEFINED, // Day
                    hours,
                    minutes,
                    seconds,
                    DatatypeConstants.FIELD_UNDEFINED, //Millisecond
                    timezone);
    }

    /**
     * <p>Create a Java instance of XML Schema builtin datatype time.</p>
     *
     * <p>A <code>null</code> value indicates that field is not set.</p>
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param hours number of hours
     * @param minutes number of minutes
     * @param seconds number of seconds
     * @param fractionalSecond value of <code>null</code> indicates that this optional field is not set.
     * @param timezone offset in minutes. {@link DatatypeConstants#FIELD_UNDEFINED} indicates optional field is not set.
     *
     * @return <code>XMLGregorianCalendar</code> created from parameter values.
     *
     * @see DatatypeConstants#FIELD_UNDEFINED
     *
     * @throws IllegalArgumentException If any individual parameter's value is outside the maximum value constraint for the field
     *   as determined by the Date/Time Data Mapping table in {@link XMLGregorianCalendar}
     *   or if the composite values constitute an invalid <code>XMLGregorianCalendar</code> instance
     *   as determined by {@link XMLGregorianCalendar#isValid()}.
     */
    public XMLGregorianCalendar newXMLGregorianCalendarTime(
            final int hours,
            final int minutes,
            final int seconds,
            final BigDecimal fractionalSecond,
            final int timezone) {

            return newXMLGregorianCalendar(
                    null, // year
                    DatatypeConstants.FIELD_UNDEFINED, // month
                    DatatypeConstants.FIELD_UNDEFINED, // day
                    hours,
                    minutes,
                    seconds,
                    fractionalSecond,
                    timezone);
            }

    /**
     * <p>Create a Java instance of XML Schema builtin datatype time.</p>
     *
     * <p>A {@link DatatypeConstants#FIELD_UNDEFINED} value indicates that field is not set.</p>
     *
     * @param hours number of hours
     * @param minutes number of minutes
     * @param seconds number of seconds
     * @param milliseconds number of milliseconds
     * @param timezone offset in minutes. {@link DatatypeConstants#FIELD_UNDEFINED} indicates optional field is not set.
     *
     * @return <code>XMLGregorianCalendar</code> created from parameter values.
     *
     * @see DatatypeConstants#FIELD_UNDEFINED
     *
     * @throws IllegalArgumentException If any individual parameter's value is outside the maximum value constraint for the field
     *   as determined by the Date/Time Data Mapping table in {@link XMLGregorianCalendar}
     *   or if the composite values constitute an invalid <code>XMLGregorianCalendar</code> instance
     *   as determined by {@link XMLGregorianCalendar#isValid()}.
     */
    public XMLGregorianCalendar newXMLGregorianCalendarTime(
            final int hours,
            final int minutes,
            final int seconds,
            final int milliseconds,
            final int timezone) {

            // millisecond may be undefined
            // millisecond must be >= 0 millisecond <= 1000
            BigDecimal realMilliseconds = null; // undefined value
            if (milliseconds != DatatypeConstants.FIELD_UNDEFINED) {
                    if (milliseconds < 0 || milliseconds > 1000) {
                            throw new IllegalArgumentException(
                                                    "javax.xml.datatype.DatatypeFactory#newXMLGregorianCalendarTime("
                                                    + "int hours, int minutes, int seconds, int milliseconds, int timezone)"
                                                    + "with invalid milliseconds: " + milliseconds
                                                    );
                    }

                    realMilliseconds = BigDecimal.valueOf((long) milliseconds).movePointLeft(3);
            }

            return newXMLGregorianCalendarTime(
                    hours,
                    minutes,
                    seconds,
                    realMilliseconds,
                    timezone
            );
    }
}
