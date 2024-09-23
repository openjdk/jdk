/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test virtual threads using synchronized
 * @library /test/lib
 * @requires vm.continuations & vm.opt.LockingMode != 1
 * @modules java.base/java.lang:+open
 * @run junit/othervm MonitorsTest
 */

/*
 * @test id=Xint
 * @library /test/lib
 * @requires vm.continuations & vm.opt.LockingMode != 1
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xint MonitorsTest
 */

/*
 * @test id=Xcomp
 * @library /test/lib
 * @requires vm.continuations & vm.opt.LockingMode != 1
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xcomp MonitorsTest
 */

/*
 * @test id=Xcomp-TieredStopAtLevel3
 * @library /test/lib
 * @requires vm.continuations & vm.opt.LockingMode != 1
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xcomp -XX:TieredStopAtLevel=3 MonitorsTest
 */

/*
 * @test id=Xcomp-noTieredCompilation
 * @summary Test virtual threads using synchronized
 * @library /test/lib
 * @requires vm.continuations & vm.opt.LockingMode != 1
 * @modules java.base/java.lang:+open
 * @run junit/othervm -Xcomp -XX:-TieredCompilation MonitorsTest
 */

/*
 * @test id=gc
 * @requires vm.debug == true & vm.continuations & vm.opt.LockingMode != 1
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @run junit/othervm -XX:+UnlockDiagnosticVMOptions -XX:+FullGCALot -XX:FullGCALotInterval=1000 MonitorsTest
 */

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;

