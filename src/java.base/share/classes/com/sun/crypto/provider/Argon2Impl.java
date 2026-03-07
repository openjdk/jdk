/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.crypto.provider;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.security.InvalidAlgorithmParameterException;
import java.security.ProviderException;
import javax.crypto.spec.Argon2ParameterSpec;
import static javax.crypto.spec.Argon2ParameterSpec.Version;
import static sun.security.provider.ByteArrayAccess.*;

/**
 * This class implements the Password Hashing Algorithm Argon2 as specified
 * in <a href="https://datatracker.ietf.org/doc/html/rfc9106">RFC 9106</a>.
 *
 * @since 27
 */
public final class Argon2Impl {

    /**
     * Type of Argon2 algorithms
     */
    private enum Type {
        /**
         * Argon2d
         */
        ARGON2D(0),
        /**
         * Argon2i
         */
        ARGON2I(1),
        /**
         * Argon2id
         */
        ARGON2ID(2);

        int value;

        Type(int value) {
            this.value = value;
        }

        /**
         * {@return 0 for ARGON2D, 1 for ARGON2I, 2 for Argon2ID}
         */
        public int value() {
            return value;
        }
    };

    private static final class Argon2Instance {
        // Variable-length hash function H' built upon Blake2b as defined in
        // RFC 9106 sec 3.3
        private static byte[] vlHash(int outLen, byte[] in) {
            byte[] lenBytes = new byte[4];
            i2bLittle4(outLen, lenBytes, 0);
            byte[] out = new byte[outLen];
            if (outLen <= 64) {
                Blake2b bl = new Blake2b(outLen);
                bl.update(lenBytes);
                bl.update(in);
                bl.doFinal(out, 0);
            } else {
                byte[] v = new byte[64];
                Blake2b bl = new Blake2b(64);
                bl.update(lenBytes);
                bl.update(in);
                bl.doFinal(v, 0);

                int r = ((outLen + 31) >> 5) - 2;
                System.arraycopy(v, 0, out, 0, 32);
                int outOfs = 32;

                while (--r > 0) {
                    bl.update(v);
                    bl.doFinal(v, 0);
                    System.arraycopy(v, 0, out, outOfs, 32);
                    outOfs += 32;
                }
                int capacity = out.length - outOfs;
                bl = new Blake2b(capacity);
                bl.update(v);
                bl.doFinal(out, outOfs);
            }
            return out;
        }

        private static final long INT_MASK = 0x0FFFFFFFFL;

        // Modified Blake MixG function as defined in RFC 9106 figure 19.
        private static void mixGB(long[] v, int a, int b, int c, int d) {
            Objects.checkIndex(a, v.length);
            Objects.checkIndex(b, v.length);
            Objects.checkIndex(c, v.length);
            Objects.checkIndex(d, v.length);

            v[a] = Long.sum(Long.sum(v[a], v[b]),
                    (v[a] & INT_MASK) * (v[b] & INT_MASK)  << 1);
            v[d] = Long.rotateRight(v[d] ^ v[a], 32);
            v[c] = Long.sum(Long.sum(v[c], v[d]),
                    (v[c] & INT_MASK) * (v[d] & INT_MASK)  << 1);
            v[b] = Long.rotateRight(v[b] ^ v[c], 24);
            v[a] = Long.sum(Long.sum(v[a], v[b]),
                    (v[a] & INT_MASK) * (v[b] & INT_MASK)  << 1);
            v[d] = Long.rotateRight(v[d] ^ v[a], 16);
            v[c] = Long.sum(Long.sum(v[c], v[d]),
                    (v[c] & INT_MASK) * (v[d] & INT_MASK)  << 1);
            v[b] = Long.rotateRight(v[b] ^ v[c], 63);
        }

