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
 * @summary Handling of assembly sub-process failure in onestep AOT training.
 * @bug 8382879
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver AssemblySubProcessFailure
 */

import java.io.File;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AssemblySubProcessFailure {
    static String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static String aotConfigFile = "hello.aot.config";
    static String aotCacheFile = "hello.aot";
    static String helloClass = "Hello";

    public static void main(String[] args) throws Exception {
        // The main training run process should report the failure if the AOT assembly
        // sub-process has failed.
        //
        // The easiest way to trigger a failure is to pass a bad VM option, only to the
        // sub-process using JDK_AOT_VM_OPTIONS.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTCacheOutput=" + aotCacheFile,
            "-cp", appJar, helloClass);
        pb.environment().put("JDK_AOT_VM_OPTIONS", "-XX:+NoSuchOption");
        OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "onestep-train");

        out.shouldContain("Hello World");
        out.shouldContain("Temporary AOTConfiguration recorded: " + aotConfigFile);
        out.shouldContain("Child process failed; status = 1");
        out.shouldContain("Picked up JDK_AOT_VM_OPTIONS: -XX:+NoSuchOption");
        out.shouldContain("Unrecognized VM option 'NoSuchOption'");
        out.shouldContain("Error: Could not create the Java Virtual Machine.");
        out.shouldNotContain("AOTCache creation is complete");
        out.shouldHaveExitValue(1);

        if (!(new File(aotConfigFile)).exists()) {
          throw new RuntimeException("Should not delete temporary AOT config file when child process fails: " + aotConfigFile);
        }
    }
}
