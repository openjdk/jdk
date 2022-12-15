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

package handle.lookup;

import java.lang.foreign.SegmentScope;
import java.lang.foreign.Linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import java.nio.file.Path;

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
                    { MethodHandles.lookup().findStatic(Linker.class, "nativeLinker",
                            MethodType.methodType(Linker.class)), "Linker::nativeLinker" },
                    { MethodHandles.lookup().findStatic(MemorySegment.class, "ofAddress",
                            MethodType.methodType(MemorySegment.class, long.class, long.class, SegmentScope.class)),
                            "MemorySegment::ofAddressNative" },
                    { MethodHandles.lookup().findStatic(SymbolLookup.class, "libraryLookup",
                            MethodType.methodType(SymbolLookup.class, String.class, SegmentScope.class)),
                            "SymbolLookup::libraryLookup(String)" },
                    { MethodHandles.lookup().findStatic(SymbolLookup.class, "libraryLookup",
                            MethodType.methodType(SymbolLookup.class, Path.class, SegmentScope.class)),
                            "SymbolLookup::libraryLookup(Path)" },
            };
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError((ex));
        }
    }
}
