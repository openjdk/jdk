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
 * @enablePreview
 * @library ../
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64" | os.arch == "riscv64"
 * @modules java.base/jdk.internal.foreign
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestLargeStub
 */

import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicReference;

public class TestLargeStub extends NativeTestHelper {
    static {
        System.loadLibrary("LargeStub");
    }

    @Test
    public void testPCAssert() throws Throwable {
        FunctionDescriptor upcallDesc = FunctionDescriptor.of(S6,
            C_POINTER, C_SHORT, C_LONG_LONG, C_INT, C_SHORT, S5, C_LONG_LONG, C_CHAR, C_DOUBLE, C_CHAR,
            C_FLOAT, C_CHAR, C_POINTER, C_CHAR, S6, U3, C_DOUBLE, C_INT, C_DOUBLE, C_CHAR,
            U5, C_INT);
        FunctionDescriptor downcallDesc = upcallDesc.insertArgumentLayouts(0, C_POINTER); // CB
        try (Arena arena = Arena.openConfined()) {
            NativeTestHelper.TestValue[] testArgs = genTestArgs(upcallDesc, arena);

            MethodHandle downcallHandle = downcallHandle("F84", downcallDesc);
            Object[] args = new Object[downcallDesc.argumentLayouts().size() + 1]; // +1 for return allocator
            AtomicReference<Object[]> returnBox = new AtomicReference<>();
            int returnIdx = 14;
            int argIdx = 0;
            args[argIdx++] = arena;
            args[argIdx++] = makeArgSaverCB(upcallDesc, arena, returnBox, returnIdx);
            for (NativeTestHelper.TestValue testArg : testArgs) {
                args[argIdx++] = testArg.value();
            }

            MemorySegment returned = (MemorySegment) downcallHandle.invokeWithArguments(args);

            testArgs[returnIdx].check().accept(returned);

            Object[] capturedArgs = returnBox.get();
            for (int i = 0; i < testArgs.length; i++) {
                testArgs[i].check().accept(capturedArgs[i]);
            }
        }
    }

    static final StructLayout S1 = MemoryLayout.structLayout(
        C_SHORT.withName("f0"),
        MemoryLayout.paddingLayout(48),
        C_DOUBLE.withName("f1"),
        C_DOUBLE.withName("f2"),
        C_SHORT.withName("f3"),
        MemoryLayout.paddingLayout(48)
    ).withName("S1");
    static final StructLayout S2 = MemoryLayout.structLayout(
        C_DOUBLE.withName("f0"),
        C_LONG_LONG.withName("f1"),
        C_INT.withName("f2"),
        MemoryLayout.paddingLayout(32),
        C_POINTER.withName("f3")
    ).withName("S2");
    static final StructLayout S3 = MemoryLayout.structLayout(
        C_FLOAT.withName("f0"),
        MemoryLayout.paddingLayout(32),
        S2.withName("f1"),
        C_LONG_LONG.withName("f2"),
        C_POINTER.withName("f3")
    ).withName("S3");
    static final UnionLayout U1 = MemoryLayout.unionLayout(
        MemoryLayout.sequenceLayout(4, C_FLOAT).withName("f0"),
        C_INT.withName("f1"),
        S3.withName("f2")
    ).withName("S4");
    static final StructLayout S5 = MemoryLayout.structLayout(
        C_INT.withName("f0"),
        MemoryLayout.paddingLayout(32),
        MemoryLayout.sequenceLayout(3, MemoryLayout.sequenceLayout(4, S1)).withName("f1"),
        U1.withName("f2"),
        C_DOUBLE.withName("f3")
    ).withName("S5");
    static final StructLayout S6 = MemoryLayout.structLayout(
        C_CHAR.withName("f0"),
        MemoryLayout.paddingLayout(56),
        C_DOUBLE.withName("f1"),
        C_SHORT.withName("f2"),
        C_CHAR.withName("f3"),
        MemoryLayout.paddingLayout(40)
    ).withName("S6");
    static final UnionLayout U2 = MemoryLayout.unionLayout(
        C_POINTER.withName("f0"),
        C_FLOAT.withName("f1"),
        C_INT.withName("f2")
    ).withName("U1");
    static final UnionLayout U3 = MemoryLayout.unionLayout(
        U2.withName("f0")
    ).withName("U2");
    static final UnionLayout U4 = MemoryLayout.unionLayout(
        C_LONG_LONG.withName("f0"),
        C_SHORT.withName("f1"),
        C_FLOAT.withName("f2")
    ).withName("U3");
    static final UnionLayout U5 = MemoryLayout.unionLayout(
        U4.withName("f0")
    ).withName("U4");

}
