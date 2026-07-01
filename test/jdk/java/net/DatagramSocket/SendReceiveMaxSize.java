/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8242885 8250886 8240901
 * @key randomness
 * @summary This test verifies that on macOS, the send buffer size is configured
 *          by default so that none of our implementations of the UDP protocol
 *          will fail with a "packet too large" exception when trying to send a
 *          packet of the maximum possible size allowed by the protocol.
 *          However, an exception is expected if the packet size exceeds that
 *          limit.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm ${test.main.class}
 */
/*
 * @test id=preferIPv4Stack
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using an IPv4 only socket.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Djava.net.preferIPv4Stack=true ${test.main.class}
 */
/*
 * @test id=preferIPv6Addresses
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using a dual socket and prefering
 *          IPv6 addresses.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true ${test.main.class}
 */
/*
 * @test id=preferLoopback
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using a dual socket and the loopback
 *          interface.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Dtest.preferLoopback=true ${test.main.class}
 */
/*
 * @test id=preferIPv6Loopback
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using a dual socket and the loopback
 *          interface.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Dtest.preferLoopback=true -Djava.net.preferIPv6Addresses=true ${test.main.class}
 */
/*
 * @test id=preferIPv4Loopback
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using an IPv4 only socket and the
 *          loopback interface
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Dtest.preferLoopback=true -Djava.net.preferIPv4Stack=true ${test.main.class}
 */

import jdk.test.lib.RandomFactory;
import jdk.test.lib.Platform;
import jdk.test.lib.net.IPSupport;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.channels.DatagramChannel;
import java.util.Random;

import static java.net.StandardSocketOptions.SO_RCVBUF;
import static jdk.test.lib.net.IPSupport.diagnoseConfigurationIssue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SendReceiveMaxSize {

    private final static boolean PREFER_LOOPBACK = Boolean.getBoolean("test.preferLoopback");

    private static int BUF_LIMIT;
    private static InetAddress HOST_ADDR;
    private final static int IPV4_SNDBUF = IPSupport.getMaxUDPSendBufSizeIPv4();
    private final static int IPV6_SNDBUF = IPSupport.getMaxUDPSendBufSizeIPv6();
    private final static Class<IOException> IOE = IOException.class;
    private final static Random random = RandomFactory.getRandom();

    public interface DatagramSocketSupplier {
        DatagramSocket open() throws IOException;
    }
    static DatagramSocketSupplier supplier(DatagramSocketSupplier supplier) { return supplier; }

    @BeforeAll
    public static void setUp() throws IOException {
        // skip test if the configuration is not operational
        diagnoseConfigurationIssue().ifPresent(Assumptions::abort);
        HOST_ADDR = PREFER_LOOPBACK ? InetAddress.getLoopbackAddress() : InetAddress.getLocalHost();
        BUF_LIMIT = (HOST_ADDR instanceof Inet6Address) ? IPV6_SNDBUF : IPV4_SNDBUF;
        System.out.printf("Host address: %s, Buffer limit: %d%n", HOST_ADDR, BUF_LIMIT);
    }

    public static Object[][] testCases() {
        var ds = supplier(() -> new DatagramSocket());
        var ms = supplier(() -> new MulticastSocket());
        var dsa = supplier(() -> DatagramChannel.open().socket());
        return new Object[][]{
                { "DatagramSocket",        BUF_LIMIT - 1, ds,   null },
                { "DatagramSocket",        BUF_LIMIT,     ds,   null },
                { "DatagramSocket",        BUF_LIMIT + 1, ds,   IOE  },
                { "MulticastSocket",       BUF_LIMIT - 1, ms,   null },
                { "MulticastSocket",       BUF_LIMIT,     ms,   null },
                { "MulticastSocket",       BUF_LIMIT + 1, ms,   IOE  },
                { "DatagramSocketAdaptor", BUF_LIMIT - 1, dsa,  null },
                { "DatagramSocketAdaptor", BUF_LIMIT,     dsa,  null },
                { "DatagramSocketAdaptor", BUF_LIMIT + 1, dsa,  IOE  },
        };
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testSendReceiveMaxSize(String name, int capacity,
                                       DatagramSocketSupplier supplier,
                                       Class<? extends Exception> exception) throws IOException {
        try (var receiver = new DatagramSocket(new InetSocketAddress(HOST_ADDR, 0))) {
            assertTrue(receiver.getOption(SO_RCVBUF) >= capacity,
                       receiver.getOption(SO_RCVBUF) +
                       " for UDP receive buffer too small to hold capacity " +
                       capacity);
            var port = receiver.getLocalPort();
            var addr = new InetSocketAddress(HOST_ADDR, port);
            try (var sender = supplier.open()) {
                if (!Platform.isOSX()) {
                    if (sender.getSendBufferSize() < capacity)
                        sender.setSendBufferSize(capacity);
                }
                byte[] testData = new byte[capacity];
                random.nextBytes(testData);
                var sendPkt = new DatagramPacket(testData, capacity, addr);

                if (exception != null) {
                    Exception ex = assertThrows(exception, () -> sender.send(sendPkt));
                    System.out.println(name + " got expected exception: " + ex);
                } else {
                    sender.send(sendPkt);
                    var receivePkt = new DatagramPacket(new byte[capacity], capacity);
                    receiver.receive(receivePkt);

                    // check packet data has been fragmented and re-assembled correctly at receiver
                    assertEquals(capacity, receivePkt.getLength());
                    assertArrayEquals(testData, receivePkt.getData());
                }
            }
        }
    }
}
