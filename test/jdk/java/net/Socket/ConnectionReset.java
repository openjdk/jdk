/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit ${test.main.class}
 * @summary Test behavior of read and available when a connection is reset
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConnectionReset {

    static final int REPEAT_COUNT = 5;

    /**
     * Tests available before read when there are no bytes to read
     */
    @Test
    public void testAvailableBeforeRead1() throws IOException {
        withResetConnection(null, s -> {
            InputStream in = s.getInputStream();
            for (int i=0; i<REPEAT_COUNT; i++) {
                int bytesAvailable = in.available();
                System.err.format("available => %d%n", bytesAvailable);
                assertEquals(0, bytesAvailable);
                IOException ioe = assertThrows(IOException.class, () -> {
                    int bytesRead = in.read();
                    if (bytesRead == -1) {
                        System.err.println("read => EOF");
                    } else {
                        System.err.println("read => 1 byte");
                    }
                });
                System.err.format("read => %s (expected)%n", ioe);
            }
        });
    }

    /**
     * Tests available before read when there are bytes to read
     */
    @Test
    public void testAvailableBeforeRead2() throws IOException {
        byte[] data = { 1, 2, 3 };
        withResetConnection(data, s -> {
            InputStream in = s.getInputStream();
            int remaining = data.length;
            for (int i=0; i<REPEAT_COUNT; i++) {
                int bytesAvailable = in.available();
                System.err.format("available => %d%n", bytesAvailable);
                assertTrue(bytesAvailable <= remaining);
                try {
                    int bytesRead = in.read();
                    assertNotEquals(-1, bytesRead, "EOF not expected");

                    System.err.println("read => 1 byte");
                    assertTrue(remaining > 0);
                    remaining--;
                } catch (IOException ioe) {
                    System.err.format("read => %s%n", ioe);
                    remaining = 0;
                }
            }
        });
    }

    /**
     * Tests read before available when there are no bytes to read
     */
    @Test
    public void testReadBeforeAvailable1() throws IOException {
        withResetConnection(null, s -> {
            InputStream in = s.getInputStream();
            for (int i=0; i<REPEAT_COUNT; i++) {
                IOException ioe = assertThrows(IOException.class, () -> {
                    int bytesRead = in.read();
                    if (bytesRead == -1) {
                        System.err.println("read => EOF");
                    } else {
                        System.err.println("read => 1 byte");
                    }
                });
                System.err.format("read => %s (expected)%n", ioe);

                int bytesAvailable = in.available();
                System.err.format("available => %d%n", bytesAvailable);
                assertEquals(0, bytesAvailable);
            }
        });
    }

    /**
     * Tests read before available when there are bytes to read
     */
    @Test
    public void testReadBeforeAvailable2() throws IOException {
        byte[] data = { 1, 2, 3 };
        withResetConnection(data, s -> {
            InputStream in = s.getInputStream();
            int remaining = data.length;
            for (int i=0; i<REPEAT_COUNT; i++) {
                try {
                    int bytesRead = in.read();
                    assertNotEquals(-1, bytesRead, "EOF not expected");

                    System.err.println("read => 1 byte");
                    assertTrue(remaining > 0);
                    remaining--;
                } catch (IOException ioe) {
                    System.err.format("read => %s%n", ioe);
                    remaining = 0;
                }
                int bytesAvailable = in.available();
                System.err.format("available => %d%n", bytesAvailable);
                assertTrue(bytesAvailable <= remaining);
            }
        });
    }

    /**
     * Tests available and read on a socket closed after connection reset
     */
    @Test
    public void testAfterClose() throws IOException {
        withResetConnection(null, s -> {
            InputStream in = s.getInputStream();
            assertThrows(IOException.class, () -> in.read());
            s.close();
            IOException ioe = assertThrows(IOException.class, () -> {
                int bytesAvailable = in.available();
                System.err.format("available => %d%n", bytesAvailable);
            });
            System.err.format("available => %s (expected)%n", ioe);
            ioe = assertThrows(IOException.class, () -> {
                int n = in.read();
                System.err.format("read => %d%n", n);
            });
            System.err.format("read => %s (expected)%n", ioe);
        });
    }

    interface ThrowingConsumer<T> {
        void accept(T t) throws IOException;
    }

    /**
     * Invokes a consumer with a Socket connected to a peer that has closed the
     * connection with a "connection reset". The peer sends the given data bytes
     * before closing (when data is not null).
     */
    static void withResetConnection(byte[] data, ThrowingConsumer<Socket> consumer)
        throws IOException
    {
        var loopback = InetAddress.getLoopbackAddress();
        try (var listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(loopback, 0));
            try (var socket = new Socket()) {
                socket.connect(listener.getLocalSocketAddress());
                try (Socket peer = listener.accept()) {
                    if (data != null) {
                        peer.getOutputStream().write(data);
                    }
                    peer.setSoLinger(true, 0);
                }
                consumer.accept(socket);
            }
        }
    }
}
