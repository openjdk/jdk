/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypes.*;

import static compiler.lib.ir_framework.IRNode.ALLOC;
import static compiler.lib.ir_framework.IRNode.PREDICATE_TRAP;
import static compiler.lib.ir_framework.IRNode.UNSTABLE_IF_TRAP;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @key randomness
 * @summary Test value class calling convention optimizations.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.valhalla.inlinetypes.TestCallingConvention 0
 */

/*
 * @test
 * @key randomness
 * @summary Test value class calling convention optimizations.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.valhalla.inlinetypes.TestCallingConvention 1
 */

/*
 * @test
 * @key randomness
 * @summary Test value class calling convention optimizations.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.valhalla.inlinetypes.TestCallingConvention 2
 */

/*
 * @test
 * @key randomness
 * @summary Test value class calling convention optimizations.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.valhalla.inlinetypes.TestCallingConvention 3
 */

/*
 * @test
 * @key randomness
 * @summary Test value class calling convention optimizations.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.valhalla.inlinetypes.TestCallingConvention 4
 */

/*
 * @test
 * @key randomness
 * @summary Test value class calling convention optimizations.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.valhalla.inlinetypes.TestCallingConvention 5
 */

/*
 * @test
 * @key randomness
 * @summary Test value class calling convention optimizations.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.valhalla.inlinetypes.TestCallingConvention 6
 */

@ForceCompileClassInitializer
public class TestCallingConvention {

