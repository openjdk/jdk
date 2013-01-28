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
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package build.tools.tzdb;

import java.util.Objects;

class Utils {

    // Returns the largest (closest to positive infinity)
    public static long floorDiv(long x, long y) {
        long r = x / y;
        // if the signs are different and modulo not zero, round down
        if ((x ^ y) < 0 && (r * y != x)) {
            r--;
        }
        return r;
    }

    // Returns the floor modulus of the {@code long} arguments.
    public static long floorMod(long x, long y) {
        return x - floorDiv(x, y) * y;
    }

    // Returns the sum of its arguments,
    public static long addExact(long x, long y) {
        long r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    // Year

    // Returns true if the specified year is a leap year.
    public static boolean isLeapYear(int year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }

    // The minimum supported year, '-999,999,999'.
    public static final int YEAR_MIN_VALUE = -999_999_999;

    // The maximum supported year, '+999,999,999'.
    public static final int YEAR_MAX_VALUE = 999_999_999;


    // Gets the length of the specified month in days.
    public static int lengthOfMonth(int month, boolean leapYear) {
        switch (month) {
            case 2:        //FEBRUARY:
                return (leapYear ? 29 : 28);
            case 4:        //APRIL:
            case 6:        //JUNE:
            case 9:        //SEPTEMBER:
            case 11:       //NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    // Gets the maximum length of the specified month in days.
    public static int maxLengthOfMonth(int month) {
        switch (month) {
            case 2:           //FEBRUARY:
                return 29;
            case 4:           //APRIL:
            case 6:           //JUNE:
            case 9:           //SEPTEMBER:
            case 11:          //NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    // DayOfWeek

    // Returns the day-of-week that is the specified number of days after
    // this one, from 1 to 7 for Monday to Sunday.
    public static int plusDayOfWeek(int dow, long days) {
        int amount = (int) (days % 7);
        return (dow - 1 + (amount + 7)) % 7 + 1;
    }

    // Returns the day-of-week that is the specified number of days before
    // this one, from 1 to 7 for Monday to Sunday.
    public static int minusDayOfWeek(int dow, long days) {
        return plusDayOfWeek(dow, -(days % 7));
    }

    // Adjusts the date to the first occurrence of the specified day-of-week
    // before the date being adjusted unless it is already on that day in
    // which case the same object is returned.
    public static LocalDate previousOrSame(LocalDate date, int dayOfWeek) {
        return adjust(date, dayOfWeek, 1);
    }

    // Adjusts the date to the first occurrence of the specified day-of-week
    // after the date being adjusted unless it is already on that day in
    // which case the same object is returned.
    public static LocalDate nextOrSame(LocalDate date, int dayOfWeek) {
        return adjust(date, dayOfWeek, 0);
    }

    // Implementation of next, previous or current day-of-week.
    // @param relative  whether the current date is a valid answer
    private static final LocalDate adjust(LocalDate date, int dow, int relative) {
        int calDow = date.getDayOfWeek();
        if (relative < 2 && calDow == dow) {
            return date;
        }
        if ((relative & 1) == 0) {
            int daysDiff = calDow - dow;
            return date.plusDays(daysDiff >= 0 ? 7 - daysDiff : -daysDiff);
        } else {
            int daysDiff = dow - calDow;
            return date.minusDays(daysDiff >= 0 ? 7 - daysDiff : -daysDiff);
        }
    }

}
