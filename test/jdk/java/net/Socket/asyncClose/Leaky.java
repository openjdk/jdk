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
 * @run junit Leaky
 * @summary Test async close when binding, connecting, or reading a socket option
 */

import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketImpl;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class Leaky {
    private static ScheduledExecutorService executor;
    private static ServerSocket listener;

    @BeforeAll
    public static void setup() throws Exception {
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        executor = Executors.newScheduledThreadPool(2);
    }

    @AfterAll
    public static void finish() throws Exception {
        executor.close();
        listener.close();
    }

    /**
     * Race Socket bind and close.
     */
    @RepeatedTest(100)
    public void raceBindAndClose() throws Exception {
        Socket socket = new Socket();

        race(socket::close, () -> {
            try {
                socket.bind(new InetSocketAddress(0));
            } catch (IOException ioe) {
                if (!socket.isClosed()) {
                    throw ioe;
                }
            }
        });

        // check that there isn't an open socket
        SocketImpl psi = getPlatformSocketImpl(socket);
        assertFalse(isSocketOpen(psi));
    }

    /**
     * Race Socket connect and close.
     */
    @RepeatedTest(100)
    public void raceConnectAndClose() throws Exception {
        Socket socket = new Socket();

        race(socket::close, () -> {
            try {
                socket.connect(listener.getLocalSocketAddress());
                // if connected, need to close other end
                listener.accept().close();
            } catch (IOException ioe) {
                if (!socket.isClosed()) {
                    throw ioe;
                }
            }
        });

        // check that there isn't an open socket
        SocketImpl psi = getPlatformSocketImpl(socket);
        assertFalse(isSocketOpen(psi));
    }

    /**
     * Race Socket getOption and close.
     */
    @RepeatedTest(100)
    public void raceGetOptionAndClose() throws Exception {
        Socket socket = new Socket();

        race(socket::close, () -> {
            try {
                socket.getOption(StandardSocketOptions.SO_REUSEADDR);
            } catch (IOException ioe) {
                if (!socket.isClosed()) {
                    throw ioe;
                }
            }
        });

        // check that there isn't an open socket
        SocketImpl psi = getPlatformSocketImpl(socket);
        assertFalse(isSocketOpen(psi));
    }

    /**
     * A task that may throw an exception.
     */
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Staggers two tasks to execute after random delays.
     */
    private void race(ThrowingRunnable task1, ThrowingRunnable task2) throws Exception {
        int delay1 = ThreadLocalRandom.current().nextInt(10);
        int delay2 = ThreadLocalRandom.current().nextInt(10);

        Future<Void> future1 = executor.schedule(() -> {
            task1.run();
            return null;
        }, delay1, TimeUnit.MILLISECONDS);

        Future<Void> future2 = executor.schedule(() -> {
            task2.run();
            return null;
        }, delay2, TimeUnit.MILLISECONDS);

        ExecutionException e = null;
        try {
            future1.get();
        } catch (ExecutionException e1) {
            e = e1;
        }
        try {
            future2.get();
        } catch (ExecutionException e2) {
            if (e == null) {
                e = e2;
            } else {
                e.addSuppressed(e2);
            }
        }
        if (e != null) {
            throw e;
        }
    }

    /**
     * Return the underlying PlatformSocketImpl for the given socket.
     */
    private static SocketImpl getPlatformSocketImpl(Socket s) {
        SocketImpl si = getSocketImpl(s);
        return getDelegate(si);
    }

    /**
     * Returns the socket's SocketImpl.
     */
    private static SocketImpl getSocketImpl(Socket s) {
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
    private static SocketImpl getDelegate(SocketImpl si) {
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
     * Returns true if the SocketImpl has an open socket.
     */
    private static boolean isSocketOpen(SocketImpl si) throws Exception {
        // check if SocketImpl.fd is set
        Field f = SocketImpl.class.getDeclaredField("fd");
        f.setAccessible(true);
        FileDescriptor fd = (FileDescriptor) f.get(si);
        if (fd == null) {
            return false;  // not created
        }

        // call getOption to get the value of the SO_REUSEADDR socket option
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
}
