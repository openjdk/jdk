/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277602
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @library /test/lib
 * @library ../
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm
 *   -Xbootclasspath/a:.
 *   -XX:+UnlockDiagnosticVMOptions
 *   -XX:+WhiteBoxAPI
 *   --enable-native-access=ALL-UNNAMED
 *   -Xbatch
 *   TestUpcallDeopt
 */

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.SymbolLookup;
import jdk.incubator.foreign.MemoryAddress;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;

import jdk.incubator.foreign.ResourceScope;
import sun.hotspot.WhiteBox;

import static java.lang.invoke.MethodHandles.lookup;

public class TestUpcallDeopt extends NativeTestHelper {
    static final WhiteBox WB = WhiteBox.getWhiteBox();

    static final CLinker linker = CLinker.systemCLinker();

    static final MethodHandle MH_foo;
    static final MethodHandle MH_m;

    static {
        try {
            System.loadLibrary("UpcallDeopt");
            SymbolLookup lookup = SymbolLookup.loaderLookup();
            MH_foo = linker.downcallHandle(
                    lookup.lookup("foo").orElseThrow(),
                    FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_INT, C_INT));
            MH_m = lookup().findStatic(TestUpcallDeopt.class, "m",
                    MethodType.methodType(void.class, int.class, int.class, int.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean armed;

    // we need to deoptimize through an uncommon trap in the callee of the optimized upcall stub
    // that is created when calling upcallStub below
    public static void main(String[] args) throws Throwable {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            NativeSymbol stub = linker.upcallStub(MH_m, FunctionDescriptor.ofVoid(C_INT, C_INT, C_INT, C_INT), scope);
            armed = false;
            for (int i = 0; i < 20_000; i++) {
                payload(stub); // warmup
            }

            armed = true;
            payload(stub); // test
        }
    }

    static void payload(NativeSymbol cb) throws Throwable {
        MH_foo.invokeExact((Addressable) cb, 0, 1, 2, 3);
        Reference.reachabilityFence(cb); // keep oop alive across call
    }

    // Takes a bunch of arguments, even though unused, to test
    // if the caller's frame is extended enough to spill these arguments.
    static void m(int a0, int a1, int a2, int a3) {
        if (armed) {
            // Trigger uncommon trap from this frame
            WB.verifyFrames(/*log=*/true, /*updateRegisterMap=*/true);
            WB.verifyFrames(/*log=*/true, /*updateRegisterMap=*/false); // triggers different code paths
        }
    }

}
