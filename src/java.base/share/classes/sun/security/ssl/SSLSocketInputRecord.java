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

import sun.security.util.HexDumpEncoder;


/**
 * {@code InputRecord} implementation for {@code SSLSocket}.
 *
 * @author David Brownell
 */
final class SSLSocketInputRecord extends InputRecord implements SSLRecord {
    private OutputStream deliverStream = null;
    private byte[] temporary = new byte[1024];

    // used by handshake hash computation for handshake fragment
    private byte prevType = -1;
    private int hsMsgOff = 0;
    private int hsMsgLen = 0;

    private boolean formatVerified = false;     // SSLv2 ruled out?

    private boolean hasHeader = false;          // Had read the record header

    SSLSocketInputRecord() {
        this.readAuthenticator = MAC.TLS_NULL;
    }

    @Override
    int bytesInCompletePacket(InputStream is) throws IOException {

        if (!hasHeader) {
            // read exactly one record
            int really = read(is, temporary, 0, headerSize);
            if (really < 0) {
                throw new EOFException("SSL peer shut down incorrectly");
            }
            hasHeader = true;
        }

        byte byteZero = temporary[0];
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
            ProtocolVersion recordVersion =
                    ProtocolVersion.valueOf(temporary[1], temporary[2]);

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
            len = ((temporary[3] & 0xFF) << 8) +
                   (temporary[4] & 0xFF) + headerSize;
        } else {
            /*
             * Must be SSLv2 or something unknown.
             * Check if it's short (2 bytes) or
             * long (3) header.
             *
             * Internals can warn about unsupported SSLv2
             */
            boolean isShort = ((byteZero & 0x80) != 0);

            if (isShort && ((temporary[2] == 1) || (temporary[2] == 4))) {
                ProtocolVersion recordVersion =
                        ProtocolVersion.valueOf(temporary[3], temporary[4]);

                // check the record version
                checkRecordVersion(recordVersion, true);

                /*
                 * Client or Server Hello
                 */
                //
                // Short header is using here.  We reverse the code here
                // in case it is used in the future.
                //
                // int mask = (isShort ? 0x7F : 0x3F);
                // len = ((byteZero & mask) << 8) +
                //        (temporary[1] & 0xFF) + (isShort ? 2 : 3);
                //
                len = ((byteZero & 0x7F) << 8) + (temporary[1] & 0xFF) + 2;
            } else {
                // Gobblygook!
                throw new SSLException(
                        "Unrecognized SSL message, plaintext connection?");
            }
        }