    private final static WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    static {
        try {
            Class<?> clazz = TestCallingConvention.class;
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(MyValue2.class, boolean.class);
            test32_mh = lookup.findVirtual(clazz, "test32_interp", mt);

            mt = MethodType.methodType(Object.class, boolean.class);
            test33_mh = lookup.findVirtual(clazz, "test33_interp", mt);

            mt = MethodType.methodType(int.class);
            test37_mh = lookup.findVirtual(Test37Value.class, "test", mt);

            mt = MethodType.methodType(MyValue2.class);
            test54_mh = lookup.findVirtual(clazz, "test54_callee", mt);

            mt = MethodType.methodType(MyValue2.class, boolean.class);
            test56_mh = lookup.findVirtual(clazz, "test56_callee", mt);

            mt = MethodType.methodType(MyValue2.class, MyValue2.class);
            test59_mh = lookup.findStatic(clazz, "test59_callee", mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    public TestCallingConvention() {
        test15_vt = MyValue3.create();
        test16_vt = MyValue3.create();
        test17_vt = MyValue3.create();
        test18_vt = MyValue4.create();
        test19_vt = MyValue4.create();
        test20_vt = MyValue4.create();
        test21_vt = MyValue3.create();
        test29_vt = MyValue3.create();
        test50_vt = MyValue3.create();
        test50_vt2 = MyValue3.create();
        super();
    }

    public static void main(String[] args) {

        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;
        // Don't generate bytecodes but call through runtime for reflective calls
        scenarios[0].addFlags("-Dsun.reflect.inflationThreshold=10000");
        scenarios[1].addFlags("-Dsun.reflect.inflationThreshold=10000");
        scenarios[3].addFlags("-XX:-UseArrayFlattening");
        scenarios[4].addFlags("-XX:-UseTLAB");

        InlineTypes.getFramework()
                   .addScenarios(scenarios[Integer.parseInt(args[0])])
                   .addHelperClasses(MyValue1.class,
                                     MyValue2.class,
                                     MyValue2Inline.class,
                                     MyValue3.class,
                                     MyValue3Inline.class,
                                     MyValue4.class)
                   .start();
    }

    // Helper methods and classes

    private void deoptimize(String name, Class<?>... params) {
        try {
            TestFramework.deoptimize(getClass().getDeclaredMethod(name, params));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    // Test interpreter to compiled code with various signatures
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test1(MyValue2 v) {
        return v.hash();
    }

    @Run(test = "test1")
    public void test1_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test1(v);
        Asserts.assertEQ(result, v.hashInterpreted());
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test2(int i1, MyValue2 v, int i2) {
        return v.hash() + i1 - i2;
    }

    @Run(test = "test2")
    public void test2_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test2(rI, v, 2*rI);
        Asserts.assertEQ(result, v.hashInterpreted() - rI);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test3(long l1, MyValue2 v, long l2) {
        return v.hash() + l1 - l2;
    }

    @Run(test = "test3")
    public void test3_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test3(rL, v, 2*rL);
        Asserts.assertEQ(result, v.hashInterpreted() - rL);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test4(int i, MyValue2 v, long l) {
        return v.hash() + i + l;
    }

    @Run(test = "test4")
    public void test4_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test4(rI, v, rL);
        Asserts.assertEQ(result, v.hashInterpreted() + rL + rI);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test5(long l, MyValue2 v, int i) {
        return v.hash() + i + l;
    }

    @Run(test = "test5")
    public void test5_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test5(rL, v, rI);
        Asserts.assertEQ(result, v.hashInterpreted() + rL + rI);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test6(long l, MyValue1 v1, int i, MyValue2 v2) {
        return v1.hash() + i + l + v2.hash();
    }

    @Run(test = "test6")
    public void test6_verifier() {
        MyValue1 v1 = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue2 v2 = MyValue2.createWithFieldsInline(rI, rD);
        long result = test6(rL, v1, rI, v2);
        Asserts.assertEQ(result, v1.hashInterpreted() + rL + rI + v2.hashInterpreted());
    }

    // Test compiled code to interpreter with various signatures
    @DontCompile
    public long test7_interp(MyValue2 v) {
        return v.hash();
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test7(MyValue2 v) {
        return test7_interp(v);
    }

    @Run(test = "test7")
    public void test7_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test7(v);
        Asserts.assertEQ(result, v.hashInterpreted());
    }

    @DontCompile
    public long test8_interp(int i1, MyValue2 v, int i2) {
        return v.hash() + i1 - i2;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test8(int i1, MyValue2 v, int i2) {
        return test8_interp(i1, v, i2);
    }

    @Run(test = "test8")
    public void test8_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test8(rI, v, 2*rI);
        Asserts.assertEQ(result, v.hashInterpreted() - rI);
    }

    @DontCompile
    public long test9_interp(long l1, MyValue2 v, long l2) {
        return v.hash() + l1 - l2;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test9(long l1, MyValue2 v, long l2) {
        return test9_interp(l1, v, l2);
    }

    @Run(test = "test9")
    public void test9_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test9(rL, v, 2*rL);
        Asserts.assertEQ(result, v.hashInterpreted() - rL);
    }

    @DontCompile
    public long test10_interp(int i, MyValue2 v, long l) {
        return v.hash() + i + l;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test10(int i, MyValue2 v, long l) {
        return test10_interp(i, v, l);
    }

    @Run(test = "test10")
    public void test10_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test10(rI, v, rL);
        Asserts.assertEQ(result, v.hashInterpreted() + rL + rI);
    }

    @DontCompile
    public long test11_interp(long l, MyValue2 v, int i) {
        return v.hash() + i + l;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test11(long l, MyValue2 v, int i) {
        return test11_interp(l, v, i);
    }

    @Run(test = "test11")
    public void test11_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        long result = test11(rL, v, rI);
        Asserts.assertEQ(result, v.hashInterpreted() + rL + rI);
    }

    @DontCompile
    public long test12_interp(long l, MyValue1 v1, int i, MyValue2 v2) {
        return v1.hash() + i + l + v2.hash();
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test12(long l, MyValue1 v1, int i, MyValue2 v2) {
        return test12_interp(l, v1, i, v2);
    }

    @Run(test = "test12")
    public void test12_verifier() {
        MyValue1 v1 = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue2 v2 = MyValue2.createWithFieldsInline(rI, rD);
        long result = test12(rL, v1, rI, v2);
        Asserts.assertEQ(result, v1.hashInterpreted() + rL + rI + v2.hashInterpreted());
    }

    // Test that debug info at a call is correct
    @DontCompile
    public long test13_interp(MyValue2 v, MyValue1[] va, boolean deopt) {
        if (deopt) {
            // uncommon trap
            deoptimize("test13", MyValue2.class, MyValue1[].class, boolean.class, long.class);
        }
        return v.hash() + va[0].hash() + va[1].hash();
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test13(MyValue2 v, MyValue1[] va, boolean flag, long l) {
        return test13_interp(v, va, flag) + l;
    }

    @Run(test = "test13")
    public void test13_verifier(RunInfo info) {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        va[0] = MyValue1.createWithFieldsDontInline(rI, rL);
        va[1] = MyValue1.createWithFieldsDontInline(rI, rL);
        long result = test13(v, va, !info.isWarmUp(), rL);
        Asserts.assertEQ(result, v.hashInterpreted() + va[0].hash() + va[1].hash() + rL);
    }

    // Test deoptimization at call return with value object returned in registers
    @DontCompile
    public MyValue2 test14_interp(boolean deopt) {
        if (deopt) {
            // uncommon trap
            deoptimize("test14", boolean.class);
        }
        return MyValue2.createWithFieldsInline(rI, rD);
    }

    @Test
    public MyValue2 test14(boolean flag) {
        return test14_interp(flag);
    }

    @Run(test = "test14")
    public void test14_verifier(RunInfo info) {
        MyValue2 result = test14(!info.isWarmUp());
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        Asserts.assertEQ(v, result);
    }

    // Return value objects in registers from interpreter -> compiled
    @NullRestricted
    final MyValue3 test15_vt;

    @DontCompile
    public MyValue3 test15_interp() {
        return test15_vt;
    }

    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue3 test15() {
        return test15_interp();
    }

    @Run(test = "test15")
    public void test15_verifier() {
        test15_vt.verify(test15());
    }

    // Return value objects in registers from compiled -> interpreter
    @NullRestricted
    final MyValue3 test16_vt;

    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue3 test16() {
        return test16_vt;
    }

    @Run(test = "test16")
    public void test16_verifier() {
        MyValue3 vt = test16();
        test16_vt.verify(vt);
    }

    // Return value objects in registers from compiled -> compiled
    @NullRestricted
    final MyValue3 test17_vt;

    @DontInline
    public MyValue3 test17_comp() {
        return test17_vt;
    }

    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue3 test17() {
        return test17_comp();
    }

    @Run(test = "test17")
    public void test17_verifier(RunInfo info) throws Exception {
        Method helper_m = getClass().getDeclaredMethod("test17_comp");
        if (!info.isWarmUp() && TestFramework.isCompiled(helper_m)) {
            TestFramework.compile(helper_m, CompLevel.C2);
            TestFramework.assertCompiledByC2(helper_m);
        }

        test17_vt.verify(test17());
    }

    // Same tests as above but with a value class that cannot be returned in registers

    // Return value objects in registers from interpreter -> compiled
    @NullRestricted
    final MyValue4 test18_vt;

    @DontCompile
    public MyValue4 test18_interp() {
        return test18_vt;
    }

    MyValue4 test18_vt2;

    @Test
    public void test18() {
        test18_vt2 = test18_interp();
    }

    @Run(test = "test18")
    public void test18_verifier() {
        test18();
        test18_vt.verify(test18_vt2);
    }

    // Return value objects in registers from compiled -> interpreter
    @NullRestricted
    final MyValue4 test19_vt;

    @Test
    public MyValue4 test19() {
        return test19_vt;
    }

    @Run(test = "test19")
    public void test19_verifier() {
        MyValue4 vt = test19();
        test19_vt.verify(vt);
    }

    // Return value objects in registers from compiled -> compiled
    @NullRestricted
    final MyValue4 test20_vt;

    @DontInline
    public MyValue4 test20_comp() {
        return test20_vt;
    }

    MyValue4 test20_vt2;

    @Test
    public void test20() {
        test20_vt2 = test20_comp();
    }

    @Run(test = "test20")
    public void test20_verifier(RunInfo info) throws Exception {
        Method helper_m = getClass().getDeclaredMethod("test20_comp");
        if (!info.isWarmUp() && TestFramework.isCompiled(helper_m)) {
            TestFramework.compile(helper_m, CompLevel.C2);
            TestFramework.assertCompiledByC2(helper_m);
        }
        test20();
        test20_vt.verify(test20_vt2);
    }

    // Test no result from inlined method for incremental inlining
    @NullRestricted
    final MyValue3 test21_vt;

    public MyValue3 test21_inlined() {
        throw new RuntimeException();
    }

    @Test
    public MyValue3 test21() {
        try {
            return test21_inlined();
        } catch (RuntimeException ex) {
            return test21_vt;
        }
    }

    @Run(test = "test21")
    public void test21_verifier() {
        MyValue3 vt = test21();
        test21_vt.verify(vt);
    }

    // Test returning a non-flattened value object as fields
    MyValue3 test22_vt = MyValue3.create();

    @Test
    public MyValue3 test22() {
        return (MyValue3) test22_vt;
    }

    @Run(test = "test22")
    public void test22_verifier() {
        MyValue3 vt = test22();
        test22_vt.verify(vt);
    }

    // Test calling a method that has circular register/stack dependencies when unpacking value class arguments
    @LooselyConsistentValue
    static value class TestValue23 {
        double f1;

        TestValue23(double val) {
            f1 = val;
        }
    }

    static double test23Callee(int i1, int i2, int i3, int i4, int i5, int i6,
                               TestValue23 v1, TestValue23 v2, TestValue23 v3, TestValue23 v4, TestValue23 v5, TestValue23 v6, TestValue23 v7, TestValue23 v8,
                               double d1, double d2, double d3, double d4, double d5, double d6, double d7, double d8) {
        return i1 + i2 + i3 + i4 + i5 + i6 + v1.f1 + v2.f1 + v3.f1 + v4.f1 + v5.f1 + v6.f1 + v7.f1 + v8.f1 + d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8;
    }

    @Test
    public double test23(int i1, int i2, int i3, int i4, int i5, int i6,
                         TestValue23 v1, TestValue23 v2, TestValue23 v3, TestValue23 v4, TestValue23 v5, TestValue23 v6, TestValue23 v7, TestValue23 v8,
                         double d1, double d2, double d3, double d4, double d5, double d6, double d7, double d8) {
        return test23Callee(i1, i2, i3, i4, i5, i6,
                            v1, v2, v3, v4, v5, v6, v7, v8,
                            d1, d2, d3, d4, d5, d6, d7, d8);
    }

    @Run(test = "test23")
    public void test23_verifier() {
        TestValue23 vt = new TestValue23(rI);
        double res1 = test23(rI, rI, rI, rI, rI, rI,
                            vt, vt, vt, vt, vt, vt, vt, vt,
                            rI, rI, rI, rI, rI, rI, rI, rI);
        double res2 = test23Callee(rI, rI, rI, rI, rI, rI,
                                   vt, vt, vt, vt, vt, vt, vt, vt,
                                   rI, rI, rI, rI, rI, rI, rI, rI);
        double res3 = 6*rI + 8*rI + 8*rI;
        Asserts.assertEQ(res1, res2);
        Asserts.assertEQ(res2, res3);
    }

    // Should not return a nullable value object as fields
    @Test
    public MyValue2 test24() {
        return null;
    }

    @Run(test = "test24")
    public void test24_verifier() {
        MyValue2 vt = test24();
        Asserts.assertEQ(vt, null);
    }

    // Same as test24 but with control flow and inlining
    @ForceInline
    public MyValue2 test26_callee(boolean b) {
        if (b) {
            return null;
        } else {
            return MyValue2.createWithFieldsInline(rI, rD);
        }
    }

    @Test
    public MyValue2 test26(boolean b) {
        return test26_callee(b);
    }

    @Run(test = "test26")
    public void test26_verifier() {
        MyValue2 vt = test26(true);
        Asserts.assertEQ(vt, null);
        vt = test26(false);
        Asserts.assertEQ(MyValue2.createWithFieldsInline(rI, rD), vt);
    }

    // Test calling convention with deep hierarchy of flattened fields
    @LooselyConsistentValue
    static value class Test27Value1 {
        Test27Value2 valueField;

        private Test27Value1(Test27Value2 val2) {
            valueField = val2;
        }

        @DontInline
        public int test(Test27Value1 val1) {
            return valueField.test(valueField) + val1.valueField.test(valueField);
        }
    }

    @LooselyConsistentValue
    static value class Test27Value2 {
        Test27Value3 valueField;

        private Test27Value2(Test27Value3 val3) {
            valueField = val3;
        }

        @DontInline
        public int test(Test27Value2 val2) {
            return valueField.test(valueField) + val2.valueField.test(valueField);
        }
    }

    @LooselyConsistentValue
    static value class Test27Value3 {
        int x;

        private Test27Value3(int x) {
            this.x = x;
        }

        @DontInline
        public int test(Test27Value3 val3) {
            return x + val3.x;
        }
    }

    @Test
    public int test27(Test27Value1 val) {
        return val.test(val);
    }

    @Run(test = "test27")
    public void test27_verifier() {
        Test27Value3 val3 = new Test27Value3(rI);
        Test27Value2 val2 = new Test27Value2(val3);
        Test27Value1 val1 = new Test27Value1(val2);
        int result = test27(val1);
        Asserts.assertEQ(result, 8*rI);
    }

    static final MyValue1 test28Val = MyValue1.createWithFieldsDontInline(rI, rL);

    @Test
    public String test28() {
        return test28Val.toString();
    }

    @Run(test = "test28")
    @Warmup(0)
    public void test28_verifier() {
        String result = test28();
    }

    // Test calling a method returning a value object as fields via reflection
    @NullRestricted
    MyValue3 test29_vt;

    @Test
    public MyValue3 test29() {
        return test29_vt;
    }

    @Run(test = "test29")
    public void test29_verifier() throws Exception {
        MyValue3 vt = (MyValue3)TestCallingConvention.class.getDeclaredMethod("test29").invoke(this);
        test29_vt.verify(vt);
    }

    @Test
    public MyValue3 test30(MyValue3[] array) {
        MyValue3 result = MyValue3.create();
        array[0] = result;
        return result;
    }

    @Run(test = "test30")
    public void test30_verifier() throws Exception {
        MyValue3[] array = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);
        MyValue3 vt = (MyValue3)TestCallingConvention.class.getDeclaredMethod("test30", MyValue3[].class).invoke(this, (Object)array);
        array[0].verify(vt);
    }

    MyValue3 test31_vt;

    @Test
    public MyValue3 test31() {
        MyValue3 result = MyValue3.create();
        test31_vt = result;
        return result;
    }

    @Run(test = "test31")
    public void test31_verifier() throws Exception {
        MyValue3 vt = (MyValue3)TestCallingConvention.class.getDeclaredMethod("test31").invoke(this);
        test31_vt.verify(vt);
    }

    // Test deoptimization at call return with value object returned in registers.
    // Same as test14, except the interpreted method is called via a MethodHandle.
    static MethodHandle test32_mh;

    @DontCompile
    public MyValue2 test32_interp(boolean deopt) {
        if (deopt) {
            // uncommon trap
            deoptimize("test32", boolean.class);
        }
        return MyValue2.createWithFieldsInline(rI+32, rD);
    }

    @Test
    public MyValue2 test32(boolean flag) throws Throwable {
        return (MyValue2)test32_mh.invokeExact(this, flag);
    }

    @Run(test = "test32")
    public void test32_verifier(RunInfo info) throws Throwable {
        MyValue2 result = test32(!info.isWarmUp());
        MyValue2 v = MyValue2.createWithFieldsInline(rI+32, rD);
        Asserts.assertEQ(v, result);
    }

    // Same as test32, except the return type is not flattenable.
    static MethodHandle test33_mh;

    @DontCompile
    public Object test33_interp(boolean deopt) {
        if (deopt) {
            // uncommon trap
            deoptimize("test33", boolean.class);
        }
        return MyValue2.createWithFieldsInline(rI+33, rD);
    }

    @Test
    public MyValue2 test33(boolean flag) throws Throwable {
        Object o = test33_mh.invokeExact(this, flag);
        return (MyValue2)o;
    }

    @Run(test = "test33")
    public void test33_verifier(RunInfo info) throws Throwable {
        MyValue2 result = test33(!info.isWarmUp());
        MyValue2 v = MyValue2.createWithFieldsInline(rI+33, rD);
        Asserts.assertEQ(v, result);
    }

    // Test selection of correct entry point in SharedRuntime::handle_wrong_method
    static boolean test34_deopt = false;

    @DontInline
    public static long test34_callee(MyValue2 vt, int i1, int i2, int i3, int i4) {
        Asserts.assertEQ(i1, rI);
        Asserts.assertEQ(i2, rI);
        Asserts.assertEQ(i3, rI);
        Asserts.assertEQ(i4, rI);

        if (test34_deopt) {
            // uncommon trap
            int result = 0;
            for (int i = 0; i < 10; ++i) {
                result += rL;
            }
            return vt.hash() + i1 + i2 + i3 + i4 + result;
        }
        return vt.hash() + i1 + i2 + i3 + i4;
    }

    @Test
    public static long test34(MyValue2 vt, int i1, int i2, int i3, int i4) {
        return test34_callee(vt, i1, i2, i3, i4);
    }

    @Run(test = "test34")
    @Warmup(10000) // Make sure test34_callee is compiled
    public void test34_verifier(RunInfo info) {
        MyValue2 vt = MyValue2.createWithFieldsInline(rI, rD);
        long result = test34(vt, rI, rI, rI, rI);
        Asserts.assertEQ(result, vt.hash()+4*rI);
        if (!info.isWarmUp()) {
            test34_deopt = true;
            for (int i = 0; i < 100; ++i) {
                result = test34(vt, rI, rI, rI, rI);
                Asserts.assertEQ(result, vt.hash()+4*rI+10*rL);
            }
        }
    }

    // Test OSR compilation of method with scalarized argument
    @Test
    public static long test35(MyValue2 vt, int i1, int i2, int i3, int i4) {
        int result = 0;
        // Trigger OSR compilation
        for (int i = 0; i < 10_000; ++i) {
            result += i1;
        }
        return vt.hash() + i1 + i2 + i3 + i4 + result;
    }

    @Run(test = "test35")
    public void test35_verifier() {
        MyValue2 vt = MyValue2.createWithFieldsInline(rI, rD);
        long result = test35(vt, rI, rI, rI, rI);
        Asserts.assertEQ(result, vt.hash()+10004*rI);
    }

    // Same as test31 but with GC in callee to verify that the
    // pre-allocated buffer for the returned value object remains valid.
    MyValue3 test36_vt;

    @Test
    public MyValue3 test36() {
        MyValue3 result = MyValue3.create();
        test36_vt = result;
        System.gc();
        return result;
    }

    @Run(test = "test36")
    public void test36_verifier() throws Exception {
        MyValue3 vt = (MyValue3)TestCallingConvention.class.getDeclaredMethod("test36").invoke(this);
        test36_vt.verify(vt);
    }

    // Test method resolution with scalarized value object receiver at invokespecial
    static final MethodHandle test37_mh;

    @LooselyConsistentValue
    static value class Test37Value {
        int x = rI;

        @DontInline
        public int test() {
            return x;
        }
    }

    @Test
    public int test37(Test37Value vt) throws Throwable {
        // Generates invokespecial call of Test37Value::test
        return (int)test37_mh.invokeExact(vt);
    }

    @Run(test = "test37")
    public void test37_verifier() throws Throwable {
        Test37Value vt = new Test37Value();
        int res = test37(vt);
        Asserts.assertEQ(res, rI);
    }

    // Test passing/returning an empty value object
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValueEmpty test38(MyValueEmpty vt) {
        return vt.copy(vt);
    }

    @Run(test = "test38")
    public void test38_verifier() {
        MyValueEmpty vt = new MyValueEmpty();
        MyValueEmpty res = test38(vt);
        Asserts.assertEQ(res, vt);
    }

    @LooselyConsistentValue
    static value class LargeValueWithOops {
        // Use all 6 int registers + 50/2 on stack = 29
        Object o1 = null;
        Object o2 = null;
        Object o3 = null;
        Object o4 = null;
        Object o5 = null;
        Object o6 = null;
        Object o7 = null;
        Object o8 = null;
        Object o9 = null;
        Object o10 = null;
        Object o11 = null;
        Object o12 = null;
        Object o13 = null;
        Object o14 = null;
        Object o15 = null;
        Object o16 = null;
        Object o17 = null;
        Object o18 = null;
        Object o19 = null;
        Object o20 = null;
        Object o21 = null;
        Object o22 = null;
        Object o23 = null;
        Object o24 = null;
        Object o25 = null;
        Object o26 = null;
        Object o27 = null;
        Object o28 = null;
        Object o29 = null;
    }

    @LooselyConsistentValue
    static value class LargeValueWithoutOops {
        // Use all 6 int registers + 50/2 on stack = 29
        int i1 = 0;
        int i2 = 0;
        int i3 = 0;
        int i4 = 0;
        int i5 = 0;
        int i6 = 0;
        int i7 = 0;
        int i8 = 0;
        int i9 = 0;
        int i10 = 0;
        int i11 = 0;
        int i12 = 0;
        int i13 = 0;
        int i14 = 0;
        int i15 = 0;
        int i16 = 0;
        int i17 = 0;
        int i18 = 0;
        int i19 = 0;
        int i20 = 0;
        int i21 = 0;
        int i22 = 0;
        int i23 = 0;
        int i24 = 0;
        int i25 = 0;
        int i26 = 0;
        int i27 = 0;
        int i28 = 0;
        int i29 = 0;
        // Use all 7 float registers
        double d1 = 0;
        double d2 = 0;
        double d3 = 0;
        double d4 = 0;
        double d5 = 0;
        double d6 = 0;
        double d7 = 0;
        double d8 = 0;
    }

    // Test passing/returning a large value object with oop fields
    @Test
    public static LargeValueWithOops test39(LargeValueWithOops vt) {
        return vt;
    }

    @Run(test = "test39")
    public void test39_verifier() {
        LargeValueWithOops vt = new LargeValueWithOops();
        LargeValueWithOops res = test39(vt);
        Asserts.assertEQ(res, vt);
    }

    // Test passing/returning a large value object with only int/float fields
    @Test
    public static LargeValueWithoutOops test40(LargeValueWithoutOops vt) {
        return vt;
    }

    @Run(test = "test40")
    public void test40_verifier() {
        LargeValueWithoutOops vt = new LargeValueWithoutOops();
        LargeValueWithoutOops res = test40(vt);
        Asserts.assertEQ(res, vt);
    }

    // Test passing/returning an empty value object together with non-empty
    // value objects such that only some value class arguments are scalarized.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValueEmpty test41(MyValue1 vt1, MyValueEmpty vt2, MyValue1 vt3) {
        return vt2.copy(vt2);
    }

    @Run(test = "test41")
    public void test41_verifier() {
        MyValueEmpty res = test41(MyValue1.createDefaultInline(), new MyValueEmpty(), MyValue1.createDefaultInline());
        Asserts.assertEQ(res, new MyValueEmpty());
    }

    // More empty value class tests with containers

    @LooselyConsistentValue
    static value class EmptyContainer {
        @NullRestricted
        private MyValueEmpty empty;

        @ForceInline
        EmptyContainer(MyValueEmpty empty) {
            this.empty = empty;
        }

        @ForceInline
        MyValueEmpty getInline() { return empty; }

        @DontInline
        MyValueEmpty getNoInline() { return empty; }
    }

    @LooselyConsistentValue
    static value class MixedContainer {
        public int val;
        @NullRestricted
        private EmptyContainer empty;

        @ForceInline
        MixedContainer(int val, EmptyContainer empty) {
            this.val = val;
            this.empty = empty;
        }

        @ForceInline
        EmptyContainer getInline() { return empty; }

        @DontInline
        EmptyContainer getNoInline() { return empty; }
    }

    // Empty value object return
    @Test
    @IR(failOn = {LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS})
    public MyValueEmpty test42() {
        EmptyContainer c = new EmptyContainer(new MyValueEmpty());
        return c.getInline();
    }

    @Run(test = "test42")
    public void test42_verifier() {
        MyValueEmpty empty = test42();
        Asserts.assertEquals(empty, new MyValueEmpty());
    }

    // Empty value class container return
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public EmptyContainer test43(EmptyContainer c) {
        return c;
    }

    @Run(test = "test43")
    public void test43_verifier() {
        EmptyContainer empty = new EmptyContainer(new MyValueEmpty());
        EmptyContainer c = test43(empty);
        Asserts.assertEquals(c, empty);
    }

    // Empty value class container (mixed) return
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MixedContainer test44() {
        MixedContainer c = new MixedContainer(rI, new EmptyContainer(new MyValueEmpty()));
        c = new MixedContainer(rI, c.getInline());
        return c;
    }

    @Run(test = "test44")
    public void test44_verifier() {
        MixedContainer c = test44();
        Asserts.assertEquals(c, new MixedContainer(rI, new EmptyContainer(new MyValueEmpty())));
    }

    // Empty value class container argument
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public EmptyContainer test45(EmptyContainer c) {
        return new EmptyContainer(c.getInline());
    }

    @Run(test = "test45")
    public void test45_verifier() {
        EmptyContainer empty = new EmptyContainer(new MyValueEmpty());
        EmptyContainer c = test45(empty);
        Asserts.assertEquals(c, empty);
    }

    // Empty value class container and mixed container arguments
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValueEmpty test46(EmptyContainer c1, MixedContainer c2, MyValueEmpty empty) {
        c2 = new MixedContainer(c2.val, c1);
        return c2.getNoInline().getNoInline();
    }

    @Run(test = "test46")
    public void test46_verifier() {
        MyValueEmpty empty = test46(new EmptyContainer(new MyValueEmpty()), new MixedContainer(0, new EmptyContainer(new MyValueEmpty())), new MyValueEmpty());
        Asserts.assertEquals(empty, new MyValueEmpty());
    }

    // No receiver and only empty argument
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public static MyValueEmpty test47(MyValueEmpty empty) {
        return empty;
    }

    @Run(test = "test47")
    public void test47_verifier() {
        MyValueEmpty empty = test47(new MyValueEmpty());
        Asserts.assertEquals(empty,new MyValueEmpty());
    }

    // No receiver and only empty container argument
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public static MyValueEmpty test48(EmptyContainer empty) {
        return empty.getNoInline();
    }

    @Run(test = "test48")
    public void test48_verifier() {
        MyValueEmpty empty = test48(new EmptyContainer(new MyValueEmpty()));
        Asserts.assertEquals(empty, new MyValueEmpty());
    }

    // Test conditional value class return with incremental inlining
    public MyValue3 test49_inlined1(boolean b) {
        if (b) {
            return MyValue3.create();
        } else {
            return MyValue3.create();
        }
    }

    public MyValue3 test49_inlined2(boolean b) {
        return test49_inlined1(b);
    }

    @Test
    public void test49(boolean b) {
        test49_inlined2(b);
    }

    @Run(test = "test49")
    public void test49_verifier() {
        test49(true);
        test49(false);
    }

    // Variant of test49 with result verification (triggered different failure mode)
    @NullRestricted
    final MyValue3 test50_vt;
    @NullRestricted
    final MyValue3 test50_vt2;

    public MyValue3 test50_inlined1(boolean b) {
        if (b) {
            return test50_vt;
        } else {
            return test50_vt2;
        }
    }

    public MyValue3 test50_inlined2(boolean b) {
        return test50_inlined1(b);
    }

    @Test
    public void test50(boolean b) {
        MyValue3 vt = test50_inlined2(b);
        vt.verify(b ? test50_vt : test50_vt2);
    }

    @Run(test = "test50")
    public void test50_verifier() {
        test50(true);
        test50(false);
    }

    // Test stack repair with stack slots reserved for monitors
    private static final Object lock1 = new Object();
    private static final Object lock2 = new Object();
    private static final Object lock3 = new Object();

    @DontInline
    static void test51_callee() { }

    @Test
    public void test51(MyValue1 val) {
        synchronized (lock1) {
            test51_callee();
        }
    }

    @Run(test = "test51")
    public void test51_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsInline(rI, rL);
        test51(vt);
    }

    @DontInline
    static void test52_callee() { }

    @Test
    public void test52(MyValue1 val) {
        synchronized (lock1) {
            synchronized (lock2) {
                test52_callee();
            }
        }
    }

    @Run(test = "test52")
    public void test52_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsInline(rI, rL);
        test52(vt);
    }

    @DontInline
    static void test53_callee() { }

    @Test
    public void test53(MyValue1 val) {
        synchronized (lock1) {
            synchronized (lock2) {
                synchronized (lock3) {
                    test53_callee();
                }
            }
        }
    }

    @Run(test = "test53")
    public void test53_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsInline(rI, rL);
        test53(vt);
    }

    static MethodHandle test54_mh;

    @DontInline
    public MyValue2 test54_callee() {
        return MyValue2.createWithFieldsInline(rI, rD);
    }

    // Test that method handle invocation does not block scalarization of return value
    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS})
    public long test54(Method m, boolean b1, boolean b2) throws Throwable {
        MyInterface obj = MyValue2.createWithFieldsInline(rI, rD);
        if (b1) {
            obj = (MyValue2)test54_mh.invokeExact(this);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
            return obj.hash();
        }
        return -1;
    }

