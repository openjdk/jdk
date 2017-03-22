/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @library /lib/testlibrary
 * @build PermitIllegalAccess AttemptAccess jdk.testlibrary.*
 * @run testng PermitIllegalAccess
 * @summary Basic test for java --permit-illegal-access
 */

import java.util.List;

import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.OutputAnalyzer;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Basic test of --permit-illegal-access to ensure that it permits access
 * via core reflection and setAccessible/trySetAccessible.
 */

@Test
public class PermitIllegalAccess {

    static final String TEST_CLASSES = System.getProperty("test.classes");
    static final String TEST_MAIN = "AttemptAccess";

    static final String WARNING = "WARNING";
    static final String STARTUP_WARNING =
        "WARNING: --permit-illegal-access will be removed in the next major release";
    static final String ILLEGAL_ACCESS_WARNING =
        "WARNING: Illegal access by " + TEST_MAIN;

    /**
     * Launches AttemptAccess to execute an action, returning the OutputAnalyzer
     * to analyze the output/exitCode.
     */
    private OutputAnalyzer tryAction(String action, int count) throws Exception {
        String arg = "" + count;
        return ProcessTools
                .executeTestJava("-cp", TEST_CLASSES, TEST_MAIN, action, arg)
                .outputTo(System.out)
                .errorTo(System.out);
    }

    /**
     * Launches AttemptAccess with --permit-illegal-access to execute an action,
     * returning the OutputAnalyzer to analyze the output/exitCode.
     */
    private OutputAnalyzer tryActionPermittingIllegalAccess(String action,
                                                            int count)
        throws Exception
    {
        String arg = "" + count;
        return ProcessTools
                .executeTestJava("-cp", TEST_CLASSES, "--permit-illegal-access",
                                 TEST_MAIN, action, arg)
                .outputTo(System.out)
                .errorTo(System.out);
    }

    /**
     * Sanity check to ensure that IllegalAccessException is thrown.
     */
    public void testAccessFail() throws Exception {
        int exitValue = tryAction("access", 1)
                .stdoutShouldNotContain(WARNING)
                .stdoutShouldNotContain("IllegalAccessException")
                .stderrShouldNotContain(WARNING)
                .stderrShouldContain("IllegalAccessException")
                .getExitValue();
        assertTrue(exitValue != 0);
    }

    /**
     * Sanity check to ensure that InaccessibleObjectException is thrown.
     */
    public void testSetAccessibleFail() throws Exception {
        int exitValue = tryAction("setAccessible", 1)
                .stdoutShouldNotContain(WARNING)
                .stdoutShouldNotContain("InaccessibleObjectException")
                .stderrShouldNotContain(WARNING)
                .stderrShouldContain("InaccessibleObjectException")
                .getExitValue();
        assertTrue(exitValue != 0);
    }

    /**
     * Permit illegal access to succeed
     */
    public void testAccessPermitted() throws Exception {
        tryActionPermittingIllegalAccess("access", 1)
                .stdoutShouldNotContain(WARNING)
                .stdoutShouldNotContain("IllegalAccessException")
                .stderrShouldContain(STARTUP_WARNING)
                .stderrShouldNotContain("IllegalAccessException")
                .stderrShouldContain(ILLEGAL_ACCESS_WARNING)
                .shouldHaveExitValue(0);
    }

    /**
     * Permit repeated illegal access to succeed
     */
    public void testRepeatedAccessPermitted() throws Exception {
        OutputAnalyzer outputAnalyzer = tryActionPermittingIllegalAccess("access", 10)
                .stdoutShouldNotContain(WARNING)
                .stdoutShouldNotContain("IllegalAccessException")
                .stderrShouldContain(STARTUP_WARNING)
                .stderrShouldNotContain("IllegalAccessException")
                .stderrShouldContain(ILLEGAL_ACCESS_WARNING)
                .shouldHaveExitValue(0);;

        // should only have one illegal access warning
        assertTrue(containsCount(outputAnalyzer.asLines(), ILLEGAL_ACCESS_WARNING) == 1);
    }

    /**
     * Permit setAccessible to succeed
     */
    public void testSetAccessiblePermitted() throws Exception {
        tryActionPermittingIllegalAccess("setAccessible", 1)
                .stdoutShouldNotContain(WARNING)
                .stdoutShouldNotContain("InaccessibleObjectException")
                .stderrShouldContain(STARTUP_WARNING)
                .stderrShouldNotContain("InaccessibleObjectException")
                .stderrShouldContain(ILLEGAL_ACCESS_WARNING)
                .shouldHaveExitValue(0);
    }

    /**
     * Permit repeated calls to setAccessible to succeed
     */
    public void testRepeatedSetAccessiblePermitted() throws Exception {
        OutputAnalyzer outputAnalyzer = tryActionPermittingIllegalAccess("setAccessible", 10)
                .stdoutShouldNotContain(WARNING)
                .stdoutShouldNotContain("InaccessibleObjectException")
                .stderrShouldContain(STARTUP_WARNING)
                .stderrShouldNotContain("InaccessibleObjectException")
                .stderrShouldContain(ILLEGAL_ACCESS_WARNING)
                .shouldHaveExitValue(0);

        // should only have one illegal access warning
        assertTrue(containsCount(outputAnalyzer.asLines(), ILLEGAL_ACCESS_WARNING) == 1);
    }

    /**
     * Permit trySetAccessible to succeed
     */
    public void testTrySetAccessiblePermitted() throws Exception {
        tryActionPermittingIllegalAccess("trySetAccessible", 1)
                .stdoutShouldNotContain(WARNING)
                .stderrShouldContain(STARTUP_WARNING)
                .stderrShouldContain(ILLEGAL_ACCESS_WARNING)
                .shouldHaveExitValue(0);
    }

    /**
     * Permit repeated calls to trySetAccessible to succeed
     */
    public void testRepeatedTrySetAccessiblePermitted() throws Exception {
        OutputAnalyzer outputAnalyzer = tryActionPermittingIllegalAccess("trySetAccessible", 10)
                .stdoutShouldNotContain(WARNING)
                .stdoutShouldNotContain("InaccessibleObjectException")
                .stderrShouldContain(STARTUP_WARNING)
                .stderrShouldNotContain("InaccessibleObjectException")
                .stderrShouldContain(ILLEGAL_ACCESS_WARNING)
                .shouldHaveExitValue(0);

        // should only have one illegal access warning
        assertTrue(containsCount(outputAnalyzer.asLines(), ILLEGAL_ACCESS_WARNING) == 1);

    }

    /**
     * Returns the number of lines in the given input that contain the
     * given char sequence.
     */
    private int containsCount(List<String> lines, CharSequence cs) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(cs)) count++;
        }
        return count;
    }
}
