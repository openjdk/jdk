/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @library ../ /test/lib
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64" | os.arch == "riscv64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestCaptureCallState
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.testng.Assert.assertEquals;

public class TestCaptureCallState extends NativeTestHelper {

    static final MethodHandle MH_UPCALL_TARGET;

    static {
        System.loadLibrary("CaptureCallState");

        try {
            MH_UPCALL_TARGET = MethodHandles.lookup().findStatic(TestCaptureCallState.class, "upcallTarget",
                    MethodType.methodType(Object.class, MemorySegment.class, VarHandle.class, int.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private record SaveValuesCase(String nativeTarget, String threadLocalName, Optional<MemoryLayout> retValueLayout) {
        SaveValuesCase(String nativeTarget, String threadLocalName) {
            this(nativeTarget, threadLocalName, Optional.empty());
        }
        SaveValuesCase(String nativeTarget, String threadLocalName, MemoryLayout retValueLayout) {
            this(nativeTarget, threadLocalName, Optional.of(retValueLayout));
        }
    }

    @Test(dataProvider = "cases")
    public void testDowncalls(SaveValuesCase testCase) throws Throwable {
        Linker.Option stl = Linker.Option.captureCallState(testCase.threadLocalName());
        FunctionDescriptor downcallDesc = testCase.retValueLayout()
                .map(rl -> FunctionDescriptor.of(rl, JAVA_INT, rl))
                .orElse(FunctionDescriptor.ofVoid(JAVA_INT));
        MethodHandle handle = downcallHandle("set_" + testCase.nativeTarget(), downcallDesc, stl);

        StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
        VarHandle errnoHandle = capturedStateLayout.varHandle(groupElement(testCase.threadLocalName()));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment saveSeg = arena.allocate(capturedStateLayout);
            TestValue captureStateValue = genTestValue(JAVA_INT, arena);
            TestValue testValue = testCase.retValueLayout().map(rl -> genTestValue(rl, arena)).orElse(null);

            boolean needsAllocator = testCase.retValueLayout().map(StructLayout.class::isInstance).orElse(false);
            Object result = needsAllocator
                ? handle.invoke(arena, saveSeg, captureStateValue.value(), testValue.value())
                : testValue != null
                    ? handle.invoke(saveSeg, captureStateValue.value(), testValue.value())
                    : handle.invoke(saveSeg, captureStateValue.value());

            if (testValue != null) {
                testValue.check().accept(result);
            }

            int savedErrno = (int) errnoHandle.get(saveSeg);
            captureStateValue.check().accept(savedErrno);
        }
    }

    @Test(dataProvider = "cases")
    public void testUpcalls(SaveValuesCase testCase) throws Throwable {
        FunctionDescriptor downcallDesc = testCase.retValueLayout()
                .map(rl -> FunctionDescriptor.of(rl, ADDRESS, ADDRESS))
                .orElse(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        MethodHandle handle = downcallHandle("get_" + testCase.nativeTarget(), downcallDesc);

        try (Arena arena = Arena.ofConfined()) {
            TestValue captureStateValue = genTestValue(JAVA_INT, arena);
            TestValue testValue = testCase.retValueLayout().map(rl -> genTestValue(rl, arena)).orElse(null);

            MemorySegment callback = makeCaptureStateCallback(arena, testCase.threadLocalName(),
                    testCase.retValueLayout(), (int) captureStateValue.value(),
                    testValue == null ? null : testValue.value());

            MemorySegment writeBack = arena.allocate(JAVA_INT);
            boolean needsAllocator = downcallDesc.returnLayout().map(StructLayout.class::isInstance).orElse(false);
            Object result = needsAllocator
                ? handle.invoke(arena, writeBack, callback)
                : handle.invoke(writeBack, callback);

            if (testValue != null) {
                testValue.check().accept(result);
            }

            int savedErrno = writeBack.get(JAVA_INT, 0);
            captureStateValue.check().accept(savedErrno);
        }
    }

    private MemorySegment makeCaptureStateCallback(Arena arena, String threadLocalName, Optional<MemoryLayout> retLayout,
                                                   int captureStateValue, Object testValue) {
        FunctionDescriptor upcallDesc = retLayout
            .map(FunctionDescriptor::of)
            .orElse(FunctionDescriptor.ofVoid());
        StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
        VarHandle errnoHandle = capturedStateLayout.varHandle(groupElement(threadLocalName));
        MethodHandle target = MethodHandles.insertArguments(MH_UPCALL_TARGET, 1, errnoHandle, captureStateValue, testValue);
        if (retLayout.isEmpty()) {
            target = MethodHandles.dropReturn(target);
        } else {
            target = target.asType(target.type().changeReturnType(carrierFor(retLayout.get())));
        }
        return LINKER.upcallStub(target, upcallDesc, arena,
                Linker.Option.captureCallState(threadLocalName));
    }

    private Class<?> carrierFor(MemoryLayout layout) {
        if (layout instanceof ValueLayout valueLayout) {
            return valueLayout.carrier();
        } else if (layout instanceof GroupLayout) {
            return MemorySegment.class;
        } else {
            throw new IllegalArgumentException("Unsupported layout: " + layout);
        }
    }

    public static Object upcallTarget(MemorySegment writeback, VarHandle writebackHandle, int captureStateValue,
                                      Object returnValue) {
        writebackHandle.set(writeback, captureStateValue);
        return returnValue;
    }

    @DataProvider
    public static Object[][] cases() {
        List<SaveValuesCase> cases = new ArrayList<>();

        cases.add(new SaveValuesCase("errno_V", "errno"));
        cases.add(new SaveValuesCase("errno_I", "errno", JAVA_INT));
        cases.add(new SaveValuesCase("errno_D", "errno", JAVA_DOUBLE));

        cases.add(new SaveValuesCase("errno_SL", "errno",
                MemoryLayout.structLayout(JAVA_LONG.withName("x")).withName("SL")));
        cases.add(new SaveValuesCase("errno_SLL", "errno",
                MemoryLayout.structLayout(JAVA_LONG.withName("x"),
                                          JAVA_LONG.withName("y")).withName("SLL")));
        cases.add(new SaveValuesCase("errno_SLLL", "errno",
                MemoryLayout.structLayout(JAVA_LONG.withName("x"),
                                          JAVA_LONG.withName("y"),
                                          JAVA_LONG.withName("z")).withName("SLLL")));
        cases.add(new SaveValuesCase("errno_SD", "errno",
                MemoryLayout.structLayout(JAVA_DOUBLE.withName("x")).withName("SD")));
        cases.add(new SaveValuesCase("errno_SDD", "errno",
                MemoryLayout.structLayout(JAVA_DOUBLE.withName("x"),
                                          JAVA_DOUBLE.withName("y")).withName("SDD")));
        cases.add(new SaveValuesCase("errno_SDDD", "errno",
                MemoryLayout.structLayout(JAVA_DOUBLE.withName("x"),
                                          JAVA_DOUBLE.withName("y"),
                                          JAVA_DOUBLE.withName("z")).withName("SDDD")));

        if (IS_WINDOWS) {
            cases.add(new SaveValuesCase("last_error", "LastError"));
            cases.add(new SaveValuesCase("wsa_last_error", "WSALastError"));
        }

        return cases.stream().map(tc -> new Object[] {tc}).toArray(Object[][]::new);
    }

}