import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitorsTest {
    final int CARRIER_COUNT = 8;
    ExecutorService scheduler = Executors.newFixedThreadPool(CARRIER_COUNT);

    static final Object globalLock = new Object();
    static volatile boolean finish = false;
    static volatile int counter = 0;

    /////////////////////////////////////////////////////////////////////
    //////////////////////////// BASIC TESTS ////////////////////////////
    /////////////////////////////////////////////////////////////////////

    static final Runnable FOO = () -> {
        Object lock = new Object();
        synchronized(lock) {
            while(!finish) {
                Thread.yield();
            }
        }
        System.err.println("Exiting FOO from thread " + Thread.currentThread().getName());
    };

    static final Runnable BAR = () -> {
        synchronized(globalLock) {
            counter++;
        }
        System.err.println("Exiting BAR from thread " + Thread.currentThread().getName());
    };

    /**
     *  Test yield while holding monitor.
     */
    @Test
    void testBasic() throws Exception {
        final int VT_COUNT = CARRIER_COUNT;

        // Create first batch of VT threads.
        Thread firstBatch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            firstBatch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("FirstBatchVT-" + i).start(FOO);
        }

        // Give time for all threads to reach Thread.yield
        Thread.sleep(1000);

        // Create second batch of VT threads.
        Thread secondBatch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            secondBatch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("SecondBatchVT-" + i).start(BAR);
        }

        while(counter != VT_COUNT) {}

        finish = true;

        for (int i = 0; i < VT_COUNT; i++) {
            firstBatch[i].join();
        }
        for (int i = 0; i < VT_COUNT; i++) {
            secondBatch[i].join();
        }
    }

    static final Runnable BAR2 = () -> {
        synchronized(globalLock) {
            counter++;
        }
        recursive2(10);
        System.err.println("Exiting BAR2 from thread " + Thread.currentThread().getName() + "with counter=" + counter);
    };

    static void recursive2(int count) {
        synchronized(Thread.currentThread()) {
            if (count > 0) {
                recursive2(count - 1);
            } else {
                synchronized(globalLock) {
                    counter++;
                    Thread.yield();
                }
            }
        }
    }

    /**
     *  Test yield while holding monitor with recursive locking.
     */
    @Test
    void testRecursive() throws Exception {
        final int VT_COUNT = CARRIER_COUNT;
        counter = 0;
        finish = false;

        // Create first batch of VT threads.
        Thread firstBatch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            firstBatch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("FirstBatchVT-" + i).start(FOO);
        }

        // Give time for all threads to reach Thread.yield
        Thread.sleep(1000);

        // Create second batch of VT threads.
        Thread secondBatch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            secondBatch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("SecondBatchVT-" + i).start(BAR2);
        }

        while(counter != 2*VT_COUNT) {}

        finish = true;

        for (int i = 0; i < VT_COUNT; i++) {
            firstBatch[i].join();
        }
        for (int i = 0; i < VT_COUNT; i++) {
            secondBatch[i].join();
        }
    }

    static final Runnable FOO3 = () -> {
        synchronized(globalLock) {
            while(!finish) {
                Thread.yield();
            }
        }
        System.err.println("Exiting FOO3 from thread " + Thread.currentThread().getName());
    };

    /**
     *  Test contention on monitorenter.
     */
    @Test
    void testContention() throws Exception {
        final int VT_COUNT = CARRIER_COUNT * 8;
        counter = 0;
        finish = false;

        // Create batch of VT threads.
        Thread batch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            batch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("BatchVT-" + i).start(FOO3);
        }

        // Give time for all threads to reach synchronized(globalLock)
        Thread.sleep(2000);

        finish = true;

        for (int i = 0; i < VT_COUNT; i++) {
            batch[i].join();
        }
    }

    /////////////////////////////////////////////////////////////////////
    //////////////////////////// MAIN TESTS /////////////////////////////
    /////////////////////////////////////////////////////////////////////

    static final int MONITORS_CNT = 12;
    static Object[] globalLockArray;
    static AtomicInteger workerCount = new AtomicInteger(0);

    static void recursive4_1(int depth, int lockNumber) {
        if (depth > 0) {
            recursive4_1(depth - 1, lockNumber);
        } else {
            if (Math.random() < 0.5) {
                Thread.yield();
            }
            recursive4_2(lockNumber);
        }
    }

    static void recursive4_2(int lockNumber) {
        if (lockNumber + 2 <= MONITORS_CNT - 1) {
            lockNumber += 2;
            synchronized(globalLockArray[lockNumber]) {
                Thread.yield();
                recursive4_2(lockNumber);
            }
        }
    }

    static final Runnable FOO4 = () -> {
        while (!finish) {
            int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITORS_CNT - 1);
            synchronized(globalLockArray[lockNumber]) {
                recursive4_1(lockNumber, lockNumber);
            }
        }
        workerCount.getAndIncrement();
        System.err.println("Exiting FOO4 from thread " + Thread.currentThread().getName());
    };

    /**
     *  Test contention on monitorenter with extra monitors on stack shared by all threads.
     */
    @Test
    void testContentionMultipleMonitors() throws Exception {
        final int VT_COUNT = CARRIER_COUNT * 8;
        workerCount.getAndSet(0);
        finish = false;

        globalLockArray = new Object[MONITORS_CNT];
        for (int i = 0; i < MONITORS_CNT; i++) {
            globalLockArray[i] = new Object();
        }

        Thread batch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            batch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("BatchVT-" + i).start(FOO4);
        }

        Thread.sleep(5000);
        finish = true;

        for (int i = 0; i < VT_COUNT; i++) {
            batch[i].join();
        }

        assertEquals(VT_COUNT, workerCount.get());
    }


    static void recursive5_1(int depth, int lockNumber, Object[] myLockArray) {
        if (depth > 0) {
            recursive5_1(depth - 1, lockNumber, myLockArray);
        } else {
            if (Math.random() < 0.5) {
                Thread.yield();
            }
            recursive5_2(lockNumber, myLockArray);
        }
    }

    static void recursive5_2(int lockNumber, Object[] myLockArray) {
        if (lockNumber + 2 <= MONITORS_CNT - 1) {
            lockNumber += 2;
            synchronized (myLockArray[lockNumber]) {
                if (Math.random() < 0.5) {
                    Thread.yield();
                }
                synchronized (globalLockArray[lockNumber]) {
                    Thread.yield();
                    recursive5_2(lockNumber, myLockArray);
                }
            }
        }
    }

    static final Runnable FOO5 = () -> {
        Object[] myLockArray = new Object[MONITORS_CNT];
        for (int i = 0; i < MONITORS_CNT; i++) {
            myLockArray[i] = new Object();
        }

        while (!finish) {
            int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITORS_CNT - 1);
            synchronized (myLockArray[lockNumber]) {
                synchronized (globalLockArray[lockNumber]) {
                    recursive5_1(lockNumber, lockNumber, myLockArray);
                }
            }
        }
        workerCount.getAndIncrement();
        System.err.println("Exiting FOO5 from thread " + Thread.currentThread().getName());
    };

    /**
     *  Test contention on monitorenter with extra monitors on stack both local only and shared by all threads.
     */
    @Test
    void testContentionMultipleMonitors2() throws Exception {
        final int VT_COUNT = CARRIER_COUNT * 8;
        workerCount.getAndSet(0);
        finish = false;

        globalLockArray = new Object[MONITORS_CNT];
        for (int i = 0; i < MONITORS_CNT; i++) {
            globalLockArray[i] = new Object();
        }

        // Create batch of VT threads.
        Thread batch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            //Thread.ofVirtual().name("FirstBatchVT-" + i).start(FOO);
            batch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("BatchVT-" + i).start(FOO5);
        }

        Thread.sleep(5000);

        finish = true;

        for (int i = 0; i < VT_COUNT; i++) {
            batch[i].join();
        }

        assertEquals(VT_COUNT, workerCount.get());
    }

    static synchronized void recursive6(int depth, Object myLock) {
        if (depth > 0) {
            recursive6(depth - 1, myLock);
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

    static final Runnable FOO6 = () -> {
        Object myLock = new Object();

        while (!finish) {
            int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITORS_CNT - 1);
            synchronized (myLock) {
                synchronized (globalLockArray[lockNumber]) {
                    recursive6(lockNumber, myLock);
                }
            }
        }
        workerCount.getAndIncrement();
        System.err.println("Exiting FOO5 from thread " + Thread.currentThread().getName());
    };

    /**
     *  Test contention on monitorenter with synchronized methods.
     */
    @Test
    void testContentionMultipleMonitors3() throws Exception {
        final int VT_COUNT = CARRIER_COUNT * 8;
        workerCount.getAndSet(0);
        finish = false;


        globalLockArray = new Object[MONITORS_CNT];
        for (int i = 0; i < MONITORS_CNT; i++) {
            globalLockArray[i] = new Object();
        }

        // Create batch of VT threads.
        Thread batch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            batch[i] = VThreadScheduler.virtualThreadBuilder(scheduler).name("BatchVT-" + i).start(FOO6);
        }

        Thread.sleep(5000);

        finish = true;

        for (int i = 0; i < VT_COUNT; i++) {
            batch[i].join();
        }

        if (workerCount.get() != VT_COUNT) {
            throw new RuntimeException("testContentionMultipleMonitors2 failed. Expected " + VT_COUNT + "but found " + workerCount.get());
        }
    }

    @Test
    void waitNotifyTest() throws Exception {
        int threadCount = 1000;
        int waitTime = 50;
        long start = System.currentTimeMillis();
        Thread[] vthread = new Thread[threadCount];
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
}
