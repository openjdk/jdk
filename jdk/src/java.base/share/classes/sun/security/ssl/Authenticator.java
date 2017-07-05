/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

/**
 * This class represents an SSL/TLS/DTLS message authentication token,
 * which encapsulates a sequence number and ensures that attempts to
 * delete or reorder messages can be detected.
 *
 * Each connection state contains a sequence number, which is maintained
 * separately for read and write states.
 *
 * For SSL/TLS protocols, the sequence number MUST be set to zero
 * whenever a connection state is made the active state.
 *
 * DTLS uses an explicit sequence number, rather than an implicit one.
 * Sequence numbers are maintained separately for each epoch, with
 * each sequence number initially being 0 for each epoch.  The sequence
 * number used to compute the DTLS MAC is the 64-bit value formed by
 * concatenating the epoch and the sequence number.
 *
 * Sequence numbers do not wrap.  If an implementation would need to wrap
 * a sequence number, it must renegotiate instead.  A sequence number is
 * incremented after each record: specifically, the first record transmitted
 * under a particular connection state MUST use sequence number 0.
 */
class Authenticator {

    // byte array containing the additional authentication information for
    // each record
    private final byte[] block;

    // the block size of SSL v3.0:
    // sequence number + record type + + record length
    private static final int BLOCK_SIZE_SSL = 8 + 1 + 2;

    // the block size of TLS v1.0 and later:
    // sequence number + record type + protocol version + record length
    private static final int BLOCK_SIZE_TLS = 8 + 1 + 2 + 2;

    // the block size of DTLS v1.0 and later:
    // epoch + sequence number + record type + protocol version + record length
    private static final int BLOCK_SIZE_DTLS = 2 + 6 + 1 + 2 + 2;

    private final boolean isDTLS;

    /**
     * Default construct, no message authentication token is initialized.
     *
     * Note that this construct can only be called for null MAC
     */
    protected Authenticator(boolean isDTLS) {
        if (isDTLS) {
            // For DTLS protocols, plaintexts use explicit epoch and
            // sequence number in each record.  The first 8 byte of
            // the block is initialized for null MAC so that the
            // epoch and sequence number can be acquired to generate
            // plaintext records.
            block = new byte[8];
        } else {
            block = new byte[0];
        }

        this.isDTLS = isDTLS;
    }

    /**
     * Constructs the message authentication token for the specified
     * SSL/TLS protocol.
     */
    Authenticator(ProtocolVersion protocolVersion) {
        if (protocolVersion.isDTLSProtocol()) {
            block = new byte[BLOCK_SIZE_DTLS];
            block[9] = protocolVersion.major;
            block[10] = protocolVersion.minor;

            this.isDTLS = true;
        } else if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            block = new byte[BLOCK_SIZE_TLS];
            block[9] = protocolVersion.major;
            block[10] = protocolVersion.minor;

            this.isDTLS = false;
        } else {
            block = new byte[BLOCK_SIZE_SSL];

            this.isDTLS = false;
        }
    }

    /**
     * Checks whether the sequence number is close to wrap.
     *
     * Sequence numbers are of type uint64 and may not exceed 2^64-1.
     * Sequence numbers do not wrap. When the sequence number is near
     * to wrap, we need to close the connection immediately.
     *
     * @return true if the sequence number is close to wrap
     */
    final boolean seqNumOverflow() {
        /*
         * Conservatively, we don't allow more records to be generated
         * when there are only 2^8 sequence numbers left.
         */
        if (isDTLS) {
            return (block.length != 0 &&
                // no epoch bytes, block[0] and block[1]
                block[2] == (byte)0xFF && block[3] == (byte)0xFF &&
                block[4] == (byte)0xFF && block[5] == (byte)0xFF &&
                block[6] == (byte)0xFF);
        } else {
            return (block.length != 0 &&
                block[0] == (byte)0xFF && block[1] == (byte)0xFF &&
                block[2] == (byte)0xFF && block[3] == (byte)0xFF &&
                block[4] == (byte)0xFF && block[5] == (byte)0xFF &&
                block[6] == (byte)0xFF);
        }
    }

    /**
     * Checks whether the sequence number close to renew.
     *
     * Sequence numbers are of type uint64 and may not exceed 2^64-1.
     * Sequence numbers do not wrap.  If a TLS
     * implementation would need to wrap a sequence number, it must
     * renegotiate instead.
     *
     * @return true if the sequence number is huge enough to renew
     */
    final boolean seqNumIsHuge() {
        /*
         * Conservatively, we should ask for renegotiation when there are
         * only 2^32 sequence numbers left.
         */
        if (isDTLS) {
            return (block.length != 0 &&
                // no epoch bytes, block[0] and block[1]
                block[2] == (byte)0xFF && block[3] == (byte)0xFF);
        } else {
            return (block.length != 0 &&
                block[0] == (byte)0xFF && block[1] == (byte)0xFF &&
                block[2] == (byte)0xFF && block[3] == (byte)0xFF);
        }
    }

    /**
     * Gets the current sequence number, including the epoch number for
     * DTLS protocols.
     *
     * @return the byte array of the current sequence number
     */
    final byte[] sequenceNumber() {
        return Arrays.copyOf(block, 8);
    }

    /**
     * Sets the epoch number (only apply to DTLS protocols).
     */
    final void setEpochNumber(int epoch) {
        if (!isDTLS) {
            throw new RuntimeException(
                "Epoch numbers apply to DTLS protocols only");
        }

        block[0] = (byte)((epoch >> 8) & 0xFF);
        block[1] = (byte)(epoch & 0xFF);
    }

    /**
     * Increase the sequence number.
     */
    final void increaseSequenceNumber() {
        /*
         * The sequence number in the block array is a 64-bit
         * number stored in big-endian format.
         */
        int k = 7;
        while ((k >= 0) && (++block[k] == 0)) {
            k--;
        }
    }

    /**
     * Acquires the current message authentication information with the
     * specified record type and fragment length, and then increases the
     * sequence number.
     *
     * @param  type the record type
     * @param  length the fragment of the record
     * @param  sequence the explicit sequence number of the record
     *
     * @return the byte array of the current message authentication information
     */
    final byte[] acquireAuthenticationBytes(
            byte type, int length, byte[] sequence) {

        byte[] copy = block.clone();
        if (sequence != null) {
            if (sequence.length != 8) {
                throw new RuntimeException(
                        "Insufficient explicit sequence number bytes");
            }

            System.arraycopy(sequence, 0, copy, 0, sequence.length);
        }   // Otherwise, use the implicit sequence number.

        if (block.length != 0) {
            copy[8] = type;

            copy[copy.length - 2] = (byte)(length >> 8);
            copy[copy.length - 1] = (byte)(length);

            if (sequence == null || sequence.length != 0) {
                // Increase the implicit sequence number in the block array.
                increaseSequenceNumber();
            }
        }

        return copy;
    }

    static final long toLong(byte[] recordEnS) {
        if (recordEnS != null && recordEnS.length == 8) {
            return ((recordEnS[0] & 0xFFL) << 56) |
                   ((recordEnS[1] & 0xFFL) << 48) |
                   ((recordEnS[2] & 0xFFL) << 40) |
                   ((recordEnS[3] & 0xFFL) << 32) |
                   ((recordEnS[4] & 0xFFL) << 24) |
                   ((recordEnS[5] & 0xFFL) << 16) |
                   ((recordEnS[6] & 0xFFL) <<  8) |
                    (recordEnS[7] & 0xFFL);
        }

        return -1L;
    }
}
