/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A OutputRecord class extension which uses external ByteBuffers
 * or the internal ByteArrayOutputStream for data manipulations.
 * <P>
 * Instead of rewriting this entire class
 * to use ByteBuffers, we leave things intact, so handshake, CCS,
 * and alerts will continue to use the internal buffers, but application
 * data will use external buffers.
 *
 * @author Brad Wetmore
 */
final class EngineOutputRecord extends OutputRecord {

    private SSLEngineImpl engine;
    private EngineWriter writer;

    private boolean finishedMsg = false;

    /*
     * All handshake hashing is done by the superclass
     */

    /*
     * Default constructor makes a record supporting the maximum
     * SSL record size.  It allocates the header bytes directly.
     *
     * @param type the content type for the record
     */
    EngineOutputRecord(byte type, SSLEngineImpl engine) {
        super(type, recordSize(type));
        this.engine = engine;
        writer = engine.writer;
    }

    /**
     * Get the size of the buffer we need for records of the specified
     * type.
     * <P>
     * Application data buffers will provide their own byte buffers,
     * and will not use the internal byte caching.
     */
    private static int recordSize(byte type) {
        switch (type) {

        case ct_change_cipher_spec:
        case ct_alert:
            return maxAlertRecordSize;

        case ct_handshake:
            return maxRecordSize;

        case ct_application_data:
            return 0;
        }

        throw new RuntimeException("Unknown record type: " + type);
    }

    void setFinishedMsg() {
        finishedMsg = true;
    }

    @Override
    public void flush() throws IOException {
        finishedMsg = false;
    }

    boolean isFinishedMsg() {
        return finishedMsg;
    }

    /*
     * Override the actual write below.  We do things this way to be
     * consistent with InputRecord.  InputRecord may try to write out
     * data to the peer, and *then* throw an Exception.  This forces
     * data to be generated/output before the exception is ever
     * generated.
     */
    @Override
    void writeBuffer(OutputStream s, byte [] buf, int off, int len,
            int debugOffset) throws IOException {
        /*
         * Copy data out of buffer, it's ready to go.
         */
        ByteBuffer netBB = ByteBuffer.allocate(len).put(buf, off, len).flip();
        writer.putOutboundData(netBB);
    }

    /*
     * Main method for writing non-application data.
     * We MAC/encrypt, then send down for processing.
     */
    void write(Authenticator authenticator, CipherBox writeCipher)
            throws IOException {

        /*
         * Sanity check.
         */
        switch (contentType()) {
            case ct_change_cipher_spec:
            case ct_alert:
            case ct_handshake:
                break;
            default:
                throw new RuntimeException("unexpected byte buffers");
        }

        /*
         * Don't bother to really write empty records.  We went this
         * far to drive the handshake machinery, for correctness; not
         * writing empty records improves performance by cutting CPU
         * time and network resource usage.  Also, some protocol
         * implementations are fragile and don't like to see empty
         * records, so this increases robustness.
         *
         * (Even change cipher spec messages have a byte of data!)
         */
        if (!isEmpty()) {
            // compress();              // eventually
            encrypt(authenticator, writeCipher);

            // send down for processing
            write((OutputStream)null, false, (ByteArrayOutputStream)null);
        }
        return;
    }

    /**
     * Main wrap/write driver.
     */
    void write(EngineArgs ea, Authenticator authenticator,
            CipherBox writeCipher) throws IOException {
        /*
         * sanity check to make sure someone didn't inadvertantly
         * send us an impossible combination we don't know how
         * to process.
         */
        assert(contentType() == ct_application_data);

        /*
         * Have we set the MAC's yet?  If not, we're not ready
         * to process application data yet.
         */
        if (authenticator == MAC.NULL) {
            return;
        }

        /*
         * Don't bother to really write empty records.  We went this
         * far to drive the handshake machinery, for correctness; not
         * writing empty records improves performance by cutting CPU
         * time and network resource usage.  Also, some protocol
         * implementations are fragile and don't like to see empty
         * records, so this increases robustness.
         */
        if (ea.getAppRemaining() == 0) {
            return;
        }

        /*
         * By default, we counter chosen plaintext issues on CBC mode
         * ciphersuites in SSLv3/TLS1.0 by sending one byte of application
         * data in the first record of every payload, and the rest in
         * subsequent record(s). Note that the issues have been solved in
         * TLS 1.1 or later.
         *
         * It is not necessary to split the very first application record of
         * a freshly negotiated TLS session, as there is no previous
         * application data to guess.  To improve compatibility, we will not
         * split such records.
         *
         * Because of the compatibility, we'd better produce no more than
         * SSLSession.getPacketBufferSize() net data for each wrap. As we
         * need a one-byte record at first, the 2nd record size should be
         * equal to or less than Record.maxDataSizeMinusOneByteRecord.
         *
         * This avoids issues in the outbound direction.  For a full fix,
         * the peer must have similar protections.
         */
        int length;
        if (engine.needToSplitPayload(writeCipher, protocolVersion)) {
            write(ea, authenticator, writeCipher, 0x01);
            ea.resetLim();      // reset application data buffer limit
            length = Math.min(ea.getAppRemaining(),
                        maxDataSizeMinusOneByteRecord);
        } else {
            length = Math.min(ea.getAppRemaining(), maxDataSize);
        }

        // Don't bother to really write empty records.
        if (length > 0) {
            write(ea, authenticator, writeCipher, length);
        }

        return;
    }

