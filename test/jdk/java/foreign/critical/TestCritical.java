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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestCritical
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.function.IntFunction;

import static org.testng.Assert.assertEquals;

public class TestCritical extends NativeTestHelper {

    static {
        System.loadLibrary("Critical");
    }

    @Test
    public void testEmpty() throws Throwable {
        MethodHandle handle = downcallHandle("empty", FunctionDescriptor.ofVoid(), Linker.Option.critical(false));
        handle.invokeExact();
    }

    @Test
    public void testIdentity() throws Throwable {
        MethodHandle handle = downcallHandle("identity", FunctionDescriptor.of(C_INT, C_INT), Linker.Option.critical(false));
        int result = (int) handle.invokeExact(42);
        assertEquals(result, 42);
    }

    @Test
    public void testWithReturnBuffer() throws Throwable {
        StructLayout bigLayout = MemoryLayout.structLayout(
                C_LONG_LONG.withName("x"),
                C_LONG_LONG.withName("y"));

        MethodHandle handle = downcallHandle("with_return_buffer", FunctionDescriptor.of(bigLayout), Linker.Option.critical(false));
        VarHandle vhX = bigLayout.varHandle(MemoryLayout.PathElement.groupElement("x"));
        VarHandle vhY = bigLayout.varHandle(MemoryLayout.PathElement.groupElement("y"));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = (MemorySegment) handle.invokeExact((SegmentAllocator) arena);
            long x = (long) vhX.get(result, 0L);
            assertEquals(x, 10);
            long y = (long) vhY.get(result, 0L);
            assertEquals(y, 11);
        }
    }

    record AllowHeapCase(IntFunction<MemorySegment> newArray, ValueLayout elementLayout) {}

    @Test(dataProvider = "allowHeapCases")
    public void testAllowHeap(AllowHeapCase testCase) throws Throwable {
        MethodHandle handle = downcallHandle("test_allow_heap", FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_LONG_LONG), Linker.Option.critical(true));
        int elementCount = 10;
        MemorySegment heapSegment = testCase.newArray().apply(elementCount);
        SequenceLayout sequence = MemoryLayout.sequenceLayout(elementCount, testCase.elementLayout());

        try (Arena arena = Arena.ofConfined()) {
            TestValue tv = genTestValue(sequence, arena);

            handle.invoke(heapSegment, tv.value(), sequence.byteSize());

            // check that writes went through to array
            tv.check().accept(heapSegment);
        }
    }

    @DataProvider
    public Object[][] allowHeapCases() {
        return new Object[][] {
            { new AllowHeapCase(i -> MemorySegment.ofArray(new byte[i]), ValueLayout.JAVA_BYTE) },
            { new AllowHeapCase(i -> MemorySegment.ofArray(new short[i]), ValueLayout.JAVA_SHORT) },
            { new AllowHeapCase(i -> MemorySegment.ofArray(new char[i]), ValueLayout.JAVA_CHAR) },
            { new AllowHeapCase(i -> MemorySegment.ofArray(new int[i]), ValueLayout.JAVA_INT) },
            { new AllowHeapCase(i -> MemorySegment.ofArray(new long[i]), ValueLayout.JAVA_LONG) },
            { new AllowHeapCase(i -> MemorySegment.ofArray(new float[i]), ValueLayout.JAVA_FLOAT) },
            { new AllowHeapCase(i -> MemorySegment.ofArray(new double[i]), ValueLayout.JAVA_DOUBLE) },
        };
    }
}
