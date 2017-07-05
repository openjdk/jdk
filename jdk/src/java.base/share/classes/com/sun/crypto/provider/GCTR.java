/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.IllegalBlockSizeException;
import static com.sun.crypto.provider.AESConstants.AES_BLOCK_SIZE;

/**
 * This class represents the GCTR function defined in NIST 800-38D
 * under section 6.5.  With a given cipher object and initial counter
 * block, a counter mode operation is performed.  Blocksize is limited
 * to 16 bytes.
 *
 * If any invariant is broken, failures can occur because the
 * AESCrypt.encryptBlock method can be intrinsified on the HotSpot VM
 * (see JDK-8067648 for details).
 *
 * The counter mode operations can be intrinsified and parallelized
 * by using CounterMode.implCrypt() if HotSpot VM supports it on the
 * architecture.
 *
 * <p>This function is used in the implementation of GCM mode.
 *
 * @since 1.8
 */
final class GCTR extends CounterMode {

    GCTR(SymmetricCipher cipher, byte[] initialCounterBlk) {
        super(cipher);
        if (initialCounterBlk.length != AES_BLOCK_SIZE) {
            throw new RuntimeException("length of initial counter block (" + initialCounterBlk.length +
                                       ") not equal to AES_BLOCK_SIZE (" + AES_BLOCK_SIZE + ")");
        }

        iv = initialCounterBlk;
        reset();
    }

    @Override
    String getFeedback() {
        return "GCTR";
    }

    // input must be multiples of 128-bit blocks when calling update
    int update(byte[] in, int inOfs, int inLen, byte[] out, int outOfs) {
        if (inLen - inOfs > in.length) {
            throw new RuntimeException("input length out of bound");
        }
        if (inLen < 0 || inLen % AES_BLOCK_SIZE != 0) {
            throw new RuntimeException("input length unsupported");
        }
        if (out.length - outOfs < inLen) {
            throw new RuntimeException("output buffer too small");
        }

        return encrypt(in, inOfs, inLen, out, outOfs);
    }

    // input can be arbitrary size when calling doFinal
    int doFinal(byte[] in, int inOfs, int inLen, byte[] out,
                          int outOfs) throws IllegalBlockSizeException {
        try {
            if (inLen < 0) {
                throw new IllegalBlockSizeException("Negative input size!");
            } else if (inLen > 0) {
                int lastBlockSize = inLen % AES_BLOCK_SIZE;
                int completeBlkLen = inLen - lastBlockSize;
                // process the complete blocks first
                update(in, inOfs, completeBlkLen, out, outOfs);
                if (lastBlockSize != 0) {
                    // do the last partial block
                    byte[] encryptedCntr = new byte[AES_BLOCK_SIZE];
                    embeddedCipher.encryptBlock(counter, 0, encryptedCntr, 0);
                    for (int n = 0; n < lastBlockSize; n++) {
                        out[outOfs + completeBlkLen + n] =
                            (byte) ((in[inOfs + completeBlkLen + n] ^
                                     encryptedCntr[n]));
                    }
                }
            }
        } finally {
            reset();
        }
        return inLen;
    }
}