    void write(EngineArgs ea, Authenticator authenticator,
            CipherBox writeCipher, int length) throws IOException {
        /*
         * Copy out existing buffer values.
         */
        ByteBuffer dstBB = ea.netData;
        int dstPos = dstBB.position();
        int dstLim = dstBB.limit();

        /*
         * Where to put the data.  Jump over the header.
         *
         * Don't need to worry about SSLv2 rewrites, if we're here,
         * that's long since done.
         */
        int dstData = dstPos + headerSize + writeCipher.getExplicitNonceSize();
        dstBB.position(dstData);

        /*
         * transfer application data into the network data buffer
         */
        ea.gather(length);
        dstBB.limit(dstBB.position());
        dstBB.position(dstData);

        /*
         * "flip" but skip over header again, add MAC & encrypt
         */
        if (authenticator instanceof MAC) {
            MAC signer = (MAC)authenticator;
            if (signer.MAClen() != 0) {
                byte[] hash = signer.compute(contentType(), dstBB, false);

                /*
                 * position was advanced to limit in compute above.
                 *
                 * Mark next area as writable (above layers should have
                 * established that we have plenty of room), then write
                 * out the hash.
                 */
                dstBB.limit(dstBB.limit() + hash.length);
                dstBB.put(hash);

                // reset the position and limit
                dstBB.limit(dstBB.position());
                dstBB.position(dstData);
            }
        }

        if (!writeCipher.isNullCipher()) {
            /*
             * Requires explicit IV/nonce for CBC/AEAD cipher suites for TLS 1.1
             * or later.
             */
            if (protocolVersion.v >= ProtocolVersion.TLS11.v &&
                    (writeCipher.isCBCMode() || writeCipher.isAEADMode())) {
                byte[] nonce = writeCipher.createExplicitNonce(
                        authenticator, contentType(), dstBB.remaining());
                dstBB.position(dstPos + headerSize);
                dstBB.put(nonce);
                if (!writeCipher.isAEADMode()) {
                    // The explicit IV in TLS 1.1 and later can be encrypted.
                    dstBB.position(dstPos + headerSize);
                }   // Otherwise, DON'T encrypt the nonce_explicit for AEAD mode
            }

            /*
             * Encrypt may pad, so again the limit may have changed.
             */
            writeCipher.encrypt(dstBB, dstLim);

            if ((debug != null) && (Debug.isOn("record") ||
                    (Debug.isOn("handshake") &&
                        (contentType() == ct_change_cipher_spec)))) {
                System.out.println(Thread.currentThread().getName()
                    // v3.0/v3.1 ...
                    + ", WRITE: " + protocolVersion
                    + " " + InputRecord.contentName(contentType())
                    + ", length = " + length);
            }
        } else {
            dstBB.position(dstBB.limit());
        }

        int packetLength = dstBB.limit() - dstPos - headerSize;

        /*
         * Finish out the record header.
         */
        dstBB.put(dstPos, contentType());
        dstBB.put(dstPos + 1, protocolVersion.major);
        dstBB.put(dstPos + 2, protocolVersion.minor);
        dstBB.put(dstPos + 3, (byte)(packetLength >> 8));
        dstBB.put(dstPos + 4, (byte)packetLength);

        /*
         * Position was already set by encrypt() above.
         */
        dstBB.limit(dstLim);
    }
}
