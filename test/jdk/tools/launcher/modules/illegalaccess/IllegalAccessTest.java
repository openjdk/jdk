/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires vm.compMode != "Xcomp"
 * @modules java.base/jdk.internal.misc
 *          java.base/sun.security.x509
 * @library /test/lib /lib/testlibrary modules
 * @build IllegalAccessTest TryAccess JarUtils
 *        jdk.test.lib.compiler.CompilerUtils
 * @build m/*
 * @run testng/othervm/timeout=180 IllegalAccessTest
 * @summary Basic test for java --illegal-access=$VALUE
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Basic test of --illegal-access=value to deny or permit access to JDK internals.
 */

@Test
public class IllegalAccessTest {

    static final String TEST_SRC = System.getProperty("test.src");
    static final String TEST_CLASSES = System.getProperty("test.classes");
    static final String MODULE_PATH = System.getProperty("jdk.module.path");

    /**
     * Represents the expected result of a test.
     */
    static final class Result {
        private final boolean success;
        private final List<String> expectedOutput = new ArrayList<>();
        private final List<String> notExpectedOutput = new ArrayList<>();

        Result(boolean success) {
            this.success = success;
        }

        Result expect(String msg) {
            expectedOutput.add(msg);
            return this;
        }

        Result doNotExpect(String msg) {
            notExpectedOutput.add(msg);
            return this;
        }

        boolean shouldSucceed() {
            return success;
        }

        Stream<String> expectedOutput() {
            return expectedOutput.stream();
        }

        Stream<String> notExpectedOutput() {
            return notExpectedOutput.stream();
        }

        @Override
        public String toString() {
            String s = (success) ? "success" : "failure";
            for (String msg : expectedOutput) {
                s += "/" + msg;
            }
            return s;
        }
    }

    static Result success() {
        return new Result(true);
    }

    static Result successNoWarning() {
        return success().doNotExpect("WARNING");
    }

    static Result successWithWarning() {
        return success().expect("WARNING");
    }

    static Result fail(String expectedOutput) {
        return new Result(false).expect(expectedOutput).doNotExpect("WARNING");
    }

    @DataProvider(name = "denyCases")
    public Object[][] denyCases() {
        return new Object[][] {
            { "accessPublicClassNonExportedPackage", fail("IllegalAccessError") },
            { "accessPublicClassJdk9NonExportedPackage", fail("IllegalAccessError") },

            { "reflectPublicMemberExportedPackage", successNoWarning() },
            { "reflectNonPublicMemberExportedPackage", fail("IllegalAccessException") },
            { "reflectPublicMemberNonExportedPackage", fail("IllegalAccessException") },
            { "reflectNonPublicMemberNonExportedPackage", fail("IllegalAccessException") },
            { "reflectPublicMemberJdk9NonExportedPackage", fail("IllegalAccessException") },
            { "reflectPublicMemberApplicationModule", successNoWarning() },

            { "setAccessiblePublicMemberExportedPackage", successNoWarning() },
            { "setAccessibleNonPublicMemberExportedPackage", fail("InaccessibleObjectException") },
            { "setAccessiblePublicMemberNonExportedPackage", fail("InaccessibleObjectException") },
            { "setAccessibleNonPublicMemberNonExportedPackage", fail("InaccessibleObjectException") },
            { "setAccessiblePublicMemberJdk9NonExportedPackage", fail("InaccessibleObjectException") },
            { "setAccessiblePublicMemberApplicationModule", successNoWarning() },
            { "setAccessibleNotPublicMemberApplicationModule", fail("InaccessibleObjectException") },

            { "privateLookupPublicClassExportedPackage", fail("IllegalAccessException") },
            { "privateLookupNonPublicClassExportedPackage", fail("IllegalAccessException") },
            { "privateLookupPublicClassNonExportedPackage", fail("IllegalAccessException") },
            { "privateLookupNonPublicClassNonExportedPackage", fail("IllegalAccessException") },
            { "privateLookupPublicClassJdk9NonExportedPackage", fail("IllegalAccessException") },
        };
    }

