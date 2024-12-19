/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib.cds;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

// This class contains common test utilities for testing CDS
public class CDSTestUtils {
    public static final String MSG_RANGE_NOT_WITHIN_HEAP =
        "Unable to allocate region, range is not within java heap.";
    public static final String MSG_RANGE_ALREADT_IN_USE =
        "Unable to allocate region, java heap range is already in use.";
    public static final String MSG_DYNAMIC_NOT_SUPPORTED =
        "-XX:ArchiveClassesAtExit is unsupported when base CDS archive is not loaded";
    public static final String MSG_STATIC_FIELD_MAY_HOLD_DIFFERENT_VALUE =
        "an object points to a static field that may hold a different value at runtime";
    public static final boolean DYNAMIC_DUMP = Boolean.getBoolean("test.dynamic.cds.archive");

    public interface Checker {
        public void check(OutputAnalyzer output) throws Exception;
    }

    /*
     * INTRODUCTION
     *
     * When testing various CDS functionalities, we need to launch JVM processes
     * using a "launch method" (such as TestCommon.run), and analyze the results of these
     * processes.
     *
     * While typical jtreg tests would use OutputAnalyzer in such cases, due to the
     * complexity of CDS failure modes, we have added the CDSTestUtils.Result class
     * to make the analysis more convenient and less error prone.
     *
     * A Java process can end in one of the following 4 states:
     *
     *    1: Unexpected error - such as JVM crashing. In this case, the "launch method"
     *                          will throw a RuntimeException.
     *    2: Mapping Failure  - this happens when the OS (intermittently) fails to map the
     *                          CDS archive, normally caused by Address Space Layout Randomization.
     *                          We usually treat this as "pass".
     *    3: Normal Exit      - the JVM process has finished without crashing, and the exit code is 0.
     *    4: Abnormal Exit    - the JVM process has finished without crashing, and the exit code is not 0.
     *
     * In most test cases, we need to check the JVM process's output in cases 3 and 4. However, we need
     * to make sure that our test code is not confused by case 2.
     *
     * For example, a JVM process is expected to print the string "Hi" and exit with 0. With the old
     * CDSTestUtils.runWithArchive API, the test may be written as this:
     *
     *     OutputAnalyzer out = CDSTestUtils.runWithArchive(args);
     *     out.shouldContain("Hi");
     *
     * However, if the JVM process fails with mapping failure, the string "Hi" will not be in the output,
     * and your test case will fail intermittently.
     *
     * Instead, the test case should be written as
     *
     *      CDSTestUtils.run(args).assertNormalExit("Hi");
     *
     * EXAMPLES/HOWTO
     *
     * 1. For simple substring matching:
     *
     *      CDSTestUtils.run(args).assertNormalExit("Hi");
     *      CDSTestUtils.run(args).assertNormalExit("a", "b", "x");
     *      CDSTestUtils.run(args).assertAbnormalExit("failure 1", "failure2");
     *
     * 2. For more complex output matching: using Lambda expressions
     *
     *      CDSTestUtils.run(args)
     *         .assertNormalExit(output -> output.shouldNotContain("this should not be printed");
     *      CDSTestUtils.run(args)
     *         .assertAbnormalExit(output -> {
     *             output.shouldNotContain("this should not be printed");
     *             output.shouldHaveExitValue(123);
     *           });
     *
     * 3. Chaining several checks:
     *
     *      CDSTestUtils.run(args)
     *         .assertNormalExit(output -> output.shouldNotContain("this should not be printed")
     *         .assertNormalExit("should have this", "should have that");
     *
     * 4. [Rare use case] if a test sometimes exit normally, and sometimes abnormally:
     *
     *      CDSTestUtils.run(args)
     *         .ifNormalExit("ths string is printed when exiting with 0")
     *         .ifAbNormalExit("ths string is printed when exiting with 1");
     *
     *    NOTE: you usually don't want to write your test case like this -- it should always
     *    exit with the same exit code. (But I kept this API because some existing test cases
     *    behave this way -- need to revisit).
     */
    public static class Result {
        private final OutputAnalyzer output;
        private final CDSOptions options;
        private final boolean hasNormalExit;
        private final String CDS_DISABLED = "warning: CDS is disabled when the";

