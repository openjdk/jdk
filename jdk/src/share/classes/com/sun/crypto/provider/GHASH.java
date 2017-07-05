/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.security.*;
import static com.sun.crypto.provider.AESConstants.AES_BLOCK_SIZE;

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

    private static final byte P128 = (byte) 0xe1; //reduction polynomial

    private static boolean getBit(byte[] b, int pos) {
        int p = pos / 8;
        pos %= 8;
        int i = (b[p] >>> (7 - pos)) & 1;
        return i != 0;
    }

    private static void shift(byte[] b) {
        byte temp, temp2;
        temp2 = 0;
        for (int i = 0; i < b.length; i++) {
            temp = (byte) ((b[i] & 0x01) << 7);
            b[i] = (byte) ((b[i] & 0xff) >>> 1);
            b[i] = (byte) (b[i] | temp2);
            temp2 = temp;
        }
    }

    // Given block X and Y, returns the muliplication of X * Y
    private static byte[] blockMult(byte[] x, byte[] y) {
        if (x.length != AES_BLOCK_SIZE || y.length != AES_BLOCK_SIZE) {
            throw new RuntimeException("illegal input sizes");
        }
        byte[] z = new byte[AES_BLOCK_SIZE];
        byte[] v = y.clone();
        // calculate Z1-Z127 and V1-V127
        for (int i = 0; i < 127; i++) {
            // Zi+1 = Zi if bit i of x is 0
            if (getBit(x, i)) {
                for (int n = 0; n < z.length; n++) {
                    z[n] ^= v[n];
                }
            }
            boolean lastBitOfV = getBit(v, 127);
            shift(v);
            if (lastBitOfV) v[0] ^= P128;
        }
        // calculate Z128
        if (getBit(x, 127)) {
            for (int n = 0; n < z.length; n++) {
                z[n] ^= v[n];
            }
        }
        return z;
    }

    // hash subkey H; should not change after the object has been constructed
    private final byte[] subkeyH;

    // buffer for storing hash
    private byte[] state;

    // variables for save/restore calls
    private byte[] stateSave = null;

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
        this.subkeyH = subkeyH;
        this.state = new byte[AES_BLOCK_SIZE];
    }

    /**
     * Resets the GHASH object to its original state, i.e. blank w/
     * the same subkey H. Used after digest() is called and to re-use
     * this object for different data w/ the same H.
     */
    void reset() {
        Arrays.fill(state, (byte) 0);
    }

    /**
     * Save the current snapshot of this GHASH object.
     */
    void save() {
        stateSave = state.clone();
    }

    /**
     * Restores this object using the saved snapshot.
     */
    void restore() {
        state = stateSave;
    }

    private void processBlock(byte[] data, int ofs) {
        if (data.length - ofs < AES_BLOCK_SIZE) {
            throw new RuntimeException("need complete block");
        }
        for (int n = 0; n < state.length; n++) {
            state[n] ^= data[ofs + n];
        }
        state = blockMult(state, subkeyH);
    }

    void update(byte[] in) {
        update(in, 0, in.length);
    }

    void update(byte[] in, int inOfs, int inLen) {
        if (inLen - inOfs > in.length) {
            throw new RuntimeException("input length out of bound");
        }
        if (inLen % AES_BLOCK_SIZE != 0) {
            throw new RuntimeException("input length unsupported");
        }

        for (int i = inOfs; i < (inOfs + inLen); i += AES_BLOCK_SIZE) {
            processBlock(in, i);
        }
    }

    byte[] digest() {
        try {
            return state.clone();
        } finally {
            reset();
        }
    }
}
