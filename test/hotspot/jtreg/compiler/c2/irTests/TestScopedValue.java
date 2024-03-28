/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
import compiler.lib.ir_framework.test.TestVM;
import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Platform;

import java.util.Arrays;
import java.util.List;

/*
 * @test
 * @bug 8320649
 * @summary C2: Optimize scoped values
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @compile --enable-preview -source ${jdk.version} TestScopedValue.java
 * @run main/othervm --enable-preview -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.TestScopedValue
 */

public class TestScopedValue {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static long tieredStopAtLevel = (long)WHITE_BOX.getVMFlag("TieredStopAtLevel");

    static ScopedValue<MyDouble> sv = ScopedValue.newInstance();
    static final ScopedValue<MyDouble> svFinal = ScopedValue.newInstance();
    static ScopedValue<Object> svObject = ScopedValue.newInstance();
    private static volatile int volatileField;

    public static void main(String[] args) {
        if (Platform.isComp()) {
            TestFramework.runWithFlags("--enable-preview");
        } else {
            // Fast path tests need to be run one at a time to prevent profile pollution
            List<String> tests = List.of("testFastPath1", "testFastPath2", "testFastPath3", "testFastPath4",
                    "testFastPath5", "testFastPath6", "testFastPath7", "testFastPath8", "testFastPath9",
                    "testFastPath10", "testFastPath11", "testFastPath12", "testFastPath13", "testFastPath14", "testFastPath15",
                    "testSlowPath1,testSlowPath2,testSlowPath3,testSlowPath4,testSlowPath5,testSlowPath6,testSlowPath7,testSlowPath8,testSlowPath9,testSlowPath10");
            for (String test : tests) {
                TestFramework.runWithFlags("-XX:+TieredCompilation", "--enable-preview", "-XX:CompileCommand=dontinline,java.lang.ScopedValue::slowGet", "-DTest=" + test);
            }
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
        forceCompilation("testFastPath1");
    }

    private static void forceCompilation(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method m = TestScopedValue.class.getDeclaredMethod(name, parameterTypes);
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        TestFramework.assertCompiledByC2(m);
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
        forceCompilation("testFastPath2");
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
        forceCompilation("testFastPath3");
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet", IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath4() {
        double res = 0;
        for (int i = 0; i < 10_000; i++) {
            res = sv.get().getValue(); // should be hoisted out of loop and loop should optimize out
        }
        return res;
    }

    @Run(test = "testFastPath4", mode = RunMode.STANDALONE)
    private void testFastPath4Runner() throws Exception {
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath4() != 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        forceCompilation("testFastPath4");
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 4" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 5" })
    public static void testFastPath5() {
        Object unused = svObject.get(); // cannot be removed if result not used
    }

