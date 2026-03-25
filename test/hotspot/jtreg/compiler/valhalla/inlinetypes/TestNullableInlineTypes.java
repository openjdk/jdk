/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import test.java.lang.invoke.lib.InstructionHelper;

import java.util.Objects;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypes.*;

import static compiler.lib.ir_framework.IRNode.ALLOC;
import static compiler.lib.ir_framework.IRNode.CMP_N;
import static compiler.lib.ir_framework.IRNode.CMP_P;
import static compiler.lib.ir_framework.IRNode.PREDICATE_TRAP;
import static compiler.lib.ir_framework.IRNode.UNSTABLE_IF_TRAP;

/*
 * @test
 * @key randomness
 * @summary Test correct handling of nullable value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main compiler.valhalla.inlinetypes.TestNullableInlineTypes 0
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of nullable value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main compiler.valhalla.inlinetypes.TestNullableInlineTypes 1
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of nullable value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main compiler.valhalla.inlinetypes.TestNullableInlineTypes 2
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of nullable value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main/othervm/timeout=300 compiler.valhalla.inlinetypes.TestNullableInlineTypes 3
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of nullable value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main compiler.valhalla.inlinetypes.TestNullableInlineTypes 4
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of nullable value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main compiler.valhalla.inlinetypes.TestNullableInlineTypes 5
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of nullable value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main compiler.valhalla.inlinetypes.TestNullableInlineTypes 6
 */

@ForceCompileClassInitializer
public class TestNullableInlineTypes {

    public TestNullableInlineTypes() {
        valueField1 = testValue1;
        flatField = MyValue1.DEFAULT;
        wrapperField = new MyValue1Wrapper(testValue1);
        test97_res1 = MyValue3.create();
        test97_res3 = MyValue3.create();
        field2 = new MyValue104();
        field4 = new MyValueEmpty();
        super();
    }

    public static void main(String[] args) {

        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;
        scenarios[3].addFlags("-XX:-MonomorphicArrayCheck", "-XX:+UseArrayFlattening");
        scenarios[4].addFlags("-XX:-MonomorphicArrayCheck");

        InlineTypes.getFramework()
                   .addScenarios(scenarios[Integer.parseInt(args[0])])
                   .addHelperClasses(MyValue1.class,
                                     MyValue2.class,
                                     MyValue2Inline.class,
                                     MyValue3.class,
                                     MyValue3Inline.class)
                   .start();
    }

