/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 */


import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

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
        File testSrc = new File(System.getProperty("test.src", "."));
        File targetDir = new File(System.getProperty("test.classes", "."));
        File sourceDir = null;
        File crulesDir = null;
        for (File d = testSrc; d != null; d = d.getParentFile()) {
            if (new File(d, "TEST.ROOT").exists()) {
                d = d.getParentFile();
                File f = new File(d, "src/share/classes");
                if (f.exists()) {
                    sourceDir = f;
                    f = new File(d, "make/tools");
                    if (f.exists())
                        crulesDir = f;
                    break;
                }
            }
        }

        if (sourceDir == null || crulesDir == null) {
            System.err.println("Warning: sources not found, test skipped.");
            return ;
        }

        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = javaCompiler.getStandardFileManager(null, null, null);
        DiagnosticListener<JavaFileObject> noErrors = diagnostic -> {
            Assert.check(diagnostic.getKind() != Diagnostic.Kind.ERROR, diagnostic.toString());
        };

        List<File> crulesFiles = Files.walk(crulesDir.toPath())
                                      .map(entry -> entry.toFile())
                                      .filter(entry -> entry.getName().endsWith(".java"))
                                      .filter(entry -> entry.getParentFile().getName().equals("crules"))
                                      .collect(Collectors.toList());

        File crulesTarget = new File(targetDir, "crules");
        crulesTarget.mkdirs();
        List<String> crulesOptions = Arrays.asList("-d", crulesTarget.getAbsolutePath());
        javaCompiler.getTask(null, fm, noErrors, crulesOptions, null,
                fm.getJavaFileObjectsFromFiles(crulesFiles)).call();
        File registration = new File(crulesTarget, "META-INF/services/com.sun.source.util.Plugin");
        registration.getParentFile().mkdirs();
        try (Writer metaInfServices = new FileWriter(registration)) {
            metaInfServices.write("crules.CodingRulesAnalyzerPlugin\n");
        }

        List<File> sources = Files.walk(sourceDir.toPath())
                                  .map(entry -> entry.toFile())
                                  .filter(entry -> entry.getName().endsWith(".java"))
                                  .collect(Collectors.toList());

        File sourceTarget = new File(targetDir, "classes");
        sourceTarget.mkdirs();
        String processorPath = crulesTarget.getAbsolutePath() + File.pathSeparator +
                crulesDir.getAbsolutePath();
        List<String> options = Arrays.asList("-d", sourceTarget.getAbsolutePath(),
                "-processorpath", processorPath, "-Xplugin:coding_rules");
        javaCompiler.getTask(null, fm, noErrors, options, null,
                fm.getJavaFileObjectsFromFiles(sources)).call();
    }
}
