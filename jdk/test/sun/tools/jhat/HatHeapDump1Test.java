/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.util.Arrays;

import jdk.testlibrary.Asserts;
import jdk.testlibrary.JDKToolLauncher;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

/* @test
 * @bug 5102009
 * @summary Sanity test of jhat functionality
 * @library /lib/testlibrary
 * @build jdk.testlibarary.*
 * @compile -g HelloWorld.java
 * @run main HatHeapDump1Test
 */
public class HatHeapDump1Test {

    private static final String TEST_CLASSES = System.getProperty("test.classes", ".");

    public static void main(String args[]) throws Exception {
        String className = "HelloWorld";
        File dumpFile = new File(className + ".hdump");

        // Generate a heap dump
        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder("-cp",
                TEST_CLASSES,
                "-Xcheck:jni",
                "-Xverify:all",
                "-agentlib:hprof=heap=dump,format=b,file=" + dumpFile.getAbsolutePath(),
                className);
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        System.out.println(output.getOutput());
        output.shouldHaveExitValue(0);
        output.shouldContain("Dumping Java heap ... done");
        Asserts.assertTrue(dumpFile.exists() && dumpFile.isFile(), "Invalid dump file " + dumpFile.getAbsolutePath());

        // Run jhat to analyze the heap dump
        output = jhat("-debug", "2", dumpFile.getAbsolutePath());
        output.shouldHaveExitValue(0);
        output.shouldContain("Snapshot resolved");
        output.shouldContain("-debug 2 was used");
        output.shouldNotContain("ERROR");

        dumpFile.delete();
    }

    private static OutputAnalyzer jhat(String... toolArgs) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhat");
        if (toolArgs != null) {
            for (String toolArg : toolArgs) {
                launcher.addToolArg(toolArg);
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(launcher.getCommand());
        System.out.println(Arrays.toString(processBuilder.command().toArray()).replace(",", ""));
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        System.out.println(output.getOutput());

        return output;
    }

}
