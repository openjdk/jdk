/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.process.OutputAnalyzer;

// A helper/utility class for testing shared strings
public class SharedStringsUtils {
    public static final String TEST_JAR_NAME =      "test";
    public static final String TEST_JAR_NAME_FULL = "test.jar";
    public static final String WHITEBOX_JAR_NAME =  "whitebox";

    public static String getWbParam() {
        return "-Xbootclasspath/a:" + TestCommon.getTestJar(WHITEBOX_JAR_NAME + ".jar");
    }

    // build the test jar
    public static void buildJar(String... classes) throws Exception {
        JarBuilder.build(TEST_JAR_NAME, classes);
    }

    // build the test jar and a whitebox jar
    public static void buildJarAndWhiteBox(String... classes) throws Exception {
        JarBuilder.build(true, WHITEBOX_JAR_NAME, "sun/hotspot/WhiteBox");
        buildJar(classes);
    }

    // execute the "dump" operation, but do not check the output
    public static OutputAnalyzer dumpWithoutChecks(String appClasses[],
        String sharedDataFile, String... extraOptions) throws Exception {

        String appJar = TestCommon.getTestJar(TEST_JAR_NAME_FULL);
        String[] args =
            TestCommon.concat(extraOptions, "-XX:+UseCompressedOops", "-XX:+UseG1GC",
            "-XX:SharedArchiveConfigFile=" +
            TestCommon.getSourceFile(sharedDataFile));

        return TestCommon.dump(appJar, appClasses, args);
    }

    // execute the dump operation and check the output
    public static OutputAnalyzer dump(String appClasses[],
        String sharedDataFile, String... extraOptions) throws Exception {
        OutputAnalyzer output = dumpWithoutChecks(appClasses, sharedDataFile, extraOptions);
        checkDump(output);
        return output;
    }

    public static OutputAnalyzer dumpWithWhiteBox(String appClasses[],
        String sharedDataFile, String... extraOptions) throws Exception {
        return dump(appClasses, sharedDataFile,
            TestCommon.concat(extraOptions, getWbParam()) );
    }

    // execute/run test with shared archive
    public static OutputAnalyzer runWithArchiveAuto(String className,
        String... extraOptions) throws Exception {

        String appJar = TestCommon.getTestJar(TEST_JAR_NAME_FULL);
        String[] args = TestCommon.concat(extraOptions,
            "-cp", appJar, "-XX:+UseCompressedOops", "-XX:+UseG1GC", className);

        OutputAnalyzer output = TestCommon.execAuto(args);
        checkExecAuto(output);
        return output;
    }

    public static OutputAnalyzer runWithArchive(String className,
        String... extraOptions) throws Exception {

        return runWithArchive(new String[0], className, extraOptions);
    }

    public static OutputAnalyzer runWithArchive(String[] extraMatches,
        String className, String... extraOptions) throws Exception {

        String appJar = TestCommon.getTestJar(TEST_JAR_NAME_FULL);
        String[] args = TestCommon.concat(extraOptions,
            "-XX:+UseCompressedOops", "-XX:+UseG1GC", className);

        OutputAnalyzer output = TestCommon.exec(appJar, args);
        checkExec(output, extraMatches);
        return output;
    }


    // execute/run test with shared archive and white box
    public static OutputAnalyzer runWithArchiveAndWhiteBox(String className,
        String... extraOptions) throws Exception {

        return runWithArchive(className,
            TestCommon.concat(extraOptions, getWbParam(),
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI") );
    }

    public static OutputAnalyzer runWithArchiveAndWhiteBox(String[] extraMatches,
        String className, String... extraOptions) throws Exception {

        return runWithArchive(extraMatches, className,
            TestCommon.concat(extraOptions, getWbParam(),
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI") );
    }


    public static void checkDump(OutputAnalyzer output) throws Exception {
        output.shouldContain("Shared string table stats");
        TestCommon.checkDump(output);
    }

    public static void checkExec(OutputAnalyzer output) throws Exception {
        TestCommon.checkExec(output, new String[0]);
    }

    public static void checkExecAuto(OutputAnalyzer output) throws Exception {
        CDSOptions opts = (new CDSOptions()).setXShareMode("auto");
        TestCommon.checkExec(output, opts);
    }

    public static void checkExec(OutputAnalyzer output, String[] extraMatches) throws Exception {
        TestCommon.checkExec(output, extraMatches);
    }
}
