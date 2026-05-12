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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.test.lib.net.IPSupport.hasIPv4;
import static jdk.test.lib.net.IPSupport.hasIPv6;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8236105 8240533
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @summary Check that DatagramChannel throws expected Exception when sending to port 0
 * @run junit/othervm SendPortZero
 * @run junit/othervm -Djava.net.preferIPv4Stack=true SendPortZero
 */

public class SendPortZero {
    private static ByteBuffer buf;
    private static List<DatagramChannel> channels;
    private static InetSocketAddress loopbackZeroAddr, wildcardZeroAddr;
    private static DatagramChannel datagramChannel, datagramChannelIPv4, datagramChannelIPv6;

    private static final Class<SocketException> SE = SocketException.class;

    @BeforeAll
    public static void setUp() throws IOException {
        buf = ByteBuffer.wrap("test".getBytes());

        wildcardZeroAddr = new InetSocketAddress(0);
        loopbackZeroAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        channels = new ArrayList<>();

        datagramChannel = DatagramChannel.open();
        channels.add(datagramChannel);
        if (hasIPv4()) {
            datagramChannelIPv4 = DatagramChannel.open(INET);
            channels.add(datagramChannelIPv4);
        }
        if (hasIPv6()) {
            datagramChannelIPv6 = DatagramChannel.open(INET6);
            channels.add(datagramChannelIPv6);
        }
    }

    @AfterAll
    public static void tearDown() throws IOException {
        for(DatagramChannel ch : channels) {
            ch.close();
        }
    }

    public static List<DatagramChannel> channels() {
        return channels;
    }

    @ParameterizedTest
    @MethodSource("channels")
    public void testChannelSend(DatagramChannel dc) {
        assertTrue(dc.isOpen());
        assertThrows(SE, () -> dc.send(buf, loopbackZeroAddr));
        assertThrows(SE, () -> dc.send(buf, wildcardZeroAddr));
    }

}
