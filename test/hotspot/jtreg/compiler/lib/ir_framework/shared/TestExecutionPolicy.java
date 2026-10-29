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

import compiler.lib.ir_framework.Skip;
import compiler.lib.ir_framework.test.DeclaredTest;
import compiler.lib.ir_framework.test.TestVM;

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

    public TestExecutionPolicy(Class<?> testClass) {
        this.testList = createTestFilterList(TEST_LIST, testClass);
        this.excludeList = createTestFilterList(EXCLUDE_LIST, testClass);
        checkPolicy();
    }

    /**
     * Parse "test1,test2,test3" into a list.
     */
    private static List<String> createTestFilterList(String list, Class<?> testClass) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        String classPrefix = testClass.getSimpleName() + ".";
        List<String> filterList = new ArrayList<>(Arrays.asList(list.split(",")));
        for (int i = filterList.size() - 1; i >= 0; i--) {
            String test = filterList.get(i);
            if (test.indexOf(".") > 0) {
                if (test.startsWith(classPrefix)) {
                    test = test.substring(classPrefix.length());
                    filterList.set(i, test);
                } else {
                    filterList.remove(i);
                }
            }
        }
        return filterList;
    }

    private void checkPolicy() {
        List<String> overlaps = new ArrayList<>(testList);
        overlaps.retainAll(excludeList);

        if (!Collections.disjoint(excludeList, testList)) {
            throw new OverlappingTestAndExcludeException(overlaps);
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
     * A test is excluded from execution if:
     * - -DTest does not list the method
     * - -DExclude lists the method
     * - The method specifies a (temporary) @Skip annotation.
     */
    public boolean shouldExcludeTestMethod(Method testMethod) {
        String testName = testMethod.getName();
        return isNotOnTestList(testName) ||
               isOnExcludeList(testName) ||
               shouldSkip(testMethod);
    }

    private boolean isNotOnTestList(String testName) {
        return isTestPolicyActive() && !testList.contains(testName);
    }

    private boolean isOnExcludeList(String testName) {
        return excludeList.contains(testName);
    }

    private boolean shouldSkip(Method method) {
        return !IGNORE_SKIP && hasSkipAnnotation(method);
    }

    private boolean hasSkipAnnotation(Method method) {
        return TestVM.getAnnotation(method, Skip.class) != null;
    }

    /**
     * A custom run test is excluded from execution if:
     * - -DExclude lists at least one associated test method
     * - -DTest does not list any associated test method
     * - At least one associated test method has a (temporary) @Skip annotation.
     */
    public boolean shouldExcludeCustomRunTest(List<DeclaredTest> tests) {
        boolean noTestMethodOnTestList = true;
        for (DeclaredTest test : tests) {
            Method testMethod = test.getTestMethod();
            String testName = testMethod.getName();
            if (shouldSkip(testMethod) || isOnExcludeList(testName)) {
                // Skip the custom run test if at least one associated test method should be skipped or excluded.
                return true;
            }
            if (isOnTestList(testName)) {
                // Enable the custom run test if at least one associated test method is on the test list and there is
                // no other reason to skip the custom run test.
                noTestMethodOnTestList = false;
            }
        }

        // -DTest set? Skip this @Run method because no associated test is on the list.
        return isTestPolicyActive() && noTestMethodOnTestList;
    }

    private boolean isOnTestList(String testName) {
        return testList.contains(testName);
    }
}
