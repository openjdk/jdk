/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8231880 8231258
 * @library /test/lib
 * @summary Test DatagramChannel bound to specific address/ephemeral port after disconnect
 * @run testng/othervm AfterDisconnect
 * @run testng/othervm -Djava.net.preferIPv4Stack=true AfterDisconnect
 * @run testng/othervm -Djava.net.preferIPv6Addresses=true AfterDisconnect
 */

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import jdk.test.lib.net.IPSupport;

public class AfterDisconnect {

    interface RetryableTest<T extends Exception> {
        public void runTest() throws T;
    }

    // retry the given lambda (RetryableTest) if an exception
    // that satisfies the predicate (retryOn) is caught.
    <T extends Exception> void testWithRetry(RetryableTest<T> test,
                                             Predicate<Throwable> retryOn,
                                             int max) throws T {
        for (int i=0; i < max; i++) {
            try {
                test.runTest();
                break;
            } catch (Throwable t) {
                if (i < max -1 && retryOn.test(t)) {
                    System.out.println("Got " + t + "; will retry");
                } else throw t;
            }
        }
    }

    /**
     * When calling {@link DatagramChannel#disconnect()} a {@link BindException}
     * may occur. In which case we want to retry the test.
     */
    class BindExceptionOnDisconnect extends BindException {
        BindExceptionOnDisconnect(BindException x) {
            super(x.getMessage());
            initCause(x);
        }
    }

    @Test
    public void execute() throws IOException {
        IPSupport.throwSkippedExceptionIfNonOperational();
        boolean preferIPv6 = Boolean.getBoolean("java.net.preferIPv6Addresses");
        InetAddress lb = InetAddress.getLoopbackAddress();

        // test with default protocol family
        System.out.println("Test with default");
        testWithRetry(() -> {
            try (DatagramChannel dc = DatagramChannel.open()) {
                dc.bind(new InetSocketAddress(lb, 0));
                test(dc);
                test(dc);
            }
        }, BindExceptionOnDisconnect.class::isInstance, 5);

        // test with IPv6 socket
        if (IPSupport.hasIPv6()) {
            System.out.println("Test with IPv6 socket");
            testWithRetry(() -> {
                try (DatagramChannel dc = DatagramChannel.open(StandardProtocolFamily.INET6)) {
                    dc.bind(new InetSocketAddress(lb, 0));
                    test(dc);
                    test(dc);
                }
            }, BindExceptionOnDisconnect.class::isInstance, 5);
        }

        // test with IPv4 socket
        if (IPSupport.hasIPv4() && !preferIPv6) {
            System.out.println("Test with IPv4 socket");
            testWithRetry(() -> {
                try (DatagramChannel dc = DatagramChannel.open(StandardProtocolFamily.INET)) {
                    dc.bind(new InetSocketAddress(lb, 0));
                    test(dc);
                    test(dc);
                }
            }, BindExceptionOnDisconnect.class::isInstance, 5);
        }
    }


    void test(DatagramChannel dc) throws IOException {
        testLocalAddress(dc);
        testSocketOptions(dc);
        testSelectorRegistration(dc);
        testMulticastGroups(dc);
    }

    /**
     * Test that disconnect restores local address
     */
    void testLocalAddress(DatagramChannel dc) throws IOException {
        try (DatagramChannel server = DatagramChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            SocketAddress local = dc.getLocalAddress();
            SocketAddress remote = server.getLocalAddress();

            dc.connect(remote);
            assertTrue(dc.isConnected());
            assertEquals(dc.getLocalAddress(), local);
            assertEquals(dc.getRemoteAddress(), remote);

            try {
                dc.disconnect();
            } catch (BindException x) {
                throw new BindExceptionOnDisconnect(x);
            }
            assertFalse(dc.isConnected());
            assertEquals(dc.getLocalAddress(), local);
            assertTrue(dc.getRemoteAddress() == null);
        }
    }

