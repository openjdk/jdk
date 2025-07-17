/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.whitebox.gc.GC;
import java.util.ArrayList;

/*
 * @test
 * bug 8308869
 * @summary C2: use profile data in subtype checks when profile has more than one class
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @requires vm.compiler2.enabled
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.ProfileAtTypeCheck
 */

public class ProfileAtTypeCheck {
    public static void main(String[] args) {
        // Only interpreter collects profile
        Scenario interpreterProfiling = new Scenario(0, "-XX:TypeProfileSubTypeCheckCommonThreshold=90", "-XX:-TieredCompilation");
        // Only c1 collects profile
        Scenario c1Profiling = new Scenario(1, "-XX:TypeProfileSubTypeCheckCommonThreshold=90", "-XX:+TieredCompilation", "-XX:-ProfileInterpreter");

        if (GC.isSelectedErgonomically() && GC.Parallel.isSupported()) {
            interpreterProfiling.addFlags("-XX:+UseParallelGC");
            c1Profiling.addFlags("-XX:+UseParallelGC");
        }

        TestFramework framework = new TestFramework();
        framework.addScenarios(interpreterProfiling, c1Profiling).start();
    }

    @DontInline
    static void dummyA(A a) {
    }

    @DontInline
    static void dummyB(B b) {
    }

    @DontInline
    static void dummyI(I i) {
    }

