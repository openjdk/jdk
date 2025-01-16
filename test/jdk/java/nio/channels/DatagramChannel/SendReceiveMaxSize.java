/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8239355 8242885 8240901
 * @key randomness
 * @summary Check that it is possible to send and receive datagrams of
 *          maximum size on macOS.
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run testng/othervm SendReceiveMaxSize
 * @run testng/othervm -Djava.net.preferIPv4Stack=true SendReceiveMaxSize
 */

import jdk.test.lib.RandomFactory;
import jdk.test.lib.NetworkConfiguration;
import jdk.test.lib.Platform;
import jdk.test.lib.net.IPSupport;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Random;
import java.util.function.Predicate;

import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static java.net.StandardSocketOptions.SO_SNDBUF;
import static java.net.StandardSocketOptions.SO_RCVBUF;
import static jdk.test.lib.net.IPSupport.hasIPv4;
import static jdk.test.lib.net.IPSupport.hasIPv6;
import static jdk.test.lib.net.IPSupport.preferIPv4Stack;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class SendReceiveMaxSize {
    private final static Class<IOException> IOE = IOException.class;
    private final static Random random = RandomFactory.getRandom();

    public interface DatagramChannelSupplier {
        DatagramChannel open() throws IOException;
    }
    static DatagramChannelSupplier supplier(DatagramChannelSupplier supplier) { return supplier; }

    @BeforeTest
    public void setUp() {
        IPSupport.throwSkippedExceptionIfNonOperational();
    }

    @DataProvider
    public Object[][] invariants() throws IOException {
        var testcases = new ArrayList<Object[]>();
        var nc = NetworkConfiguration.probe();
        if (hasIPv4()) {
            InetAddress IPv4Addr = nc.ip4Addresses()
                    .filter(Predicate.not(InetAddress::isLoopbackAddress))
                    .findFirst()
                    .orElse((Inet4Address) InetAddress.getByName("127.0.0.1"));
            testcases.add(new Object[]{
                    supplier(() -> DatagramChannel.open()),
                    IPSupport.getMaxUDPSendBufSizeIPv4(),
                    IPv4Addr
            });
            testcases.add(new Object[]{
                    supplier(() -> DatagramChannel.open(INET)),
                    IPSupport.getMaxUDPSendBufSizeIPv4(),
                    IPv4Addr
            });
        }
        if (!preferIPv4Stack() && hasIPv6()) {
            InetAddress IPv6Addr = nc.ip6Addresses()
                    .filter(Predicate.not(InetAddress::isLoopbackAddress))
                    .findFirst()
                    .orElse((Inet6Address) InetAddress.getByName("::1"));
            testcases.add(new Object[]{
                    supplier(() -> DatagramChannel.open()),
                    IPSupport.getMaxUDPSendBufSizeIPv6(),
                    IPv6Addr
            });
            testcases.add(new Object[]{
                    supplier(() -> DatagramChannel.open(INET6)),
                    IPSupport.getMaxUDPSendBufSizeIPv6(),
                    IPv6Addr
            });
        }
        return testcases.toArray(Object[][]::new);
    }

    @Test(dataProvider = "invariants")
    public void testGetOption(DatagramChannelSupplier supplier, int capacity, InetAddress host)
            throws IOException {
        if (Platform.isOSX()) {
            try (var dc = supplier.open()){
                assertTrue(dc.getOption(SO_SNDBUF) >= capacity);
            }
        }
    }

    @Test(dataProvider = "invariants")
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
                assertEquals(sendBuf, receiveBuf);
                assertEquals(sendBuf.compareTo(receiveBuf), 0);

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
                assertEquals(sendBuf, receiveBuf);
                assertEquals(sendBuf.compareTo(receiveBuf), 0);

                var failSendBuf = ByteBuffer.allocate(capacity + 1);
                assertThrows(IOE, () ->  sender.send(failSendBuf, addr));
            }
        }
    }
}
