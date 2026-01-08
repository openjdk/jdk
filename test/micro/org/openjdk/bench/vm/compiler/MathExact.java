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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.MILLISECONDS)
public abstract class MathExact {
    @Param({"1000000"})
    public int SIZE;


    // === multiplyExact(int, int) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testMultiplyI(int i) {
        try {
            return Math.multiplyExact(i, i);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @Benchmark
    public void loopMultiplyIOverflow() {
        for (int i = 0; i < SIZE; i++) {
            testMultiplyI(i);
        }
    }

    @Benchmark
    public void loopMultiplyIInBounds() {
        for (int i = 0; i < SIZE; i++) {
            // 46_340 < 2 ^ 15.5 (< 46_341, but that's not important)
            // so
            // 46_340 ^ 2 < 2 ^ 31
            testMultiplyI(i % 46_341);
        }
    }


    // === multiplyExact(long, long) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long testMultiplyL(long i) {
        try {
            return Math.multiplyExact(i, i);
        } catch (ArithmeticException e) {
            return 0L;
        }
    }

    @Benchmark
    public void loopMultiplyLOverflow() {
        for (long i = 0L; i < (long)SIZE; i++) {
            // (2 ^ 63 - 1)^0.5 ~= 3_037_000_499.9761
            // Starting at 3_037_000_000 so that almost all computations overflow
            testMultiplyL(3_037_000_000L + i);
        }
    }

