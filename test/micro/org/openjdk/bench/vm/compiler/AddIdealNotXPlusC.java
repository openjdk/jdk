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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Tests transformation that converts "~x + c" into "(c - 1) - x" in
 * AddNode::IdealIL and "~(x+c)" into "(-c - 1) - x" in XorINode:Ideal
 * and XorLNode::Ideal.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class AddIdealNotXPlusC {

    private static final int I_C = 1234567;

    private static final long L_C = 123_456_789_123_456L;

    private int iFld = 4711;

    private long lFld = 4711 * 4711 * 4711;

    private final int SIZE = 10;

    @Benchmark
    public void baselineInt(Blackhole bh) {
        for (int i = 0; i < SIZE; i++) {
            bh.consume(iFld);
        }
    }

    @Benchmark
    public void baselineLong(Blackhole bh) {
        for (int i = 0; i < SIZE; i++) {
            bh.consume(lFld);
        }
    }

    // Convert "~x + c" into "(c - 1) - x" for int.
    // (c - 1) -x + x is then converted into c - 1.
    @Benchmark
    public void testInt1(Blackhole bh) {
        for (int i = 0; i < SIZE; i++) {
            bh.consume(~iFld + I_C + iFld);
        }
    }

    // Convert "~(x + c)" into "(-c - 1) - x" for int.
    // (-c - 1) -x + x is then converted into -c - 1.
    @Benchmark
    public void testInt2(Blackhole bh) {
        for (int i = 0; i < SIZE; i++) {
            bh.consume(~(iFld + I_C) + iFld);
        }
    }

    // Convert "~x + c" into "(c - 1) - x" for long.
     // (c - 1) -x + x is then converted into c - 1.
    @Benchmark
    public void testLong1(Blackhole bh) {
        for (int i = 0; i < SIZE; i++) {
            bh.consume(~lFld + L_C + lFld);
        }
    }

    // Convert "~(x + c)" into "(-c - 1) - x" for long.
     // (-c - 1) -x + x is then converted into -c - 1.
    @Benchmark
    public void testLong2(Blackhole bh) {
        for (int i = 0; i < SIZE; i++) {
            bh.consume(~(lFld + L_C) + lFld);
        }
    }
}
