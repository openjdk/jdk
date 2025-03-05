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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public abstract class MultiplyExact {
    @Param({"1000000"})
    public int SIZE;

    public int square(int a) {
        return Math.multiplyExact(a, a);
    }

    public int test(int i) {
        try {
            return square(i);
        } catch (Throwable e) {
            return 0;
        }
    }

    @Benchmark
    public void loop() {
        for (int i = 0; i < SIZE; i++) {
            test(i);
        }
    }



    @Fork(value = 1)
    public static class C2 extends MultiplyExact {}

    @Fork(value = 1, jvmArgs = {"-XX:TieredStopAtLevel=1"})
    public static class C1_1 extends MultiplyExact {}

    @Fork(value = 1, jvmArgs = {"-XX:TieredStopAtLevel=2"})
    public static class C1_2 extends MultiplyExact {}

    @Fork(value = 1, jvmArgs = {"-XX:TieredStopAtLevel=3"})
    public static class C1_3 extends MultiplyExact {}
}