        return len;
    }

    // destination.position() is zero.
    @Override
    Plaintext decode(InputStream is, ByteBuffer destination)
            throws IOException, BadPaddingException {

        if (isClosed) {
            return null;
        }

        if (!hasHeader) {
            // read exactly one record
            int really = read(is, temporary, 0, headerSize);
            if (really < 0) {
                throw new EOFException("SSL peer shut down incorrectly");
            }
            hasHeader = true;
        }

        Plaintext plaintext = null;
        if (!formatVerified) {
            formatVerified = true;

            /*
             * The first record must either be a handshake record or an
             * alert message. If it's not, it is either invalid or an
             * SSLv2 message.
             */
            if ((temporary[0] != ct_handshake) &&
                (temporary[0] != ct_alert)) {

                plaintext = handleUnknownRecord(is, temporary, destination);
            }
        }

        if (plaintext == null) {
            plaintext = decodeInputRecord(is, temporary, destination);
        }

        // The record header should has comsumed.
        hasHeader = false;

        return plaintext;
    }

    @Override
    void setDeliverStream(OutputStream outputStream) {
        this.deliverStream = outputStream;
    }

    // Note that destination may be null
    private Plaintext decodeInputRecord(InputStream is, byte[] header,
            ByteBuffer destination) throws IOException, BadPaddingException {

        byte contentType = header[0];
        byte majorVersion = header[1];
        byte minorVersion = header[2];
        int contentLen = ((header[3] & 0xFF) << 8) + (header[4] & 0xFF);

        //
        // Check for upper bound.
        //
        // Note: May check packetSize limit in the future.
        if (contentLen < 0 || contentLen > maxLargeRecordSize - headerSize) {
            throw new SSLProtocolException(
                "Bad input record size, TLSCiphertext.length = " + contentLen);
        }

        //
        // Read a complete record.
        //
        if (destination == null) {
            destination = ByteBuffer.allocate(headerSize + contentLen);
        }  // Otherwise, the destination buffer should have enough room.

        int dstPos = destination.position();
        destination.put(temporary, 0, headerSize);
        while (contentLen > 0) {
            int howmuch = Math.min(temporary.length, contentLen);
            int really = read(is, temporary, 0, howmuch);
            if (really < 0) {
                throw new EOFException("SSL peer shut down incorrectly");
            }

            destination.put(temporary, 0, howmuch);
            contentLen -= howmuch;
        }
        destination.flip();
        destination.position(dstPos + headerSize);

        if (debug != null && Debug.isOn("record")) {
             System.out.println(Thread.currentThread().getName() +
                    ", READ: " +
                    ProtocolVersion.valueOf(majorVersion, minorVersion) +
                    " " + Record.contentName(contentType) + ", length = " +
                    destination.remaining());
        }

        //
        // Decrypt the fragment
        //
        ByteBuffer plaintext =
            decrypt(readAuthenticator, readCipher, contentType, destination);

        if ((contentType != ct_handshake) && (hsMsgOff != hsMsgLen)) {
            throw new SSLProtocolException(
                    "Expected to get a handshake fragment");
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
                    if (remains < (handshakeLen + 1)) { // 1: handshake type
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

    private Plaintext handleUnknownRecord(InputStream is, byte[] header,
            ByteBuffer destination) throws IOException, BadPaddingException {

        byte firstByte = header[0];
        byte thirdByte = header[2];

        // Does it look like a Version 2 client hello (V2ClientHello)?
        if (((firstByte & 0x80) != 0) && (thirdByte == 1)) {
            /*
             * If SSLv2Hello is not enabled, throw an exception.
             */
            if (helloVersion != ProtocolVersion.SSL20Hello) {
                throw new SSLHandshakeException("SSLv2Hello is not enabled");
            }

            byte majorVersion = header[3];
            byte minorVersion = header[4];

            if ((majorVersion == ProtocolVersion.SSL20Hello.major) &&
                (minorVersion == ProtocolVersion.SSL20Hello.minor)) {

                /*
                 * Looks like a V2 client hello, but not one saying
                 * "let's talk SSLv3".  So we need to send an SSLv2
                 * error message, one that's treated as fatal by
                 * clients (Otherwise we'll hang.)
                 */
                deliverStream.write(SSLRecord.v2NoCipher);      // SSLv2Hello

                if (debug != null) {
                    if (Debug.isOn("record")) {
                         System.out.println(Thread.currentThread().getName() +
                                "Requested to negotiate unsupported SSLv2!");
                    }

                    if (Debug.isOn("packet")) {
                        Debug.printHex(
                                "[Raw write]: length = " +
                                SSLRecord.v2NoCipher.length,
                                SSLRecord.v2NoCipher);
                    }
                }

                throw new SSLException("Unsupported SSL v2.0 ClientHello");
            }

            int msgLen = ((header[0] & 0x7F) << 8) | (header[1] & 0xFF);

            if (destination == null) {
                destination = ByteBuffer.allocate(headerSize + msgLen);
            }
            destination.put(temporary, 0, headerSize);
            msgLen -= 3;            // had read 3 bytes of content as header
            while (msgLen > 0) {
                int howmuch = Math.min(temporary.length, msgLen);
                int really = read(is, temporary, 0, howmuch);
                if (really < 0) {
                    throw new EOFException("SSL peer shut down incorrectly");
                }

                destination.put(temporary, 0, howmuch);
                msgLen -= howmuch;
            }
            destination.flip();

            /*
             * If we can map this into a V3 ClientHello, read and
             * hash the rest of the V2 handshake, turn it into a
             * V3 ClientHello message, and pass it up.
             */
            destination.position(2);     // exclude the header

            if (handshakeHash == null) {
                // used for cache only
                handshakeHash = new HandshakeHash(false);
            }
            handshakeHash.update(destination);
            destination.position(0);

            ByteBuffer converted = convertToClientHello(destination);

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

    // Read the exact bytes of data, otherwise, return -1.
    private static int read(InputStream is,
            byte[] buffer, int offset, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int readLen = is.read(buffer, offset + n, len - n);
            if (readLen < 0) {
                return -1;
            }

            if (debug != null && Debug.isOn("packet")) {
                 Debug.printHex(
                        "[Raw read]: length = " + readLen,
                        buffer, offset + n, readLen);
            }

            n += readLen;
        }

        return n;
    }
}
