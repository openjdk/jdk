/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.shared.TestFrameworkSocket;
import compiler.lib.ir_framework.shared.NoTestsRunException;
import compiler.lib.ir_framework.shared.TestFormatException;
import compiler.lib.ir_framework.test.TestVM;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class prepares, creates, and runs the "test" VM with verification of proper termination. The class also stores
 * information about the test VM which is later queried for IR matching. The communication between this driver VM
 * and the test VM is done over a dedicated socket.
 *
 * @see TestVM
 * @see TestFrameworkSocket
 */
public class TestVMProcess {
    private static final boolean VERBOSE = Boolean.getBoolean("Verbose");
    private static final boolean PREFER_COMMAND_LINE_FLAGS = Boolean.getBoolean("PreferCommandLineFlags");
    private static final int WARMUP_ITERATIONS = Integer.getInteger("Warmup", -1);
    private static final boolean VERIFY_VM = Boolean.getBoolean("VerifyVM") && Platform.isDebugBuild();
    private static final boolean REPORT_STDOUT = Boolean.getBoolean("ReportStdout");
    private static final boolean EXCLUDE_RANDOM = Boolean.getBoolean("ExcludeRandom");

    private static String lastTestVMOutput = "";

    private final ArrayList<String> cmds;
    private String hotspotPidFileName;
    private String commandLine;
    private OutputAnalyzer oa;
    private String irEncoding;

    public TestVMProcess(List<String> additionalFlags, Class<?> testClass, Set<Class<?>> helperClasses, int defaultWarmup,
                         boolean allowNotCompilable, boolean testClassesOnBootClassPath) {
        this.cmds = new ArrayList<>();
        TestFrameworkSocket socket = new TestFrameworkSocket();
        try (socket) {
            prepareTestVMFlags(additionalFlags, socket, testClass, helperClasses, defaultWarmup,
                               allowNotCompilable, testClassesOnBootClassPath);
            start();
        }
        processSocketOutput(socket);
        checkTestVMExitCode();
    }

    public String getCommandLine() {
        return commandLine;
    }

    public String getIrEncoding() {
        return irEncoding;
    }

    public String getHotspotPidFileName() {
        return hotspotPidFileName;
    }

    public static String getLastTestVMOutput() {
        return lastTestVMOutput;
    }

    private void prepareTestVMFlags(List<String> additionalFlags, TestFrameworkSocket socket, Class<?> testClass,
                                    Set<Class<?>> helperClasses, int defaultWarmup, boolean allowNotCompilable,
                                    boolean testClassesOnBootClassPath) {
        // Set java.library.path so JNI tests which rely on jtreg nativepath setting work
        cmds.add("-Djava.library.path=" + Utils.TEST_NATIVE_PATH);
        // Need White Box access in test VM.
        String bootClassPath = "-Xbootclasspath/a:.";
        if (testClassesOnBootClassPath) {
            // Add test classes themselves to boot classpath to make them privileged.
            bootClassPath += File.pathSeparator + Utils.TEST_CLASS_PATH;
        }
        cmds.add(bootClassPath);
        cmds.add("-XX:+UnlockDiagnosticVMOptions");
        cmds.add("-XX:+WhiteBoxAPI");
        // Ignore CompileCommand flags which have an impact on the profiling information.
        List<String> jtregVMFlags = Arrays.stream(Utils.getTestJavaOpts()).filter(s -> !s.contains("CompileThreshold")).collect(Collectors.toList());
        if (!PREFER_COMMAND_LINE_FLAGS) {
            cmds.addAll(jtregVMFlags);
        }
        // Add server property flag that enables test VM to print encoding for IR verification last and debug messages.
        cmds.add(socket.getPortPropertyFlag());
        cmds.addAll(additionalFlags);
        cmds.addAll(Arrays.asList(getDefaultFlags()));
        if (VERIFY_VM) {
            cmds.addAll(Arrays.asList(getVerifyFlags()));
        }

        if (PREFER_COMMAND_LINE_FLAGS) {
            // Prefer flags set via the command line over the ones set by scenarios.
            cmds.addAll(jtregVMFlags);
        }

        if (WARMUP_ITERATIONS < 0 && defaultWarmup != -1) {
            // Only use the set warmup for the framework if not overridden by a valid -DWarmup property set by a test.
            cmds.add("-DWarmup=" + defaultWarmup);
        }

        if (allowNotCompilable) {
            cmds.add("-DAllowNotCompilable=true");
        }

        cmds.add(TestVM.class.getName());
        cmds.add(testClass.getName());
        if (helperClasses != null) {
            helperClasses.forEach(c -> cmds.add(c.getName()));
        }
    }

    /**
     * Default flags that are added used for the test VM.
     */
    private static String[] getDefaultFlags() {
        return new String[] {"-XX:-BackgroundCompilation", "-XX:CompileCommand=quiet"};
    }

    /**
     * Additional verification flags that are used if -DVerifyVM=true is with a debug build.
     */
    private static String[] getVerifyFlags() {
        return new String[] {
                "-XX:+UnlockDiagnosticVMOptions", "-XX:+VerifyOops", "-XX:+VerifyStack", "-XX:+VerifyLastFrame",
                "-XX:+VerifyBeforeGC", "-XX:+VerifyAfterGC", "-XX:+VerifyDuringGC", "-XX:+VerifyAdapterSharing"
        };
    }

