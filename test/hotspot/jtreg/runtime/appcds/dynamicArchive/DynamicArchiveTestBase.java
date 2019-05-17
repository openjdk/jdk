/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSTestUtils.Result;

/**
 * Base class for test cases in test/hotspot/jtreg/runtime/appcds/dynamicArchive/
 */
class DynamicArchiveTestBase {
    private static boolean executedIn_run = false;

    public static interface DynamicArchiveTest {
        public void run() throws Exception;
    }

    public static interface DynamicArchiveTestWithArgs {
        public void run(String args[]) throws Exception;
    }


    /*
     * Tests for dynamic archives should be written using this pattern:
     *
     * public class HelloDynamic extends DynamicArchiveTestBase {
     *     public static void main(String[] args) throws Exception {
     *        runTest(HelloDynamic::testDefaultBase); // launch one test case
     *     }
     *
     *     // the body of a test case
     *     static void testDefaultBase() throws Exception {
     *         String topArchiveName = getNewArchiveName("top");
     *         doTest(null, topArchiveName);
     *     }
     * }
     *
     * The reason for this is so that we can clean up the archive files
     * created by prior test cases. Otherwise tests with lots of
     * test cases may fill up the scratch directory.
     */
    public static void runTest(DynamicArchiveTest t) throws Exception {
        executedIn_run = true;
        try {
            TestCommon.deletePriorArchives();
            t.run();
        } finally {
            executedIn_run = false;
        }
    }

    public static void runTest(DynamicArchiveTestWithArgs t, String... args) throws Exception {
        executedIn_run = true;
        try {
            TestCommon.deletePriorArchives();
            t.run(args);
        } finally {
            executedIn_run = false;
        }
    }

    public static String getNewArchiveName() {
        return TestCommon.getNewArchiveName();
    }
    public static String getNewArchiveName(String stem) {
        return TestCommon.getNewArchiveName(stem);
    }

    /**
     * Execute a JVM using the base archive (given by baseArchiveName) with the command line
     * (given by cmdLineSuffix). At JVM exit, dump all eligible classes into the top archive
     * (give by topArchiveName).
     *
     * If baseArchiveName is null, use the JDK's default archive as the base archive.
     */
    public static Result dump2(String baseArchiveName, String topArchiveName, String ... cmdLineSuffix)
        throws Exception
    {
        String[] cmdLine = TestCommon.concat(
            "-XX:ArchiveClassesAtExit=" + topArchiveName);
        // to allow dynamic archive tests to be run in the "rt-non-cds-mode"
        cmdLine = TestCommon.concat(cmdLine, "-Xshare:auto");
        if (baseArchiveName != null) {
            cmdLine = TestCommon.concat(cmdLine, "-XX:SharedArchiveFile=" + baseArchiveName);
        }
        cmdLine = TestCommon.concat(cmdLine, cmdLineSuffix);
        return execProcess("dump", cmdLine);
    }

    public static Result dump2_WB(String baseArchiveName, String topArchiveName, String ... cmdLineSuffix)
        throws Exception
    {
        return dump2(baseArchiveName, topArchiveName,
                     TestCommon.concat(wbRuntimeArgs(), cmdLineSuffix));
    }

    /**
     * A convenience method similar to dump2, but always use the JDK's default archive
     * as the base archive.
     *
     * Most dynamicArchive/*.java test cases should be using this method instead of run2.
     */
    public static Result dump(String topArchiveName, String ... cmdLineSuffix)
        throws Exception
    {
        return dump2(null, topArchiveName, cmdLineSuffix);
    }

    /**
     * Dump the base archive. The JDK's default class list is used (unless otherwise specified
     * in cmdLineSuffix).
     */
    public static void dumpBaseArchive(String baseArchiveName, String ... cmdLineSuffix)
        throws Exception
    {
        CDSOptions opts = new CDSOptions();
        opts.setArchiveName(baseArchiveName);
        opts.addSuffix(cmdLineSuffix);
        opts.addSuffix("-Djava.class.path=");
        OutputAnalyzer out = CDSTestUtils.createArchive(opts);
        CDSTestUtils.checkDump(out);
    }

