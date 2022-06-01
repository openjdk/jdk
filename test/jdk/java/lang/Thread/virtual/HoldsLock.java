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
 * @summary Test Thread.holdsLock when lock held by carrier thread
 * @modules java.base/java.lang:+open
 * @compile --enable-preview -source ${jdk.version} HoldsLock.java
 * @run testng/othervm --enable-preview HoldsLock
 * @run testng/othervm --enable-preview -XX:+UseHeavyMonitors HoldsLock
 */

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class HoldsLock {
    static final Object LOCK1 = new Object();
    static final Object LOCK2 = new Object();

    @Test(enabled=false) // JDK-8281642
    public void testHoldsLock() throws Exception {
        var q = new ArrayBlockingQueue<Runnable>(5);

        Thread carrier = Thread.ofPlatform().start(() -> {
            synchronized (LOCK1) {
                eventLoop(q);
            }
        });

        var ex = new AtomicReference<Throwable>();
        Thread vthread = spawnVirtual(ex, executor(q), () -> {
            assertTrue(Thread.currentThread().isVirtual());
            assertFalse(carrier.isVirtual());

            synchronized (LOCK2) {
                assertTrue(Thread.holdsLock(LOCK2)); // virtual thread holds lock2
                assertFalse(Thread.holdsLock(LOCK1)); // carrier thread holds lock1
            }
        });

        join(vthread, ex);
        stop(carrier);
    }

    @Test
    public void testThreadInfo() throws Exception {
        var q = new ArrayBlockingQueue<Runnable>(5);

        Thread carrier = spawnCarrier(q);
        Thread vthread = spawnVirtual(executor(q), () -> {
            synchronized (LOCK1) {
                try {
                    LOCK1.wait();
                } catch (InterruptedException e) {}
            }
        });

        while (vthread.getState() != Thread.State.WAITING) {
            Thread.sleep(10);
        }
        System.out.format("%s is waiting on %s%n", vthread, LOCK1);
        long vthreadId = vthread.getId();
        long carrierId = carrier.getId();

        System.out.format("\t\t%s%n", LOCK1);
        String lockAsString = LOCK1.toString();

        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] tids = bean.getAllThreadIds();
        boolean foundCarrier = false;
        for (long tid : tids) {
            ThreadInfo info = bean.getThreadInfo(tid);
            System.out.println(info); // System.out.format("%d\t%s%n", tid, info.getThreadName());

            LockInfo lock = info.getLockInfo();
            if (lock != null && lockAsString.equals(lock.toString())) {
                assert false; // should never get here
                assert tid == vthreadId : "Actual waiter is: " + info.getThreadName()
                        + " vthread: " + vthread + " carrier: " + carrier;
            }

            if (tid == carrierId) {
                // Carrier is WAITING on vthread
                assertEquals(info.getThreadState(), Thread.State.WAITING);
                assertEquals(info.getLockInfo().getClassName(), vthread.getClass().getName());
                assertEquals(info.getLockInfo().getIdentityHashCode(), System.identityHashCode(vthread));
                assertEquals(info.getLockOwnerId(), vthreadId);
                foundCarrier = true;
            }
        }
        assertTrue(foundCarrier);

        stop(vthread);
        stop(carrier);
    }

    static Thread spawnCarrier(BlockingQueue<Runnable> q) {
        return Thread.ofPlatform().start(() -> { eventLoop(q); });
    }

    static Executor executor(BlockingQueue<Runnable> q) {
        return r -> {
            if (!q.offer(r)) throw new RejectedExecutionException();
        };
    }

    static void eventLoop(BlockingQueue<Runnable> q) {
        try {
            while (!Thread.interrupted())
                q.take().run();
        } catch (InterruptedException e) {}
    }

    static Thread spawnVirtual(Executor scheduler, Runnable task) {
        var t = newThread(scheduler, task);
        t.start();
        return t;
    }

    static Thread spawnVirtual(AtomicReference<Throwable> ex, Executor scheduler, Runnable task) {
       var t = newThread(scheduler, () -> {
            try {
                task.run();
            } catch (Throwable x) {
                ex.set(x);
            }
        });
        t.start();
        return t;
    }

    static void stop(Thread t) throws InterruptedException {
        t.interrupt();
        t.join();
    }

    static void join(Thread t, AtomicReference<Throwable> ex) throws Exception {
        t.join();
        var ex0 = ex.get();
        if (ex0 != null)
            throw new ExecutionException("Thread " + t + " threw an uncaught exception.", ex0);
    }

    static Thread newThread(Executor scheduler, Runnable task) {
        ThreadFactory factory = ThreadBuilders.virtualThreadBuilder(scheduler).factory();
        return factory.newThread(task);
    }
}
