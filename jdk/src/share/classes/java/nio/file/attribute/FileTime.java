/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.nio.file.attribute;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Represents the value of a file's time stamp attribute. For example, it may
 * represent the time that the file was last modified, accessed, or created.
 *
 * <p> Instances of this class are immutable.
 *
 * @since 1.7
 * @see BasicFileAttributes
 * @see Attributes#setLastModifiedTime
 */

public final class FileTime implements Comparable<FileTime> {
    private final long value;
    private final TimeUnit unit;
    private String valueAsString;  // created lazily

    private FileTime(long value, TimeUnit unit) {
        if (unit == null)
            throw new NullPointerException();
        this.value = value;
        this.unit = unit;
    }

    /**
     * Returns a {@code FileTime} representing a value at the given unit of
     * granularity.
     *
     * @param   value
     *          the value since the epoch (1970-01-01T00:00:00Z); can be
     *          negative
     * @param   unit
     *          the unit of granularity to interpret the value
     *
     * @return  a {@code FileTime} representing the given value
     */
    public static FileTime from(long value, TimeUnit unit) {
        return new FileTime(value, unit);
    }

    /**
     * Returns a {@code FileTime} representing the given value in milliseconds.
     *
     * @param   value
     *          the value, in milliseconds, since the epoch
     *          (1970-01-01T00:00:00Z); can be negative
     *
     * @return  a {@code FileTime} representing the given value
     */
    public static FileTime fromMillis(long value) {
        return new FileTime(value, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the value at the given unit of granularity.
     *
     * <p> Conversion from a coarser granularity that would numerically overflow
     * saturate to {@code Long.MIN_VALUE} if negative or {@code Long.MAX_VALUE}
     * if positive.
     *
     * @param   unit
     *          the unit of granularity for the return value
     *
     * @return  value in the given unit of granularity, since the epoch
     *          since the epoch (1970-01-01T00:00:00Z); can be negative
     */
    public long to(TimeUnit unit) {
        return unit.convert(this.value, this.unit);
    }

    /**
     * Returns the value in milliseconds.
     *
     * <p> Conversion from a coarser granularity that would numerically overflow
     * saturate to {@code Long.MIN_VALUE} if negative or {@code Long.MAX_VALUE}
     * if positive.
     *
     * @return  the value in milliseconds, since the epoch (1970-01-01T00:00:00Z)
     */
    public long toMillis() {
        return unit.toMillis(value);
    }

    /**
     * Tests this {@code FileTime} for equality with the given object.
     *
     * <p> The result is {@code true} if and only if the argument is not {@code
     * null} and is a {@code FileTime} that represents the same time. This
     * method satisfies the general contract of the {@code Object.equals} method.
     *
     * @param   obj
     *          the object to compare with
     *
     * @return  {@code true} if, and only if, the given object is a {@code
     *          FileTime} that represents the same time
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof FileTime) ? compareTo((FileTime)obj) == 0 : false;
    }

    /**
     * Computes a hash code for this file time.
     *
     * <p> The hash code is based upon the value represented, and satisfies the
     * general contract of the {@link Object#hashCode} method.
     *
     * @return  the hash-code value
     */
    @Override
    public int hashCode() {
        // hash value for fixed granularity to satisfy contract with equals
        long ms = toMillis();
        return (int)(ms ^ (ms >>> 32));
    }

    /**
     * Compares the value of two {@code FileTime} objects for order.
     *
     * @param   other
     *          the other {@code FileTime} to be compared
     *
     * @return  {@code 0} if this {@code FileTime} is equal to {@code other}, a
     *          value less than 0 if this {@code FileTime} represents a time
     *          that is before {@code other}, and a value greater than 0 if this
     *          {@code FileTime} represents a time that is after {@code other}
     */
    @Override
    public int compareTo(FileTime other) {
        // same granularity
        if (unit == other.unit)
            return (value < other.value) ? -1 : (value == other.value ? 0 : 1);

        // compare in days
        long thisValueInDays = unit.toDays(value);
        long otherValueInDays = other.unit.toDays(other.value);
        if (thisValueInDays != otherValueInDays)
            return (thisValueInDays < otherValueInDays) ? -1 : 1;

        // compare remainder in nanoseconds
        long thisRemainder = remainderInNanos(thisValueInDays);
        long otherRemainder = other.remainderInNanos(otherValueInDays);
        return (thisRemainder < otherRemainder) ? -1 :
            (thisRemainder == otherRemainder) ? 0 : 1;
    }

    private long remainderInNanos(long days) {
        // constants for conversion
        final long C0 = 1L;
        final long C1 = C0 * 24L;
        final long C2 = C1 * 60L;
        final long C3 = C2 * 60L;
        final long C4 = C3 * 1000L;
        final long C5 = C4 * 1000L;
        final long C6 = C5 * 1000L;

        long scale;
        switch (unit) {
            case DAYS         : scale = C0; break;
            case HOURS        : scale = C1; break;
            case MINUTES      : scale = C2; break;
            case SECONDS      : scale = C3; break;
            case MILLISECONDS : scale = C4; break;
            case MICROSECONDS : scale = C5; break;
            case NANOSECONDS  : scale = C6; break;
            default:
                throw new AssertionError("Unit not handled");
        }
        long rem = value - (days * scale);
        return unit.toNanos(rem);
    }

    /**
     * Returns the string representation of this {@code FileTime}. The string
     * is returned in the <a
     * href="http://www.w3.org/TR/NOTE-datetime">ISO&nbsp;8601</a> format:
     * <pre>
     *     YYYY-MM-DDThh:mm:ss[.s+]Z
     * </pre>
     * where "{@code [.s+]}" represents a dot followed by one of more digits
     * for the decimal fraction of a second. It is only present when the decimal
     * fraction of a second is not zero. For example, {@code
     * FileTime.fromMillis(1234567890000L).toString()} yields {@code
     * "2009-02-13T23:31:30Z"}, and {@code FileTime.fromMillis(1234567890123L).toString()}
     * yields {@code "2009-02-13T23:31:30.123Z"}.
     *
     * <p> A {@code FileTime} is primarly intended to represent the value of a
     * file's time stamp. Where used to represent <i>extreme values</i>, where
     * the year is less than "{@code 0001}" or greater than "{@code 9999}" then
     * the year may be expanded to more than four digits and may be
     * negative-signed. If more than four digits then leading zeros are not
     * present. The year before "{@code 0001}" is "{@code -0001}".
     *
     * @return  the string representation of this file time
     */
    @Override
    public String toString() {
        String v = valueAsString;
        if (v == null) {
            // overflow saturates to Long.MIN_VALUE or Long.MAX_VALUE so this
            // limits the range:
            // [-292275056-05-16T16:47:04.192Z,292278994-08-17T07:12:55.807Z]
            long ms = toMillis();

            // nothing to do when seconds/minutes/hours/days
            String fractionAsString = "";
            if (unit.compareTo(TimeUnit.SECONDS) < 0) {
                // constants for conversion
                final long C0 = 1L;
                final long C1 = C0 * 1000L;
                final long C2 = C1 * 1000L;
                final long C3 = C2 * 1000L;

                long scale;
                int width;
                switch (unit) {
                    case MILLISECONDS : scale = C1; width = 3; break;
                    case MICROSECONDS : scale = C2; width = 6; break;
                    case NANOSECONDS  : scale = C3; width = 9; break;
                    default:
                        throw new AssertionError("Unit not handled");
                }
                long fraction = value % scale;
                if (fraction != 0L) {
                    // fraction must be positive
                    if (fraction < 0L) {
                        fraction += scale;
                        if (ms != Long.MIN_VALUE) ms--;
                    }

                    // convert to String, adding leading zeros as required and
                    // stripping any trailing zeros
                    String s = Long.toString(fraction);
                    int len = s.length();
                    width -= len;
                    StringBuilder sb = new StringBuilder(".");
                    while (width-- > 0) {
                        sb.append('0');
                    }
                    if (s.charAt(len-1) == '0') {
                        // drop trailing zeros
                        len--;
                        while (s.charAt(len-1) == '0')
                            len--;
                        sb.append(s.substring(0, len));
                    } else {
                        sb.append(s);
                    }
                    fractionAsString = sb.toString();
                }
            }

            // create calendar to use with formatter.
            GregorianCalendar cal =
                new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            if (value < 0L)
                cal.setGregorianChange(new Date(Long.MIN_VALUE));
            cal.setTimeInMillis(ms);

            // years are negative before common era
            String sign = (cal.get(Calendar.ERA) == GregorianCalendar.BC) ? "-" : "";

            // [-]YYYY-MM-DDThh:mm:ss[.s]Z
            v = new Formatter(Locale.ROOT)
                .format("%s%tFT%tR:%tS%sZ", sign, cal, cal, cal, fractionAsString)
                .toString();
            valueAsString = v;
        }
        return v;
    }
}
