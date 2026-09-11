/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java StrictFinalTest.java
 * @compile StrictFinalTest.java
 * @run driver jdk.test.lib.helpers.StrictProcessor StrictFinalTest
 *             StrictFinalTest$Container5 StrictFinalTest$Container6
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseNullableNonAtomicValueFlattening StrictFinalTest
 */


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.internal.vm.annotation.LooselyConsistentValue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import jdk.test.lib.Asserts;
import jdk.test.lib.helpers.StrictInit;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import runtime.valhalla.inlinetypes.field_layout.FieldLayoutAnalyzer;

public class StrictFinalTest {

    static class TestRunner {
        public static void main(String[] args) throws Exception {
            Class testClass = Class.forName("StrictFinalTest");
            Asserts.assertNotNull(testClass);
            Method[] testMethods = testClass.getMethods();
            for (Method test : testMethods) {
                if (test.getName().startsWith("test_")) {
                    Asserts.assertTrue(Modifier.isStatic(test.getModifiers()));
                    Asserts.assertTrue(test.getReturnType().equals(Void.TYPE));
                    System.out.println("Running " + test.getName());
                    test.invoke(null);
                }
            }
        }
    }

    @LooselyConsistentValue
    static value class Value0 {
        // bigger than 64 bits even without the null marker
        long i = 0;
        long j = 0;
    }

    static value class Container0 {
        Value0 val0;
        Container0(Value0 v) {
          val0 = v;
        }
    }

    static public void test_0() {
        var c = new Container0(null);
        Asserts.assertNull(c.val0);
        c = new Container0(new Value0());
        Asserts.assertNotNull(c.val0);
    }

    static public void check_0(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container0");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULLABLE_NON_ATOMIC_FLAT, f.layoutKind());
    }

    static value class Value1 {
        // Just big enough to be bigger than 64 bits with the null marker
        int i = 0;
        int j = 0;
    }

    static value class Container1 {
        Value1 val0;
        Container1(Value1 v) {
          val0 = v;
        }
    }

    static public void test_1() {
        var c = new Container1(null);
        Asserts.assertNull(c.val0);
        c = new Container1(new Value1());
        Asserts.assertNotNull(c.val0);
    }

    static public void check_1(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container1");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
        // Value classes' fields are always strict and final, must be flattened
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULLABLE_NON_ATOMIC_FLAT, f.layoutKind());
    }

    static class Container2 {
        Value1 val0;
    }


    static public void test_2() {
        Container2 c = new Container2();
        Asserts.assertNull(c.val0);
        c.val0 = new Value1();
        Asserts.assertNotNull(c.val0);
        c.val0 = null;
        Asserts.assertNull(c.val0);
    }

    static public void check_2(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container2");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
        // Not strict nor final, must not be flattened
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NON_FLAT, f.layoutKind());
    }

    // Test temporarily disabled, to be be re-enabled when strict non-final fields are supported
    //
    // static class Container3 {
    //     @StrictInit
    //     Value1 val0 = new Value1();
    // }


    // static public void test_3() {
    //     Container3 c = new Container3();
    // }

    // static public void check_3(FieldLayoutAnalyzer fla) {
    //     FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container3");
    //     FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
    //     // Not final, must not be flattened
    //     Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NON_FLAT, f.layoutKind());
    // }

    static class Container4 {
        final Value1 val0;
        Container4(Value1 v) {
          val0 = v;
        }
    }


    static public void test_4() {
        var c = new Container4(null);
        Asserts.assertNull(c.val0);
        c = new Container4(new Value1());
        Asserts.assertNotNull(c.val0);
    }

    static public void check_4(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container4");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
        // Not strict, must not be flattened
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NON_FLAT, f.layoutKind());
    }

    static class Container5 {
        @StrictInit
        final Value1 val0;
        Container5(Value1 v) {
          val0 = v;
          super();
        }
    }

    static public void test_5() {
        var c = new Container5(new Value1());
        Asserts.assertNotNull(c.val0);
        c = new Container5(null);
        Asserts.assertNull(c.val0);
    }

    static public void check_5(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container5");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
        // Strict and final, must be flattened
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULLABLE_NON_ATOMIC_FLAT, f.layoutKind());
    }

    static class Container6 {
        @StrictInit
        final Value1 val0;

        Container6() {
            val0 = null;
            super();
        }
    }

    static public void test_6() {
        Container6 c = new Container6();
        Asserts.assertNull(c.val0);
    }

    static public void check_6(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container6");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
        // Strict and final, must be flattened
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULLABLE_NON_ATOMIC_FLAT, f.layoutKind());
    }

    @LooselyConsistentValue
    static value class Container7 {
        Value1 val0;
        Container7(Value1 v) {
          val0 = v;
        }
    }

    static public void test_7() {
        var c = new Container7(new Value1());
        Asserts.assertNotNull(c.val0);
        c = new Container7(null);
        Asserts.assertNull(c.val0);
    }

    static public void check_7(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("StrictFinalTest$Container7");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("val0", false);
        // Container is not atomic, must not flattened
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NON_FLAT, f.layoutKind());
    }

    static ProcessBuilder exec(String... args) throws Exception {
        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, "--enable-preview");
        Collections.addAll(argsList, "-XX:+UnlockDiagnosticVMOptions");
        Collections.addAll(argsList, "-XX:+UnlockExperimentalVMOptions");
        Collections.addAll(argsList, "-XX:+PrintFieldLayout");
        Collections.addAll(argsList, "-Xshare:off");
        Collections.addAll(argsList, "-Xmx256m");
        Collections.addAll(argsList, "-XX:+UseNullableNonAtomicValueFlattening");
        Collections.addAll(argsList, "-cp", System.getProperty("java.class.path") + System.getProperty("path.separator") + ".");
        Collections.addAll(argsList, args);
        return ProcessTools.createTestJavaProcessBuilder(argsList);
    }

    public static void main(String[] args) throws Exception {

        // Generate test classes
        StrictFinalTest sft = new StrictFinalTest();

        // Execute the test runner in charge of loading all test classes
        ProcessBuilder pb = exec("StrictFinalTest$TestRunner");
        OutputAnalyzer out = new OutputAnalyzer(pb.start());

        // Checking the status of the process execution before trying to parse the output
        out.shouldHaveExitValue(0);

        // Get and parse the test output
        FieldLayoutAnalyzer.LogOutput lo = new FieldLayoutAnalyzer.LogOutput(out.asLines());
        FieldLayoutAnalyzer fla =  FieldLayoutAnalyzer.createFieldLayoutAnalyzer(lo);

        // Running tests verification method (check that tests produced the right configuration)
        Class testClass = StrictFinalTest.class;
        Method[] testMethods = testClass.getMethods();
        for (Method test : testMethods) {
            if (test.getName().startsWith("check_")) {
                Asserts.assertTrue(Modifier.isStatic(test.getModifiers()));
                Asserts.assertTrue(test.getReturnType().equals(Void.TYPE));
                test.invoke(null, fla);
            }
        }

        // Verify that all layouts are correct
        try {
            fla.check();
        } catch (Throwable t) {
            System.out.print(out.getOutput());
          throw t;
        }
    }
}
