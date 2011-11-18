/*
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
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5.internal;

import java.util.TimeZone;
import sun.security.util.*;
import sun.security.krb5.Config;
import sun.security.krb5.KrbException;
import sun.security.krb5.Asn1Exception;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.io.IOException;

/**
 * Implements the ASN.1 KerberosTime type.
 *
 * <xmp>
 * KerberosTime    ::= GeneralizedTime -- with no fractional seconds
 * </xmp>
 *
 * The timestamps used in Kerberos are encoded as GeneralizedTimes. A
 * KerberosTime value shall not include any fractional portions of the
 * seconds.  As required by the DER, it further shall not include any
 * separators, and it shall specify the UTC time zone (Z).
 *
 * <p>
 * This definition reflects the Network Working Group RFC 4120
 * specification available at
 * <a href="http://www.ietf.org/rfc/rfc4120.txt">
 * http://www.ietf.org/rfc/rfc4120.txt</a>.
 *
 * The implementation also includes the microseconds info so that the
 * same class can be used as a precise timestamp in Authenticator etc.
 */

public class KerberosTime implements Cloneable {

    private long kerberosTime; // milliseconds since epoch, a Date.getTime() value
    private int  microSeconds; // the last three digits of the microsecond value

    // The time when this class is loaded. Used in setNow()
    private static long initMilli = System.currentTimeMillis();
    private static long initMicro = System.nanoTime() / 1000;

    private static long syncTime;
    private static boolean DEBUG = Krb5.DEBUG;

    public static final boolean NOW = true;
    public static final boolean UNADJUSTED_NOW = false;

    public KerberosTime(long time) {
        kerberosTime = time;
    }

    private KerberosTime(long time, int micro) {
        kerberosTime = time;
        microSeconds = micro;
    }

    public Object clone() {
        return new KerberosTime(kerberosTime, microSeconds);
    }

    // This constructor is used in the native code
    // src/windows/native/sun/security/krb5/NativeCreds.c
    public KerberosTime(String time) throws Asn1Exception {
        kerberosTime = toKerberosTime(time);
    }

    /**
     * Constructs a KerberosTime object.
     * @param encoding a DER-encoded data.
     * @exception Asn1Exception if an error occurs while decoding an ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     */
    public KerberosTime(DerValue encoding) throws Asn1Exception, IOException {
        GregorianCalendar calendar = new GregorianCalendar();
        Date temp = encoding.getGeneralizedTime();
        kerberosTime = temp.getTime();
    }

    private static long toKerberosTime(String time) throws Asn1Exception {
        // this method only used by KerberosTime class.

        // ASN.1 GeneralizedTime format:

        // "19700101000000Z"
        //  |   | | | | | |
        //  0   4 6 8 | | |
        //           10 | |
        //             12 |
        //               14

        if (time.length() != 15)
            throw new Asn1Exception(Krb5.ASN1_BAD_TIMEFORMAT);
        if (time.charAt(14) != 'Z')
            throw new Asn1Exception(Krb5.ASN1_BAD_TIMEFORMAT);
        int year = Integer.parseInt(time.substring(0, 4));
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear(); // so that millisecond is zero
        calendar.set(year,
                     Integer.parseInt(time.substring(4, 6)) - 1,
                     Integer.parseInt(time.substring(6, 8)),
                     Integer.parseInt(time.substring(8, 10)),
                     Integer.parseInt(time.substring(10, 12)),
                     Integer.parseInt(time.substring(12, 14)));

        //The Date constructor assumes the setting are local relative
        //and converts the time to UTC before storing it.  Since we
        //want the internal representation to correspond to local
        //and not UTC time we subtract the UTC time offset.
        return (calendar.getTime().getTime());

    }

    // should be moved to sun.security.krb5.util class
    public static String zeroPad(String s, int length) {
        StringBuffer temp = new StringBuffer(s);
        while (temp.length() < length)
            temp.insert(0, '0');
        return temp.toString();
    }

    public KerberosTime(Date time) {
        kerberosTime = time.getTime(); // (time.getTimezoneOffset() * 60000L);
    }

    public KerberosTime(boolean initToNow) {
        if (initToNow) {
            setNow();
        }
    }

    /**
     * Returns a string representation of KerberosTime object.
     * @return a string representation of this object.
     */
    public String toGeneralizedTimeString() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();

