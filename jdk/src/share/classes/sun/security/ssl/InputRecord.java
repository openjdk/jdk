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
 * SSL 3.0 records, as pulled off a TCP stream.  Input records are
 * basically buffers tied to a particular input stream ... a layer
 * above this must map these records into the model of a continuous
 * stream of data.
 *
 * Since this returns SSL 3.0 records, it's the layer that needs to
 * map SSL 2.0 style handshake records into SSL 3.0 ones for those
 * "old" clients that interop with both V2 and V3 servers.  Not as
 * pretty as might be desired.
 *
 * NOTE:  During handshaking, each message must be hashed to support
 * verification that the handshake process wasn't compromised.
 *
 * @author David Brownell
 */
class InputRecord extends ByteArrayInputStream implements Record {

    private HandshakeHash       handshakeHash;
    private int                 lastHashed;
    boolean                     formatVerified = true;  // SSLv2 ruled out?
    private boolean             isClosed;
    private boolean             appDataValid;

    // The ClientHello version to accept. If set to ProtocolVersion.SSL20Hello
    // and the first message we read is a ClientHello in V2 format, we convert
    // it to V3. Otherwise we throw an exception when encountering a V2 hello.
    private ProtocolVersion     helloVersion;

    /* Class and subclass dynamic debugging support */
    static final Debug debug = Debug.getInstance("ssl");

    /* The existing record length */
    private int exlen;

    /* V2 handshake message */
    private byte v2Buf[];

    /*
     * Construct the record to hold the maximum sized input record.
     * Data will be filled in separately.
     *
     * The structure of the byte buffer looks like:
     *
     *     |--------+---------+---------------------------------|
     *     | header |   IV    | content, MAC/TAG, padding, etc. |
     *     | headerPlusIVSize |
     *
     * header: the header of an SSL records
     * IV:     the optional IV/nonce field, it is only required for block
     *         (TLS 1.1 or later) and AEAD cipher suites.
     *
     */
    InputRecord() {
        super(new byte[maxRecordSize]);
        setHelloVersion(ProtocolVersion.DEFAULT_HELLO);
        pos = headerSize;
        count = headerSize;
        lastHashed = count;
        exlen = 0;
        v2Buf = null;
    }

    void setHelloVersion(ProtocolVersion helloVersion) {
        this.helloVersion = helloVersion;
    }

    ProtocolVersion getHelloVersion() {
        return helloVersion;
    }

    /*
     * Enable format checks if initial handshaking hasn't completed
     */
    void enableFormatChecks() {
        formatVerified = false;
    }

    // return whether the data in this record is valid, decrypted data
    boolean isAppDataValid() {
        return appDataValid;
    }

    void setAppDataValid(boolean value) {
        appDataValid = value;
    }

    /*
     * Return the content type of the record.
     */
    byte contentType() {
        return buf[0];
    }

    /*
     * For handshaking, we need to be able to hash every byte above the
     * record marking layer.  This is where we're guaranteed to see those
     * bytes, so this is where we can hash them ... especially in the
     * case of hashing the initial V2 message!
     */
    void setHandshakeHash(HandshakeHash handshakeHash) {
        this.handshakeHash = handshakeHash;
    }

    HandshakeHash getHandshakeHash() {
        return handshakeHash;
    }

