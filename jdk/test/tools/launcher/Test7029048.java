/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7029048
 * @summary Checks for LD_LIBRARY_PATH on *nixes
 * @compile -XDignore.symbol.file ExecutionEnvironment.java TestHelper.java Test7029048.java
 * @run main Test7029048
 */

/*
 * 7029048: test for LD_LIBRARY_PATH set to different paths pointing which may
 * contain a libjvm.so and may not, but we test to ensure that the launcher
 * behaves correctly in all cases.
 */
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test7029048 {

    static int passes = 0;
    static int errors = 0;

    private static final String LIBJVM = ExecutionEnvironment.LIBJVM;
    private static final String LD_LIBRARY_PATH =
            ExecutionEnvironment.LD_LIBRARY_PATH;
    private static final String LD_LIBRARY_PATH_32 =
            ExecutionEnvironment.LD_LIBRARY_PATH_32;
    private static final String LD_LIBRARY_PATH_64 =
            ExecutionEnvironment.LD_LIBRARY_PATH_64;

    private static final File libDir =
            new File(System.getProperty("sun.boot.library.path"));
    private static final File srcServerDir = new File(libDir, "server");
    private static final File srcLibjvmSo = new File(srcServerDir, LIBJVM);

    private static final File dstLibDir = new File("lib");
    private static final File dstLibArchDir =
            new File(dstLibDir, TestHelper.getJreArch());

    private static final File dstServerDir = new File(dstLibArchDir, "server");
    private static final File dstServerLibjvm = new File(dstServerDir, LIBJVM);

    private static final File dstClientDir = new File(dstLibArchDir, "client");
    private static final File dstClientLibjvm = new File(dstClientDir, LIBJVM);

    // used primarily to test the solaris variants in dual mode
    private static final File dstOtherArchDir;
    private static final File dstOtherServerDir;
    private static final File dstOtherServerLibjvm;

    private static final Map<String, String> env = new HashMap<>();

    static {
        if (TestHelper.isDualMode) {
            dstOtherArchDir = new File(dstLibDir, TestHelper.getComplementaryJreArch());
            dstOtherServerDir = new File(dstOtherArchDir, "server");
            dstOtherServerLibjvm = new File(dstOtherServerDir, LIBJVM);
        } else {
            dstOtherArchDir = null;
            dstOtherServerDir = null;
            dstOtherServerLibjvm = null;
        }
    }

    static String getValue(String name, List<String> in) {
        for (String x : in) {
            String[] s = x.split("=");
            if (name.equals(s[0].trim())) {
                return s[1].trim();
            }
        }
        return null;
    }

    static void run(boolean want32, String dflag, Map<String, String> env,
            int nLLPComponents, String caseID) {
        final boolean want64 = want32 == false;
        env.put(ExecutionEnvironment.JLDEBUG_KEY, "true");
        List<String> cmdsList = new ArrayList<>();

        // only for a dual-mode system
        if (want64 && TestHelper.isDualMode) {
            cmdsList.add(TestHelper.java64Cmd);
        } else {
            cmdsList.add(TestHelper.javaCmd); // a 32-bit java command for all
        }

        /*
         * empty or null strings can confuse the ProcessBuilder. A null flag
         * indicates that the appropriate data model is enforced on the chosen
         * launcher variant.
         */

        if (dflag != null) {
            cmdsList.add(dflag);
        } else {
            cmdsList.add(want32 ? "-d32" : "-d64");
        }
        cmdsList.add("-server");
        cmdsList.add("-jar");
        cmdsList.add(ExecutionEnvironment.testJarFile.getAbsolutePath());
        String[] cmds = new String[cmdsList.size()];
        TestHelper.TestResult tr = TestHelper.doExec(env, cmdsList.toArray(cmds));
        analyze(tr, nLLPComponents, caseID);
    }

    // no cross launch, ie. no change to the data model.
    static void run(Map<String, String> env, int nLLPComponents, String caseID)
            throws IOException {
        boolean want32 = TestHelper.is32Bit;
        run(want32, null, env, nLLPComponents, caseID);
    }

    static void analyze(TestHelper.TestResult tr, int nLLPComponents, String caseID) {
        String envValue = getValue(LD_LIBRARY_PATH, tr.testOutput);
       /*
        * the envValue can never be null, since the test code should always
        * print a "null" string.
        */
        if (envValue == null) {
            System.out.println(tr);
            throw new RuntimeException("NPE, likely a program crash ??");
        }
        String values[] = envValue.split(File.pathSeparator);
        if (values.length == nLLPComponents) {
            System.out.println(caseID + " :OK");
            passes++;
        } else {
            System.out.println("FAIL: test7029048, " + caseID);
            System.out.println(" expected " + nLLPComponents
                    + " but got " + values.length);
            System.out.println(envValue);
            System.out.println(tr);
            errors++;
        }
    }

    /*
     * A crucial piece, specifies what we should expect, given the conditions.
     * That is for a given enum type, the value indicates how many absolute
     * environment variables that can be expected. This value is used to base
     * the actual expected values by adding the set environment variable usually
     * it is 1, but it could be more if the test wishes to set more paths in
     * the future.
     */
    private static enum LLP_VAR {
        LLP_SET_NON_EXISTENT_PATH(0),   // env set, but the path does not exist
        LLP_SET_EMPTY_PATH(0),          // env set, with a path but no libjvm.so
        LLP_SET_WITH_JVM(3);            // env set, with a libjvm.so
        private final int value;
        LLP_VAR(int i) {
            this.value = i;
        }
    }

    /*
     * test for 7029048
     */
    static void test7029048() throws IOException {
        String desc = null;
        for (LLP_VAR v : LLP_VAR.values()) {
            switch (v) {
                case LLP_SET_WITH_JVM:
                    // copy the files into the directory structures
                    TestHelper.copyFile(srcLibjvmSo, dstServerLibjvm);
                    // does not matter if it is client or a server
                    TestHelper.copyFile(srcLibjvmSo, dstClientLibjvm);
                    // does not matter if the arch do not match either
                    if (TestHelper.isDualMode) {
                        TestHelper.copyFile(srcLibjvmSo, dstOtherServerLibjvm);
                    }
                    desc = "LD_LIBRARY_PATH should be set";
                    break;
                case LLP_SET_EMPTY_PATH:
                    if (!dstClientDir.exists()) {
                        Files.createDirectories(dstClientDir.toPath());
                    } else {
                        Files.deleteIfExists(dstClientLibjvm.toPath());
                    }

                    if (!dstServerDir.exists()) {
                        Files.createDirectories(dstServerDir.toPath());
                    } else {
                        Files.deleteIfExists(dstServerLibjvm.toPath());
                    }

                    if (TestHelper.isDualMode) {
                        if (!dstOtherServerDir.exists()) {
                            Files.createDirectories(dstOtherServerDir.toPath());
                        } else {
                            Files.deleteIfExists(dstOtherServerLibjvm.toPath());
                        }
                    }

                    desc = "LD_LIBRARY_PATH should not be set";
                    break;
                case LLP_SET_NON_EXISTENT_PATH:
                    if (dstLibDir.exists()) {
                        TestHelper.recursiveDelete(dstLibDir);
                    }
                    desc = "LD_LIBRARY_PATH should not be set";
                    break;
                default:
                    throw new RuntimeException("unknown case");
            }

            /*
             * Case 1: set the server path
             */
            env.clear();
            env.put(LD_LIBRARY_PATH, dstServerDir.getAbsolutePath());
            run(env, v.value + 1, "Case 1: " + desc);

            /*
             * Case 2: repeat with client path
             */
            env.clear();
            env.put(LD_LIBRARY_PATH, dstClientDir.getAbsolutePath());
            run(env, v.value + 1, "Case 2: " + desc);

            if (!TestHelper.isDualMode) {
                continue; // nothing more to do for Linux
            }

            // Tests applicable only to solaris.

            // initialize test variables for dual mode operations
            final File dst32ServerDir = TestHelper.is32Bit
                    ? dstServerDir
                    : dstOtherServerDir;

            final File dst64ServerDir = TestHelper.is64Bit
                    ? dstServerDir
                    : dstOtherServerDir;

            /*
             * Case 3: set the appropriate LLP_XX flag,
             * java32 -d32, LLP_32 is relevant, LLP_64 is ignored
             * java64 -d64, LLP_64 is relevant, LLP_32 is ignored
             */
            env.clear();
            env.put(LD_LIBRARY_PATH_32, dst32ServerDir.getAbsolutePath());
            env.put(LD_LIBRARY_PATH_64, dst64ServerDir.getAbsolutePath());
            run(TestHelper.is32Bit, null, env, v.value + 1, "Case 3: " + desc);

            /*
             * Case 4: we are in dual mode environment, running 64-bit then
             * we have the following scenarios:
             * java32 -d64, LLP_64 is relevant, LLP_32 is ignored
             * java64 -d32, LLP_32 is relevant, LLP_64 is ignored
             */
            if (TestHelper.dualModePresent()) {
                run(true, "-d64", env, v.value + 1, "Case 4A: " + desc);
                run(false,"-d32", env, v.value + 1, "Case 4B: " + desc);
            }
        }
        return;
    }

    public static void main(String... args) throws Exception {
        if (TestHelper.isWindows) {
            System.out.println("Warning: noop on windows");
            return;
        }
        // create our test jar first
        ExecutionEnvironment.createTestJar();

        // run the tests
        test7029048();
        if (errors > 0) {
            throw new Exception("Test7029048: FAIL: with "
                    + errors + " errors and passes " + passes);
        } else if (TestHelper.dualModePresent() && passes < 15) {
            throw new Exception("Test7029048: FAIL: " +
                    "all tests did not run, expected " + 15 + " got " + passes);
        } else if (TestHelper.isSolaris && passes < 9) {
            throw new Exception("Test7029048: FAIL: " +
                    "all tests did not run, expected " + 9 + " got " + passes);
        } else if (TestHelper.isLinux && passes < 6) {
             throw new Exception("Test7029048: FAIL: " +
                    "all tests did not run, expected " + 6 + " got " + passes);
        } else {
            System.out.println("Test7029048: PASS " + passes);
        }
    }
}