        // Permutation P as defined in RFC 9106 sec 3.6
        private static void permuteP(Block b, int ofs, int inc) {
            // populate V[0..15] with Block b
            long[] v = new long[16];
            for (int i = 0, sIdx = ofs; i < v.length; sIdx += inc) {
                v[i++] = b.value[sIdx];
                v[i++] = b.value[sIdx + 1];
            }
            mixGB(v, 0, 4, 8, 12);
            mixGB(v, 1, 5, 9, 13);
            mixGB(v, 2, 6, 10, 14);
            mixGB(v, 3, 7, 11, 15);
            mixGB(v, 0, 5, 10, 15);
            mixGB(v, 1, 6, 11, 12);
            mixGB(v, 2, 7, 8, 13);
            mixGB(v, 3, 4, 9, 14);

            // store V into Block b
            for (int i = 0, sIdx = ofs; i < v.length; sIdx += inc) {
                b.value[sIdx] = v[i++];
                b.value[sIdx + 1] = v[i++];
            }
        }

        // Compression function G as defined in RFC 9106 sec 3.5
        private static void compressG(Block a, Block b, Block dst,
                boolean xor) {
            Block blockR = Block.xor(a, b);
            Block tmp = (Block) blockR.clone();

            if (xor) {
                // tmp = r xor dst
                tmp.xor(dst);
            }

            // r.value = 128 longs (8-byte each) which is arranged into an
            // 8x8 array of elements whose length is 16 bytes

            // Apply permutation P on columns of 64-bit words: (0,1,...,15),
            // then (16,17,..31)... finally (112,113,...127)
            for (int i = 0; i < 8; i++) {
                permuteP(blockR, i << 4, 2);
            }

            // Apply permutation P on rows of 64-bit words:
            // (0,1,16,17,...112,113), then (2,3,18,19,...,114,115) ...,
            // finally (14,15,30,31,...,126,127)
            for (int i = 0; i < 8; i++) {
                permuteP(blockR, i << 1, 16);
            }

            Block.xor(dst, tmp, blockR);
        }

        static Block nextAddresses(Block inBlock) {
            Block addressBlock = new Block();
            inBlock.value[6]++;
            compressG(Block.ZERO_BLK, inBlock, addressBlock, false);
            compressG(Block.ZERO_BLK, addressBlock, addressBlock, false);
            return addressBlock;
        }

        private final Type type;
        private final int lanes;
        private final int passes;
        private final int segLen;
        private final int columns;
        private final int mem2;
        private final Block[][] b;

        Argon2Instance(Type type, int parallelism, int memory, int passes) {
            this.type = type;
            this.lanes = parallelism;
            this.segLen = memory / (parallelism << 2);
            this.columns = segLen << 2;
            this.mem2 = (parallelism << 2) * segLen;
            this.passes = passes;
            this.b = new Block[this.lanes][this.columns];
        }

        void fillFirstTwoColumns(byte[] h0Plus8Bytes) {
            // 3) Compute B[i][0] for i = [0...p-1]
            // B[i][0] = hash^(1024)(H_0 || LE32(0) || LE32(i))
            // no need to set LE32(0) as that should be the value
            for (int k = 0; k < lanes; k++) {
                i2bLittle4(k, h0Plus8Bytes, 68);
                b[k][0] = new Block(vlHash(ARGON2_BLOCK_SIZE,
                        h0Plus8Bytes));
            }

            // 4) Compute B[i][1] for i = [0...p-1]
            // B[i][1] = hash^(1024)(H_0 || LE32(1) || LE32(i))
            i2bLittle4(1, h0Plus8Bytes, ARGON2_PREHASH_DIGEST_LENGTH);
            for (int k = 0; k < lanes; k++) {
                i2bLittle4(k, h0Plus8Bytes, 68);
                b[k][1] = new Block(vlHash(ARGON2_BLOCK_SIZE,
                        h0Plus8Bytes));
            }
        }

