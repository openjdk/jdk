/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package gc.NativeWrapperCollection;

/*
 * @test NativeWrapperCollection
 * @summary Test that native wrappers are collected after becoming not entrant
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                          gc.NativeWrapperCollection.NativeWrapperCollection
 */

import java.lang.reflect.Method;
import java.util.Iterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.whitebox.WhiteBox;

public class NativeWrapperCollection {

    static {
        System.loadLibrary("nativeWrapperCollection");
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    static native void method();
    static native void callRegisterNatives(int index);

    public static void main(String[] args) throws Exception {
        Method method = NativeWrapperCollection.class.getDeclaredMethod("method");

        callRegisterNatives(0);

        WB.enqueueMethodForCompilation(method, 1 /* compLevel */);
        while (WB.isMethodQueuedForCompilation(method)) {
            Thread.sleep(50 /* ms */);
        }

        callRegisterNatives(1);

        WB.enqueueMethodForCompilation(method, 1 /* compLevel */);
        while (WB.isMethodQueuedForCompilation(method)) {
            Thread.sleep(50 /* ms */);
        }

        WB.fullGC(); // mark the nmethod as not on stack
        WB.fullGC(); // reclaim the nmethod

        OutputAnalyzer output = new JMXExecutor().execute("Compiler.codelist");
        Iterator<String> lines = output.asLines().iterator();

        boolean foundOne = false;
        while (lines.hasNext()) {
            String line = lines.next();
            if (!line.contains("NativeWrapperCollection.method")) {
                continue;
            }
            if (foundOne) {
                throw new AssertionError("Expected one CodeCache entry for " +
                        "'NativeWrapperCollection.method', found at least 2");
            }

            String[] parts = line.split(" ");
            int codeState = Integer.parseInt(parts[2]);
            if (codeState == 1 /* not_entrant */) {
                throw new AssertionError("Unexpected not-entrant entry for " +
                        "'NativeWrapperCollection.method'");
            }

            // Found one NativeWrapperCollection.method, exactly one is
            // expected
            foundOne = true;
        }
    }
}
