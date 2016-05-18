/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static sun.security.provider.ByteArrayAccess.*;
import java.nio.*;
import java.util.*;
import java.security.*;

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

    private byte[] state;

    /**
     * Creates a new SHA-3 object.
     */
    SHA3(String name, int digestLength) {
        super(name, digestLength, (WIDTH - (2 * digestLength)));
        implReset();
    }

    /**
     * Core compression function. Processes blockSize bytes at a time
     * and updates the state of this object.
     */
    void implCompress(byte[] b, int ofs) {
        for (int i = 0; i < buffer.length; i++) {
            state[i] ^= b[ofs++];
        }
        state = keccak(state);
    }

    /**
     * Return the digest. Subclasses do not need to reset() themselves,
     * DigestBase calls implReset() when necessary.
     */
    void implDigest(byte[] out, int ofs) {
        int numOfPadding =
            setPaddingBytes(buffer, (int)(bytesProcessed % buffer.length));
        if (numOfPadding < 1) {
            throw new ProviderException("Incorrect pad size: " + numOfPadding);
        }
        for (int i = 0; i < buffer.length; i++) {
            state[i] ^= buffer[i];
        }
        state = keccak(state);
        System.arraycopy(state, 0, out, ofs, engineGetDigestLength());
    }

    /**
     * Resets the internal state to start a new hash.
     */
    void implReset() {
        state = new byte[WIDTH];
    }

    /**
     * Utility function for circular shift the specified long
     * value to the left for n bits.
     */
    private static long circularShiftLeft(long lane, int n) {
        return ((lane << n) | (lane >>> (64 - n)));
    }

    /**
     * Utility function for padding the specified data based on the
     * pad10*1 algorithm (section 5.1) and the 2-bit suffix "01" required
     * for SHA-3 hash (section 6.1).
     */
    private static int setPaddingBytes(byte[] in, int len) {
        if (len != in.length) {
            // erase leftover values
            Arrays.fill(in, len, in.length, (byte)0);
            // directly store the padding bytes into the input
            // as the specified buffer is allocated w/ size = rateR
            in[len] |= (byte) 0x06;
            in[in.length - 1] |= (byte) 0x80;
        }
        return (in.length - len);
    }

    /**
     * Utility function for transforming the specified state from
     * the byte array format into array of lanes as defined in
     * section 3.1.2.
     */
    private static long[][] bytes2Lanes(byte[] s) {
        if (s.length != WIDTH) {
            throw new ProviderException("Error: incorrect input size " +
                s.length);
        }
        // The conversion traverses along x-axis before y-axis. So, y is the
        // first dimension and x is the second dimension.
        long[][] s2 = new long[DM][DM];
        int sOfs = 0;
        for (int y = 0; y < DM; y++, sOfs += 40) {
            b2lLittle(s, sOfs, s2[y], 0, 40);
        }
        return s2;
    }

    /**
     * Utility function for transforming the specified arrays of
     * lanes into a byte array as defined in section 3.1.3.
     */
    private static byte[] lanes2Bytes(long[][] m) {
        byte[] s = new byte[WIDTH];
        int sOfs = 0;
        // The conversion traverses along x-axis before y-axis. So, y is the
        // first dimension and x is the second dimension.
        for (int y = 0; y < DM; y++, sOfs += 40) {
            l2bLittle(m[y], 0, s, sOfs, 40);
        }
        return s;
    }

    /**
     * Step mapping Theta as defined in section 3.2.1 .
     */
    private static long[][] smTheta(long[][] a) {
        long[] c = new long[DM];
        for (int i = 0; i < DM; i++) {
            c[i] = a[0][i]^a[1][i]^a[2][i]^a[3][i]^a[4][i];
        }
        long[] d = new long[DM];
        for (int i = 0; i < DM; i++) {
            long c1 = c[(i + 4) % DM];
            // left shift and wrap the leftmost bit into the rightmost bit
            long c2 = circularShiftLeft(c[(i + 1) % DM], 1);
            d[i] = c1^c2;
        }
        for (int y = 0; y < DM; y++) {
            for (int x = 0; x < DM; x++) {
                a[y][x] ^= d[x];
            }
        }
        return a;
    }

    /**
     * Step mapping Rho as defined in section 3.2.2.
     */
    private static long[][] smRho(long[][] a) {
        long[][] a2 = new long[DM][DM];
        a2[0][0] = a[0][0];
        int xNext, yNext;
        for (int t = 0, x = 1, y = 0; t <= 23; t++, x = xNext, y = yNext) {
            int numberOfShift = ((t + 1)*(t + 2)/2) % 64;
            a2[y][x] = circularShiftLeft(a[y][x], numberOfShift);
            xNext = y;
            yNext = (2 * x + 3 * y) % DM;
        }
        return a2;
    }

    /**
     * Step mapping Pi as defined in section 3.2.3.
     */
    private static long[][] smPi(long[][] a) {
        long[][] a2 = new long[DM][DM];
        for (int y = 0; y < DM; y++) {
            for (int x = 0; x < DM; x++) {
                a2[y][x] = a[x][(x + 3 * y) % DM];
            }
        }
        return a2;
    }

    /**
     * Step mapping Chi as defined in section 3.2.4.
     */
    private static long[][] smChi(long[][] a) {
        long[][] a2 = new long[DM][DM];
        for (int y = 0; y < DM; y++) {
            for (int x = 0; x < DM; x++) {
                a2[y][x] = a[y][x] ^
                    ((a[y][(x + 1) % DM] ^ 0xFFFFFFFFFFFFFFFFL) &
                     a[y][(x + 2) % DM]);
            }
        }
        return a2;
    }

    /**
     * Step mapping Iota as defined in section 3.2.5.
     *
     * @return the processed state array
     * @param state the state array to be processed
     */
    private static long[][] smIota(long[][] a, int rndIndex) {
        a[0][0] ^= RC_CONSTANTS[rndIndex];
        return a;
    }

    /**
     * The function Keccak as defined in section 5.2 with
     * rate r = 1600 and capacity c = (digest length x 2).
     */
    private static byte[] keccak(byte[] state) {
        long[][] lanes = bytes2Lanes(state);
        for (int ir = 0; ir < NR; ir++) {
            lanes = smIota(smChi(smPi(smRho(smTheta(lanes)))), ir);
        }
        return lanes2Bytes(lanes);
    }

    public Object clone() throws CloneNotSupportedException {
        SHA3 copy = (SHA3) super.clone();
        copy.state = copy.state.clone();
        return copy;
    }

    /**
     * SHA3-224 implementation class.
     */
    public static final class SHA224 extends SHA3 {
        public SHA224() {
            super("SHA3-224", 28);
        }
    }

    /**
     * SHA3-256 implementation class.
     */
    public static final class SHA256 extends SHA3 {
        public SHA256() {
            super("SHA3-256", 32);
        }
    }

    /**
     * SHAs-384 implementation class.
     */
    public static final class SHA384 extends SHA3 {
        public SHA384() {
            super("SHA3-384", 48);
        }
    }

    /**
     * SHA3-512 implementation class.
     */
    public static final class SHA512 extends SHA3 {
        public SHA512() {
            super("SHA3-512", 64);
        }
    }
}