    static {
        // Make sure RuntimeException is loaded to prevent uncommon traps in IR verified tests
        RuntimeException tmp = new RuntimeException("42");
        try {
            Class<?> clazz = TestNullableInlineTypes.class;
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType test18_mt = MethodType.methodType(void.class, MyValue1.class);
            test18_mh1 = lookup.findStatic(clazz, "test18_target1", test18_mt);
            test18_mh2 = lookup.findStatic(clazz, "test18_target2", test18_mt);

            MethodType test19_mt = MethodType.methodType(void.class, MyValue1.class);
            test19_mh1 = lookup.findStatic(clazz, "test19_target1", test19_mt);
            test19_mh2 = lookup.findStatic(clazz, "test19_target2", test19_mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    @NullRestricted
    private static final MyValue1 testValue1 = MyValue1.createWithFieldsInline(rI, rL);

    private static final MyValue1[] testValue1Array = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 3, MyValue1.DEFAULT);
    static {
        for (int i = 0; i < 3; ++i) {
            testValue1Array[i] = testValue1;
        }
    }

    MyValue1 nullField;

    @NullRestricted
    MyValue1 valueField1;

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public long test1(MyValue1 vt) {
        long result = 0;
        try {
            result = vt.hash();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        return result;
    }

    @Run(test = "test1")
    public void test1_verifier() {
        long result = test1(null);
        Asserts.assertEquals(result, 0L);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public long test2(MyValue1 vt) {
        long result = 0;
        try {
            result = vt.hashInterpreted();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        return result;
    }

    @Run(test = "test2")
    public void test2_verifier() {
        long result = test2(null);
        Asserts.assertEquals(result, 0L);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public long test3() {
        long result = 0;
        try {
            if ((Object)nullField != null) {
                throw new RuntimeException("nullField should be null");
            }
            result = nullField.hash();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        return result;
    }

    @Run(test = "test3")
    public void test3_verifier() {
        long result = test3();
        Asserts.assertEquals(result, 0L);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test4() {
        try {
            valueField1 = nullField;
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Run(test = "test4")
    public void test4_verifier() {
        test4();
    }

    @Test
    // TODO 8284443 When passing vt to test5_inline and incrementally inlining, we lose the oop
    @IR(applyIfOr = {"InlineTypePassFieldsAsArgs", "false", "AlwaysIncrementalInline", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS})
    public MyValue1 test5(MyValue1 vt) {
        Object o = vt;
        vt = (MyValue1)o;
        vt = test5_dontinline(vt);
        vt = test5_inline(vt);
        return vt;
    }

    @Run(test = "test5")
    public void test5_verifier() {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        Asserts.assertEquals(test5(val), val);
        Asserts.assertEquals(test5(null), null);
    }

    @DontInline
    public MyValue1 test5_dontinline(MyValue1 vt) {
        return vt;
    }

    @ForceInline
    public MyValue1 test5_inline(MyValue1 vt) {
        return vt;
    }

    @Test
    public MyValue1 test6(Object obj) {
        MyValue1 vt = MyValue1.createWithFieldsInline(rI, rL);
        try {
            vt = (MyValue1)Objects.requireNonNull(obj);
        } catch (NullPointerException e) {
            // Expected
        }
        return vt;
    }

    @Run(test = "test6")
    public void test6_verifier() {
        MyValue1 vt = test6(null);
        Asserts.assertEquals(testValue1, vt);
    }

    @ForceInline
    public MyValue1 getNullInline() {
        return null;
    }

    @DontInline
    public MyValue1 getNullDontInline() {
        return null;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test7() {
        nullField = getNullInline();     // Should not throw
        nullField = getNullDontInline(); // Should not throw
        try {
            valueField1 = getNullInline();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            valueField1 = getNullDontInline();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Run(test = "test7")
    public void test7_verifier() {
        test7();
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test8() {
        try {
            valueField1 = nullField;
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Run(test = "test8")
    public void test8_verifier() {
        test8();
    }

    // Merge of two value objects, one being null
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test9(boolean flag) {
        MyValue1 v;
        if (flag) {
            v = valueField1;
        } else {
            v = nullField;
        }
        valueField1 = v;
    }

    @Run(test = "test9")
    public void test9_verifier() {
        test9(true);
        try {
            test9(false);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    // null constant
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test10(boolean flag) {
        MyValue1 val = flag ? valueField1 : null;
        valueField1 = val;
    }

    @Run(test = "test10")
    public void test10_verifier() {
        test10(true);
        try {
            test10(false);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    // null constant
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test11(boolean flag) {
        MyValue1 val = flag ? null : valueField1;
        valueField1 = val;
    }

    @Run(test = "test11")
    public void test11_verifier() {
        test11(false);
        try {
            test11(true);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    // null return
    int test12_cnt;

    @DontInline
    public MyValue1 test12_helper() {
        test12_cnt++;
        return nullField;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test12() {
        valueField1 = test12_helper();
    }

    @Run(test = "test12")
    public void test12_verifier() {
        try {
            test12_cnt = 0;
            test12();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        if (test12_cnt != 1) {
            throw new RuntimeException("call executed twice");
        }
    }

    // null return at virtual call
    class A {
        public MyValue1 test13_helper() {
            return nullField;
        }
    }

    class B extends A {
        public MyValue1 test13_helper() {
            return nullField;
        }
    }

    class C extends A {
        public MyValue1 test13_helper() {
            return nullField;
        }
    }

    class D extends C {
        public MyValue1 test13_helper() {
            return nullField;
        }
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test13(A a) {
        valueField1 = a.test13_helper();
    }

    @Run(test = "test13")
    public void test13_verifier() {
        A a = new A();
        A b = new B();
        A c = new C();
        A d = new D();
        try {
            test13(a);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            test13(b);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            test13(c);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            test13(d);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    // Test writing null to a (flat) value class array
    @ForceInline
    public void test14_inline(Object[] oa, Object o, int index) {
        oa[index] = o;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test14(MyValue1[] va, int index) {
        test14_inline(va, nullField, index);
    }

    @Run(test = "test14")
    public void test14_verifier() {
        int index = Math.abs(rI) % 3;
        try {
            test14(testValue1Array, index);
            throw new RuntimeException("No NPE thrown");
        } catch (NullPointerException e) {
            // Expected
        }
        Asserts.assertEQ(testValue1, testValue1Array[index]);
    }

    @DontInline
    MyValue1 getNullField1() {
        return nullField;
    }

    @DontInline
    MyValue1 getNullField2() {
        return null;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test15() {
        nullField = getNullField1(); // should not throw
        try {
            valueField1 = getNullField1();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            valueField1 = getNullField2();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Run(test = "test15")
    public void test15_verifier() {
        test15();
    }

    @DontInline
    public boolean test16_dontinline(MyValue1 vt) {
        return vt == null;
    }

    // Test c2c call passing null for a value class
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public boolean test16(Object arg) throws Exception {
        Method test16method = getClass().getMethod("test16_dontinline", MyValue1.class);
        return (boolean)test16method.invoke(this, arg);
    }

    @Run(test = "test16")
    @Warmup(10000) // Warmup to make sure 'test17_dontinline' is compiled
    public void test16_verifier() throws Exception {
        boolean res = test16(null);
        Asserts.assertTrue(res);
    }

    // Test scalarization of value class with non-flattenable field
    @LooselyConsistentValue
    final value class Test17Value {
        public final MyValue1 valueField;

        @ForceInline
        public Test17Value(MyValue1 valueField) {
            this.valueField = valueField;
        }
    }

    @Test
    // TODO 8284443 When passing testValue1 to the constructor in scalarized form and incrementally inlining, we lose the oop
    @IR(applyIfOr = {"InlineTypePassFieldsAsArgs", "false", "AlwaysIncrementalInline", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS})
    public Test17Value test17(boolean b) {
        Test17Value vt1 = new Test17Value(null);
        Test17Value vt2 = new Test17Value(testValue1);
        return b ? vt1 : vt2;
    }

    @Run(test = "test17")
    public void test17_verifier() {
        test17(true);
        test17(false);
    }

    static final MethodHandle test18_mh1;
    static final MethodHandle test18_mh2;

    static MyValue1 nullValue;

    @DontInline
    static void test18_target1(MyValue1 vt) {
        nullValue = vt;
    }

    @ForceInline
    static void test18_target2(MyValue1 vt) {
        nullValue = vt;
    }

    // Test passing null for a value class
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test18() throws Throwable {
        test18_mh1.invokeExact(nullValue);
        test18_mh2.invokeExact(nullValue);
    }

    @Run(test = "test18")
    @Warmup(11000) // Make sure lambda forms get compiled
    public void test18_verifier() {
        try {
            test18();
        } catch (Throwable t) {
            throw new RuntimeException("test18 failed", t);
        }
    }

    static MethodHandle test19_mh1;
    static MethodHandle test19_mh2;

    @DontInline
    static void test19_target1(MyValue1 vt) {
        nullValue = vt;
    }

    @ForceInline
    static void test19_target2(MyValue1 vt) {
        nullValue = vt;
    }

    // Same as test12 but with non-final mh
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test19() throws Throwable {
        test19_mh1.invokeExact(nullValue);
        test19_mh2.invokeExact(nullValue);
    }

    @Run(test = "test19")
    @Warmup(11000) // Make sure lambda forms get compiled
    public void test19_verifier() {
        try {
            test19();
        } catch (Throwable t) {
            throw new RuntimeException("test19 failed", t);
        }
    }

    // Same as test12/13 but with constant null
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test20(MethodHandle mh) throws Throwable {
        mh.invoke(null);
    }

    @Run(test = "test20")
    @Warmup(11000) // Make sure lambda forms get compiled
    public void test20_verifier() {
        try {
            test20(test18_mh1);
            test20(test18_mh2);
            test20(test19_mh1);
            test20(test19_mh2);
        } catch (Throwable t) {
            throw new RuntimeException("test20 failed", t);
        }
    }

    // Test writing null to a flattenable/non-flattenable value class field in a value class
    @LooselyConsistentValue
    value class Test21Value {
        MyValue1 valueField1;
        @NullRestricted
        MyValue1 valueField2;

        @ForceInline
        public Test21Value(MyValue1 valueField1, MyValue1 valueField2) {
            this.valueField1 = valueField1;
            this.valueField2 = valueField2;
        }

        @ForceInline
        public Test21Value test1() {
            return new Test21Value(null, this.valueField2); // Should not throw NPE
        }

        @ForceInline
        public Test21Value test2() {
            return new Test21Value(this.valueField1, null); // Should throw NPE
        }
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public Test21Value test21(Test21Value vt) {
        vt = vt.test1();
        try {
            vt = vt.test2();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        return vt;
    }

    @Run(test = "test21")
    public void test21_verifier() {
        test21(new Test21Value(null, MyValue1.createDefaultInline()));
    }

    @DontInline
    public MyValue1 test22_helper() {
        return nullField;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test22() {
        valueField1 = test22_helper();
    }

    @Run(test = "test22")
    public void test22_verifier() {
        try {
            test22();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    @IR(applyIfAnd = {"UseArrayFlattening", "true", "InlineTypePassFieldsAsArgs", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS})
    @IR(applyIfAnd = {"UseArrayFlattening", "false", "InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS})
    public void test23(MyValue1 val) {
        MyValue1[] arr = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        arr[0] = val;
    }

    @Run(test = "test23")
    public void test23_verifier() {
        MyValue1 val = null;
        try {
            test23(val);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    static MyValue1 nullBox;

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public MyValue1 test24() {
        return Objects.requireNonNull(nullBox);
    }

    @Run(test = "test24")
    public void test24_verifier() {
        try {
            test24();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @DontInline
    public void test25_callee(MyValue1 val) { }

    // Test that when checkcasting from null-ok to null-free and back to null-ok we
    // keep track of the information that the value object can never be null.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test25(boolean b, MyValue1 vt1, MyValue1 vt2) {
        vt1 = (MyValue1)vt1;
        Object obj = b ? vt1 : vt2; // We should not allocate here
        test25_callee((MyValue1) vt1);
        return ((MyValue1)obj).x;
    }

    @Run(test = "test25")
    public void test25_verifier(RunInfo info) {
        int res = test25(true, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
        res = test25(false, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
        if (!info.isWarmUp()) {
            try {
                test25(true, null, testValue1);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Test that chains of casts are folded and don't trigger an allocation
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public MyValue3 test26(MyValue3 vt) {
        return ((MyValue3)((Object)((MyValue3)(MyValue3)((MyValue3)((Object)vt)))));
    }

    @Run(test = "test26")
    public void test26_verifier() {
        MyValue3 vt = MyValue3.create();
        MyValue3 result = test26(vt);
        Asserts.assertEquals(result, vt);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public MyValue3 test27(MyValue3 vt) {
        return ((MyValue3)((Object)((MyValue3)(MyValue3)((MyValue3)((Object)vt)))));
    }

    @Run(test = "test27")
    public void test27_verifier() {
        MyValue3 vt = MyValue3.create();
        MyValue3 result = (MyValue3) test27(vt);
        Asserts.assertEquals(result, vt);
    }

    // Some more casting tests
    @Test
    public MyValue1 test28(MyValue1 vt, MyValue1 vtBox, int i) {
        MyValue1 result = null;
        if (i == 0) {
            result = (MyValue1)vt;
            result = null;
        } else if (i == 1) {
            result = (MyValue1)vt;
        } else if (i == 2) {
            result = vtBox;
        }
        return result;
    }

    @Run(test = "test28")
    public void test28_verifier() {
        MyValue1 result = test28(testValue1, null, 0);
        Asserts.assertEquals(result, null);
        result = test28(testValue1, testValue1, 1);
        Asserts.assertEquals(result, testValue1);
        result = test28(testValue1, null, 2);
        Asserts.assertEquals(result, null);
        result = test28(testValue1, testValue1, 2);
        Asserts.assertEquals(result, testValue1);
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public long test29(MyValue1 vt, MyValue1 vtBox) {
        long result = 0;
        for (int i = 0; i < 100; ++i) {
            MyValue1 box;
            if (i == 0) {
                box = (MyValue1)vt;
                box = null;
            } else if (i < 99) {
                box = (MyValue1)vt;
            } else {
                box = vtBox;
            }
            if (box != null) {
                result += box.hash();
            }
        }
        return result;
    }

    @Run(test = "test29")
    public void test29_verifier() {
        long result = test29(testValue1, null);
        Asserts.assertEquals(result, testValue1.hash()*98);
        result = test29(testValue1, testValue1);
        Asserts.assertEquals(result, testValue1.hash()*99);
    }

    // Test null check of value object receiver with incremental inlining
    public long test30_callee(MyValue1 vt) {
        long result = 0;
        try {
            result = vt.hashInterpreted();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        return result;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public long test30() {
        return test30_callee(nullField);
    }

    @Run(test = "test30")
    public void test30_verifier() {
        long result = test30();
        Asserts.assertEquals(result, 0L);
    }

    // Test casting null to unloaded value class
    value class Test31Value {
        private int i = 0;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public Object test31(Object o) {
        return (Test31Value)o;
    }

    @Run(test = "test31")
    public void test31_verifier() {
        test31(null);
    }

    private static final MyValue1 constNullRefField = null;

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public MyValue1 test32() {
        return constNullRefField;
    }

    @Run(test = "test32")
    public void test32_verifier() {
        MyValue1 result = test32();
        Asserts.assertEquals(result, null);
    }

    @LooselyConsistentValue
    static value class Test33Value1 {
        int x = 0;
    }

    @LooselyConsistentValue
    static value class Test33Value2 {
        Test33Value1 vt;

        public Test33Value2() {
            vt = new Test33Value1();
        }
    }

    @NullRestricted
    public static final Test33Value2 test33Val = new Test33Value2();

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public Test33Value2 test33() {
        return test33Val;
    }

    @Run(test = "test33")
    public void test33_verifier() {
        Test33Value2 result = test33();
        Asserts.assertEquals(result, test33Val);
    }

    // Verify that static nullable inline-type fields are not
    // treated as never-null by C2 when initialized at compile time.
    private static MyValue1 test34Val;

    @Test
    public void test34(MyValue1 vt) {
        if (test34Val == null) {
            test34Val = vt;
        }
    }

    @Run(test = "test34")
    public void test34_verifier(RunInfo info) {
        test34(testValue1);
        if (!info.isWarmUp()) {
            test34Val = null;
            test34(testValue1);
            Asserts.assertEquals(test34Val, testValue1);
        }
    }

    // Same as test17 but with non-allocated value object
    @Test
    public Test17Value test35(boolean b) {
        Test17Value vt1 = new Test17Value(null);
        if ((Object)vt1.valueField != null) {
            throw new RuntimeException("Should be null");
        }
        MyValue1 vt3 = MyValue1.createWithFieldsInline(rI, rL);
        Test17Value vt2 = new Test17Value(vt3);
        return b ? vt1 : vt2;
    }

    @Run(test = "test35")
    public void test35_verifier() {
        test35(true);
        test35(false);
    }

    // Test that when explicitly null checking a value object, we keep
    // track of the information that the value object can never be null.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test37(boolean b, MyValue1 vt1, MyValue1 vt2) {
        if (vt1 == null) {
            return 0;
        }
        // vt1 should be scalarized because it's always non-null
        Object obj = b ? vt1 : vt2; // We should not allocate vt2 here
        test25_callee(vt1);
        return ((MyValue1)obj).x;
    }

    @Run(test = "test37")
    public void test37_verifier() {
        int res = test37(true, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
        res = test37(false, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
    }

    // Test that when explicitly null checking a value object receiver,
    // we keep track of the information that the value object can never be null.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test38(boolean b, MyValue1 vt1, MyValue1 vt2) {
        vt1.hash(); // Inlined - Explicit null check
        // vt1 should be scalarized because it's always non-null
        Object obj = b ? vt1 : vt2; // We should not allocate vt2 here
        test25_callee(vt1);
        return ((MyValue1)obj).x;
    }

    @Run(test = "test38")
    public void test38_verifier() {
        int res = test38(true, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
        res = test38(false, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
    }

    // Test that when implicitly null checking a value object receiver,
    // we keep track of the information that the value object can never be null.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test39(boolean b, MyValue1 vt1, MyValue1 vt2) {
        vt1.hashInterpreted(); // Not inlined - Implicit null check
        // vt1 should be scalarized because it's always non-null
        Object obj = b ? vt1 : vt2; // We should not allocate vt2 here
        test25_callee(vt1);
        return ((MyValue1)obj).x;
    }

    @Run(test = "test39")
    public void test39_verifier() {
        int res = test39(true, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
        res = test39(false, testValue1, testValue1);
        Asserts.assertEquals(res, testValue1.x);
    }

    // Test NPE when casting constant null to a value class
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public MyValue1 test40() {
        Object NULL = null;
        MyValue1 val = (MyValue1)NULL;
        return Objects.requireNonNull(val);
    }

    @Run(test = "test40")
    public void test40_verifier() {
        try {
            test40();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    MyValue1 refField;
    @NullRestricted
    MyValue1 flatField;

    // Test scalarization of .ref
    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public int test41(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = refField;
        }
        return val.x;
    }

    @Run(test = "test41")
    public void test41_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test41(true), refField.x);
        Asserts.assertEquals(test41(false), testValue1.x);
        if (!info.isWarmUp()) {
            refField = null;
            try {
                Asserts.assertEquals(test41(false), testValue1.x);
                test41(true);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Same as test41 but with call to hash()
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test42(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = refField;
        }
        return val.hash();
    }

    @Run(test = "test42")
    public void test42_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test42(true), refField.hash());
        Asserts.assertEquals(test42(false), testValue1.hash());
        if (!info.isWarmUp()) {
            refField = null;
            try {
                Asserts.assertEquals(test42(false), testValue1.hash());
                test42(true);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @Test
    public MyValue1 test43(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = refField;
        }
        return val;
    }

    @Run(test = "test43")
    public void test43_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(refField, test43(true));
        Asserts.assertEquals(testValue1, test43(false));
        if (!info.isWarmUp()) {
            refField = null;
            Asserts.assertEquals(null, test43(true));
        }
    }

    // Test scalarization when .ref is referenced in safepoint debug info
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test44(boolean b1, boolean b2, Method m) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = refField;
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return val.x;
    }

    @Run(test = "test44")
    public void test44_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test44(true, false, info.getTest()), refField.x);
        Asserts.assertEquals(test44(false, false, info.getTest()), testValue1.x);
        if (!info.isWarmUp()) {
            refField = null;
            try {
                Asserts.assertEquals(test44(false, false, info.getTest()), testValue1.x);
                test44(true, false, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
            Asserts.assertEquals(test44(true, true, info.getTest()), refField.x);
            Asserts.assertEquals(test44(false, true, info.getTest()), testValue1.x);
        }
    }

    @Test
    public MyValue1 test45(boolean b1, boolean b2, Method m) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = refField;
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return val;
    }

    @Run(test = "test45")
    public void test45_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(refField, test45(true, false, info.getTest()));
        Asserts.assertEquals(testValue1, test45(false, false, info.getTest()));
        if (!info.isWarmUp()) {
            refField = null;
            Asserts.assertEquals(null, test45(true, false, info.getTest()));
            refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
            Asserts.assertEquals(refField, test45(true, true, info.getTest()));
            Asserts.assertEquals(testValue1, test45(false, true, info.getTest()));
        }
    }

    @Test
    @IR(failOn = {ALLOC, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public int test46(boolean b) {
        MyValue1 val = null;
        if (b) {
            val = MyValue1.createWithFieldsInline(rI, rL);
        }
        return val.x;
    }

    @Run(test = "test46")
    public void test46_verifier() {
        Asserts.assertEquals(test46(true), testValue1.x);
        try {
            test46(false);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public MyValue1 test47(boolean b) {
        MyValue1 val = null;
        if (b) {
            val = MyValue1.createWithFieldsInline(rI, rL);
        }
        return val;
    }

    @Run(test = "test47")
    public void test47_verifier() {
        Asserts.assertEquals(testValue1, test47(true));
        Asserts.assertEquals(null, test47(false));
    }

    @Test
    @IR(failOn = {ALLOC, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public int test48(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = null;
        }
        return val.x;
    }

    @Run(test = "test48")
    public void test48_verifier() {
        Asserts.assertEquals(test48(false), testValue1.x);
        try {
            test48(true);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public MyValue1 test49(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = null;
        }
        return val;
    }

    @Run(test = "test49")
    public void test49_verifier() {
        Asserts.assertEquals(testValue1, test49(false));
        Asserts.assertEquals(null, test49(true));
    }

    @ForceInline
    public Object test50_helper() {
        return flatField;
    }

    @Test
    @IR(failOn = {ALLOC, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public void test50(boolean b) {
        Object o = null;
        if (b) {
            o = testValue1;
        } else {
            o = test50_helper();
        }
        flatField = (MyValue1)o;
    }

    @Run(test = "test50")
    public void test50_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsInline(rI+1, rL+1);
        flatField = vt;
        test50(false);
        Asserts.assertEquals(vt, flatField);
        test50(true);
        Asserts.assertEquals(testValue1, flatField);
    }

    @LooselyConsistentValue
    static value class MyValue1Wrapper {
        MyValue1 vt;

        @ForceInline
        public MyValue1Wrapper(MyValue1 vt) {
            this.vt = vt;
        }

        @ForceInline
        public long hash() {
            return (vt != null) ? vt.hash() : 0;
        }
    }

    @NullRestricted
    MyValue1Wrapper wrapperField;

    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test51(boolean b) {
        MyValue1Wrapper val = new MyValue1Wrapper(null);
        if (b) {
            val = wrapperField;
        }
        return val.hash();
    }

    @Run(test = "test51")
    public void test51_verifier() {
        Asserts.assertEquals(test51(true), wrapperField.hash());
        Asserts.assertEquals(test51(false), (new MyValue1Wrapper(null)).hash());
    }

    @Test
    @IR(failOn = {ALLOC, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public boolean test52(boolean b) {
        MyValue1 val = MyValue1.createDefaultInline();
        if (b) {
            val = null;
        }
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        return w.vt == null;
    }

    @Run(test = "test52")
    public void test52_verifier() {
        Asserts.assertTrue(test52(true));
        Asserts.assertFalse(test52(false));
    }

    @Test
    @IR(failOn = {ALLOC, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public boolean test53(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = null;
        }
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        return w.vt == null;
    }

    @Run(test = "test53")
    public void test53_verifier() {
        Asserts.assertTrue(test53(true));
        Asserts.assertFalse(test53(false));
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test54(boolean b1, boolean b2) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = null;
        }
        MyValue1Wrapper w = new MyValue1Wrapper(null);
        if (b2) {
            w = new MyValue1Wrapper(val);
        }
        return w.hash();
    }

    @Run(test = "test54")
    public void test54_verifier() {
        MyValue1Wrapper w = new MyValue1Wrapper(MyValue1.createWithFieldsInline(rI, rL));
        Asserts.assertEquals(test54(false, false), (new MyValue1Wrapper(null)).hash());
        Asserts.assertEquals(test54(false, true), w.hash());
        Asserts.assertEquals(test54(true, false), (new MyValue1Wrapper(null)).hash());
        Asserts.assertEquals(test54(true, true), 0L);
    }

    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public int test55(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        if (b) {
            w = new MyValue1Wrapper(refField);
        }
        return w.vt.x;
    }

    @Run(test = "test55")
    public void test55_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test55(true), refField.x);
        Asserts.assertEquals(test55(false), testValue1.x);
        if (!info.isWarmUp()) {
            refField = null;
            try {
                Asserts.assertEquals(test55(false), testValue1.x);
                test55(true);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test56(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        if (b) {
            w = new MyValue1Wrapper(refField);
        }
        return w.vt.hash();
    }

    @Run(test = "test56")
    public void test56_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test56(true), refField.hash());
        Asserts.assertEquals(test56(false), testValue1.hash());
        if (!info.isWarmUp()) {
            refField = null;
            try {
                Asserts.assertEquals(test56(false), testValue1.hash());
                test56(true);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @Test
    public MyValue1 test57(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        if (b) {
            w = new MyValue1Wrapper(refField);
        }
        return w.vt;
    }

    @Run(test = "test57")
    public void test57_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(refField, test57(true));
        Asserts.assertEquals(testValue1, test57(false));
        if (!info.isWarmUp()) {
            refField = null;
            Asserts.assertEquals(null, test57(true));
        }
    }

    // Test scalarization when .ref is referenced in safepoint debug info
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test58(boolean b1, boolean b2, Method m) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        if (b1) {
            w = new MyValue1Wrapper(refField);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return w.vt.x;
    }

    @Run(test = "test58")
    public void test58_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test58(true, false, info.getTest()), refField.x);
        Asserts.assertEquals(test58(false, false, info.getTest()), testValue1.x);
        if (!info.isWarmUp()) {
            refField = null;
            try {
                Asserts.assertEquals(test58(false, false, info.getTest()), testValue1.x);
                test58(true, false, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
            Asserts.assertEquals(test58(true, true, info.getTest()), refField.x);
            Asserts.assertEquals(test58(false, true, info.getTest()), testValue1.x);
        }
    }

    @Test
    public MyValue1 test59(boolean b1, boolean b2, Method m) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        if (b1) {
            w = new MyValue1Wrapper(refField);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return w.vt;
    }

    @Run(test = "test59")
    public void test59_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(refField, test59(true, false, info.getTest()));
        Asserts.assertEquals(testValue1, test59(false, false, info.getTest()));
        if (!info.isWarmUp()) {
            refField = null;
            Asserts.assertEquals(null, test59(true, false, info.getTest()));
            refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
            Asserts.assertEquals(refField, test59(true, true, info.getTest()));
            Asserts.assertEquals(testValue1, test59(false, true, info.getTest()));
        }
    }

    @Test
    @IR(failOn = {ALLOC, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public int test60(boolean b) {
        MyValue1Wrapper w = new MyValue1Wrapper(null);
        if (b) {
            MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
            w = new MyValue1Wrapper(val);
        }
        return w.vt.x;
    }

    @Run(test = "test60")
    public void test60_verifier() {
        Asserts.assertEquals(test60(true), testValue1.x);
        try {
            test60(false);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public MyValue1 test61(boolean b) {
        MyValue1Wrapper w = new MyValue1Wrapper(null);
        if (b) {
            MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
            w = new MyValue1Wrapper(val);
        }
        return w.vt;
    }

    @Run(test = "test61")
    public void test61_verifier() {
        Asserts.assertEquals(testValue1, test61(true));
        Asserts.assertEquals(null, test61(false));
    }

    @Test
    @IR(failOn = {ALLOC, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public int test62(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        if (b) {
            w = new MyValue1Wrapper(null);
        }
        return w.vt.x;
    }

    @Run(test = "test62")
    public void test62_verifier() {
        Asserts.assertEquals(test62(false), testValue1.x);
        try {
            test62(true);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public MyValue1 test63(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1Wrapper w = new MyValue1Wrapper(val);
        if (b) {
            w = new MyValue1Wrapper(null);
        }
        return w.vt;
    }

    @Run(test = "test63")
    public void test63_verifier() {
        Asserts.assertEquals(testValue1, test63(false));
        Asserts.assertEquals(null, test63(true));
    }

    @ForceInline
    public MyValue1 test64_helper() {
        return flatField;
    }

    @Test
    @IR(failOn = {ALLOC, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public void test64(boolean b) {
        MyValue1Wrapper w = new MyValue1Wrapper(null);
        if (b) {
            w = new MyValue1Wrapper(testValue1);
        } else {
            w = new MyValue1Wrapper(test64_helper());
        }
        flatField = w.vt;
    }

    @Run(test = "test64")
    public void test64_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsInline(rI+1, rL+1);
        flatField = vt;
        test64(false);
        Asserts.assertEquals(vt, flatField);
        test64(true);
        Asserts.assertEquals(testValue1, flatField);
    }

    @Test
    @IR(failOn = {ALLOC, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test65(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = null;
        }
        if (val != null) {
            return val.hashPrimitive();
        }
        return 42;
    }

    @Run(test = "test65")
    public void test65_verifier() {
        Asserts.assertEquals(test65(true), 42L);
        Asserts.assertEquals(test65(false), MyValue1.createWithFieldsInline(rI, rL).hashPrimitive());
    }

    @ForceInline
    public Object test66_helper(Object arg) {
        return arg;
    }

    // Test that .ref arg does not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test66(boolean b1, boolean b2, MyValue1 arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test66_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).x;
    }

    @Run(test = "test66")
    public void test66_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test66(true, false, arg, info.getTest()), arg.x);
        Asserts.assertEquals(test66(false, false, arg, info.getTest()), testValue1.x);
        if (!info.isWarmUp()) {
            try {
                Asserts.assertEquals(test66(false, false, arg, info.getTest()), testValue1.x);
                test66(true, false, null, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            Asserts.assertEquals(test66(true, true, arg, info.getTest()), arg.x);
            Asserts.assertEquals(test66(false, true, arg, info.getTest()), testValue1.x);
        }
    }

    @DontInline
    public MyValue1 test67_helper1() {
        return refField;
    }

    @ForceInline
    public Object test67_helper2() {
        return test67_helper1();
    }

    // Test that .ref return does not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public long test67(boolean b1, boolean b2, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test67_helper2();
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).hash();
    }

    @Run(test = "test67")
    public void test67_verifier(RunInfo info) {
        refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test67(true, false, info.getTest()), refField.hash());
        Asserts.assertEquals(test67(false, false, info.getTest()), testValue1.hash());
        if (!info.isWarmUp()) {
            refField = null;
            try {
                Asserts.assertEquals(test67(false, false, info.getTest()), testValue1.hash());
                test67(true, false, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            refField = MyValue1.createWithFieldsInline(rI+1, rL+1);
            Asserts.assertEquals(test67(true, true, info.getTest()), refField.hash());
            Asserts.assertEquals(test67(false, true, info.getTest()), testValue1.hash());
        }
    }

    @ForceInline
    public Object test68_helper(Object arg) {
        MyValue1 tmp = (MyValue1)arg; // Result of cast is unused
        return arg;
    }

    // Test that scalarization enabled by cast is applied to parsing map
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test68(boolean b1, boolean b2, Object arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test68_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).x;
    }

    @Run(test = "test68")
    public void test68_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test68(true, false, arg, info.getTest()), arg.x);
        Asserts.assertEquals(test68(false, false, arg, info.getTest()), testValue1.x);
        if (!info.isWarmUp()) {
            try {
                Asserts.assertEquals(test68(false, false, arg, info.getTest()), testValue1.x);
                test68(true, false, null, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            Asserts.assertEquals(test68(true, true, arg, info.getTest()), arg.x);
            Asserts.assertEquals(test68(false, true, arg, info.getTest()), testValue1.x);
        }
    }

    @ForceInline
    public Object test69_helper(Object arg) {
        MyValue1 tmp = (MyValue1)arg; // Result of cast is unused
        return arg;
    }

    // Same as test68 but with ClassCastException
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test69(boolean b1, boolean b2, Object arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test69_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).x;
    }

    @Run(test = "test69")
    @Warmup(10000) // Make sure precise profile information is available
    public void test69_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test69(true, false, arg, info.getTest()), arg.x);
        Asserts.assertEquals(test69(false, false, arg, info.getTest()), testValue1.x);
        try {
            test69(true, false, 42, info.getTest());
            throw new RuntimeException("ClassCastException expected");
        } catch (ClassCastException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            try {
                Asserts.assertEquals(test69(false, false, arg, info.getTest()), testValue1.x);
                test69(true, false, null, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            Asserts.assertEquals(test69(true, true, arg, info.getTest()), arg.x);
            Asserts.assertEquals(test69(false, true, arg, info.getTest()), testValue1.x);
        }
    }

    @ForceInline
    public Object test70_helper(Object arg) {
        MyValue1 tmp = (MyValue1)arg; // Result of cast is unused
        return arg;
    }

    // Same as test68 but with ClassCastException and frequent NullPointerException
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test70(boolean b1, boolean b2, Object arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test70_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).x;
    }

    @Run(test = "test70")
    @Warmup(10000) // Make sure precise profile information is available
    public void test70_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test70(true, false, arg, info.getTest()), arg.x);
        Asserts.assertEquals(test70(false, false, arg, info.getTest()), testValue1.x);
        try {
            test70(true, false, 42, info.getTest());
            throw new RuntimeException("ClassCastException expected");
        } catch (ClassCastException e) {
            // Expected
        }
        try {
            Asserts.assertEquals(test70(false, false, arg, info.getTest()), testValue1.x);
            test70(true, false, null, info.getTest());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test70(true, true, arg, info.getTest()), arg.x);
            Asserts.assertEquals(test70(false, true, arg, info.getTest()), testValue1.x);
        }
    }

    @ForceInline
    public Object test71_helper(Object arg) {
        MyValue1 tmp = (MyValue1)arg; // Result of cast is unused
        return arg;
    }

    // Same as test68 but with .ref cast
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test71(boolean b1, boolean b2, Object arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test71_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).x;
    }

    @Run(test = "test71")
    public void test71_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test71(true, false, arg, info.getTest()), arg.x);
        Asserts.assertEquals(test71(false, false, arg, info.getTest()), testValue1.x);
        if (!info.isWarmUp()) {
            try {
                Asserts.assertEquals(test71(false, false, arg, info.getTest()), testValue1.x);
                test71(true, false, null, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            Asserts.assertEquals(test71(true, true, arg, info.getTest()), arg.x);
            Asserts.assertEquals(test71(false, true, arg, info.getTest()), testValue1.x);
        }
    }

    @ForceInline
    public Object test72_helper(Object arg) {
        MyValue1 tmp = (MyValue1)arg; // Result of cast is unused
        return arg;
    }

    // Same as test71 but with ClassCastException and hash() call
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public long test72(boolean b1, boolean b2, Object arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test72_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).hash();
    }

    @Run(test = "test72")
    @Warmup(10000) // Make sure precise profile information is available
    public void test72_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test72(true, false, arg, info.getTest()), arg.hash());
        Asserts.assertEquals(test72(false, false, arg, info.getTest()), testValue1.hash());
        try {
            test72(true, false, 42, info.getTest());
            throw new RuntimeException("ClassCastException expected");
        } catch (ClassCastException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            try {
                Asserts.assertEquals(test72(false, false, arg, info.getTest()), testValue1.hash());
                test72(true, false, null, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            Asserts.assertEquals(test72(true, true, arg, info.getTest()), arg.hash());
            Asserts.assertEquals(test72(false, true, arg, info.getTest()), testValue1.hash());
        }
    }

    @ForceInline
    public Object test73_helper(Object arg) {
        MyValue1 tmp = (MyValue1)arg; // Result of cast is unused
        return arg;
    }

    // Same as test71 but with ClassCastException and frequent NullPointerException
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test73(boolean b1, boolean b2, Object arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test73_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).x;
    }

    @Run(test = "test73")
    @Warmup(10000) // Make sure precise profile information is available
    public void test73_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test73(true, false, arg, info.getTest()), arg.x);
        Asserts.assertEquals(test73(false, false, arg, info.getTest()), testValue1.x);
        try {
            test73(true, false, 42, info.getTest());
            throw new RuntimeException("ClassCastException expected");
        } catch (ClassCastException e) {
            // Expected
        }
        try {
            Asserts.assertEquals(test73(false, false, arg, info.getTest()), testValue1.x);
            test73(true, false, null, info.getTest());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test73(true, true, arg, info.getTest()), arg.x);
            Asserts.assertEquals(test73(false, true, arg, info.getTest()), testValue1.x);
        }
    }

    @ForceInline
    public Object test74_helper(Object arg) {
        return (MyValue1)arg;
    }

    // Same as test73 but result of cast is used and hash() is called
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public long test74(boolean b1, boolean b2, Object arg, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test74_helper(arg);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).hash();
    }

    @Run(test = "test74")
    @Warmup(10000) // Make sure precise profile information is available
    public void test74_verifier(RunInfo info) {
        MyValue1 arg = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test74(true, false, arg, info.getTest()), arg.hash());
        Asserts.assertEquals(test74(false, false, arg, info.getTest()), testValue1.hash());
        try {
            test74(true, false, 42, info.getTest());
            throw new RuntimeException("ClassCastException expected");
        } catch (ClassCastException e) {
            // Expected
        }
        try {
            Asserts.assertEquals(test74(false, false, arg, info.getTest()), testValue1.hash());
            test74(true, false, null, info.getTest());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test74(true, true, arg, info.getTest()), arg.hash());
            Asserts.assertEquals(test74(false, true, arg, info.getTest()), testValue1.hash());
        }
    }

    // Test new merge path being added for exceptional control flow
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public MyValue1 test75(MyValue1 vt, Object obj) {
        try {
            vt = (MyValue1)obj;
            throw new RuntimeException("ClassCastException expected");
        } catch (ClassCastException e) {
            // Expected
        }
        return vt;
    }

    @Run(test = "test75")
    public void test75_verifier() {
        MyValue1 vt = testValue1;
        MyValue1 result = test75(vt, Integer.valueOf(rI));
        Asserts.assertEquals(vt, result);
    }

    @ForceInline
    public Object test76_helper() {
        return constNullRefField;
    }

    // Test that constant null field does not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test76(boolean b1, boolean b2, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test76_helper();
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        val = Objects.requireNonNull(val);
        return ((MyValue1)val).hash();
    }

    @Run(test = "test76")
    public void test76_verifier(RunInfo info) {
        Asserts.assertEquals(test76(false, false, info.getTest()), testValue1.hash());
        try {
            test76(true, false, info.getTest());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test76(false, true, info.getTest()), testValue1.hash());
            try {
                test76(true, true, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    private static final Object constObjectValField = MyValue1.createWithFieldsInline(rI+1, rL+1);

    @ForceInline
    public Object test77_helper() {
        return constObjectValField;
    }

    // Test that constant object field with value class content does not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test77(boolean b1, boolean b2, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test77_helper();
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)val).hash();
    }

    @Run(test = "test77")
    public void test77_verifier(RunInfo info) {
        Asserts.assertEquals(test77(true, false, info.getTest()), ((MyValue1)constObjectValField).hash());
        Asserts.assertEquals(test77(false, false, info.getTest()), testValue1.hash());
        if (!info.isWarmUp()) {
          Asserts.assertEquals(test77(true, false, info.getTest()), ((MyValue1)constObjectValField).hash());
          Asserts.assertEquals(test77(false, false, info.getTest()), testValue1.hash());
        }
    }

    @ForceInline
    public Object test78_helper() {
        return null;
    }

    // Test that constant null does not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test78(boolean b1, boolean b2, Method m) {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = test78_helper();
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        val = Objects.requireNonNull(val);
        return ((MyValue1)val).hash();
    }

    @Run(test = "test78")
    public void test78_verifier(RunInfo info) {
        Asserts.assertEquals(test78(false, false, info.getTest()), testValue1.hash());
        try {
            test78(true, false, info.getTest());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test78(false, true, info.getTest()), testValue1.hash());
            try {
                test78(true, true, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @ForceInline
    public Object test79_helper() {
        return null;
    }

    // Same as test78 but will trigger different order of PhiNode inputs
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test79(boolean b1, boolean b2, Method m) {
        Object val = test79_helper();
        if (b1) {
            val = MyValue1.createWithFieldsInline(rI, rL);
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        val = Objects.requireNonNull(val);
        return ((MyValue1)val).hash();
    }

    @Run(test = "test79")
    public void test79_verifier(RunInfo info) {
        Asserts.assertEquals(test79(true, false, info.getTest()), testValue1.hash());
        try {
            test79(false, false, info.getTest());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test79(true, true, info.getTest()), testValue1.hash());
            try {
                test79(false, true, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

// TODO 8325632 Fails with -XX:+UnlockExperimentalVMOptions -XX:PerMethodSpecTrapLimit=0 -XX:PerMethodTrapLimit=0
/*
    @ForceInline
    public Object test80_helper(Object obj, int i) {
        if ((i % 2) == 0) {
            return MyValue1.createWithFieldsInline(i, i);
        }
        return obj;
    }

    // Test that phi nodes referencing themselves (loops) do not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test80() {
        Object val = MyValue1.createWithFieldsInline(rI, rL);
        for (int i = 0; i < 100; ++i) {
            val = test80_helper(val, i);
        }
        return ((MyValue1)val).hash();
    }

    private long test80Result = 0;

    @Run(test = "test80")
    public void test80_verifier() {
        if (test80Result == 0) {
            test80Result = test80();
        }
        Asserts.assertEquals(test80(), test80Result);
    }

    @ForceInline
    public Object test81_helper(Object obj, int i) {
        if ((i % 2) == 0) {
            return MyValue1.createWithFieldsInline(i, i);
        }
        return obj;
    }

    // Test nested loops
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test81() {
        Object val = null;
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                for (int k = 0; k < 10; ++k) {
                    val = test81_helper(val, i + j + k);
                }
                val = test81_helper(val, i + j);
            }
            val = test81_helper(val, i);
        }
        return ((MyValue1)val).hash();
    }

    private long test81Result = 0;

    @Run(test = "test81")
    public void test81_verifier() {
        if (test81Result == 0) {
            test81Result = test81();
        }
        Asserts.assertEquals(test81(), test81Result);
    }

    @ForceInline
    public Object test82_helper(Object obj, int i) {
        if ((i % 2) == 0) {
            return MyValue1.createWithFieldsInline(i, i);
        }
        return obj;
    }

    // Test loops with casts
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test82() {
        Object val = null;
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                for (int k = 0; k < 10; ++k) {
                    val = test82_helper(val, i + j + k);
                }
                if (val != null) {
                    val = test82_helper(val, i + j);
                }
            }
            val = test82_helper(val, i);
        }
        return ((MyValue1)val).hash();
    }

    private long test82Result = 0;

    @Run(test = "test82")
    public void test82_verifier() {
        if (test82Result == 0) {
            test82Result = test81();
        }
        Asserts.assertEquals(test82(), test82Result);
    }
*/

    @ForceInline
    public Object test83_helper(boolean b) {
        if (b) {
            return MyValue1.createWithFieldsInline(rI, rL);
        }
        return null;
    }

    // Test that CastPP does not block scalarization in safepoints
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test83(boolean b, Method m) {
        Object val = test83_helper(b);
        if (val != null) {
            // Uncommon trap
            TestFramework.deoptimize(m);
            return ((MyValue1)val).hash();
        }
        return 0;
    }

    @Run(test = "test83")
    public void test83_verifier(RunInfo info) {
        Asserts.assertEquals(test83(false, info.getTest()), 0L);
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test83(true, info.getTest()), testValue1.hash());
        }
    }

    @ForceInline
    public Object test84_helper(Object obj, int i) {
        if ((i % 2) == 0) {
            return new MyValue1Wrapper(MyValue1.createWithFieldsInline(i, i));
        }
        return obj;
    }

    // Same as test80 but with wrapper
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test84() {
        Object val = new MyValue1Wrapper(MyValue1.createWithFieldsInline(rI, rL));
        for (int i = 0; i < 100; ++i) {
            val = test84_helper(val, i);
        }
        return ((MyValue1Wrapper)val).vt.hash();
    }

    private long test84Result = 0;

    @Run(test = "test84")
    public void test84_verifier() {
        if (test84Result == 0) {
            test84Result = test84();
        }
        Asserts.assertEquals(test84(), test84Result);
    }

    @ForceInline
    public Object test85_helper(Object obj, int i) {
        if ((i % 2) == 0) {
            return new MyValue1Wrapper(MyValue1.createWithFieldsInline(i, i));
        }
        return obj;
    }

    // Same as test81 but with wrapper
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public long test85() {
        Object val = new MyValue1Wrapper(null);
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                for (int k = 0; k < 10; ++k) {
                    val = test85_helper(val, i + j + k);
                }
                val = test85_helper(val, i + j);
            }
            val = test85_helper(val, i);
        }
        MyValue1 vt = ((MyValue1Wrapper)val).vt;
        vt = Objects.requireNonNull(vt);
        return vt.hash();
    }

    private long test85Result = 0;

    @Run(test = "test85")
    public void test85_verifier() {
        if (test85Result == 0) {
            test85Result = test85();
        }
        Asserts.assertEquals(test85(), test85Result);
    }

    static final class ObjectWrapper {
        public Object obj;

        @ForceInline
        public ObjectWrapper(Object obj) {
            this.obj = obj;
        }
    }

    // Test scalarization with phi referencing itself
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS},
        counts = {LOAD_OF_ANY_KLASS, " = 4"}) // 4 loads from the non-flattened MyValue1.v4 fields
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public long test86(MyValue1 vt) {
        ObjectWrapper val = new ObjectWrapper(vt);
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                val.obj = val.obj;
            }
        }
        return ((MyValue1)val.obj).hash();
    }

    @Run(test = "test86")
    public void test86_verifier() {
        test86(testValue1);
        Asserts.assertEquals(test86(testValue1), testValue1.hash());
    }

    @LooselyConsistentValue
    public static value class Test87C0 {
        int x = 0;
    }

    @LooselyConsistentValue
    public static value class Test87C1 {
        @NullRestricted
        Test87C0 field = new Test87C0();
    }

    @LooselyConsistentValue
    public static value class Test87C2 {
        @NullRestricted
        Test87C1 field = new Test87C1();
    }

    // Test merging field loads in return
    @Test
    public Test87C1 test87(boolean b, Test87C2 v1, Test87C2 v2) {
        if (b) {
            return v1.field;
        } else {
            return v2.field;
        }
    }

    @Run(test = "test87")
    public void test87_verifier() {
        Test87C2 v = new Test87C2();
        Asserts.assertEQ(test87(true, v, v), v.field);
        Asserts.assertEQ(test87(false, v, v), v.field);
    }

    @LooselyConsistentValue
    static value class Test88Value {
        int x = 0;
    }

    static class Test88MyClass {
        int x = 0;
        int y = rI;
    }

    @ForceInline
    Object test88Helper() {
        return new Test88Value();
    }

    // Test LoadNode::Identity optimization with always failing checkcast
    @Test
    public int test88() {
        Object obj = test88Helper();
        return ((Test88MyClass)obj).y;
    }

    @Run(test = "test88")
    public void test88_verifier() {
        try {
            test88();
            throw new RuntimeException("No ClassCastException thrown");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    // Same as test88 but with Phi
    @Test
    public int test89(boolean b) {
        Test88MyClass obj = b ? (Test88MyClass)test88Helper() : (Test88MyClass)test88Helper();
        return obj.y;
    }

    @Run(test = "test89")
    public void test89_verifier() {
        try {
            test89(false);
            throw new RuntimeException("No ClassCastException thrown");
        } catch (ClassCastException e) {
            // Expected
        }
        try {
            test89(true);
            throw new RuntimeException("No ClassCastException thrown");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    @ForceInline
    public boolean test90_inline(MyValue1 vt) {
        return vt == null;
    }

    // Test scalarization with speculative NULL type
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS})
    public boolean test90(Method m) throws Exception {
        Object arg = null;
        return (boolean)m.invoke(this, arg);
    }

    @Run(test = "test90")
    @Warmup(10000)
    public void test90_verifier() throws Exception {
        Method m = getClass().getMethod("test90_inline", MyValue1.class);
        Asserts.assertTrue(test90(m));
    }

    // Test that scalarization does not introduce redundant/unused checks
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, CMP_N, CMP_P})
    public Object test91(MyValue1 vt) {
        return vt;
    }

    @Run(test = "test91")
    public void test91_verifier() {
        Asserts.assertEQ(test91(testValue1), testValue1);
    }

    MyValue1 test92Field = testValue1;

    // Same as test91 but with field access
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, CMP_N, CMP_P})
    public Object test92() {
        return test92Field;
    }

    @Run(test = "test92")
    public void test92_verifier() {
        Asserts.assertEQ(test92(), testValue1);
    }

    private static final MethodHandle refCheckCast = InstructionHelper.buildMethodHandle(MethodHandles.lookup(),
        "refCheckCast",
        MethodType.methodType(MyValue2.class, TestNullableInlineTypes.class, MyValue1.class),
        CODE -> {
            CODE.
            aload(1).
            checkcast(MyValue2.class.describeConstable().orElseThrow()).
            areturn();
        });

    // Test checkcast that only passes with null
    @Test
    public Object test93(MyValue1 vt) throws Throwable {
        return refCheckCast.invoke(this, vt);
    }

    @Run(test = "test93")
    @Warmup(10000)
    public void test93_verifier() throws Throwable {
        Asserts.assertEQ(test93(null), null);
    }

    @DontInline
    public MyValue1 test94_helper1(MyValue1 vt) {
        return vt;
    }

    @ForceInline
    public MyValue1 test94_helper2(MyValue1 vt) {
        return test94_helper1(vt);
    }

    @ForceInline
    public MyValue1 test94_helper3(Object vt) {
        return test94_helper2((MyValue1)vt);
    }

    // Test that calling convention optimization prevents buffering of arguments
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC, " <= 2"}) // 1 MyValue2 allocation + 1 Integer allocation (if not the all-zero value)
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        counts = {ALLOC, " <= 3"}) // 1 MyValue1 allocation + 1 MyValue2 allocation + 1 Integer allocation (if not the all-zero value)
    public MyValue1 test94(MyValue1 vt) {
        MyValue1 res = test94_helper1(vt);
        vt = MyValue1.createWithFieldsInline(rI, rL);
        test94_helper1(vt);
        test94_helper2(vt);
        test94_helper3(vt);
        return res;
    }

    @Run(test = "test94")
    public void test94_verifier() {
        Asserts.assertEQ(test94(testValue1), testValue1);
        Asserts.assertEQ(test94(null), null);
    }

    @DontInline
    public static MyValue1 test95_helper1(MyValue1 vt) {
        return vt;
    }

    @ForceInline
    public static MyValue1 test95_helper2(MyValue1 vt) {
        return test95_helper1(vt);
    }

    @ForceInline
    public static MyValue1 test95_helper3(Object vt) {
        return test95_helper2((MyValue1)vt);
    }

    // Same as test94 but with static methods to trigger simple adapter logic
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC, " <= 2"}) // 1 MyValue2 allocation + 1 Integer allocation (if not the all-zero value)
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        counts = {ALLOC, " <= 3"}) // 1 MyValue1 allocation + 1 MyValue2 allocation + 1 Integer allocation (if not the all-zero value)
    public static MyValue1 test95(MyValue1 vt) {
        MyValue1 res = test95_helper1(vt);
        vt = MyValue1.createWithFieldsInline(rI, rL);
        test95_helper1(vt);
        test95_helper2(vt);
        test95_helper3(vt);
        return res;
    }

    @Run(test = "test95")
    public void test95_verifier() {
        Asserts.assertEQ(test95(testValue1), testValue1);
        Asserts.assertEQ(test95(null), null);
    }

    @DontInline
    public MyValue2 test96_helper1(boolean b) {
        return b ? null : MyValue2.createWithFieldsInline(rI, rD);
    }

    @ForceInline
    public MyValue2 test96_helper2() {
        return null;
    }

    @ForceInline
    public MyValue2 test96_helper3(boolean b) {
        return b ? null : MyValue2.createWithFieldsInline(rI, rD);
    }

    // Test that calling convention optimization prevents buffering of return values
    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "false"},
        counts = {ALLOC, " <= 1"}) // No allocation required if the MyValue2 return is the all-zero value
    public MyValue2 test96(int c, boolean b) {
        MyValue2 res = null;
        if (c == 1) {
            res = test96_helper1(b);
        } else if (c == 2) {
            res = test96_helper2();
        } else if (c == 3) {
            res = test96_helper3(b);
        }
        return res;
    }

    @Run(test = "test96")
    public void test96_verifier() {
        Asserts.assertEQ(null, test96(0, false));
        Asserts.assertEQ(MyValue2.createWithFieldsInline(rI, rD), test96(1, false));
        Asserts.assertEQ(null, test96(1, true));
        Asserts.assertEQ(null, test96(2, false));
        Asserts.assertEQ(MyValue2.createWithFieldsInline(rI, rD), test96(3, false));
        Asserts.assertEQ(null, test96(3, true));
    }

    @DontInline
    public MyValue3 test97_helper1(boolean b) {
        return b ? null : test97_res1;
    }

    @ForceInline
    public MyValue3 test97_helper2() {
        return null;
    }

    @ForceInline
    public MyValue3 test97_helper3(boolean b) {
        return b ? null : test97_res3;
    }

    @NullRestricted
    final MyValue3 test97_res1;

    @NullRestricted
    final MyValue3 test97_res3;

    // Same as test96 but with MyValue3 return
    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "false"},
        counts = {ALLOC, " <= 1"}) // No allocation required if the MyValue3 return is the all-zero value
    public MyValue3 test97(int c, boolean b) {
        MyValue3 res = null;
        if (c == 1) {
            res = test97_helper1(b);
        } else if (c == 2) {
            res = test97_helper2();
        } else if (c == 3) {
            res = test97_helper3(b);
        }
        return res;
    }

    @Run(test = "test97")
    public void test97_verifier() {
        Asserts.assertEQ(test97(0, false), null);
        Asserts.assertEQ(test97(1, false), test97_res1);
        Asserts.assertEQ(test97(1, true), null);
        Asserts.assertEQ(test97(2, false), null);
        Asserts.assertEQ(test97(3, false), test97_res3);
        Asserts.assertEQ(test97(3, true), null);
    }

    @LooselyConsistentValue
    static value class CircularValue1 {
        CircularValue1 val;
        int x;

        @ForceInline
        public CircularValue1(CircularValue1 val) {
            this.val = val;
            this.x = rI;
        }
    }

    // Test scalarization of value class with circularity in fields
    @Test
    public CircularValue1 test98(CircularValue1 val) {
        return new CircularValue1(val);
    }

    @Run(test = "test98")
    public void test98_verifier()  {
        CircularValue1 val = new CircularValue1(null);
        CircularValue1 res = test98(val);
        Asserts.assertEQ(res.x, rI);
        Asserts.assertEQ(res.val, val);
    }

    @LooselyConsistentValue
    static value class CircularValue2 {
        @NullRestricted
        CircularValue1 val;

        @ForceInline
        public CircularValue2(CircularValue1 val) {
            this.val = val;
        }
    }

    // Same as test98 but with circularity in class of flattened field
    @Test
    public CircularValue2 test99(CircularValue2 val) {
        return new CircularValue2(val.val);
    }

    @Run(test = "test99")
    public void test99_verifier()  {
        CircularValue1 val1 = new CircularValue1(null);
        CircularValue2 val2 = new CircularValue2(val1);
        CircularValue2 res = test99(val2);
        Asserts.assertEQ(res.val, val1);
    }

    @LooselyConsistentValue
    static value class CircularValue3 {
        CircularValue4 val;
        int x;

        @ForceInline
        public CircularValue3(CircularValue4 val, int x) {
            this.val = val;
            this.x = x;
        }
    }

    @LooselyConsistentValue
    static value class CircularValue4 {
        @NullRestricted
        CircularValue3 val;

        @ForceInline
        public CircularValue4(CircularValue3 val) {
            this.val = val;
        }
    }

    // Same as test94 but with "indirect" circularity through field of flattened field
    @Test
    public CircularValue4 test100(CircularValue4 val) {
        return new CircularValue4(new CircularValue3(val, rI));
    }

    @Run(test = "test100")
    public void test100_verifier()  {
        CircularValue3 val3 = new CircularValue3(null, 42);
        CircularValue4 val4 = new CircularValue4(val3);
        CircularValue4 res = test100(val4);
        Asserts.assertEQ(res.val, new CircularValue3(val4, rI));
    }

    @LooselyConsistentValue
    static value class CircularValue5 {
        @NullRestricted
        CircularValue6 val;
        int x;

        @ForceInline
        public CircularValue5(CircularValue6 val, int x) {
            this.val = val;
            this.x = x;
        }
    }

    @LooselyConsistentValue
    static value class CircularValue6 {
        CircularValue5 val;

        @ForceInline
        public CircularValue6(CircularValue5 val) {
            this.val = val;
        }
    }

    // Same as test100 but with different combination of field types
    @Test
    public CircularValue6 test101(CircularValue6 val) {
        return new CircularValue6(new CircularValue5(val, rI));
    }

    @Run(test = "test101")
    public void test101_verifier()  {
        CircularValue5 val5 = new CircularValue5(new CircularValue6(null), 42);
        CircularValue6 val6 = new CircularValue6(val5);
        CircularValue6 res = test101(val6);
        Asserts.assertEQ(res.val, new CircularValue5(val6, rI));
    }

    // Test merging of fields with different scalarization depth
    @Test
    public CircularValue1 test102(boolean b) {
        CircularValue1 val = new CircularValue1(new CircularValue1(null));
        if (b) {
            val = null;
        }
        return val;
    }

    @Run(test = "test102")
    public void test102_verifier() {
        Asserts.assertEQ(test102(false), new CircularValue1(new CircularValue1(null)));
        Asserts.assertEQ(test102(true), null);
    }

    // Might be incrementally inlined
    public static Object hide(Object obj) {
        return (MyValue1)obj;
    }

    // Test that the ConstraintCastNode::Ideal transformation propagates null-free information
    @Test
    public MyValue1 test103() {
        Object obj = hide(null);
        return (MyValue1)obj;
    }

    @Run(test = "test103")
    public void test103_verifier() {
        Asserts.assertEQ(test103(), null);
    }

    // Test null restricted fields

    @LooselyConsistentValue
    static value class MyValue104 {
        @NullRestricted
        static MyValue105 field1 = new MyValue105();

        @NullRestricted
        MyValue105 field2;

        @NullRestricted
        static MyValueEmpty field3 = new MyValueEmpty();

        @NullRestricted
        MyValueEmpty field4;

        @ForceInline
        public MyValue104() {
            this.field1 = new MyValue105();
            this.field2 = new MyValue105();
            this.field3 = new MyValueEmpty();
            this.field4 = new MyValueEmpty();
        }

        @ForceInline
        public MyValue104(MyValue105 val1, MyValue105 val2, MyValueEmpty val3, MyValueEmpty val4) {
            this.field1 = val1;
            this.field2 = val2;
            this.field3 = val3;
            this.field4 = val4;
        }
    }

    @LooselyConsistentValue
    static value class MyValue105 {
        int x = 42;
    }

    @NullRestricted
    static MyValue104 field1 = new MyValue104();

    @NullRestricted
    MyValue104 field2;

    @NullRestricted
    static MyValueEmpty field3 = new MyValueEmpty();

    @NullRestricted
    MyValueEmpty field4;

    @Test
    void test105(MyValue104 arg) {
        field1 = arg;
    }

    @Run(test = "test105")
    public void test105_verifier() {
        try {
            test105(null);
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test106() {
        field1 = null;
    }

    @Run(test = "test106")
    public void test106_verifier() {
        try {
            test106();
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test107(MyValue104 arg) {
        field2 = arg;
    }

    @Run(test = "test107")
    public void test107_verifier() {
        try {
            test107(null);
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test108(TestNullableInlineTypes t, MyValue104 arg) {
        t.field2 = arg;
    }

    @Run(test = "test108")
    public void test108_verifier() {
        try {
            test108(null, new MyValue104());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test109() {
        TestNullableInlineTypes t = null;
        t.field2 = null;
    }

    @Run(test = "test109")
    void test109_verifier() {
        try {
            test109();
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test110() {
        field2 = null;
    }

    @Run(test = "test110")
    public void test110_verifier() {
        try {
            test110();
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test111(MyValueEmpty arg) {
        field3 = arg;
    }

    @Run(test = "test111")
    public void test111_verifier() {
        try {
            test111(null);
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test112() {
        field3 = null;
    }

    @Run(test = "test112")
    public void test112_verifier() {
        try {
            test112();
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test113(MyValueEmpty arg) {
        field4 = arg;
    }

    @Run(test = "test113")
    public void test113_verifier() {
        try {
            test113(null);
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test114(TestNullableInlineTypes t, MyValueEmpty arg) {
        t.field4 = arg;
    }

    @Run(test = "test114")
    public void test114_verifier() {
        try {
            test114(null, new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test115(MyValueEmpty arg) {
        TestNullableInlineTypes t = null;
        t.field4 = arg;
    }

    @Run(test = "test115")
    public void test115_verifier() {
        try {
            test115(new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    void test116() {
        field4 = null;
    }

    @Run(test = "test116")
    public void test116_verifier() {
        try {
            test116();
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test117(MyValue105 val1, MyValue105 val2, MyValueEmpty val3, MyValueEmpty val4) {
        return new MyValue104(val1, val2, val3, val4);
    }

    @Run(test = "test117")
    public void test117_verifier() {
        try {
            test117(null, new MyValue105(), new MyValueEmpty(), new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test118(MyValue105 val1, MyValue105 val2, MyValueEmpty val3, MyValueEmpty val4) {
        return new MyValue104(val1, val2, val3, val4);
    }

    @Run(test = "test118")
    public void test118_verifier() {
        try {
            test118(new MyValue105(), null, new MyValueEmpty(), new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test119(MyValue105 val1, MyValue105 val2, MyValueEmpty val3, MyValueEmpty val4) {
        return new MyValue104(val1, val2, val3, val4);
    }

    @Run(test = "test119")
    public void test119_verifier() {
        try {
            test119(new MyValue105(), new MyValue105(), null, new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test120(MyValue105 val1, MyValue105 val2, MyValueEmpty val3, MyValueEmpty val4) {
        return new MyValue104(val1, val2, val3, val4);
    }

    @Run(test = "test120")
    public void test120_verifier() {
        try {
            test120(new MyValue105(), new MyValue105(), new MyValueEmpty(), null);
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test121(MyValue105 val2, MyValueEmpty val3, MyValueEmpty val4) {
        return new MyValue104(null, val2, val3, val4);
    }

    @Run(test = "test121")
    public void test121_verifier() {
        try {
            test121(new MyValue105(), new MyValueEmpty(), new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test122(MyValue105 val1, MyValueEmpty val3, MyValueEmpty val4) {
        return new MyValue104(val1, null, val3, val4);
    }

    @Run(test = "test122")
    public void test122_verifier() {
        try {
            test122(new MyValue105(), new MyValueEmpty(), new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test123(MyValue105 val1, MyValue105 val2, MyValueEmpty val4) {
        return new MyValue104(val1, val2, null, val4);
    }

    @Run(test = "test123")
    public void test123_verifier() {
        try {
            test123(new MyValue105(), new MyValue105(), new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    MyValue104 test124(MyValue105 val1, MyValue105 val2, MyValueEmpty val3) {
        return new MyValue104(val1, val2, val3, null);
    }

    @Run(test = "test124")
    public void test124_verifier() {
        try {
            test124(new MyValue105(), new MyValue105(), new MyValueEmpty());
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    static value class CircularValue7 {
        CircularValue7 v;
        int i;

        public CircularValue7(int i) {
            this.v = new CircularValue7(); // When <init> is incrementally inlined: StoreN into object
            dontInline(); // Not inlined -> safepoint which also saves StoreN.
            this.i = i;
        }

        public CircularValue7(boolean ignored) {
            this.v = new CircularValue7();
            this.i = 23;
        }

        public CircularValue7() {
            this.v = null;
            this.i = 34;
        }

        @DontInline
        static void dontInline() {}
    }

    @Test
    @IR(failOn = ALLOC)
    int testCircularSafepointUse() {
        CircularValue7 v = new CircularValue7(true);  // v is non escaping -> EA can remove allocation
        dontInline(); // Not inlined -> safepoint
        return v.i; // Use v such that it is still required in the safepoint at dontInline()
    }

    @DontInline
    void dontInline() {}

    @Run(test = "testCircularSafepointUse")
    public void testCircularSafepointUse_verifier() {
        Asserts.assertEQ(testCircularSafepointUse(), new CircularValue7(true).i);
    }


    @Test
    @IR(failOn = ALLOC)
    int testCircularSafepointUse2(int i) {
        // With AlwaysIncrementalInline:
        // We allocate here because <init> is not inlined at parsing.
        // At late inline: The store of v.v is done with a StoreN into the allocation to make the effect visible.
        CircularValue7 v = new CircularValue7(i);
        return v.i;
    }

    @Run(test = "testCircularSafepointUse2")
    public void testCircularSafepointUse2_verifier() {
        int rand = rI;
        Asserts.assertEQ(testCircularSafepointUse2(rand), new CircularValue7(rand).i);
    }

}
