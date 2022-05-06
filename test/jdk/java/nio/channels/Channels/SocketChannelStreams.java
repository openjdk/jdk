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
 * @bug 8279339
 * @run testng SocketChannelStreams
 * @summary Exercise InputStream/OutputStream returned by Channels.newXXXStream
 *    when channel is a SocketChannel
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.*;
import static org.testng.Assert.*;

@Test
public class SocketChannelStreams {
    private ScheduledExecutorService executor;

    @BeforeClass()
    public void init() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterClass
    public void finish() {
        executor.shutdown();
    }

    /**
     * Test read when bytes are available.
     */
    public void testRead1() throws Exception {
        withConnection((sc, peer) -> {
            write(peer, 99);
            int n = Channels.newInputStream(sc).read();
            assertEquals(n, 99);
        });
    }

    /**
     * Test read blocking before bytes are available.
     */
    public void testRead2() throws Exception {
        withConnection((sc, peer) -> {
            scheduleWrite(peer, 99, 1000);
            int n = Channels.newInputStream(sc).read();
            assertEquals(n, 99);
        });
    }

    /**
     * Test read after peer has closed connection.
     */
    public void testRead3() throws Exception {
        withConnection((sc, peer) -> {
            peer.close();
            int n = Channels.newInputStream(sc).read();
            assertEquals(n, -1);
        });
    }

    /**
     * Test read blocking before peer closes connection.
     */
    public void testRead4() throws Exception {
        withConnection((sc, peer) -> {
            scheduleClose(peer, 1000);
            int n = Channels.newInputStream(sc).read();
            assertEquals(n, -1);
        });
    }

    /**
     * Test async close of channel when thread blocked in read.
     */
    public void testRead5() throws Exception {
        withConnection((sc, peer) -> {
            scheduleClose(sc, 2000);
            InputStream in = Channels.newInputStream(sc);
            expectThrows(IOException.class, () -> in.read());
        });
    }

    /**
     * Test async close of input stream, when thread blocked in read.
     */
    public void testRead6() throws Exception {
        withConnection((sc, peer) -> {
            InputStream in = Channels.newInputStream(sc);
            scheduleClose(in, 2000);
            expectThrows(IOException.class, () -> in.read());
        });
    }

    /**
     * Test interrupt status set before read.
     */
    public void testRead7() throws Exception {
        withConnection((sc, peer) -> {
            Thread.currentThread().interrupt();
            try {
                InputStream in = Channels.newInputStream(sc);
                expectThrows(IOException.class, () -> in.read());
            } finally {
                Thread.interrupted();  // clear interrupt
            }
            assertFalse(sc.isOpen());
        });
    }

    /**
     * Test interrupt of thread blocked in read.
     */
    public void testRead8() throws Exception {
        withConnection((sc, peer) -> {
            Future<?> interrupter = scheduleInterrupt(Thread.currentThread(), 2000);
            try {
                InputStream in = Channels.newInputStream(sc);
                expectThrows(IOException.class, () -> in.read());
            } finally {
                interrupter.cancel(true);
                Thread.interrupted();  // clear interrupt
            }
            assertFalse(sc.isOpen());
        });
    }

    /**
     * Test that read is untimed when SO_TIMEOUT is set on the Socket adaptor.
     */
    public void testRead9() throws Exception {
        withConnection((sc, peer) -> {
            sc.socket().setSoTimeout(100);
            scheduleWrite(peer, 99, 2000);
            // read should block until bytes are available
            int b = Channels.newInputStream(sc).read();
            assertTrue(b == 99);
        });
    }

    /**
     * Test write.
     */
    public void testWrite1() throws Exception {
        withConnection((sc, peer) -> {
            OutputStream out = Channels.newOutputStream(sc);
            out.write(99);
            int n = read(peer);
            assertEquals(n, 99);
        });
    }

