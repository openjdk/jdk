/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestPosixSig.java
 * @bug 8285792
 * @summary fix issues with signal handler modification checks
 * @requires os.family != "windows"
 * @library /test/lib
 * @run driver TestPosixSig
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestPosixSig {

    // Check that a substring occurs exactly once.
    public static boolean occursOnce(String source, String substring) {
        int index = source.indexOf(substring);
        if (index == -1) return false;
        return (source.indexOf(substring, index + 1) == -1);
    }

    private static native void changeSigActionFor(int val);

    public static void main(String[] args) throws Throwable {
        // Get the library path property.
        String libpath = System.getProperty("java.library.path");

        if (args.length == 0) {

            // Create a new java process for the TestPsig Java/JNI test
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+CheckJNICalls",
                "-Djava.library.path=" + libpath + ":.",
                "TestPosixSig", "dummy");

            // Start the process and check the output
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            String outputString = output.getOutput();
            if (!occursOnce(outputString, "SIGFPE: sig_handler in ") ||
                !occursOnce(outputString, "SIGILL: sig_handler in ")) {
                System.out.println("output: " + outputString);
                throw new RuntimeException("Test failed, bad output.");
            }
            output.shouldHaveExitValue(0);

        } else {
            System.loadLibrary("TestPsig");
            TestPosixSig.changeSigActionFor(8); // SIGFPE
            TestPosixSig.changeSigActionFor(4); // SIGILL
            Thread.sleep(600);
        }
    }
}
