/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=default
 * @summary Tests for object monitors that have been useful to find bugs
 * @library /test/lib
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run junit/othervm MiscMonitorTests
 */

/*
 * @test id=Xint
 * @library /test/lib
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xint MiscMonitorTests
 */

/*
 * @test id=Xcomp
 * @library /test/lib
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xcomp MiscMonitorTests
 */

/*
 * @test id=Xcomp-TieredStopAtLevel3
 * @library /test/lib
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xcomp -XX:TieredStopAtLevel=3 MiscMonitorTests
 */

/*
 * @test id=Xcomp-noTieredCompilation
 * @summary Test virtual threads using synchronized
 * @library /test/lib
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xcomp -XX:-TieredCompilation MiscMonitorTests
 */

/*
 * @test id=gc
 * @requires vm.debug == true & vm.continuations
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @run junit/othervm -XX:+UnlockDiagnosticVMOptions -XX:+FullGCALot -XX:FullGCALotInterval=1000 MiscMonitorTests
 */

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiscMonitorTests {
    static final int CARRIER_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * Test that yielding while holding monitors releases carrier.
     */
    @Test
    void testReleaseOnYield() throws Exception {
        try (var test = new TestReleaseOnYield()) {
            test.runTest();
        }
    }

    private static class TestReleaseOnYield extends TestBase {
        final Object lock = new Object();
        volatile boolean finish;
        volatile int counter;

        @Override
        void runTest() throws Exception {
            int vthreadCount = CARRIER_COUNT;

            startVThreads(() -> foo(), vthreadCount, "Batch1");
            sleep(500);  // Give time for threads to reach Thread.yield
            startVThreads(() -> bar(), vthreadCount, "Batch2");

            while (counter != vthreadCount) {
                Thread.onSpinWait();
            }
            finish = true;
            joinVThreads();
        }

        void foo() {
            Object lock = new Object();
            synchronized (lock) {
                while (!finish) {
                    Thread.yield();
                }
            }
            System.err.println("Exiting foo from thread " + Thread.currentThread().getName());
        }

        void bar() {
            synchronized (lock) {
                counter++;
            }
            System.err.println("Exiting bar from thread " + Thread.currentThread().getName());
        }
    }

    /**
     * Test yielding while holding monitors with recursive locking releases carrier.
     */
    @Test
    void testReleaseOnYieldRecursive() throws Exception {
        try (var test = new TestReleaseOnYieldRecursive()) {
            test.runTest();
        }
    }

    private static class TestReleaseOnYieldRecursive extends TestBase {
        final Object lock = new Object();
        volatile boolean finish;
        volatile int counter;

        @Override
        void runTest() throws Exception {
            int vthreadCount = CARRIER_COUNT;

            startVThreads(() -> foo(), vthreadCount, "Batch1");
            sleep(500);  // Give time for threads to reach Thread.yield
            startVThreads(() -> bar(), vthreadCount, "Batch2");

            while (counter != 2 * vthreadCount) {
                Thread.onSpinWait();
            }
            finish = true;
            joinVThreads();
        }

        void foo() {
           Object lock = new Object();
            synchronized (lock) {
                while (!finish) {
                    Thread.yield();
                }
            }
            System.err.println("Exiting foo from thread " + Thread.currentThread().getName());
        }

        void bar() {
            synchronized (lock) {
                counter++;
            }
            recursive(10);
            System.err.println("Exiting bar from thread " + Thread.currentThread().getName());
        };

        void recursive(int count) {
            synchronized (Thread.currentThread()) {
                if (count > 0) {
                    recursive(count - 1);
                } else {
                    synchronized (lock) {
                        counter++;
                        Thread.yield();
                    }
                }
            }
        }
    }

    /**
     * Test that contention on monitorenter releases carrier.
     */
    @Test
    void testReleaseOnContention() throws Exception {
        try (var test = new TestReleaseOnContention()) {
            test.runTest();
        }
    }

    private static class TestReleaseOnContention extends TestBase {
        final Object lock = new Object();
        volatile boolean finish;
        volatile int counter;

        @Override
        void runTest() throws Exception {
            int vthreadCount = CARRIER_COUNT * 8;

            startVThreads(() -> foo(), vthreadCount, "VThread");
            sleep(500);  // Give time for threads to reach synchronized (lock)

            finish = true;
            joinVThreads();
        }

        void foo() {
            synchronized (lock) {
                while (!finish) {
                    Thread.yield();
                }
            }
            System.err.println("Exiting foo from thread " + Thread.currentThread().getName());
        }
    }

    /**
     * Test contention on monitorenter with extra monitors on stack shared by all threads.
     */
    @Test
    void testContentionMultipleMonitors() throws Exception {
        try (var test = new TestContentionMultipleMonitors()) {
            test.runTest();
        }
    }

    private static class TestContentionMultipleMonitors extends TestBase {
        static int MONITOR_COUNT = 12;
        final Object[] lockArray = new Object[MONITOR_COUNT];
        final AtomicInteger workerCount = new AtomicInteger(0);
        volatile boolean finish;

        @Override
        void runTest() throws Exception {
            int vthreadCount = CARRIER_COUNT * 8;
            for (int i = 0; i < MONITOR_COUNT; i++) {
                lockArray[i] = new Object();
            }

            startVThreads(() -> foo(), vthreadCount, "VThread");

            sleep(5000);
            finish = true;
            joinVThreads();
            assertEquals(vthreadCount, workerCount.get());
        }

        void foo() {
            while (!finish) {
                int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITOR_COUNT - 1);
                synchronized (lockArray[lockNumber]) {
                    recursive1(lockNumber, lockNumber);
                }
            }
            workerCount.getAndIncrement();
            System.err.println("Exiting foo from thread " + Thread.currentThread().getName());
        }

        public void recursive1(int depth, int lockNumber) {
            if (depth > 0) {
                recursive1(depth - 1, lockNumber);
            } else {
                if (Math.random() < 0.5) {
                    Thread.yield();
                }
                recursive2(lockNumber);
            }
        }

        public void recursive2(int lockNumber) {
            if (lockNumber + 2 <= MONITOR_COUNT - 1) {
                lockNumber += 2;
                synchronized (lockArray[lockNumber]) {
                    Thread.yield();
                    recursive2(lockNumber);
                }
            }
        }
    }

    /**
     * Test contention on monitorenter with extra monitors on stack both local only and shared by all threads.
     */
    @Test
    void testContentionMultipleMonitors2() throws Exception {
        try (var test = new TestContentionMultipleMonitors2()) {
            test.runTest();
        }
    }

    private static class TestContentionMultipleMonitors2 extends TestBase {
        int MONITOR_COUNT = 12;
        final Object[] lockArray = new Object[MONITOR_COUNT];
        final AtomicInteger workerCount = new AtomicInteger(0);
        volatile boolean finish;

        @Override
        void runTest() throws Exception {
            int vthreadCount = CARRIER_COUNT * 8;
            for (int i = 0; i < MONITOR_COUNT; i++) {
                lockArray[i] = new Object();
            }

            startVThreads(() -> foo(), vthreadCount, "VThread");

            sleep(5000);
            finish = true;
            joinVThreads();
            assertEquals(vthreadCount, workerCount.get());
        }

        void foo() {
            Object[] myLockArray = new Object[MONITOR_COUNT];
            for (int i = 0; i < MONITOR_COUNT; i++) {
                myLockArray[i] = new Object();
            }

            while (!finish) {
                int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITOR_COUNT - 1);
                synchronized (myLockArray[lockNumber]) {
                    synchronized (lockArray[lockNumber]) {
                        recursive1(lockNumber, lockNumber, myLockArray);
                    }
                }
            }
            workerCount.getAndIncrement();
            System.err.println("Exiting foo from thread " + Thread.currentThread().getName());
        }

        public void recursive1(int depth, int lockNumber, Object[] myLockArray) {
            if (depth > 0) {
                recursive1(depth - 1, lockNumber, myLockArray);
            } else {
                if (Math.random() < 0.5) {
                    Thread.yield();
                }
                recursive2(lockNumber, myLockArray);
            }
        }

        public void recursive2(int lockNumber, Object[] myLockArray) {
            if (lockNumber + 2 <= MONITOR_COUNT - 1) {
                lockNumber += 2;
                synchronized (myLockArray[lockNumber]) {
                    if (Math.random() < 0.5) {
                        Thread.yield();
                    }
                    synchronized (lockArray[lockNumber]) {
                        Thread.yield();
                        recursive2(lockNumber, myLockArray);
                    }
                }
            }
        }
    }


    /**
     * Test contention on monitorenter with synchronized methods.
     */
    @Test
    void testContentionWithSyncMethods() throws Exception {
        try (var test = new TestContentionWithSyncMethods()) {
            test.runTest();
        }
    }

    private static class TestContentionWithSyncMethods extends TestBase {
        static final int MONITOR_COUNT = 12;
        final Object[] lockArray = new Object[MONITOR_COUNT];
        final AtomicInteger workerCount = new AtomicInteger(0);
        volatile boolean finish;

        @Override
        void runTest() throws Exception {
            int vthreadCount = CARRIER_COUNT * 8;
            for (int i = 0; i < MONITOR_COUNT; i++) {
                lockArray[i] = new Object();
            }

            startVThreads(() -> foo(), vthreadCount, "VThread");

            sleep(5000);
            finish = true;
            joinVThreads();
            assertEquals(vthreadCount, workerCount.get());
        }

        void foo() {
            Object myLock = new Object();

            while (!finish) {
                int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITOR_COUNT - 1);
                synchronized (myLock) {
                    synchronized (lockArray[lockNumber]) {
                        recursive(lockNumber, myLock);
                    }
                }
            }
            workerCount.getAndIncrement();
            System.err.println("Exiting foo from thread " + Thread.currentThread().getName());
        };

        synchronized void recursive(int depth, Object myLock) {
            if (depth > 0) {
                recursive(depth - 1, myLock);
            } else {
                if (Math.random() < 0.5) {
                    Thread.yield();
                } else {
                    synchronized (myLock) {
                        Thread.yield();
                    }
                }
            }
        }
    }

    /**
     * Test wait/notify mechanism.
     */
    @Test
    void waitNotifyTest() throws Exception {
        int threadCount = 1000;
        int waitTime = 50;
        Thread[] vthread = new Thread[threadCount];
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < 5000) {
            CountDownLatch latchStart = new CountDownLatch(threadCount);
            CountDownLatch latchFinish = new CountDownLatch(threadCount);
            Object object = new Object();

            for (int i = 0; i < threadCount; i++) {
                vthread[i] = Thread.ofVirtual().start(() -> {
                    synchronized (object) {
                        try {
                            latchStart.countDown();
                            object.wait(waitTime);
                        } catch (InterruptedException e) {
                            //do nothing;
                        }
                    }
                    latchFinish.countDown();
                });
            }

            try {
                latchStart.await();
                synchronized (object) {
                    object.notifyAll();
                }
                latchFinish.await();
                for (int i = 0; i < threadCount; i++) {
                    vthread[i].join();
                }
            } catch (InterruptedException e) {
                //do nothing;
            }
        }
    }

    private static abstract class TestBase implements AutoCloseable {
        final ExecutorService scheduler = Executors.newFixedThreadPool(CARRIER_COUNT);
        final List<Thread[]> vthreadList = new ArrayList<>();

        abstract void runTest() throws Exception;

        void startVThreads(Runnable r, int count, String name) {
            Thread vthreads[] = new Thread[count];
            for (int i = 0; i < count; i++) {
                vthreads[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name(name + "-" + i).start(r);
            }
            vthreadList.add(vthreads);
        }

        void joinVThreads() throws Exception {
            for (Thread[] vthreads : vthreadList) {
                for (Thread vthread : vthreads) {
                    vthread.join();
                }
            }
        }

        void sleep(long ms) throws Exception {
            Thread.sleep(ms);
        }

        @Override
        public void close() {
            scheduler.close();
        }
    }
}
