/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import sun.hotspot.WhiteBox;

/*
 * @test HandshakeTransitionTest
 * @summary This does a sanity test of the poll in the native wrapper.
 * @requires vm.debug
 * @library /testlibrary /test/lib
 * @build HandshakeTransitionTest
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm/native -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI HandshakeTransitionTest
 */

public class HandshakeTransitionTest {

    public static native void someTime(int ms);

    public static void main(String[] args) throws Exception {
        String lib = System.getProperty("test.nativepath");
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
            ProcessTools.createJavaProcessBuilder(
                    true,
                    "-Djava.library.path=" + lib,
                    "-XX:+SafepointALot",
                    "-XX:GuaranteedSafepointInterval=20",
                    "-Xlog:ergo*",
                    "-XX:ParallelGCThreads=1",
                    "-XX:ConcGCThreads=1",
                    "-XX:CICompilerCount=2",
                    "-XX:+UnlockExperimentalVMOptions",
                    useJVMCICompilerStr,
                    "HandshakeTransitionTest$Test");


        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0);
        output.stdoutShouldContain("JOINED");
    }

    static class Test implements Runnable {
        final static int testLoops = 2000;
        final static int testSleep = 1; //ms

        public static void main(String[] args) throws Exception {
            System.loadLibrary("HandshakeTransitionTest");
            Test test = new Test();
            Thread[] threads = new Thread[64];
            for (int i = 0; i<threads.length ; i++) {
                threads[i] = new Thread(test);
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }
            System.out.println("JOINED");
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i<testLoops ; i++) {
                    someTime(testSleep);
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.exit(1);
            }
        }
    }
}
