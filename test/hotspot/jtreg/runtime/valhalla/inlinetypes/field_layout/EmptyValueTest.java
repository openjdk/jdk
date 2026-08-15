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
 * @compile FieldLayoutAnalyzer.java EmptyValueTest.java
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseNullableNonAtomicValueFlattening EmptyValueTest
 */


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import runtime.valhalla.inlinetypes.field_layout.FieldLayoutAnalyzer;

public class EmptyValueTest {

    static class TestRunner {
        public static void main(String[] args) throws Exception {
            Class testClass = Class.forName("EmptyValueTest");
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

    static value class Empty0 {}

    static class Container0 {
      Empty0 empty;
    }

    static public void test_0() {
      Container0 c = new Container0();
      Asserts.assertNull(c.empty);
      c.empty = new Empty0();
      Asserts.assertNotNull(c.empty);
      c.empty = null;
      Asserts.assertNull(c.empty);
    }

    static public void check_0(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("EmptyValueTest$Container0");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("empty", false);
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULLABLE_NON_ATOMIC_FLAT, f.layoutKind());
    }

    static value class WrappedEmpty1 {
      @NullRestricted
      Empty0 empty = new Empty0();
    }

    static class Container1 {
      WrappedEmpty1 we = new WrappedEmpty1();
    }

    static public void test_1() {
      Container1 c = new Container1();
    }

    static public void check_1(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("EmptyValueTest$Container1");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("we", false);
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULLABLE_ATOMIC_FLAT, f.layoutKind());
    }

    static class Container2 {
      @NullRestricted
      WrappedEmpty1 we;

      Container2() {
          we = new WrappedEmpty1();
          super();
      }
    }

    static public void test_2() {
      Container2 c = new Container2();
    }

    static public void check_2(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("EmptyValueTest$Container2");
        FieldLayoutAnalyzer.FieldBlock f = cl.getFieldFromName("we", false);
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULL_FREE_NON_ATOMIC_FLAT, f.layoutKind());
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
        EmptyValueTest sft = new EmptyValueTest();

        // Execute the test runner in charge of loading all test classes
        ProcessBuilder pb = exec("EmptyValueTest$TestRunner");
        OutputAnalyzer out = new OutputAnalyzer(pb.start());

        if (out.getExitValue() != 0) {
            System.out.print(out.getOutput());
        }
        Asserts.assertEquals(out.getExitValue(), 0, "Something went wrong while running the tests");

        // Get and parse the test output
        FieldLayoutAnalyzer.LogOutput lo = new FieldLayoutAnalyzer.LogOutput(out.asLines());
        FieldLayoutAnalyzer fla =  FieldLayoutAnalyzer.createFieldLayoutAnalyzer(lo);

        // Running tests verification method (check that tests produced the right configuration)
        Class testClass = EmptyValueTest.class;
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
