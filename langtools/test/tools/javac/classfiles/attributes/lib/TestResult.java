/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class accumulates test results. Test results can be checked with method @{code checkStatus}.
 */
public class TestResult extends TestBase {

    private final List<Info> testCases;

    public TestResult() {
        testCases = new ArrayList<>();
        testCases.add(new Info("Global test info"));
    }

    /**
     * Adds new test case info.
     *
     * @param info the information about test case
     */
    public void addTestCase(String info) {
        testCases.add(new Info(info));
    }

    private String errorMessage() {
        return testCases.stream().filter(Info::isFailed)
                .map(tc -> String.format("Failure in test case:\n%s\n%s", tc.info(), tc.getMessage()))
                .collect(Collectors.joining("\n"));
    }

    public boolean checkEquals(Object actual, Object expected, String message) {
        echo("Testing : " + message);
        if (!Objects.equals(actual, expected)) {
            getLastTestCase().addAssert(new AssertionFailedException(
                    String.format("%s%nGot: %s, Expected: %s", message, actual, expected)));
            return false;
        }
        return true;
    }

    public boolean checkNull(Object actual, String message) {
        return checkEquals(actual, null, message);
    }

    public boolean checkNotNull(Object actual, String message) {
        echo("Testing : " + message);
        if (Objects.isNull(actual)) {
            getLastTestCase().addAssert(new AssertionFailedException(
                    message + " : Expected not null value"));
            return false;
        }
        return true;
    }

    public boolean checkFalse(boolean actual, String message) {
        return checkEquals(actual, false, message);
    }

    public boolean checkTrue(boolean actual, String message) {
        return checkEquals(actual, true, message);
    }

    public boolean checkContains(Set<?> found, Set<?> expected, String message) {
        Set<?> copy = new HashSet<>(expected);
        copy.removeAll(found);
        return checkTrue(found.containsAll(expected), message + " : " + copy);
    }

    public void addFailure(Throwable th) {
        testCases.get(testCases.size() - 1).addFailure(th);
    }

    private Info getLastTestCase() {
        if (testCases.size() == 1) {
            throw new IllegalStateException("Test case should be created");
        }
        return testCases.get(testCases.size() - 1);
    }

    /**
     * Throws {@code TestFailedException} if one of the checks are failed
     * or an exception occurs. Prints error message of failed test cases.
     *
     * @throws TestFailedException if one of the checks are failed
     *                             or an exception occurs
     */
    public void checkStatus() throws TestFailedException {
        if (testCases.stream().anyMatch(Info::isFailed)) {
            echo(errorMessage());
            throw new TestFailedException("Test failed");
        }
    }

    private class Info {

        private final String info;
        private final List<AssertionFailedException> asserts;
        private final List<Throwable> errors;

        private Info(String info) {
            this.info = info;
            asserts = new ArrayList<>();
            errors = new ArrayList<>();
        }

        public String info() {
            return info;
        }

        public boolean isFailed() {
            return !asserts.isEmpty() || !errors.isEmpty();
        }

        public void addFailure(Throwable th) {
            errors.add(th);
            printf("[ERROR] : %s\n", getStackTrace(th));
        }

        public void addAssert(AssertionFailedException e) {
            asserts.add(e);
            printf("[ASSERT] : %s\n", getStackTrace(e));
        }

        public String getMessage() {
            return (asserts.size() > 0 ? getErrorMessage("[ASSERT]", asserts) + "\n" : "")
                    + getErrorMessage("[ERROR]", errors);
        }

        public String getErrorMessage(String header, List<? extends Throwable> list) {
            return list.stream()
                    .map(throwable -> String.format("%s : %s", header, getStackTrace(throwable)))
                    .collect(Collectors.joining("\n"));
        }

        public String getStackTrace(Throwable throwable) {
            StringWriter stringWriter = new StringWriter();
            try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
                throwable.printStackTrace(printWriter);
            }
            return stringWriter.toString();
        }
    }

    public static class TestFailedException extends Exception {
        public TestFailedException(String message) {
            super(message);
        }
    }
}
