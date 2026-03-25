/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.whitebox.WhiteBox;

import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_ARRAY_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypes.*;

import static compiler.lib.ir_framework.IRNode.LOOP;
import static compiler.lib.ir_framework.IRNode.PREDICATE_TRAP;
import static compiler.lib.ir_framework.IRNode.SCOPE_OBJECT;
import static compiler.lib.ir_framework.IRNode.UNSTABLE_IF_TRAP;

/*
 * @test
 * @key randomness
 * @bug 8327695
 * @summary Test the basic value class implementation in C2.
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestBasicFunctionality 0
 */

/*
 * @test
 * @key randomness
 * @bug 8327695
 * @summary Test the basic value class implementation in C2.
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestBasicFunctionality 1
 */

/*
 * @test
 * @key randomness
 * @bug 8327695
 * @summary Test the basic value class implementation in C2.
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestBasicFunctionality 2
 */

/*
 * @test
 * @key randomness
 * @bug 8327695
 * @summary Test the basic value class implementation in C2.
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestBasicFunctionality 3
 */

/*
 * @test
 * @key randomness
 * @bug 8327695
 * @summary Test the basic value class implementation in C2.
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestBasicFunctionality 4
 */

/*
 * @test
 * @key randomness
 * @bug 8327695
 * @summary Test the basic value class implementation in C2.
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestBasicFunctionality 5
 */

/*
 * @test
 * @key randomness
 * @bug 8327695
 * @summary Test the basic value class implementation in C2.
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestBasicFunctionality 6
 */

@ForceCompileClassInitializer
public class TestBasicFunctionality {

    public TestBasicFunctionality() {
        val3 = MyValue1.createWithFieldsInline(rI, rL);
        super();
    }

    public static void main(String[] args) {
        InlineTypes.getFramework()
                   .addScenarios(InlineTypes.DEFAULT_SCENARIOS[Integer.parseInt(args[0])])
                   .addHelperClasses(MyValue1.class,
                                     MyValue2.class,
                                     MyValue2Inline.class,
                                     MyValue3.class,
                                     MyValue3Inline.class)
                   .start();
    }

    protected long hash() {
        return hash(rI, rL);
    }

    protected long hash(int x, long y) {
        return MyValue1.createWithFieldsInline(x, y).hash();
    }

    @DontInline
    static void call() {}

    // Receive value class through call to interpreter
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test1() {
        MyValue1 v = MyValue1.createWithFieldsDontInline(rI, rL);
        return v.hash();
    }

    @Run(test = "test1")
    public void test1_verifier() {
        long result = test1();
        Asserts.assertEQ(result, hash());
    }

    // Receive value object from interpreter via parameter
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test2(MyValue1 v) {
        return v.hash();
    }

    @Run(test = "test2")
    public void test2_verifier() {
        MyValue1 v = MyValue1.createWithFieldsDontInline(rI, rL);
        long result = test2(v);
        Asserts.assertEQ(result, hash());
    }

