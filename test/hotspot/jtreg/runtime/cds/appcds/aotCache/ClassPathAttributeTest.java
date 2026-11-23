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
 *
 */

/**
 * @test
 * @summary Test the Class-Path attribute in a JAR manifest
 * @bug 8390025
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build ClassPathAttributeTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar ClassPathAttributeApp
 * @run driver ClassPathAttributeTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.cds.CDSJarUtils;
import jdk.test.lib.cds.CDSJarUtils.JarOptions;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class ClassPathAttributeTest {
    public static void main(String[] args) throws Exception {
        final String mainClass = "ClassPathAttributeApp";
        final String appJar = ClassFileInstaller.getJarPath("app.jar");
        final String emptyDir = "empty";
        final String emptyJar = "cp.jar";

        String appAbsolutePath = Path.of(appJar).toAbsolutePath().toString().replace("\\", "/");
        final String classPathAttribute = Paths.get(appJar).toUri().toString();

        // Create manifest using absolute path
        Path manifest = Path.of("cp.mf");
        Files.writeString(manifest,
            "Manifest-Version: 1.0\n" +
            "Class-Path: " + classPathAttribute + "\n\n");
        Files.createDirectory(Path.of(emptyDir));
        CDSJarUtils.buildFromDirectory(emptyJar, emptyDir, JarOptions.of().setManifest(manifest.toString()));

        // Use JAR with no class files which calls the app through the class path in the
        // manifest file.
        SimpleCDSAppTester.of("ClassPathAttribute")
            .addVmArgs("-Xlog:cds,aot,class+path=debug")
            .classpath(emptyJar)
            .appCommandLine(mainClass)
            .setTrainingChecker((OutputAnalyzer out) -> {
                out.shouldContain("ClassPathAttributeApp loaded successfully");
                // Prefix "file:" should not be in the classpath
                out.shouldNotMatch("path \\[\\d+\\] = file:.*");
                out.shouldMatch("path \\[\\d+\\] = " + appAbsolutePath);
            })
            .runAOTWorkflow();
    }
}

final class ClassPathAttributeApp {
    public static void main(String[] args) {
        System.out.println("ClassPathAttributeApp loaded successfully");
        System.out.println("ClassPathAttributeApp.class = " + ClassPathAttributeApp.class.getResource("/ClassPathAttributeApp.class"));
        System.out.println("java.class.path = " + System.getProperty("java.class.path"));
    }
}
