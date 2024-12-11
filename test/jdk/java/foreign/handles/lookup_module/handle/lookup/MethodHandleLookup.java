/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

package handle.lookup;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.testng.annotations.*;

public class MethodHandleLookup {

    @Test(dataProvider = "restrictedMethods")
    public void testRestrictedHandles(MethodHandle handle, String testName) throws Throwable {
        new handle.invoker.MethodHandleInvoker().call(handle);
    }

    @DataProvider(name = "restrictedMethods")
    static Object[][] restrictedMethods() {
        try {
            return new Object[][]{
                    { MethodHandles.lookup().findVirtual(Linker.class, "downcallHandle",
                            MethodType.methodType(MethodHandle.class, FunctionDescriptor.class, Linker.Option[].class)), "Linker::downcallHandle/1" },
                    { MethodHandles.lookup().findVirtual(Linker.class, "downcallHandle",
                            MethodType.methodType(MethodHandle.class, MemorySegment.class, FunctionDescriptor.class, Linker.Option[].class)), "Linker::downcallHandle/2" },
                    { MethodHandles.lookup().findVirtual(Linker.class, "upcallStub",
                            MethodType.methodType(MemorySegment.class, MethodHandle.class, FunctionDescriptor.class, Arena.class, Linker.Option[].class)), "Linker::upcallStub" },
                    { MethodHandles.lookup().findVirtual(MemorySegment.class, "reinterpret",
                            MethodType.methodType(MemorySegment.class, long.class)),
                            "MemorySegment::reinterpret/1" },
                    { MethodHandles.lookup().findVirtual(MemorySegment.class, "reinterpret",
                            MethodType.methodType(MemorySegment.class, Arena.class, Consumer.class)),
                            "MemorySegment::reinterpret/2" },
                    { MethodHandles.lookup().findVirtual(MemorySegment.class, "reinterpret",
                            MethodType.methodType(MemorySegment.class, long.class, Arena.class, Consumer.class)),
                            "MemorySegment::reinterpret/3" },
                    { MethodHandles.lookup().findStatic(SymbolLookup.class, "libraryLookup",
                            MethodType.methodType(SymbolLookup.class, String.class, Arena.class)),
                            "SymbolLookup::libraryLookup(String)" },
                    { MethodHandles.lookup().findStatic(SymbolLookup.class, "libraryLookup",
                            MethodType.methodType(SymbolLookup.class, Path.class, Arena.class)),
                            "SymbolLookup::libraryLookup(Path)" },
                    { MethodHandles.lookup().findStatic(System.class, "load",
                            MethodType.methodType(void.class, String.class)),
                            "System::load" },
                    { MethodHandles.lookup().findStatic(System.class, "loadLibrary",
                            MethodType.methodType(void.class, String.class)),
                            "System::loadLibrary" },
                    { MethodHandles.lookup().findVirtual(Runtime.class, "load",
                            MethodType.methodType(void.class, String.class)),
                            "Runtime::load" },
                    { MethodHandles.lookup().findVirtual(Runtime.class, "loadLibrary",
                            MethodType.methodType(void.class, String.class)),
                            "Runtime::loadLibrary" }
            };
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError((ex));
        }
    }
}
