/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331535 8351435 8347050 8361613
 * @summary Verify the jdk.internal.le's console provider works properly.
 * @modules java.base/jdk.internal.io
 *          jdk.internal.le/jdk.internal.org.jline
 * @library /test/lib
 * @run main JLineConsoleProviderTest
 */

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import jdk.internal.org.jline.JdkConsoleProviderImpl;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class JLineConsoleProviderTest {

    private static final String NL = System.getProperty("line.separator");

    public static void main(String... args) throws Throwable {
        for (Method m : JLineConsoleProviderTest.class.getDeclaredMethods()) {
            if (m.getName().startsWith("test")) {
                m.invoke(new JLineConsoleProviderTest());
            }
        }
    }

    void testCorrectOutputReadLine() throws Exception {
        doRunConsoleTest("testCorrectOutputReadLine", "inp", "%s");
    }

    void testCorrectOutputReadPassword() throws Exception {
        doRunConsoleTest("testCorrectOutputReadPassword", "inp", "%s");
    }

    void testEvenExpansionDisabled() throws Exception {
        doRunConsoleTest("readAndPrint", "a\\b\n", "'a\\b'" + NL);
        doRunConsoleTest("readAndPrint2", "a\n!!\n", "1: 'a'" + NL +
                                                     "2: '!!'" + NL);
    }

    void doRunConsoleTest(String testName,
                          String input,
                          String expectedOut) throws Exception {
        ProcessBuilder builder =
                ProcessTools.createTestJavaProcessBuilder(
                    "--add-exports",
                    "java.base/jdk.internal.io=ALL-UNNAMED",
                    "--add-exports",
                    "jdk.internal.le/jdk.internal.org.jline=ALL-UNNAMED",
                    ConsoleTest.class.getName(),
                    testName);
        OutputAnalyzer output = ProcessTools.executeProcess(builder, input);

        output.waitFor();

        if (output.getExitValue() != 0) {
            throw new AssertionError("Unexpected return value: " + output.getExitValue() +
                                     ", actualOut: " + output.getStdout() +
                                     ", actualErr: " + output.getStderr());
        }

        String actualOut = output.getStdout();

        if (!Objects.equals(expectedOut, actualOut)) {
            throw new AssertionError("Unexpected stdout content. " +
                                     "Expected: '" + expectedOut + "'" +
                                     ", got: '" + actualOut + "'");
        }

        String expectedErr = "";
        String actualErr = output.getStderr();

        if (!Objects.equals(expectedErr, actualErr)) {
            throw new AssertionError("Unexpected stderr content. " +
                                     "Expected: '" + expectedErr + "'" +
                                     ", got: '" + actualErr + "'");
        }
    }

    public static class ConsoleTest {
        public static void main(String... args) {
            // directly instantiate JLine JdkConsole, simulating isTTY=true
            var impl = new JdkConsoleProviderImpl().console(true, StandardCharsets.UTF_8, StandardCharsets.UTF_8);
            switch (args[0]) {
                case "testCorrectOutputReadLine" ->
                    impl.readLine(null, "%%s");
                case "testCorrectOutputReadPassword" ->
                    impl.readPassword(null, "%%s");
                case "readAndPrint" ->
                    System.out.println("'" + impl.readLine() + "'");
                case "readAndPrint2" -> {
                    System.out.println("1: '" + impl.readLine() + "'");
                    System.out.println("2: '" + impl.readLine() + "'");
                }
                default -> throw new UnsupportedOperationException(args[0]);
            }

            System.exit(0);
        }
    }
}
