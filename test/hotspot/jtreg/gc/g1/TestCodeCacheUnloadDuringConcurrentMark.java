/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/*
 * @test TestCodeCacheUnloadDuringConcurrentMark
 * @summary Tests that G1 concurrent marking unloads a freshly not-entrant nmethod.
 * @requires vm.gc.G1
 * @requires vm.compiler1.enabled
 * @requires vm.opt.ClassUnloading != false
 * @requires vm.opt.ClassUnloadingWithConcurrentMark != false
 * @requires vm.opt.MethodFlushing != false
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:-BackgroundCompilation
 *                   -XX:+UseG1GC
 *                   -XX:+ClassUnloadingWithConcurrentMark
 *                   gc.g1.TestCodeCacheUnloadDuringConcurrentMark
 */

import java.lang.reflect.Method;

import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.whitebox.WhiteBox;

public class TestCodeCacheUnloadDuringConcurrentMark {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    static class Target {
        public static int test(int value) {
            return value + 1;
        }
    }

    private static void compileAndMakeNotEntrant(Method method) throws Exception {
        Target.test(1);
        if (!WB.enqueueMethodForCompilation(method, 1 /* compLevel */)) {
            throw new AssertionError("Failed to enqueue target for compilation");
        }
        while (WB.isMethodQueuedForCompilation(method)) {
            Thread.sleep(50);
        }
        if (!WB.isMethodCompiled(method)) {
            throw new AssertionError("Target is not compiled");
        }

        int deoptimized = WB.deoptimizeMethod(method);
        if (deoptimized == 0) {
            throw new AssertionError("No target nmethod was made not-entrant");
        }
    }

    private static int countNotEntrantEntries() {
        String target = TestCodeCacheUnloadDuringConcurrentMark.class.getName() + "$Target.test";
        int result = 0;

        for (String line : new JMXExecutor().execute("Compiler.codelist", true).asLines()) {
            if (!line.contains(target)) {
                continue;
            }

            System.out.println("Found codelist entry: " + line);
            String[] parts = line.trim().split("\\s+");
            int codeState = Integer.parseInt(parts[2]);
            if (codeState == 1 /* not_entrant */) {
                result++;
            }
        }

        return result;
    }

    public static void main(String[] args) throws Exception {
        compileAndMakeNotEntrant(Target.class.getDeclaredMethod("test", int.class));

        int notEntrantEntries = countNotEntrantEntries();
        System.out.println("Target not-entrant entries before concurrent mark: " + notEntrantEntries);
        if (notEntrantEntries == 0) {
            throw new AssertionError("Expected a not-entrant target nmethod before concurrent mark");
        }

        WB.g1RunConcurrentGC();

        notEntrantEntries = countNotEntrantEntries();
        System.out.println("Target not-entrant entries after concurrent mark: " + notEntrantEntries);
        if (notEntrantEntries != 0) {
            throw new AssertionError("Expected concurrent mark to unload the not-entrant target nmethod");
        }
    }
}
