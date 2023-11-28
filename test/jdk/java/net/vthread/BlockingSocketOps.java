/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8284161
 * @summary Test virtual threads doing blocking I/O on java.net Sockets
 * @library /test/lib
 * @run junit BlockingSocketOps
 */

/**
 * @test id=poller-modes
 * @requires (os.family == "linux") | (os.family == "mac")
 * @library /test/lib
 * @run junit/othervm -Djdk.pollerMode=1 BlockingSocketOps
 * @run junit/othervm -Djdk.pollerMode=2 BlockingSocketOps
 */

/**
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @library /test/lib
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations BlockingSocketOps
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockingSocketOps {

    /**
     * Socket read/write, no blocking.
     */
    @Test
    void testSocketReadWrite1() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s1 = connection.socket1();
                Socket s2 = connection.socket2();

                // write should not block
                byte[] ba = "XXX".getBytes("UTF-8");
                s1.getOutputStream().write(ba);

                // read should not block
                ba = new byte[10];
                int n = s2.getInputStream().read(ba);
                assertTrue(n > 0);
                assertTrue(ba[0] == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in read.
     */
    @Test
    void testSocketRead1() throws Exception {
        testSocketRead(0);
    }

    /**
     * Virtual thread blocks in timed read.
     */
    @Test
    void testSocketRead2() throws Exception {
        testSocketRead(60_000);
    }

    void testSocketRead(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s1 = connection.socket1();
                Socket s2 = connection.socket2();

                // delayed write from sc1
                byte[] ba1 = "XXX".getBytes("UTF-8");
                runAfterParkedAsync(() -> s1.getOutputStream().write(ba1));

                // read from sc2 should block
                if (timeout > 0) {
                    s2.setSoTimeout(timeout);
                }
                byte[] ba2 = new byte[10];
                int n = s2.getInputStream().read(ba2);
                assertTrue(n > 0);
                assertTrue(ba2[0] == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in write.
     */
    @Test
    void testSocketWrite1() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s1 = connection.socket1();
                Socket s2 = connection.socket2();

                // delayed read from s2 to EOF
                InputStream in = s2.getInputStream();
                Thread reader = runAfterParkedAsync(() ->
                        in.transferTo(OutputStream.nullOutputStream()));

                // write should block
                byte[] ba = new byte[100*1024];
                try (OutputStream out = s1.getOutputStream()) {
                    for (int i = 0; i < 1000; i++) {
                        out.write(ba);
                    }
                }

                // wait for reader to finish
                reader.join();
            }
        });
    }

    /**
     * Virtual thread blocks in read, peer closes connection gracefully.
     */
    @Test
    void testSocketReadPeerClose1() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s1 = connection.socket1();
                Socket s2 = connection.socket2();

                // delayed close of s2
                runAfterParkedAsync(s2::close);

                // read from s1 should block, then read -1
                int n = s1.getInputStream().read();
                assertTrue(n == -1);
            }
        });
    }

    /**
     * Virtual thread blocks in read, peer closes connection abruptly.
     */
    @Test
    void testSocketReadPeerClose2() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s1 = connection.socket1();
                Socket s2 = connection.socket2();

                // delayed abrupt close of s2
                s2.setSoLinger(true, 0);
                runAfterParkedAsync(s2::close);

                // read from s1 should block, then throw
                try {
                    int n = s1.getInputStream().read();
                    fail("read " + n);
                } catch (IOException ioe) {
                    // expected
                }
            }
        });
    }

    /**
     * Socket close while virtual thread blocked in read.
     */
    @Test
    void testSocketReadAsyncClose1() throws Exception {
        testSocketReadAsyncClose(0);
    }

    /**
     * Socket close while virtual thread blocked in timed read.
     */
    @Test
    void testSocketReadAsyncClose2() throws Exception {
        testSocketReadAsyncClose(0);
    }

    void testSocketReadAsyncClose(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s = connection.socket1();

                // delayed close of s
                runAfterParkedAsync(s::close);

                // read from s should block, then throw
                if (timeout > 0) {
                    s.setSoTimeout(timeout);
                }
                try {
                    int n = s.getInputStream().read();
                    fail("read " + n);
                } catch (SocketException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in Socket read.
     */
    @Test
    void testSocketReadInterrupt1() throws Exception {
        testSocketReadInterrupt(0);
    }

    /**
     * Virtual thread interrupted while blocked in Socket read with timeout
     */
    @Test
    void testSocketReadInterrupt2() throws Exception {
        testSocketReadInterrupt(60_000);
    }

    void testSocketReadInterrupt(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s = connection.socket1();


                // delayed interrupt of current thread
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                // read from s should block, then throw
                if (timeout > 0) {
                    s.setSoTimeout(timeout);
                }
                try {
                    int n = s.getInputStream().read();
                    fail("read " + n);
                } catch (SocketException expected) {
                    assertTrue(Thread.interrupted());
                    assertTrue(s.isClosed());
                }
            }
        });
    }

    /**
     * Socket close while virtual thread blocked in write.
     */
    @Test
    void testSocketWriteAsyncClose() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s = connection.socket1();

                // delayedclose of s
                runAfterParkedAsync(s::close);

                // write to s should block, then throw
                try {
                    byte[] ba = new byte[100*1024];
                    OutputStream out = s.getOutputStream();
                    for (;;) {
                        out.write(ba);
                    }
                } catch (SocketException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in Socket write.
     */
    @Test
    void testSocketWriteInterrupt() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s = connection.socket1();

                // delayed interrupt of current thread
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                // write to s should block, then throw
                try {
                    byte[] ba = new byte[100*1024];
                    OutputStream out = s.getOutputStream();
                    for (;;) {
                        out.write(ba);
                    }
                } catch (SocketException expected) {
                    assertTrue(Thread.interrupted());
                    assertTrue(s.isClosed());
                }
            }
        });
    }

    /**
     * Virtual thread reading urgent data when SO_OOBINLINE is enabled.
     */
    @Test
    void testSocketReadUrgentData() throws Exception {
        VThreadRunner.run(() -> {
            try (var connection = new Connection()) {
                Socket s1 = connection.socket1();
                Socket s2 = connection.socket2();

                // urgent data should be received
                runAfterParkedAsync(() -> s2.sendUrgentData('X'));

                // read should block, then read the OOB byte
                s1.setOOBInline(true);
                byte[] ba = new byte[10];
                int n = s1.getInputStream().read(ba);
                assertTrue(n == 1);
                assertTrue(ba[0] == 'X');

                // urgent data should not be received
                s1.setOOBInline(false);
                s1.setSoTimeout(500);
                s2.sendUrgentData('X');
                try {
                    s1.getInputStream().read(ba);
                    fail();
                } catch (SocketTimeoutException expected) { }
            }
        });
    }

    /**
     * ServerSocket accept, no blocking.
     */
    @Test
    void testServerSocketAccept1() throws Exception {
        VThreadRunner.run(() -> {
            try (var listener = new ServerSocket()) {
                InetAddress loopback = InetAddress.getLoopbackAddress();
                listener.bind(new InetSocketAddress(loopback, 0));

                // establish connection
                var socket1 = new Socket(loopback, listener.getLocalPort());

                // accept should not block
                var socket2 = listener.accept();
                socket1.close();
                socket2.close();
            }
        });
    }

    /**
     * Virtual thread blocks in accept.
     */
    @Test
    void testServerSocketAccept2() throws Exception {
        testServerSocketAccept(0);
    }

    /**
     * Virtual thread blocks in timed accept.
     */
    @Test
    void testServerSocketAccept3() throws Exception {
        testServerSocketAccept(60_000);
    }

    void testServerSocketAccept(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var listener = new ServerSocket()) {
                InetAddress loopback = InetAddress.getLoopbackAddress();
                listener.bind(new InetSocketAddress(loopback, 0));

                // schedule connect
                var socket1 = new Socket();
                SocketAddress remote = listener.getLocalSocketAddress();
                runAfterParkedAsync(() -> socket1.connect(remote));

                // accept should block
                if (timeout > 0) {
                    listener.setSoTimeout(timeout);
                }
                var socket2 = listener.accept();
                socket1.close();
                socket2.close();
            }
        });
    }

    /**
     * ServerSocket close while virtual thread blocked in accept.
     */
    @Test
    void testServerSocketAcceptAsyncClose1() throws Exception {
        testServerSocketAcceptAsyncClose(0);
    }

    /**
     * ServerSocket close while virtual thread blocked in timed accept.
     */
    @Test
    void testServerSocketAcceptAsyncClose2() throws Exception {
        testServerSocketAcceptAsyncClose(60_000);
    }

    void testServerSocketAcceptAsyncClose(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var listener = new ServerSocket()) {
                InetAddress loopback = InetAddress.getLoopbackAddress();
                listener.bind(new InetSocketAddress(loopback, 0));

                // delayed close of listener
                runAfterParkedAsync(listener::close);

                // accept should block, then throw
                if (timeout > 0) {
                    listener.setSoTimeout(timeout);
                }
                try {
                    listener.accept().close();
                    fail("connection accepted???");
                } catch (SocketException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in ServerSocket accept.
     */
    @Test
    void testServerSocketAcceptInterrupt1() throws Exception {
        testServerSocketAcceptInterrupt(0);
    }

    /**
     * Virtual thread interrupted while blocked in ServerSocket accept with timeout.
     */
    @Test
    void testServerSocketAcceptInterrupt2() throws Exception {
        testServerSocketAcceptInterrupt(60_000);
    }

    void testServerSocketAcceptInterrupt(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var listener = new ServerSocket()) {
                InetAddress loopback = InetAddress.getLoopbackAddress();
                listener.bind(new InetSocketAddress(loopback, 0));

                // delayed interrupt of current thread
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                // accept should block, then throw
                if (timeout > 0) {
                    listener.setSoTimeout(timeout);
                }
                try {
                    listener.accept().close();
                    fail("connection accepted???");
                } catch (SocketException expected) {
                    assertTrue(Thread.interrupted());
                    assertTrue(listener.isClosed());
                }
            }
        });
    }

    /**
     * DatagramSocket receive/send, no blocking.
     */
    @Test
    void testDatagramSocketSendReceive1() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramSocket s1 = new DatagramSocket(null);
                 DatagramSocket s2 = new DatagramSocket(null)) {

                InetAddress lh = InetAddress.getLoopbackAddress();
                s1.bind(new InetSocketAddress(lh, 0));
                s2.bind(new InetSocketAddress(lh, 0));

                // send should not block
                byte[] bytes = "XXX".getBytes("UTF-8");
                DatagramPacket p1 = new DatagramPacket(bytes, bytes.length);
                p1.setSocketAddress(s2.getLocalSocketAddress());
                s1.send(p1);

                // receive should not block
                byte[] ba = new byte[100];
                DatagramPacket p2 = new DatagramPacket(ba, ba.length);
                s2.receive(p2);
                assertEquals(s1.getLocalSocketAddress(), p2.getSocketAddress());
                assertTrue(ba[0] == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in DatagramSocket receive.
     */
    @Test
    void testDatagramSocketSendReceive2() throws Exception {
        testDatagramSocketSendReceive(0);
    }

    /**
     * Virtual thread blocks in DatagramSocket receive with timeout.
     */
    @Test
    void testDatagramSocketSendReceive3() throws Exception {
        testDatagramSocketSendReceive(60_000);
    }

    private void testDatagramSocketSendReceive(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramSocket s1 = new DatagramSocket(null);
                 DatagramSocket s2 = new DatagramSocket(null)) {

                InetAddress lh = InetAddress.getLoopbackAddress();
                s1.bind(new InetSocketAddress(lh, 0));
                s2.bind(new InetSocketAddress(lh, 0));

                // delayed send
                byte[] bytes = "XXX".getBytes("UTF-8");
                DatagramPacket p1 = new DatagramPacket(bytes, bytes.length);
                p1.setSocketAddress(s2.getLocalSocketAddress());
                runAfterParkedAsync(() -> s1.send(p1));

                // receive should block
                if (timeout > 0) {
                    s2.setSoTimeout(timeout);
                }
                byte[] ba = new byte[100];
                DatagramPacket p2 = new DatagramPacket(ba, ba.length);
                s2.receive(p2);
                assertEquals(s1.getLocalSocketAddress(), p2.getSocketAddress());
                assertTrue(ba[0] == 'X');
            }
        });
    }

    /**
     * Virtual thread blocks in DatagramSocket receive that times out.
     */
    @Test
    void testDatagramSocketReceiveTimeout() throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramSocket s = new DatagramSocket(null)) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                s.bind(new InetSocketAddress(lh, 0));
                s.setSoTimeout(500);
                byte[] ba = new byte[100];
                DatagramPacket p = new DatagramPacket(ba, ba.length);
                try {
                    s.receive(p);
                    fail();
                } catch (SocketTimeoutException expected) { }
            }
        });
    }

    /**
     * DatagramSocket close while virtual thread blocked in receive.
     */
    @Test
    void testDatagramSocketReceiveAsyncClose1() throws Exception {
        testDatagramSocketReceiveAsyncClose(0);
    }

    /**
     * DatagramSocket close while virtual thread blocked with timeout.
     */
    @Test
    void testDatagramSocketReceiveAsyncClose2() throws Exception {
        testDatagramSocketReceiveAsyncClose(60_000);
    }

    private void testDatagramSocketReceiveAsyncClose(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramSocket s = new DatagramSocket(null)) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                s.bind(new InetSocketAddress(lh, 0));

                // delayed close of s
                runAfterParkedAsync(s::close);

                // receive should block, then throw
                if (timeout > 0) {
                    s.setSoTimeout(timeout);
                }
                try {
                    byte[] ba = new byte[100];
                    DatagramPacket p = new DatagramPacket(ba, ba.length);
                    s.receive(p);
                    fail();
                } catch (SocketException expected) { }
            }
        });
    }

    /**
     * Virtual thread interrupted while blocked in DatagramSocket receive.
     */
    @Test
    void testDatagramSocketReceiveInterrupt1() throws Exception {
        testDatagramSocketReceiveInterrupt(0);
    }

    /**
     * Virtual thread interrupted while blocked in DatagramSocket receive with timeout.
     */
    @Test
    void testDatagramSocketReceiveInterrupt2() throws Exception {
        testDatagramSocketReceiveInterrupt(60_000);
    }

    private void testDatagramSocketReceiveInterrupt(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (DatagramSocket s = new DatagramSocket(null)) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                s.bind(new InetSocketAddress(lh, 0));

                // delayed interrupt of current thread
                Thread thisThread = Thread.currentThread();
                runAfterParkedAsync(thisThread::interrupt);

                // receive should block, then throw
                if (timeout > 0) {
                    s.setSoTimeout(timeout);
                }
                try {
                    byte[] ba = new byte[100];
                    DatagramPacket p = new DatagramPacket(ba, ba.length);
                    s.receive(p);
                    fail();
                } catch (SocketException expected) {
                    assertTrue(Thread.interrupted());
                    assertTrue(s.isClosed());
                }
            }
        });
    }

    /**
     * Creates a loopback connection
     */
    static class Connection implements Closeable {
        private final Socket s1;
        private final Socket s2;
        Connection() throws IOException {
            var lh = InetAddress.getLoopbackAddress();
            try (var listener = new ServerSocket()) {
                listener.bind(new InetSocketAddress(lh, 0));
                Socket s1 = new Socket();
                Socket s2;
                try {
                    s1.connect(listener.getLocalSocketAddress());
                    s2 = listener.accept();
                } catch (IOException ioe) {
                    s1.close();
                    throw ioe;
                }
                this.s1 = s1;
                this.s2 = s2;
            }

        }
        Socket socket1() {
            return s1;
        }
        Socket socket2() {
            return s2;
        }
        @Override
        public void close() throws IOException {
            s1.close();
            s2.close();
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs the given task asynchronously after the current virtual thread has parked.
     * @return the thread started to run the task
     */
    static Thread runAfterParkedAsync(ThrowingRunnable task) {
        Thread target = Thread.currentThread();
        if (!target.isVirtual())
            throw new WrongThreadException();
        return Thread.ofPlatform().daemon().start(() -> {
            try {
                Thread.State state = target.getState();
                while (state != Thread.State.WAITING
                        && state != Thread.State.TIMED_WAITING) {
                    Thread.sleep(20);
                    state = target.getState();
                }
                Thread.sleep(20);  // give a bit more time to release carrier
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
