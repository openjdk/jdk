/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for ProcessTools.executeProcess()
 * @library /test/lib
 * @run main ProcessToolsExecuteProcessTest
 */

import java.util.function.Consumer;
import java.io.File;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ProcessToolsExecuteProcessTest {
    static void testExecuteProcessExit() throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("ProcessToolsExecuteProcessTest", "testExecuteProcessExit");

        OutputAnalyzer analyzer = ProcessTools.executeProcess(pb);
        int exitValue = analyzer.getExitValue();
        if (exitValue != 0) {
            throw new RuntimeException("Failed: wrong exit value: " + exitValue);
        }
    }

    static void testExecuteProcessStdout() throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("ProcessToolsExecuteProcessTest", "testExecuteProcessStdout");

        OutputAnalyzer analyzer = ProcessTools.executeProcess(pb);
        String stdout = analyzer.getStdout();
        if (!stdout.contains("After sleep")) {
            throw new RuntimeException("Failed: stdout lacks expected string");
        }
    }

    static void testNewOutputAnalyzerExit() throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("ProcessToolsExecuteProcessTest", "testNewOutputAnalyzerExit");

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        int exitValue = analyzer.getExitValue();
        if (exitValue != 0) {
            throw new RuntimeException("Failed: wrong exit value: " + exitValue);
        }
    }

    static void testNewOutputAnalyzerStdout() throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("ProcessToolsExecuteProcessTest", "testNewOutputAnalyzerStdout");

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        String stdout = analyzer.getStdout();
        if (!stdout.contains("After sleep")) {
            throw new RuntimeException("Failed: stdout lacks expected string");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            // Spawned process

            System.out.println("Before sleep");
            
            // Sleep for a while, to get some interesting timestamps for the process logging.
            Thread.sleep(2 * 1000);
            
            System.out.println("After sleep");
        } else {
            // Driver process
            testExecuteProcessExit();
            testExecuteProcessStdout();
            testNewOutputAnalyzerExit();
            testNewOutputAnalyzerStdout();
        }
    }
}
