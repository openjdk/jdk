/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.openjdk.bench.valhalla.array.walk;


import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class WalkBase {

    @State(Scope.Thread)
    public static abstract class SizeState {

        @Param({
                "100",      // tiny size, to fit into all caches and check codegeneration quality
                "1000000"   // large size, to be out of all caches and memory effects
        })
        public int size;
    }

    public static void shuffle(int[] a, Random rnd) {
        for (int i = a.length; i > 1; i--) {
            int idx = rnd.nextInt(i);
            int tmp = a[i - 1];
            a[i - 1] = a[idx];
            a[idx] = tmp;
        }

    }

    public static int[] makeRandomRing(int size) {
        int[] A = new int[size - 1];
        for (int i = 0; i < A.length; i++) {
            A[i] = i + 1;
        }
        shuffle(A, new Random(42));
        int[] a = new int[size];
        int x = 0;
        for (int i = 0; i < A.length; i++) {
            x = a[x] = A[i];
        }
        a[x] = 0;
        return a;
    }

}
