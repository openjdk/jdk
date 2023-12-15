/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
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

/*
 * @test
 * @summary Test passing of Homogeneous Float Aggregates.
 *
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestHFA
 */

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.*;

public class TestHFA {

    static {
        System.loadLibrary("TestHFA");
    }

    final static Linker abi = Linker.nativeLinker();
    final static SymbolLookup lookup = SymbolLookup.loaderLookup();

    final static OfFloat  C_FLOAT  = (ValueLayout.OfFloat)  abi.canonicalLayouts().get("float");
    final static OfDouble C_DOUBLE = (ValueLayout.OfDouble) abi.canonicalLayouts().get("double");

    final static GroupLayout S_FFLayout = MemoryLayout.structLayout(
        C_FLOAT.withName("p0"),
        C_FLOAT.withName("p1")
    ).withName("S_FF");

    final static GroupLayout S_FFFFFFFLayout = MemoryLayout.structLayout(
        C_FLOAT.withName("p0"),
        C_FLOAT.withName("p1"),
        C_FLOAT.withName("p2"),
        C_FLOAT.withName("p3"),
        C_FLOAT.withName("p4"),
        C_FLOAT.withName("p5"),
        C_FLOAT.withName("p6")
    ).withName("S_FFFF");

    static final FunctionDescriptor fdadd_float_structs = FunctionDescriptor.of(S_FFFFFFFLayout, S_FFFFFFFLayout, S_FFFFFFFLayout);
    static final FunctionDescriptor fdadd_float_to_struct_after_floats = FunctionDescriptor.of(S_FFLayout,
        C_FLOAT, C_FLOAT, C_FLOAT, C_FLOAT, C_FLOAT,
        C_FLOAT, C_FLOAT, C_FLOAT, C_FLOAT, C_FLOAT,
        C_FLOAT, C_FLOAT, S_FFLayout, C_FLOAT);
    static final FunctionDescriptor fdadd_float_to_struct_after_structs = FunctionDescriptor.of(S_FFLayout,
        S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout,
        S_FFLayout, C_FLOAT);
    static final FunctionDescriptor fdadd_double_to_struct_after_structs = FunctionDescriptor.of(S_FFLayout,
        S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout,
        S_FFLayout, C_DOUBLE);
    static final FunctionDescriptor fdadd_float_to_large_struct_after_structs = FunctionDescriptor.of(S_FFFFFFFLayout,
        S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout, S_FFLayout,
        S_FFFFFFFLayout, C_FLOAT);

    static final FunctionDescriptor fdpass_two_large_structs = FunctionDescriptor.of(S_FFFFFFFLayout, ADDRESS, S_FFFFFFFLayout, S_FFFFFFFLayout);
    static final FunctionDescriptor fdpass_struct_after_floats = FunctionDescriptor.of(S_FFLayout, ADDRESS, S_FFLayout, C_FLOAT);
    static final FunctionDescriptor fdpass_struct_after_structs = FunctionDescriptor.of(S_FFLayout, ADDRESS, S_FFLayout, C_FLOAT);
    static final FunctionDescriptor fdpass_struct_after_structs_plus_double = FunctionDescriptor.of(S_FFLayout, ADDRESS, S_FFLayout, C_DOUBLE);
    static final FunctionDescriptor fdpass_large_struct_after_structs = FunctionDescriptor.of(S_FFFFFFFLayout, ADDRESS, S_FFFFFFFLayout, C_FLOAT);

    final static MethodHandle mhadd_float_structs = abi.downcallHandle(lookup.find("add_float_structs").orElseThrow(),
        fdadd_float_structs);
    final static MethodHandle mhadd_float_to_struct_after_floats = abi.downcallHandle(lookup.find("add_float_to_struct_after_floats").orElseThrow(),
        fdadd_float_to_struct_after_floats);
    final static MethodHandle mhadd_float_to_struct_after_structs = abi.downcallHandle(lookup.find("add_float_to_struct_after_structs").orElseThrow(),
        fdadd_float_to_struct_after_structs);
    final static MethodHandle mhadd_double_to_struct_after_structs = abi.downcallHandle(lookup.find("add_double_to_struct_after_structs").orElseThrow(),
        fdadd_double_to_struct_after_structs);
    final static MethodHandle mhadd_float_to_large_struct_after_structs = abi.downcallHandle(lookup.find("add_float_to_large_struct_after_structs").orElseThrow(),
        fdadd_float_to_large_struct_after_structs);

