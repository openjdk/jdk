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
 */
package jdk.test.lib.cds;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


// This class contains common test utilities for testing CDS
public class CDSTestUtils {
    // Specify this property to copy sdandard output of the child test process to
    // the parent/main stdout of the test.
    // By default such output is logged into a file, but not copied into the main stdout
    // to avoid excessive log pollution. See executeAndLog() for details
    public static final boolean CopyChildStdoutToMainStdout =
        Boolean.getBoolean("test.cds.copy.child.stdout");

    // This property is passed to child test processes
    public static final String TestTimeoutFactor = System.getProperty("test.timeout.factor", "1.0");

    public static final String UnableToMapMsg =
        "Unable to map shared archive: test did not complete; assumed PASS";

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

        ArrayList<String> cmd = new ArrayList<String>();

        for (String p : opts.prefix) cmd.add(p);

        cmd.add("-Xshare:dump");
        cmd.add("-XX:+PrintSharedSpaces");
        cmd.add("-XX:+UnlockDiagnosticVMOptions");
        if (opts.archiveName == null)
            opts.archiveName = getDefaultArchiveName();
        cmd.add("-XX:SharedArchiveFile=./" + opts.archiveName);

        for (String s : opts.suffix) cmd.add(s);

        String[] cmdLine = cmd.toArray(new String[cmd.size()]);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, cmdLine);
        return executeAndLog(pb, "dump");
    }


    // check result of 'dump-the-archive' operation, that is "-Xshare:dump"
    public static OutputAnalyzer checkDump(OutputAnalyzer output, String... extraMatches)
        throws Exception {

        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);

        for (String match : extraMatches) {
            output.shouldContain(match);
        }

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
        if (output.getStdout().contains("http://bugreport.java.com/bugreport/crash.jsp")) {
            throw new RuntimeException("Hotspot crashed");
        }
        if (output.getStdout().contains("TEST FAILED")) {
            throw new RuntimeException("Test Failed");
        }
        if (output.getOutput().contains("shared class paths mismatch")) {
            throw new RuntimeException("shared class paths mismatch");
        }
        if (output.getOutput().contains("Unable to unmap shared space")) {
            throw new RuntimeException("Unable to unmap shared space");
        }

        // Special case -- sometimes Xshare:on fails because it failed to map
        // at given address. This behavior is platform-specific, machine config-specific
        // and can be random (see ASLR).
        if (isUnableToMap(output)) {
            System.out.println(UnableToMapMsg);
            return;
        }

        if (e != null)
            throw e;
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
    public static boolean isUnableToMap(OutputAnalyzer output) {
        String outStr = output.getOutput();
        if ((output.getExitValue() == 1) && (
            outStr.contains("Unable to reserve shared space at required address") ||
            outStr.contains("Unable to map ReadOnly shared space at required address") ||
            outStr.contains("Unable to map ReadWrite shared space at required address") ||
            outStr.contains("Unable to map MiscData shared space at required address") ||
            outStr.contains("Unable to map MiscCode shared space at required address") ||
            outStr.contains("Unable to map shared string space at required address") ||
            outStr.contains("Could not allocate metaspace at a compatible address") ||
            outStr.contains("Unable to allocate shared string space: range is not within java heap") ))
        {
            return true;
        }

        return false;
    }


    // Execute JVM with CDS archive, specify command line args suffix
    public static OutputAnalyzer runWithArchive(String... cliPrefix)
        throws Exception {

        return runWithArchive( (new CDSOptions())
                               .setArchiveName(getDefaultArchiveName())
                               .addPrefix(cliPrefix) );
    }


    // Execute JVM with CDS archive, specify CDSOptions
    public static OutputAnalyzer runWithArchive(CDSOptions opts)
        throws Exception {

        ArrayList<String> cmd = new ArrayList<String>();

        for (String p : opts.prefix) cmd.add(p);

        cmd.add("-Xshare:" + opts.xShareMode);
        cmd.add("-XX:+UnlockDiagnosticVMOptions");
        cmd.add("-Dtest.timeout.factor=" + TestTimeoutFactor);

        if (opts.archiveName == null)
            opts.archiveName = getDefaultArchiveName();
        cmd.add("-XX:SharedArchiveFile=" + opts.archiveName);

        if (opts.useVersion)
            cmd.add("-version");

        for (String s : opts.suffix) cmd.add(s);

        String[] cmdLine = cmd.toArray(new String[cmd.size()]);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, cmdLine);
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

        checkExtraMatches(output, extraMatches);
        return output;
    }


    public static OutputAnalyzer checkExecExpectError(OutputAnalyzer output,
                                             int expectedExitValue,
                                             String... extraMatches) throws Exception {
        if (isUnableToMap(output)) {
            System.out.println(UnableToMapMsg);
            return output;
        }

        output.shouldHaveExitValue(expectedExitValue);
        checkExtraMatches(output, extraMatches);
        return output;
    }

    public static OutputAnalyzer checkExtraMatches(OutputAnalyzer output,
                                                    String... extraMatches) throws Exception {
        for (String match : extraMatches) {
            output.shouldContain(match);
        }
        return output;
    }


    // get the file object for the test artifact
    public static File getTestArtifact(String name, boolean checkExistence) {
        File dir = new File(System.getProperty("test.classes", "."));
        File file = new File(dir, name);

        if (checkExistence && !file.exists()) {
            throw new RuntimeException("Cannot find " + file.getPath());
        }

        return file;
    }


    // create file containing the specified class list
    public static File makeClassList(String classes[])
        throws Exception {
        return makeClassList(getTestName() + "-", classes);
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


    // Optimization for getting a test name.
    // Test name does not change during execution of the test,
    // but getTestName() uses stack walking hence it is expensive.
    // Therefore cache it and reuse it.
    private static String testName;
    public static String getTestName() {
        if (testName == null) {
            testName = Utils.getTestName();
        }
        return testName;
    }


    public static String getDefaultArchiveName() {
        return getTestName() + ".jsa";
    }


    // ===================== FILE ACCESS convenience methods
    public static File getOutputFile(String name) {
        File dir = new File(System.getProperty("test.classes", "."));
        return new File(dir, getTestName() + "-" + name);
    }


    public static File getOutputSourceFile(String name) {
        File dir = new File(System.getProperty("test.classes", "."));
        return new File(dir, name);
    }


    public static File getSourceFile(String name) {
        File dir = new File(System.getProperty("test.src", "."));
        return new File(dir, name);
    }


    // ============================= Logging
    public static OutputAnalyzer executeAndLog(ProcessBuilder pb, String logName) throws Exception {
        long started = System.currentTimeMillis();
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        writeFile(getOutputFile(logName + ".stdout"), output.getStdout());
        writeFile(getOutputFile(logName + ".stderr"), output.getStderr());
        System.out.println("[ELAPSED: " + (System.currentTimeMillis() - started) + " ms]");
        System.out.println("[STDERR]\n" + output.getStderr());

        if (CopyChildStdoutToMainStdout)
            System.out.println("[STDOUT]\n" + output.getStdout());

        return output;
    }


    private static void writeFile(File file, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        PrintStream ps = new PrintStream(fos);
        ps.print(content);
        ps.close();
        fos.close();
    }
}
