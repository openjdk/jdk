/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestMixedMallocFree
 */

import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.assertEquals;

public class TestMixedMallocFree extends NativeTestHelper {

    static final MethodHandle MH_my_malloc;

    static {
        System.loadLibrary("Malloc");
        MH_my_malloc = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("my_malloc"),
                FunctionDescriptor.of(C_POINTER, C_LONG_LONG));
    }

    @Test
    public void testMalloc() throws Throwable {
        MemoryAddress ma = (MemoryAddress) MH_my_malloc.invokeExact(4L);
        MemorySegment seg = MemorySegment.ofAddress(ma, 4L, MemorySession.openImplicit());
        seg.set(JAVA_INT, 0, 42);
        assertEquals(seg.get(JAVA_INT, 0), 42);
        // Test if this free crashes the VM, which might be the case if we load the wrong default library
        // and end up mixing two allocators together.
        freeMemory(ma);
    }

}
