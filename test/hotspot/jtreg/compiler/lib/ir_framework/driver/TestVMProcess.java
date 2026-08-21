/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.driver.network.TestVMData;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class prepares, creates, and runs the Test VM with verification of proper termination. The class also stores
 * information about the Test VM which is later queried for IR matching. The communication between this Driver VM
 * and the Test VM is done over a dedicated socket.
 *
 * @see TestVM
 * @see TestFrameworkSocket
 */
public class TestVMProcess {
    private static final boolean PREFER_COMMAND_LINE_FLAGS = Boolean.getBoolean("PreferCommandLineFlags");
    private static final int WARMUP_ITERATIONS = Integer.getInteger("Warmup", -1);
    private static final boolean VERIFY_VM = Boolean.getBoolean("VerifyVM") && Platform.isDebugBuild();
    private static final boolean VERBOSE = Boolean.getBoolean("Verbose");
    private static final boolean EXCLUDE_RANDOM = Boolean.getBoolean("ExcludeRandom");
    private static final boolean REPORT_STDOUT = Boolean.getBoolean("ReportStdout");
    private static final boolean DUMP_OUTPUT = VERBOSE || EXCLUDE_RANDOM || REPORT_STDOUT;

    private static final String FATAL_ERROR_MARKER = "# A fatal error has been detected by the Java Runtime Environment:";

    private static String lastTestVMOutput = "";

    private final ArrayList<String> cmds;
    private String commandLine;
    private OutputAnalyzer oa;
    private final TestVMData testVmData;

    public TestVMProcess(List<String> additionalFlags, Class<?> testClass, Set<Class<?>> helperClasses, int defaultWarmup,
                         boolean allowNotCompilable, boolean testClassesOnBootClassPath) {
        this.cmds = new ArrayList<>();
        TestFrameworkSocket socket = new TestFrameworkSocket();
        try (socket) {
            socket.start();
            prepareTestVMFlags(additionalFlags, socket, testClass, helperClasses, defaultWarmup,
                               allowNotCompilable, testClassesOnBootClassPath);
            start();
            // Test VM has exited. Do not close the socket, yet, because the accept and/or reader threads could still
            // be processing its connection. We wait for the socket result and only then close the socket by leaving
            // this scope.
            testVmData = processTestVmResult(socket, allowNotCompilable);
        } // Socket closed here.
    }

    private TestVMData processTestVmResult(TestFrameworkSocket socket, boolean allowNotCompilable) {
        if (oa.getExitValue() == 0) {
            dumpTestVmOutputIfRequested();
            return readAndDumpTestVmData(socket, allowNotCompilable);
        }

        if (isTestFormatViolation()) {
            // When a test is malformed, we only show the violation. This kind of failure should be caught during the
            // development phase of new tests.
            dumpTestVmOutputIfRequested();
            throw createTestFormatException();
        }
        if (noTestsRun()) {
            // If no test was selected, we just show the exception message. This kind of failure only happens during
            // debugging when specifying an empty test set with property flags.
            dumpTestVmOutputIfRequested();
            throw createNoTestsRunException();
        }
        throw createTestVMExceptionForNonZeroExit(socket, allowNotCompilable);
    }

    /**
     * Dump the Test VM output if flags request it.
     */
    private void dumpTestVmOutputIfRequested() {
        if (DUMP_OUTPUT) {
            System.out.println("Test VM Output");
            System.out.println("--------------");
            System.out.println(oa.getOutput());
        }
    }

    private TestVMData readAndDumpTestVmData(TestFrameworkSocket socket, boolean allowNotCompilable) {
        String hotspotPidFileName = String.format("hotspot_pid%d.log", oa.pid());
        TestVMData testVMData = socket.testVmData(hotspotPidFileName, allowNotCompilable);
        testVMData.printJavaMessages();
        return testVMData;
    }

    private boolean isTestFormatViolation() {
        return oa.getStderr().contains("TestFormat.throwIfAnyFailures");
    }

    private TestFormatException createTestFormatException() {
        Pattern pattern = Pattern.compile("Violations \\(\\d+\\)[\\s\\S]*(?=/============/)");
        Matcher matcher = pattern.matcher(oa.getStderr());
        TestFramework.check(matcher.find(), "Must find violation matches");
        return new TestFormatException(System.lineSeparator() + System.lineSeparator() + matcher.group());
    }

    private boolean noTestsRun() {
        return oa.getStderr().contains("NoTestsRunException");
    }

