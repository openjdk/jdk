/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8387705
 * @summary Test that G1 barriers for StoreLSpecial are generated and optimized as expected
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @library /test/lib /
 * @requires vm.gc.G1 & vm.bits == 64
 * @run driver ${test.main.class}
 */

package compiler.gcbarriers;

import compiler.lib.ir_framework.*;
import static compiler.lib.ir_framework.IRNode.*;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

public class TestG1BarrierCapturedStoreLSpecial {
    static final String PRE_ONLY = "pre";
    static final String PRE_AND_POST = "pre post";
    static final String PRE_AND_POST_NOT_NULL = "pre post notnull";
    static final String ANY = ".*";

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario[] scenarios = new Scenario[2];
        int scenarioIndex = 0;
        for (int i = 0; i < 2; i++) {
            scenarios[scenarioIndex] =
                new Scenario(scenarioIndex,
                             "--enable-preview",
                             "-XX:" + (i == 0 ? "-" : "+") + "UseCompressedOops"
                        );
            scenarioIndex++;
        }
        framework.addScenarios(scenarios)
                .addFlags(
                        "-XX:+UseG1GC",
                        "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                        "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
                )
                .start();
    }

    static value class MyValue {
        Object value;

        MyValue(Object value) {
            this.value = value;
        }
    }

    static class Holder {
        Integer i = null;
        MyValue v = null;
    }

    static value class MyValueWithTwoOops {
        Object v1;
        Object v2;

        MyValueWithTwoOops(Object v1, Object v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    static class HolderWithTwoOops {
        @NullRestricted
        MyValueWithTwoOops v;

        HolderWithTwoOops() {
            this.v = new MyValueWithTwoOops(null, null);
            super();
        }
        HolderWithTwoOops(MyValueWithTwoOops v) {
            this.v = v;
            super();
        }
    }

    static abstract value class MyValueOopFirstBase {
        Object v;

        MyValueOopFirstBase(Object v) {
            this.v = v;
        }
    }
    static value class MyValueOopFirst extends MyValueOopFirstBase {
        int i;

        MyValueOopFirst(int i, Object v) {
            this.i = i;
            super(v);
        }
    }
    static class HolderWithOopsFirst {
        @NullRestricted
        MyValueOopFirst v;

        HolderWithOopsFirst() {
            this.v = new MyValueOopFirst(0, null);
            super();
        }
        HolderWithOopsFirst(MyValueOopFirst v) {
            this.v = v;
            super();
        }
    }

    static abstract value class MyValueOopSecondBase {
        int i;

        MyValueOopSecondBase(int i) {
            this.i = i;
        }
    }
    static value class MyValueOopSecond extends MyValueOopSecondBase {
        Object v;

        MyValueOopSecond(int i, Object v) {
            this.v = v;
            super(i);
        }
    }
    static class HolderWithOopsSecond {
        @NullRestricted
        MyValueOopSecond v;

        HolderWithOopsSecond() {
            this.v = new MyValueOopSecond(0, null);
            super();
        }
        HolderWithOopsSecond(MyValueOopSecond v) {
            this.v = v;
            super();
        }
    }

    // Assignment of value of unknown nullness, just after allocation
    // No barrier needed. Store could be captured.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL, "2"},  // Should be 1 with store capturing
        failOn = {G1_STORE_L_SPECIAL_WITH_BARRIER_FLAG, ANY})
    static Holder test1(Integer i, MyValue v) {
        Holder t = new Holder();
        t.i = i;
        t.v = v;
        return t;
    }

    @Run(test = {"test1"})
    public void test1_verifier() {
        MyValue v = new MyValue(new Object());
        Holder h = test1(42, v);
        Asserts.assertEQ(h.i, 42);
        Asserts.assertEQ(h.v, v);
    }

    // Assignment of value of unknown nullness, in existing object.
    // Pre- and post-barrier needed.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_WITH_BARRIER_FLAG, PRE_AND_POST, "1"})
    static Holder test1_no_capture(Holder t, Integer i, MyValue v) {
        t.i = i;
        t.v = v;
        return t;
    }

    @Run(test = {"test1_no_capture"})
    public void test1_no_capture_verifier() {
        MyValue v = new MyValue(new Object());
        Holder t = new Holder();
        Holder h = test1_no_capture(t, 42, v);
        Asserts.assertEQ(t.i, 42);
        Asserts.assertEQ(t.v, v);
        Asserts.assertEQ(t, h);
    }

    // Similar to test1, but v is also freshly allocated, hence not-null.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL, "2"},  // Should be 1 with store capturing
        failOn = {G1_STORE_L_SPECIAL_WITH_BARRIER_FLAG, ANY})
    static Holder test2(Integer i) {
        MyValue v = new MyValue(new Object());
        Holder t = new Holder();
        t.i = i;
        t.v = v;
        return t;
    }

    @Run(test = {"test2"})
    public void test2_verifier() {
        Holder h = test2(42);
        Asserts.assertEQ(h.i, 42);
        Asserts.assertNE(h.v, null);
    }

    // Similar to test1, but v is known to be null.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL, "2"},  // Should be 1 with store capturing
        failOn = {G1_STORE_L_SPECIAL_WITH_BARRIER_FLAG, ANY})
    static Holder test3(Integer i) {
        Holder t = new Holder();
        t.i = i;
        t.v = null;
        return t;
    }

    @Run(test = {"test3"})
    public void test3_verifier() {
        Holder h = test3(42);
        Asserts.assertEQ(h.i, 42);
        Asserts.assertEQ(h.v, null);
    }

    // Similar to test2, v is still not-null at assignment point, but it is already allocated
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_TWO_OOPS_WITH_BARRIER_FLAG, PRE_AND_POST, "1"})
    static HolderWithTwoOops test4(HolderWithTwoOops h, MyValueWithTwoOops v) {
        var _ = v.v1;  // Force v != null
        h.v = v;
        return h;
    }

    @Run(test = {"test4"})
    public void test4_verifier() {
        MyValueWithTwoOops v = new MyValueWithTwoOops(new Object(), new Object());
        HolderWithTwoOops h = new HolderWithTwoOops();
        HolderWithTwoOops h2 = test4(h, v);
        Asserts.assertEQ(h2, h);
        Asserts.assertEQ(h.v, v);
    }

    // Assign 2 oops in a since StoreLSpecial. Both are known to be not-null.
    // We can use a not-null post-barrier.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_TWO_OOPS_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"})
    static HolderWithTwoOops test5(HolderWithTwoOops h) {
        MyValueWithTwoOops v = new MyValueWithTwoOops(new Object(), new Object());
        h.v = v;
        return h;
    }

    @Run(test = {"test5"})
    public void test5_verifier() {
        HolderWithTwoOops h = new HolderWithTwoOops();
        HolderWithTwoOops h2 = test5(h);
        Asserts.assertEQ(h2, h);
        Asserts.assertNE(h2.v.v1, null);
        Asserts.assertNE(h2.v.v2, null);
    }

    // Assign 2 oops in a since StoreLSpecial. Only left is known to be not-null.
    // We cannot use a not-null post-barrier.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_TWO_OOPS_WITH_BARRIER_FLAG, PRE_AND_POST, "1"})
    static HolderWithTwoOops test6(HolderWithTwoOops h) {
        MyValueWithTwoOops v = new MyValueWithTwoOops(new Object(), null);
        h.v = v;
        return h;
    }

    @Run(test = {"test6"})
    public void test6_verifier() {
        HolderWithTwoOops h = new HolderWithTwoOops();
        HolderWithTwoOops h2 = test6(h);
        Asserts.assertEQ(h2, h);
        Asserts.assertNE(h2.v.v1, null);
        Asserts.assertEQ(h2.v.v2, null);
    }

    // Similar to test6, but this time, only right is not-null.
    // We cannot use a not-null post-barrier.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_TWO_OOPS_WITH_BARRIER_FLAG, PRE_AND_POST, "1"})
    static HolderWithTwoOops test7(HolderWithTwoOops h) {
        MyValueWithTwoOops v = new MyValueWithTwoOops(null, new Object());
        h.v = v;
        return h;
    }

    @Run(test = {"test7"})
    public void test7_verifier() {
        HolderWithTwoOops h = new HolderWithTwoOops();
        HolderWithTwoOops h2 = test7(h);
        Asserts.assertEQ(h2, h);
        Asserts.assertEQ(h2.v.v1, null);
        Asserts.assertNE(h2.v.v2, null);
    }

    // Assign 2 null in a since StoreLSpecial.
    // Not post barrier is needed.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_TWO_OOPS_WITH_BARRIER_FLAG, PRE_ONLY, "1"})
    static HolderWithTwoOops test8(HolderWithTwoOops h) {
        MyValueWithTwoOops v = new MyValueWithTwoOops(null, null);
        h.v = v;
        return h;
    }

    @Run(test = {"test8"})
    public void test8_verifier() {
        HolderWithTwoOops h = new HolderWithTwoOops();
        HolderWithTwoOops h2 = test8(h);
        Asserts.assertEQ(h2, h);
        Asserts.assertEQ(h2.v.v1, null);
        Asserts.assertEQ(h2.v.v2, null);
    }

    // Assign a single not-null oop at offset 0 with a StoreLSpecial. Not-null post-barrier does the trick.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_ONE_OOP_0_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"})
    static HolderWithOopsFirst test9_non_null(HolderWithOopsFirst h) {
        MyValueOopFirst v = new MyValueOopFirst(42, new Object());
        h.v = v;
        return h;
    }

    @Run(test = {"test9_non_null"})
    public void test9_non_null_verifier() {
        HolderWithOopsFirst h = new HolderWithOopsFirst();
        HolderWithOopsFirst h2 = test9_non_null(h);
        Asserts.assertEQ(h2, h);
        Asserts.assertEQ(h2.v.i, 42);
        Asserts.assertNE(h2.v.v, null);
    }

    // Assign a single null at offset 0 with a StoreLSpecial. No post-barrier needed.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_ONE_OOP_0_WITH_BARRIER_FLAG, PRE_AND_POST, "1"})
    static HolderWithOopsFirst test9_maybe_null(HolderWithOopsFirst h, Object o) {
        MyValueOopFirst v = new MyValueOopFirst(42, o);
        h.v = v;
        return h;
    }

    @Run(test = {"test9_maybe_null"})
    public void test9_maybe_null_verifier() {
        HolderWithOopsFirst h = new HolderWithOopsFirst();
        {
            HolderWithOopsFirst h2 = test9_maybe_null(h, null);
            Asserts.assertEQ(h2, h);
            Asserts.assertEQ(h2.v.i, 42);
            Asserts.assertEQ(h2.v.v, null);
        }
        {
            HolderWithOopsFirst h2 = test9_maybe_null(h, new Object());
            Asserts.assertEQ(h2, h);
            Asserts.assertEQ(h2.v.i, 42);
            Asserts.assertNE(h2.v.v, null);
        }
    }

    // Same as test9_non_null, but oop is at offset 4.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_ONE_OOP_4_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"})
    static HolderWithOopsSecond test10_non_null(HolderWithOopsSecond h) {
        MyValueOopSecond v = new MyValueOopSecond(42, new Object());
        h.v = v;
        return h;
    }

    @Run(test = {"test10_non_null"})
    public void test10_non_null_verifier() {
        HolderWithOopsSecond h = new HolderWithOopsSecond();
        HolderWithOopsSecond h2 = test10_non_null(h);
        Asserts.assertEQ(h2, h);
        Asserts.assertEQ(h2.v.i, 42);
        Asserts.assertNE(h2.v.v, null);
    }

    // Same as test9_maybe_null, but null is at offset 4.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL_ONE_OOP_4_WITH_BARRIER_FLAG, PRE_AND_POST, "1"})
    static HolderWithOopsSecond test10_maybe_null(HolderWithOopsSecond h, Object o) {
        MyValueOopSecond v = new MyValueOopSecond(42, o);
        h.v = v;
        return h;
    }

    @Run(test = {"test10_maybe_null"})
    public void test10_maybe_null_verifier() {
        HolderWithOopsSecond h = new HolderWithOopsSecond();
        {
            HolderWithOopsSecond h2 = test10_maybe_null(h, null);
            Asserts.assertEQ(h2, h);
            Asserts.assertEQ(h2.v.i, 42);
            Asserts.assertEQ(h2.v.v, null);
        }
        {
            HolderWithOopsSecond h2 = test10_maybe_null(h, new Object());
            Asserts.assertEQ(h2, h);
            Asserts.assertEQ(h2.v.i, 42);
            Asserts.assertNE(h2.v.v, null);
        }
    }

    // Assign two oops in a single StoreLSpecial, just after initialization
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL, "1"},
        failOn = {G1_STORE_L_SPECIAL_WITH_BARRIER_FLAG, ANY})
    static HolderWithTwoOops test11(MyValueWithTwoOops v) {
        var _ = v.v1;  // Force v != null
        HolderWithTwoOops h = new HolderWithTwoOops(v);
        return h;
    }

    @Run(test = {"test11"})
    public void test11_verifier() {
        MyValueWithTwoOops v = new MyValueWithTwoOops(new Object(), new Object());
        HolderWithTwoOops h = test11(v);
        Asserts.assertEQ(h.v, v);
    }

    // Variation of test11: instead of getting the value object containing the two oops, we create it here.
    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {G1_STORE_L_SPECIAL})
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {G1_STORE_L_SPECIAL, "1"},
        failOn = {G1_STORE_L_SPECIAL_WITH_BARRIER_FLAG, ANY})
    static HolderWithTwoOops test12(Object first, Object second) {
        HolderWithTwoOops h = new HolderWithTwoOops(new MyValueWithTwoOops(first, second));
        return h;
    }

    @Run(test = {"test12"})
    public void test12_verifier() {
        Object first = new Object();
        Object second = new Object();
        HolderWithTwoOops holder = test12(first, second);
        Asserts.assertEQ(first, holder.v.v1);
        Asserts.assertEQ(second, holder.v.v2);
    }
}
