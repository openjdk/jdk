/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8386116
 * @summary Test suspending and sending async exception to a yielding virtual thread
 * @requires vm.continuations
 * @requires vm.jvmti
 * @requires test.thread.factory == null
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm/native -agentlib:StopThreadTest2 StopThreadTest2 1
 */

/*
 * @test
 * @bug 8386116
 * @summary Test suspending and sending async exception to a virtual thread with empty task
 * @requires vm.continuations
 * @requires vm.jvmti
 * @requires test.thread.factory == null
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm/native -agentlib:StopThreadTest2 StopThreadTest2 2
 */

import jdk.test.lib.Asserts;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class StopThreadTest2 {
    static final int MAX_VTHREAD_COUNT = Runtime.getRuntime().availableProcessors();
    static volatile boolean done;
    static AtomicInteger asyncThrownCounter = new AtomicInteger();

    private static native void suspendAllVirtualThreads();
    private static native void resumeAllVirtualThreads();
    private static native boolean stopThread(Thread thread, Throwable th, boolean allowNotAlive);

    public static void main(String args[]) throws Exception {
        int testCase = (args.length > 0) ? Integer.parseInt(args[0]) : 1;
        switch (testCase) {
            case 1 -> testStopAtYield();
            case 2 -> testStopAtEmptyTask();
            default -> throw new RuntimeException("Invalid test case");
        }
    }

    public static void foo(CountDownLatch started) {
        try {
            started.countDown();
            while (!done) {
                Thread.yield();
            }
        } catch (MyException t) {
            asyncThrownCounter.incrementAndGet();
        }
    }

    /**
     * Test StopThread targeting virtual thread calling Thread.yield
     */
    static void testStopAtYield() throws Exception {
        Thread[] vthreads = new Thread[MAX_VTHREAD_COUNT];
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            var started = new CountDownLatch(1);
            vthreads[i] = Thread.ofVirtual().name("VThread#" + i).start(() -> foo(started));
            started.await();
        }

        int asyncInstalledCounter = 0;
        suspendAllVirtualThreads();
        for (Thread vthread : vthreads) {
            if (stopThread(vthread, new MyException(), /*allowNotAlive*/false)) {
                asyncInstalledCounter++;
            }
        }
        resumeAllVirtualThreads();
        done = true;

        for (Thread vthread : vthreads) {
            vthread.join();
        }
        Asserts.assertEquals(asyncInstalledCounter, asyncThrownCounter.get());
    }

    /**
     * Test StopThread targeting virtual thread executing empty task
     */
    static void testStopAtEmptyTask() throws Exception {
        Thread[] vthreads = new Thread[MAX_VTHREAD_COUNT];
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            vthreads[i] = Thread.ofVirtual().name("VThread#" + i).start(() -> {});
        }

        suspendAllVirtualThreads();
        for (Thread vthread : vthreads) {
            stopThread(vthread, new MyException(), /*allowNotAlive*/true);
        }
        resumeAllVirtualThreads();

        for (Thread vthread : vthreads) {
            vthread.join();
        }
    }

    static class MyException extends RuntimeException {}
}
