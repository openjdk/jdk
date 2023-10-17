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
@Threads(Threads.MAX)
@State(Scope.Benchmark)
public class SecondarySuperCache {

    // This test targets C1 specifically, to enter the interesting code path
    // without heavily optimizing compiler like C2 optimizing based on profiles,
    // or folding the instanceof checks.

    interface IA {}
    interface IB {}
    interface I extends IA, IB {}
    public class C1 implements I {}
    public class C2 implements I {}

    I c1, c2;

    @Setup
    public void setup() {
        c1 = new C1();
        c2 = new C2();
    }

    @Benchmark
    public void contended(Blackhole bh) {
        bh.consume(c1 instanceof IA);
        bh.consume(c2 instanceof IA);
        bh.consume(c1 instanceof IB);
        bh.consume(c2 instanceof IB);
    }

    @Benchmark
    public void uncontended(Blackhole bh) {
        bh.consume(c1 instanceof IA);
        bh.consume(c1 instanceof IA);
        bh.consume(c2 instanceof IB);
        bh.consume(c2 instanceof IB);
    }

}
