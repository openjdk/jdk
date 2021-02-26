/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import sun.hotspot.WhiteBox;

/*
 * @test HandshakeTimeoutTest
 * @summary Test handshake timeout for single target.
 * @requires vm.debug
 * @library /testlibrary /test/lib
 * @build HandshakeTimeoutTest
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI HandshakeTimeoutTest
 */

public class HandshakeTimeoutTest {
    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        Boolean useJVMCICompiler = wb.getBooleanVMFlag("UseJVMCICompiler");
        String useJVMCICompilerStr;
        if (useJVMCICompiler != null) {
            useJVMCICompilerStr = useJVMCICompiler ?  "-XX:+UseJVMCICompiler" : "-XX:-UseJVMCICompiler";
        } else {
            // pass something innocuous
            useJVMCICompilerStr = "-XX:+UnlockExperimentalVMOptions";
        }
        ProcessBuilder pb =
            ProcessTools.createTestJvm(
                    "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                    "-XX:+HandshakeALot",
                    "-XX:GuaranteedSafepointInterval=10",
                    "-XX:ParallelGCThreads=1",
                    "-XX:ConcGCThreads=1",
                    "-XX:CICompilerCount=2",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:HandshakeTimeout=1",
                    useJVMCICompilerStr,
                    "HandshakeTimeoutTest$Test");

        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.reportDiagnosticSummary();
        output.shouldMatch("has not cleared handshake op|No thread with an unfinished handshake op");
    }

    static class Test implements Runnable {
        public static void main(String[] args) throws Exception {
            Test test = new Test();
            Thread thread = new Thread(test);
            thread.start();
            thread.join();
        }

        @Override
        public void run() {
            int i = 0;
            while (true) {
                i++;
            }
        }
    }
}