    @Run(test = "test54")
    @Warmup(10000)
    public void test54_verifier(RunInfo info) throws Throwable {
        Asserts.assertEQ(test54(info.getTest(), true, false), -1L);
        Asserts.assertEQ(test54(info.getTest(), false, false), -1L);
        if (!info.isWarmUp()) {
            MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
            Asserts.assertEQ(test54(info.getTest(), true, true), v.hash());
        }
    }

    @DontInline
    public MyValue2 test55_callee() {
        return MyValue2.createWithFieldsInline(rI, rD);
    }

    // Test scalarization of nullable return value that is unused
    @Test
    public void test55() {
        test55_callee();
    }

    @Run(test = "test55")
    public void test55_verifier() {
        test55();
    }

    static MethodHandle test56_mh;

    @DontInline
    public MyValue2 test56_callee(boolean b) {
        return b ? MyValue2.createWithFieldsInline(rI, rD) : null;
    }

    // Test that scalarization of nullable return works properly for method handle calls
    @Test
    public MyValue2 test56(boolean b) throws Throwable {
        return (MyValue2)test56_mh.invokeExact(this, b);
    }

    @Run(test = "test56")
    @Warmup(10000)
    public void test56_verifier(RunInfo info) throws Throwable {
        MyValue2 vt = MyValue2.createWithFieldsInline(rI, rD);
        Asserts.assertEQ(vt, test56(true));
        if (!info.isWarmUp()) {
            Asserts.assertEQ(null, test56(false));
        }
    }

