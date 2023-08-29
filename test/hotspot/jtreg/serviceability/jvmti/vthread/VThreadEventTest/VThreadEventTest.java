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
 * @run main/othervm/native
 *     -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading VThreadEventTest attach
 */

import com.sun.tools.attach.VirtualMachine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.List;
import java.util.ArrayList;

class Counter {
    private int count = 0;

    Counter (int count) {
        this.count = count;
    }

    public synchronized void decr() {
        count--;
        notify();
    }

    public synchronized void await() {
        try {
            while (count > 0) {
                wait(1);
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException("wait was interrupted: " + ex);
        }
    }
}

public class VThreadEventTest {
    static final int TCNT1 = 10;
    static final int TCNT2 = 4;
    static final int TCNT3 = 4;
    static final int THREAD_CNT = TCNT1 + TCNT2 + TCNT3;
    static final long TIMEOUT_BASE = 1_000_000L;

    private static void log(String msg) { System.out.println(msg); }

    private static native int threadEndCount();
    private static native int threadMountCount();
    private static native int threadUnmountCount();

    private static volatile int completedNo;
    private static volatile boolean attached;
    private static boolean failed;
    private static List<Thread> threads = new ArrayList(TCNT1);

    public static void main(String[] args) throws Exception {
        if (Runtime.getRuntime().availableProcessors() < 8) {
            log("WARNING: test expects at least 8 processors.");
        }
        Counter ready1 = new Counter(THREAD_CNT);
        Counter ready2 = new Counter(THREAD_CNT);
        Counter mready = new Counter(1);

        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int tCnt = 0; tCnt < TCNT1; tCnt++) {
                executorService.execute(() -> {
                    synchronized (threads) {
                        threads.add(Thread.currentThread());
                    }
                    log("test1 vthread started");
                    ready1.decr();
                    mready.await();
                    try {
                        // timeout is big enough to keep mounted untill interrupted
                        Thread.sleep(20000);
                    } catch (InterruptedException ex) {
                        // it is expected, ignore
                    }
                    ready2.decr();
                    completedNo++;
                });
            }
            for (int tCnt = 0; tCnt < TCNT2; tCnt++) {
                executorService.execute(() -> {
                    log("test2 vthread started");
                    ready1.decr();
                    mready.await();
                    while (!attached) {
                        // keep mounted
                    }
                    ready2.decr();
                    completedNo++;
                });
            }
            for (int tCnt = 0; tCnt < TCNT3; tCnt++) {
                executorService.execute(() -> {
                    log("test3 vthread started");
                    ready1.decr();
                    mready.await();
                    while (!attached) {
                        // keep mounted
                    }
                    LockSupport.parkNanos(10 * TIMEOUT_BASE);
                    ready2.decr();
                    completedNo++;
                });
            }
            ready1.await();
            mready.decr();
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
            vm.loadAgentLibrary("VThreadEventTest");
            Thread.sleep(100);
            log("main: completedNo: " + completedNo);
            attached = true;
            for (Thread t : threads) {
                t.interrupt();
            }
            ready2.await();
        }
        // wait not more than 10 secs until all VirtualThreadEnd events are sent
        for (int sleepNo = 0; sleepNo < 10 && threadEndCount() < THREAD_CNT; sleepNo++) {
            log("main: wait iter: " + sleepNo);
            Thread.sleep(100);
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
            log("unexpected count of ThreadEnd events");
            failed = true;
        }
        if (threadMountCnt != threadMountExp) {
            log("unexpected count of ThreadMount events");
            failed = true;
        }
        if (threadUnmountCnt != threadUnmountExp) {
            log("unexpected count of ThreadUnmount events");
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("FAILED: event count is wrong");
        }
    }

}

