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
 * Tests transformation that converts "(x|y)-(x^y)" into "x&y" in
 * SubINode:Ideal and SubLNode::Ideal.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class SubIdeal_XOrY_Minus_XXorY_ {

    private int iX = 4711;

    private int iY = 1174;

    private long lX = 4711 * 4711 * 4711;

    private long lY = 1174 * 1174 * 1174;

    @Benchmark
    public void baselineInt(Blackhole bh) {
        bh.consume(iX);
    }

    @Benchmark
    public void baselineLong(Blackhole bh) {
        bh.consume(lX);
    }

    // Convert "(x|y)-(x^y)" into "x&y" for int.
    @Benchmark
    public void testInt(Blackhole bh) {
        bh.consume((iX | iY) - (iX ^ iY));
    }

    // Convert "(x|y)-(x^y)" into "x&y" for long.
    @Benchmark
    public void testLong(Blackhole bh) {
        bh.consume((lX | lY) - (lX ^ lY));
    }
}
