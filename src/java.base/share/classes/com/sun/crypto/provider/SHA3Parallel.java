/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.IntrinsicCandidate;

import java.util.Arrays;

import static sun.security.provider.ByteArrayAccess.b2lLittle;
import static sun.security.provider.ByteArrayAccess.l2bLittle;

import static sun.security.provider.SHA3.keccak;

public class SHA3Parallel {
    private int blockSize = 0;
    private static final int WIDTH = 200; // in bytes, e.g. 1600 bits
    private static final int DM = 5; // dimension of lanesArr
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
    private byte[][] buffers;
    private long[][] lanesArr;
    private long[] fakeLanes = new long[DM * DM];
    private int nrPar;

    private SHA3Parallel(byte[][] buffers, int blockSize) {
        nrPar = buffers.length;
        this.buffers = buffers;
        this.blockSize = blockSize;
        lanesArr = new long[nrPar][];
        for (int i = 0; i < nrPar; i++) {
            lanesArr[i] = new long[DM * DM];
            b2lLittle(buffers[i], 0, lanesArr[i], 0, blockSize);
        }
    }

    public void reset(byte[][] buffers) {
        nrPar = buffers.length;
        this.buffers = buffers;
        boolean newSize = (nrPar > lanesArr.length);
        if (newSize) {
            lanesArr = new long[nrPar][];
        }
        for (int i = 0; i < nrPar; i++) {
            if (newSize) {
                lanesArr[i] = new long[DM * DM];
            } else {
                Arrays.fill(lanesArr[i], 0L);
            }
            b2lLittle(buffers[i], 0, lanesArr[i], 0, blockSize);
        }
    }

    public int squeezeBlock() {
        int retVal = parKeccak();
        for (int i = 0; i < nrPar; i++) {
            l2bLittle(lanesArr[i], 0, buffers[i], 0, blockSize);
        }
        return retVal;
    }

    private int parKeccak() {
        int inlined = 0;
        for (int i = 0; i < (nrPar + 1) / 2; i ++) {
            inlined = doubleKeccak(lanesArr[2 * i],
                    2 * i + 1 == nrPar ? fakeLanes : lanesArr[2 * i + 1]);
        }
        return inlined;
    }

    @IntrinsicCandidate
    private static int doubleKeccak(long[] lanes0, long[] lanes1) {
        doubleKeccakJava(lanes0, lanes1);
        return 1;
    }

    private static int doubleKeccakJava(long[] lanes0, long[] lanes1) {
        keccak(lanes0);
        keccak(lanes1);
        return 1;
    }

    public static final class Shake128Parallel extends SHA3Parallel {
        public Shake128Parallel(byte[][] buf) {
            super(buf, 168);
        }
    }

    public static final class Shake256Parallel extends SHA3Parallel {
        public Shake256Parallel(byte[][] buf) {
            super(buf, 136);
        }
    }
}
