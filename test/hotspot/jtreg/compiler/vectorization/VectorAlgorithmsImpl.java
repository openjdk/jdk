/*
 *  Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorization;

import jdk.incubator.vector.*;

/**
 * The code below is supposed to be an exact copy of:
 *   micro/org/openjdk/bench/vm/compiler/VectorAlgorithmsImpl.java
 */
public class VectorAlgorithmsImpl {
    private static final VectorSpecies<Integer> SPECIES_I    = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> SPECIES_I512 = IntVector.SPECIES_512;

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
        for (i = 0; i < a.length - 3; i+=4) {
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
        for (; i < a.length - 3; i+=4) {
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
}