        // calculate and returns the absolute column index z
        private int indexAlpha(Argon2Position pos, long j1, boolean sameLane) {
            /*
             * Pass 0:
             *      Same lane   : all already finished segments plus already
             * constructed blocks in this segment
             *      Other lanes : all already finished segments
             * Pass 1+:
             *      Same lane   : last 3 segments plus already constructed
             * blocks in this segment
             *      Other lanes : last 3 segments
             */
            int wSize = 0;

            if (pos.pass == 0 && pos.slice == 0) {
                // first slice; all blocks in current segment but the previous
                wSize = pos.index - 1;
            } else {
                wSize = (pos.pass == 0 ? pos.slice * this.segLen :
                         this.columns - this.segLen);
                if (sameLane) {
                    // add blocks in current segment but the previous
                    wSize += (pos.index - 1);
                } else {
                    wSize += (pos.index == 0 ? -1 : 0);
                }
            }

            long x = j1*j1 >>> 32;
            int y = (int) (x * wSize >>> 32);
            int zz = wSize - 1 - y;

            int startPosition = 0;
            if (pos.pass != 0 && pos.slice != 3) {
                // starts from the next segment if sliceNum = 0, 1, 2
                startPosition = (pos.slice + 1) * this.segLen;
            }
            int z = (startPosition + zz) % this.columns;
            return z;
        }

        private void fillSegment(Argon2Position pos) {
            Block inBlock = null;
            boolean independentAddr =
                (type == Type.ARGON2ID && (pos.pass == 0) && (pos.slice < 2)
                || type == Type.ARGON2I);
            if (independentAddr) {
                inBlock = new Block();
                inBlock.value[0] = pos.pass;
                inBlock.value[1] = pos.lane;
                inBlock.value[2] = pos.slice;
                inBlock.value[3] = this.mem2;
                inBlock.value[4] = this.passes;
                inBlock.value[5] = this.type.value();
            }
            int startingIdx = 0;
            Block addressBlock = null;

            if (pos.pass == 0 && pos.slice == 0) {
                // adjust startingIdx as the first two blocks are generated
                // in fillFirstTwoColumns() already
                startingIdx = 2;
                if (independentAddr) {
                    addressBlock = nextAddresses(inBlock);
                }
            }
            int currOfs = pos.slice * this.segLen + startingIdx;

            long pseudoRand;
            for (int i = startingIdx; i < this.segLen; i++, currOfs++) {
                int prevOfs = (currOfs == 0 ?  this.columns - 1 : currOfs - 1);

                // computing the index of the reference block
                // Taking pseudo-random value from the previous block
                if (independentAddr) {
                    if (i % ARGON2_ADDRESSES_IN_BLOCK == 0) {
                        addressBlock = nextAddresses(inBlock);
                    }
                    pseudoRand = addressBlock.value[i %
                            ARGON2_ADDRESSES_IN_BLOCK];
                } else {
                    pseudoRand = b[pos.lane][prevOfs].value[0];
                }

                int refLane = (pos.pass == 0) && (pos.slice == 0) ?
                        // can't reference other lanes yet
                        pos.lane :
                        (int) ((pseudoRand >>> 32) % this.lanes);

                // Computing the number of possible reference block within
                // the lane
                pos.index = i;
                int refIndex = indexAlpha(pos, pseudoRand & INT_MASK,
                        refLane == pos.lane);

                // Creating a new block
                Block prevBlock = b[pos.lane][prevOfs];
                Block refBlock = b[refLane][refIndex];
                if (pos.pass == 0) {
                    Block currBlock = new Block();
                    // first pass, no xor
                    compressG(prevBlock, refBlock, currBlock, false);
                    b[pos.lane][currOfs] = currBlock;
                } else {
                    Block currBlock = b[pos.lane][currOfs];
                    compressG(prevBlock, refBlock, currBlock, true);
                }
            }
        }

        void fillMemoryBlocks() {
            try {
                ExecutorService workers = Executors.newFixedThreadPool(lanes);

                // 5), 6) Compute B[i][j] for number of passes
                for (int r = 0; r < passes; r++) {
                    for (int s = 0; s < ARGON2_SYNC_POINTS; s++) {
                        CountDownLatch latch = new CountDownLatch(lanes);
                        for (int k = 0; k < lanes; k++) {
                            Argon2Position pos =  new Argon2Position(r, k, s);
                            workers.submit(() -> {
                                this.fillSegment(pos);
                                latch.countDown();
                            });
                        }
                        latch.await();
                    }
                }
                workers.shutdown();
                if (!workers.awaitTermination(2, TimeUnit.SECONDS)) {
                    workers.shutdownNow();
                }
            } catch (InterruptedException ie) {
                throw new ProviderException("Interrupted", ie);
            }
        }

