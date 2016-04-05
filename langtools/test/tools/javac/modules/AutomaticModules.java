/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test automatic modules
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.JarTask ModuleTestBase
 * @run main AutomaticModules
 */

import java.nio.file.Files;
import java.nio.file.Path;

import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class AutomaticModules extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        AutomaticModules t = new AutomaticModules();
        t.runTests();
    }

    @Test
    void testSimple(Path base) throws Exception {
        Path legacySrc = base.resolve("legacy-src");
        tb.writeJavaFiles(legacySrc,
                          "package api; import java.awt.event.ActionListener; public abstract class Api implements ActionListener {}");
        Path legacyClasses = base.resolve("legacy-classes");
        Files.createDirectories(legacyClasses);

        String log = new JavacTask(tb)
                .options()
                .outdir(legacyClasses)
                .files(findJavaFiles(legacySrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty()) {
            throw new Exception("unexpected output: " + log);
        }

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        Path jar = modulePath.resolve("test-api-1.0.jar");

        new JarTask(tb, jar)
          .baseDir(legacyClasses)
          .files("api/Api.class")
          .run();

        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1 { requires test.api; }",
                          "package impl; public class Impl { public void e(api.Api api) { api.actionPerformed(null); } }");

        new JavacTask(tb)
                .options("-modulesourcepath", moduleSrc.toString(), "-modulepath", modulePath.toString(), "-addmods", "java.desktop")
                .outdir(classes)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll();
    }

    @Test
    void testUnnamedModule(Path base) throws Exception {
        Path legacySrc = base.resolve("legacy-src");
        tb.writeJavaFiles(legacySrc,
                          "package api; public abstract class Api { public void run(CharSequence str) { } private void run(base.Base base) { } }",
                          "package base; public interface Base { public void run(); }");
        Path legacyClasses = base.resolve("legacy-classes");
        Files.createDirectories(legacyClasses);

        String log = new JavacTask(tb)
                .options()
                .outdir(legacyClasses)
                .files(findJavaFiles(legacySrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty()) {
            throw new Exception("unexpected output: " + log);
        }

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        Path apiJar = modulePath.resolve("test-api-1.0.jar");

        new JarTask(tb, apiJar)
          .baseDir(legacyClasses)
          .files("api/Api.class")
          .run();

        Path baseJar = base.resolve("base.jar");

        new JarTask(tb, baseJar)
          .baseDir(legacyClasses)
          .files("base/Base.class")
          .run();

        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1 { requires test.api; }",
                          "package impl; public class Impl { public void e(api.Api api) { api.run(\"\"); } }");

        new JavacTask(tb)
                .options("-modulesourcepath", moduleSrc.toString(), "-modulepath", modulePath.toString(), "-classpath", baseJar.toString())
                .outdir(classes)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll();
    }

    @Test
    void testModuleInfoFromClassFileDependsOnAutomatic(Path base) throws Exception {
        Path automaticSrc = base.resolve("automaticSrc");
        tb.writeJavaFiles(automaticSrc, "package api; public class Api {}");
        Path automaticClasses = base.resolve("automaticClasses");
        tb.createDirectories(automaticClasses);

        String automaticLog = new JavacTask(tb)
                                .outdir(automaticClasses)
                                .files(findJavaFiles(automaticSrc))
                                .run()
                                .writeAll()
                                .getOutput(Task.OutputKind.DIRECT);

        if (!automaticLog.isEmpty())
            throw new Exception("expected output not found: " + automaticLog);

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        Path automaticJar = modulePath.resolve("automatic-1.0.jar");

        new JarTask(tb, automaticJar)
          .baseDir(automaticClasses)
          .files("api/Api.class")
          .run();

        Path depSrc = base.resolve("depSrc");
        Path depClasses = base.resolve("depClasses");

        Files.createDirectories(depSrc);
        Files.createDirectories(depClasses);

        tb.writeJavaFiles(depSrc,
                          "module m1 { requires public automatic; }",
                          "package dep; public class Dep { api.Api api; }");

        new JavacTask(tb)
                .options("-modulepath", modulePath.toString())
                .outdir(depClasses)
                .files(findJavaFiles(depSrc))
                .run()
                .writeAll();

        Path moduleJar = modulePath.resolve("m1.jar");

        new JarTask(tb, moduleJar)
          .baseDir(depClasses)
          .files("module-info.class", "dep/Dep.class")
          .run();

        Path testSrc = base.resolve("testSrc");
        Path testClasses = base.resolve("testClasses");

        Files.createDirectories(testSrc);
        Files.createDirectories(testClasses);

        tb.writeJavaFiles(testSrc,
                          "module m2 { requires automatic; }",
                          "package test; public class Test { }");

        new JavacTask(tb)
                .options("-modulepath", modulePath.toString())
                .outdir(testClasses)
                .files(findJavaFiles(testSrc))
                .run()
                .writeAll();
    }
}
