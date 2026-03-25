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

import java.lang.invoke.*;
import java.lang.reflect.Method;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.Asserts;

import jdk.test.whitebox.WhiteBox;

/*
 * @test id=default
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.*::test*
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering C1
 */

/*
 * @test id=no-TLAB
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:-UseTLAB -Xbatch
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering
 */

/*
 * @test id=no-mono-no-field
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:-UseTLAB -Xbatch -XX:-MonomorphicArrayCheck -XX:-AlwaysIncrementalInline
 *                   -XX:-InlineTypePassFieldsAsArgs -XX:-InlineTypeReturnedAsFields -XX:+UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.*::test*
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering
 */

/*
 * @test id=no-mono-no-field-AII
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:-UseTLAB -Xbatch -XX:-MonomorphicArrayCheck -XX:+AlwaysIncrementalInline
 *                   -XX:-InlineTypePassFieldsAsArgs -XX:-InlineTypeReturnedAsFields -XX:+UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.*::test*
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering
 */

/*
 * @test id=no-mono
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:-UseTLAB -Xbatch -XX:-MonomorphicArrayCheck -XX:-AlwaysIncrementalInline
 *                   -XX:+InlineTypePassFieldsAsArgs -XX:+InlineTypeReturnedAsFields -XX:+UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.*::test*
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering
 */

/*
 * @test id=no-mono-AII
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:-UseTLAB -Xbatch -XX:-MonomorphicArrayCheck -XX:+AlwaysIncrementalInline
 *                   -XX:+InlineTypePassFieldsAsArgs -XX:+InlineTypeReturnedAsFields -XX:+UseArrayFlattening
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.*::test*
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering
 */

/*
 * @test id=no-mono-no-FF
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:-UseTLAB -Xbatch -XX:-MonomorphicArrayCheck -XX:-AlwaysIncrementalInline
 *                   -XX:+InlineTypePassFieldsAsArgs -XX:+InlineTypeReturnedAsFields -XX:+UseArrayFlattening -XX:-UseFieldFlattening
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.*::test*
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering
 */

/*
 * @test id=no-mono-no-FF-AII
 * @summary Test correct execution after deoptimizing from inline type specific runtime calls.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeALot -XX:-UseTLAB -Xbatch -XX:-MonomorphicArrayCheck -XX:+AlwaysIncrementalInline
 *                   -XX:+InlineTypePassFieldsAsArgs -XX:+InlineTypeReturnedAsFields -XX:+UseArrayFlattening -XX:-UseFieldFlattening
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.*::test*
 *                   compiler.valhalla.inlinetypes.TestDeoptimizationWhenBuffering
 */

public class TestDeoptimizationWhenBuffering {
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    static final int COMP_LEVEL_FULL_OPTIMIZATION = 4; // C2 or JVMCI

    @LooselyConsistentValue
    static value class MyValue1 {
        static int cnt = 0;
        int x;
        @NullRestricted
        MyValue2 vtField1;
        MyValue2 vtField2;

        public MyValue1() {
            cnt++;
            x = cnt;
            vtField1 = new MyValue2();
            vtField2 = new MyValue2();
        }

        public MyValue1(int x, MyValue2 vtField1, MyValue2 vtField2) {
            this.x = x;
            this.vtField1 = vtField1;
            this.vtField2 = vtField2;
        }

        public int hash() {
            return x + vtField1.x + vtField2.x;
        }

        public MyValue1 testWithField(int x) {
            return new MyValue1(x, vtField1, vtField2);
        }

        public static MyValue1 makeDefault() {
            return new MyValue1(0, MyValue2.makeDefault(), null);
        }

        public static final MyValue1 DEFAULT = new MyValue1(0, new MyValue2(0), new MyValue2(0));
    }

    @LooselyConsistentValue
    static value class MyValue2 {
        static int cnt = 0;
        int x;

        public MyValue2() {
            cnt++;
            x = cnt;
        }

        public MyValue2(int x) {
            this.x = x;
        }

        public static MyValue2 makeDefault() {
            return new MyValue2(0);
        }
    }

