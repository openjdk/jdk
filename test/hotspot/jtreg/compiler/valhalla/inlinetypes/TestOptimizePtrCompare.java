/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @bug 8377480
 * @summary [lworld] incorrect execution due to EA pointer comparison optimization at scalarized call
 * @library /test/lib /
 * @enablePreview
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run driver ${test.main.class}
 */

package compiler.valhalla.inlinetypes;
import compiler.lib.ir_framework.*;

public class TestOptimizePtrCompare {
    public static void main(String[] args) {
        TestFramework.runWithFlags("--enable-preview", "-XX:+InlineTypePassFieldsAsArgs", "-XX:+InlineTypeReturnedAsFields");
        TestFramework.runWithFlags("--enable-preview", "-XX:-InlineTypePassFieldsAsArgs", "-XX:+InlineTypeReturnedAsFields");
        TestFramework.runWithFlags("--enable-preview", "-XX:+InlineTypePassFieldsAsArgs", "-XX:-InlineTypeReturnedAsFields");
        TestFramework.runWithFlags("--enable-preview", "-XX:-InlineTypePassFieldsAsArgs", "-XX:-InlineTypeReturnedAsFields");
    }

    @Test
    @IR(failOn = {IRNode.CMP_P_OR_N})
    public static void test1() {
        Object notUsed = new Object(); // make sure EA runs
        Object arg = null;
        Object res = notInlined1(arg);
        if (res != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static Object notInlined1(Object o) {
        return o;
    }

    static value class MyValue {
        Object o;

        MyValue(Object o) {
            this.o = o;
        }
    }

    @Test
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "true"}, failOn = {IRNode.CMP_P_OR_N})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "2", IRNode.CMP_I, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "3"})
    public static void test2() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue arg = new MyValue(null);
        MyValue res = notInlined2(arg);
        if (res.o != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static MyValue notInlined2(MyValue v) {
        return v;
    }

    static value class MyValue2 {
        Object o1;
        Object o2;

        MyValue2(Object o1, Object o2) {
            this.o1 = o1;
            this.o2 = o2;
        }
    }

    static value class MyValue3 {
        MyValue v1;
        MyValue2 v2;

        MyValue3(MyValue v1, MyValue2 v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    static Object fieldO = new Object();

    @Test
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "true"}, failOn = {IRNode.CMP_P_OR_N})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "2", IRNode.CMP_I, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "3"})
    public static void test3() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue2 arg = new MyValue2(null, fieldO);
        MyValue2 res = notInlined3(arg);
        if (res.o1 != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static MyValue2 notInlined3(MyValue2 v) {
        return v;
    }

    @Test
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "2"})
    public static void test4() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue arg = new MyValue(null);
        Object res = notInlined4(arg);
        if (res == null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static Object notInlined4(MyValue v) {
        return v;
    }

    @Test
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "true"}, failOn = {IRNode.CMP_P_OR_N})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "2", IRNode.CMP_I, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "3"})
    public static void test5() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue2 arg = new MyValue2(fieldO, null);
        MyValue2 res = notInlined5(arg);
        if (res.o2 != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static MyValue2 notInlined5(MyValue2 v) {
        return v;
    }

    // TODO 8376254: C1 bails out if the type of the nullable flat field is uninitialized
    @Test(allowNotCompilable = true)
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "true"}, failOn = {IRNode.CMP_P_OR_N})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "4", IRNode.CMP_I, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "5"})
    public static void test6() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue v1 = new MyValue(fieldO);
        MyValue2 v2 = new MyValue2(fieldO, null);
        MyValue3 v3 = new MyValue3(v1, v2);
        MyValue3 res = notInlined6(v1, v2, v3);
        if (res.v2.o2 != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static MyValue3 notInlined6(MyValue v1, MyValue2 v2, MyValue3 v3) {
        return v3;
    }

    @Test
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "2", IRNode.CMP_I, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "3"})
    public static void test7() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue2 arg = new MyValue2(fieldO, null);
        MyValue2 res = notInlined7(arg, true);
        if (res.o2 != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static MyValue2 notInlined7(MyValue2 v, boolean flag) {
        if (flag) {
            return v;
        }
        return new MyValue2(fieldO, fieldO);
    }

    @Test
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "true"}, counts = {IRNode.CMP_P_OR_N, "2", IRNode.CMP_I, "1"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "true", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "2"})
    @IR(applyIfAnd = {"InlineTypePassFieldsAsArgs", "false", "InlineTypeReturnedAsFields", "false"}, counts = {IRNode.CMP_P_OR_N, "3"})
    public static void test8() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue2 arg = new MyValue2(fieldO, null);
        MyValue2 res = notInlined8(arg, true);
        if (res.o2 != null) {
            throw new RuntimeException("never taken");
        }
    }

    static MyValue2 fieldValue2 = new MyValue2(fieldO, fieldO);

    @DontInline
    static MyValue2 notInlined8(MyValue2 v, boolean flag) {
        if (flag) {
            return v;
        }
        return fieldValue2;
    }
}
