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
 * @library ../ /test/lib
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestTrivial
 */

import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static org.testng.Assert.assertEquals;

public class TestTrivial extends NativeTestHelper {

    static {
        System.loadLibrary("Trivial");
    }

    @Test
    public void testEmpty() throws Throwable {
        MethodHandle handle = downcallHandle("empty", FunctionDescriptor.ofVoid(), Linker.Option.isTrivial());
        handle.invokeExact();
    }

    @Test
    public void testIdentity() throws Throwable {
        MethodHandle handle = downcallHandle("identity", FunctionDescriptor.of(C_INT, C_INT), Linker.Option.isTrivial());
        int result = (int) handle.invokeExact(42);
        assertEquals(result, 42);
    }

    @Test
    public void testWithReturnBuffer() throws Throwable {
        StructLayout bigLayout = MemoryLayout.structLayout(
                C_LONG_LONG.withName("x"),
                C_LONG_LONG.withName("y"));

        MethodHandle handle = downcallHandle("with_return_buffer", FunctionDescriptor.of(bigLayout), Linker.Option.isTrivial());
        VarHandle vhX = bigLayout.varHandle(MemoryLayout.PathElement.groupElement("x"));
        VarHandle vhY = bigLayout.varHandle(MemoryLayout.PathElement.groupElement("y"));
        try (Arena arena  = Arena.ofConfined()) {
            MemorySegment result = (MemorySegment) handle.invokeExact((SegmentAllocator) arena);
            long x = (long) vhX.get(result);
            assertEquals(x, 10);
            long y = (long) vhY.get(result);
            assertEquals(y, 11);
        }
    }

    @Test
    public void testCaptureErrno() throws Throwable {
        Linker.Option ccs = Linker.Option.captureCallState("errno");
        MethodHandle handle = downcallHandle("capture_errno", FunctionDescriptor.ofVoid(C_INT), Linker.Option.isTrivial(), ccs);
        StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
        VarHandle errnoHandle = capturedStateLayout.varHandle(MemoryLayout.PathElement.groupElement("errno"));
        try (Arena arena  = Arena.ofConfined()) {
            MemorySegment captureSeg = arena.allocate(capturedStateLayout);
            handle.invokeExact(captureSeg, 42);
            int capturedErrno = (int) errnoHandle.get(captureSeg);
            assertEquals(capturedErrno, 42);
        }
    }


}
