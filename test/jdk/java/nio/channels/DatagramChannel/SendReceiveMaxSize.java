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
 * @bug 8239355 8242885 8240901
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm SendReceiveMaxSize
 */
/*
 * @test id=preferIPv4Stack
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using an IPv4 only socket.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Djava.net.preferIPv4Stack=true SendReceiveMaxSize
 */
/*
 * @test id=preferIPv6Loopback
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using a dual socket and the loopback
 *          interface.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Dtest.preferLoopback=true SendReceiveMaxSize
 */
/*
 * @test id=preferIPv4Loopback
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS, using an IPv4 only socket and the
 *          loopback interface
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run junit/othervm -Dtest.preferLoopback=true -Djava.net.preferIPv4Stack=true SendReceiveMaxSize
 */

import jdk.test.lib.RandomFactory;
import jdk.test.lib.NetworkConfiguration;
import jdk.test.lib.Platform;
import jdk.test.lib.net.IPSupport;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static java.net.StandardSocketOptions.SO_SNDBUF;
import static java.net.StandardSocketOptions.SO_RCVBUF;
import static jdk.test.lib.net.IPSupport.hasIPv4;
import static jdk.test.lib.net.IPSupport.hasIPv6;
import static jdk.test.lib.net.IPSupport.preferIPv4Stack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SendReceiveMaxSize {
    private final static Class<IOException> IOE = IOException.class;
    private final static Random random = RandomFactory.getRandom();
    private final static boolean PREFER_LOOPBACK = Boolean.getBoolean("test.preferLoopback");

    public interface DatagramChannelSupplier {
        DatagramChannel open() throws IOException;
    }
    static DatagramChannelSupplier supplier(DatagramChannelSupplier supplier) { return supplier; }

    @BeforeAll
    public static void setUp() {
        IPSupport.throwSkippedExceptionIfNonOperational();
    }

    public static List<Arguments> testCases() throws IOException {
        var testcases = new ArrayList<Arguments>();
        var nc = NetworkConfiguration.probe();
        var ipv4Loopback = (Inet4Address) InetAddress.getByName("127.0.0.1");
        var ipv6Loopback = (Inet6Address) InetAddress.getByName("::1");
        if (hasIPv4()) {
            InetAddress IPv4Addr = PREFER_LOOPBACK ? ipv4Loopback
                    : nc.ip4Addresses()
                    .filter(Predicate.not(InetAddress::isLoopbackAddress))
                    .findFirst()
                    .orElse(ipv4Loopback);
            testcases.add(Arguments.of(
                    supplier(() -> DatagramChannel.open()),
                    IPSupport.getMaxUDPSendBufSizeIPv4(),
                    IPv4Addr
            ));
            testcases.add(Arguments.of(
                    supplier(() -> DatagramChannel.open(INET)),
                    IPSupport.getMaxUDPSendBufSizeIPv4(),
                    IPv4Addr
            ));
        }
        if (!preferIPv4Stack() && hasIPv6()) {
            InetAddress IPv6Addr = PREFER_LOOPBACK ? ipv6Loopback
                    : nc.ip6Addresses()
                    .filter(Predicate.not(InetAddress::isLoopbackAddress))
                    .findFirst()
                    .orElse(ipv6Loopback);
            testcases.add(Arguments.of(
                    supplier(() -> DatagramChannel.open()),
                    IPSupport.getMaxUDPSendBufSizeIPv6(),
                    IPv6Addr
            ));
            testcases.add(Arguments.of(
                    supplier(() -> DatagramChannel.open(INET6)),
                    IPSupport.getMaxUDPSendBufSizeIPv6(),
                    IPv6Addr
            ));
        }
        return testcases;
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testGetOption(DatagramChannelSupplier supplier, int capacity, InetAddress host)
            throws IOException {
        if (Platform.isOSX()) {
            try (var dc = supplier.open()){
                assertTrue(dc.getOption(SO_SNDBUF) >= capacity);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testSendReceiveMaxSize(DatagramChannelSupplier supplier, int capacity, InetAddress host)
            throws IOException {
        try (var receiver = DatagramChannel.open()) {
            receiver.bind(new InetSocketAddress(host, 0));
            assertTrue(receiver.getOption(SO_RCVBUF) >= capacity,
                       receiver.getOption(SO_RCVBUF) +
                       " for UDP receive buffer too small to hold capacity " +
                       capacity);
            var port = receiver.socket().getLocalPort();
            var addr = new InetSocketAddress(host, port);

            try (var sender = supplier.open()) {
                sender.bind(new InetSocketAddress(host, 0));
                System.out.format("testSendReceiveMaxSize: sender: %s -> receiver: %s%n",
                        sender.getLocalAddress(), receiver.getLocalAddress());
                if (!Platform.isOSX()) {
                    if (sender.getOption(SO_SNDBUF) < capacity)
                        sender.setOption(SO_SNDBUF, capacity);
                }
                byte[] testData = new byte[capacity];
                random.nextBytes(testData);

                var sendBuf = ByteBuffer.wrap(testData);
                sender.send(sendBuf, addr);
                var receiveBuf = ByteBuffer.allocate(capacity);
                SocketAddress src;
                int count = 0;
                do {
                    receiveBuf.clear();
                    src = receiver.receive(receiveBuf);
                    if (sender.getLocalAddress().equals(src)) break;
                    System.out.println("step1: received unexpected datagram from: " + src);
                    System.out.println("\texpected: " + sender.getLocalAddress());
                    if (++count > 10) {
                        throw new AssertionError("too many unexpected messages");
                    }
                } while (true);

                sendBuf.flip();
                receiveBuf.flip();

                // check that data has been fragmented and re-assembled correctly at receiver
                System.out.println("sendBuf:    " + sendBuf);
                System.out.println("receiveBuf: " + receiveBuf);
                assertEquals(receiveBuf, sendBuf);
                assertEquals(0, sendBuf.compareTo(receiveBuf));

                testData = new byte[capacity - 1];
                random.nextBytes(testData);

                sendBuf = ByteBuffer.wrap(testData);
                sender.send(sendBuf, addr);
                receiveBuf = ByteBuffer.allocate(capacity - 1);
                count = 0;
                do {
                    receiveBuf.clear();
                    src = receiver.receive(receiveBuf);
                    if (sender.getLocalAddress().equals(src)) break;
                    System.out.println("step1: received unexpected datagram from: " + src);
                    System.out.println("\texpected: " + sender.getLocalAddress());
                    if (++count > 10) {
                        throw new AssertionError("too many unexpected messages");
                    }
                } while (true);

                sendBuf.flip();
                receiveBuf.flip();

                // check that data has been fragmented and re-assembled correctly at receiver
                System.out.println("sendBuf:    " + sendBuf);
                System.out.println("receiveBuf: " + receiveBuf);
                assertEquals(receiveBuf, sendBuf);
                assertEquals(0, sendBuf.compareTo(receiveBuf));

                var failSendBuf = ByteBuffer.allocate(capacity + 1);
                assertThrows(IOE, () ->  sender.send(failSendBuf, addr));
            }
        }
    }
}
