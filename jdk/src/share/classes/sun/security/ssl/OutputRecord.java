/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
import sun.misc.HexDumpEncoder;


/**
 * SSL 3.0 records, as written to a TCP stream.
 *
 * Each record has a message area that starts out with data supplied by the
 * application.  It may grow/shrink due to compression and will be modified
 * in place for mac-ing and encryption.
 *
 * Handshake records have additional needs, notably accumulation of a set
 * of hashes which are used to establish that handshaking was done right.
 * Handshake records usually have several handshake messages each, and we
 * need message-level control over what's hashed.
 *
 * @author David Brownell
 */
class OutputRecord extends ByteArrayOutputStream implements Record {

    private HandshakeHash       handshakeHash;
    private int                 lastHashed;
    private boolean             firstMessage;
    final private byte          contentType;
    private int                 headerOffset;

    // current protocol version, sent as record version
    ProtocolVersion     protocolVersion;

    // version for the ClientHello message. Only relevant if this is a
    // client handshake record. If set to ProtocolVersion.SSL20Hello,
    // the V3 client hello is converted to V2 format.
    private ProtocolVersion     helloVersion;

    /* Class and subclass dynamic debugging support */
    static final Debug debug = Debug.getInstance("ssl");

    /*
     * Default constructor makes a record supporting the maximum
     * SSL record size.  It allocates the header bytes directly.
     *
     * The structure of the byte buffer looks like:
     *
     *     |---------+--------+-------+---------------------------------|
     *     | unused  | header |  IV   | content, MAC/TAG, padding, etc. |
     *     |    headerPlusMaxIVSize   |
     *
     * unused: unused part of the buffer of size
     *
     *             headerPlusMaxIVSize - header size - IV size
     *
     *         When this object is created, we don't know the protocol
     *         version number, IV length, etc., so reserve space in front
     *         to avoid extra data movement (copies).
     * header: the header of an SSL record
     * IV:     the optional IV/nonce field, it is only required for block
     *         (TLS 1.1 or later) and AEAD cipher suites.
     *
     * @param type the content type for the record
     */
    OutputRecord(byte type, int size) {
        super(size);
        this.protocolVersion = ProtocolVersion.DEFAULT;
        this.helloVersion = ProtocolVersion.DEFAULT_HELLO;
        firstMessage = true;
        count = headerPlusMaxIVSize;
        contentType = type;
        lastHashed = count;
        headerOffset = headerPlusMaxIVSize - headerSize;
    }

    OutputRecord(byte type) {
        this(type, recordSize(type));
    }

    /**
     * Get the size of the buffer we need for records of the specified
     * type.
     */
    private static int recordSize(byte type) {
        if ((type == ct_change_cipher_spec) || (type == ct_alert)) {
            return maxAlertRecordSize;
        } else {
            return maxRecordSize;
        }
    }

    /*
     * Updates the SSL version of this record.
     */
    synchronized void setVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    /*
     * Updates helloVersion of this record.
     */
    synchronized void setHelloVersion(ProtocolVersion helloVersion) {
        this.helloVersion = helloVersion;
    }

    /*
     * Reset the record so that it can be refilled, starting
     * immediately after the header.
     */
    @Override
    public synchronized void reset() {
        super.reset();
        count = headerPlusMaxIVSize;
        lastHashed = count;
        headerOffset = headerPlusMaxIVSize - headerSize;
    }

    /*
     * For handshaking, we need to be able to hash every byte above the
     * record marking layer.  This is where we're guaranteed to see those
     * bytes, so this is where we can hash them.
     */
    void setHandshakeHash(HandshakeHash handshakeHash) {
        assert(contentType == ct_handshake);
        this.handshakeHash = handshakeHash;
    }

    /*
     * We hash (the plaintext) on demand.  There is one place where
     * we want to access the hash in the middle of a record:  client
     * cert message gets hashed, and part of the same record is the
     * client cert verify message which uses that hash.  So we track
     * how much of each record we've hashed so far.
     */
    void doHashes() {
        int len = count - lastHashed;

        if (len > 0) {
            hashInternal(buf, lastHashed, len);
            lastHashed = count;
        }
    }

    /*
     * Need a helper function so we can hash the V2 hello correctly
     */
    private void hashInternal(byte buf [], int offset, int len) {
        if (debug != null && Debug.isOn("data")) {
            try {
                HexDumpEncoder hd = new HexDumpEncoder();

                System.out.println("[write] MD5 and SHA1 hashes:  len = "
                    + len);
                hd.encodeBuffer(new ByteArrayInputStream(buf,
                    lastHashed, len), System.out);
            } catch (IOException e) { }
        }

        handshakeHash.update(buf, lastHashed, len);
        lastHashed = count;
    }

