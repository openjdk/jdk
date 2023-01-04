/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278326
 * @modules java.base/java.net:+open
 * @run junit ImplAccept
 * @summary Test ServerSocket.implAccept with Sockets in different states
 */

import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketImpl;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.io.IOException;

import org.junit.*;
import static org.junit.jupiter.api.Assertions.*;

public class ImplAccept {

    /**
     * Test ServerSocket.implAccept with an unbound Socket.
     */
    @Test
    public void testUnbound() throws Exception {
        try (Socket socket = new Socket()) {

            // Socket -> DelegatingSocketImpl -> PlatformSocketImpl
            SocketImpl si = getSocketImpl(socket);
            assertFalse(isPlatformSocketImpl(si));

            try (ServerSocket ss = serverSocketToAccept(socket);
                 Socket peer = new Socket(ss.getInetAddress(), ss.getLocalPort())) {

                Socket s = ss.accept();
                assertTrue(s == socket);

                // Socket.impl should be replaced with a new PlatformSocketImpl
                SocketImpl psi = getSocketImpl(socket);
                assertTrue(isPlatformSocketImpl(psi));
            }
        }
    }

    /**
     * Test ServerSocket.implAccept with a bound Socket.
     */
    @Test
    public void testBound() throws Exception {
        try (Socket socket = new Socket()) {

            // Socket -> DelegatingSocketImpl -> PlatformSocketImpl
            SocketImpl si = getSocketImpl(socket);
            SocketImpl psi1 = getDelegate(si);

            // bind to local address
            socket.bind(loopbackSocketAddress());
            assertTrue(isSocketOpen(psi1));

            try (ServerSocket ss = serverSocketToAccept(socket);
                 Socket peer = new Socket(ss.getInetAddress(), ss.getLocalPort())) {

                Socket s = ss.accept();
                assertTrue(s == socket);

                // Socket.impl should be replaced with a new PlatformSocketImpl
                SocketImpl psi2 = getSocketImpl(socket);
                assertTrue(isPlatformSocketImpl(psi2));

                // psi1 should be closed
                assertFalse(isSocketOpen(psi1));
            }
        }
    }

    /**
     * Test ServerSocket.implAccept with a connected Socket.
     */
    @Test
    public void testConnected() throws Exception {
        Socket socket;
        Socket peer1;
        try (ServerSocket ss = new ServerSocket()) {
            ss.bind(loopbackSocketAddress());
            socket = new Socket(ss.getInetAddress(), ss.getLocalPort());
            peer1 = ss.accept();
        }

        // Socket -> DelegatingSocketImpl -> PlatformSocketImpl
        SocketImpl si = getSocketImpl(socket);
        SocketImpl psi1 = getDelegate(si);
        assertTrue(isPlatformSocketImpl(psi1));

        try (ServerSocket ss = serverSocketToAccept(socket);
             Socket peer2 = new Socket(ss.getInetAddress(), ss.getLocalPort())) {

            Socket s = ss.accept();
            assertTrue(s == socket);

            // Socket.impl should be replaced with a new PlatformSocketImpl
            SocketImpl psi2 = getSocketImpl(socket);
            assertTrue(isPlatformSocketImpl(psi2));

            // psi1 should be closed and peer should read EOF
            assertFalse(isSocketOpen(psi1));
            assertTrue(peer1.getInputStream().read() == -1);

        } finally {
            socket.close();
            peer1.close();
        }
    }

    /**
     * Test ServerSocket.implAccept with a closed Socket.
     */
    @Test
    public void testClosed() throws Exception {
        Socket socket = new Socket();
        socket.close();

        try (ServerSocket ss = serverSocketToAccept(socket);
             Socket peer = new Socket(ss.getInetAddress(), ss.getLocalPort())) {

            SocketImpl si = getSocketImpl(socket);

            // accept should throw
            assertThrows(IOException.class, ss::accept);

            // the SocketImpl should have not changed
            assertTrue(getSocketImpl(socket) == si);
        }
    }

    /**
     * Returns the socket's SocketImpl.
     */
    static SocketImpl getSocketImpl(Socket s) {
        try {
            Field f = Socket.class.getDeclaredField("impl");
            f.setAccessible(true);
            return (SocketImpl) f.get(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the SocketImpl that the given SocketImpl delegates to.
     */
    static SocketImpl getDelegate(SocketImpl si) {
        try {
            Class<?> clazz = Class.forName("java.net.DelegatingSocketImpl");
            Field f = clazz.getDeclaredField("delegate");
            f.setAccessible(true);
            return (SocketImpl) f.get(si);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns true if the SocketImpl is a PlatformSocketImpl.
     */
    static boolean isPlatformSocketImpl(SocketImpl si) {
        try {
            Class<?> clazz = Class.forName("sun.net.PlatformSocketImpl");
            return clazz.isInstance(si);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns true if the SocketImpl has an open socket.
     */
    static boolean isSocketOpen(SocketImpl si) throws Exception {
        assertTrue(isPlatformSocketImpl(si));

        // check if SocketImpl.fd is set
        Field f = SocketImpl.class.getDeclaredField("fd");
        f.setAccessible(true);
        FileDescriptor fd = (FileDescriptor) f.get(si);
        if (fd == null) {
            return false;  // not created
        }

        // call getSocket to get the value of the SO_REUSEADDR socket option
        Method m = SocketImpl.class.getDeclaredMethod("getOption", SocketOption.class);
        m.setAccessible(true);
        try {
            m.invoke(si, StandardSocketOptions.SO_REUSEADDR);
            return true; // socket is open
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                return false; // assume socket is closed
            }
            throw e;
        }
    }

    /**
     * Creates a ServerSocket that returns the given Socket from accept.
     */
    static ServerSocket serverSocketToAccept(Socket s) throws IOException {
        ServerSocket ss = new ServerSocket() {
            @Override
            public Socket accept() throws IOException {
                implAccept(s);
                return s;
            }
        };
        ss.bind(loopbackSocketAddress());
        return ss;
    }

    /**
     * Returns a new InetSocketAddress with the loopback interface and port 0.
     */
    static InetSocketAddress loopbackSocketAddress() {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        return new InetSocketAddress(loopback, 0);
    }
}
