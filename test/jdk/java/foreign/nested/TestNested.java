/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../ /test/lib
 * @requires jdk.foreign.linker != "FALLBACK"
 * @build NativeTestHelper
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestNested
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class TestNested extends NativeTestHelper {

    static {
        System.loadLibrary("Nested");
    }

    @Test(dataProvider = "nestedLayouts")
    public void testNested(GroupLayout layout) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Random random = new Random(0);
            TestValue testValue = genTestValue(random, layout, arena);

            String funcName = "test_" + layout.name().orElseThrow();
            FunctionDescriptor downcallDesc = FunctionDescriptor.of(layout, layout, C_POINTER);
            FunctionDescriptor upcallDesc = FunctionDescriptor.of(layout, layout);

            MethodHandle downcallHandle = downcallHandle(funcName, downcallDesc);
            AtomicReference<Object[]> returnBox = new AtomicReference<>();
            MemorySegment stub = makeArgSaverCB(upcallDesc, arena, returnBox, 0);

            MemorySegment returned = (MemorySegment) downcallHandle.invokeExact(
                    (SegmentAllocator) arena, (MemorySegment) testValue.value(), stub);

            testValue.check().accept(returnBox.get()[0]);
            testValue.check().accept(returned);
        }
    }

    @DataProvider
    public static Object[][] nestedLayouts() {
        List<GroupLayout> layouts = List.of(
                S1, U1, U17, S2, S3, S4, S5, S6, U2, S7, U3, U4, U5, U6, U7, S8, S9, U8, U9, U10, S10,
                U11, S11, U12, S12, U13, U14, U15, S13, S14, U16, S15);
        return layouts.stream().map(l -> new Object[]{l}).toArray(Object[][]::new);
    }

    static final StructLayout S1 = MemoryLayout.structLayout(
            C_DOUBLE.withName("f0"),
            C_LONG_LONG.withName("f1"),
            C_DOUBLE.withName("f2"),
            C_INT.withName("f3"),
            MemoryLayout.paddingLayout(4)
    ).withName("S1");
    static final UnionLayout U1 = MemoryLayout.unionLayout(
            C_SHORT.withName("f0"),
            C_LONG_LONG.withName("f1"),
            C_SHORT.withName("f2"),
            MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(3, C_CHAR)).withName("f3"),
            MemoryLayout.paddingLayout(16)
    ).withName("U1");
    static final UnionLayout U17 = MemoryLayout.unionLayout(
            C_CHAR.withName("f0"),
            C_CHAR.withName("f1"),
            C_LONG_LONG.withName("f2"),
            C_DOUBLE.withName("f3")
    ).withName("U17");
    static final StructLayout S2 = MemoryLayout.structLayout(
            U17.withName("f0"),
            MemoryLayout.sequenceLayout(4, C_LONG_LONG).withName("f1"),
            C_SHORT.withName("f2"),
            MemoryLayout.paddingLayout(6)
    ).withName("S2");
    static final StructLayout S3 = MemoryLayout.structLayout(
            C_FLOAT.withName("f0"),
            C_INT.withName("f1"),
            U1.withName("f2"),
            S2.withName("f3")
    ).withName("S3");
    static final StructLayout S4 = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(2, C_SHORT).withName("f0"),
            MemoryLayout.paddingLayout(4),
            S1.withName("f1")
    ).withName("S4");
    static final StructLayout S5 = MemoryLayout.structLayout(
            C_FLOAT.withName("f0"),
            MemoryLayout.paddingLayout(4),
            C_POINTER.withName("f1"),
            S4.withName("f2")
    ).withName("S5");
    static final StructLayout S6 = MemoryLayout.structLayout(
            S5.withName("f0")
    ).withName("S6");
    static final UnionLayout U2 = MemoryLayout.unionLayout(
            C_FLOAT.withName("f0"),
            C_SHORT.withName("f1"),
            C_POINTER.withName("f2"),
            C_FLOAT.withName("f3")
    ).withName("U2");
    static final StructLayout S7 = MemoryLayout.structLayout(
            C_DOUBLE.withName("f0"),
            C_SHORT.withName("f1"),
            C_SHORT.withName("f2"),
            MemoryLayout.paddingLayout(4),
            C_LONG_LONG.withName("f3")
    ).withName("S7");
    static final UnionLayout U3 = MemoryLayout.unionLayout(
            C_POINTER.withName("f0"),
            U2.withName("f1"),
            C_LONG_LONG.withName("f2"),
            S7.withName("f3")
    ).withName("U3");
    static final UnionLayout U4 = MemoryLayout.unionLayout(
            C_FLOAT.withName("f0")
    ).withName("U4");
    static final UnionLayout U5 = MemoryLayout.unionLayout(
            U3.withName("f0"),
            MemoryLayout.sequenceLayout(3, C_LONG_LONG).withName("f1"),
            U4.withName("f2"),
            C_FLOAT.withName("f3")
    ).withName("U5");
    static final UnionLayout U6 = MemoryLayout.unionLayout(
            C_SHORT.withName("f0"),
            C_FLOAT.withName("f1"),
            U5.withName("f2"),
            C_SHORT.withName("f3")
    ).withName("U6");
    static final UnionLayout U7 = MemoryLayout.unionLayout(
            C_SHORT.withName("f0")
    ).withName("U7");
    static final StructLayout S8 = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(3, C_DOUBLE).withName("f0"),
            U7.withName("f1"),
            MemoryLayout.paddingLayout(6),
            C_POINTER.withName("f2"),
            C_POINTER.withName("f3")
    ).withName("S8");
    static final StructLayout S9 = MemoryLayout.structLayout(
            C_CHAR.withName("f0"),
            MemoryLayout.paddingLayout(7),
            MemoryLayout.sequenceLayout(2, C_DOUBLE).withName("f1"),
            C_CHAR.withName("f2"),
            MemoryLayout.paddingLayout(7),
            S8.withName("f3")
    ).withName("S9");
    static final UnionLayout U8 = MemoryLayout.unionLayout(
            C_LONG_LONG.withName("f0"),
            C_POINTER.withName("f1"),
            S9.withName("f2")
    ).withName("U8");
    static final UnionLayout U9 = MemoryLayout.unionLayout(
            C_INT.withName("f0"),
            C_DOUBLE.withName("f1"),
            MemoryLayout.sequenceLayout(2, C_SHORT).withName("f2"),
            C_LONG_LONG.withName("f3")
    ).withName("U9");
    static final UnionLayout U10 = MemoryLayout.unionLayout(
            C_LONG_LONG.withName("f0"),
            U9.withName("f1"),
            C_CHAR.withName("f2"),
            C_FLOAT.withName("f3")
    ).withName("U10");
    static final StructLayout S10 = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(4, C_DOUBLE).withName("f0")
    ).withName("S10");
    static final UnionLayout U11 = MemoryLayout.unionLayout(
            MemoryLayout.sequenceLayout(3, S10).withName("f0")
    ).withName("U11");
    static final StructLayout S11 = MemoryLayout.structLayout(
            C_SHORT.withName("f0"),
            C_CHAR.withName("f1"),
            MemoryLayout.paddingLayout(1)
    ).withName("S11");
    static final UnionLayout U12 = MemoryLayout.unionLayout(
            C_FLOAT.withName("f0"),
            S11.withName("f1"),
            C_CHAR.withName("f2"),
            C_CHAR.withName("f3")
    ).withName("U12");
    static final StructLayout S12 = MemoryLayout.structLayout(
            U12.withName("f0"),
            C_FLOAT.withName("f1")
    ).withName("S12");
    static final UnionLayout U13 = MemoryLayout.unionLayout(
            C_FLOAT.withName("f0"),
            S12.withName("f1")
    ).withName("U13");
    static final UnionLayout U14 = MemoryLayout.unionLayout(
            C_INT.withName("f0"),
            MemoryLayout.sequenceLayout(2, C_POINTER).withName("f1"),
            MemoryLayout.sequenceLayout(2, MemoryLayout.sequenceLayout(3, C_FLOAT)).withName("f2")
    ).withName("U14");
    static final UnionLayout U15 = MemoryLayout.unionLayout(
            C_POINTER.withName("f0"),
            C_LONG_LONG.withName("f1"),
            MemoryLayout.sequenceLayout(1, C_DOUBLE).withName("f2"),
            C_LONG_LONG.withName("f3")
    ).withName("U15");
    static final StructLayout S13 = MemoryLayout.structLayout(
            C_INT.withName("f0"),
            C_CHAR.withName("f1"),
            MemoryLayout.paddingLayout(3),
            C_POINTER.withName("f2"),
            C_CHAR.withName("f3"),
            MemoryLayout.paddingLayout(7)
    ).withName("S13");
    static final StructLayout S14 = MemoryLayout.structLayout(
            C_LONG_LONG.withName("f0")
    ).withName("S14");
    static final UnionLayout U16 = MemoryLayout.unionLayout(
            MemoryLayout.sequenceLayout(4, C_SHORT).withName("f0"),
            C_INT.withName("f1"),
            S13.withName("f2"),
            S14.withName("f3")
    ).withName("U16");
    static final StructLayout S15 = MemoryLayout.structLayout(
            U16.withName("f0"),
            C_FLOAT.withName("f1"),
            C_INT.withName("f2"),
            C_LONG_LONG.withName("f3")
    ).withName("S15");
}
