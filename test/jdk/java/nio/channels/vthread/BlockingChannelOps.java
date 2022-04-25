/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests of virtual threads doing blocking I/O with NIO channels
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} BlockingChannelOps.java
 * @run testng/othervm/timeout=300 --enable-preview BlockingChannelOps
 * @run testng/othervm/timeout=300 --enable-preview -Djdk.useDirectRegister BlockingChannelOps
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
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import jdk.test.lib.thread.VThreadRunner;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class BlockingChannelOps {
    private static final long DELAY = 4000;

    /**
     * SocketChannel read/write, no blocking.
     */
    @Test
    public void testSocketChannelReadWrite1() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // write should not block
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                int n = sc1.write(bb);
                assertTrue(n > 0);

                // read should not block
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
    public void testSocketChannelRead() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // schedule write
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                ScheduledWriter.schedule(sc1, bb, DELAY);

                // read should block
                bb = ByteBuffer.allocate(10);
                int n = sc2.read(bb);
                assertTrue(n > 0);
                assertTrue(bb.get(0) == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in SocketChannel write.
     */
    @Test
    public void testSocketChannelWrite() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // schedule thread to read to EOF
                ScheduledReader.schedule(sc2, true, DELAY);

                // write should block
                ByteBuffer bb = ByteBuffer.allocate(100*1024);
                for (int i=0; i<1000; i++) {
                    int n = sc1.write(bb);
                    assertTrue(n > 0);
                    bb.clear();
                }
            }
        });
    }

    /**
     * SocketChannel close while virtual thread blocked in read.
     */
    @Test
    public void testSocketChannelReadAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();
                ScheduledCloser.schedule(sc, DELAY);
                try {
                    int n = sc.read(ByteBuffer.allocate(100));
                    throw new RuntimeException("read returned " + n);
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in SocketChannel read.
     */
    @Test
    public void testSocketChannelReadInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();
                ScheduledInterrupter.schedule(Thread.currentThread(), DELAY);
                try {
                    int n = sc.read(ByteBuffer.allocate(100));
                    throw new RuntimeException("read returned " + n);
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
    public void testSocketChannelWriteAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();
                ScheduledCloser.schedule(sc, DELAY);
                try {
                    ByteBuffer bb = ByteBuffer.allocate(100*1024);
                    for (;;) {
                        int n = sc.write(bb);
                        assertTrue(n > 0);
                        bb.clear();
                    }
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in SocketChannel write.
     */
    @Test
    public void testSocketChannelWriteInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc = connection.channel1();
                ScheduledInterrupter.schedule(Thread.currentThread(), DELAY);
                try {
                    ByteBuffer bb = ByteBuffer.allocate(100*1024);
                    for (;;) {
                        int n = sc.write(bb);
                        assertTrue(n > 0);
                        bb.clear();
                    }
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Virtual thread blocks in SocketChannel adaptor read.
     */
    @Test
    public void testSocketAdaptorRead1() throws Exception {
        testSocketAdaptorRead(0);
    }

    /**
     * Virtual thread blocks in SocketChannel adaptor read with timeout.
     */
    @Test
    public void testSocketAdaptorRead2() throws Exception {
        testSocketAdaptorRead(60_000);
    }

    private void testSocketAdaptorRead(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                SocketChannel sc1 = connection.channel1();
                SocketChannel sc2 = connection.channel2();

                // schedule write
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                ScheduledWriter.schedule(sc1, bb, DELAY);

                // read should block
                if (timeout > 0)
                    sc2.socket().setSoTimeout(timeout);

                byte[] array = new byte[100];
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
    public void testServerSocketChannelAccept1() throws Exception {
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
    public void testServerSocketChannelAccept2() throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                var sc1 = SocketChannel.open();
                ScheduledConnector.schedule(sc1, ssc.getLocalAddress(), DELAY);
                // accept will block
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
    public void testServerSocketChannelAcceptAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lh, 0));
                ScheduledCloser.schedule(ssc, DELAY);
                try {
                    SocketChannel sc = ssc.accept();
                    sc.close();
                    throw new RuntimeException("connection accepted???");
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in ServerSocketChannel accept.
     */
    @Test
    public void testServerSocketChannelAcceptInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lh, 0));
                ScheduledInterrupter.schedule(Thread.currentThread(), DELAY);
                try {
                    SocketChannel sc = ssc.accept();
                    sc.close();
                    throw new RuntimeException("connection accepted???");
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Virtual thread blocks in ServerSocketChannel adaptor accept.
     */
    @Test
    public void testSocketChannelAdaptorAccept1() throws Exception {
        testSocketChannelAdaptorAccept(0);
    }

    /**
     * Virtual thread blocks in ServerSocketChannel adaptor accept with timeout.
     */
    @Test
    public void testSocketChannelAdaptorAccept2() throws Exception {
        testSocketChannelAdaptorAccept(60_000);
    }

    private void testSocketChannelAdaptorAccept(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                var sc1 = SocketChannel.open();
                ScheduledConnector.schedule(sc1, ssc.getLocalAddress(), DELAY);

                if (timeout > 0)
                    ssc.socket().setSoTimeout(timeout);

                // accept will block
                Socket s = ssc.socket().accept();
                sc1.close();
                s.close();
            }
        });
    }

    /**
     * DatagramChannel receive/send, no blocking.
     */
    @Test
    public void testDatagramChannelSendReceive1() throws Exception {
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
    public void testDatagramChannelSendReceive2() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc1 = DatagramChannel.open();
                 DatagramChannel dc2 = DatagramChannel.open()) {

                InetAddress lh = InetAddress.getLoopbackAddress();
                dc2.bind(new InetSocketAddress(lh, 0));

                // schedule send
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                ScheduledSender.schedule(dc1, bb, dc2.getLocalAddress(), DELAY);

                // read should block
                bb = ByteBuffer.allocate(10);
                dc2.receive(bb);
                assertTrue(bb.get(0) == 'X');
            }
        });
    }

    /**
     * DatagramChannel close while virtual thread blocked in receive.
     */
    @Test
    public void testDatagramChannelReceiveAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));
                ScheduledCloser.schedule(dc, DELAY);
                try {
                    dc.receive(ByteBuffer.allocate(100));
                    throw new RuntimeException("receive returned");
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in DatagramChannel receive.
     */
    @Test
    public void testDatagramChannelReceiveInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));
                ScheduledInterrupter.schedule(Thread.currentThread(), DELAY);
                try {
                    dc.receive(ByteBuffer.allocate(100));
                    throw new RuntimeException("receive returned");
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Virtual thread blocks in DatagramSocket adaptor receive.
     */
    @Test
    public void testDatagramSocketAdaptorReceive1() throws Exception {
        testDatagramSocketAdaptorReceive(0);
    }

    /**
     * Virtual thread blocks in DatagramSocket adaptor receive with timeout.
     */
    @Test
    public void testDatagramSocketAdaptorReceive2() throws Exception {
        testDatagramSocketAdaptorReceive(60_1000);
    }

    private void testDatagramSocketAdaptorReceive(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc1 = DatagramChannel.open();
                 DatagramChannel dc2 = DatagramChannel.open()) {

                InetAddress lh = InetAddress.getLoopbackAddress();
                dc2.bind(new InetSocketAddress(lh, 0));

                // schedule send
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                ScheduledSender.schedule(dc1, bb, dc2.getLocalAddress(), DELAY);

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
    @Test
    public void testDatagramSocketAdaptorReceiveAsyncClose1() throws Exception {
        testDatagramSocketAdaptorReceiveAsyncClose(0);
    }

    /**
     * DatagramChannel close while virtual thread blocked in adaptor receive
     * with timeout.
     */
    @Test
    public void testDatagramSocketAdaptorReceiveAsyncClose2() throws Exception {
        testDatagramSocketAdaptorReceiveAsyncClose(60_1000);
    }

    private void testDatagramSocketAdaptorReceiveAsyncClose(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));

                byte[] array = new byte[100];
                DatagramPacket p = new DatagramPacket(array, 0, array.length);
                if (timeout > 0)
                    dc.socket().setSoTimeout(timeout);

                // schedule channel/socket to be asynchronously closed
                ScheduledCloser.schedule(dc, DELAY);
                assertThrows(SocketException.class, () -> dc.socket().receive(p));
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in DatagramSocket adaptor receive.
     */
    @Test
    public void testDatagramSocketAdaptorReceiveInterrupt1() throws Exception {
        testDatagramSocketAdaptorReceiveInterrupt(0);
    }

    /**
     * Virtual thread interrupted while blocked in DatagramSocket adaptor receive
     * with timeout.
     */
    @Test
    public void testDatagramSocketAdaptorReceiveInterrupt2() throws Exception {
        testDatagramSocketAdaptorReceiveInterrupt(60_1000);
    }

    private void testDatagramSocketAdaptorReceiveInterrupt(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                dc.bind(new InetSocketAddress(lh, 0));

                byte[] array = new byte[100];
                DatagramPacket p = new DatagramPacket(array, 0, array.length);
                if (timeout > 0)
                    dc.socket().setSoTimeout(timeout);

                // receive should block
                ScheduledInterrupter.schedule(Thread.currentThread(), DELAY);
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
    public void testPipeReadWrite1() throws Exception {
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
    public void testPipeReadWrite2() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink();
                 Pipe.SourceChannel source = p.source()) {

                // schedule write
                ByteBuffer bb = ByteBuffer.wrap("XXX".getBytes("UTF-8"));
                ScheduledWriter.schedule(sink, bb, DELAY);

                // read should block
                bb = ByteBuffer.allocate(10);
                int n = source.read(bb);
                assertTrue(n > 0);
                assertTrue(bb.get(0) == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in Pipe.SinkChannel write.
     */
    @Test
    public void testPipeReadWrite3() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink();
                 Pipe.SourceChannel source = p.source()) {

                // schedule thread to read to EOF
                ScheduledReader.schedule(source, true, DELAY);

                // write should block
                ByteBuffer bb = ByteBuffer.allocate(100*1024);
                for (int i=0; i<1000; i++) {
                    int n = sink.write(bb);
                    assertTrue(n > 0);
                    bb.clear();
                }
            }
        });
    }

    /**
     * Pipe.SourceChannel close while virtual thread blocked in read.
     */
    @Test
    public void testPipeReadAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SourceChannel source = p.source()) {
                ScheduledCloser.schedule(source, DELAY);
                try {
                    int n = source.read(ByteBuffer.allocate(100));
                    throw new RuntimeException("read returned " + n);
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in Pipe.SourceChannel read.
     */
    @Test
    public void testPipeReadInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SourceChannel source = p.source()) {
                ScheduledInterrupter.schedule(Thread.currentThread(), DELAY);
                try {
                    int n = source.read(ByteBuffer.allocate(100));
                    throw new RuntimeException("read returned " + n);
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
    public void testPipeWriteAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink()) {
                ScheduledCloser.schedule(sink, DELAY);
                try {
                    ByteBuffer bb = ByteBuffer.allocate(100*1024);
                    for (;;) {
                        int n = sink.write(bb);
                        assertTrue(n > 0);
                        bb.clear();
                    }
                } catch (AsynchronousCloseException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in Pipe.SinkChannel write.
     */
    @Test
    public void testPipeWriteInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            try (Pipe.SinkChannel sink = p.sink()) {
                ScheduledInterrupter.schedule(Thread.currentThread(), DELAY);
                try {
                    ByteBuffer bb = ByteBuffer.allocate(100*1024);
                    for (;;) {
                        int n = sink.write(bb);
                        assertTrue(n > 0);
                        bb.clear();
                    }
                } catch (ClosedByInterruptException expected) {
                    assertTrue(Thread.interrupted());
                }
            }
        });
    }

    // -- supporting classes --

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
                    sc1.socket().connect(listener.getLocalAddress(), 10_000);
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
     * Closes a channel after a delay
     */
    static class ScheduledCloser implements Runnable {
        private final Closeable c;
        private final long delay;
        ScheduledCloser(Closeable c, long delay) {
            this.c = c;
            this.delay = delay;
        }
        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                c.close();
            } catch (Exception e) { }
        }
        static void schedule(Closeable c, long delay) {
            new Thread(new ScheduledCloser(c, delay)).start();
        }
    }

    /**
     * Interrupts a thread after a delay
     */
    static class ScheduledInterrupter implements Runnable {
        private final Thread thread;
        private final long delay;

        ScheduledInterrupter(Thread thread, long delay) {
            this.thread = thread;
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                thread.interrupt();
            } catch (Exception e) { }
        }

        static void schedule(Thread thread, long delay) {
            new Thread(new ScheduledInterrupter(thread, delay)).start();
        }
    }

    /**
     * Establish a connection to a socket address after a delay
     */
    static class ScheduledConnector implements Runnable {
        private final SocketChannel sc;
        private final SocketAddress address;
        private final long delay;

        ScheduledConnector(SocketChannel sc, SocketAddress address, long delay) {
            this.sc = sc;
            this.address = address;
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                sc.connect(address);
            } catch (Exception e) { }
        }

        static void schedule(SocketChannel sc, SocketAddress address, long delay) {
            new Thread(new ScheduledConnector(sc, address, delay)).start();
        }
    }

    /**
     * Reads from a connection, and to EOF, after a delay
     */
    static class ScheduledReader implements Runnable {
        private final ReadableByteChannel rbc;
        private final boolean readAll;
        private final long delay;

        ScheduledReader(ReadableByteChannel rbc, boolean readAll, long delay) {
            this.rbc = rbc;
            this.readAll = readAll;
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                ByteBuffer bb = ByteBuffer.allocate(100*1024);
                for (;;) {
                    int n = rbc.read(bb);
                    if (n == -1 || !readAll)
                        break;
                    bb.clear();
                }
            } catch (Exception e) { }
        }

        static void schedule(ReadableByteChannel rbc, boolean readAll, long delay) {
            new Thread(new ScheduledReader(rbc, readAll, delay)).start();
        }
    }

    /**
     * Writes to a connection after a delay
     */
    static class ScheduledWriter implements Runnable {
        private final WritableByteChannel wbc;
        private final ByteBuffer buf;
        private final long delay;

        ScheduledWriter(WritableByteChannel wbc, ByteBuffer buf, long delay) {
            this.wbc = wbc;
            this.buf = buf;
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                wbc.write(buf);
            } catch (Exception e) { }
        }

        static void schedule(WritableByteChannel wbc, ByteBuffer buf, long delay) {
            new Thread(new ScheduledWriter(wbc, buf, delay)).start();
        }
    }

    /**
     * Sends a datagram to a target address after a delay
     */
    static class ScheduledSender implements Runnable {
        private final DatagramChannel dc;
        private final ByteBuffer buf;
        private final SocketAddress address;
        private final long delay;

        ScheduledSender(DatagramChannel dc, ByteBuffer buf, SocketAddress address, long delay) {
            this.dc = dc;
            this.buf = buf;
            this.address = address;
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                dc.send(buf, address);
            } catch (Exception e) { }
        }

        static void schedule(DatagramChannel dc, ByteBuffer buf,
                             SocketAddress address, long delay) {
            new Thread(new ScheduledSender(dc, buf, address, delay)).start();
        }
    }
}
