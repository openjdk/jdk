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
 * @summary Test suspend and send async exception to a yielding virtual thread
 * @requires vm.continuations
 * @requires test.thread.factory == null
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm/native -agentlib:StopThreadTest2 StopThreadTest2
 */

import java.util.concurrent.CountDownLatch;
import jdk.test.lib.Asserts;

public class StopThreadTest2 {
    static final int MAX_VTHREAD_COUNT = Runtime.getRuntime().availableProcessors();
    static volatile boolean done;

    private static native void suspendAllVirtualThreads();
    private static native void resumeAllVirtualThreads();
    private static native void stopThread(Thread thread, Throwable th);

    public static void foo(CountDownLatch start) {
        try {
            start.countDown();
            while (!done) {
                Thread.yield();
            }
        } catch (Throwable t) {
        }
    }

    public static void main(String[] args) throws Exception {
        Thread[] vthreads = new Thread[MAX_VTHREAD_COUNT];
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            var started = new CountDownLatch(1);
            vthreads[i] = Thread.ofVirtual().name("VThread#" + i).start(() -> foo(started));
            started.await();
        }

        suspendAllVirtualThreads();
        for (Thread vthread : vthreads) {
            stopThread(vthread, new ThreadDeath());
        }
        resumeAllVirtualThreads();
        done = true;

        for (Thread vthread : vthreads) {
            vthread.join();
        }
    }
}
