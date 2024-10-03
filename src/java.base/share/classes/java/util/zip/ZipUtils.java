/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.util.zip;

import java.nio.ByteBuffer;
import java.nio.file.attribute.FileTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.util.zip.ZipConstants.ENDHDR;

import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;

class ZipUtils {

    static final JavaNioAccess NIO_ACCESS = SharedSecrets.getJavaNioAccess();

    // used to adjust values between Windows and java epoch
    private static final long WINDOWS_EPOCH_IN_MICROSECONDS = -11644473600000000L;

    // used to indicate the corresponding windows time is not available
    public static final long WINDOWS_TIME_NOT_AVAILABLE = Long.MIN_VALUE;

    // static final ByteBuffer defaultBuf = ByteBuffer.allocateDirect(0);
    static final ByteBuffer defaultBuf = ByteBuffer.allocate(0);

    /**
     * Converts Windows time (in microseconds, UTC/GMT) time to FileTime.
     */
    public static final FileTime winTimeToFileTime(long wtime) {
        return FileTime.from(wtime / 10 + WINDOWS_EPOCH_IN_MICROSECONDS,
                             TimeUnit.MICROSECONDS);
    }

    /**
     * Converts FileTime to Windows time.
     */
    public static final long fileTimeToWinTime(FileTime ftime) {
        return (ftime.to(TimeUnit.MICROSECONDS) - WINDOWS_EPOCH_IN_MICROSECONDS) * 10;
    }

    /**
     * The upper bound of the 32-bit unix time, the "year 2038 problem".
     */
    public static final long UPPER_UNIXTIME_BOUND = 0x7fffffff;

    /**
     * Converts "standard Unix time"(in seconds, UTC/GMT) to FileTime
     */
    public static final FileTime unixTimeToFileTime(long utime) {
        return FileTime.from(utime, TimeUnit.SECONDS);
    }

    /**
     * Converts FileTime to "standard Unix time".
     */
    public static final long fileTimeToUnixTime(FileTime ftime) {
        return ftime.to(TimeUnit.SECONDS);
    }

    /**
     * Converts DOS time to Java time (number of milliseconds since epoch).
     */
    public static long dosToJavaTime(long dtime) {
        int year = (int) (((dtime >> 25) & 0x7f) + 1980);
        int month = (int) ((dtime >> 21) & 0x0f);
        int day = (int) ((dtime >> 16) & 0x1f);
        int hour = (int) ((dtime >> 11) & 0x1f);
        int minute = (int) ((dtime >> 5) & 0x3f);
        int second = (int) ((dtime << 1) & 0x3e);

        if (month > 0 && month < 13 && day > 0 && hour < 24 && minute < 60 && second < 60) {
            try {
                LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
                return TimeUnit.SECONDS.toMillis(ldt.toEpochSecond(
                        ZoneId.systemDefault().getRules().getOffset(ldt)));
            } catch (DateTimeException dte) {
                // ignore
            }
        }
        return overflowDosToJavaTime(year, month, day, hour, minute, second);
    }

    /*
     * Deal with corner cases where an arguably mal-formed DOS time is used
     */
    @SuppressWarnings("deprecation") // Use of Date constructor
    private static long overflowDosToJavaTime(int year, int month, int day,
                                              int hour, int minute, int second) {
        return new Date(year - 1900, month - 1, day, hour, minute, second).getTime();
    }


    /**
     * Converts extended DOS time to Java time, where up to 1999 milliseconds
     * might be encoded into the upper half of the returned long.
     *
     * @param xdostime the extended DOS time value
     * @return milliseconds since epoch
     */
    public static long extendedDosToJavaTime(long xdostime) {
        long time = dosToJavaTime(xdostime);
        return time + (xdostime >> 32);
    }

    /**
     * Converts Java time to DOS time.
     */
    private static long javaToDosTime(LocalDateTime ldt) {
        int year = ldt.getYear() - 1980;
        return (year << 25 |
            ldt.getMonthValue() << 21 |
            ldt.getDayOfMonth() << 16 |
            ldt.getHour() << 11 |
            ldt.getMinute() << 5 |
            ldt.getSecond() >> 1) & 0xffffffffL;
    }

    /**
     * Converts Java time to DOS time, encoding any milliseconds lost
     * in the conversion into the upper half of the returned long.
     *
     * @param time milliseconds since epoch
     * @return DOS time with 2s remainder encoded into upper half
     */
    static long javaToExtendedDosTime(long time) {
        LocalDateTime ldt = javaEpochToLocalDateTime(time);
        if (ldt.getYear() >= 1980) {
            return javaToDosTime(ldt) + ((time % 2000) << 32);
        }
        return ZipEntry.DOSTIME_BEFORE_1980;
    }

    static LocalDateTime javaEpochToLocalDateTime(long time) {
        Instant instant = Instant.ofEpochMilli(time);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * Fetches unsigned 16-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     */
    public static final int get16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    /**
     * Fetches unsigned 32-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     */
    public static final long get32(byte[] b, int off) {
        return (get16(b, off) | ((long)get16(b, off+2) << 16)) & 0xffffffffL;
    }

    /**
     * Fetches signed 64-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     */
    public static final long get64(byte[] b, int off) {
        return get32(b, off) | (get32(b, off+4) << 32);
    }

    /**
     * Fetches signed 32-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     *
     */
    public static final int get32S(byte[] b, int off) {
        return (get16(b, off) | (get16(b, off+2) << 16));
    }

    /*
     * File attribute compatibility types of CEN field "version made by"
     */
    static final int FILE_ATTRIBUTES_UNIX = 3; // Unix

    /*
     * Base values for CEN field "version made by"
     */
    static final int VERSION_MADE_BY_BASE_UNIX = FILE_ATTRIBUTES_UNIX << 8; // Unix

    // Extra central directory header (CEN) fields (rest defined in ZipConstants)
    static final int CENVEM_FA = 5;
    static final int CENATX_PERMS = 40; // posix permission data

    // The END header is followed by a variable length comment of size < 64k.
    static final long END_MAXLEN = 0xFFFF + ENDHDR;
    static final int READBLOCKSZ = 128;

    /**
     * Loads ZIP native library, if not already loaded
     */
    static void loadLibrary() {
        jdk.internal.loader.BootLoader.loadLibrary("zip");
    }

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private static final long byteBufferArrayOffset = unsafe.objectFieldOffset(ByteBuffer.class, "hb");
    private static final long byteBufferOffsetOffset = unsafe.objectFieldOffset(ByteBuffer.class, "offset");

    static byte[] getBufferArray(ByteBuffer byteBuffer) {
        return (byte[]) unsafe.getReference(byteBuffer, byteBufferArrayOffset);
    }

    static int getBufferOffset(ByteBuffer byteBuffer) {
        return unsafe.getInt(byteBuffer, byteBufferOffsetOffset);
    }
}
