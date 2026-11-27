/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.shared;

import compiler.lib.ir_framework.test.DeclaredTest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Class to answer queries about whether a test or method should be excluded or not which includes:
 * - @Skip
 * - DTest
 * - DExclude
 */
public class TestExecutionPolicy {
    private static final String TEST_LIST = SystemProperty.getTestList();
    private static final String EXCLUDE_LIST = SystemProperty.getExcludeList();
    private static final boolean IGNORE_SKIP = Boolean.getBoolean("IgnoreSkip");

    private final List<String> excludeList;
    private final List<String> testList;

    public TestExecutionPolicy() {
        this.testList = createTestFilterList(TEST_LIST);
        this.excludeList = createTestFilterList(EXCLUDE_LIST);
        checkPolicy();
    }

    /**
     * Parse "test1,test2,test3" into a list of method names.
     */
    private static List<String> createTestFilterList(String methods) {
        if (methods.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(methods.split(",")));
    }

    private void checkPolicy() {
        if (!Collections.disjoint(excludeList, testList)) {
            List<String> overlaps = new ArrayList<>(testList);
            overlaps.retainAll(excludeList);
            throw OverlappingTestAndExcludeException.create(overlaps);
        }
    }

    public void checkPolicyForCustomRunTest(Method runMethod, String[] testNames) {
        List<String> testNamesList = Arrays.asList(testNames);
        List<String> overlapInclude = new ArrayList<>(testList);
        List<String> overlapExclude = new ArrayList<>(excludeList);
        overlapInclude.retainAll(testNamesList);
        overlapExclude.retainAll(testNamesList);
        if (!overlapInclude.isEmpty() && !overlapExclude.isEmpty()) {
            throw OverlappingTestAndExcludeException.createForRunWithMultipleTests(overlapInclude, overlapExclude,
                                                                                   runMethod);
        }
    }

    /**
     * Has the user set any valid -DTest or -DExclude properties?
     */
    public boolean isTestOrExcludePolicyActive() {
        return isTestPolicyActive() || !excludeList.isEmpty();
    }

    private boolean isTestPolicyActive() {
        return !testList.isEmpty();
    }

    /**
     * A base or checked test is excluded from execution if either:
     * - -DTest does not list the method
     * - -DExclude lists the method
     * - The method specifies a (temporary) @Skip annotation.
     */
    public boolean shouldExcludeBaseOrCheckedTest(DeclaredTest test) {
        Method testMethod = test.getTestMethod();
        String testName = testMethod.getName();
        return isNotOnExistingTestList(testName) ||
               isOnExcludeList(testName) ||
               shouldSkip(test);
    }

    private boolean isNotOnExistingTestList(String testName) {
        return isTestPolicyActive() && !testList.contains(testName);
    }

    private boolean isOnExcludeList(String testName) {
        return excludeList.contains(testName);
    }

    private boolean shouldSkip(DeclaredTest test) {
        return !IGNORE_SKIP && test.hasSkipAnno();
    }

    /**
     * A custom run test is excluded from execution if either:
     * - -DTest does not list any associated test method
     * - -DExclude lists at least one associated test method
     * - At least one associated test method has a (temporary) @Skip annotation.
     *
     * <p>
     * Note that specifying distinct methods in -DTest and -DExclude which are both associated with the same @Run-method
     * results in a {@link OverlappingTestAndExcludeException}.
     */
    public boolean shouldExcludeCustomRunTest(List<DeclaredTest> tests) {
        return hasSkipOrOnExcludeList(tests) || isNotOnExistingTestList(tests);
    }

    private boolean hasSkipOrOnExcludeList(List<DeclaredTest> tests) {
        return tests.stream()
                    .anyMatch(test -> shouldSkip(test) ||
                                      isOnExcludeList(test.getTestMethod().getName()));
    }

    private boolean isNotOnExistingTestList(List<DeclaredTest> tests) {
        return isTestPolicyActive() &&
               tests.stream()
                    .noneMatch(test -> isOnTestList(test.getTestMethod().getName()));
    }

    private boolean isOnTestList(String testName) {
        return testList.contains(testName);
    }
}
