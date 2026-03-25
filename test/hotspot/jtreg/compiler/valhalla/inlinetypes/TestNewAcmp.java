/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestNewAcmp
 * @summary Verifies correctness of the acmp bytecode with value object operands.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               compiler.valhalla.inlinetypes.TestNewAcmp
 */

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.CompLevel;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.test.whitebox.WhiteBox;

interface MyInterfaceNewAcmp {

}

abstract value class MyAbstractNewAcmp implements MyInterfaceNewAcmp {

}

value class MyValue1NewAcmp extends MyAbstractNewAcmp {
    int x;

    MyValue1NewAcmp(int x) {
        this.x = x;
    }

    static MyValue1NewAcmp createDefault() {
        return new MyValue1NewAcmp(0);
    }

    static MyValue1NewAcmp setX(MyValue1NewAcmp v, int x) {
        return new MyValue1NewAcmp(x);
    }
}

value class MyValue2NewAcmp extends MyAbstractNewAcmp {
    int x;

    MyValue2NewAcmp(int x) {
        this.x = x;
    }

    static MyValue2NewAcmp createDefault() {
        return new MyValue2NewAcmp(0);
    }

    static MyValue2NewAcmp setX(MyValue2NewAcmp v, int x) {
        return new MyValue2NewAcmp(x);
    }
}

class MyObjectNewAcmp extends MyAbstractNewAcmp {
    int x;
}

// Mark test methods that return false if the argument is null
@Retention(RetentionPolicy.RUNTIME)
@interface FalseIfNull { }

// Mark test methods that return true if the argument is null
@Retention(RetentionPolicy.RUNTIME)
@interface TrueIfNull { }

public class TestNewAcmp {

    public boolean testEq01_1(Object u1, Object u2) {
        return get(u1) == u2; // new acmp
    }

    public boolean testEq01_2(Object u1, Object u2) {
        return u1 == get(u2); // new acmp
    }

    public boolean testEq01_3(Object u1, Object u2) {
        return get(u1) == get(u2); // new acmp
    }

    @FalseIfNull
    public boolean testEq01_4(Object u1, Object u2) {
        return getNotNull(u1) == u2; // new acmp without null check
    }

    @FalseIfNull
    public boolean testEq01_5(Object u1, Object u2) {
        return u1 == getNotNull(u2); // new acmp without null check
    }

    @FalseIfNull
    public boolean testEq01_6(Object u1, Object u2) {
        return getNotNull(u1) == getNotNull(u2); // new acmp without null check
    }

    public boolean testEq02_1(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return get(v1) == (Object)v2; // only true if both null
    }

    public boolean testEq02_2(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return (Object)v1 == get(v2); // only true if both null
    }

    public boolean testEq02_3(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return get(v1) == get(v2); // only true if both null
    }

    public boolean testEq03_1(MyValue1NewAcmp v, Object u) {
        return get(v) == u; // only true if both null
    }

    public boolean testEq03_2(MyValue1NewAcmp v, Object u) {
        return (Object)v == get(u); // only true if both null
    }

    public boolean testEq03_3(MyValue1NewAcmp v, Object u) {
        return get(v) == get(u); // only true if both null
    }

    public boolean testEq04_1(Object u, MyValue1NewAcmp v) {
        return get(u) == (Object)v; // only true if both null
    }

    public boolean testEq04_2(Object u, MyValue1NewAcmp v) {
        return u == get(v); // only true if both null
    }

    public boolean testEq04_3(Object u, MyValue1NewAcmp v) {
        return get(u) == get(v); // only true if both null
    }

    public boolean testEq05_1(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return get(o) == (Object)v; // only true if both null
    }

    public boolean testEq05_2(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return o == get(v); // only true if both null
    }

    public boolean testEq05_3(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return get(o) == get(v); // only true if both null
    }

    public boolean testEq06_1(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return get(v) == o; // only true if both null
    }

    public boolean testEq06_2(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return (Object)v == get(o); // only true if both null
    }

    public boolean testEq06_3(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return get(v) == get(o); // only true if both null
    }

    public boolean testEq07_1(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return getNotNull(v1) == (Object)v2; // false
    }

    public boolean testEq07_2(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return (Object)v1 == getNotNull(v2); // false
    }

    public boolean testEq07_3(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return getNotNull(v1) == getNotNull(v2); // false
    }

    public boolean testEq08_1(MyValue1NewAcmp v, Object u) {
        return getNotNull(v) == u; // false
    }

    public boolean testEq08_2(MyValue1NewAcmp v, Object u) {
        return (Object)v == getNotNull(u); // false
    }

    public boolean testEq08_3(MyValue1NewAcmp v, Object u) {
        return getNotNull(v) == getNotNull(u); // false
    }

    public boolean testEq09_1(Object u, MyValue1NewAcmp v) {
        return getNotNull(u) == (Object)v; // false
    }

    public boolean testEq09_2(Object u, MyValue1NewAcmp v) {
        return u == getNotNull(v); // false
    }

    public boolean testEq09_3(Object u, MyValue1NewAcmp v) {
        return getNotNull(u) == getNotNull(v); // false
    }

    public boolean testEq10_1(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return getNotNull(o) == (Object)v; // false
    }

    public boolean testEq10_2(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return o == getNotNull(v); // false
    }

    public boolean testEq10_3(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return getNotNull(o) == getNotNull(v); // false
    }

    public boolean testEq11_1(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return getNotNull(v) == o; // false
    }

    public boolean testEq11_2(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return (Object)v == getNotNull(o); // false
    }

    public boolean testEq11_3(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return getNotNull(v) == getNotNull(o); // false
    }

    public boolean testEq12_1(MyObjectNewAcmp o1, MyObjectNewAcmp o2) {
        return get(o1) == o2; // old acmp
    }

    public boolean testEq12_2(MyObjectNewAcmp o1, MyObjectNewAcmp o2) {
        return o1 == get(o2); // old acmp
    }

    public boolean testEq12_3(MyObjectNewAcmp o1, MyObjectNewAcmp o2) {
        return get(o1) == get(o2); // old acmp
    }

    public boolean testEq13_1(Object u, MyObjectNewAcmp o) {
        return get(u) == o; // old acmp
    }

    public boolean testEq13_2(Object u, MyObjectNewAcmp o) {
        return u == get(o); // old acmp
    }

    public boolean testEq13_3(Object u, MyObjectNewAcmp o) {
        return get(u) == get(o); // old acmp
    }

    public boolean testEq14_1(MyObjectNewAcmp o, Object u) {
        return get(o) == u; // old acmp
    }

    public boolean testEq14_2(MyObjectNewAcmp o, Object u) {
        return o == get(u); // old acmp
    }

    public boolean testEq14_3(MyObjectNewAcmp o, Object u) {
        return get(o) == get(u); // old acmp
    }

    public boolean testEq15_1(Object[] a, Object u) {
        return get(a) == u; // old acmp
    }

