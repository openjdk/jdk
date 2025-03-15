/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test various test cases for archived MethodHandle and VarHandle objects.
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @comment TODO ...tested only against G1
 * @requires vm.gc.G1
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build MethodHandleTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar mh.jar
 *             MethodHandleTestApp MethodHandleTestApp$A
 * @run driver MethodHandleTest AOT
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class MethodHandleTest {
    static final String appJar = ClassFileInstaller.getJarPath("mh.jar");
    static final String mainClass = "MethodHandleTestApp";

    public static void main(String[] args) throws Exception {
        Tester t = new Tester();
        t.run(args);
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {
                    "-Xlog:gc,cds+class=debug",
                    "-XX:AOTInitTestClass=MethodHandleTestApp",
                    "-Xlog:cds+map,cds+map+oops=trace:file=cds.oops.txt:none:filesize=0",
                };
            } else {
                return new String[] {};
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                runMode.toString(),
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            out.shouldHaveExitValue(0);

            if (!runMode.isProductionRun()) {
                // MethodHandleTestApp should be initialized in the assembly phase as well,
                // due to -XX:AOTInitTestClass.
                out.shouldContain("MethodHandleTestApp.<clinit>");
            } else {
                // Make sure MethodHandleTestApp is aot-initialized in the production run.
                out.shouldNotContain("MethodHandleTestApp.<clinit>");
            }
        }
    }
}

class MethodHandleTestApp {
    static int state_A;

    static class A {
        public void virtualMethod() {}

        public static void staticMethod() {
            System.out.println("MethodHandleTestApp$A.staticMethod()");
            state_A *= 2;
        }

        static {
            System.out.println("MethodHandleTestApp$A.<clinit>");
            state_A += 3;
        }
    }

    static MethodHandle staticMH;
    static MethodHandle virtualMH;

    static {
        System.out.println("MethodHandleTestApp.<clinit>");

        try {
            setupCachedStatics();
        } catch (Throwable t) {
            throw new RuntimeException("Unexpected exception", t);
        }
    }

    static void setupCachedStatics() throws Throwable {
        MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        virtualMH = LOOKUP.findVirtual(A.class, "virtualMethod", MethodType.methodType(void.class));

        // Make sure A is initialized before create staticMH, but staticMH
        // should still include the init barrier even if A has been initialized.
        A.staticMethod();
        staticMH = LOOKUP.findStatic(A.class, "staticMethod", MethodType.methodType(void.class));
    }

    private static Object invoke(MethodHandle mh, Object ... args) {
        try {
            for (Object o : args) {
                mh = MethodHandles.insertArguments(mh, 0, o);
            }
            return mh.invoke();
        } catch (Throwable t) {
            throw new RuntimeException("Unexpected exception", t);
        }
    }

    public static void main(String[] args) throws Throwable {
        boolean isProduction = args[0].equals("PRODUCTION");

        testMethodHandles(isProduction);
    }


    static void testMethodHandles(boolean isProduction) throws Throwable {
        state_A = 0;

        // (1) Invoking virtual method handle should not initialize the class
        try {
            virtualMH.invoke(null);
            throw new RuntimeException("virtualMH.invoke(null) must not succeed");
        } catch (NullPointerException t) {
            System.out.println("Expected: " + t);
        }

        if (isProduction) {
            if (state_A != 0) {
                throw new RuntimeException("state_A should be 0 but is: " + state_A);
            }
        }

        // (2) Invoking static method handle must ensure A is initialized.
        invoke(staticMH);
        if (isProduction) {
            if (state_A != 6) {
                // A.<clinit> must be executed before A.staticMethod.
                throw new RuntimeException("state_A should be 6 but is: " + state_A);
            }
        }
    }
}
