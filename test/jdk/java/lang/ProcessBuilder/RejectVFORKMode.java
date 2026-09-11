/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, IBM Corp.
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
 * @test
 * @bug 8357089
 * @summary Check that specifying VFORK correctly falls back to FORK with a clear warning to stderr.
 * @requires (os.family == "linux")
 * @library /test/lib
 * @run main RejectVFORKMode GRANDPARENT
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class RejectVFORKMode {
    public static void main(String[] args) throws Exception {

        switch (args[0]) {
            case "PARENT" -> {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", "echo 'Child Process'; exit 12;");
                // This should result in a (written to this process' stderr) warning about VFORK mode.
                // But child should have been started successfully.
                OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(pb);
                outputAnalyzer.shouldHaveExitValue(12);
                outputAnalyzer.shouldContain("Child Process");
                System.exit(0);
            }
            case "GRANDPARENT" -> {
                ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Djdk.lang.Process.launchMechanism=VFORK", RejectVFORKMode.class.getName(), "PARENT");
                OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(pb);
                outputAnalyzer.shouldHaveExitValue(0);
                outputAnalyzer.shouldContain("The VFORK launch mechanism has been removed. Switching to FORK instead.");
            }
            default -> throw new RuntimeException("Bad arg");
        }
    }
}