    // profile reports many classes

    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "2", IRNode.LOAD_KLASS_OR_NKLASS, "2" })
    public static void test1(Object o) {
        dummyA((A)o);
    }

    @Run(test = "test1")
    @Warmup(10000)
    private void test1Runner() {
        test1(a);
        test1(b);
        test1(c);
        test1(d);
    }

    // profile reports a single class

    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, failOn = { IRNode.SUBTYPE_CHECK })
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.CMP_P, "2", IRNode.LOAD_KLASS_OR_NKLASS, "1" })
    public static void test2(Object o) {
        dummyA((A)o);
    }

    @Run(test = "test2")
    @Warmup(10000)
    private void test2Runner() {
        test2(a);
    }

    // "easy" test because B has no subclass

    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "2", IRNode.LOAD_KLASS_OR_NKLASS, "1" })
    public static void test3(Object o) {
        if (o instanceof B) {
            dummyB((B)o);
        }
    }

    @Run(test = "test3")
    @Warmup(10000)
    private void test3Runner() {
        test3(b);
        test3(c);
        test3(d);
    }

    // full subtype check
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "3", IRNode.LOAD_KLASS_OR_NKLASS, "2", IRNode.PARTIAL_SUBTYPE_CHECK, "1" })
    public static void test4(Object o) {
        dummyI((I)o);
    }

    @Run(test = "test4")
    @Warmup(10000)
    private void test4Runner() {
        test4(a);
        test4(b);
        test4(c);
        test4(d);
    }

    // full subtype check + profile use for success path
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "5", IRNode.LOAD_KLASS_OR_NKLASS, "2", IRNode.PARTIAL_SUBTYPE_CHECK, "1" })
    public static void test5(Object o) {
        dummyI((I)o);
    }

    @Run(test = "test5")
    @Warmup(10000)
    private void test5Runner() {
        test5(a);
        test5(b);
    }

    // Check primary super
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "2", IRNode.LOAD_KLASS_OR_NKLASS, "2" }, failOn = { IRNode.PARTIAL_SUBTYPE_CHECK })
    public static void test6(Object o) {
        dummyA((A)o);
    }

    @Run(test = "test6")
    @Warmup(10000)
    private void test6Runner() {
        test6(b);
        test6(c);
        test6(d);
    }

    // full subtype check + profile use for both success and failure paths
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "5", IRNode.LOAD_KLASS_OR_NKLASS, "2", IRNode.PARTIAL_SUBTYPE_CHECK, "1" })
    public static boolean test7(Object o) {
        return o instanceof I;
    }

    @Run(test = "test7")
    @Warmup(10000)
    private void test7Runner() {
        test7(a);
        test7(e);
    }

    // full subtype check + profile use for success path (profile has unrecorded entries)
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "5", IRNode.LOAD_KLASS_OR_NKLASS, "2", IRNode.PARTIAL_SUBTYPE_CHECK, "1" })
    public static void test8(Object o) {
        dummyI((I)o);
    }

    @Run(test = "test8")
    @Warmup(10000)
    private void test8Runner() {
        for (int i = 0; i < 40; i++) {
            test8(a); // 95% of profile is A
        }
        // plus some B and C
        test8(b);
        test8(c);
    }

    // test that split if triggers
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.PHASEIDEALLOOP1 }, counts = { IRNode.SUBTYPE_CHECK, "2" })
    public static void test9(boolean flag1, boolean flag2, Object o1, Object o2) {
        if (o1 == null) {
            throw new RuntimeException();
        }
        if (o2 == null) {
            throw new RuntimeException();
        }
        Object o;
        if (flag1) {
            o = a;
            if (o == null) {
            }
        } else {
            if (flag2) {
                o = o1;
            } else {
                o = o2;
            }
        }
        dummyI((I)o);
    }

    @Run(test = "test9")
    @Warmup(10_000)
    private void test9Runner() {
        test9(true, true, a, a);
        test9(false, true, c, c);
        test9(false, false, d, d);
    }

    // test that dominating subtype check is removed
    static Object fieldTest10;
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "2" })
    @IR(phase = { CompilePhase.ITER_GVN1 }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    public static void test10(boolean flag) {
        if (fieldTest10 instanceof I) {
            if (flag) {
                dummyI((I)fieldTest10);
            }
        }
    }

    @Run(test = "test10")
    @Warmup(10_000)
    private void test10Runner() {
        fieldTest10 = a;
        test10(true);
        fieldTest10 = b;
        test10(false);
        fieldTest10 = c;
        test10(true);
        fieldTest10 = d;
        test10(false);
    }

    static Object fieldTest11;
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "2" })
    @IR(phase = { CompilePhase.PHASEIDEALLOOP_ITERATIONS }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    public static void test11(boolean flag1, boolean flag2) {
        if (fieldTest11 instanceof I) {
            if (flag1) {
                for (int i = 1; i < 10; i *= 2) {
                }
            }
            if (flag2) {
                dummyI((I)fieldTest11);
            }
        }
    }

    @Run(test = "test11")
    @Warmup(10_000)
    private void test11Runner() {
        fieldTest11 = a;
        test11(true, true);
        fieldTest11 = b;
        test11(false, false);
        fieldTest11 = c;
        test11(true, true);
        fieldTest11 = d;
        test11(false, false);
    }

    static Object fieldTest12;
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "3" })
    @IR(phase = { CompilePhase.PHASEIDEALLOOP_ITERATIONS }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    public static void test12() {
        test12Helper(true);
    }

    public static void test12Helper(boolean flag) {
        if (fieldTest12 instanceof I) {
            for (int i = 1; i < 10; i *= 2) {
            }
        }
        if (flag) {
            if (fieldTest12 instanceof I) {
                dummyI((I)fieldTest12);
            }
        }
    }

    @Run(test = "test12")
    @Warmup(10_000)
    private void test12Runner() {
        fieldTest12 = a;
        test12();
        test12Helper(false);
        fieldTest12 = b;
        test12();
        test12Helper(false);
        fieldTest12 = c;
        test12();
        test12Helper(true);
        fieldTest12 = d;
        test12();
        test12Helper(true);
        fieldTest12 = e;
        test12();
    }

    // Test that subtype checks with different profile don't common
    @Test
    @IR(phase = { CompilePhase.ITER_GVN1 }, counts = { IRNode.SUBTYPE_CHECK, "2" })
    public static void test13(boolean flag, Object o) {
        if (o == null) {
            throw new RuntimeException();
        }
        if (flag) {
            dummyI((I)o);
        } else {
            dummyI((I)o);
        }
    }

    @Run(test = "test13")
    @Warmup(10_000)
    private void test13Runner() {
        test13(true, a);
        test13(true, b);
        test13(false, c);
        test13(false, d);
    }

    static Object fieldTest14_1;
    static Object fieldTest14_2;
    // test that split if triggers
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "2" })
    @IR(applyIf = { "UseParallelGC", "true" }, phase = { CompilePhase.PHASEIDEALLOOP1 }, counts = { IRNode.SUBTYPE_CHECK, "3" })
    public static void test14(boolean flag1, boolean flag2, Object o1, Object o2, Object o3) {
        if (o1 == null) {
            throw new RuntimeException();
        }
        if (o2 == null) {
            throw new RuntimeException();
        }
        if (o3 == null) {
            throw new RuntimeException();
        }
        Object o;
        if (flag1) {
            fieldTest14_1 = o3;
            fieldTest14_2 = (I)o3;
            o = fieldTest14_1;
        } else {
            if (flag2) {
                o = o1;
            } else {
                o = o2;
            }
        }
        dummyI((I)o);
    }

    @Run(test = "test14")
    @Warmup(10_000)
    private void test14Runner() {
        test14(true, true, a, a, a);
        test14(true, true, b, b, b);
        test14(false, true, c, c, c);
        test14(false, false, d, d, d);
    }

    // full subtype check + profile use for success path
    @Test
    @IR(phase = { CompilePhase.AFTER_PARSING }, counts = { IRNode.SUBTYPE_CHECK, "1" })
    @IR(phase = { CompilePhase.AFTER_MACRO_EXPANSION }, counts = { IRNode.CMP_P, "5", IRNode.LOAD_KLASS_OR_NKLASS, "2", IRNode.PARTIAL_SUBTYPE_CHECK, "1" })
    public static void test15(Object o) {
        array[0] = o;
    }

    @Run(test = "test15")
    @Warmup(10000)
    private void test15Runner() {
        test15(a);
        test15(b);
    }

    interface I {
    }


    static A a = new A();
    static B b = new B();
    static C c = new C();
    static D d = new D();
    static E e = new E();
    static final Object[] array = new I[1];

    static class A implements I {
    }

    static class B extends A {
    }

    static class C extends A {
    }

    static class D extends A {
    }

    static class E {
    }
}