    @DataProvider(name = "permitCases")
    public Object[][] permitCases() {
        return new Object[][] {
            { "accessPublicClassNonExportedPackage", successNoWarning() },
            { "accessPublicClassJdk9NonExportedPackage", fail("IllegalAccessError") },

            { "reflectPublicMemberExportedPackage", successNoWarning() },
            { "reflectNonPublicMemberExportedPackage", fail("IllegalAccessException") },
            { "reflectPublicMemberNonExportedPackage", successWithWarning() },
            { "reflectNonPublicMemberNonExportedPackage", fail("IllegalAccessException") },
            { "reflectPublicMemberJdk9NonExportedPackage", fail("IllegalAccessException") },

            { "setAccessiblePublicMemberExportedPackage", successNoWarning()},
            { "setAccessibleNonPublicMemberExportedPackage", successWithWarning() },
            { "setAccessiblePublicMemberNonExportedPackage", successWithWarning() },
            { "setAccessibleNonPublicMemberNonExportedPackage", successWithWarning() },
            { "setAccessiblePublicMemberJdk9NonExportedPackage", fail("InaccessibleObjectException") },
            { "setAccessiblePublicMemberApplicationModule", successNoWarning() },
            { "setAccessibleNotPublicMemberApplicationModule", fail("InaccessibleObjectException") },

            { "privateLookupPublicClassExportedPackage", successWithWarning() },
            { "privateLookupNonPublicClassExportedPackage", successWithWarning() },
            { "privateLookupPublicClassNonExportedPackage", successWithWarning() },
            { "privateLookupNonPublicClassNonExportedPackage",  successWithWarning() },
            { "privateLookupPublicClassJdk9NonExportedPackage", fail("IllegalAccessException") },
            { "privateLookupPublicClassApplicationModule", fail("IllegalAccessException") },
        };
    }

    /**
     * Checks an expected result with the output captured by the given
     * OutputAnalyzer.
     */
    void checkResult(Result expectedResult, OutputAnalyzer outputAnalyzer) {
        expectedResult.expectedOutput().forEach(outputAnalyzer::shouldContain);
        expectedResult.notExpectedOutput().forEach(outputAnalyzer::shouldNotContain);
        int exitValue = outputAnalyzer.getExitValue();
        if (expectedResult.shouldSucceed()) {
            assertTrue(exitValue == 0);
        } else {
            assertTrue(exitValue != 0);
        }
    }

