/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4091803 7021373
 * @summary this tests that the constructor of DatagramPacket rejects
 *          bogus arguments properly.
 * @run junit ${test.main.class}
 */

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Constructor {

    private static final byte[] buf = new byte[128];

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final Class<NullPointerException> NPE = NullPointerException.class;
    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    @Test
    public void testNullPacket() {
        assertThrows(NPE,
                () -> new DatagramPacket(null, 100));
    }
    @Test
    public void testNull() throws Exception {
        assertThrows(NPE, () -> new DatagramPacket(null, 100));
        assertThrows(NPE, () -> new DatagramPacket(null, 0, 10));
        assertThrows(NPE, () -> new DatagramPacket(null, 0, 10, LOOPBACK, 80));
        assertThrows(NPE, () -> new DatagramPacket(null, 10, LOOPBACK, 80));
        assertThrows(NPE, () -> new DatagramPacket(null, 0, 10, new InetSocketAddress(80)));
        assertThrows(NPE, () -> new DatagramPacket(null, 10, new InetSocketAddress(80)));

        // no Exception expected for null addresses
        assertDoesNotThrow(() -> new DatagramPacket(buf, 10, null, 0));
        assertDoesNotThrow(() -> new DatagramPacket(buf, 10, 10, null, 0));
    }

    @Test
    public void testNegativeBufferLength() {
        /* length lesser than buffer length */
        assertThrows(IAE, () -> new DatagramPacket(buf, -128));
    }

    @Test
    public void testPacketLengthTooLarge() {
        /* length greater than buffer length */
        assertThrows(IAE, () -> new DatagramPacket(buf, 256));
    }

    @Test
    public void testNegativePortValue() throws Exception {
        /* negative port */
        InetAddress addr = InetAddress.getLocalHost();

        assertThrows(IAE,
                () -> new DatagramPacket(buf, 100, addr, -1));
    }

    @Test
    public void testPortValueTooLarge() {
        /* invalid port value */
        assertThrows(IAE,
                () -> new DatagramPacket(buf, 128, LOOPBACK, Integer.MAX_VALUE));
    }

    @Test
    public void testSimpleConstructor() {
        int offset = 10;
        int length = 50;
        DatagramPacket pkt = new DatagramPacket(buf, offset, length);

        assertSame(buf, pkt.getData());
        assertEquals(offset, pkt.getOffset());
        assertEquals(length, pkt.getLength());
    }

    @Test
    public void testFullConstructor() {
        int offset = 10;
        int length = 50;
        int port = 8080;
        DatagramPacket packet = new DatagramPacket(buf, offset, length, LOOPBACK, port);

        assertSame(buf, packet.getData());
        assertEquals(offset, packet.getOffset());
        assertEquals(length, packet.getLength());
        assertSame(LOOPBACK, packet.getAddress());
        assertEquals(port, packet.getPort());
    }

    @Test
    public void testDefaultValues() {
        DatagramPacket packet = new DatagramPacket(buf, 0);
        assertNull(packet.getAddress());
        assertEquals(0, packet.getPort());

        DatagramPacket packet1 = new DatagramPacket(buf, 0, 0);
        assertNull(packet1.getAddress());
        assertEquals(0, packet1.getPort());
    }
}
