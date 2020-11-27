/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8023130 8166026
 * @summary Unit test for java.lang.ProcessBuilder inheritance of standard output and standard error streams
 * @library /test/lib
 * @build jdk.test.lib.process.*
 *        InheritIo
 * @run testng InheritIoTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class InheritIoTest {

    private static final String EXPECTED_RESULT_STDOUT = "message";
    private static final String EXPECTED_RESULT_STDERR = InheritIo.EXIT_VALUE_TEMPLATE.formatted(0);

    @DataProvider
    public Object[][] testCases() {
        return new Object[][]{
             new Object[] { List.of("InheritIo$TestInheritIo", "printf", EXPECTED_RESULT_STDOUT) },
             new Object[] { List.of("InheritIo$TestRedirectInherit", "printf", EXPECTED_RESULT_STDOUT) }
        };
    }

    @Test(dataProvider = "testCases")
    public void testInheritWithoutRedirect(List<String> arguments) throws Throwable {
        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(arguments);
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(processBuilder);
        outputAnalyzer.shouldHaveExitValue(0);
        outputAnalyzer.stderrShouldMatch(EXPECTED_RESULT_STDERR);
        outputAnalyzer.stdoutShouldMatch(EXPECTED_RESULT_STDOUT);
        assertEquals(outputAnalyzer.getOutput(),EXPECTED_RESULT_STDOUT + EXPECTED_RESULT_STDERR);
    }

}
