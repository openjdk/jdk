/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test virtual threads doing selection operations
 * @library /test/lib
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED SelectorOps
 */

/*
 * @test id=poller-modes
 * @requires (os.family == "linux") | (os.family == "mac")
 * @library /test/lib
 * @run junit/othervm/native -Djdk.pollerMode=1 --enable-native-access=ALL-UNNAMED SelectorOps
 * @run junit/othervm/native -Djdk.pollerMode=2 --enable-native-access=ALL-UNNAMED SelectorOps
 * @run junit/othervm/native -Djdk.pollerMode=3 --enable-native-access=ALL-UNNAMED SelectorOps
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @library /test/lib
 * @run junit/othervm/native -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations
 *     --enable-native-access=ALL-UNNAMED SelectorOps
 */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadPinner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SelectorOps {
    private static String selectorClassName;  // platform specific class name

    @BeforeAll
    static void setup() throws Exception {
        try (Selector sel = Selector.open()) {
            selectorClassName = sel.getClass().getName();
        }
    }

    /**
     * Test that select wakes up when a channel is ready for I/O.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testSelect(boolean timed) throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Selector sel = Selector.open()) {
                Pipe.SinkChannel sink = p.sink();
                Pipe.SourceChannel source = p.source();
                source.configureBlocking(false);
                SelectionKey key = source.register(sel, SelectionKey.OP_READ);

                // write to sink to ensure source is readable
                ByteBuffer buf = ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8));
                onSelect(() -> sink.write(buf));

                int n = timed ? sel.select(60_000) : sel.select();
                assertEquals(1, n);
                assertTrue(sel.isOpen());
                assertTrue(key.isReadable());
            } finally {
                closePipe(p);
            }
        });
    }

    /**
     * Test that select wakes up when a channel is ready for I/O and thread is pinned.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testSelectWhenPinned(boolean timed) throws Exception {
        VThreadPinner.runPinned(() -> { testSelect(timed); });
    }

    /**
     * Test that select wakes up when timeout is reached.
     */
    @Test
    public void testSelectTimeout() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Selector sel = Selector.open()) {
                Pipe.SourceChannel source = p.source();
                source.configureBlocking(false);
                SelectionKey key = source.register(sel, SelectionKey.OP_READ);

                long start = millisTime();
                int n = sel.select(1000);
                expectDuration(start, /*min*/500, /*max*/20_000);

                assertEquals(0, n);
                assertTrue(sel.isOpen());
                assertFalse(key.isReadable());
            } finally {
                closePipe(p);
            }
        });
    }

    /**
     * Test that select wakes up when timeout is reached and thread is pinned.
     */
    @Test
    public void testSelectTimeoutWhenPinned() throws Exception {
        VThreadPinner.runPinned(() -> { testSelectTimeout(); });
    }

    /**
     * Test that selectNow is non-blocking.
     */
    @Test
    public void testSelectNow() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Selector sel = Selector.open()) {
                Pipe.SinkChannel sink = p.sink();
                Pipe.SourceChannel source = p.source();
                source.configureBlocking(false);
                SelectionKey key = source.register(sel, SelectionKey.OP_READ);

                // selectNow should return immediately
                for (int i = 0; i < 3; i++) {
                    long start = millisTime();
                    int n = sel.selectNow();
                    expectDuration(start, -1, /*max*/20_000);
                    assertEquals(0, n);
                }

                // write to sink to ensure source is readable
                ByteBuffer buf = ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8));
                sink.write(buf);

                // call selectNow until key added to selected key set
                int n = 0;
                while (n == 0) {
                    Thread.sleep(10);
                    long start = millisTime();
                    n = sel.selectNow();
                    expectDuration(start, -1, /*max*/20_000);
                }
                assertEquals(1, n);
                assertTrue(sel.isOpen());
                assertTrue(key.isReadable());
            } finally {
                closePipe(p);
            }
        });
    }

    /**
     * Test calling wakeup before select.
     */
    @Test
    public void testWakeupBeforeSelect() throws Exception {
        VThreadRunner.run(() -> {
            try (Selector sel = Selector.open()) {
                sel.wakeup();
                int n = sel.select();
                assertEquals(0, n);
                assertTrue(sel.isOpen());
            }
        });
    }

    /**
     * Test calling wakeup before select and thread is pinned.
     */
    @Test
    public void testWakeupBeforeSelectWhenPinned() throws Exception {
        VThreadPinner.runPinned(() -> { testWakeupBeforeSelect(); });
    }

    /**
     * Test calling wakeup while a thread is blocked in select.
     */
    @Test
    public void testWakeupDuringSelect() throws Exception {
        VThreadRunner.run(() -> {
            try (Selector sel = Selector.open()) {
                onSelect(sel::wakeup);
                int n = sel.select();
                assertEquals(0, n);
                assertTrue(sel.isOpen());
            }
        });
    }

    /**
     * Test calling wakeup while a thread is blocked in select and the thread is pinned.
     */
    @Test
    public void testWakeupDuringSelectWhenPinned() throws Exception {
        VThreadPinner.runPinned(() -> { testWakeupDuringSelect(); });
    }

    /**
     * Test closing selector while a thread is blocked in select.
     */
    @Test
    public void testCloseDuringSelect() throws Exception {
        VThreadRunner.run(() -> {
            try (Selector sel = Selector.open()) {
                onSelect(sel::close);
                int n = sel.select();
                assertEquals(0, n);
                assertFalse(sel.isOpen());
            }
        });
    }

    /**
     * Test closing selector while a thread is blocked in select and the thread is pinned.
     */
    @Test
    public void testCloseDuringSelectWhenPinned() throws Exception {
        VThreadPinner.runPinned(() -> { testCloseDuringSelect(); });
    }

    /**
     * Test calling select with interrupted status set.
     */
    @Test
    public void testInterruptBeforeSelect() throws Exception {
        VThreadRunner.run(() -> {
            try (Selector sel = Selector.open()) {
                Thread me = Thread.currentThread();
                me.interrupt();
                int n = sel.select();
                assertEquals(0, n);
                assertTrue(me.isInterrupted());
                assertTrue(sel.isOpen());
            }
        });
    }

    /**
     * Test calling select with interrupted status set and thread is pinned.
     */
    @Test
    public void testInterruptBeforeSelectWhenPinned() throws Exception {
        VThreadPinner.runPinned(() -> { testInterruptDuringSelect(); });
    }

    /**
     * Test interrupting a thread blocked in select.
     */
    @Test
    public void testInterruptDuringSelect() throws Exception {
        VThreadRunner.run(() -> {
            try (Selector sel = Selector.open()) {
                Thread me = Thread.currentThread();
                onSelect(me::interrupt);
                int n = sel.select();
                assertEquals(0, n);
                assertTrue(me.isInterrupted());
                assertTrue(sel.isOpen());
            }
        });
    }

    /**
     * Test interrupting a thread blocked in select and the thread is pinned.
     */
    @Test
    public void testInterruptDuringSelectWhenPinned() throws Exception {
        VThreadPinner.runPinned(() -> { testInterruptDuringSelect(); });
    }

    /**
     * Close a pipe's sink and source channels.
     */
    private void closePipe(Pipe p) {
        try { p.sink().close(); } catch (IOException ignore) { }
        try { p.source().close(); } catch (IOException ignore) { }
    }

    /**
     * Runs the given action when the current thread is sampled in a selection operation.
     */
    private void onSelect(VThreadRunner.ThrowingRunnable<Exception> action) {
        Thread target = Thread.currentThread();
        Thread.ofPlatform().daemon().start(() -> {
            try {
                boolean found = false;
                while (!found) {
                    Thread.sleep(20);
                    StackTraceElement[] stack = target.getStackTrace();
                    found = Arrays.stream(stack)
                            .anyMatch(e -> selectorClassName.equals(e.getClassName())
                                    && "doSelect".equals(e.getMethodName()));
                }
                action.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check the duration of a task
     * @param start start time, in milliseconds
     * @param min minimum expected duration, in milliseconds
     * @param max maximum expected duration, in milliseconds
     * @return the duration (now - start), in milliseconds
     */
    private static void expectDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
    }
}
