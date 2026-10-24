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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.NetworkConfiguration;
import static jdk.test.lib.net.IPSupport.diagnoseConfigurationIssue;
import static java.lang.String.format;
import static java.lang.System.out;
import static java.net.StandardSocketOptions.IP_MULTICAST_IF;
import static java.util.stream.Collectors.toList;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @test
 * @bug 8236441
 * @summary Bound MulticastSocket fails when setting outbound interface on Windows
 * @library /test/lib
 * @run junit ${test.main.class}
 * @run junit/othervm -Djava.net.preferIPv4Stack=true ${test.main.class}
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true ${test.main.class}
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Stack=true ${test.main.class}
 */
public class IPMulticastIF {

    @BeforeAll
    public static void sanity() {
        diagnoseConfigurationIssue().ifPresent(Assumptions::abort);
        NetworkConfiguration.printSystemConfiguration(out);
    }

    public static Object[][] positive() throws Exception {
        List<InetAddress> addrs = List.of(InetAddress.getLocalHost(),
                                          InetAddress.getLoopbackAddress());
        List<Object[]> list = new ArrayList<>();
        NetworkConfiguration nc = NetworkConfiguration.probe();
        // retains only network interface whose bound addresses match
        addrs.stream().forEach(a -> nc.multicastInterfaces(true)
                .filter(nif -> nif.inetAddresses().toList().contains(a))
                .map(nif -> new Object[] { new InetSocketAddress(a, 0), nif })
                .forEach(list::add) );
        // any network interface should work with the wildcard address
        nc.multicastInterfaces(true)
                .map(nif -> new Object[] {new InetSocketAddress(0), nif})
                .forEach(list::add);
        return list.stream().toArray(Object[][]::new);
    }

    public static Object[][] interfaces() throws Exception {
        List<Object[]> list = new ArrayList<>();
        NetworkConfiguration nc = NetworkConfiguration.probe();
        nc.multicastInterfaces(true)
                .map(nif -> new Object[] {nif})
                .forEach(list::add);

        return list.stream().toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("positive")
    public void testSetGetInterfaceBound(InetSocketAddress bindAddr, NetworkInterface nif)
        throws Exception
    {
        out.println(format("\n\n--- testSetGetInterfaceBound bindAddr=[%s], nif=[%s]", bindAddr, nif));
        try (MulticastSocket ms = new MulticastSocket(bindAddr)) {
            ms.setNetworkInterface(nif);
            NetworkInterface msNetIf = ms.getNetworkInterface();
            assertEquals(nif, msNetIf);
        }
    }

    @ParameterizedTest
    @MethodSource("interfaces")
    public void testSetGetInterfaceUnbound(NetworkInterface nif)
        throws Exception
    {
        out.println(format("\n\n--- testSetGetInterfaceUnbound nif=[%s]", nif));
        try (MulticastSocket ms = new MulticastSocket()) {
            ms.setNetworkInterface(nif);
            NetworkInterface msNetIf = ms.getNetworkInterface();
            assertEquals(nif, msNetIf);
        }
    }

    @ParameterizedTest
    @MethodSource("positive")
    public void testSetGetOptionBound(InetSocketAddress bindAddr, NetworkInterface nif)
        throws Exception
    {
        out.println(format("\n\n--- testSetGetOptionBound bindAddr=[%s], nif=[%s]", bindAddr, nif));
        try (MulticastSocket ms = new MulticastSocket(bindAddr)) {
            ms.setOption(IP_MULTICAST_IF, nif);
            NetworkInterface msNetIf = ms.getOption(IP_MULTICAST_IF);
            assertEquals(nif, msNetIf);
        }
    }

    @ParameterizedTest
    @MethodSource("interfaces")
    public void testSetGetOptionUnbound(NetworkInterface nif)
        throws Exception
    {
        out.println(format("\n\n--- testSetGetOptionUnbound nif=[%s]", nif));
        try (MulticastSocket ms = new MulticastSocket()) {
            ms.setOption(IP_MULTICAST_IF, nif);
            NetworkInterface msNetIf = ms.getOption(IP_MULTICAST_IF);
            assertEquals(nif, msNetIf);
        }
    }

    // -- get without set

    public static Object[][] bindAddresses() throws Exception {
        return new Object[][] {
            { new InetSocketAddress(InetAddress.getLocalHost(), 0)       },
            { new InetSocketAddress(InetAddress.getLoopbackAddress(), 0) },
        };
    }

    @ParameterizedTest
    @MethodSource("bindAddresses")
    public void testGetInterfaceBound(InetSocketAddress bindAddr)
        throws Exception
    {
        out.println(format("\n\n--- testGetInterfaceBound bindAddr=[%s]", bindAddr));
        try (MulticastSocket ms = new MulticastSocket(bindAddr)) {
            assertPlaceHolder(ms.getNetworkInterface());
        }
    }

    @Test
    public void testGetInterfaceUnbound() throws Exception {
        out.println("\n\n--- testGetInterfaceUnbound ");
        try (MulticastSocket ms = new MulticastSocket()) {
            assertPlaceHolder(ms.getNetworkInterface());
        }
    }

    @ParameterizedTest
    @MethodSource("bindAddresses")
    public void testGetOptionBound(InetSocketAddress bindAddr)
        throws Exception
    {
        out.println(format("\n\n--- testGetOptionBound bindAddr=[%s]", bindAddr));
        try (MulticastSocket ms = new MulticastSocket(bindAddr)) {
            assertEquals(null, ms.getOption(IP_MULTICAST_IF));
        }
    }

    @Test
    public void testGetOptionUnbound() throws Exception {
        out.println("\n\n--- testGetOptionUnbound ");
        try (MulticastSocket ms = new MulticastSocket()) {
            assertEquals(null, ms.getOption(IP_MULTICAST_IF));
        }
    }

    // Asserts that the placeholder NetworkInterface has a single InetAddress
    // that represent any local address.
    static void assertPlaceHolder(NetworkInterface nif) {
        List<InetAddress> addrs = nif.inetAddresses().collect(toList());
        assertEquals(1, addrs.size());
        assertTrue(addrs.get(0).isAnyLocalAddress());
    }
}
