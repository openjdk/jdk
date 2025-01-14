/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024 SAP SE. All rights reserved.
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
 * @summary Test passing of a structure which contains a double with 4 Byte alignment on AIX.
 *
 * @run testng/othervm --enable-native-access=ALL-UNNAMED Test4BAlignedDouble
 */

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.*;

public class Test4BAlignedDouble {

    static {
      System.loadLibrary("Test4BAlignedDouble");
    }

    static final Linker abi = Linker.nativeLinker();
    static final SymbolLookup lookup = SymbolLookup.loaderLookup();
    static final boolean isAix = System.getProperty("os.name").equals("AIX");

    static final OfInt C_INT = JAVA_INT;
    static final OfFloat C_FLOAT = JAVA_FLOAT;
    static final OfDouble C_DOUBLE = JAVA_DOUBLE;
    // Double with platform specific alignment rule. Can be used on AIX with #pragma align (power).
    static final OfDouble C_DOUBLE4B = JAVA_DOUBLE.withByteAlignment(4);
    static final OfDouble platform_C_DOUBLE = isAix ? C_DOUBLE4B : C_DOUBLE;

    static final StructLayout S_IDFLayout_with_padding = MemoryLayout.structLayout(
        C_INT.withName("p0"),
        MemoryLayout.paddingLayout(4), // AIX: only with #pragma align (natural)
        C_DOUBLE.withName("p1"),
        C_FLOAT.withName("p2"),
        MemoryLayout.paddingLayout(4)
    ).withName("S_IDF");

    static final StructLayout S_IDFLayout_without_padding = MemoryLayout.structLayout(
        C_INT.withName("p0"),
        // AIX uses #pragma align (power) by default. This means no padding, here.
        C_DOUBLE4B.withName("p1"),
        C_FLOAT.withName("p2")
    ).withName("S_IDF");

    static final StructLayout platform_S_IDFLayout = isAix ? S_IDFLayout_without_padding : S_IDFLayout_with_padding;

    static final long p0_offs = platform_S_IDFLayout.byteOffset(PathElement.groupElement("p0")),
                      p1_offs = platform_S_IDFLayout.byteOffset(PathElement.groupElement("p1")),
                      p2_offs = platform_S_IDFLayout.byteOffset(PathElement.groupElement("p2"));

    static final FunctionDescriptor fdpass_S_IDF = FunctionDescriptor.of(platform_S_IDFLayout, platform_S_IDFLayout);

    static final MethodHandle mhpass_S_IDF = abi.downcallHandle(lookup.find("pass_S_IDF").orElseThrow(), fdpass_S_IDF);
    static final MethodHandle mhpass_S_IDF_fun = abi.downcallHandle(lookup.find("call_S_IDF_fun").orElseThrow(),
        FunctionDescriptor.of(platform_S_IDFLayout, ADDRESS, platform_S_IDFLayout));

    @Test
    public static void testDowncall() {
        int p0 = 0;
        double p1 = 0.0d;
        float p2 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(platform_S_IDFLayout);
            s.set(C_INT, p0_offs, 1);
            s.set(platform_C_DOUBLE, p1_offs, 2.0d);
            s.set(C_FLOAT, p2_offs, 3.0f);
            s = (MemorySegment) mhpass_S_IDF.invokeExact((SegmentAllocator) arena, s);
            p0 = s.get(C_INT, p0_offs);
            p1 = s.get(platform_C_DOUBLE, p1_offs);
            p2 = s.get(C_FLOAT, p2_offs);
            System.out.println("S_IDF(" + p0 + ";" + p1 + ";" + p2 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2 || p1 != 5.0d || p2 != 3.0f) throw new RuntimeException("pass_S_IDF downcall error");
    }

    // Java version for Upcall test.
    public static MemorySegment S_IDF_fun(MemorySegment p) {
        int    p0 = p.get(C_INT,  p0_offs);
        double p1 = p.get(platform_C_DOUBLE, p1_offs);
        float  p2 = p.get(C_FLOAT, p2_offs);
        p.set(C_INT,  p0_offs, p0 + 1);
        p.set(platform_C_DOUBLE, p1_offs, p1 + (double) p2);
        return p;
    }

    @Test
    public static void testUpcall() {
        int p0 = 0;
        double p1 = 0.0d;
        float p2 = 0.0f;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(platform_S_IDFLayout);
            s.set(C_INT, p0_offs, 1);
            s.set(platform_C_DOUBLE, p1_offs, 2.0d);
            s.set(C_FLOAT, p2_offs, 3.0f);
            MethodType mt = MethodType.methodType(MemorySegment.class, MemorySegment.class);
            MemorySegment stub = abi.upcallStub(MethodHandles.lookup().findStatic(Test4BAlignedDouble.class, "S_IDF_fun", mt),
                                                fdpass_S_IDF, arena);
            s = (MemorySegment) mhpass_S_IDF_fun.invokeExact((SegmentAllocator) arena, stub, s);
            p0 = s.get(C_INT, p0_offs);
            p1 = s.get(platform_C_DOUBLE, p1_offs);
            p2 = s.get(C_FLOAT, p2_offs);
            System.out.println("S_IDF(" + p0 + ";" + p1 + ";" + p2 + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (p0 != 2 || p1 != 5.0d || p2 != 3.0f) throw new RuntimeException("pass_S_IDF upcall error");
    }

}
