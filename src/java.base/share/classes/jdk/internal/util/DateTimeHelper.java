/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Helper for java.time
 */
public final class DateTimeHelper {
    private DateTimeHelper() {
    }
    /**
     * Prints the toString result to the given buf, avoiding extra string allocations.
     */
    public static void formatTo(StringBuilder buf, LocalDateTime dateTime) {
        DateTimeHelper.formatTo(buf, dateTime.toLocalDate());
        buf.append('T');
        DateTimeHelper.formatTo(buf, dateTime.toLocalTime());
    }

    /**
     * Prints the toString result to the given buf, avoiding extra string allocations.
     * Requires extra capacity of 10 to avoid StringBuilder reallocation.
     */
    public static void formatTo(StringBuilder buf, LocalDate date) {
        int year    = date.getYear(),
            absYear = Math.abs(year);
        if (absYear < 10000) {
            if (year < 0) {
                buf.append('-');
            }
            DecimalDigits.appendQuad(buf, absYear);
        } else {
            if (year > 9999) {
                buf.append('+');
            }
            buf.append(year);
        }
        buf.append('-');
        DecimalDigits.appendPair(buf, date.getMonthValue());
        buf.append('-');
        DecimalDigits.appendPair(buf, date.getDayOfMonth());
    }

    /**
     * Prints the toString result to the given buf, avoiding extra string allocations.
     * Requires extra capacity of 18 to avoid StringBuilder reallocation.
     */
    public static void formatTo(StringBuilder buf, LocalTime time) {
        DecimalDigits.appendPair(buf, time.getHour());
        buf.append(':');
        DecimalDigits.appendPair(buf, time.getMinute());
        int second = time.getSecond(),
            nano   = time.getNano();
        if ((second | nano) > 0) {
            buf.append(':');
            DecimalDigits.appendPair(buf, second);
            if (nano > 0) {
                buf.append('.');
                int zeros = 9 - DecimalDigits.stringSize(nano);
                if (zeros > 0) {
                    buf.repeat('0', zeros);
                }
                int digits;
                if (nano % 1_000_000 == 0) {
                    digits = nano / 1_000_000;
                } else if (nano % 1000 == 0) {
                    digits = nano / 1000;
                } else {
                    digits = nano;
                }
                buf.append(digits);
            }
        }
    }
}