    /**
     * Same as dumpBaseArchive, but also add WhiteBox to the bootcp
     */
    public static void dumpBaseArchive_WB(String baseArchiveName, String ... cmdLineSuffix)
        throws Exception
    {
        dumpBaseArchive(baseArchiveName,
                        TestCommon.concat("-Xbootclasspath/a:" + getWhiteBoxJar(), cmdLineSuffix));
    }

    private static String getWhiteBoxJar() {
        String wbJar = ClassFileInstaller.getJarPath("WhiteBox.jar");
        if (!(new File(wbJar)).exists()) {
            throw new RuntimeException("Test error: your test must have " +
                                       "'@run driver ClassFileInstaller -jar WhiteBox.jar sun.hotspot.WhiteBox'");
        }
        return wbJar;
    }

    private static String[] wbRuntimeArgs() {
        return TestCommon.concat("-Xbootclasspath/a:" + getWhiteBoxJar(),
                                 "-XX:+UnlockDiagnosticVMOptions",
                                 "-XX:+WhiteBoxAPI");
    }

    /**
     * Execute a JVM using the base archive (given by baseArchiveName) and the top archive
     * (give by topArchiveName), using the command line (given by cmdLineSuffix).
     *
     * If baseArchiveName is null, use the JDK's default archive as the base archive.
     */
    public static Result run2(String baseArchiveName, String topArchiveName, String ... cmdLineSuffix)
        throws Exception {
        if (baseArchiveName == null && topArchiveName == null) {
            throw new RuntimeException("Both baseArchiveName and topArchiveName cannot be null at the same time.");
        }
        String archiveFiles = (baseArchiveName == null) ? topArchiveName :
            (topArchiveName == null) ? baseArchiveName :
            baseArchiveName + File.pathSeparator + topArchiveName;
        String[] cmdLine = TestCommon.concat(
            "-Xshare:on",
            "-XX:SharedArchiveFile=" + archiveFiles);
        cmdLine = TestCommon.concat(cmdLine, cmdLineSuffix);
        return execProcess("exec", cmdLine);
    }

    public static Result run2_WB(String baseArchiveName, String topArchiveName, String ... cmdLineSuffix)
        throws Exception
    {
        return run2(baseArchiveName, topArchiveName,
                    TestCommon.concat(wbRuntimeArgs(), cmdLineSuffix));
    }

    /**
     * A convenience method similar to run2, but always use the JDK's default archive
     * as the base archive.
     *
     * Most dynamicArchive/*.java test cases should be using this method instead of run2.
     */
    public static Result run(String topArchiveName, String ... cmdLineSuffix)
        throws Exception
    {
        return run2(null, topArchiveName, cmdLineSuffix);
    }

    private static String getXshareMode(String[] cmdLine) {
        for (int i = 0; i <= cmdLine.length - 1; i++) {
            int j = cmdLine[i].indexOf("-Xshare:");
            if (j != -1) {
                return (cmdLine[i].substring(j));
            }
        }
        return null;
   }


    private static Result execProcess(String mode, String[] cmdLine) throws Exception {
        if (!executedIn_run) {
            throw new Exception("Test error: dynamic archive tests must be executed via DynamicArchiveTestBase.run()");
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, cmdLine);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, mode);
        CDSOptions opts = new CDSOptions();
        String xShareMode = getXshareMode(cmdLine);
        if (xShareMode != null) {
            opts.setXShareMode(xShareMode);
        }
        return new Result(opts, output);
    }

    /**
     * A convenience method for dumping and running, using the default CDS archive from the
     * JDK. Both dumping and running should exit normally.
     */
    public static void dumpAndRun(String topArchiveName, String ... cmdLineSuffix) throws Exception {
        dump(topArchiveName, cmdLineSuffix).assertNormalExit();
        run(topArchiveName,  cmdLineSuffix).assertNormalExit();
    }
}
