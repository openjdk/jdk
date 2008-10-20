/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6717789
 * @summary Check that a lock is not held when a LeaseManager expires.
 * @author Eamonn McManus
 * @compile -XDignore.symbol.file=true LeaseManagerDeadlockTest.java
 * @run main LeaseManagerDeadlockTest
 */

import com.sun.jmx.event.LeaseManager;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class LeaseManagerDeadlockTest {
    public static String failure;
    public static LeaseManager leaseManager;
    public static Semaphore callbackThreadCompleted = new Semaphore(0);
    public static Object lock = new Object();

    public static Runnable triggerDeadlock = new Runnable() {
        public void run() {
            Runnable pingLeaseManager = new Runnable() {
                public void run() {
                    System.out.println("Ping thread starts");
                    synchronized (lock) {
                        leaseManager.lease(1);
                    }
                    System.out.println("Ping thread completes");
                }
            };
            Thread t = new Thread(pingLeaseManager);
            t.start();
            try {
                Thread.sleep(10);  // enough time for ping thread to grab lock
                synchronized (lock) {
                    t.join();
                }
            } catch (InterruptedException e) {
                fail(e.toString());
            }
            System.out.println("Callback thread completes");
            callbackThreadCompleted.release();
        }
    };

    public static void main(String[] args) throws Exception {
        // Also test that we can shorten the lease from its initial value.
        leaseManager = new LeaseManager(triggerDeadlock, 1000000);
        leaseManager.lease(1L);

        boolean callbackRan =
                callbackThreadCompleted.tryAcquire(3, TimeUnit.SECONDS);

        if (!callbackRan) {
            fail("Callback did not complete - probable deadlock");
            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            System.out.println(Arrays.toString(threads.findDeadlockedThreads()));
            System.out.println("PRESS RETURN");
            System.in.read();
        }

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    public static void fail(String why) {
        System.out.println("TEST FAILS: " + why);
        failure = why;
    }
}
