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
import java.util.*;

import javax.crypto.BadPaddingException;

import javax.net.ssl.*;

import sun.security.util.HexDumpEncoder;


/**
 * {@code InputRecord} takes care of the management of SSL/TLS/DTLS input
 * records, including buffering, decryption, handshake messages marshal, etc.
 *
 * @author David Brownell
 */
class InputRecord implements Record, Closeable {

    /* Class and subclass dynamic debugging support */
    static final Debug debug = Debug.getInstance("ssl");

    Authenticator       readAuthenticator;
    CipherBox           readCipher;

    HandshakeHash       handshakeHash;
    boolean             isClosed;

    // The ClientHello version to accept. If set to ProtocolVersion.SSL20Hello
    // and the first message we read is a ClientHello in V2 format, we convert
    // it to V3. Otherwise we throw an exception when encountering a V2 hello.
    ProtocolVersion     helloVersion;

    // fragment size
    int                 fragmentSize;

    InputRecord() {
        this.readCipher = CipherBox.NULL;
        this.readAuthenticator = null;      // Please override this assignment.
        this.helloVersion = ProtocolVersion.DEFAULT_HELLO;
        this.fragmentSize = Record.maxDataSize;
    }

    void setHelloVersion(ProtocolVersion helloVersion) {
        this.helloVersion = helloVersion;
    }

    ProtocolVersion getHelloVersion() {
        return helloVersion;
    }

    /*
     * Set instance for the computation of handshake hashes.
     *
     * For handshaking, we need to be able to hash every byte above the
     * record marking layer.  This is where we're guaranteed to see those
     * bytes, so this is where we can hash them ... especially in the
     * case of hashing the initial V2 message!
     */
    void setHandshakeHash(HandshakeHash handshakeHash) {
        if (handshakeHash != null) {
            byte[] reserved = null;
            if (this.handshakeHash != null) {
                reserved = this.handshakeHash.getAllHandshakeMessages();
            }
            if ((reserved != null) && (reserved.length != 0)) {
                handshakeHash.update(reserved, 0, reserved.length);

               if (debug != null && Debug.isOn("data")) {
                    Debug.printHex(
                        "[reserved] handshake hash: len = " + reserved.length,
                        reserved);
               }
            }
        }

        this.handshakeHash = handshakeHash;
    }

    boolean seqNumIsHuge() {
        return (readAuthenticator != null) &&
                        readAuthenticator.seqNumIsHuge();
    }

    boolean isEmpty() {
        return false;
    }

    // apply to DTLS SSLEngine
    void expectingFinishFlight() {
        // blank
    }

