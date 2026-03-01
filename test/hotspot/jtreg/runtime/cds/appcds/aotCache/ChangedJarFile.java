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
 *
 */

/*
 * @test
 * @summary AOT cache should be rejected if JAR file(s) in the classpath have changed
 * @bug 8377932
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build ChangedJarFile
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar MyTestApp OtherClass
 * @run driver ChangedJarFile AOT
 */

import jdk.jfr.Event;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class ChangedJarFile {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = MyTestApp.class.getName();

    public static void main(String[] args) throws Exception {
        // Train and run with unchanged JAR file (which has OtherClass.class)
        Tester tester = new Tester();
        tester.run(args);

        // Run again with changed JAR file (which doesn't have OtherClass.class anymore)
        ClassFileInstaller.writeJar(appJar, "MyTestApp");

        // First disable AOT cache to verify test login
        tester.productionRun(new String[] {"-XX:AOTMode=off"},
                             new String[] {"jarHasChanged"});

        // Now see if the AOT cache will be automatically disabled
        OutputAnalyzer out =
            tester.productionRun(new String[] {"-XX:AOTMode=auto", "-Xlog:aot"},
                                 new String[] {"jarHasChanged"});
        out.shouldMatch("This file is not the one used while building the " +
                        "AOT cache: '.*app.jar', timestamp has changed, size has changed");
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
        public String[] appCommandLine(RunMode runMode) {
            return new String[] { mainClass };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {

        }
    }


}

class MyTestApp {
    public static void main(String args[]) {
        boolean jarHasChanged = (args.length != 0);

        System.out.println("JAR has changed = " + (jarHasChanged));
        Class c = null;
        try {
            c = Class.forName("OtherClass");
            System.out.println("Other class = " + c);
        } catch (Throwable t) {
            if (!jarHasChanged) {
                throw new RuntimeException("OtherClass should have been loaded because JAR has not been changed yet", t);
            }
        }

        if (jarHasChanged && c != null) {
            throw new RuntimeException("OtherClass should not be in JAR file");
        }
    }
}

class OtherClass {}
