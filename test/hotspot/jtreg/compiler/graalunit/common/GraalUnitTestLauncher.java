/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package compiler.graalunit.common;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.nio.file.*;
import java.util.stream.Collectors;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.JDKToolFinder;

/*
 * This is helper class used to run Graal unit tests.
 * It accepts two arguments:
 *  -prefix TEST_PREFIX_TO_DEFINE_SET_OF_TESTS_TO_RUN (Ex: -prefix org.graalvm.compiler.api.test)
 *  -exclude EXCLUDED_TESTS_FILE_NAME
 */
public class GraalUnitTestLauncher {

    static final String MXTOOL_JARFILE = "com.oracle.mxtool.junit.jar";
    static final String GRAAL_UNITTESTS_JARFILE = "jdk.vm.compiler.tests.jar";

    static final String[] GRAAL_EXTRA_JARS = {"junit-4.12.jar", "asm-5.0.4.jar", "asm-tree-5.0.4.jar",
                                              "hamcrest-core-1.3.jar", "java-allocation-instrumenter.jar"};

    static final String GENERATED_TESTCLASSES_FILENAME = "list.testclasses";

    // Library dir used to find Graal specific jar files.
    static String libsDir;
    static {
        libsDir = System.getProperty("graalunit.libs");
        if (libsDir == null || libsDir.isEmpty()) {
            libsDir = System.getenv("TEST_IMAGE_GRAAL_DIR");
        }

        if (libsDir == null || libsDir.isEmpty())
            throw new RuntimeException("ERROR: Graal library directory is not specified, use -Dgraalunit.libs or TEST_IMAGE_GRAAL_DIR environment variable.");

        System.out.println("INFO: graal libs dir is '" + libsDir + "'");
    }

    /*
     * Generates --add-exports <module>/<package>=<target-module> flags and
     * returns them as array list.
     *
     * @param moduleName
     *        Name of the module to update export data
     *
     * @param targetModule
     *        Name of the module to whom to export
     */
    static ArrayList<String> getModuleExports(String moduleName, String targetModule) {
        ArrayList<String> exports = new ArrayList<String>();

        Optional<Module> mod = ModuleLayer.boot().findModule(moduleName);
        Set<String> packages;
        if (mod.isPresent()) {
            packages = mod.get().getPackages();

            for (String pName : packages) {
                exports.add("--add-exports");
                exports.add(moduleName + "/" + pName + "=" + targetModule);
            }
        }

        return exports;
    }

    /*
     * Return list of tests which match specified prefix
     *
     * @param testPrefix
     *        String prefix to select tests
     */
    static ArrayList<String> getListOfTestsByPrefix(String testPrefix, Set<String> excludeTests) throws Exception {
        ArrayList<String> classes = new ArrayList<String>();

        final String testAnnotationName = "@Test";

        // return empty list in case no selection prefix specified
        if (testPrefix == null || testPrefix.isEmpty())
            return classes;

        // replace "." by "\." in test pattern
        testPrefix = testPrefix.replaceAll("\\.", "\\\\.") + ".*";
        System.out.println("INFO: use following pattern to find tests: " + testPrefix);

        String graalUnitTestFilePath = String.join(File.separator, libsDir, GRAAL_UNITTESTS_JARFILE);
        String classPath = String.join(File.pathSeparator, System.getProperty("java.class.path"),
                String.join(File.separator, libsDir, MXTOOL_JARFILE));

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-cp",  classPath,
                "com.oracle.mxtool.junit.FindClassesByAnnotatedMethods", graalUnitTestFilePath, testAnnotationName);

        System.out.println("INFO: run command " + String.join(" ", pb.command()));

        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        int exitCode = out.getExitValue();
        if (exitCode != 0) {
            throw new Exception("Failed to find tests, VM crashed with exit code " + exitCode);
        }

        String outStr = out.getOutput().trim();
        System.out.println("INFO: command output: [" + outStr + "]");

        String[] lines = outStr.split(" ");
        Arrays.sort(lines);

        if (lines.length > 1) { // first line contains jar file name
            for (int i = 1; i < lines.length; i++) {
                String className = lines[i];

                if (testPrefix.equals(".*") || className.matches(testPrefix)) {
                    // add the test only in case it is not in exclude list
                    if (excludeTests!= null && excludeTests.contains(className)) {
                        System.out.println("INFO: excluded test: " + className);
                    } else {
                        classes.add(className);
                    }
                }
            }
        }

