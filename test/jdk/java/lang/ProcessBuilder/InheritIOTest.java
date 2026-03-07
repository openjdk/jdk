/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for java.lang.ProcessBuilder inheritance of standard output and standard error streams
 * @bug 8023130 8166026
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.lib.process.*
 * @run junit InheritIOTest
 */

import java.util.List;
import static java.lang.ProcessBuilder.Redirect.INHERIT;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class InheritIOTest {

    private static final String EXIT_VALUE_TEMPLATE = "exit value: %d";
    private static final String EXPECTED_RESULT_STDOUT = "message";
    private static final String EXPECTED_RESULT_STDERR = EXIT_VALUE_TEMPLATE.formatted(0);

    public static Object[][] testCases() {
        return new Object[][]{
             new Object[] { List.of("InheritIOTest$TestInheritIO", "printf", EXPECTED_RESULT_STDOUT) },
             new Object[] { List.of("InheritIOTest$TestRedirectInherit", "printf", EXPECTED_RESULT_STDOUT) }
        };
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testInheritWithoutRedirect(List<String> arguments) throws Throwable {
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(arguments);
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(processBuilder);
        outputAnalyzer.shouldHaveExitValue(0);
        assertEquals(EXPECTED_RESULT_STDOUT, outputAnalyzer.getStdout());
        assertEquals(EXPECTED_RESULT_STDERR, outputAnalyzer.getStderr());
    }

    public static class TestInheritIO {
        public static void main(String args[]) throws Throwable {
            int err = new ProcessBuilder(args).inheritIO().start().waitFor();
            System.err.printf(EXIT_VALUE_TEMPLATE, err);
            System.exit(err);
        }
    }

    public static class TestRedirectInherit {
        public static void main(String args[]) throws Throwable {
            int err = new ProcessBuilder(args)
                    .redirectInput(INHERIT)
                    .redirectOutput(INHERIT)
                    .redirectError(INHERIT)
                    .start().waitFor();
            System.err.printf(EXIT_VALUE_TEMPLATE, err);
            System.exit(err);
        }
    }
}