    // Return incoming value object without accessing fields
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "= 1", STORE_OF_ANY_KLASS, "= 19"},
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1 test3(MyValue1 v) {
        return v;
    }

    @Run(test = "test3")
    public void test3_verifier() {
        MyValue1 v1 = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1 v2 = test3(v1);
        Asserts.assertEQ(v1.x, v2.x);
        Asserts.assertEQ(v1.y, v2.y);
    }

    // Create a value object in compiled code and only use fields.
    // Allocation should go away because value object does not escape.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test4() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        return v.hash();
    }

    @Run(test = "test4")
    public void test4_verifier() {
        long result = test4();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in compiled code and pass it to
    // an inlined compiled method via a call.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test5() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        return test5Inline(v);
    }

    @ForceInline
    public long test5Inline(MyValue1 v) {
        return v.hash();
    }

    @Run(test = "test5")
    public void test5_verifier() {
        long result = test5();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in compiled code and pass it to
    // the interpreter via a call.
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 1"}, // 1 MyValue2 allocation (if not the all-zero value)
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 2"}, // 1 MyValue1 and 1 MyValue2 allocation (if not the all-zero value)
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test6() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        // Pass to interpreter
        return v.hashInterpreted();
    }

    @Run(test = "test6")
    public void test6_verifier() {
        long result = test6();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in compiled code and pass it to
    // the interpreter by returning.
    @Test
    @IR(counts = {ALLOC_OF_MYVALUE_KLASS, "<= 2"},
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1 test7(int x, long y) {
        return MyValue1.createWithFieldsInline(x, y);
    }

    @Run(test = "test7")
    public void test7_verifier() {
        MyValue1 v = test7(rI, rL);
        Asserts.assertEQ(v.hash(), hash());
    }

    // Merge value objects created from two branches
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test8(boolean b) {
        MyValue1 v;
        if (b) {
            v = MyValue1.createWithFieldsInline(rI, rL);
        } else {
            v = MyValue1.createWithFieldsDontInline(rI + 1, rL + 1);
        }
        return v.hash();
    }

    @Run(test = "test8")
    public void test8_verifier() {
        Asserts.assertEQ(test8(true), hash());
        Asserts.assertEQ(test8(false), hash(rI + 1, rL + 1));
    }

static MyValue1 tmp = null;
    // Merge value objects created from two branches
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "= 1", LOAD_OF_ANY_KLASS, "= 19",
                  STORE_OF_ANY_KLASS, "= 3"}, // InitializeNode::coalesce_subword_stores merges stores
        failOn = {UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "= 2", STORE_OF_ANY_KLASS, "= 19"},
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1 test9(boolean b, int localrI, long localrL) {
        MyValue1 v;
        if (b) {
            // Value object is not allocated
            // Do not use rI/rL directly here as null values may cause
            // some redundant null initializations to be optimized out
            // and matching to fail.
            v = MyValue1.createWithFieldsInline(localrI, localrL);
            v.hashInterpreted();
        } else {
            // Value object is allocated by the callee
            v = MyValue1.createWithFieldsDontInline(rI + 1, rL + 1);
        }
        // Need to allocate value object if 'b' is true
        long sum = v.hashInterpreted();
        if (b) {
            v = MyValue1.createWithFieldsDontInline(rI, sum);
        } else {
            v = MyValue1.createWithFieldsDontInline(rI, sum + 1);
        }
        // Don't need to allocate value object because both branches allocate
        return v;
    }

    @Run(test = "test9")
    public void test9_verifier() {
        MyValue1 v = test9(true, rI, rL);
        Asserts.assertEQ(v.x, rI);
        Asserts.assertEQ(v.y, hash());
        v = test9(false, rI, rL);
        Asserts.assertEQ(v.x, rI);
        Asserts.assertEQ(v.y, hash(rI + 1, rL + 1) + 1);
    }

    // Merge value objects created in a loop (not inlined)
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test10(int x, long y) {
        MyValue1 v = MyValue1.createWithFieldsDontInline(x, y);
        for (int i = 0; i < 10; ++i) {
            v = MyValue1.createWithFieldsDontInline(v.x + 1, v.y + 1);
        }
        return v.hash();
    }

    @Run(test = "test10")
    public void test10_verifier() {
        long result = test10(rI, rL);
        Asserts.assertEQ(result, hash(rI + 10, rL + 10));
    }

    // Merge value objects created in a loop (inlined)
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test11(int x, long y) {
        MyValue1 v = MyValue1.createWithFieldsInline(x, y);
        for (int i = 0; i < 10; ++i) {
            v = MyValue1.createWithFieldsInline(v.x + 1, v.y + 1);
        }
        return v.hash();
    }

    @Run(test = "test11")
    public void test11_verifier() {
        long result = test11(rI, rL);
        Asserts.assertEQ(result, hash(rI + 10, rL + 10));
    }

    // Test loop with uncommon trap referencing a value object
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = LOAD_OF_ANY_KLASS,
        counts = {SCOPE_OBJECT, ">= 1"})
    public long test12(boolean b) {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, Math.abs(rI) % 10, MyValue1.DEFAULT);
        for (int i = 0; i < va.length; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        long result = rL;
        for (int i = 0; i < 1000; ++i) {
            if (b) {
                result += v.x;
            } else {
                // Uncommon trap referencing v. We delegate allocation to the
                // interpreter by adding a SafePointScalarObjectNode.
                result = v.hashInterpreted();
                for (int j = 0; j < va.length; ++j) {
                    result += va[j].hash();
                }
            }
        }
        return result;
    }

    @Run(test = "test12")
    public void test12_verifier(RunInfo info) {
        // Disable OSR compilation prevents the method from getting recompiled because the IR rules
        // expect all loads moved into the uncommon trap, which is not the case when the method get
        // recompiled and the path that was unreached before is now compiled
        WhiteBox.getWhiteBox().makeMethodNotCompilable(info.getTest(), CompLevel.C2.getValue(), true);
        long result = test12(info.isWarmUp());
        Asserts.assertEQ(result, info.isWarmUp() ? rL + (1000 * rI) : ((Math.abs(rI) % 10) + 1) * hash());
    }

    // Test loop with uncommon trap referencing a value object
    @Test
    public long test13(boolean b) {
        MyValue1 v = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, Math.abs(rI) % 10, MyValue1.DEFAULT);
        for (int i = 0; i < va.length; ++i) {
            va[i] = MyValue1.createWithFieldsDontInline(rI, rL);
        }
        long result = rL;
        for (int i = 0; i < 1000; ++i) {
            if (b) {
                result += v.x;
            } else {
                // Uncommon trap referencing v. Should not allocate
                // but just pass the existing oop to the uncommon trap.
                result = v.hashInterpreted();
                for (int j = 0; j < va.length; ++j) {
                    result += va[j].hashInterpreted();
                }
            }
        }
        return result;
    }

    @Run(test = "test13")
    public void test13_verifier(RunInfo info) {
        long result = test13(info.isWarmUp());
        Asserts.assertEQ(result, info.isWarmUp() ? rL + (1000 * rI) : ((Math.abs(rI) % 10) + 1) * hash());
    }

    // Create a value object in a non-inlined method and then call a
    // non-inlined method on that value object.
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP},
        counts = {LOAD_OF_ANY_KLASS, "= 19"})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test14() {
        MyValue1 v = MyValue1.createWithFieldsDontInline(rI, rL);
        return v.hashInterpreted();
    }

    @Run(test = "test14")
    public void test14_verifier() {
        long result = test14();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in an inlined method and then call a
    // non-inlined method on that value object.
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 1"}) // 1 MyValue2 allocation (if not the all-zero value)
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 2"}) // 1 MyValue1 and 1 MyValue2 allocation (if not the all-zero value)
    public long test15() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        return v.hashInterpreted();
    }

    @Run(test = "test15")
    public void test15_verifier() {
        long result = test15();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in a non-inlined method and then call an
    // inlined method on that value object.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test16() {
        MyValue1 v = MyValue1.createWithFieldsDontInline(rI, rL);
        return v.hash();
    }

    @Run(test = "test16")
    public void test16_verifier() {
        long result = test16();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in an inlined method and then call an
    // inlined method on that value object.
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test17() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        return v.hash();
    }

    @Run(test = "test17")
    public void test17_verifier() {
        long result = test17();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in compiled code and pass it to the
    // interpreter via a call. The value object is live at the first call so
    // debug info should include a reference to all its fields.
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 1"}, // 1 MyValue2 allocation (if not the all-zero value)
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 2"}, // 1 MyValue1 and 1 MyValue2 allocation (if not the all-zero value)
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test18() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        v.hashInterpreted();
        return v.hashInterpreted();
    }

    @Run(test = "test18")
    public void test18_verifier() {
        long result = test18();
        Asserts.assertEQ(result, hash());
    }

    // Create a value object in compiled code and pass it to the
    // interpreter via a call. The value object is passed twice but
    // should only be allocated once.
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 1"}, // 1 MyValue2 allocation (if not the all-zero value)
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 2"}, // 1 MyValue1 and 1 MyValue2 allocation (if not the all-zero value)
        failOn = {LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test19() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        return sumValue(v, v);
    }

    @DontCompile
    public long sumValue(MyValue1 v, MyValue1 dummy) {
        return v.hash();
    }

    @Run(test = "test19")
    public void test19_verifier() {
        long result = test19();
        Asserts.assertEQ(result, hash());
    }

    // Create a value type (array) in compiled code and pass it to the
    // interpreter via a call. The value object is live at the uncommon
    // trap: verify that deoptimization causes the value object to be
    // correctly allocated.
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        counts = {ALLOC_OF_MYVALUE_KLASS, "<= 1"}, // 1 MyValue2 allocation (if not the all-zero value)
        failOn = {LOAD_OF_ANY_KLASS})
    // TODO 8350865
    //@IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
    //    counts = {ALLOC_OF_MYVALUE_KLASS, "<= 2"}, // 1 MyValue1 and 1 MyValue2 allocation (if not the all-zero value)
    //    failOn = LOAD_OF_ANY_KLASS)
    public long test20(boolean deopt, Method m) {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        MyValue2[] va = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 3, MyValue2.DEFAULT);
        if (deopt) {
            // uncommon trap
            TestFramework.deoptimize(m);
        }

        return v.hashInterpreted() + va[0].hashInterpreted() +
               va[1].hashInterpreted() + va[2].hashInterpreted();
    }

    @Run(test = "test20")
    public void test20_verifier(RunInfo info) {
        MyValue2[] va = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 42, MyValue2.DEFAULT);
        long result = test20(!info.isWarmUp(), info.getTest());
        Asserts.assertEQ(result, hash() + va[0].hash() + va[1].hash() + va[2].hash());
    }

    // Value class fields in regular object
    MyValue1 val1;
    MyValue2 val2;
    @NullRestricted
    final MyValue1 val3;
    @NullRestricted
    static MyValue1 val4 = MyValue1.DEFAULT;
    @NullRestricted
    static final MyValue1 val5 = MyValue1.createWithFieldsInline(rI, rL);

    // Test value class fields in objects
    @Test
    @IR(counts = {ALLOC_OF_MYVALUE_KLASS, "= 4"}, failOn = {UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test21(int x, long y) {
        // Compute hash of value class fields
        long result = val1.hash() + val2.hash() + val3.hash() + val4.hash() + val5.hash();
        // Update fields
        val1 = MyValue1.createWithFieldsInline(x, y);
        val2 = MyValue2.createWithFieldsInline(x, rD);
        val4 = MyValue1.createWithFieldsInline(x, y);
        return result;
    }

    @Run(test = "test21")
    public void test21_verifier() {
        // Check if hash computed by test18 is correct
        val1 = MyValue1.createWithFieldsInline(rI, rL);
        val2 = val1.v2;
        // val3 is initialized in the constructor
        val4 = val1;
        // val5 is initialized in the static initializer
        long hash = val1.hash() + val2.hash() + val3.hash() + val4.hash() + val5.hash();
        long result = test21(rI + 1, rL + 1);
        Asserts.assertEQ(result, hash);
        // Check if value class fields were updated
        Asserts.assertEQ(val1.hash(), hash(rI + 1, rL + 1));
        Asserts.assertEQ(val2.hash(), MyValue2.createWithFieldsInline(rI + 1, rD).hash());
        Asserts.assertEQ(val4.hash(), hash(rI + 1, rL + 1));
    }

    // Test folding of constant value class fields
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test22() {
        // This should be constant folded
        return val5.hash() + val5.v3.hash();
    }

    @Run(test = "test22")
    public void test22_verifier() {
        long result = test22();
        Asserts.assertEQ(result, val5.hash() + val5.v3.hash());
    }

    // Test value class initialization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test23() {
        MyValue2 v = MyValue2.createDefaultInline();
        return v.hash();
    }

    @Run(test = "test23")
    public void test23_verifier() {
        long result = test23();
        Asserts.assertEQ(result, MyValue2.createDefaultInline().hash());
    }

    // Test value class initialization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test24() {
        MyValue1 v1 = MyValue1.createDefaultInline();
        MyValue1 v2 = MyValue1.createDefaultDontInline();
        return v1.hashPrimitive() + v2.hashPrimitive();
    }

    @Run(test = "test24")
    public void test24_verifier() {
        long result = test24();
        Asserts.assertEQ(result, 2 * MyValue1.createDefaultInline().hashPrimitive());
    }

    // Test field initialization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test25() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        return v.hash();
    }

    @Run(test = "test25")
    public void test25_verifier() {
        long result = test25();
        Asserts.assertEQ(result, MyValue2.createWithFieldsInline(rI, rD).hash());
    }

    // Test field initialization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test26() {
        MyValue1 v1 = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1 v2 = MyValue1.createWithFieldsDontInline(rI, rL);
        return v1.hash() + v2.hash();
    }

    @Run(test = "test26")
    public void test26_verifier() {
        long result = test26();
        Asserts.assertEQ(result, 2 * hash());
    }

    class TestClass27 {
        @NullRestricted
        public MyValue1 v;

        TestClass27() {
            v = MyValue1.DEFAULT;
            super();
        }
    }

    // Test allocation elimination of unused object with initialized value class field
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP})
    public void test27(boolean deopt, Method m) {
        TestClass27 unused = new TestClass27();
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        unused.v = v;
        if (deopt) {
            // uncommon trap
            TestFramework.deoptimize(m);
        }
    }

    @Run(test = "test27")
    public void test27_verifier(RunInfo info) {
        test27(!info.isWarmUp(), info.getTest());
    }

    @NullRestricted
    static MyValue3 staticVal3 = MyValue3.DEFAULT;
    @NullRestricted
    static MyValue3 staticVal3_copy = MyValue3.DEFAULT;

    // Check elimination of redundant value class allocations
    @Test
    @IR(counts = {ALLOC_OF_MYVALUE_KLASS, "= 1"})
    public MyValue3 test28(MyValue3[] va) {
        // Create value object and force allocation
        MyValue3 vt = MyValue3.create();
        va[0] = vt;
        staticVal3 = vt;
        vt.verify(staticVal3);

        // Value object is now allocated, make a copy and force allocation.
        // Because copy is equal to vt, C2 should remove this redundant allocation.
        MyValue3 copy = MyValue3.setC(vt, vt.c);
        va[0] = copy;
        staticVal3_copy = copy;
        copy.verify(staticVal3_copy);
        return copy;
    }

    @Run(test = "test28")
    public void test28_verifier() {
        MyValue3[] va = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);
        MyValue3 vt = test28(va);
        staticVal3.verify(vt);
        staticVal3.verify(va[0]);
        staticVal3_copy.verify(vt);
        staticVal3_copy.verify(va[0]);
    }

    // Verify that only dominating allocations are re-used
    @Test
    public MyValue3 test29(boolean warmup) {
        MyValue3 vt = MyValue3.create();
        if (warmup) {
            staticVal3 = vt; // Force allocation
        }
        // Force allocation to verify that above
        // non-dominating allocation is not re-used
        MyValue3 copy = MyValue3.setC(vt, vt.c);
        staticVal3_copy = copy;
        copy.verify(vt);
        return copy;
    }

    @Run(test = "test29")
    public void test29_verifier(RunInfo info) {
        MyValue3 vt = test29(info.isWarmUp());
        if (info.isWarmUp()) {
            staticVal3.verify(vt);
        }
    }

    // Verify that C2 recognizes value class loads and re-uses the oop to avoid allocations
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public MyValue3 test30() {
        // C2 can re-use the oop of staticVal3 because staticVal3 is equal to copy
        MyValue3[] va = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);
        MyValue3 copy = MyValue3.copy(staticVal3);
        va[0] = copy;
        copy.verify(va[0]);
        staticVal3 = copy;
        copy.verify(staticVal3);
        return copy;
    }

    @Run(test = "test30")
    public void test30_verifier() {
        staticVal3 = MyValue3.create();
        MyValue3 vt = test30();
        staticVal3.verify(vt);
    }

    // Verify that C2 recognizes value class loads and re-uses the oop to avoid allocations
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public MyValue3 test31() {
        // C2 can re-use the oop returned by createDontInline()
        // because the corresponding value object is equal to 'copy'.
        MyValue3[] va = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);
        MyValue3 copy = MyValue3.copy(MyValue3.createDontInline());
        va[0] = copy;
        copy.verify(va[0]);
        staticVal3 = copy;
        copy.verify(staticVal3);
        return copy;
    }

    @Run(test = "test31")
    public void test31_verifier() {
        MyValue3 vt = test31();
        staticVal3.verify(vt);
    }

    // Verify that C2 recognizes value class loads and re-uses the oop to avoid allocations
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public MyValue3 test32(MyValue3 vt) {
        // C2 can re-use the oop of vt because vt is equal to 'copy'.
        MyValue3[] va = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);
        MyValue3 copy = MyValue3.copy(vt);
        va[0] = copy;
        copy.verify(vt);
        staticVal3 = copy;
        copy.verify(staticVal3);
        return copy;
    }

    @Run(test = "test32")
    public void test32_verifier() {
        MyValue3 vt = MyValue3.create();
        MyValue3 result = test32(vt);
        staticVal3.verify(vt);
        result.verify(vt);
    }

    // Test correct identification of value object copies
    @Test
    public MyValue3 test33() {
        MyValue3[] va = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);
        MyValue3 vt = MyValue3.copy(staticVal3);
        vt = MyValue3.setI(vt, vt.c);
        // vt is not equal to staticVal3, so C2 should not re-use the oop
        va[0] = vt;
        Asserts.assertEQ(va[0].i, (int)vt.c);
        staticVal3 = vt;
        vt.verify(staticVal3);
        return vt;
    }

    @Run(test = "test33")
    public void test33_verifier() {
        staticVal3 = MyValue3.create();
        MyValue3 vt = test33();
        Asserts.assertEQ(staticVal3.i, (int)staticVal3.c);
        Asserts.assertEQ(vt.i, (int)staticVal3.c);
    }

    static final MyValue3[] test34Array = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 2, MyValue3.DEFAULT);

    // Verify that the all-zero value class is never allocated.
    // C2 code should load and use the all-zero oop from the java mirror.
    @Test
    // The concept of a pre-allocated "all-zero value" was removed.
    // @IR(applyIf = {"UseArrayFlattening", "true"},
    //     failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue3 test34() {
        // Explicitly create all-zero value
        MyValue3 vt = MyValue3.createDefault();
        test34Array[0] = vt;
        staticVal3 = vt;
        vt.verify(vt);

        // Load all-zero value from uninitialized value class array
        MyValue3[] dva = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);
        staticVal3_copy = dva[0];
        test34Array[1] = dva[0];
        dva[0].verify(dva[0]);
        return vt;
    }

    @Run(test = "test34")
    public void test34_verifier() {
        MyValue3 vt = MyValue3.createDefault();
        test34Array[0] = MyValue3.create();
        test34Array[1] = MyValue3.create();
        MyValue3 res = test34();
        res.verify(vt);
        staticVal3.verify(vt);
        staticVal3_copy.verify(vt);
        test34Array[0].verify(vt);
        test34Array[1].verify(vt);
    }

    static final MyValue3[] test35Array = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, MyValue3.DEFAULT);

    // Same as above but manually initialize value class fields to all-zero.
    @Test
    // The concept of a pre-allocated "all-zero value" was removed.
    // @IR(applyIf = {"UseArrayFlattening", "true"},
    //     failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue3 test35(MyValue3 vt) {
        vt = MyValue3.setC(vt, (char)0);
        vt = MyValue3.setBB(vt, (byte)0);
        vt = MyValue3.setS(vt, (short)0);
        vt = MyValue3.setI(vt, 0);
        vt = MyValue3.setL(vt, 0);
        vt = MyValue3.setO(vt, null);
        vt = MyValue3.setF1(vt, 0);
        vt = MyValue3.setF2(vt, 0);
        vt = MyValue3.setF3(vt, 0);
        vt = MyValue3.setF4(vt, 0);
        vt = MyValue3.setF5(vt, 0);
        vt = MyValue3.setF6(vt, 0);
        vt = MyValue3.setV1(vt, MyValue3Inline.createDefault());
        test35Array[0] = vt;
        staticVal3 = vt;
        vt.verify(vt);
        return vt;
    }

    @Run(test = "test35")
    public void test35_verifier() {
        MyValue3 vt = MyValue3.createDefault();
        test35Array[0] = MyValue3.create();
        MyValue3 res = test35(test35Array[0]);
        res.verify(vt);
        staticVal3.verify(vt);
        test35Array[0].verify(vt);
    }

    // Merge value objects created from two branches

    private Object test36_helper(Object v) {
        return v;
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test36(boolean b) {
        Object o;
        if (b) {
            o = test36_helper(MyValue1.createWithFieldsInline(rI, rL));
        } else {
            o = test36_helper(MyValue1.createWithFieldsDontInline(rI + 1, rL + 1));
        }
        MyValue1 v = (MyValue1)o;
        return v.hash();
    }

    @Run(test = "test36")
    public void test36_verifier() {
        Asserts.assertEQ(test36(true), hash());
        Asserts.assertEQ(test36(false), hash(rI + 1, rL + 1));
    }

    // Test correct loading of flattened fields
    @LooselyConsistentValue
    value class Test37Value2 {
        int x = 0;
        int y = 0;
    }

    @LooselyConsistentValue
    value class Test37Value1 {
        double d = 0;
        float f = 0;
        @NullRestricted
        Test37Value2 v = new Test37Value2();
    }

    @Test
    public Test37Value1 test37(Test37Value1 vt) {
        return vt;
    }

    @Run(test = "test37")
    public void test37_verifier() {
        Test37Value1 vt = new Test37Value1();
        Asserts.assertEQ(test37(vt), vt);
    }

    // Test elimination of value class allocations without a unique CheckCastPP
    @LooselyConsistentValue
    static value class Test38Value {
        public int i;
        public Test38Value(int i) { this.i = i; }
    }

    @NullRestricted
    static Test38Value test38Field = new Test38Value(0);

    @Test
    public void test38() {
        for (int i = 3; i < 100; ++i) {
            int j = 1;
            while (++j < 11) {
                try {
                    test38Field = new Test38Value(i);
                } catch (ArithmeticException ae) { }
            }
        }
    }

    @Run(test = "test38")
    public void test38_verifier() {
        test38Field = new Test38Value(0);
        test38();
        Asserts.assertEQ(test38Field, new Test38Value(99));
    }

    // Tests split if with value class Phi users
    @LooselyConsistentValue
    static value class Test39Value {
        public int iFld1;
        public int iFld2;

        public Test39Value(int i1, int i2) { iFld1 = i1; iFld2 = i2; }
    }

    static int test39A1[][] = new int[400][400];
    static double test39A2[] = new double[400];
    @NullRestricted
    static Test39Value test39Val = new Test39Value(0, 0);

    @DontInline
    public int[] getArray() {
        return new int[400];
    }

    @Test
    public int test39() {
        int result = 0;
        for (int i = 0; i < 100; ++i) {
            switch ((i >>> 1) % 3) {
                case 0:
                    test39A1[i][i] = i;
                    break;
                case 1:
                    for (int j = 0; j < 100; ++j) {
                        test39A1[i] = getArray();
                        test39Val = new Test39Value(j, test39Val.iFld2);
                    }
                    break;
                case 2:
                    for (float f = 142; f > i; f--) {
                        test39A2[i + 1] += 3;
                    }
                    result += test39Val.iFld1;
                    break;
            }
            double d1 = 1;
            while (++d1 < 142) {
                test39A1[(i >>> 1) % 400][i + 1] = result;
                test39Val = new Test39Value(i, test39Val.iFld2);
            }
        }
        return result;
    }

    @Run(test = "test39")
    @Warmup(10)
    public void test39_verifier() {
        int result = test39();
        Asserts.assertEQ(result, 1552);
    }

    // Test scalar replacement of value class array containing value class with oop fields
    @Test
    public long test40(boolean b) {
        MyValue1[] va = {MyValue1.createWithFieldsInline(rI, rL)};
        long result = 0;
        for (int i = 0; i < 1000; ++i) {
            if (!b) {
                result = va[0].hash();
            }
        }
        return result;
    }

    @Run(test = "test40")
    public void test40_verifier(RunInfo info) {
        long result = test40(info.isWarmUp());
        Asserts.assertEQ(result, info.isWarmUp() ? 0 : hash());
    }

    static value class MyValue41 {
        int x;

        public MyValue41(int x) {
            this.x = x;
        }

        static MyValue41 make() {
            return new MyValue41(0);
        }
    }

    static MyValue41 field41;

    // Test detection of value object copies and removal of the MemBarRelease following the value buffer initialization
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public void test41(MyValue41 val) {
        field41 = new MyValue41(val.x);
    }

    @Run(test = "test41")
    public void test41_verifier() {
        MyValue41 val = new MyValue41(rI);
        test41(val);
        Asserts.assertEQ(field41, val);
    }

    @DontInline
    public void test42_helper(MyValue41 val) {
        Asserts.assertEQ(val, new MyValue41(rI));
    }

    // Same as test41 but with call argument requiring buffering
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public void test42(MyValue41 val) {
        test42_helper(new MyValue41(val.x));
    }

    @Run(test = "test42")
    public void test42_verifier() {
        MyValue41 val = new MyValue41(rI);
        test42(val);
    }

    static value class MyValue42 {
        int x;

        @ForceInline
        MyValue42(int x) {
            this.x = x;
            call();
            super();
        }

        @ForceInline
        static Object make(int x) {
            return new MyValue42(x);
        }
    }

    @Test
    @IR(failOn = {LOAD_OF_ANY_KLASS})
    public MyValue42 test43(int x) {
        return (MyValue42) MyValue42.make(x);
    }

    @Run(test = "test43")
    public void test43_verifier() {
        MyValue42 v = test43(rI);
        Asserts.assertEQ(rI, v.x);
    }

    static value class MyValue43 {
        int x;

        @ForceInline
        MyValue43(int x) {
            this.x = x;
            super();
            call();
        }

        @ForceInline
        static Object make(int x) {
            return new MyValue43(x);
        }
    }

    @Test
    @IR(failOn = {LOAD_OF_ANY_KLASS})
    public MyValue43 test44(int x) {
        return (MyValue43) MyValue43.make(x);
    }

    @Run(test = "test44")
    public void test44_verifier() {
        MyValue43 v = test44(rI);
        Asserts.assertEQ(rI, v.x);
    }

    @LooselyConsistentValue
    static value class MyValue45 {
        Integer v;

        MyValue45(Integer v) {
            this.v = v;
        }
    }

    static value class MyValue45ValueHolder {
        @NullRestricted
        MyValue45 v;

        MyValue45ValueHolder(Integer v) {
            this.v = new MyValue45(v);
        }
    }

    static class MyValue45Holder {
        @NullRestricted
        MyValue45 v;

        MyValue45Holder(Integer v) {
            this.v = new MyValue45(v);
            super();
        }
    }

    @Test
    // TODO 8357580 more aggressive flattening
    // @IR(applyIfAnd = {"UseFieldFlattening", "true", "UseNullableValueFlattening", "true"}, counts = {IRNode.LOAD_I, "1", IRNode.LOAD_B, "1"})
    public Integer test45(Object arg) {
        return ((MyValue45ValueHolder) arg).v.v;
    }

    @Run(test = "test45")
    public void test45_verifier() {
        Integer v = null;
        Asserts.assertEQ(test45(new MyValue45ValueHolder(v)), v);
        v = rI;
        Asserts.assertEQ(test45(new MyValue45ValueHolder(v)), v);
    }

    @Test
    // TODO 8357580 more aggressive flattening
    // @IR(applyIfAnd = {"UseFieldFlattening", "true", "UseNullableValueFlattening", "true"}, counts = {IRNode.LOAD_L, "1"})
    // @IR(applyIfAnd = {"UseFieldFlattening", "true", "UseNullableValueFlattening", "true"}, failOn = {IRNode.LOAD_I, IRNode.LOAD_B})
    public Integer test46(Object arg) {
        return ((MyValue45Holder) arg).v.v;
    }

    @Run(test = "test46")
    public void test46_verifier() {
        Integer v = null;
        Asserts.assertEQ(test46(new MyValue45Holder(v)), v);
        v = rI;
        Asserts.assertEQ(test46(new MyValue45Holder(v)), v);
    }

    static value class MyValue47 {
        byte b1;
        byte b2;

        MyValue47(byte b1, byte b2) {
            this.b1 = b1;
            this.b2 = b2;
        }
    }

    static value class MyValue47Holder {
        @NullRestricted
        MyValue47 v;

        MyValue47Holder(int v) {
            byte b1 = (byte) v;
            byte b2 = (byte) (v >>> 8);
            this.v = new MyValue47(b1, b2);
        }
    }

    static class MyValue47HolderHolder {
        @NullRestricted
        MyValue47Holder v;

        MyValue47HolderHolder(MyValue47Holder v) {
            this.v = v;
            super();
        }
    }

    @Test
    @IR(applyIfAnd = {"UseFieldFlattening", "true", "UseAtomicValueFlattening", "true"}, counts = {IRNode.LOAD_S, "1"})
    @IR(applyIfAnd = {"UseFieldFlattening", "true", "UseAtomicValueFlattening", "true"}, failOn = {IRNode.LOAD_B})
    public MyValue47Holder test47(MyValue47HolderHolder arg) {
        return arg.v;
    }

    @Run(test = "test47")
    public void test47_verifier() {
        MyValue47Holder v = new MyValue47Holder(rI);
        Asserts.assertEQ(test47(new MyValue47HolderHolder(v)), v);
    }

    static final MyValue47Holder[] MY_VALUE_47_HOLDERS = (MyValue47Holder[]) ValueClass.newNullRestrictedAtomicArray(MyValue47Holder.class, 2, new MyValue47Holder(rI));

    @Test
    @IR(applyIfAnd = {"UseFieldFlattening", "true", "UseArrayFlattening", "true", "UseAtomicValueFlattening", "true"}, counts = {IRNode.LOAD_S, "1"})
    @IR(applyIfAnd = {"UseFieldFlattening", "true", "UseArrayFlattening", "true", "UseAtomicValueFlattening", "true"}, failOn = {IRNode.LOAD_B})
    public MyValue47Holder test48() {
        return MY_VALUE_47_HOLDERS[0];
    }

    @Run(test = "test48")
    public void test48_verifier() {
        MyValue47Holder v = new MyValue47Holder(rI);
        Asserts.assertEQ(test48(), v);
    }
}
