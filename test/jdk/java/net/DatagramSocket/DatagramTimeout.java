/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4163126 8222829
 * @summary Test to see if timeout hangs. Also checks that
 * negative timeout value fails as expected.
 * @run testng DatagramTimeout
 */
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.channels.DatagramChannel;

import org.testng.annotations.Test;

import static org.testng.Assert.expectThrows;


public class DatagramTimeout {
    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    private static final Class<SocketTimeoutException> STE = SocketTimeoutException.class;

    /**
     * Test DatagramSocket setSoTimeout with a valid timeout value.
     */
    @Test
    public void testSetTimeout() throws Exception {
        try (DatagramSocket s = new DatagramSocket()) {
            byte[] buffer = new byte[50];
            DatagramPacket p = new DatagramPacket(buffer, buffer.length);
            s.setSoTimeout(2);
            expectThrows(STE, () -> s.receive(p));
        }
    }

    /**
     * Test DatagramSocket setSoTimeout with a negative timeout.
     */
    @Test
    public void testSetNegativeTimeout() throws Exception {
        try (DatagramSocket s = new DatagramSocket()) {
            expectThrows(IAE, () -> s.setSoTimeout(-1));
        }
    }

    /**
     * Test DatagramSocketAdaptor setSoTimeout with a negative timeout.
     */
    @Test
    public void testNegativeTimeout() throws Exception {
        try (DatagramChannel dc = DatagramChannel.open()) {
            var s = dc.socket();
            expectThrows(IAE, () -> s.setSoTimeout(-1));
        }
    }
}