        byte[] getFinalTag(int outLen) {
            // 7) Compute the final block C, i.e. xor of the last column
            Block c = b[0][this.columns - 1];
            byte[] cBytes = null;
            try {
                // xor the remaining blocks of the same column
                for (int i = 1; i < this.lanes; i++) {
                    c.xor(b[i][this.columns - 1]);
                }
                cBytes = c.getBytes();

                // 8) Compute the output tag
                return vlHash(outLen, cBytes);
            } finally {
                // erase all involved block here
                if (cBytes != null) {
                    Arrays.fill(cBytes, (byte) 0);
                }
                for (int i = 0; i < this.lanes; i++) {
                    b[i][this.columns - 1].erase();
                }
            }
        }
    }

    // the current position where the block is constructed right now.
    private static class Argon2Position {
        final int pass; // [0...t-1]
        final int lane; // [0...p-1]
        final int slice; // [0...3]
        int index; // [0...segLen-1]

        Argon2Position(int pass, int lane, int slice) {
            this.pass = pass;
            this.lane = lane;
            this.slice = slice;
            this.index = 0; // will be set later
        }
        public String toString() {
            return "pass = " + pass + ", lane = " + lane + ", slice = " +
                slice + ", index = " + index;
        }
    }

    // constants
    public static final int ARGON2_SYNC_POINTS = 4;
    public static final int ARGON2_BLOCK_SIZE = 1024;
    public static final int ARGON2_QWORDS_IN_BLOCK = 128; // QWORD=8-byte
    /* Number of pseudo-random values generated by one call to Blake2b in
       Argon2i to generate reference block positions */
    public static final int ARGON2_ADDRESSES_IN_BLOCK = 128;
    // Pre-hashing digest length and its extension
    public static final int ARGON2_PREHASH_DIGEST_LENGTH = 64;
    public static final int ARGON2_PREHASH_SEED_LENGTH   = 72;

    // implementation limits to deter DOS
    private static final long MEMORY_MAX;
    private static final int P_MAX;
    static {
        Runtime r = Runtime.getRuntime();
        // memory limit = 1/4 of maximum amount of the jvm memory limit
        MEMORY_MAX = r.maxMemory() >>> 12;
        // parallelism limit = 16 * number of cores
        P_MAX = r.availableProcessors() << 4;
    }

    private static class Block {
        static final Block ZERO_BLK = new Block();

        final long[] value;

        private Block() {
            value = new long[ARGON2_QWORDS_IN_BLOCK];
        }

        Block(byte byteVal) {
            long l = ((long)byteVal) << 56 | ((long)byteVal) << 48 |
                    ((long)byteVal) << 40 | ((long)byteVal) << 32 |
                    ((long)byteVal) << 24 | ((long)byteVal) << 16 |
                    ((long)byteVal) << 8 | (long)byteVal;
            value = new long[ARGON2_QWORDS_IN_BLOCK];
            Arrays.fill(value, l);
        }

        Block(long[] value) {
            Objects.requireNonNull(value, "Input array should not be null");
            if (value.length != ARGON2_QWORDS_IN_BLOCK) {
                throw new ProviderException("Wrong input array size: " +
                        value.length);
            }
            this.value = value.clone();
        }

        Block(byte[] bytes) {
            Objects.requireNonNull(bytes, "Input array should not be null");
            if (bytes.length != ARGON2_BLOCK_SIZE) {
                throw new ProviderException("Wrong input array size: " +
                        bytes.length);
            }
            this.value = new long[ARGON2_QWORDS_IN_BLOCK];
            b2lLittle(bytes, 0, value, 0, ARGON2_BLOCK_SIZE);
        }

        @Override
        public Object clone() {
            return new Block(value);
        }

        @Override
        public String toString() {
            String result = "";
            for (int i = 0; i < value.length; i++) {
                result += "[" + i + "]" + Long.toHexString(value[i]) + "\n";
            }
            return result;
        }

        byte[] getBytes() {
            byte[] out = new byte[ARGON2_BLOCK_SIZE];
            l2bLittle(value, 0, out, 0, ARGON2_BLOCK_SIZE);
            return out;
        }

