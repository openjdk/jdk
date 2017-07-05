/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 */


/*
 * @test ClassInitializationTest
 * @bug 8142976
 * @library /testlibrary
 * @compile BadMap50.jasm
 * @build jdk.test.lib.OutputAnalyzer jdk.test.lib.Platform jdk.test.lib.ProcessTools
 * @run driver ClassInitializationTest
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.ProcessTools;

public class ClassInitializationTest {

    public static void main(String... args) throws Exception {

        // (1)
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:classinit=info", "-Xverify:all", "-Xmx64m", "BadMap50");
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldContain("Start class verification for:");
        out.shouldContain("End class verification for:");
        out.shouldContain("Initializing");
        out.shouldContain("Verification for BadMap50 failed");
        out.shouldContain("Fail over class verification to old verifier for: BadMap50");

        // (2)
        if (Platform.isDebugBuild()) {
          pb = ProcessTools.createJavaProcessBuilder("-Xlog:classinit=info", "-Xverify:all", "-XX:+EagerInitialization", "-Xmx64m", "-version");
          out = new OutputAnalyzer(pb.start());
          out.shouldContain("[Initialized").shouldContain("without side effects]");
          out.shouldHaveExitValue(0);
        }
        // (3) Ensure that VerboseVerification still triggers appropriate messages.
        pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions", "-XX:+VerboseVerification", "-Xverify:all", "-Xmx64m", "BadMap50");
        out = new OutputAnalyzer(pb.start());
        out.shouldContain("End class verification for:");
        out.shouldContain("Verification for BadMap50 failed");
        out.shouldContain("Fail over class verification to old verifier for: BadMap50");
    }
}
