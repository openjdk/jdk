/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySession;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--enable-preview" })
public class VaList extends CLayouts {

    static final Linker linker = Linker.nativeLinker();
    static {
        System.loadLibrary("VaList");
    }

    static final MethodHandle MH_ellipsis;
    static final MethodHandle MH_vaList;

    static {
        SymbolLookup loaderLibs = SymbolLookup.loaderLookup();
        MH_ellipsis = linker.downcallHandle(loaderLibs.lookup("ellipsis").get(),
                FunctionDescriptor.ofVoid(C_INT).asVariadic(C_INT, C_DOUBLE, C_LONG_LONG));
        MH_vaList = linker.downcallHandle(loaderLibs.lookup("vaList").get(),
                FunctionDescriptor.ofVoid(C_INT, C_POINTER));
    }

    @Benchmark
    public void ellipsis() throws Throwable {
        MH_ellipsis.invokeExact(3,
                                1, 2D, 3L);
    }

    @Benchmark
    public void vaList() throws Throwable {
        try (MemorySession session = MemorySession.openConfined()) {
            java.lang.foreign.VaList vaList = java.lang.foreign.VaList.make(b ->
                    b.addVarg(C_INT, 1)
                            .addVarg(C_DOUBLE, 2D)
                            .addVarg(C_LONG_LONG, 3L), session);
            MH_vaList.invokeExact(3,
                    (Addressable)vaList);
        }
    }
}
