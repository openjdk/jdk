/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @summary Test DatagramChannel local address after disconnect.
 * @requires (os.family != "mac")
 * @run testng/othervm AddressesAfterDisconnect
 * @run testng/othervm -Djava.net.preferIPv6Addresses=true AddressesAfterDisconnect
 * @run testng/othervm -Djava.net.preferIPv4Stack=true AddressesAfterDisconnect
 */

import jdk.test.lib.net.IPSupport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

public class AddressesAfterDisconnect {

    public static void main(String[] args) throws IOException {
        new AddressesAfterDisconnect().execute();
    }

    @Test
    public void execute() throws IOException {
        IPSupport.throwSkippedExceptionIfNonOperational();
        boolean preferIPv6 = Boolean.getBoolean("java.net.preferIPv6Addresses");

        // test with default protocol family
        try (DatagramChannel dc = DatagramChannel.open()) {
            System.out.println("Test with default");
            dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            test(dc);
            test(dc);
        }

        if (IPSupport.hasIPv6()) {
            // test with IPv6 only
            System.out.println("Test with IPv6 only");
            try (DatagramChannel dc = DatagramChannel.open(StandardProtocolFamily.INET6)) {
                dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                test(dc);
                test(dc);
            }
        }

        if (IPSupport.hasIPv4() && !preferIPv6) {
            // test with IPv4 only
            System.out.println("Test with IPv4 only");
            try (DatagramChannel dc = DatagramChannel.open(StandardProtocolFamily.INET)) {
                dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                test(dc);
                test(dc);
            }
        }
    }

    /**
     * Connect DatagramChannel to a server, write a datagram and disconnect. Invoke
     * a second or subsequent time with the same DatagramChannel instance to check
     * that disconnect works as expected.
     */
    static void test(DatagramChannel dc) throws IOException {
        SocketAddress local = dc.getLocalAddress();
        try (DatagramChannel server = DatagramChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            SocketAddress remote = server.getLocalAddress();
            dc.connect(remote);
            assertTrue(dc.isConnected());
            // comment the following two lines on OS X to see JDK-8231259
            assertEquals(dc.getLocalAddress(), local, "local address after connect");
            assertEquals(dc.getRemoteAddress(), remote, "remote address after connect");
            dc.disconnect();
            assertFalse(dc.isConnected());
            assertEquals(dc.getLocalAddress(), local, "local address after disconnect");
            assertEquals(dc.getRemoteAddress(), null, "remote address after disconnect");
        }
    }

}
