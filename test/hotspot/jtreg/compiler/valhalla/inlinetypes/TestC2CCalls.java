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

/*
 * @test id=default
 * @key randomness
 * @summary Test value class calling convention with compiled to compiled calls.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               compiler.valhalla.inlinetypes.TestC2CCalls
 */

/*
 * @test id=no-bimorphic
 * @key randomness
 * @summary Test value class calling convention with compiled to compiled calls.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -XX:-UseBimorphicInlining -Xbatch
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestC2CCalls*::test*
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestC2CCalls*::test*
 *                               compiler.valhalla.inlinetypes.TestC2CCalls
 */

/*
 * @test id=no-bimorphic-no-prof
 * @key randomness
 * @summary Test value class calling convention with compiled to compiled calls.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -XX:-UseBimorphicInlining -Xbatch -XX:-ProfileInterpreter
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestC2CCalls*::test*
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestC2CCalls*::test*
 *                               compiler.valhalla.inlinetypes.TestC2CCalls
 */

/*
 * @test id=no-bimorphic2
 * @key randomness
 * @summary Test value class calling convention with compiled to compiled calls.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                              -XX:-UseBimorphicInlining -Xbatch
 *                              -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestC2CCalls::test*
 *                              -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestC2CCalls*::test*
 *                              compiler.valhalla.inlinetypes.TestC2CCalls
 */

