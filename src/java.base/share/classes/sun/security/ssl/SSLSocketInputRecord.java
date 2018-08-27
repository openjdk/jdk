/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import javax.crypto.BadPaddingException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;

import sun.security.ssl.SSLCipher.SSLReadCipher;

/**
 * {@code InputRecord} implementation for {@code SSLSocket}.
 *
 * @author David Brownell
 */
final class SSLSocketInputRecord extends InputRecord implements SSLRecord {
    private InputStream is = null;
    private OutputStream os = null;
    private final byte[] temporary = new byte[1024];

    private boolean formatVerified = false;     // SSLv2 ruled out?

    // Cache for incomplete handshake messages.
    private ByteBuffer handshakeBuffer = null;

    private boolean hasHeader = false;          // Had read the record header

    SSLSocketInputRecord(HandshakeHash handshakeHash) {
        super(handshakeHash, SSLReadCipher.nullTlsReadCipher());
    }

    @Override
    int bytesInCompletePacket() throws IOException {
        if (!hasHeader) {
            // read exactly one record
            try {
                int really = read(is, temporary, 0, headerSize);
                if (really < 0) {
                    // EOF: peer shut down incorrectly
                    return -1;
                }
            } catch (EOFException eofe) {
                // The caller will handle EOF.
                return -1;
            }
            hasHeader = true;
        }

        byte byteZero = temporary[0];
        int len = 0;

        /*
         * If we have already verified previous packets, we can
         * ignore the verifications steps, and jump right to the
         * determination.  Otherwise, try one last heuristic to
         * see if it's SSL/TLS.
         */
        if (formatVerified ||
                (byteZero == ContentType.HANDSHAKE.id) ||
                (byteZero == ContentType.ALERT.id)) {
            /*
             * Last sanity check that it's not a wild record
             */
            if (!ProtocolVersion.isNegotiable(
                    temporary[1], temporary[2], false, false)) {
                throw new SSLException("Unrecognized record version " +
                        ProtocolVersion.nameOf(temporary[1], temporary[2]) +
                        " , plaintext connection?");
            }

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
                if (!ProtocolVersion.isNegotiable(
                        temporary[3], temporary[4], false, false)) {
                    throw new SSLException("Unrecognized record version " +
                            ProtocolVersion.nameOf(temporary[3], temporary[4]) +
                            " , plaintext connection?");
                }

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

    // Note that the input arguments are not used actually.
    @Override
    Plaintext[] decode(ByteBuffer[] srcs, int srcsOffset,
            int srcsLength) throws IOException, BadPaddingException {

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
            if ((temporary[0] != ContentType.HANDSHAKE.id) &&
                (temporary[0] != ContentType.ALERT.id)) {
                hasHeader = false;
                return handleUnknownRecord(temporary);
            }
        }

        // The record header should has consumed.
        hasHeader = false;
        return decodeInputRecord(temporary);
    }

    @Override
    void setReceiverStream(InputStream inputStream) {
        this.is = inputStream;
    }

    @Override
    void setDeliverStream(OutputStream outputStream) {
        this.os = outputStream;
    }

    // Note that destination may be null
    private Plaintext[] decodeInputRecord(
            byte[] header) throws IOException, BadPaddingException {
        byte contentType = header[0];                   // pos: 0
        byte majorVersion = header[1];                  // pos: 1
        byte minorVersion = header[2];                  // pos: 2
        int contentLen = ((header[3] & 0xFF) << 8) +
                           (header[4] & 0xFF);          // pos: 3, 4

        if (SSLLogger.isOn && SSLLogger.isOn("record")) {
            SSLLogger.fine(
                    "READ: " +
                    ProtocolVersion.nameOf(majorVersion, minorVersion) +
                    " " + ContentType.nameOf(contentType) + ", length = " +
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
        // Read a complete record.
        //
        ByteBuffer destination = ByteBuffer.allocate(headerSize + contentLen);
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

        if (SSLLogger.isOn && SSLLogger.isOn("record")) {
            SSLLogger.fine(
                    "READ: " +
                    ProtocolVersion.nameOf(majorVersion, minorVersion) +
                    " " + ContentType.nameOf(contentType) + ", length = " +
                    destination.remaining());
        }

        //
        // Decrypt the fragment
        //
        ByteBuffer fragment;
        try {
            Plaintext plaintext =
                    readCipher.decrypt(contentType, destination, null);
            fragment = plaintext.fragment;
            contentType = plaintext.contentType;
        } catch (BadPaddingException bpe) {
            throw bpe;
        } catch (GeneralSecurityException gse) {
            throw (SSLProtocolException)(new SSLProtocolException(
                    "Unexpected exception")).initCause(gse);
        }

        if (contentType != ContentType.HANDSHAKE.id &&
                handshakeBuffer != null && handshakeBuffer.hasRemaining()) {
            throw new SSLProtocolException(
                    "Expecting a handshake fragment, but received " +
                    ContentType.nameOf(contentType));
        }

        //
        // parse handshake messages
        //
        if (contentType == ContentType.HANDSHAKE.id) {
            ByteBuffer handshakeFrag = fragment;
            if ((handshakeBuffer != null) &&
                    (handshakeBuffer.remaining() != 0)) {
                ByteBuffer bb = ByteBuffer.wrap(new byte[
                        handshakeBuffer.remaining() + fragment.remaining()]);
                bb.put(handshakeBuffer);
                bb.put(fragment);
                handshakeFrag = bb.rewind();
                handshakeBuffer = null;
            }

            ArrayList<Plaintext> plaintexts = new ArrayList<>(5);
            while (handshakeFrag.hasRemaining()) {
                int remaining = handshakeFrag.remaining();
                if (remaining < handshakeHeaderSize) {
                    handshakeBuffer = ByteBuffer.wrap(new byte[remaining]);
                    handshakeBuffer.put(handshakeFrag);
                    handshakeBuffer.rewind();
                    break;
                }

                handshakeFrag.mark();
                // skip the first byte: handshake type
                byte handshakeType = handshakeFrag.get();
                int handshakeBodyLen = Record.getInt24(handshakeFrag);
                handshakeFrag.reset();
                int handshakeMessageLen =
                        handshakeHeaderSize + handshakeBodyLen;
                if (remaining < handshakeMessageLen) {
                    handshakeBuffer = ByteBuffer.wrap(new byte[remaining]);
                    handshakeBuffer.put(handshakeFrag);
                    handshakeBuffer.rewind();
                    break;
                } if (remaining == handshakeMessageLen) {
                    if (handshakeHash.isHashable(handshakeType)) {
                        handshakeHash.receive(handshakeFrag);
                    }

                    plaintexts.add(
                        new Plaintext(contentType,
                            majorVersion, minorVersion, -1, -1L, handshakeFrag)
                    );
                    break;
                } else {
                    int fragPos = handshakeFrag.position();
                    int fragLim = handshakeFrag.limit();
                    int nextPos = fragPos + handshakeMessageLen;
                    handshakeFrag.limit(nextPos);

                    if (handshakeHash.isHashable(handshakeType)) {
                        handshakeHash.receive(handshakeFrag);
                    }

                    plaintexts.add(
                        new Plaintext(contentType, majorVersion, minorVersion,
                            -1, -1L, handshakeFrag.slice())
                    );

                    handshakeFrag.position(nextPos);
                    handshakeFrag.limit(fragLim);
                }
            }

            return plaintexts.toArray(new Plaintext[0]);
        }

        return new Plaintext[] {
                new Plaintext(contentType,
                    majorVersion, minorVersion, -1, -1L, fragment)
            };
    }

    private Plaintext[] handleUnknownRecord(
            byte[] header) throws IOException, BadPaddingException {
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
                os.write(SSLRecord.v2NoCipher);      // SSLv2Hello

                if (SSLLogger.isOn) {
                    if (SSLLogger.isOn("record")) {
                         SSLLogger.fine(
                                "Requested to negotiate unsupported SSLv2!");
                    }

                    if (SSLLogger.isOn("packet")) {
                        SSLLogger.fine("Raw write", SSLRecord.v2NoCipher);
                    }
                }

                throw new SSLException("Unsupported SSL v2.0 ClientHello");
            }

            int msgLen = ((header[0] & 0x7F) << 8) | (header[1] & 0xFF);

            ByteBuffer destination = ByteBuffer.allocate(headerSize + msgLen);
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
            handshakeHash.receive(destination);
            destination.position(0);

            ByteBuffer converted = convertToClientHello(destination);

            if (SSLLogger.isOn && SSLLogger.isOn("packet")) {
                SSLLogger.fine(
                        "[Converted] ClientHello", converted);
            }

            return new Plaintext[] {
                    new Plaintext(ContentType.HANDSHAKE.id,
                    majorVersion, minorVersion, -1, -1L, converted)
                };
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
                if (SSLLogger.isOn && SSLLogger.isOn("packet")) {
                    SSLLogger.fine("Raw read: EOF");
                }
                return -1;
            }

            if (SSLLogger.isOn && SSLLogger.isOn("packet")) {
                ByteBuffer bb = ByteBuffer.wrap(buffer, offset + n, readLen);
                SSLLogger.fine("Raw read", bb);
            }

            n += readLen;
        }

        return n;
    }
}