    /**
     * Test async close of channel when thread blocked in write.
     */
    public void testWrite2() throws Exception {
        withConnection((sc, peer) -> {
            scheduleClose(sc, 2000);
            expectThrows(IOException.class, () -> {
                OutputStream out = Channels.newOutputStream(sc);
                byte[] data = new byte[64*1000];
                while (true) {
                    out.write(data);
                }
            });
        });
    }

    /**
     * Test async close of output stream when thread blocked in write.
     */
    public void testWrite3() throws Exception {
        withConnection((sc, peer) -> {
            OutputStream out = Channels.newOutputStream(sc);
            scheduleClose(out, 2000);
            expectThrows(IOException.class, () -> {
                byte[] data = new byte[64*1000];
                while (true) {
                    out.write(data);
                }
            });
        });
    }

    /**
     * Test interrupt status set before write.
     */
    public void testWrite4() throws Exception {
        withConnection((sc, peer) -> {
            Thread.currentThread().interrupt();
            try {
                OutputStream out = Channels.newOutputStream(sc);
                expectThrows(IOException.class, () -> out.write(99));
            } finally {
                Thread.interrupted();  // clear interrupt
            }
            assertFalse(sc.isOpen());
        });
    }

    /**
     * Test interrupt of thread blocked in write.
     */
    public void testWrite5() throws Exception {
        withConnection((sc, peer) -> {
            Future<?> interrupter = scheduleInterrupt(Thread.currentThread(), 2000);
            try {
                expectThrows(IOException.class, () -> {
                    OutputStream out = Channels.newOutputStream(sc);
                    byte[] data = new byte[64*1000];
                    while (true) {
                        out.write(data);
                    }
                });
            } finally {
                interrupter.cancel(true);
                Thread.interrupted();  // clear interrupt
            }
            assertFalse(sc.isOpen());
        });
    }

    /**
     * Test read when another thread is blocked in write. The read should
     * complete immediately.
     */
    public void testConcurrentReadWrite1() throws Exception {
        withConnection((sc, peer) -> {
            InputStream in = Channels.newInputStream(sc);
            OutputStream out = Channels.newOutputStream(sc);

            // block thread in write
            fork(() -> {
                var data = new byte[64*1024];
                for (;;) {
                    out.write(data);
                }
            });
            Thread.sleep(1000); // give writer time to block

            // test read, should not be blocked by writer thread
            write(peer, 99);
            int n = in.read();
            assertEquals(n, 99);
        });
    }

    /**
     * Test read when another thread is blocked in write. The read should
     * block until bytes are available.
     */
    public void testConcurrentReadWrite2() throws Exception {
        withConnection((sc, peer) -> {
            InputStream in = Channels.newInputStream(sc);
            OutputStream out = Channels.newOutputStream(sc);

            // block thread in write
            fork(() -> {
                var data = new byte[64*1024];
                for (;;) {
                    out.write(data);
                }
            });
            Thread.sleep(1000); // give writer time to block

            // test read, should not be blocked by writer thread
            scheduleWrite(peer, 99, 500);
            int n = in.read();
            assertEquals(n, 99);
        });
    }

    /**
     * Test writing when another thread is blocked in read.
     */
    public void testConcurrentReadWrite3() throws Exception {
        withConnection((sc, peer) -> {
            InputStream in = Channels.newInputStream(sc);
            OutputStream out = Channels.newOutputStream(sc);

            // block thread in read
            fork(() -> {
                in.read();
            });
            Thread.sleep(100); // give reader time to block

            // test write, should not be blocked by reader thread
            out.write(99);
            int n = read(peer);
            assertEquals(n, 99);
        });
    }

    /**
     * Test read/write when channel configured non-blocking.
     */
    public void testIllegalBlockingMode() throws Exception {
        withConnection((sc, peer) -> {
            InputStream in = Channels.newInputStream(sc);
            OutputStream out = Channels.newOutputStream(sc);

            sc.configureBlocking(false);
            expectThrows(IllegalBlockingModeException.class, () -> in.read());
            expectThrows(IllegalBlockingModeException.class, () -> out.write(99));
        });
    }

