/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Try different combination of mismatched UseAppCDS between dump time and run time.
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @compile test-classes/CheckIfShared.java
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main MismatchedUseAppCDS
 */

import jdk.test.lib.process.OutputAnalyzer;

public class MismatchedUseAppCDS {
  public static void main(String[] args) throws Exception {
    String wbJar = JarBuilder.build(true, "WhiteBox", "sun/hotspot/WhiteBox");
    String use_whitebox_jar = "-Xbootclasspath/a:" + wbJar;

    String appJar = JarBuilder.build("MismatchedUseAppCDS", "CheckIfShared");

    OutputAnalyzer output;

    // (1): dump with -XX:+UseAppCDS, but run with -XX:-UseAppCDS
    TestCommon.testDump(appJar, TestCommon.list("CheckIfShared"),
                        // command-line arguments ...
                        "-XX:+UseAppCDS",
                        use_whitebox_jar);

    output = TestCommon.exec(appJar,
                             // command-line arguments ...
                             use_whitebox_jar,
                             "-XX:-UseAppCDS",
                             "-XX:+UnlockDiagnosticVMOptions",
                             "-XX:+WhiteBoxAPI",
                             "CheckIfShared", "true");
    TestCommon.checkExec(output);

    // (2): dump with -XX:-UseAppCDS, but run with -XX:+UseAppCDS
    TestCommon.testDump(appJar, TestCommon.list("CheckIfShared"),
                        // command-line arguments ...
                        "-XX:-UseAppCDS",
                        use_whitebox_jar);

    output = TestCommon.exec(appJar,
                             // command-line arguments ...
                             use_whitebox_jar,
                             "-XX:+UseAppCDS",
                             "-XX:+UnlockDiagnosticVMOptions",
                             "-XX:+WhiteBoxAPI",
                             "CheckIfShared", "true");
    TestCommon.checkExec(output);
  }
}
