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
 * @test Make sure loader constraints are passed from AOT preimage to final image.
 * @bug 8348426
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib
 * @build AOTLoaderConstraintsTest BootClass
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar boot.jar BootClass
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar AOTLoaderConstraintsTestApp.jar AOTLoaderConstraintsTestApp AppClass
 * @run driver AOTLoaderConstraintsTest AOT
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTLoaderConstraintsTest {
    static final String appJar = ClassFileInstaller.getJarPath("AOTLoaderConstraintsTestApp.jar");
    static final String mainClass = "AOTLoaderConstraintsTestApp";

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
            return new String[] {
                "-Xbootclasspath/a:boot.jar",
                "-Xlog:class+loader+constraints=debug",
                "-Xlog:class+path=debug",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            switch (runMode) {
            case RunMode.ASSEMBLY:   // JEP 485 + binary AOTConfiguration -- should load AppClass from preimage
            case RunMode.PRODUCTION:
                out.shouldContain("CDS add loader constraint for class AppClass symbol java/lang/String loader[0] 'app' loader[1] 'bootstrap'");
            }
        }
    }
}

class AOTLoaderConstraintsTestApp {
    public static void main(String args[]) throws Exception {
        AppClass obj = new AppClass();
        obj.func("Hello");
    }
}

class AppClass extends BootClass {
    @Override
    public void func(String s) {
        // This method overrides BootClass, which is loaded by the boot loader.
        // AppClass is loaded by the app loader. To make sure that you cannot use
        // type masquerade attacks, we need to add a loader constraint that says:
        //  app and boot loaders must resolve the symbol "java/lang/String" to the same type.
        super.func(s + " From AppClass");
    }
}
