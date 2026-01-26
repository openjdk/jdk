/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8376290
 * @summary Verify that when a SocketChannel is registered with a Selector
 *          with an interest in CONNECT operation, then SocketChannel.finishConnect()
 *          throws the correct exception message, if the connect() fails
 * @run junit ${test.main.class}
 */
class FailedConnect {

    /*
     * On a non-blocking SocketChannel, registered with a Selector, this test method
     * attempts a SocketChannel.connect() against an address that is expected to return
     * Connection refused. The test then calls SocketChannel.finishConnect() when the
     * Selector makes available the ready key for this connect operation and expects
     * that finishConnect() throws a ConnectException with the expected exception message.
     */
    @Test
    void testFinishConnect() throws Exception {
        // find a suitable address against which the connect() attempt
        // will result in a Connection refused exception
        final InetSocketAddress destAddr = findSuitableRefusedAddress();
        // we don't want to skip the test, because we expect at least one port
        // which would result in a connection refused
        assertNotNull(destAddr, "couldn't find a suitable port which will generate" +
                " a connection refused error");
        try (Selector selector = Selector.open();
             SocketChannel sc = SocketChannel.open()) {

            // non-blocking
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_CONNECT);

            System.err.println("establishing connection to " + destAddr);
            boolean connected = sc.connect(destAddr);
            assertFalse(connected, "unexpectedly connected to " + destAddr);
            // wait for ready ops
            int numReady = selector.select(Duration.ofMinutes(10).toMillis());
            System.err.println("Num ready keys = " + numReady);
            for (SelectionKey readyKey : selector.selectedKeys()) {
                System.err.println("ready key: " + readyKey);
                assertTrue(readyKey.isConnectable(), "unexpected key, readyOps = "
                        + readyKey.readyOps());
                readyKey.cancel();

                AtomicBoolean success = new AtomicBoolean();
                // expect SocketChannel.finishConnect() to throw a ConnectException
                ConnectException ce = assertThrows(ConnectException.class, () -> {
                    success.set(sc.finishConnect());
                }, "finishConnect() was expected to fail but didn't, connected = "
                        + success.get());
                System.err.println("got expected exception - " + ce);
                // verify exception message
                assertEquals("Connection refused", ce.getMessage());
            }
        }
    }

    // Try to find a suitable port to provoke a "Connection Refused" error.
    private static InetSocketAddress findSuitableRefusedAddress() throws IOException {
        final InetAddress loopbackAddr = InetAddress.getLoopbackAddress();
        // port 47 is reserved - there should be nothing listening on it
        InetSocketAddress destAddr = new InetSocketAddress(loopbackAddr, 47);
        try (SocketChannel sc1 = SocketChannel.open(destAddr)) {
            // If we manage to connect, let's try to use some other
            // port.
            // port 51 is reserved too - there should be nothing there.
            destAddr = new InetSocketAddress(loopbackAddr, 51);
            try (SocketChannel sc2 = SocketChannel.open(destAddr)) {
            }
            // OK, last attempt...
            // port 61 is reserved too - there should be nothing there.
            destAddr = new InetSocketAddress(loopbackAddr, 61);
            try (SocketChannel sc3 = SocketChannel.open(destAddr)) {
            }
            return null;
        } catch (ConnectException x) {
        }
        // the address which will generate a connection refused, when a connection is attempted
        return destAddr;
    }
}
