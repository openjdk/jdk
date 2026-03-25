/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

import jdk.test.lib.Asserts;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @summary Test of NullRestricted, Strict and LooselyConsistentValue annotations
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 * @library /test/lib
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI runtime.valhalla.inlinetypes.AnnotationsTests
 */

 public class AnnotationsTests {
    private static final WhiteBox WHITEBOX = WhiteBox.getWhiteBox();
    private static final boolean UseNullableValueFlattening = WHITEBOX.getBooleanVMFlag("UseNullableValueFlattening");

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] args) {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        AnnotationsTests tests = new AnnotationsTests();
        Class c = tests.getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().startsWith("test_")) {
                try {
                    System.out.println("Running " + m.getName());
                  m.invoke(tests);
                } catch (Throwable t) {
                  t.printStackTrace();
                  throw new RuntimeException(t);
                }
            }
        }
    }

    // Test invalid usage of @LooselyConsistentValue on an identity class
    @LooselyConsistentValue
    static class BadClass4 {

    }

    void test_4() {
        Throwable exception = null;
        try {
            BadClass4 tc = new BadClass4();
        } catch (ClassFormatError e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNotNull(exception, "Failed to detect illegal use of @LooselyConsistentValue");
    }

    // Test field flattening of @NullRestricted annotated fields

    @LooselyConsistentValue
    static value class ValueClass5 {
        int i = 0;
    }

    static class GoodClass5 {
        ValueClass5 f0 = new ValueClass5();

        @NullRestricted
        ValueClass5 f1;

        GoodClass5() {
            f1 = new ValueClass5();
            super();
        }
    }

    void test_5() {
        Throwable exception = null;
        try {
            GoodClass5 vc = new GoodClass5();
            Field f0 = vc.getClass().getDeclaredField("f0");
            if (UseNullableValueFlattening) {
                Asserts.assertTrue(UNSAFE.isFlatField(f0), "Flat field expected, but field is not flat");
            } else {
                Asserts.assertFalse(UNSAFE.isFlatField(f0), "Unexpected flat field");
            }
            Field f1 = vc.getClass().getDeclaredField("f1");
            Asserts.assertTrue(UNSAFE.isFlatField(f1), "Flat field expected, but field is not flat");
        } catch (IncompatibleClassChangeError e) {
            exception = e;
            System.out.println("Received " + e);
        } catch(NoSuchFieldException e) {
            Asserts.fail("Test error");
        }
        Asserts.assertNull(exception, "Unexpected exception: " + exception);
    }


    // Test detection/handling of circularity

    static value class ValueClass6a {
        @NullRestricted
        ValueClass6b val = new ValueClass6b();
    }

    static value class ValueClass6b {
        @NullRestricted
        ValueClass6a val = new ValueClass6a();
    }

    static class BadClass6 {
        @NullRestricted
        ValueClass6a val;

        BadClass6() {
            val = new ValueClass6a();
            super();
        }
    }

    void test_6() {
        Throwable exception = null;
        try {
            BadClass6 bc = new BadClass6();
        } catch (StackOverflowError e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNotNull(exception, "Failed to trigger infinite recursion");
    }

    // Test null restricted static field
    static value class ValueClass7 {
        int i = 0;
    }

    static class GoodClass7 {
        @NullRestricted
        static ValueClass7 sval = new ValueClass7();
    }

    void test_7() {
        Throwable exception = null;
        try {
            ValueClass7 val = GoodClass7.sval;
            Asserts.assertNotNull(val, "Unexpected null value");
        } catch (Throwable e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNull(exception, "Unexpected exception: " + exception);
    }

    // Test circularity on static fields
    static value class ValueClass8 {
        @NullRestricted
        static ValueClass8 sval = new ValueClass8();
    }

    void test_8() {
        Throwable exception = null;
        try {
            ValueClass8 val = ValueClass8.sval;
            Asserts.assertNotNull(val, "Unexpected null value");
        } catch (Throwable e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNull(exception, "Unexpected exception: " + exception);
    }

    // Test that writing null to a @NullRestricted non-static field throws an exception
    static value class ValueClass9 {
        int i = 0;
    }

    static class GoodClass9 {
        @NullRestricted
        ValueClass9 val;

        GoodClass9() {
            val = new ValueClass9();
            super();
        }
    }

    void test_9() {
        Throwable exception = null;
        try {
            GoodClass9 gc = new GoodClass9();
            gc.val = null;
        } catch(NullPointerException e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNotNull(exception, "Expected NullPointerException not received");
    }

    // Test that writing null to a @NullRestricted static field throws an exception
    static value class ValueClass10 {
        @NullRestricted
        static ValueClass10 sval = new ValueClass10();
    }

    void test_10() {
        Throwable exception = null;
        try {
            ValueClass10.sval = null;
        } catch(NullPointerException e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNotNull(exception, "Expected NullPointerException not received");
    }

    // Test illegal use of @NullRestricted on a primitive field
    static class BadClass12 {
        @NullRestricted
        int i;

        BadClass12() {
            i = 0;
            super();
        }
    }
    void test_12() {
        Throwable exception = null;
        try {
            BadClass12 val = new BadClass12();
            System.out.println(val);
        } catch(ClassFormatError e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNotNull(exception, "Expected ClassFormatError not received");
    }

    // Test illegal use of @NullRestricted on an array field
    static class BadClass13 {
        @NullRestricted
        int[] intArray;

        BadClass13() {
            intArray = new int[1];
            super();
        }
    }
    void test_13() {
        Throwable exception = null;
        try {
            BadClass13 val = new BadClass13();
            System.out.println(val);
        } catch(ClassFormatError e) {
            exception = e;
            System.out.println("Received " + e);
        }
        Asserts.assertNotNull(exception, "Expected ClassFormatError not received");
    }

 }

