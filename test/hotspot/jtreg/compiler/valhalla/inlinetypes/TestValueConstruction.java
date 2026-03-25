/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.Label;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.Random;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;
import test.java.lang.invoke.lib.InstructionHelper;

/**
 * @test id=Xbatch
 * @summary Test construction of value objects.
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=MemLimit,*.*,2G~crash
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=DeoptimizeALot
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:+DeoptimizeALot
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=MemLimit,*.*,2G~crash
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=CompileonlyTest
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=compileonly,*TestValueConstruction::test* -Xbatch
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/**
 * @test id=DontInlineHelper
 * @summary Test construction of value objects.
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                   -XX:CompileCommand=dontinline,compiler*::helper*
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=DontInlineMyValueInit
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=dontinline,*MyValue*::<init> -Xbatch
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=DontInlineObjectInit
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=dontinline,*Object::<init> -Xbatch
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=DontInlineObjectInitDeoptimizeALot
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+DeoptimizeALot -XX:CompileCommand=dontinline,*Object::<init> -Xbatch
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=DontInlineMyAbstractInit
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=dontinline,*MyAbstract::<init> -Xbatch
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=MemLimit,*.*,2G~crash
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=StressIncrementalInlining
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch
 *                   -XX:-TieredCompilation -XX:+StressIncrementalInlining
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=MemLimit,*.*,2G~crash
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=StressIncrementalInliningCompileOnlyTest
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation -XX:+StressIncrementalInlining
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=compileonly,*TestValueConstruction::test* -Xbatch
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/* @test id=StressIncrementalInliningDontInlineMyValueInit
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation -XX:+StressIncrementalInlining
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=dontinline,*MyValue*::<init> -Xbatch
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=StressIncrementalInliningDontInlineObjectInit
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation -XX:+StressIncrementalInlining
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=dontinline,*Object::<init> -Xbatch
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=StressIncrementalInliningDontInlineMyAbstractInit
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation -XX:+StressIncrementalInlining
 *                   -XX:CompileCommand=inline,TestValueConstruction::checkDeopt
 *                   -XX:CompileCommand=dontinline,*MyAbstract::<init> -Xbatch
 *                   -XX:CompileCommand=MemLimit,*.*,2G~crash
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

/*
 * @test id=StressIncrementalInliningOnStackReplacement
 * @key randomness
 * @library /testlibrary /test/lib /compiler/whitebox /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox test.java.lang.invoke.lib.InstructionHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation -XX:+StressIncrementalInlining
 *                   -XX:Tier0BackedgeNotifyFreqLog=0 -XX:Tier2BackedgeNotifyFreqLog=0 -XX:Tier3BackedgeNotifyFreqLog=0
 *                   -XX:Tier2BackEdgeThreshold=1 -XX:Tier3BackEdgeThreshold=1 -XX:Tier4BackEdgeThreshold=1 -Xbatch
 *                   -XX:CompileCommand=MemLimit,*.*,2G~crash
 *                   compiler.valhalla.inlinetypes.TestValueConstruction
 */

public class TestValueConstruction {
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    static boolean VERBOSE = false;
    static boolean[] deopt = new boolean[14];
    static boolean[] deoptBig = new boolean[24];
    static boolean[] deoptHuge = new boolean[37];

    static Object o = new Object();

    static void reportDeopt(int deoptNum) {
        System.out.println("Deopt " + deoptNum + " triggered");
        if (VERBOSE) {
            new Exception().printStackTrace(System.out);
        }
    }

    // Trigger deopts at various places
    static void checkDeopt(int deoptNum) {
        if (deopt[deoptNum]) {
            // C2 will add an uncommon trap here
            reportDeopt(deoptNum);
        }
    }

    // Trigger deopts at various places
    static void checkDeoptBig(int deoptNum) {
        if (deoptBig[deoptNum]) {
            // C2 will add an uncommon trap here
            reportDeopt(deoptNum);
        }
    }

    // Trigger deopts at various places
    static void checkDeoptHuge(int deoptNum) {
        if (deoptHuge[deoptNum]) {
            // C2 will add an uncommon trap here
            reportDeopt(deoptNum);
        }
    }

    static interface MyInterface {

    }

