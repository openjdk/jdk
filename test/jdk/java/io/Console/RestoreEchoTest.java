/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Paths;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @test
 * @bug 8332161
 * @summary Tests JdkConsoleImpl restores the echo state after readPassword() call
 * @library /test/lib
 * @run junit RestoreEchoTest
 */
public class RestoreEchoTest {

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    public void testRestoreEcho() throws Throwable {
        // check "expect" command availability
        var expect = Paths.get("/usr/bin/expect");
        if (!Files.exists(expect) || !Files.isExecutable(expect)) {
            Assumptions.abort("'" + expect + "' not found");
        }

        expectRunner("-echo");
        expectRunner("echo");
    }

    private static void expectRunner(String initialEcho) throws Throwable {
        // invoking "expect" command
        var testSrc = System.getProperty("test.src", ".");
        var testClasses = System.getProperty("test.classes", ".");
        var jdkDir = System.getProperty("test.jdk");
        OutputAnalyzer output = ProcessTools.executeProcess(
                "expect",
                "-n",
                testSrc + "/restoreEcho.exp",
                initialEcho,
                jdkDir + "/bin/java",
                "-Djdk.console=java.base",
                "-classpath", testClasses,
                "RestoreEchoTest");
        output.reportDiagnosticSummary();
        assertEquals(0, output.getExitValue());
    }

    public static void main(String... args) throws Throwable {
        if (!"java.base".equals(System.getProperty("jdk.console"))) {
            throw new RuntimeException("Test failed. jdk.console is not java.base");
        }

        Console con = System.console();
        if (con == null) {
            throw new RuntimeException("Test failed. System.console() returned null");
        }

        // testing readLine()
        String input = con.readLine("prompt: ");
        con.printf("input is %s%n", input);

        // testing readPassword()
        input = String.valueOf(con.readPassword("password prompt: "));
        con.printf("password is %s%n", input);
    }
}
