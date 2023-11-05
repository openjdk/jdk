/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 8043758
 * @summary Datagram Transport Layer Security (DTLS)
 * @modules java.base/sun.security.util
 * @library /test/lib
 * @build DTLSOverDatagram
 * @run main/othervm InvalidRecords
 */

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test that if handshake messages are changed, the handshake would fail
 * because of handshaking hash verification.
 */
public class InvalidRecords extends DTLSOverDatagram {
    private static final AtomicBoolean needInvalidRecords = new AtomicBoolean(true);

    public static void main(String[] args) throws Exception {
        InvalidRecords testCase = new InvalidRecords();
        testCase.runTest(testCase);

        if (needInvalidRecords.get()) {
            // if this is true, the createHandshakePacket() method
            // was NOT called twice to create ClientHello messages
            throw new RuntimeException(
                    "The invalid handshake packet was not"
                    + " rejected as it should have been.");
        }
    }


    @Override
    DatagramPacket createHandshakePacket(byte[] ba, SocketAddress socketAddr) {
        if (needInvalidRecords.get() && (ba.length >= 60) &&
                (ba[0x00] == (byte)0x16) && (ba[0x0D] == (byte)0x01) &&
                (ba[0x3B] == (byte)0x00) && (ba[0x3C] > 0)) {

            // ba[0x00]: record type
            // ba[0x0D]: handshake type
            // ba[0x3B]: length of session ID
            // ba[0x3C]: length of cookie

            // ClientHello with cookie
            needInvalidRecords.set(false);
            System.out.println("invalidate ClientHello message");
            if (ba[ba.length - 1] == (byte)0xFF) {
                ba[ba.length - 1] = (byte)0xFE;
            } else {
                ba[ba.length - 1] = (byte)0xFF;
            }
        }

        return super.createHandshakePacket(ba, socketAddr);
    }
}
