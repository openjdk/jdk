/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test stack traces in exceptions, stack frames walked by the StackWalker,
 *    and the stack trace returned by Thread.getStackTrace
 * @requires vm.continuations
 * @modules java.base/java.lang:+open java.management
 * @library /test/lib
 * @run junit StackFrames
 * @run junit/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowCarrierFrames StackFrames
 */

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static java.lang.StackWalker.Option.*;

import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StackFrames {

    /**
     * Test that the stack trace in exceptions does not include the carrier thread
     * frames, except when running with -XX:+ShowCarrierFrames.
     */
    @Test
    void testStackTraceException() throws Exception {
        VThreadRunner.run(() -> {
            Exception e = new Exception();
            boolean found = Arrays.stream(e.getStackTrace())
                    .map(StackTraceElement::getClassName)
                    .anyMatch("java.util.concurrent.ForkJoinPool"::equals);
            assertTrue(found == hasJvmArgument("-XX:+ShowCarrierFrames"));
        });
    }

    /**
     * Test that StackWalker does not include carrier thread frames in the stream of
     * stack frames.
     */
    @Test
    void testStackWalker() throws Exception {
        VThreadRunner.run(() -> {
            StackWalker walker = StackWalker.getInstance(Set.of(RETAIN_CLASS_REFERENCE));
            boolean found = walker.walk(sf ->
                    sf.map(StackWalker.StackFrame::getDeclaringClass)
                            .anyMatch(c -> c == ForkJoinPool.class));
            assertFalse(found);
        });
    }

    /**
     * Test Thread.getStackTrace returns the expected bottom frame for both the carrier
     * and virtual thread.
     */
    @Test
    void testBottomFrames() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            var carrierRef = new AtomicReference<Thread>();
            Executor scheduler = task -> {
                pool.submit(() -> {
                    carrierRef.set(Thread.currentThread());
                    task.run();
                });
            };
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            var ready = new AtomicBoolean();
            var done = new AtomicBoolean();

            // create virtual thread to use custom scheduler
            var vthread = factory.newThread(() -> {
                ready.set(true);
                while (!done.get()) {
                    Thread.onSpinWait();
                }
            });

            vthread.start();
            try {
                awaitTrue(ready);

                // get carrier Thread
                Thread carrier = carrierRef.get();
                assertTrue(carrier instanceof ForkJoinWorkerThread);

                // bottom-most frame of virtual thread should be VirtualThread.run
                System.err.println(vthread);
                StackTraceElement[] vthreadStack = vthread.getStackTrace();
                Stream.of(vthreadStack).forEach(e -> System.err.println("    " + e));
                StackTraceElement bottomFrame = vthreadStack[vthreadStack.length - 1];
                assertEquals("java.lang.VirtualThread.run",
                        bottomFrame.getClassName() + "." + bottomFrame.getMethodName());

                // bottom-most frame of carrier thread should be Thread.run
                System.err.println(carrier);
                StackTraceElement[] carrierStack = carrier.getStackTrace();
                Stream.of(carrierStack).forEach(e -> System.err.println("    " + e));
                bottomFrame = carrierStack[carrierStack.length - 1];
                assertEquals("java.util.concurrent.ForkJoinWorkerThread.run",
                        bottomFrame.getClassName() + "." + bottomFrame.getMethodName());

            } finally {
                done.set(true);
                vthread.join();
            }
        }
    }

    /**
     * Returns true if started with the given VM option.
     */
    private static boolean hasJvmArgument(String arg) {
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.equals(arg)) return true;
        }
        return false;
    }

    /**
     * Waits for the boolean value to become true.
     */
    private static void awaitTrue(AtomicBoolean ref) throws InterruptedException {
        while (!ref.get()) {
            Thread.sleep(20);
        }
    }
}
