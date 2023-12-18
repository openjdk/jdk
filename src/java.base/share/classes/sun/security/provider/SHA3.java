/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.security.ProviderException;
import java.util.Arrays;
import java.util.Objects;

import jdk.internal.vm.annotation.IntrinsicCandidate;
import static sun.security.provider.ByteArrayAccess.b2lLittle;
import static sun.security.provider.ByteArrayAccess.l2bLittle;

/**
 * This class implements the Secure Hash Algorithm SHA-3 developed by
 * the National Institute of Standards and Technology along with the
 * National Security Agency as defined in FIPS PUB 202.
 *
 * <p>It implements java.security.MessageDigestSpi, and can be used
 * through Java Cryptography Architecture (JCA), as a pluggable
 * MessageDigest implementation.
 *
 * @since       9
 * @author      Valerie Peng
 */
abstract class SHA3 extends DigestBase {

    private static final int WIDTH = 200; // in bytes, e.g. 1600 bits
    private static final int DM = 5; // dimension of lanes

    private static final int NR = 24; // number of rounds

    // precomputed round constants needed by the step mapping Iota
    private static final long[] RC_CONSTANTS = {
        0x01L, 0x8082L, 0x800000000000808aL,
        0x8000000080008000L, 0x808bL, 0x80000001L,
        0x8000000080008081L, 0x8000000000008009L, 0x8aL,
        0x88L, 0x80008009L, 0x8000000aL,
        0x8000808bL, 0x800000000000008bL, 0x8000000000008089L,
        0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
        0x800aL, 0x800000008000000aL, 0x8000000080008081L,
        0x8000000000008080L, 0x80000001L, 0x8000000080008008L,
    };

    private final byte suffix;
    private byte[] state = new byte[WIDTH];
    private long[] lanes = new long[DM*DM];

    /**
     * Creates a new SHA-3 object.
     */
    SHA3(String name, int digestLength, byte suffix, int c) {
        super(name, digestLength, (WIDTH - c));
        this.suffix = suffix;
    }

    private void implCompressCheck(byte[] b, int ofs) {
        Objects.requireNonNull(b);
    }

    /**
     * Core compression function. Processes blockSize bytes at a time
     * and updates the state of this object.
     */
    void implCompress(byte[] b, int ofs) {
        implCompressCheck(b, ofs);
        implCompress0(b, ofs);
    }

    @IntrinsicCandidate
    private void implCompress0(byte[] b, int ofs) {
       for (int i = 0; i < buffer.length; i++) {
           state[i] ^= b[ofs++];
       }
       keccak();
    }

    /**
     * Return the digest. Subclasses do not need to reset() themselves,
     * DigestBase calls implReset() when necessary.
     */
    void implDigest(byte[] out, int ofs) {
        int numOfPadding =
            setPaddingBytes(suffix, buffer, (int)(bytesProcessed % buffer.length));
        if (numOfPadding < 1) {
            throw new ProviderException("Incorrect pad size: " + numOfPadding);
        }
        implCompress(buffer, 0);
        int availableBytes = buffer.length;
        int numBytes = engineGetDigestLength();
        while (numBytes > availableBytes) {
            System.arraycopy(state, 0, out, ofs, availableBytes);
            numBytes -= availableBytes;
            ofs += availableBytes;
            keccak();
        }
        System.arraycopy(state, 0, out, ofs, numBytes);
    }

    /**
     * Resets the internal state to start a new hash.
     */
    void implReset() {
        Arrays.fill(state, (byte)0);
        Arrays.fill(lanes, 0L);
    }

    /**
     * Utility function for padding the specified data based on the
     * pad10*1 algorithm (section 5.1) and the 2-bit suffix "01" required
     * for SHA-3 hash (section 6.1).
     */
    private static int setPaddingBytes(byte suffix, byte[] in, int len) {
        if (len != in.length) {
            // erase leftover values
            Arrays.fill(in, len, in.length, (byte)0);
            // directly store the padding bytes into the input
            // as the specified buffer is allocated w/ size = rateR
            in[len] |= suffix;
            in[in.length - 1] |= (byte) 0x80;
        }
        return (in.length - len);
    }

    /**
     * Utility function for transforming the specified byte array 's'
     * into array of lanes 'm' as defined in section 3.1.2.
     */
    private static void bytes2Lanes(byte[] s, long[] m) {
        int sOfs = 0;
        // Conversion traverses along x-axis before y-axis
        for (int y = 0; y < DM; y++, sOfs += 40) {
            b2lLittle(s, sOfs, m, DM*y, 40);
        }
    }

