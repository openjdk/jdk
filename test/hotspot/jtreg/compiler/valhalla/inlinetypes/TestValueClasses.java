/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;

import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypes.*;

import static compiler.lib.ir_framework.IRNode.ALLOC;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @key randomness
 * @summary Test correct handling of value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestValueClasses 0
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestValueClasses 1
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestValueClasses 2
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestValueClasses 3
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestValueClasses 4
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestValueClasses 5
 */

/*
 * @test
 * @key randomness
 * @summary Test correct handling of value classes.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestValueClasses 6
 */

@ForceCompileClassInitializer
public class TestValueClasses {

    public static void main(String[] args) {
        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;
        // Don't generate bytecodes but call through runtime for reflective calls
        scenarios[0].addFlags("-Dsun.reflect.inflationThreshold=10000");
        scenarios[1].addFlags("-Dsun.reflect.inflationThreshold=10000");
        scenarios[3].addFlags("-XX:-MonomorphicArrayCheck", "-XX:+UseArrayFlattening");
        scenarios[4].addFlags("-XX:-UseTLAB", "-XX:-MonomorphicArrayCheck");

        InlineTypes.getFramework()
                   .addScenarios(scenarios[Integer.parseInt(args[0])])
                   .addHelperClasses(MyValueClass1.class,
                                     MyValueClass2.class,
                                     MyValueClass2Inline.class)
                   .start();
    }

    static {
        // Make sure RuntimeException is loaded to prevent uncommon traps in IR verified tests
        RuntimeException tmp = new RuntimeException("42");
    }

    private static final MyValueClass1 testValue1 = MyValueClass1.createWithFieldsInline(rI, rL);

    MyValueClass1 nullValField = null;
    MyValueClass1 testField1;
    MyValueClass1 testField2;
    MyValueClass1 testField3;
    MyValueClass1 testField4;
    static MyValueClass1 testField5;
    static MyValueClass1 testField6;
    static MyValueClass1 testField7;
    static MyValueClass1 testField8;

    // Test field loads
    @Test
    public long test1(boolean b) {
        MyValueClass1 val1 = b ? testField3 : MyValueClass1.createWithFieldsInline(rI, rL);
        MyValueClass1 val2 = b ? testField7 : MyValueClass1.createWithFieldsInline(rI, rL);
        long res = 0;
        res += testField1.hash();
        res += ((Object)testField2 == null) ? 42 : testField2.hash();
        res += val1.hash();
        res += testField4.hash();

        res += testField5.hash();
        res += ((Object)testField6 == null) ? 42 : testField6.hash();
        res += val2.hash();
        res += testField8.hash();
        return res;
    }

    @Run(test = "test1")
    public void test1_verifier() {
        testField1 = testValue1;
        testField2 = nullValField;
        testField3 = testValue1;
        testField4 = testValue1;

        testField5 = testValue1;
        testField6 = nullValField;
        testField7 = testValue1;
        testField8 = testValue1;
        long res = test1(true);
        Asserts.assertEquals(res, 2*42 + 6*testValue1.hash());

        testField2 = testValue1;
        testField6 = testValue1;
        res = test1(false);
        Asserts.assertEquals(res, 8*testValue1.hash());
    }

    // Test field stores
    @Test
    public MyValueClass1 test2(MyValueClass1 val1) {
        MyValueClass1 ret = MyValueClass1.createWithFieldsInline(rI, rL);
        MyValueClass1 val2 = MyValueClass1.setV4(testValue1, null);
        testField1 = testField4;
        testField2 = val1;
        testField3 = val2;

        testField5 = ret;
        testField6 = val1;
        testField7 = val2;
        testField8 = testField4;
        return ret;
    }

    @Run(test = "test2")
    public void test2_verifier() {
        testField4 = testValue1;
        MyValueClass1 ret = test2(null);
        MyValueClass1 val2 = MyValueClass1.setV4(testValue1, null);
        Asserts.assertEquals(testField1, testValue1);
        Asserts.assertEquals(testField2, null);
        Asserts.assertEquals(testField3, val2);

        Asserts.assertEquals(testField5, ret);
        Asserts.assertEquals(testField6, null);
        Asserts.assertEquals(testField7, val2);
        Asserts.assertEquals(testField8, testField4);

        testField4 = null;
        test2(null);
        Asserts.assertEquals(testField1, testField4);
        Asserts.assertEquals(testField8, testField4);
    }