        calendar.setTimeInMillis(kerberosTime);
        return zeroPad(Integer.toString(calendar.get(Calendar.YEAR)), 4) +
            zeroPad(Integer.toString(calendar.get(Calendar.MONTH) + 1), 2) +
            zeroPad(Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)), 2) +
            zeroPad(Integer.toString(calendar.get(Calendar.HOUR_OF_DAY)), 2) +
            zeroPad(Integer.toString(calendar.get(Calendar.MINUTE)), 2) +
            zeroPad(Integer.toString(calendar.get(Calendar.SECOND)), 2) + 'Z';

    }

    /**
     * Encodes this object to a byte array.
     * @return a byte array of encoded data.
     * @exception Asn1Exception if an error occurs while decoding an ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     */
    public byte[] asn1Encode() throws Asn1Exception, IOException {
        DerOutputStream out = new DerOutputStream();
        out.putGeneralizedTime(this.toDate());
        return out.toByteArray();
    }

    public long getTime() {
        return kerberosTime;
    }


    public void setTime(Date time) {
        kerberosTime = time.getTime(); // (time.getTimezoneOffset() * 60000L);
        microSeconds = 0;
    }

    public void setTime(long time) {
        kerberosTime = time;
        microSeconds = 0;
    }

    public Date toDate() {
        Date temp = new Date(kerberosTime);
        temp.setTime(temp.getTime());
        return temp;
    }

    public void setNow() {
        long newMilli = System.currentTimeMillis();
        long newMicro = System.nanoTime() / 1000;
        long microElapsed = newMicro - initMicro;
        long calcMilli = initMilli + microElapsed/1000;
        if (calcMilli - newMilli > 100 || newMilli - calcMilli > 100) {
            if (DEBUG) {
                System.out.println("System time adjusted");
            }
            initMilli = newMilli;
            initMicro = newMicro;
            setTime(newMilli);
            microSeconds = 0;
        } else {
            setTime(calcMilli);
            microSeconds = (int)(microElapsed % 1000);
        }
    }

    public int getMicroSeconds() {
        Long temp_long = new Long((kerberosTime % 1000L) * 1000L);
        return temp_long.intValue() + microSeconds;
    }

    public void setMicroSeconds(int usec) {
        microSeconds = usec % 1000;
        Integer temp_int = new Integer(usec);
        long temp_long = temp_int.longValue() / 1000L;
        kerberosTime = kerberosTime - (kerberosTime % 1000L) + temp_long;
    }

    public void setMicroSeconds(Integer usec) {
        if (usec != null) {
            microSeconds = usec.intValue() % 1000;
            long temp_long = usec.longValue() / 1000L;
            kerberosTime = kerberosTime - (kerberosTime % 1000L) + temp_long;
        }
    }

    public boolean inClockSkew(int clockSkew) {
        KerberosTime now = new KerberosTime(KerberosTime.NOW);

        if (java.lang.Math.abs(kerberosTime - now.kerberosTime) >
            clockSkew * 1000L)
            return false;
        return true;
    }

    public boolean inClockSkew() {
        return inClockSkew(getDefaultSkew());
    }

    public boolean inClockSkew(int clockSkew, KerberosTime now) {
        if (java.lang.Math.abs(kerberosTime - now.kerberosTime) >
            clockSkew * 1000L)
            return false;
        return true;
    }

    public boolean inClockSkew(KerberosTime time) {
        return inClockSkew(getDefaultSkew(), time);
    }

    public boolean greaterThanWRTClockSkew(KerberosTime time, int clockSkew) {
        if ((kerberosTime - time.kerberosTime) > clockSkew * 1000L)
            return true;
        return false;
    }

    public boolean greaterThanWRTClockSkew(KerberosTime time) {
        return greaterThanWRTClockSkew(time, getDefaultSkew());
    }

    public boolean greaterThan(KerberosTime time) {
        return kerberosTime > time.kerberosTime ||
            kerberosTime == time.kerberosTime &&
                    microSeconds > time.microSeconds;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof KerberosTime)) {
            return false;
        }

        return kerberosTime == ((KerberosTime)obj).kerberosTime &&
                microSeconds == ((KerberosTime)obj).microSeconds;
    }

    public int hashCode() {
        int result = 37 * 17 + (int)(kerberosTime ^ (kerberosTime >>> 32));
        return result * 17 + microSeconds;
    }

    public boolean isZero() {
        return kerberosTime == 0 && microSeconds == 0;
    }

    public int getSeconds() {
        Long temp_long = new Long(kerberosTime / 1000L);
        return temp_long.intValue();
    }

    public void setSeconds(int sec) {
        Integer temp_int = new Integer(sec);
        kerberosTime = temp_int.longValue() * 1000L;
    }

    /**
     * Parse (unmarshal) a kerberostime from a DER input stream.  This form
     * parsing might be used when expanding a value which is part of
     * a constructed sequence and uses explicitly tagged type.
     *
     * @exception Asn1Exception on error.
     * @param data the Der input stream value, which contains one or more marshaled value.
     * @param explicitTag tag number.
     * @param optional indicates if this data field is optional
     * @return an instance of KerberosTime.
     *
     */
    public static KerberosTime parse(DerInputStream data, byte explicitTag, boolean optional) throws Asn1Exception, IOException {
        if ((optional) && (((byte)data.peekByte() & (byte)0x1F)!= explicitTag))
            return null;
        DerValue der = data.getDerValue();
        if (explicitTag != (der.getTag() & (byte)0x1F))  {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
        else {
            DerValue subDer = der.getData().getDerValue();
            return new KerberosTime(subDer);
        }
    }

    public static int getDefaultSkew() {
        int tdiff = Krb5.DEFAULT_ALLOWABLE_CLOCKSKEW;
        try {
            Config c = Config.getInstance();
            if ((tdiff = c.getDefaultIntValue("clockskew",
                                              "libdefaults")) == Integer.MIN_VALUE) {   //value is not defined
                tdiff = Krb5.DEFAULT_ALLOWABLE_CLOCKSKEW;
            }
        } catch (KrbException e) {
            if (DEBUG) {
                System.out.println("Exception in getting clockskew from " +
                                   "Configuration " +
                                   "using default value " +
                                   e.getMessage());
            }
        }
        return tdiff;
    }

    public String toString() {
        return toGeneralizedTimeString();
    }
}