    @Benchmark
    public void loopMultiplyLInBounds() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testMultiplyL(i);
        }
    }


    // === negateExact(int) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testNegateI(int i) {
        try {
            return Math.negateExact(i);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @Benchmark
    public void loopNegateIOverflow() {
        for (int i = 0; i < SIZE; i++) {
            testNegateI(i);
        }
        for (int i = 0; i < SIZE; i++) {
            testNegateI(Integer.MIN_VALUE);
        }
    }

    @Benchmark
    public void loopNegateIInBounds() {
        for (int i = 0; i < SIZE; i++) {
            testNegateI(i);
        }
        for (int i = 0; i < SIZE; i++) {
            testNegateI(Integer.MAX_VALUE);
        }
    }


    // === negateExact(long) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long testNegateL(long i) {
        try {
            return Math.negateExact(i);
        } catch (ArithmeticException e) {
            return 0L;
        }
    }

    @Benchmark
    public void loopNegateLOverflow() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testNegateL(i);
        }
        for (long i = 0L; i < (long)SIZE; i++) {
            testNegateL(Long.MIN_VALUE);
        }
    }

    @Benchmark
    public void loopNegateLInBounds() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testNegateL(i);
        }
        for (long i = 0L; i < (long)SIZE; i++) {
            testNegateL(Long.MAX_VALUE);
        }
    }


    // === incrementExact(int) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testIncrementI(int i) {
        try {
            return Math.incrementExact(i);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @Benchmark
    public void loopIncrementIOverflow() {
        for (int i = 0; i < SIZE; i++) {
            testIncrementI(i);
        }
        for (int i = 0; i < SIZE; i++) {
            testIncrementI(Integer.MAX_VALUE);
        }
    }

    @Benchmark
    public void loopIncrementIInBounds() {
        for (int i = 0; i < SIZE; i++) {
            testIncrementI(i);
        }
        for (int i = 0; i < SIZE; i++) {
            testIncrementI(Integer.MIN_VALUE + i);
        }
    }


    // === incrementExact(long) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long testIncrementL(long i) {
        try {
            return Math.incrementExact(i);
        } catch (ArithmeticException e) {
            return 0L;
        }
    }

    @Benchmark
    public void loopIncrementLOverflow() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testIncrementL(i);
        }
        for (long i = 0L; i < (long)SIZE; i++) {
            testIncrementL(Long.MAX_VALUE);
        }
    }

    @Benchmark
    public void loopIncrementLInBounds() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testIncrementL(i);
        }
        for (long i = 0L; i < (long)SIZE; i++) {
            testIncrementL(Long.MIN_VALUE + i);
        }
    }


    // === decrementExact(int) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testDecrementI(int i) {
        try {
            return Math.decrementExact(i);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @Benchmark
    public void loopDecrementIOverflow() {
        for (int i = 0; i < SIZE; i++) {
            testDecrementI(i);
        }
        for (int i = 0; i < SIZE; i++) {
            testDecrementI(Integer.MIN_VALUE);
        }
    }

    @Benchmark
    public void loopDecrementIInBounds() {
        for (int i = 0; i < SIZE; i++) {
            testDecrementI(i);
        }
        for (int i = 0; i < SIZE; i++) {
            testDecrementI(Integer.MAX_VALUE - i);
        }
    }


    // === decrementExact(long) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long testDecrementL(long i) {
        try {
            return Math.decrementExact(i);
        } catch (ArithmeticException e) {
            return 0L;
        }
    }

    @Benchmark
    public void loopDecrementLOverflow() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testDecrementL(i);
        }
        for (long i = 0L; i < (long)SIZE; i++) {
            testDecrementL(Long.MIN_VALUE);
        }
    }

    @Benchmark
    public void loopDecrementLInBounds() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testDecrementL(i);
        }
        for (long i = 0L; i < (long)SIZE; i++) {
            testDecrementL(Long.MAX_VALUE - i);
        }
    }


    // === addExact(int) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testAddI(int l, int r) {
        try {
            return Math.addExact(l, r);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @Benchmark
    public void loopAddIOverflow() {
        for (int i = 0; i < SIZE; i++) {
            testAddI(Integer.MAX_VALUE - 1_000, i);
        }
    }

    @Benchmark
    public void loopAddIInBounds() {
        for (int i = 0; i < SIZE; i++) {
            testAddI(i * 5, i);
        }
    }


    // === addExact(long) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long testAddL(long l, long r) {
        try {
            return Math.addExact(l, r);
        } catch (ArithmeticException e) {
            return 0L;
        }
    }

    @Benchmark
    public void loopAddLOverflow() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testAddL(Long.MAX_VALUE - 1_000L, i);
        }
    }

    @Benchmark
    public void loopAddLInBounds() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testAddL(i * 5L, i);
        }
    }


    // === subtractExact(int) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testSubtractI(int l, int r) {
        try {
            return Math.subtractExact(l, r);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @Benchmark
    public void loopSubtractIOverflow() {
        for (int i = 0; i < SIZE; i++) {
            testSubtractI(Integer.MIN_VALUE + 1_000, i);
        }
    }

    @Benchmark
    public void loopSubtractIInBounds() {
        for (int i = 0; i < SIZE; i++) {
            testSubtractI(i * 5, i);
        }
    }


    // === subtractExact(long) ===
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long testSubtractL(long l, long r) {
        try {
            return Math.subtractExact(l, r);
        } catch (ArithmeticException e) {
            return 0L;
        }
    }

    @Benchmark
    public void loopSubtractLOverflow() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testSubtractL(Long.MIN_VALUE + 1_000L, i);
        }
    }

    @Benchmark
    public void loopSubtractLInBounds() {
        for (long i = 0L; i < (long)SIZE; i++) {
            testSubtractL(i * 5L, i);
        }
    }



    @Fork(value = 1, jvmArgs = {"-XX:TieredStopAtLevel=1"})
    public static class C1_1 extends MathExact {}

    @Fork(value = 1, jvmArgs = {"-XX:TieredStopAtLevel=2"})
    public static class C1_2 extends MathExact {}

    @Fork(value = 1, jvmArgs = {"-XX:TieredStopAtLevel=3"})
    public static class C1_3 extends MathExact {}

    @Fork(value = 1)
    public static class C2 extends MathExact {}

    @Fork(value = 1,
            jvmArgs = {
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:DisableIntrinsic=_addExactI,_incrementExactI,_addExactL,_incrementExactL,_subtractExactI,_decrementExactI,_subtractExactL,_decrementExactL,_negateExactI,_negateExactL,_multiplyExactI,_multiplyExactL",
            })
    public static class C2_no_intrinsics extends MathExact {}

    @Fork(value = 1, jvmArgs = {"-XX:-OmitStackTraceInFastThrow"})
    public static class C2_no_builtin_throw extends MathExact {}
}
