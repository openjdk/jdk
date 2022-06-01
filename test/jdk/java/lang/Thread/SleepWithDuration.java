/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Thread.sleep(Duration)
 * @run testng SleepWithDuration
 */

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class SleepWithDuration {

    /**
     * Basic test for sleep(Duration).
     */
    @Test
    public void testSleep() throws Exception {
        // sleep for 2 seconds
        long start = millisTime();
        Thread.sleep(Duration.ofMillis(2000));
        expectDuration(start, /*min*/1900, /*max*/20_000);

        Thread.sleep(Duration.ofNanos(-1));
        Thread.sleep(Duration.ofNanos(0));
        Thread.sleep(Duration.ofNanos(1));

        assertThrows(NullPointerException.class, () -> Thread.sleep(null));
    }

    /**
     * Test Thread.sleep with interrupt status set.
     */
    @Test
    public void testSleepWithInterruptStatusSet() throws Exception {
        Thread.currentThread().interrupt();
        try {
            Thread.sleep(Duration.ofNanos(0));
            fail();
        } catch (InterruptedException e) {
            assertFalse(Thread.interrupted());
        }

        Thread.currentThread().interrupt();
        try {
            Thread.sleep(Duration.ofSeconds(2));
            fail();
        } catch (InterruptedException e) {
            assertFalse(Thread.interrupted());
        }
    }

    /**
     * Test interrupting Thread.sleep.
     */
    @Test
    public void testInterruptSleep() throws Exception {
        // schedule current thread to interrupted after 1s
        Thread targetThread = Thread.currentThread();
        Thread wakerThread = new Thread(() -> {
            try {
                Thread.sleep(1000);
                targetThread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        wakerThread.start();
        try {
            Thread.sleep(Duration.ofSeconds(60));
            fail();
        } catch (InterruptedException e) {
            // interrupt status should be cleared
            assertFalse(Thread.interrupted());
        } finally {
            wakerThread.interrupt();
        }
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check the duration of a task
     * @param start start time, in milliseconds
     * @param min minimum expected duration, in milliseconds
     * @param max maximum expected duration, in milliseconds
     * @return the duration (now - start), in milliseconds
     */
    private static void expectDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
    }
}
