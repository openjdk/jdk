/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that VM rejects and invalid replay file.
 * @library /test/lib
 * @requires vm.compMode != "Xint"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestInvalidReplayFile
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.io.FileWriter;

public class TestInvalidReplayFile {


    public static void main(String[] args) throws Exception {

        // This test also serves as a very basic sanity test for release VMs (accepting the replay options and
        // attempting to read the replay file). Most of the tests in ciReplay use -XX:CICrashAt to induce artificial
        // crashes into the compiler, and that option is not available for release VMs. Therefore we cannot generate
        // replay files as a test in release builds.

        File f = new File("bogus-replay-file.txt");
        FileWriter w = new FileWriter(f);
        w.write("Bogus 123");
        w.flush();
        w.close();

        ProcessBuilder pb = ProcessTools.createTestJvm(
                "-XX:+UnlockDiagnosticVMOptions",
                "-Xmx100M",
                "-XX:+ReplayCompiles", "-XX:ReplayDataFile=./bogus-replay-file.txt");

        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());
        output_detail.shouldNotHaveExitValue(0);
        output_detail.shouldContain("Error while parsing");

    }
}