        public Result(CDSOptions opts, OutputAnalyzer out) throws Exception {
            checkMappingFailure(out);
            this.options = opts;
            this.output = out;
            hasNormalExit = (output.getExitValue() == 0);

            if (hasNormalExit) {
                if ("on".equals(options.xShareMode) &&
                    output.getStderr().contains("java version") &&
                    !output.getStderr().contains(CDS_DISABLED)) {
                    // "-showversion" is always passed in the command-line by the execXXX methods.
                    // During normal exit, we require that the VM to show that sharing was enabled.
                    output.shouldContain("sharing");
                }
            }
        }

        public Result assertNormalExit(Checker checker) throws Exception {
            checker.check(output);
            output.shouldHaveExitValue(0);
            return this;
        }

        public Result assertAbnormalExit(Checker checker) throws Exception {
            checker.check(output);
            output.shouldNotHaveExitValue(0);
            return this;
        }

        // When {--limit-modules, --patch-module, and/or --upgrade-module-path}
        // are specified, CDS is silently disabled for both -Xshare:auto and -Xshare:on.
        public Result assertSilentlyDisabledCDS(Checker checker) throws Exception {
            // this comes from a JVM warning message.
            output.shouldContain(CDS_DISABLED);
            checker.check(output);
            return this;
        }

        public Result assertSilentlyDisabledCDS(int exitCode, String... matches) throws Exception {
            return assertSilentlyDisabledCDS((out) -> {
                out.shouldHaveExitValue(exitCode);
                checkMatches(out, matches);
                   });
        }

        public Result ifNormalExit(Checker checker) throws Exception {
            if (hasNormalExit) {
                checker.check(output);
            }
            return this;
        }

        public Result ifAbnormalExit(Checker checker) throws Exception {
            if (!hasNormalExit) {
                checker.check(output);
            }
            return this;
        }

        public Result ifNoMappingFailure(Checker checker) throws Exception {
            checker.check(output);
            return this;
        }


        public Result assertNormalExit(String... matches) throws Exception {
            checkMatches(output, matches);
            output.shouldHaveExitValue(0);
            return this;
        }

        public Result assertAbnormalExit(String... matches) throws Exception {
            checkMatches(output, matches);
            output.shouldNotHaveExitValue(0);
            return this;
        }
    }

    // A number to be included in the filename of the stdout and the stderr output file.
    static int logCounter = 0;

    private static int getNextLogCounter() {
        return logCounter++;
    }

    // By default, stdout of child processes are logged in files such as
    // <testname>-0000-exec.stdout. If you want to also include the stdout
    // inside jtr files, you can override this in the jtreg command line like
    // "jtreg -Dtest.cds.copy.child.stdout=true ...."
    public static final boolean copyChildStdoutToMainStdout =
        Boolean.getBoolean("test.cds.copy.child.stdout");

    // This property is passed to child test processes
    public static final String TestTimeoutFactor = System.getProperty("test.timeout.factor", "1.0");

    public static final String UnableToMapMsg =
        "Unable to map shared archive: test did not complete";

    // Create bootstrap CDS archive,
    // use extra JVM command line args as a prefix.
    // For CDS tests specifying prefix makes more sense than specifying suffix, since
    // normally there are no classes or arguments to classes, just "-version"
    // To specify suffix explicitly use CDSOptions.addSuffix()
    public static OutputAnalyzer createArchive(String... cliPrefix)
        throws Exception {
        return createArchive((new CDSOptions()).addPrefix(cliPrefix));
    }

    // Create bootstrap CDS archive
    public static OutputAnalyzer createArchive(CDSOptions opts)
        throws Exception {

        startNewArchiveName();

        ArrayList<String> cmd = new ArrayList<String>();

        for (String p : opts.prefix) cmd.add(p);

        cmd.add("-Xshare:dump");
        cmd.add("-Xlog:cds,cds+hashtables");
        if (opts.archiveName == null)
            opts.archiveName = getDefaultArchiveName();
        cmd.add("-XX:SharedArchiveFile=" + opts.archiveName);

        if (opts.classList != null) {
            File classListFile = makeClassList(opts.classList);
            cmd.add("-XX:ExtraSharedClassListFile=" + classListFile.getPath());
        }

        for (String s : opts.suffix) cmd.add(s);

        String[] cmdLine = cmd.toArray(new String[cmd.size()]);
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(cmdLine);
        return executeAndLog(pb, "dump");
    }

