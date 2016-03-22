/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8043643
 * @summary Run the langtools coding rules over the langtools source code.
 * @modules java.base/sun.reflect.annotation
 *          java.logging
 *          java.xml
 *          jdk.compiler/com.sun.tools.javac.resources
 *          jdk.compiler/com.sun.tools.javac.util
 */


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.tools.javac.util.Assert;

public class RunCodingRules {
    public static void main(String... args) throws Exception {
        new RunCodingRules().run();
    }

    public void run() throws Exception {
        Path testSrc = Paths.get(System.getProperty("test.src", "."));
        Path targetDir = Paths.get(System.getProperty("test.classes", "."));
        List<Path> sourceDirs = null;
        Path crulesDir = null;
        for (Path d = testSrc; d != null; d = d.getParent()) {
            if (Files.exists(d.resolve("TEST.ROOT"))) {
                d = d.getParent();
                Path toolsPath = d.resolve("make/tools");
                if (Files.exists(toolsPath)) {
                    crulesDir = toolsPath;
                    sourceDirs = Files.walk(d.resolve("src"), 1)
                                      .map(p -> p.resolve("share/classes"))
                                      .filter(p -> Files.isDirectory(p))
                                      .collect(Collectors.toList());
                    break;
                }
            }
        }

        if (sourceDirs == null || crulesDir == null) {
            System.err.println("Warning: sources not found, test skipped.");
            return ;
        }

        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = javaCompiler.getStandardFileManager(null, null, null)) {
            DiagnosticListener<JavaFileObject> noErrors = diagnostic -> {
                Assert.check(diagnostic.getKind() != Diagnostic.Kind.ERROR, diagnostic.toString());
            };

            List<File> crulesFiles = Files.walk(crulesDir)
                                          .filter(entry -> entry.getFileName().toString().endsWith(".java"))
                                          .filter(entry -> entry.getParent().endsWith("crules"))
                                          .map(entry -> entry.toFile())
                                          .collect(Collectors.toList());

            Path crulesTarget = targetDir.resolve("crules");
            Files.createDirectories(crulesTarget);
            List<String> crulesOptions = Arrays.asList(
                    "-XaddExports:"
                        + "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,"
                        + "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED,"
                        + "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED,"
                        + "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED,"
                        + "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                    "-d", crulesTarget.toString());
            javaCompiler.getTask(null, fm, noErrors, crulesOptions, null,
                    fm.getJavaFileObjectsFromFiles(crulesFiles)).call();
            Path registration = crulesTarget.resolve("META-INF/services/com.sun.source.util.Plugin");
            Files.createDirectories(registration.getParent());
            try (Writer metaInfServices = Files.newBufferedWriter(registration, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                metaInfServices.write("crules.CodingRulesAnalyzerPlugin\n");
            }

            List<File> sources = sourceDirs.stream()
                                           .flatMap(dir -> silentFilesWalk(dir))
                                           .filter(entry -> entry.getFileName().toString().endsWith(".java"))
                                           .map(p -> p.toFile())
                                           .collect(Collectors.toList());

            Path sourceTarget = targetDir.resolve("classes");
            Files.createDirectories(sourceTarget);
            String processorPath = crulesTarget.toString() + File.pathSeparator + crulesDir.toString();
            List<String> options = Arrays.asList(
                    "-d", sourceTarget.toString(),
                    "-processorpath", processorPath,
                    "-Xplugin:coding_rules");
            javaCompiler.getTask(null, fm, noErrors, options, null,
                    fm.getJavaFileObjectsFromFiles(sources)).call();
        }
    }

    Stream<Path> silentFilesWalk(Path dir) throws IllegalStateException {
        try {
            return Files.walk(dir);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
