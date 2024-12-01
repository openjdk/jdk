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
 *
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;

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

    public record AllowHeapCase(IntFunction<MemorySegment> newArraySegment, ValueLayout elementLayout,
                                String fName, FunctionDescriptor fDesc, boolean readOnly) {}

    @Test(dataProvider = "allowHeapCases")
    public void testAllowHeap(AllowHeapCase testCase) throws Throwable {
        MethodHandle handle = downcallHandle(testCase.fName(), testCase.fDesc(), Linker.Option.critical(true));
        int elementCount = 10;
        MemorySegment heapSegment = testCase.newArraySegment().apply(elementCount);
        if (testCase.readOnly()) {
            heapSegment = heapSegment.asReadOnly();
        }
        SequenceLayout sequence = MemoryLayout.sequenceLayout(elementCount, testCase.elementLayout());

        try (Arena arena = Arena.ofConfined()) {
            TestValue[] tvs = genTestArgs(testCase.fDesc(), arena);
            Object[] args = Stream.of(tvs).map(TestValue::value).toArray();

            // inject our custom last three arguments
            args[args.length - 1] = (int) sequence.byteSize();
            TestValue sourceSegment = genTestValue(sequence, arena);
            args[args.length - 2] = sourceSegment.value();
            args[args.length - 3] = heapSegment;

            if (handle.type().parameterType(0) == SegmentAllocator.class) {
                Object[] newArgs = new Object[args.length + 1];
                newArgs[0] = arena;
                System.arraycopy(args, 0, newArgs, 1, args.length);
                args = newArgs;
            }

            Object o = handle.invokeWithArguments(args);

            if (o != null) {
                tvs[0].check(o);
            }

            // check that writes went through to array
            sourceSegment.check(heapSegment);
        }
    }

    @DataProvider
    public Object[][] allowHeapCases() {
        FunctionDescriptor voidDesc = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT);
        FunctionDescriptor intDesc = voidDesc.changeReturnLayout(C_INT).insertArgumentLayouts(0, C_INT);
        StructLayout L2 = MemoryLayout.structLayout(
            C_LONG_LONG.withName("x"),
            C_LONG_LONG.withName("y")
        );
        FunctionDescriptor L2Desc = voidDesc.changeReturnLayout(L2).insertArgumentLayouts(0, L2);
        StructLayout L3 = MemoryLayout.structLayout(
            C_LONG_LONG.withName("x"),
            C_LONG_LONG.withName("y"),
            C_LONG_LONG.withName("z")
        );
        FunctionDescriptor L3Desc = voidDesc.changeReturnLayout(L3).insertArgumentLayouts(0, L3);
        FunctionDescriptor stackDesc = voidDesc.insertArgumentLayouts(0,
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
                C_CHAR, C_SHORT, C_INT);

        List<AllowHeapCase> cases = new ArrayList<>();

        for (HeapSegmentFactory hsf : HeapSegmentFactory.values()) {
            cases.add(new AllowHeapCase(hsf.newArray, hsf.elementLayout, "test_allow_heap_void", voidDesc, false));
            cases.add(new AllowHeapCase(hsf.newArray, hsf.elementLayout, "test_allow_heap_int", intDesc, false));
            cases.add(new AllowHeapCase(hsf.newArray, hsf.elementLayout, "test_allow_heap_return_buffer", L2Desc, false));
            cases.add(new AllowHeapCase(hsf.newArray, hsf.elementLayout, "test_allow_heap_imr", L3Desc, false));
            cases.add(new AllowHeapCase(hsf.newArray, hsf.elementLayout, "test_allow_heap_void_stack", stackDesc, false));
            // readOnly
            cases.add(new AllowHeapCase(hsf.newArray, hsf.elementLayout, "test_allow_heap_void", voidDesc, true));
        }

        return cases.stream().map(e -> new Object[]{ e }).toArray(Object[][]::new);
    }

    private enum HeapSegmentFactory {
        BYTE(i -> MemorySegment.ofArray(new byte[i]), ValueLayout.JAVA_BYTE),
        SHORT(i -> MemorySegment.ofArray(new short[i]), ValueLayout.JAVA_SHORT),
        CHAR(i -> MemorySegment.ofArray(new char[i]), ValueLayout.JAVA_CHAR),
        INT(i -> MemorySegment.ofArray(new int[i]), ValueLayout.JAVA_INT),
        LONG(i -> MemorySegment.ofArray(new long[i]), ValueLayout.JAVA_LONG),
        FLOAT(i -> MemorySegment.ofArray(new float[i]), ValueLayout.JAVA_FLOAT),
        DOUBLE(i -> MemorySegment.ofArray(new double[i]), ValueLayout.JAVA_DOUBLE);

        IntFunction<MemorySegment> newArray;
        ValueLayout elementLayout;

        private HeapSegmentFactory(IntFunction<MemorySegment> newArray, ValueLayout elementLayout) {
            this.newArray = newArray;
            this.elementLayout = elementLayout;
        }
    }
}
