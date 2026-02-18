/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package sql;

/*
 * @test
 * @bug 8007520
 * @summary Test those bridge methods to/from java.time date/time classes
 * @key randomness
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.List;
import java.util.Random;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavatimeTest {

    private static final int NANOS_PER_SECOND = 1000000000;
    private static final long t1970 = new java.util.Date(70, 0, 01).getTime();
    private static final Random R = new Random();
    // Data provider contains 10,000 randomized arguments
    // which are used as the dates and times for the tests
    private static final List<Arguments> DATE_TIME_ARGS = IntStream.range(0, 10_000)
            .mapToObj(i -> {
                int days = R.nextInt(50) * 365 + R.nextInt(365);
                long secs = t1970 + days * 86400 + R.nextInt(86400);
                int nanos = R.nextInt(NANOS_PER_SECOND);
                int nanos_ms = nanos / 1000000 * 1000000; // millis precision
                long millis = secs * 1000 + R.nextInt(1000);
                LocalDateTime ldt = LocalDateTime.ofEpochSecond(secs, nanos, ZoneOffset.UTC);
                return Arguments.of(millis, nanos, ldt, secs, nanos_ms);
            }).toList();

    @ParameterizedTest(autoCloseArguments = false)
    @FieldSource("DATE_TIME_ARGS")
    void timestampTest(long millis, int nanos, LocalDateTime ldt, long secs) {
        Timestamp ta = new Timestamp(millis);
        ta.setNanos(nanos);
        assertTrue(isEqual(ta.toLocalDateTime(), ta),
                errMsg("j.s.ts -> ldt", millis, nanos, ldt, results(ta.toLocalDateTime(), ta)));

        assertTrue(isEqual(ldt, Timestamp.valueOf(ldt)),
                errMsg("ldt -> j.s.ts", millis, nanos, ldt, results(ldt, Timestamp.valueOf(ldt))));

        Instant inst0 = ta.toInstant();
        assertAll(errMsg("j.s.ts -> instant -> j.s.ts", millis, nanos, ldt),
                () -> assertEquals(ta.getTime(), inst0.toEpochMilli()),
                () -> assertEquals(ta.getNanos(), inst0.getNano()),
                () -> assertEquals(ta, Timestamp.from(inst0))
        );

        Instant inst = Instant.ofEpochSecond(secs, nanos);
        Timestamp ta0 = Timestamp.from(inst);
        assertAll(errMsg("instant -> timestamp -> instant", millis, nanos, ldt),
                () -> assertEquals(ta0.getTime(), inst.toEpochMilli()),
                () -> assertEquals(ta0.getNanos(), inst.getNano()),
                () -> assertEquals(inst, ta0.toInstant())
        );
    }

    @ParameterizedTest(autoCloseArguments = false)
    @FieldSource("DATE_TIME_ARGS")
    void sqlDateTest(long millis, int nanos, LocalDateTime ldt) {
        // j.s.d/t uses j.u.d.equals() !!!!!!!!
        java.sql.Date jsd = new java.sql.Date(millis);
        assertTrue(isEqual(jsd.toLocalDate(), jsd),
                errMsg("j.s.d -> ld", millis, nanos, ldt, results(jsd.toLocalDate(), jsd)));

        LocalDate ld = ldt.toLocalDate();
        assertTrue(isEqual(ld, java.sql.Date.valueOf(ld)),
                errMsg("ld -> j.s.d", millis, nanos, ldt, results(ld, java.sql.Date.valueOf(ld))));
    }

    @ParameterizedTest(autoCloseArguments = false)
    @FieldSource("DATE_TIME_ARGS")
    void sqlTimeTest(long millis, int nanos, LocalDateTime ldt, long secs, int nanos_ms) {
        java.sql.Time jst = new java.sql.Time(millis);
        assertTrue(isEqual(jst.toLocalTime(), jst),
                errMsg("j.s.t -> lt", millis, nanos, ldt, results(jst.toLocalTime(), jst)));

        // millis precision
        LocalDateTime ldt_ms = LocalDateTime.ofEpochSecond(secs, nanos_ms, ZoneOffset.UTC);
        LocalTime lt = ldt_ms.toLocalTime();
        assertTrue(isEqual(lt, java.sql.Time.valueOf(lt)),
                errMsg("lt -> j.s.t", millis, nanos, ldt, results(lt, java.sql.Time.valueOf(lt))));
    }

    private static boolean isEqual(LocalDateTime ldt, Timestamp ts) {
        ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
        return zdt.getYear() == ts.getYear() + 1900 &&
               zdt.getMonthValue() == ts.getMonth() + 1 &&
               zdt.getDayOfMonth() == ts.getDate() &&
               zdt.getHour() == ts.getHours() &&
               zdt.getMinute() == ts.getMinutes() &&
               zdt.getSecond() == ts.getSeconds() &&
               zdt.getNano() == ts.getNanos();
    }

    private static String results(LocalDateTime ldt, Timestamp ts) {
        ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
        return "ldt:ts  %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, nano:[%d/%d]%n".formatted(
               zdt.getYear(), ts.getYear() + 1900,
               zdt.getMonthValue(), ts.getMonth() + 1,
               zdt.getDayOfMonth(), ts.getDate(),
               zdt.getHour(), ts.getHours(),
               zdt.getMinute(), ts.getMinutes(),
               zdt.getSecond(), ts.getSeconds(),
               zdt.getNano(), ts.getNanos());
    }

    private static boolean isEqual(LocalDate ld, java.sql.Date d) {
        return ld.getYear() == d.getYear() + 1900 &&
               ld.getMonthValue() == d.getMonth() + 1 &&
               ld.getDayOfMonth() == d.getDate();
    }

    private static String results(LocalDate ld, java.sql.Date d) {
        return "%d/%d, %d/%d, %d/%d%n".formatted(
               ld.getYear(), d.getYear() + 1900,
               ld.getMonthValue(), d.getMonth() + 1,
               ld.getDayOfMonth(), d.getDate());
    }

    private static boolean isEqual(LocalTime lt, java.sql.Time t) {
        return lt.getHour() == t.getHours() &&
               lt.getMinute() == t.getMinutes() &&
               lt.getSecond() == t.getSeconds();
    }

    private static String results(LocalTime lt, java.sql.Time t) {
        return "%d/%d, %d/%d, %d/%d%n".formatted(
                lt.getHour(), t.getHours(),
                lt.getMinute(), t.getMinutes(),
                lt.getSecond(), t.getSeconds());
    }

    private static String errMsg(String testCase, long millis, int nanos,
                                 LocalDateTime ldt, String results) {
        return "FAILED: %s%n INPUTS: ms: %16d  ns: %10d  ldt:[%s]%n ACTUAL: %s"
                .formatted(testCase, millis, nanos, ldt, results);
    }

    private static String errMsg(String testCase, long millis, int nanos,
                                 LocalDateTime ldt) {
        return "FAILED: %s%n INPUTS: ms: %16d  ns: %10d  ldt:[%s]%n"
                .formatted(testCase, millis, nanos, ldt);
    }
}