    void decrypt(Authenticator authenticator,
            CipherBox box) throws BadPaddingException {
        BadPaddingException reservedBPE = null;
        int tagLen =
            (authenticator instanceof MAC) ? ((MAC)authenticator).MAClen() : 0;
        int cipheredLength = count - headerSize;

        if (!box.isNullCipher()) {
            try {
                // apply explicit nonce for AEAD/CBC cipher suites if needed
                int nonceSize = box.applyExplicitNonce(authenticator,
                        contentType(), buf, headerSize, cipheredLength);
                pos = headerSize + nonceSize;
                lastHashed = pos;   // don't digest the explicit nonce

                // decrypt the content
                int offset = headerSize;
                if (box.isAEADMode()) {
                    // DON'T encrypt the nonce_explicit for AEAD mode
                    offset += nonceSize;
                }   // The explicit IV for CBC mode can be decrypted.

                // Note that the CipherBox.decrypt() does not change
                // the capacity of the buffer.
                count = offset +
                    box.decrypt(buf, offset, count - offset, tagLen);

                // Note that we don't remove the nonce from the buffer.
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
        if (authenticator instanceof MAC && tagLen != 0) {
            MAC signer = (MAC)authenticator;
            int macOffset = count - tagLen;
            int contentLen = macOffset - pos;

            // Note that although it is not necessary, we run the same MAC
            // computation and comparison on the payload for both stream
            // cipher and CBC block cipher.
            if (contentLen < 0) {
                // negative data length, something is wrong
                if (reservedBPE == null) {
                    reservedBPE = new BadPaddingException("bad record");
                }

                // set offset of the dummy MAC
                macOffset = headerSize + cipheredLength - tagLen;
                contentLen = macOffset - headerSize;
            }

            count -= tagLen;  // Set the count before any MAC checking
                              // exception occurs, so that the following
                              // process can read the actual decrypted
                              // content (minus the MAC) in the fragment
                              // if necessary.

            // Run MAC computation and comparison on the payload.
            if (checkMacTags(contentType(),
                    buf, pos, contentLen, signer, false)) {
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
                // hash algorithm of the MAC) than the cipheredLength. However,
                // We won't need to worry about it because we always use a
                // maximum buffer for every record.  We need a change here if
                // we use small buffer size in the future.
                if (remainingLen > buf.length) {
                    // unlikely to happen, just a placehold
                    throw new RuntimeException(
                        "Internal buffer capacity error");
                }

                // Won't need to worry about the result on the remainder. And
                // then we won't need to worry about what's actual data to
                // check MAC tag on.  We start the check from the header of the
                // buffer so that we don't need to construct a new byte buffer.
                checkMacTags(contentType(), buf, 0, remainingLen, signer, true);
            }
        }

        // Is it a failover?
        if (reservedBPE != null) {
            throw reservedBPE;
        }
    }

    /*
     * Run MAC computation and comparison
     *
     * Please DON'T change the content of the byte buffer parameter!
     */
    static boolean checkMacTags(byte contentType, byte[] buffer,
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
    static int calculateRemainingLen(
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
                Math.ceil(usedLen/(1.0d * blockLen))) * signer.hashBlockLen();
    }

    /*
     * Well ... hello_request messages are _never_ hashed since we can't
     * know when they'd appear in the sequence.
     */
    void ignore(int bytes) {
        if (bytes > 0) {
            pos += bytes;
            lastHashed = pos;
        }
    }

    /*
     * We hash the (plaintext) we've processed, but only on demand.
     *
     * There is one place where we want to access the hash in the middle
     * of a record:  client cert message gets hashed, and part of the
     * same record is the client cert verify message which uses that hash.
     * So we track how much we've read and hashed.
     */
    void doHashes() {
        int len = pos - lastHashed;

        if (len > 0) {
            hashInternal(buf, lastHashed, len);
            lastHashed = pos;
        }
    }

    /*
     * Need a helper function so we can hash the V2 hello correctly
     */
    private void hashInternal(byte databuf [], int offset, int len) {
        if (debug != null && Debug.isOn("data")) {
            try {
                HexDumpEncoder hd = new HexDumpEncoder();

                System.out.println("[read] MD5 and SHA1 hashes:  len = "
                    + len);
                hd.encodeBuffer(new ByteArrayInputStream(databuf, offset, len),
                    System.out);
            } catch (IOException e) { }
        }
        handshakeHash.update(databuf, offset, len);
    }


    /*
     * Handshake messages may cross record boundaries.  We "queue"
     * these in big buffers if we need to cope with this problem.
     * This is not anticipated to be a common case; if this turns
     * out to be wrong, this can readily be sped up.
     */
    void queueHandshake(InputRecord r) throws IOException {
        int len;

        /*
         * Hash any data that's read but unhashed.
         */
        doHashes();

        /*
         * Move any unread data to the front of the buffer,
         * flagging it all as unhashed.
         */
        if (pos > headerSize) {
            len = count - pos;
            if (len != 0) {
                System.arraycopy(buf, pos, buf, headerSize, len);
            }
            pos = headerSize;
            lastHashed = pos;
            count = headerSize + len;
        }

        /*
         * Grow "buf" if needed
         */
        len = r.available() + count;
        if (buf.length < len) {
            byte        newbuf [];

            newbuf = new byte [len];
            System.arraycopy(buf, 0, newbuf, 0, count);
            buf = newbuf;
        }

        /*
         * Append the new buffer to this one.
         */
        System.arraycopy(r.buf, r.pos, buf, count, len - count);
        count = len;

        /*
         * Adjust lastHashed; important for now with clients which
         * send SSL V2 client hellos.  This will go away eventually,
         * by buffer code cleanup.
         */
        len = r.lastHashed - r.pos;
        if (pos == headerSize) {
            lastHashed += len;
        } else {
            throw new SSLProtocolException("?? confused buffer hashing ??");
        }
        // we've read the record, advance the pointers
        r.pos = r.count;
    }


    /**
     * Prevent any more data from being read into this record,
     * and flag the record as holding no data.
     */
    @Override
    public void close() {
        appDataValid = false;
        isClosed = true;
        mark = 0;
        pos = 0;
        count = 0;
    }


    /*
     * We may need to send this SSL v2 "No Cipher" message back, if we
     * are faced with an SSLv2 "hello" that's not saying "I talk v3".
     * It's the only one documented in the V2 spec as a fatal error.
     */
    private static final byte[] v2NoCipher = {
        (byte)0x80, (byte)0x03, // unpadded 3 byte record
        (byte)0x00,             // ... error message
        (byte)0x00, (byte)0x01  // ... NO_CIPHER error
    };

    private int readFully(InputStream s, byte b[], int off, int len)
            throws IOException {
        int n = 0;
        while (n < len) {
            int readLen = s.read(b, off + n, len - n);
            if (readLen < 0) {
                return readLen;
            }

            if (debug != null && Debug.isOn("packet")) {
                try {
                    HexDumpEncoder hd = new HexDumpEncoder();
                    ByteBuffer bb = ByteBuffer.wrap(b, off + n, readLen);

                    System.out.println("[Raw read]: length = " +
                        bb.remaining());
                    hd.encodeBuffer(bb, System.out);
                } catch (IOException e) { }
            }

            n += readLen;
            exlen += readLen;
        }

        return n;
    }

    /*
     * Read the SSL V3 record ... first time around, check to see if it
     * really IS a V3 record.  Handle SSL V2 clients which can talk V3.0,
     * as well as real V3 record format; otherwise report an error.
     */
    void read(InputStream s, OutputStream o) throws IOException {
        if (isClosed) {
            return;
        }

        /*
         * For SSL it really _is_ an error if the other end went away
         * so ungracefully as to not shut down cleanly.
         */
        if(exlen < headerSize) {
            int really = readFully(s, buf, exlen, headerSize - exlen);
            if (really < 0) {
                throw new EOFException("SSL peer shut down incorrectly");
            }

            pos = headerSize;
            count = headerSize;
            lastHashed = pos;
        }

        /*
         * The first record might use some other record marking convention,
         * typically SSL v2 header.  (PCT could also be detected here.)
         * This case is currently common -- Navigator 3.0 usually works
         * this way, as do IE 3.0 and other products.
         */
        if (!formatVerified) {
            formatVerified = true;
            /*
             * The first record must either be a handshake record or an
             * alert message. If it's not, it is either invalid or an
             * SSLv2 message.
             */
            if (buf[0] != ct_handshake && buf[0] != ct_alert) {
                handleUnknownRecord(s, o);
            } else {
                readV3Record(s, o);
            }
        } else { // formatVerified == true
            readV3Record(s, o);
        }
    }

    /**
     * Return true if the specified record protocol version is out of the
     * range of the possible supported versions.
     */
    static void checkRecordVersion(ProtocolVersion version,
            boolean allowSSL20Hello) throws SSLException {
        // Check if the record version is too old (currently not possible)
        // or if the major version does not match.
        //
        // The actual version negotiation is in the handshaker classes
        if ((version.v < ProtocolVersion.MIN.v) ||
            ((version.major & 0xFF) > (ProtocolVersion.MAX.major & 0xFF))) {

            // if it's not SSLv2, we're out of here.
            if (!allowSSL20Hello ||
                    (version.v != ProtocolVersion.SSL20Hello.v)) {
                throw new SSLException("Unsupported record version " + version);
            }
        }
    }

    /**
     * Read a SSL/TLS record. Throw an IOException if the format is invalid.
     */
    private void readV3Record(InputStream s, OutputStream o)
            throws IOException {
        ProtocolVersion recordVersion = ProtocolVersion.valueOf(buf[1], buf[2]);

        // check the record version
        checkRecordVersion(recordVersion, false);

        /*
         * Get and check length, then the data.
         */
        int contentLen = ((buf[3] & 0x0ff) << 8) + (buf[4] & 0xff);

        /*
         * Check for upper bound.
         */
        if (contentLen < 0 || contentLen > maxLargeRecordSize - headerSize) {
            throw new SSLProtocolException("Bad InputRecord size"
                + ", count = " + contentLen
                + ", buf.length = " + buf.length);
        }

        /*
         * Grow "buf" if needed. Since buf is maxRecordSize by default,
         * this only occurs when we receive records which violate the
         * SSL specification. This is a workaround for a Microsoft SSL bug.
         */
        if (contentLen > buf.length - headerSize) {
            byte[] newbuf = new byte[contentLen + headerSize];
            System.arraycopy(buf, 0, newbuf, 0, headerSize);
            buf = newbuf;
        }

        if (exlen < contentLen + headerSize) {
            int really = readFully(
                s, buf, exlen, contentLen + headerSize - exlen);
            if (really < 0) {
                throw new SSLException("SSL peer shut down incorrectly");
            }
        }

        // now we've got a complete record.
        count = contentLen + headerSize;
        exlen = 0;

        if (debug != null && Debug.isOn("record")) {
            if (count < 0 || count > (maxRecordSize - headerSize)) {
                System.out.println(Thread.currentThread().getName()
                    + ", Bad InputRecord size" + ", count = " + count);
            }
            System.out.println(Thread.currentThread().getName()
                + ", READ: " + recordVersion + " "
                + contentName(contentType()) + ", length = " + available());
        }
        /*
         * then caller decrypts, verifies, and uncompresses
         */
    }

    /**
     * Deal with unknown records. Called if the first data we read on this
     * connection does not look like an SSL/TLS record. It could a SSLv2
     * message, or just garbage.
     */
    private void handleUnknownRecord(InputStream s, OutputStream o)
            throws IOException {
        /*
         * No?  Oh well; does it look like a V2 "ClientHello"?
         * That'd be an unpadded handshake message; we don't
         * bother checking length just now.
         */
        if (((buf[0] & 0x080) != 0) && buf[2] == 1) {
            /*
             * if the user has disabled SSLv2Hello (using
             * setEnabledProtocol) then throw an
             * exception
             */
            if (helloVersion != ProtocolVersion.SSL20Hello) {
                throw new SSLHandshakeException("SSLv2Hello is disabled");
            }

            ProtocolVersion recordVersion =
                                ProtocolVersion.valueOf(buf[3], buf[4]);

            if (recordVersion == ProtocolVersion.SSL20Hello) {
                /*
                 * Looks like a V2 client hello, but not one saying
                 * "let's talk SSLv3".  So we send an SSLv2 error
                 * message, one that's treated as fatal by clients.
                 * (Otherwise we'll hang.)
                 */
                try {
                    writeBuffer(o, v2NoCipher, 0, v2NoCipher.length);
                } catch (Exception e) {
                    /* NOTHING */
                }
                throw new SSLException("Unsupported SSL v2.0 ClientHello");
            }

            /*
             * If we can map this into a V3 ClientHello, read and
             * hash the rest of the V2 handshake, turn it into a
             * V3 ClientHello message, and pass it up.
             */
            int len = ((buf[0] & 0x7f) << 8) +
                (buf[1] & 0xff) - 3;
            if (v2Buf == null) {
                v2Buf = new byte[len];
            }
            if (exlen < len + headerSize) {
                int really = readFully(
                        s, v2Buf, exlen - headerSize, len + headerSize - exlen);
                if (really < 0) {
                    throw new EOFException("SSL peer shut down incorrectly");
                }
            }

            // now we've got a complete record.
            exlen = 0;

            hashInternal(buf, 2, 3);
            hashInternal(v2Buf, 0, len);
            V2toV3ClientHello(v2Buf);
            v2Buf = null;
            lastHashed = count;

            if (debug != null && Debug.isOn("record"))  {
                System.out.println(
                    Thread.currentThread().getName()
                    + ", READ:  SSL v2, contentType = "
                    + contentName(contentType())
                    + ", translated length = " + available());
            }
            return;

        } else {
            /*
             * Does it look like a V2 "ServerHello"?
             */
            if (((buf [0] & 0x080) != 0) && buf [2] == 4) {
                throw new SSLException(
                    "SSL V2.0 servers are not supported.");
            }

            /*
             * If this is a V2 NoCipher message then this means
             * the other server doesn't support V3. Otherwise, we just
             * don't understand what it's saying.
             */
            for (int i = 0; i < v2NoCipher.length; i++) {
                if (buf[i] != v2NoCipher[i]) {
                    throw new SSLException(
                        "Unrecognized SSL message, plaintext connection?");
                }
            }

            throw new SSLException("SSL V2.0 servers are not supported.");
        }
    }

    /*
     * Actually do the write here.  For SSLEngine's HS data,
     * we'll override this method and let it take the appropriate
     * action.
     */
    void writeBuffer(OutputStream s, byte [] buf, int off, int len)
            throws IOException {
        s.write(buf, 0, len);
        s.flush();
    }

    /*
     * Support "old" clients which are capable of SSL V3.0 protocol ... for
     * example, Navigator 3.0 clients.  The V2 message is in the header and
     * the bytes passed as parameter.  This routine translates the V2 message
     * into an equivalent V3 one.
     */
    private void V2toV3ClientHello(byte v2Msg []) throws SSLException
    {
        int i;

        /*
         * Build the first part of the V3 record header from the V2 one
         * that's now buffered up.  (Lengths are fixed up later).
         */
        buf [0] = ct_handshake;
        buf [1] = buf [3];      // V3.x
        buf[2] = buf[4];
        // header [3..4] for handshake message length
        // count = 5;

        /*
         * Store the generic V3 handshake header:  4 bytes
         */
        buf [5] = 1;    // HandshakeMessage.ht_client_hello
        // buf [6..8] for length of ClientHello (int24)
        // count += 4;

        /*
         * ClientHello header starts with SSL version
         */
        buf [9] = buf [1];
        buf [10] = buf [2];
        // count += 2;
        count = 11;

        /*
         * Start parsing the V2 message ...
         */
        int      cipherSpecLen, sessionIdLen, nonceLen;

        cipherSpecLen = ((v2Msg [0] & 0xff) << 8) + (v2Msg [1] & 0xff);
        sessionIdLen  = ((v2Msg [2] & 0xff) << 8) + (v2Msg [3] & 0xff);
        nonceLen   = ((v2Msg [4] & 0xff) << 8) + (v2Msg [5] & 0xff);

        /*
         * Copy Random value/nonce ... if less than the 32 bytes of
         * a V3 "Random", right justify and zero pad to the left.  Else
         * just take the last 32 bytes.
         */
        int      offset = 6 + cipherSpecLen + sessionIdLen;

        if (nonceLen < 32) {
            for (i = 0; i < (32 - nonceLen); i++)
                buf [count++] = 0;
            System.arraycopy(v2Msg, offset, buf, count, nonceLen);
            count += nonceLen;
        } else {
            System.arraycopy(v2Msg, offset + (nonceLen - 32),
                    buf, count, 32);
            count += 32;
        }

        /*
         * Copy Session ID (only one byte length!)
         */
        offset -= sessionIdLen;
        buf [count++] = (byte) sessionIdLen;

        System.arraycopy(v2Msg, offset, buf, count, sessionIdLen);
        count += sessionIdLen;

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
        j = count + 2;

        for (i = 0; i < cipherSpecLen; i += 3) {
            if (v2Msg [offset + i] != 0)
                continue;
            buf [j++] = v2Msg [offset + i + 1];
            buf [j++] = v2Msg [offset + i + 2];
        }

        j -= count + 2;
        buf [count++] = (byte) (j >>> 8);
        buf [count++] = (byte) j;
        count += j;

        /*
         * Append compression methods (default/null only)
         */
        buf [count++] = 1;
        buf [count++] = 0;      // Session.compression_null

        /*
         * Fill in lengths of the messages we synthesized (nested:
         * V3 handshake message within V3 record) and then return
         */
        buf [3] = (byte) (count - headerSize);
        buf [4] = (byte) ((count - headerSize) >>> 8);

        buf [headerSize + 1] = 0;
        buf [headerSize + 2] = (byte) (((count - headerSize) - 4) >>> 8);
        buf [headerSize + 3] = (byte) ((count - headerSize) - 4);

        pos = headerSize;
    }

    /**
     * Return a description for the given content type. This method should be
     * in Record, but since that is an interface this is not possible.
     * Called from InputRecord and OutputRecord.
     */
    static String contentName(int contentType) {
        switch (contentType) {
        case ct_change_cipher_spec:
            return "Change Cipher Spec";
        case ct_alert:
            return "Alert";
        case ct_handshake:
            return "Handshake";
        case ct_application_data:
            return "Application Data";
        default:
            return "contentType = " + contentType;
        }
    }

}
