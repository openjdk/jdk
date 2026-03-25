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
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import static compiler.lib.ir_framework.IRNode.*;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_UNKNOWN_INLINE;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_UNKNOWN_INLINE;
import static compiler.valhalla.inlinetypes.InlineTypes.*;

/*
 * @test
 * @key randomness
 * @summary Test value class specific type profiling.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/timeout=300 compiler.valhalla.inlinetypes.TestLWorldProfiling
 */

@ForceCompileClassInitializer
public class TestLWorldProfiling {

    public static void main(String[] args) {
        final Scenario[] scenarios = {
                new Scenario(0,
                        "-XX:+UseArrayFlattening",
                        "-XX:-UseArrayLoadStoreProfile",
                        "-XX:-UseACmpProfile",
                        "-XX:TypeProfileLevel=0",
                        "-XX:-MonomorphicArrayCheck"),
                new Scenario(1,
                        "-XX:+UseArrayFlattening",
                        "-XX:+UseArrayLoadStoreProfile",
                        "-XX:+UseACmpProfile",
                        "-XX:TypeProfileLevel=0",
                        "-XX:+UseFieldFlattening"),
                new Scenario(2,
                        "-XX:+UseArrayFlattening",
                        "-XX:-UseArrayLoadStoreProfile",
                        "-XX:-UseACmpProfile",
                        "-XX:TypeProfileLevel=222",
                        "-XX:-MonomorphicArrayCheck"),
                new Scenario(3,
                        "-XX:+UseArrayFlattening",
                        "-XX:-UseArrayLoadStoreProfile",
                        "-XX:-UseACmpProfile",
                        "-XX:TypeProfileLevel=0",
                        "-XX:-MonomorphicArrayCheck",
                        "-XX:TieredStopAtLevel=4",
                        "-XX:-TieredCompilation"),
                new Scenario(4,
                        "-XX:+UseArrayFlattening",
                        "-XX:+UseArrayLoadStoreProfile",
                        "-XX:+UseACmpProfile",
                        "-XX:TypeProfileLevel=0",
                        "-XX:TieredStopAtLevel=4",
                        "-XX:-TieredCompilation",
                        "-XX:+UseFieldFlattening"),
                new Scenario(5,
                        "-XX:+UseArrayFlattening",
                        "-XX:-UseArrayLoadStoreProfile",
                        "-XX:-UseACmpProfile",
                        "-XX:TypeProfileLevel=222",
                        "-XX:-MonomorphicArrayCheck",
                        "-XX:TieredStopAtLevel=4",
                        "-XX:-TieredCompilation")
        };

        InlineTypes.getFramework()
                   .addScenarios(scenarios)
                   .addFlags("-XX:+IgnoreUnrecognizedVMOptions", "--enable-preview",
                             "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                             "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED")
                   .addHelperClasses(MyValue1.class,
                                     MyValue2.class)
                   .start();
    }

    @NullRestricted
    private static final MyValue1 testValue1 = MyValue1.createWithFieldsInline(rI, rL);
    @NullRestricted
    private static final MyValue2 testValue2 = MyValue2.createWithFieldsInline(rI, rD);
    private static final MyValue1[] testValue1Array = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
    static {
        testValue1Array[0] = testValue1;
    }
    private static final MyValue2[] testValue2Array = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, MyValue2.DEFAULT);
    static {
        testValue2Array[0] = testValue2;
    }

    // Some non-value classes
    static class MyInteger extends Number {
        int val;

