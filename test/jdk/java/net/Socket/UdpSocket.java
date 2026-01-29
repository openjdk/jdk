/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.Socket;

import org.testng.annotations.Test;
import static org.testng.Assert.fail;

/*
 * @test
 * @summary Basic test for the UDP sockets through the java.net.Socket constructors
 * @run testng UdpSocket
 */
public class UdpSocket {

    /**
     * Verifies that the {@code Socket} constructors that take the {@code stream}
     * parameter don't allow construction of UDP sockets.
     */
    @Test
    public void testUDPConstructors() throws Exception {
        try {
            new Socket("doesnotmatter", 12345, false);
            fail("Socket constructor was expected to throw IllegalArgumentException" +
                    " for stream=false, but didn't");
        } catch (IllegalArgumentException iae) {
            // verify it's thrown for the right reason
            assertExceptionMessage(iae);
        }

        try {
            new Socket(InetAddress.getLoopbackAddress(), 12345, false);
            fail("Socket constructor was expected to throw IllegalArgumentException" +
                    " for stream=false, but didn't");
        } catch (IllegalArgumentException iae) {
            // verify it's thrown for the right reason
            assertExceptionMessage(iae);
        }
    }

    private static void assertExceptionMessage(final IllegalArgumentException iae) {
        final String msg = iae.getMessage();
        if (msg != null && msg.contains(
                "Socket constructor does not support creation of datagram socket")) {
            // contains the expected message
            return;
        }
        // unexpected exception message, propagate the original exception
        throw iae;
    }
}
