/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for Selector.select/selectNow(Consumer)
 * @bug 8199433 8208780
 * @run junit SelectWithConsumer
 */

/* @test
 * @requires (os.family == "windows")
 * @run junit/othervm -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.WindowsSelectorProvider SelectWithConsumer
 */

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.concurrent.TimeUnit.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SelectWithConsumer {

    /**
     * Invoke the select methods that take an action and check that the
     * accumulated ready ops notified to the action matches the expected ops.
     */
    void testActionInvoked(SelectionKey key, int expectedOps) throws Exception {
        var callerThread = Thread.currentThread();
        var sel = key.selector();
        var interestOps = key.interestOps();
        var notifiedOps = new AtomicInteger();

        if (expectedOps == 0) {
            // ensure select(Consumer) does not block indefinitely
            sel.wakeup();
        } else {
            // ensure that the channel is ready for all expected operations
            sel.select();
            while ((key.readyOps() & interestOps) != expectedOps) {
                Thread.sleep(100);
                sel.select();
            }
        }

        // select(Consumer)
        notifiedOps.set(0);
        int n = sel.select(k -> {
            assertSame(callerThread, Thread.currentThread());
            assertEquals(key, k);
            int readyOps = key.readyOps();
            assertNotEquals(0, readyOps & interestOps);
            assertEquals(0, readyOps & notifiedOps.get());
            notifiedOps.set(notifiedOps.get() | readyOps);
        });
        assertTrue((n == 1) ^ (expectedOps == 0));
        assertEquals(expectedOps, notifiedOps.get());

        // select(Consumer, timeout)
        notifiedOps.set(0);
        n = sel.select(k -> {
            assertSame(callerThread, Thread.currentThread());
            assertEquals(key, k);
            int readyOps = key.readyOps();
            assertNotEquals(0, readyOps & interestOps);
            assertEquals(0, readyOps & notifiedOps.get());
            notifiedOps.set(notifiedOps.get() | readyOps);
        }, 1000);
        assertTrue((n == 1) ^ (expectedOps == 0));
        assertEquals(expectedOps, notifiedOps.get());

        // selectNow(Consumer)
        notifiedOps.set(0);
        n = sel.selectNow(k -> {
            assertSame(callerThread, Thread.currentThread());
            assertEquals(key, k);
            int readyOps = key.readyOps();
            assertNotEquals(0, readyOps & interestOps);
            assertEquals(0, readyOps & notifiedOps.get());
            notifiedOps.set(notifiedOps.get() | readyOps);
        });
        assertTrue((n == 1) ^ (expectedOps == 0));
        assertEquals(expectedOps, notifiedOps.get());
    }

    /**
     * Test that an action is performed when a channel is ready for reading.
     */
    @Test
    public void testReadable() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SinkChannel sink = p.sink();
            Pipe.SourceChannel source = p.source();
            source.configureBlocking(false);
            SelectionKey key = source.register(sel, SelectionKey.OP_READ);

            // write to sink to ensure source is readable
            scheduleWrite(sink, messageBuffer(), 100, MILLISECONDS);

            // test that action is invoked
            testActionInvoked(key, SelectionKey.OP_READ);
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test that an action is performed when a channel is ready for writing.
     */
    @Test
    public void testWritable() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SourceChannel source = p.source();
            Pipe.SinkChannel sink = p.sink();
            sink.configureBlocking(false);
            SelectionKey key = sink.register(sel, SelectionKey.OP_WRITE);

            // test that action is invoked
            testActionInvoked(key, SelectionKey.OP_WRITE);
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test that an action is performed when a channel is ready for both
     * reading and writing.
     */
    @Test
    public void testReadableAndWriteable() throws Exception {
        ServerSocketChannel ssc = null;
        SocketChannel sc = null;
        SocketChannel peer = null;
        try (Selector sel = Selector.open()) {
            ssc = ServerSocketChannel.open().bind(new InetSocketAddress(0));
            sc = SocketChannel.open(ssc.getLocalAddress());
            sc.configureBlocking(false);
            SelectionKey key = sc.register(sel, (SelectionKey.OP_READ |
                                                 SelectionKey.OP_WRITE));

            // accept connection and write data so the source is readable
            peer = ssc.accept();
            peer.write(messageBuffer());

            // test that action is invoked
            testActionInvoked(key, (SelectionKey.OP_READ | SelectionKey.OP_WRITE));
        } finally {
            if (ssc != null) ssc.close();
            if (sc != null) sc.close();
            if (peer != null) peer.close();
        }
    }

    /**
     * Test that the action is called for two selected channels
     */
    @Test
    public void testTwoChannels() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SourceChannel source = p.source();
            Pipe.SinkChannel sink = p.sink();
            source.configureBlocking(false);
            sink.configureBlocking(false);
            SelectionKey key1 = source.register(sel, SelectionKey.OP_READ);
            SelectionKey key2 = sink.register(sel, SelectionKey.OP_WRITE);

            // write to sink to ensure that the source is readable
            sink.write(messageBuffer());

            // wait for key1 to be readable
            sel.select();
            assertTrue(key2.isWritable());
            while (!key1.isReadable()) {
                Thread.sleep(20);
                sel.select();
            }

            var counter = new AtomicInteger();

            // select(Consumer)
            counter.set(0);
            int n = sel.select(k -> {
                assertTrue(k == key1 || k == key2);
                counter.incrementAndGet();
            });
            assertEquals(2, n);
            assertEquals(2, counter.get());

            // select(Consumer, timeout)
            counter.set(0);
            n = sel.select(k -> {
                assertTrue(k == key1 || k == key2);
                counter.incrementAndGet();
            }, 1000);
            assertEquals(2, n);
            assertEquals(2, counter.get());

            // selectNow(Consumer)
            counter.set(0);
            n = sel.selectNow(k -> {
                assertTrue(k == key1 || k == key2);
                counter.incrementAndGet();
            });
            assertEquals(2, n);
            assertEquals(2, counter.get());
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test calling select twice, the action should be invoked each time
     */
    @Test
    public void testRepeatedSelect1() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SourceChannel source = p.source();
            Pipe.SinkChannel sink = p.sink();
            source.configureBlocking(false);
            SelectionKey key = source.register(sel, SelectionKey.OP_READ);

            // write to sink to ensure that the source is readable
            sink.write(messageBuffer());

            // test that action is invoked
            testActionInvoked(key, SelectionKey.OP_READ);
            testActionInvoked(key, SelectionKey.OP_READ);

        } finally {
            closePipe(p);
        }
    }

    /**
     * Test calling select twice. An I/O operation is performed after the
     * first select so the channel will not be selected by the second select.
     */
    @Test
    public void testRepeatedSelect2() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SourceChannel source = p.source();
            Pipe.SinkChannel sink = p.sink();
            source.configureBlocking(false);
            SelectionKey key = source.register(sel, SelectionKey.OP_READ);

            // write to sink to ensure that the source is readable
            sink.write(messageBuffer());

            // test that action is invoked
            testActionInvoked(key, SelectionKey.OP_READ);

            // read all bytes
            int n;
            ByteBuffer bb = ByteBuffer.allocate(100);
            do {
                n = source.read(bb);
                bb.clear();
            } while (n > 0);

            // test that action is not invoked
            testActionInvoked(key, 0);
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test timeout
     */
    @Test
    public void testTimeout() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SourceChannel source = p.source();
            Pipe.SinkChannel sink = p.sink();
            source.configureBlocking(false);
            source.register(sel, SelectionKey.OP_READ);
            long start = millisTime();
            int n = sel.select(k -> assertTrue(false), 1000L);
            expectDuration(start, 500, Long.MAX_VALUE);
            assertEquals(0, n);
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test wakeup prior to select
     */
    @Test
    public void testWakeupBeforeSelect() throws Exception {
        // select(Consumer)
        try (Selector sel = Selector.open()) {
            sel.wakeup();
            int n = sel.select(k -> assertTrue(false));
            assertEquals(0, n);
        }

        // select(Consumer, timeout)
        try (Selector sel = Selector.open()) {
            sel.wakeup();
            long start = millisTime();
            int n = sel.select(k -> assertTrue(false), 60*1000);
            expectDuration(start, 0, 20_000);
            assertEquals(0, n);
        }
    }

    /**
     * Test wakeup during select
     */
    @Test
    public void testWakeupDuringSelect() throws Exception {
        // select(Consumer)
        try (Selector sel = Selector.open()) {
            scheduleWakeup(sel, 1, SECONDS);
            int n = sel.select(k -> assertTrue(false));
            assertEquals(0, n);
        }

        // select(Consumer, timeout)
        try (Selector sel = Selector.open()) {
            scheduleWakeup(sel, 1, SECONDS);
            long start = millisTime();
            int n = sel.select(k -> assertTrue(false), 60*1000);
            expectDuration(start, 0, 20_000);
            assertEquals(0, n);
        }
    }

    /**
     * Test invoking select with interrupted status set
     */
    @Test
    public void testInterruptBeforeSelect() throws Exception {
        // select(Consumer)
        try (Selector sel = Selector.open()) {
            Thread.currentThread().interrupt();
            int n = sel.select(k -> assertTrue(false));
            assertEquals(0, n);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(sel.isOpen());
        } finally {
            Thread.currentThread().interrupted();  // clear interrupted status
        }

        // select(Consumer, timeout)
        try (Selector sel = Selector.open()) {
            Thread.currentThread().interrupt();
            long start = millisTime();
            int n = sel.select(k -> assertTrue(false), 60*1000);
            expectDuration(start, 0, 20_000);
            assertEquals(0, n);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(sel.isOpen());
        } finally {
            Thread.currentThread().interrupted();  // clear interrupted status
        }
    }

    /**
     * Test interrupt thread during select
     */
    @Test
    public void testInterruptDuringSelect() throws Exception {
        // select(Consumer)
        try (Selector sel = Selector.open()) {
            scheduleInterrupt(Thread.currentThread(), 1, SECONDS);
            int n = sel.select(k -> assertTrue(false));
            assertEquals(0, n);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(sel.isOpen());
        } finally {
            Thread.currentThread().interrupted();  // clear interrupted status
        }

        // select(Consumer, timeout)
        try (Selector sel = Selector.open()) {
            scheduleInterrupt(Thread.currentThread(), 1, SECONDS);
            int n = sel.select(k -> assertTrue(false), 60*1000);
            assertEquals(0, n);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(sel.isOpen());
        } finally {
            Thread.currentThread().interrupted();  // clear interrupted status
        }
    }

    /**
     * Test invoking select on a closed selector
     */
    @Test
    public void testClosedSelector1() throws Exception {
        Selector sel = Selector.open();
        sel.close();
        assertThrows(ClosedSelectorException.class,
                     () -> sel.select(k -> assertTrue(false)));
    }
    @Test
    public void testClosedSelector2() throws Exception {
        Selector sel = Selector.open();
        sel.close();
        assertThrows(ClosedSelectorException.class,
                     () -> sel.select(k -> assertTrue(false), 1000));
    }
    @Test
    public void testClosedSelector3() throws Exception {
        Selector sel = Selector.open();
        sel.close();
        assertThrows(ClosedSelectorException.class,
                     () -> sel.selectNow(k -> assertTrue(false)));
    }

    /**
     * Test closing selector while in a selection operation
     */
    @Test
    public void testCloseDuringSelect() throws Exception {
        // select(Consumer)
        try (Selector sel = Selector.open()) {
            scheduleClose(sel, 3, SECONDS);
            int n = sel.select(k -> assertTrue(false));
            assertEquals(0, n);
            assertFalse(sel.isOpen());
        }

        // select(Consumer, timeout)
        try (Selector sel = Selector.open()) {
            long before = System.nanoTime();
            scheduleClose(sel, 3, SECONDS);
            long start = System.nanoTime();
            int n = sel.select(k -> assertTrue(false), 60*1000);
            long after = System.nanoTime();
            long selectDuration = (after - start) / 1000000;
            long scheduleDuration = (start - before) / 1000000;
            assertEquals(0, n);
            assertTrue(selectDuration > 2000 && selectDuration < 10*1000,
                    "select took " + selectDuration + " ms schedule took " +
                    scheduleDuration + " ms");
            assertFalse(sel.isOpen());
        }
    }

    /**
     * Test action closing selector
     */
    @Test
    public void testActionClosingSelector() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SourceChannel source = p.source();
            Pipe.SinkChannel sink = p.sink();
            source.configureBlocking(false);
            SelectionKey key = source.register(sel, SelectionKey.OP_READ);

            // write to sink to ensure that the source is readable
            sink.write(messageBuffer());

            // should relay ClosedSelectorException
            assertThrows(ClosedSelectorException.class,
                () -> sel.select(k -> {
                    assertTrue(k == key);
                    try {
                        sel.close();
                    } catch (IOException ioe) { }
                })
            );
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test that the action is invoked while synchronized on the selector and
     * its selected-key set.
     */
    @Test
    public void testLocks() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SourceChannel source = p.source();
            Pipe.SinkChannel sink = p.sink();
            source.configureBlocking(false);
            SelectionKey key = source.register(sel, SelectionKey.OP_READ);

            // write to sink to ensure that the source is readable
            sink.write(messageBuffer());

            // select(Consumer)
            sel.select(k -> {
                assertEquals(key, k);
                assertTrue(Thread.holdsLock(sel));
                assertFalse(Thread.holdsLock(sel.keys()));
                assertTrue(Thread.holdsLock(sel.selectedKeys()));
            });

            // select(Consumer, timeout)
            sel.select(k -> {
                assertEquals(key, k);
                assertTrue(Thread.holdsLock(sel));
                assertFalse(Thread.holdsLock(sel.keys()));
                assertTrue(Thread.holdsLock(sel.selectedKeys()));
            }, 1000L);

            // selectNow(Consumer)
            sel.selectNow(k -> {
                assertEquals(key, k);
                assertTrue(Thread.holdsLock(sel));
                assertFalse(Thread.holdsLock(sel.keys()));
                assertTrue(Thread.holdsLock(sel.selectedKeys()));
            });
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test that selection operations remove cancelled keys from the selector's
     * key and selected-key sets.
     */
    @Test
    public void testCancel() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SinkChannel sink = p.sink();
            Pipe.SourceChannel source = p.source();

            // write to sink to ensure that the source is readable
            sink.write(messageBuffer());

            source.configureBlocking(false);
            SelectionKey key1 = source.register(sel, SelectionKey.OP_READ);
            // make sure pipe source is readable before we do following checks.
            // this is sometime necessary on windows where pipe is implemented
            // as a pair of connected socket, so there is no guarantee that written
            // bytes on sink side is immediately available on source side.
            sel.select();

            sink.configureBlocking(false);
            SelectionKey key2 = sink.register(sel, SelectionKey.OP_WRITE);
            sel.selectNow();

            assertTrue(sel.keys().contains(key1));
            assertTrue(sel.keys().contains(key2));
            assertTrue(sel.selectedKeys().contains(key1));
            assertTrue(sel.selectedKeys().contains(key2));

            // cancel key1
            key1.cancel();
            int n = sel.selectNow(k -> assertTrue(k == key2));
            assertEquals(1, n);
            assertFalse(sel.keys().contains(key1));
            assertTrue(sel.keys().contains(key2));
            assertFalse(sel.selectedKeys().contains(key1));
            assertTrue(sel.selectedKeys().contains(key2));

            // cancel key2
            key2.cancel();
            n = sel.selectNow(k -> assertTrue(false));
            assertEquals(0, n);
            assertFalse(sel.keys().contains(key1));
            assertFalse(sel.keys().contains(key2));
            assertFalse(sel.selectedKeys().contains(key1));
            assertFalse(sel.selectedKeys().contains(key2));
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test an action invoking select()
     */
    @Test
    public void testReentrantSelect1() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SinkChannel sink = p.sink();
            Pipe.SourceChannel source = p.source();
            source.configureBlocking(false);
            source.register(sel, SelectionKey.OP_READ);

            // write to sink to ensure that the source is readable
            scheduleWrite(sink, messageBuffer(), 100, MILLISECONDS);

            int n = sel.select(k -> {
                try {
                    sel.select();
                    assertTrue(false);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                } catch (IllegalStateException expected) {
                }
            });
            assertEquals(1, n);
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test an action invoking selectNow()
     */
    @Test
    public void testReentrantSelect2() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SinkChannel sink = p.sink();
            Pipe.SourceChannel source = p.source();

            // write to sink to ensure that the source is readable
            scheduleWrite(sink, messageBuffer(), 100, MILLISECONDS);

            source.configureBlocking(false);
            source.register(sel, SelectionKey.OP_READ);
            int n = sel.select(k -> {
                try {
                    sel.selectNow();
                    assertTrue(false);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                } catch (IllegalStateException expected) {
                }
            });
            assertEquals(1, n);
        } finally {
            closePipe(p);
        }
    }

    /**
     * Test an action invoking select(Consumer)
     */
    @Test
    public void testReentrantSelect3() throws Exception {
        Pipe p = Pipe.open();
        try (Selector sel = Selector.open()) {
            Pipe.SinkChannel sink = p.sink();
            Pipe.SourceChannel source = p.source();

            // write to sink to ensure that the source is readable
            scheduleWrite(sink, messageBuffer(), 100, MILLISECONDS);

            source.configureBlocking(false);
            source.register(sel, SelectionKey.OP_READ);
            int n = sel.select(k -> {
                try {
                    sel.select(x -> assertTrue(false));
                    assertTrue(false);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                } catch (IllegalStateException expected) {
                }
            });
            assertEquals(1, n);
        } finally {
            closePipe(p);
        }
    }

    /**
     * Negative timeout
     */
    @Test
    public void testNegativeTimeout() throws Exception {
        try (Selector sel = Selector.open()) {
            assertThrows(IllegalArgumentException.class,
                         () -> sel.select(k -> { }, -1L));
        }
    }

    /**
     * Null action
     */
    @Test
    public void testNull1() throws Exception {
        try (Selector sel = Selector.open()) {
            assertThrows(NullPointerException.class,
                         () -> sel.select(null));
        }
    }
    @Test
    public void testNull2() throws Exception {
        try (Selector sel = Selector.open()) {
            assertThrows(NullPointerException.class,
                         () -> sel.select(null, 1000));
        }
    }
    @Test
    public void testNull3() throws Exception {
        try (Selector sel = Selector.open()) {
            assertThrows(NullPointerException.class,
                         () -> sel.selectNow(null));
        }
    }


    // -- support methods ---

    private static final ScheduledExecutorService POOL = Executors.newScheduledThreadPool(1);

    @AfterAll
    static void shutdownThreadPool() {
        POOL.shutdown();
    }

    void scheduleWakeup(Selector sel, long delay, TimeUnit unit) {
        POOL.schedule(() -> sel.wakeup(), delay, unit);
    }

    void scheduleInterrupt(Thread t, long delay, TimeUnit unit) {
        POOL.schedule(() -> t.interrupt(), delay, unit);
    }

    void scheduleClose(Closeable c, long delay, TimeUnit unit) {
        POOL.schedule(() -> {
            try {
                c.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }, delay, unit);
    }

    void scheduleWrite(WritableByteChannel sink, ByteBuffer buf, long delay, TimeUnit unit) {
        POOL.schedule(() -> {
            try {
                sink.write(buf);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }, delay, unit);
    }

    static void closePipe(Pipe p) {
        try { p.sink().close(); } catch (IOException ignore) { }
        try { p.source().close(); } catch (IOException ignore) { }
    }

    static ByteBuffer messageBuffer() {
        try {
            return ByteBuffer.wrap("message".getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check the duration of a task. The method will fail with an
     * AssertionError if the millisecond duration does not satisfy:
     *
     *     duration >= min && duration <= max
     *
     * Note that the inequalities are not strict, i.e., are inclusive.
     *
     * @param start start time, in milliseconds
     * @param min minimum expected duration, in milliseconds
     * @param max maximum expected duration, in milliseconds
     */
    private static void expectDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
    }
}