        public MyInteger(int val) {
            this.val = val;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MyInteger)) {
                return false;
            }
            return this.val == ((MyInteger)o).val;
        }

        public double doubleValue() { return val; }
        public float floatValue() { return val; }
        public int intValue() { return val; }
        public long longValue() { return val; }
    }

    static class MyLong extends Number {
        long val;

        public MyLong(long val) {
            this.val = val;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MyLong)) {
                return false;
            }
            return this.val == ((MyLong)o).val;
        }

        public double doubleValue() { return val; }
        public float floatValue() { return val; }
        public int intValue() { return (int)val; }
        public long longValue() { return val; }
    }

    private static final MyInteger[] testMyIntegerArray = new MyInteger[] { new MyInteger(42) };
    private static final MyLong[] testMyLongArray = new MyLong[] { new MyLong(42L) };
    private static final MyValue1[] testValue1NotFlatArray = new MyValue1[] { testValue1 };
    private static final MyValue1[][] testValue1ArrayArray = new MyValue1[][] { testValue1Array };

    // Wrap these variables into helper class because
    // WhiteBox API needs to be initialized by TestFramework first.
    static class WBFlags {
        static final boolean UseACmpProfile = (Boolean) WhiteBox.getWhiteBox().getVMFlag("UseACmpProfile");
        static final boolean TieredCompilation = (Boolean) WhiteBox.getWhiteBox().getVMFlag("TieredCompilation");
        static final boolean ProfileInterpreter = (Boolean) WhiteBox.getWhiteBox().getVMFlag("ProfileInterpreter");
        static final boolean UseArrayLoadStoreProfile = (Boolean) WhiteBox.getWhiteBox().getVMFlag("UseArrayLoadStoreProfile");
        static final long TypeProfileLevel = (Long) WhiteBox.getWhiteBox().getVMFlag("TypeProfileLevel");
    }

    static abstract value class ValueAbstract {

    }

    static class NonValueClass1 extends ValueAbstract {
        int x;

        public NonValueClass1(int x) {
            this.x = x;
        }
    }

    static class NonValueClass2 extends ValueAbstract {
        int x;

        public NonValueClass2(int x) {
            this.x = x;
        }
    }

    static final NonValueClass1 obj = new NonValueClass1(rI);
    static final NonValueClass2 otherObj = new NonValueClass2(rI);

    // aaload

    @Test
    @IR(applyIfOr = {"UseArrayLoadStoreProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {LOAD_UNKNOWN_INLINE})
    @IR(applyIfAnd={"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    public Object test1(Object[] array) {
        return array[0];
    }

    @Run(test = "test1")
    @Warmup(10000)
    public void test1_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            Object o = test1(testValue1Array);
            Asserts.assertEQ(testValue1, o);
        } else {
            Object o = test1(testValue2Array);
            Asserts.assertEQ(testValue2, o);
        }
    }

    @Test
    @IR(applyIfOr = {"UseArrayLoadStoreProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {LOAD_UNKNOWN_INLINE})
    @IR(applyIfAnd = {"UseArrayLoadStoreProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    public Object test2(Object[] array) {
        return array[0];
    }

    @Run(test = "test2")
    @Warmup(10000)
    public void test2_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            Object o = test2(testMyIntegerArray);
            Asserts.assertEQ(o, new MyInteger(42));
        } else {
            Object o = test2(testMyLongArray);
            Asserts.assertEQ(o, new MyLong(42L));
        }
    }

    @Test
    @IR(counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    public Object test3(Object[] array) {
        return array[0];
    }

    @Run(test = "test3")
    @Warmup(10000)
    public void test3_verifier() {
        Object o = test3(testValue1Array);
        Asserts.assertEQ(testValue1, o);
        o = test3(testValue2Array);
        Asserts.assertEQ(testValue2, o);
    }

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        failOn = {LOAD_UNKNOWN_INLINE})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    public Object test4(Object[] array) {
        return array[0];
    }

    @Run(test = "test4")
    @Warmup(10000)
    public void test4_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            Object o = test4(testMyIntegerArray);
            Asserts.assertEQ(o, new MyInteger(42));
            o = test4(testMyLongArray);
            Asserts.assertEQ(o, new MyLong(42L));
        } else {
            Object o = test4(testValue2Array);
            Asserts.assertEQ(testValue2, o);
        }
    }

    @Test
    @IR(counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    public Object test5(Object[] array) {
        return array[0];
    }

    @Run(test = "test5")
    @Warmup(10000)
    public void test5_verifier() {
        Object o = test5(testValue1Array);
        Asserts.assertEQ(testValue1, o);
        o = test5(testValue1NotFlatArray);
        Asserts.assertEQ(testValue1, o);
    }

    // Check that profile data that's useless at the aaload is
    // leveraged at a later point
    @DontInline
    public void test6_no_inline() {
    }

    @ForceInline
    public void test6_helper(ValueAbstract[] arg) {
        if (arg instanceof NonValueClass1[]) {
            test6_no_inline();
        }
    }

    @Test
    @IR(applyIfOr = {"UseArrayLoadStoreProfile", "true", "TypeProfileLevel", "= 222"},
        counts = {STATIC_CALL, "= 4", CLASS_CHECK_TRAP, "= 1", NULL_CHECK_TRAP, "= 1", RANGE_CHECK_TRAP, "= 1"})
    @IR(applyIfAnd = {"UseArrayLoadStoreProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL, "= 4", RANGE_CHECK_TRAP, "= 1", NULL_CHECK_TRAP, "= 1"})
    public Object test6(ValueAbstract[] array) {
        ValueAbstract v = array[0];
        test6_helper(array);
        return v;
    }

    @Run(test = "test6")
    @Warmup(10000)
    public void test6_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            // pollute profile
            test6_helper(new NonValueClass1[1]);
            test6_helper(new NonValueClass2[1]);
        }
        test6(new NonValueClass1[1]);
    }

    @DontInline
    public void test7_no_inline() {
    }

    @ForceInline
    public void test7_helper(ValueAbstract arg) {
        if (arg instanceof NonValueClass1) {
            test7_no_inline();
        }
    }

    @Test
    @IR(applyIfOr = {"UseArrayLoadStoreProfile", "true", "TypeProfileLevel", "= 222"},
        counts = {STATIC_CALL, "= 4", CLASS_CHECK_TRAP, "= 1", NULL_CHECK_TRAP, "= 1", RANGE_CHECK_TRAP, "= 1"})
    @IR(applyIfAnd = {"UseArrayLoadStoreProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL, "= 4", RANGE_CHECK_TRAP, "= 1", NULL_CHECK_TRAP, "= 1"})
    public Object test7(ValueAbstract[] array) {
        ValueAbstract v = array[0];
        test7_helper(v);
        return v;
    }

    @Run(test = "test7")
    @Warmup(10000)
    public void test7_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            // pollute profile
            test7_helper(new NonValueClass1(rI));
            test7_helper(new NonValueClass2(rI));
        }
        test7(new NonValueClass1[1]);
    }

    @DontInline
    public void test8_no_inline() {
    }

    public void test8_helper(Object arg) {
        if (arg instanceof Long) {
            test8_no_inline();
        }
    }

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        counts = {STATIC_CALL, "= 5", CLASS_CHECK_TRAP, "= 1", NULL_CHECK_TRAP, "= 2",
                  RANGE_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {STATIC_CALL, "= 5", RANGE_CHECK_TRAP, "= 1", NULL_CHECK_TRAP, "= 2"})
    public Object test8(Object[] array) {
        Object v = array[0];
        test8_helper(v);
        return v;
    }

    @Run(test = "test8")
    @Warmup(10000)
    public void test8_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            // pollute profile
            test8_helper(42L);
            test8_helper(42.0D);
        }
        test8(testValue1Array);
        test8(testValue1NotFlatArray);
    }

    // aastore

    @Test
    @IR(applyIfOr = {"UseArrayLoadStoreProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIfAnd = {"UseArrayLoadStoreProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STORE_UNKNOWN_INLINE, "= 1"})
    public void test9(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test9")
    @Warmup(10000)
    public void test9_verifier() {
        test9(testValue1Array, testValue1);
        Asserts.assertEQ(testValue1, testValue1Array[0]);
    }

    @Test
    @IR(applyIfOr = {"UseArrayLoadStoreProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIfAnd = {"UseArrayLoadStoreProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STORE_UNKNOWN_INLINE, "= 1"})
    public void test10(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test10")
    @Warmup(10000)
    public void test10_verifier() {
        test10(testMyIntegerArray, new MyInteger(42));
    }

    @Test
    @IR(counts = {STORE_UNKNOWN_INLINE, "= 1"})
    public void test11(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test11")
    @Warmup(10000)
    public void test11_verifier() {
        test11(testValue1Array, testValue1);
        test11(testValue2Array, testValue2);
    }

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {STORE_UNKNOWN_INLINE, "= 1"})
    public void test12(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test12")
    @Warmup(10000)
    public void test12_verifier() {
        test12(testMyIntegerArray, new MyInteger(42));
        test12(testMyLongArray, new MyLong(42L));
    }

    @Test
    @IR(counts = {STORE_UNKNOWN_INLINE, "= 1"})
    public void test13(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test13")
    @Warmup(10000)
    public void test13_verifier() {
        test13(testValue1Array, testValue1);
        test13(testValue1NotFlatArray, testValue1);
    }

    // MonomorphicArrayCheck
    @Test
    public void test14(Number[] array, Number v) {
        array[0] = v;
    }

    @Run(test = "test14")
    @Warmup(10000)
    public void test14_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            test14(testMyIntegerArray, new MyInteger(42));
        } else {
            Method m = info.getTest();
            boolean deopt = false;
            for (int i = 0; i < 100; i++) {
                test14(testMyIntegerArray, new MyInteger(42));
                if (!info.isCompilationSkipped() && !TestFramework.isCompiled(m)) {
                    deopt = true;
                }
            }
            if (deopt && TestFramework.isStableDeopt(m, CompLevel.C2) && !WBFlags.TieredCompilation && WBFlags.ProfileInterpreter &&
                (WBFlags.UseArrayLoadStoreProfile || WBFlags.TypeProfileLevel == 222)) {
                throw new RuntimeException("Monomorphic array check should rely on profiling and be accurate");
            }
        }
    }

    // null free array profiling

    @LooselyConsistentValue
    static value class NotFlattenable {
        private Object o1 = null;
        private Object o2 = null;
        private Object o3 = null;
        private Object o4 = null;
        private Object o5 = null;
        private Object o6 = null;
    }

    @NullRestricted
    private static final NotFlattenable notFlattenable = new NotFlattenable();
    private static final NotFlattenable[] testNotFlattenableArray = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 1, new NotFlattenable());

    @Test
    @IR(applyIfOr = {"UseArrayLoadStoreProfile", "true", "TypeProfileLevel", "= 222"},
        counts = {NULL_CHECK_TRAP, "= 2"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIfAnd = {"UseArrayLoadStoreProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {NULL_CHECK_TRAP, "= 2", STORE_UNKNOWN_INLINE, "= 1"})
    public void test15(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test15")
    @Warmup(10000)
    public void test15_verifier() {
        test15(testNotFlattenableArray, notFlattenable);
        try {
            test15(testNotFlattenableArray, null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        counts = {NULL_CHECK_TRAP, "= 2"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {NULL_CHECK_TRAP, "= 2", STORE_UNKNOWN_INLINE, "= 1"})
    public void test16(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test16")
    @Warmup(10000)
    public void test16_verifier() {
        test16(testNotFlattenableArray, notFlattenable);
        try {
            test16(testNotFlattenableArray, null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) {
            // Expected
        }
        test16(testMyIntegerArray, new MyInteger(42));
    }

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        counts = {NULL_CHECK_TRAP, "= 1"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {NULL_CHECK_TRAP, "= 2", STORE_UNKNOWN_INLINE, "= 1"})
    public void test17(Object[] array, Object v) {
        array[0] = v;
    }

    @Run(test = "test17")
    @Warmup(10000)
    public void test17_verifier() {
        test17(testMyIntegerArray, new MyInteger(42));
        test17(testMyIntegerArray, null);
        testMyIntegerArray[0] = new MyInteger(42);
        test17(testMyLongArray, new MyLong(42L));
    }

    public void test18_helper(Object[] array, Object v) {
        array[0] = v;
    }

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        counts = {NULL_CHECK_TRAP, "= 1"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {NULL_CHECK_TRAP, "= 2", STORE_UNKNOWN_INLINE, "= 1"})
    public Object test18(Object[] array, Object v1) {
        Object v2 = array[0];
        test18_helper(array, v1);
        return v2;
    }

    @Run(test = "test18")
    @Warmup(10000)
    public void test18_verifier() {
        test18_helper(testValue1Array, testValue1); // pollute profile
        test18(testMyIntegerArray, new MyInteger(42));
        test18(testMyIntegerArray, null);
        testMyIntegerArray[0] = new MyInteger(42);
        test18(testMyLongArray, new MyLong(42L));
    }

    // maybe null free, not flat

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        failOn = {LOAD_UNKNOWN_INLINE})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    public Object test19(Object[] array) {
        return array[0];
    }

    @Run(test = "test19")
    @Warmup(10000)
    public void test19_verifier() {
        Object o = test19(testMyIntegerArray);
        Asserts.assertEQ(o, new MyInteger(42));
        o = test19(testNotFlattenableArray);
        Asserts.assertEQ(o, notFlattenable);
    }

    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        failOn = {STORE_UNKNOWN_INLINE})
    @IR(applyIf = {"UseArrayLoadStoreProfile", "false"},
        counts = {STORE_UNKNOWN_INLINE, "= 1"})
    public void test20(Object[] array, Object o) {
        array[0] = o;
    }

    @Run(test = "test20")
    @Warmup(10000)
    public void test20_verifier() {
        test20(testMyIntegerArray, new MyInteger(42));
        test20(testNotFlattenableArray, notFlattenable);
    }

    // acmp tests

    // branch frequency profiling causes not equal branch to be optimized out
    @Test
    @IR(counts = {IRNode.UNSTABLE_IF_TRAP, " = 1"})
    public boolean test21(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test21")
    @Warmup(10000)
    public void test21_verifier() {
        test21(obj, obj);
        test21(testValue1, testValue1);
    }

    // Input profiled non null
    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_ASSERT_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test22(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test22")
    @Warmup(10000)
    public void test22_verifier(RunInfo info) {
        test22(obj, null);
        test22(otherObj, null);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            test22(obj, otherObj);
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable*"},
        counts = {NULL_ASSERT_TRAP, "= 1"})
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test23(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test23")
    @Warmup(10000)
    public void test23_verifier(RunInfo info) {
        test23(null, obj);
        test23(null, otherObj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            test23(obj, otherObj);
            if (WBFlags.UseACmpProfile || WBFlags.TypeProfileLevel != 0) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_ASSERT_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test24(Object o1, Object o2) {
        return o1 != o2;
    }

    @Run(test = "test24")
    @Warmup(10000)
    public void test24_verifier(RunInfo info) {
        test24(obj, null);
        test24(otherObj, null);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            test24(obj, otherObj);
             if (WBFlags.UseACmpProfile) {
                 TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_ASSERT_TRAP, "= 1"})
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test25(Object o1, Object o2) {
        return o1 != o2;
    }

    @Run(test = "test25")
    @Warmup(10000)
    public void test25_verifier(RunInfo info) {
        test25(null, obj);
        test25(null, otherObj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            test25(obj, otherObj);
            if (WBFlags.UseACmpProfile || WBFlags.TypeProfileLevel != 0) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    // Input profiled not value class with known type
    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test26(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test26")
    @Warmup(10000)
    public void test26_verifier(RunInfo info) {
        test26(obj, obj);
        test26(obj, otherObj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test26(otherObj, obj);
            }
            if (WBFlags.UseACmpProfile || WBFlags.TypeProfileLevel != 0) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = { NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test27(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test27")
    @Warmup(10000)
    public void test27_verifier(RunInfo info) {
        test27(obj, obj);
        test27(otherObj, obj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test27(obj, otherObj);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test28(Object o1, Object o2) {
        return o1 != o2;
    }

    @Run(test = "test28")
    @Warmup(10000)
    public void test28_verifier(RunInfo info) {
        test28(obj, obj);
        test28(obj, otherObj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test28(otherObj, obj);
            }
            if (WBFlags.UseACmpProfile || WBFlags.TypeProfileLevel != 0) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test29(Object o1, Object o2) {
        return o1 != o2;
    }

    @Run(test = "test29")
    @Warmup(10000)
    public void test29_verifier(RunInfo info) {
        test29(obj, obj);
        test29(otherObj, obj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test29(obj, otherObj);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", NULL_CHECK_TRAP},
        counts = {CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test30(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test30")
    @Warmup(10000)
    public void test30_verifier(RunInfo info) {
        test30(obj, obj);
        test30(obj, otherObj);
        test30(null, 42);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test30(otherObj, obj);
            }
            if (WBFlags.UseACmpProfile || WBFlags.TypeProfileLevel != 0) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", NULL_CHECK_TRAP})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test31(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test31")
    @Warmup(10000)
    public void test31_verifier(RunInfo info) {
        test31(obj, obj);
        test31(otherObj, obj);
        test31(obj, null);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test31(obj, otherObj);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    // Input profiled not value class with unknown type
    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test32(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test32")
    @Warmup(10000)
    public void test32_verifier(RunInfo info) {
        test32(obj, obj);
        test32(obj, testValue1);
        test32(otherObj, obj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test32(testValue1, 42);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test33(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test33")
    @Warmup(10000)
    public void test33_verifier(RunInfo info) {
        test33(obj, obj);
        test33(testValue1, obj);
        test33(obj, otherObj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test33(obj, testValue1);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test34(Object o1, Object o2) {
        return o1 != o2;
    }

    @Run(test = "test34")
    @Warmup(10000)
    public void test34_verifier(RunInfo info) {
        test34(obj, obj);
        test34(obj, testValue1);
        test34(otherObj, obj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test34(testValue1, 42);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {NULL_CHECK_TRAP, "= 1", CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test35(Object o1, Object o2) {
        return o1 != o2;
    }

    @Run(test = "test35")
    @Warmup(10000)
    public void test35_verifier(RunInfo info) {
        test35(obj, obj);
        test35(testValue1, obj);
        test35(obj, otherObj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test35(obj, testValue1);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", NULL_CHECK_TRAP},
        counts = {CLASS_CHECK_TRAP, "= 1"})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test36(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test36")
    @Warmup(10000)
    public void test36_verifier(RunInfo info) {
        test36(obj, otherObj);
        test36(otherObj, testValue1);
        test36(null, obj);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test36(testValue1, obj);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    @Test
    @IR(applyIf = {"UseACmpProfile", "true"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", NULL_CHECK_TRAP})
    @IR(applyIf = {"UseACmpProfile", "false"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 1"})
    public boolean test37(Object o1, Object o2) {
        return o1 == o2;
    }

    @Run(test = "test37")
    @Warmup(10000)
    public void test37_verifier(RunInfo info) {
        test37(otherObj, obj);
        test37(testValue1, otherObj);
        test37(obj, null);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
            for (int i = 0; i < 10; i++) {
                test37(obj, testValue1);
            }
            if (WBFlags.UseACmpProfile) {
                TestFramework.assertDeoptimizedByC2(m);
            }
        }
    }

    // Test that acmp profile data that's unused at the acmp is fed to
    // speculation and leverage later
    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {CLASS_CHECK_TRAP, "= 2"})
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 2"})
    public void test38(Object o1, Object o2, Object o3) {
        if (o1 == o2) {
            test38_helper2();
        }
        test38_helper(o1, o3);
    }

    public void test38_helper(Object o1, Object o2) {
        if (o1 == o2) {
        }
    }

    public void test38_helper2() {
    }

    @Run(test = "test38")
    @Warmup(10000)
    public void test38_verifier() {
        test38(obj, obj, obj);
        test38_helper(testValue1, testValue2);
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {CLASS_CHECK_TRAP, "= 2"})
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", "= 2"})
    public void test39(Object o1, Object o2, Object o3) {
        if (o1 == o2) {
            test39_helper2();
        }
        test39_helper(o2, o3);
    }

    public void test39_helper(Object o1, Object o2) {
        if (o1 == o2) {
        }
    }

    public void test39_helper2() {
    }

    @Run(test = "test39")
    @Warmup(10000)
    public void test39_verifier() {
        test39(obj, obj, obj);
        test39_helper(testValue1, testValue2);
    }

    // Test array access with polluted array type profile
    static abstract value class Test40Abstract { }
    static value class Test40Class extends Test40Abstract { }

    @LooselyConsistentValue
    static value class Test40Inline extends Test40Abstract { }

    @ForceInline
    public Object test40_access(Object[] array) {
        return array[0];
    }

    @Test
    public Object test40(Test40Abstract[] array) {
        return test40_access(array);
    }

    @Run(test = "test40")
    @Warmup(10000)
    public void test40_verifier(RunInfo info) {
        // Make sure multiple implementors of Test40Abstract are loaded
        Test40Inline tmp1 = new Test40Inline();
        Test40Class tmp2 = new Test40Class();
        if (info.isWarmUp()) {
            // Pollute profile with Object[] (exact)
            test40_access(new Object[1]);
        } else {
            // When inlining test40_access, profiling contradicts actual type of array
            test40(new Test40Class[1]);
        }
    }

    // Same as test40 but with array store
    @ForceInline
    public void test41_access(Object[] array, Object val) {
        array[0] = val;
    }

    @Test
    public void test41(Test40Inline[] array, Object val) {
        test41_access(array, val);
    }

    @Run(test = "test41")
    @Warmup(10000)
    public void test41_verifier(RunInfo info) {
        // Make sure multiple implementors of Test40Abstract are loaded
        Test40Inline tmp1 = new Test40Inline();
        Test40Class tmp2 = new Test40Class();
        if (info.isWarmUp()) {
            // Pollute profile with exact Object[]
            test41_access(new Object[1], new Object());
        } else {
            // When inlining test41_access, profiling contradicts actual type of array
            Test40Inline[] array = (Test40Inline[])ValueClass.newNullRestrictedNonAtomicArray(Test40Inline.class, 1, new Test40Inline());
            test41(array, new Test40Inline());
        }
    }

    @Test
    static long test42(Long... v) {
        return v[0];
    }

    @Run(test = "test42")
    @Warmup(10000)
    public void test42_verifier() {
        Long[] arg = (Long[])ValueClass.newNullRestrictedNonAtomicArray(Long.class, 1, 0L);
        test42(arg);
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {LOAD_OF_CLASS, "MyValue1", "> 30"} // Loading all the fields, there are many.
    )
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        failOn = {LOAD_OF_CLASS, "MyValue1"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", ">= 1"}
    )
    public static boolean test43(Object a, Object b) {
        return a == b;
    }

    @Run(test = "test43")
    @Warmup(10000)
    public void test43_verifier() {
        var other = MyValue1.createWithFieldsInline(rI + 1, rL);
        Asserts.assertTrue(test43(testValue1, testValue1));
        Asserts.assertFalse(test43(testValue1, other));
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {LOAD_OF_CLASS, "MyValue1", "> 30"} // Loading all the fields, there are many.
    )
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        failOn = {LOAD_OF_CLASS, "MyValue1"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", ">= 1"}
    )
    public static boolean test44(Object a, Object b) {
        return a == b;
    }

    @Run(test = "test44")
    @Warmup(10000)
    public void test44_verifier() {
        Asserts.assertTrue(test44(testValue1, testValue1));
        Asserts.assertFalse(test44(testValue1, null));
        Asserts.assertFalse(test44(null, testValue1));
        Asserts.assertTrue(test44(null, null));
    }

    @Test
    @IR(applyIfOr = {"UseACmpProfile", "true", "TypeProfileLevel", "= 222"},
        failOn = {STATIC_CALL_OF_METHOD, "isSubstitutable.*"},
        counts = {LOAD_OF_CLASS, "MyValue1", "> 30"} // Loading all the fields, there are many.
    )
    @IR(applyIfAnd = {"UseACmpProfile", "false", "TypeProfileLevel", "!= 222"},
        failOn = {LOAD_OF_CLASS, "MyValue1"},
        counts = {STATIC_CALL_OF_METHOD, "isSubstitutable.*", ">= 1"}
    )
    public static boolean test45(Object a, Object b) {
        return a == b;
    }

    @Run(test = "test45")
    @Warmup(10000)
    public void test45_verifier(RunInfo info) {
        Asserts.assertTrue(test45(testValue1, testValue1));
        if (!info.isWarmUp()) {
            Asserts.assertFalse(test45(testValue1, null));
            Asserts.assertFalse(test45(null, testValue1));
            Asserts.assertTrue(test45(null, null));
        }
    }
}