    // Non-value class Wrapper
    static class Test3Wrapper {
        MyValueClass1 val;

        public Test3Wrapper(MyValueClass1 val) {
            this.val = val;
        }
    }

    // Test scalarization in safepoint debug info and re-allocation on deopt
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public long test3(boolean deopt, boolean b1, boolean b2, Method m) {
        MyValueClass1 ret = MyValueClass1.createWithFieldsInline(rI, rL);
        if (b1) {
            ret = null;
        }
        if (b2) {
            ret = MyValueClass1.setV4(ret, null);
        }
        Test3Wrapper wrapper = new Test3Wrapper(ret);
        if (deopt) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        long res = ((Object)ret != null && (Object)ret.v4 != null) ? ret.hash() : 42;
        res += ((Object)wrapper.val != null && (Object)wrapper.val.v4 != null) ? wrapper.val.hash() : 0;
        return res;
    }

    @Run(test = "test3")
    public void test3_verifier(RunInfo info) {
        Asserts.assertEquals(test3(false, false, false, info.getTest()), 2*testValue1.hash());
        Asserts.assertEquals(test3(false, true, false, info.getTest()), 42L);
        if (!info.isWarmUp()) {
            switch (rI % 4) {
            case 0:
                Asserts.assertEquals(test3(true, false, false, info.getTest()), 2*testValue1.hash());
                break;
            case 1:
                Asserts.assertEquals(test3(true, true, false, info.getTest()), 42L);
                break;
            case 2:
                Asserts.assertEquals(test3(true, false, true, info.getTest()), 42L);
                break;
            case 3:
                try {
                    Asserts.assertEquals(test3(true, true, true, info.getTest()), 42L);
                    throw new RuntimeException("NullPointerException expected");
                } catch (NullPointerException e) {
                    // Expected
                }
                break;
            }
        }
    }

    // Test scalarization in safepoint debug info and re-allocation on deopt
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public boolean test4(boolean deopt, boolean b, Method m) {
        MyValueClass1 val = b ? null : MyValueClass1.createWithFieldsInline(rI, rL);
        Test3Wrapper wrapper = new Test3Wrapper(val);
        if (deopt) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return (Object)wrapper.val == null;
    }

    @Run(test = "test4")
    public void test4_verifier(RunInfo info) {
        Asserts.assertTrue(test4(false, true, info.getTest()));
        Asserts.assertFalse(test4(false, false, info.getTest()));
        if (!info.isWarmUp()) {
            switch (rI % 2) {
                case 0:
                    Asserts.assertTrue(test4(true, true, info.getTest()));
                    break;
                case 1:
                    Asserts.assertFalse(test4(false, false, info.getTest()));
                    break;
            }
        }
    }

    static value class SmallNullable2 {
        float f1;
        double f2;

        @ForceInline
        public SmallNullable2() {
            f1 = (float)rL;
            f2 = (double)rL;
        }
    }

    static value class SmallNullable1 {
        char c;
        byte b;
        short s;
        int i;
        SmallNullable2 vt;

        @ForceInline
        public SmallNullable1(boolean useNull) {
            c = (char)rL;
            b = (byte)rL;
            s = (short)rL;
            i = (int)rL;
            vt = useNull ? null : new SmallNullable2();
        }
    }

    @DontCompile
    public SmallNullable1 test5_interpreted(boolean b1, boolean b2) {
        return b1 ? null : new SmallNullable1(b2);
    }

    @DontInline
    public SmallNullable1 test5_compiled(boolean b1, boolean b2) {
        return b1 ? null : new SmallNullable1(b2);
    }

    SmallNullable1 test5_field1;
    SmallNullable1 test5_field2;

    // Test scalarization in returns
    @Test
    public SmallNullable1 test5(boolean b1, boolean b2) {
        SmallNullable1 ret = test5_interpreted(b1, b2);
        if (b1 != ((Object)ret == null)) {
            throw new RuntimeException("test5 failed");
        }
        test5_field1 = ret;
        ret = test5_compiled(b1, b2);
        if (b1 != ((Object)ret == null)) {
            throw new RuntimeException("test5 failed");
        }
        test5_field2 = ret;
        return ret;
    }

