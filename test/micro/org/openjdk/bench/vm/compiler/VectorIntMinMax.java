/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class VectorIntMinMax {
    @Param({"2048"})
    private int LENGTH;

    private int[] ia;
    private int[] ib;
    private int[] ic;

    @Param("0")
    private int seed;
    private Random random = new Random(seed);

    @Setup
    public void init() {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = random.nextInt();
            ib[i] = random.nextInt();
        }
    }

    // Test Math.max for int arrays
    @Benchmark
    public void testMaxInt() {
        for (int i = 0; i < LENGTH; i++) {
            ic[i] = Math.max(ia[i], ib[i]);
        }
    }

    // Test Math.min for int arrays
    @Benchmark
    public void testMinInt() {
        for (int i = 0; i < LENGTH; i++) {
            ic[i] = Math.min(ia[i], ib[i]);
        }
    }

    // Test StrictMath.min for int arrays
    @Benchmark
    public void testStrictMinInt() {
        for (int i = 0; i < LENGTH; i++) {
            ic[i] = StrictMath.min(ia[i], ib[i]);
        }
    }

    // Test StrictMath.max for int arrays
    @Benchmark
    public void testStrictMaxInt() {
        for (int i = 0; i < LENGTH; i++) {
            ic[i] = StrictMath.max(ia[i], ib[i]);
        }
    }
}