    static value class MyValue1 implements MyInterface {
        int x;

        public MyValue1(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            super();
            checkDeopt(2);
        }

        public MyValue1(int x, int deoptNum1, int deoptNum2, int deoptNum3) {
            checkDeopt(deoptNum1);
            this.x = x;
            checkDeopt(deoptNum2);
            super();
            checkDeopt(deoptNum3);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static value class MyValue1a extends MyAbstract1 implements MyInterface {
        int x;

        public MyValue1a(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            super();
            checkDeopt(2);
        }

        public MyValue1a(int x, int deoptNum1, int deoptNum2, int deoptNum3) {
            checkDeopt(deoptNum1);
            this.x = x;
            checkDeopt(deoptNum2);
            super();
            checkDeopt(deoptNum3);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static abstract value class AMyValue1 implements MyInterface {
        int x;

        public AMyValue1(int x) {
            checkDeopt(3);
            this.x = x;
            checkDeopt(4);
            super();
            checkDeopt(5);
        }

        public AMyValue1(int x, int deoptNum1, int deoptNum2, int deoptNum3) {
            checkDeopt(deoptNum1);
            this.x = x;
            checkDeopt(deoptNum2);
            super();
            checkDeopt(deoptNum3);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static value class MyValue1b extends AMyValue1 implements MyInterface {
        int x;

        public MyValue1b(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            super(x);
            checkDeopt(2);
        }

        public MyValue1b(int x, int deoptNum1, int deoptNum2, int deoptNum3) {
            checkDeopt(deoptNum1);
            this.x = x;
            checkDeopt(deoptNum2);
            super(x, deoptNum1 + 3, deoptNum2 + 3, deoptNum3 + 3);
            checkDeopt(deoptNum3);
        }

        public String toString() {
            return "x: " + x;
        }
    }


    static abstract value class MyAbstract1 { }

    static value class MyValue2 extends MyAbstract1 {
        int x;

        public MyValue2(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            super();
            checkDeopt(2);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static abstract value class MyAbstract2 {
        public MyAbstract2(int x) {
            checkDeopt(0);
        }
    }

    static value class MyValue3 extends MyAbstract2 {
        int x;

        public MyValue3(int x) {
            checkDeopt(1);
            this(x, 0);
            helper1(this, x, 2); // 'this' escapes through argument
            helper2(x, 3); // 'this' escapes through receiver
            checkDeopt(4);
        }

        public MyValue3(int x, int unused) {
            this.x = helper3(x, 5);
            super(x);
            helper1(this, x, 6); // 'this' escapes through argument
            helper2(x, 7); // 'this' escapes through receiver
            checkDeopt(8);
        }

        public static void helper1(MyValue3 obj, int x, int deoptNum) {
            checkDeopt(deoptNum);
            Asserts.assertEQ(obj.x, x);
        }

        public void helper2(int x, int deoptNum) {
            checkDeopt(deoptNum);
            Asserts.assertEQ(this.x, x);
        }

        public static int helper3(int x, int deoptNum) {
            checkDeopt(deoptNum);
            return x;
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static abstract value class AMyValue3a {
        int x;

        public AMyValue3a(int x) {
            this.x = helper3(x, 5);
            super();
            helper1(this, x, 6); // 'this' escapes through argument
            helper2(x, 7); // 'this' escapes through receiver
            checkDeopt(8);
        }

        public static void helper1(AMyValue3a obj, int x, int deoptNum) {
            checkDeopt(deoptNum);
            Asserts.assertEQ(obj.x, x);
        }

        public void helper2(int x, int deoptNum) {
            checkDeopt(deoptNum);
            Asserts.assertEQ(this.x, x);
        }

        public static int helper3(int x, int deoptNum) {
            checkDeopt(deoptNum);
            return x;
        }
    }

    static value class MyValue3a extends AMyValue3a {
        int y;

        public MyValue3a(int y) {
            checkDeopt(1);
            this.y = helper3(y, 5);
            super(y);
            helper1(this, y, 2); // 'this' escapes through argument
            helper2(y, 3); // 'this' escapes through receiver
            checkDeopt(4);
        }


        public static void helper1(MyValue3a obj, int y, int deoptNum) {
            checkDeopt(deoptNum);
            Asserts.assertEQ(obj.y, y);
        }

        public void helper2(int y, int deoptNum) {
            checkDeopt(deoptNum);
            Asserts.assertEQ(this.y, y);
        }

        public static int helper3(int y, int deoptNum) {
            checkDeopt(deoptNum);
            return y;
        }

        public String toString() {
            return "x: " + y;
        }
    }

    static value class MyValue4 {
        Integer x;

        public MyValue4(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            super();
            checkDeopt(2);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    abstract static value class AMyValue4a {
        Integer y;

        public AMyValue4a(int y) {
            checkDeopt(3);
            this.y = y;
            checkDeopt(4);
            super();
            checkDeopt(5);
        }

        public String toString() {
            return "y: " + y;
        }
    }

    static value class MyValue4a extends AMyValue4a {
        Integer x;

        public MyValue4a(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            super(x);
            checkDeopt(2);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static value class MyValue5 extends MyAbstract1 {
        int x;

        public MyValue5(int x, boolean b) {
            checkDeopt(0);
            if (b) {
                checkDeopt(1);
                this.x = 42;
                checkDeopt(2);
            } else {
                checkDeopt(3);
                this.x = x;
                checkDeopt(4);
            }
            checkDeopt(5);
            super();
            checkDeopt(6);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static abstract value class AMyValue5a {
        int y;

        public AMyValue5a(int y, boolean b) {
            checkDeopt(7);
            if (b) {
                checkDeopt(8);
                this.y = 42;
                checkDeopt(9);
            } else {
                checkDeopt(10);
                this.y = y;
                checkDeopt(11);
            }
            checkDeopt(12);
            super();
            checkDeopt(13);
        }

        public String toString() {
            return "y: " + y;
        }
    }

    static value class MyValue5a extends AMyValue5a {
        int x;

        public MyValue5a(int x, boolean b) {
            checkDeopt(0);
            if (b) {
                checkDeopt(1);
                this.x = 42;
                checkDeopt(2);
            } else {
                checkDeopt(3);
                this.x = x;
                checkDeopt(4);
            }
            checkDeopt(5);
            super(x, b);
            checkDeopt(6);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static value class MyValue6 {
        int x;
        MyValue1 val1;
        MyValue1 val2;

        public MyValue6(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            this.val1 = new MyValue1(x, 2, 3, 4);
            checkDeopt(5);
            this.val2 = new MyValue1(x + 1, 6, 7, 8);
            checkDeopt(9);
            super();
            checkDeopt(10);
        }

        public String toString() {
            return "x: " + x + ", val1: [" + val1 + "], val2: [" + val2 + "]";
        }
    }

    static value class MyValue6a {
        int x;
        MyValue1a val1;
        MyValue1a val2;

        public MyValue6a(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            this.val1 = new MyValue1a(x, 2, 3, 4);
            checkDeopt(5);
            this.val2 = new MyValue1a(x + 1, 6, 7, 8);
            checkDeopt(9);
            super();
            checkDeopt(10);
        }

        public String toString() {
            return "x: " + x + ", val1: [" + val1 + "], val2: [" + val2 + "]";
        }
    }

    static value class MyValue6b {
        int x;
        MyValue1b val1;
        MyValue1b val2;

        public MyValue6b(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            this.val1 = new MyValue1b(x, 2, 3, 4);
            checkDeopt(5);
            this.val2 = new MyValue1b(x + 1, 6, 7, 8);
            checkDeopt(12);
            super();
            checkDeopt(13);
        }

        public String toString() {
            return "x: " + x + ", val1: [" + val1 + "], val2: [" + val2 + "]";
        }
    }

    // Same as MyValue6 but unused MyValue1 construction
    static value class MyValue7 {
        int x;

        public MyValue7(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            new MyValue1(42, 2, 3, 4);
            checkDeopt(5);
            new MyValue1(43, 6, 7, 8);
            checkDeopt(9);
            super();
            checkDeopt(10);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    // Same as MyValue6 but unused MyValue1 construction
    static value class MyValue7a {
        int x;

        public MyValue7a(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            new MyValue1a(42, 2, 3, 4);
            checkDeopt(5);
            new MyValue1a(43, 6, 7, 8);
            checkDeopt(9);
            super();
            checkDeopt(10);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    // Same as MyValue6 but unused MyValue1 construction
    static value class MyValue7b {
        int x;

        public MyValue7b(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            new MyValue1b(42, 2, 3, 4);
            checkDeopt(5);
            new MyValue1b(43, 6, 7, 8);
            checkDeopt(12);
            super();
            checkDeopt(13);
        }

        public String toString() {
            return "x: " + x;
        }
    }

    // Constructor calling another constructor of the same value class with control flow dependent initialization
    static value class MyValue8 {
        int x;

        public MyValue8(int x) {
            checkDeopt(0);
            this(x, 0);
            checkDeopt(1);
        }

        public MyValue8(int x, int unused1) {
            checkDeopt(2);
            if ((x % 2) == 0) {
                checkDeopt(3);
                this.x = 42;
                checkDeopt(4);
            } else {
                checkDeopt(5);
                this.x = x;
                checkDeopt(6);
            }
            checkDeopt(7);
            super();
            checkDeopt(8);
        }

        public MyValue8(int x, int unused1, int unused2) {
            checkDeopt(3);
            this.x = x;
            checkDeopt(4);
        }

        public static MyValue8 valueOf(int x) {
            checkDeopt(0);
            if ((x % 2) == 0) {
                checkDeopt(1);
                return new MyValue8(42, 0, 0);
            } else {
                checkDeopt(2);
                return new MyValue8(x, 0, 0);
            }
        }

        public String toString() {
            return "x: " + x;
        }
    }

    // Constructor calling another constructor of the same value class with control flow dependent initialization
    static abstract value class AMyValue8a {
        int y;

        public AMyValue8a(int y) {
            checkDeoptBig(9);
            this(y, 0);
            checkDeoptBig(10);
        }

        public AMyValue8a(int y, int unused1) {
            checkDeoptBig(11);
            if ((y % 2) == 0) {
                checkDeoptBig(12);
                this.y = 42;
                checkDeoptBig(13);
            } else {
                checkDeoptBig(14);
                this.y = y;
                checkDeoptBig(15);
            }
            checkDeoptBig(16);
            super();
            checkDeoptBig(17);
        }

        public AMyValue8a(int y, int unused1, int unused2) {
            checkDeoptBig(12);
            this.y = y;
            checkDeoptBig(13);
        }

        public static AMyValue8a valueOf(int y) {
            checkDeoptBig(0);
            if ((y % 2) == 0) {
                checkDeoptBig(1);
                return new MyValue8a(42, 0, 0);
            } else {
                checkDeoptBig(2);
                return new MyValue8a(y, 0, 0);
            }
        }

        public String toString() {
            return "y: " + y;
        }
    }

    // Constructor calling another constructor of the same value class with control flow dependent initialization
    static value class MyValue8a extends AMyValue8a {
        int x;

        public MyValue8a(int x) {
            checkDeoptBig(0);
            this(x, 0);
            checkDeoptBig(1);
        }

        public MyValue8a(int x, int unused1) {
            checkDeoptBig(2);
            if ((x % 2) == 0) {
                checkDeoptBig(3);
                this.x = 42;
                checkDeoptBig(4);
            } else {
                checkDeoptBig(5);
                this.x = x;
                checkDeoptBig(6);
            }
            checkDeoptBig(7);
            super(unused1);
            checkDeoptBig(8);
        }

        public MyValue8a(int x, int unused1, int unused2) {
            checkDeoptBig(3);
            this.x = x;
            checkDeoptBig(4);
            super(x, unused1, unused2);
        }

        public static MyValue8a valueOf(int x) {
            checkDeoptBig(0);
            if ((x % 2) == 0) {
                checkDeoptBig(1);
                return new MyValue8a(42, 0, 0);
            } else {
                checkDeoptBig(2);
                return new MyValue8a(x, 0, 0);
            }
        }

        public String toString() {
            return "x: " + x;
        }
    }

    // Constructor calling another constructor of a different value class
    static value class MyValue9 {
        MyValue8 val;

        public MyValue9(int x) {
            checkDeopt(9);
            this(x, 0);
            checkDeopt(10);
        }

        public MyValue9(int i, int unused1) {
            checkDeopt(11);
            val = new MyValue8(i);
            checkDeopt(12);
        }

        public MyValue9(int x, int unused1, int unused2) {
            checkDeopt(5);
            this(x, 0, 0, 0);
            checkDeopt(6);
        }

        public MyValue9(int i, int unused1, int unused2, int unused3) {
            checkDeopt(7);
            val = MyValue8.valueOf(i);
            checkDeopt(8);
        }

        public String toString() {
            return "val: [" + val + "]";
        }
    }

    abstract static value class AMyValue9a {
        AMyValue8a valA;

        public AMyValue9a(int x) {
            checkDeoptHuge(22);
            this(x, 0);
            checkDeoptHuge(23);
        }

        public AMyValue9a(int i, int unused1) {
            checkDeoptHuge(24);
            valA = new MyValue8a(i);
            checkDeoptHuge(25);
        }

        public AMyValue9a(int x, int unused1, int unused2) {
            checkDeoptHuge(18);
            this(x, 0, 0, 0);
            checkDeoptHuge(19);
        }

        public AMyValue9a(int i, int unused1, int unused2, int unused3) {
            checkDeoptHuge(20);
            valA = MyValue8a.valueOf(i);
            checkDeoptHuge(21);
        }

        public String toString() {
            return "valA: [" + valA + "]";
        }
    }

    // Constructor calling another constructor of a different value class
    static value class MyValue9a extends AMyValue9a {
        MyValue8a val;

        public MyValue9a(int x) {
            checkDeoptHuge(18);
            this(x, 0);
            checkDeoptHuge(19);
        }

        public MyValue9a(int i, int unused1) {
            checkDeoptHuge(20);
            val = new MyValue8a(i);
            checkDeoptHuge(21);
            super(i, unused1);
            checkDeoptHuge(26);
        }

        public MyValue9a(int x, int unused1, int unused2) {
            checkDeoptHuge(14);
            this(x, 0, 0, 0);
            checkDeoptHuge(15);
        }

        public MyValue9a(int i, int unused1, int unused2, int unused3) {
            checkDeoptHuge(16);
            val = MyValue8a.valueOf(i);
            checkDeoptHuge(17);
            super(i, unused1, unused2, unused3);
            checkDeoptHuge(27);
        }

        public String toString() {
            return "val: [" + val + "]";
        }
    }

    // Constructor with a loop
    static value class MyValue10 {
        int x;
        int y;

        public MyValue10(int x, int cnt) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            int res = 0;
            for (int i = 0; i < cnt; ++i) {
                checkDeopt(2);
                res += x;
                checkDeopt(3);
            }
            checkDeopt(4);
            this.y = res;
            checkDeopt(5);
            super();
            checkDeopt(6);
        }

        public String toString() {
            return "x: " + x + ", y: " + y;
        }
    }

    // Constructor with a loop
    static abstract value class AMyValue10a {
        int a;
        int b;

        public AMyValue10a(int a, int cnt) {
            checkDeopt(7);
            this.a = a;
            checkDeopt(8);
            int res = 0;
            for (int i = 0; i < cnt; ++i) {
                checkDeopt(9);
                res += a;
                checkDeopt(10);
            }
            checkDeopt(11);
            this.b = res;
            checkDeopt(12);
            super();
            checkDeopt(13);
        }

        public String toString() {
            return "x: " + a + ", y: " + b;
        }
    }

    // Constructor with a loop
    static value class MyValue10a extends AMyValue10a {
        int x;
        int y;

        public MyValue10a(int x, int cnt) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            int res = 0;
            for (int i = 0; i < cnt; ++i) {
                checkDeopt(2);
                res += x;
                checkDeopt(3);
            }
            checkDeopt(4);
            this.y = res;
            checkDeopt(5);
            super(x, cnt);
            checkDeopt(6);
        }

        public String toString() {
            return "x: " + x + ", y: " + y;
        }
    }

    // Value class with recursive field definitions
    static value class MyValue11 {
        int x;
        MyValue11 val1;
        MyValue11 val2;

        public MyValue11(int x) {
            checkDeopt(0);
            this.x = x;
            checkDeopt(1);
            this.val1 = new MyValue11(x + 1, 2, 3, 4, 5);
            checkDeopt(6);
            this.val2 = new MyValue11(x + 2, 7, 8, 9, 10);
            checkDeopt(11);
        }

        public MyValue11(int x, int deoptNum1, int deoptNum2, int deoptNum3, int deoptNum4) {
            checkDeopt(deoptNum1);
            this.x = x;
            checkDeopt(deoptNum2);
            this.val1 = null;
            checkDeopt(deoptNum3);
            this.val2 = null;
            checkDeopt(deoptNum4);
        }

        public String toString() {
            return "x: " + x + ", val1: [" + (val1 != this ? val1 : "this") + "], val2: [" + (val2 != this ? val2 : "this") + "]";
        }
    }

    // Value class with recursive field definitions
    static abstract value class AMyValue11a {
        int y;
        AMyValue11a valA1;
        AMyValue11a valA2;

        public AMyValue11a(int y) {
            checkDeoptHuge(19);
            this.y = y;
            checkDeoptHuge(20);
            this.valA1 = new MyValue11a(y + 1, 21, 22, 23, 24, 25);
            checkDeoptHuge(26);
            this.valA2 = new MyValue11a(y + 2, 27, 28, 29, 30, 31);
            checkDeoptHuge(36);
        }

        public AMyValue11a(int y, int deoptNum1, int deoptNum2, int deoptNum3, int deoptNum4) {
            checkDeoptHuge(deoptNum1);
            this.y = y;
            checkDeoptHuge(deoptNum2);
            this.valA1 = null;
            checkDeoptHuge(deoptNum3);
            this.valA2 = null;
            checkDeoptHuge(deoptNum4);
        }

        public String toString() {
            return "x: " + y + ", val1: [" + (valA1 != this ? valA1 : "this") + "], val2: [" + (valA2 != this ? valA2 : "this") + "]";
        }
    }

    // Value class with recursive field definitions
    static value class MyValue11a extends AMyValue11a {
        int x;
        MyValue11a val1;
        MyValue11a val2;

        public MyValue11a(int x) {
            checkDeoptHuge(0);
            this.x = x;
            checkDeoptHuge(1);
            this.val1 = new MyValue11a(x + 1, 2, 3, 4, 5, 6);
            checkDeoptHuge(7);
            this.val2 = new MyValue11a(x + 2, 8, 9, 10, 11, 12);
            checkDeoptHuge(17);
            super(x);
            checkDeoptHuge(18);
        }

        public MyValue11a(int x, int deoptNum1, int deoptNum2, int deoptNum3, int deoptNum4, int deoptNum5) {
            checkDeoptHuge(deoptNum1);
            this.x = x;
            checkDeoptHuge(deoptNum2);
            this.val1 = null;
            checkDeoptHuge(deoptNum3);
            this.val2 = null;
            checkDeoptHuge(deoptNum4);
            super(x, deoptNum5 + 1, deoptNum5 + 2, deoptNum5 + 3, deoptNum5 + 4);
            checkDeoptHuge(deoptNum5);
        }

        public String toString() {
            return "x: " + x + ", val1: [" + (val1 != this ? val1 : "this") + "], val2: [" + (val2 != this ? val2 : "this") + "]";
        }
    }

    static value class MyValue12 {
        Object o;

        public MyValue12() {
            checkDeopt(0);
            this.o = new Object();
            checkDeopt(1);
            super();
            checkDeopt(2);
        }
    }

    static abstract value class MyAbstract13b {
        MyAbstract13b() {
            checkDeopt(4);
            super();
            checkDeopt(5);
        }
    }

    static abstract value class MyAbstract13a extends MyAbstract13b {
        MyAbstract13a() {
            checkDeopt(2);
            super();
            checkDeopt(3);
        }
    }

    static value class MyValue13 extends MyAbstract13a {
        public MyValue13() {
            checkDeopt(0);
            super();
            checkDeopt(1);
        }
    }

    static value class MyValue14 {
        private Object o;

        public MyValue14(Object o) {
            this.o = o;
        }

        public static MyValue14 get(Object o) {
            return new MyValue14(getO(o));
        }

        public static Object getO(Object obj) {
            return obj;
        }
    }

    static abstract value class MyAbstract15 {
        int i;

        public MyAbstract15(int i) {
            checkDeoptBig(2);
            this.i = 34;
            checkDeoptBig(3);
            super();
            checkDeoptBig(4);
            MyValue15 v = new MyValue15();
            checkDeoptBig(11);
            foo(v);
            checkDeoptBig(13);
        }

        MyValue15 foo(MyValue15 v) {
            checkDeoptBig(12);
            return v;
        }

        public MyAbstract15() {
            checkDeoptBig(17);
            this.i = 4;
            checkDeoptBig(18);
            super();
            checkDeoptBig(19);
        }
    }

    static value class MyValue15 extends MyAbstract15 {
        int i;

        public MyValue15(int i) {
            checkDeoptBig(0);
            this.i = 3;
            checkDeoptBig(1);
            super(i);
            checkDeoptBig(14);
            MyValue15 v = new MyValue15();
            checkDeoptBig(21);
            getO(v);
            checkDeoptBig(23);
        }


        public MyValue15() {
            checkDeoptBig( 15);
            this.i = 43;
            checkDeoptBig(16);
            super();
            checkDeoptBig(20);
        }

        static Object getO(Object o) {
            checkDeoptBig(22);
            return o;
        }
    }

    static abstract value class MyAbstract16 {
        int i;
        public MyAbstract16() {
            checkDeoptBig(8);
            this.i = 4;
            checkDeoptBig(9);
            super();
            checkDeoptBig(10);
        }

        public MyAbstract16(int i) {
            checkDeoptBig(2);
            this.i = 34;
            checkDeoptBig(3);
            super();
            checkDeoptBig(4);
            getV();
            checkDeoptBig(13);
        }

        public MyAbstract16(boolean ignore) {
            checkDeoptBig(17);
            this.i = 4;
            checkDeoptBig(18);
            super();
            checkDeoptBig(19);
        }

        public static MyValue16 getV() {
            checkDeoptBig(5);
            MyValue16 v = new MyValue16();
            checkDeoptBig(12);
            return v;
        }
    }

    static value class MyValue16 extends MyAbstract16 {
        int i;

        public MyValue16(int i) {
            checkDeoptBig(0);
            this.i = 3;
            checkDeoptBig(1);
            super(i);
            checkDeoptBig(14);
            MyValue16 v = new MyValue16(true);
            checkDeoptBig(21);
            getO(v);
            checkDeoptBig(23);
        }

        public MyValue16() {
            checkDeoptBig( 6);
            this.i = 34;
            checkDeoptBig(7);
            super();
            checkDeoptBig(11);
        }

        public MyValue16(boolean ignore) {
            checkDeoptBig( 15);
            this.i = 43;
            checkDeoptBig(16);
            super(true);
            checkDeoptBig(20);
        }

        static Object getO(Object o) {
            checkDeoptBig(22);
            return o;
        }
    }

    public static int test1(int x) {
        MyValue1 val = new MyValue1(x);
        checkDeopt(3);
        return val.x;
    }

    public static int test1a(int x) {
        MyValue1a val = new MyValue1a(x);
        checkDeopt(3);
        return val.x;
    }

    public static int test1b(int x) {
        MyValue1b val = new MyValue1b(x);
        checkDeopt(6);
        return val.x;
    }

    public static MyValue1 helper1(int x) {
        return new MyValue1(x);
    }

    public static MyValue1a helper1a(int x) {
        return new MyValue1a(x);
    }

    public static MyValue1b helper1b(int x) {
        return new MyValue1b(x);
    }

    public static Object test2(int x) {
        return helper1(x);
    }
    public static Object test2a(int x) {
        return helper1a(x);
    }
    public static Object test2b(int x) {
        return helper1b(x);
    }

    public static Object test3(int limit) {
        MyValue1 res = null;
        for (int i = 0; i <= 10; ++i) {
            res = new MyValue1(i);
            checkDeopt(3);
        }
        return res;
    }

    public static Object test3a(int limit) {
        MyValue1a res = null;
        for (int i = 0; i <= 10; ++i) {
            res = new MyValue1a(i);
            checkDeopt(3);
        }
        return res;
    }

    public static Object test3b(int limit) {
        MyValue1b res = null;
        for (int i = 0; i <= 10; ++i) {
            res = new MyValue1b(i);
            checkDeopt(6);
        }
        return res;
    }

    public static MyValue1 test4(int x) {
        MyValue1 v = new MyValue1(x);
        checkDeopt(3);
        v = new MyValue1(x);
        return v;
    }

    public static MyValue1a test4a(int x) {
        MyValue1a v = new MyValue1a(x);
        checkDeopt(3);
        v = new MyValue1a(x);
        return v;
    }

    public static MyValue1b test4b(int x) {
        MyValue1b v = new MyValue1b(x);
        checkDeopt(6);
        v = new MyValue1b(x);
        return v;
    }

    public static int test5(int x) {
        MyValue2 val = new MyValue2(x);
        checkDeopt(3);
        return val.x;
    }

    public static MyValue2 helper2(int x) {
        return new MyValue2(x);
    }

    public static Object test6(int x) {
        return helper2(x);
    }

    public static Object test7(int limit) {
        MyValue2 res = null;
        for (int i = 0; i <= 10; ++i) {
            res = new MyValue2(i);
            checkDeopt(3);
        }
        return res;
    }

    public static MyValue2 test8(int x) {
        MyValue2 v = new MyValue2(x);
        checkDeopt(3);
        v = new MyValue2(x);
        return v;
    }

    public static int test9(int x) {
        MyValue3 val = new MyValue3(x);
        checkDeopt(9);
        return val.x;
    }

    public static int test9a(int x) {
        MyValue3a val = new MyValue3a(x);
        checkDeopt(9);
        return val.x + val.y;
    }

    public static MyValue3 helper3(int x) {
        return new MyValue3(x);
    }

    public static Object test10(int x) {
        return helper3(x);
    }

    public static MyValue3a helper3a(int x) {
        return new MyValue3a(x);
    }

    public static Object test10a(int x) {
        return helper3a(x);
    }

    public static Object test11(int limit) {
        MyValue3 res = null;
        for (int i = 0; i <= 10; ++i) {
            checkDeopt(9);
            res = new MyValue3(i);
        }
        return res;
    }

    public static Object test11a(int limit) {
        MyValue3a res = null;
        for (int i = 0; i <= 10; ++i) {
            checkDeopt(9);
            res = new MyValue3a(i);
        }
        return res;
    }

    public static MyValue3 test12(int x) {
        MyValue3 v = new MyValue3(x);
        checkDeopt(9);
        v = new MyValue3(x);
        return v;
    }

    public static MyValue3a test12a(int x) {
        MyValue3a v = new MyValue3a(x);
        checkDeopt(9);
        v = new MyValue3a(x);
        return v;
    }

    public static MyValue4 test13(int x) {
        return new MyValue4(x);
    }

    public static MyValue4a test13a(int x) {
        return new MyValue4a(x);
    }

    public static MyValue5 test14(int x, boolean b) {
        return new MyValue5(x, b);
    }

    public static MyValue5a test14a(int x, boolean b) {
        return new MyValue5a(x, b);
    }

    public static Object test15(int x) {
        return new MyValue6(x);
    }

    public static Object test15a(int x) {
        return new MyValue6a(x);
    }

    public static Object test15b(int x) {
        return new MyValue6b(x);
    }

    public static Object test16(int x) {
        return new MyValue7(x);
    }

    public static Object test16a(int x) {
        return new MyValue7a(x);
    }

    public static Object test16b(int x) {
        return new MyValue7b(x);
    }

    public static MyValue8 test17(int x) {
        return new MyValue8(x);
    }

    public static MyValue8a test17a(int x) {
        return new MyValue8a(x);
    }

    public static MyValue8 test18(int x) {
        return new MyValue8(x, 0);
    }

    public static MyValue8a test18a(int x) {
        return new MyValue8a(x, 0);
    }

    public static MyValue8 test19(int x) {
        return MyValue8.valueOf(x);
    }

    public static MyValue8a test19a(int x) {
        return MyValue8a.valueOf(x);
    }

    public static AMyValue8a test19b(int x) {
        return AMyValue8a.valueOf(x);
    }

    public static MyValue9 test20(int x) {
        return new MyValue9(x);
    }

    public static MyValue9a test20a(int x) {
        return new MyValue9a(x);
    }

    public static MyValue9 test21(int x) {
        return new MyValue9(x, 0);
    }

    public static MyValue9a test21a(int x) {
        return new MyValue9a(x, 0);
    }

    public static MyValue9 test22(int x) {
        return new MyValue9(x, 0, 0);
    }

    public static MyValue9a test22a(int x) {
        return new MyValue9a(x, 0, 0);
    }

    public static MyValue9 test23(int x) {
        return new MyValue9(x, 0, 0, 0);
    }

    public static MyValue9a test23a(int x) {
        return new MyValue9a(x, 0, 0, 0);
    }

    public static MyValue10 test24(int x, int cnt) {
        return new MyValue10(x, cnt);
    }

    public static MyValue10a test24a(int x, int cnt) {
        return new MyValue10a(x, cnt);
    }

    public static MyValue11 test25(int x) {
        return new MyValue11(x);
    }

    public static MyValue11a test25a(int x) {
        return new MyValue11a(x);
    }

    public static MyValue12 testObjectCallInsideConstructor() {
        return new MyValue12();
    }

    public static MyValue13 testMultipleAbstract() {
        return new MyValue13();
    }

    public static MyValue14 testCallAsConstructorArgument() {
        return MyValue14.get(o);
    }

    public static MyValue15 testBackAndForthAbstract(int x) {
        return new MyValue15(x);
    }

    public static MyValue16 testBackAndForthAbstract2(int x) {
        return new MyValue16(x);
    }

    private static final MethodHandle MULTIPLE_OCCURRENCES_IN_JVMS = InstructionHelper.buildMethodHandle(MethodHandles.lookup(),
            "multipleOccurrencesInJVMS",
            MethodType.methodType(MyValue1.class, int.class),
            CODE -> {
                Label loopHead = CODE.newLabel();
                Label loopExit = CODE.newLabel();
                CODE.
                        new_(MyValue1.class.describeConstable().get()).
                        dup().
                        // Duplicate the larval oop across multiple local slots
                        astore(1).
                        astore(2).
                        iconst_0().
                        istore(3).
                        labelBinding(loopHead).
                        iload(3).
                        ldc(100).
                        if_icmpge(loopExit).
                        iinc(3, 1).
                        goto_(loopHead).
                        labelBinding(loopExit).
                        aload(2).
                        iload(0).
                        invokespecial(MyValue1.class.describeConstable().get(), "<init>", MethodType.methodType(void.class, int.class).describeConstable().get()).
                        aload(2).
                        areturn();
            });

    public static MyValue1 testMultipleOccurrencesInJVMS(int x) throws Throwable {
        return (MyValue1) MULTIPLE_OCCURRENCES_IN_JVMS.invokeExact(x);
    }

    private static final MethodHandle OSR_LARVAL_LOCAL = InstructionHelper.buildMethodHandle(MethodHandles.lookup(),
            "osrLarvalLocal",
            MethodType.methodType(MyValue1.class, MyValue1.class, int.class),
            CODE -> {
                Label loopHead = CODE.newLabel();
                Label loopExit = CODE.newLabel();
                CODE.
                        new_(MyValue1.class.describeConstable().get()).
                        // Overwrite a parameter with the larval oop
                        astore(0).
                        iconst_0().
                        istore(2).
                        labelBinding(loopHead).
                        iload(2).
                        ldc(100).
                        if_icmpge(loopExit).
                        iinc(2, 1).
                        goto_(loopHead).
                        labelBinding(loopExit).
                        aload(0).
                        iload(1).
                        invokespecial(MyValue1.class.describeConstable().get(), "<init>", MethodType.methodType(void.class, int.class).describeConstable().get()).
                        aload(0).
                        areturn();
            });

    public static MyValue1 testOsrLarvalLocal(int x) throws Throwable {
        MyValue1 dummy = new MyValue1(x - 1);
        return (MyValue1) OSR_LARVAL_LOCAL.invokeExact(dummy, x);
    }

    static final Constructor<MyValue1> MY_VALUE1_CONSTRUCTOR;
    static {
        try {
            MY_VALUE1_CONSTRUCTOR = MyValue1.class.getConstructor(int.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static MyValue1 testReflectionCon(int x) throws Exception {
        return MY_VALUE1_CONSTRUCTOR.newInstance(x);
    }

    public static MyValue1 testReflectionVar(Constructor<MyValue1> constructor, int x) throws Exception {
        return constructor.newInstance(x);
    }

    static final MethodHandle MY_VALUE1_CONSTRUCTOR_HANDLE;
    static {
        try {
            MY_VALUE1_CONSTRUCTOR_HANDLE = MethodHandles.lookup().unreflectConstructor(MY_VALUE1_CONSTRUCTOR);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static MyValue1 testMethodHandleCon(int x) throws Throwable {
        return (MyValue1) MY_VALUE1_CONSTRUCTOR_HANDLE.invoke(x);
    }

    public static MyValue1 testMethodHandleVar(MethodHandle mh, int x) throws Throwable {
        return (MyValue1) mh.invoke(x);
    }

    public static void main(String[] args) throws Throwable {
        Random rand = Utils.getRandomInstance();

        // Randomly exclude some constructors from inlining via the WhiteBox API because CompileCommands don't match on different signatures.
        WHITE_BOX.testSetDontInlineMethod(MyValue1.class.getConstructor(int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue1a.class.getConstructor(int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue1b.class.getConstructor(int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue1.class.getConstructor(int.class, int.class, int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue1a.class.getConstructor(int.class, int.class, int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue1b.class.getConstructor(int.class, int.class, int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue3.class.getConstructor(int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue3.class.getConstructor(int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue8.class.getConstructor(int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue8.class.getConstructor(int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue8.class.getConstructor(int.class, int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue9.class.getConstructor(int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue9.class.getConstructor(int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue9.class.getConstructor(int.class, int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue9.class.getConstructor(int.class, int.class, int.class, int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue11.class.getConstructor(int.class), rand.nextBoolean());
        WHITE_BOX.testSetDontInlineMethod(MyValue11.class.getConstructor(int.class, int.class, int.class, int.class, int.class), rand.nextBoolean());
        int randValue = rand.nextInt(0, 4);
        if (randValue > 0) {
            // Some variation
            WHITE_BOX.testSetDontInlineMethod(MyValue15.class.getConstructor(), rand.nextBoolean());
            WHITE_BOX.testSetDontInlineMethod(MyValue15.class.getConstructor(int.class), rand.nextBoolean());
            WHITE_BOX.testSetDontInlineMethod(MyValue16.class.getConstructor(), rand.nextBoolean());
            WHITE_BOX.testSetDontInlineMethod(MyValue16.class.getConstructor(int.class), rand.nextBoolean());
            if (randValue > 1) {
                WHITE_BOX.testSetDontInlineMethod(MyAbstract15.class.getConstructor(), rand.nextBoolean());
                WHITE_BOX.testSetDontInlineMethod(MyAbstract15.class.getConstructor(int.class), rand.nextBoolean());
                WHITE_BOX.testSetDontInlineMethod(MyAbstract16.class.getConstructor(), rand.nextBoolean());
                WHITE_BOX.testSetDontInlineMethod(MyAbstract16.class.getConstructor(int.class), rand.nextBoolean());
            }
        }

        Integer deoptNum = Integer.getInteger("deoptNum");
        Integer deoptNumBig = Integer.getInteger("deoptNumBig");
        Integer deoptNumHuge = Integer.getInteger("deoptNumHuge");
        if (deoptNum == null) {
            deoptNum = rand.nextInt(deopt.length);
            System.out.println("deoptNum = " + deoptNum);
        }
        if (deoptNumBig == null) {
            deoptNumBig = rand.nextInt(deoptBig.length);
            System.out.println("deoptNumBig = " + deoptNumBig);
        }
        if (deoptNumHuge == null) {
            deoptNumHuge = rand.nextInt(deoptHuge.length);
            System.out.println("deoptNumHuge = " + deoptNumHuge);
        }
        run(0, true);
        for (int x = 1; x <= 50_000; ++x) {
            if (x == 50_000) {
                // Last iteration, trigger deoptimization
                run(x, true);
                deopt[deoptNum] = true;
                deoptBig[deoptNumBig] = true;
                deoptHuge[deoptNumHuge] = true;
                run(x, true);
            } else {
                run(x, false);
            }
        }
    }

    private static void run(int x, boolean doCheck) throws Throwable {
        check(test1(x), x, doCheck);
        check(test1a(x), x, doCheck);
        check(test1b(x), x, doCheck);
        check(test2(x), new MyValue1(x), doCheck);
        check(test2a(x), new MyValue1a(x), doCheck);
        check(test2b(x), new MyValue1b(x), doCheck);
        check(test3(10), new MyValue1(10), doCheck);
        check(test3a(10), new MyValue1a(10), doCheck);
        check(test3b(10), new MyValue1b(10), doCheck);
        check(test4(x), new MyValue1(x), doCheck);
        check(test4a(x), new MyValue1a(x), doCheck);
        check(test4b(x), new MyValue1b(x), doCheck);
        check(test5(x), x, doCheck);
        check(test5(x), x, doCheck);
        check(test6(x), new MyValue2(x), doCheck);
        check(test6(x), new MyValue2(x), doCheck);
        check(test7(10), new MyValue2(10), doCheck);
        check(test8(x), new MyValue2(x), doCheck);
        check(test9(x), x, doCheck);
        check(test9a(x), x + x, doCheck);
        check(test10(x), new MyValue3(x), doCheck);
        check(test10a(x), new MyValue3a(x), doCheck);
        check(test11(10), new MyValue3(10), doCheck);
        check(test11a(10), new MyValue3a(10), doCheck);
        check(test12(x), new MyValue3(x), doCheck);
        check(test12a(x), new MyValue3a(x), doCheck);
        check(test13(x), new MyValue4(x), doCheck);
        check(test13a(x), new MyValue4a(x), doCheck);
        check(test14(x, (x % 2) == 0), new MyValue5(x, (x % 2) == 0), doCheck);
        check(test14a(x, (x % 2) == 0), new MyValue5a(x, (x % 2) == 0), doCheck);
        check(test15(x), new MyValue6(x), doCheck);
        check(test15a(x), new MyValue6a(x), doCheck);
        check(test15b(x), new MyValue6b(x), doCheck);
        check(test16(x), new MyValue7(x), doCheck);
        check(test16a(x), new MyValue7a(x), doCheck);
        check(test16b(x), new MyValue7b(x), doCheck);
        check(test17(x), new MyValue8(x), doCheck);
        check(test17a(x), new MyValue8a(x), doCheck);
        check(test18(x), new MyValue8(x), doCheck);
        check(test18a(x), new MyValue8a(x), doCheck);
        check(test19(x), new MyValue8(x), doCheck);
        check(test19a(x), new MyValue8a(x), doCheck);
        check(test19b(x), new MyValue8a(x), doCheck);
        check(test20(x), new MyValue9(x), doCheck);
        check(test20a(x), new MyValue9a(x), doCheck);
        check(test21(x), new MyValue9(x), doCheck);
        check(test21a(x), new MyValue9a(x), doCheck);
        check(test22(x), new MyValue9(x), doCheck);
        check(test22a(x), new MyValue9a(x), doCheck);
        check(test23(x), new MyValue9(x), doCheck);
        check(test23a(x), new MyValue9a(x), doCheck);
        check(test24(x, x % 10), new MyValue10(x, x % 10), doCheck);
        check(test24a(x, x % 10), new MyValue10a(x, x % 10), doCheck);
        check(test25(x), new MyValue11(x), doCheck);
        check(test25a(x), new MyValue11a(x), doCheck);
        testObjectCallInsideConstructor(); // Creates a new Object each time - cannot compare on equality.
        check(testMultipleAbstract(), new MyValue13(), doCheck);
        check(testCallAsConstructorArgument(), new MyValue14(o), doCheck);
        check(testBackAndForthAbstract(x), new MyValue15(x), doCheck);
        check(testBackAndForthAbstract2(x), new MyValue16(x), doCheck);
        check(testMultipleOccurrencesInJVMS(x), new MyValue1(x), doCheck);
        check(testOsrLarvalLocal(x), new MyValue1(x), doCheck);
        check(testReflectionCon(x), new MyValue1(x), doCheck);
        check(testReflectionVar(MY_VALUE1_CONSTRUCTOR, x), new MyValue1(x), doCheck);
        check(testMethodHandleCon(x), new MyValue1(x), doCheck);
        check(testMethodHandleVar(MY_VALUE1_CONSTRUCTOR_HANDLE, x), new MyValue1(x), doCheck);
    }

    private static void check(Object testResult, Object expectedResult, boolean check) {
        if (check) {
            Asserts.assertEQ(testResult, expectedResult);
        }
    }
}
