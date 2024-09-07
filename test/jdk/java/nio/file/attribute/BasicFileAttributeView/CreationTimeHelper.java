/*
 * Copyright (c) 2024 Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public class CreationTimeHelper extends NativeTestHelper {

    static {
        System.loadLibrary("CreationTimeHelper");
    }

    final static Linker abi = Linker.nativeLinker();
    static final SymbolLookup lookup = SymbolLookup.loaderLookup();
    final static MethodHandle methodHandle = abi.
            downcallHandle(lookup.findOrThrow("linuxIsCreationTimeSupported"),
            FunctionDescriptor.of(C_BOOL, C_POINTER));

    // Helper so as to determine 'statx' support on the runtime system
    // static boolean linuxIsCreationTimeSupported(String file);
    static boolean linuxIsCreationTimeSupported(String file) throws Throwable {
        if (!abi.defaultLookup().find("statx").isPresent())
            return false;
        try (var arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocateFrom(file);
            return (boolean)methodHandle.invokeExact(s);
        }
    }
}
