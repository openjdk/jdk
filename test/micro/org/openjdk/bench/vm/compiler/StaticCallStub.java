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

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/*
 * Measures the cost of compiled -> interpreted (c2i):
 * static-call-stub -> c2i -> interpreter.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StaticCallStub {

    private static int[] heavyData;

    @Setup(Level.Trial)
    public void setup() {
        heavyData = new int[64];
        for (int i = 0; i < heavyData.length; i++) {
            heavyData[i] = i;
        }
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    private static int c2iHeavy() {
        int[] d = heavyData;
        int s = 0;
        for (int i = 0; i < d.length; i++) {
            s += d[i];
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    private static void c2iEmpty() {
        return;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void c2cEmpty() {
        return;
    }

    @Benchmark
    public int callC2IHeavy() {
        return c2iHeavy();
    }

    @Benchmark
    public void callC2IEmpty() {
        c2iEmpty();
    }

    @Benchmark
    public void callC2CEmpty() {
        c2cEmpty();
    }
}
