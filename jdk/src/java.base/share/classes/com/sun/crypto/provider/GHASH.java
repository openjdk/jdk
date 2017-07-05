/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015 Red Hat, Inc.
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
/*
 * (C) Copyright IBM Corp. 2013
 */

package com.sun.crypto.provider;

import java.security.ProviderException;

/**
 * This class represents the GHASH function defined in NIST 800-38D
 * under section 6.4. It needs to be constructed w/ a hash subkey, i.e.
 * block H. Given input of 128-bit blocks, it will process and output
 * a 128-bit block.
 *
 * <p>This function is used in the implementation of GCM mode.
 *
 * @since 1.8
 */
final class GHASH {

    private static long getLong(byte[] buffer, int offset) {
        long result = 0;
        int end = offset + 8;
        for (int i = offset; i < end; ++i) {
            result = (result << 8) + (buffer[i] & 0xFF);
        }
        return result;
    }

    private static void putLong(byte[] buffer, int offset, long value) {
        int end = offset + 8;
        for (int i = end - 1; i >= offset; --i) {
            buffer[i] = (byte) value;
            value >>= 8;
        }
    }

    private static final int AES_BLOCK_SIZE = 16;

    // Multiplies state[0], state[1] by subkeyH[0], subkeyH[1].
    private static void blockMult(long[] st, long[] subH) {
        long Z0 = 0;
        long Z1 = 0;
        long V0 = subH[0];
        long V1 = subH[1];
        long X;

        // Separate loops for processing state[0] and state[1].
        X = st[0];
        for (int i = 0; i < 64; i++) {
            // Zi+1 = Zi if bit i of x is 0
            long mask = X >> 63;
            Z0 ^= V0 & mask;
            Z1 ^= V1 & mask;

            // Save mask for conditional reduction below.
            mask = (V1 << 63) >> 63;

            // V = rightshift(V)
            long carry = V0 & 1;
            V0 = V0 >>> 1;
            V1 = (V1 >>> 1) | (carry << 63);

            // Conditional reduction modulo P128.
            V0 ^= 0xe100000000000000L & mask;
            X <<= 1;
        }

        X = st[1];
        for (int i = 64; i < 127; i++) {
            // Zi+1 = Zi if bit i of x is 0
            long mask = X >> 63;
            Z0 ^= V0 & mask;
            Z1 ^= V1 & mask;

            // Save mask for conditional reduction below.
            mask = (V1 << 63) >> 63;

            // V = rightshift(V)
            long carry = V0 & 1;
            V0 = V0 >>> 1;
            V1 = (V1 >>> 1) | (carry << 63);

            // Conditional reduction.
            V0 ^= 0xe100000000000000L & mask;
            X <<= 1;
        }

        // calculate Z128
        long mask = X >> 63;
        Z0 ^= V0 & mask;
        Z1 ^= V1 & mask;

        // Save result.
        st[0] = Z0;
        st[1] = Z1;

    }

    /* subkeyH and state are stored in long[] for GHASH intrinsic use */

    // hash subkey H; should not change after the object has been constructed
    private final long[] subkeyH;

    // buffer for storing hash
    private final long[] state;

    // variables for save/restore calls
    private long stateSave0, stateSave1;

    /**
     * Initializes the cipher in the specified mode with the given key
     * and iv.
     *
     * @param subkeyH the hash subkey
     *
     * @exception ProviderException if the given key is inappropriate for
     * initializing this digest
     */
    GHASH(byte[] subkeyH) throws ProviderException {
        if ((subkeyH == null) || subkeyH.length != AES_BLOCK_SIZE) {
            throw new ProviderException("Internal error");
        }
        state = new long[2];
        this.subkeyH = new long[2];
        this.subkeyH[0] = getLong(subkeyH, 0);
        this.subkeyH[1] = getLong(subkeyH, 8);
    }

    /**
     * Resets the GHASH object to its original state, i.e. blank w/
     * the same subkey H. Used after digest() is called and to re-use
     * this object for different data w/ the same H.
     */
    void reset() {
        state[0] = 0;
        state[1] = 0;
    }

    /**
     * Save the current snapshot of this GHASH object.
     */
    void save() {
        stateSave0 = state[0];
        stateSave1 = state[1];
    }

    /**
     * Restores this object using the saved snapshot.
     */
    void restore() {
        state[0] = stateSave0;
        state[1] = stateSave1;
    }

    private static void processBlock(byte[] data, int ofs, long[] st, long[] subH) {
        st[0] ^= getLong(data, ofs);
        st[1] ^= getLong(data, ofs + 8);
        blockMult(st, subH);
    }

    void update(byte[] in) {
        update(in, 0, in.length);
    }

    void update(byte[] in, int inOfs, int inLen) {
        if (inLen == 0) {
            return;
        }
        ghashRangeCheck(in, inOfs, inLen, state, subkeyH);
        processBlocks(in, inOfs, inLen/AES_BLOCK_SIZE, state, subkeyH);
    }

    private static void ghashRangeCheck(byte[] in, int inOfs, int inLen, long[] st, long[] subH) {
        if (inLen < 0) {
            throw new RuntimeException("invalid input length: " + inLen);
        }
        if (inOfs < 0) {
            throw new RuntimeException("invalid offset: " + inOfs);
        }
        if (inLen > in.length - inOfs) {
            throw new RuntimeException("input length out of bound: " +
                                       inLen + " > " + (in.length - inOfs));
        }
        if (inLen % AES_BLOCK_SIZE != 0) {
            throw new RuntimeException("input length/block size mismatch: " +
                                       inLen);
        }

        // These two checks are for C2 checking
        if (st.length != 2) {
            throw new RuntimeException("internal state has invalid length: " +
                                       st.length);
        }
        if (subH.length != 2) {
            throw new RuntimeException("internal subkeyH has invalid length: " +
                                       subH.length);
        }
    }
    /*
     * This is an intrinsified method.  The method's argument list must match
     * the hotspot signature.  This method and methods called by it, cannot
     * throw exceptions or allocate arrays as it will breaking intrinsics
     */
    private static void processBlocks(byte[] data, int inOfs, int blocks, long[] st, long[] subH) {
        int offset = inOfs;
        while (blocks > 0) {
            processBlock(data, offset, st, subH);
            blocks--;
            offset += AES_BLOCK_SIZE;
        }
    }

    byte[] digest() {
        byte[] result = new byte[AES_BLOCK_SIZE];
        putLong(result, 0, state[0]);
        putLong(result, 8, state[1]);
        reset();
        return result;
    }
}
