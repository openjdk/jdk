/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class UnsignedComparison {
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intEQ(int arg0, int arg1) {
        return arg0 + Integer.MIN_VALUE == arg1 + Integer.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intNE(int arg0, int arg1) {
        return arg0 + Integer.MIN_VALUE != arg1 + Integer.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intLT(int arg0, int arg1) {
        return arg0 + Integer.MIN_VALUE < arg1 + Integer.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intLE(int arg0, int arg1) {
        return arg0 + Integer.MIN_VALUE <= arg1 + Integer.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intGT(int arg0, int arg1) {
        return arg0 + Integer.MIN_VALUE > arg1 + Integer.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intGE(int arg0, int arg1) {
        return arg0 + Integer.MIN_VALUE >= arg1 + Integer.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intLibLT(int arg0, int arg1) {
        return Integer.compareUnsigned(arg0, arg1) < 0;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean intLibGT(int arg0, int arg1) {
        return Integer.compareUnsigned(arg0, arg1) > 0;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longEQ(long arg0, long arg1) {
        return arg0 + Long.MIN_VALUE == arg1 + Long.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longNE(long arg0, long arg1) {
        return arg0 + Long.MIN_VALUE != arg1 + Long.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longLT(long arg0, long arg1) {
        return arg0 + Long.MIN_VALUE < arg1 + Long.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longLE(long arg0, long arg1) {
        return arg0 + Long.MIN_VALUE <= arg1 + Long.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longGT(long arg0, long arg1) {
        return arg0 + Long.MIN_VALUE > arg1 + Long.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longGE(long arg0, long arg1) {
        return arg0 + Long.MIN_VALUE >= arg1 + Long.MIN_VALUE;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longLibLT(long arg0, long arg1) {
        return Long.compareUnsigned(arg0, arg1) < 0;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static boolean longLibGT(long arg0, long arg1) {
        return Long.compareUnsigned(arg0, arg1) > 0;
    }

    @Benchmark
    public void runIntEQ() {
        intEQ(0, 0);
        intEQ(-1, 0);
    }

    @Benchmark
    public void runIntNE() {
        intNE(0, 0);
        intNE(-1, 0);
    }

    @Benchmark
    public void runIntLT() {
        intLT(0, -1);
        intLT(-1, 0);
    }

    @Benchmark
    public void runIntLE() {
        intLE(0, -1);
        intLE(-1, 0);
    }

    @Benchmark
    public void runIntGT() {
        intGT(0, -1);
        intGT(-1, 0);
    }

    @Benchmark
    public void runIntGE() {
        intGE(0, -1);
        intGE(-1, 0);
    }

    @Benchmark
    public void runIntLibLT() {
        intLibLT(0, -1);
        intLibLT(-1, 0);
    }

    @Benchmark
    public void runIntLibGT() {
        intLibGT(0, -1);
        intLibGT(-1, 0);
    }

    @Benchmark
    public void runLongEQ() {
        longEQ(0, 0);
        longEQ(-1, 0);
    }

    @Benchmark
    public void runLongNE() {
        longNE(0, 0);
        longNE(-1, 0);
    }

    @Benchmark
    public void runLongLT() {
        longLT(0, -1);
        longLT(-1, 0);
    }

    @Benchmark
    public void runLongLE() {
        longLE(0, -1);
        longLE(-1, 0);
    }

    @Benchmark
    public void runLongGT() {
        longGT(0, -1);
        longGT(-1, 0);
    }

    @Benchmark
    public void runLongGE() {
        longGE(0, -1);
        longGE(-1, 0);
    }

    @Benchmark
    public void runLongLibLT() {
        longLibLT(0, -1);
        longLibLT(-1, 0);
    }

    @Benchmark
    public void runLongLibGT() {
        longLibGT(0, -1);
        longLibGT(-1, 0);
    }
}
