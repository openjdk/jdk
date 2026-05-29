/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6173575 6388987
 * @summary Unit tests for appendToBootstrapClassLoaderSearch and
 *   appendToSystemClassLoaderSearch methods.
 *
 * @library /test/lib
 * @build Agent AgentSupport BootSupport BasicTest PrematureLoadTest DynamicTest Application
 * @run main/othervm/timeout=240 RunTestsDriver
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class RunTestsDriver {

    public static void main(String[] args) throws Exception {
        String testClasses = System.getProperty("test.classes");
        String testSrc = System.getProperty("test.src");
        Path classesDir = Path.of(testClasses);
        int failures = 0;

        // Create Agent.jar with manifest
        Manifest agentMan = new Manifest();
        Attributes agentAttrs = agentMan.getMainAttributes();
        agentAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        agentAttrs.putValue("Premain-Class", "Agent");
        agentAttrs.putValue("Can-Redefine-Classes", "true");
        JarUtils.createJarFile(Path.of("Agent.jar"), agentMan, classesDir,
            Path.of("Agent.class"));

        // Create support jars
        JarUtils.createJarFile(Path.of("AgentSupport.jar"), classesDir, Path.of("AgentSupport.class"));
        JarUtils.createJarFile(Path.of("BootSupport.jar"), classesDir, Path.of("BootSupport.class"));
        JarUtils.createJarFile(Path.of("SimpleTests.jar"), classesDir,
            Path.of("BasicTest.class"), Path.of("PrematureLoadTest.class"));

        // Run BasicTest
        System.out.println("\n=== BasicTest ===");
        OutputAnalyzer out = ProcessTools.executeTestJava(
            "-javaagent:Agent.jar", "-classpath", "SimpleTests.jar", "BasicTest");
        out.reportDiagnosticSummary();
        if (out.getExitValue() != 0) {
            System.out.println("FAIL: BasicTest");
            failures++;
        }

        // Run PrematureLoadTest
        System.out.println("\n=== PrematureLoadTest ===");
        out = ProcessTools.executeTestJava(
            "-javaagent:Agent.jar", "-classpath", "SimpleTests.jar", "PrematureLoadTest");
        out.reportDiagnosticSummary();
        if (out.getExitValue() != 0) {
            System.out.println("FAIL: PrematureLoadTest");
            failures++;
        }

        // Setup for DynamicTest
        System.out.println("\n=== DynamicTest setup ===");

        // Create Tracer.jar (org.tools.Tracer)
        Path tmpDir = Path.of("tmp");
        Files.createDirectories(tmpDir);
        CompilerUtils.compile(Path.of(testSrc, "Tracer.java"), tmpDir);
        JarUtils.createJarFile(Path.of("Tracer.jar"), tmpDir, Path.of("org/tools/Tracer.class"));

        // Compile InstrumentedApplication as Application, save bytecode
        Path instrSrc = Path.of("instrsrc");
        Files.createDirectories(instrSrc);
        Files.copy(Path.of(testSrc, "InstrumentedApplication.java"), instrSrc.resolve("Application.java"));
        Path instrOut = Path.of("instrout");
        Files.createDirectories(instrOut);
        CompilerUtils.compile(instrSrc, instrOut, "-classpath", "Tracer.jar");
        Files.copy(instrOut.resolve("Application.class"), Path.of("InstrumentedApplication.bytes"));

        // Run DynamicTest
        System.out.println("\n=== DynamicTest ===");
        out = ProcessTools.executeTestJava(
            "-classpath", testClasses, "-javaagent:Agent.jar", "DynamicTest");
        out.reportDiagnosticSummary();
        if (out.getExitValue() != 0) {
            System.out.println("FAIL: DynamicTest");
            failures++;
        }

        if (failures > 0) {
            throw new RuntimeException(failures + " test(s) failed");
        }
        System.out.println("\nAll tests passed.");
    }
}
