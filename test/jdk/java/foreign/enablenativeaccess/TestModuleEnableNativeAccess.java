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
 * @build TestModuleEnableNativeAccess
 *        panama_module/*
 *        org.openjdk.foreigntest.PanamaMainUnnamedModule
 *        othermodule/*
 * @run testng/othervm/timeout=180 TestModuleEnableNativeAccess
 * @summary Basic tests for Module.enableNativeAccess()
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
public class TestModuleEnableNativeAccess {

    static final String MODULE_PATH = System.getProperty("jdk.module.path");

    static final String LAUNCHER = "othermodule/org.openjdk.foreigntest.othermodule.Launcher";

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

    static Result fail() {
        return new Result(false);
    }

    @DataProvider(name = "succeedCases")
    public Object[][] succeedCases() {
        return new Object[][] {
                { "enable_direct", false, new String[]{"--enable-native-access=panama_module"} },
                { "enable_indirect", true, new String[]{"--enable-native-access=othermodule"} },
                { "enable_direct_plus", true, new String[]{"--enable-native-access=othermodule,panama_module"} }
        };
    }

    @DataProvider(name = "failCases")
    public Object[][] failCases() {
        return new Object[][] {
                { "not_enabled", false, new String[]{"--enable-native-access=java.base"} },
                { "launcher_not_enabled", true, new String[]{"--enable-native-access=panama_module"} },
                { "not_delegated", false, new String[]{"--enable-native-access=othermodule"} }
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
    OutputAnalyzer run(String action, Result expectedResult, boolean doEnableNativeAccess, String[] vmopts)
            throws Exception
    {
        String[][] optsArray = {
            vmopts,
            {
                "-Djava.library.path=" + System.getProperty("java.library.path"),
                "--enable-preview",
                "-p", MODULE_PATH,
                "-m", LAUNCHER,
                String.valueOf(doEnableNativeAccess)
            }
        };
        String[] opts = Stream.of(optsArray).flatMap(Stream::of).toArray(String[]::new);
        OutputAnalyzer outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.out)
                .errorTo(System.out);
        checkResult(expectedResult, outputAnalyzer);
        return outputAnalyzer;
    }

    @Test(dataProvider = "succeedCases")
    public void testSucceed(String action, boolean doEnableNativeAccess, String[] vmopts) throws Exception {
        run(action, successNoWarning(), doEnableNativeAccess, vmopts);
    }

    @Test(dataProvider = "failCases")
    public void testFail(String action, boolean doEnableNativeAccess, String[] vmopts) throws Exception {
        run(action, fail(), doEnableNativeAccess, vmopts);
    }

    private int count(Iterable<String> lines, CharSequence cs) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(cs)) count++;
        }
        return count;
    }
}