    private void start() {
        ProcessBuilder process = ProcessTools.createLimitedTestJavaProcessBuilder(cmds);
        try {
            // Calls 'main' of TestVM to run all specified tests with commands 'cmds'.
            // Use executeProcess instead of executeTestJava as we have already added the JTreg VM and
            // Java options in prepareTestVMFlags().
            oa = ProcessTools.executeProcess(process);
        } catch (Exception e) {
            throw new TestFrameworkException("Error while executing Test VM", e);
        }

        process.command().add(1, "-DReproduce=true"); // Add after "/path/to/bin/java" in order to rerun the test VM directly
        commandLine = "Command Line:" + System.lineSeparator() + String.join(" ", process.command())
                      + System.lineSeparator();
        hotspotPidFileName = String.format("hotspot_pid%d.log", oa.pid());
        lastTestVMOutput = oa.getOutput();
    }

    /**
     * Process the socket output: All prefixed lines are dumped to the standard output while the remaining lines
     * represent the IR encoding used for IR matching later.
     */
    private void processSocketOutput(TestFrameworkSocket socket) {
        String output = socket.getOutput();
        if (socket.hasStdOut()) {
            StringBuilder testListBuilder = new StringBuilder();
            StringBuilder messagesBuilder = new StringBuilder();
            StringBuilder nonStdOutBuilder = new StringBuilder();
            Scanner scanner = new Scanner(output);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith(TestFrameworkSocket.STDOUT_PREFIX)) {
                    // Exclude [STDOUT] from message.
                    line = line.substring(TestFrameworkSocket.STDOUT_PREFIX.length());
                    if (line.startsWith(TestFrameworkSocket.TESTLIST_TAG)) {
                        // Exclude [TESTLIST] from message for better formatting.
                        line = "> " + line.substring(TestFrameworkSocket.TESTLIST_TAG.length() + 1);
                        testListBuilder.append(line).append(System.lineSeparator());
                    } else {
                        messagesBuilder.append(line).append(System.lineSeparator());
                    }
                } else {
                    nonStdOutBuilder.append(line).append(System.lineSeparator());
                }
            }
            System.out.println();
            if (!testListBuilder.isEmpty()) {
                System.out.println("Run flag defined test list");
                System.out.println("--------------------------");
                System.out.println(testListBuilder);
                System.out.println();
            }
            if (!messagesBuilder.isEmpty()) {
                System.out.println("Messages from Test VM");
                System.out.println("---------------------");
                System.out.println(messagesBuilder);
            }
            irEncoding = nonStdOutBuilder.toString();
        } else {
            irEncoding = output;
        }
    }

    private void checkTestVMExitCode() {
        final int exitCode = oa.getExitValue();
        if (EXCLUDE_RANDOM || REPORT_STDOUT || (VERBOSE && exitCode == 0)) {
            System.out.println("--- OUTPUT TestFramework test VM ---");
            System.out.println(oa.getOutput());
        }

        if (exitCode != 0) {
            throwTestVMException();
        }
    }

    /**
     * Exit code was non-zero of test VM. Check the stderr to determine what kind of exception that should be thrown to
     * react accordingly later.
     */
    private void throwTestVMException() {
        String stdErr = oa.getStderr();
        if (stdErr.contains("TestFormat.throwIfAnyFailures")) {
            Pattern pattern = Pattern.compile("Violations \\(\\d+\\)[\\s\\S]*(?=/============/)");
            Matcher matcher = pattern.matcher(stdErr);
            TestFramework.check(matcher.find(), "Must find violation matches");
            throw new TestFormatException(System.lineSeparator() + System.lineSeparator() + matcher.group());
        } else if (stdErr.contains("NoTestsRunException")) {
            throw new NoTestsRunException(">>> No tests run due to empty set specified with -DTest and/or -DExclude. " +
                                          "Make sure to define a set of at least one @Test method");
        } else {
            throw new TestVMException(getExceptionInfo());
        }
    }

    /**
     * Get more detailed information about the exception in a pretty format.
     */
    private String getExceptionInfo() {
        int exitCode = oa.getExitValue();
        String stdErr = oa.getStderr();
        String stdOut = "";
        boolean osIsWindows = Platform.isWindows();
        boolean JVMHadError = (!osIsWindows && exitCode == 134) || (osIsWindows && exitCode == -1);
        if (JVMHadError) {
            // Also dump the stdout if we experience a JVM error (e.g. to show hit assertions etc.).
            stdOut = System.lineSeparator() + System.lineSeparator() + "Standard Output" + System.lineSeparator()
                     + "---------------" + System.lineSeparator() + oa.getOutput();
        }
        return "TestFramework test VM exited with code " + exitCode + System.lineSeparator() + stdOut
               + System.lineSeparator() + commandLine + System.lineSeparator() + System.lineSeparator()
               + "Error Output" + System.lineSeparator() + "------------" + System.lineSeparator() + stdErr
               + System.lineSeparator() + System.lineSeparator();
    }
}
