/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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
 * @summary Verifies that on Windows, HotSpot's access-violation recovery for
 *          an in-Java stack fault advances TEB.StackLimit to cover the newly
 *          committed stack memory.
 * @requires os.family == "windows"
 * @library /test/lib
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED TestWindowsStackLimit
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public class TestWindowsStackLimit {
    public static void main(String[] args) throws Throwable {
        System.loadLibrary("StackLimit");
        SymbolLookup lookup = SymbolLookup.loaderLookup();
        MemorySegment segment = lookup.find("checkStackLimit").orElseThrow();

        // `Linker.Option.critical()` keeps the calling thread's state as
        // `_thread_in_java` for the whole native call, which is a prerequisite
        // for the code that commits memory pages in `topLevelExceptionFilter()`
        Linker.Option option = Linker.Option.critical(/* heap access */ false);
        FunctionDescriptor desc = FunctionDescriptor.of(ValueLayout.JAVA_INT);

        Linker linker = Linker.nativeLinker();
        MethodHandle check = linker.downcallHandle(segment, desc, option);
        if ((int) check.invokeExact() != 0) {
            throw new RuntimeException("TEB.StackLimit was not advanced");
        }
    }
}
