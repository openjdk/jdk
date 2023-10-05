/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import java.lang.invoke.MethodHandle;
import java.time.*;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaUtilDateAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.Stable;

import sun.util.calendar.BaseCalendar;

/**
 * DateTimeUtils
 *
 * @since 21
 */
public final class DateTimeUtils {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaUtilDateAccess JDA = SharedSecrets.getJavaUtilDateAccess();

    /**
     * Constructor.
     */
    private DateTimeUtils() {
    }

    /**
     * Hours per day.
     */
    static final int HOURS_PER_DAY = 24;
    /**
     * Minutes per hour.
     */
    static final int MINUTES_PER_HOUR = 60;
    /**
     * Minutes per day.
     */
    static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY;
    /**
     * Seconds per minute.
     */
    static final int SECONDS_PER_MINUTE = 60;
    /**
     * Seconds per hour.
     */
    static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;
    /**
     * Seconds per day.
     */
    public static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;

    public static int yearSize(int year) {
        if (Math.abs(year) < 1000) {
            return year < 0 ? 5 : 4;
        }
        return JLA.stringSize(year) + (year > 9999 ? 1 : 0);
    }

    public static int nanoSize(int nano) {
        if (nano == 0) {
            return 0;
        }

        int div = nano / 1000;
        int div2 = div / 1000;

        if (nano - div * 1000 != 0) {
            return 10;
        }

        return (div - div2 * 1000 == 0) ? 4 : 7;
    }

    public static void getCharsLatin1(byte[] buf, int off, LocalDate value) {
        getDateCharsLatin1(buf, off, value.getYear(), value.getMonthValue(), value.getDayOfMonth());
    }

    public static void getCharsUTF16(byte[] buf, int off, LocalDate value) {
        getDateCharsUTF16(buf, off, value.getYear(), value.getMonthValue(), value.getDayOfMonth());
    }

    public static void getDateCharsLatin1(byte[] buf, int off, int year, int month, int dayOfMonth) {
        int yearSize = yearSize(year);
        int yearAbs = Math.abs(year);

        int yearEnd = off + yearSize;
        if (yearAbs < 1000) {
            if (year < 0) {
                buf[off++] = '-';
            }
            int y01 = yearAbs / 100;
            int y23 = yearAbs - y01 * 100;

            JLA.writeDigitPairLatin1(buf, off, y01);
            JLA.writeDigitPairLatin1(buf, off + 2, y23);
        } else {
            if (year > 9999) {
                buf[off] = '+';
            }
            JLA.getCharsLatin1(year, yearEnd, buf);
        }

        off = yearEnd;
        buf[off] = '-';
        JLA.writeDigitPairLatin1(buf, off + 1, month); // mm
        buf[off + 3] = '-';
        JLA.writeDigitPairLatin1(buf, off + 4, dayOfMonth); // dd
    }

    public static void getDateCharsUTF16(byte[] buf, int off, int year, int month, int dayOfMonth) {
        int yearSize = yearSize(year);
        int yearAbs = Math.abs(year);

        int yearEnd = off + yearSize;
        if (yearAbs < 1000) {
            if (year < 0) {
                JLA.putCharUTF16(buf, off++, '-');
            }
            int y01 = yearAbs / 100;
            int y23 = yearAbs - y01 * 100;

            JLA.writeDigitPairUTF16(buf, off, y01);
            JLA.writeDigitPairUTF16(buf, off + 2, y23);
        } else {
            if (year > 9999) {
                JLA.putCharUTF16(buf, off, '+');
            }
            JLA.getCharsUTF16(year, yearEnd, buf);
        }

        off = yearEnd;
        JLA.putCharUTF16(buf, off, '-');
        JLA.writeDigitPairUTF16(buf, off + 1, month);
        JLA.putCharUTF16(buf, off + 3, '-');
        JLA.writeDigitPairUTF16(buf, off + 4, dayOfMonth);
    }

    public static void getCharsLatin1(byte[] buf, int off, LocalTime value) {
        getLocalTimeCharsLatin1(buf, off, value.getHour(), value.getMinute(), value.getSecond());
        getNanoCharsLatin1(buf, off + 8, value.getNano());
    }

    public static void getLocalTimeCharsLatin1(byte[] buf, int off, int hour, int minute, int second) {
        JLA.writeDigitPairLatin1(buf, off, hour);
        buf[off + 2] = ':';
        JLA.writeDigitPairLatin1(buf, off + 3, minute);
        buf[off + 5] = ':';
        JLA.writeDigitPairLatin1(buf, off + 6, second);
    }

