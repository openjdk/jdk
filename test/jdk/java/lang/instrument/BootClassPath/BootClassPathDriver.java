/*
 * Copyright (c) 2004, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5055293 8273188
 * @key intermittent
 * @summary Test non US-ASCII characters in the value of the Boot-Class-Path
 *          attribute.
 *
 * @library /test/lib
 * @build Setup Agent AgentSupport DummyMain Cleanup
 * @run main/othervm/timeout=240 BootClassPathDriver
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class BootClassPathDriver {

    public static void main(String[] args) throws Exception {
        String testClasses = System.getProperty("test.classes");
        String testSrc = System.getProperty("test.src");

        // Run Setup to create non-ASCII boot dir and manifest
        OutputAnalyzer setup = ProcessTools.executeTestJava(
            "-classpath", testClasses, "Setup", testClasses, "Agent");
        setup.reportDiagnosticSummary();
        setup.shouldHaveExitValue(0);

        // Read the boot directory name
        String bootDir = Files.readString(Path.of(testClasses, "boot.dir")).trim();
        System.out.println("Boot dir: " + bootDir);

        // Compile AgentSupport into the boot directory
        CompilerUtils.compile(Path.of(testSrc, "AgentSupport.java"), Path.of(bootDir));

        // Create agent jar
        Path manifestPath = Path.of(testClasses, "MANIFEST.MF");
        Path agentJar = Path.of(testClasses, "Agent.jar");

        ProcessBuilder pb = new ProcessBuilder(
            Path.of(System.getProperty("test.jdk"), "bin", "jar").toString(),
            "cvfm", agentJar.toString(), manifestPath.toString(),
            "-C", testClasses, "Agent.class");
        pb.inheritIO();
        int rc = pb.start().waitFor();
        if (rc != 0) throw new RuntimeException("jar failed with exit code " + rc);

        // Run the test
        OutputAnalyzer output = ProcessTools.executeTestJava(
            "-javaagent:" + agentJar,
            "-classpath", testClasses,
            "DummyMain");
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0);

        // Cleanup
        OutputAnalyzer cleanup = ProcessTools.executeTestJava(
            "-classpath", testClasses, "Cleanup", bootDir);
        cleanup.reportDiagnosticSummary();

        System.out.println("PASS: BootClassPathTest passed.");
    }
}