    /**
     * Utility function for transforming the specified array of
     * lanes 'm' into a byte array 's' as defined in section 3.1.3.
     */
    private static void lanes2Bytes(long[] m, byte[] s) {
        int sOfs = 0;
        // Conversion traverses along x-axis before y-axis
        for (int y = 0; y < DM; y++, sOfs += 40) {
            l2bLittle(m, DM*y, s, sOfs, 40);
        }
    }

    /**
     * The function Keccak as defined in section 5.2 with
     * rate r = 1600 and capacity c.
     */
    private void keccak() {
        // convert the 200-byte state into 25 lanes
        bytes2Lanes(state, lanes);

        long a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12;
        long a13, a14, a15, a16, a17, a18, a19, a20, a21, a22, a23, a24;
        // move data into local variables
        a0 = lanes[0]; a1 = lanes[1]; a2 = lanes[2]; a3 = lanes[3]; a4 = lanes[4];
        a5 = lanes[5]; a6 = lanes[6]; a7 = lanes[7]; a8 = lanes[8]; a9 = lanes[9];
        a10 = lanes[10]; a11 = lanes[11]; a12 = lanes[12]; a13 = lanes[13]; a14 = lanes[14];
        a15 = lanes[15]; a16 = lanes[16]; a17 = lanes[17]; a18 = lanes[18]; a19 = lanes[19];
        a20 = lanes[20]; a21 = lanes[21]; a22 = lanes[22]; a23 = lanes[23]; a24 = lanes[24];

        // process the lanes through step mappings
        for (int ir = 0; ir < NR; ir++) {
            // Step mapping Theta as defined in section 3.2.1.
            long c0 = a0^a5^a10^a15^a20;
            long c1 = a1^a6^a11^a16^a21;
            long c2 = a2^a7^a12^a17^a22;
            long c3 = a3^a8^a13^a18^a23;
            long c4 = a4^a9^a14^a19^a24;
            long d0 = c4 ^ Long.rotateLeft(c1, 1);
            long d1 = c0 ^ Long.rotateLeft(c2, 1);
            long d2 = c1 ^ Long.rotateLeft(c3, 1);
            long d3 = c2 ^ Long.rotateLeft(c4, 1);
            long d4 = c3 ^ Long.rotateLeft(c0, 1);
            a0  ^= d0; a1  ^= d1; a2  ^= d2; a3  ^= d3; a4  ^= d4;
            a5  ^= d0; a6  ^= d1; a7  ^= d2; a8  ^= d3; a9  ^= d4;
            a10 ^= d0; a11 ^= d1; a12 ^= d2; a13 ^= d3; a14 ^= d4;
            a15 ^= d0; a16 ^= d1; a17 ^= d2; a18 ^= d3; a19 ^= d4;
            a20 ^= d0; a21 ^= d1; a22 ^= d2; a23 ^= d3; a24 ^= d4;

            /*
             * Merged Step mapping Rho (section 3.2.2) and Pi (section 3.2.3).
             * for performance. Optimization is achieved by precalculating
             * shift constants for the following loop
             *   int xNext, yNext;
             *   for (int t = 0, x = 1, y = 0; t <= 23; t++, x = xNext, y = yNext) {
             *        int numberOfShift = ((t + 1)*(t + 2)/2) % 64;
             *        a[y][x] = Long.rotateLeft(a[y][x], numberOfShift);
             *        xNext = y;
             *        yNext = (2 * x + 3 * y) % DM;
             *   }
             * and with inplace permutation.
             */
            long ay = Long.rotateLeft(a10, 3);
            a10 = Long.rotateLeft(a1, 1);
            a1 = Long.rotateLeft(a6, 44);
            a6 = Long.rotateLeft(a9, 20);
            a9 = Long.rotateLeft(a22, 61);
            a22 = Long.rotateLeft(a14, 39);
            a14 = Long.rotateLeft(a20, 18);
            a20 = Long.rotateLeft(a2, 62);
            a2 = Long.rotateLeft(a12, 43);
            a12 = Long.rotateLeft(a13, 25);
            a13 = Long.rotateLeft(a19, 8);
            a19 = Long.rotateLeft(a23, 56);
            a23 = Long.rotateLeft(a15, 41);
            a15 = Long.rotateLeft(a4, 27);
            a4 = Long.rotateLeft(a24, 14);
            a24 = Long.rotateLeft(a21, 2);
            a21 = Long.rotateLeft(a8, 55);
            a8 = Long.rotateLeft(a16, 45);
            a16 = Long.rotateLeft(a5, 36);
            a5 = Long.rotateLeft(a3, 28);
            a3 = Long.rotateLeft(a18, 21);
            a18 = Long.rotateLeft(a17, 15);
            a17 = Long.rotateLeft(a11, 10);
            a11 = Long.rotateLeft(a7, 6);
            a7 = ay;

            // Step mapping Chi as defined in section 3.2.4.
            long tmp0 = a0;
            long tmp1 = a1;
            long tmp2 = a2;
            long tmp3 = a3;
            long tmp4 = a4;
            a0 = tmp0 ^ ((~tmp1) & tmp2);
            a1 = tmp1 ^ ((~tmp2) & tmp3);
            a2 = tmp2 ^ ((~tmp3) & tmp4);
            a3 = tmp3 ^ ((~tmp4) & tmp0);
            a4 = tmp4 ^ ((~tmp0) & tmp1);

            tmp0 = a5; tmp1 = a6; tmp2 = a7; tmp3 = a8; tmp4 = a9;
            a5 = tmp0 ^ ((~tmp1) & tmp2);
            a6 = tmp1 ^ ((~tmp2) & tmp3);
            a7 = tmp2 ^ ((~tmp3) & tmp4);
            a8 = tmp3 ^ ((~tmp4) & tmp0);
            a9 = tmp4 ^ ((~tmp0) & tmp1);

            tmp0 = a10; tmp1 = a11; tmp2 = a12; tmp3 = a13; tmp4 = a14;
            a10 = tmp0 ^ ((~tmp1) & tmp2);
            a11 = tmp1 ^ ((~tmp2) & tmp3);
            a12 = tmp2 ^ ((~tmp3) & tmp4);
            a13 = tmp3 ^ ((~tmp4) & tmp0);
            a14 = tmp4 ^ ((~tmp0) & tmp1);

            tmp0 = a15; tmp1 = a16; tmp2 = a17; tmp3 = a18; tmp4 = a19;
            a15 = tmp0 ^ ((~tmp1) & tmp2);
            a16 = tmp1 ^ ((~tmp2) & tmp3);
            a17 = tmp2 ^ ((~tmp3) & tmp4);
            a18 = tmp3 ^ ((~tmp4) & tmp0);
            a19 = tmp4 ^ ((~tmp0) & tmp1);

            tmp0 = a20; tmp1 = a21; tmp2 = a22; tmp3 = a23; tmp4 = a24;
            a20 = tmp0 ^ ((~tmp1) & tmp2);
            a21 = tmp1 ^ ((~tmp2) & tmp3);
            a22 = tmp2 ^ ((~tmp3) & tmp4);
            a23 = tmp3 ^ ((~tmp4) & tmp0);
            a24 = tmp4 ^ ((~tmp0) & tmp1);

            // Step mapping Iota as defined in section 3.2.5.
            a0 ^= RC_CONSTANTS[ir];
        }

        lanes[0] = a0; lanes[1] = a1; lanes[2] = a2; lanes[3] = a3; lanes[4] = a4;
        lanes[5] = a5; lanes[6] = a6; lanes[7] = a7; lanes[8] = a8; lanes[9] = a9;
        lanes[10] = a10; lanes[11] = a11; lanes[12] = a12; lanes[13] = a13; lanes[14] = a14;
        lanes[15] = a15; lanes[16] = a16; lanes[17] = a17; lanes[18] = a18; lanes[19] = a19;
        lanes[20] = a20; lanes[21] = a21; lanes[22] = a22; lanes[23] = a23; lanes[24] = a24;

        // convert the resulting 25 lanes back into 200-byte state
        lanes2Bytes(lanes, state);
    }

    public Object clone() throws CloneNotSupportedException {
        SHA3 copy = (SHA3) super.clone();
        copy.state = copy.state.clone();
        copy.lanes = new long[DM*DM];
        return copy;
    }

    /**
     * SHA3-224 implementation class.
     */
    public static final class SHA224 extends SHA3 {
        public SHA224() {
            super("SHA3-224", 28, (byte)0x06, 56);
        }
    }

    /**
     * SHA3-256 implementation class.
     */
    public static final class SHA256 extends SHA3 {
        public SHA256() {
            super("SHA3-256", 32, (byte)0x06, 64);
        }
    }

    /**
     * SHAs-384 implementation class.
     */
    public static final class SHA384 extends SHA3 {
        public SHA384() {
            super("SHA3-384", 48, (byte)0x06, 96);
        }
    }

    /**
     * SHA3-512 implementation class.
     */
    public static final class SHA512 extends SHA3 {
        public SHA512() {
            super("SHA3-512", 64, (byte)0x06, 128);
        }
    }
}
