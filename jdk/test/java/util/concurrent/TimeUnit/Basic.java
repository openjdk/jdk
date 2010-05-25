/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5057341 6363898
 * @summary Basic tests for TimeUnit
 * @author Martin Buchholz
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.*;

public class Basic {
    private static void realMain(String[] args) throws Throwable {

        for (TimeUnit u : TimeUnit.values()) {
            System.out.println(u);
            check(u instanceof TimeUnit);
            equal(42L, u.convert(42, u));
            for (TimeUnit v : TimeUnit.values())
                if (u.convert(42, v) >= 42)
                    equal(42L, v.convert(u.convert(42, v), u));
            check(readObject(serializedForm(u)) == u);
        }

        equal(  24L, HOURS.convert       (1, DAYS));
        equal(  60L, MINUTES.convert     (1, HOURS));
        equal(  60L, SECONDS.convert     (1, MINUTES));
        equal(1000L, MILLISECONDS.convert(1, SECONDS));
        equal(1000L, MICROSECONDS.convert(1, MILLISECONDS));
        equal(1000L, NANOSECONDS.convert (1, MICROSECONDS));

        equal(  24L, DAYS.toHours(1));
        equal(  60L, HOURS.toMinutes(1));
        equal(  60L, MINUTES.toSeconds(1));
        equal(1000L, SECONDS.toMillis(1));
        equal(1000L, MILLISECONDS.toMicros(1));
        equal(1000L, MICROSECONDS.toNanos(1));

        long t0 = System.nanoTime();
        MILLISECONDS.sleep(3); /* See windows bug 6313903, might not sleep */
        long elapsedMillis = (System.nanoTime() - t0)/(1000L * 1000L);
        System.out.printf("elapsed=%d%n", elapsedMillis);
        check(elapsedMillis >= 0);
        /* Might not sleep on windows: check(elapsedMillis >= 3); */
        check(elapsedMillis < 1000);

        //----------------------------------------------------------------
        // Tests for serialized form compatibility with previous release
        //----------------------------------------------------------------
        byte[] serializedForm = /* Generated using tiger */
            {-84, -19, 0, 5, '~', 'r', 0, 29, 'j', 'a', 'v', 'a', '.',
             'u', 't', 'i', 'l', '.', 'c', 'o', 'n', 'c', 'u', 'r', 'r', 'e',
             'n', 't', '.', 'T', 'i', 'm', 'e', 'U', 'n', 'i', 't', 0, 0,
             0, 0, 0, 0, 0, 0, 18, 0, 0, 'x', 'r', 0, 14,
             'j', 'a', 'v', 'a', '.', 'l', 'a', 'n', 'g', '.', 'E', 'n', 'u',
             'm', 0, 0, 0, 0, 0, 0, 0, 0, 18, 0, 0, 'x',
             'p', 't', 0, 7, 'S', 'E', 'C', 'O', 'N', 'D', 'S', };
        check(Arrays.equals(serializedForm(SECONDS), serializedForm));
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
    static byte[] serializedForm(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(obj);
        return baos.toByteArray();
    }
    static Object readObject(byte[] bytes)
        throws IOException, ClassNotFoundException {
        InputStream is = new ByteArrayInputStream(bytes);
        return new ObjectInputStream(is).readObject();
    }
}
