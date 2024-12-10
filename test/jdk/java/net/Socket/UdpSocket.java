/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @run testng/othervm UdpSocket
 * @summary Basic test for a Socket to a UDP socket
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class UdpSocket {

    private static final int MAX_RETRIES = 3;

    /**
     * Test using the Socket API to send/receive datagrams
     */
    public void testSendReceive() throws IOException {
        final String MESSAGE = "hello";

        try (DatagramChannel dc = DatagramChannel.open()) {
            var loopback = InetAddress.getLoopbackAddress();
            dc.bind(new InetSocketAddress(loopback, 0));

            int port = ((InetSocketAddress) dc.getLocalAddress()).getPort();
            try (Socket s = new Socket(loopback, port, false)) {
                // send datagram with socket output stream
                byte[] array1 = MESSAGE.getBytes("UTF-8");
                s.getOutputStream().write(array1);

                // receive the datagram
                var buf = ByteBuffer.allocate(100);
                SocketAddress remote = dc.receive(buf);
                buf.flip();
                assertTrue(buf.remaining() == MESSAGE.length(), "Unexpected size");

                // echo the datagram
                dc.send(buf, remote);

                // receive datagram with the socket input stream
                byte[] array2 = new byte[100];
                int n = s.getInputStream().read(array2);
                assertTrue(n == MESSAGE.length(), "Unexpected size");
                assertEquals(Arrays.copyOf(array1, n), Arrays.copyOf(array2, n),
                            "Unexpected contents");
            }
        }
    }
}
