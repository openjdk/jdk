/*
 * Copyright (c) 1996, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;

import javax.net.ssl.SSLException;
import sun.security.util.HexDumpEncoder;


/**
 * {@code OutputRecord} takes care of the management of SSL/TLS/DTLS output
 * records, including buffering, encryption, handshake messages marshal, etc.
 *
 * @author David Brownell
 */
abstract class OutputRecord extends ByteArrayOutputStream
            implements Record, Closeable {

    /* Class and subclass dynamic debugging support */
    static final Debug          debug = Debug.getInstance("ssl");

    Authenticator               writeAuthenticator;
    CipherBox                   writeCipher;

    HandshakeHash               handshakeHash;
    boolean                     firstMessage;

    // current protocol version, sent as record version
    ProtocolVersion             protocolVersion;

    // version for the ClientHello message. Only relevant if this is a
    // client handshake record. If set to ProtocolVersion.SSL20Hello,
    // the V3 client hello is converted to V2 format.
    ProtocolVersion             helloVersion;

    // Is it the first application record to write?
    boolean                     isFirstAppOutputRecord = true;

    // packet size
    int                         packetSize;

    // fragment size
    int                         fragmentSize;

    // closed or not?
    boolean                     isClosed;

    /*
     * Mappings from V3 cipher suite encodings to their pure V2 equivalents.
     * This is taken from the SSL V3 specification, Appendix E.
     */
    private static int[] V3toV2CipherMap1 =
        {-1, -1, -1, 0x02, 0x01, -1, 0x04, 0x05, -1, 0x06, 0x07};
    private static int[] V3toV2CipherMap3 =
        {-1, -1, -1, 0x80, 0x80, -1, 0x80, 0x80, -1, 0x40, 0xC0};

    OutputRecord() {
        this.writeCipher = CipherBox.NULL;
        this.firstMessage = true;
        this.fragmentSize = Record.maxDataSize;

        // Please set packetSize and protocolVersion in the implementation.
    }

    void setVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    /*
     * Updates helloVersion of this record.
     */
    synchronized void setHelloVersion(ProtocolVersion helloVersion) {
        this.helloVersion = helloVersion;
    }

    /*
     * For handshaking, we need to be able to hash every byte above the
     * record marking layer.  This is where we're guaranteed to see those
     * bytes, so this is where we can hash them.
     */
    void setHandshakeHash(HandshakeHash handshakeHash) {
        this.handshakeHash = handshakeHash;
    }

    /*
     * Return true iff the record is empty -- to avoid doing the work
     * of sending empty records over the network.
     */
    boolean isEmpty() {
        return false;
    }

    boolean seqNumIsHuge() {
        return (writeAuthenticator != null) &&
                        writeAuthenticator.seqNumIsHuge();
    }

    // SSLEngine and SSLSocket
    abstract void encodeAlert(byte level, byte description) throws IOException;

    // SSLEngine and SSLSocket
    abstract void encodeHandshake(byte[] buffer,
            int offset, int length) throws IOException;

    // SSLEngine and SSLSocket
    abstract void encodeChangeCipherSpec() throws IOException;

    // apply to SSLEngine only
    Ciphertext encode(ByteBuffer[] sources, int offset, int length,
            ByteBuffer destination) throws IOException {
        throw new UnsupportedOperationException();
    }

    // apply to SSLEngine only
    void encodeV2NoCipher() throws IOException {
        throw new UnsupportedOperationException();
    }

    // apply to SSLSocket only
    void deliver(byte[] source, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    // apply to SSLSocket only
    void setDeliverStream(OutputStream outputStream) {
        throw new UnsupportedOperationException();
    }

    // apply to SSLEngine only
    Ciphertext acquireCiphertext(ByteBuffer destination) throws IOException {
        throw new UnsupportedOperationException();
    }

    void changeWriteCiphers(Authenticator writeAuthenticator,
            CipherBox writeCipher) throws IOException {

        encodeChangeCipherSpec();

        /*
         * Dispose of any intermediate state in the underlying cipher.
         * For PKCS11 ciphers, this will release any attached sessions,
         * and thus make finalization faster.
         *
         * Since MAC's doFinal() is called for every SSL/TLS packet, it's
         * not necessary to do the same with MAC's.
         */
        writeCipher.dispose();

        this.writeAuthenticator = writeAuthenticator;
        this.writeCipher = writeCipher;
        this.isFirstAppOutputRecord = true;
    }

    void changePacketSize(int packetSize) {
        this.packetSize = packetSize;
    }

    void changeFragmentSize(int fragmentSize) {
        this.fragmentSize = fragmentSize;
    }

    int getMaxPacketSize() {
        return packetSize;
    }

    // apply to DTLS SSLEngine
    void initHandshaker() {
        // blank
    }

    // apply to DTLS SSLEngine
    void launchRetransmission() {
        // blank
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
            writeCipher.dispose();
        }
    }

    //
    // shared helpers
    //

    // Encrypt a fragment and wrap up a record.
    //
    // To be consistent with the spec of SSLEngine.wrap() methods, the
    // destination ByteBuffer's position is updated to reflect the amount
    // of data produced.  The limit remains the same.
    static long encrypt(Authenticator authenticator,
            CipherBox encCipher, byte contentType, ByteBuffer destination,
            int headerOffset, int dstLim, int headerSize,
            ProtocolVersion protocolVersion, boolean isDTLS) {

        byte[] sequenceNumber = null;
        int dstContent = destination.position();

        // Acquire the current sequence number before using.
        if (isDTLS) {
            sequenceNumber = authenticator.sequenceNumber();
        }

        // The sequence number may be shared for different purpose.
        boolean sharedSequenceNumber = false;

        // "flip" but skip over header again, add MAC & encrypt
        if (authenticator instanceof MAC) {
            MAC signer = (MAC)authenticator;
            if (signer.MAClen() != 0) {
                byte[] hash = signer.compute(contentType, destination, false);

                /*
                 * position was advanced to limit in MAC compute above.
                 *
                 * Mark next area as writable (above layers should have
                 * established that we have plenty of room), then write
                 * out the hash.
                 */
                destination.limit(destination.limit() + hash.length);
                destination.put(hash);

                // reset the position and limit
                destination.limit(destination.position());
                destination.position(dstContent);

                // The signer has used and increased the sequence number.
                if (isDTLS) {
                    sharedSequenceNumber = true;
                }
            }
        }

        if (!encCipher.isNullCipher()) {
            if (protocolVersion.useTLS11PlusSpec() &&
                    (encCipher.isCBCMode() || encCipher.isAEADMode())) {
                byte[] nonce = encCipher.createExplicitNonce(
                        authenticator, contentType, destination.remaining());
                destination.position(headerOffset + headerSize);
                destination.put(nonce);
            }
            if (!encCipher.isAEADMode()) {
                // The explicit IV in TLS 1.1 and later can be encrypted.
                destination.position(headerOffset + headerSize);
            }   // Otherwise, DON'T encrypt the nonce_explicit for AEAD mode

            // Encrypt may pad, so again the limit may be changed.
            encCipher.encrypt(destination, dstLim);

            // The cipher has used and increased the sequence number.
            if (isDTLS && encCipher.isAEADMode()) {
                sharedSequenceNumber = true;
            }
        } else {
            destination.position(destination.limit());
        }

        // Finish out the record header.
        int fragLen = destination.limit() - headerOffset - headerSize;

        destination.put(headerOffset, contentType);         // content type
        destination.put(headerOffset + 1, protocolVersion.major);
        destination.put(headerOffset + 2, protocolVersion.minor);
        if (!isDTLS) {
            // fragment length
            destination.put(headerOffset + 3, (byte)(fragLen >> 8));
            destination.put(headerOffset + 4, (byte)fragLen);
        } else {
            // epoch and sequence_number
            destination.put(headerOffset + 3, sequenceNumber[0]);
            destination.put(headerOffset + 4, sequenceNumber[1]);
            destination.put(headerOffset + 5, sequenceNumber[2]);
            destination.put(headerOffset + 6, sequenceNumber[3]);
            destination.put(headerOffset + 7, sequenceNumber[4]);
            destination.put(headerOffset + 8, sequenceNumber[5]);
            destination.put(headerOffset + 9, sequenceNumber[6]);
            destination.put(headerOffset + 10, sequenceNumber[7]);

            // fragment length
            destination.put(headerOffset + 11, (byte)(fragLen >> 8));
            destination.put(headerOffset + 12, (byte)fragLen);

            // Increase the sequence number for next use if it is not shared.
            if (!sharedSequenceNumber) {
                authenticator.increaseSequenceNumber();
            }
        }

        // Update destination position to reflect the amount of data produced.
        destination.position(destination.limit());

        return Authenticator.toLong(sequenceNumber);
    }

    // Encrypt a fragment and wrap up a record.
    //
    // Uses the internal expandable buf variable and the current
    // protocolVersion variable.
    void encrypt(Authenticator authenticator,
            CipherBox encCipher, byte contentType, int headerSize) {

        int position = headerSize + writeCipher.getExplicitNonceSize();

        // "flip" but skip over header again, add MAC & encrypt
        int macLen = 0;
        if (authenticator instanceof MAC) {
            MAC signer = (MAC)authenticator;
            macLen = signer.MAClen();
            if (macLen != 0) {
                byte[] hash = signer.compute(contentType,
                        buf, position, (count - position), false);

                write(hash, 0, hash.length);
            }
        }

        if (!encCipher.isNullCipher()) {
            // Requires explicit IV/nonce for CBC/AEAD cipher suites for
            // TLS 1.1 or later.
            if (protocolVersion.useTLS11PlusSpec() &&
                    (encCipher.isCBCMode() || encCipher.isAEADMode())) {

                byte[] nonce = encCipher.createExplicitNonce(
                        authenticator, contentType, (count - position));
                int noncePos = position - nonce.length;
                System.arraycopy(nonce, 0, buf, noncePos, nonce.length);
            }

            if (!encCipher.isAEADMode()) {
                // The explicit IV in TLS 1.1 and later can be encrypted.
                position = headerSize;
            }   // Otherwise, DON'T encrypt the nonce_explicit for AEAD mode

            // increase buf capacity if necessary
            int fragSize = count - position;
            int packetSize =
                    encCipher.calculatePacketSize(fragSize, macLen, headerSize);
            if (packetSize > (buf.length - position)) {
                byte[] newBuf = new byte[position + packetSize];
                System.arraycopy(buf, 0, newBuf, 0, count);
                buf = newBuf;
            }

            // Encrypt may pad, so again the count may be changed.
            count = position +
                    encCipher.encrypt(buf, position, (count - position));
        }

        // Fill out the header, write it and the message.
        int fragLen = count - headerSize;
        buf[0] = contentType;
        buf[1] = protocolVersion.major;
        buf[2] = protocolVersion.minor;
        buf[3] = (byte)((fragLen >> 8) & 0xFF);
        buf[4] = (byte)(fragLen & 0xFF);
    }

    static ByteBuffer encodeV2ClientHello(
            byte[] fragment, int offset, int length) throws IOException {

        int v3SessIdLenOffset = offset + 34;      //  2: client_version
                                                  // 32: random

        int v3SessIdLen = fragment[v3SessIdLenOffset];
        int v3CSLenOffset = v3SessIdLenOffset + 1 + v3SessIdLen;
        int v3CSLen = ((fragment[v3CSLenOffset] & 0xff) << 8) +
                       (fragment[v3CSLenOffset + 1] & 0xff);
        int cipherSpecs = v3CSLen / 2;        // 2: cipher spec size

        // Estimate the max V2ClientHello message length
        //
        // 11: header size
        // (cipherSpecs * 6): cipher_specs
        //    6: one cipher suite may need 6 bytes, see V3toV2CipherSuite.
        // 3: placeholder for the TLS_EMPTY_RENEGOTIATION_INFO_SCSV
        //    signaling cipher suite
        // 32: challenge size
        int v2MaxMsgLen = 11 + (cipherSpecs * 6) + 3 + 32;

        // Create a ByteBuffer backed by an accessible byte array.
        byte[] dstBytes = new byte[v2MaxMsgLen];
        ByteBuffer dstBuf = ByteBuffer.wrap(dstBytes);

        /*
         * Copy over the cipher specs. We don't care about actually
         * translating them for use with an actual V2 server since
         * we only talk V3.  Therefore, just copy over the V3 cipher
         * spec values with a leading 0.
         */
        int v3CSOffset = v3CSLenOffset + 2;   // skip length field
        int v2CSLen = 0;

        dstBuf.position(11);
        boolean containsRenegoInfoSCSV = false;
        for (int i = 0; i < cipherSpecs; i++) {
            byte byte1, byte2;

            byte1 = fragment[v3CSOffset++];
            byte2 = fragment[v3CSOffset++];
            v2CSLen += V3toV2CipherSuite(dstBuf, byte1, byte2);
            if (!containsRenegoInfoSCSV &&
                    byte1 == (byte)0x00 && byte2 == (byte)0xFF) {
                containsRenegoInfoSCSV = true;
            }
        }

        if (!containsRenegoInfoSCSV) {
            v2CSLen += V3toV2CipherSuite(dstBuf, (byte)0x00, (byte)0xFF);
        }

        /*
         * Copy in the nonce.
         */
        dstBuf.put(fragment, (offset + 2), 32);

        /*
         * Build the first part of the V3 record header from the V2 one
         * that's now buffered up.  (Lengths are fixed up later).
         */
        int msgLen = dstBuf.position() - 2;   // Exclude the legth field itself
        dstBuf.position(0);
        dstBuf.put((byte)(0x80 | ((msgLen >>> 8) & 0xFF)));  // pos: 0
        dstBuf.put((byte)(msgLen & 0xFF));                   // pos: 1
        dstBuf.put(HandshakeMessage.ht_client_hello);        // pos: 2
        dstBuf.put(fragment[offset]);         // major version, pos: 3
        dstBuf.put(fragment[offset + 1]);     // minor version, pos: 4
        dstBuf.put((byte)(v2CSLen >>> 8));                   // pos: 5
        dstBuf.put((byte)(v2CSLen & 0xFF));                  // pos: 6
        dstBuf.put((byte)0x00);           // session_id_length, pos: 7
        dstBuf.put((byte)0x00);                              // pos: 8
        dstBuf.put((byte)0x00);           // challenge_length,  pos: 9
        dstBuf.put((byte)32);                                // pos: 10

        dstBuf.position(0);
        dstBuf.limit(msgLen + 2);

        return dstBuf;
    }

    private static int V3toV2CipherSuite(ByteBuffer dstBuf,
            byte byte1, byte byte2) {
        dstBuf.put((byte)0);
        dstBuf.put(byte1);
        dstBuf.put(byte2);

        if (((byte2 & 0xff) > 0xA) || (V3toV2CipherMap1[byte2] == -1)) {
            return 3;
        }

        dstBuf.put((byte)V3toV2CipherMap1[byte2]);
        dstBuf.put((byte)0);
        dstBuf.put((byte)V3toV2CipherMap3[byte2]);

        return 6;
    }
}
