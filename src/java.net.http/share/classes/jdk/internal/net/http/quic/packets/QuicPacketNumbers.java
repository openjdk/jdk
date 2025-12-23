/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.packets;

import java.nio.ByteBuffer;

/**
 * QUIC packet number encoding/decoding routines.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public class QuicPacketNumbers {

    /**
     * Returns the number of bytes needed to encode a packet number
     * given the full packet number and the largest ACK'd packet.
     *
     * @param fullPN       the full packet number
     * @param largestAcked the largest ACK'd packet, or -1 if none so far
     *
     * @throws IllegalArgumentException if number can't represented in 4 bytes
     * @return the number of bytes required to encode the packet
     */
    public static int computePacketNumberLength(long fullPN, long largestAcked) {

        long numUnAcked;

        if (largestAcked == -1) {
            numUnAcked = fullPN + 1;
        } else {
            numUnAcked = fullPN - largestAcked;
        }

        /*
         * log(n, 2) + 1; ceil(minBits / 8);
         *
         * value will never be non-positive, so don't need to worry about the
         * special cases.
         */
        assert numUnAcked > 0 : "numUnAcked %s < 0 (fullPN: %s, largestAcked: %s)"
                .formatted(numUnAcked, fullPN, largestAcked);
        int minBits = 64 - Long.numberOfLeadingZeros(numUnAcked) + 1;
        int numBytes = (minBits + 7) / 8;

        if (numBytes > 4) {
            throw new IllegalArgumentException(
                    "Encoded packet number needs %s bytes for pn=%s, ack=%s"
                    .formatted(numBytes, fullPN, largestAcked));
        }

        return numBytes;
    }

    /**
     * Encode the full packet number against the largest ACK'd packet.
     *
     * Follows the algorithm outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-sample-packet-number-encodi">
     *     RFC 9000. Appendix A.2</a>
     *
     * @param fullPN       the full packet number
     * @param largestAcked the largest ACK'd packet, or -1 if none so far
     *
     * @throws IllegalArgumentException if number can't be represented in 4 bytes
     * @return byte array containing fullPN
     */
    public static byte[] encodePacketNumber(
            long fullPN, long largestAcked) {

        // throws IAE if more than 4 bytes are needed
        int numBytes = computePacketNumberLength(fullPN, largestAcked);
        assert numBytes <= 4 : numBytes;
        return truncatePacketNumber(fullPN, numBytes);
    }

    /**
     * Truncate the full packet number to fill into {@code numBytes}.
     *
     * Follows the algorithm outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-sample-packet-number-encodi">
     *     RFC 9000, Appendix A.2</a>
     *
     * @apiNote
     * {@code numBytes} should have been computed using
     * {@link #computePacketNumberLength(long, long)}
     *
     * @param fullPN    the full packet number
     * @param numBytes  the number of bytes in which to encode
     *                  the packet number
     *
     * @throws IllegalArgumentException if numBytes is out of range
     * @return byte array containing fullPN
     */
    public static byte[] truncatePacketNumber(
            long fullPN, int numBytes) {

        if (numBytes <= 0 || numBytes > 4) {
            throw new IllegalArgumentException(
                    "Invalid packet number length: " + numBytes);
        }

        // Fill in the array.
        byte[] retval = new byte[numBytes];
        for (int i = numBytes - 1; i >= 0; i--) {
            retval[i] = (byte) (fullPN & 0xff);
            fullPN = fullPN >>> 8;
        }

        return retval;
    }

    /**
     * Decode the packet numbers against the largest ACK'd packet after header
     * protection has been removed.
     *
     * Follows the algorithm outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-sample-packet-number-decodi">
     *     RFC 9000, Appendix A.3</a>
     *
     * @param largestPN   the largest packet number that has been successfully
     *                    processed in the current packet number space
     * @param buf         a {@code ByteBuffer} containing the value of the
     *                    Packet Number field
     * @param pnNBytes    the number of <b>bytes</b> indicated by the Packet
     *                    Number Length field
     *
     * @throws java.nio.BufferUnderflowException if there is not enough data in the
     *                                  buffer
     * @return the decoded packet number
     */
    public static long decodePacketNumber(
            long largestPN, ByteBuffer buf, int pnNBytes) {

        assert pnNBytes >= 1 && pnNBytes <= 4
                : "decodePacketNumber: " + pnNBytes;

        long truncatedPN = 0;
        for (int i = 0; i < pnNBytes; i++) {
            truncatedPN = (truncatedPN << 8) | (buf.get() & 0xffL);
        }

        int pnNBits = pnNBytes * 8;

        long expectedPN = largestPN + 1L;
        assert expectedPN >= 0 : "expectedPN: " + expectedPN;
        long pnWin = 1L << pnNBits;
        long pnHWin = pnWin / 2L;
        long pnMask = pnWin - 1L;

        // The incoming packet number should be greater than
        // expectedPN - pn_HWin and less than or equal to
        // expectedPN + pn_HWin
        //
        // This means we cannot just strip the trailing bits from
        // expectedPN and add the truncatedPN because that might
        // yield a value outside the window.
        //
        // The following code calculates a candidate value and
        // makes sure it's within the packet number window.
        // Note the extra checks to prevent overflow and underflow.
        long candidatePN = (expectedPN & ~pnMask) | truncatedPN;

        if ((candidatePN <= (expectedPN - pnHWin))
                && (candidatePN < ((1L << 62) - pnWin))) {
            return candidatePN + pnWin;
        }

        if ((candidatePN - pnHWin > expectedPN)
                && (candidatePN >= pnWin)) {
            return candidatePN - pnWin;
        }
        return candidatePN;
    }
}
