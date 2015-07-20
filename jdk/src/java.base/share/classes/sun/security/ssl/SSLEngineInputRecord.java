/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.ssl;

import java.io.*;
import java.nio.*;

import javax.crypto.BadPaddingException;

import javax.net.ssl.*;

import sun.misc.HexDumpEncoder;


/**
 * {@code InputRecord} implementation for {@code SSLEngine}.
 */
final class SSLEngineInputRecord extends InputRecord implements SSLRecord {
    // used by handshake hash computation for handshake fragment
    private byte prevType = -1;
    private int hsMsgOff = 0;
    private int hsMsgLen = 0;

    private boolean formatVerified = false;     // SSLv2 ruled out?

    SSLEngineInputRecord() {
        this.readAuthenticator = MAC.TLS_NULL;
    }

    @Override
    int estimateFragmentSize(int packetSize) {
        int macLen = 0;
        if (readAuthenticator instanceof MAC) {
            macLen = ((MAC)readAuthenticator).MAClen();
        }

        if (packetSize > 0) {
            return readCipher.estimateFragmentSize(
                    packetSize, macLen, headerSize);
        } else {
            return Record.maxDataSize;
        }
    }

    @Override
    int bytesInCompletePacket(ByteBuffer packet) throws SSLException {
        /*
         * SSLv2 length field is in bytes 0/1
         * SSLv3/TLS length field is in bytes 3/4
         */
        if (packet.remaining() < 5) {
            return -1;
        }

        int pos = packet.position();
        byte byteZero = packet.get(pos);

        int len = 0;

        /*
         * If we have already verified previous packets, we can
         * ignore the verifications steps, and jump right to the
         * determination.  Otherwise, try one last hueristic to
         * see if it's SSL/TLS.
         */
        if (formatVerified ||
                (byteZero == ct_handshake) || (byteZero == ct_alert)) {
            /*
             * Last sanity check that it's not a wild record
             */
            ProtocolVersion recordVersion = ProtocolVersion.valueOf(
                                    packet.get(pos + 1), packet.get(pos + 2));

            // check the record version
            checkRecordVersion(recordVersion, false);

            /*
             * Reasonably sure this is a V3, disable further checks.
             * We can't do the same in the v2 check below, because
             * read still needs to parse/handle the v2 clientHello.
             */
            formatVerified = true;

            /*
             * One of the SSLv3/TLS message types.
             */
            len = ((packet.get(pos + 3) & 0xFF) << 8) +
                   (packet.get(pos + 4) & 0xFF) + headerSize;

        } else {
            /*
             * Must be SSLv2 or something unknown.
             * Check if it's short (2 bytes) or
             * long (3) header.
             *
             * Internals can warn about unsupported SSLv2
             */
            boolean isShort = ((byteZero & 0x80) != 0);

            if (isShort &&
                    ((packet.get(pos + 2) == 1) || packet.get(pos + 2) == 4)) {

                ProtocolVersion recordVersion = ProtocolVersion.valueOf(
                                    packet.get(pos + 3), packet.get(pos + 4));

                // check the record version
                checkRecordVersion(recordVersion, true);

                /*
                 * Client or Server Hello
                 */
                int mask = (isShort ? 0x7F : 0x3F);
                len = ((byteZero & mask) << 8) +
                        (packet.get(pos + 1) & 0xFF) + (isShort ? 2 : 3);

            } else {
                // Gobblygook!
                throw new SSLException(
                        "Unrecognized SSL message, plaintext connection?");
            }
        }

        return len;
    }

    @Override
    void checkRecordVersion(ProtocolVersion recordVersion,
            boolean allowSSL20Hello) throws SSLException {

        if (recordVersion.maybeDTLSProtocol()) {
            throw new SSLException(
                    "Unrecognized record version " + recordVersion +
                    " , DTLS packet?");
        }

        // Check if the record version is too old.
        if ((recordVersion.v < ProtocolVersion.MIN.v)) {
            // if it's not SSLv2, we're out of here.
            if (!allowSSL20Hello ||
                    (recordVersion.v != ProtocolVersion.SSL20Hello.v)) {
                throw new SSLException(
                    "Unsupported record version " + recordVersion);
            }
        }
    }

