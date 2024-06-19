/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8329190
 * @summary Test that I/O operations on a closed network channel throw ClosedChannelException
 *    and not AsynchronousCloseException
 * @run junit ClosedNetworkChannels
 */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClosedNetworkChannels {

    /**
     * An operation that does not return a result but may throw an exception.
     */
    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Assert that the given operation throws ClosedChannelException.
     */
    private void assertThrowsCCE(ThrowingRunnable op) throws Exception {
        try {
            op.run();
            fail();
        } catch (AsynchronousCloseException e) {
            fail(e + " thrown");
        } catch (ClosedChannelException e) {
            // expected
        }
    }

    /**
     * Closes the given SocketChannel and checks that I/O ops throw ClosedChannelException.
     */
    private void testSocketChannel(SocketChannel sc) throws Exception {
        sc.close();

        InetAddress lb = InetAddress.getLoopbackAddress();
        SocketAddress target = new InetSocketAddress(lb, 7777);  // any port will do

        ByteBuffer bb = ByteBuffer.allocate(100);
        ByteBuffer[] bufs = new ByteBuffer[] { bb };

        assertThrowsCCE(() -> sc.connect(target));
        assertThrowsCCE(() -> sc.finishConnect());
        assertThrowsCCE(() -> sc.read(bb));
        assertThrowsCCE(() -> sc.read(bufs));
        assertThrowsCCE(() -> sc.read(bufs, 0, 1));
        assertThrowsCCE(() -> sc.write(bb));
        assertThrowsCCE(() -> sc.write(bufs));
        assertThrowsCCE(() -> sc.write(bufs, 0, 1));
    }

    /**
     * Test that I/O operations on a closed (but previously unconnected) SocketChannel
     * throw ClosedChannelException.
     */
    @Test
    void testUnconnectedSocketChannel() throws Exception {
        SocketChannel sc = SocketChannel.open();
        testSocketChannel(sc);
    }

    /**
     * Test that I/O operations on a closed (but previously connected) SocketChannel
     * throw ClosedChannelException.
     */
    @Test
    void testConnectedSocketChannel() throws Exception {
        try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
            ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            try (SocketChannel sc = SocketChannel.open(ssc.getLocalAddress());
                 SocketChannel peer = ssc.accept()) {
                testSocketChannel(sc);
            }
        }
    }

    /**
     * Test that the accept operation on a closed (but previously unbound) ServerSocketChannel
     * throws ClosedChannelException.
     */
    @Test
    void testUnboundServerSocketChannel() throws Exception {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.close();
        assertThrowsCCE(() -> ssc.accept());
    }

    /**
     * Test that the accept operation on a closed (but previously bound) ServerSocketChannel
     * throws ClosedChannelException.
     */
    @Test
    void testBoundServerSocketChannel() throws Exception {
        try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
            ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            ssc.close();
            assertThrowsCCE(() -> ssc.accept());
        }
    }

    /**
     * Test that I/O operations on a closed Pipe.SourceChannel and Pipe.SinkChannel
     * throw ClosedChannelException.
     */
    @Test
    void testSourceAndSinkChannels() throws Exception {
        Pipe p = Pipe.open();
        try (Pipe.SourceChannel source = p.source();
             Pipe.SinkChannel sink = p.sink()) {
            source.close();
            sink.close();

            ByteBuffer bb = ByteBuffer.allocate(100);
            ByteBuffer[] bufs = new ByteBuffer[]{bb};

            assertThrowsCCE(() -> source.read(bb));
            assertThrowsCCE(() -> source.read(bufs));
            assertThrowsCCE(() -> source.read(bufs, 0, 1));
            assertThrowsCCE(() -> sink.write(bb));
            assertThrowsCCE(() -> sink.write(bufs));
            assertThrowsCCE(() -> sink.write(bufs, 0, 1));
        }
    }

    /**
     * Closes the given DatagramChannel and checks that I/O ops throw ClosedChannelException.
     */
    private void testDatagramChannel(DatagramChannel dc) throws Exception {
        dc.close();

        InetAddress lb = InetAddress.getLoopbackAddress();
        SocketAddress target = new InetSocketAddress(lb, 7777);  // any port will do

        ByteBuffer bb = ByteBuffer.allocate(100);
        ByteBuffer[] bufs = new ByteBuffer[] { bb };

        assertThrowsCCE(() -> dc.send(bb, target));
        assertThrowsCCE(() -> dc.receive(bb));
        assertThrowsCCE(() -> dc.read(bb));
        assertThrowsCCE(() -> dc.read(bufs));
        assertThrowsCCE(() -> dc.read(bufs, 0, 1));
        assertThrowsCCE(() -> dc.write(bb));
        assertThrowsCCE(() -> dc.write(bufs));
        assertThrowsCCE(() -> dc.write(bufs, 0, 1));
    }

    /**
     * Test that I/O operations on a closed (but previously unconnected) DatagramChannel
     * throw ClosedChannelException.
     */
    @Test
    void testUnconnectedDatagramChannel() throws Exception {
        DatagramChannel dc = DatagramChannel.open();
        testDatagramChannel(dc);
    }

    /**
     * Test that I/O operations on a closed (but previously connected) DatagramChannel
     * throw ClosedChannelException.
     */
    @Test
    void testConnectedDatagramChannel() throws Exception {
        try (DatagramChannel dc = DatagramChannel.open()) {
            InetAddress lb = InetAddress.getLoopbackAddress();
            dc.bind(new InetSocketAddress(lb, 0));
            dc.connect(new InetSocketAddress(lb, 7777));  // any port will do
            testDatagramChannel(dc);
        }
    }
}