    static boolean expectedUseArrayFlattening = WHITE_BOX.getBooleanVMFlag("UseArrayFlattening");

    // Test value class return from native method
    @Test
    public boolean test57() {
        return WHITE_BOX.getBooleanVMFlag("UseArrayFlattening");
    }

    @Run(test = "test57")
    public void test57_verifier() {
        Asserts.assertEQ(test57(), expectedUseArrayFlattening);
    }

    // Test abstract value class with flat fields
    @LooselyConsistentValue
    abstract value class MyAbstract58 {
        @NullRestricted
        MyValue58Inline nullfree = new MyValue58Inline();

        MyValue58Inline nullable = new MyValue58Inline();
    }

    @LooselyConsistentValue
    value class MyValue58Inline {
        int x = rI;
    }

    @LooselyConsistentValue
    value class MyValue58A extends MyAbstract58 {
    }

    @LooselyConsistentValue
    value class MyValue58B extends MyAbstract58 {
        int x = rI;
    }

    @LooselyConsistentValue
    value class MyValue58C extends MyAbstract58 {
        int x = rI;

        @NullRestricted
        MyValue1 nullfree = MyValue1.DEFAULT;

        MyValue1 nullable = null;
    }

    @Test
    public MyValue58C test58(MyValue58A arg1, MyValue58B arg2, MyValue58C arg3) {
        Asserts.assertEQ(arg1, new MyValue58A());
        Asserts.assertEQ(arg2, new MyValue58B());
        Asserts.assertEQ(arg3, new MyValue58C());
        return arg3;
    }

    @Run(test = "test58")
    public void test58_verifier() {
        Asserts.assertEQ(test58(new MyValue58A(), new MyValue58B(), new MyValue58C()), new MyValue58C());
    }

    static MethodHandle test59_mh;

    public static MyValue2 test59_callee(MyValue2 arg) {
        int div = 0;
        int res = 42 / div; // Always throws an ArithmeticException
        return arg;
    }

    // Method handle with a scalarized return that will always throw an exception
    @Test
    public static MyValue2 test59(MyValue2 val) throws Throwable {
        return (MyValue2)test59_mh.invokeExact(val);
    }

    @Run(test = "test59")
    @Warmup(10000) // Trigger compilation of LambdaForm method
    public void test59_verifier() throws Throwable {
        try {
            test59(null);
        } catch (ArithmeticException e) {
            // Expected
        }
    }
}
