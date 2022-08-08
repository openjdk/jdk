/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Thread.getStackTrace to examine the stack trace of a virtual
 *     thread and its carrier
 * @modules java.base/java.lang:+open
 * @compile --enable-preview -source ${jdk.version} GetStackTrace.java
 * @run testng/othervm --enable-preview GetStackTrace
 */

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;
import java.util.stream.Stream;

public class GetStackTrace {

    private static final Object LOCK = new Object();

    public static void main(String[] args) throws Exception {
        try (var scheduler = new Scheduler()) {
            Thread vthread = scheduler.startVirtualThread(() -> {
                synchronized (LOCK) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) { }
                }
            });

            try {
                // wait for virtual thread to wait
                while (vthread.getState() != Thread.State.WAITING) {
                    Thread.sleep(10);
                }

                // bottom-most frame of virtual thread should be Continuation.enter
                System.out.println(vthread);
                StackTraceElement[] vthreadStack = vthread.getStackTrace();
                Stream.of(vthreadStack).forEach(System.out::println);
                assertEquals("enter", vthreadStack[vthreadStack.length - 1].getMethodName());

                System.out.println();

                // top-most frame of carrier thread should be Continuation.run
                // bottom-most frame of carrier thread should be Thread.run
                var carrier = scheduler.thread();
                System.out.println(carrier);
                StackTraceElement[] carrierStack = carrier.getStackTrace();
                Stream.of(carrierStack).forEach(System.out::println);
                assertEquals("run", carrierStack[0].getMethodName());
                assertEquals("run", carrierStack[carrierStack.length - 1].getMethodName());
            } finally {
                vthread.interrupt();
            }
        }
    }

    /**
     * A scheduler with one thread.
     */
    private static class Scheduler implements AutoCloseable, Executor {
        private final BlockingQueue<Runnable> tasks = new LinkedTransferQueue<>();
        private final Thread thread;
        private volatile boolean done;

        Scheduler() {
            this.thread = Thread.ofPlatform().start(() -> {
                try {
                    while (!done) {
                        Runnable task = tasks.take();
                        task.run();
                    }
                } catch (InterruptedException e) { }
            });
        }

        Thread thread() {
            return thread;
        }

        @Override
        public void close() throws InterruptedException {
            done = true;
            thread.interrupt();
            thread.join();
        }

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        Thread startVirtualThread(Runnable task) {
            return ThreadBuilders.virtualThreadBuilder(this).start(task);
        }
    }

    private static void assertTrue(boolean e) {
        if (!e) throw new RuntimeException();
    }

    private static void assertEquals(Object x, Object y) {
        if (!Objects.equals(x, y))
            throw new RuntimeException();
    }
}
