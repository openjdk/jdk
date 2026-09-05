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
 * @test id=thaw_fast_full
 * @bug 8390240
 * @summary Test the full thaw path when the runtime stub's compiled
 *          caller is marked for deoptimization
 * @requires vm.continuations
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm -Xcomp DeoptimizedMethodAtMonitorEnter
 */

/*
 * @test id=thaw_fast_partial
 * @bug 8390240
 * @summary Test the partial thaw path when the runtime stub's compiled
 *          caller is marked for deoptimization
 * @requires vm.debug == true & vm.continuations
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm -Xcomp -XX:+ForceSingleFrameThaw DeoptimizedMethodAtMonitorEnter
 */

/*
 * @test id=thaw_slow
 * @bug 8390240
 * @summary Test the slow thaw path when the runtime stub's compiled
 *          caller is marked for deoptimization
 * @requires vm.continuations
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm DeoptimizedMethodAtMonitorEnter
 */

import java.util.concurrent.CountDownLatch;

import jdk.test.lib.Asserts;

public class DeoptimizedMethodAtMonitorEnter {
    private static final Object lock = new Object();
    private static A receiver = new A();
    private static int result;

    public static void main(String[] args) throws Exception {
        warmUp();
        Asserts.assertTrue(receiver.m() == 1, "unexpected value=" + receiver.m());

        var started = new CountDownLatch(1);
        Thread vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            foo();
        });

        synchronized (lock) {
            vthread.start();
            started.await();
            await(vthread, Thread.State.BLOCKED);
            receiver = new B();
        }

        vthread.join();
        Asserts.assertTrue(result == 3, "unexpected result=" + result);
    }

    public static void foo() {
        synchronized (lock) {
            result = receiver.m();
        }
    }

    private static void warmUp() {
        for (int i = 0; i < 30_000; i++) {
            foo();
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private static void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            Asserts.assertTrue(state != Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    static class A {
        int m() {
            return 1;
        }
    }

    static class B extends A {
        int m() {
            return 3;
        }
    }
}
