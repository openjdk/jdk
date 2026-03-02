/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8378559
 * @summary Verify that downcall callers can reset errno before the call.
 * @requires vm.bits == 64
 * @library /test/lib
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestDowncallErrnoReset
 */

import module java.base;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestDowncallErrnoReset {

    @Test
    public void errnoCapturedProperly() throws Throwable {
        Linker nativeLinker = Linker.nativeLinker();

        // Create a VarHandle for errno.
        Linker.Option ccs = Linker.Option.captureCallState("errno");
        StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
        VarHandle errnoHandle = capturedStateLayout.varHandle(MemoryLayout.PathElement.groupElement("errno"));

        // long int strtol (const char* str, char** endptr, int base);
        FunctionDescriptor strtol = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                          ValueLayout.ADDRESS,
                                                          ValueLayout.ADDRESS,
                                                          ValueLayout.JAVA_INT);
        MethodHandle methodHandle = nativeLinker
                                    .defaultLookup()
                                    .find("strtol")
                                    .map(symbolSegment ->
                                         nativeLinker.downcallHandle(symbolSegment, strtol, ccs))
                                    .orElse(null);
        assertNotNull(methodHandle);


        // Get the memory and perform the function calls, one good weather and one bad weather test case.
        try (Arena memorySession = Arena.ofConfined()) {
            MemorySegment capturedState = memorySession.allocate(capturedStateLayout);
            Boilerplate boilerplate = new Boilerplate(methodHandle, errnoHandle, capturedState);

            // 65535, errno 0
            MemorySegment withinLong = memorySession.allocateFrom("ffff");
            makeErrnoSettingCall(boilerplate, withinLong, 65535, 0);

            // -1, errno 34
            MemorySegment overflowLong = memorySession.allocateFrom("fffffffffffffffffffffffffff");
            makeErrnoSettingCall(boilerplate, overflowLong, -1, /* ERANGE */ 34);
        }
    }

    // Does a downcall with the argument and asserts the results are expected.
    private static void makeErrnoSettingCall(Boilerplate bp,
                                             MemorySegment argument,
                                             int expectedResult,
                                             int expectedErrno) throws Throwable {
        // A program tht uses errno sets it to zero before the libray call, and then
        // checks the value after the library call.
        bp.setErrnoZero();

        int result = bp.strtol(argument, MemorySegment.NULL, 16);
        assertEquals(expectedResult, result);

        // This is only not going to be the expected value of HotSpot does not capture
        // errno properly.
        int errno = bp.getErrno();
        assertEquals(expectedErrno, errno);
    }

    private record Boilerplate(MethodHandle fn, VarHandle errno, MemorySegment capturedState) {
        private int strtol(MemorySegment argument, MemorySegment nil, int format) throws Throwable {
            return (int) fn.invoke(capturedState, argument, nil, format);
        }

        private void setErrnoZero() {
            errno.set(capturedState, 0L, 0);
        }

        private int getErrno() {
            return (int) errno.get(capturedState, 0L);
        }
    }
}
