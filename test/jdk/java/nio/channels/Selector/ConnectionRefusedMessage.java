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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/*
 * @test
 * @bug 8376290
 * @summary Verify that when a SocketChannel is registered with a Selector
 *          with an interest in CONNECT operation, then SocketChannel.finishConnect()
 *          throws the correct exception message, if the connect() fails
 * @run junit ${test.main.class}
 */
class ConnectionRefusedMessage {

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
        // skip the test if we couldn't find a port which would raise a connection refused error
        assumeTrue(destAddr != null,
                "couldn't find a suitable port which will generate a connection refused error");
        try (Selector selector = Selector.open();
             SocketChannel sc = SocketChannel.open()) {

            // non-blocking
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_CONNECT);

            System.err.println("establishing connection to " + destAddr);
            boolean connected;
            try {
                connected = sc.connect(destAddr);
            } catch (ConnectException ce) {
                // Connect failed immediately, which is OK.
                System.err.println("SocketChannel.connect() threw ConnectException - " + ce);
                assertExceptionMessage(ce);
                return; // nothing more to test
            }
            // this test checks the exception message of a ConnectException, so it's
            // OK to skip the test if something unexpectedly accepted the connection
            assumeFalse(connected, "unexpectedly connected to " + destAddr);
            // wait for ready ops
            int numReady = selector.select(Duration.ofMinutes(10).toMillis());
            System.err.println("Num ready keys = " + numReady);
            for (SelectionKey readyKey : selector.selectedKeys()) {
                System.err.println("ready key: " + readyKey);
                assertTrue(readyKey.isConnectable(), "unexpected key, readyOps = "
                        + readyKey.readyOps());
                readyKey.cancel();
                try {
                    boolean success = sc.finishConnect();
                    // this test checks the exception message of a ConnectException, so it's
                    // OK to skip the test if something unexpectedly accepted the connection
                    assumeFalse(success, "unexpectedly connected to " + destAddr);
                    // this test doesn't expect finishConnect() to return normally
                    // with a return value of false
                    fail("ConnectException was not thrown");
                } catch (ConnectException ce) {
                    System.err.println("got (expected) ConnectException from " +
                            "SocketChannel.finishConnect() - " + ce);
                    // verify exception message
                    assertExceptionMessage(ce);
                }
            }
        }
    }

    private static void assertExceptionMessage(final ConnectException ce) {
        if (!"Connection refused".equals(ce.getMessage())) {
            // propagate the original exception
            fail("unexpected exception message: " + ce.getMessage(), ce);
        }
    }

    // Try to find a suitable port to provoke a "Connection Refused" error.
    private static InetSocketAddress findSuitableRefusedAddress() throws IOException {
        final InetAddress loopbackAddr = InetAddress.getLoopbackAddress();
        // Ports 47, 51, 61 are in the IANA reserved port list, and
        // are currently unassigned to any specific service.
        // We use them here on the assumption that there won't be
        // any service listening on them.
        InetSocketAddress destAddr = new InetSocketAddress(loopbackAddr, 47);
        try (SocketChannel sc1 = SocketChannel.open(destAddr)) {
            // we managed to connect (unexpectedly), let's try the next reserved port
            destAddr = new InetSocketAddress(loopbackAddr, 51);
            try (SocketChannel sc2 = SocketChannel.open(destAddr)) {
            }
            // we managed to connect (unexpectedly again), let's try the next reserved port
            // as a last attempt
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
