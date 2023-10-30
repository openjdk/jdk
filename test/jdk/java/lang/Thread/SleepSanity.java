/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/**
 * @test
 * @summary Sanity test Thread.sleep behavior
 * @run junit SleepSanity
 */

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SleepSanity {

    static final int[] TRY_MILLIS = new int[] { 0, 1, 10, 100, 1_000 };
    static final int[] TRY_NANOS  = new int[] { 0, 1, 10, 100, 1_000, 10_000, 100_000, 999_999 };

    @Test
    void testMillis() throws Exception {
        testIAE(() -> Thread.sleep(-1), "timeout value is negative");

        testTimeout(() -> Thread.sleep(10_000),            5_000);
        testTimeout(() -> Thread.sleep(Integer.MAX_VALUE), 5_000);
        testTimeout(() -> Thread.sleep(Long.MAX_VALUE),    5_000);

        for (final int millis : TRY_MILLIS) {
            testTimes(() -> Thread.sleep(millis), millis, 20_000);
        }
    }

    @Test
    void testMillisNanos() throws Exception {
        testIAE(() -> Thread.sleep(-1),    "timeout value is negative");

        testIAE(() -> Thread.sleep(0, -1),                "nanosecond timeout value out of range");
        testIAE(() -> Thread.sleep(0, 1_000_000),         "nanosecond timeout value out of range");
        testIAE(() -> Thread.sleep(0, Integer.MAX_VALUE), "nanosecond timeout value out of range");

        testTimeout(() -> Thread.sleep(10_000, 0),            5_000);
        testTimeout(() -> Thread.sleep(Integer.MAX_VALUE, 0), 5_000);
        testTimeout(() -> Thread.sleep(Long.MAX_VALUE, 0),    5_000);

        testTimeout(() -> Thread.sleep(10_000, 999_999),            5_000);
        testTimeout(() -> Thread.sleep(Integer.MAX_VALUE, 999_999), 5_000);
        testTimeout(() -> Thread.sleep(Long.MAX_VALUE, 999_999),    5_000);

        for (final int millis : TRY_MILLIS) {
            for (final int nanos : TRY_NANOS) {
                testTimes(() -> Thread.sleep(millis, nanos), millis, 20_000);
            }
        }
    }

    private static void testTimes(TestCase t, long millisMin, long millisMax) throws Exception {
        long start = System.nanoTime();
        t.run();
        long end = System.nanoTime();
        long duration = TimeUnit.NANOSECONDS.toMillis(end - start);
        assertTrue(duration >= millisMin, "Duration " + duration + "ms, expected >= " + millisMin + "ms");
        assertTrue(duration <= millisMax, "Duration " + duration + "ms, expected <= " + millisMax + "ms");
    }

    private static void testTimeout(TestCase t, long millis) throws Exception {
        Thread captThread = Thread.currentThread();
        Thread watcher = new Thread(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ie)  {
                // Do nothing
            }
            captThread.interrupt();
        });
        watcher.setDaemon(true);
        watcher.start();
        try {
            t.run();
            fail("Exited before timeout");
        } catch (InterruptedException ie) {
            // Expected
        }
        watcher.join();
    }

    private static void testIAE(TestCase t, String msg) throws Exception {
        try {
            t.run();
            fail("Should have thrown the IAE");
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains(msg),
                       "Thrown IAE does not contain the string: " + msg + " " + iae);
        }
    }

    private interface TestCase {
        void run() throws Exception;
    }

}
