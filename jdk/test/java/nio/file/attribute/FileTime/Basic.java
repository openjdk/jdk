/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6844313
 * @summary Unit test for java.nio.file.FileTime
 */

import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.*;
import java.util.Random;

public class Basic {

    static final Random rand = new Random();

    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        long tomorrowInDays = TimeUnit.DAYS.convert(now, MILLISECONDS) + 1;
        long yesterdayInDays = TimeUnit.DAYS.convert(now, MILLISECONDS) - 1;

        // equals
        eq(now, MILLISECONDS, now, MILLISECONDS);
        eq(now, MILLISECONDS, now*1000L, MICROSECONDS);
        neq(now, MILLISECONDS, 0, MILLISECONDS);
        neq(now, MILLISECONDS, 0, MICROSECONDS);

        // compareTo
        cmp(now, MILLISECONDS, now, MILLISECONDS, 0);
        cmp(now, MILLISECONDS, now*1000L, MICROSECONDS, 0);
        cmp(now, MILLISECONDS, now-1234, MILLISECONDS, 1);
        cmp(now, MILLISECONDS, now+1234, MILLISECONDS, -1);
        cmp(tomorrowInDays, DAYS, now, MILLISECONDS, 1);
        cmp(now, MILLISECONDS, tomorrowInDays, DAYS, -1);
        cmp(yesterdayInDays, DAYS, now, MILLISECONDS, -1);
        cmp(now, MILLISECONDS, yesterdayInDays, DAYS, 1);
        cmp(yesterdayInDays, DAYS, now, MILLISECONDS, -1);
        cmp(Long.MAX_VALUE, DAYS, Long.MAX_VALUE, NANOSECONDS, 1);
        cmp(Long.MAX_VALUE, DAYS, Long.MIN_VALUE, NANOSECONDS, 1);
        cmp(Long.MIN_VALUE, DAYS, Long.MIN_VALUE, NANOSECONDS, -1);
        cmp(Long.MIN_VALUE, DAYS, Long.MAX_VALUE, NANOSECONDS, -1);

        // to(TimeUnit)
        to(MILLISECONDS.convert(1, DAYS) - 1, MILLISECONDS);
        to(MILLISECONDS.convert(1, DAYS) + 0, MILLISECONDS);
        to(MILLISECONDS.convert(1, DAYS) + 1, MILLISECONDS);
        to(1, MILLISECONDS);
        to(0, MILLISECONDS);
        to(1, MILLISECONDS);
        to(MILLISECONDS.convert(-1, DAYS) - 1, MILLISECONDS);
        to(MILLISECONDS.convert(-1, DAYS) + 0, MILLISECONDS);
        to(MILLISECONDS.convert(-1, DAYS) + 1, MILLISECONDS);
        for (TimeUnit unit: TimeUnit.values()) {
            for (int i=0; i<100; i++) { to(rand.nextLong(), unit); }
            to(Long.MIN_VALUE, unit);
            to(Long.MAX_VALUE, unit);
        }

        // toString
        ts(1L, DAYS, "1970-01-02T00:00:00Z");
        ts(1L, HOURS, "1970-01-01T01:00:00Z");
        ts(1L, MINUTES, "1970-01-01T00:01:00Z");
        ts(1L, SECONDS, "1970-01-01T00:00:01Z");
        ts(1L, MILLISECONDS, "1970-01-01T00:00:00.001Z");
        ts(1L, MICROSECONDS, "1970-01-01T00:00:00.000001Z");
        ts(1L, NANOSECONDS, "1970-01-01T00:00:00.000000001Z");
        ts(999999999L, NANOSECONDS, "1970-01-01T00:00:00.999999999Z");
        ts(9999999999L, NANOSECONDS, "1970-01-01T00:00:09.999999999Z");

        ts(-1L, DAYS, "1969-12-31T00:00:00Z");
        ts(-1L, HOURS, "1969-12-31T23:00:00Z");
        ts(-1L, MINUTES, "1969-12-31T23:59:00Z");
        ts(-1L, SECONDS, "1969-12-31T23:59:59Z");
        ts(-1L, MILLISECONDS, "1969-12-31T23:59:59.999Z");
        ts(-1L, MICROSECONDS, "1969-12-31T23:59:59.999999Z");
        ts(-1L, NANOSECONDS, "1969-12-31T23:59:59.999999999Z");
        ts(-999999999L, NANOSECONDS, "1969-12-31T23:59:59.000000001Z");
        ts(-9999999999L, NANOSECONDS, "1969-12-31T23:59:50.000000001Z");

        ts(-62135596799999L, MILLISECONDS, "0001-01-01T00:00:00.001Z");
        ts(-62135596800000L, MILLISECONDS, "0001-01-01T00:00:00Z");
        ts(-62135596800001L, MILLISECONDS, "-0001-12-31T23:59:59.999Z");

        ts(253402300799999L, MILLISECONDS, "9999-12-31T23:59:59.999Z");
        ts(-377642044800001L, MILLISECONDS, "-9999-12-31T23:59:59.999Z");

        // NTFS epoch in usec.
        ts(-11644473600000000L, MICROSECONDS, "1601-01-01T00:00:00Z");

        // nulls
        try {
            FileTime.from(0L, null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
        FileTime time = FileTime.fromMillis(now);
        if (time.equals(null))
            throw new RuntimeException("should not be equal to null");
        try {
            time.compareTo(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
    }

    static void cmp(long v1, TimeUnit u1, long v2, TimeUnit u2, int expected) {
        int result = FileTime.from(v1, u1).compareTo(FileTime.from(v2, u2));
        if (result != expected)
            throw new RuntimeException("unexpected order");
    }

    static void eq(long v1, TimeUnit u1, long v2, TimeUnit u2) {
        FileTime t1 = FileTime.from(v1, u1);
        FileTime t2 = FileTime.from(v2, u2);
        if (!t1.equals(t2))
            throw new RuntimeException("not equal");
        if (t1.hashCode() != t2.hashCode())
            throw new RuntimeException("hashCodes should be equal");
    }

    static void neq(long v1, TimeUnit u1, long v2, TimeUnit u2) {
        FileTime t1 = FileTime.from(v1, u1);
        FileTime t2 = FileTime.from(v2, u2);
        if (t1.equals(t2))
            throw new RuntimeException("should not be equal");
    }

    static void to(long v, TimeUnit unit) {
        FileTime t = FileTime.from(v, unit);
        for (TimeUnit u: TimeUnit.values()) {
            long result = t.to(u);
            long expected = u.convert(v, unit);
            if (result != expected) {
                throw new RuntimeException("unexpected result");
            }
        }
    }

    static void ts(long v, TimeUnit unit, String expected) {
        String result = FileTime.from(v, unit).toString();
        if (!result.equals(expected)) {
            System.err.format("FileTime.from(%d, %s).toString() failed\n", v, unit);
            System.err.format("Expected: %s\n", expected);
            System.err.format("     Got: %s\n", result);
            throw new RuntimeException();
        }
    }
}
