/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8043758 8307383
 * @summary Datagram Transport Layer Security (DTLS)
 * @modules java.base/sun.security.util
 * @library /test/lib
 * @build DTLSOverDatagram
 * @run main/othervm InvalidRecords
 */

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
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
            // We will alter the compression method field in order to make the cookie
            // check fail.
            ByteBuffer chRec = ByteBuffer.wrap(ba);
            // Skip 59 bytes past the record header (13), the handshake header (12),
            // the protocol version (2), and client random (32)
            chRec.position(59);
            // Jump past the session ID
            int len = Byte.toUnsignedInt(chRec.get());
            chRec.position(chRec.position() + len);
            // Skip the cookie
            len = Byte.toUnsignedInt(chRec.get());
            chRec.position(chRec.position() + len);
            // Skip past cipher suites
            len = Short.toUnsignedInt(chRec.getShort());
            chRec.position(chRec.position() + len);
            // Read the data on the compression methods, should be at least 1
            len = Byte.toUnsignedInt(chRec.get());
            if (len >= 1) {
                System.out.println("Detected compression methods (count = " + len + ")");
            } else {
                ba[ba.length - 1] = (byte)0xFF;
                throw new RuntimeException("Got zero length comp methods");
            }
            // alter the first comp method.
            int compMethodVal = Byte.toUnsignedInt(chRec.get(chRec.position()));
            System.out.println("Changing value at position " + chRec.position() +
                    " from " + compMethodVal + " to " + ++compMethodVal);
            chRec.put(chRec.position(), (byte)compMethodVal);
        }

        return super.createHandshakePacket(ba, socketAddr);
    }
}
