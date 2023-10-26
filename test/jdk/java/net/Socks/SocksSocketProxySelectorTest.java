/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @bug 8318130
 * @summary Tests that java.net.SocksSocketImpl produces correct arguments
 *      for proxy selector
 * @run junit/othervm SocksSocketProxySelectorTest
 */
public class SocksSocketProxySelectorTest {

    public static final String SHORTEN_IPV6 = "((?<=\\[)0)?:(0:)+";

    @BeforeAll
    public static void beforeTest() {
        ProxySelector.setDefault(new LoggingProxySelector());
    }

    // should match the host name
    public static Stream<String> ipLiterals() {
        return Stream.of("127.0.0.1",
                "[::1]",
                "[fe80::1%1234567890]");
    }

    // should be wrapped in [ ]
    public static Stream<String> shortIpv6Literals() {
        return Stream.of("::1",
                "fe80::1%1234567890");
    }

    // with real interface names in scope
    // should be wrapped in [ ], repeated 0's not trimmed
    public static Stream<String> linkLocalIpv6Literals() throws SocketException {
        return NetworkInterface.networkInterfaces()
                        .flatMap(NetworkInterface::inetAddresses)
                        .filter(InetAddress::isLinkLocalAddress)
                        .filter(Inet6Address.class::isInstance)
                        .map(InetAddress::getHostAddress);
    }

    public static Stream<InetAddress> hostNames() throws UnknownHostException {
        return Stream.of(
                InetAddress.getByAddress("localhost", new byte[] {127,0,0,1}),
                InetAddress.getByAddress("bugs.openjdk.org", new byte[] {127,0,0,1}),
                InetAddress.getByAddress("xn--kda4b0koi.com", new byte[] {127,0,0,1})
                );
    }

    /**
     * Creates a socket connection, which internally triggers proxy selection for the target
     * address. The test has been configured to use a {@link LoggingProxySelector ProxySelector}
     * which throws an {@link IllegalArgumentException} with hostname in exception message.
     * The test then verifies that the hostname matches the expected one.
     *
     * @throws Exception
     */
    @ParameterizedTest
    @MethodSource("ipLiterals")
    public void testIpLiterals(String host) throws Exception {
        try (Socket s1 = new Socket(host, 80)) {
            fail("IOException was expected to be thrown, but wasn't");
        } catch (IOException ioe) {
            // expected
            // now verify the IOE was thrown for the correct expected reason
            if (!(ioe.getCause() instanceof IllegalArgumentException iae)) {
                // rethrow this so that the test output failure will capture the entire/real
                // cause in its stacktrace
                throw ioe;
            }
            assertNotNull(iae.getMessage(), "Host not found");
            assertEquals(host,
                    iae.getMessage().replaceFirst(SHORTEN_IPV6, "::"),
                    "Found unexpected host");
        }
    }

    @ParameterizedTest
    @MethodSource("shortIpv6Literals")
    public void testShortIpv6Literals(String host) throws Exception {
        try (Socket s1 = new Socket(host, 80)) {
            fail("IOException was expected to be thrown, but wasn't");
        } catch (IOException ioe) {
            // expected
            // now verify the IOE was thrown for the correct expected reason
            if (!(ioe.getCause() instanceof IllegalArgumentException iae)) {
                // rethrow this so that the test output failure will capture the entire/real
                // cause in its stacktrace
                throw ioe;
            }
            assertNotNull(iae.getMessage(), "Host not found");
            assertEquals('[' + host + ']',
                    iae.getMessage().replaceFirst(SHORTEN_IPV6, "::"),
                    "Found unexpected host");
        }
    }

    @Test
    public void testLinkLocalIpv6Literals() throws Exception {
        String host = linkLocalIpv6Literals()
                .findFirst()
                .orElseGet(() -> Assumptions.abort("No IPv6 link-local addresses found"));
        System.err.println(host);
        try (Socket s1 = new Socket(host, 80)) {
            fail("IOException was expected to be thrown, but wasn't");
        } catch (IOException ioe) {
            // expected
            // now verify the IOE was thrown for the correct expected reason
            if (!(ioe.getCause() instanceof IllegalArgumentException iae)) {
                // rethrow this so that the test output failure will capture the entire/real
                // cause in its stacktrace
                throw ioe;
            }
            assertNotNull(iae.getMessage(), "Host not found");
            assertEquals('[' + host + ']',
                    iae.getMessage(),
                    "Found unexpected host");
        }
    }

    @ParameterizedTest
    @MethodSource("hostNames")
    public void testHostNames(InetAddress host) throws Exception {
        try (Socket s1 = new Socket(host, 80)) {
            fail("IOException was expected to be thrown, but wasn't");
        } catch (IOException ioe) {
            // expected
            // now verify the IOE was thrown for the correct expected reason
            if (!(ioe.getCause() instanceof IllegalArgumentException iae)) {
                // rethrow this so that the test output failure will capture the entire/real
                // cause in its stacktrace
                throw ioe;
            }
            assertNotNull(iae.getMessage(), "Host not found");
            assertEquals(host.getHostName(),
                    iae.getMessage(),
                    "Found unexpected host");
        }
    }

    /**
     * A {@link ProxySelector} which throws an IllegalArgumentException
     * with the given hostname in exception message
     */
    private static final class LoggingProxySelector extends
            ProxySelector {

        @Override
        public List<Proxy> select(final URI uri) {
            throw new IllegalArgumentException(uri.getHost());
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

        }
    }
}