    @Override
    Plaintext decode(ByteBuffer packet)
            throws IOException, BadPaddingException {

        if (isClosed) {
            return null;
        }

        if (debug != null && Debug.isOn("packet")) {
             Debug.printHex(
                    "[Raw read]: length = " + packet.remaining(), packet);
        }

        // The caller should have validated the record.
        if (!formatVerified) {
            formatVerified = true;

            /*
             * The first record must either be a handshake record or an
             * alert message. If it's not, it is either invalid or an
             * SSLv2 message.
             */
            int pos = packet.position();
            byte byteZero = packet.get(pos);
            if (byteZero != ct_handshake && byteZero != ct_alert) {
                return handleUnknownRecord(packet);
            }
        }

        return decodeInputRecord(packet);
    }

    private Plaintext decodeInputRecord(ByteBuffer packet)
            throws IOException, BadPaddingException {

        //
        // The packet should be a complete record, or more.
        //

        int srcPos = packet.position();
        int srcLim = packet.limit();

        byte contentType = packet.get();                   // pos: 0
        byte majorVersion = packet.get();                  // pos: 1
        byte minorVersion = packet.get();                  // pos: 2
        int contentLen = ((packet.get() & 0xFF) << 8) +
                          (packet.get() & 0xFF);           // pos: 3, 4

        if (debug != null && Debug.isOn("record")) {
             System.out.println(Thread.currentThread().getName() +
                    ", READ: " +
                    ProtocolVersion.valueOf(majorVersion, minorVersion) +
                    " " + Record.contentName(contentType) + ", length = " +
                    contentLen);
        }

        //
        // Check for upper bound.
        //
        // Note: May check packetSize limit in the future.
        if (contentLen < 0 || contentLen > maxLargeRecordSize - headerSize) {
            throw new SSLProtocolException(
                "Bad input record size, TLSCiphertext.length = " + contentLen);
        }

        //
        // check for handshake fragment
        //
        if ((contentType != ct_handshake) && (hsMsgOff != hsMsgLen)) {
            throw new SSLProtocolException(
                    "Expected to get a handshake fragment");
        }

        //
        // Decrypt the fragment
        //
        int recLim = srcPos + SSLRecord.headerSize + contentLen;
        packet.limit(recLim);
        packet.position(srcPos + SSLRecord.headerSize);

        ByteBuffer plaintext;
        try {
            plaintext =
                decrypt(readAuthenticator, readCipher, contentType, packet);
        } finally {
            // comsume a complete record
            packet.limit(srcLim);
            packet.position(recLim);
        }

        //
        // handshake hashing
        //
        if (contentType == ct_handshake) {
            int pltPos = plaintext.position();
            int pltLim = plaintext.limit();
            int frgPos = pltPos;
            for (int remains = plaintext.remaining(); remains > 0;) {
                int howmuch;
                byte handshakeType;
                if (hsMsgOff < hsMsgLen) {
                    // a fragment of the handshake message
                    howmuch = Math.min((hsMsgLen - hsMsgOff), remains);
                    handshakeType = prevType;

                    hsMsgOff += howmuch;
                    if (hsMsgOff == hsMsgLen) {
                        // Now is a complete handshake message.
                        hsMsgOff = 0;
                        hsMsgLen = 0;
                    }
                } else {    // hsMsgOff == hsMsgLen, a new handshake message
                    handshakeType = plaintext.get();
                    int handshakeLen = ((plaintext.get() & 0xFF) << 16) |
                                       ((plaintext.get() & 0xFF) << 8) |
                                        (plaintext.get() & 0xFF);
                    plaintext.position(frgPos);
                    if (remains < (handshakeLen + 4)) { // 4: handshake header
                        // This handshake message is fragmented.
                        prevType = handshakeType;
                        hsMsgOff = remains - 4;         // 4: handshake header
                        hsMsgLen = handshakeLen;
                    }

                    howmuch = Math.min(handshakeLen + 4, remains);
                }

                plaintext.limit(frgPos + howmuch);

                if (handshakeType == HandshakeMessage.ht_hello_request) {
                    // omitted from handshake hash computation
                } else if ((handshakeType != HandshakeMessage.ht_finished) &&
                    (handshakeType != HandshakeMessage.ht_certificate_verify)) {

                    if (handshakeHash == null) {
                        // used for cache only
                        handshakeHash = new HandshakeHash(false);
                    }
                    handshakeHash.update(plaintext);
                } else {
                    // Reserve until this handshake message has been processed.
                    if (handshakeHash == null) {
                        // used for cache only
                        handshakeHash = new HandshakeHash(false);
                    }
                    handshakeHash.reserve(plaintext);
                }

                plaintext.position(frgPos + howmuch);
                plaintext.limit(pltLim);

                frgPos += howmuch;
                remains -= howmuch;
            }

            plaintext.position(pltPos);
        }

        return new Plaintext(contentType,
                majorVersion, minorVersion, -1, -1L, plaintext);
                // recordEpoch, recordSeq, plaintext);
    }

