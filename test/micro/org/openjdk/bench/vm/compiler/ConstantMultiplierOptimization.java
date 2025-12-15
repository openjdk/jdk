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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.lang.invoke.*;
import java.util.Random;


@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MINUTES)
@State(Scope.Thread)
@Fork(value = 1, jvmArgs = {"-XX:LoopUnrollLimit=1"})
public class ConstantMultiplierOptimization {

    public static int mul_by_25_I(int a) {
       return a * 25;
    }
    public static int mul_by_27_I(int a) {
       return a * 27;
    }
    public static int mul_by_37_I(int a) {
       return a * 37;
    }
    public static int mul_by_19_I(int a) {
       return a * 19;
    }
    public static int mul_by_13_I(int a) {
       return a * 13;
    }
    public static int mul_by_11_I(int a) {
       return a * 11;
    }

    public static long mul_by_25_L(long a) {
       return a * 25;
    }
    public static long mul_by_27_L(long a) {
       return a * 27;
    }
    public static long mul_by_37_L(long a) {
       return a * 37;
    }
    public static long mul_by_19_L(long a) {
       return a * 19;
    }
    public static long mul_by_13_L(long a) {
       return a * 13;
    }
    public static long mul_by_11_L(long a) {
       return a * 11;
    }

    @Benchmark
    public long testConstMultiplierL() {
        long res = 0;
        for (long i = 0 ; i < 100000000; i++) {
            res += mul_by_37_L(i);
            res += mul_by_25_L(i);
            res += mul_by_27_L(i);
            res += mul_by_19_L(i);
            res += mul_by_13_L(i);
            res += mul_by_11_L(i);
        }
        return res;
    }

    @Benchmark
    public int testConstMultiplierI() {
        int res = 0;
        for (int i = 0 ; i < 100000000; i++) {
            res += mul_by_37_I(i);
            res += mul_by_25_I(i);
            res += mul_by_27_I(i);
            res += mul_by_19_I(i);
            res += mul_by_13_I(i);
            res += mul_by_11_I(i);
        }
        return res;
    }
}