    static {
        try {
            Class<?> clazz = TestDeoptimizationWhenBuffering.class;
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType mt = MethodType.methodType(MyValue1.class);
            test9_mh = lookup.findStatic(clazz, "test9Callee", mt);
            test10_mh = lookup.findStatic(clazz, "test10Callee", mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    MyValue1 test1() {
        return new MyValue1();
    }

    @NullRestricted
    static MyValue1 vtField1 = MyValue1.DEFAULT;

    MyValue1 test2() {
        vtField1 = new MyValue1();
        return vtField1;
    }

    public int test3Callee(MyValue1 vt) {
        return vt.hash();
    }

    int test3() {
        MyValue1 vt = new MyValue1();
        return test3Callee(vt);
    }

    static MyValue1[] vtArray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);

    MyValue1 test4() {
        vtArray[0] = new MyValue1();
        return vtArray[0];
    }

    Object test5(Object[] array) {
        array[0] = new MyValue1();
        return array[0];
    }

    boolean test6(Object obj) {
        MyValue1 vt = new MyValue1();
        return vt == obj;
    }

    Object test7(Object[] obj) {
        return obj[0];
    }

    MyValue1 test8(MyValue1[] obj) {
        return obj[0];
    }

    static final MethodHandle test9_mh;

    public static MyValue1 test9Callee() {
        return new MyValue1();
    }

    MyValue1 test9() throws Throwable {
        return (MyValue1)test9_mh.invokeExact();
    }

    static final MethodHandle test10_mh;
    @NullRestricted
    static final MyValue1 test10Field = new MyValue1();
    static int test10Counter = 0;

    public static MyValue1 test10Callee() {
        test10Counter++;
        return test10Field;
    }

    Object test10() throws Throwable {
        return test10_mh.invoke();
    }

    MyValue1 test11(MyValue1 vt) {
        return vt.testWithField(42);
    }

    MyValue1 vtField2;

    MyValue1 test12() {
        vtField2 = new MyValue1();
        return vtField2;
    }

    public static void main(String[] args) throws Throwable {
        if (args.length > 0) {
            // Compile callees with C1 only, to exercise deoptimization while buffering at method entry
            Asserts.assertEQ(args[0], "C1", "unsupported mode");
            Method m = MyValue1.class.getMethod("testWithField", int.class);
            WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
            m = TestDeoptimizationWhenBuffering.class.getMethod("test3Callee", MyValue1.class);
            WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
            m = TestDeoptimizationWhenBuffering.class.getMethod("test9Callee");
            WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
            m = TestDeoptimizationWhenBuffering.class.getMethod("test10Callee");
            WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
        }

        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 3, MyValue1.DEFAULT);
        va[0] = new MyValue1();
        Object[] oa = new Object[3];
        oa[0] = va[0];
        TestDeoptimizationWhenBuffering t = new TestDeoptimizationWhenBuffering();
        for (int i = 0; i < 100_000; ++i) {
            // Check counters to make sure that we don't accidentally reexecute calls when deoptimizing
            int expected = MyValue1.cnt + MyValue2.cnt + MyValue2.cnt;
            Asserts.assertEQ(t.test1().hash(), expected + 4);
            vtField1 = MyValue1.makeDefault();
            Asserts.assertEQ(t.test2().hash(), expected + 9);
            Asserts.assertEQ(vtField1.hash(), expected + 9);
            Asserts.assertEQ(t.test3(), expected + 14);
            Asserts.assertEQ(t.test4().hash(), expected + 19);
            Asserts.assertEQ(((MyValue1)t.test5(vtArray)).hash(), expected + 24);
            Asserts.assertEQ(t.test6(vtField1), false);
            Asserts.assertEQ(t.test7(((i % 2) == 0) ? va : oa), va[0]);
            Asserts.assertEQ(t.test8(va), va[0]);
            Asserts.assertEQ(t.test8(va), va[0]);
            Asserts.assertEQ(t.test9().hash(), expected + 34);
            int count = test10Counter;
            Asserts.assertEQ(test10Field, t.test10());
            Asserts.assertEQ(t.test10Counter, count + 1);
            Asserts.assertEQ(t.test11(va[0]), va[0].testWithField(42));
            t.vtField2 = MyValue1.makeDefault();
            Asserts.assertEQ(t.test12().hash(), expected + 39);
            Asserts.assertEQ(t.vtField2.hash(), expected + 39);
        }
    }
}
