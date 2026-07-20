/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8284161 8287103
 * @summary Test ThredMXBean.findMonitorDeadlockedThreads with cycles of
 *   platform and virtual threads in deadlock
 * @modules java.management jdk.management
 * @library /test/lib
 * @run main/othervm VirtualThreadDeadlocks PP
 * @run main/othervm VirtualThreadDeadlocks PV
 * @run main/othervm VirtualThreadDeadlocks VV
 */

/**
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @modules java.management jdk.management
 * @library /test/lib
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreadDeadlocks PP
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreadDeadlocks PV
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreadDeadlocks VV
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import jdk.test.lib.thread.VThreadRunner;   // ensureParallelism requires jdk.management

public class VirtualThreadDeadlocks {
    private static final Object LOCK1 = new Object();
    private static final Object LOCK2 = new Object();

    private static final CyclicBarrier barrier = new CyclicBarrier(2);
    private static final AtomicBoolean reached1 = new AtomicBoolean();
    private static final AtomicBoolean reached2 = new AtomicBoolean();

    /**
     * PP = test deadlock with two platform threads
     * PV = test deadlock with one platform thread and one virtual thread
     * VV = test deadlock with two virtual threads
     */
    public static void main(String[] args) throws Exception {
        // need at least two carrier threads due to pinning
        VThreadRunner.ensureParallelism(2);

        // start thread1
        Thread.Builder builder1 = (args[0].charAt(0) == 'P')
                ? Thread.ofPlatform().daemon()
                : Thread.ofVirtual();
        Thread thread1 = builder1.start(() -> {
            synchronized (LOCK1) {
                try { barrier.await(); } catch (Exception ie) {}
                reached1.set(true);
                synchronized (LOCK2) { }
            }
        });
        System.out.println("thread1 => " + thread1);

        // start thread2
        Thread.Builder builder2 = (args[0].charAt(1) == 'P')
                ? Thread.ofPlatform().daemon()
                : Thread.ofVirtual();
        Thread thread2 = builder2.start(() -> {
            synchronized (LOCK2) {
                try { barrier.await(); } catch (Exception ie) {}
                reached2.set(true);
                synchronized (LOCK1) { }
            }
        });
        System.out.println("thread2 => " + thread2);

        System.out.println("Waiting for thread1 and thread2 to deadlock ...");
        awaitTrueAndBlocked(thread1, reached1);
        awaitTrueAndBlocked(thread2, reached2);

        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        long[] deadlockedThreads = sorted(bean.findMonitorDeadlockedThreads());
        System.out.println("findMonitorDeadlockedThreads => " + Arrays.toString(deadlockedThreads));

        // deadlocks involving virtual threads are not detected
        long[] expectedThreads = (!thread1.isVirtual() && !thread2.isVirtual())
            ? sorted(thread1.threadId(), thread2.threadId())
            : null;

        System.out.println("expected => " + Arrays.toString(expectedThreads));

        if (!Arrays.equals(deadlockedThreads, expectedThreads))
            throw new RuntimeException("Unexpected result");
    }

    private static void awaitTrueAndBlocked(Thread thread, AtomicBoolean flag) throws InterruptedException {
        while (!flag.get() || thread.getState() != Thread.State.BLOCKED) {
            Thread.sleep(10);
            if (!thread.isAlive()) {
                throw new RuntimeException("Thread " + thread + " is terminated.");
            }
        }
    }

    private static long[] sorted(long... array) {
        if (array != null) Arrays.sort(array);
        return array;
    }
}
