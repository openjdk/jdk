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
import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;
import compiler.whitebox.CompilerWhiteBoxTest;

import java.util.Arrays;
import java.util.List;

/*
 * @test
 * @bug 8320649
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @compile --enable-preview -source ${jdk.version} TestScopedValue.java
 * @run main/othervm --enable-preview -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.TestScopedValue
 */

public class TestScopedValue {

    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    static ScopedValue<MyDouble> sv = ScopedValue.newInstance();
    static final ScopedValue<MyDouble> svFinal = ScopedValue.newInstance();
    static ScopedValue<Object> svObject = ScopedValue.newInstance();
    private static volatile int volatileField;

    public static void main(String[] args) {
        // Fast path tests need to be run one at a time to prevent profile pollution
        List<String> tests = List.of("testFastPath1", "testFastPath2", "testFastPath3", "testFastPath5",
                "testFastPath6", "testFastPath7", "testFastPath8", "testFastPath9", "testFastPath10",
                "testFastPath11", "testFastPath12", "testFastPath13","testFastPath14",
                "testSlowPath1,testSlowPath2,testSlowPath3,testSlowPath4,testSlowPath5,testSlowPath6,testSlowPath7,testSlowPath8,testSlowPath9");
        for (String test : tests) {
            TestFramework.runWithFlags("--enable-preview", "-XX:CompileCommand=dontinline,java.lang.ScopedValue::slowGet", "-DTest=" + test);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath1() {
        MyDouble sv1 = sv.get();
        MyDouble sv2 = sv.get(); // Should optimize out
        return sv1.getValue() + sv2.getValue();
    }

    @Run(test = "testFastPath1", mode = RunMode.STANDALONE)
    private void testFastPath1Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath1() != 42 + 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath1");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @DontInline
    static void notInlined() {
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath2() {
        ScopedValue<MyDouble> scopedValue = sv;
        MyDouble sv1 = scopedValue.get();
        notInlined();
        MyDouble sv2 = scopedValue.get(); // Should optimize out
        return sv1.getValue() + sv2.getValue();
    }

    @Run(test = "testFastPath2", mode = RunMode.STANDALONE)
    private void testFastPath2Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath2() != 42 + 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath2");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.LOAD_D, "2" })
    public static double testFastPath3() {
        MyDouble sv1 = sv.get();
        notInlined();
        MyDouble sv2 = sv.get(); // Doesn't optimize out (load of sv cannot common)
        return sv1.getValue() + sv2.getValue();
    }

    @Run(test = "testFastPath3", mode = RunMode.STANDALONE)
    private void testFastPath3Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath3() != 42 + 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath3");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    // Split if test but it doesn't trigger at this point
    // @Test
    // @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    // @IR(counts = {IRNode.LOAD_L, "2" })
    // public static long test4(boolean flag) throws Exception {
    //     ScopedValue<MyLong> scopedValue;
    //     MyLong long1;
    //     if (flag) {
    //         scopedValue = svFinal;
    //         long1 = (MyLong)svFinal.get();
    //     } else {
    //         scopedValue = svFinal2;
    //         long1 = (MyLong)svFinal2.get();
    //     }
    //     MyLong long2 = (MyLong)scopedValue.get();
    //     return long1.getValue() + long2.getValue();
    // }

    // @Run(test = "test4", mode = RunMode.STANDALONE)
    // private void test4Runner() throws Exception {
    //     ScopedValue.where(svFinal, new MyLong(42)).where(svFinal2, new MyLong(42)).run(
    //             () -> {
    //                 try {
    //                     MyLong unused = (MyLong)svFinal.get();
    //                     unused = (MyLong)svFinal2.get();
    //                     for (int i = 0; i < 20_000; i++) {
    //                         if (test4(true) != 42 + 42) {
    //                             throw new RuntimeException();
    //                         }
    //                         if (test4(false) != 42 + 42) {
    //                             throw new RuntimeException();
    //                         }
    //                     }
    //                 } catch(Exception ex) {}
    //             });
    //     Method m = TestScopedValue.class.getDeclaredMethod("test4", boolean.class);
    //     WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
    //     if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
    //         throw new RuntimeException("should be compiled");
    //     }
    // }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet", IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath5() {
        double res = 0;
        for (int i = 0; i < 10_000; i++) {
            res = sv.get().getValue(); // should be hoisted out of loop and loop should optimize out
        }
        return res;
    }

    @Run(test = "testFastPath5", mode = RunMode.STANDALONE)
    private void testFastPath5Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath5() != 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath5");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 4" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 5" })
    public static void testFastPath6() {
        Object unused = svObject.get(); // cannot be removed if result not used
    }

    @Run(test = "testFastPath6", mode = RunMode.STANDALONE)
    private void testFastPath6Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath6();
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath6");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    static Object testFastPath7Field;
    @ForceInline
    static void testFastPath7Helper(int i, Object o) {
        if (i != 10) {
            testFastPath7Field = o;
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 4" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 5" })
    public static void testFastPath7() {
        Object unused = svObject.get(); // cannot be removed even if result not used (after opts)
        int i;
        for (i = 0; i < 10; i++);
        testFastPath7Helper(i, unused);
    }

    @Run(test = "testFastPath7", mode = RunMode.STANDALONE)
    private void testFastPath7Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath7();
                        testFastPath7Helper(9, null);
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath7");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet", IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath8(boolean[] flags) {
        double res = 0;
        for (int i = 0; i < 10_000; i++) {
            if (flags[i]) {
                res = sv.get().getValue(); // Should be hoisted by predication
            } else {
                res = sv.get().getValue(); // should be hoisted by predication
            }
        }
        return res;
    }

    @Run(test = "testFastPath8", mode = RunMode.STANDALONE)
    private void testFastPath8Runner() throws Exception {
        boolean[] allTrue = new boolean[10_000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[10_000];
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath8(allTrue) != 42) {
                            throw new RuntimeException();
                        }
                        if (testFastPath8(allFalse) != 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath8", boolean[].class);
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath9(boolean[] flags) {
        double res = 0;
        for (int i = 0; i < 10_000; i++) {
            notInlined();
            if (flags[i]) {
                res = svFinal.get().getValue(); // should be hoisted by predication
            } else {
                res = svFinal.get().getValue(); // should be hoisted by predication
            }
        }
        return res;
    }

    @Run(test = "testFastPath9", mode = RunMode.STANDALONE)
    private void testFastPath9Runner() throws Exception {
        boolean[] allTrue = new boolean[10_000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[10_000];
        ScopedValue.where(svFinal, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = svFinal.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath9(allTrue) != 42) {
                            throw new RuntimeException();
                        }
                        if (testFastPath9(allFalse) != 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath9", boolean[].class);
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    public static Object testFastPath10(boolean[] flags) {
        // result of get() is candidate for sinking
        Object res = null;
        for (int i = 0; i < 10_000; i++) {
            notInlined();
            res = svObject.get();
            if (flags[i]) {
                break;
            }
        }
        return res;
    }

    @Run(test = "testFastPath10", mode = RunMode.STANDALONE)
    private void testFastPath10Runner() throws Exception {
        boolean[] allTrue = new boolean[10_000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[10_000];
        ScopedValue.where(svObject, new MyDouble(42)).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath10(allTrue);
                        testFastPath10(allFalse);
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath10", boolean[].class);
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }
    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    public static Object testFastPath11(boolean[] flags) {
        for (int i = 0; i < 10_000; i++) {
            volatileField = 0x42;
            final boolean flag = flags[i];
            Object res = svObject.get(); // result used out of loop
            if (flag) {
                return res;
            }
        }
        return null;
    }

    @Run(test = "testFastPath11", mode = RunMode.STANDALONE)
    private void testFastPath11Runner() throws Exception {
        boolean[] allTrue = new boolean[10_000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[10_000];
        ScopedValue.where(svObject, new MyDouble(42)).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath11(allTrue);
                        testFastPath11(allFalse);
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath11", boolean[].class);
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 7" })
    public static Object testFastPath12() {
        // test commoning when the result of one is unused
        Object unused = svObject.get();
        return svObject.get();
    }

    @Run(test = "testFastPath12", mode = RunMode.STANDALONE)
    private void testFastPath12Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath12();
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath12");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 7" })
    public static Object testFastPath13() {
        // test commoning when the result of one is unused
        int i;
        for (i = 0; i < 10; i++) {

        }
        final Object result = testFastPath13Inlined(i);
        Object unused = svObject.get();
        return result;
    }

    @ForceInline
    private static Object testFastPath13Inlined(int i) {
        Object result = null;
        if (i == 10) {
            result = svObject.get();
        }
        return result;
    }

    @Run(test = "testFastPath13", mode = RunMode.STANDALONE)
    private void testFastPath13Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath13();
                        testFastPath13Inlined(0);
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath13");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 6" })
    public static Object testFastPath14() {
        // checks code shape once fully expanded
        return svObject.get();
    }

    @Run(test = "testFastPath14", mode = RunMode.STANDALONE)
    private void testFastPath14Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath14();
                    }
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath14");
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should be compiled");
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_D, "1", IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static double testSlowPath1() {
        ScopedValue<MyDouble> localSV = sv;
        MyDouble sv1 = localSV.get();
        MyDouble sv2 = localSV.get(); // should optimize out
        return sv1.getValue() + sv2.getValue();
    }

    @Run(test = "testSlowPath1")
    private void testSlowPath1Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    if (testSlowPath1() != 42 + 42) {
                        throw new RuntimeException();
                    }
                });
    }

    @Test
    @IR(counts = {IRNode.LOAD_D, "1", IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static double testSlowPath2() {
        ScopedValue<MyDouble> localSV = sv;
        MyDouble sv1 = localSV.get();
        notInlined();
        MyDouble sv2 = localSV.get(); // should optimize out
        return sv1.getValue() + sv2.getValue();
    }

    @Run(test = "testSlowPath2")
    private void testSlowPath2Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    if (testSlowPath2() != 42 + 42) {
                        throw new RuntimeException();
                    }
                });
    }

    @Test
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 4", IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static void testSlowPath3() {
        Object unused = svObject.get(); // Can't be optimized out even tough result is unused
    }