    @Run(test = "test5")
    public void test5_verifier() {
        SmallNullable1 vt = new SmallNullable1(false);
        Asserts.assertEquals(test5(true, false), null);
        Asserts.assertEquals(test5_field1, null);
        Asserts.assertEquals(test5_field2, null);
        Asserts.assertEquals(test5(false, false), vt);
        Asserts.assertEquals(test5_field1, vt);
        Asserts.assertEquals(test5_field2, vt);
        vt = new SmallNullable1(true);
        Asserts.assertEquals(test5(true, true), null);
        Asserts.assertEquals(test5_field1, null);
        Asserts.assertEquals(test5_field2, null);
        Asserts.assertEquals(test5(false, true), vt);
        Asserts.assertEquals(test5_field1, vt);
        Asserts.assertEquals(test5_field2, vt);
    }

    static value class Empty2 {

    }

    static value class Empty1 {
        Empty2 empty2 = new Empty2();
    }

    static value class Container {
        int x = 0;
        Empty1 empty1;
        Empty2 empty2 = new Empty2();

        @ForceInline
        public Container(Empty1 val) {
            empty1 = val;
        }
    }

    @DontInline
    public static Empty1 test6_helper1(Empty1 vt) {
        return vt;
    }

    @DontInline
    public static Empty2 test6_helper2(Empty2 vt) {
        return vt;
    }

    @DontInline
    public static Container test6_helper3(Container vt) {
        return vt;
    }

    // Test scalarization in calls and returns with empty value classes
    @Test
    public Empty1 test6(Empty1 vt) {
        Empty1 empty1 = test6_helper1(vt);
        test6_helper2((empty1 != null) ? empty1.empty2 : null);
        Container c = test6_helper3(new Container(empty1));
        return c.empty1;
    }

    @Run(test = "test6")
    @Warmup(10000) // Warmup to make sure helper methods are compiled as well
    public void test6_verifier() {
        Asserts.assertEQ(test6(new Empty1()), new Empty1());
        Asserts.assertEQ(test6(null), null);
    }