    /*
     * Return true iff the record is empty -- to avoid doing the work
     * of sending empty records over the network.
     */
    boolean isEmpty() {
        return count == headerPlusMaxIVSize;
    }

    /*
     * Return true if the record is of an alert of the given description.
     *
     * Per SSL/TLS specifications, alert messages convey the severity of the
     * message (warning or fatal) and a description of the alert. An alert
     * is defined with a two bytes struct, {byte level, byte description},
     * following after the header bytes.
     */
    boolean isAlert(byte description) {
        if ((count > (headerPlusMaxIVSize + 1)) && (contentType == ct_alert)) {
            return buf[headerPlusMaxIVSize + 1] == description;
        }

        return false;
    }

    /*
     * Encrypt ... length may grow due to block cipher padding, or
     * message authentication code or tag.
     */
    void encrypt(Authenticator authenticator, CipherBox box)
            throws IOException {

        // In case we are automatically flushing a handshake stream, make
        // sure we have hashed the message first.
        //
        // when we support compression, hashing can't go here
        // since it'll need to be done on the uncompressed data,
        // and the MAC applies to the compressed data.
        if (contentType == ct_handshake) {
            doHashes();
        }

        // Requires message authentication code for stream and block
        // cipher suites.
        if (authenticator instanceof MAC) {
            MAC signer = (MAC)authenticator;
            if (signer.MAClen() != 0) {
                byte[] hash = signer.compute(contentType, buf,
                    headerPlusMaxIVSize, count - headerPlusMaxIVSize, false);
                write(hash);
            }
        }

        if (!box.isNullCipher()) {
            // Requires explicit IV/nonce for CBC/AEAD cipher suites for
            // TLS 1.1 or later.
            if ((protocolVersion.v >= ProtocolVersion.TLS11.v) &&
                                    (box.isCBCMode() || box.isAEADMode())) {
                byte[] nonce = box.createExplicitNonce(authenticator,
                                    contentType, count - headerPlusMaxIVSize);
                int offset = headerPlusMaxIVSize - nonce.length;
                System.arraycopy(nonce, 0, buf, offset, nonce.length);
                headerOffset = offset - headerSize;
            } else {
                headerOffset = headerPlusMaxIVSize - headerSize;
            }

            // encrypt the content
            int offset = headerPlusMaxIVSize;
            if (!box.isAEADMode()) {
                // The explicit IV can be encrypted.
                offset = headerOffset + headerSize;
            }   // Otherwise, DON'T encrypt the nonce_explicit for AEAD mode

            count = offset + box.encrypt(buf, offset, count - offset);
        }
    }

    /*
     * Tell how full the buffer is ... for filling it with application or
     * handshake data.
     */
    final int availableDataBytes() {
        int dataSize = count - headerPlusMaxIVSize;
        return maxDataSize - dataSize;
    }