/*
 * @test id=no-bimorphic-no-prof2
 * @key randomness
 * @summary Test value class calling convention with compiled to compiled calls.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -XX:-UseBimorphicInlining -Xbatch -XX:-ProfileInterpreter
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestC2CCalls::test*
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestC2CCalls*::test*
 *                               compiler.valhalla.inlinetypes.TestC2CCalls
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import jdk.test.whitebox.WhiteBox;

public class TestC2CCalls {
    public static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    public static final int COMP_LEVEL_FULL_OPTIMIZATION = 4; // C2 or JVMCI
    public static final int rI = Utils.getRandomInstance().nextInt() % 1000;

    static value class OtherVal {
        public int x;

        private OtherVal(int x) {
            this.x = x;
        }
    }

    static interface MyInterface1 {
        public MyInterface1 test1(OtherVal other, int y);
        public MyInterface1 test2(OtherVal other1, OtherVal other2, int y);
        public MyInterface1 test3(OtherVal other1, OtherVal other2, int y, boolean deopt);
        public MyInterface1 test4(OtherVal other1, OtherVal other2, int y);
        public MyInterface1 test5(OtherVal other1, OtherVal other2, int y);
        public MyInterface1 test6();
        public MyInterface1 test7(int i1, int i2, int i3, int i4, int i5, int i6);
        public MyInterface1 test8(int i1, int i2, int i3, int i4, int i5, int i6, int i7);
        public MyInterface1 test9(MyValue3 other, int i1, int i2, int i3, int i4, int i5, int i6);
        public MyInterface1 test10(MyValue4 other, int i1, int i2, int i3, int i4, int i5, int i6);

        public int getValue();
    }

    static value class MyValue1 implements MyInterface1 {
        public int x;

        private MyValue1(int x) {
            this.x = x;
        }

        @Override
        public int getValue() {
            return x;
        }

        @Override
        public MyValue1 test1(OtherVal other, int y) {
            return new MyValue1(x + other.x + y);
        }

        @Override
        public MyValue1 test2(OtherVal other1, OtherVal other2, int y) {
            return new MyValue1(x + other1.x + other2.x + y);
        }

        @Override
        public MyValue1 test3(OtherVal other1, OtherVal other2, int y, boolean deopt) {
            if (!deopt) {
              return new MyValue1(x + other1.x + other2.x + y);
            } else {
              // Uncommon trap
              return test1(other1, y);
            }
        }

        @Override
        public MyValue1 test4(OtherVal other1, OtherVal other2, int y) {
            return new MyValue1(x + other1.x + other2.x + y);
        }

        @Override
        public MyValue1 test5(OtherVal other1, OtherVal other2, int y) {
            return new MyValue1(x + other1.x + other2.x + y);
        }

        @Override
        public MyValue1 test6() {
            return this;
        }

        @Override
        public MyValue1 test7(int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue1(x + i1 + i2 + i3 + i4 + i5 + i6);
        }

        @Override
        public MyValue1 test8(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
            return new MyValue1(x + i1 + i2 + i3 + i4 + i5 + i6 + i7);
        }

        public MyValue1 test9(MyValue3 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue1(x + (int)(other.d1 + other.d2 + other.d3 + other.d4) + i1 + i2 + i3 + i4 + i5 + i6);
        }

        public MyValue1 test10(MyValue4 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue1(x + other.x1 + other.x2 + other.x3 + other.x4 + i1 + i2 + i3 + i4 + i5 + i6);
        }
    }

    static value class MyValue2 implements MyInterface1 {
        public int x;

        private MyValue2(int x) {
            this.x = x;
        }

        @Override
        public int getValue() {
            return x;
        }

        @Override
        public MyValue2 test1(OtherVal other, int y) {
            return new MyValue2(x + other.x + y);
        }

        @Override
        public MyValue2 test2(OtherVal other1, OtherVal other2, int y) {
            return new MyValue2(x + other1.x + other2.x + y);
        }

        @Override
        public MyValue2 test3(OtherVal other1, OtherVal other2, int y, boolean deopt) {
            if (!deopt) {
              return new MyValue2(x + other1.x + other2.x + y);
            } else {
              // Uncommon trap
              return test1(other1, y);
            }
        }

        @Override
        public MyValue2 test4(OtherVal other1, OtherVal other2, int y) {
            return new MyValue2(x + other1.x + other2.x + y);
        }

        @Override
        public MyValue2 test5(OtherVal other1, OtherVal other2, int y) {
            return new MyValue2(x + other1.x + other2.x + y);
        }

        @Override
        public MyValue2 test6() {
            return this;
        }

        @Override
        public MyValue2 test7(int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue2(x + i1 + i2 + i3 + i4 + i5 + i6);
        }

        @Override
        public MyValue2 test8(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
            return new MyValue2(x + i1 + i2 + i3 + i4 + i5 + i6 + i7);
        }

        public MyValue2 test9(MyValue3 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue2(x + (int)(other.d1 + other.d2 + other.d3 + other.d4) + i1 + i2 + i3 + i4 + i5 + i6);
        }

        public MyValue2 test10(MyValue4 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue2(x + other.x1 + other.x2 + other.x3 + other.x4 + i1 + i2 + i3 + i4 + i5 + i6);
        }
    }

    static value class MyValue3 implements MyInterface1 {
        public double d1;
        public double d2;
        public double d3;
        public double d4;

        private MyValue3(double d) {
            this.d1 = d;
            this.d2 = d;
            this.d3 = d;
            this.d4 = d;
        }

        @Override
        public int getValue() {
            return (int)d4;
        }

        @Override
        public MyValue3 test1(OtherVal other, int y) { return new MyValue3(0); }
        @Override
        public MyValue3 test2(OtherVal other1, OtherVal other2, int y)  { return new MyValue3(0); }
        @Override
        public MyValue3 test3(OtherVal other1, OtherVal other2, int y, boolean deopt)  { return new MyValue3(0); }
        @Override
        public MyValue3 test4(OtherVal other1, OtherVal other2, int y)  { return new MyValue3(0); }
        @Override
        public MyValue3 test5(OtherVal other1, OtherVal other2, int y)  { return new MyValue3(0); }
        @Override
        public MyValue3 test6()  { return new MyValue3(0); }

        @Override
        public MyValue3 test7(int i1, int i2, int i3, int i4, int i5, int i6)  {
            return new MyValue3(d1 + d2 + d3 + d4 + i1 + i2 + i3 + i4 + i5 + i6);
        }

        @Override
        public MyValue3 test8(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
            return new MyValue3(d1 + d2 + d3 + d4 + i1 + i2 + i3 + i4 + i5 + i6 + i7);
        }

        public MyValue3 test9(MyValue3 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue3(d1 + d2 + d3 + d4 + other.d1 + other.d2 + other.d3 + other.d4 + i1 + i2 + i3 + i4 + i5 + i6);
        }

        public MyValue3 test10(MyValue4 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue3(d1 + d2 + d3 + d4 + other.x1 + other.x2 + other.x3 + other.x4 + i1 + i2 + i3 + i4 + i5 + i6);
        }
    }

    static value class MyValue4 implements MyInterface1 {
        public int x1;
        public int x2;
        public int x3;
        public int x4;

        private MyValue4(int i) {
            this.x1 = i;
            this.x2 = i;
            this.x3 = i;
            this.x4 = i;
        }

        @Override
        public int getValue() {
            return x4;
        }

        @Override
        public MyValue4 test1(OtherVal other, int y) { return new MyValue4(0); }
        @Override
        public MyValue4 test2(OtherVal other1, OtherVal other2, int y)  { return new MyValue4(0); }
        @Override
        public MyValue4 test3(OtherVal other1, OtherVal other2, int y, boolean deopt)  { return new MyValue4(0); }
        @Override
        public MyValue4 test4(OtherVal other1, OtherVal other2, int y)  { return new MyValue4(0); }
        @Override
        public MyValue4 test5(OtherVal other1, OtherVal other2, int y)  { return new MyValue4(0); }
        @Override
        public MyValue4 test6()  { return new MyValue4(0); }

        @Override
        public MyValue4 test7(int i1, int i2, int i3, int i4, int i5, int i6)  {
            return new MyValue4(x1 + x2 + x3 + x4 + i1 + i2 + i3 + i4 + i5 + i6);
        }

        @Override
        public MyValue4 test8(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
            return new MyValue4(x1 + x2 + x3 + x4 + i1 + i2 + i3 + i4 + i5 + i6 + i7);
        }

        public MyValue4 test9(MyValue3 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue4(x1 + x2 + x3 + x4 + (int)(other.d1 + other.d2 + other.d3 + other.d4) + i1 + i2 + i3 + i4 + i5 + i6);
        }

        public MyValue4 test10(MyValue4 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyValue4(x1 + x2 + x3 + x4 + other.x1 + other.x2 + other.x3 + other.x4 + i1 + i2 + i3 + i4 + i5 + i6);
        }
    }

    static class MyObject implements MyInterface1 {
        private final int x;

        private MyObject(int x) {
            this.x = x;
        }

        @Override
        public int getValue() {
            return x;
        }

        @Override
        public MyObject test1(OtherVal other, int y) {
            return new MyObject(x + other.x + y);
        }

        @Override
        public MyObject test2(OtherVal other1, OtherVal other2, int y) {
            return new MyObject(x + other1.x + other2.x + y);
        }

        @Override
        public MyObject test3(OtherVal other1, OtherVal other2, int y, boolean deopt) {
            if (!deopt) {
              return new MyObject(x + other1.x + other2.x + y);
            } else {
              // Uncommon trap
              return test1(other1, y);
            }
        }

        @Override
        public MyObject test4(OtherVal other1, OtherVal other2, int y) {
            return new MyObject(x + other1.x + other2.x + y);
        }

        @Override
        public MyObject test5(OtherVal other1, OtherVal other2, int y) {
            return new MyObject(x + other1.x + other2.x + y);
        }

        @Override
        public MyObject test6() {
            return this;
        }

        @Override
        public MyObject test7(int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyObject(x + i1 + i2 + i3 + i4 + i5 + i6);
        }

        @Override
        public MyObject test8(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
            return new MyObject(x + i1 + i2 + i3 + i4 + i5 + i6 + i7);
        }

        public MyObject test9(MyValue3 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyObject(x + (int)(other.d1 + other.d2 + other.d3 + other.d4) + i1 + i2 + i3 + i4 + i5 + i6);
        }

        public MyObject test10(MyValue4 other, int i1, int i2, int i3, int i4, int i5, int i6) {
            return new MyObject(x + other.x1 + other.x2 + other.x3 + other.x4 + i1 + i2 + i3 + i4 + i5 + i6);
        }
    }

    // Test calling methods with value class arguments through an interface
    public static int test1(MyInterface1 intf, OtherVal other, int y) {
        return intf.test1(other, y).getValue();
    }

    public static int test2(MyInterface1 intf, OtherVal other, int y) {
        return intf.test2(other, other, y).getValue();
    }

    // Test mixing null-tolerant and null-free value class arguments
    public static int test3(MyValue1 vt, OtherVal other, int y) {
        return vt.test2(other, other, y).getValue();
    }

    public static int test4(MyObject obj, OtherVal other, int y) {
        return obj.test2(other, other, y).getValue();
    }

    // Optimized interface call with value class receiver
    public static int test5(MyInterface1 intf, OtherVal other, int y) {
        return intf.test1(other, y).getValue();
    }

    public static int test6(MyInterface1 intf, OtherVal other, int y) {
        return intf.test2(other, other, y).getValue();
    }

    // Optimized interface call with object receiver
    public static int test7(MyInterface1 intf, OtherVal other, int y) {
        return intf.test1(other, y).getValue();
    }

    public static int test8(MyInterface1 intf, OtherVal other, int y) {
        return intf.test2(other, other, y).getValue();
    }

    // Interface calls with deoptimized callee
    public static int test9(MyInterface1 intf, OtherVal other, int y, boolean deopt) {
        return intf.test3(other, other, y, deopt).getValue();
    }

    public static int test10(MyInterface1 intf, OtherVal other, int y, boolean deopt) {
        return intf.test3(other, other, y, deopt).getValue();
    }

    // Optimized interface calls with deoptimized callee
    public static int test11(MyInterface1 intf, OtherVal other, int y, boolean deopt) {
        return intf.test3(other, other, y, deopt).getValue();
    }

    public static int test12(MyInterface1 intf, OtherVal other, int y, boolean deopt) {
        return intf.test3(other, other, y, deopt).getValue();
    }

    public static int test13(MyInterface1 intf, OtherVal other, int y, boolean deopt) {
        return intf.test3(other, other, y, deopt).getValue();
    }

    public static int test14(MyInterface1 intf, OtherVal other, int y, boolean deopt) {
        return intf.test3(other, other, y, deopt).getValue();
    }

    // Interface calls without warmed up / compiled callees
    public static int test15(MyInterface1 intf, OtherVal other, int y) {
        return intf.test4(other, other, y).getValue();
    }

    public static int test16(MyInterface1 intf, OtherVal other, int y) {
        return intf.test5(other, other, y).getValue();
    }

    // Interface call with no arguments
    public static int test17(MyInterface1 intf) {
        return intf.test6().getValue();
    }

    // Calls that require stack extension
    public static int test18(MyInterface1 intf, int y) {
        return intf.test7(y, y, y, y, y, y).getValue();
    }

    public static int test19(MyInterface1 intf, int y) {
        return intf.test8(y, y, y, y, y, y, y).getValue();
    }

    public static int test20(MyInterface1 intf, MyValue3 v, int y) {
        return intf.test9(v, y, y, y, y, y, y).getValue();
    }

    public static int test21(MyInterface1 intf, MyValue4 v, int y) {
        return intf.test10(v, y, y, y, y, y, y).getValue();
    }

    public static void main(String[] args) {
        // Sometimes, exclude some methods from compilation with C2 to stress test the calling convention
        if (Utils.getRandomInstance().nextBoolean()) {
            ArrayList<Method> methods = new ArrayList<Method>();
            Collections.addAll(methods, MyValue1.class.getDeclaredMethods());
            Collections.addAll(methods, MyValue2.class.getDeclaredMethods());
            Collections.addAll(methods, MyValue3.class.getDeclaredMethods());
            Collections.addAll(methods, MyValue4.class.getDeclaredMethods());
            Collections.addAll(methods, MyObject.class.getDeclaredMethods());
            Collections.addAll(methods, compiler.valhalla.inlinetypes.TestC2CCalls.class.getDeclaredMethods());
            System.out.println("Excluding methods from C2 compilation:");
            for (Method m : methods) {
                if (Utils.getRandomInstance().nextBoolean()) {
                    System.out.println(m);
                    WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
                }
            }
        }

        MyValue1 val1 = new MyValue1(rI);
        MyValue2 val2 = new MyValue2(rI+1);
        MyValue3 val3 = new MyValue3(rI+2);
        MyValue4 val4 = new MyValue4(rI+3);
        OtherVal other = new OtherVal(rI+4);
        MyObject obj = new MyObject(rI+5);

        // Make sure callee methods are compiled
        for (int i = 0; i < 10_000; ++i) {
            Asserts.assertEQ(val1.test1(other, rI).getValue(), val1.x + other.x + rI);
            Asserts.assertEQ(val2.test1(other, rI).getValue(), val2.x + other.x + rI);
            Asserts.assertEQ(obj.test1(other, rI).getValue(), obj.x + other.x + rI);
            Asserts.assertEQ(val1.test2(other, other, rI).getValue(), val1.x + 2*other.x + rI);
            Asserts.assertEQ(val2.test2(other, other, rI).getValue(), val2.x + 2*other.x + rI);
            Asserts.assertEQ(obj.test2(other, other, rI).getValue(), obj.x + 2*other.x + rI);
            Asserts.assertEQ(val1.test3(other, other, rI, false).getValue(), val1.x + 2*other.x + rI);
            Asserts.assertEQ(val2.test3(other, other, rI, false).getValue(), val2.x + 2*other.x + rI);
            Asserts.assertEQ(obj.test3(other, other, rI, false).getValue(), obj.x + 2*other.x + rI);
            Asserts.assertEQ(val1.test7(rI, rI, rI, rI, rI, rI).getValue(), val1.x + 6*rI);
            Asserts.assertEQ(val2.test7(rI, rI, rI, rI, rI, rI).getValue(), val2.x + 6*rI);
            Asserts.assertEQ(val3.test7(rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val3.d1 + 6*rI));
            Asserts.assertEQ(val4.test7(rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val4.x1 + 6*rI));
            Asserts.assertEQ(obj.test7(rI, rI, rI, rI, rI, rI).getValue(), obj.x + 6*rI);
            Asserts.assertEQ(val1.test8(rI, rI, rI, rI, rI, rI, rI).getValue(), val1.x + 7*rI);
            Asserts.assertEQ(val2.test8(rI, rI, rI, rI, rI, rI, rI).getValue(), val2.x + 7*rI);
            Asserts.assertEQ(val3.test8(rI, rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val3.d1 + 7*rI));
            Asserts.assertEQ(val4.test8(rI, rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val4.x1 + 7*rI));
            Asserts.assertEQ(obj.test8(rI, rI, rI, rI, rI, rI, rI).getValue(), obj.x + 7*rI);
            Asserts.assertEQ(val1.test9(val3, rI, rI, rI, rI, rI, rI).getValue(), (int)(val1.x + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(val2.test9(val3, rI, rI, rI, rI, rI, rI).getValue(), (int)(val2.x + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(val3.test9(val3, rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val3.d1 + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(val4.test9(val3, rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val4.x1 + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(obj.test9(val3, rI, rI, rI, rI, rI, rI).getValue(), (int)(obj.x + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(val1.test10(val4, rI, rI, rI, rI, rI, rI).getValue(), (int)(val1.x + 4*val4.x1 + 6*rI));
            Asserts.assertEQ(val2.test10(val4, rI, rI, rI, rI, rI, rI).getValue(), (int)(val2.x + 4*val4.x1 + 6*rI));
            Asserts.assertEQ(val3.test10(val4, rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val3.d1 + 4*val4.x1 + 6*rI));
            Asserts.assertEQ(val4.test10(val4, rI, rI, rI, rI, rI, rI).getValue(), (int)(4*val4.x1 + 4*val4.x1 + 6*rI));
            Asserts.assertEQ(obj.test10(val4, rI, rI, rI, rI, rI, rI).getValue(), (int)(obj.x + 4*val4.x1 + 6*rI));
        }

        // Pollute call profile
        for (int i = 0; i < 100; ++i) {
            Asserts.assertEQ(test15(val1, other, rI), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test16(obj, other, rI), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test17(obj), obj.x);
        }

        // Trigger compilation of caller methods
        for (int i = 0; i < 100_000; ++i) {
            val1 = new MyValue1(rI+i);
            val2 = new MyValue2(rI+i+1);
            val3 = new MyValue3(rI+i+2);
            val4 = new MyValue4(rI+i+3);
            other = new OtherVal(rI+i+4);
            obj = new MyObject(rI+i+5);

            Asserts.assertEQ(test1(val1, other, rI), val1.x + other.x + rI);
            Asserts.assertEQ(test1(obj, other, rI), obj.x + other.x + rI);
            Asserts.assertEQ(test2(obj, other, rI), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test2(val1, other, rI), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test3(val1, other, rI), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test4(obj, other, rI), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test5(val1, other, rI), val1.x + other.x + rI);
            Asserts.assertEQ(test6(val1, other, rI), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test7(obj, other, rI), obj.x + other.x + rI);
            Asserts.assertEQ(test8(obj, other, rI), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test9(val1, other, rI, false), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test9(obj, other, rI, false), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test10(val1, other, rI, false), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test10(obj, other, rI, false), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test11(val1, other, rI, false), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test12(val1, other, rI, false), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test13(obj, other, rI, false), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test14(obj, other, rI, false), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test15(obj, other, rI), obj.x + 2*other.x + rI);
            Asserts.assertEQ(test16(val1, other, rI), val1.x + 2*other.x + rI);
            Asserts.assertEQ(test17(val1), val1.x);
            Asserts.assertEQ(test18(val1, rI), val1.x + 6*rI);
            Asserts.assertEQ(test18(val2, rI), val2.x + 6*rI);
            Asserts.assertEQ(test18(val3, rI), (int)(4*val3.d1 + 6*rI));
            Asserts.assertEQ(test18(val4, rI), 4*val4.x1 + 6*rI);
            Asserts.assertEQ(test18(obj, rI), obj.x + 6*rI);
            Asserts.assertEQ(test19(val1, rI), val1.x + 7*rI);
            Asserts.assertEQ(test19(val2, rI), val2.x + 7*rI);
            Asserts.assertEQ(test19(val3, rI), (int)(4*val3.d1 + 7*rI));
            Asserts.assertEQ(test19(val4, rI), 4*val4.x1 + 7*rI);
            Asserts.assertEQ(test19(obj, rI), obj.x + 7*rI);
            Asserts.assertEQ(test20(val1, val3, rI), (int)(val1.x + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(test20(val2, val3, rI), (int)(val2.x + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(test20(val3, val3, rI), (int)(4*val3.d1 + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(test20(val4, val3, rI), (int)(4*val4.x1 + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(test20(obj, val3, rI), (int)(obj.x + 4*val3.d1 + 6*rI));
            Asserts.assertEQ(test21(val1, val4, rI), val1.x + 4*val4.x1 + 6*rI);
            Asserts.assertEQ(test21(val2, val4, rI), val2.x + 4*val4.x1 + 6*rI);
            Asserts.assertEQ(test21(val3, val4, rI), (int)(4*val3.d1 + 4*val4.x1 + 6*rI));
            Asserts.assertEQ(test21(val4, val4, rI), 4*val4.x1 + 4*val4.x1 + 6*rI);
            Asserts.assertEQ(test21(obj, val4, rI), obj.x + 4*val4.x1 + 6*rI);
        }

        // Trigger deoptimization
        Asserts.assertEQ(val1.test3(other, other, rI, true).getValue(), val1.x + other.x + rI);
        Asserts.assertEQ(obj.test3(other, other, rI, true).getValue(), obj.x + other.x + rI);

        // Check results of methods still calling the deoptimized methods
        Asserts.assertEQ(test9(val1, other, rI, false), val1.x + 2*other.x + rI);
        Asserts.assertEQ(test9(obj, other, rI, false), obj.x + 2*other.x + rI);
        Asserts.assertEQ(test10(obj, other, rI, false), obj.x + 2*other.x + rI);
        Asserts.assertEQ(test10(val1, other, rI, false), val1.x + 2*other.x + rI);
        Asserts.assertEQ(test11(val1, other, rI, false), val1.x + 2*other.x + rI);
        Asserts.assertEQ(test11(obj, other, rI, false), obj.x + 2*other.x + rI);
        Asserts.assertEQ(test12(obj, other, rI, false), obj.x + 2*other.x + rI);
        Asserts.assertEQ(test12(val1, other, rI, false), val1.x + 2*other.x + rI);
        Asserts.assertEQ(test13(val1, other, rI, false), val1.x + 2*other.x + rI);
        Asserts.assertEQ(test13(obj, other, rI, false), obj.x + 2*other.x + rI);
        Asserts.assertEQ(test14(obj, other, rI, false), obj.x + 2*other.x + rI);
        Asserts.assertEQ(test14(val1, other, rI, false), val1.x + 2*other.x + rI);

        // Check with unexpected arguments
        Asserts.assertEQ(test1(val2, other, rI), val2.x + other.x + rI);
        Asserts.assertEQ(test2(val2, other, rI), val2.x + 2*other.x + rI);
        Asserts.assertEQ(test5(val2, other, rI), val2.x + other.x + rI);
        Asserts.assertEQ(test6(val2, other, rI), val2.x + 2*other.x + rI);
        Asserts.assertEQ(test7(val1, other, rI), val1.x + other.x + rI);
        Asserts.assertEQ(test8(val1, other, rI), val1.x + 2*other.x + rI);
        Asserts.assertEQ(test15(val1, other, rI), val1.x + 2*other.x + rI);
        Asserts.assertEQ(test16(obj, other, rI), obj.x + 2*other.x + rI);
        Asserts.assertEQ(test17(obj), obj.x);
    }
}