    @Run(test = "testFastPath5", mode = RunMode.STANDALONE)
    private void testFastPath5Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath5();
                    }
                });
        forceCompilation("testFastPath5");
    }

    static Object testFastPath6Field;
    @ForceInline
    static void testFastPath6Helper(int i, Object o) {
        if (i != 10) {
            testFastPath6Field = o;
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 4" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 5" })
    public static void testFastPath6() {
        Object unused = svObject.get(); // cannot be removed even if result not used (after opts)
        int i;
        for (i = 0; i < 10; i++);
        testFastPath6Helper(i, unused);
    }

    @Run(test = "testFastPath6", mode = RunMode.STANDALONE)
    private void testFastPath6Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath6();
                        testFastPath6Helper(9, null);
                    }
                });
        forceCompilation("testFastPath6");
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet", IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath7(boolean[] flags) {
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

    @Run(test = "testFastPath7", mode = RunMode.STANDALONE)
    private void testFastPath7Runner() throws Exception {
        boolean[] allTrue = new boolean[10_000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[10_000];
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath7(allTrue) != 42) {
                            throw new RuntimeException();
                        }
                        if (testFastPath7(allFalse) != 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        forceCompilation("testFastPath7", boolean[].class);
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.LOAD_D, "1" })
    public static double testFastPath8(boolean[] flags) {
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

    @Run(test = "testFastPath8", mode = RunMode.STANDALONE)
    private void testFastPath8Runner() throws Exception {
        boolean[] allTrue = new boolean[10_000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[10_000];
        ScopedValue.where(svFinal, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = svFinal.get();
                    for (int i = 0; i < 20_000; i++) {
                        if (testFastPath8(allTrue) != 42) {
                            throw new RuntimeException();
                        }
                        if (testFastPath8(allFalse) != 42) {
                            throw new RuntimeException();
                        }
                    }
                });
        forceCompilation("testFastPath8", boolean[].class);
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    public static Object testFastPath9(boolean[] flags) {
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

    @Run(test = "testFastPath9", mode = RunMode.STANDALONE)
    private void testFastPath9Runner() throws Exception {
        boolean[] allTrue = new boolean[10_000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[10_000];
        ScopedValue.where(svObject, new MyDouble(42)).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath9(allTrue);
                        testFastPath9(allFalse);
                    }
                });
        forceCompilation("testFastPath9", boolean[].class);
    }
    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    public static Object testFastPath10(boolean[] flags) {
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
        forceCompilation("testFastPath10", boolean[].class);
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 7" })
    public static Object testFastPath11() {
        // test commoning when the result of one is unused
        Object unused = svObject.get();
        return svObject.get();
    }

    @Run(test = "testFastPath11", mode = RunMode.STANDALONE)
    private void testFastPath11Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath11();
                    }
                });
        forceCompilation("testFastPath11");
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 7" })
    public static Object testFastPath12() {
        // test commoning when the result of one is unused
        int i;
        for (i = 0; i < 10; i++) {

        }
        final Object result = testFastPath12Inlined(i);
        Object unused = svObject.get();
        return result;
    }

    @ForceInline
    private static Object testFastPath12Inlined(int i) {
        Object result = null;
        if (i == 10) {
            result = svObject.get();
        }
        return result;
    }

    @Run(test = "testFastPath12", mode = RunMode.STANDALONE)
    private void testFastPath12Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath12();
                        testFastPath12Inlined(0);
                    }
                });
        forceCompilation("testFastPath12");
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.IF, ">= 3", IRNode.LOAD_P_OR_N, ">= 5" })
    @IR(counts = {IRNode.IF, "<= 4", IRNode.LOAD_P_OR_N, "<= 6" })
    public static Object testFastPath13() {
        // checks code shape once fully expanded
        return svObject.get();
    }

    @Run(test = "testFastPath13", mode = RunMode.STANDALONE)
    private void testFastPath13Runner() throws Exception {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath13();
                    }
                });
        forceCompilation("testFastPath13");
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF_METHOD, "slowGet"})
    @IR(counts = {IRNode.LOAD_VECTOR_D, ">=1" })
    public static void testFastPath14(double[] src, double[] dst) {
        for (int i = 0; i < 10_000; i++) {
            dst[i] = src[i] * sv.get().getValue();
        }
    }

    @Run(test = "testFastPath14", mode = RunMode.STANDALONE)
    private void testFastPath14Runner() throws Exception {
        double[] array = new double[10_000];
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    MyDouble unused = sv.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath14(array, array);
                    }
                });
        forceCompilation("testFastPath14", double[].class, double[].class);
    }

    // Check uncommon trap is recorded at the right byte code (a cache miss) so on re-compilation,
    // the cache null check still branches to an uncommon trap
    @Test
    @IR(counts = {IRNode.UNSTABLE_IF_TRAP, ">= 1" })
    public static Object testFastPath15() {
        return svObject.get();
    }

    @Run(test = "testFastPath15", mode = RunMode.STANDALONE)
    private void testFastPath15Runner() throws Exception {
        // Profile data will report a single of the 2 cache locations as a hit
        runAndCompile15();
        // Force a cache miss
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    testFastPath15();
                });
        Method m = TestScopedValue.class.getDeclaredMethod("testFastPath15");
        TestFramework.assertDeoptimizedByC2(m);
        // Compile again
        runAndCompile15();
    }

    private static void runAndCompile15() throws NoSuchMethodException {
        ScopedValue.where(svObject, new Object()).run(
                () -> {
                    Object unused = svObject.get();
                    for (int i = 0; i < 20_000; i++) {
                        testFastPath15();
                    }
                });
        forceCompilation("testFastPath15");
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

    @Test
    @IR(counts = {IRNode.CALL_OF_METHOD, "slowGet", "1", IRNode.LOAD_VECTOR_D, ">=1" })
    public static void testSlowPath10(double[] src, double[] dst) {
        ScopedValue<MyDouble> localSV = sv;
        for (int i = 0; i < 10_000; i++) {
            dst[i] = src[i] * localSV.get().getValue();
        }
    }

    @Run(test = "testSlowPath10")
    private void testSlowPath10Runner() throws Exception {
        double[] array = new double[10_000];
        ScopedValue.where(sv, new MyDouble(42)).run(
                () -> {
                    testSlowPath10(array, array);
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
