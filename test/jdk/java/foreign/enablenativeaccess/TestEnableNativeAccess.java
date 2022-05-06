/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @library /test/lib
 * @build TestEnableNativeAccess
 *        panama_module/*
 *        org.openjdk.foreigntest.PanamaMainUnnamedModule
 * @run testng/othervm/timeout=180 TestEnableNativeAccess
 * @summary Basic test for java --enable-native-access
 */

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Basic test of --enable-native-access with expected behaviour:
 *
 *  if flag present:        - permit access to modules that are specified
 *                          - deny access to modules that are not specified
 *                            (throw IllegalCallerException)
 *  if flag not present:    - permit access to all modules and omit a warning
 *                            (on first access per module only)
*/

@Test
public class TestEnableNativeAccess {

    static final String MODULE_PATH = System.getProperty("jdk.module.path");

    static final String PANAMA_MAIN = "panama_module/org.openjdk.foreigntest.PanamaMainDirect";
    static final String PANAMA_REFLECTION = "panama_module/org.openjdk.foreigntest.PanamaMainReflection";
    static final String PANAMA_INVOKE = "panama_module/org.openjdk.foreigntest.PanamaMainInvoke";
    static final String PANAMA_JNI = "panama_module/org.openjdk.foreigntest.PanamaMainJNI";
    static final String UNNAMED = "org.openjdk.foreigntest.PanamaMainUnnamedModule";

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

    static Result successWithWarning(String moduleName) {
        return success().expect("WARNING").expect("--enable-native-access=" + moduleName);
    }

    static Result failWithWarning(String expectedOutput) {
        return new Result(false).expect(expectedOutput).expect("WARNING");
    }

    @DataProvider(name = "succeedCases")
    public Object[][] succeedCases() {
        return new Object[][] {
                { "panama_enable_native_access", PANAMA_MAIN, successNoWarning(), new String[]{"--enable-native-access=panama_module"} },
                { "panama_enable_native_access_reflection", PANAMA_REFLECTION, successNoWarning(), new String[]{"--enable-native-access=panama_module"} },
                { "panama_enable_native_access_invoke", PANAMA_INVOKE, successNoWarning(), new String[]{"--enable-native-access=panama_module"} },
                { "panama_enable_native_access_jni", PANAMA_JNI, successNoWarning(), new String[]{"--enable-native-access=ALL-UNNAMED"} },

                { "panama_comma_separated_enable", PANAMA_MAIN, successNoWarning(), new String[]{"--enable-native-access=java.base,panama_module"} },
                { "panama_comma_separated_enable_reflection", PANAMA_REFLECTION, successNoWarning(), new String[]{"--enable-native-access=java.base,panama_module"} },
                { "panama_comma_separated_enable_invoke", PANAMA_INVOKE, successNoWarning(), new String[]{"--enable-native-access=java.base,panama_module"} },
                { "panama_comma_separated_enable_jni", PANAMA_JNI, successNoWarning(), new String[]{"--enable-native-access=java.base,ALL-UNNAMED"} },

                { "panama_enable_native_access_warn", PANAMA_MAIN, successWithWarning("panama"), new String[]{} },
                { "panama_enable_native_access_warn_reflection", PANAMA_REFLECTION, successWithWarning("panama"), new String[]{} },
                { "panama_enable_native_access_warn_invoke", PANAMA_INVOKE, successWithWarning("panama"), new String[]{} },
                { "panama_enable_native_access_warn_jni", PANAMA_JNI, successWithWarning("ALL-UNNAMED"), new String[]{} },

                { "panama_no_unnamed_module_native_access", UNNAMED, successWithWarning("ALL-UNNAMED"), new String[]{} },
                { "panama_all_unnamed_module_native_access", UNNAMED, successNoWarning(), new String[]{"--enable-native-access=ALL-UNNAMED"} },
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
    OutputAnalyzer run(String action, String cls, Result expectedResult, String... vmopts)
            throws Exception
    {
        Stream<String> s1 = Stream.concat(
                Stream.of(vmopts),
                Stream.of("-Djava.library.path=" + System.getProperty("java.library.path")));
        Stream<String> s2 = cls.equals(UNNAMED) ? Stream.of("--enable-preview", "-p", MODULE_PATH, cls, action)
                : Stream.of("--enable-preview", "-p", MODULE_PATH, "-m", cls, action);
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        OutputAnalyzer outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.out)
                .errorTo(System.out);
        checkResult(expectedResult, outputAnalyzer);
        return outputAnalyzer;
    }

    @Test(dataProvider = "succeedCases")
    public void testSucceed(String action, String cls, Result expectedResult, String... vmopts) throws Exception {
        run(action, cls, expectedResult, vmopts);
    }

    /**
     * Tests that without --enable-native-access, a multi-line warning is printed
     * on first access of a module.
     */
    public void testWarnFirstAccess() throws Exception {
        List<String> output1 = run("panama_enable_native_access_first", PANAMA_MAIN,
                successWithWarning("panama")).asLines();
        assertTrue(count(output1, "WARNING") == 3);  // 3 on first access, none on subsequent access
    }

    /**
     * Specifies --enable-native-access more than once, each list of module names
     * is appended.
     */
    public void testRepeatedOption() throws Exception {
        run("panama_enable_native_access_last_one_wins", PANAMA_MAIN,
                success(), "--enable-native-access=java.base", "--enable-native-access=panama_module");
        run("panama_enable_native_access_last_one_wins", PANAMA_MAIN,
                success(), "--enable-native-access=panama_module", "--enable-native-access=java.base");
    }

    /**
     * Specifies bad value to --enable-native-access.
     */
    public void testBadValue() throws Exception {
        run("panama_enable_native_access_warn_unknown_module", PANAMA_MAIN,
                failWithWarning("WARNING: Unknown module: BAD specified to --enable-native-access"),
                "--enable-native-access=BAD");
        run("panama_no_all_module_path_blanket_native_access", PANAMA_MAIN,
                failWithWarning("WARNING: Unknown module: ALL-MODULE-PATH specified to --enable-native-access"),
                "--enable-native-access=ALL-MODULE-PATH" );
    }

    private int count(Iterable<String> lines, CharSequence cs) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(cs)) count++;
        }
        return count;
    }
}