    public static void getCharsUTF16(byte[] buf, int off, LocalTime value) {
        getLocalTimeCharsUTF16(buf, off, value.getHour(), value.getMinute(), value.getSecond());
        getNanoCharsUTF16(buf, off + 8, value.getNano());
    }

    public static void getLocalTimeCharsUTF16(byte[] buf, int off, int hour, int minute, int second) {
        JLA.writeDigitPairUTF16(buf, off, hour);
        JLA.putCharUTF16(buf, off + 2, ':');
        JLA.writeDigitPairUTF16(buf, off + 3, minute);
        JLA.putCharUTF16(buf, off + 5, ':');
        JLA.writeDigitPairUTF16(buf, off + 6, second);
    }

    public static void getCharsLatin1(byte[] buf, int off, Instant value) {
        long epochSecond = value.getEpochSecond();
        LocalDate date = LocalDate.ofEpochDay(Math.floorDiv(epochSecond, SECONDS_PER_DAY));
        LocalTime time = LocalTime.ofSecondOfDay(Math.floorMod(epochSecond, SECONDS_PER_DAY));
        int nano = value.getNano();
        int dateSize = stringSize(date);
        int timeSize = 8;
        getCharsLatin1(buf, off, date);
        off += dateSize;
        buf[off++] = 'T';
        getLocalTimeCharsLatin1(buf, off, time.getHour(), time.getMinute(), time.getSecond());
        off += timeSize;
        getNanoCharsLatin1(buf, off, nano);
        buf[off + nanoSize(nano)] = 'Z';
    }

    public static void getCharsUTF16(byte[] buf, int off, Instant value) {
        long epochSecond = value.getEpochSecond();
        LocalDate date = LocalDate.ofEpochDay(Math.floorDiv(epochSecond, SECONDS_PER_DAY));
        LocalTime time = LocalTime.ofSecondOfDay(Math.floorMod(epochSecond, SECONDS_PER_DAY));
        int nano = value.getNano();
        int dateSize = stringSize(date);
        int timeSize = 8;
        getCharsUTF16(buf, off, date);
        off += dateSize;
        JLA.putCharUTF16(buf, off++, 'T');
        getLocalTimeCharsUTF16(buf, off, time.getHour(), time.getMinute(), time.getSecond());
        off += timeSize;
        getNanoCharsUTF16(buf, off, nano);
        JLA.putCharUTF16(buf, off + nanoSize(nano), 'Z');
    }

    private static void getNanoCharsLatin1(byte[] buf, int off, int nano) {
        if (nano == 0) {
            return;
        }

        int div = nano / 1000;
        int div2 = div / 1000;

        int div2_k = div2 / 100;
        buf[off] = '.';
        buf[off + 1] = (byte) ('0' + div2_k);
        JLA.writeDigitPairLatin1(buf, off + 2, div2 - div2_k * 100);
        off += 4;

        int rem1 = nano - div * 1000;
        int rem2;
        if (rem1 == 0) {
            rem2 = div - div2 * 1000;
            if (rem2 == 0) {
                return;
            }
        } else {
            rem2 = div - div2 * 1000;
        }

        int rem2_k = rem2 / 100;
        buf[off] = (byte) ('0' + rem2_k);
        JLA.writeDigitPairLatin1(buf, off + 1, rem2 - rem2_k * 100);
        off += 3;

        if (rem1 != 0) {
            int rem1_k = rem1 / 100;
            buf[off] = (byte) ('0' + rem1_k);
            JLA.writeDigitPairLatin1(buf, off + 1, rem1 - rem1_k * 100);
        }
    }

    private static void getNanoCharsUTF16(byte[] buf, int off, int nano) {
        if (nano == 0) {
            return;
        }

        int div = nano / 1000;
        int div2 = div / 1000;

        int div2_k = div2 / 100;
        JLA.putCharUTF16(buf, off, '.');
        JLA.putCharUTF16(buf, off + 1, '0' + div2_k);
        JLA.writeDigitPairUTF16(buf, off + 2, div2 - div2_k * 100);
        off += 4;

        int rem1 = nano - div * 1000;
        int rem2;
        if (rem1 == 0) {
            rem2 = div - div2 * 1000;
            if (rem2 == 0) {
                return;
            }
        } else {
            rem2 = div - div2 * 1000;
        }

        int rem2_k = rem2 / 100;
        JLA.putCharUTF16(buf, off, '0' + rem2_k);
        JLA.writeDigitPairUTF16(buf, off + 1, rem2 - rem2_k * 100);
        off += 3;

        if (rem1 != 0) {
            int rem1_k = rem1 / 100;
            JLA.putCharUTF16(buf, off, '0' + rem1_k);
            JLA.writeDigitPairUTF16(buf, off + 1, rem1 - rem1_k * 100);
        }
    }

