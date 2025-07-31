/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary CDS should fail to load if production time GC flags do not match training run.
 * @requires vm.flagless
 * @requires vm.cds.write.archived.java.heap
 * @library /test/jdk/lib/testlibrary /test/lib
 * @build OldClass
 * @build LeydenAndOldClasses
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AppUsesOldClass OldClass
 * @run driver LeydenAndOldClasses
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class LeydenAndOldClasses {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "AppUsesOldClass";

    public static void main(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.run(new String[] {"AOT"} );
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
            return new String[] {"-Xlog:aot+class=debug", "-Xlog:cds+class=debug"};
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {"-Xlog:cds+class=debug", mainClass};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("OldClass@");
            } else if (runMode == RunMode.TRAINING) {
                // When PreloadSharedClasses is enabled, we can safely archive old classes. See comments around
                // CDSConfig::preserve_all_dumptime_verification_states().
                out.shouldMatch("aot,class.* = 0x.* app *OldClass aot-linked");
            }
        }
    }
}

class AppUsesOldClass {
    public static void main(String args[]) {
        System.out.println("Old Class Instance: " + new OldClass());
    }
}
