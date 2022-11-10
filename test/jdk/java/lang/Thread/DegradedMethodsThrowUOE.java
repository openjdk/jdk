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

/* @test
 * @bug  8289610 8249627 8205132
 * @summary Test that Thread stop/suspend/resume/countStackFrames throw UOE
 * @run junit DegradedMethodsThrowUOE
 */

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class DegradedMethodsThrowUOE {

    /**
     * Returns a stream of operations on a Thread that should throw UOE.
     */
    static Stream<Consumer<Thread>> ops() {
        return Stream.<Consumer<Thread>>of(
                Thread::stop,
                Thread::suspend,
                Thread::resume,
                Thread::countStackFrames
        );
    }

    /**
     * Test degraded method on current thread.
     */
    @ParameterizedTest
    @MethodSource("ops")
    void testCurrentThread(Consumer<Thread> op) {
        var thread = Thread.currentThread();
        assertThrows(UnsupportedOperationException.class, () -> op.accept(thread));
    }

    /**
     * Test degraded method on an unstarted thread.
     */
    @ParameterizedTest
    @MethodSource("ops")
    void testUnstartedThread(Consumer<Thread> op) {
        Thread thread = new Thread(() -> { });
        assertThrows(UnsupportedOperationException.class, () -> op.accept(thread));
        assertTrue(thread.getState() == Thread.State.NEW);
    }

    /**
     * Test degraded method on a thread spinning in a loop.
     */
    @ParameterizedTest
    @MethodSource("ops")
    void testRunnableThread(Consumer<Thread> op) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            while (!done.get()) {
                Thread.onSpinWait();
            }
        });
        thread.start();
        try {
            assertThrows(UnsupportedOperationException.class, () -> op.accept(thread));

            // thread should not terminate
            boolean terminated = thread.join(Duration.ofMillis(500));
            assertFalse(terminated);
        } finally {
            done.set(true);
            thread.join();
        }
    }

    /**
     * Test degraded method on a thread that is parked.
     */
    @ParameterizedTest
    @MethodSource("ops")
    void testWaitingThread(Consumer<Thread> op) throws Exception {
        Thread thread = new Thread(LockSupport::park);
        thread.start();
        try {
            // wait for thread to park
            while ((thread.getState() != Thread.State.WAITING)) {
                Thread.sleep(10);
            }
            assertThrows(UnsupportedOperationException.class, () -> op.accept(thread));
            assertTrue(thread.getState() == Thread.State.WAITING);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test degraded method on a terminated thread.
     */
    @ParameterizedTest
    @MethodSource("ops")
    void testTerminatedThread(Consumer<Thread> op) throws Exception {
        Thread thread = new Thread(() -> { });
        thread.start();
        thread.join();
        assertThrows(UnsupportedOperationException.class, () -> op.accept(thread));
        assertTrue(thread.getState() == Thread.State.TERMINATED);
    }
}
