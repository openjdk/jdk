/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

package gc;

/*
 * @test id=ParallelGC
 * @bug 8370947
 * @summary Check no assertion is triggered when UseDeferredICacheInvalidation is enabled for ParallelGC
 * @library /test/lib
 * @requires vm.debug
 * @requires os.family=="linux"
 * @requires os.arch=="aarch64"
 * @requires vm.gc.Parallel
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseParallelGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseParallelGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseParallelGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C2
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseParallelGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C2
 */

/*
 * @test id=G1GC
 * @bug 8370947
 * @summary Check no assertion is triggered when UseDeferredICacheInvalidation is enabled for G1GC
 * @library /test/lib
 * @requires vm.debug
 * @requires os.family=="linux"
 * @requires os.arch=="aarch64"
 * @requires vm.gc.G1
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C2
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C2
 */

/*
 * @test id=ShenandoahGC
 * @bug 8370947
 * @summary Check no assertion is triggered when UseDeferredICacheInvalidation is enabled for ShenandoahGC
 * @library /test/lib
 * @requires vm.debug
 * @requires os.family=="linux"
 * @requires os.arch=="aarch64"
 * @requires vm.gc.Shenandoah
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseShenandoahGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseShenandoahGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C2
 */

/*
 * @test id=GenShenGC
 * @bug 8370947
 * @summary Check no assertion is triggered when UseDeferredICacheInvalidation is enabled for generational ShenandoahGC
 * @library /test/lib
 * @requires vm.debug
 * @requires os.family=="linux"
 * @requires os.arch=="aarch64"
 * @requires vm.gc.Shenandoah
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=generational -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=generational -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=generational -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C2
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=generational -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C2
 */

/*
 * @test id=ZGC
 * @bug 8370947
 * @summary Check no assertion is triggered when UseDeferredICacheInvalidation is enabled for ZGC
 * @library /test/lib
 * @requires vm.debug
 * @requires os.family=="linux"
 * @requires os.arch=="aarch64"
 * @requires vm.gc.Z
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseZGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseZGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C1
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseZGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation youngGC C2
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseZGC -XX:-UseCodeCacheFlushing gc.TestDeferredICacheInvalidation fullGC C2
 */

/*
 * Nmethods have GC barriers and OOPs embedded into their code. GCs can patch nmethod's code
 * which requires icache invalidation. Doing invalidation per instruction can be expensive.
 * CPU can support hardware dcache and icache coherence. This would allow to defer cache
 * invalidation.
 *
 * There are assertions for deferred cache invalidation. This test checks that all of them
 * are passed.
 */

import jdk.test.whitebox.WhiteBox;

public class TestDeferredICacheInvalidation {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static class A {
        public String s1;
        public String s2;
        public String s3;
        public String s4;
        public String s5;
        public String s6;
        public String s7;
        public String s8;
        public String s9;
    }

    public static A a = new A();

    private static int compLevel;

    public static class B {
        public static void test0() {
        }

        public static void test1() {
            a.s1 = a.s1 + "1";
        }

        public static void test2() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
        }

        public static void test3() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
            a.s3 = a.s3 + "3";
        }

        public static void test4() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
            a.s3 = a.s3 + "3";
            a.s4 = a.s4 + "4";
        }

        public static void test5() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
            a.s3 = a.s3 + "3";
            a.s4 = a.s4 + "4";
            a.s5 = a.s5 + "5";
        }

        public static void test6() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
            a.s3 = a.s3 + "3";
            a.s4 = a.s4 + "4";
            a.s5 = a.s5 + "5";
            a.s6 = a.s6 + "6";
        }

        public static void test7() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
            a.s3 = a.s3 + "3";
            a.s4 = a.s4 + "4";
            a.s5 = a.s5 + "5";
            a.s6 = a.s6 + "6";
            a.s7 = a.s7 + "7";
        }

        public static void test8() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
            a.s3 = a.s3 + "3";
            a.s4 = a.s4 + "4";
            a.s5 = a.s5 + "5";
            a.s6 = a.s6 + "6";
            a.s7 = a.s7 + "7";
            a.s8 = a.s8 + "8";
        }

        public static void test9() {
            a.s1 = a.s1 + "1";
            a.s2 = a.s2 + "2";
            a.s3 = a.s3 + "3";
            a.s4 = a.s4 + "4";
            a.s5 = a.s5 + "5";
            a.s6 = a.s6 + "6";
            a.s7 = a.s7 + "7";
            a.s8 = a.s8 + "8";
            a.s9 = a.s9 + "9";
        }
    }

    private static void compileMethods() throws Exception {
        for (var m : B.class.getDeclaredMethods()) {
            if (!m.getName().startsWith("test")) {
                continue;
            }
            m.invoke(null);
            WB.markMethodProfiled(m);
            WB.enqueueMethodForCompilation(m, compLevel);
            while (WB.isMethodQueuedForCompilation(m)) {
                Thread.onSpinWait();
            }
            if (WB.getMethodCompilationLevel(m) != compLevel) {
                throw new IllegalStateException("Method " + m + " is not compiled at the compilation level: " + compLevel + ". Got: " + WB.getMethodCompilationLevel(m));
            }
        }
    }

    public static void youngGC() throws Exception {
        a = null;
        WB.youngGC();
    }

    public static void fullGC() throws Exception {
        a = null;
        WB.fullGC();
    }

    public static void main(String[] args) throws Exception {
        if (!Boolean.TRUE.equals(WB.getBooleanVMFlag("UseDeferredICacheInvalidation"))) {
            System.out.println("Skip. Test requires UseDeferredICacheInvalidation enabled.");
        }
        compLevel = (args[1].equals("C1")) ? 1 : 4;
        compileMethods();
        TestDeferredICacheInvalidation.class.getMethod(args[0]).invoke(null);
    }
}
