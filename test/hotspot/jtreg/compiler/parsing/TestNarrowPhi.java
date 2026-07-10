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
package compiler.parsing;

import java.util.Objects;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @bug 8387328
 * @summary A Phi having a narrower Type than its inputs may result in incorrect scheduling
 * @library /test/lib
 * @requires vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=0 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=1 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=2 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=3 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=4 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=5 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=6 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=7 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=8 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=9 ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::nonInline
 *                   -XX:CompileCommand=delayinline,${test.main.class}::inlineTestHelper
 *                   -DIdx=10 ${test.main.class}
 */
public class TestNarrowPhi {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static volatile Throwable failure;

    private static abstract class P {
        int u;

        private static P allocate() {
            return new C();
        }
    }

    private static class C extends P {
        int v;
    }

    public static void main(String[] args) throws InterruptedException, NoSuchMethodException {
        // Since we cannot reliably coordinate the compiler thread and the thread that load the
        // child class, randomly delaying one of them
        int idx = Integer.getInteger("Idx");
        var testMethod = TestNarrowPhi.class.getDeclaredMethod("testMethod", boolean.class, P.class, P.class, P.class);
        var _ = Objects.class;
        Thread loader = new Thread(() -> {
            try {
                if (idx < 5) {
                    Thread.sleep((5 - idx) * 10L);
                }
                var _ = C.class;
            } catch (Exception e) {
                failure = e;
            }
        });
        loader.start();

        if (idx > 5) {
            Thread.sleep((idx - 5) * 10L);
        }
        if (!WHITE_BOX.enqueueMethodForCompilation(testMethod, 4)) {
            throw new RuntimeException("Could not enqueue the test method for C2 compilation");
        }
        while (WHITE_BOX.isMethodQueuedForCompilation(testMethod)) {
            Thread.yield();
        }
        P p = P.allocate();
        Asserts.assertEQ(0, testMethod(true, p, p, p));
        loader.join();
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }

    private static int testMethod(boolean b, P p1, P p2, P p3) {
        // Arbitrarily delay the parser between generating the Type for P1 and for the loop Phi
        // below
        inline0();
        // This method is late-inlined, which increases the chance that C has been loaded then
        return inlineTestHelper(b, p1, p2, p3);
    }

    private static int inlineTestHelper(boolean b, P p1, P p2, P p3) {
        // Random access that can be used as an implicit null-check, so that the load below can
        // float freely
        p1.u = 0;
        P p = p1;
        for (int i = 0; i < 1; i++) {
            if (i % 2 != 0) {
                p = p2;
            }
        }

        C cp = (C) Objects.requireNonNull(p);
        C cp3 = (C) Objects.requireNonNull(p3);
        int res = cp.v;
        cp3.v = 1;
        if (b) {
            cp3.v = 2;
            return res;
        } else {
            return nonInline();
        }
    }

    private static int nonInline() {
        return 0;
    }

    private static void inline0() {
        inline1();
        inline1();
        inline1();
        inline1();
    }

    private static void inline1() {
        inline2();
        inline2();
        inline2();
        inline2();
    }

    private static void inline2() {
        inline3();
        inline3();
        inline3();
        inline3();
    }

    private static void inline3() {
        inline4();
        inline4();
        inline4();
        inline4();
    }

    private static void inline4() {
        inline5();
        inline5();
        inline5();
        inline5();
    }

    private static void inline5() {}
}
