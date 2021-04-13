/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8247403
 * @summary Verify JavaShellToolBuilder uses provided inputs
 * @modules jdk.jshell
 * @build KullaTesting TestingInputStream
 * @run testng CustomInputToolBuilder
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import jdk.jshell.tool.JavaShellToolBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Test
public class CustomInputToolBuilder extends KullaTesting {

    private static final String TEST_JDK = "test.jdk";

    public void checkCustomInput() throws Exception {
        String testJdk = System.getProperty(TEST_JDK);
        try {
            System.clearProperty(TEST_JDK);
            doTest("System.out.println(\"read: \" + System.in.read());",
                   "\u0005System.out.println(\"read: \" + System.in.read());",
                   "read: 97",
                   "\u0005/exit");
            doTest("1 + 1", "\u00051 + 1", "$1 ==> 2", "\u0005/exit");
            doTest("for (int i = 0; i < 100; i++) {\nSystem.err.println(i);\n}\n",
                   "\u0005for (int i = 0; i < 100; i++) {",
                   "\u0006System.err.println(i);", "\u0006}",
                   "\u0005/exit");
            StringBuilder longInput = new StringBuilder();
            String constant = "1_______________1";
            longInput.append(constant);
            for (int i = 0; i < 100; i++) {
                longInput.append(" + ");
                longInput.append(constant);
            }
            doTest(longInput.toString(), "\u0005" + longInput);
        } finally {
            System.setProperty(TEST_JDK, testJdk);
        }
    }

    private void doTest(String code, String... expectedLines) throws Exception {
            byte[] cmdInputData = (code + "\n/exit\n").getBytes();
            InputStream cmdInput = new ByteArrayInputStream(cmdInputData);
            InputStream userInput = new ByteArrayInputStream("a\n".getBytes());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream printOut = new PrintStream(out);

            JavaShellToolBuilder.builder()
                    .in(cmdInput, userInput)
                    .out(printOut, printOut, printOut)
                    .promptCapture(true)
                    .start("--no-startup");

            String expected = "read: 97";
            String actual = new String(out.toByteArray());
            List<String> actualLines = Arrays.asList(actual.split("\\R"));

            for (String expectedLine : expectedLines) {
                assertTrue(actualLines.contains(expectedLine),
                            "actual:\n" + actualLines + "\n, expected:\n" + expectedLine);
            }
    }
}

