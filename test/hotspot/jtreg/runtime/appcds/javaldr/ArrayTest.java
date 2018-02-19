/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test the ability to archive array classes and load them from the archive
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds
 * @modules jdk.jartool/sun.tools.jar
 * @compile ArrayTestHelper.java
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main ArrayTest
 */

import java.util.List;
import java.util.ArrayList;
import jdk.test.lib.process.OutputAnalyzer;

public class ArrayTest {

    static String arrayClasses[] = {
        "ArrayTestHelper",
        "[Ljava/lang/Comparable;",
        "[I",
        "[[[Ljava/lang/Object;",
        "[[B"
    };

    public static void main(String[] args) throws Exception {
        JarBuilder.build("arrayTestHelper", "ArrayTestHelper");

        String appJar = TestCommon.getTestJar("arrayTestHelper.jar");
        JarBuilder.build(true, "WhiteBox", "sun/hotspot/WhiteBox");
        String whiteBoxJar = TestCommon.getTestJar("WhiteBox.jar");
        String bootClassPath = "-Xbootclasspath/a:" + whiteBoxJar;

        // create an archive containing array classes
        OutputAnalyzer output = TestCommon.dump(appJar, TestCommon.list(arrayClasses), bootClassPath, "-verbose:class");
        // we currently don't support array classes during CDS dump
        output.shouldContain("Preload Warning: Cannot find [Ljava/lang/Comparable;")
              .shouldContain("Preload Warning: Cannot find [I")
              .shouldContain("Preload Warning: Cannot find [[[Ljava/lang/Object;")
              .shouldContain("Preload Warning: Cannot find [[B");

        List<String> argsList = new ArrayList<String>();
        argsList.add("-XX:+UnlockDiagnosticVMOptions");
        argsList.add("-XX:+WhiteBoxAPI");
        argsList.add("-cp");
        argsList.add(appJar);
        argsList.add(bootClassPath);
        argsList.add("-verbose:class");
        argsList.add("ArrayTestHelper");
        // the following are input args to the ArrayTestHelper.
        // skip checking array classes during run time
        for (int i = 0; i < 1; i++) {
            argsList.add(arrayClasses[i]);
        }
        String[] opts = new String[argsList.size()];
        opts = argsList.toArray(opts);
        TestCommon.run(opts).assertNormalExit();
    }
}