    @DontCompile
    public void test7_helper2(boolean doit) {
        if (doit) {
            // uncommon trap
            try {
                TestFramework.deoptimize(getClass().getDeclaredMethod("test7", boolean.class, boolean.class, boolean.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Test deoptimization at call return with value object returned in registers
    @DontInline
    public SmallNullable1 test7_helper1(boolean deopt, boolean b1, boolean b2) {
        test7_helper2(deopt);
        return b1 ? null : new SmallNullable1(b2);
    }

    @Test
    public SmallNullable1 test7(boolean flag, boolean b1, boolean b2) {
        return test7_helper1(flag, b1, b2);
    }

    @Run(test = "test7")
    @Warmup(10000)
    public void test7_verifier(RunInfo info) {
        boolean b1 = ((rI % 3) == 0);
        boolean b2 = ((rI % 3) == 1);
        SmallNullable1 result = test7(!info.isWarmUp(), b1, b2);
        SmallNullable1 vt = new SmallNullable1(b2);
        Asserts.assertEQ(result, b1 ? null : vt);
    }

    // Test calling a method returning a value class as fields via reflection
    @Test
    public SmallNullable1 test8(boolean b1, boolean b2) {
        return b1 ? null : new SmallNullable1(b2);
    }

    @Run(test = "test8")
    public void test8_verifier() throws Exception {
        Method m = getClass().getDeclaredMethod("test8", boolean.class, boolean.class);
        Asserts.assertEQ(m.invoke(this, false, true), new SmallNullable1(true));
        Asserts.assertEQ(m.invoke(this, false, false), new SmallNullable1(false));
        Asserts.assertEQ(m.invoke(this, true, false), null);
    }

    // Test value classes as arg/return
    @Test
    public SmallNullable1 test9(MyValueClass1 vt1, MyValueClass1 vt2, boolean b1, boolean b2) {
        Asserts.assertEQ(vt1, testValue1);
        if (b1) {
            Asserts.assertEQ(vt2, null);
        } else {
            Asserts.assertEQ(vt2, testValue1);
        }
        return b1 ? null : new SmallNullable1(b2);
    }

    @Run(test = "test9")
    public void test9_verifier() {
        Asserts.assertEQ(test9(testValue1, testValue1, false, true), new SmallNullable1(true));
        Asserts.assertEQ(test9(testValue1, testValue1, false, false), new SmallNullable1(false));
        Asserts.assertEQ(test9(testValue1, null, true, false), null);
    }

    // Class.cast
    @Test
    public Object test10(Class c, MyValueClass1 vt) {
        return c.cast(vt);
    }

    @Run(test = "test10")
    public void test10_verifier() {
        Asserts.assertEQ(test10(MyValueClass1.class, testValue1), testValue1);
        Asserts.assertEQ(test10(MyValueClass1.class, null), null);
        Asserts.assertEQ(test10(Integer.class, null), null);
        try {
            test10(MyValueClass2.class, testValue1);
            throw new RuntimeException("ClassCastException expected");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    // Test acmp
    @Test
    public boolean test12(MyValueClass1 vt1, MyValueClass1 vt2) {
        return vt1 == vt2;
    }

    @Run(test = "test12")
    public void test12_verifier() {
        Asserts.assertTrue(test12(testValue1, testValue1));
        Asserts.assertTrue(test12(null, null));
        Asserts.assertFalse(test12(testValue1, null));
        Asserts.assertFalse(test12(null, testValue1));
        Asserts.assertFalse(test12(testValue1, MyValueClass1.createDefaultInline()));
    }

    // Same as test13 but with Object argument
    @Test
    public boolean test13(Object obj, MyValueClass1 vt2) {
        return obj == vt2;
    }

    @Run(test = "test13")
    public void test13_verifier() {
        Asserts.assertTrue(test13(testValue1, testValue1));
        Asserts.assertTrue(test13(null, null));
        Asserts.assertFalse(test13(testValue1, null));
        Asserts.assertFalse(test13(null, testValue1));
        Asserts.assertFalse(test13(testValue1, MyValueClass1.createDefaultInline()));
    }

    static MyValueClass1 test14_field1;
    static MyValueClass1 test14_field2;

    // Test buffer checks emitted by acmp followed by buffering
    @Test
    public boolean test14(MyValueClass1 vt1, MyValueClass1 vt2) {
        // Trigger buffer checks
        if (vt1 != vt2) {
            throw new RuntimeException("Should be equal");
        }
        if (vt2 != vt1) {
            throw new RuntimeException("Should be equal");
        }
        // Trigger buffering
        test14_field1 = vt1;
        test14_field2 = vt2;
        return vt1 == null;
    }

    @Run(test = "test14")
    public void test14_verifier() {
        Asserts.assertFalse(test14(testValue1, testValue1));
        Asserts.assertTrue(test14(null, null));
    }

    @DontInline
    public MyValueClass1 test15_helper1(MyValueClass1 vt) {
        return vt;
    }

    @ForceInline
    public MyValueClass1 test15_helper2(MyValueClass1 vt) {
        return test15_helper1(vt);
    }

    @ForceInline
    public MyValueClass1 test15_helper3(Object vt) {
        return test15_helper2((MyValueClass1)vt);
    }

    // Test that calling convention optimization prevents buffering of arguments
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC, " <= 7"}) // 6 MyValueClass2/MyValueClass2Inline allocations + 1 Integer allocation (if not the all-zero value)
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        counts = {ALLOC, " <= 8"}) // 1 MyValueClass1 allocation + 6 MyValueClass2/MyValueClass2Inline allocations + 1 Integer allocation (if not the all-zero value)
    public MyValueClass1 test15(MyValueClass1 vt) {
        MyValueClass1 res = test15_helper1(vt);
        vt = MyValueClass1.createWithFieldsInline(rI, rL);
        test15_helper1(vt);
        test15_helper2(vt);
        test15_helper3(vt);
        vt.dontInline(vt);
        return res;
    }

    @Run(test = "test15")
    public void test15_verifier() {
        Asserts.assertEQ(test15(testValue1), testValue1);
        Asserts.assertEQ(test15(null), null);
    }

    @DontInline
    public MyValueClass2 test16_helper1(boolean b) {
        return b ? null : MyValueClass2.createWithFieldsInline(rI, rD);
    }

    @ForceInline
    public MyValueClass2 test16_helper2() {
        return null;
    }

    @ForceInline
    public MyValueClass2 test16_helper3(boolean b) {
        return b ? null : MyValueClass2.createWithFieldsInline(rI, rD);
    }

    // Test that calling convention optimization prevents buffering of return values
    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        counts = {ALLOC, " <= 1"}) // 1 MyValueClass2Inline allocation (if not the all-zero value)
    @IR(applyIf = {"InlineTypeReturnedAsFields", "false"},
        counts = {ALLOC, " <= 2"}) // 1 MyValueClass2 + 1 MyValueClass2Inline allocation  (if not the all-zero value)
    public MyValueClass2 test16(int c, boolean b) {
        MyValueClass2 res = null;
        if (c == 1) {
            res = test16_helper1(b);
        } else if (c == 2) {
            res = test16_helper2();
        } else if (c == 3) {
            res = test16_helper3(b);
        }
        return res;
    }

    @Run(test = "test16")
    public void test16_verifier() {
        Asserts.assertEQ(null, test16(0, false));
        Asserts.assertEQ(MyValueClass2.createWithFieldsInline(rI, rD), test16(1, false));
        Asserts.assertEQ(null, test16(1, true));
        Asserts.assertEQ(null, test16(2, false));
        Asserts.assertEQ(MyValueClass2.createWithFieldsInline(rI, rD), test16(3, false));
        Asserts.assertEQ(null, test16(3, true));
    }

    @LooselyConsistentValue
    static value class MyPrimitive17 {
        MyValueClass1 nonFlattened;

        public MyPrimitive17(MyValueClass1 val) {
            this.nonFlattened = val;
        }
    }

    static value class MyValue17 {
        @NullRestricted
        MyPrimitive17 flattened;

        public MyValue17(boolean b) {
            this.flattened = new MyPrimitive17(b ? null : testValue1);
        }
    }

    @DontCompile
    public MyValue17 test17_interpreted(boolean b1, boolean b2) {
        return b1 ? null : new MyValue17(b2);
    }

    @DontInline
    public MyValue17 test17_compiled(boolean b1, boolean b2) {
        return b1 ? null : new MyValue17(b2);
    }

    MyValue17 test17_field1;
    MyValue17 test17_field2;

    // Test handling of null when mixing nullable and null-restricted fields
    @Test
    public MyValue17 test17(boolean b1, boolean b2) {
        MyValue17 ret = test17_interpreted(b1, b2);
        if (b1 != ((Object)ret == null)) {
            throw new RuntimeException("test17 failed");
        }
        test17_field1 = ret;
        ret = test17_compiled(b1, b2);
        if (b1 != ((Object)ret == null)) {
            throw new RuntimeException("test17 failed");
        }
        test17_field2 = ret;
        return ret;
    }

    @Run(test = "test17")
    public void test17_verifier() {
        MyValue17 vt = new MyValue17(false);
        Asserts.assertEquals(test17(true, false), null);
        Asserts.assertEquals(test17_field1, null);
        Asserts.assertEquals(test17_field2, null);
        Asserts.assertEquals(test17(false, false), vt);
        Asserts.assertEquals(test17_field1, vt);
        Asserts.assertEquals(test17_field2, vt);
        vt = new MyValue17(true);
        Asserts.assertEquals(test17(true, true), null);
        Asserts.assertEquals(test17_field1, null);
        Asserts.assertEquals(test17_field2, null);
        Asserts.assertEquals(test17(false, true), vt);
        Asserts.assertEquals(test17_field1, vt);
        Asserts.assertEquals(test17_field2, vt);
    }

    // Uses all registers available for returning values on x86_64
    static value class UseAllRegs {
        long l1;
        long l2;
        long l3;
        long l4;
        long l5;
        long l6;
        double d1;
        double d2;
        double d3;
        double d4;
        double d5;
        double d6;
        double d7;
        double d8;

        @ForceInline
        public UseAllRegs(long l1, long l2, long l3, long l4, long l5, long l6,
                          double d1, double d2, double d3, double d4, double d5, double d6, double d7, double d8) {
            this.l1 = l1;
            this.l2 = l2;
            this.l3 = l3;
            this.l4 = l4;
            this.l5 = l5;
            this.l6 = l6;
            this.d1 = d1;
            this.d2 = d2;
            this.d3 = d3;
            this.d4 = d4;
            this.d5 = d5;
            this.d6 = d6;
            this.d7 = d7;
            this.d8 = d8;
        }
    }

    @DontInline
    public UseAllRegs test18_helper1(UseAllRegs val, long a, long b, long c, long d, long e, long f, long g, long h, long i, long j) {
        Asserts.assertEquals(a & b & c & d & e & f & g & h & i & j, 0L);
        return val;
    }

    @DontCompile
    public UseAllRegs test18_helper2(UseAllRegs val, long a, long b, long c, long d, long e, long f, long g, long h, long i, long j) {
        Asserts.assertEquals(a & b & c & d & e & f & g & h & i & j, 0L);
        return val;
    }

    static boolean test18_b;

    // Methods with no arguments (no stack slots reserved for incoming args)
    @DontInline
    public static UseAllRegs test18_helper3() {
        return test18_b ? null : new UseAllRegs(rL + 1, rL + 2, rL + 3, rL + 4, rL + 5, rL + 6, rL + 7, rL + 8, rL + 9, rL + 10, rL + 11, rL + 12, rL + 13, rL + 14);
    }

    @DontCompile
    public static UseAllRegs test18_helper4() {
        return test18_b ? null : test18_helper3();
    }

    // Test proper register allocation of isInit projection of a call in C2
    @Test
    public UseAllRegs test18(boolean b, long val1, long l1, long val2, long l2, long val3, long l3, long val4, long l4, long val5, long l5, long val6, long l6,
                             long val7, double d1, long val8, double d2, long val9, double d3, long val10, double d4, long val11, double d5, long val12, double d6, long val13, double d7, long val14, double d8, long val15) {
        Asserts.assertEquals(val1, rL);
        Asserts.assertEquals(val2, rL);
        Asserts.assertEquals(val3, rL);
        Asserts.assertEquals(val4, rL);
        Asserts.assertEquals(val5, rL);
        Asserts.assertEquals(val6, rL);
        Asserts.assertEquals(val7, rL);
        Asserts.assertEquals(val8, rL);
        Asserts.assertEquals(val9, rL);
        Asserts.assertEquals(val10, rL);
        Asserts.assertEquals(val11, rL);
        Asserts.assertEquals(val12, rL);
        Asserts.assertEquals(val13, rL);
        Asserts.assertEquals(val14, rL);
        Asserts.assertEquals(val15, rL);
        UseAllRegs val = b ? null : new UseAllRegs(l1, l2, l3, l4, l5, l6, d1, d2, d3, d4, d5, d6, d7, d8);
        val = test18_helper1(val, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        val = test18_helper2(val, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        Asserts.assertEquals(val1, rL);
        Asserts.assertEquals(val2, rL);
        Asserts.assertEquals(val3, rL);
        Asserts.assertEquals(val4, rL);
        Asserts.assertEquals(val5, rL);
        Asserts.assertEquals(val6, rL);
        Asserts.assertEquals(val7, rL);
        Asserts.assertEquals(val8, rL);
        Asserts.assertEquals(val9, rL);
        Asserts.assertEquals(val10, rL);
        Asserts.assertEquals(val11, rL);
        Asserts.assertEquals(val12, rL);
        Asserts.assertEquals(val13, rL);
        Asserts.assertEquals(val14, rL);
        Asserts.assertEquals(val15, rL);
        Asserts.assertEquals(test18_helper3(), val);
        Asserts.assertEquals(test18_helper4(), val);
        return val;
    }

    @Run(test = "test18")
    public void test18_verifier() {
        UseAllRegs val = new UseAllRegs(rL + 1, rL + 2, rL + 3, rL + 4, rL + 5, rL + 6, rL + 7, rL + 8, rL + 9, rL + 10, rL + 11, rL + 12, rL + 13, rL + 14);
        test18_b = false;
        Asserts.assertEquals(test18(false, rL, rL + 1, rL, rL + 2, rL, rL + 3, rL, rL + 4, rL, rL + 5, rL, rL + 6,
                                    rL, rL + 7, rL, rL + 8, rL, rL + 9, rL, rL + 10, rL, rL + 11, rL, rL + 12, rL, rL + 13, rL, rL + 14, rL), val);
        test18_b = true;
        Asserts.assertEquals(test18(true, rL, rL + 1, rL, rL + 2, rL, rL + 3, rL, rL + 4, rL, rL + 5, rL, rL + 6,
                                    rL, rL + 7, rL, rL + 8, rL, rL + 9, rL, rL + 10, rL, rL + 11, rL, rL + 12, rL, rL + 13, rL, rL + 14, rL), null);
    }

    @DontInline
    static public UseAllRegs test19_helper() {
        return new UseAllRegs(rL + 1, rL + 2, rL + 3, rL + 4, rL + 5, rL + 6, rL + 7, rL + 8, rL + 9, rL + 10, rL + 11, rL + 12, rL + 13, rL + 14);
    }

    // Test proper register allocation of isInit projection of a call in C2
    @Test
    static public void test19(long a, long b, long c, long d, long e, long f) {
        if (test19_helper() == null) {
            throw new RuntimeException("test19 failed: Unexpected null");
        }
        if ((a & b & c & d & e & f) != 0) {
            throw new RuntimeException("test19 failed: Unexpected argument values");
        }
    }

    @Run(test = "test19")
    public void test19_verifier() {
        test19(0, 0, 0, 0, 0, 0);
    }

    // Uses almost all registers available for returning values on x86_64
    static value class UseAlmostAllRegs {
        long l1;
        long l2;
        long l3;
        long l4;
        long l5;
        double d1;
        double d2;
        double d3;
        double d4;
        double d5;
        double d6;
        double d7;

        @ForceInline
        public UseAlmostAllRegs(long l1, long l2, long l3, long l4, long l5,
                                double d1, double d2, double d3, double d4, double d5, double d6, double d7) {
            this.l1 = l1;
            this.l2 = l2;
            this.l3 = l3;
            this.l4 = l4;
            this.l5 = l5;
            this.d1 = d1;
            this.d2 = d2;
            this.d3 = d3;
            this.d4 = d4;
            this.d5 = d5;
            this.d6 = d6;
            this.d7 = d7;
        }
    }

    @DontInline
    public UseAlmostAllRegs test20_helper1(UseAlmostAllRegs val, long a, long b, long c, long d, long e, long f, long g, long h, long i, long j) {
        Asserts.assertEquals(a & b & c & d & e & f & g & h & i & j, 0L);
        return val;
    }

    @DontCompile
    public UseAlmostAllRegs test20_helper2(UseAlmostAllRegs val, long a, long b, long c, long d, long e, long f, long g, long h, long i, long j) {
        Asserts.assertEquals(a & b & c & d & e & f & g & h & i & j, 0L);
        return val;
    }

    static boolean test20_b;

    // Methods with no arguments (no stack slots reserved for incoming args)
    @DontInline
    public static UseAlmostAllRegs test20_helper3() {
        return test20_b ? null : new UseAlmostAllRegs(rL + 1, rL + 2, rL + 3, rL + 4, rL + 5, rL + 6, rL + 7, rL + 8, rL + 9, rL + 10, rL + 11, rL + 12);
    }

    @DontCompile
    public static UseAlmostAllRegs test20_helper4() {
        return test20_b ? null : test20_helper3();
    }

    // Test proper register allocation of isInit projection of a call in C2
    @Test
    public UseAlmostAllRegs test20(boolean b, long val1, long l1, long val2, long l2, long val3, long l3, long val4, long l4, long val5, long l5, long val6,
                                   long val7, double d1, long val8, double d2, long val9, double d3, long val10, double d4, long val11, double d5, long val12, double d6, long val13, double d7, long val14, long val15) {
        Asserts.assertEquals(val1, rL);
        Asserts.assertEquals(val2, rL);
        Asserts.assertEquals(val3, rL);
        Asserts.assertEquals(val4, rL);
        Asserts.assertEquals(val5, rL);
        Asserts.assertEquals(val6, rL);
        Asserts.assertEquals(val7, rL);
        Asserts.assertEquals(val8, rL);
        Asserts.assertEquals(val9, rL);
        Asserts.assertEquals(val10, rL);
        Asserts.assertEquals(val11, rL);
        Asserts.assertEquals(val12, rL);
        Asserts.assertEquals(val13, rL);
        Asserts.assertEquals(val14, rL);
        Asserts.assertEquals(val15, rL);
        UseAlmostAllRegs val = b ? null : new UseAlmostAllRegs(l1, l2, l3, l4, l5, d1, d2, d3, d4, d5, d6, d7);
        val = test20_helper1(val, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        val = test20_helper2(val, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        Asserts.assertEquals(val1, rL);
        Asserts.assertEquals(val2, rL);
        Asserts.assertEquals(val3, rL);
        Asserts.assertEquals(val4, rL);
        Asserts.assertEquals(val5, rL);
        Asserts.assertEquals(val6, rL);
        Asserts.assertEquals(val7, rL);
        Asserts.assertEquals(val8, rL);
        Asserts.assertEquals(val9, rL);
        Asserts.assertEquals(val10, rL);
        Asserts.assertEquals(val11, rL);
        Asserts.assertEquals(val12, rL);
        Asserts.assertEquals(val13, rL);
        Asserts.assertEquals(val14, rL);
        Asserts.assertEquals(val15, rL);
        Asserts.assertEquals(test20_helper3(), val);
        Asserts.assertEquals(test20_helper4(), val);
        return val;
    }

    @Run(test = "test20")
    public void test20_verifier() {
        UseAlmostAllRegs val = new UseAlmostAllRegs(rL + 1, rL + 2, rL + 3, rL + 4, rL + 5, rL + 6, rL + 7, rL + 8, rL + 9, rL + 10, rL + 11, rL + 12);
        test20_b = false;
        Asserts.assertEquals(test20(false, rL, rL + 1, rL, rL + 2, rL, rL + 3, rL, rL + 4, rL, rL + 5, rL,
                                    rL, rL + 6, rL, rL + 7, rL, rL + 8, rL, rL + 9, rL, rL + 10, rL, rL + 11, rL, rL + 12, rL, rL), val);
        test20_b = true;
        Asserts.assertEquals(test20(true, rL, rL + 1, rL, rL + 2, rL, rL + 3, rL, rL + 4, rL, rL + 5, rL,
                                    rL, rL + 6, rL, rL + 7, rL, rL + 8, rL, rL + 9, rL, rL + 10, rL, rL + 11, rL, rL + 12, rL, rL), null);
    }

    @DontInline
    static public UseAlmostAllRegs test21_helper() {
        return new UseAlmostAllRegs(rL + 1, rL + 2, rL + 3, rL + 4, rL + 5, rL + 6, rL + 7, rL + 8, rL + 9, rL + 10, rL + 11, rL + 12);
    }

    // Test proper register allocation of isInit projection of a call in C2
    @Test
    static public void test21(long a, long b, long c, long d, long e, long f) {
        if (test21_helper() == null) {
            throw new RuntimeException("test21 failed: Unexpected null");
        }
        if ((a & b & c & d & e & f) != 0) {
            throw new RuntimeException("test21 failed: Unexpected argument values");
        }
    }

    @Run(test = "test21")
    public void test21_verifier() {
        test21(0, 0, 0, 0, 0, 0);
    }

    static value class ManyOopsValue {
        Integer i1 = 1;
        Integer i2 = 2;
        Integer i3 = 3;
        Integer i4 = 4;
        Integer i5 = 5;
        Integer i6 = 6;
        Integer i7 = 7;
        Integer i8 = 8;
        Integer i9 = 9;
        Integer i10 = 10;
        Integer i11 = 11;
        Integer i12 = 12;
        Integer i13 = 13;
        Integer i14 = 14;
        Integer i15 = 15;

        @DontInline
        public int sum() {
            return i1 + i2 + i3 + i4 + i5 + i6 + i7 + i8 + i9 + i10 + i11 + i12 + i13 + i14 + i15;
        }
    }

    // Verify that C2 scratch buffer size is large enough to hold many GC barriers used by the entry points
    @Test
    static public int test22(ManyOopsValue val) {
        return val.sum();
    }

    @Run(test = "test22")
    @Warmup(10_000)
    public void test22_verifier() {
        Asserts.assertEquals(test22(new ManyOopsValue()), 120);
    }
}
