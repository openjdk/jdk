/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.security.InvalidAlgorithmParameterException;
import java.util.Arrays;

import static sun.security.provider.ByteArrayAccess.b2lLittle;
import static sun.security.provider.ByteArrayAccess.l2bLittle;

import static sun.security.provider.SHA3.keccak;

/*
 * This class is for making it possible that NRPAR (= 2) (rather restricted)
 * SHAKE computations execute in parallel.
 * The restrictions are:
 *  1. The messages processed should be such that the absorb phase should
 * execute a single keccak() call and the byte arrays passed to the constructor
 * (or reset() method) of this class should be the message padded with the
 * appropriate padding described in
 * https://nvlpubs.nist.gov/nistpubs/fips/nist.fips.202.pdf.
 *  2. The only available way for extracting data is the squeeze() method
 * that extracts exactly 1 block of data of each computation, delivering it
 * in the arrays that were passed to the class in the constructor (or the
 * reset() call).
 */
public class SHA3Parallel {
    private int blockSize = 0;
    private static final int DM = 5; // dimension of lanesArr
    private byte[][] buffers;
    private long[][] lanesArr;
    private static final int NRPAR = 2;

    private SHA3Parallel(byte[][] buffers, int blockSize) throws InvalidAlgorithmParameterException {
        if ((buffers.length != NRPAR) || (buffers[0].length < blockSize)) {
            throw new InvalidAlgorithmParameterException("Bad buffersize.");
        }
        this.buffers = buffers;
        this.blockSize = blockSize;
        lanesArr = new long[NRPAR][];
        for (int i = 0; i < NRPAR; i++) {
            lanesArr[i] = new long[DM * DM];
            b2lLittle(buffers[i], 0, lanesArr[i], 0, blockSize);
        }
    }

    public void reset(byte[][] buffers) throws InvalidAlgorithmParameterException {
        if ((buffers.length != NRPAR) || (buffers[0].length < blockSize)) {
            throw new InvalidAlgorithmParameterException("Bad buffersize.");
        }
        this.buffers = buffers;
        for (int i = 0; i < NRPAR; i++) {
            Arrays.fill(lanesArr[i], 0L);
            b2lLittle(buffers[i], 0, lanesArr[i], 0, blockSize);
        }
    }

    public int squeezeBlock() {
        int retVal = doubleKeccak(lanesArr[0], lanesArr[1]);
        for (int i = 0; i < NRPAR; i++) {
            l2bLittle(lanesArr[i], 0, buffers[i], 0, blockSize);
        }
        return retVal;
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
        public Shake128Parallel(byte[][] buf) throws InvalidAlgorithmParameterException {
            super(buf, 168);
        }
    }
}