    public static int stringSize(LocalDate value) {
        return yearSize(value.getYear()) + 6;
    }

    public static int stringSize(LocalTime value) {
        return nanoSize(value.getNano()) + 8;
    }

    public static int stringSize(LocalDateTime value) {
        return yearSize(value.getYear()) + nanoSize(value.getNano()) + 15;
    }

    public static int stringSize(OffsetDateTime value) {
        return stringSize(value.toLocalDateTime()) + value.getOffset().getId().length();
    }

    public static int stringSize(OffsetTime value) {
        return stringSize(value.toLocalTime()) + value.getOffset().getId().length();
    }

    public static int stringSize(ZonedDateTime value) {
        ZoneOffset offset = value.getOffset();
        int size = stringSize(value.toLocalDateTime()) + offset.getId().length();
        ZoneId zone = value.getZone();
        if (offset != zone) {
            size += zone.toString().length() + 2;
        }
        return size;
    }

    public static int stringSize(Instant value) {
        LocalDate localDate = LocalDate.ofEpochDay(Math.floorDiv(value.getEpochSecond(), SECONDS_PER_DAY));
        return yearSize(localDate.getYear()) + 16 + nanoSize(value.getNano());
    }

    public static void getCharsLatin1(byte[] buf, int off, LocalDateTime value) {
        int dateSize = yearSize(value.getYear()) + 6;
        int nanoSize = nanoSize(value.getNano());

        getCharsLatin1(buf, off, value.toLocalDate());
        buf[off + dateSize] = 'T';
        getCharsLatin1(buf, off + dateSize + 1, value.toLocalTime());
    }

    public static void getCharsUTF16(byte[] buf, int off, LocalDateTime value) {
        int dateSize = yearSize(value.getYear()) + 6;
        int nanoSize = nanoSize(value.getNano());

        getCharsUTF16(buf, off, value.toLocalDate());
        JLA.putCharUTF16(buf, off + dateSize, 'T');
        getCharsUTF16(buf, off + dateSize + 1, value.toLocalTime());
    }

    private static final byte[] wtb = {
            // weeks
            'S', 'u', 'n',
            'M', 'o', 'n',
            'T', 'u', 'e',
            'W', 'e', 'd',
            'T', 'h', 'u',
            'F', 'r', 'i',
            'S', 'a', 't',

            // months
            'J', 'a', 'n',
            'F', 'e', 'b',
            'M', 'a', 'r',
            'A', 'p', 'r',
            'M', 'a', 'y',
            'J', 'u', 'n',
            'J', 'u', 'l',
            'A', 'u', 'g',
            'S', 'e', 'p',
            'O', 'c', 't',
            'N', 'o', 'v',
            'D', 'e', 'c',
    };

    public static int yearSize(BaseCalendar.Date date) {
        return yearSize(
                date.getYear());
    }

    public static int stringSize(Date date) {
        return stringSize(
                JDA.normalize(date));
    }

    public static int stringSize(BaseCalendar.Date date) {
        return JLA.stringSize(date.getYear()) + 24;
    }

    public static void getCharsLatin1(byte[] buf, int off, Date date) {
        getCharsLatin1(buf, off, JDA.normalize(date));
    }

    public static void getCharsUTF16(byte[] buf, int off, Date date) {
        getCharsUTF16(buf, off, JDA.normalize(date));
    }

    public static void getCharsLatin1(byte[] buf, int off, BaseCalendar.Date date) {
        // EEE
        int weekIndex = (date.getDayOfWeek() - 1) * 3;
        buf[off] = wtb[weekIndex];
        buf[off + 1] = wtb[weekIndex + 1];
        buf[off + 2] = wtb[weekIndex + 2];

        buf[off + 3] = ' ';

        // MMM
        int monthIndex = (date.getMonth() + 6) * 3;
        buf[off + 4] = wtb[monthIndex];
        buf[off + 5] = wtb[monthIndex + 1];
        buf[off + 6] = wtb[monthIndex + 2];

        buf[off + 7] = ' ';
        JLA.writeDigitPairLatin1(buf, off + 8, date.getDayOfMonth()); // dd
        buf[off + 10] = ' ';
        JLA.writeDigitPairLatin1(buf, off + 11, date.getHours()); // HH
        buf[off + 13] = ':';
        JLA.writeDigitPairLatin1(buf, off + 14, date.getMinutes()); // mm
        buf[off + 16] = ':';
        JLA.writeDigitPairLatin1(buf, off + 17, date.getSeconds()); // ss
        buf[off + 19] = ' ';

        TimeZone zi = date.getZone();
        String shortName = zi != null ? zi.getDisplayName(date.isDaylightTime(), TimeZone.SHORT, Locale.US) : "GMT";
        buf[off + 20] = (byte) shortName.charAt(0);
        buf[off + 21] = (byte) shortName.charAt(1);
        buf[off + 22] = (byte) shortName.charAt(2);
        buf[off + 23] = ' ';

        int year = date.getYear();
        int yearSize = JLA.stringSize(year);
        JLA.getCharsLatin1(year, off + 24 + yearSize, buf);
    }

