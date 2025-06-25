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
 * @bug 8359436
 * @summary Sanity-check that eager compilation flags are accepted
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver AOTCompileEagerly
 */

import java.io.File;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AOTCompileEagerly {
    static final String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static final String aotConfigFile = "hello.aotconfig";
    static final String aotCacheFile = "hello.aot";
    static final String helloClass = "Hello";

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer out;

        //----------------------------------------------------------------------
        System.out.println("Training Run");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        System.out.println("Assembly Phase");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar);
        out = CDSTestUtils.executeAndLog(pb, "asm");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        System.out.println("Production Run with AOTCache defaults");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod-default");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        System.out.println("Production Run with AOTCache and eager compilation explicitly ON");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+AOTCompileEagerly",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod-eager-on");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        System.out.println("Production Run with AOTCache and eager compilation explicitly OFF");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:-AOTCompileEagerly",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod-eager-off");
        out.shouldHaveExitValue(0);
    }
}
