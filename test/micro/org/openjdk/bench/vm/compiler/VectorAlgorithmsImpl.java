/*
 *  Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.vm.compiler;

import java.util.Arrays;
import java.util.Random;
import jdk.incubator.vector.*;

/**
 * The code below is supposed to be an exact copy of:
 *   test/hotspot/jtreg/compiler/vectorization/VectorAlgorithmsImpl.java
 */
public class VectorAlgorithmsImpl {
    private static final VectorSpecies<Integer> SPECIES_I    = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> SPECIES_I512 = IntVector.SPECIES_512;
    private static final VectorSpecies<Integer> SPECIES_I256 = IntVector.SPECIES_256;
    private static final VectorSpecies<Byte> SPECIES_B       = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Byte> SPECIES_B64     = ByteVector.SPECIES_64;
    private static final VectorSpecies<Float> SPECIES_F      = FloatVector.SPECIES_PREFERRED;

    // This class stores the input and output arrays.
    // The constructor sets up all the data.
    //
    // IMPORTANT:
    //   If you want to use some array but do NOT modify it: just use it.
    //   If you want to use it and DO want to modify it: clone it. This
    //   ensures that each test gets a separate copy, and that when we
    //   capture the modified arrays they are different for every method
    //   and run.
    //   An alternative to cloning is to use different return arrays for
    //   different implementations of the same group, e.g. rI1, rI2, ...
    //
    public static class Data {
        public int[] aI;
        public int[] rI1;
        public int[] rI2;
        public int[] rI3;
        public int[] rI4;
        public int[] eI;
        // The test has to use the same index into eI for all implementations. But in the
        // benchmark, we'd like to use random indices, so we use the index to advance through
        // the array.
        public int eI_idx = 0;

        public float[] aF;
        public float[] bF;

        public byte[] aB;
        public byte[] strB;
        public byte[] rB1;
        public byte[] rB2;
        public byte[] rB3;

        public int[] oopsX4;
        public int[] memX4;

        public Data(int size, int seed, int numX4Objects) {
            Random random = new Random(seed);

            // int: one input array and multiple output arrays so different implementations can
            // store their results to different arrays.
            aI = new int[size];
            rI1 = new int[size];
            rI2 = new int[size];
            rI3 = new int[size];
            rI4 = new int[size];
            Arrays.setAll(aI, i -> random.nextInt());

            // Populate with some random values from aI, and some totally random values.
            eI = new int[0x10000];
            for (int i = 0; i < eI.length; i++) {
                eI[i] = (random.nextInt(10) == 0) ? random.nextInt() : aI[random.nextInt(size)];
            }

            // X4 oop setup.
            // oopsX4 holds "addresses" (i.e. indices), that point to the 16-byte objects in memX4.
            oopsX4 = new int[size];
            memX4 = new int[numX4Objects * 4];
            for (int i = 0; i < size; i++) {
                // assign either a zero=null, or assign a random oop.
                oopsX4[i] = (random.nextInt(10) == 0) ? 0 : random.nextInt(numX4Objects) * 4;
            }
            // Just fill the whole array with random values.
            // The relevant field is only at every "4 * i + 3" though.
            memX4 = new int[4 * numX4Objects];
            for (int i = 0; i < memX4.length; i++) {
                memX4[i] = random.nextInt();
            }

            // float inputs. To avoid rounding issues, only use small integers.
            aF = new float[size];
            bF = new float[size];
            for (int i = 0; i < size; i++) {
                aF[i] = random.nextInt(32) - 16;
                bF[i] = random.nextInt(32) - 16;
            }

            // byte: just random data.
            aB = new byte[size];
            strB = new byte[size];
            rB1 = new byte[size];
            rB2 = new byte[size];
            rB3 = new byte[size];
            random.nextBytes(aB);
            random.nextBytes(strB); // TODO: special data!
        }
    }

