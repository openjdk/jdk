/*
 * Copyright (c) 1996, 2004, Oracle and/or its affiliates. All rights reserved.
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

package java.sql;

/**
 * <P>A thin wrapper around the <code>java.util.Date</code> class that allows the JDBC
 * API to identify this as an SQL <code>TIME</code> value. The <code>Time</code>
 * class adds formatting and
 * parsing operations to support the JDBC escape syntax for time
 * values.
 * <p>The date components should be set to the "zero epoch"
 * value of January 1, 1970 and should not be accessed.
 */
public class Time extends java.util.Date {

    /**
     * Constructs a <code>Time</code> object initialized with the
     * given values for the hour, minute, and second.
     * The driver sets the date components to January 1, 1970.
     * Any method that attempts to access the date components of a
     * <code>Time</code> object will throw a
     * <code>java.lang.IllegalArgumentException</code>.
     * <P>
     * The result is undefined if a given argument is out of bounds.
     *
     * @param hour 0 to 23
     * @param minute 0 to 59
     * @param second 0 to 59
     *
     * @deprecated Use the constructor that takes a milliseconds value
     *             in place of this constructor
     */
    @Deprecated
    public Time(int hour, int minute, int second) {
        super(70, 0, 1, hour, minute, second);
    }

    /**
     * Constructs a <code>Time</code> object using a milliseconds time value.
     *
     * @param time milliseconds since January 1, 1970, 00:00:00 GMT;
     *             a negative number is milliseconds before
     *               January 1, 1970, 00:00:00 GMT
     */
    public Time(long time) {
        super(time);
    }

    /**
     * Sets a <code>Time</code> object using a milliseconds time value.
     *
     * @param time milliseconds since January 1, 1970, 00:00:00 GMT;
     *             a negative number is milliseconds before
     *               January 1, 1970, 00:00:00 GMT
     */
    public void setTime(long time) {
        super.setTime(time);
    }

    /**
     * Converts a string in JDBC time escape format to a <code>Time</code> value.
     *
     * @param s time in format "hh:mm:ss"
     * @return a corresponding <code>Time</code> object
     */
    public static Time valueOf(String s) {
        int hour;
        int minute;
        int second;
        int firstColon;
        int secondColon;

        if (s == null) throw new java.lang.IllegalArgumentException();

        firstColon = s.indexOf(':');
        secondColon = s.indexOf(':', firstColon+1);
        if ((firstColon > 0) & (secondColon > 0) &
            (secondColon < s.length()-1)) {
            hour = Integer.parseInt(s.substring(0, firstColon));
            minute =
                Integer.parseInt(s.substring(firstColon+1, secondColon));
            second = Integer.parseInt(s.substring(secondColon+1));
        } else {
            throw new java.lang.IllegalArgumentException();
        }

        return new Time(hour, minute, second);
    }

    /**
     * Formats a time in JDBC time escape format.
     *
     * @return a <code>String</code> in hh:mm:ss format
     */
    public String toString () {
        int hour = super.getHours();
        int minute = super.getMinutes();
        int second = super.getSeconds();
        String hourString;
        String minuteString;
        String secondString;

        if (hour < 10) {
            hourString = "0" + hour;
        } else {
            hourString = Integer.toString(hour);
        }
        if (minute < 10) {
            minuteString = "0" + minute;
        } else {
            minuteString = Integer.toString(minute);
        }
        if (second < 10) {
            secondString = "0" + second;
        } else {
            secondString = Integer.toString(second);
        }
        return (hourString + ":" + minuteString + ":" + secondString);
    }

    // Override all the date operations inherited from java.util.Date;

   /**
    * This method is deprecated and should not be used because SQL <code>TIME</code>
    * values do not have a year component.
    *
    * @deprecated
    * @exception java.lang.IllegalArgumentException if this
    *           method is invoked
    * @see #setYear
    */
    @Deprecated
    public int getYear() {
        throw new java.lang.IllegalArgumentException();
    }

   /**
    * This method is deprecated and should not be used because SQL <code>TIME</code>
    * values do not have a month component.
    *
    * @deprecated
    * @exception java.lang.IllegalArgumentException if this
    *           method is invoked
    * @see #setMonth
    */
    @Deprecated
    public int getMonth() {
        throw new java.lang.IllegalArgumentException();
    }

   /**
    * This method is deprecated and should not be used because SQL <code>TIME</code>
    * values do not have a day component.
    *
    * @deprecated
    * @exception java.lang.IllegalArgumentException if this
    *           method is invoked
    */
    @Deprecated
    public int getDay() {
        throw new java.lang.IllegalArgumentException();
    }

   /**
    * This method is deprecated and should not be used because SQL <code>TIME</code>
    * values do not have a date component.
    *
    * @deprecated
    * @exception java.lang.IllegalArgumentException if this
    *           method is invoked
    * @see #setDate
    */
    @Deprecated
    public int getDate() {
        throw new java.lang.IllegalArgumentException();
    }

   /**
    * This method is deprecated and should not be used because SQL <code>TIME</code>
    * values do not have a year component.
    *
    * @deprecated
    * @exception java.lang.IllegalArgumentException if this
    *           method is invoked
    * @see #getYear
    */
    @Deprecated
    public void setYear(int i) {
        throw new java.lang.IllegalArgumentException();
    }

   /**
    * This method is deprecated and should not be used because SQL <code>TIME</code>
    * values do not have a month component.
    *
    * @deprecated
    * @exception java.lang.IllegalArgumentException if this
    *           method is invoked
    * @see #getMonth
    */
    @Deprecated
    public void setMonth(int i) {
        throw new java.lang.IllegalArgumentException();
    }

   /**
    * This method is deprecated and should not be used because SQL <code>TIME</code>
    * values do not have a date component.
    *
    * @deprecated
    * @exception java.lang.IllegalArgumentException if this
    *           method is invoked
    * @see #getDate
    */
    @Deprecated
    public void setDate(int i) {
        throw new java.lang.IllegalArgumentException();
    }

   /**
    * Private serial version unique ID to ensure serialization
    * compatibility.
    */
    static final long serialVersionUID = 8397324403548013681L;
}
