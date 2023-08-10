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
 * @test id=default_gc
 * @requires vm.gc != "Z"
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
 *   -Xbatch
 *   TestAsyncStackWalk
 */

/*
 * @test id=ZSinglegen
 * @requires vm.gc.ZSinglegen
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
 *   -Xbatch
 *   -XX:+UseZGC -XX:-ZGenerational
 *   TestAsyncStackWalk
 */

/*
 * @test id=ZGenerational
 * @requires vm.gc.ZGenerational
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
 *   -Xbatch
 *   -XX:+UseZGC -XX:+ZGenerational
 *   TestAsyncStackWalk
 */

/*
 * @test id=shenandoah
 * @requires vm.gc.Shenandoah
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
 *   -Xbatch
 *   -XX:+UseShenandoahGC
 *   TestAsyncStackWalk
 */

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import jdk.test.whitebox.WhiteBox;

import static java.lang.invoke.MethodHandles.lookup;
import static jdk.test.lib.Asserts.assertTrue;

public class TestAsyncStackWalk extends NativeTestHelper {
    static final WhiteBox WB = WhiteBox.getWhiteBox();

    static final Linker linker = Linker.nativeLinker();

    static final MethodHandle MH_asyncStackWalk;
    static final MethodHandle MH_m;

    static {
        try {
            System.loadLibrary("AsyncStackWalk");
            MH_asyncStackWalk = linker.downcallHandle(
                    findNativeOrThrow("asyncStackWalk"),
                    FunctionDescriptor.ofVoid(C_POINTER));
            MH_m = lookup().findStatic(TestAsyncStackWalk.class, "m", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static int invocations;
    static boolean didStackWalk;

    public static void main(String[] args) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stub = linker.upcallStub(MH_m, FunctionDescriptor.ofVoid(), arena);
            invocations = 0;
            didStackWalk = false;
            payload(stub);
            assertTrue(didStackWalk);
        }
    }

    static void payload(MemorySegment cb) throws Throwable {
        MH_asyncStackWalk.invoke(cb);
    }

    static void m() {
        if (invocations++ >= 20000) { // warmup
            didStackWalk = true;
            WB.verifyFrames(/*log=*/true, /*updateRegisterMap=*/true);
            WB.verifyFrames(/*log=*/true, /*updateRegisterMap=*/false); // triggers different code paths
        }
    }

}
