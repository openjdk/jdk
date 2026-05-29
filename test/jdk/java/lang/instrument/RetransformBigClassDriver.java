/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7122253 8016838
 * @key intermittent
 * @summary Retransform a big class.
 * @modules java.instrument
 *          java.management
 *
 * @library /test/lib
 * @build RetransformBigClassAgent SimpleIdentityTransformer BigClass RetransformBigClassApp NMTHelper
 * @run main/othervm/timeout=600 RetransformBigClassDriver
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class RetransformBigClassDriver {

    public static void main(String[] args) throws Exception {
        String testClasses = System.getProperty("test.classes");
        String testSrc = System.getProperty("test.src");

        // Create agent jar with manifest, agent classes, and source file
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Premain-Class", "RetransformBigClassAgent");
        attrs.putValue("Can-Retransform-Classes", "true");

        Path agentJar = Path.of("RetransformBigClassAgent.jar");
        Path classesDir = Path.of(testClasses);

        // MakeJAR4 packages agent classes + SimpleIdentityTransformer source
        // We need: RetransformBigClassAgent*.class, SimpleIdentityTransformer*.java
        List<Path> entries = new ArrayList<>();
        for (File f : classesDir.toFile().listFiles()) {
            if (f.getName().startsWith("RetransformBigClassAgent") && f.getName().endsWith(".class")) {
                entries.add(Path.of(f.getName()));
            }
        }
        // Copy source file into classes dir temporarily
        Path srcFile = Path.of(testSrc, "SimpleIdentityTransformer.java");
        Path destFile = classesDir.resolve("SimpleIdentityTransformer.java");
        Files.copy(srcFile, destFile);
        entries.add(Path.of("SimpleIdentityTransformer.java"));

        JarUtils.createJarFile(agentJar, man, classesDir, entries.toArray(new Path[0]));
        Files.deleteIfExists(destFile);

        // Detect NMT level
        String nmt;
        try {
            OutputAnalyzer oa = ProcessTools.executeTestJava(
                "-XX:NativeMemoryTracking=detail", "-version");
            nmt = (oa.getExitValue() == 0) ?
                "-XX:NativeMemoryTracking=detail" :
                "-XX:NativeMemoryTracking=summary";
        } catch (Exception e) {
            nmt = "-XX:NativeMemoryTracking=summary";
        }

        // Run the test
        OutputAnalyzer output = ProcessTools.executeTestJava(
            "-Xlog:redefine+class+load=debug,redefine+class+load+exceptions=info",
            nmt,
            "-javaagent:RetransformBigClassAgent.jar=BigClass.class",
            "-classpath", testClasses,
            "RetransformBigClassApp");

        output.reportDiagnosticSummary();

        if (output.getExitValue() != 0) {
            throw new RuntimeException("RetransformBigClassApp exited with status " + output.getExitValue());
        }
        if (output.getStdout().contains("Exception") || output.getStderr().contains("Exception")) {
            throw new RuntimeException("Found 'Exception' in test output");
        }
        System.out.println("PASS: RetransformBigClass test passed.");
    }
}