    public boolean testEq15_2(Object[] a, Object u) {
        return a == get(u); // old acmp
    }

    public boolean testEq15_3(Object[] a, Object u) {
        return get(a) == get(u); // old acmp
    }

    public boolean testEq16_1(Object u, Object[] a) {
        return get(u) == a; // old acmp
    }

    public boolean testEq16_2(Object u, Object[] a) {
        return u == get(a); // old acmp
    }

    public boolean testEq16_3(Object u, Object[] a) {
        return get(u) == get(a); // old acmp
    }

    public boolean testEq17_1(Object[] a, MyValue1NewAcmp v) {
        return get(a) == (Object)v; // only true if both null
    }

    public boolean testEq17_2(Object[] a, MyValue1NewAcmp v) {
        return a == get(v); // only true if both null
    }

    public boolean testEq17_3(Object[] a, MyValue1NewAcmp v) {
        return get(a) == get(v); // only true if both null
    }

    public boolean testEq18_1(MyValue1NewAcmp v, Object[] a) {
        return get(v) == a; // only true if both null
    }

    public boolean testEq18_2(MyValue1NewAcmp v, Object[] a) {
        return (Object)v == get(a); // only true if both null
    }

    public boolean testEq18_3(MyValue1NewAcmp v, Object[] a) {
        return get(v) == get(a); // only true if both null
    }

    public boolean testEq19_1(Object[] a, MyValue1NewAcmp v) {
        return getNotNull(a) == (Object)v; // false
    }

    public boolean testEq19_2(Object[] a, MyValue1NewAcmp v) {
        return a == getNotNull(v); // false
    }

    public boolean testEq19_3(Object[] a, MyValue1NewAcmp v) {
        return getNotNull(a) == getNotNull(v); // false
    }

    public boolean testEq20_1(MyValue1NewAcmp v, Object[] a) {
        return getNotNull(v) == a; // false
    }

    public boolean testEq20_2(MyValue1NewAcmp v, Object[] a) {
        return (Object)v == getNotNull(a); // false
    }

    public boolean testEq20_3(MyValue1NewAcmp v, Object[] a) {
        return getNotNull(v) == getNotNull(a); // false
    }

    public boolean testEq21_1(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return get(u1) == u2; // new acmp
    }

    public boolean testEq21_2(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return u1 == get(u2); // new acmp
    }

    public boolean testEq21_3(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return get(u1) == get(u2); // new acmp
    }

    @FalseIfNull
    public boolean testEq21_4(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return getNotNull(u1) == u2; // new acmp without null check
    }

    @FalseIfNull
    public boolean testEq21_5(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return u1 == getNotNull(u2); // new acmp without null check
    }

    @FalseIfNull
    public boolean testEq21_6(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return getNotNull(u1) == getNotNull(u2); // new acmp without null check
    }

    public boolean testEq21_7(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return get(u1) == u2; // new acmp
    }

    public boolean testEq21_8(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return u1 == get(u2); // new acmp
    }

    public boolean testEq21_9(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return get(u1) == get(u2); // new acmp
    }

    @FalseIfNull
    public boolean testEq21_10(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return getNotNull(u1) == u2; // new acmp without null check
    }

    @FalseIfNull
    public boolean testEq21_11(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return u1 == getNotNull(u2); // new acmp without null check
    }

    @FalseIfNull
    public boolean testEq21_12(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return getNotNull(u1) == getNotNull(u2); // new acmp without null check
    }

    public boolean testEq22_1(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return get(v) == u; // only true if both null
    }

    public boolean testEq22_2(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return (Object)v == get(u); // only true if both null
    }

    public boolean testEq22_3(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return get(v) == get(u); // only true if both null
    }

    public boolean testEq22_4(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return get(v) == u; // only true if both null
    }

    public boolean testEq22_5(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return (Object)v == get(u); // only true if both null
    }

    public boolean testEq22_6(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return get(v) == get(u); // only true if both null
    }

    public boolean testEq23_1(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return get(u) == (Object)v; // only true if both null
    }

    public boolean testEq23_2(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return u == get(v); // only true if both null
    }

    public boolean testEq23_3(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return get(u) == get(v); // only true if both null
    }

    public boolean testEq23_4(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return get(u) == (Object)v; // only true if both null
    }

    public boolean testEq23_5(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return u == get(v); // only true if both null
    }

    public boolean testEq23_6(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return get(u) == get(v); // only true if both null
    }

    public boolean testEq24_1(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return getNotNull(v) == u; // false
    }

    public boolean testEq24_2(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return (Object)v == getNotNull(u); // false
    }

    public boolean testEq24_3(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return getNotNull(v) == getNotNull(u); // false
    }

    public boolean testEq24_4(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return getNotNull(v) == u; // false
    }

    public boolean testEq24_5(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return (Object)v == getNotNull(u); // false
    }

    public boolean testEq24_6(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return getNotNull(v) == getNotNull(u); // false
    }

