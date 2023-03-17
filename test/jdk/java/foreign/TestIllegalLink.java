/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64" | os.arch == "riscv64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestIllegalLink
 */

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestIllegalLink extends NativeTestHelper {

    private static final MemorySegment DUMMY_TARGET = MemorySegment.ofAddress(1);
    private static final MethodHandle DUMMY_TARGET_MH = MethodHandles.empty(MethodType.methodType(void.class));
    private static final Linker ABI = Linker.nativeLinker();

    @Test(dataProvider = "types")
    public void testIllegalLayouts(FunctionDescriptor desc, String expectedExceptionMessage) {
        try {
            ABI.downcallHandle(DUMMY_TARGET, desc);
            fail("Expected IllegalArgumentException was not thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(expectedExceptionMessage),
                    e.getMessage() + " != " + expectedExceptionMessage);
        }
    }

    @Test(dataProvider = "downcallOnlyOptions",
          expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*Not supported for upcall.*")
    public void testIllegalUpcallOptions(Linker.Option downcallOnlyOption) {
        ABI.upcallStub(DUMMY_TARGET_MH, FunctionDescriptor.ofVoid(), Arena.ofAuto(), downcallOnlyOption);
    }

    @Test(dataProvider = "illegalCaptureState",
          expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*Unknown name.*")
    public void testIllegalCaptureState(String name) {
        Linker.Option.captureCallState(name);
    }

    // where

    @DataProvider
    public static Object[][] illegalCaptureState() {
        if (!IS_WINDOWS) {
            return new Object[][]{
                { "GetLastError" },
                { "WSAGetLastError" },
            };
        }
        return new Object[][]{};
    }

    @DataProvider
    public static Object[][] downcallOnlyOptions() {
        return new Object[][]{
            { Linker.Option.firstVariadicArg(0) },
            { Linker.Option.captureCallState("errno") },
            { Linker.Option.isTrivial() },
        };
    }

    @DataProvider
    public static Object[][] types() {
        return new Object[][]{
            {
                    FunctionDescriptor.of(MemoryLayout.paddingLayout(64)),
                    "Unsupported layout: x64"
            },
            {
                    FunctionDescriptor.ofVoid(MemoryLayout.paddingLayout(64)),
                    "Unsupported layout: x64"
            },
            {
                    FunctionDescriptor.of(MemoryLayout.sequenceLayout(2, C_INT)),
                    "Unsupported layout: [2:i32]"
            },
            {
                    FunctionDescriptor.ofVoid(MemoryLayout.sequenceLayout(2, C_INT)),
                    "Unsupported layout: [2:i32]"
            },
            {
                    FunctionDescriptor.ofVoid(C_INT.withBitAlignment(16)),
                    "Layout bit alignment must be natural alignment"
            },
            {
                    FunctionDescriptor.ofVoid(C_POINTER.withBitAlignment(16)),
                    "Layout bit alignment must be natural alignment"
            },
            {
                    FunctionDescriptor.ofVoid(ValueLayout.JAVA_CHAR.withBitAlignment(32)),
                    "Layout bit alignment must be natural alignment"
            },
            {
                    FunctionDescriptor.ofVoid(MemoryLayout.structLayout(
                            C_CHAR.withName("x").withBitAlignment(8),
                            C_SHORT.withName("y").withBitAlignment(8),
                            C_INT.withName("z").withBitAlignment(8)
                            ).withBitAlignment(8)),
                    "Layout bit alignment must be natural alignment"
            },
            {
                    FunctionDescriptor.ofVoid(MemoryLayout.structLayout(
                            MemoryLayout.structLayout(
                                C_CHAR.withName("x").withBitAlignment(8),
                                C_SHORT.withName("y").withBitAlignment(8),
                                C_INT.withName("z").withBitAlignment(8)
                            ))),
                    "Layout bit alignment must be natural alignment"
            },
            {
                    FunctionDescriptor.ofVoid(MemoryLayout.structLayout(
                            MemoryLayout.sequenceLayout(
                                C_INT.withBitAlignment(8)
                            ))),
                    "Layout bit alignment must be natural alignment"
            },
        };
    }

}
