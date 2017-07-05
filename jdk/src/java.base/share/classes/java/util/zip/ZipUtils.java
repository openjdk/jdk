/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

class ZipUtils {

    // used to adjust values between Windows and java epoch
    private static final long WINDOWS_EPOCH_IN_MICROSECONDS = -11644473600000000L;

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
        LocalDateTime ldt = LocalDateTime.of(
                (int) (((dtime >> 25) & 0x7f) + 1980),
                (int) ((dtime >> 21) & 0x0f),
                (int) ((dtime >> 16) & 0x1f),
                (int) ((dtime >> 11) & 0x1f),
                (int) ((dtime >> 5) & 0x3f),
                (int) ((dtime << 1) & 0x3e));
        return TimeUnit.MILLISECONDS.convert(ldt.toEpochSecond(
                ZoneId.systemDefault().getRules().getOffset(ldt)), TimeUnit.SECONDS);
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
    private static long javaToDosTime(long time) {
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime ldt = LocalDateTime.ofInstant(
                instant, ZoneId.systemDefault());
        int year = ldt.getYear() - 1980;
        if (year < 0) {
            return (1 << 21) | (1 << 16);
        }
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
    public static long javaToExtendedDosTime(long time) {
        if (time < 0) {
            return ZipEntry.DOSTIME_BEFORE_1980;
        }
        long dostime = javaToDosTime(time);
        return (dostime != ZipEntry.DOSTIME_BEFORE_1980)
                ? dostime + ((time % 2000) << 32)
                : ZipEntry.DOSTIME_BEFORE_1980;
    }

    /**
     * Fetches unsigned 16-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     */
    public static final int get16(byte b[], int off) {
        return Byte.toUnsignedInt(b[off]) | (Byte.toUnsignedInt(b[off+1]) << 8);
    }

    /**
     * Fetches unsigned 32-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     */
    public static final long get32(byte b[], int off) {
        return (get16(b, off) | ((long)get16(b, off+2) << 16)) & 0xffffffffL;
    }

    /**
     * Fetches signed 64-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     */
    public static final long get64(byte b[], int off) {
        return get32(b, off) | (get32(b, off+4) << 32);
    }

    /**
     * Fetches signed 32-bit value from byte array at specified offset.
     * The bytes are assumed to be in Intel (little-endian) byte order.
     *
     */
    public static final int get32S(byte b[], int off) {
        return (get16(b, off) | (get16(b, off+2) << 16));
    }
}
