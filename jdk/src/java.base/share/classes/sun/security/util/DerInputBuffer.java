/*
 * Copyright (c) 1996, 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import sun.util.calendar.CalendarDate;
import sun.util.calendar.CalendarSystem;

/**
 * DER input buffer ... this is the main abstraction in the DER library
 * which actively works with the "untyped byte stream" abstraction.  It
 * does so with impunity, since it's not intended to be exposed to
 * anyone who could violate the "typed value stream" DER model and hence
 * corrupt the input stream of DER values.
 *
 * @author David Brownell
 */
class DerInputBuffer extends ByteArrayInputStream implements Cloneable {

    boolean allowBER = true;

    // used by sun/security/util/DerInputBuffer/DerInputBufferEqualsHashCode.java
    DerInputBuffer(byte[] buf) {
        this(buf, true);
    }

    DerInputBuffer(byte[] buf, boolean allowBER) {
        super(buf);
        this.allowBER = allowBER;
    }

    DerInputBuffer(byte[] buf, int offset, int len, boolean allowBER) {
        super(buf, offset, len);
        this.allowBER = allowBER;
    }

    DerInputBuffer dup() {
        try {
            DerInputBuffer retval = (DerInputBuffer)clone();
            retval.mark(Integer.MAX_VALUE);
            return retval;
        } catch (CloneNotSupportedException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

    byte[] toByteArray() {
        int     len = available();
        if (len <= 0)
            return null;
        byte[]  retval = new byte[len];

        System.arraycopy(buf, pos, retval, 0, len);
        return retval;
    }

    int peek() throws IOException {
        if (pos >= count)
            throw new IOException("out of data");
        else
            return buf[pos];
    }

    /**
     * Compares this DerInputBuffer for equality with the specified
     * object.
     */
    public boolean equals(Object other) {
        if (other instanceof DerInputBuffer)
            return equals((DerInputBuffer)other);
        else
            return false;
    }

    boolean equals(DerInputBuffer other) {
        if (this == other)
            return true;

        int max = this.available();
        if (other.available() != max)
            return false;
        for (int i = 0; i < max; i++) {
            if (this.buf[this.pos + i] != other.buf[other.pos + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a hashcode for this DerInputBuffer.
     *
     * @return a hashcode for this DerInputBuffer.
     */
    public int hashCode() {
        int retval = 0;

        int len = available();
        int p = pos;

        for (int i = 0; i < len; i++)
            retval += buf[p + i] * i;
        return retval;
    }

    void truncate(int len) throws IOException {
        if (len > available())
            throw new IOException("insufficient data");
        count = pos + len;
    }

    /**
     * Returns the integer which takes up the specified number
     * of bytes in this buffer as a BigInteger.
     * @param len the number of bytes to use.
     * @param makePositive whether to always return a positive value,
     *   irrespective of actual encoding
     * @return the integer as a BigInteger.
     */
    BigInteger getBigInteger(int len, boolean makePositive) throws IOException {
        if (len > available())
            throw new IOException("short read of integer");

        if (len == 0) {
            throw new IOException("Invalid encoding: zero length Int value");
        }

        byte[] bytes = new byte[len];

        System.arraycopy(buf, pos, bytes, 0, len);
        skip(len);

        // BER allows leading 0s but DER does not
        if (!allowBER && (len >= 2 && (bytes[0] == 0) && (bytes[1] >= 0))) {
            throw new IOException("Invalid encoding: redundant leading 0s");
        }

        if (makePositive) {
            return new BigInteger(1, bytes);
        } else {
            return new BigInteger(bytes);
        }
    }

    /**
     * Returns the integer which takes up the specified number
     * of bytes in this buffer.
     * @throws IOException if the result is not within the valid
     * range for integer, i.e. between Integer.MIN_VALUE and
     * Integer.MAX_VALUE.
     * @param len the number of bytes to use.
     * @return the integer.
     */
    public int getInteger(int len) throws IOException {

        BigInteger result = getBigInteger(len, false);
        if (result.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
            throw new IOException("Integer below minimum valid value");
        }
        if (result.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IOException("Integer exceeds maximum valid value");
        }
        return result.intValue();
    }

    /**
     * Returns the bit string which takes up the specified
     * number of bytes in this buffer.
     */
    public byte[] getBitString(int len) throws IOException {
        if (len > available())
            throw new IOException("short read of bit string");

        if (len == 0) {
            throw new IOException("Invalid encoding: zero length bit string");
        }

        int numOfPadBits = buf[pos];
        if ((numOfPadBits < 0) || (numOfPadBits > 7)) {
            throw new IOException("Invalid number of padding bits");
        }
        // minus the first byte which indicates the number of padding bits
        byte[] retval = new byte[len - 1];
        System.arraycopy(buf, pos + 1, retval, 0, len - 1);
        if (numOfPadBits != 0) {
            // get rid of the padding bits
            retval[len - 2] &= (0xff << numOfPadBits);
        }
        skip(len);
        return retval;
    }

    /**
     * Returns the bit string which takes up the rest of this buffer.
     */
    byte[] getBitString() throws IOException {
        return getBitString(available());
    }

    /**
     * Returns the bit string which takes up the rest of this buffer.
     * The bit string need not be byte-aligned.
     */
    BitArray getUnalignedBitString() throws IOException {
        if (pos >= count)
            return null;
        /*
         * Just copy the data into an aligned, padded octet buffer,
         * and consume the rest of the buffer.
         */
        int len = available();
        int unusedBits = buf[pos] & 0xff;
        if (unusedBits > 7 ) {
            throw new IOException("Invalid value for unused bits: " + unusedBits);
        }
        byte[] bits = new byte[len - 1];
        // number of valid bits
        int length = (bits.length == 0) ? 0 : bits.length * 8 - unusedBits;

        System.arraycopy(buf, pos + 1, bits, 0, len - 1);

        BitArray bitArray = new BitArray(length, bits);
        pos = count;
        return bitArray;
    }

    /**
     * Returns the UTC Time value that takes up the specified number
     * of bytes in this buffer.
     * @param len the number of bytes to use
     */
    public Date getUTCTime(int len) throws IOException {
        if (len > available())
            throw new IOException("short read of DER UTC Time");

        if (len < 11 || len > 17)
            throw new IOException("DER UTC Time length error");

        return getTime(len, false);
    }

    /**
     * Returns the Generalized Time value that takes up the specified
     * number of bytes in this buffer.
     * @param len the number of bytes to use
     */
    public Date getGeneralizedTime(int len) throws IOException {
        if (len > available())
            throw new IOException("short read of DER Generalized Time");

        if (len < 13)
            throw new IOException("DER Generalized Time length error");

        return getTime(len, true);

    }

    /**
     * Private helper routine to extract time from the der value.
     * @param len the number of bytes to use
     * @param generalized true if Generalized Time is to be read, false
     * if UTC Time is to be read.
     */
    private Date getTime(int len, boolean generalized) throws IOException {

        /*
         * UTC time encoded as ASCII chars:
         *       YYMMDDhhmmZ
         *       YYMMDDhhmmssZ
         *       YYMMDDhhmm+hhmm
         *       YYMMDDhhmm-hhmm
         *       YYMMDDhhmmss+hhmm
         *       YYMMDDhhmmss-hhmm
         * UTC Time is broken in storing only two digits of year.
         * If YY < 50, we assume 20YY;
         * if YY >= 50, we assume 19YY, as per RFC 5280.
         *
         * Generalized time has a four-digit year and allows any
         * precision specified in ISO 8601. However, for our purposes,
         * we will only allow the same format as UTC time, except that
         * fractional seconds (millisecond precision) are supported.
         */

        int year, month, day, hour, minute, second, millis;
        String type = null;

        if (generalized) {
            type = "Generalized";
            year = 1000 * Character.digit((char)buf[pos++], 10);
            year += 100 * Character.digit((char)buf[pos++], 10);
            year += 10 * Character.digit((char)buf[pos++], 10);
            year += Character.digit((char)buf[pos++], 10);
            len -= 2; // For the two extra YY
        } else {
            type = "UTC";
            year = 10 * Character.digit((char)buf[pos++], 10);
            year += Character.digit((char)buf[pos++], 10);

            if (year < 50)              // origin 2000
                year += 2000;
            else
                year += 1900;   // origin 1900
        }

        month = 10 * Character.digit((char)buf[pos++], 10);
        month += Character.digit((char)buf[pos++], 10);

        day = 10 * Character.digit((char)buf[pos++], 10);
        day += Character.digit((char)buf[pos++], 10);

        hour = 10 * Character.digit((char)buf[pos++], 10);
        hour += Character.digit((char)buf[pos++], 10);

        minute = 10 * Character.digit((char)buf[pos++], 10);
        minute += Character.digit((char)buf[pos++], 10);

        len -= 10; // YYMMDDhhmm

        /*
         * We allow for non-encoded seconds, even though the
         * IETF-PKIX specification says that the seconds should
         * always be encoded even if it is zero.
         */

        millis = 0;
        if (len > 2) {
            second = 10 * Character.digit((char)buf[pos++], 10);
            second += Character.digit((char)buf[pos++], 10);
            len -= 2;
            // handle fractional seconds (if present)
            if (buf[pos] == '.' || buf[pos] == ',') {
                len --;
                pos++;
                int precision = 0;
                while (buf[pos] != 'Z' &&
                       buf[pos] != '+' &&
                       buf[pos] != '-') {
                    // Validate all digits in the fractional part but
                    // store millisecond precision only
                    int thisDigit = Character.digit((char)buf[pos], 10);
                    precision++;
                    pos++;
                    switch (precision) {
                        case 1:
                            millis += 100 * thisDigit;
                            break;
                        case 2:
                            millis += 10 * thisDigit;
                            break;
                        case 3:
                            millis += thisDigit;
                            break;
                    }
                }
                if (precision == 0) {
                    throw new IOException("Parse " + type +
                            " time, empty fractional part");
                }
                len -= precision;
            }
        } else
            second = 0;

        if (month == 0 || day == 0
            || month > 12 || day > 31
            || hour >= 24 || minute >= 60 || second >= 60)
            throw new IOException("Parse " + type + " time, invalid format");

        /*
         * Generalized time can theoretically allow any precision,
         * but we're not supporting that.
         */
        CalendarSystem gcal = CalendarSystem.getGregorianCalendar();
        CalendarDate date = gcal.newCalendarDate(null); // no time zone
        date.setDate(year, month, day);
        date.setTimeOfDay(hour, minute, second, millis);
        long time = gcal.getTime(date);

        /*
         * Finally, "Z" or "+hhmm" or "-hhmm" ... offsets change hhmm
         */
        if (! (len == 1 || len == 5))
            throw new IOException("Parse " + type + " time, invalid offset");

        int hr, min;

        switch (buf[pos++]) {
        case '+':
            hr = 10 * Character.digit((char)buf[pos++], 10);
            hr += Character.digit((char)buf[pos++], 10);
            min = 10 * Character.digit((char)buf[pos++], 10);
            min += Character.digit((char)buf[pos++], 10);

            if (hr >= 24 || min >= 60)
                throw new IOException("Parse " + type + " time, +hhmm");

            time -= ((hr * 60) + min) * 60 * 1000;
            break;

        case '-':
            hr = 10 * Character.digit((char)buf[pos++], 10);
            hr += Character.digit((char)buf[pos++], 10);
            min = 10 * Character.digit((char)buf[pos++], 10);
            min += Character.digit((char)buf[pos++], 10);

            if (hr >= 24 || min >= 60)
                throw new IOException("Parse " + type + " time, -hhmm");

            time += ((hr * 60) + min) * 60 * 1000;
            break;

        case 'Z':
            break;

        default:
            throw new IOException("Parse " + type + " time, garbage offset");
        }
        return new Date(time);
    }
}
