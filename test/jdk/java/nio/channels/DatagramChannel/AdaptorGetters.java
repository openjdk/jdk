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

/* @test
 * @bug 8232673
 * @summary Test the DatagramChannel socket adaptor getter methods
 * @run junit AdaptorGetters
 */

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AdaptorGetters {

    /**
     * Test getters on unbound socket, before and after it is closed.
     */
    @Test
    public void testUnboundSocket() throws Exception {
        DatagramChannel dc = DatagramChannel.open();
        DatagramSocket s = dc.socket();
        try {

            // state
            assertFalse(s.isBound());
            assertFalse(s.isConnected());
            assertFalse(s.isClosed());

            // local address
            assertTrue(s.getLocalAddress().isAnyLocalAddress());
            assertEquals(0, s.getLocalPort());
            assertNull(s.getLocalSocketAddress());

            // remote address
            assertNull(s.getInetAddress());
            assertEquals(-1, s.getPort());

        } finally {
            dc.close();
        }

        // state
        assertFalse(s.isBound());
        assertFalse(s.isConnected());
        assertTrue(s.isClosed());

        // local address
        assertNull(s.getLocalAddress());
        assertEquals(-1, s.getLocalPort());
        assertNull(s.getLocalSocketAddress());

        // remote address
        assertNull(s.getInetAddress());
        assertEquals(-1, s.getPort());
        assertNull(s.getRemoteSocketAddress());
    }

    /**
     * Test getters on bound socket, before and after it is closed.
     */
    @Test
    public void testBoundSocket() throws Exception {
        DatagramChannel dc = DatagramChannel.open();
        DatagramSocket s = dc.socket();
        try {
            dc.bind(new InetSocketAddress(0));
            var localAddress = (InetSocketAddress) dc.getLocalAddress();

            // state
            assertTrue(s.isBound());
            assertFalse(s.isConnected());
            assertFalse(s.isClosed());

            // local address
            assertEquals(localAddress.getAddress(), s.getLocalAddress());
            assertEquals(localAddress.getPort(), s.getLocalPort());
            assertEquals(localAddress, s.getLocalSocketAddress());

            // remote address
            assertNull(s.getInetAddress());
            assertEquals(-1, s.getPort());
            assertNull(s.getRemoteSocketAddress());

        } finally {
            dc.close();
        }

        // state
        assertTrue(s.isBound());
        assertFalse(s.isConnected());
        assertTrue(s.isClosed());

        // local address
        assertNull(s.getLocalAddress());
        assertEquals(-1, s.getLocalPort());
        assertNull(s.getLocalSocketAddress());

        // remote address
        assertNull(s.getInetAddress());
        assertEquals(-1, s.getPort());
        assertNull(s.getRemoteSocketAddress());
    }

    /**
     * Test getters on connected socket, before and after it is closed.
     */
    @Test
    public void testConnectedSocket() throws Exception {
        var loopback = InetAddress.getLoopbackAddress();
        var remoteAddress = new InetSocketAddress(loopback, 7777);
        DatagramChannel dc = DatagramChannel.open();
        DatagramSocket s = dc.socket();
        try {
            dc.connect(remoteAddress);
            var localAddress = (InetSocketAddress) dc.getLocalAddress();

            // state
            assertTrue(s.isBound());
            assertTrue(s.isConnected());
            assertFalse(s.isClosed());

            // local address
            assertEquals(localAddress.getAddress(), s.getLocalAddress());
            assertEquals(localAddress.getPort(), s.getLocalPort());
            assertEquals(localAddress, s.getLocalSocketAddress());

            // remote address
            assertEquals(remoteAddress.getAddress(), s.getInetAddress());
            assertEquals(remoteAddress.getPort(), s.getPort());
            assertEquals(remoteAddress, s.getRemoteSocketAddress());

        } finally {
            dc.close();
        }

        // state
        assertTrue(s.isBound());
        assertTrue(s.isConnected());
        assertTrue(s.isClosed());

        // local address
        assertNull(s.getLocalAddress());
        assertEquals(-1, s.getLocalPort());
        assertNull(s.getLocalSocketAddress());

        // remote address
        assertEquals(remoteAddress.getAddress(), s.getInetAddress());
        assertEquals(remoteAddress.getPort(), s.getPort());
        assertEquals(remoteAddress, s.getRemoteSocketAddress());
    }

    /**
     * Test getters on disconnected socket, before and after it is closed.
     */
    @Test
    public void testDisconnectedSocket() throws Exception {
        DatagramChannel dc = DatagramChannel.open();
        DatagramSocket s = dc.socket();
        try {
            var loopback = InetAddress.getLoopbackAddress();
            dc.connect(new InetSocketAddress(loopback, 7777));
            dc.disconnect();

            var localAddress = (InetSocketAddress) dc.getLocalAddress();

            // state
            assertTrue(s.isBound());
            assertFalse(s.isConnected());
            assertFalse(s.isClosed());

            // local address
            assertEquals(localAddress.getAddress(), s.getLocalAddress());
            assertEquals(localAddress.getPort(), s.getLocalPort());
            assertEquals(localAddress, s.getLocalSocketAddress());

            // remote address
            assertNull(s.getInetAddress());
            assertEquals(-1, s.getPort());
            assertNull(s.getRemoteSocketAddress());


        } finally {
            dc.close();
        }

        // state
        assertTrue(s.isBound());
        assertFalse(s.isConnected());
        assertTrue(s.isClosed());

        // local address
        assertNull(s.getLocalAddress());
        assertEquals(-1, s.getLocalPort());
        assertNull(s.getLocalSocketAddress());

        // remote address
        assertNull(s.getInetAddress());
        assertEquals(-1, s.getPort());
        assertNull(s.getRemoteSocketAddress());
    }
}
