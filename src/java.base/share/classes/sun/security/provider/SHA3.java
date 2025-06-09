/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.security.ProviderException;
import java.util.Arrays;
import java.util.Objects;

import jdk.internal.util.Preconditions;
import jdk.internal.vm.annotation.IntrinsicCandidate;

import static java.lang.Math.min;

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
public abstract class SHA3 extends DigestBase {

    private static final int WIDTH = 200; // in bytes, e.g. 1600 bits
    private static final int DM = 5; // dimension of state matrix

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

    // The starting byte combining the 2 or 4-bit domain separator and
    // leading bits of the 10*1 padding, see Table 6 in B.2 of FIPS PUB 202
    // for examples
    private final byte suffix;

    // the state matrix flattened into an array
    private long[] state = new long[DM*DM];

    // The byte offset in the state where the next squeeze() will start.
    // -1 indicates that either we are in the absorbing phase (only
    // update() calls were made so far) in an extendable-output function (XOF)
    // or the class was initialized as a hash.
    // The first squeeze() call (after a possibly empty sequence of update()
    // calls) will set it to 0 at its start.
    // When a squeeze() call uses up all available bytes from this state
    // and so a new keccak() call is made, squeezeOffset is reset to 0.
    protected int squeezeOffset = -1;

    static final VarHandle asLittleEndian
            = MethodHandles.byteArrayViewVarHandle(long[].class,
            ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();

    /**
     * Creates a new SHA-3 object.
     */
    private SHA3(String name, int digestLength, byte suffix, int c) {
        super(name, digestLength, (WIDTH - c));
        this.suffix = suffix;
    }

    private void implCompressCheck(byte[] b, int ofs) {
        Objects.requireNonNull(b);
        Preconditions.checkIndex(ofs + blockSize - 1, b.length, Preconditions.AIOOBE_FORMATTER);
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
        for (int i = 0; i < blockSize / 8; i++) {
            state[i] ^= (long) asLittleEndian.get(b, ofs);
            ofs += 8;
        }

        keccak();
    }

     void finishAbsorb() {
        int numOfPadding =
                setPaddingBytes(suffix, buffer, (int)(bytesProcessed % blockSize));
        if (numOfPadding < 1) {
            throw new ProviderException("Incorrect pad size: " + numOfPadding);
        }
        implCompress(buffer, 0);
    }

    /**
     * Return the digest. Subclasses do not need to reset() themselves,
     * DigestBase calls implReset() when necessary.
     */
    void implDigest(byte[] out, int ofs) {
        // Moving this allocation to the block where it is used causes a little
        // performance drop, that is why it is here.
        byte[] byteState = new byte[8];
        if (engineGetDigestLength() == 0) {
            // This is an XOF, so the digest() call is illegal.
            throw new ProviderException("Calling digest() is not allowed in an XOF");
        }

        finishAbsorb();

        int availableBytes = blockSize;
        int numBytes = engineGetDigestLength();

        while (numBytes > availableBytes) {
            for (int i = 0; i < availableBytes / 8; i++) {
                asLittleEndian.set(out, ofs, state[i]);
                ofs += 8;
            }
            numBytes -= availableBytes;
            keccak();
        }
        int numLongs = numBytes / 8;

        for (int i = 0; i < numLongs; i++) {
            asLittleEndian.set(out, ofs, state[i]);
            ofs += 8;
        }
        if (numBytes % 8 != 0) {
            asLittleEndian.set(byteState, 0, state[numLongs]);
            System.arraycopy(byteState, 0, out, ofs, numBytes % 8);
        }
    }

    void implSqueeze(byte[] output, int offset, int numBytes) {
        // Moving this allocation to the block where it is used causes a little
        // performance drop, that is why it is here.
        byte[] byteState = new byte[8];
        if (engineGetDigestLength() != 0) {
            // This is not an XOF, so the squeeze() call is illegal.
            throw new ProviderException("Squeezing is only allowed in XOF mode.");
        }

        if (squeezeOffset == -1) {
            finishAbsorb();
            squeezeOffset = 0;
        }

        int availableBytes = blockSize - squeezeOffset;

        while (numBytes > availableBytes) {
            int longOffset = squeezeOffset / 8;
            int bytesToCopy = 0;

            if (longOffset * 8 < squeezeOffset) {
                asLittleEndian.set(byteState, 0, state[longOffset]);
                longOffset++;
                bytesToCopy = longOffset * 8 - squeezeOffset;
                System.arraycopy(byteState, 8 - bytesToCopy,
                        output, offset, bytesToCopy);
                offset += bytesToCopy;
            }
            for (int i = longOffset; i < blockSize / 8; i++) {
                asLittleEndian.set(output, offset, state[i]);
                offset += 8;
            }
            keccak();
            squeezeOffset = 0;
            numBytes -= availableBytes;
            availableBytes = blockSize;
        }
        // now numBytes <= availableBytes
        int longOffset = squeezeOffset / 8;

        if (longOffset * 8 < squeezeOffset) {
            asLittleEndian.set(byteState, 0, state[longOffset]);
            int bytesToCopy = min((longOffset + 1) * 8 - squeezeOffset, numBytes);
            System.arraycopy(byteState, squeezeOffset - 8 * longOffset,
                    output, offset, bytesToCopy);
            longOffset++;
            numBytes -= bytesToCopy;
            offset += bytesToCopy;
            squeezeOffset += bytesToCopy;

            if (numBytes == 0) return;
        }

        int numLongs = numBytes / 8;

        for (int i = longOffset; i < longOffset + numLongs; i++) {
            asLittleEndian.set(output, offset, state[i]);
            offset += 8;
            numBytes -= 8;
            squeezeOffset += 8;
        }

        if (numBytes > 0) {
            asLittleEndian.set(byteState, 0, state[squeezeOffset / 8]);
            System.arraycopy(byteState, 0, output, offset, numBytes);
            squeezeOffset += numBytes;
        }
    }

    byte[] implSqueeze(int numBytes) {
        byte[] result = new byte[numBytes];
        implSqueeze(result, 0, numBytes);
        return result;
    }

    /**
     * Resets the internal state to start a new hash.
     */
    void implReset() {
        Arrays.fill(state, 0L);
        squeezeOffset = -1;
    }

    /**
     * Utility function for padding the specified data based on the
     * pad10*1 algorithm (section 5.1) and the 2-bit suffix "01" or 4-bit
     * suffix "1111" required for SHA-3 hash functions (section 6.1) and
     * extendable-output functions (section 6.1) respectively.
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
     * The function Keccak as defined in section 5.2 with
     * rate r = 1600 and capacity c.
     */
    private void keccak() {
        keccak(state);
    }

    public static void keccak(long[] stateArr) {
        long a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12;
        long a13, a14, a15, a16, a17, a18, a19, a20, a21, a22, a23, a24;
        // move data into local variables
        a0 = stateArr[0]; a1 = stateArr[1]; a2 = stateArr[2]; a3 = stateArr[3]; a4 = stateArr[4];
        a5 = stateArr[5]; a6 = stateArr[6]; a7 = stateArr[7]; a8 = stateArr[8]; a9 = stateArr[9];
        a10 = stateArr[10]; a11 = stateArr[11]; a12 = stateArr[12]; a13 = stateArr[13]; a14 = stateArr[14];
        a15 = stateArr[15]; a16 = stateArr[16]; a17 = stateArr[17]; a18 = stateArr[18]; a19 = stateArr[19];
        a20 = stateArr[20]; a21 = stateArr[21]; a22 = stateArr[22]; a23 = stateArr[23]; a24 = stateArr[24];

        // process the stateArr through step mappings
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

        stateArr[0] = a0; stateArr[1] = a1; stateArr[2] = a2; stateArr[3] = a3; stateArr[4] = a4;
        stateArr[5] = a5; stateArr[6] = a6; stateArr[7] = a7; stateArr[8] = a8; stateArr[9] = a9;
        stateArr[10] = a10; stateArr[11] = a11; stateArr[12] = a12; stateArr[13] = a13; stateArr[14] = a14;
        stateArr[15] = a15; stateArr[16] = a16; stateArr[17] = a17; stateArr[18] = a18; stateArr[19] = a19;
        stateArr[20] = a20; stateArr[21] = a21; stateArr[22] = a22; stateArr[23] = a23; stateArr[24] = a24;
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

    public abstract static class SHA3XOF extends SHA3 {
        public SHA3XOF(String name, int digestLength, byte offset, int c) {
            super(name, digestLength, offset, c);
        }
        public void update(byte in) {
            if (squeezeOffset != -1) {
                throw new ProviderException("update() after squeeze() is not allowed.");
            }
            engineUpdate(in);
        }
        public void update(byte[] in, int off, int len) {
            if (squeezeOffset != -1) {
                throw new ProviderException("update() after squeeze() is not allowed.");
            }
            engineUpdate(in, off, len);
        }

        public void update(byte[] in) {
            if (squeezeOffset != -1) {
                throw new ProviderException("update() after squeeze() is not allowed.");
            }
            engineUpdate(in, 0, in.length);
        }

        public byte[] digest() {
            return engineDigest();
        }

        public void squeeze(byte[] output, int offset, int numBytes) {
            implSqueeze(output, offset, numBytes);
        }
        public byte[] squeeze(int numBytes) {
            return implSqueeze(numBytes);
        }

        public void reset() {
            engineReset();
            // engineReset (final in DigestBase) skips implReset if there's
            // no update. This works for MessageDigest, since digest() always
            // resets. But for XOF, squeeze() may be called without update,
            // and still modifies state. So we always call implReset here
            // to ensure correct behavior.
            implReset();
        }
    }

    public static final class SHAKE128Hash extends SHA3 {
        public SHAKE128Hash() {
            super("SHAKE128-256", 32, (byte) 0x1F, 32);
        }
    }

    public static final class SHAKE256Hash extends SHA3 {
        public SHAKE256Hash() {
            super("SHAKE256-512", 64, (byte) 0x1F, 64);
        }
    }


    /*
     * The SHAKE128 extendable output function.
     */
    public static final class SHAKE128 extends SHA3XOF {
        // d is the required number of output bytes.
        // If this constructor is used with d > 0, the squeezing methods
        // will throw a ProviderException.
        public SHAKE128(int d) {
            super("SHAKE128", d, (byte) 0x1F, 32);
        }

        // If this constructor is used to get an instance of the class, then,
        // after the last update, one can get the generated bytes using the
        // squeezing methods.
        // Calling digest method will throw a ProviderException.
        public SHAKE128() {
            super("SHAKE128", 0, (byte) 0x1F, 32);
        }
    }

    /*
     * The SHAKE256 extendable output function.
     */
    public static final class SHAKE256 extends SHA3XOF {
        // d is the required number of output bytes.
        // If this constructor is used with d > 0, the squeezing methods will
        // throw a ProviderException.
        public SHAKE256(int d) {
            super("SHAKE256", d, (byte) 0x1F, 64);
        }

        // If this constructor is used to get an instance of the class, then,
        // after the last update, one can get the generated bytes using the
        // squeezing methods.
        // Calling a digest method will throw a ProviderException.
        public SHAKE256() {
            super("SHAKE256", 0, (byte) 0x1F, 64);
        }
    }

}