    public boolean testEq25_1(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) == (Object)v; // false
    }

    public boolean testEq25_2(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return u == getNotNull(v); // false
    }

    public boolean testEq25_3(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) == getNotNull(v); // false
    }

    public boolean testEq25_4(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) == (Object)v; // false
    }

    public boolean testEq25_5(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return u == getNotNull(v); // false
    }

    public boolean testEq25_6(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) == getNotNull(v); // false
    }

    public boolean testEq26_1(MyInterfaceNewAcmp u, MyObjectNewAcmp o) {
        return get(u) == o; // old acmp
    }

    public boolean testEq26_2(MyInterfaceNewAcmp u, MyObjectNewAcmp o) {
        return u == get(o); // old acmp
    }

    public boolean testEq26_3(MyInterfaceNewAcmp u, MyObjectNewAcmp o) {
        return get(u) == get(o); // old acmp
    }

    public boolean testEq26_4(MyAbstractNewAcmp u, MyObjectNewAcmp o) {
        return get(u) == o; // old acmp
    }

    public boolean testEq26_5(MyAbstractNewAcmp u, MyObjectNewAcmp o) {
        return u == get(o); // old acmp
    }

    public boolean testEq26_6(MyAbstractNewAcmp u, MyObjectNewAcmp o) {
        return get(u) == get(o); // old acmp
    }

    public boolean testEq27_1(MyObjectNewAcmp o, MyInterfaceNewAcmp u) {
        return get(o) == u; // old acmp
    }

    public boolean testEq27_2(MyObjectNewAcmp o, MyInterfaceNewAcmp u) {
        return o == get(u); // old acmp
    }

    public boolean testEq27_3(MyObjectNewAcmp o, MyInterfaceNewAcmp u) {
        return get(o) == get(u); // old acmp
    }

    public boolean testEq27_4(MyObjectNewAcmp o, MyAbstractNewAcmp u) {
        return get(o) == u; // old acmp
    }

    public boolean testEq27_5(MyObjectNewAcmp o, MyAbstractNewAcmp u) {
        return o == get(u); // old acmp
    }

    public boolean testEq27_6(MyObjectNewAcmp o, MyAbstractNewAcmp u) {
        return get(o) == get(u); // old acmp
    }

    public boolean testEq28_1(MyInterfaceNewAcmp[] a, MyInterfaceNewAcmp u) {
        return get(a) == u; // old acmp
    }

    public boolean testEq28_2(MyInterfaceNewAcmp[] a, MyInterfaceNewAcmp u) {
        return a == get(u); // old acmp
    }

    public boolean testEq28_3(MyInterfaceNewAcmp[] a, MyInterfaceNewAcmp u) {
        return get(a) == get(u); // old acmp
    }

    public boolean testEq28_4(MyAbstractNewAcmp[] a, MyAbstractNewAcmp u) {
        return get(a) == u; // old acmp
    }

    public boolean testEq28_5(MyAbstractNewAcmp[] a, MyAbstractNewAcmp u) {
        return a == get(u); // old acmp
    }

    public boolean testEq28_6(MyAbstractNewAcmp[] a, MyAbstractNewAcmp u) {
        return get(a) == get(u); // old acmp
    }

    public boolean testEq29_1(MyInterfaceNewAcmp u, MyInterfaceNewAcmp[] a) {
        return get(u) == a; // old acmp
    }

    public boolean testEq29_2(MyInterfaceNewAcmp u, MyInterfaceNewAcmp[] a) {
        return u == get(a); // old acmp
    }

    public boolean testEq29_3(MyInterfaceNewAcmp u, MyInterfaceNewAcmp[] a) {
        return get(u) == get(a); // old acmp
    }

    public boolean testEq29_4(MyAbstractNewAcmp u, MyAbstractNewAcmp[] a) {
        return get(u) == a; // old acmp
    }

    public boolean testEq29_5(MyAbstractNewAcmp u, MyAbstractNewAcmp[] a) {
        return u == get(a); // old acmp
    }

    public boolean testEq29_6(MyAbstractNewAcmp u, MyAbstractNewAcmp[] a) {
        return get(u) == get(a); // old acmp
    }

    public boolean testEq30_1(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) == (Object)v; // only true if both null
    }

    public boolean testEq30_2(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return a == get(v); // only true if both null
    }

    public boolean testEq30_3(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) == get(v); // only true if both null
    }

    public boolean testEq30_4(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) == (Object)v; // only true if both null
    }

    public boolean testEq30_5(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return a == get(v); // only true if both null
    }

    public boolean testEq30_6(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) == get(v); // only true if both null
    }

    public boolean testEq31_1(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return get(v) == a; // only true if both null
    }

    public boolean testEq31_2(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return (Object)v == get(a); // only true if both null
    }

    public boolean testEq31_3(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return get(v) == get(a); // only true if both null
    }

    public boolean testEq31_4(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return get(v) == a; // only true if both null
    }

    public boolean testEq31_5(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return (Object)v == get(a); // only true if both null
    }

    public boolean testEq31_6(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return get(v) == get(a); // only true if both null
    }

    public boolean testEq32_1(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) == (Object)v; // false
    }

    public boolean testEq32_2(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return a == getNotNull(v); // false
    }

    public boolean testEq32_3(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) == getNotNull(v); // false
    }

    public boolean testEq32_4(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) == (Object)v; // false
    }

    public boolean testEq32_5(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return a == getNotNull(v); // false
    }

    public boolean testEq32_6(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) == getNotNull(v); // false
    }

    public boolean testEq33_1(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return getNotNull(v) == a; // false
    }

    public boolean testEq33_2(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return (Object)v == getNotNull(a); // false
    }

    public boolean testEq33_3(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return getNotNull(v) == getNotNull(a); // false
    }

    public boolean testEq33_4(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return getNotNull(v) == a; // false
    }

    public boolean testEq33_5(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return (Object)v == getNotNull(a); // false
    }

    public boolean testEq33_6(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return getNotNull(v) == getNotNull(a); // false
    }


    // Null tests

    public boolean testNull01_1(MyValue1NewAcmp v) {
        return (Object)v == null; // old acmp
    }

    public boolean testNull01_2(MyValue1NewAcmp v) {
        return get(v) == null; // old acmp
    }

    public boolean testNull01_3(MyValue1NewAcmp v) {
        return (Object)v == get((Object)null); // old acmp
    }

    public boolean testNull01_4(MyValue1NewAcmp v) {
        return get(v) == get((Object)null); // old acmp
    }

    public boolean testNull02_1(MyValue1NewAcmp v) {
        return null == (Object)v; // old acmp
    }

    public boolean testNull02_2(MyValue1NewAcmp v) {
        return get((Object)null) == (Object)v; // old acmp
    }

    public boolean testNull02_3(MyValue1NewAcmp v) {
        return null == get(v); // old acmp
    }

    public boolean testNull02_4(MyValue1NewAcmp v) {
        return get((Object)null) == get(v); // old acmp
    }

    public boolean testNull03_1(Object u) {
        return u == null; // old acmp
    }

    public boolean testNull03_2(Object u) {
        return get(u) == null; // old acmp
    }

    public boolean testNull03_3(Object u) {
        return u == get((Object)null); // old acmp
    }

    public boolean testNull03_4(Object u) {
        return get(u) == get((Object)null); // old acmp
    }

    public boolean testNull04_1(Object u) {
        return null == u; // old acmp
    }

    public boolean testNull04_2(Object u) {
        return get((Object)null) == u; // old acmp
    }

    public boolean testNull04_3(Object u) {
        return null == get(u); // old acmp
    }

    public boolean testNull04_4(Object u) {
        return get((Object)null) == get(u); // old acmp
    }

    public boolean testNull05_1(MyObjectNewAcmp o) {
        return o == null; // old acmp
    }

    public boolean testNull05_2(MyObjectNewAcmp o) {
        return get(o) == null; // old acmp
    }

    public boolean testNull05_3(MyObjectNewAcmp o) {
        return o == get((Object)null); // old acmp
    }

    public boolean testNull05_4(MyObjectNewAcmp o) {
        return get(o) == get((Object)null); // old acmp
    }

    public boolean testNull06_1(MyObjectNewAcmp o) {
        return null == o; // old acmp
    }

    public boolean testNull06_2(MyObjectNewAcmp o) {
        return get((Object)null) == o; // old acmp
    }

    public boolean testNull06_3(MyObjectNewAcmp o) {
        return null == get(o); // old acmp
    }

    public boolean testNull06_4(MyObjectNewAcmp o) {
        return get((Object)null) == get(o); // old acmp
    }

    public boolean testNull07_1(MyInterfaceNewAcmp u) {
        return u == null; // old acmp
    }

    public boolean testNull07_2(MyInterfaceNewAcmp u) {
        return get(u) == null; // old acmp
    }

    public boolean testNull07_3(MyInterfaceNewAcmp u) {
        return u == get((Object)null); // old acmp
    }

    public boolean testNull07_4(MyInterfaceNewAcmp u) {
        return get(u) == get((Object)null); // old acmp
    }

    public boolean testNull07_5(MyAbstractNewAcmp u) {
        return u == null; // old acmp
    }

    public boolean testNull07_6(MyAbstractNewAcmp u) {
        return get(u) == null; // old acmp
    }

    public boolean testNull07_7(MyAbstractNewAcmp u) {
        return u == get((Object)null); // old acmp
    }

    public boolean testNull07_8(MyAbstractNewAcmp u) {
        return get(u) == get((Object)null); // old acmp
    }

    public boolean testNull08_1(MyInterfaceNewAcmp u) {
        return null == u; // old acmp
    }

    public boolean testNull08_2(MyInterfaceNewAcmp u) {
        return get((Object)null) == u; // old acmp
    }

    public boolean testNull08_3(MyInterfaceNewAcmp u) {
        return null == get(u); // old acmp
    }

    public boolean testNull08_4(MyInterfaceNewAcmp u) {
        return get((Object)null) == get(u); // old acmp
    }

    public boolean testNull08_5(MyAbstractNewAcmp u) {
        return null == u; // old acmp
    }

    public boolean testNull08_6(MyAbstractNewAcmp u) {
        return get((Object)null) == u; // old acmp
    }

    public boolean testNull08_7(MyAbstractNewAcmp u) {
        return null == get(u); // old acmp
    }

    public boolean testNull08_8(MyAbstractNewAcmp u) {
        return get((Object)null) == get(u); // old acmp
    }

    // Same tests as above but negated

    public boolean testNotEq01_1(Object u1, Object u2) {
        return get(u1) != u2; // new acmp
    }

    public boolean testNotEq01_2(Object u1, Object u2) {
        return u1 != get(u2); // new acmp
    }

    public boolean testNotEq01_3(Object u1, Object u2) {
        return get(u1) != get(u2); // new acmp
    }

    @TrueIfNull
    public boolean testNotEq01_4(Object u1, Object u2) {
        return getNotNull(u1) != u2; // new acmp without null check
    }

    @TrueIfNull
    public boolean testNotEq01_5(Object u1, Object u2) {
        return u1 != getNotNull(u2); // new acmp without null check
    }

    @TrueIfNull
    public boolean testNotEq01_6(Object u1, Object u2) {
        return getNotNull(u1) != getNotNull(u2); // new acmp without null check
    }

    public boolean testNotEq02_1(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return get(v1) != (Object)v2; // only false if both null
    }

    public boolean testNotEq02_2(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return (Object)v1 != get(v2); // only false if both null
    }

    public boolean testNotEq02_3(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return get(v1) != get(v2); // only false if both null
    }

    public boolean testNotEq03_1(MyValue1NewAcmp v, Object u) {
        return get(v) != u; // only false if both null
    }

    public boolean testNotEq03_2(MyValue1NewAcmp v, Object u) {
        return (Object)v != get(u); // only false if both null
    }

    public boolean testNotEq03_3(MyValue1NewAcmp v, Object u) {
        return get(v) != get(u); // only false if both null
    }

    public boolean testNotEq04_1(Object u, MyValue1NewAcmp v) {
        return get(u) != (Object)v; // only false if both null
    }

    public boolean testNotEq04_2(Object u, MyValue1NewAcmp v) {
        return u != get(v); // only false if both null
    }

    public boolean testNotEq04_3(Object u, MyValue1NewAcmp v) {
        return get(u) != get(v); // only false if both null
    }

    public boolean testNotEq05_1(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return get(o) != (Object)v; // only false if both null
    }

    public boolean testNotEq05_2(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return o != get(v); // only false if both null
    }

    public boolean testNotEq05_3(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return get(o) != get(v); // only false if both null
    }

    public boolean testNotEq06_1(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return get(v) != o; // only false if both null
    }

    public boolean testNotEq06_2(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return (Object)v != get(o); // only false if both null
    }

    public boolean testNotEq06_3(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return get(v) != get(o); // only false if both null
    }

    public boolean testNotEq07_1(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return getNotNull(v1) != (Object)v2; // true
    }

    public boolean testNotEq07_2(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return (Object)v1 != getNotNull(v2); // true
    }

    public boolean testNotEq07_3(MyValue1NewAcmp v1, MyValue1NewAcmp v2) {
        return getNotNull(v1) != getNotNull(v2); // true
    }

    public boolean testNotEq08_1(MyValue1NewAcmp v, Object u) {
        return getNotNull(v) != u; // true
    }

    public boolean testNotEq08_2(MyValue1NewAcmp v, Object u) {
        return (Object)v != getNotNull(u); // true
    }

    public boolean testNotEq08_3(MyValue1NewAcmp v, Object u) {
        return getNotNull(v) != getNotNull(u); // true
    }

    public boolean testNotEq09_1(Object u, MyValue1NewAcmp v) {
        return getNotNull(u) != (Object)v; // true
    }

    public boolean testNotEq09_2(Object u, MyValue1NewAcmp v) {
        return u != getNotNull(v); // true
    }

    public boolean testNotEq09_3(Object u, MyValue1NewAcmp v) {
        return getNotNull(u) != getNotNull(v); // true
    }

    public boolean testNotEq10_1(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return getNotNull(o) != (Object)v; // true
    }

    public boolean testNotEq10_2(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return o != getNotNull(v); // true
    }

    public boolean testNotEq10_3(MyObjectNewAcmp o, MyValue1NewAcmp v) {
        return getNotNull(o) != getNotNull(v); // true
    }

    public boolean testNotEq11_1(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return getNotNull(v) != o; // true
    }

    public boolean testNotEq11_2(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return (Object)v != getNotNull(o); // true
    }

    public boolean testNotEq11_3(MyValue1NewAcmp v, MyObjectNewAcmp o) {
        return getNotNull(v) != getNotNull(o); // true
    }

    public boolean testNotEq12_1(MyObjectNewAcmp o1, MyObjectNewAcmp o2) {
        return get(o1) != o2; // old acmp
    }

    public boolean testNotEq12_2(MyObjectNewAcmp o1, MyObjectNewAcmp o2) {
        return o1 != get(o2); // old acmp
    }

    public boolean testNotEq12_3(MyObjectNewAcmp o1, MyObjectNewAcmp o2) {
        return get(o1) != get(o2); // old acmp
    }

    public boolean testNotEq13_1(Object u, MyObjectNewAcmp o) {
        return get(u) != o; // old acmp
    }

    public boolean testNotEq13_2(Object u, MyObjectNewAcmp o) {
        return u != get(o); // old acmp
    }

    public boolean testNotEq13_3(Object u, MyObjectNewAcmp o) {
        return get(u) != get(o); // old acmp
    }

    public boolean testNotEq14_1(MyObjectNewAcmp o, Object u) {
        return get(o) != u; // old acmp
    }

    public boolean testNotEq14_2(MyObjectNewAcmp o, Object u) {
        return o != get(u); // old acmp
    }

    public boolean testNotEq14_3(MyObjectNewAcmp o, Object u) {
        return get(o) != get(u); // old acmp
    }

    public boolean testNotEq15_1(Object[] a, Object u) {
        return get(a) != u; // old acmp
    }

    public boolean testNotEq15_2(Object[] a, Object u) {
        return a != get(u); // old acmp
    }

    public boolean testNotEq15_3(Object[] a, Object u) {
        return get(a) != get(u); // old acmp
    }

    public boolean testNotEq16_1(Object u, Object[] a) {
        return get(u) != a; // old acmp
    }

    public boolean testNotEq16_2(Object u, Object[] a) {
        return u != get(a); // old acmp
    }

    public boolean testNotEq16_3(Object u, Object[] a) {
        return get(u) != get(a); // old acmp
    }

    public boolean testNotEq17_1(Object[] a, MyValue1NewAcmp v) {
        return get(a) != (Object)v; // only false if both null
    }

    public boolean testNotEq17_2(Object[] a, MyValue1NewAcmp v) {
        return a != get(v); // only false if both null
    }

    public boolean testNotEq17_3(Object[] a, MyValue1NewAcmp v) {
        return get(a) != get(v); // only false if both null
    }

    public boolean testNotEq18_1(MyValue1NewAcmp v, Object[] a) {
        return get(v) != a; // only false if both null
    }

    public boolean testNotEq18_2(MyValue1NewAcmp v, Object[] a) {
        return (Object)v != get(a); // only false if both null
    }

    public boolean testNotEq18_3(MyValue1NewAcmp v, Object[] a) {
        return get(v) != get(a); // only false if both null
    }

    public boolean testNotEq19_1(Object[] a, MyValue1NewAcmp v) {
        return getNotNull(a) != (Object)v; // true
    }

    public boolean testNotEq19_2(Object[] a, MyValue1NewAcmp v) {
        return a != getNotNull(v); // true
    }

    public boolean testNotEq19_3(Object[] a, MyValue1NewAcmp v) {
        return getNotNull(a) != getNotNull(v); // true
    }

    public boolean testNotEq20_1(MyValue1NewAcmp v, Object[] a) {
        return getNotNull(v) != a; // true
    }

    public boolean testNotEq20_2(MyValue1NewAcmp v, Object[] a) {
        return (Object)v != getNotNull(a); // true
    }

    public boolean testNotEq20_3(MyValue1NewAcmp v, Object[] a) {
        return getNotNull(v) != getNotNull(a); // true
    }

    public boolean testNotEq21_1(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return get(u1) != u2; // new acmp
    }

    public boolean testNotEq21_2(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return u1 != get(u2); // new acmp
    }

    public boolean testNotEq21_3(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return get(u1) != get(u2); // new acmp
    }

    @TrueIfNull
    public boolean testNotEq21_4(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return getNotNull(u1) != u2; // new acmp without null check
    }

    @TrueIfNull
    public boolean testNotEq21_5(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return u1 != getNotNull(u2); // new acmp without null check
    }

    @TrueIfNull
    public boolean testNotEq21_6(MyInterfaceNewAcmp u1, MyInterfaceNewAcmp u2) {
        return getNotNull(u1) != getNotNull(u2); // new acmp without null check
    }

    public boolean testNotEq21_7(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return get(u1) != u2; // new acmp
    }

    public boolean testNotEq21_8(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return u1 != get(u2); // new acmp
    }

    public boolean testNotEq21_9(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return get(u1) != get(u2); // new acmp
    }

    @TrueIfNull
    public boolean testNotEq21_10(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return getNotNull(u1) != u2; // new acmp without null check
    }

    @TrueIfNull
    public boolean testNotEq21_11(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return u1 != getNotNull(u2); // new acmp without null check
    }

    @TrueIfNull
    public boolean testNotEq21_12(MyAbstractNewAcmp u1, MyAbstractNewAcmp u2) {
        return getNotNull(u1) != getNotNull(u2); // new acmp without null check
    }

    public boolean testNotEq22_1(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return get(v) != u; // only false if both null
    }

    public boolean testNotEq22_2(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return (Object)v != get(u); // only false if both null
    }

    public boolean testNotEq22_3(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return get(v) != get(u); // only false if both null
    }

    public boolean testNotEq22_4(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return get(v) != u; // only false if both null
    }

    public boolean testNotEq22_5(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return (Object)v != get(u); // only false if both null
    }

    public boolean testNotEq22_6(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return get(v) != get(u); // only false if both null
    }

    public boolean testNotEq23_1(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return get(u) != (Object)v; // only false if both null
    }

    public boolean testNotEq23_2(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return u != get(v); // only false if both null
    }

    public boolean testNotEq23_3(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return get(u) != get(v); // only false if both null
    }

    public boolean testNotEq23_4(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return get(u) != (Object)v; // only false if both null
    }

    public boolean testNotEq23_5(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return u != get(v); // only false if both null
    }

    public boolean testNotEq23_6(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return get(u) != get(v); // only false if both null
    }

    public boolean testNotEq24_1(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return getNotNull(v) != u; // true
    }

    public boolean testNotEq24_2(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return (Object)v != getNotNull(u); // true
    }

    public boolean testNotEq24_3(MyValue1NewAcmp v, MyInterfaceNewAcmp u) {
        return getNotNull(v) != getNotNull(u); // true
    }

    public boolean testNotEq24_4(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return getNotNull(v) != u; // true
    }

    public boolean testNotEq24_5(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return (Object)v != getNotNull(u); // true
    }

    public boolean testNotEq24_6(MyValue1NewAcmp v, MyAbstractNewAcmp u) {
        return getNotNull(v) != getNotNull(u); // true
    }

    public boolean testNotEq25_1(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) != (Object)v; // true
    }

    public boolean testNotEq25_2(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return u != getNotNull(v); // true
    }

    public boolean testNotEq25_3(MyInterfaceNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) != getNotNull(v); // true
    }

    public boolean testNotEq25_4(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) != (Object)v; // true
    }

    public boolean testNotEq25_5(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return u != getNotNull(v); // true
    }

    public boolean testNotEq25_6(MyAbstractNewAcmp u, MyValue1NewAcmp v) {
        return getNotNull(u) != getNotNull(v); // true
    }

    public boolean testNotEq26_1(MyInterfaceNewAcmp u, MyObjectNewAcmp o) {
        return get(u) != o; // old acmp
    }

    public boolean testNotEq26_2(MyInterfaceNewAcmp u, MyObjectNewAcmp o) {
        return u != get(o); // old acmp
    }

    public boolean testNotEq26_3(MyInterfaceNewAcmp u, MyObjectNewAcmp o) {
        return get(u) != get(o); // old acmp
    }

    public boolean testNotEq26_4(MyAbstractNewAcmp u, MyObjectNewAcmp o) {
        return get(u) != o; // old acmp
    }

    public boolean testNotEq26_5(MyAbstractNewAcmp u, MyObjectNewAcmp o) {
        return u != get(o); // old acmp
    }

    public boolean testNotEq26_6(MyAbstractNewAcmp u, MyObjectNewAcmp o) {
        return get(u) != get(o); // old acmp
    }

    public boolean testNotEq27_1(MyObjectNewAcmp o, MyInterfaceNewAcmp u) {
        return get(o) != u; // old acmp
    }

    public boolean testNotEq27_2(MyObjectNewAcmp o, MyInterfaceNewAcmp u) {
        return o != get(u); // old acmp
    }

    public boolean testNotEq27_3(MyObjectNewAcmp o, MyInterfaceNewAcmp u) {
        return get(o) != get(u); // old acmp
    }

    public boolean testNotEq27_4(MyObjectNewAcmp o, MyAbstractNewAcmp u) {
        return get(o) != u; // old acmp
    }

    public boolean testNotEq27_5(MyObjectNewAcmp o, MyAbstractNewAcmp u) {
        return o != get(u); // old acmp
    }

    public boolean testNotEq27_6(MyObjectNewAcmp o, MyAbstractNewAcmp u) {
        return get(o) != get(u); // old acmp
    }

    public boolean testNotEq28_1(MyInterfaceNewAcmp[] a, MyInterfaceNewAcmp u) {
        return get(a) != u; // old acmp
    }

    public boolean testNotEq28_2(MyInterfaceNewAcmp[] a, MyInterfaceNewAcmp u) {
        return a != get(u); // old acmp
    }

    public boolean testNotEq28_3(MyInterfaceNewAcmp[] a, MyInterfaceNewAcmp u) {
        return get(a) != get(u); // old acmp
    }

    public boolean testNotEq28_4(MyAbstractNewAcmp[] a, MyAbstractNewAcmp u) {
        return get(a) != u; // old acmp
    }

    public boolean testNotEq28_5(MyAbstractNewAcmp[] a, MyAbstractNewAcmp u) {
        return a != get(u); // old acmp
    }

    public boolean testNotEq28_6(MyAbstractNewAcmp[] a, MyAbstractNewAcmp u) {
        return get(a) != get(u); // old acmp
    }

    public boolean testNotEq29_1(MyInterfaceNewAcmp u, MyInterfaceNewAcmp[] a) {
        return get(u) != a; // old acmp
    }

    public boolean testNotEq29_2(MyInterfaceNewAcmp u, MyInterfaceNewAcmp[] a) {
        return u != get(a); // old acmp
    }

    public boolean testNotEq29_3(MyInterfaceNewAcmp u, MyInterfaceNewAcmp[] a) {
        return get(u) != get(a); // old acmp
    }

    public boolean testNotEq29_4(MyAbstractNewAcmp u, MyAbstractNewAcmp[] a) {
        return get(u) != a; // old acmp
    }

    public boolean testNotEq29_5(MyAbstractNewAcmp u, MyAbstractNewAcmp[] a) {
        return u != get(a); // old acmp
    }

    public boolean testNotEq29_6(MyAbstractNewAcmp u, MyAbstractNewAcmp[] a) {
        return get(u) != get(a); // old acmp
    }

    public boolean testNotEq30_1(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) != (Object)v; // only false if both null
    }

    public boolean testNotEq30_2(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return a != get(v); // only false if both null
    }

    public boolean testNotEq30_3(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) != get(v); // only false if both null
    }

    public boolean testNotEq30_4(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) != (Object)v; // only false if both null
    }

    public boolean testNotEq30_5(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return a != get(v); // only false if both null
    }

    public boolean testNotEq30_6(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return get(a) != get(v); // only false if both null
    }

    public boolean testNotEq31_1(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return get(v) != a; // only false if both null
    }

    public boolean testNotEq31_2(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return (Object)v != get(a); // only false if both null
    }

    public boolean testNotEq31_3(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return get(v) != get(a); // only false if both null
    }

    public boolean testNotEq31_4(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return get(v) != a; // only false if both null
    }

    public boolean testNotEq31_5(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return (Object)v != get(a); // only false if both null
    }

    public boolean testNotEq31_6(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return get(v) != get(a); // only false if both null
    }

    public boolean testNotEq32_1(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) != (Object)v; // true
    }

    public boolean testNotEq32_2(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return a != getNotNull(v); // true
    }

    public boolean testNotEq32_3(MyInterfaceNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) != getNotNull(v); // true
    }

    public boolean testNotEq32_4(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) != (Object)v; // true
    }

    public boolean testNotEq32_5(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return a != getNotNull(v); // true
    }

    public boolean testNotEq32_6(MyAbstractNewAcmp[] a, MyValue1NewAcmp v) {
        return getNotNull(a) != getNotNull(v); // true
    }

    public boolean testNotEq33_1(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return getNotNull(v) != a; // true
    }

    public boolean testNotEq33_2(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return (Object)v != getNotNull(a); // true
    }

    public boolean testNotEq33_3(MyValue1NewAcmp v, MyInterfaceNewAcmp[] a) {
        return getNotNull(v) != getNotNull(a); // true
    }

    public boolean testNotEq33_4(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return getNotNull(v) != a; // true
    }

    public boolean testNotEq33_5(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return (Object)v != getNotNull(a); // true
    }

    public boolean testNotEq33_6(MyValue1NewAcmp v, MyAbstractNewAcmp[] a) {
        return getNotNull(v) != getNotNull(a); // true
    }

    // Null tests

    public boolean testNotNull01_1(MyValue1NewAcmp v) {
        return (Object)v != null; // old acmp
    }

    public boolean testNotNull01_2(MyValue1NewAcmp v) {
        return get(v) != null; // old acmp
    }

    public boolean testNotNull01_3(MyValue1NewAcmp v) {
        return (Object)v != get((Object)null); // old acmp
    }

    public boolean testNotNull01_4(MyValue1NewAcmp v) {
        return get(v) != get((Object)null); // old acmp
    }

    public boolean testNotNull02_1(MyValue1NewAcmp v) {
        return null != (Object)v; // old acmp
    }

    public boolean testNotNull02_2(MyValue1NewAcmp v) {
        return get((Object)null) != (Object)v; // old acmp
    }

    public boolean testNotNull02_3(MyValue1NewAcmp v) {
        return null != get(v); // old acmp
    }

    public boolean testNotNull02_4(MyValue1NewAcmp v) {
        return get((Object)null) != get(v); // old acmp
    }

    public boolean testNotNull03_1(Object u) {
        return u != null; // old acmp
    }

    public boolean testNotNull03_2(Object u) {
        return get(u) != null; // old acmp
    }

    public boolean testNotNull03_3(Object u) {
        return u != get((Object)null); // old acmp
    }

    public boolean testNotNull03_4(Object u) {
        return get(u) != get((Object)null); // old acmp
    }

    public boolean testNotNull04_1(Object u) {
        return null != u; // old acmp
    }

    public boolean testNotNull04_2(Object u) {
        return get((Object)null) != u; // old acmp
    }

    public boolean testNotNull04_3(Object u) {
        return null != get(u); // old acmp
    }

    public boolean testNotNull04_4(Object u) {
        return get((Object)null) != get(u); // old acmp
    }

    public boolean testNotNull05_1(MyObjectNewAcmp o) {
        return o != null; // old acmp
    }

    public boolean testNotNull05_2(MyObjectNewAcmp o) {
        return get(o) != null; // old acmp
    }

    public boolean testNotNull05_3(MyObjectNewAcmp o) {
        return o != get((Object)null); // old acmp
    }

    public boolean testNotNull05_4(MyObjectNewAcmp o) {
        return get(o) != get((Object)null); // old acmp
    }

    public boolean testNotNull06_1(MyObjectNewAcmp o) {
        return null != o; // old acmp
    }

    public boolean testNotNull06_2(MyObjectNewAcmp o) {
        return get((Object)null) != o; // old acmp
    }

    public boolean testNotNull06_3(MyObjectNewAcmp o) {
        return null != get(o); // old acmp
    }

    public boolean testNotNull06_4(MyObjectNewAcmp o) {
        return get((Object)null) != get(o); // old acmp
    }

    public boolean testNotNull07_1(MyInterfaceNewAcmp u) {
        return u != null; // old acmp
    }

    public boolean testNotNull07_2(MyInterfaceNewAcmp u) {
        return get(u) != null; // old acmp
    }

    public boolean testNotNull07_3(MyInterfaceNewAcmp u) {
        return u != get((Object)null); // old acmp
    }

    public boolean testNotNull07_4(MyInterfaceNewAcmp u) {
        return get(u) != get((Object)null); // old acmp
    }

    public boolean testNotNull07_5(MyAbstractNewAcmp u) {
        return u != null; // old acmp
    }

    public boolean testNotNull07_6(MyAbstractNewAcmp u) {
        return get(u) != null; // old acmp
    }

    public boolean testNotNull07_7(MyAbstractNewAcmp u) {
        return u != get((Object)null); // old acmp
    }

    public boolean testNotNull07_8(MyAbstractNewAcmp u) {
        return get(u) != get((Object)null); // old acmp
    }

    public boolean testNotNull08_1(MyInterfaceNewAcmp u) {
        return null != u; // old acmp
    }

    public boolean testNotNull08_2(MyInterfaceNewAcmp u) {
        return get((Object)null) != u; // old acmp
    }

    public boolean testNotNull08_3(MyInterfaceNewAcmp u) {
        return null != get(u); // old acmp
    }

    public boolean testNotNull08_4(MyInterfaceNewAcmp u) {
        return get((Object)null) != get(u); // old acmp
    }

    public boolean testNotNull08_5(MyAbstractNewAcmp u) {
        return null != u; // old acmp
    }

    public boolean testNotNull08_6(MyAbstractNewAcmp u) {
        return get((Object)null) != u; // old acmp
    }

    public boolean testNotNull08_7(MyAbstractNewAcmp u) {
        return null != get(u); // old acmp
    }

    public boolean testNotNull08_8(MyAbstractNewAcmp u) {
        return get((Object)null) != get(u); // old acmp
    }

    // The following methods are used with -XX:+AlwaysIncrementalInline to hide exact types during parsing

    public Object get(Object u) {
        return u;
    }

    public Object getNotNull(Object u) {
        return (u != null) ? u : new Object();
    }

    public Object get(MyValue1NewAcmp v) {
        return v;
    }

    public Object getNotNull(MyValue1NewAcmp v) {
        return ((Object)v != null) ? v : MyValue1NewAcmp.createDefault();
    }

    public Object get(MyObjectNewAcmp o) {
        return o;
    }

    public Object getNotNull(MyObjectNewAcmp o) {
        return (o != null) ? o : MyValue1NewAcmp.createDefault();
    }

    public Object get(Object[] a) {
        return a;
    }

    public Object getNotNull(Object[] a) {
        return (a != null) ? a : new Object[1];
    }

    public boolean trueIfNull(Method m) {
        return m.isAnnotationPresent(TrueIfNull.class);
    }

    public boolean falseIfNull(Method m) {
        return m.isAnnotationPresent(FalseIfNull.class);
    }

    public boolean isNegated(Method m) {
        return m.getName().startsWith("testNot");
    }

    // Tests with profiling
    public boolean cmpAlwaysEqual1(Object a, Object b) {
        return a == b;
    }

    public boolean cmpAlwaysEqual2(Object a, Object b) {
        return a != b;
    }

    public boolean cmpAlwaysEqual3(Object a) {
        return a == a;
    }

    public boolean cmpAlwaysEqual4(Object a) {
        return a != a;
    }

    public boolean cmpAlwaysUnEqual1(Object a, Object b) {
        return a == b;
    }

    public boolean cmpAlwaysUnEqual2(Object a, Object b) {
        return a != b;
    }

    public boolean cmpAlwaysUnEqual3(Object a) {
        return a == a;
    }

    public boolean cmpAlwaysUnEqual4(Object a) {
        return a != a;
    }

    public boolean cmpSometimesEqual1(Object a) {
        return a == a;
    }

    public boolean cmpSometimesEqual2(Object a) {
        return a != a;
    }

    static int get_full_opt_level() {
        int n = (int)TieredStopAtLevel;
        if (n >= 4) {
            n = 4;
        }
        return n;
    }
    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    protected static final long TieredStopAtLevel = (Long)WHITE_BOX.getVMFlag("TieredStopAtLevel");
    protected static final int COMP_LEVEL_FULL_OPTIMIZATION = get_full_opt_level();

    public void runTest(Method m, Object[] args, int warmup, int nullMode, boolean[][] equalities) throws Exception {
        Class<?>[] parameterTypes = m.getParameterTypes();
        int parameterCount = parameterTypes.length;
        // Nullness mode for first argument
        // 0: default, 1: never null, 2: always null
        int start = (nullMode != 1) ? 0 : 1;
        int end = (nullMode != 2) ? args.length : 1;
        for (int i = start; i < end; ++i) {
            if (args[i] != null && !parameterTypes[0].isInstance(args[i])) {
                continue;
            }
            if (args[i] == null && parameterTypes[0] == MyValue1NewAcmp.class) {
                continue;
            }
            if (parameterCount == 1) {
                // Null checks
                System.out.print("Testing " + m.getName() + "(" + args[i] + ")");
                // Avoid acmp in the computation of the expected result!
                boolean expected = isNegated(m) ? (i != 0) : (i == 0);
                for (int run = 0; run < warmup; ++run) {
                    Boolean result = (Boolean)m.invoke(this, args[i]);
                    if (result != expected && WHITE_BOX.isMethodCompiled(m, false)) {
                        System.out.println(" = " + result);
                        throw new RuntimeException("Test failed: should return " + expected);
                    }
                }
                System.out.println(" = " + expected);
            } else {
                // Equality checks
                for (int j = 0; j < args.length; ++j) {
                    if (args[j] != null && !parameterTypes[1].isInstance(args[j])) {
                        continue;
                    }
                    if (args[j] == null && parameterTypes[1] == MyValue1NewAcmp.class) {
                        continue;
                    }
                    System.out.print("Testing " + m.getName() + "(" + args[i] + ", " + args[j] + ")");
                    // Avoid acmp in the computation of the expected result!
                    boolean equal = equalities[i][j];
                    equal = isNegated(m) ? !equal : equal;
                    boolean expected = ((i == 0 || j == 0) && trueIfNull(m)) || (equal && !(i == 0 && falseIfNull(m)));
                    for (int run = 0; run < warmup; ++run) {
                        Boolean result = (Boolean)m.invoke(this, args[i], args[j]);
                        if (result != expected && WHITE_BOX.isMethodCompiled(m, false) && warmup == 1) {
                            System.out.println(" = " + result);
                            throw new RuntimeException("Test failed: should return " + expected);
                        }
                    }
                    System.out.println(" = " + expected);
                }
            }
        }
    }

    public void run(int nullMode) throws Exception {
        // Prepare test arguments
        Object[] args =  { null,
                           new Object(),
                           new MyObjectNewAcmp(),
                           MyValue1NewAcmp.setX(MyValue1NewAcmp.createDefault(), 42),
                           new Object[10],
                           new MyObjectNewAcmp[10],
                           MyValue1NewAcmp.setX(MyValue1NewAcmp.createDefault(), 0x42),
                           MyValue1NewAcmp.setX(MyValue1NewAcmp.createDefault(), 42),
                           MyValue2NewAcmp.setX(MyValue2NewAcmp.createDefault(), 42), };

        boolean[][] equalities = { { true,  false, false, false, false, false, false, false, false },
                                   { false, true,  false, false, false, false, false, false, false },
                                   { false, false, true,  false, false, false, false, false, false },
                                   { false, false, false, true,  false, false, false, true,  false },
                                   { false, false, false, false, true,  false, false, false, false },
                                   { false, false, false, false, false, true,  false, false, false },
                                   { false, false, false, false, false, false, true,  false, false },
                                   { false, false, false, true,  false, false, false, true,  false },
                                   { false, false, false, false, false, false, false, false, true  } };

        // Run tests
        for (Method m : getClass().getMethods()) {
            if (m.getName().startsWith("test")) {
                // Do some warmup runs
                runTest(m, args, 1000, nullMode, equalities);
                // Make sure method is compiled
                TestFramework.compile(m, CompLevel.ANY);
                Asserts.assertTrue(WHITE_BOX.isMethodCompiled(m, false), m + " not compiled");
                // Run again to verify correctness of compiled code
                runTest(m, args, 1, nullMode, equalities);
            }
        }

        Method cmpAlwaysUnEqual3_m = getClass().getMethod("cmpAlwaysUnEqual3", Object.class);
        Method cmpAlwaysUnEqual4_m = getClass().getMethod("cmpAlwaysUnEqual4", Object.class);
        Method cmpSometimesEqual1_m = getClass().getMethod("cmpSometimesEqual1", Object.class);
        Method cmpSometimesEqual2_m = getClass().getMethod("cmpSometimesEqual2", Object.class);

        for (int i = 0; i < 20_000; ++i) {
            Asserts.assertTrue(cmpAlwaysEqual1(args[1], args[1]));
            Asserts.assertFalse(cmpAlwaysEqual2(args[1], args[1]));
            Asserts.assertTrue(cmpAlwaysEqual3(args[1]));
            Asserts.assertFalse(cmpAlwaysEqual4(args[1]));

            Asserts.assertFalse(cmpAlwaysUnEqual1(args[1], args[2]));
            Asserts.assertTrue(cmpAlwaysUnEqual2(args[1], args[2]));
            boolean res = cmpAlwaysUnEqual3(args[3]);
            Asserts.assertTrue(res);
            res = cmpAlwaysUnEqual4(args[3]);
            Asserts.assertFalse(res);

            int idx = i % args.length;
            res = cmpSometimesEqual1(args[idx]);
            Asserts.assertTrue(res);
            res = cmpSometimesEqual2(args[idx]);
            Asserts.assertFalse(res);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            enumerateVMOptions();
        } else {
            int nullMode = Integer.valueOf(args[0]);
            TestNewAcmp t = new TestNewAcmp();
            t.run(nullMode);
        }
    }

    private static String[] addOptions(String prefix[], String... extra) {
        ArrayList<String> list = new ArrayList<String>();
        if (prefix != null) {
            for (String s : prefix) {
                list.add(s);
            }
        }
        if (extra != null) {
            for (String s : extra) {
                System.out.println("    " + s);
                list.add(s);
            }
        }

        return list.toArray(new String[list.size()]);
    }

    private static void enumerateVMOptions() throws Exception {
        String[] baseOptions = {
            "--enable-preview",
            "-Xbootclasspath/a:.",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-Xbatch",
            "-XX:TypeProfileLevel=222",
            "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
            "-XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestNewAcmp::test*",
            "-XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestNewAcmp::cmp*"};

        String SCENARIOS = System.getProperty("Scenarios", "");
        List<String> scenarios = null;
        if (!SCENARIOS.isEmpty()) {
           scenarios = Arrays.asList(SCENARIOS.split(","));
        }

        int scenario = -1;
        for (int nullMode = 0; nullMode <= 2; nullMode++) {          // null mode
            for (int incrInline = 0; incrInline < 2; incrInline++) { // 0 = default, 1 = -XX:+AlwaysIncrementalInline
                scenario++;
                System.out.println("Scenario #" + scenario + " -------------------");
                String[] cmds = baseOptions;
                if (incrInline != 0) {
                    cmds = addOptions(cmds, "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AlwaysIncrementalInline");
                }

                cmds = addOptions(cmds, "compiler.valhalla.inlinetypes.TestNewAcmp");
                cmds = addOptions(cmds, Integer.toString(nullMode));

                if (scenarios != null && !scenarios.contains(Integer.toString(scenario))) {
                    System.out.println("Scenario #" + scenario + " is skipped due to -Dscenarios=" + SCENARIOS);
                    continue;
                }

                OutputAnalyzer oa = ProcessTools.executeTestJava(cmds);
                String output = oa.getOutput();
                oa.shouldHaveExitValue(0);
                System.out.println(output);
            }
        }
    }
}
