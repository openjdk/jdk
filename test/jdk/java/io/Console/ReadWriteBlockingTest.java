/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8340830
 * @summary Check if writing to Console is not blocked by other thread's read.
 *          This test relies on sleep(1000) to ensure that readLine() is
 *          invoked before printf(), which may not always occur.
 * @library /test/lib
 * @requires (os.family == "linux" | os.family == "mac")
 * @run junit ReadWriteBlockingTest
 */

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ReadWriteBlockingTest {

    @ParameterizedTest
    @ValueSource(strings = {"java.base", "jdk.internal.le"})
    @EnabledOnOs({OS.LINUX, OS.MAC})
    public void testReadWriteBlocking(String consoleModule) throws Exception {
        // check "expect" command availability
        var expect = Path.of("/usr/bin/expect");
        if (!Files.exists(expect) || !Files.isExecutable(expect)) {
            Assumptions.abort("'" + expect + "' not found");
        }

        // invoking "expect" command
        var testSrc = System.getProperty("test.src", ".");
        var testClasses = System.getProperty("test.classes", ".");
        var jdkDir = System.getProperty("test.jdk");
        OutputAnalyzer output = ProcessTools.executeProcess(
            "/usr/bin/expect",
            "-n",
            testSrc + "/readWriteBlocking.exp",
            jdkDir + "/bin/java",
            "-classpath", testClasses,
            "-Djdk.console=" + consoleModule,
            "ReadWriteBlockingTest");
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0);
    }

    public static void main(String... args) {
        var con = System.console();
        Thread.ofVirtual().start(() -> {
            try {
                // give some time for main thread to invoke readLine()
                Thread.sleep(1000);
            } catch (InterruptedException _) {}
            con.printf("printf() invoked");
        });
        con.readLine("");
    }
}
