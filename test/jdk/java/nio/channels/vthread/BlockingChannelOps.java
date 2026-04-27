/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284161
 * @summary Test virtual threads doing blocking I/O on NIO channels
 * @library /test/lib
 * @run junit/timeout=480 BlockingChannelOps
 */

/*
 * @test id=poller-modes
 * @requires (os.family == "linux") | (os.family == "mac")
 * @library /test/lib
 * @run junit/othervm/timeout=480 -Djdk.pollerMode=1 BlockingChannelOps
 * @run junit/othervm/timeout=480 -Djdk.pollerMode=2 BlockingChannelOps
 * @run junit/othervm/timeout=480 -Djdk.pollerMode=3 BlockingChannelOps
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @library /test/lib
 * @run junit/othervm/timeout=480 -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations BlockingChannelOps
 */

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.locks.LockSupport;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class BlockingChannelOps {

    /**
     * SocketChannel read/write, no blocking.
     */
    @Test
    void testSocketChannelReadWrite1() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // write to sc1
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                int n = sc1.write(bb);
                assertTrue(n > 0);

                // read from sc2 should not block
                bb = ByteBuffer.allocate(10);
                n = sc2.read(bb);
                assertTrue(n > 0);
                assertTrue(bb.get(0) == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in SocketChannel read.
     */
    @Test
    void testSocketChannelRead() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // write to sc1 when current thread blocks in sc2.read
                ByteBuffer bb1 = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                runAfterParkedAsync(() -> sc1.write(bb1));

                // read from sc2 should block
                ByteBuffer bb2 = ByteBuffer.allocate(10);
                int n = sc2.read(bb2);
                assertTrue(n > 0);
                assertTrue(bb2.get(0) == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in SocketChannel write.
     */
    @Test
    void testSocketChannelWrite() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // read from sc2 to EOF when current thread blocks in sc1.write
                Thread reader = runAfterParkedAsync(() -> readToEOF(sc2));

                // write to sc1 should block
                ByteBuffer bb = ByteBuffer.allocate(100*1024);
                for (int i=0; i<1000; i++) {
                    int n = sc1.write(bb);
                    assertTrue(n > 0);
                    bb.clear();
                }
                sc1.close();

                // wait for reader to finish
                reader.join();
            }
        });
    }

    /**
     * SocketChannel close while virtual thread blocked in read.
     */
    @Test
    void testSocketChannelReadAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();
                runAfterParkedAsync(sc::close);
                try {
                    int n = sc.read(ByteBuffer.allocate(100));
                    fail("read returned " + n);
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * SocketChannel shutdownInput while virtual thread blocked in read.
     */
    @Test
    void testSocketChannelReadAsyncShutdownInput() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();
                runAfterParkedAsync(sc::shutdownInput);
                int n = sc.read(ByteBuffer.allocate(100));
                assertEquals(-1, n);
                assertTrue(sc.isOpen());
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in SocketChannel read.
     */
    @Test
    void testSocketChannelReadInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();

                // interrupt current thread when it blocks in read
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                try {
                    int n = sc.read(ByteBuffer.allocate(100));
                    fail("read returned " + n);
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * SocketChannel close while virtual thread blocked in write.
     */
    @Test
    void testSocketChannelWriteAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            boolean done = false;
            while (!done) {
                try (var connection = new Connection()) {
                    SocketChannel sc = connection.channel1();

                    // close sc when current thread blocks in write
                    runAfterParkedAsync(sc::close, true);

                    // write until channel is closed
                    try {
                        ByteBuffer bb = ByteBuffer.allocate(100*1024);
                        for (;;) {
                            int n = sc.write(bb);
                            assertTrue(n > 0);
                            bb.clear();
                        }
                    } catch (AsynchronousCloseException expected) {
                        // closed when blocked in write
                        done = true;
                    } catch (ClosedChannelException e) {
                        // closed but not blocked in write, need to retry test
                        System.err.format("%s, need to retry!%n", e);
                    }
                }
            }
        });
    }


    /**
     * SocketChannel shutdownOutput while virtual thread blocked in write.
     */
    @Test
    void testSocketChannelWriteAsyncShutdownOutput() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();

                // shutdown output when current thread blocks in write
                runAfterParkedAsync(sc::shutdownOutput);
                try {
                    ByteBuffer bb = ByteBuffer.allocate(100*1024);
                    for (;;) {
                        int n = sc.write(bb);
                        assertTrue(n > 0);
                        bb.clear();
                    }
                } catch (ClosedChannelException e) {
                    // expected
                }
                assertTrue(sc.isOpen());
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in SocketChannel write.
     */
    @Test
    void testSocketChannelWriteInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            boolean done = false;
            while (!done) {
                try (var connection = new Connection()) {
                    SocketChannel sc = connection.channel1();

                    // interrupt current thread when it blocks in write
                    Thread thisThread = Thread.currentThread();
                    runAfterParkedAsync(thisThread::interrupt, true);

                    // write until channel is closed
                    try {
                        ByteBuffer bb = ByteBuffer.allocate(100*1024);
                        for (;;) {
                            int n = sc.write(bb);
                            assertTrue(n > 0);
                            bb.clear();
                        }
                    } catch (ClosedByInterruptException e) {
                        // closed when blocked in write
                        assertTrue(Thread.interrupted());
                        done = true;
                    } catch (ClosedChannelException e) {
                        // closed but not blocked in write, need to retry test
                        System.err.format("%s, need to retry!%n", e);
                    }
                }
            }
        });
    }

    /**
     * Virtual thread blocks in SocketChannel adaptor read.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 60_000 })
    void testSocketAdaptorRead(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // write to sc1 when currnet thread blocks reading from sc2
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                runAfterParkedAsync(() -> sc1.write(bb));

                // read from sc2 should block
                byte[] array = new byte[100];
                if (timeout > 0)
                    sc2.socket().setSoTimeout(timeout);
                int n = sc2.socket().getInputStream().read(array);
                assertTrue(n > 0);
                assertTrue(array[0] == 'X');
            }
        });
    }

    /**
     * ServerSocketChannel accept, no blocking.
     */
    @Test
    void testServerSocketChannelAccept1() throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                var sc1 = SocketChannel.open(ssc.getLocalAddress());
                // accept should not block
                var sc2 = ssc.accept();
                sc1.close();
                sc2.close();
            }
        });
    }

    /**
     * Virtual thread blocks in ServerSocketChannel accept.
     */
    @Test
    void testServerSocketChannelAccept2() throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                var sc1 = SocketChannel.open();

                // connect when current thread when it blocks in accept
                runAfterParkedAsync(() -> sc1.connect(ssc.getLocalAddress()));

                // accept should block
                var sc2 = ssc.accept();
                sc1.close();
                sc2.close();
            }
        });
    }

    /**
     * SeverSocketChannel close while virtual thread blocked in accept.
     */
    @Test
    void testServerSocketChannelAcceptAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lh, 0));
                runAfterParkedAsync(ssc::close);
                try {
                    SocketChannel sc = ssc.accept();
                    sc.close();
                    fail("connection accepted???");
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in ServerSocketChannel accept.
     */
    @Test
    void testServerSocketChannelAcceptInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lh, 0));

                // interrupt current thread when it blocks in accept
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                try {
                    SocketChannel sc = ssc.accept();
                    sc.close();
                    fail("connection accepted???");
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Virtual thread blocks in ServerSocketChannel adaptor accept.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 60_000 })
    void testSocketChannelAdaptorAccept(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                var sc = SocketChannel.open();

                // interrupt current thread when it blocks in accept
                runAfterParkedAsync(() -> sc.connect(ssc.getLocalAddress()));

                // accept should block
                if (timeout > 0)
                    ssc.socket().setSoTimeout(timeout);
                Socket s = ssc.socket().accept();
                sc.close();
                s.close();
            }
        });
    }

    /**
     * DatagramChannel receive/send, no blocking.
     */
    @Test
    void testDatagramChannelSendReceive1() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc1 = DatagramChannel.open();
                 DatagramChannel dc2 = DatagramChannel.open()) {

                InetAddress lh = InetAddress.getLoopbackAddress();
                dc2.bind(new InetSocketAddress(lh, 0));

                // send should not block
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                int n = dc1.send(bb, dc2.getLocalAddress());
                assertTrue(n > 0);

                // receive should not block
                bb = ByteBuffer.allocate(10);
                dc2.receive(bb);
                assertTrue(bb.get(0) == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in DatagramChannel receive.
     */
    @Test
    void testDatagramChannelSendReceive2() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc1 = DatagramChannel.open();
                 DatagramChannel dc2 = DatagramChannel.open()) {

                InetAddress lh = InetAddress.getLoopbackAddress();
                dc2.bind(new InetSocketAddress(lh, 0));

                // send from dc1 when current thread blocked in dc2.receive
                ByteBuffer bb1 = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                runAfterParkedAsync(() -> dc1.send(bb1, dc2.getLocalAddress()));

                // read from dc2 should block
                ByteBuffer bb2 = ByteBuffer.allocate(10);
                dc2.receive(bb2);
                assertTrue(bb2.get(0) == 'X');
            }
        });
    }

    /**
     * DatagramChannel close while virtual thread blocked in receive.
     */
    @Test
    void testDatagramChannelReceiveAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));
                runAfterParkedAsync(dc::close);
                try {
                    dc.receive(ByteBuffer.allocate(100));
                    fail("receive returned");
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in DatagramChannel receive.
     */
    @Test
    void testDatagramChannelReceiveInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));

                // interrupt current thread when it blocks in receive
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                try {
                    dc.receive(ByteBuffer.allocate(100));
                    fail("receive returned");
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Virtual thread blocks in DatagramSocket adaptor receive.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 60_000 })
    void testDatagramSocketAdaptorReceive(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc1 = DatagramChannel.open();
                 DatagramChannel dc2 = DatagramChannel.open()) {

                InetAddress lh = InetAddress.getLoopbackAddress();
                dc2.bind(new InetSocketAddress(lh, 0));

                // send from dc1 when current thread blocks in dc2 receive
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                runAfterParkedAsync(() -> dc1.send(bb, dc2.getLocalAddress()));

                // receive should block
                byte[] array = new byte[100];
                DatagramPacket p = new DatagramPacket(array, 0, array.length);
                if (timeout > 0)
                    dc2.socket().setSoTimeout(timeout);
                dc2.socket().receive(p);
                assertTrue(p.getLength() == 3 && array[0] == 'X');
            }
        });
    }

    /**
     * DatagramChannel close while virtual thread blocked in adaptor receive.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 60_000 })
    void testDatagramSocketAdaptorReceiveAsyncClose(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));

                byte[] array = new byte[100];
                DatagramPacket p = new DatagramPacket(array, 0, array.length);
                if (timeout > 0)
                    dc.socket().setSoTimeout(timeout);

                // close channel/socket when current thread blocks in receive
                runAfterParkedAsync(dc::close);

                assertThrows(SocketException.class, () -> dc.socket().receive(p));
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in DatagramSocket adaptor receive.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 60_000 })
    void testDatagramSocketAdaptorReceiveInterrupt(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));

                byte[] array = new byte[100];
                DatagramPacket p = new DatagramPacket(array, 0, array.length);
                if (timeout > 0)
                    dc.socket().setSoTimeout(timeout);

                // interrupt current thread when it blocks in receive
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                try {
                    dc.socket().receive(p);
                    fail();
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Pipe read/write, no blocking.
     */
    @Test
    void testPipeReadWrite1() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink();
                 Pipe.SourceChannel source = p.source()) {

                // write should not block
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                int n = sink.write(bb);
                assertTrue(n > 0);

                // read should not block
                bb = ByteBuffer.allocate(10);
                n = source.read(bb);
                assertTrue(n > 0);
                assertTrue(bb.get(0) == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in Pipe.SourceChannel read.
     */
    @Test
    void testPipeReadWrite2() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink();
                 Pipe.SourceChannel source = p.source()) {

                // write from sink when current thread blocks reading from source
                ByteBuffer bb1 = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                runAfterParkedAsync(() -> sink.write(bb1));

                // read should block
                ByteBuffer bb2 = ByteBuffer.allocate(10);
                int n = source.read(bb2);
                assertTrue(n > 0);
                assertTrue(bb2.get(0) == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in Pipe.SinkChannel write.
     */
    @Test
    void testPipeReadWrite3() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink();
                 Pipe.SourceChannel source = p.source()) {

                // read from source to EOF when current thread blocking in write
                Thread reader = runAfterParkedAsync(() -> readToEOF(source));

                // write to sink should block
                ByteBuffer bb = ByteBuffer.allocate(100*1024);
                for (int i=0; i<1000; i++) {
                    int n = sink.write(bb);
                    assertTrue(n > 0);
                    bb.clear();
                }
                sink.close();

                // wait for reader to finish
                reader.join();
            }
        });
    }

    /**
     * Pipe.SourceChannel close while virtual thread blocked in read.
     */
    @Test
    void testPipeReadAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink();
                 Pipe.SourceChannel source = p.source()) {
                runAfterParkedAsync(source::close);
                try {
                    int n = source.read(ByteBuffer.allocate(100));
                    fail("read returned " + n);
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in Pipe.SourceChannel read.
     */
    @Test
    void testPipeReadInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink();
                 Pipe.SourceChannel source = p.source()) {

                // interrupt current thread when it blocks reading from source
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                try {
                    int n = source.read(ByteBuffer.allocate(100));
                    fail("read returned " + n);
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Pipe.SinkChannel close while virtual thread blocked in write.
     */
    @Test
    void testPipeWriteAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            boolean done = false;
            while (!done) {
                Pipe p = Pipe.open();
                try (Pipe.SinkChannel sink = p.sink();
                     Pipe.SourceChannel source = p.source()) {

                    // close sink when current thread blocks in write
                    runAfterParkedAsync(sink::close, true);

                    // write until channel is closed
                    try {
                        ByteBuffer bb = ByteBuffer.allocate(100*1024);
                        for (;;) {
                            int n = sink.write(bb);
                            assertTrue(n > 0);
                            bb.clear();
                        }
                    } catch (AsynchronousCloseException e) {
                        // closed when blocked in write
                        done = true;
                    } catch (ClosedChannelException e) {
                        // closed but not blocked in write, need to retry test
                        System.err.format("%s, need to retry!%n", e);
                    }
                }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in Pipe.SinkChannel write.
     */
    @Test
    void testPipeWriteInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            boolean done = false;
            while (!done) {
                Pipe p = Pipe.open();
                try (Pipe.SinkChannel sink = p.sink();
                     Pipe.SourceChannel source = p.source()) {

                    // interrupt current thread when it blocks in write
                    Thread thisThread = Thread.currentThread();
                    runAfterParkedAsync(thisThread::interrupt, true);

                    // write until channel is closed
                    try {
                        ByteBuffer bb = ByteBuffer.allocate(100*1024);
                        for (;;) {
                            int n = sink.write(bb);
                            assertTrue(n > 0);
                            bb.clear();
                        }
                    } catch (ClosedByInterruptException expected) {
                        // closed when blocked in write
                        assertTrue(Thread.interrupted());
                        done = true;
                    } catch (ClosedChannelException e) {
                        // closed but not blocked in write, need to retry test
                        System.err.format("%s, need to retry!%n", e);
                    }
                }
            }
        });
    }

    /**
     * Creates a loopback connection
     */
    static class Connection implements Closeable {
        private final SocketChannel sc1;
        private final SocketChannel sc2;
        Connection() throws IOException {
            var lh = InetAddress.getLoopbackAddress();
            try (var listener = ServerSocketChannel.open()) {
                listener.bind(new InetSocketAddress(lh, 0));
                SocketChannel sc1 = SocketChannel.open();
                SocketChannel sc2 = null;
                try {
                    sc1.socket().connect(listener.getLocalAddress());
                    sc2 = listener.accept();
                } catch (IOException ioe) {
                    sc1.close();
                    throw ioe;
                }
                this.sc1 = sc1;
                this.sc2 = sc2;
            }
        }
        SocketChannel channel1() {
            return sc1;
        }
        SocketChannel channel2() {
            return sc2;
        }
        @Override
        public void close() throws IOException {
            sc1.close();
            sc2.close();
        }
    }

    /**
     * Read from a channel until all bytes have been read or an I/O error occurs.
     */
    static void readToEOF(ReadableByteChannel rbc) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(16*1024);
        int n;
        while ((n = rbc.read(bb)) > 0) {
            bb.clear();
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs the given task asynchronously after the current virtual thread parks.
     * @param writing if the thread will block in write
     * @return the thread started to run the task
     */
    private static Thread runAfterParkedAsync(ThrowingRunnable task, boolean writing) {
        Thread target = Thread.currentThread();
        if (!target.isVirtual())
            throw new WrongThreadException();
        return Thread.ofPlatform().daemon().start(() -> {
            try {
                // wait for target thread to park
                while (!isWaiting(target)) {
                    Thread.sleep(20);
                }

                // if the target thread is parked in write then we nudge it a few times
                // to avoid wakeup with some bytes written
                if (writing) {
                    for (int i = 0; i < 3; i++) {
                        LockSupport.unpark(target);
                        while (!isWaiting(target)) {
                            Thread.sleep(20);
                        }
                    }
                }

                task.run();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static Thread runAfterParkedAsync(ThrowingRunnable task) {
        return runAfterParkedAsync(task, false);
    }

    /**
     * Return true if the given Thread is parked.
     */
    private static boolean isWaiting(Thread target) {
        Thread.State state = target.getState();
        assertNotEquals(Thread.State.TERMINATED, state);
        return (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING);
    }
}
