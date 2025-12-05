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
 * @test
 * @summary AOT cache should exclude old classes when AOT class linking is disabled.
 * @bug 8372045
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build OldClass
 * @build OldClassSupport2
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AppUsesOldClass2 OldClass
 * @run driver OldClassSupport2
 */

import jdk.jfr.Event;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class OldClassSupport2 {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "AppUsesOldClass2";

    public static void main(String[] args) throws Exception {
        // Explicitly disable
        Tester tester1 = new Tester("-XX:-AOTClassLinking");
        tester1.run(new String[] {"AOT", "--two-step-training"} );

        // Full module graph caching is disabled with -Djdk.module.showModuleResolution=true.
        // This will disable AOT class linking.
        Tester tester2 = new Tester("-Djdk.module.showModuleResolution=true");
        tester2.run(new String[] {"AOT", "--two-step-training"} );

        // Heap archiving is disable with -XX:-UseCompressedClassPointers.
        // This will disable AOT class linking.
        Tester tester3 = new Tester("-XX:-UseCompressedClassPointers");
        tester3.run(new String[] {"AOT", "--two-step-training"} );
    }

    static class Tester extends CDSAppTester {
        String extraArg;
        public Tester(String extraArg) {
            super(mainClass);
            this.extraArg = extraArg;
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
                "-Xlog:aot",
                "-Xlog:aot+class=debug",
                extraArg,
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {mainClass};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            String prefix = "aot,class.* = 0x.* app *";
            if (runMode == RunMode.ASSEMBLY) {
                out.shouldMatch(prefix + "AppUsesOldClass2");
                out.shouldNotMatch(prefix + "OldClass");
            }
        }
    }
}

class AppUsesOldClass2 {
    public static void main(String args[]) {
        System.out.println("Old Class Instance: " + new OldClass());
    }
}
