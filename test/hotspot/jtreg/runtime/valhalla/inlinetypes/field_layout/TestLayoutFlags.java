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
 * @test id=TestLayoutFlags_0
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 0
 */

 /*
 * @test id=TestLayoutFlags_1
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 1
 */

 /* @test id=TestLayoutFlags_2
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 2
 */

 /* @test id=TestLayoutFlags_3
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 3
 */

/* @test id=TestLayoutFlags_4
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 4
 */

/* @test id=TestLayoutFlags_5
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 5
 */

/* @test id=TestLayoutFlags_6
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 6
 */

/* @test id=TestLayoutFlags_7
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java TestLayoutFlags.java
 * @run main runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags 7
 */

package runtime.valhalla.inlinetypes.field_layout;

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

public class TestLayoutFlags {

    static class TestRunner {
        public static void main(String[] args) throws Exception {
            Class testClass = Class.forName("runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags");
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
        byte b0 = 0;
        byte b1 = 0;
    }

    static class Container0 {
        Value0 val0 = new Value0();
    }

    static public void test_0() {
        Container0 c = new Container0();
    }

    static public void check_0(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("runtime/valhalla/inlinetypes/field_layout/TestLayoutFlags$Container0");
        FieldLayoutAnalyzer.FieldBlock f0 = cl.getFieldFromName("val0", false);
        if (useNullableAtomicFlat) {
            Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULLABLE_ATOMIC_FLAT, f0.layoutKind());
        } else {
            Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NON_FLAT, f0.layoutKind());
        }
    }

    static class Container1 {
        @NullRestricted
        volatile Value0 val0;

        Container1() {
            val0 = new Value0();
            super();
        }
    }

    static public void test_1() {
        Container1 c = new Container1();
    }

    static public void check_1(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("runtime/valhalla/inlinetypes/field_layout/TestLayoutFlags$Container1");
        FieldLayoutAnalyzer.FieldBlock f0 = cl.getFieldFromName("val0", false);
        // volatile fields are never flattened
        Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NON_FLAT, f0.layoutKind());
    }

    static class Container2 {
        @NullRestricted
        Value0 val0;

        Container2() {
            val0 = new Value0();
            super();
        }
    }

    static public void test_2() {
        Container2 c = new Container2();
    }

    static public void check_2(FieldLayoutAnalyzer fla) {
        FieldLayoutAnalyzer.ClassLayout cl = fla.getClassLayoutFromName("runtime/valhalla/inlinetypes/field_layout/TestLayoutFlags$Container2");
        FieldLayoutAnalyzer.FieldBlock f0 = cl.getFieldFromName("val0", false);
        if (useNonAtomicFlat) {
            Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NULL_FREE_NON_ATOMIC_FLAT, f0.layoutKind());
        } else {
            Asserts.assertEquals(FieldLayoutAnalyzer.LayoutKind.NON_FLAT, f0.layoutKind());
        }
    }

    static ProcessBuilder exec(String... args) throws Exception {
        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, "--enable-preview");
        Collections.addAll(argsList, "-XX:+UnlockDiagnosticVMOptions");
        Collections.addAll(argsList, "-XX:+UnlockExperimentalVMOptions");
        Collections.addAll(argsList, "-XX:+PrintFieldLayout");
        Collections.addAll(argsList, "-Xshare:off");
        Collections.addAll(argsList, "-Xmx256m");
        Collections.addAll(argsList, useNonAtomicFlat ? "-XX:+UseNullFreeNonAtomicValueFlattening" : "-XX:-UseNullFreeNonAtomicValueFlattening");
        Collections.addAll(argsList, useAtomicFlat ? "-XX:+UseNullFreeAtomicValueFlattening" : "-XX:-UseNullFreeAtomicValueFlattening");
        Collections.addAll(argsList, useNullableAtomicFlat ?  "-XX:+UseNullableAtomicValueFlattening" : "-XX:-UseNullableAtomicValueFlattening");
        Collections.addAll(argsList, "-cp", System.getProperty("java.class.path") + System.getProperty("path.separator") + ".");
        Collections.addAll(argsList, args);
        return ProcessTools.createTestJavaProcessBuilder(argsList);
    }

    static boolean useNonAtomicFlat;
    static boolean useAtomicFlat;
    static boolean useNullableAtomicFlat;

    public static void main(String[] args) throws Exception {

        switch(args[0]) {
            case "0": useNonAtomicFlat = false;
                        useAtomicFlat = false;
                        useNullableAtomicFlat = false;
                        break;
            case "1": useNonAtomicFlat = false;
                        useAtomicFlat = true;
                        useNullableAtomicFlat = true;
                        break;
            case "2": useNonAtomicFlat = false;
                        useAtomicFlat = false;
                        useNullableAtomicFlat = true;
                        break;
            case "3": useNonAtomicFlat = false;
                        useAtomicFlat = true;
                        useNullableAtomicFlat = false;
                        break;
            case "4": useNonAtomicFlat = true;
                        useAtomicFlat = false;
                        useNullableAtomicFlat = false;
                        break;
            case "5": useNonAtomicFlat = true;
                        useAtomicFlat = true;
                        useNullableAtomicFlat = true;
                        break;
            case "6": useNonAtomicFlat = true;
                        useAtomicFlat = false;
                        useNullableAtomicFlat = true;
                        break;
            case "7": useNonAtomicFlat = true;
                        useAtomicFlat = true;
                        useNullableAtomicFlat = false;
                        break;
            default: throw new RuntimeException("Unrecognized configuration");
        }

        // Generate test classes
        TestLayoutFlags vct = new TestLayoutFlags();

        // Execute the test runner in charge of loading all test classes
        ProcessBuilder pb = exec("runtime.valhalla.inlinetypes.field_layout.TestLayoutFlags$TestRunner");
        OutputAnalyzer out = new OutputAnalyzer(pb.start());

        // Checking the status of the process execution before trying to parse the output
        out.shouldHaveExitValue(0);

        // Get and parse the test output
        FieldLayoutAnalyzer.LogOutput lo = new FieldLayoutAnalyzer.LogOutput(out.asLines());
        FieldLayoutAnalyzer fla =  FieldLayoutAnalyzer.createFieldLayoutAnalyzer(lo);

        // Running tests verification method (check that tests produced the right configuration)
        Class testClass = TestLayoutFlags.class;
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
