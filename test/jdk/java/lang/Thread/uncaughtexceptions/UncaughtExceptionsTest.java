/*
 * Copyright (c) 2004, 2020 Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 4833089 4992454
 * @summary Check for proper handling of uncaught exceptions
 * @author Martin Buchholz
 * @library /test/lib
 * @build jdk.test.lib.process.*
 *        Seppuku
 * @run testng UncaughtExceptionsTest
 */
public class UncaughtExceptionsTest {

    @DataProvider
    public Object[][] testCases() {
        return new Object[][]{
            new Object[] { "ThreadIsDeadAfterJoin",
                           0,
                           Seppuku.EXPECTED_RESULT,
                           "Exception in thread \"Thread-0\".*Seppuku"
            },
            new Object[] {
                            "MainThreadAbruptTermination",
                            1,
                            Seppuku.EXPECTED_RESULT,
                            "Exception in thread \"main\".*Seppuku"
            },
            new Object[] { "MainThreadNormalTermination", 0, Seppuku.EXPECTED_RESULT, ""},
            new Object[] { "DefaultUncaughtExceptionHandlerOnMainThread", 1, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "DefaultUncaughtExceptionHandlerOnMainThreadOverride", 1, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "DefaultUncaughtExceptionHandlerOnNonMainThreadOverride", 0, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "DefaultUncaughtExceptionHandlerOnNonMainThread", 0, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "ThreadGroupUncaughtExceptionHandlerOnNonMainThread", 0, Seppuku.EXPECTED_RESULT, "" }
        };
    }

    @Test(dataProvider = "testCases")
    public void test(String className, int exitValue, String stdOutMatch, String stdErrMatch) throws Throwable {
        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(String.format("Seppuku$%s",className));
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(processBuilder);
        outputAnalyzer.shouldHaveExitValue(exitValue);
        outputAnalyzer.stderrShouldMatch(stdErrMatch);
        outputAnalyzer.stdoutShouldMatch(stdOutMatch);
    }

}