    /**
     * Test that disconnect does not change socket options
     */
    void testSocketOptions(DatagramChannel dc) throws IOException {
        // set a few socket options
        dc.setOption(StandardSocketOptions.SO_SNDBUF, 32*1024);
        dc.setOption(StandardSocketOptions.SO_RCVBUF, 64*1024);
        InetAddress ia = dc.socket().getLocalAddress();
        NetworkInterface ni = NetworkInterface.getByInetAddress(ia);
        if (ni != null && ni.supportsMulticast())
            dc.setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);

        // capture values of socket options
        Map<SocketOption<?>, Object> map = options(dc);

        dc.connect(dc.getLocalAddress());
        try {
            dc.disconnect();
        } catch (BindException x) {
            throw new BindExceptionOnDisconnect(x);
        }

        // check socket options have not changed
        assertEquals(map, options(dc));
    }

    /**
     * Returns a map of the given channel's socket options and values.
     */
    private Map<SocketOption<?>, Object> options(DatagramChannel dc) throws IOException {
        Map<SocketOption<?>, Object> map = new HashMap<>();
        for (SocketOption<?> option : dc.supportedOptions()) {
            try {
                Object value = dc.getOption(option);
                if (value != null) {
                    map.put(option, value);
                }
            } catch (IOException ignore) { }
        }
        return map;
    }

    /**
     * Test that disconnect does not interfere with Selector registrations
     */
    void testSelectorRegistration(DatagramChannel dc) throws IOException {
        try (Selector sel = Selector.open()) {
            dc.configureBlocking(false);
            SelectionKey key = dc.register(sel, SelectionKey.OP_READ);

            // ensure socket is registered
            sel.selectNow();

            dc.connect(dc.getLocalAddress());
            try {
                dc.disconnect();
            } catch (BindException x) {
                throw new BindExceptionOnDisconnect(x);
            }

            // selection key should still be valid
            assertTrue(key.isValid());

            // check blocking mode with non-blocking receive
            ByteBuffer bb = ByteBuffer.allocate(100);
            SocketAddress sender = dc.receive(bb);
            assertTrue(sender == null);

            // send datagram and ensure that channel is selected
            dc.send(ByteBuffer.wrap("Hello".getBytes("UTF-8")), dc.getLocalAddress());
            assertFalse(key.isReadable());
            while (sel.select() == 0);
            assertTrue(key.isReadable());
            sender = dc.receive(bb);
            assertEquals(sender, dc.getLocalAddress());

            // cancel key, flush from Selector, and restore blocking mode
            key.cancel();
            sel.selectNow();
            dc.configureBlocking(true);
        }
    }

    /**
     * Test that disconnect does not interfere with multicast group membership
     */
    void testMulticastGroups(DatagramChannel dc) throws IOException {
        InetAddress localAddress = dc.socket().getLocalAddress();
        InetAddress group;
        if (localAddress instanceof Inet6Address) {
            group = InetAddress.getByName("ff02::a");
        } else {
            group = InetAddress.getByName("225.4.5.6");
        }
        NetworkInterface ni = NetworkInterface.getByInetAddress(localAddress);
        if (ni != null && ni.supportsMulticast()) {
            // join group
            MembershipKey key = dc.join(group, ni);

            dc.connect(dc.getLocalAddress());
            try {
                dc.disconnect();
            } catch (BindException x) {
                throw new BindExceptionOnDisconnect(x);
            }

            // membership key should still be valid
            assertTrue(key.isValid());

            // send datagram to multicast group, should be received
            dc.send(ByteBuffer.wrap("Hello".getBytes("UTF-8")), dc.getLocalAddress());
            ByteBuffer bb = ByteBuffer.allocate(100);
            SocketAddress sender = dc.receive(bb);
            assertEquals(sender, dc.getLocalAddress());

            // drop membership
            key.drop();
        }
    }
}
