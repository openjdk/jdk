/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.random.RandomGeneratorFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class CMove {
    static final int SIZE = 1000000;

    @Param({"0.003", "0.006", "0.01", "0.02", "0.03", "0.06", "0.1", "0.2", "0.3", "0.6"})
    double freq;

    boolean[] conds;

    @Setup(Level.Iteration)
    public void setup() {
        var r = RandomGeneratorFactory.getDefault().create(1);
        conds = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            conds[i] = r.nextDouble() < freq;
        }
    }

    @Benchmark
    public void run(Blackhole bh) {
        for (int i = 0; i < conds.length; i++) {
            bh.consume(conds[i] ? 2 : 1);
        }
    }
}