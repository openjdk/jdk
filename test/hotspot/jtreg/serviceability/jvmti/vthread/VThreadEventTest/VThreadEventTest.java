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

/**
 * @test
 * @bug 8312174
 * @summary missing JVMTI events from vthreads parked during JVMTI attach
 * @requires vm.continuations
 * @requires vm.jvmti
 * @requires vm.compMode != "Xcomp"
 * @run main/othervm/native
 *     -Djdk.virtualThreadScheduler.parallelism=9
 *     -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading VThreadEventTest attach
 */

import com.sun.tools.attach.VirtualMachine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.List;
import java.util.ArrayList;

/*
 * The test uses custom implementation of the CountDownLatch class.
 * The reason is we want the state of tested thread to be predictable.
 * With java.util.concurrent.CountDownLatch it is not clear what thread state is expected.
 */
class CountDownLatch {
    private int count = 0;

    CountDownLatch(int count) {
        this.count = count;
    }

    public synchronized void countDown() {
        count--;
        notify();
    }

    public synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait(1);
        }
    }
}

public class VThreadEventTest {
    static final int TCNT1 = 10;
    static final int TCNT2 = 4;
    static final int TCNT3 = 4;
    static final int THREAD_CNT = TCNT1 + TCNT2 + TCNT3;

    private static void log(String msg) { System.out.println(msg); }

    private static native int threadEndCount();
    private static native int threadMountCount();
    private static native int threadUnmountCount();

    private static volatile boolean attached;
    private static boolean failed;
    private static List<Thread> test1Threads = new ArrayList(TCNT1);

    private static CountDownLatch ready0 = new CountDownLatch(THREAD_CNT);
    private static CountDownLatch ready1 = new CountDownLatch(TCNT1);
    private static CountDownLatch ready2 = new CountDownLatch(THREAD_CNT);
    private static CountDownLatch mready = new CountDownLatch(1);

    private static void await(CountDownLatch dumpedLatch) {
        try {
            dumpedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // The test1 vthreads are kept unmounted until interrupted after agent attach.
    static final Runnable test1 = () -> {
        synchronized (test1Threads) {
            test1Threads.add(Thread.currentThread());
        }
        log("test1 vthread started");
        ready0.countDown();
        await(mready);
        ready1.countDown(); // to guaranty state is not State.WAITING after await(mready)
        try {
            Thread.sleep(20000); // big timeout to keep unmounted until interrupted
        } catch (InterruptedException ex) {
            // it is expected, ignore
        }
        ready2.countDown();
    };

    // The test2 vthreads are kept mounted until agent attach.
    static final Runnable test2 = () -> {
        log("test2 vthread started");
        ready0.countDown();
        await(mready);
        while (!attached) {
            // keep mounted
        }
        ready2.countDown();
    };

    // The test3 vthreads are kept mounted until agent attach.
    static final Runnable test3 = () -> {
        log("test3 vthread started");
        ready0.countDown();
        await(mready);
        while (!attached) {
            // keep mounted
        }
        LockSupport.parkNanos(10_000_000L); // will cause extra mount and unmount
        ready2.countDown();
    };

    public static void main(String[] args) throws Exception {
        if (Runtime.getRuntime().availableProcessors() < 8) {
            log("WARNING: test expects at least 8 processors.");
        }
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TCNT1; i++) {
                executorService.execute(test1);
            }
            for (int i = 0; i < TCNT2; i++) {
                executorService.execute(test2);
            }
            for (int i = 0; i < TCNT3; i++) {
                executorService.execute(test3);
            }
            await(ready0);
            mready.countDown();
            await(ready1); // to guarantee state is not State.TIMED_WAITING after await(mready) in test1()
            // wait for test1 threads to reach TIMED_WAITING state in sleep()
            for (Thread t : test1Threads) {
                Thread.State state = t.getState();
                log("DBG: state: " + state);
                while (state != Thread.State.TIMED_WAITING) {
                    Thread.sleep(10);
                    state = t.getState();
                    log("DBG: state: " + state);
                }
            }

            VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
            vm.loadAgentLibrary("VThreadEventTest");
            Thread.sleep(200); // to allow the agent to get ready

            attached = true;
            for (Thread t : test1Threads) {
                 t.interrupt();
            }
            ready2.await();
        }
        // wait until all VirtualThreadEnd events have been sent
        for (int sleepNo = 1; threadEndCount() < THREAD_CNT; sleepNo++) {
            Thread.sleep(100);
            if (sleepNo % 100 == 0) { // 10 sec period of waiting
                log("main: waited seconds: " + sleepNo/10);
            }
        }
        int threadEndCnt = threadEndCount();
        int threadMountCnt = threadMountCount();
        int threadUnmountCnt = threadUnmountCount();
        int threadEndExp = THREAD_CNT;
        int threadMountExp = THREAD_CNT - TCNT2;
        int threadUnmountExp = THREAD_CNT + TCNT3;

        log("ThreadEnd cnt: "     + threadEndCnt     + " (expected: " + threadEndExp + ")");
        log("ThreadMount cnt: "   + threadMountCnt   + " (expected: " + threadMountExp + ")");
        log("ThreadUnmount cnt: " + threadUnmountCnt + " (expected: " + threadUnmountExp + ")");

        if (threadEndCnt != threadEndExp) {
            log("FAILED: unexpected count of ThreadEnd events");
            failed = true;
        }
        if (threadMountCnt != threadMountExp) {
            log("FAILED: unexpected count of ThreadMount events");
            failed = true;
        }
        if (threadUnmountCnt != threadUnmountExp) {
            log("FAILED: unexpected count of ThreadUnmount events");
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("FAILED: event count is wrong");
        }
    }

}

