/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package sun.security.ssl;

import java.io.*;
import java.nio.*;

import javax.net.ssl.SSLException;
import sun.misc.HexDumpEncoder;


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

    public void flush() throws IOException {
        finishedMsg = false;
    }

    boolean isFinishedMsg() {
        return finishedMsg;
    }


    /**
     * Calculate the MAC value, storing the result either in
     * the internal buffer, or at the end of the destination
     * ByteBuffer.
     * <P>
     * We assume that the higher levels have assured us enough
     * room, otherwise we'll indirectly throw a
     * BufferOverFlowException runtime exception.
     *
     * position should equal limit, and points to the next
     * free spot.
     */
    private void addMAC(MAC signer, ByteBuffer bb)
            throws IOException {

        if (signer.MAClen() != 0) {
            byte[] hash = signer.compute(contentType(), bb);

            /*
             * position was advanced to limit in compute above.
             *
             * Mark next area as writable (above layers should have
             * established that we have plenty of room), then write
             * out the hash.
             */
            bb.limit(bb.limit() + hash.length);
            bb.put(hash);
        }
    }

    /*
     * Encrypt a ByteBuffer.
     *
     * We assume that the higher levels have assured us enough
     * room for the encryption (plus padding), otherwise we'll
     * indirectly throw a BufferOverFlowException runtime exception.
     *
     * position and limit will be the same, and points to the
     * next free spot.
     */
    void encrypt(CipherBox box, ByteBuffer bb) {
        box.encrypt(bb);
    }

    /*
     * Override the actual write below.  We do things this way to be
     * consistent with InputRecord.  InputRecord may try to write out
     * data to the peer, and *then* throw an Exception.  This forces
     * data to be generated/output before the exception is ever
     * generated.
     */
    void writeBuffer(OutputStream s, byte [] buf, int off, int len)
            throws IOException {
        /*
         * Copy data out of buffer, it's ready to go.
         */
        ByteBuffer netBB = (ByteBuffer)
            ByteBuffer.allocate(len).put(buf, 0, len).flip();
        writer.putOutboundData(netBB);
    }

    /*
     * Main method for writing non-application data.
     * We MAC/encrypt, then send down for processing.
     */
    void write(MAC writeMAC, CipherBox writeCipher) throws IOException {
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
            addMAC(writeMAC);
            encrypt(writeCipher);
            write((OutputStream)null);  // send down for processing
        }
        return;
    }

    /**
     * Main wrap/write driver.
     */
    void write(EngineArgs ea, MAC writeMAC, CipherBox writeCipher)
            throws IOException {
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
        if (writeMAC == MAC.NULL) {
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
        int length = Math.min(ea.getAppRemaining(), maxDataSize);
        if (length == 0) {
            return;
        }

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
        int dstData = dstPos + headerSize;
        dstBB.position(dstData);

        ea.gather(length);

        /*
         * "flip" but skip over header again, add MAC & encrypt
         * addMAC will expand the limit to reflect the new
         * data.
         */
        dstBB.limit(dstBB.position());
        dstBB.position(dstData);
        addMAC(writeMAC, dstBB);

        /*
         * Encrypt may pad, so again the limit may have changed.
         */
        dstBB.limit(dstBB.position());
        dstBB.position(dstData);
        encrypt(writeCipher, dstBB);

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

        int packetLength = dstBB.limit() - dstData;

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

        return;
    }
}
