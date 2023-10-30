/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @test HandshakeTransitionTest
 * @summary This does a sanity test of the poll in the native wrapper.
 * @library /testlibrary /test/lib
 * @build HandshakeTransitionTest
 * @run main/native HandshakeTransitionTest
 */

public class HandshakeTransitionTest {
    public static native void someTime(int ms);

    public static void main(String[] args) throws Exception {
        List<String> commands = new ArrayList<>();
        commands.add("-Djava.library.path=" + Utils.TEST_NATIVE_PATH);
        commands.add("-XX:+UnlockDiagnosticVMOptions");
        commands.add("-XX:+SafepointALot");
        commands.add("-XX:+HandshakeALot");
        commands.add("-XX:GuaranteedSafepointInterval=20");
        commands.add("-XX:ParallelGCThreads=1");
        commands.add("-XX:ConcGCThreads=1");
        commands.add("-XX:CICompilerCount=2");
        commands.addAll(Arrays.asList(args));
        commands.add("HandshakeTransitionTest$Test");
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(commands);

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
