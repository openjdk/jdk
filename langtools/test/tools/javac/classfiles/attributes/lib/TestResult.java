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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

public class TestResult extends TestBase {

    private final List<Info> testCases;

    public TestResult() {
        testCases = new ArrayList<>();
        testCases.add(new Info("Global test info"));
    }

    public void addTestCase(String src) {
        testCases.add(new Info(src));
    }

    public String errorMessage() {
        return testCases.stream().filter(Info::isFailed)
                .map(tc -> format("Failure in test case:\n%s\n%s", tc.info(),
                        (tc.asserts.size() > 0 ? tc.getAssertMessage() + "\n" : "")
                                + tc.getErrorMessage()))
                .collect(joining("\n"));
    }

    @Override
    public void assertEquals(Object actual, Object expected, String message) {
        getLastTestCase().assertEquals(actual, expected, message);
    }

    @Override
    public void assertNull(Object actual, String message) {
        getLastTestCase().assertEquals(actual, null, message);
    }

    @Override
    public void assertNotNull(Object actual, String message) {
        getLastTestCase().assertNotNull(actual, message);
    }

    @Override
    public void assertFalse(boolean actual, String message) {
        getLastTestCase().assertEquals(actual, false, message);
    }

    @Override
    public void assertTrue(boolean actual, String message) {
        getLastTestCase().assertEquals(actual, true, message);
    }

    public void addFailure(Throwable th) {
        getLastTestCase().addFailure(th);
    }

    private Info getLastTestCase() {
        if (testCases.size() == 1) {
            throw new IllegalStateException("Test case should be created");
        }
        return testCases.get(testCases.size() - 1);
    }

    public void checkStatus() throws TestFailedException {
        if (testCases.stream().anyMatch(Info::isFailed)) {
            echo(errorMessage());
            throw new TestFailedException("Test failed");
        }
    }

    private class Info {

        private final String info;
        private final List<String> asserts;
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

        public void addFailure(String message) {
            asserts.add(message);
            printf("[ASSERT] : %s\n", message);
        }

        public void assertEquals(Object actual, Object expected, String message) {
            echo("Testing : " + message);
            if (!Objects.equals(actual, expected)) {
                addFailure(message + ": Got: " + actual + ", " + "Expected: " + expected);
            }
        }

        public void assertNotNull(Object actual, String message) {
            echo("Testing : " + message);
            if (actual == null) {
                addFailure(message + " : Expected not null value");
            }
        }

        public String getAssertMessage() {
            return asserts.stream()
                    .map(failure -> "[ASSERT] : " + failure)
                    .collect(joining("\n"));
        }

        public String getErrorMessage() {
            return errors.stream()
                    .map(throwable ->
                            format("[ERROR] : %s", getStackTrace(throwable)))
                    .collect(joining("\n"));
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
