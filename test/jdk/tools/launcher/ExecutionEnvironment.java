/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.compiler
 *          jdk.zipfs
 * @compile -XDignore.symbol.file ExecutionEnvironment.java
 * @run main/othervm ExecutionEnvironment
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
 *      lib/$arch/libjvm.so -> client/libjvm.so
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

public class ExecutionEnvironment extends TestHelper {
    static final String LD_LIBRARY_PATH    = TestHelper.isMacOSX
            ? "DYLD_LIBRARY_PATH"
            : TestHelper.isAIX
                    ? "LIBPATH"
                    : "LD_LIBRARY_PATH";
    static final String LD_LIBRARY_PATH_32 = LD_LIBRARY_PATH + "_32";
    static final String LD_LIBRARY_PATH_64 = LD_LIBRARY_PATH + "_64";

    // Note: these paths need not exist on the filesytem
    static final String LD_LIBRARY_PATH_VALUE    = "/Bridge/On/The/River/Kwai";
    static final String LD_LIBRARY_PATH_32_VALUE = "/Lawrence/Of/Arabia";
    static final String LD_LIBRARY_PATH_64_VALUE = "/A/Passage/To/India";

    static final String[] LD_PATH_STRINGS = {
        LD_LIBRARY_PATH + "=" + LD_LIBRARY_PATH_VALUE,
        LD_LIBRARY_PATH_32 + "=" + LD_LIBRARY_PATH_32_VALUE,
        LD_LIBRARY_PATH_64 + "=" + LD_LIBRARY_PATH_64_VALUE
    };

    static final File testJarFile = new File("EcoFriendly.jar");

    public ExecutionEnvironment() {
        createTestJar();
    }

    static void createTestJar() {
        try {
            List<String> codeList = new ArrayList<>();
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
            createJar(testJarFile, codeList.toArray(clist));
        } catch (FileNotFoundException fnfe) {
            throw new RuntimeException(fnfe);
        }
    }
    private void flagError(TestResult tr, String message) {
        System.err.println(tr);
        throw new RuntimeException(message);
    }
    /*
     * tests if the launcher pollutes the LD_LIBRARY_PATH variables ie. there
     * should not be any new variables or pollution/mutations of any kind, the
     * environment should be pristine.
     */
    @Test
    void testEcoFriendly() {
        Map<String, String> env = new HashMap<>();
        for (String x : LD_PATH_STRINGS) {
            String pairs[] = x.split("=");
            env.put(pairs[0], pairs[1]);
        }

        TestResult tr =
            doExec(env, javaCmd, "-jar", testJarFile.getAbsolutePath());

        if (!tr.isNotZeroOutput()) {
            flagError(tr, "Error: No output at all. Did the test execute ?");
        }

        for (String x : LD_PATH_STRINGS) {
            if (!tr.contains(x)) {
                if (TestHelper.isAIX && x.startsWith(LD_LIBRARY_PATH)) {
                    // AIX does not support the '-rpath' linker options so the
                    // launchers have to prepend the jdk library path to 'LIBPATH'.
                    String aixLibPath = LD_LIBRARY_PATH + "=" +
                        System.getenv(LD_LIBRARY_PATH) +
                        System.getProperty("path.separator") + LD_LIBRARY_PATH_VALUE;
                    if (!tr.matches(aixLibPath)) {
                        flagError(tr, "FAIL: did not get <" + aixLibPath + ">");
                    }
                }
                else {
                    flagError(tr, "FAIL: did not get <" + x + ">");
                }
            }
        }
    }

    /*
     * ensures that there are no execs as long as we are in the same
     * data model
     */
    @Test
    void testNoExec() {
        Map<String, String> env = new HashMap<>();
        env.put(JLDEBUG_KEY, "true");
        TestResult tr = doExec(env, javaCmd, "-version");
        if (tr.testOutput.contains(EXPECTED_MARKER)) {
            flagError(tr, "testNoExec: found  warning <" + EXPECTED_MARKER +
                    "> the process execing ?");
        }
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
    @Test
    void testJavaLibraryPath() {
        TestResult tr;

        Map<String, String> env = new HashMap<>();

        if (TestHelper.isSolaris) {
            // no override
            env.clear();
            env.put(LD_LIBRARY_PATH, LD_LIBRARY_PATH_VALUE);
            tr = doExec(env, javaCmd, "-jar", testJarFile.getAbsolutePath());
            verifyJavaLibraryPathGeneric(tr);

            env.clear();
            for (String x : LD_PATH_STRINGS) {
                String pairs[] = x.split("=");
                env.put(pairs[0], pairs[1]);
            }

            // verify the override occurs for 64-bit system
            tr = doExec(env, javaCmd, "-jar", testJarFile.getAbsolutePath());
            verifyJavaLibraryPathOverride(tr, false);
        } else {
            for (String x : LD_PATH_STRINGS) {
                String pairs[] = x.split("=");
                env.put(pairs[0], pairs[1]);
            }

            tr = doExec(env, javaCmd, "-jar", testJarFile.getAbsolutePath());
            verifyJavaLibraryPathGeneric(tr);
        }
    }

    private void verifyJavaLibraryPathGeneric(TestResult tr) {
        if (!tr.matches("java.library.path=.*" + LD_LIBRARY_PATH_VALUE + ".*")) {
            flagError(tr, "testJavaLibraryPath: java.library.path does not contain " +
                    LD_LIBRARY_PATH_VALUE);
        }
    }

    private void verifyJavaLibraryPathOverride(TestResult tr,
            boolean is32Bit) {
        // make sure the 32/64 bit value exists
        if (!tr.matches("java.library.path=.*" +
                (is32Bit ? LD_LIBRARY_PATH_32_VALUE : LD_LIBRARY_PATH_64_VALUE) + ".*")) {
            flagError(tr, "verifyJavaLibraryPathOverride: " +
                " java.library.path does not contain " +
                    (is32Bit ? LD_LIBRARY_PATH_32_VALUE : LD_LIBRARY_PATH_64_VALUE));

        }
        // make sure the generic value is absent
        if (!tr.notMatches("java.library.path=.*" + LD_LIBRARY_PATH_VALUE + ".*")) {
            flagError(tr, "verifyJavaLibraryPathOverride: " +
                    " java.library.path contains " + LD_LIBRARY_PATH_VALUE);
        }
    }

    /*
     * ensures we have indeed exec'ed the correct vm of choice if it exists
     */
    @Test
    void testVmSelection() {
        if (haveClientVM) {
            TestResult tr = doExec(javaCmd, "-client", "-version");
            if (!tr.matches(".*Client VM.*")) {
                flagError(tr, "the expected vm -client did not launch");
            }
        }
        if (haveServerVM) {
            TestResult tr = doExec(javaCmd, "-server", "-version");
            if (!tr.matches(".*Server VM.*")) {
                flagError(tr, "the expected vm -server did not launch");
            }
        }
    }

    /*
     * checks to see there is no extra libjvm.so than needed
     */
    @Test
    void testNoSymLink() {
        if (is64Bit) {
            return;
        }

        File symLink = null;
        String libPathPrefix = "/lib";
        symLink = new File(JAVAHOME, libPathPrefix +
                getJreArch() + "/" + LIBJVM);
        if (symLink.exists()) {
            throw new RuntimeException("symlink exists " + symLink.getAbsolutePath());
        }
    }
    public static void main(String... args) throws Exception {
        if (isWindows) {
            System.err.println("Warning: test not applicable to windows");
            return;
        }
        ExecutionEnvironment ee = new ExecutionEnvironment();
        ee.run(args);
    }
}