    public static boolean isDynamicArchive() {
        return DYNAMIC_DUMP;
    }

    // check result of 'dump-the-archive' operation, that is "-Xshare:dump"
    public static OutputAnalyzer checkDump(OutputAnalyzer output, String... extraMatches)
        throws Exception {

        if (!DYNAMIC_DUMP) {
            output.shouldContain("Loading classes to share");
        } else {
            output.shouldContain("Written dynamic archive 0x");
        }
        output.shouldHaveExitValue(0);
        output.shouldNotContain(MSG_STATIC_FIELD_MAY_HOLD_DIFFERENT_VALUE);

        for (String match : extraMatches) {
            output.shouldContain(match);
        }

        return output;
    }

    // check result of dumping base archive
    public static OutputAnalyzer checkBaseDump(OutputAnalyzer output) throws Exception {
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);
        output.shouldNotContain(MSG_STATIC_FIELD_MAY_HOLD_DIFFERENT_VALUE);
        return output;
    }

    // A commonly used convenience methods to create an archive and check the results
    // Creates an archive and checks for errors
    public static OutputAnalyzer createArchiveAndCheck(CDSOptions opts)
        throws Exception {
        return checkDump(createArchive(opts));
    }


    public static OutputAnalyzer createArchiveAndCheck(String... cliPrefix)
        throws Exception {
        return checkDump(createArchive(cliPrefix));
    }


    // This method should be used to check the output of child VM for common exceptions.
    // Most of CDS tests deal with child VM processes for creating and using the archive.
    // However exceptions that occur in the child process do not automatically propagate
    // to the parent process. This mechanism aims to improve the propagation
    // of exceptions and common errors.
    // Exception e argument - an exception to be re-thrown if none of the common
    // exceptions match. Pass null if you wish not to re-throw any exception.
    public static void checkCommonExecExceptions(OutputAnalyzer output, Exception e)
        throws Exception {
        if (output.getStdout().contains("https://bugreport.java.com/bugreport/crash.jsp")) {
            throw new RuntimeException("Hotspot crashed");
        }
        if (output.getStdout().contains("TEST FAILED")) {
            throw new RuntimeException("Test Failed");
        }
        if (output.getOutput().contains("Unable to unmap shared space")) {
            throw new RuntimeException("Unable to unmap shared space");
        }

        // Special case -- sometimes Xshare:on fails because it failed to map
        // at given address. This behavior is platform-specific, machine config-specific
        // and can be random (see ASLR).
        checkMappingFailure(output);

        if (e != null) {
            throw e;
        }
    }

    public static void checkCommonExecExceptions(OutputAnalyzer output) throws Exception {
        checkCommonExecExceptions(output, null);
    }


    // Check the output for indication that mapping of the archive failed.
    // Performance note: this check seems to be rather costly - searching the entire
    // output stream of a child process for multiple strings. However, it is necessary
    // to detect this condition, a failure to map an archive, since this is not a real
    // failure of the test or VM operation, and results in a test being "skipped".
    // Suggestions to improve:
    // 1. VM can designate a special exit code for such condition.
    // 2. VM can print a single distinct string indicating failure to map an archive,
    //    instead of utilizing multiple messages.
    // These are suggestions to improve testibility of the VM. However, implementing them
    // could also improve usability in the field.
    private static String hasUnableToMapMessage(OutputAnalyzer output) {
        String outStr = output.getOutput();
        if ((output.getExitValue() == 1)) {
            if (outStr.contains(MSG_RANGE_NOT_WITHIN_HEAP)) {
                return MSG_RANGE_NOT_WITHIN_HEAP;
            }
            if (outStr.contains(MSG_DYNAMIC_NOT_SUPPORTED)) {
                return MSG_DYNAMIC_NOT_SUPPORTED;
            }
        }

        return null;
    }

    public static boolean isUnableToMap(OutputAnalyzer output) {
        return hasUnableToMapMessage(output) != null;
    }

    public static void checkMappingFailure(OutputAnalyzer out) throws SkippedException {
        String match = hasUnableToMapMessage(out);
        if (match != null) {
            throw new SkippedException(UnableToMapMsg + ": " + match);
        }
    }

    public static Result run(String... cliPrefix) throws Exception {
        CDSOptions opts = new CDSOptions();
        opts.setArchiveName(getDefaultArchiveName());
        opts.addPrefix(cliPrefix);
        return new Result(opts, runWithArchive(opts));
    }

    public static Result run(CDSOptions opts) throws Exception {
        return new Result(opts, runWithArchive(opts));
    }

    // Dump a classlist using the -XX:DumpLoadedClassList option.
    public static Result dumpClassList(String classListName, String... cli)
        throws Exception {
        CDSOptions opts = (new CDSOptions())
            .setUseVersion(false)
            .setXShareMode("auto")
            .addPrefix("-XX:DumpLoadedClassList=" + classListName)
            .addSuffix(cli);
        Result res = run(opts).assertNormalExit();
        return res;
    }

    // Execute JVM with CDS archive, specify command line args suffix
    public static OutputAnalyzer runWithArchive(String... cliPrefix)
        throws Exception {

        return runWithArchive( (new CDSOptions())
                               .setArchiveName(getDefaultArchiveName())
                               .addPrefix(cliPrefix) );
    }

    // Enable basic verification (VerifyArchivedFields=1, no side effects) for all CDS
    // tests to make sure the archived heap objects are mapped/loaded properly.
    public static void addVerifyArchivedFields(ArrayList<String> cmd) {
        cmd.add("-XX:+UnlockDiagnosticVMOptions");
        cmd.add("-XX:VerifyArchivedFields=1");
    }

    // Execute JVM with CDS archive, specify CDSOptions
    public static OutputAnalyzer runWithArchive(CDSOptions opts)
        throws Exception {

        ArrayList<String> cmd = new ArrayList<String>();
        cmd.addAll(opts.prefix);
        cmd.add("-Xshare:" + opts.xShareMode);
        cmd.add("-Dtest.timeout.factor=" + TestTimeoutFactor);

        if (!opts.useSystemArchive) {
            if (opts.archiveName == null)
                opts.archiveName = getDefaultArchiveName();
            cmd.add("-XX:SharedArchiveFile=" + opts.archiveName);
        }
        addVerifyArchivedFields(cmd);

        if (opts.useVersion)
            cmd.add("-version");

        for (String s : opts.suffix) cmd.add(s);

        String[] cmdLine = cmd.toArray(new String[cmd.size()]);
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(cmdLine);
        return executeAndLog(pb, "exec");
    }


    // A commonly used convenience methods to create an archive and check the results
    // Creates an archive and checks for errors
    public static OutputAnalyzer runWithArchiveAndCheck(CDSOptions opts) throws Exception {
        return checkExec(runWithArchive(opts));
    }


    public static OutputAnalyzer runWithArchiveAndCheck(String... cliPrefix) throws Exception {
        return checkExec(runWithArchive(cliPrefix));
    }


    public static OutputAnalyzer checkExec(OutputAnalyzer output,
                                     String... extraMatches) throws Exception {
        CDSOptions opts = new CDSOptions();
        return checkExec(output, opts, extraMatches);
    }


    // check result of 'exec' operation, that is when JVM is run using the archive
    public static OutputAnalyzer checkExec(OutputAnalyzer output, CDSOptions opts,
                                     String... extraMatches) throws Exception {
        try {
            if ("on".equals(opts.xShareMode)) {
                output.shouldContain("sharing");
            }
            output.shouldHaveExitValue(0);
        } catch (RuntimeException e) {
            checkCommonExecExceptions(output, e);
            return output;
        }

        checkMatches(output, extraMatches);
        return output;
    }


    public static OutputAnalyzer checkExecExpectError(OutputAnalyzer output,
                                             int expectedExitValue,
                                             String... extraMatches) throws Exception {
        checkMappingFailure(output);
        output.shouldHaveExitValue(expectedExitValue);
        checkMatches(output, extraMatches);
        return output;
    }

    public static OutputAnalyzer checkMatches(OutputAnalyzer output,
                                              String... matches) throws Exception {
        for (String match : matches) {
            output.shouldContain(match);
        }
        return output;
    }

    private static final String outputDir;
    private static final File outputDirAsFile;

    static {
        outputDir = System.getProperty("user.dir", ".");
        outputDirAsFile = new File(outputDir);
    }

    public static String getOutputDir() {
        return outputDir;
    }

    public static File getOutputDirAsFile() {
        return outputDirAsFile;
    }

    // get the file object for the test artifact
    public static File getTestArtifact(String name, boolean checkExistence) {
        File file = new File(outputDirAsFile, name);

        if (checkExistence && !file.exists()) {
            throw new RuntimeException("Cannot find " + file.getPath());
        }

        return file;
    }


    // create file containing the specified class list
    public static File makeClassList(String classes[])
        throws Exception {
        return makeClassList(testName + "-", classes);
    }

    // create file containing the specified class list
    public static File makeClassList(String testCaseName, String classes[])
        throws Exception {

        File classList = getTestArtifact(testCaseName + "test.classlist", false);
        FileOutputStream fos = new FileOutputStream(classList);
        PrintStream ps = new PrintStream(fos);

        addToClassList(ps, classes);

        ps.close();
        fos.close();

        return classList;
    }


    public static void addToClassList(PrintStream ps, String classes[])
        throws IOException
    {
        if (classes != null) {
            for (String s : classes) {
                ps.println(s);
            }
        }
    }

    private static String testName = Utils.TEST_NAME.replace('/', '.');

    private static final SimpleDateFormat timeStampFormat =
        new SimpleDateFormat("HH'h'mm'm'ss's'SSS");

    private static String defaultArchiveName;

    // Call this method to start new archive with new unique name
    public static void startNewArchiveName() {
        defaultArchiveName = testName +
            timeStampFormat.format(new Date()) + ".jsa";
    }

    public static String getDefaultArchiveName() {
        return defaultArchiveName;
    }


    // ===================== FILE ACCESS convenience methods
    public static File getOutputFile(String name) {
        return new File(outputDirAsFile, testName + "-" + name);
    }

    public static String getOutputFileName(String name) {
        return getOutputFile(name).getName();
    }


    public static File getOutputSourceFile(String name) {
        return new File(outputDirAsFile, name);
    }


    public static File getSourceFile(String name) {
        File dir = new File(System.getProperty("test.src", "."));
        return new File(dir, name);
    }

    // Check commandline for the last instance of Xshare to see if the process can load
    // a CDS archive
    public static boolean isRunningWithArchive(List<String> cmd) {
        // -Xshare only works for the java executable
        if (!cmd.get(0).equals(JDKToolFinder.getJDKTool("java")) || cmd.size() < 2) {
            return false;
        }

        // -Xshare options are likely at the end of the args list
        for (int i = cmd.size() - 1; i >= 1; i--) {
            String s = cmd.get(i);
            if (s.equals("-Xshare:dump") || s.equals("-Xshare:off")) {
                return false;
            }
        }
        return true;
    }

    public static boolean isGCOption(String s) {
        return s.startsWith("-XX:+Use") && s.endsWith("GC");
    }

    public static boolean hasGCOption(List<String> cmd) {
        for (String s : cmd) {
            if (isGCOption(s)) {
                return true;
            }
        }
        return false;
    }

    // Handle and insert test.cds.runtime.options to commandline
    // The test.cds.runtime.options property is used to inject extra VM options to
    // subprocesses launched by the CDS test cases using executeAndLog().
    // The injection applies only to subprocesses that:
    //   - are launched by the standard java launcher (bin/java)
    //   - are not dumping the CDS archive with -Xshare:dump
    //   - do not explicitly disable CDS via -Xshare:off
    //
    // The main purpose of this property is to test the runtime loading of
    // the CDS "archive heap region" with non-default garbage collectors. E.g.,
    //
    // jtreg -vmoptions:-Dtest.cds.runtime.options=-XX:+UnlockExperimentalVMOptions,-XX:+UseEpsilonGC \
    //       test/hotspot/jtreg/runtime/cds
    //
    // Note that the injection is not applied to -Xshare:dump, so that the CDS archives
    // will be dumped with G1, which is the only collector that supports dumping
    // the archive heap region. Similarly, if a UseXxxGC option already exists in the command line,
    // the UseXxxGC option added in test.cds.runtime.options will be ignored.
    public static void handleCDSRuntimeOptions(ProcessBuilder pb) {
        List<String> cmd = pb.command();
        String jtropts = System.getProperty("test.cds.runtime.options");
        if (jtropts != null && isRunningWithArchive(cmd)) {
            // There cannot be multiple GC options in the command line so some
            // options may be ignored
            ArrayList<String> cdsRuntimeOpts = new ArrayList<String>();
            boolean hasGCOption = hasGCOption(cmd);
            for (String s : jtropts.split(",")) {
                if (!CDSOptions.disabledRuntimePrefixes.contains(s) &&
                    !(hasGCOption && isGCOption(s))) {
                    cdsRuntimeOpts.add(s);
                }
            }
            pb.command().addAll(1, cdsRuntimeOpts);
        }
    }

    // ============================= Logging
    public static OutputAnalyzer executeAndLog(ProcessBuilder pb, String logName) throws Exception {
        handleCDSRuntimeOptions(pb);
        return executeAndLog(pb.start(), logName);
    }

    public static OutputAnalyzer executeAndLog(Process process, String logName) throws Exception {
        long started = System.currentTimeMillis();

        OutputAnalyzer output = new OutputAnalyzer(process);
        String logFileNameStem =
            String.format("%04d", getNextLogCounter()) + "-" + logName;

        File stdout = getOutputFile(logFileNameStem + ".stdout");
        File stderr = getOutputFile(logFileNameStem + ".stderr");

        writeFile(stdout, output.getStdout());
        writeFile(stderr, output.getStderr());
        System.out.println("[ELAPSED: " + (System.currentTimeMillis() - started) + " ms]");
        System.out.println("[logging stdout to " + stdout + "]");
        System.out.println("[logging stderr to " + stderr + "]");
        System.out.println("[STDERR]\n" + output.getStderr());

        if (copyChildStdoutToMainStdout)
            System.out.println("[STDOUT]\n" + output.getStdout());

        if (output.getExitValue() != 0 && output.getStdout().contains("A fatal error has been detected")) {
          throw new RuntimeException("Hotspot crashed");
        }
        return output;
    }


    private static void writeFile(File file, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        PrintStream ps = new PrintStream(fos);
        ps.print(content);
        ps.close();
        fos.close();
    }

    // Format a line that defines an extra symbol in the file specify by -XX:SharedArchiveConfigFile=<file>
    public static String formatArchiveConfigSymbol(String symbol) {
        int refCount = -1; // This is always -1 in the current HotSpot implementation.
        if (isAsciiPrintable(symbol)) {
            return symbol.length() + " " + refCount + ": " + symbol;
        } else {
            StringBuilder sb = new StringBuilder();
            int utf8_length = escapeArchiveConfigString(sb, symbol);
            return utf8_length + " " + refCount + ": " + sb.toString();
        }
    }

    // This method generates the same format as HashtableTextDump::put_utf8() in HotSpot,
    // to be used by -XX:SharedArchiveConfigFile=<file>.
    private static int escapeArchiveConfigString(StringBuilder sb, String s) {
        byte arr[];
        try {
            arr = s.getBytes("UTF8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Unexpected", e);
        }
        for (int i = 0; i < arr.length; i++) {
            char ch = (char)(arr[i] & 0xff);
            if (isAsciiPrintable(ch)) {
                sb.append(ch);
            } else if (ch == '\t') {
                sb.append("\\t");
            } else if (ch == '\r') {
                sb.append("\\r");
            } else if (ch == '\n') {
                sb.append("\\n");
            } else if (ch == '\\') {
                sb.append("\\\\");
            } else {
                String hex = Integer.toHexString(ch);
                if (ch < 16) {
                    sb.append("\\x0");
                } else {
                    sb.append("\\x");
                }
                sb.append(hex);
            }
        }

        return arr.length;
    }

    private static boolean isAsciiPrintable(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isAsciiPrintable(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiPrintable(char ch) {
        return ch >= 32 && ch < 127;
    }

    // JDK utility

    // Do a cheap clone of the JDK. Most files can be sym-linked. However, $JAVA_HOME/bin/java and $JAVA_HOME/lib/.../libjvm.so"
    // must be copied, because the java.home property is derived from the canonicalized paths of these 2 files.
    // Set a list of {jvm, "java"} which will be physically copied. If a file needs copied physically, add it to the list.
    private static String[] phCopied = {System.mapLibraryName("jvm"), "java"};
    public static void clone(File src, File dst) throws Exception {
        if (dst.exists()) {
            if (!dst.isDirectory()) {
                throw new RuntimeException("Not a directory :" + dst);
            }
        } else {
            if (!dst.mkdir()) {
                throw new RuntimeException("Cannot create directory: " + dst);
            }
        }
        // final String jvmLib = System.mapLibraryName("jvm");
        for (String child : src.list()) {
            if (child.equals(".") || child.equals("..")) {
                continue;
            }

            File child_src = new File(src, child);
            File child_dst = new File(dst, child);
            if (child_dst.exists()) {
                throw new RuntimeException("Already exists: " + child_dst);
            }
            if (child_src.isFile()) {
                boolean needPhCopy = false;
                for (String target : phCopied) {
                    if (child.equals(target)) {
                        needPhCopy = true;
                        break;
                    }
                }
                if (needPhCopy) {
                    Files.copy(child_src.toPath(), /* copy data to -> */ child_dst.toPath(),
                               new CopyOption[] { StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES});
                } else {
                    Files.createSymbolicLink(child_dst.toPath(),  /* link to -> */ child_src.toPath());
                }
            } else {
                clone(child_src, child_dst);
            }
        }
    }

    // modulesDir, like $JDK/lib
    // oldName, module name under modulesDir
    // newName, new name for oldName
    public static void rename(File fromFile, File toFile) throws Exception {
        if (!fromFile.exists()) {
            throw new RuntimeException(fromFile.getName() + " does not exist");
        }

        if (toFile.exists()) {
            throw new RuntimeException(toFile.getName() + " already exists");
        }

        boolean success = fromFile.renameTo(toFile);
        if (!success) {
            throw new RuntimeException("rename file " + fromFile.getName()+ " to " + toFile.getName() + " failed");
        }
    }

    public static ProcessBuilder makeBuilder(String... args) throws Exception {
        System.out.print("[");
        for (String s : args) {
            System.out.print(" " + s);
        }
        System.out.println(" ]");
        return new ProcessBuilder(args);
    }

    public static Path copyFile(String srcFile, String destDir) throws Exception {
        int idx = srcFile.lastIndexOf(File.separator);
        String jarName = srcFile.substring(idx + 1);
        Path srcPath = Paths.get(jarName);
        Path newPath = Paths.get(destDir);
        Path newDir;
        if (!Files.exists(newPath)) {
            newDir = Files.createDirectories(newPath);
        } else {
            newDir = newPath;
        }
        Path destPath = newDir.resolve(jarName);
        Files.copy(srcPath, destPath, REPLACE_EXISTING, COPY_ATTRIBUTES);
        return destPath;
    }

    // Some tests were initially written without the knowledge of -XX:+AOTClassLinking. These tests need to
    // be adjusted if -XX:+AOTClassLinking is specified in jtreg -vmoptions or -javaoptions:
    public static boolean isAOTClassLinkingEnabled() {
        return isBooleanVMOptionEnabledInCommandLine("AOTClassLinking");
    }

    public static boolean isBooleanVMOptionEnabledInCommandLine(String optionName) {
        String lastMatch = null;
        String pattern = "^-XX:." + optionName + "$";
        for (String s : Utils.getTestJavaOpts()) {
            if (s.matches(pattern)) {
                lastMatch = s;
            }
        }
        if (lastMatch != null && lastMatch.equals("-XX:+" + optionName)) {
            return true;
        }
        return false;
    }
}
