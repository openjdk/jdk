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
 * @requires !vm.musl
 *
 * @library /test/lib
 * @build TestEnableNativeAccessDynamic
 *        panama_module/*
          NativeAccessDynamicMain
 * @run testng/othervm/timeout=180 TestEnableNativeAccessDynamic
 * @summary Test for dynamically setting --enable-native-access flag for a module
 */

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class TestEnableNativeAccessDynamic {

    static final String MODULE_PATH = System.getProperty("jdk.module.path");

    static final String PANAMA_MAIN = "panama_module/org.openjdk.foreigntest.PanamaMainDirect";
    static final String PANAMA_REFLECTION = "panama_module/org.openjdk.foreigntest.PanamaMainReflection";
    static final String PANAMA_INVOKE = "panama_module/org.openjdk.foreigntest.PanamaMainInvoke";
    static final String PANAMA_JNI = "panama_module/org.openjdk.foreigntest.PanamaMainJNI";

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

    static Result failWithError(String expectedOutput) {
        return new Result(false).expect(expectedOutput);
    }

    @DataProvider(name = "succeedCases")
    public Object[][] succeedCases() {
        return new Object[][] {
                { "panama_enable_native_access", PANAMA_MAIN, successNoWarning() },
                { "panama_enable_native_access_reflection", PANAMA_REFLECTION, successNoWarning() },
                { "panama_enable_native_access_invoke", PANAMA_INVOKE, successNoWarning() },
        };
    }

    @DataProvider(name = "failureCases")
    public Object[][] failureCases() {
        String errMsg = "Illegal native access from: module panama_module";
        return new Object[][] {
                { "panama_enable_native_access_fail", PANAMA_MAIN, failWithError(errMsg) },
                { "panama_enable_native_access_fail_reflection", PANAMA_REFLECTION, failWithError(errMsg) },
                { "panama_enable_native_access_fail_invoke", PANAMA_INVOKE, failWithError(errMsg) },
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
    OutputAnalyzer run(String action, String moduleAndCls, boolean enableNativeAccess,
            Result expectedResult, boolean panamaModuleInBootLayer) throws Exception
    {
        List<String> list = new ArrayList<>();
        list.add("--enable-preview");
        if (panamaModuleInBootLayer) {
            list.addAll(List.of("-p", MODULE_PATH));
            list.add("--add-modules=panama_module");
            list.add("--enable-native-access=panama_module");
        } else {
            list.add("--enable-native-access=ALL-UNNAMED");
        }
        list.addAll(List.of("NativeAccessDynamicMain", MODULE_PATH,
                moduleAndCls, Boolean.toString(enableNativeAccess), action));
        String[] opts = list.toArray(String[]::new);
        OutputAnalyzer outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.out)
                .errorTo(System.out);
        checkResult(expectedResult, outputAnalyzer);
        return outputAnalyzer;
    }

    @Test(dataProvider = "succeedCases")
    public void testSucceed(String action, String moduleAndCls,
            Result expectedResult) throws Exception {
        run(action, moduleAndCls, true, expectedResult, false);
    }

    @Test(dataProvider = "failureCases")
    public void testFailures(String action, String moduleAndCls,
            Result expectedResult) throws Exception {
        run(action, moduleAndCls, false, expectedResult, false);
    }

    // make sure that having a same named module in boot layer with native access
    // does not influence same named dynamic module.
    @Test(dataProvider = "failureCases")
    public void testFailuresWithPanamaModuleInBootLayer(String action, String moduleAndCls,
            Result expectedResult) throws Exception {
        run(action, moduleAndCls, false, expectedResult, true);
    }
}