    final static MethodHandle mhpass_two_large_structs = abi.downcallHandle(lookup.find("pass_two_large_structs").orElseThrow(),
        fdpass_two_large_structs);
    final static MethodHandle mhpass_struct_after_floats = abi.downcallHandle(lookup.find("pass_struct_after_floats").orElseThrow(),
        fdpass_struct_after_floats);
    final static MethodHandle mhpass_struct_after_structs = abi.downcallHandle(lookup.find("pass_struct_after_structs").orElseThrow(),
        fdpass_struct_after_structs);
    final static MethodHandle mhpass_struct_after_structs_plus_double = abi.downcallHandle(lookup.find("pass_struct_after_structs_plus_double").orElseThrow(),
        fdpass_struct_after_structs_plus_double);
    final static MethodHandle mhpass_large_struct_after_structs = abi.downcallHandle(lookup.find("pass_large_struct_after_structs").orElseThrow(),
        fdpass_large_struct_after_structs);

    @Test
    public static void testAddFloatStructs() {
        float p0 = 0.0f, p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f, p5 = 0.0f, p6 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFFFFFFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 2.0f);
            s.set(C_FLOAT, 8, 3.0f);
            s.set(C_FLOAT, 12, 4.0f);
            s.set(C_FLOAT, 16, 5.0f);
            s.set(C_FLOAT, 20, 6.0f);
            s.set(C_FLOAT, 24, 7.0f);
            s = (MemorySegment)mhadd_float_structs.invokeExact((SegmentAllocator)arena, s, s);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            p2 = s.get(C_FLOAT, 8);
            p3 = s.get(C_FLOAT, 12);
            p4 = s.get(C_FLOAT, 16);
            p5 = s.get(C_FLOAT, 20);
            p6 = s.get(C_FLOAT, 24);
            System.out.println("S_FFFFFFF(" + p0 + ";" + p1 + ";" + p2 + ";" + p3 + ";" + p4 + ";" + p5 + ";" + p6 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 4.0f || p2 != 6.0f || p3 != 8.0f || p4 != 10.0f || p5 != 12.0f || p6 != 14.0f)
            throw new RuntimeException("add_float_structs error");
    }

    @Test
    public static void testAddFloatToStructAfterFloats() {
        float p0 = 0.0f, p1 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 1.0f);
            s = (MemorySegment)mhadd_float_to_struct_after_floats.invokeExact((SegmentAllocator)arena,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f,
                6.0f, 7.0f, 8.0f, 9.0f, 10.0f,
                11.0f, 12.0f, s, 1.0f);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_float_to_struct_after_floats error");
    }

    @Test
    public static void testAddFloatToStructAfterStructs() {
        float p0 = 0.0f, p1 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 1.0f);
            s = (MemorySegment)mhadd_float_to_struct_after_structs.invokeExact((SegmentAllocator)arena,
                 s, s, s, s, s, s,
                 s, 1.0f);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_float_to_struct_after_structs error");
    }

    @Test
    public static void testAddDoubleToStructAfterStructs() {
        float p0 = 0.0f, p1 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 1.0f);
            s = (MemorySegment)mhadd_double_to_struct_after_structs.invokeExact((SegmentAllocator)arena,
                 s, s, s, s, s, s,
                 s, 1.0d);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_double_to_struct_after_structs error");
    }

    @Test
    public static void testAddFloatToLargeStructAfterStructs() {
        float p0 = 0.0f, p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f, p5 = 0.0f, p6 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFFFFFFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 2.0f);
            s.set(C_FLOAT, 8, 3.0f);
            s.set(C_FLOAT, 12, 4.0f);
            s.set(C_FLOAT, 16, 5.0f);
            s.set(C_FLOAT, 20, 6.0f);
            s.set(C_FLOAT, 24, 7.0f);
            s = (MemorySegment)mhadd_float_to_large_struct_after_structs.invokeExact((SegmentAllocator)arena,
                 s, s, s, s, s, s,
                 s, 1.0f);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            p2 = s.get(C_FLOAT, 8);
            p3 = s.get(C_FLOAT, 12);
            p4 = s.get(C_FLOAT, 16);
            p5 = s.get(C_FLOAT, 20);
            p6 = s.get(C_FLOAT, 24);
            System.out.println("S_FFFFFFF(" + p0 + ";" + p1 + ";" + p2 + ";" + p3 + ";" + p4 + ";" + p5 + ";" + p6 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 2.0f || p2 != 3.0f || p3 != 4.0f || p4 != 5.0f || p5 != 6.0f || p6 != 7.0f)
            throw new RuntimeException("add_float_to_large_struct_after_structs error");
    }

    // Java versions for Upcall tests.
    public static MemorySegment addFloatStructs(MemorySegment p0, MemorySegment p1) {
        float val0 = p0.get(C_FLOAT,  0) + p1.get(C_FLOAT,  0);
        float val1 = p0.get(C_FLOAT,  4) + p1.get(C_FLOAT,  4);
        float val2 = p0.get(C_FLOAT,  8) + p1.get(C_FLOAT,  8);
        float val3 = p0.get(C_FLOAT, 12) + p1.get(C_FLOAT, 12);
        float val4 = p0.get(C_FLOAT, 16) + p1.get(C_FLOAT, 16);
        float val5 = p0.get(C_FLOAT, 20) + p1.get(C_FLOAT, 20);
        float val6 = p0.get(C_FLOAT, 24) + p1.get(C_FLOAT, 24);
        p0.set(C_FLOAT,  0, val0);
        p0.set(C_FLOAT,  4, val1);
        p0.set(C_FLOAT,  8, val2);
        p0.set(C_FLOAT, 12, val3);
        p0.set(C_FLOAT, 16, val4);
        p0.set(C_FLOAT, 20, val5);
        p0.set(C_FLOAT, 24, val6);
        return p0;
    }

    public static MemorySegment addFloatToStructAfterFloats(
            float f1, float f2, float f3, float f4, float f5,
            float f6, float f7, float f8, float f9, float f10,
            float f11, float f12, MemorySegment s, float f) {
        float val = s.get(C_FLOAT, 0);
        s.set(C_FLOAT, 0, val + f);
        return s;
    }

    public static MemorySegment addFloatToStructAfterStructs(
            MemorySegment s1, MemorySegment s2, MemorySegment s3,
            MemorySegment s4, MemorySegment s5, MemorySegment s6,
            MemorySegment s, float f) {
        float val = s.get(C_FLOAT, 0);
        s.set(C_FLOAT, 0, val + f);
        return s;
    }

    public static MemorySegment addDoubleToStructAfterStructs(
            MemorySegment s1, MemorySegment s2, MemorySegment s3,
            MemorySegment s4, MemorySegment s5, MemorySegment s6,
            MemorySegment s, double f) {
        float val = s.get(C_FLOAT, 0);
        s.set(C_FLOAT, 0, val + (float) f);
        return s;
    }

    @Test
    public static void testAddFloatStructsUpcall() {
        float p0 = 0.0f, p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f, p5 = 0.0f, p6 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFFFFFFLayout);
            s.set(C_FLOAT,  0, 1.0f);
            s.set(C_FLOAT,  4, 2.0f);
            s.set(C_FLOAT,  8, 3.0f);
            s.set(C_FLOAT, 12, 4.0f);
            s.set(C_FLOAT, 16, 5.0f);
            s.set(C_FLOAT, 20, 6.0f);
            s.set(C_FLOAT, 24, 7.0f);
            MethodType mt = MethodType.methodType(MemorySegment.class,
                                                  MemorySegment.class, MemorySegment.class);
            MemorySegment stub = abi.upcallStub(MethodHandles.lookup().findStatic(TestHFA.class, "addFloatStructs", mt),
                                                fdadd_float_structs, arena);
            s = (MemorySegment)mhpass_two_large_structs.invokeExact((SegmentAllocator)arena, stub, s, s);
            p0 = s.get(C_FLOAT,  0);
            p1 = s.get(C_FLOAT,  4);
            p2 = s.get(C_FLOAT,  8);
            p3 = s.get(C_FLOAT, 12);
            p4 = s.get(C_FLOAT, 16);
            p5 = s.get(C_FLOAT, 20);
            p6 = s.get(C_FLOAT, 24);
            System.out.println("S_FFFFFFF(" + p0 + ";" + p1 + ";" + p2 + ";" + p3 + ";" + p4 + ";" + p5 + ";" + p6 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 4.0f || p2 != 6.0f || p3 != 8.0f || p4 != 10.0f || p5 != 12.0f || p6 != 14.0f)
            throw new RuntimeException("add_float_structs (Upcall)");
    }

    @Test
    public static void testAddFloatToStructAfterFloatsUpcall() {
        float p0 = 0.0f, p1 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 1.0f);
            MethodType mt = MethodType.methodType(MemorySegment.class,
                                                  float.class, float.class, float.class, float.class,
                                                  float.class, float.class, float.class, float.class,
                                                  float.class, float.class, float.class, float.class,
                                                  MemorySegment.class, float.class);
            MemorySegment stub = abi.upcallStub(MethodHandles.lookup().findStatic(TestHFA.class, "addFloatToStructAfterFloats", mt),
                                                fdadd_float_to_struct_after_floats, arena);
            s = (MemorySegment)mhpass_struct_after_floats.invokeExact((SegmentAllocator)arena, stub, s, 1.0f);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_float_to_struct_after_floats (Upcall)");
    }

    @Test
    public static void testAddFloatToStructAfterStructsUpcall() {
        float p0 = 0.0f, p1 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 1.0f);
            MethodType mt = MethodType.methodType(MemorySegment.class,
                                                  MemorySegment.class, MemorySegment.class, MemorySegment.class,
                                                  MemorySegment.class, MemorySegment.class, MemorySegment.class,
                                                  MemorySegment.class, float.class);
            MemorySegment stub = abi.upcallStub(MethodHandles.lookup().findStatic(TestHFA.class, "addFloatToStructAfterStructs", mt),
                                                fdadd_float_to_struct_after_structs, arena);
            s = (MemorySegment)mhpass_struct_after_structs.invokeExact((SegmentAllocator)arena, stub, s, 1.0f);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_float_to_struct_after_structs (Upcall)");
    }

    @Test
    public static void testAddDoubleToStructAfterStructsUpcall() {
        float p0 = 0.0f, p1 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFLayout);
            s.set(C_FLOAT, 0, 1.0f);
            s.set(C_FLOAT, 4, 1.0f);
            MethodType mt = MethodType.methodType(MemorySegment.class,
                                                  MemorySegment.class, MemorySegment.class, MemorySegment.class,
                                                  MemorySegment.class, MemorySegment.class, MemorySegment.class,
                                                  MemorySegment.class, double.class);
            MemorySegment stub = abi.upcallStub(MethodHandles.lookup().findStatic(TestHFA.class, "addDoubleToStructAfterStructs", mt),
                                                fdadd_double_to_struct_after_structs, arena);
            s = (MemorySegment)mhpass_struct_after_structs_plus_double.invokeExact((SegmentAllocator)arena, stub, s, 1.0d);
            p0 = s.get(C_FLOAT, 0);
            p1 = s.get(C_FLOAT, 4);
            System.out.println("S_FF(" + p0 + ";" + p1 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 1.0f) throw new RuntimeException("add_double_to_struct_after_structs (Upcall)");
    }

    @Test
    public static void testAddFloatToLargeStructAfterStructsUpcall() {
        float p0 = 0.0f, p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f, p5 = 0.0f, p6 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(S_FFFFFFFLayout);
            s.set(C_FLOAT,  0, 1.0f);
            s.set(C_FLOAT,  4, 2.0f);
            s.set(C_FLOAT,  8, 3.0f);
            s.set(C_FLOAT, 12, 4.0f);
            s.set(C_FLOAT, 16, 5.0f);
            s.set(C_FLOAT, 20, 6.0f);
            s.set(C_FLOAT, 24, 7.0f);
            MethodType mt = MethodType.methodType(MemorySegment.class,
                                                  MemorySegment.class, MemorySegment.class, MemorySegment.class,
                                                  MemorySegment.class, MemorySegment.class, MemorySegment.class,
                                                  MemorySegment.class, float.class);
            MemorySegment stub = abi.upcallStub(MethodHandles.lookup().findStatic(TestHFA.class, "addFloatToStructAfterStructs", mt),
                                                fdadd_float_to_large_struct_after_structs, arena);
            s = (MemorySegment)mhpass_large_struct_after_structs.invokeExact((SegmentAllocator)arena, stub, s, 1.0f);
            p0 = s.get(C_FLOAT,  0);
            p1 = s.get(C_FLOAT,  4);
            p2 = s.get(C_FLOAT,  8);
            p3 = s.get(C_FLOAT, 12);
            p4 = s.get(C_FLOAT, 16);
            p5 = s.get(C_FLOAT, 20);
            p6 = s.get(C_FLOAT, 24);
            System.out.println("S_FFFFFFF(" + p0 + ";" + p1 + ";" + p2 + ";" + p3 + ";" + p4 + ";" + p5 + ";" + p6 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2.0f || p1 != 2.0f || p2 != 3.0f || p3 != 4.0f || p4 != 5.0f || p5 != 6.0f || p6 != 7.0f)
            throw new RuntimeException("add_float_to_large_struct_after_structs (Upcall)");
    }
}
