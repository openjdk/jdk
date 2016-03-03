/*
 * Copyright (c) 2016 SAP SE. All rights reserved.
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
 * @bug 8150646
 * @summary Add support for blocking compiles through whitebox API
 * @library /testlibrary /test/lib /
 * @build sun.hotspot.WhiteBox
 *        compiler.testlibrary.CompilerUtils
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *
 * @run main/othervm
 *        -Xbootclasspath/a:.
 *        -Xmixed
 *        -XX:+UnlockDiagnosticVMOptions
 *        -XX:+WhiteBoxAPI
 *        -XX:+PrintCompilation
 *        -XX:CompileCommand=option,BlockingCompilation::foo,PrintInlining
 *        BlockingCompilation
 */

import java.lang.reflect.Method;
import java.util.Random;

import sun.hotspot.WhiteBox;
import compiler.testlibrary.CompilerUtils;

public class BlockingCompilation {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Random RANDOM = new Random();

    public static int foo() {
        return RANDOM.nextInt();
    }

    public static void main(String[] args) throws Exception {
        long sum = 0;
        int level = 0;
        boolean enqued = false;
        Method m = BlockingCompilation.class.getMethod("foo");
        int[] levels = CompilerUtils.getAvailableCompilationLevels();

        // If there are no compilers available these tests don't make any sense.
        if (levels.length == 0) return;
        int max_level = levels[levels.length - 1];

        // Normal, non-blocking compilation
        for (int i = 0; i < 500_000; i++) {
            sum += foo();
            if (!enqued && WB.isMethodQueuedForCompilation(m)) {
                System.out.println("==> " + m + " enqued for compilation in iteration " + i);
                enqued = true;
            }
            if (WB.isMethodCompiled(m)) {
                if (WB.getMethodCompilationLevel(m) != level) {
                    level = WB.getMethodCompilationLevel(m);
                    System.out.println("==> " + m + " compiled at level " + level + " in iteration " + i);
                    enqued = false;
                    if (level == max_level) break;
                }
            }
        }

        // This is necessarry because WB.deoptimizeMethod doesn't clear the methods
        // MDO and therefore level 3 compilations will be downgraded to level 2.
        WB.clearMethodState(m);

        // Blocking compilations on all levels, using the default versions of
        // WB.enqueueMethodForCompilation() and manually setting compiler directives.
        String directive = "[{ match: \"BlockingCompilation.foo\", BackgroundCompilation: false }]";
        WB.addCompilerDirective(directive);

        for (int l : levels) {
            WB.deoptimizeMethod(m);
            WB.enqueueMethodForCompilation(m, l);

            if (!WB.isMethodCompiled(m) || WB.getMethodCompilationLevel(m) != l) {
                String msg = m + " should be compiled at level " + l +
                             "(but is actually compiled at level " +
                             WB.getMethodCompilationLevel(m) + ")";
                System.out.println("==> " + msg);
                throw new Exception(msg);
            }
        }

        WB.removeCompilerDirective(1);

        WB.deoptimizeMethod(m);
        WB.clearMethodState(m);
        level = 0;
        enqued = false;
        int iteration = 0;

        // Normal, non-blocking compilation
        for (int i = 0; i < 500_000; i++) {
            sum += foo();
            if (!enqued && WB.isMethodQueuedForCompilation(m)) {
                System.out.println("==> " + m + " enqued for compilation in iteration " + i);
                iteration = i;
                enqued = true;
            }
            if (WB.isMethodCompiled(m)) {
                if (WB.getMethodCompilationLevel(m) != level) {
                    level = WB.getMethodCompilationLevel(m);
                    System.out.println("==> " + m + " compiled at level " + level + " in iteration " + i);
                    if (level == 4 && iteration == i) {
                        throw new Exception("This seems to be a blocking compilation although it shouldn't.");
                    }
                    enqued = false;
                    if (level == max_level) break;
                }
            }
        }
    }
}