    private Plaintext handleUnknownRecord(ByteBuffer packet)
            throws IOException, BadPaddingException {

        //
        // The packet should be a complete record.
        //
        int srcPos = packet.position();
        int srcLim = packet.limit();

        byte firstByte = packet.get(srcPos);
        byte thirdByte = packet.get(srcPos + 2);

        // Does it look like a Version 2 client hello (V2ClientHello)?
        if (((firstByte & 0x80) != 0) && (thirdByte == 1)) {
            /*
             * If SSLv2Hello is not enabled, throw an exception.
             */
            if (helloVersion != ProtocolVersion.SSL20Hello) {
                throw new SSLHandshakeException("SSLv2Hello is not enabled");
            }

            byte majorVersion = packet.get(srcPos + 3);
            byte minorVersion = packet.get(srcPos + 4);

            if ((majorVersion == ProtocolVersion.SSL20Hello.major) &&
                (minorVersion == ProtocolVersion.SSL20Hello.minor)) {

                /*
                 * Looks like a V2 client hello, but not one saying
                 * "let's talk SSLv3".  So we need to send an SSLv2
                 * error message, one that's treated as fatal by
                 * clients (Otherwise we'll hang.)
                 */
                if (debug != null && Debug.isOn("record")) {
                     System.out.println(Thread.currentThread().getName() +
                            "Requested to negotiate unsupported SSLv2!");
                }

                // hack code, the exception is caught in SSLEngineImpl
                // so that SSLv2 error message can be delivered properly.
                throw new UnsupportedOperationException(        // SSLv2Hello
                        "Unsupported SSL v2.0 ClientHello");
            }

            /*
             * If we can map this into a V3 ClientHello, read and
             * hash the rest of the V2 handshake, turn it into a
             * V3 ClientHello message, and pass it up.
             */
            packet.position(srcPos + 2);        // exclude the header

            if (handshakeHash == null) {
                // used for cache only
                handshakeHash = new HandshakeHash(false);
            }
            handshakeHash.update(packet);
            packet.position(srcPos);

            ByteBuffer converted = convertToClientHello(packet);

            if (debug != null && Debug.isOn("packet")) {
                 Debug.printHex(
                        "[Converted] ClientHello", converted);
            }

            return new Plaintext(ct_handshake,
                majorVersion, minorVersion, -1, -1L, converted);
        } else {
            if (((firstByte & 0x80) != 0) && (thirdByte == 4)) {
                throw new SSLException("SSL V2.0 servers are not supported.");
            }

            throw new SSLException("Unsupported or unrecognized SSL message");
        }
    }

}
