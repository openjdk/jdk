/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;

public class Basic {

    public static void main(String[] args) throws IOException {
        long now = System.currentTimeMillis();
        long tomorrowInDays = TimeUnit.DAYS.convert(now, MILLISECONDS) + 1;

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

        // toString
        ts(1L, DAYS, "1970-01-02T00:00:00Z");
        ts(1L, HOURS, "1970-01-01T01:00:00Z");
        ts(1L, MINUTES, "1970-01-01T00:01:00Z");
        ts(1L, SECONDS, "1970-01-01T00:00:01Z");
        ts(1L, MILLISECONDS, "1970-01-01T00:00:00.001Z");
        ts(1L, MICROSECONDS, "1970-01-01T00:00:00.000001Z");
        ts(1L, NANOSECONDS, "1970-01-01T00:00:00.000000001Z");

        ts(-1L, DAYS, "1969-12-31T00:00:00Z");
        ts(-1L, HOURS, "1969-12-31T23:00:00Z");
        ts(-1L, MINUTES, "1969-12-31T23:59:00Z");
        ts(-1L, SECONDS, "1969-12-31T23:59:59Z");
        ts(-1L, MILLISECONDS, "1969-12-31T23:59:59.999Z");
        ts(-1L, MICROSECONDS, "1969-12-31T23:59:59.999999Z");
        ts(-1L, NANOSECONDS, "1969-12-31T23:59:59.999999999Z");

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

    static void ts(long v, TimeUnit y, String expected) {
        String s = FileTime.from(v, y).toString();
        if (!s.equals(expected))
            throw new RuntimeException("unexpected format");
    }
}
