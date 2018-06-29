/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

import org.junit.internal.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;

class VerboseTextListener extends TextRunListener {

    private static final int DEFAULT_MAX_TEST_PER_CLASS = 50;
    public static final int SHOW_ALL_TESTS = Integer.MAX_VALUE;
    private final int classesCount;
    private final int maxTestsPerClass;
    private int currentClassNum;
    private int currentTestNum;

    VerboseTextListener(JUnitSystem system, int classesCount) {
        this(system.out(), classesCount, DEFAULT_MAX_TEST_PER_CLASS);
    }

    VerboseTextListener(JUnitSystem system, int classesCount, int maxTests) {
        this(system.out(), classesCount, maxTests);
    }

    VerboseTextListener(PrintStream writer, int classesCount, int maxTests) {
        super(writer);
        maxTestsPerClass = maxTests;
        this.classesCount = classesCount;
    }

    @Override
    public boolean beVerbose() {
        return currentTestNum < maxTestsPerClass;
    }

    @Override
    public void testClassStarted(Class<?> clazz) {
        ++currentClassNum;
        getWriter().printf("%s started (%d of %d)", clazz.getName(), currentClassNum, classesCount);
        currentTestNum = 0;
    }

    @Override
    public void testClassFinished(Class<?> clazz, int numPassed, int numFailed) {
        getWriter().print(clazz.getName() + " finished");
    }

    @Override
    public void testStarted(Description description) {
        if (beVerbose()) {
            getWriter().print("  " + description.getMethodName() + ": ");
            currentTestNum++;
        } else {
            super.testStarted(description);
        }
    }

    @Override
    public void testIgnored(Description description) {
        if (beVerbose()) {
            getWriter().print("Ignored");
        } else {
            super.testIgnored(description);
        }
    }

    @Override
    public void testSucceeded(Description description) {
        if (beVerbose()) {
            getWriter().print("Passed");
        } else {
            super.testSucceeded(description);
        }
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        if (beVerbose()) {
            getWriter().printf("(%s) ", failure.getMessage());
        } else {
            super.testAssumptionFailure(failure);
        }
    }

    @Override
    public void testFailed(Failure failure) {
        getWriter().print("FAILED");
        lastFailure = failure;
    }

    @Override
    public void testClassFinishedDelimiter() {
        getWriter().println();
    }

    @Override
    public void testClassStartedDelimiter() {
        getWriter().println();
    }

    @Override
    public void testFinishedDelimiter() {
        if (beVerbose()) {
            getWriter().println();
        } else {
            super.testFinishedDelimiter();
        }
    }
}