    private NoTestsRunException createNoTestsRunException() {
        return new NoTestsRunException(">>> No tests run due to empty set specified with -DTest and/or -DExclude. " +
                                       "Make sure to define a set of at least one @Test method");
    }

    private TestVMException createTestVMExceptionForNonZeroExit(TestFrameworkSocket socket, boolean allowNotCompilable) {
        String secondaryException = "";
        try {
            readAndDumpTestVmData(socket, allowNotCompilable);
        } catch (RuntimeException e) {
            // We observed a message processing exception. We treat it as secondary failure because messages could be
            // incomplete when the VM crashed or not even sent by the Test VM when it exits early on start-up
            // (e.g. passing in an unknown VM flag).
            secondaryException = buildSecondaryExceptionInfo(e);
        }
        // Primary exception: non-zero Test VM exit.
        return new TestVMException(buildExceptionInfo() + secondaryException);
    }

    private String buildSecondaryExceptionInfo(RuntimeException e) {
        String secondaryException;
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));

        secondaryException = System.lineSeparator() +
                             "Secondary Message-Processing Exception" + System.lineSeparator() +
                             "--------------------------------------" + System.lineSeparator() +
                             stringWriter;
        return secondaryException;
    }

    /**
     * Get more detailed information about the exception in a pretty format.
     */
    private String buildExceptionInfo() {
        StringBuilder builder = new StringBuilder();
        builder.append("Test VM exited with code ").append(oa.getExitValue()).append(System.lineSeparator());
        if (hasFatalErrorMarker() || DUMP_OUTPUT) {
            // Also dump the Test VM output if we experience a JVM error to show assertion failures etc.
            builder.append(System.lineSeparator())
                   .append(System.lineSeparator())
                   .append("Test VM - Standard Output").append(System.lineSeparator())
                   .append("-------------------------").append(System.lineSeparator())
                   .append(oa.getStdout());
        }
        builder.append(System.lineSeparator())
               .append(commandLine)
               .append(System.lineSeparator())
               .append(System.lineSeparator())
               .append("Test VM - Error Output").append(System.lineSeparator())
               .append("----------------------").append(System.lineSeparator())
               .append(oa.getStderr())
               .append(System.lineSeparator())
               .append(System.lineSeparator());
        return builder.toString();
    }

    /**
     * Best-effort VM crash detection by matching the start of the fatal error message. This covers most of the crashes
     * but fails when the Test VM was killed externally or when the output is unavailable or truncated which could
     * happen in native stack overflow cases.
     */
    private boolean hasFatalErrorMarker() {
        return oa.getExitValue() != 0 && oa.getOutput().contains(FATAL_ERROR_MARKER);
    }

    public String getCommandLine() {
        return commandLine;
    }

    public TestVMData testVmData() {
        return testVmData;
    }

    public static String getLastTestVMOutput() {
        return lastTestVMOutput;
    }

    private void prepareTestVMFlags(List<String> additionalFlags, TestFrameworkSocket socket, Class<?> testClass,
                                    Set<Class<?>> helperClasses, int defaultWarmup, boolean allowNotCompilable,
                                    boolean testClassesOnBootClassPath) {
        // Set java.library.path so JNI tests which rely on jtreg nativepath setting work
        cmds.add("-Djava.library.path=" + Utils.TEST_NATIVE_PATH);
        // Need White Box access in Test VM.
        String bootClassPath = "-Xbootclasspath/a:.";
        if (testClassesOnBootClassPath) {
            // Add test classes themselves to boot classpath to make them privileged.
            bootClassPath += File.pathSeparator + Utils.TEST_CLASS_PATH;
        }
        cmds.add(bootClassPath);
        cmds.add("-XX:+UnlockDiagnosticVMOptions");
        cmds.add("-XX:+WhiteBoxAPI");
        // Ignore CompileCommand flags which have an impact on the profiling information.
        List<String> jtregVMFlags = Arrays.stream(Utils.getTestJavaOpts()).filter(s -> !s.contains("CompileThreshold")).toList();
        if (!PREFER_COMMAND_LINE_FLAGS) {
            cmds.addAll(jtregVMFlags);
        }
        // Add server property flag that enables the Test VM to print the Applicable IR Rules for IR verification and
        // debug messages.
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
     * Default flags that are added used for the Test VM.
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

        process.command().add(1, "-DReproduce=true"); // Add after "/path/to/bin/java" in order to rerun the Test VM directly
        commandLine = "Command Line:" + System.lineSeparator() + String.join(" ", process.command())
                      + System.lineSeparator();
        lastTestVMOutput = oa.getOutput();
    }
}
