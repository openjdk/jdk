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
 * @test id=specialized
 * @library ../
 * @requires (!(os.name == "Mac OS X" & os.arch == "aarch64") | jdk.foreign.linker != "FALLBACK")
 * @modules java.base/jdk.internal.foreign
 * @run testng/othervm
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestArrayStructs
 */

/*
 * @test id=interpreted
 * @library ../
 * @requires (!(os.name == "Mac OS X" & os.arch == "aarch64") | jdk.foreign.linker != "FALLBACK")
 * @modules java.base/jdk.internal.foreign
 * @run testng/othervm
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestArrayStructs
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemoryLayout.structLayout;

public class TestArrayStructs extends NativeTestHelper {
    static {
        System.loadLibrary("ArrayStructs");
    }

    // Test if structs of various different sizes, including non-powers of two, work correctly
    @Test(dataProvider = "arrayStructs")
    public void testArrayStruct(String functionName, FunctionDescriptor baseDesc, int numPrefixArgs, int numElements) throws Throwable {
        FunctionDescriptor downcallDesc = baseDesc.insertArgumentLayouts(0, C_POINTER); // CB
        MemoryLayout[] elementLayouts = Collections.nCopies(numElements, C_CHAR).toArray(MemoryLayout[]::new);
        FunctionDescriptor upcallDesc = baseDesc.appendArgumentLayouts(elementLayouts);
        try (Arena arena = Arena.ofConfined()) {
            TestValue[] testArgs = genTestArgs(baseDesc, arena);

            MethodHandle downcallHandle = downcallHandle(functionName, downcallDesc);
            Object[] args = new Object[downcallDesc.argumentLayouts().size() + 1]; // +1 for return allocator
            AtomicReference<Object[]> returnBox = new AtomicReference<>();
            int returnIdx = numPrefixArgs;
            int argIdx = 0;
            args[argIdx++] = arena;
            args[argIdx++] = makeArgSaverCB(upcallDesc, arena, returnBox, returnIdx);
            for (TestValue testArg : testArgs) {
                args[argIdx++] = testArg.value();
            }

            MemorySegment returned = (MemorySegment) downcallHandle.invokeWithArguments(args);
            Consumer<Object> structCheck = testArgs[returnIdx].check();

            structCheck.accept(returned);

            Object[] capturedArgs = returnBox.get();
            int capturedArgIdx;
            for (capturedArgIdx = numPrefixArgs; capturedArgIdx < testArgs.length; capturedArgIdx++) {
                testArgs[capturedArgIdx].check().accept(capturedArgs[capturedArgIdx]);
            }

            byte[] elements = new byte[numElements];
            for (int elIdx = 0; elIdx < numElements; elIdx++, capturedArgIdx++) {
                elements[elIdx] = (byte) capturedArgs[capturedArgIdx];
            }

            structCheck.accept(MemorySegment.ofArray(elements)); // reuse the check for the struct
        }
    }

    @DataProvider
    public static Object[][] arrayStructs() {
        List<Object[]> cases = new ArrayList<>();
        for (int i = 0; i < layouts.size(); i++) {
            StructLayout layout = layouts.get(i);
            int numElements = i + 1;
            cases.add(new Object[]{"F" + numElements, FunctionDescriptor.of(layout, layout), 0, numElements});
        }
        for (int i = 0; i < layouts.size(); i++) {
            StructLayout layout = layouts.get(i);
            MemoryLayout[] argLayouts = Stream.concat(PREFIX_LAYOUTS.stream(), Stream.of(layout)).toArray(MemoryLayout[]::new);
            int numElements = i + 1;
            cases.add(new Object[]{"F" + numElements + "_stack", FunctionDescriptor.of(layout, argLayouts), PREFIX_LAYOUTS.size(), numElements});
        }

        return cases.toArray(Object[][]::new);
    }

    static final List<MemoryLayout> PREFIX_LAYOUTS = List.of(
        C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG, C_LONG_LONG,
        C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE, C_DOUBLE);

    static final List<StructLayout> layouts = List.of(
        structLayout(sequenceLayout(1, C_CHAR).withName("f0")).withName("S1"),
        structLayout(sequenceLayout(2, C_CHAR).withName("f0")).withName("S2"),
        structLayout(sequenceLayout(3, C_CHAR).withName("f0")).withName("S3"),
        structLayout(sequenceLayout(4, C_CHAR).withName("f0")).withName("S4"),
        structLayout(sequenceLayout(5, C_CHAR).withName("f0")).withName("S5"),
        structLayout(sequenceLayout(6, C_CHAR).withName("f0")).withName("S6"),
        structLayout(sequenceLayout(7, C_CHAR).withName("f0")).withName("S7"),
        structLayout(sequenceLayout(8, C_CHAR).withName("f0")).withName("S8"),
        structLayout(sequenceLayout(9, C_CHAR).withName("f0")).withName("S9"),
        structLayout(sequenceLayout(10, C_CHAR).withName("f0")).withName("S10"),
        structLayout(sequenceLayout(11, C_CHAR).withName("f0")).withName("S11"),
        structLayout(sequenceLayout(12, C_CHAR).withName("f0")).withName("S12"),
        structLayout(sequenceLayout(13, C_CHAR).withName("f0")).withName("S13"),
        structLayout(sequenceLayout(14, C_CHAR).withName("f0")).withName("S14"),
        structLayout(sequenceLayout(15, C_CHAR).withName("f0")).withName("S15"),
        structLayout(sequenceLayout(16, C_CHAR).withName("f0")).withName("S16"));
}