        void erase() {
            Arrays.fill(value, 0L);
        }

        // xor this w/ 'other' and store the result in this
        void xor(Block other) {
            for (int i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
                value[i] = value[i] ^ other.value[i];
            }
        }

        // return a new block whose value equals to ('src1' xor 'src2')
        static Block xor(Block src1, Block src2) {
            Block dst = new Block();
            xor(dst, src1, src2);
            return dst;
        }

        // store ('src1' xor 'src2') into 'dst'
        static void xor(Block dst, Block src1, Block src2) {
            for (int i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
                dst.value[i] = src1.value[i] ^ src2.value[i];
            }
        }
    }

    private final Type type;

    private static byte[] initialHash(int parallelism, int tagLen, int memory,
            int iterations, Version v, Type type, byte[] msg, byte[] nonce,
            byte[] secret, byte[] ad) {
        // 1) Initial hashing
        // Hashing all inputs to generate the initialHash (H_0, 64-byte)
        // Allocate 72 bytes for storing initialHash(h0) since H_0 is
        // appended w/ additional 8 bytes for generating the first 2
        // blocks in step 3.
        byte[] h0Plus8Bytes = new byte[ARGON2_PREHASH_SEED_LENGTH];

        Blake2b bl = new Blake2b(ARGON2_PREHASH_DIGEST_LENGTH);
        byte[] in = new byte[24];
        i2bLittle4(parallelism, in, 0);
        i2bLittle4(tagLen, in, 4);
        i2bLittle4(memory, in, 8);
        i2bLittle4(iterations, in, 12);
        i2bLittle4(v.value(), in, 16);
        i2bLittle4(type.value(), in, 20);
        bl.update(in);
        byte[][] byteArrays = { msg, nonce, secret, ad };
        for (byte[] b : byteArrays) {
            int len = (b == null ? 0 : b.length);
            i2bLittle4(len, in, 0);
            bl.update(in, 0, 4);
            if (len > 0) {
                bl.update(b, 0, len);
            }
        }
        bl.doFinal(h0Plus8Bytes, 0);
        return h0Plus8Bytes;
    }

    public Argon2Impl(String algoUpper) {
        this.type = Type.valueOf(algoUpper);
    }

    private static int checkMax(int value, long max, String errMsg)
            throws InvalidAlgorithmParameterException {
        if (value > max) {
            throw new InvalidAlgorithmParameterException(String.format(errMsg,
                    value, max));
        }
        return value;
    }

    public byte[] derive(Argon2ParameterSpec spec)
            throws InvalidAlgorithmParameterException {
        if (spec.version() != Version.V13) {
            throw new InvalidAlgorithmParameterException
                    ("Unsupported version, SunJCE only supports V13, but got " +
                    spec.version());
        }
        int memory = checkMax(spec.memory(), MEMORY_MAX,
                "Memory size %d exceeds SunJCE's maximum %d");
        int parallelism = checkMax(spec.parallelism(), P_MAX,
                "Parallelism value %d exceeds SunJCE's maximum %d");
        int tagLen = spec.tagLen();
        int iterations = spec.iterations();
        byte[] msg = spec.msg();
        byte[] nonce = spec.nonce();
        byte[] secret = spec.secret();
        byte[] ad = spec.ad();

        byte[] h0Plus8Bytes = null;
        try {
            // 1) Establish initial hash H_0
            // Allocate 72 bytes for storing initialHash(h0) since H_0 is
            // appended w/ additional 8 bytes for generating the first 2
            // blocks in fillFirstTwoColumns(...).
            h0Plus8Bytes = initialHash(parallelism, tagLen, memory,
                iterations, Version.V13, type, msg, nonce, secret, ad);

            // 2) Allocate memory m' - stored inside Argon2Instance
            Argon2Instance instance = new Argon2Instance(type, parallelism,
                    memory, iterations);
            instance.fillFirstTwoColumns(h0Plus8Bytes);
            instance.fillMemoryBlocks();
            return instance.getFinalTag(tagLen);
        } finally {
            // erase initial hash
            Arrays.fill(h0Plus8Bytes, (byte)0);
        }
    }
}
