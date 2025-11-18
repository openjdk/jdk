/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Runs each GTests in separate gtestlauncher invocations
 * @comment Used to identify GTests which have dependencies on other GTests.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @requires vm.flagless
 * @run main/native/timeout=1800 CheckGtestDependencies
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class CheckGtestDependencies extends GTestWrapper {
    static record TestFilter(String name, boolean enabled) {}

    static TestFilter TF(String name, boolean enabled) {
        return new TestFilter(name, enabled);
    }

    static TestFilter TF(String name) {
        return TF(name, true);
    }

    // List of tests which have dependencies, these should have bugs associated
    // with them, and should be removed once they are resolved.
    static final TestFilter[] TEST_FILTERS = {
        TF("istream.coverage_vm"),
        TF("ZForwardingTest.find_every_other_vm"),
    };

    public static Stream<String> getFilteredTests() {
        return Stream.concat(// Not VM gtests which have VM dependencies
                             AllNotVMGtest.getFilteredTests(),
                             // VM gtests which have dependencies on other gtests
                             Arrays.stream(TEST_FILTERS)
                                   .filter(testFilter -> testFilter.enabled)
                                   .map(testFilter -> testFilter.name));
    }

    private static List<String> getAllGTests() throws Throwable {
        // Run the gtestlauncher with "--gtest_list_tests" to list all tests
        var oa = runGTestLauncher("--gtest_list_tests");
        if (oa.getExitValue() != 0) {
            throw new AssertionError("gtest list tests failed; exit code = " + oa.getExitValue());
        }

        // The expected format is:
        //
        // testSuite1.
        //   testX
        //   testY
        // testSuite2.
        //   testX
        //
        // Which we transform into:
        // List.of("testSuite1.testX", "testSuite1.testY", "testSuite2.testX")
        var lines = oa.stdoutAsLines();
        var tests = new ArrayList<String>(lines.size());
        for (int i = 0; i < lines.size();) {
            // Get the test suite.
            var testSuite = lines.get(i++).trim();
            if (!testSuite.endsWith(".")) {
                throw new AssertionError("gtest list tests failed; invalid test suite: " + testSuite);
            }

            // Get each test in the test suite.
            while (i < lines.size()) {
                var test = lines.get(i).trim();
                if (test.endsWith(".")) {
                    // New test suite
                    break;
                }
                tests.add(testSuite + test);
                i++;
            }
        }
        return tests;
    }

    public static void main(String[] args) throws Throwable {
        // Run each of our gtests in its own gtestlauncher invocation,
        // ignoring test with known issues
        var filteredTests = getFilteredTests().toList();
        for (var test : getAllGTests()) {
            if (filteredTests.contains(test)) {
                // Skip filtered tests
                continue;
            }
            // Run specific test
            GTestWrapper.main(Stream.concat(Arrays.stream(args),
                                            Stream.of("--gtest_filter=" + test))
                                    .toArray(String[]::new));
        }
    }
}
