/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../
 * @run testng/othervm
 *   --enable-native-access=ALL-UNNAMED
 *   TestVirtualCalls
 */

import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import org.testng.annotations.*;

import static org.testng.Assert.assertEquals;

public class TestVirtualCalls extends NativeTestHelper {

    static final Linker abi = Linker.nativeLinker();

    static final MethodHandle func;
    static final MemorySegment funcA;
    static final MemorySegment funcB;
    static final MemorySegment funcC;

    static {
        func = abi.downcallHandle(
                FunctionDescriptor.of(C_INT));

        System.loadLibrary("Virtual");
        funcA = findNativeOrThrow("funcA");
        funcB = findNativeOrThrow("funcB");
        funcC = findNativeOrThrow("funcC");
    }

    @Test
    public void testVirtualCalls() throws Throwable {
        assertEquals((int) func.invokeExact(funcA), 1);
        assertEquals((int) func.invokeExact(funcB), 2);
        assertEquals((int) func.invokeExact(funcC), 3);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullTarget() throws Throwable {
        int x = (int) func.invokeExact((MemorySegment)null);
    }

}
