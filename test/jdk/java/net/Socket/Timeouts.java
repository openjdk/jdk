/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8221481 8358496
 * @library /test/lib
 * @build jdk.test.lib.Utils
 * @run junit/othervm/timeout=180 Timeouts
 * @summary Test Socket timeouts
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import jdk.test.lib.Utils;

class Timeouts {

    /**
     * Test timed connect where connection is established.
     */
    @Test
    void testTimedConnect1() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            try (Socket s = new Socket()) {
                s.connect(ss.getLocalSocketAddress(), 2000);
            }
        }
    }

    /**
     * Test timed connect where connection is refused.
     */
    @Test
    void testTimedConnect2() throws IOException {
        try (Socket s = new Socket()) {
            SocketAddress remote = Utils.refusingEndpoint();
            assertThrows(ConnectException.class,  () -> s.connect(remote, 10000));
        }
    }

    /**
     * Test connect with a timeout of Integer.MAX_VALUE.
     */
    @Test
    void testTimedConnect3() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            try (Socket s = new Socket()) {
                s.connect(ss.getLocalSocketAddress(), Integer.MAX_VALUE);
            }
        }
    }

    /**
     * Test connect with a negative timeout.
     */
    @Test
    void testTimedConnect4() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            try (Socket s = new Socket()) {
                assertThrows(IllegalArgumentException.class,
                             () -> s.connect(ss.getLocalSocketAddress(), -1));
            }
        }
    }

    /**
     * Test timed read where the read succeeds immediately.
     */
    @Test
    void testTimedRead1() throws IOException {
        withConnection((s1, s2) -> {
            s1.getOutputStream().write(99);
            s2.setSoTimeout(30*1000);
            int b = s2.getInputStream().read();
            assertEquals(99, b);
        });
    }

    /**
     * Test timed read where the read succeeds after a delay.
     */
    @Test
    void testTimedRead2() throws IOException {
        withConnection((s1, s2) -> {
            scheduleWrite(s1.getOutputStream(), 99, 2000);
            s2.setSoTimeout(30*1000);
            int b = s2.getInputStream().read();
            assertEquals(99, b);
        });
    }

    /**
     * Test timed read where the read times out.
     */
    @Test
    void testTimedRead3() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            long startMillis = millisTime();
            assertThrows(SocketTimeoutException.class, () -> s2.getInputStream().read());
            int timeout = s2.getSoTimeout();
            checkDuration(startMillis, timeout-100, timeout+20_000);
        });
    }

    /**
     * Test timed read that succeeds after a previous read has timed out.
     */
    @Test
    void testTimedRead4() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            assertThrows(SocketTimeoutException.class, () -> s2.getInputStream().read());
            s1.getOutputStream().write(99);
            int b = s2.getInputStream().read();
            assertEquals(99, b);
        });
    }

    /**
     * Test timed read that succeeds after a previous read has timed out and
     * after a short delay.
     */
    @Test
    void testTimedRead5() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            assertThrows(SocketTimeoutException.class, () -> s2.getInputStream().read());
            s2.setSoTimeout(30*3000);
            scheduleWrite(s1.getOutputStream(), 99, 2000);
            int b = s2.getInputStream().read();
            assertEquals(99, b);
        });
    }

    /**
     * Test untimed read that succeeds after a previous read has timed out.
     */
    @Test
    void testTimedRead6() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            assertThrows(SocketTimeoutException.class, () -> s2.getInputStream().read());
            s1.getOutputStream().write(99);
            s2.setSoTimeout(0);
            int b = s2.getInputStream().read();
            assertEquals(99, b);
        });
    }

    /**
     * Test untimed read that succeeds after a previous read has timed out and
     * after a short delay.
     */
    @Test
    void testTimedRead7() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            assertThrows(SocketTimeoutException.class, () -> s2.getInputStream().read());
            scheduleWrite(s1.getOutputStream(), 99, 2000);
            s2.setSoTimeout(0);
            int b = s2.getInputStream().read();
            assertEquals(99, b);
        });
    }

    /**
     * Test async close of timed read.
     */
    @Test
    void testTimedRead8() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(30*1000);
            scheduleClose(s2, 2000);
            assertThrows(SocketException.class, () -> s2.getInputStream().read());
        });
    }

    /**
     * Test read with a timeout of Integer.MAX_VALUE.
     */
    @Test
    void testTimedRead9() throws IOException {
        withConnection((s1, s2) -> {
            scheduleWrite(s1.getOutputStream(), 99, 2000);
            s2.setSoTimeout(Integer.MAX_VALUE);
            int b = s2.getInputStream().read();
            assertEquals(99, b);
        });
    }

    /**
     * Test 100 threads concurrently reading the same Socket with a timeout of 2s.
     * Each read should throw SocketTimeoutException after 2s, not 2s for the first,
     * 4s for the second, 6s for the third, up to 200s for the last thread.
     */
    @Test
    void testTimedRead10() throws Exception {
        var futures = new ArrayList<Future<?>>();
        withConnection((_, s) -> {
            s.setSoTimeout(2000);
            Callable<?> timedReadTask = () -> {
                long startMillis = millisTime();
                assertThrows(SocketTimeoutException.class,
                        () -> s.getInputStream().read());
                int timeout = s.getSoTimeout();
                checkDuration(startMillis, timeout-100, timeout+20_000);
                return null;
            };
            // start 100 virtual threads to read from the socket
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 100; i++) {
                    Future<?> future = executor.submit(timedReadTask);
                    futures.add(future);
                }
            }
        });
        for (Future<?> future : futures) {
            future.get();
        }
    }

    /**
     * Test writing after a timed read.
     */
    @Test
    void testTimedWrite1() throws IOException {
        withConnection((s1, s2) -> {
            s1.getOutputStream().write(99);
            s2.setSoTimeout(3000);
            int b = s2.getInputStream().read();
            assertEquals(99, b);

            // schedule thread to read s1 to EOF
            scheduleReadToEOF(s1.getInputStream(), 3000);

            // write a lot so that write blocks
            byte[] data = new byte[128*1024];
            for (int i = 0; i < 100; i++) {
                s2.getOutputStream().write(data);
            }
        });
    }

    /**
     * Test async close of writer (after a timed read).
     */
    @Test
    void testTimedWrite2() throws IOException {
        withConnection((s1, s2) -> {
            s1.getOutputStream().write(99);
            s2.setSoTimeout(3000);
            int b = s2.getInputStream().read();
            assertEquals(99, b);

            // schedule s2 to be closed
            scheduleClose(s2, 3000);

            // write a lot so that write blocks
            byte[] data = new byte[128*1024];
            try {
                while (true) {
                    s2.getOutputStream().write(data);
                }
            } catch (SocketException expected) { }
        });
    }

    /**
     * Test timed accept where a connection is established immediately.
     */
    @Test
    void testTimedAccept1() throws IOException {
        Socket s1 = null;
        Socket s2 = null;
        try (ServerSocket ss = boundServerSocket()) {
            s1 = new Socket();
            s1.connect(ss.getLocalSocketAddress());
            ss.setSoTimeout(30*1000);
            s2 = ss.accept();
        } finally {
            if (s1 != null) s1.close();
            if (s2 != null) s2.close();
        }
    }

    /**
     * Test timed accept where a connection is established after a short delay.
     */
    @Test
    void testTimedAccept2() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(30*1000);
            scheduleConnect(ss.getLocalSocketAddress(), 2000);
            Socket s = ss.accept();
            s.close();
        }
    }

    /**
     * Test timed accept where the accept times out
     */
    @Test
    void testTimedAccept3() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(2000);
            long startMillis = millisTime();
            try {
                Socket s = ss.accept();
                s.close();
                fail();
            } catch (SocketTimeoutException expected) {
                int timeout = ss.getSoTimeout();
                checkDuration(startMillis, timeout-100, timeout+20_000);
            }
        }
    }

    /**
     * Test timed accept where a connection is established immediately after a
     * previous accept timed out.
     */
    @Test
    void testTimedAccept4() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(2000);
            try {
                Socket s = ss.accept();
                s.close();
                fail();
            } catch (SocketTimeoutException expected) { }
            try (Socket s1 = new Socket()) {
                s1.connect(ss.getLocalSocketAddress());
                Socket s2 = ss.accept();
                s2.close();
            }
        }
    }

    /**
     * Test untimed accept where a connection is established after a previous
     * accept timed out.
     */
    @Test
    void testTimedAccept5() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(2000);
            try {
                Socket s = ss.accept();
                s.close();
                fail();
            } catch (SocketTimeoutException expected) { }
            ss.setSoTimeout(0);
            try (Socket s1 = new Socket()) {
                s1.connect(ss.getLocalSocketAddress());
                Socket s2 = ss.accept();
                s2.close();
            }
        }
    }

    /**
     * Test untimed accept where a connection is established after a previous
     * accept timed out and after a short delay.
     */
    @Test
    void testTimedAccept6() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(2000);
            try {
                Socket s = ss.accept();
                s.close();
                fail();
            } catch (SocketTimeoutException expected) { }
            ss.setSoTimeout(0);
            scheduleConnect(ss.getLocalSocketAddress(), 2000);
            Socket s = ss.accept();
            s.close();
        }
    }

    /**
     * Test async close of a timed accept.
     */
    @Test
    void testTimedAccept7() throws IOException {
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(30*1000);
            long delay = 2000;
            scheduleClose(ss, delay);
            long startMillis = millisTime();
            try {
                ss.accept().close();
                fail();
            } catch (SocketException expected) {
                checkDuration(startMillis, delay-100, delay+20_000);
            }
        }
    }

    /**
     * Test timed accept with the thread interrupt status set.
     */
    @Test
    void testTimedAccept8() throws IOException {
        assumeFalse(Thread.currentThread().isVirtual(), "Main test is a virtual thread");
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(2000);
            Thread.currentThread().interrupt();
            long startMillis = millisTime();
            try {
                Socket s = ss.accept();
                s.close();
                fail();
            } catch (SocketTimeoutException expected) {
                // accept should have blocked for 2 seconds
                int timeout = ss.getSoTimeout();
                checkDuration(startMillis, timeout-100, timeout+20_000);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt status
            }
        }
    }

    /**
     * Test interrupt of thread blocked in timed accept.
     */
    @Test
    void testTimedAccept9() throws IOException {
        assumeFalse(Thread.currentThread().isVirtual(), "Main test is a virtual thread");
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(4000);
            // interrupt thread after 1 second
            Future<?> interrupter = scheduleInterrupt(Thread.currentThread(), 1000);
            long startMillis = millisTime();
            try {
                Socket s = ss.accept();   // should block for 4 seconds
                s.close();
                fail();
            } catch (SocketTimeoutException expected) {
                // accept should have blocked for 4 seconds
                int timeout = ss.getSoTimeout();
                checkDuration(startMillis, timeout-100, timeout+20_000);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                interrupter.cancel(true);
                Thread.interrupted(); // clear interrupt status
            }
        }
    }

    /**
     * Test two threads blocked in timed accept where no connection is established.
     */
    @Test
    void testTimedAccept10() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(4000);

            long startMillis = millisTime();

            Future<Socket> result1 = pool.submit(ss::accept);
            Future<Socket> result2 = pool.submit(ss::accept);

            // both tasks should complete with SocketTimeoutException
            Throwable e = assertThrows(ExecutionException.class, result1::get);
            assertTrue(e.getCause() instanceof SocketTimeoutException);
            e = assertThrows(ExecutionException.class, result2::get);
            assertTrue(e.getCause() instanceof SocketTimeoutException);

            // should get here in 4 seconds, not 8 seconds
            int timeout = ss.getSoTimeout();
            checkDuration(startMillis, timeout-100, timeout+20_000);
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Test two threads blocked in timed accept where one connection is established.
     */
    @Test
    void testTimedAccept11() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (ServerSocket ss = boundServerSocket()) {
            ss.setSoTimeout(4000);

            long startMillis = millisTime();

            Future<Socket> result1 = pool.submit(ss::accept);
            Future<Socket> result2 = pool.submit(ss::accept);

            // establish connection after 2 seconds
            scheduleConnect(ss.getLocalSocketAddress(), 2000);

            // one task should have accepted the connection, the other should
            // have completed with SocketTimeoutException
            Socket s1 = null;
            try {
                s1 = result1.get();
                s1.close();
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof SocketTimeoutException);
            }
            Socket s2 = null;
            try {
                s2 = result2.get();
                s2.close();
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof SocketTimeoutException);
            }
            assertTrue((s1 != null) ^ (s2 != null));

            // should get here in 4 seconds, not 8 seconds
            int timeout = ss.getSoTimeout();
            checkDuration(startMillis, timeout-100, timeout+20_000);
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Test Socket setSoTimeout with a negative timeout.
     */
    @Test
    void testBadTimeout1() throws IOException {
        try (Socket s = new Socket()) {
            assertThrows(IllegalArgumentException.class, () -> s.setSoTimeout(-1));
        }
    }

    /**
     * Test ServerSocket setSoTimeout with a negative timeout.
     */
    @Test
    void testBadTimeout2() throws IOException {
        try (ServerSocket ss = new ServerSocket()) {
            assertThrows(IllegalArgumentException.class, () -> ss.setSoTimeout(-1));
        }
    }

    /**
     * Returns a ServerSocket bound to a port on the loopback address.
     */
    static ServerSocket boundServerSocket() throws IOException {
        var loopback = InetAddress.getLoopbackAddress();
        ServerSocket ss = new ServerSocket();
        ss.bind(new InetSocketAddress(loopback, 0));
        return ss;
    }

    /**
     * An operation that accepts two arguments and may throw IOException.
     */
    interface ThrowingBiConsumer<T, U> {
        void accept(T t, U u) throws IOException;
    }

    /**
     * Invokes the consumer with a connected pair of sockets.
     */
    static void withConnection(ThrowingBiConsumer<Socket, Socket> consumer)
        throws IOException
    {
        Socket s1 = null;
        Socket s2 = null;
        try (ServerSocket ss = boundServerSocket()) {
            s1 = new Socket();
            s1.connect(ss.getLocalSocketAddress());
            s2 = ss.accept();
            consumer.accept(s1, s2);
        } finally {
            if (s1 != null) s1.close();
            if (s2 != null) s2.close();
        }
    }

    /**
     * Schedule c to be closed after a delay.
     */
    static void scheduleClose(Closeable c, long delay) {
        schedule(() -> {
            try {
                c.close();
            } catch (IOException ioe) { }
        }, delay);
    }

    /**
     * Schedule thread to be interrupted after a delay.
     */
    static Future<?> scheduleInterrupt(Thread thread, long delay) {
        return schedule(() -> thread.interrupt(), delay);
    }

    /**
     * Schedule a thread to connect to the given end point after a delay.
     */
    static void scheduleConnect(SocketAddress remote, long delay) {
        schedule(() -> {
            try (Socket s = new Socket()) {
                s.connect(remote);
            } catch (IOException ioe) { }
        }, delay);
    }

    /**
     * Schedule a thread to read to EOF after a delay.
     */
    static void scheduleReadToEOF(InputStream in, long delay) {
        schedule(() -> {
            byte[] bytes = new byte[8192];
            try {
                while (in.read(bytes) != -1) { }
            } catch (IOException ioe) { }
        }, delay);
    }

    /**
     * Schedule a thread to write after a delay.
     */
    static void scheduleWrite(OutputStream out, byte[] data, long delay) {
        schedule(() -> {
            try {
                out.write(data);
            } catch (IOException ioe) { }
        }, delay);
    }
    static void scheduleWrite(OutputStream out, int b, long delay) {
        scheduleWrite(out, new byte[] { (byte)b }, delay);
    }

    static Future<?> schedule(Runnable task, long delay) {
        return ForkJoinPool.commonPool().schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check the duration of a task.
     * @param start start time, in milliseconds
     * @param min minimum expected duration, in milliseconds
     * @param max maximum expected duration, in milliseconds
     * @return the duration (now - start), in milliseconds
     */
    private static long checkDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
        return duration;
    }
}