    public static void getCharsUTF16(byte[] buf, int off, BaseCalendar.Date date) {
        // EEE
        int weekIndex = (date.getDayOfWeek() - 1) * 3;
        JLA.putCharUTF16(buf, off, wtb[weekIndex]);
        JLA.putCharUTF16(buf, off + 1, wtb[weekIndex + 1]);
        JLA.putCharUTF16(buf, off + 2, wtb[weekIndex + 2]);

        JLA.putCharUTF16(buf, off + 3, ' ');

        // MMM
        int monthIndex = (date.getMonth() + 6) * 3;
        JLA.putCharUTF16(buf, off + 4, wtb[monthIndex]);
        JLA.putCharUTF16(buf, off + 5, wtb[monthIndex + 1]);
        JLA.putCharUTF16(buf, off + 6, wtb[monthIndex + 2]);

        JLA.putCharUTF16(buf, off + 7, ' ');
        JLA.writeDigitPairUTF16(buf, off + 8, date.getDayOfMonth()); // dd
        JLA.putCharUTF16(buf, off + 10, ' ');
        JLA.writeDigitPairUTF16(buf, off + 11, date.getHours()); // HH
        JLA.putCharUTF16(buf, off + 13, ':');
        JLA.writeDigitPairUTF16(buf, off + 14, date.getMinutes()); // mm
        JLA.putCharUTF16(buf, off + 16, ':');
        JLA.writeDigitPairUTF16(buf, off + 17, date.getSeconds()); // ss
        JLA.putCharUTF16(buf, off + 19, ' ');

        TimeZone zi = date.getZone();
        String shortName = zi != null ? zi.getDisplayName(date.isDaylightTime(), TimeZone.SHORT, Locale.US) : "GMT";
        JLA.putCharUTF16(buf, off + 20, shortName.charAt(0));
        JLA.putCharUTF16(buf, off + 21, shortName.charAt(1));
        JLA.putCharUTF16(buf, off + 22, shortName.charAt(2));
        JLA.putCharUTF16(buf, off + 23, ' ');

        int year = date.getYear();
        int yearSize = JLA.stringSize(year);
        JLA.getCharsUTF16(year, off + 24 + yearSize, buf);
    }

    public static void getDateCharsLatin1(byte[] buf, int off, BaseCalendar.Date date) {
        int year = date.getYear();
        int month = date.getMonth();
        int dayOfMonth = date.getDayOfMonth();

        int yearSize = JLA.stringSize(year);
        int yearEnd = off + Math.max(yearSize, 4);
        for (int i = 0; i < 4 - yearSize; ++i) {
            buf[off + i] = '0';
        }
        JLA.getCharsLatin1(year, yearEnd, buf);
        off = yearEnd;
        buf[off] = '-';
        JLA.writeDigitPairLatin1(buf, off + 1, month); // mm
        buf[off + 3] = '-';
        JLA.writeDigitPairLatin1(buf, off + 4, dayOfMonth); // dd
    }

    public static void getDateCharsUTF16(byte[] buf, int off, BaseCalendar.Date date) {
        int year = date.getYear();
        int month = date.getMonth();
        int dayOfMonth = date.getDayOfMonth();

        int yearSize = JLA.stringSize(year);
        int yearEnd = off + Math.max(yearSize, 4);
        for (int i = 0; i < 4 - yearSize; ++i) {
            JLA.putCharUTF16(buf, off, '0');
        }
        JLA.getCharsUTF16(year, yearEnd, buf);
        off = yearEnd;
        JLA.putCharUTF16(buf, off, '-');
        JLA.writeDigitPairUTF16(buf, off + 1, month);
        JLA.putCharUTF16(buf, off + 3, '-');
        JLA.writeDigitPairUTF16(buf, off + 4, dayOfMonth);
    }
}
