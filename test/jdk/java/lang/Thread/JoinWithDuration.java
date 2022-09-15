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
 * @summary Test Thread.join(Duration)
 * @run testng JoinWithDuration
 */

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class JoinWithDuration {
    /**
     * Test join on unstarted thread.
     */
    @Test
    public void testJoinOnUnstartedThread() {
        var thread = new Thread(() -> { });
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofNanos(-100)));
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofNanos(0)));
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofNanos(100)));
    }

    /**
     * Test join on thread that does not terminate while waiting.
     */
    @Test
    public void testJoinOnRunningThread() throws Exception {
        var thread = new Thread(LockSupport::park);
        thread.start();
        try {
            assertFalse(thread.join(Duration.ofNanos(-100)));
            assertFalse(thread.join(Duration.ofNanos(0)));
            assertFalse(thread.join(Duration.ofNanos(100)));

            // test duration of join
            long start = millisTime();
            assertFalse(thread.join(Duration.ofMillis(2000)));
            expectDuration(start, /*min*/1900, /*max*/20_000);

        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test join on thread that terminates while waiting.
     */
    @Test
    public void testJoinOnTerminatingThread() throws Exception {
        var thread = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) { }
        });
        thread.start();
        assertTrue(thread.join(Duration.ofMinutes(1)));
    }

    /**
     * Test join on terminated thread.
     */
    @Test
    public void testJoinOnTerminatedThread() throws Exception {
        var thread = new Thread(() -> { });
        thread.start();
        thread.join();
        assertTrue(thread.join(Duration.ofNanos(-100)));
        assertTrue(thread.join(Duration.ofNanos(0)));
        assertTrue(thread.join(Duration.ofNanos(100)));
    }

    /**
     * Test invoking join with interrupt status set.
     */
    @Test
    public void testJoinWithInterruptStatusSet() throws Exception {
        var thread = new Thread(LockSupport::park);
        thread.start();
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> thread.join(Duration.ofMinutes(1)));
            assertFalse(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test interrupting join.
     */
    @Test
    public void testInterruptJoin() throws Exception {
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

        var thread = new Thread(LockSupport::park);
        thread.start();
        try {
            thread.join(Duration.ofMinutes(1));
            fail();
        } catch (InterruptedException e) {
            // interrupt status should be cleared
            assertFalse(thread.isInterrupted());
        } finally {
            wakerThread.interrupt();
        }
    }

    /**
     * Test join on current thread.
     */
    @Test
    public void testJoinSelf() throws Exception {
        Thread thread = Thread.currentThread();

        assertFalse(thread.join(Duration.ofNanos(-100)));
        assertFalse(thread.join(Duration.ofNanos(0)));
        assertFalse(thread.join(Duration.ofNanos(100)));

        // test duration of join
        long start = millisTime();
        assertFalse(thread.join(Duration.ofMillis(2000)));
        expectDuration(start, /*min*/1900, /*max*/20_000);
    }

    /**
     * Test join(null).
     */
    @Test
    public void testJoinNull() throws Exception {
        var thread = new Thread(LockSupport::park);

        // unstarted
        assertThrows(NullPointerException.class, () -> thread.join(null));

        // started
        thread.start();
        try {
            assertThrows(NullPointerException.class, () -> thread.join(null));
        } finally {
            LockSupport.unpark(thread);
        }
        thread.join();

        // terminated
        assertThrows(NullPointerException.class, () -> thread.join(null));
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
