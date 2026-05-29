/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.JarUtils;

/**
 * Builds agent.jar for BootstrapClassPathTest.
 * Compiles BootstrapClassPathAgent (package p) with inner classes
 * and creates agent.jar with Boot-Class-Path: agent.jar manifest.
 */
public class BuildBootstrapAgent {
    public static void main(String[] args) throws Exception {
        String testSrc = System.getProperty("test.src");

        Path agentClasses = Path.of("agentclasses");
        Files.createDirectories(agentClasses);

        CompilerUtils.compile(Path.of(testSrc, "BootstrapClassPathAgent.java"), agentClasses);

        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Boot-Class-Path", "agent.jar");
        attrs.putValue("Premain-Class", "p.BootstrapClassPathAgent");

        // Collect all class files including inner classes
        Path[] classFiles;
        try (var walk = Files.walk(agentClasses)) {
            classFiles = walk
                .filter(p -> p.toString().endsWith(".class"))
                .map(agentClasses::relativize)
                .toArray(Path[]::new);
        }

        JarUtils.createJarFile(Path.of("agent.jar"), man, agentClasses, classFiles);
    }
}