    @Run(test = "testSlowPath3")
    private void testSlowPath3Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    testSlowPath3();
                });
    }

    static Object testSlowPath4Field;
    @ForceInline
    static void testSlowPath4Helper(int i, Object o) {
        if (i != 10) {
            testSlowPath4Field = o;
        }
    }

    @Test
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 4", IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static void testSlowPath4() {
        Object unused = svObject.get(); // Can't be optimized out even tough result is unused (after opts)
        int i;
        for (i = 0; i < 10; i++);
        testSlowPath4Helper(i, unused);
    }

    @Run(test = "testSlowPath4")
    private void testSlowPath4Runner() throws Exception {
        testSlowPath4Helper(9, null);
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    testSlowPath4();
                });
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.LOAD_D, "1", IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static double testSlowPath5() {
        ScopedValue<MyDouble> localSV = sv;
        double res = 0;
        for (int i = 0; i < 10_000; i++) {
            res = localSV.get().getValue(); // one iteration of the loop should be peeled to optimize get() out of loop
        }
        return res;
    }

    @Run(test = "testSlowPath5")
    private void testSlowPath5Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    if (testSlowPath5() != 42) {
                        throw new RuntimeException();
                    }
                });
    }


    @Test
    @IR(counts = {IRNode.CALL_OF_METHOD, "slowGet", "2" })
    public static double testSlowPath6() {
        // Should not optimize because of where() call
        ScopedValue<MyDouble> localSV = sv;
        MyDouble sv1 = localSV.get();
        MyDouble sv2 = ScopedValue.where(sv, new MyDouble(0x42)).get(() -> localSV.get());
        return sv1.getValue() + sv2.getValue();
    }

    @Run(test = "testSlowPath6")
    private void testSlowPath6Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    if (testSlowPath6() != 42 + 0x42) {
                        throw new RuntimeException();
                    }
                });
    }

    @Test
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 7" })
    @IR(counts = {IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static Object testSlowPath7() {
        // test optimization of redundant get() when one doesn't use its result
        final ScopedValue<Object> scopedValue = svObject;
        Object unused = scopedValue.get();
        return scopedValue.get();
    }

    @Run(test = "testSlowPath7")
    private void testSlowPath7Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    testSlowPath7();
                });
    }

    @Test
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 7" })
    @IR(counts = {IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static Object testSlowPath8() {
        // test optimization of redundant get() when one doesn't use its result
        final ScopedValue<Object> scopedValue = svObject;
        Object result = scopedValue.get();
        Object unused = scopedValue.get();
        return result;
    }

    @Run(test = "testSlowPath8")
    private void testSlowPath8Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    testSlowPath8();
                });
    }

    @Test
    @IR(counts = {IRNode.IF, ">=3", IRNode.LOAD_P_OR_N, ">=5", IRNode.CALL_OF_METHOD, "slowGet", "1" })
    @IR(counts = {IRNode.IF, "<=4", IRNode.LOAD_P_OR_N, "<=7", IRNode.CALL_OF_METHOD, "slowGet", "1" })
    public static Object testSlowPath9() {
        // Test right pattern once fully expanded
        return svObject.get();
    }

    @Run(test = "testSlowPath9")
    private void testSlowPath9Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    testSlowPath9();
                });
    }

    static class MyDouble {
        final private double value;

        public MyDouble(long value) {
            this.value = value;
        }

        @ForceInline
        public double getValue() {
            return value;
        }
    }

}