    /**
     * Runs the test to execute the given test action. The VM is run with the
     * given VM options and the output checked to see that it matches the
     * expected result.
     */
    OutputAnalyzer run(String action, Result expectedResult, String... vmopts)
        throws Exception
    {
        Stream<String> s1 = Stream.of(vmopts);
        Stream<String> s2 = Stream.of("-p", MODULE_PATH, "--add-modules=m",
                "-cp", TEST_CLASSES, "TryAccess", action);
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        OutputAnalyzer outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.out)
                .errorTo(System.out);
        if (expectedResult != null)
            checkResult(expectedResult, outputAnalyzer);
        return outputAnalyzer;
    }

    OutputAnalyzer run(String action, String... vmopts) throws Exception {
        return run(action, null, vmopts);
    }

    /**
     * Runs an executable JAR to execute the given test action. The VM is run
     * with the given VM options and the output checked to see that it matches
     * the expected result.
     */
    void run(Path jarFile, String action, Result expectedResult, String... vmopts)
        throws Exception
    {
        Stream<String> s1 = Stream.of(vmopts);
        Stream<String> s2 = Stream.of("-jar", jarFile.toString(), action);
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        checkResult(expectedResult, ProcessTools.executeTestJava(opts)
                                                .outputTo(System.out)
                                                .errorTo(System.out));
    }

    @Test(dataProvider = "denyCases")
    public void testDeny(String action, Result expectedResult) throws Exception {
        run(action, expectedResult, "--illegal-access=deny");
    }

    @Test(dataProvider = "permitCases")
    public void testDefault(String action, Result expectedResult) throws Exception {
        run(action, expectedResult);
    }

    @Test(dataProvider = "permitCases")
    public void testPermit(String action, Result expectedResult) throws Exception {
        run(action, expectedResult, "--illegal-access=permit");
    }

    @Test(dataProvider = "permitCases")
    public void testWarn(String action, Result expectedResult) throws Exception {
        run(action, expectedResult, "--illegal-access=warn");
    }

    @Test(dataProvider = "permitCases")
    public void testDebug(String action, Result expectedResult) throws Exception {
        // expect stack trace with WARNING
        if (expectedResult.expectedOutput().anyMatch("WARNING"::equals)) {
            expectedResult.expect("TryAccess.main");
        }
        run(action, expectedResult, "--illegal-access=debug");
    }


    /**
     * Specify --add-exports to export a package
     */
    public void testWithAddExportsOption() throws Exception {
        // warning
        run("reflectPublicMemberNonExportedPackage", successWithWarning());

        // no warning due to --add-exports
        run("reflectPublicMemberNonExportedPackage", successNoWarning(),
                "--add-exports", "java.base/sun.security.x509=ALL-UNNAMED");

        // attempt two illegal accesses, one allowed by --add-exports
        run("reflectPublicMemberNonExportedPackage"
                + ",setAccessibleNonPublicMemberExportedPackage",
            successWithWarning(),
            "--add-exports", "java.base/sun.security.x509=ALL-UNNAMED");
    }

    /**
     * Specify --add-open to open a package
     */
    public void testWithAddOpensOption() throws Exception {
        // warning
        run("setAccessibleNonPublicMemberExportedPackage", successWithWarning());

        // no warning due to --add-opens
        run("setAccessibleNonPublicMemberExportedPackage", successNoWarning(),
                "--add-opens", "java.base/java.lang=ALL-UNNAMED");

        // attempt two illegal accesses, one allowed by --add-opens
        run("reflectPublicMemberNonExportedPackage"
                + ",setAccessibleNonPublicMemberExportedPackage",
            successWithWarning(),
            "--add-opens", "java.base/java.lang=ALL-UNNAMED");
    }

    /**
     * Test reflective API to export a package
     */
    public void testWithReflectiveExports() throws Exception {
        // compile patch for java.base
        Path src = Paths.get(TEST_SRC, "patchsrc", "java.base");
        Path patch = Files.createDirectories(Paths.get("patches", "java.base"));
        assertTrue(CompilerUtils.compile(src, patch,
                                         "--patch-module", "java.base=" + src));

        // reflectively export, then access
        run("exportNonExportedPackages,reflectPublicMemberNonExportedPackage",
                successNoWarning(),
                "--patch-module", "java.base=" + patch);

        // access, reflectively export, access again
        List<String> output = run("reflectPublicMemberNonExportedPackage,"
                        + "exportNonExportedPackages,"
                        + "reflectPublicMemberNonExportedPackage",
                "--patch-module", "java.base="+patch,
                "--illegal-access=warn").asLines();
        assertTrue(count(output, "WARNING") == 1);  // one warning
    }

    /**
     * Test reflective API to open a package
     */
    public void testWithReflectiveOpens() throws Exception {
        // compile patch for java.base
        Path src = Paths.get(TEST_SRC, "patchsrc", "java.base");
        Path patch = Files.createDirectories(Paths.get("patches", "java.base"));
        assertTrue(CompilerUtils.compile(src, patch,
                                         "--patch-module", "java.base=" + src));

        // reflectively open exported package, then access
        run("openExportedPackage,setAccessibleNonPublicMemberExportedPackage",
                successNoWarning(),
                "--patch-module", "java.base=" + patch);

        // access, reflectively open exported package, access again
        List<String> output1 = run("setAccessibleNonPublicMemberExportedPackage"
                        + ",openExportedPackage"
                        + ",setAccessibleNonPublicMemberExportedPackage",
                "--patch-module", "java.base=" + patch,
                "--illegal-access=warn").asLines();
        assertTrue(count(output1, "WARNING") == 1);  // one warning

        // reflectively open non-exported packages, then access
        run("openNonExportedPackages,setAccessibleNonPublicMemberNonExportedPackage",
                successNoWarning(),
                "--patch-module", "java.base=" + patch);

        // access, reflectively open non-exported package, access again
        List<String> output2 = run("setAccessibleNonPublicMemberNonExportedPackage"
                        + ",openNonExportedPackages"
                        + ",setAccessibleNonPublicMemberNonExportedPackage",
                "--patch-module", "java.base=" + patch,
                "--illegal-access=warn").asLines();
        assertTrue(count(output2, "WARNING") == 1);  // one warning
    }

    /**
     * Specify Add-Exports in JAR file manifest
     */
    public void testWithAddExportsInManifest() throws Exception {
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "TryAccess");
        attrs.put(new Attributes.Name("Add-Exports"), "java.base/sun.security.x509");
        Path jarfile = Paths.get("x.jar");
        Path classes = Paths.get(TEST_CLASSES);
        JarUtils.createJarFile(jarfile, man, classes, Paths.get("TryAccess.class"));

        run(jarfile, "reflectPublicMemberNonExportedPackage", successNoWarning());

        run(jarfile, "setAccessibleNonPublicMemberExportedPackage", successWithWarning());

        // attempt two illegal accesses, one allowed by Add-Exports
        run(jarfile, "reflectPublicMemberNonExportedPackage,"
                + "setAccessibleNonPublicMemberExportedPackage",
            successWithWarning());
    }

    /**
     * Specify Add-Opens in JAR file manifest
     */
    public void testWithAddOpensInManifest() throws Exception {
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "TryAccess");
        attrs.put(new Attributes.Name("Add-Opens"), "java.base/java.lang");
        Path jarfile = Paths.get("x.jar");
        Path classes = Paths.get(TEST_CLASSES);
        JarUtils.createJarFile(jarfile, man, classes, Paths.get("TryAccess.class"));

        run(jarfile, "setAccessibleNonPublicMemberExportedPackage", successNoWarning());

        run(jarfile, "reflectPublicMemberNonExportedPackage", successWithWarning());

        // attempt two illegal accesses, one allowed by Add-Opens
        run(jarfile, "reflectPublicMemberNonExportedPackage,"
                + "setAccessibleNonPublicMemberExportedPackage",
            successWithWarning());
    }

    /**
     * Test that default behavior is to print a warning on the first illegal
     * access only.
     */
    public void testWarnOnFirstIllegalAccess() throws Exception {
        String action1 = "reflectPublicMemberNonExportedPackage";
        String action2 = "setAccessibleNonPublicMemberExportedPackage";
        int warningCount = count(run(action1).asLines(), "WARNING");

        // same illegal access
        List<String> output1 = run(action1 + "," + action1).asLines();
        assertTrue(count(output1, "WARNING") == warningCount);

        // different illegal access
        List<String> output2 = run(action1 + "," + action2).asLines();
        assertTrue(count(output2, "WARNING") == warningCount);
    }

    /**
     * Test that --illegal-access=warn prints a one-line warning per each unique
     * illegal access.
     */
    public void testWarnPerIllegalAccess() throws Exception {
        String action1 = "reflectPublicMemberNonExportedPackage";
        String action2 = "setAccessibleNonPublicMemberExportedPackage";

        // same illegal access
        String repeatedActions = action1 + "," + action1;
        List<String> output1 = run(repeatedActions, "--illegal-access=warn").asLines();
        assertTrue(count(output1, "WARNING") == 1);

        // different illegal access
        String differentActions = action1 + "," + action2;
        List<String> output2 = run(differentActions, "--illegal-access=warn").asLines();
        assertTrue(count(output2, "WARNING") == 2);
    }

    /**
     * Specify --illegal-access more than once, last one wins
     */
    public void testRepeatedOption() throws Exception {
        run("accessPublicClassNonExportedPackage", successNoWarning(),
                "--illegal-access=deny", "--illegal-access=permit");
        run("accessPublicClassNonExportedPackage", fail("IllegalAccessError"),
                "--illegal-access=permit", "--illegal-access=deny");
    }

    /**
     * Specify bad value to --illegal-access
     */
    public void testBadValue() throws Exception {
        run("accessPublicClassNonExportedPackage",
                fail("Value specified to --illegal-access not recognized"),
                "--illegal-access=BAD");
    }

    private int count(Iterable<String> lines, CharSequence cs) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(cs)) count++;
        }
        return count;
    }
}
