/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6935563 7044870
 * @summary Test that Selector does not select an unconnected DatagramChannel when
 *    ICMP port unreachable received
 * @run junit/othervm SelectWhenRefused
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class SelectWhenRefused {
    static final int MAX_TRIES = 10;
    static final String GREETINGS_MESSAGE = System.nanoTime() + ": Greetings from SelectWhenRefused!";

    @Test
    public void test() throws IOException {
        DatagramChannel dc1 = DatagramChannel.open().bind(new InetSocketAddress(0));
        int port = dc1.socket().getLocalPort();

        // datagram sent to this address should be refused
        SocketAddress refuser = new InetSocketAddress(InetAddress.getLocalHost(), port);
        System.err.println("Refuser is: " + refuser);

        DatagramChannel dc = null;
        for (int i=0; i < MAX_TRIES; i++) {
            dc = DatagramChannel.open();
            try {
                dc.bind(new InetSocketAddress(0));
            } catch (Throwable t) {
                dc.close();
                throw t;
            }

            // check the port assigned to dc
            if (((InetSocketAddress)dc.getLocalAddress()).getPort() != port) {
                // We got a good port. Do not retry
                break;
            }

            // We bound to the same port that the refuser is using, This will not
            // work. Retry binding if possible.
            if (i < MAX_TRIES - 1) {
                // we will retry...
                System.err.format("Refuser port has been reused by dc: %s, retrying...%n",
                        dc.getLocalAddress());
            } else {
                // that was the last attempt... Skip the test
                System.err.format("Skipping test: refuser port has been reused by dc: %s%n",
                        dc.getLocalAddress());
                return;
            }
        }
        dc1.close();
        assert dc != null;

        Selector sel = Selector.open();
        try {
            dc.configureBlocking(false);
            dc.register(sel, SelectionKey.OP_READ);

            /* Test 1: not connected so ICMP port unreachable should not be received */
            for (int i = 1; i <= MAX_TRIES; i++) {
                if (testNoPUEBeforeConnection(dc, refuser, sel, i)) {
                    break; // test succeeded
                }
                assertNotEquals(i, MAX_TRIES, "testNoPUEBeforeConnection: too many retries");
            }

            /* Test 2: connected so ICMP port unreachable may be received */
            dc.connect(refuser);
            try {
                for (int i = 1; i <= MAX_TRIES; i++) {
                    if (testPUEOnConnect(dc, refuser, sel, i)) {
                        break; // test passed
                    }
                    assertNotEquals(i, MAX_TRIES, "testPUEOnConnect: too many retries");
                }
            } finally {
                dc.disconnect();
            }

            /* Test 3: not connected so ICMP port unreachable should not be received */
            for (int i = 1; i <= MAX_TRIES; i++) {
                if (testNoPUEAfterDisconnect(dc, refuser, sel, i)) {
                    break; // test passed
                }
                assertNotEquals(i, MAX_TRIES, "testNoPUEAfterDisconnect: too many retries");
            }
        } catch (BindException e) {
            // Do nothing, some other test has used this port
            System.err.println("Skipping test: refuser port has been reused: " + e);
        } finally {
            sel.close();
            dc.close();
        }
    }

    /*
     * Send a datagram to non existent unconnected UDP end point
     * This shouldn't result in an PortUnreachableException
     * Handle unexpected read events on the senders DC with
     * retry when message received is external and Throw Exception
     * on receipt of own message
     */
    static boolean testNoPUEBeforeConnection(DatagramChannel dc,
                                             SocketAddress refuser,
                                             Selector sel,
                                             int retryCount) throws IOException {
        sendDatagram(dc, refuser);
        int n = sel.select(2000);
        if (n > 0) {
            boolean mayRetry = checkUnexpectedWakeup(sel.selectedKeys());
            boolean tooManyRetries = retryCount >= MAX_TRIES;
            sel.selectedKeys().clear();

            if (mayRetry && !tooManyRetries) {
                return false; // will retry
            }

            // BindException will be thrown if another service is using
            // our expected refuser port, cannot run just exit.
            try (DatagramChannel dc2 = DatagramChannel.open()) {
                dc2.bind(refuser);
            }
            throw new RuntimeException("Unexpected wakeup");
        }
        return true; // test passed
    }

    /*
     * Send a datagram to a connected UDP end point
     * This should result in an PortUnreachableException
     * Handle unexpected read events on the senders DC with
     * retry when message received is external and Throw Exception
     * on receipt of own message
     */
    static boolean testPUEOnConnect(DatagramChannel dc,
                                    SocketAddress refuser,
                                    Selector sel,
                                    int retryCount) throws IOException {
        sendDatagram(dc, refuser);
        int n = sel.select(2000);
        if (n > 0) {
            sel.selectedKeys().clear();

            try {
                // Attempt to read from Selected Key
                ByteBuffer buf = ByteBuffer.allocate(100);
                SocketAddress sa = dc.receive(buf);

                if (sa != null) {
                    buf.flip();
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    String message = new String(bytes);
                    System.err.format("received %s at %s from %s%n", message, dc.getLocalAddress(), sa);

                    // If any received data contains the message from sendDatagram then throw exception
                    if (message.contains(GREETINGS_MESSAGE)) {
                        throw new RuntimeException("Unexpected datagram received");
                    }
                }

                boolean mayRetry = retryCount < MAX_TRIES;
                if (mayRetry) {
                    return false; // will retry
                }

                // BindException will be thrown if another service is using
                // our expected refuser port, cannot run just exit.
                try (DatagramChannel dc2 = DatagramChannel.open()) {
                    dc2.bind(refuser);
                }
                throw new RuntimeException("PortUnreachableException not raised");
            } catch (PortUnreachableException pue) {
                System.err.println("Got expected PortUnreachableException " + pue);
            }
        }
        return true; // test passed
    }

    /*
     * Send a datagram to a disconnected UDP end point
     * This should result in an PortUnreachableException
     * Handle unexpected read events on the senders DC with
     * retry when message received is external and Throw Exception
     * on receipt of own message
     */
    static boolean testNoPUEAfterDisconnect(DatagramChannel dc,
                                            SocketAddress refuser,
                                            Selector sel,
                                            int retryCount) throws IOException {
        sendDatagram(dc, refuser);
        int n = sel.select(2000);
        if (n > 0) {
            boolean mayRetry = checkUnexpectedWakeup(sel.selectedKeys());
            boolean tooManyRetries = retryCount >= MAX_TRIES;
            sel.selectedKeys().clear();

            if (mayRetry && !tooManyRetries) {
                return false; // will retry
            }

            throw new RuntimeException("Unexpected wakeup after disconnect");
        }
        return true; // test passed
    }

    static void sendDatagram(DatagramChannel dc, SocketAddress remote)
            throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(GREETINGS_MESSAGE.getBytes());
        dc.send(bb, remote);
    }

    /*
     * Attempt to read and Log the data from SelectedKeys,
     * If a message can be received, and it came from
     * another test return True
     *
     */
    static boolean checkUnexpectedWakeup(Set<SelectionKey> selectedKeys) {
        System.err.format("Received %d keys%n", selectedKeys.size());

        for (SelectionKey key : selectedKeys) {
            if (!key.isValid() || !key.isReadable()) {
                System.err.println("Invalid or unreadable key: " + key);
                continue;
            }

            try {
                System.err.println("Attempting to read datagram from key: " + key);
                DatagramChannel datagramChannel = (DatagramChannel) key.channel();
                ByteBuffer buf = ByteBuffer.allocate(100);
                SocketAddress sa = datagramChannel.receive(buf);

                if (sa != null) {
                    buf.flip();
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    String message = new String(bytes);
                    System.err.format("received %s at %s from %s%n", message, datagramChannel.getLocalAddress(), sa);

                    // If any received data contains the message from sendDatagram then return false
                    if (message.contains(GREETINGS_MESSAGE)) {
                        return false;
                    }
                }

            } catch (IOException io) {
                System.err.println("Unable to read from datagram " + io);
            }
        }
        return true;
    }
}
