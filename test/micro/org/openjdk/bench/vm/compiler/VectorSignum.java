/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
public class VectorSignum {
    @Param({"256", "512", "1024", "2048"})
    private static int SIZE;

    private double[] res_doubles = new double[SIZE];
    private double[] doubles = new double[SIZE];
    private float[] res_floats = new float[SIZE];
    private float[] floats = new float[SIZE];

    private Random r = new Random(1024);

    @Setup
    public void init() {
        doubles = new double[SIZE];
        floats = new float[SIZE];
        res_doubles = new double[SIZE];
        res_floats = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            floats[i] = r.nextFloat();
            doubles[i] = r.nextDouble();
        }
    }

    @Benchmark
    public void floatSignum() {
        for (int i = 0; i < SIZE; i++) {
            res_floats[i] = Math.signum(floats[i]);
        }
    }

    @Benchmark
    public void doubleSignum() {
        for (int i = 0; i < SIZE; i++) {
            res_doubles[i] = Math.signum(doubles[i]);
        }
    }
}
