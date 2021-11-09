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
    public long compareInt(int arg0, int arg1) {
        return arg0 + Integer.MIN_VALUE < arg1 + Integer.MIN_VALUE ? 1 : 0;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long compareLong(long arg0, long arg1) {
        return arg0 + Long.MIN_VALUE < arg1 + Long.MIN_VALUE ? 1 : 0;
    }

    @Benchmark
    public void runInt() {
        compareInt(0, -1);
        compareInt(-1, 0);
    }

    @Benchmark
    public void runLong() {
        compareLong(0L, -1L);
        compareLong(-1L, 0L);
    }
}
