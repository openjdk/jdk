/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * @test
 * @summary Test passing of Homogeneous Float Aggregates.
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64" | os.arch == "ppc64le" | os.arch == "riscv64"
 * @requires !vm.musl
 *
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestHFA
 */

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.*;

public class TestHFA {

    static {
        System.loadLibrary("TestHFA");
    }

    final static Linker abi = Linker.nativeLinker();
    final static SymbolLookup lookup = SymbolLookup.loaderLookup();

    static final OfFloat FLOAT = JAVA_FLOAT.withBitAlignment(32);

    final static GroupLayout S_FFLayout = MemoryLayout.structLayout(
        FLOAT.withName("p0"),
        FLOAT.withName("p1")
    ).withName("S_FF");

    final static GroupLayout S_FFFFFFFLayout = MemoryLayout.structLayout(
        FLOAT.withName("p0"),
        FLOAT.withName("p1"),
        FLOAT.withName("p2"),
        FLOAT.withName("p3"),
        FLOAT.withName("p4"),
        FLOAT.withName("p5"),
        FLOAT.withName("p6")
    ).withName("S_FFFF");

    static final FunctionDescriptor fdadd_floats_structs = FunctionDescriptor.of(S_FFFFFFFLayout, S_FFFFFFFLayout, S_FFFFFFFLayout);
    static final FunctionDescriptor fdadd_float_to_struct_after_floats = FunctionDescriptor.of(S_FFLayout,
        JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
        JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
        JAVA_FLOAT, JAVA_FLOAT, S_FFLayout, JAVA_FLOAT);
    static final FunctionDescriptor fdadd_float_to_struct_after_structs = FunctionDescriptor.of(S_FFLayout,
        S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout,
        S_FFLayout, JAVA_FLOAT);
    static final FunctionDescriptor fdadd_float_to_large_struct_after_structs = FunctionDescriptor.of(S_FFFFFFFLayout,
        S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout,
        S_FFFFFFFLayout, JAVA_FLOAT);

    final static MethodHandle mhadd_float_structs = abi.downcallHandle(lookup.find("add_float_structs").orElseThrow(),
        fdadd_floats_structs);
    final static MethodHandle mhadd_float_to_struct_after_floats = abi.downcallHandle(lookup.find("add_float_to_struct_after_floats").orElseThrow(),
        fdadd_float_to_struct_after_floats);
    final static MethodHandle mhadd_float_to_struct_after_structs = abi.downcallHandle(lookup.find("add_float_to_struct_after_structs").orElseThrow(),
        fdadd_float_to_struct_after_structs);
    final static MethodHandle mhadd_float_to_large_struct_after_structs = abi.downcallHandle(lookup.find("add_float_to_large_struct_after_structs").orElseThrow(),
        fdadd_float_to_large_struct_after_structs);

    @Test
    public static void testAddFloatStructs() {
        float p0 = 0.0f, p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f, p5 = 0.0f, p6 = 0.0f;
        try {
            Arena arena = Arena.openConfined();
            MemorySegment s = MemorySegment.allocateNative(S_FFFFFFFLayout, arena.scope());
            s.set(FLOAT, 0, 1.0f);
            s.set(FLOAT, 4, 2.0f);
            s.set(FLOAT, 8, 3.0f);
            s.set(FLOAT, 12, 4.0f);
            s.set(FLOAT, 16, 5.0f);
            s.set(FLOAT, 20, 6.0f);
            s.set(FLOAT, 24, 7.0f);
            s = (MemorySegment)mhadd_float_structs.invokeExact((SegmentAllocator)arena, s, s);
            p0 = s.get(FLOAT, 0);
            p1 = s.get(FLOAT, 4);
            p2 = s.get(FLOAT, 8);
            p3 = s.get(FLOAT, 12);
            p4 = s.get(FLOAT, 16);
            p5 = s.get(FLOAT, 20);
            p6 = s.get(FLOAT, 24);
            System.out.println("S_FFFFFFF(" + p0 + ";" + p1 + ";" + p2 + ";" + p3 + ";" + p4 + ";" + p5 + ";" + p6 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 4.0f || p2 != 6.0f || p3 != 8.0f || p4 != 10.0f || p5 != 12.0f || p6 != 14.0f)
            throw new RuntimeException("add_float_structs");
    }

    @Test
    public static void testAddFloatToStructAfterFloats() {
        float p0 = 0.0f, p1 = 0.0f;
        try {
            Arena arena = Arena.openConfined();
            MemorySegment s = MemorySegment.allocateNative(S_FFLayout, arena.scope());
            s.set(FLOAT, 0, 1.0f);
            s.set(FLOAT, 4, 1.0f);
            s = (MemorySegment)mhadd_float_to_struct_after_floats.invokeExact((SegmentAllocator)arena,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f,
                6.0f, 7.0f, 8.0f, 9.0f, 10.0f,
                11.0f, 12.0f, s, 1.0f);
            p0 = s.get(FLOAT, 0);
            p1 = s.get(FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_float_to_struct_after_floats error");
    }

    @Test
    public static void testAddFloatToStructAfterStructs() {
        float p0 = 0.0f, p1 = 0.0f;
        try {
            Arena arena = Arena.openConfined();
            MemorySegment s = MemorySegment.allocateNative(S_FFLayout, arena.scope());
            s.set(FLOAT, 0, 1.0f);
            s.set(FLOAT, 4, 1.0f);
            s = (MemorySegment)mhadd_float_to_struct_after_structs.invokeExact((SegmentAllocator)arena,
                 s, s, s, s, s, s,
                 s, 1.0f);
            p0 = s.get(FLOAT, 0);
            p1 = s.get(FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_float_to_struct_after_structs error");
    }

    @Test
    public static void testAddFloatToLargeStructAfterStructs() {
        float p0 = 0.0f, p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f, p5 = 0.0f, p6 = 0.0f;
        try {
            Arena arena = Arena.openConfined();
            MemorySegment s = MemorySegment.allocateNative(S_FFFFFFFLayout, arena.scope());
            s.set(FLOAT, 0, 1.0f);
            s.set(FLOAT, 4, 2.0f);
            s.set(FLOAT, 8, 3.0f);
            s.set(FLOAT, 12, 4.0f);
            s.set(FLOAT, 16, 5.0f);
            s.set(FLOAT, 20, 6.0f);
            s.set(FLOAT, 24, 7.0f);
            s = (MemorySegment)mhadd_float_to_large_struct_after_structs.invokeExact((SegmentAllocator)arena,
                 s, s, s, s, s, s,
                 s, 1.0f);
            p0 = s.get(FLOAT, 0);
            p1 = s.get(FLOAT, 4);
            p2 = s.get(FLOAT, 8);
            p3 = s.get(FLOAT, 12);
            p4 = s.get(FLOAT, 16);
            p5 = s.get(FLOAT, 20);
            p6 = s.get(FLOAT, 24);
            System.out.println("S_FFFFFFF(" + p0 + ";" + p1 + ";" + p2 + ";" + p3 + ";" + p4 + ";" + p5 + ";" + p6 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 2.0f || p2 != 3.0f || p3 != 4.0f || p4 != 5.0f || p5 != 6.0f || p6 != 7.0f)
            throw new RuntimeException("add_float_to_large_struct_after_structs error");
    }
}
