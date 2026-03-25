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

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypes.rI;
import static compiler.valhalla.inlinetypes.InlineTypes.rL;

/*
 * @test
 * @key randomness
 * @summary Test on stack replacement (OSR) with value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestOnStackReplacement 0
 */

/*
 * @test
 * @key randomness
 * @summary Test on stack replacement (OSR) with value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestOnStackReplacement 1
 */

/*
 * @test
 * @key randomness
 * @summary Test on stack replacement (OSR) with value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestOnStackReplacement 2
 */

/*
 * @test
 * @key randomness
 * @summary Test on stack replacement (OSR) with value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/timeout=240 compiler.valhalla.inlinetypes.TestOnStackReplacement 3
 */

/*
 * @test
 * @key randomness
 * @summary Test on stack replacement (OSR) with value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestOnStackReplacement 4
 */

/*
 * @test
 * @key randomness
 * @summary Test on stack replacement (OSR) with value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestOnStackReplacement 5
 */

/*
 * @test
 * @key randomness
 * @summary Test on stack replacement (OSR) with value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestOnStackReplacement 6
 */

public class TestOnStackReplacement {

    public static void main(String[] args) throws Throwable {
        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;
        scenarios[3].addFlags("-XX:-UseArrayFlattening");

        InlineTypes.getFramework()
                   .addScenarios(scenarios[Integer.parseInt(args[0])])
                   .addHelperClasses(MyValue1.class,
                                     MyValue2.class,
                                     MyValue2Inline.class,
                                     MyValue3.class,
                                     MyValue3Inline.class)
                   .start();
    }

    // Helper methods

    protected long hash() {
        return hash(rI, rL);
    }

    protected long hash(int x, long y) {
        return MyValue1.createWithFieldsInline(x, y).hash();
    }

    // Test OSR compilation
    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public long test1() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, Math.abs(rI) % 3, MyValue1.DEFAULT);
        for (int i = 0; i < va.length; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        long result = 0;
        // Long loop to trigger OSR compilation
        for (int i = 0; i < 50_000; ++i) {
            // Reference local value object in interpreter state
            result = v.hash();
            for (int j = 0; j < va.length; ++j) {
                result += va[j].hash();
            }
        }
        return result;
    }

    @Run(test = "test1")
    @Warmup(0)
    public void test1_verifier() {
        long result = test1();
        Asserts.assertEQ(result, ((Math.abs(rI) % 3) + 1) * hash());
    }

    // Test loop peeling
    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public void test2() {
        MyValue1 v = MyValue1.createWithFieldsInline(0, 1);
        // Trigger OSR compilation and loop peeling
        for (int i = 0; i < 50_000; ++i) {
            if (v.x != i || v.y != i + 1) {
                // Uncommon trap
                throw new RuntimeException("test2 failed");
            }
            v = MyValue1.createWithFieldsInline(i + 1, i + 2);
        }
    }

    @Run(test = "test2")
    @Warmup(0)
    public void test2_verifier() {
        test2();
    }

    // Test loop peeling and unrolling
    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public void test3() {
        MyValue1 v1 = MyValue1.createWithFieldsInline(0, 0);
        MyValue1 v2 = MyValue1.createWithFieldsInline(1, 1);
        // Trigger OSR compilation and loop peeling
        for (int i = 0; i < 50_000; ++i) {
            if (v1.x != 2*i || v2.x != i+1 || v2.y != i+1) {
                // Uncommon trap
                throw new RuntimeException("test3 failed");
            }
            v1 = MyValue1.createWithFieldsInline(2*(i+1), 0);
            v2 = MyValue1.createWithFieldsInline(i+2, i+2);
        }
    }

    //@Run(test = "test3")
    //@Warmup(0)
    public void test3_verifier() {
        test3();
    }

    // OSR compilation with Object local
    @DontCompile
    public Object test4_init() {
        return MyValue1.createWithFieldsInline(rI, rL);
    }

    @DontCompile
    public Object test4_body() {
        return MyValue1.createWithFieldsInline(rI, rL);
    }

    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public Object test4() {
        Object vt = test4_init();
        for (int i = 0; i < 50_000; i++) {
            if (i % 2 == 1) {
                vt = test4_body();
            }
        }
        return vt;
    }

    @Run(test = "test4")
    @Warmup(0)
    public void test4_verifier() {
        test4();
    }

    // OSR compilation with null value class local

    MyValue1 nullField;

    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public void test5() {
        MyValue1 vt = nullField;
        for (int i = 0; i < 50_000; i++) {
            if (vt != null) {
                throw new RuntimeException("test5 failed: vt should be null");
            }
        }
    }

    @Run(test = "test5")
    @Warmup(0)
    public void test5_verifier() {
        test5();
    }

    // Test OSR in method with value class receiver
    @LooselyConsistentValue
    value class Test6Value {
        public int f = 0;

        public int test() {
            int res = 0;
            for (int i = 1; i < 20_000; ++i) {
                res -= i;
            }
            return res;
        }
    }

    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public void test6() {
        Test6Value tmp = new Test6Value();
        for (int i = 0; i < 100; ++i) {
            tmp.test();
        }
    }

    @Run(test = "test6")
    @Warmup(0)
    public void test6_verifier() {
        test6();
    }

    // Similar to test6 but with more fields and reserved stack entry
    @LooselyConsistentValue
    static value class Test7Value1 {
        public int i1 = rI;
        public int i2 = rI;
        public int i3 = rI;
        public int i4 = rI;
        public int i5 = rI;
        public int i6 = rI;
    }

    @LooselyConsistentValue
    static value class Test7Value2 {
        public int i1 = rI;
        public int i2 = rI;
        public int i3 = rI;
        public int i4 = rI;
        public int i5 = rI;
        public int i6 = rI;
        public int i7 = rI;
        public int i8 = rI;
        public int i9 = rI;
        public int i10 = rI;
        public int i11 = rI;
        public int i12 = rI;
        public int i13 = rI;
        public int i14 = rI;
        public int i15 = rI;
        public int i16 = rI;
        public int i17 = rI;
        public int i18 = rI;
        public int i19 = rI;
        public int i20 = rI;
        public int i21 = rI;

        @NullRestricted
        public Test7Value1 vt = new Test7Value1();

        public int test(String[] args) {
            int res = 0;
            for (int i = 1; i < 20_000; ++i) {
                res -= i;
            }
            return res;
        }
    }

    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public void test7() {
        Test7Value2 tmp = new Test7Value2();
        for (int i = 0; i < 10; ++i) {
            tmp.test(null);
        }
    }

    @Run(test = "test7")
    @Warmup(0)
    public void test7_verifier() {
        test7();
    }

    // Test OSR with scalarized value class return
    MyValue3 test8_vt;

    @DontInline
    public MyValue3 test8_callee(int len) {
        test8_vt = MyValue3.create();
        int val = 0;
        for (int i = 0; i < len; ++i) {
            val = i;
        }
        test8_vt = test8_vt.setI(test8_vt, val);
        return test8_vt;
    }

    @Test
    public int test8(int start) {
        MyValue3 vt = test8_callee(start);
        test8_vt.verify(vt);
        int result = 0;
        for (int i = 0; i < 50_000; ++i) {
            result += i;
        }
        return result;
    }

    @Run(test = "test8")
    @Warmup(2)
    public void test8_verifier() {
        test8(1);
        test8(50_000);
    }
}
