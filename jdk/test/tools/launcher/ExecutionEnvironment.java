/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4780570 4731671 6354700 6367077 6670965 4882974
 * @summary Checks for LD_LIBRARY_PATH and execution  on *nixes
 * @compile -XDignore.symbol.file ExecutionEnvironment.java TestHelper.java
 * @run main ExecutionEnvironment
 */

/*
 * This tests for various things as follows:
 * Ensures that:
 *   1. uneccessary execs do not occur
 *   2. the environment is pristine,  users environment variable wrt.
 *      LD_LIBRARY_PATH if set are not modified in any way.
 *   3. the correct vm is chosen with -server and -client options
 *   4. the VM on Solaris correctly interprets the LD_LIBRARY_PATH32
 *      and LD_LIBRARY_PATH64 variables if set by the user, ie.
 *      i. on 32 bit systems:
 *         a. if LD_LIBRARY_PATH32 is set it will override LD_LIBRARY_PATH
 *         b. LD_LIBRARY_PATH64 is ignored if set
 *      ii. on 64 bit systems:
 *            a. if LD_LIBRARY_PATH64 is set it will override LD_LIBRARY_PATH
 *            b. LD_LIBRARY_PATH32 is ignored if set
 *   5. no extra symlink exists on Solaris ie.
 *      jre/lib/$arch/libjvm.so -> client/libjvm.so
 * TODO:
 *      a. perhaps we need to add a test to audit all environment variables are
 *         in pristine condition after the launch, there may be a few that the
 *         launcher may add as implementation details.
 *      b. add a pldd for solaris to ensure only one libjvm.so is linked
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ExecutionEnvironment {
    static final String LD_LIBRARY_PATH    = "LD_LIBRARY_PATH";
    static final String LD_LIBRARY_PATH_32 = LD_LIBRARY_PATH + "_32";
    static final String LD_LIBRARY_PATH_64 = LD_LIBRARY_PATH + "_64";

    // Note: these paths need not exist on the filesytem
    static final String LD_LIBRARY_PATH_VALUE    = "/Bridge/On/The/River/Kwai";
    static final String LD_LIBRARY_PATH_32_VALUE = "/Lawrence/Of/Arabia";
    static final String LD_LIBRARY_PATH_64_VALUE = "/A/Passage/To/India";

    static final String JLDEBUG_KEY = "_JAVA_LAUNCHER_DEBUG";
    static final String EXPECTED_MARKER = "TRACER_MARKER:About to EXEC";

    static final String[] LD_PATH_STRINGS = {
        LD_LIBRARY_PATH + "=" + LD_LIBRARY_PATH_VALUE,
        LD_LIBRARY_PATH_32 + "=" + LD_LIBRARY_PATH_32_VALUE,
        LD_LIBRARY_PATH_64 + "=" + LD_LIBRARY_PATH_64_VALUE
    };

    static final File testJarFile = new File("EcoFriendly.jar");

    static int errors = 0;
    static int passes = 0;

    static final String LIBJVM = TestHelper.isWindows ? "jvm.dll" : "libjvm.so";

    static void createTestJar() {
        try {
            List<String> codeList = new ArrayList<String>();
            codeList.add("static void printValue(String name, boolean property) {\n");
            codeList.add("    String value = (property) ? System.getProperty(name) : System.getenv(name);\n");
            codeList.add("    System.out.println(name + \"=\" + value);\n");
            codeList.add("}\n");
            codeList.add("public static void main(String... args) {\n");
            codeList.add("    System.out.println(\"Execute test:\");\n");
            codeList.add("    printValue(\"os.name\", true);\n");
            codeList.add("    printValue(\"os.arch\", true);\n");
            codeList.add("    printValue(\"os.version\", true);\n");
            codeList.add("    printValue(\"sun.arch.data.model\", true);\n");
            codeList.add("    printValue(\"java.library.path\", true);\n");
            codeList.add("    printValue(\"" + LD_LIBRARY_PATH + "\", false);\n");
            codeList.add("    printValue(\"" + LD_LIBRARY_PATH_32 + "\", false);\n");
            codeList.add("    printValue(\"" + LD_LIBRARY_PATH_64 + "\", false);\n");
            codeList.add("}\n");
            String[] clist = new String[codeList.size()];
            TestHelper.createJar(testJarFile, codeList.toArray(clist));
        } catch (FileNotFoundException fnfe) {
            throw new RuntimeException(fnfe);
        }
    }

    /*
     * tests if the launcher pollutes the LD_LIBRARY_PATH variables ie. there
     * should not be any new variables or pollution/mutations of any kind, the
     * environment should be pristine.
     */
    private static void ensureEcoFriendly() {
        TestHelper.TestResult tr = null;

        Map<String, String> env = new HashMap<String, String>();
        for (String x : LD_PATH_STRINGS) {
            String pairs[] = x.split("=");
            env.put(pairs[0], pairs[1]);
        }

        tr = TestHelper.doExec(env, TestHelper.javaCmd, "-jar",
                testJarFile.getAbsolutePath());

        if (!tr.isNotZeroOutput()) {
            System.out.println(tr);
            throw new RuntimeException("Error: No output at all. Did the test execute ?");
        }

        for (String x : LD_PATH_STRINGS) {
            if (!tr.contains(x)) {
                System.out.println("FAIL: did not get <" + x + ">");
                System.out.println(tr);
                errors++;
            } else {
                passes++;
            }
        }
    }

    /*
     * ensures that there are no execs as long as we are in the same
     * data model
     */
    static void ensureNoExec() {
        Map<String, String> env = new HashMap<String, String>();
        env.put(JLDEBUG_KEY, "true");
        TestHelper.TestResult tr =
                TestHelper.doExec(env, TestHelper.javaCmd, "-version");
        if (tr.testOutput.contains(EXPECTED_MARKER)) {
            System.out.println("FAIL: EnsureNoExecs: found expected warning <" +
                    EXPECTED_MARKER +
                    "> the process execing ?");
            errors++;
        } else {
            passes++;
        }
        return;
    }

    /*
     * This test ensures that LD_LIBRARY_PATH* values are interpreted by the VM
     * and the expected java.library.path behaviour.
     * For Generic platforms (All *nixes):
     *    * All LD_LIBRARY_PATH variable should be on java.library.path
     * For Solaris 32-bit
     *    * The LD_LIBRARY_PATH_32 should override LD_LIBRARY_PATH if specified
     * For Solaris 64-bit
     *    * The LD_LIBRARY_PATH_64 should override LD_LIBRARY_PATH if specified
     */

    static void verifyJavaLibraryPath() {
        TestHelper.TestResult tr = null;

        Map<String, String> env = new HashMap<String, String>();

        if (TestHelper.isLinux) {
            for (String x : LD_PATH_STRINGS) {
                String pairs[] = x.split("=");
                env.put(pairs[0], pairs[1]);
            }

            tr = TestHelper.doExec(env, TestHelper.javaCmd, "-jar",
                    testJarFile.getAbsolutePath());
            verifyJavaLibraryPathGeneric(tr);
        } else {
            // no override
            env.clear();
            env.put(LD_LIBRARY_PATH, LD_LIBRARY_PATH_VALUE);
            tr = TestHelper.doExec(env, TestHelper.javaCmd, "-jar",
                    testJarFile.getAbsolutePath());
            verifyJavaLibraryPathGeneric(tr);

            env.clear();
            for (String x : LD_PATH_STRINGS) {
                String pairs[] = x.split("=");
                env.put(pairs[0], pairs[1]);
            }

            // verify the override occurs, since we know the invocation always
            // uses by default is 32-bit, therefore we also set the test
            // expectation to be the same.
            tr = TestHelper.doExec(env, TestHelper.javaCmd, "-jar",
                    testJarFile.getAbsolutePath());
            verifyJavaLibraryPathOverride(tr, true);

            // try changing the model from 32 to 64 bit
            if (TestHelper.dualModePresent() && TestHelper.is32Bit) {
                // verify the override occurs
                env.clear();
                for (String x : LD_PATH_STRINGS) {
                    String pairs[] = x.split("=");
                    env.put(pairs[0], pairs[1]);
                }
                tr = TestHelper.doExec(env, TestHelper.javaCmd, "-d64", "-jar",
                    testJarFile.getAbsolutePath());
                verifyJavaLibraryPathOverride(tr, false);

                // no override
                env.clear();
                env.put(LD_LIBRARY_PATH, LD_LIBRARY_PATH_VALUE);
                tr = TestHelper.doExec(env, TestHelper.javaCmd, "-jar",
                        testJarFile.getAbsolutePath());
                verifyJavaLibraryPathGeneric(tr);
            }

            // try changing the model from 64 to 32 bit
            if (TestHelper.java64Cmd != null && TestHelper.is64Bit) {
                // verify the override occurs
                env.clear();
                for (String x : LD_PATH_STRINGS) {
                    String pairs[] = x.split("=");
                    env.put(pairs[0], pairs[1]);
                }
                tr = TestHelper.doExec(env, TestHelper.java64Cmd, "-d32", "-jar",
                    testJarFile.getAbsolutePath());
                verifyJavaLibraryPathOverride(tr, true);

                // no override
                env.clear();
                env.put(LD_LIBRARY_PATH, LD_LIBRARY_PATH_VALUE);
                tr = TestHelper.doExec(env, TestHelper.java64Cmd, "-d32", "-jar",
                        testJarFile.getAbsolutePath());
                verifyJavaLibraryPathGeneric(tr);
            }
        }
    }

    private static void verifyJavaLibraryPathGeneric(TestHelper.TestResult tr) {
        if (!tr.matches("java.library.path=.*" + LD_LIBRARY_PATH_VALUE + ".*")) {
            System.out.print("FAIL: verifyJavaLibraryPath: ");
            System.out.println(" java.library.path does not contain " +
                    LD_LIBRARY_PATH_VALUE);
            System.out.println(tr);
            errors++;
        } else {
            passes++;
        }
    }

    private static void verifyJavaLibraryPathOverride(TestHelper.TestResult tr,
            boolean is32Bit) {
        // make sure the 32/64 bit value exists
        if (!tr.matches("java.library.path=.*" +
                (is32Bit ? LD_LIBRARY_PATH_32_VALUE : LD_LIBRARY_PATH_64_VALUE) + ".*")) {
            System.out.print("FAIL: verifyJavaLibraryPathOverride: ");
            System.out.println(" java.library.path does not contain " +
                    (is32Bit ? LD_LIBRARY_PATH_32_VALUE : LD_LIBRARY_PATH_64_VALUE));
            System.out.println(tr);
            errors++;
        } else {
            passes++;
        }
        // make sure the generic value is absent
        if (tr.matches("java.library.path=.*" + LD_LIBRARY_PATH_VALUE + ".*")) {
            System.out.print("FAIL: verifyJavaLibraryPathOverride: ");
            System.out.println(" java.library.path contains " +
                    LD_LIBRARY_PATH_VALUE);
            System.out.println(tr);
            errors++;
        } else {
            passes++;
        }
    }

    /*
     * ensures we have indeed exec'ed the correct vm of choice, all VMs support
     * -server, however 32-bit VMs support -client and -server.
     */
    static void verifyVmSelection() {

        TestHelper.TestResult tr = null;

        if (TestHelper.is32Bit) {
            tr = TestHelper.doExec(TestHelper.javaCmd, "-client", "-version");
            if (!tr.matches("Java.*Client VM.*")) {
                System.out.println("FAIL: the expected vm -client did launch");
                System.out.println(tr);
                errors++;
            } else {
                passes++;
            }
        }
        tr = TestHelper.doExec(TestHelper.javaCmd, "-server", "-version");
        if (!tr.matches("Java.*Server VM.*")) {
            System.out.println("FAIL: the expected vm -server did launch");
            System.out.println(tr);
            errors++;
        } else {
            passes++;
        }
    }

    /*
     * checks to see there is no extra libjvm.so than needed
     */
    static void verifyNoSymLink() {
        if (TestHelper.is64Bit) {
            return;
        }

        File symLink = null;
        String libPathPrefix = TestHelper.isSDK ? "jre/lib" : "/lib";
        symLink = new File(TestHelper.JAVAHOME, libPathPrefix +
                TestHelper.getJreArch() + "/" + LIBJVM);
        if (symLink.exists()) {
            System.out.println("FAIL: The symlink exists " +
                    symLink.getAbsolutePath());
            errors++;
        } else {
            passes++;
        }
    }

    public static void main(String... args) throws Exception {
        if (TestHelper.isWindows) {
            System.out.println("Warning: noop on windows");
            return;
        }
        // create our test jar first
        createTestJar();
        ensureNoExec();
        verifyVmSelection();
        ensureEcoFriendly();
        verifyJavaLibraryPath();
        verifyNoSymLink();
        if (errors > 0) {
            throw new Exception("ExecutionEnvironment: FAIL: with " +
                    errors + " errors and passes " + passes );
        } else {
            System.out.println("ExecutionEnvironment: PASS " + passes);
        }
    }
}
