/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8358764
 * @summary Test closing a socket while a thread is blocked in read. The connection
 *     should be closed gracefuly so that the peer reads EOF.
 * @run junit PeerReadsAfterAsyncClose
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class PeerReadsAfterAsyncClose {

    static Stream<ThreadFactory> factories() {
        return Stream.of(Thread.ofPlatform().factory(), Thread.ofVirtual().factory());
    }

    /**
     * Close SocketChannel while a thread is blocked reading from the channel's socket.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseDuringSocketChannelRead(ThreadFactory factory) throws Exception {
        var loopback = InetAddress.getLoopbackAddress();
        try (var listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(loopback, 0));

            try (SocketChannel sc = SocketChannel.open(listener.getLocalSocketAddress());
                 Socket peer = listener.accept()) {

                // start thread to read from channel
                var cceThrown = new AtomicBoolean();
                Thread thread = factory.newThread(() -> {
                    try {
                        sc.read(ByteBuffer.allocate(1));
                        fail();
                    } catch (ClosedChannelException e) {
                        cceThrown.set(true);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                thread.start();
                try {
                    // close SocketChannel when thread sampled in implRead
                    onReach(thread, "sun.nio.ch.SocketChannelImpl.implRead", () -> {
                        try {
                            sc.close();
                        } catch (IOException ignore) { }
                    });

                    // peer should read EOF
                    int n = peer.getInputStream().read();
                    assertEquals(-1, n);
                } finally {
                    thread.join();
                }
                assertEquals(true, cceThrown.get(), "ClosedChannelException not thrown");
            }
        }
    }

    /**
     * Close Socket while a thread is blocked reading from the socket.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseDuringSocketUntimedRead(ThreadFactory factory) throws Exception {
        testCloseDuringSocketRead(factory, 0);
    }

    /**
     * Close Socket while a thread is blocked reading from the socket with a timeout.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseDuringSockeTimedRead(ThreadFactory factory) throws Exception {
        testCloseDuringSocketRead(factory, 60_000);
    }

    private void testCloseDuringSocketRead(ThreadFactory factory, int timeout) throws Exception {
        var loopback = InetAddress.getLoopbackAddress();
        try (var listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(loopback, 0));

            try (Socket s = new Socket(loopback, listener.getLocalPort());
                 Socket peer = listener.accept()) {

                // start thread to read from socket
                var seThrown = new AtomicBoolean();
                Thread thread = factory.newThread(() -> {
                    try {
                        s.setSoTimeout(timeout);
                        s.getInputStream().read();
                        fail();
                    } catch (SocketException e) {
                        seThrown.set(true);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                thread.start();
                try {
                    // close Socket when thread sampled in implRead
                    onReach(thread, "sun.nio.ch.NioSocketImpl.implRead", () -> {
                        try {
                            s.close();
                        } catch (IOException ignore) { }
                    });

                    // peer should read EOF
                    int n = peer.getInputStream().read();
                    assertEquals(-1, n);
                } finally {
                    thread.join();
                }
                assertEquals(true, seThrown.get(), "SocketException not thrown");
            }
        }
    }

    /**
     * Runs the given action when the given target thread is sampled at the given
     * location. The location takes the form "{@code c.m}" where
     * {@code c} is the fully qualified class name and {@code m} is the method name.
     */
    private void onReach(Thread target, String location, Runnable action) {
        int index = location.lastIndexOf('.');
        String className = location.substring(0, index);
        String methodName = location.substring(index + 1);
        Thread.ofPlatform().daemon(true).start(() -> {
            try {
                boolean found = false;
                while (!found) {
                    found = contains(target.getStackTrace(), className, methodName);
                    if (!found) {
                        Thread.sleep(20);
                    }
                }
                action.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Returns true if the given stack trace contains an element for the given class
     * and method name.
     */
    private boolean contains(StackTraceElement[] stack, String className, String methodName) {
        return Arrays.stream(stack)
                .anyMatch(e -> className.equals(e.getClassName())
                        && methodName.equals(e.getMethodName()));
    }
}