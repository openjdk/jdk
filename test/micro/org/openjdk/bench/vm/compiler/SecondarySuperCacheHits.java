/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class SecondarySuperCacheHits {

    // This test targets C1 specifically, to enter the interesting code path
    // without heavily optimizing compiler like C2 optimizing based on profiles,
    // or folding the instanceof checks.

    // The test verifies what happens on a happy path, when we can actually cache
    // the last super and use it effectively.

    interface I01 {}
    interface I02 {}
    interface I03 {}
    interface I04 {}
    interface I05 {}
    interface I06 {}
    interface I07 {}
    interface I08 {}
    interface I09 {}
    interface I10 {}
    interface I11 {}
    interface I12 {}
    interface I13 {}
    interface I14 {}
    interface I15 {}
    interface I16 {}
    interface I17 {}
    interface I18 {}
    interface I19 {}
    interface I20 {}

    class B {}
    class C1 extends B implements I01, I02, I03, I04, I05, I06, I07, I08, I09, I10, I11, I12, I13, I14, I15, I16, I17, I18, I19, I20 {}

    volatile B o;

    @Setup
    public void setup() {
        o = new C1();
    }

    static final int ITERS = 10000;

    @Benchmark
    @OperationsPerInvocation(20*ITERS)
    public void test(Blackhole bh) {
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I01);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I02);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I03);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I04);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I05);

        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I06);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I07);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I08);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I09);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I10);

        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I11);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I12);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I13);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I14);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I15);

        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I16);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I17);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I18);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I19);
        for (int c = 0; c < ITERS; c++) bh.consume(o instanceof I20);
    }

}