    /**
     * Test NullPointerException.
     */
    public void testNullPointerException() throws Exception {
        withConnection((sc, peer) -> {
            InputStream in = Channels.newInputStream(sc);
            OutputStream out = Channels.newOutputStream(sc);

            expectThrows(NullPointerException.class, () -> in.read(null));
            expectThrows(NullPointerException.class, () -> in.read(null, 0, 0));

            expectThrows(NullPointerException.class, () -> out.write(null));
            expectThrows(NullPointerException.class, () -> out.write(null, 0, 0));
        });
    }

    /**
     * Test IndexOutOfBoundsException.
     */
    public void testIndexOutOfBoundsException() throws Exception {
        withConnection((sc, peer) -> {
            InputStream in = Channels.newInputStream(sc);
            OutputStream out = Channels.newOutputStream(sc);
            byte[] ba = new byte[100];

            expectThrows(IndexOutOfBoundsException.class, () -> in.read(ba, -1, 1));
            expectThrows(IndexOutOfBoundsException.class, () -> in.read(ba, 0, -1));
            expectThrows(IndexOutOfBoundsException.class, () -> in.read(ba, 0, 1000));
            expectThrows(IndexOutOfBoundsException.class, () -> in.read(ba, 1, 100));

            expectThrows(IndexOutOfBoundsException.class, () -> out.write(ba, -1, 1));
            expectThrows(IndexOutOfBoundsException.class, () -> out.write(ba, 0, -1));
            expectThrows(IndexOutOfBoundsException.class, () -> out.write(ba, 0, 1000));
            expectThrows(IndexOutOfBoundsException.class, () -> out.write(ba, 1, 100));
        });
    }

    // -- test infrastructure --

    private interface ThrowingTask {
        void run() throws Exception;
    }

    private interface ThrowingBiConsumer<T, U> {
        void accept(T t, U u) throws Exception;
    }

    /**
     * Invokes the consumer with a connected pair of socket channels.
     */
    private static void withConnection(ThrowingBiConsumer<SocketChannel, SocketChannel> consumer)
        throws Exception
    {
        var loopback = InetAddress.getLoopbackAddress();
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress(loopback, 0));
            try (SocketChannel sc = SocketChannel.open(listener.getLocalAddress())) {
                try (SocketChannel peer = listener.accept()) {
                    consumer.accept(sc, peer);
                }
            }
        }
    }

    /**
     * Forks a thread to execute the given task.
     */
    private Future<?> fork(ThrowingTask task) {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            return pool.submit(() -> {
                task.run();
                return null;
            });
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Read a byte from the given socket channel.
     */
    private int read(SocketChannel sc) throws IOException {
        return sc.socket().getInputStream().read();
    }

    /**
     * Write a byte to the given socket channel.
     */
    private void write(SocketChannel sc, int b) throws IOException {
        sc.socket().getOutputStream().write(b);
    }

    /**
     * Writes the given data to the socket channel after a delay.
     */
    private Future<?> scheduleWrite(SocketChannel sc, byte[] data, long delay) {
        return schedule(() -> {
            try {
                sc.socket().getOutputStream().write(data);
            } catch (IOException ioe) { }
        }, delay);
    }

    /**
     * Writes a byte to the socket channel after a delay.
     */
    private Future<?> scheduleWrite(SocketChannel sc, int b, long delay) {
        return scheduleWrite(sc, new byte[] { (byte)b }, delay);
    }

    /**
     * Closes the given object after a delay.
     */
    private Future<?> scheduleClose(Closeable c, long delay) {
        return schedule(() -> {
            try {
                c.close();
            } catch (IOException ioe) { }
        }, delay);
    }

    /**
     * Interrupts the given Thread after a delay.
     */
    private Future<?> scheduleInterrupt(Thread t, long delay) {
        return schedule(() -> t.interrupt(), delay);
    }

    /**
     * Schedules the given task to run after a delay.
     */
    private Future<?> schedule(Runnable task, long delay) {
        return executor.schedule(task, delay, TimeUnit.MILLISECONDS);
    }
}