        return classes;
    }

    /*
     * Return set of excluded tests
     *
     * @param excludeFileName
     *        Name of the file to read excluded test list
     */
    static Set loadExcludeList(String excludeFileName) {
        Set<String> excludeTests;

        Path excludeFilePath = Paths.get(excludeFileName);
        try {
            excludeTests = Files.readAllLines(excludeFilePath).stream()
                    .filter(l -> !l.trim().isEmpty())
                    .filter(l -> !l.trim().startsWith("#"))
                    .map(l -> l.split(" ")[0])
                    .collect(Collectors.toSet());

        } catch (IOException ioe) {
            throw new Error("TESTBUG: failed to read " + excludeFilePath);
        }

        return excludeTests;
    }

    static String getUsageString() {
        return "Usage: " + GraalUnitTestLauncher.class.getName() + " " +
                "-prefix (org.graalvm.compiler.api.test) " +
                "-exclude <ExcludedTestsFileName>" + System.lineSeparator();
    }

    public static void main(String... args) throws Exception {

        String testPrefix = null;
        String excludeFileName = null;
        ArrayList<String> testJavaFlags = new ArrayList<String>();

        int i=0;
        String arg, val;
        while (i+1 < args.length) {
            arg = args[i++];
            val = args[i++];

            switch (arg) {
                case "-prefix":
                    testPrefix = val;
                    break;

                case "-exclude":
                    excludeFileName = val;
                    break;

                case "-vmargs":
                   testJavaFlags.addAll(Arrays.asList(val.split("(?i):space:")));
                   break;

                default:
                    System.out.println("WARN: illegal option " + arg);
                    break;
            }
        }

        if (testPrefix == null)
            throw new Error("TESTBUG: no tests to run specified." + System.lineSeparator() + getUsageString());


        Set<String> excludeTests = null;
        if (excludeFileName != null) {
            excludeTests = loadExcludeList(excludeFileName);
        }

        // Find list of tests which match provided predicate and write into GENERATED_TESTCLASSES_FILENAME file
        ArrayList<String> tests = getListOfTestsByPrefix(testPrefix, excludeTests);
        if (tests.size() > 0) {
            Files.write(Paths.get(GENERATED_TESTCLASSES_FILENAME), String.join(System.lineSeparator(), tests).getBytes());
        } else {
            throw new Error("TESTBUG: no tests found for prefix " + testPrefix);
        }

        ArrayList<String> javaFlags = new ArrayList<String>();

        // add modules and exports
        javaFlags.add("--add-modules");
        javaFlags.add("jdk.internal.vm.compiler,jdk.internal.vm.ci");
        javaFlags.add("--add-exports");
        javaFlags.add("java.base/jdk.internal.module=ALL-UNNAMED");
        javaFlags.add("--add-exports");
        javaFlags.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        javaFlags.addAll(getModuleExports("jdk.internal.vm.compiler", "ALL-UNNAMED"));
        javaFlags.addAll(getModuleExports("jdk.internal.vm.ci", "ALL-UNNAMED,jdk.internal.vm.compiler"));

        // add test specific flags
        javaFlags.addAll(testJavaFlags);

        // add VM flags
        javaFlags.add("-XX:+UnlockExperimentalVMOptions");
        javaFlags.add("-XX:+EnableJVMCI");
        javaFlags.add("-Djava.awt.headless=true");
        javaFlags.add("-esa");
        javaFlags.add("-ea");
        // Make sure exception message is never null
        javaFlags.add("-XX:-OmitStackTraceInFastThrow");
        // set timeout factor based on jtreg harness settings
        javaFlags.add("-Dgraaltest.timeout.factor=" + System.getProperty("test.timeout.factor", "1.0"));

        // generate class path
        ArrayList<String> graalJars = new ArrayList<String>(Arrays.asList(GRAAL_EXTRA_JARS));
        graalJars.add(MXTOOL_JARFILE);
        graalJars.add(GRAAL_UNITTESTS_JARFILE);

        String graalJarsCP = graalJars.stream()
                                      .map(s -> String.join(File.separator, libsDir, s))
                                      .collect(Collectors.joining(File.pathSeparator));

        javaFlags.add("-cp");
        // Existing classpath returned by System.getProperty("java.class.path") may contain another
        // version of junit with which the jtreg tool is built. It may be incompatible with required
        // junit version. So we put graalJarsCP before existing classpath when generating a new one
        // to avoid incompatibility issues.
        javaFlags.add(String.join(File.pathSeparator, graalJarsCP, System.getProperty("java.class.path")));

        //
        javaFlags.add("com.oracle.mxtool.junit.MxJUnitWrapper");
        javaFlags.add("-JUnitVerbose");
        javaFlags.add("-JUnitEagerStackTrace");
        javaFlags.add("-JUnitEnableTiming");

        javaFlags.add("@"+GENERATED_TESTCLASSES_FILENAME);

        ProcessBuilder javaPB = ProcessTools.createTestJvm(javaFlags);

        // Some tests rely on MX_SUBPROCESS_COMMAND_FILE env variable which contains
        // name of the file with java executable and java args used to launch the current process.
        Path cmdFile = Files.createTempFile(Path.of(""), "mx_subprocess_", ".cmd");
        Files.write(cmdFile, javaPB.command());
        javaPB.environment().put("MX_SUBPROCESS_COMMAND_FILE", cmdFile.toAbsolutePath().toString());

        System.out.println("INFO: run command: " + String.join(" ", javaPB.command()));

        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(javaPB.start());
        System.out.println("INFO: execution result: " + outputAnalyzer.getOutput());
        outputAnalyzer.shouldHaveExitValue(0);
    }
}
