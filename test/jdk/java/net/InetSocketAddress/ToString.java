/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8225499 4464064
 * @library /test/lib
 * @summary InetSocketAddress::toString not friendly to IPv6 literal addresses
 * @run junit/othervm ${test.main.class}
 * @run junit/othervm -Djava.net.preferIPv4Stack=true ${test.main.class}
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true ${test.main.class}
 */

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static jdk.test.lib.net.IPSupport.diagnoseConfigurationIssue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToString {

    private static final String loopbackAddr;
    private static final String wildcardAddr;
    private static final String localAddr;

    static {
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            String addr = loopback.getHostAddress();
            if (loopback instanceof Inet6Address) {
                addr = "[" + addr + "]";
            }
            loopbackAddr = addr;

            InetSocketAddress isa = new InetSocketAddress((InetAddress) null, 80);
            addr = isa.getAddress().toString();
            if (isa.getAddress() instanceof Inet6Address) {
                addr = "::/[0:0:0:0:0:0:0:0]";
            }
            wildcardAddr = addr;

            InetAddress ia = InetAddress.getLocalHost();
            addr = ia.toString();
            if (ia instanceof Inet6Address) {
                addr = ia.getHostName() + "/[" + ia.getHostAddress() + "]";
            }
            localAddr = addr;

        } catch (UnknownHostException uhe) {
            throw new RuntimeException(uhe);
        }
    }

    @BeforeAll
    public static void setup() {
        diagnoseConfigurationIssue().ifPresent(Assumptions::abort);
    }

    @Test
    public void NPETest() {
        // Test that InetSocketAddress.toString() does not throw NPE with unresolved address
        assertDoesNotThrow(() -> System.out.println(
                new InetSocketAddress("unresolved", 12345)));
    }

    public static Object[][] fromHostStringAndPort() {
        return new Object[][]{
                // hostname, port number, expected string in format
                // <hostname>/<IP literal>:<port> or
                // <hostname>/<unresolved>:<port> if address is unresolved
                {"::1", 80, "/[0:0:0:0:0:0:0:1]:80"},
                {"fedc:ba98:7654:3210:fedc:ba98:7654:3210", 80, "/[fedc:ba98:7654:3210:fedc:ba98:7654:3210]:80"},
                {"::192.9.5.5", 80, "/[0:0:0:0:0:0:c009:505]:80"},
                {"127.0.0.1", 80, "/127.0.0.1:80"},
                {"::ffff:192.0.2.128", 80, "/192.0.2.128:80"},
                {"0", 80, "/0.0.0.0:80"},
                {":", 80, ":/<unresolved>:80"},
                {":1", 80, ":1/<unresolved>:80"}
        };
    }

    @ParameterizedTest
    @MethodSource("fromHostStringAndPort")
    public void testConstructor(String host, int port, String string) {
        String received = new InetSocketAddress(host, port).toString();
        assertEquals(string, received);
    }

    public static Object[][] fromInetAddressAndPort() throws UnknownHostException {
        InetAddress nullAddr = null;
        return new Object[][]{
                // InetAddress, port number, expected string
                {InetAddress.getLoopbackAddress(), 80, "localhost/" + loopbackAddr + ":80"},
                {InetAddress.getLocalHost(), 80, localAddr + ":80"},
                {InetAddress.getByAddress(new byte[]{1, 1, 1, 1}), 80, "/1.1.1.1:80"},
                {InetAddress.getByAddress(new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}), 80, "/[101:101:101:101:101:101:101:101]:80"},
                {InetAddress.getByName("225.225.225.0"), 80, "/225.225.225.0:80"},
                {nullAddr, 80, wildcardAddr + ":80"}
        };
    }

    @ParameterizedTest
    @MethodSource("fromInetAddressAndPort")
    public void testConstructor(InetAddress addr, int port, String string) {
        String received = new InetSocketAddress(addr, port).toString();
        assertEquals(string, received);
    }

    public static Object[][] unresolvedFromHostStringAndPort() {
        return new Object[][]{
                // hostname, port number, expected string
                {"::1", 80, "::1/<unresolved>:80"},
                {"fedc:ba98:7654:3210:fedc:ba98:7654:3210", 80, "fedc:ba98:7654:3210:fedc:ba98:7654:3210/<unresolved>:80"},
                {"::192.9.5.5", 80, "::192.9.5.5/<unresolved>:80"},
                {"127.0.0.1", 80, "127.0.0.1/<unresolved>:80"},
                {"::ffff:192.0.2.128", 80, "::ffff:192.0.2.128/<unresolved>:80"},
                {"0", 80, "0/<unresolved>:80"},
                {"foo", 80, "foo/<unresolved>:80"},
                {":", 80, ":/<unresolved>:80"},
                {":1", 80, ":1/<unresolved>:80"}
        };
    }

    @ParameterizedTest
    @MethodSource("unresolvedFromHostStringAndPort")
    public void testCreateUnresolved(String host, int port, String string) {
        String received = InetSocketAddress.createUnresolved(host, port).toString();
        assertEquals(string, received);
    }
}
