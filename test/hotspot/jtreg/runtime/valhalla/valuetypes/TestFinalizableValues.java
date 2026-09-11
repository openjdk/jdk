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
 * @test TestFinalizableValues
 * @library /test/lib
 * @enablePreview
 * @compile TestFinalizableValues.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI TestFinalizableValues
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestFinalizableValues {

    static WhiteBox WB = WhiteBox.getWhiteBox();

    public static class TestHelper {
        static boolean finalizer1WasCalled = false;
        static boolean finalizer2WasCalled = false;

        static value class MyVal {
            static volatile boolean finalizerWasCalled = false;
            int i = 0;

            @SuppressWarnings("deprecation")
            protected void finalize() {
                finalizerWasCalled = true;
            }
        }

        static abstract value class MyAbstractVal {
            int i = 0;

            @SuppressWarnings("deprecation")
            protected void finalize() {
                finalizer1WasCalled = true;
            }
        }

        static value class MyVal2 extends MyAbstractVal {}

        static class MyId extends MyAbstractVal {}

        static class MyId2 extends MyAbstractVal {
            int i = 0;

            @SuppressWarnings("deprecation")
            protected void finalize() {
                finalizer2WasCalled = true;
            }
        }

        static void create(Class<?> c) {
            try {
                c.newInstance();
            } catch(InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        public static void main(String[] args) throws ClassNotFoundException {
            Class<?> c = Class.forName(args[0]);
            boolean expectFinalizer1 = Boolean.valueOf(args[1]);
            boolean expectFinalizer2 = Boolean.valueOf(args[2]);

            create(c);
            for (int i = 0; i < 100; i++) {
                System.gc();
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            if (finalizer1WasCalled != expectFinalizer1) {
                throw new RuntimeException("Finalizer1 was "
                                           + (finalizer1WasCalled ? "" : "not ")
                                           + "executed");
            }
            if (finalizer2WasCalled != expectFinalizer2) {
                throw new RuntimeException("Finalizer2 was "
                                           + (finalizer2WasCalled ? "" : "not ")
                                           + "executed");
            }
        }
    }

    static void test(String classname, boolean expectRegistration, String... args) throws IOException {
        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, "--enable-preview");
        Collections.addAll(argsList, "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED");
        Collections.addAll(argsList, "-Dtest.class.path=" + System.getProperty("test.class.path", "."));
        Collections.addAll(argsList, "-XX:+IgnoreUnrecognizedVMOptions");
        Collections.addAll(argsList, "-XX:+TraceFinalizerRegistration");
        Collections.addAll(argsList, "TestFinalizableValues$TestHelper");
        Collections.addAll(argsList, "TestFinalizableValues$TestHelper$" + classname);
        Collections.addAll(argsList, args);
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(argsList);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        if (WB.getBooleanVMFlag("TraceFinalizerRegistration")) {
          if (expectRegistration) {
            out.shouldContain("as finalizable");
          } else {
            out.shouldNotContain("as finalizable");
          }
        }
        out.shouldHaveExitValue(0);
    }
    public static void main(String[] args) throws IOException {
        test("MyVal", false, "false", "false");
        test("MyVal2", false, "false", "false");
        test("MyId", false, "false", "false");
        test("MyId2", true, "false", "true");
    }
}
