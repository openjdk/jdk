/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Thread::getStackTrace on a virtual thread that is runnable-unmounted
 * @requires vm.continuations
 * @run main/othervm -Djdk.virtualThreadScheduler.maxPoolSize=1 GetStackTraceWhenRunnable
 */

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class GetStackTraceWhenRunnable {

    public static void main(String[] args) throws Exception {

        // start thread1 and wait for it to park
        Thread thread1 = Thread.startVirtualThread(LockSupport::park);
        while (thread1.getState() != Thread.State.WAITING) {
            Thread.sleep(20);
        }

        // start thread2 to pin the carrier thread
        var started = new AtomicBoolean();
        var done = new AtomicBoolean();
        Thread thread2 = Thread.startVirtualThread(() -> {
            started.set(true);
            while (!done.get()) {
                Thread.onSpinWait();
            }
        });
        try {
            // wait for thread2 to start
            while (!started.get()) {
                Thread.sleep(10);
            }

            // unpark thread1 and check that it is "stuck" in the runnable state
            // (the carrier thread is pinned, no other virtual thread can run)
            LockSupport.unpark(thread1);
            for (int i = 0; i < 5; i++) {
                assertTrue(thread1.getState() == Thread.State.RUNNABLE);
                Thread.sleep(100);
            }

            // print thread1's stack trace
            StackTraceElement[] stack = thread1.getStackTrace();
            assertTrue(stack.length > 0);
            for (StackTraceElement e : stack) {
                System.out.println(e);
            }
        } finally {
            done.set(true);
            thread2.join();
            thread1.join();
        }
    }

    static void assertTrue(boolean e) {
        if (!e) throw new RuntimeException();
    }
}
