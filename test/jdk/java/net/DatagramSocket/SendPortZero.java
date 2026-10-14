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


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8236105 8240533
 * @summary Check that DatagramSocket throws expected
 *          Exception when sending a DatagramPacket with port 0
 * @run junit/othervm ${test.main.class}
 */

public class SendPortZero {
    private static InetAddress loopbackAddr, wildcardAddr;
    private static DatagramSocket datagramSocket, datagramSocketAdaptor;
    private static DatagramPacket loopbackZeroPkt, wildcardZeroPkt, wildcardValidPkt;

    private static final Class<SocketException> SE = SocketException.class;

    @BeforeAll
    public static void setUp() throws IOException {
        datagramSocket = new DatagramSocket();
        datagramSocketAdaptor = DatagramChannel.open().socket();

        byte[] buf = "test".getBytes();

        // Addresses
        loopbackAddr = InetAddress.getLoopbackAddress();
        wildcardAddr = new InetSocketAddress(0).getAddress();

        // Packets
        // loopback w/port 0
        loopbackZeroPkt = new DatagramPacket(buf, 0, buf.length);
        loopbackZeroPkt.setAddress(loopbackAddr);
        loopbackZeroPkt.setPort(0);

        // wildcard w/port 0
        wildcardZeroPkt = new DatagramPacket(buf, 0, buf.length);
        wildcardZeroPkt.setAddress(wildcardAddr);
        wildcardZeroPkt.setPort(0);

        // wildcard addr w/valid port
        // Not currently tested. See JDK-8236807
        wildcardValidPkt = new DatagramPacket(buf, 0, buf.length);
        wildcardValidPkt.setAddress(wildcardAddr);
        wildcardValidPkt.setPort(datagramSocket.getLocalPort());
    }

    @AfterAll
    public static void tearDown() {
        datagramSocket.close();
        datagramSocketAdaptor.close();
    }

    public static Object[][] testCases() {
        return new Object[][]{
                { datagramSocket,        loopbackZeroPkt },
                { datagramSocket,        wildcardZeroPkt },
                // Re-enable when JDK-8236807 fixed
                //{ datagramSocket,        wildcardValidPkt },

                { datagramSocketAdaptor, loopbackZeroPkt },
                { datagramSocketAdaptor, wildcardZeroPkt },
                // Re-enable when JDK-8236807 fixed
                //{ datagramSocketAdaptor, wildcardValidPkt },
        };
    }

    @ParameterizedTest(autoCloseArguments = false) // closed in tearDown
    @MethodSource("testCases")
    public void testSend(DatagramSocket ds, DatagramPacket pkt) {
        assertFalse(ds.isClosed());
        assertThrows(SE, () -> ds.send(pkt));
    }
}
