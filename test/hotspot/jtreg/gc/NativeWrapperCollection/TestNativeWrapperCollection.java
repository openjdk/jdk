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
 * @test TestNativeWrapperCollection
 * @summary TODO
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -ea -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseSerialGC
 *                          gc.NativeWrapperCollection.TestNativeWrapperCollection
 */

import java.lang.reflect.Method;
import java.util.Iterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.whitebox.WhiteBox;

public class TestNativeWrapperCollection {

    static {
        System.loadLibrary("nativeWrapperCollection");
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    static native void method();
    static native void callRegisterNatives(int index);

    public static void main(String... args) throws Exception {
        Method method = TestNativeWrapperCollection.class.getDeclaredMethod("method");

        callRegisterNatives(0);

        WB.enqueueMethodForCompilation(method, 1 /* compLevel */);
        while (WB.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }

        callRegisterNatives(1);

        WB.enqueueMethodForCompilation(method, 1 /* compLevel */);
        while (WB.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }

        WB.fullGC();
        System.gc(); // TODO: Why is this needed?

        assert(checkOneOccurrence()) : "CodeCache entry wasn't collected";
    }

    private static boolean checkOneOccurrence() {
        // Get output from dcmd (diagnostic command)
        OutputAnalyzer output = new JMXExecutor().execute("Compiler.codelist");
        Iterator<String> lines = output.asLines().iterator();

        int count = 0;
        while (lines.hasNext()) {
            String line = lines.next();
            if (!line.contains("TestNativeWrapperCollection.method")) {
                continue;
            }
            if (++count > 1) {
                return false;
            }

            String[] parts = line.split(" ");
            int codeState = Integer.parseInt(parts[2]);
            if (codeState == 1 /* not_entrant */) {
                return false;
            }
        }
        return true;
    }
}