    /**
     * Prevent any more data from being read into this record,
     * and flag the record as holding no data.
     */
    @Override
    public synchronized void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
            readCipher.dispose();
        }
    }

    // apply to SSLSocket and SSLEngine
    void changeReadCiphers(
            Authenticator readAuthenticator, CipherBox readCipher) {

        /*
         * Dispose of any intermediate state in the underlying cipher.
         * For PKCS11 ciphers, this will release any attached sessions,
         * and thus make finalization faster.
         *
         * Since MAC's doFinal() is called for every SSL/TLS packet, it's
         * not necessary to do the same with MAC's.
         */
        readCipher.dispose();

        this.readAuthenticator = readAuthenticator;
        this.readCipher = readCipher;
    }

    // change fragment size
    void changeFragmentSize(int fragmentSize) {
        this.fragmentSize = fragmentSize;
    }

    /*
     * Check if there is enough inbound data in the ByteBuffer to make
     * a inbound packet.
     *
     * @return -1 if there are not enough bytes to tell (small header),
     */
    // apply to SSLEngine only
    int bytesInCompletePacket(ByteBuffer buf) throws SSLException {
        throw new UnsupportedOperationException();
    }

    // apply to SSLSocket only
    int bytesInCompletePacket(InputStream is) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Return true if the specified record protocol version is out of the
     * range of the possible supported versions.
     */
    void checkRecordVersion(ProtocolVersion version,
            boolean allowSSL20Hello) throws SSLException {
        // blank
    }

    // apply to DTLS SSLEngine only
    Plaintext acquirePlaintext()
            throws IOException, BadPaddingException {
        throw new UnsupportedOperationException();
    }

    // read, decrypt and decompress the network record.
    //
    // apply to SSLEngine only
    Plaintext decode(ByteBuffer netData)
            throws IOException, BadPaddingException {
        throw new UnsupportedOperationException();
    }

    // apply to SSLSocket only
    Plaintext decode(InputStream is, ByteBuffer destination)
            throws IOException, BadPaddingException {
        throw new UnsupportedOperationException();
    }

    // apply to SSLSocket only
    void setDeliverStream(OutputStream outputStream) {
        throw new UnsupportedOperationException();
    }

    // calculate plaintext fragment size
    //
    // apply to SSLEngine only
    int estimateFragmentSize(int packetSize) {
        throw new UnsupportedOperationException();
    }

    //
    // shared helpers
    //

    // Not apply to DTLS
    static ByteBuffer convertToClientHello(ByteBuffer packet) {

        int srcPos = packet.position();
        int srcLim = packet.limit();

        byte firstByte = packet.get();
        byte secondByte = packet.get();
        int recordLen = (((firstByte & 0x7F) << 8) | (secondByte & 0xFF)) + 2;

        packet.position(srcPos + 3);        // the V2ClientHello record header

        byte majorVersion = packet.get();
        byte minorVersion = packet.get();

        int cipherSpecLen = ((packet.get() & 0xFF) << 8) +
                             (packet.get() & 0xFF);
        int sessionIdLen  = ((packet.get() & 0xFF) << 8) +
                             (packet.get() & 0xFF);
        int nonceLen      = ((packet.get() & 0xFF) << 8) +
                             (packet.get() & 0xFF);

        // Required space for the target SSLv3 ClientHello message.
        //  5: record header size
        //  4: handshake header size
        //  2: ClientHello.client_version
        // 32: ClientHello.random
        //  1: length byte of ClientHello.session_id
        //  2: empty ClientHello.compression_methods
        int requiredSize = 46 + sessionIdLen + ((cipherSpecLen * 2 ) / 3 );
        byte[] converted = new byte[requiredSize];

        /*
         * Build the first part of the V3 record header from the V2 one
         * that's now buffered up.  (Lengths are fixed up later).
         */
        // Note: need not to set the header actually.
        converted[0] = ct_handshake;
        converted[1] = majorVersion;
        converted[2] = minorVersion;
        // header [3..4] for handshake message length
        // required size is 5;

        /*
         * Store the generic V3 handshake header:  4 bytes
         */
        converted[5] = 1;    // HandshakeMessage.ht_client_hello
        // buf [6..8] for length of ClientHello (int24)
        // required size += 4;

        /*
         * ClientHello header starts with SSL version
         */
        converted[9] = majorVersion;
        converted[10] = minorVersion;
        // required size += 2;
        int pointer = 11;

        /*
         * Copy Random value/nonce ... if less than the 32 bytes of
         * a V3 "Random", right justify and zero pad to the left.  Else
         * just take the last 32 bytes.
         */
        int offset = srcPos + 11 + cipherSpecLen + sessionIdLen;

        if (nonceLen < 32) {
            for (int i = 0; i < (32 - nonceLen); i++) {
                converted[pointer++] = 0;
            }
            packet.position(offset);
            packet.get(converted, pointer, nonceLen);

            pointer += nonceLen;
        } else {
            packet.position(offset + nonceLen - 32);
            packet.get(converted, pointer, 32);

            pointer += 32;
        }

        /*
         * Copy session ID (only one byte length!)
         */
        offset -= sessionIdLen;
        converted[pointer++] = (byte)(sessionIdLen & 0xFF);
        packet.position(offset);
        packet.get(converted, pointer, sessionIdLen);

        /*
         * Copy and translate cipher suites ... V2 specs with first byte zero
         * are really V3 specs (in the last 2 bytes), just copy those and drop
         * the other ones.  Preference order remains unchanged.
         *
         * Example:  Netscape Navigator 3.0 (exportable) says:
         *
         * 0/3,     SSL_RSA_EXPORT_WITH_RC4_40_MD5
         * 0/6,     SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5
         *
         * Microsoft Internet Explorer 3.0 (exportable) supports only
         *
         * 0/3,     SSL_RSA_EXPORT_WITH_RC4_40_MD5
         */
        int j;

        offset -= cipherSpecLen;
        packet.position(offset);

        j = pointer + 2;
        for (int i = 0; i < cipherSpecLen; i += 3) {
            if (packet.get() != 0) {
                // Ignore version 2.0 specifix cipher suite.  Clients
                // should also include the version 3.0 equivalent in
                // the V2ClientHello message.
                packet.get();           // ignore the 2nd byte
                packet.get();           // ignore the 3rd byte
                continue;
            }

            converted[j++] = packet.get();
            converted[j++] = packet.get();
        }

        j -= pointer + 2;
        converted[pointer++] = (byte)((j >>> 8) & 0xFF);
        converted[pointer++] = (byte)(j & 0xFF);
        pointer += j;

        /*
         * Append compression methods (default/null only)
         */
        converted[pointer++] = 1;
        converted[pointer++] = 0;      // Session.compression_null

        /*
         * Fill in lengths of the messages we synthesized (nested:
         * V3 handshake message within V3 record).
         */
        // Note: need not to set the header actually.
        int fragLen = pointer - 5;                      // TLSPlaintext.length
        converted[3] = (byte)((fragLen >>> 8) & 0xFF);
        converted[4] = (byte)(fragLen & 0xFF);

        /*
         * Handshake.length, length of ClientHello message
         */
        fragLen = pointer - 9;                          // Handshake.length
        converted[6] = (byte)((fragLen >>> 16) & 0xFF);
        converted[7] = (byte)((fragLen >>> 8) & 0xFF);
        converted[8] = (byte)(fragLen & 0xFF);

        // consume the full record
        packet.position(srcPos + recordLen);

        // Need no header bytes.
        return ByteBuffer.wrap(converted, 5, pointer - 5);  // 5: header size
    }

    static ByteBuffer decrypt(Authenticator authenticator, CipherBox box,
            byte contentType, ByteBuffer bb) throws BadPaddingException {

        return decrypt(authenticator, box, contentType, bb, null);
    }

    static ByteBuffer decrypt(Authenticator authenticator,
            CipherBox box, byte contentType, ByteBuffer bb,
            byte[] sequence) throws BadPaddingException {

        BadPaddingException reservedBPE = null;
        int tagLen =
            (authenticator instanceof MAC) ? ((MAC)authenticator).MAClen() : 0;
        int cipheredLength = bb.remaining();
        int srcPos = bb.position();
        if (!box.isNullCipher()) {
            try {
                // apply explicit nonce for AEAD/CBC cipher suites if needed
                int nonceSize = box.applyExplicitNonce(
                        authenticator, contentType, bb, sequence);

                // decrypt the content
                if (box.isAEADMode()) {
                    // DON'T decrypt the nonce_explicit for AEAD mode
                    bb.position(srcPos + nonceSize);
                }   // The explicit IV for CBC mode can be decrypted.

                // Note that the CipherBox.decrypt() does not change
                // the capacity of the buffer.
                box.decrypt(bb, tagLen);
                // We don't actually remove the nonce.
                bb.position(srcPos + nonceSize);
            } catch (BadPaddingException bpe) {
                // RFC 2246 states that decryption_failed should be used
                // for this purpose. However, that allows certain attacks,
                // so we just send bad record MAC. We also need to make
                // sure to always check the MAC to avoid a timing attack
                // for the same issue. See paper by Vaudenay et al and the
                // update in RFC 4346/5246.
                //
                // Failover to message authentication code checking.
                reservedBPE = bpe;
            }
        }

        // Requires message authentication code for null, stream and block
        // cipher suites.
        if ((authenticator instanceof MAC) && (tagLen != 0)) {
            MAC signer = (MAC)authenticator;
            int contentLen = bb.remaining() - tagLen;

            // Note that although it is not necessary, we run the same MAC
            // computation and comparison on the payload for both stream
            // cipher and CBC block cipher.
            if (contentLen < 0) {
                // negative data length, something is wrong
                if (reservedBPE == null) {
                    reservedBPE = new BadPaddingException("bad record");
                }

                // set offset of the dummy MAC
                contentLen = cipheredLength - tagLen;
                bb.limit(srcPos + cipheredLength);
            }

            // Run MAC computation and comparison on the payload.
            //
            // MAC data would be stripped off during the check.
            if (checkMacTags(contentType, bb, signer, sequence, false)) {
                if (reservedBPE == null) {
                    reservedBPE = new BadPaddingException("bad record MAC");
                }
            }

            // Run MAC computation and comparison on the remainder.
            //
            // It is only necessary for CBC block cipher.  It is used to get a
            // constant time of MAC computation and comparison on each record.
            if (box.isCBCMode()) {
                int remainingLen = calculateRemainingLen(
                                        signer, cipheredLength, contentLen);

                // NOTE: remainingLen may be bigger (less than 1 block of the
                // hash algorithm of the MAC) than the cipheredLength.
                //
                // Is it possible to use a static buffer, rather than allocate
                // it dynamically?
                remainingLen += signer.MAClen();
                ByteBuffer temporary = ByteBuffer.allocate(remainingLen);

                // Won't need to worry about the result on the remainder. And
                // then we won't need to worry about what's actual data to
                // check MAC tag on.  We start the check from the header of the
                // buffer so that we don't need to construct a new byte buffer.
                checkMacTags(contentType, temporary, signer, sequence, true);
            }
        }

        // Is it a failover?
        if (reservedBPE != null) {
            throw reservedBPE;
        }

        return bb.slice();
    }

    /*
     * Run MAC computation and comparison
     *
     */
    private static boolean checkMacTags(byte contentType, ByteBuffer bb,
            MAC signer, byte[] sequence, boolean isSimulated) {

        int tagLen = signer.MAClen();
        int position = bb.position();
        int lim = bb.limit();
        int macOffset = lim - tagLen;

        bb.limit(macOffset);
        byte[] hash = signer.compute(contentType, bb, sequence, isSimulated);
        if (hash == null || tagLen != hash.length) {
            // Something is wrong with MAC implementation.
            throw new RuntimeException("Internal MAC error");
        }

        bb.position(macOffset);
        bb.limit(lim);
        try {
            int[] results = compareMacTags(bb, hash);
            return (results[0] != 0);
        } finally {
            // reset to the data
            bb.position(position);
            bb.limit(macOffset);
        }
    }

    /*
     * A constant-time comparison of the MAC tags.
     *
     * Please DON'T change the content of the ByteBuffer parameter!
     */
    private static int[] compareMacTags(ByteBuffer bb, byte[] tag) {

        // An array of hits is used to prevent Hotspot optimization for
        // the purpose of a constant-time check.
        int[] results = {0, 0};     // {missed #, matched #}

        // The caller ensures there are enough bytes available in the buffer.
        // So we won't need to check the remaining of the buffer.
        for (int i = 0; i < tag.length; i++) {
            if (bb.get() != tag[i]) {
                results[0]++;       // mismatched bytes
            } else {
                results[1]++;       // matched bytes
            }
        }

        return results;
    }

    /*
     * Run MAC computation and comparison
     *
     * Please DON'T change the content of the byte buffer parameter!
     */
    private static boolean checkMacTags(byte contentType, byte[] buffer,
            int offset, int contentLen, MAC signer, boolean isSimulated) {

        int tagLen = signer.MAClen();
        byte[] hash = signer.compute(
                contentType, buffer, offset, contentLen, isSimulated);
        if (hash == null || tagLen != hash.length) {
            // Something is wrong with MAC implementation.
            throw new RuntimeException("Internal MAC error");
        }

        int[] results = compareMacTags(buffer, offset + contentLen, hash);
        return (results[0] != 0);
    }

    /*
     * A constant-time comparison of the MAC tags.
     *
     * Please DON'T change the content of the byte buffer parameter!
     */
    private static int[] compareMacTags(
            byte[] buffer, int offset, byte[] tag) {

        // An array of hits is used to prevent Hotspot optimization for
        // the purpose of a constant-time check.
        int[] results = {0, 0};    // {missed #, matched #}

        // The caller ensures there are enough bytes available in the buffer.
        // So we won't need to check the length of the buffer.
        for (int i = 0; i < tag.length; i++) {
            if (buffer[offset + i] != tag[i]) {
                results[0]++;       // mismatched bytes
            } else {
                results[1]++;       // matched bytes
            }
        }

        return results;
    }

    /*
     * Calculate the length of a dummy buffer to run MAC computation
     * and comparison on the remainder.
     *
     * The caller MUST ensure that the fullLen is not less than usedLen.
     */
    private static int calculateRemainingLen(
            MAC signer, int fullLen, int usedLen) {

        int blockLen = signer.hashBlockLen();
        int minimalPaddingLen = signer.minimalPaddingLen();

        // (blockLen - minimalPaddingLen) is the maximum message size of
        // the last block of hash function operation. See FIPS 180-4, or
        // MD5 specification.
        fullLen += 13 - (blockLen - minimalPaddingLen);
        usedLen += 13 - (blockLen - minimalPaddingLen);

        // Note: fullLen is always not less than usedLen, and blockLen
        // is always bigger than minimalPaddingLen, so we don't worry
        // about negative values. 0x01 is added to the result to ensure
        // that the return value is positive.  The extra one byte does
        // not impact the overall MAC compression function evaluations.
        return 0x01 + (int)(Math.ceil(fullLen/(1.0d * blockLen)) -
                Math.ceil(usedLen/(1.0d * blockLen))) * blockLen;
    }
}

