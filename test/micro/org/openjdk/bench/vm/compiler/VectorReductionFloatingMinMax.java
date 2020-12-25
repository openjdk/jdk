/*
 * Copyright (c) 2020, Huawei Technologies Co., Ltd. All rights reserved.
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
public class VectorReduction {
    @Param({"512"})
    public int COUNT;

    private float[]  floatsA;
    private float[]  floatsB;
    private double[] doublesA;
    private double[] doublesB;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        floatsA = new float[COUNT];
        floatsB = new float[COUNT];
        doublesA = new double[COUNT];
        doublesB = new double[COUNT];

        for (int i = 0; i < COUNT; i++) {
            floatsA[i] = r.nextFloat();
            floatsB[i] = r.nextFloat();
            doublesA[i] = r.nextDouble();
            doublesB[i] = r.nextDouble();
        }
    }

    @Benchmark
    public void maxRedF(Blackhole bh) {
        float max = 0.0f;
        for (int i = 0; i < COUNT; i++) {
            max = Math.max(max, floatsA[i] - floatsB[i]);
        }
        bh.consume(max);
    }

    @Benchmark
    public void minRedF(Blackhole bh) {
        float min = 0.0f;
        for (int i = 0; i < COUNT; i++) {
            min = Math.min(min, floatsA[i] - floatsB[i]);
        }
        bh.consume(min);
    }

    @Benchmark
    public void maxRedD(Blackhole bh) {
        double max = 0.0d;
        for (int i = 0; i < COUNT; i++) {
            max = Math.max(max, doublesA[i] - doublesB[i]);
        }
        bh.consume(max);
    }

    @Benchmark
    public void minRedD(Blackhole bh) {
        double min = 0.0d;
        for (int i = 0; i < COUNT; i++) {
            min = Math.min(min, doublesA[i] - doublesB[i]);
        }
        bh.consume(min);
    }
}