    public static Object fillI_loop(int[] r) {
        for (int i = 0; i < r.length; i++) {
            r[i] = 42;
        }
        return r;
    }

    public static Object fillI_Arrays(int[] r) {
        Arrays.fill(r, 42);
        return r;
    }

    public static Object fillI_VectorAPI(int[] r) {
        var v = IntVector.broadcast(SPECIES_I, 42);
        int i = 0;
        for (; i < SPECIES_I.loopBound(r.length); i += SPECIES_I.length()) {
            v.intoArray(r, i);
        }
        for (; i < r.length; i++) {
            r[i] = 42;
        }
        return r;
    }

    public static Object iotaI_loop(int[] r) {
        for (int i = 0; i < r.length; i++) {
            r[i] = i;
        }
        return r;
    }

    public static Object iotaI_VectorAPI(int[] r) {
        var iota = IntVector.broadcast(SPECIES_I, 0).addIndex(1);
        int i = 0;
        for (; i < SPECIES_I.loopBound(r.length); i += SPECIES_I.length()) {
            iota.intoArray(r, i);
            iota = iota.add(SPECIES_I.length());
        }
        for (; i < r.length; i++) {
            r[i] = i;
        }
        return r;
    }

    public static Object copyI_loop(int[] a, int[] r) {
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i];
        }
        return r;
    }

    public static Object copyI_System_arraycopy(int[] a, int[] r) {
        System.arraycopy(a, 0, r, 0, a.length);
        return r;
    }

    public static Object copyI_VectorAPI(int[] a, int[] r) {
        int i = 0;
        for (; i < SPECIES_I.loopBound(r.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            v.intoArray(r, i);
        }
        for (; i < r.length; i++) {
            r[i] = a[i];
        }
        return r;
    }

    public static Object mapI_loop(int[] a, int[] r) {
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i] * 42;
        }
        return r;
    }

    public static Object mapI_VectorAPI(int[] a, int[] r) {
        int i = 0;
        for (; i < SPECIES_I.loopBound(r.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            v = v.mul(42);
            v.intoArray(r, i);
        }
        for (; i < r.length; i++) {
            r[i] = a[i] * 42;
        }
        return r;
    }

    public static int reduceAddI_loop(int[] a) {
        int sum = 0;
        for (int i = 0; i < a.length; i++) {
            // Relying on simple reduction loop should vectorize since JDK26.
            sum += a[i];
        }
        return sum;
    }

    public static int reduceAddI_reassociate(int[] a) {
        int sum = 0;
        int i;
        for (i = 0; i < a.length - 3; i += 4) {
            // Unroll 4x, reassociate inside.
            sum += a[i] + a[i + 1] + a[i + 2] + a[i + 3];
        }
        for (; i < a.length; i++) {
            // Tail
            sum += a[i];
        }
        return sum;
    }

    public static int reduceAddI_VectorAPI_naive(int[] a) {
        var sum = 0;
        int i;
        for (i = 0; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            // reduceLanes in loop is better than scalar performance, but still
            // relatively slow.
            sum += v.reduceLanes(VectorOperators.ADD);
        }
        for (; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }

    public static int reduceAddI_VectorAPI_reduction_after_loop(int[] a) {
        var acc = IntVector.broadcast(SPECIES_I, 0);
        int i;
        for (i = 0; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            // Element-wide addition into a vector of partial sums is much faster.
            // Now, we only need to do a reduceLanes after the loop.
            // This works because int-addition is associative and commutative.
            acc = acc.add(v);
        }
        int sum = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }

    public static float dotProductF_loop(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static float dotProductF_VectorAPI_naive(float[] a, float[] b) {
        float sum = 0;
        int i;
        for (i = 0; i < SPECIES_F.loopBound(a.length); i += SPECIES_F.length()) {
            var va = FloatVector.fromArray(SPECIES_F, a, i);
            var vb = FloatVector.fromArray(SPECIES_F, b, i);
            sum += va.mul(vb).reduceLanes(VectorOperators.ADD);
        }
        for (; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static float dotProductF_VectorAPI_reduction_after_loop(float[] a, float[] b) {
        var sums = FloatVector.broadcast(SPECIES_F, 0.0f);
        int i;
        for (i = 0; i < SPECIES_F.loopBound(a.length); i += SPECIES_F.length()) {
            var va = FloatVector.fromArray(SPECIES_F, a, i);
            var vb = FloatVector.fromArray(SPECIES_F, b, i);
            sums = sums.add(va.mul(vb));
        }
        float sum = sums.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static int hashCodeB_loop(byte[] a) {
        int h = 1;
        for (int i = 0; i < a.length; i++) {
            h = 31 * h + a[i];
        }
        return h;
    }

    public static int hashCodeB_Arrays(byte[] a) {
        return Arrays.hashCode(a);
    }

    // Simplified intrinsic code from C2_MacroAssembler::arrays_hashcode in c2_MacroAssembler_x86.cpp
    //
    // Ideas that may help understand the code:
    //   h(i) = 31 * h(i-1) + a[i]
    // "unroll" by factor of L=8:
    //   h(i+8) = h(i) * 31^8 + a[i+1] * 31^7 + a[i+2] * 31^6 + ... + a[i+8] * 1
    //            -----------   ------------------------------------------------
    //            scalar        vector: notice the powers of 31 in reverse
    //
    // We notice that we can load a[i+1 .. i+8], then element-wise multiply with
    // the vector of reversed powers-of-31, and then do reduceLanes(ADD).
    // But we can do even better: By looking at multiple such 8-unrolled iterations.
    // Instead of applying the "next" factor of "31^8" to the reduced scalar, we can
    // already apply it element-wise. That allows us to move the reduction out
    // of the loop.
    //
    // Note: the intrinsic additionally unrolls the loop by a factor of 4,
    //       but we want to keep thins simple for demonstration purposes.
    //
    private static int[] REVERSE_POWERS_OF_31 = new int[9];
    static {
        int p = 1;
        for (int i = REVERSE_POWERS_OF_31.length - 1; i >= 0; i--) {
            REVERSE_POWERS_OF_31[i] = p;
            p *= 31;
        }
    }
    public static int hashCodeB_VectorAPI_v1(byte[] a) {
        int result = 1; // initialValue
        var vresult = IntVector.zero(SPECIES_I256);
        int next = REVERSE_POWERS_OF_31[0]; // 31^L
        var vcoef = IntVector.fromArray(SPECIES_I256, REVERSE_POWERS_OF_31, 1); // powers of 2 in reverse
        int i;
        for (i = 0; i < SPECIES_B64.loopBound(a.length); i += SPECIES_B64.length()) {
            // scalar part: result *= 31^L
            result *= next;
            // vector part: element-wise apply the next factor and add in the new values.
            var vb = ByteVector.fromArray(SPECIES_B64, a, i);
            var vi = vb.castShape(SPECIES_I256, 0);
            vresult = vresult.mul(next).add(vi);
        }
        // reduce the partial hashes in the elements, using the reverse list of powers of 2.
        result += vresult.mul(vcoef).reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    // This second approach follows the idea from this blog post by Otmar Ertl:
    // https://www.dynatrace.com/news/blog/java-arrays-hashcode-byte-efficiency-techniques/
    //
    // I simplified the algorithm a little, so that it is a bit closer
    // to the solution "v1" above.
    //
    // The major issue with "v1" is that we cannot load a full vector of bytes,
    // because of the cast to ints. So we can only fill 1/4 of the maximal
    // vector size. The trick here is to do an unrolling of factor 4, from:
    //   h(i) = 31 * h(i-1) + a[i]
    // to:
    //   h(i+4) = h(i) * 31^4 + a[i + 1] * 31^3
    //                        + a[i + 2] * 31^2
    //                        + a[i + 3] * 31^1
    //                        + a[i + 4] * 31^0
    // The goal is now to compute this value for 4 bytes within a 4 byte
    // lane of the vector. One concern is that we start with byte values,
    // but need to do int-multiplication with powers of 31. If we instead
    // did a byte-multiplication, we could get overflows that we would not
    // have had in the int-multiplication.
    // One trick that helps with chaning the size of the lanes from byte
    // to short to int is doing all operations with unsigned integers. That
    // way, we can zero-extend instead of sign-bit extend. The first step
    // is thus to convert the bytes into unsigned values. Since byte is in
    // range [-128..128), doing "a[i+j] + 128" makes it a positive value,
    // allowing for unsigned multiplication.
    // h(i+4) = h(i) * 31^4 +   a[i + 1]              * 31^3
    //                      +   a[i + 2]              * 31^2
    //                      +   a[i + 3]              * 31^1
    //                      +   a[i + 4]              * 31^0
    //        = h(i) * 31^4 +  (a[i + 1] + 128 - 128) * 31^3
    //                      +  (a[i + 2] + 128 - 128) * 31^2
    //                      +  (a[i + 3] + 128 - 128) * 31^1
    //                      +  (a[i + 4] + 128 - 128) * 31^0
    //        = h(i) * 31^4 +  (a[i + 1] + 128      ) * 31^3
    //                      +  (a[i + 2] + 128      ) * 31^2
    //                      +  (a[i + 3] + 128      ) * 31^1
    //                      +  (a[i + 4] + 128      ) * 31^0
    //                      +  -128 * (31^3 + 31^2 + 31^1 + 1)
    //        = h(i) * 31^4 + ((a[i + 1] + 128) * 31
    //                      +  (a[i + 2] + 128      ) * 31^2
    //                      + ((a[i + 3] + 128) * 31
    //                      +  (a[i + 4] + 128      )
    //                      +  -128 * (31^3 + 31^2 + 31^1 + 1)
    //
    // Getting from the signed a[i] value to unsigned with +128, we can
    // just xor with 0x80=128. Any numbers there in range [-128..0) are
    // now in range [0..128). And any numbers that were in range [0..128)
    // are now in unsigned range [128..255). What a neat trick!
    //
    // We then apply a byte->short transition where we crunch 2 bytes
    // into one short, applying a multiplication with 31 to one of the
    // two bytes. This multiplication cannot overflow in a short.
    // then we apply a short->int transition where we crunch 2 shorts
    // into one int, applying a multiplication with 31^2 to one of the
    // two shorts. This multiplication cannot overflow in an int.
    //
    public static int hashCodeB_VectorAPI_v2(byte[] a) {
        return HashCodeB_VectorAPI_V2.compute(a);
    }

    private static class HashCodeB_VectorAPI_V2 {
        private static final int L = Math.min(ByteVector.SPECIES_PREFERRED.length(),
                                              IntVector.SPECIES_PREFERRED.length() * 4);
        private static final VectorShape SHAPE = VectorShape.forBitSize(8 * L);
        private static final VectorSpecies<Byte>    SPECIES_B = SHAPE.withLanes(byte.class);
        private static final VectorSpecies<Integer> SPECIES_I = SHAPE.withLanes(int.class);

        private static int[] REVERSE_POWERS_OF_31_STEP_4 = new int[L / 4 + 1];
        static {
            int p = 1;
            int step = 31 * 31 * 31 * 31; // step by 4
            for (int i = REVERSE_POWERS_OF_31_STEP_4.length - 1; i >= 0; i--) {
                REVERSE_POWERS_OF_31_STEP_4[i] = p;
                p *= step;
            }
        }

        public static int compute(byte[] a) {
            int result = 1; // initialValue
            int next = REVERSE_POWERS_OF_31_STEP_4[0]; // 31^L
            var vcoef = IntVector.fromArray(SPECIES_I, REVERSE_POWERS_OF_31_STEP_4, 1); // W
            var vresult = IntVector.zero(SPECIES_I);
            int i;
            for (i = 0; i < SPECIES_B.loopBound(a.length); i += SPECIES_B.length()) {
                var vb = ByteVector.fromArray(SPECIES_B, a, i);
                // Add 128 to each byte.
                var vs = vb.lanewise(VectorOperators.XOR, (byte)0x80)
                           .reinterpretAsShorts();
                // Each short lane contains 2 bytes, crunch them.
                var vi = vs.and((short)0xff) // lower byte
                           .mul((short)31)
                           .add(vs.lanewise(VectorOperators.LSHR, 8)) // upper byte
                           .reinterpretAsInts();
                // Each int contains 2 shorts, crunch them.
                var v  = vi.and(0xffff) // lower short
                           .mul(31 * 31)
                           .add(vi.lanewise(VectorOperators.LSHR, 16)); // upper short
                // Add the correction for the 128 additions above.
                v = v.add(-128 * (31*31*31 + 31*31 + 31 + 1));
                // Every element of v now contains a crunched int-package of 4 bytes.
                result *= next;
                vresult = vresult.mul(next).add(v);
            }
            result += vresult.mul(vcoef).reduceLanes(VectorOperators.ADD);
            for (; i < a.length; i++) {
                result = 31 * result + a[i];
            }
            return result;
        }
    }

    public static Object scanAddI_loop(int[] a, int[] r) {
        int sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
            r[i] = sum;
        }
        return r;
    }

    public static Object scanAddI_loop_reassociate(int[] a, int[] r) {
        int sum = 0;
        int i = 0;
        for (; i < a.length - 3; i += 4) {
            // We cut the latency by a factor of 4, but increase the number of additions.
            int old_sum = sum;
            int v0 = a[i + 0];
            int v1 = a[i + 1];
            int v2 = a[i + 2];
            int v3 = a[i + 3];
            int v01 = v0 + v1;
            int v23 = v2 + v3;
            int v0123 = v01 + v23;
            sum += v0123;
            r[i + 0] = old_sum + v0;
            r[i + 1] = old_sum + v01;
            r[i + 2] = old_sum + v01 + v2;
            r[i + 3] = old_sum + v0123;
        }
        for (; i < a.length; i++) {
            sum += a[i];
            r[i] = sum;
        }
        return r;
    }

    public static Object scanAddI_VectorAPI_permute_add(int[] a, int[] r) {
        // Using Naive Parallel Algorithm: Hills and Steele
        int sum = 0;
        int xx = 0; // masked later anyway
        var shf1 = VectorShuffle.fromArray(SPECIES_I512, new int[]{xx,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14}, 0);
        var shf2 = VectorShuffle.fromArray(SPECIES_I512, new int[]{xx, xx,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13}, 0);
        var shf3 = VectorShuffle.fromArray(SPECIES_I512, new int[]{xx, xx, xx, xx,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11}, 0);
        var shf4 = VectorShuffle.fromArray(SPECIES_I512, new int[]{xx, xx, xx, xx, xx, xx, xx, xx,  0,  1,  2,  3,  4,  5,  6,  7}, 0);
        var mask1 = VectorMask.fromLong(SPECIES_I512, 0b1111111111111110);
        var mask2 = VectorMask.fromLong(SPECIES_I512, 0b1111111111111100);
        var mask3 = VectorMask.fromLong(SPECIES_I512, 0b1111111111110000);
        var mask4 = VectorMask.fromLong(SPECIES_I512, 0b1111111100000000);
        int i = 0;
        for (; i < SPECIES_I512.loopBound(a.length); i += SPECIES_I512.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I512, a, i);
            v = v.add(v.rearrange(shf1), mask1);
            v = v.add(v.rearrange(shf2), mask2);
            v = v.add(v.rearrange(shf3), mask3);
            v = v.add(v.rearrange(shf4), mask4);
            v = v.add(sum);
            v.intoArray(r, i);
            sum = v.lane(SPECIES_I512.length() - 1);
        }
        for (; i < a.length; i++) {
            sum += a[i];
            r[i] = sum;
        }
        return r;
    }

    public static int findMinIndexI_loop(int[] a) {
        int min = a[0];
        int index = 0;
        for (int i = 1; i < a.length; i++) {
            int ai = a[i];
            if (ai < min) {
                min = ai;
                index = i;
            }
        }
        return index;
    }

    public static int findMinIndexI_VectorAPI(int[] a) {
        // Main approach: have partial results in mins and idxs.
        var mins = IntVector.broadcast(SPECIES_I, a[0]);
        var idxs = IntVector.broadcast(SPECIES_I, 0);
        var iota = IntVector.broadcast(SPECIES_I, 0).addIndex(1);
        int i = 0;
        for (; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            var mask = v.compare(VectorOperators.LT, mins);
            mins = mins.blend(v, mask);
            idxs = idxs.blend(iota, mask);
            iota = iota.add(SPECIES_I.length());
        }
        // Reduce the vectors down
        int min = mins.reduceLanes(VectorOperators.MIN);
        var not_min_mask = mins.compare(VectorOperators.NE, min);
        int index = idxs.blend(a.length, not_min_mask).reduceLanes(VectorOperators.MIN);
        // Tail loop
        for (; i < a.length; i++) {
            int ai = a[i];
            if (ai < min) {
                min = ai;
                index = i;
            }
        }
        return index;
    }

    public static int findI_loop(int[] a, int e) {
        for (int i = 0; i < a.length; i++) {
            int ai = a[i];
            if (ai == e) {
                return i;
            }
        }
        return -1;
    }

    public static int findI_VectorAPI(int[] a, int e) {
        var es = IntVector.broadcast(SPECIES_I, e);
        int i = 0;
        for (; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            var mask = v.compare(VectorOperators.EQ, es);
            if (mask.anyTrue()) {
                return i + mask.firstTrue();
            }
        }
        for (; i < a.length; i++) {
            int ai = a[i];
            if (ai == e) {
                return i;
            }
        }
        return -1;
    }

    public static Object reverseI_loop(int[] a, int[] r) {
        for (int i = 0; i < a.length; i++) {
            r[a.length - i - 1] = a[i];
        }
        return r;
    }

    private static final VectorShuffle<Integer> REVERSE_SHUFFLE_I = SPECIES_I.iotaShuffle(SPECIES_I.length()-1, -1, true);

    public static Object reverseI_VectorAPI(int[] a, int[] r) {
        int i = 0;
        for (; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            v = v.rearrange(REVERSE_SHUFFLE_I);
            v.intoArray(r, r.length - SPECIES_I.length() - i);
        }
        for (; i < a.length; i++) {
            r[a.length - i - 1] = a[i];
        }
        return r;
    }

    public static Object filterI_loop(int[] a, int[] r, int threshold) {
        int j = 0;
        for (int i = 0; i < a.length; i++) {
            int ai = a[i];
            if (ai >= threshold) {
                r[j++] = ai;
            }
        }
        // Just force the resulting length onto the same array.
        r[r.length - 1] = j;
        return r;
    }

    public static Object filterI_VectorAPI(int[] a, int[] r, int threshold) {
        var thresholds = IntVector.broadcast(SPECIES_I, threshold);
        int j = 0;
        int i = 0;
        for (; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            var mask = v.compare(VectorOperators.GE, thresholds);
            v = v.compress(mask);
            int trueCount = mask.trueCount();
            var prefixMask = mask.compress();
            v.intoArray(r, j, prefixMask);
            j += trueCount;
        }

        for (; i < a.length; i++) {
            int ai = a[i];
            if (ai >= threshold) {
                r[j++] = ai;
            }
        }
        // Just force the resulting length onto the same array.
        r[r.length - 1] = j;
        return r;
    }

    // X4: ints simulate 4-byte oops.
    // oops: if non-zero (= non-null), every entry simulates a 4-byte oop, pointing into mem.
    // mem: an int array that simulates the memory.
    //
    // Task: Find all non-null oops, and dereference them, get the relevant field.
    //       Objects have 16 bytes, and the relevant field is at bytes 12-16.
    //       That maps to 4 ints, and the relevant field is the 4th element of 4.
    //       Sum up all the field values.
    public static int reduceAddIFieldsX4_loop(int[] oops, int[] mem) {
        int sum = 0;
        for (int i = 0; i < oops.length; i++) {
            int oop = oops[i];
            if (oop != 0) {
                int fieldValue = mem[oop + 3]; // oop+12
                sum += fieldValue;
            }
        }
        return sum;
    }

    public static int reduceAddIFieldsX4_VectorAPI(int[] oops, int[] mem) {
        var acc = IntVector.broadcast(SPECIES_I, 0);
        int i = 0;
        for (; i < SPECIES_I.loopBound(oops.length); i += SPECIES_I.length()) {
            var oopv = IntVector.fromArray(SPECIES_I, oops, i);
            var mask = oopv.compare(VectorOperators.NE, /* null */0);
            // We are lucky today: we need to access mem[oop + 3]
            var fieldValues = IntVector.fromArray(SPECIES_I, mem, 3, oops, i, mask);
            acc = acc.add(fieldValues);
        }
        int sum = acc.reduceLanes(VectorOperators.ADD);
        for (; i < oops.length; i++) {
            int oop = oops[i];
            if (oop != 0) {
                int fieldValue = mem[oop + 3]; // oop+12
                sum += fieldValue;
            }
        }
        return sum;
    }

    // The lowerCase example demonstrates a lane-wise control-flow diamond.
    public static Object lowerCaseB_loop(byte[] a, byte[] r) {
        for (int i = 0; i < a.length; i++) {
            byte c = a[i];
            if (c >= 'A' && c <= 'Z') {
                c += ('a' - 'A'); // c += 32
            }
            r[i] = c;
        }
        return r;
    }

    // Control-flow diamonds can easily be simulated by "if-conversion", i.e.
    // by using masked operations. An alternative would be to use blend.
    public static Object lowerCaseB_VectorAPI_v1(byte[] a, byte[] r) {
        int i;
        for (i = 0; i < SPECIES_B.loopBound(a.length); i += SPECIES_B.length()) {
            var vc = ByteVector.fromArray(SPECIES_B, a, i);
            var maskA = vc.compare(VectorOperators.GE, (byte)'A');
            var maskZ = vc.compare(VectorOperators.LE, (byte)'Z');
            var mask = maskA.and(maskZ);
            vc = vc.add((byte)32, mask);
            vc.intoArray(r, i);
        }
        for (; i < a.length; i++) {
            byte c = a[i];
            if (c >= 'A' && c <= 'Z') {
                c += ('a' - 'A');
            }
            r[i] = c;
        }
        return r;
    }

    public static Object lowerCaseB_VectorAPI_v2(byte[] a, byte[] r) {
        int i;
        for (i = 0; i < SPECIES_B.loopBound(a.length); i += SPECIES_B.length()) {
            var vc = ByteVector.fromArray(SPECIES_B, a, i);
            // We can convert the range 65..90 (represents ascii A..Z) into a range 0..25.
            // This allows us to only use a single unsigned comparison.
            var vt = vc.add((byte)-'A');
            var mask = vt.compare(VectorOperators.ULE, (byte)25);
            vc = vc.add((byte)32, mask);
            vc.intoArray(r, i);
        }
        for (; i < a.length; i++) {
            byte c = a[i];
            if (c >= 'A' && c <= 'Z') {
                c += ('a' - 'A');
            }
            r[i] = c;
        }
        return r;
    }
}

