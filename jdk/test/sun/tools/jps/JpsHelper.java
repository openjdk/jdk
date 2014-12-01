/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.testlibrary.Asserts.assertGreaterThan;
import static jdk.testlibrary.Asserts.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.testlibrary.Asserts;
import jdk.testlibrary.JDKToolLauncher;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.Utils;
import jdk.testlibrary.ProcessTools;

/**
 * The helper class for running jps utility and verifying output from it
 */
public final class JpsHelper {

    /**
     * Helper class for handling jps arguments
     */
    public enum JpsArg {
        q,
        l,
        m,
        v,
        V;

        /**
         * Generate all possible combinations of {@link JpsArg}
         * (31 argument combinations and no arguments case)
         */
        public static List<List<JpsArg>> generateCombinations() {
            final int argCount = JpsArg.values().length;
            // If there are more than 30 args this algorithm will overflow.
            Asserts.assertLessThan(argCount, 31, "Too many args");

            List<List<JpsArg>> combinations = new ArrayList<>();
            int combinationCount = (int) Math.pow(2, argCount);
            for (int currCombo = 0; currCombo < combinationCount; ++currCombo) {
                List<JpsArg> combination = new ArrayList<>();
                for (int position = 0; position < argCount; ++position) {
                    int bit = 1 << position;
                    if ((bit & currCombo) != 0) {
                        combination.add(JpsArg.values()[position]);
                    }
                }
                combinations.add(combination);
            }
            return combinations;
        }

        /**
         *  Return combination of {@link JpsArg} as a String array
         */
        public static String[] asCmdArray(List<JpsArg> jpsArgs) {
            List<String> list = new ArrayList<>();
            for (JpsArg jpsArg : jpsArgs) {
                list.add("-" + jpsArg.toString());
            }
            return list.toArray(new String[list.size()]);
        }

    }

    /**
     * VM arguments to start test application with
     */
    public static final String[] VM_ARGS = {"-Xmx512m", "-XX:+UseParallelGC"};
    /**
     * VM flag to start test application with
     */
    public static final String VM_FLAG = "+DisableExplicitGC";

    private static File vmFlagsFile = null;
    private static List<String> testVmArgs = null;
    private static File manifestFile = null;

    /**
     * Create a file containing VM_FLAG in the working directory
     */
    public static File getVmFlagsFile() throws IOException {
        if (vmFlagsFile == null) {
            vmFlagsFile = new File("vmflags");
            try (BufferedWriter output = new BufferedWriter(new FileWriter(vmFlagsFile))) {
                output.write(VM_FLAG);
            }
            vmFlagsFile.deleteOnExit();
        }
        return vmFlagsFile;
    }

    /**
     * Return a list of VM arguments
     */
    public static List<String> getVmArgs() throws IOException {
        if (testVmArgs == null) {
            testVmArgs = new ArrayList<>();
            testVmArgs.addAll(Arrays.asList(VM_ARGS));
            testVmArgs.add("-XX:Flags=" + getVmFlagsFile().getAbsolutePath());
        }
        return testVmArgs;
    }

    /**
     * Start jps utility without any arguments
     */
    public static OutputAnalyzer jps() throws Exception {
        return jps(null, null);
    }

    /**
     * Start jps utility with tool arguments
     */
    public static OutputAnalyzer jps(String... toolArgs) throws Exception {
        return jps(null, Arrays.asList(toolArgs));
    }

    /**
     * Start jps utility with VM args and tool arguments
     */
    public static OutputAnalyzer jps(List<String> vmArgs, List<String> toolArgs) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jps");
        if (vmArgs != null) {
            for (String vmArg : vmArgs) {
                launcher.addVMArg(vmArg);
            }
        }
        if (toolArgs != null) {
            for (String toolArg : toolArgs) {
                launcher.addToolArg(toolArg);
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
        System.out.println(Arrays.toString(processBuilder.command().toArray()).replace(",", ""));
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        System.out.println(output.getOutput());

        return output;
    }

    /**
     * Verify jps stdout contains only pids and programs' name information.
     * jps stderr may contain VM warning messages which will be ignored.
     *
     * The output can look like:
     * 35536 Jps
     * 35417 Main
     * 31103 org.eclipse.equinox.launcher_1.3.0.v20120522-1813.jar
     */
    public static void verifyJpsOutput(OutputAnalyzer output, String regex) throws Exception {
        output.shouldHaveExitValue(0);
        int matchedCount = output.stdoutShouldMatchByLine(regex);
        assertGreaterThan(matchedCount , 0, "Found no lines matching pattern: " + regex);
        output.stderrShouldNotMatch("[E|e]xception");
        output.stderrShouldNotMatch("[E|e]rror");
    }

    /**
     * Compare jps output with a content in a file line by line
     */
    public static void verifyOutputAgainstFile(OutputAnalyzer output) throws IOException {
        String testSrc = System.getProperty("test.src", "?");
        File file = new File(testSrc, "usage.out");
        List<String> fileOutput = Utils.fileAsList(file);
        List<String> outputAsLines = output.asLines();
        assertTrue(outputAsLines.containsAll(fileOutput),
                "The ouput should contain all content of " + file.getAbsolutePath());
    }

    private static File getManifest(String className) throws IOException {
        if (manifestFile == null) {
            manifestFile = new File(className + ".mf");
            try (BufferedWriter output = new BufferedWriter(new FileWriter(manifestFile))) {
                output.write("Main-Class: " + className + Utils.NEW_LINE);
            }
        }
        return manifestFile;
    }

    /**
     * Build a jar of test classes in runtime
     */
    public static File buildJar(String className) throws Exception {
        File jar = new File(className + ".jar");

        List<String> jarArgs = new ArrayList<>();
        jarArgs.add("-cfm");
        jarArgs.add(jar.getAbsolutePath());
        File manifestFile = getManifest(className);
        jarArgs.add(manifestFile.getAbsolutePath());
        String testClassPath = System.getProperty("test.class.path", "?");
        for (String path : testClassPath.split(File.pathSeparator)) {
            jarArgs.add("-C");
            jarArgs.add(path);
            jarArgs.add(".");
        }

        System.out.println("Running jar " + jarArgs.toString());
        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(jarArgs.toArray(new String[jarArgs.size()]))) {
            throw new Exception("jar failed: args=" + jarArgs.toString());
        }

        manifestFile.delete();
        jar.deleteOnExit();

        return jar;
    }

}
