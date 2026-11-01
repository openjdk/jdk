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
 * @test
 * @summary Test local address type
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run junit/othervm LocalSocketAddressType
 * @run junit/othervm -Djava.net.preferIPv4Stack=true LocalSocketAddressType
 */

import jdk.test.lib.NetworkConfiguration;
import jdk.test.lib.net.IPSupport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.stream.Stream;

import static java.lang.System.out;

import static jdk.test.lib.net.IPSupport.*;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalSocketAddressType {

    @BeforeAll()
    public static void setup() {
        IPSupport.printPlatformSupport(out);
        diagnoseConfigurationIssue().ifPresent(Assumptions::abort);
    }

    public static Iterator<Object[]> addresses() throws Exception {
        NetworkConfiguration nc = NetworkConfiguration.probe();
        return Stream.concat(nc.ip4Addresses(), nc.ip6Addresses())
                .map(ia -> new Object[] { new InetSocketAddress(ia, 0) })
                .iterator();
    }

    @ParameterizedTest
    @MethodSource("addresses")
    public void testSocketChannel(InetSocketAddress addr) throws Exception {
        try (var c = SocketChannel.open()) {
            Class<? extends InetAddress> cls = addr.getAddress().getClass();
            InetAddress ia = ((InetSocketAddress)c.bind(addr).getLocalAddress()).getAddress();
            assertEquals(cls, ia.getClass());
            ia = c.socket().getLocalAddress();
            assertEquals(cls, ia.getClass());
        }
    }

    @ParameterizedTest
    @MethodSource("addresses")
    public void testServerSocketChannel(InetSocketAddress addr) throws Exception {
        try (var c = ServerSocketChannel.open()) {
            Class<? extends InetAddress> cls = addr.getAddress().getClass();
            InetAddress ia = ((InetSocketAddress)c.bind(addr).getLocalAddress()).getAddress();
            assertEquals(cls, ia.getClass());
            ia = c.socket().getInetAddress();
            assertEquals(cls, ia.getClass());
        }
    }

    @ParameterizedTest
    @MethodSource("addresses")
    public void testDatagramChannel(InetSocketAddress addr) throws Exception {
        try (var c = DatagramChannel.open()) {
            Class<? extends InetAddress> cls = addr.getAddress().getClass();
            InetAddress ia = ((InetSocketAddress)c.bind(addr).getLocalAddress()).getAddress();
            assertEquals(cls, ia.getClass());
            ia = c.socket().getLocalAddress();
            assertEquals(cls, ia.getClass());
        }
    }
}
