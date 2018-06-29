/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.mxtool.junit;

import java.io.PrintStream;

import org.junit.internal.JUnitSystem;
import org.junit.internal.TextListener;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

class TextRunListener implements MxRunListener {

    private final PrintStream fWriter;
    protected Failure lastFailure;

    TextRunListener(JUnitSystem system) {
        this(system.out());
    }

    TextRunListener(PrintStream writer) {
        fWriter = writer;
    }

    public boolean beVerbose() {
        return false;
    }

    @Override
    public PrintStream getWriter() {
        return fWriter;
    }

    public Failure getLastFailure() {
        return lastFailure;
    }

    @Override
    public void testStarted(Description description) {
        getWriter().print('.');
    }

    @Override
    public void testFailed(Failure failure) {
        getWriter().print('E');
        lastFailure = failure;
    }

    @Override
    public void testIgnored(Description description) {
        getWriter().print('I');
    }

    public static RunListener createRunListener(MxRunListener l) {
        return new TextListener(l.getWriter()) {
            private Class<?> lastClass;
            private int passedInLastClass;
            private int failedInLastClass;
            private boolean failed;

            @Override
            public void testStarted(Description description) {
                Class<?> currentClass = description.getTestClass();
                if (currentClass != lastClass) {
                    if (lastClass != null) {
                        l.testClassFinished(lastClass, passedInLastClass, failedInLastClass);
                        l.testClassFinishedDelimiter();
                    }
                    lastClass = currentClass;
                    passedInLastClass = 0;
                    failedInLastClass = 0;
                    l.testClassStarted(currentClass);
                    l.testClassStartedDelimiter();
                }
                failed = false;
                l.testStarted(description);
                l.testStartedDelimiter();
            }

            @Override
            public void testFailure(Failure failure) {
                failed = true;
                failedInLastClass++;
                l.testFailed(failure);
            }

            @Override
            public void testFinished(Description description) {
                // we have to do this because there is no callback for successful tests
                if (!failed) {
                    l.testSucceeded(description);
                    passedInLastClass++;
                }
                l.testFinished(description);
                l.testFinishedDelimiter();
            }

            @Override
            public void testIgnored(Description description) {
                testStarted(description);
                l.testIgnored(description);
                l.testFinished(description);
                l.testFinishedDelimiter();
            }

            @Override
            public void testRunStarted(Description description) {
                l.testRunStarted(description);
            }

            @Override
            public void testRunFinished(Result result) {
                if (lastClass != null) {
                    l.testClassFinished(lastClass, passedInLastClass, failedInLastClass);
                }
                l.testRunFinished(result);
                super.testRunFinished(result);
            }

            @Override
            public void testAssumptionFailure(Failure failure) {
                l.testAssumptionFailure(failure);
            }
        };
    }
}
