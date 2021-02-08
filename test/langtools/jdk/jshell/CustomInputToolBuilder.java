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
            byte[] cmdInputData = "System.out.println(\"read: \" + System.in.read());\n/exit\n".getBytes();
            InputStream cmdInput = new ByteArrayInputStream(cmdInputData);
            InputStream userInput = new ByteArrayInputStream("a\n".getBytes());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream printOut = new PrintStream(out);

            JavaShellToolBuilder.builder()
                    .in(cmdInput, userInput)
                    .out(printOut, new PrintStream(new ByteArrayOutputStream()), printOut)
                    .promptCapture(true)
                    .start("--no-startup");

            String expected = "read: 97";
            String actual = new String(out.toByteArray());

            assertTrue(actual.contains(expected),
                        "actual:\n" + actual + "\n, expected:\n" + expected);
        } finally {
            System.setProperty(TEST_JDK, testJdk);
        }
    }

}

