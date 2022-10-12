/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test virtual threads using park/unpark
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} Parking.java
 * @run testng/othervm --enable-preview Parking
 */

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import jdk.test.lib.thread.VThreadRunner;
import org.testng.SkipException;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class Parking {
    private static final Object lock = new Object();

    /**
     * Park, unparked by platform thread.
     */
    @Test
    public void testPark1() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        Thread.sleep(1000); // give time for virtual thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park, unparked by virtual thread.
     */
    @Test
    public void testPark2() throws Exception {
        var thread1 = Thread.ofVirtual().start(LockSupport::park);
        Thread.sleep(1000); // give time for virtual thread to park
        var thread2 = Thread.ofVirtual().start(() -> LockSupport.unpark(thread1));
        thread1.join();
        thread2.join();
    }

    /**
     * Park while holding monitor, unparked by platform thread.
     */
    @Test
    public void testPark3() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                LockSupport.park();
            }
        });
        Thread.sleep(1000); // give time for virtual thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with native frame on stack.
     */
    @Test
    public void testPark4() throws Exception {
        throw new SkipException("Not implemented");
    }

    /**
     * Unpark before park.
     */
    @Test
    public void testPark5() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.unpark(Thread.currentThread());
            LockSupport.park();
        });
        thread.join();
    }

    /**
     * 2 x unpark before park.
     */
    @Test
    public void testPark6() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            Thread me = Thread.currentThread();
            LockSupport.unpark(me);
            LockSupport.unpark(me);
            LockSupport.park();
            LockSupport.park();  // should park
        });
        Thread.sleep(1000); // give time for thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * 2 x park and unpark by platform thread.
     */
    @Test
    public void testPark7() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.park();
            LockSupport.park();
        });

        Thread.sleep(1000); // give time for thread to park

        // unpark, virtual thread should park again
        LockSupport.unpark(thread);
        Thread.sleep(1000);
        assertTrue(thread.isAlive());

        // let it terminate
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with interrupt status set.
     */
    @Test
    public void testPark8() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            LockSupport.park();
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked.
     */
    @Test
    public void testPark9() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.park();
            }
        });
    }

    /**
     * Park while holding monitor and with interrupt status set.
     */
    @Test
    public void testPark10() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            synchronized (lock) {
                LockSupport.park();
            }
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked while holding monitor
     */
    @Test
    public void testPark11() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (lock) {
                    LockSupport.park();
                }
            }
        });
    }

    /**
     * parkNanos(-1) completes immediately
     */
    @Test
    public void testParkNanos1() throws Exception {
        VThreadRunner.run(() -> LockSupport.parkNanos(-1));
    }

    /**
     * parkNanos(0) completes immediately
     */
    @Test
    public void testParkNanos2() throws Exception {
        VThreadRunner.run(() -> LockSupport.parkNanos(0));
    }

    /**
     * parkNanos(1000ms) parks thread.
     */
    @Test
    public void testParkNanos3() throws Exception {
        VThreadRunner.run(() -> {
            // park for 1000ms
            long nanos = TimeUnit.NANOSECONDS.convert(1000, TimeUnit.MILLISECONDS);
            long start = System.nanoTime();
            LockSupport.parkNanos(nanos);

            // check that virtual thread parked for >= 900ms
            long elapsed = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start,
                    TimeUnit.NANOSECONDS);
            assertTrue(elapsed >= 900);
        });
    }

    /**
     * Park with parkNanos, unparked by platform thread.
     */
    @Test
    public void testParkNanos4() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            long nanos = TimeUnit.NANOSECONDS.convert(1, TimeUnit.DAYS);
            LockSupport.parkNanos(nanos);
        });
        Thread.sleep(100); // give time for virtual thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with parkNanos, unparked by virtual thread.
     */
    @Test
    public void testParkNanos5() throws Exception {
        var thread1 = Thread.ofVirtual().start(() -> {
            long nanos = TimeUnit.NANOSECONDS.convert(1, TimeUnit.DAYS);
            LockSupport.parkNanos(nanos);
        });
        Thread.sleep(100);  // give time for virtual thread to park
        var thread2 = Thread.ofVirtual().start(() -> LockSupport.unpark(thread1));
        thread1.join();
        thread2.join();
    }

    /**
     * Unpark before parkNanos.
     */
    @Test
    public void testParkNanos6() throws Exception {
        VThreadRunner.run(() -> {
            LockSupport.unpark(Thread.currentThread());
            long nanos = TimeUnit.NANOSECONDS.convert(1, TimeUnit.DAYS);
            LockSupport.parkNanos(nanos);
        });
    }

    /**
     * Unpark before parkNanos(0), should consume parking permit.
     */
    @Test
    public void testParkNanos7() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.unpark(Thread.currentThread());
            LockSupport.parkNanos(0);  // should consume parking permit
            LockSupport.park();  // should block
        });
        boolean isAlive = thread.join(Duration.ofSeconds(2));
        assertTrue(isAlive);
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with parkNanos and interrupt status set.
     */
    @Test
    public void testParkNanos8() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            LockSupport.parkNanos(Duration.ofDays(1).toNanos());
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked in parkNanos.
     */
    @Test
    public void testParkNanos9() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(Duration.ofDays(1).toNanos());
            }
        });
    }

    /**
     * Park with parkNanos while holding monitor and with interrupt status set.
     */
    @Test
    public void testParkNanos10() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            synchronized (lock) {
                LockSupport.parkNanos(Duration.ofDays(1).toNanos());
            }
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked in parkNanos and while holding monitor.
     */
    @Test
    public void testParkNanos11() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (lock) {
                    LockSupport.parkNanos(Duration.ofDays(1).toNanos());
                }
            }
        });
    }

    /**
     * Schedule a thread to be interrupted after a delay.
     */
    private static void scheduleInterrupt(Thread thread, long delay) {
        Runnable interruptTask = () -> {
            try {
                Thread.sleep(delay);
                thread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        new Thread(interruptTask).start();
    }
}