    /*
     * Increases the capacity if necessary to ensure that it can hold
     * at least the number of elements specified by the minimum
     * capacity argument.
     *
     * Note that the increased capacity is only can be used for held
     * record buffer. Please DO NOT update the availableDataBytes()
     * according to the expended buffer capacity.
     *
     * @see availableDataBytes()
     */
    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        if (minCapacity > buf.length) {
            buf = Arrays.copyOf(buf, minCapacity);
        }
    }

    /*
     * Return the type of SSL record that's buffered here.
     */
    final byte contentType() {
        return contentType;
    }

    /*
     * Write the record out on the stream.  Note that you must have (in
     * order) compressed the data, appended the MAC, and encrypted it in
     * order for the record to be understood by the other end.  (Some of
     * those steps will be null early in handshaking.)
     *
     * Note that this does no locking for the connection, it's required
     * that synchronization be done elsewhere.  Also, this does its work
     * in a single low level write, for efficiency.
     */
    void write(OutputStream s, boolean holdRecord,
            ByteArrayOutputStream heldRecordBuffer) throws IOException {

        /*
         * Don't emit content-free records.  (Even change cipher spec
         * messages have a byte of data!)
         */
        if (count == headerPlusMaxIVSize) {
            return;
        }

        int length = count - headerOffset - headerSize;
        // "should" really never write more than about 14 Kb...
        if (length < 0) {
            throw new SSLException("output record size too small: "
                + length);
        }

        if (debug != null
                && (Debug.isOn("record") || Debug.isOn("handshake"))) {
            if ((debug != null && Debug.isOn("record"))
                    || contentType() == ct_change_cipher_spec)
                System.out.println(Thread.currentThread().getName()
                    // v3.0/v3.1 ...
                    + ", WRITE: " + protocolVersion
                    + " " + InputRecord.contentName(contentType())
                    + ", length = " + length);
        }

        /*
         * If this is the initial ClientHello on this connection and
         * we're not trying to resume a (V3) session then send a V2
         * ClientHello instead so we can detect V2 servers cleanly.
         */
         if (firstMessage && useV2Hello()) {
            byte[] v3Msg = new byte[length - 4];
            System.arraycopy(buf, headerPlusMaxIVSize + 4,
                                        v3Msg, 0, v3Msg.length);
            headerOffset = 0;   // reset the header offset
            V3toV2ClientHello(v3Msg);
            handshakeHash.reset();
            lastHashed = 2;
            doHashes();
            if (debug != null && Debug.isOn("record"))  {
                System.out.println(
                    Thread.currentThread().getName()
                    + ", WRITE: SSLv2 client hello message"
                    + ", length = " + (count - 2)); // 2 byte SSLv2 header
            }
        } else {
            /*
             * Fill out the header, write it and the message.
             */
            buf[headerOffset + 0] = contentType;
            buf[headerOffset + 1] = protocolVersion.major;
            buf[headerOffset + 2] = protocolVersion.minor;
            buf[headerOffset + 3] = (byte)(length >> 8);
            buf[headerOffset + 4] = (byte)(length);
        }
        firstMessage = false;

        /*
         * The upper levels may want us to delay sending this packet so
         * multiple TLS Records can be sent in one (or more) TCP packets.
         * If so, add this packet to the heldRecordBuffer.
         *
         * NOTE:  all writes have been synchronized by upper levels.
         */
        int debugOffset = 0;
        if (holdRecord) {
            /*
             * If holdRecord is true, we must have a heldRecordBuffer.
             *
             * Don't worry about the override of writeBuffer(), because
             * when holdRecord is true, the implementation in this class
             * will be used.
             */
            writeBuffer(heldRecordBuffer,
                        buf, headerOffset, count - headerOffset, debugOffset);
        } else {
            // It's time to send, do we have buffered data?
            // May or may not have a heldRecordBuffer.
            if (heldRecordBuffer != null && heldRecordBuffer.size() > 0) {
                int heldLen = heldRecordBuffer.size();

                // Ensure the capacity of this buffer.
                int newCount = count + heldLen - headerOffset;
                ensureCapacity(newCount);

                // Slide everything in the buffer to the right.
                System.arraycopy(buf, headerOffset,
                                    buf, heldLen, count - headerOffset);

                // Prepend the held record to the buffer.
                System.arraycopy(
                    heldRecordBuffer.toByteArray(), 0, buf, 0, heldLen);
                count = newCount;
                headerOffset = 0;

                // Clear the held buffer.
                heldRecordBuffer.reset();

                // The held buffer has been dumped, set the debug dump offset.
                debugOffset = heldLen;
            }
            writeBuffer(s, buf, headerOffset,
                        count - headerOffset, debugOffset);
        }

        reset();
    }

    /*
     * Actually do the write here.  For SSLEngine's HS data,
     * we'll override this method and let it take the appropriate
     * action.
     */
    void writeBuffer(OutputStream s, byte [] buf, int off, int len,
            int debugOffset) throws IOException {
        s.write(buf, off, len);
        s.flush();

        // Output only the record from the specified debug offset.
        if (debug != null && Debug.isOn("packet")) {
            try {
                HexDumpEncoder hd = new HexDumpEncoder();

                System.out.println("[Raw write]: length = " +
                                                    (len - debugOffset));
                hd.encodeBuffer(new ByteArrayInputStream(buf,
                    off + debugOffset, len - debugOffset), System.out);
            } catch (IOException e) { }
        }
    }

    /*
     * Return whether the buffer contains a ClientHello message that should
     * be converted to V2 format.
     */
    private boolean useV2Hello() {
        return firstMessage
            && (helloVersion == ProtocolVersion.SSL20Hello)
            && (contentType == ct_handshake)
            && (buf[headerOffset + 5] == HandshakeMessage.ht_client_hello)
                                            //  5: recode header size
            && (buf[headerPlusMaxIVSize + 4 + 2 + 32] == 0);
                                            // V3 session ID is empty
                                            //  4: handshake header size
                                            //  2: client_version in ClientHello
                                            // 32: random in ClientHello
    }

    /*
     * Detect "old" servers which are capable of SSL V2.0 protocol ... for
     * example, Netscape Commerce 1.0 servers.  The V3 message is in the
     * header and the bytes passed as parameter.  This routine translates
     * the V3 message into an equivalent V2 one.
     *
     * Note that the translation will strip off all hello extensions as
     * SSL V2.0 does not support hello extension.
     */
    private void V3toV2ClientHello(byte v3Msg []) throws SSLException {
        int v3SessionIdLenOffset = 2 + 32; // version + nonce
        int v3SessionIdLen = v3Msg[v3SessionIdLenOffset];
        int v3CipherSpecLenOffset = v3SessionIdLenOffset + 1 + v3SessionIdLen;
        int v3CipherSpecLen = ((v3Msg[v3CipherSpecLenOffset] & 0xff) << 8) +
          (v3Msg[v3CipherSpecLenOffset + 1] & 0xff);
        int cipherSpecs = v3CipherSpecLen / 2; // 2 bytes each in V3

        /*
         * Copy over the cipher specs. We don't care about actually translating
         * them for use with an actual V2 server since we only talk V3.
         * Therefore, just copy over the V3 cipher spec values with a leading
         * 0.
         */
        int v3CipherSpecOffset = v3CipherSpecLenOffset + 2; // skip length
        int v2CipherSpecLen = 0;
        count = 11;
        boolean containsRenegoInfoSCSV = false;
        for (int i = 0; i < cipherSpecs; i++) {
            byte byte1, byte2;

            byte1 = v3Msg[v3CipherSpecOffset++];
            byte2 = v3Msg[v3CipherSpecOffset++];
            v2CipherSpecLen += V3toV2CipherSuite(byte1, byte2);
            if (!containsRenegoInfoSCSV &&
                        byte1 == (byte)0x00 && byte2 == (byte)0xFF) {
                containsRenegoInfoSCSV = true;
            }
        }

        if (!containsRenegoInfoSCSV) {
            v2CipherSpecLen += V3toV2CipherSuite((byte)0x00, (byte)0xFF);
        }

        /*
         * Build the first part of the V3 record header from the V2 one
         * that's now buffered up.  (Lengths are fixed up later).
         */
        buf[2] = HandshakeMessage.ht_client_hello;
        buf[3] = v3Msg[0];      // major version
        buf[4] = v3Msg[1];      // minor version
        buf[5] = (byte)(v2CipherSpecLen >>> 8);
        buf[6] = (byte)v2CipherSpecLen;
        buf[7] = 0;
        buf[8] = 0;             // always no session
        buf[9] = 0;
        buf[10] = 32;           // nonce length (always 32 in V3)

        /*
         * Copy in the nonce.
         */
        System.arraycopy(v3Msg, 2, buf, count, 32);
        count += 32;

        /*
         * Set the length of the message.
         */
        count -= 2; // don't include length field itself
        buf[0] = (byte)(count >>> 8);
        buf[0] |= 0x80;
        buf[1] = (byte)(count);
        count += 2;
    }

    /*
     * Mappings from V3 cipher suite encodings to their pure V2 equivalents.
     * This is taken from the SSL V3 specification, Appendix E.
     */
    private static int[] V3toV2CipherMap1 =
        {-1, -1, -1, 0x02, 0x01, -1, 0x04, 0x05, -1, 0x06, 0x07};
    private static int[] V3toV2CipherMap3 =
        {-1, -1, -1, 0x80, 0x80, -1, 0x80, 0x80, -1, 0x40, 0xC0};

    /*
     * See which matching pure-V2 cipher specs we need to include.
     * We are including these not because we are actually prepared
     * to talk V2 but because the Oracle Web Server insists on receiving
     * at least 1 "pure V2" cipher suite that it supports and returns an
     * illegal_parameter alert unless one is present. Rather than mindlessly
     * claiming to implement all documented pure V2 cipher suites the code below
     * just claims to implement the V2 cipher suite that is "equivalent"
     * in terms of cipher algorithm & exportability with the actual V3 cipher
     * suite that we do support.
     */
    private int V3toV2CipherSuite(byte byte1, byte byte2) {
        buf[count++] = 0;
        buf[count++] = byte1;
        buf[count++] = byte2;

        if (((byte2 & 0xff) > 0xA) ||
                (V3toV2CipherMap1[byte2] == -1)) {
            return 3;
        }

        buf[count++] = (byte)V3toV2CipherMap1[byte2];
        buf[count++] = 0;
        buf[count++] = (byte)V3toV2CipherMap3[byte2];

        return 6;
    }
}
