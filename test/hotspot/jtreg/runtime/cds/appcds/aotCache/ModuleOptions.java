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
 * @summary Edge cases of module options for AOT cache
 * @bug 8391443
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build ModuleOptions
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller Hello
 * @run driver ModuleOptions AOT
 */

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.cds.CDSAppTester.RunMode;
import jdk.test.lib.cds.CDSAppTester.TerminateWorkflowException;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class ModuleOptions {
    static final String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static final String mainClass = Hello.class.getName();

    public static void main(String[] args) throws Exception {
        nonExistentPath(args);
        notJar(args);
    }

    // Non-existent paths specified by --module-path should be ignored by AOTClassLocation checks
    static void nonExistentPath(String[] args) throws Exception {
        nonExistentPath(args, true, false);
        nonExistentPath(args, false, true);
        nonExistentPath(args, true, true);
    }

    static void nonExistentPath(String[] args, boolean train, boolean production) throws Exception {
        CDSAppTester tester = new CDSAppTester(mainClass) {
                private boolean useNoSuchFile(RunMode runMode) {
                    if (train && (runMode == RunMode.TRAINING || runMode == RunMode.ASSEMBLY)) {
                        return true;
                    }
                    if (production && runMode == RunMode.PRODUCTION) {
                        return true;
                    }
                    return false;
                }

                @Override
                public String[] vmArgs(RunMode runMode) {
                    if (useNoSuchFile(runMode)) {
                        return new String [] { "--module-path", "nosuchfile", "-Xlog:class+path" };
                    } else {
                        return new String[] {};
                    }
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
                    if (useNoSuchFile(runMode)) {
                        out.shouldContain("Found non-existent module path (ignored): 'nosuchfile'");
                    }
                }
            };
        tester.run(args);
    }

    static void notJar(String[] args) throws Exception {
        Files.writeString(Path.of("file.notjar"), "");
        notJar(args, RunMode.TRAINING);
        notJar(args, RunMode.ASSEMBLY);
        notJar(args, RunMode.PRODUCTION);
    }

    // Add argument {--module-path file.notjar} when the AOT workflow is at the specified
    // testPhase. The workflow should fail at this phase.
    static void notJar(String[] args, RunMode testPhase) throws Exception {
        CDSAppTester tester = new CDSAppTester(mainClass) {
                private boolean isTestPhase(RunMode runMode) {
                    return runMode == testPhase;
                }

                @Override
                public String[] vmArgs(RunMode runMode) {
                    if (isTestPhase(runMode)) {
                        return new String [] { "--module-path", "file.notjar", "-Xlog:class+path" };
                    } else {
                        return new String[] {};
                    }
                }

                @Override
                public String[] appCommandLine(RunMode runMode) {
                    return new String[] { mainClass };
                }

                @Override
                public void checkExecution(OutputAnalyzer out, RunMode runMode) {
                    if (isTestPhase(runMode)) {
                        out.shouldContain("Module path points to a single non-JAR file: 'file.notjar'");
                        out.shouldNotHaveExitValue(0);
                        if (runMode != RunMode.TRAINING) {
                            out.shouldContain("module path contains sub-directories or non-JAR files (incompatible with full module graph");
                        }

                        // We can't go on with the AOT workflow. Terminate it now.
                        throw new TerminateWorkflowException();
                    }
                }
            };

        tester.run(args);
    }
}
