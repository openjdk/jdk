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
 * @library /test/lib
 * @library ../
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm
 *   -Xbootclasspath/a:.
 *   -XX:+UnlockDiagnosticVMOptions
 *   -XX:+WhiteBoxAPI
 *   --enable-native-access=ALL-UNNAMED
 *   TestReentrantUpcalls
 */

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.test.whitebox.WhiteBox;

import static java.lang.invoke.MethodHandles.lookup;

public class TestReentrantUpcalls extends NativeTestHelper {
    static final WhiteBox WB = WhiteBox.getWhiteBox();

    static final MethodHandle MH_m;

    static {
        System.loadLibrary("ReentrantUpcalls");
        try {
            MH_m = lookup().findStatic(TestReentrantUpcalls.class, "m",
                    MethodType.methodType(void.class, int.class, MemorySegment.class, MethodHandle.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Throwable {
        FunctionDescriptor descriptor = FunctionDescriptor.ofVoid(C_INT, C_POINTER);
        MethodHandle downcallHandle = downcallHandle("do_recurse", descriptor);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stub = LINKER.upcallStub(
                    MethodHandles.insertArguments(MH_m, 2, downcallHandle), descriptor, arena);

            downcallHandle.invokeExact(0, stub);
        }
    }

    static void m(int depth, MemorySegment thisStub, MethodHandle downcallHandle) throws Throwable {
        if (depth < 50) {
            downcallHandle.invokeExact(depth + 1, thisStub);
        } else {
            WB.verifyFrames(/*log=*/true, /*updateRegisterMap=*/true);
            WB.verifyFrames(/*log=*/true, /*updateRegisterMap=*/false); // triggers different code paths
        }
    }

}
