/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @requires vm.continuations
 * @run main/othervm/native -agentlib:SingleStepKlassInit -XX:CompileCommand=exclude,SingleStepKlassInit::lambda$main*
 *      -XX:CompileCommand=exclude,SingleStepKlassInit$$Lambda*::run SingleStepKlassInit
 */

import java.util.concurrent.CountDownLatch;

public class SingleStepKlassInit {
    private static final int MAX_VTHREAD_COUNT = 8 * Runtime.getRuntime().availableProcessors();
    private static final CountDownLatch finishInvokeStatic = new CountDownLatch(1);

    private static native void setSingleSteppingMode(boolean enable);
    private static native boolean didSingleStep();

    public static void main(String args[]) throws Exception {
        class TestClass {
            static {
                try {
                    finishInvokeStatic.await();
                } catch (InterruptedException e) {}
            }
            static void m() {
            }
        }

        setSingleSteppingMode(true);
        Thread[] vthreads = new Thread[MAX_VTHREAD_COUNT];
        CountDownLatch[] started = new CountDownLatch[MAX_VTHREAD_COUNT];
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            final int id = i;
            started[i] = new CountDownLatch(1);
            vthreads[i] = Thread.ofVirtual().start(() -> {
                started[id].countDown();
                TestClass.m();
            });
        }
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            started[i].await();
            await(vthreads[i], Thread.State.WAITING);
        }

        finishInvokeStatic.countDown();
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            vthreads[i].join();
        }
        setSingleSteppingMode(false);

        if (!didSingleStep()) {
            throw new RuntimeException("No SingleStep events");
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private static void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assert state != Thread.State.TERMINATED : "Thread has terminated";
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}
