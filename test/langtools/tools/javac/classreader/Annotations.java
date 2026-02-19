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

/*
 * @test
 * @bug 8305250
 * @summary Check behavior w.r.t. annotations missing from the classpath.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit Annotations
 */

import java.nio.file.Files;
import java.util.List;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.Task;

public class Annotations {
    private ToolBox tb = new ToolBox();
    private Path base;
    

    @Test
    public void testParameterModifiersNotVisible() throws Exception {
        Path ann = base.resolve("annotations");
        Path annSrc = ann.resolve("src");
        Path annClasses = ann.resolve("classes");

        tb.writeJavaFiles(annSrc,
                          """
                          package annotations;
                          public @interface Ann {
                              public E e();
                          }
                          """,
                          """
                          package annotations;
                          public enum E {
                              A;
                          }
                          """);

        Files.createDirectories(annClasses);

        new JavacTask(tb)
                .outdir(annClasses)
                .files(tb.findJavaFiles(annSrc))
                .run()
                .writeAll();

        Path lib = base.resolve("lib");
        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");

        tb.writeJavaFiles(libSrc,
                          """
                          package lib;
                          import annotations.*;
                          @Ann(e = E.A)
                          public class Lib {
                          }
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
                .outdir(libClasses)
                .classpath(annClasses)
                .files(tb.findJavaFiles(libSrc))
                .run()
                .writeAll();

        Path test = base.resolve("test");
        Path testSrc = test.resolve("src");
        Path testClasses = test.resolve("classes");

        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import lib.*;
                          public class Test {
                              Lib l;
                          }
                          """);

        Files.createDirectories(testClasses);

        //annotations available, no errors/warnings:
        new JavacTask(tb)
                .outdir(testClasses)
                .classpath(libClasses, annClasses)
                .options("-Werror", "-Xlint:classfile")
                .files(tb.findJavaFiles(testSrc))
                .run()
                .writeAll();

        //annotation and enum missing, no errors/warnings:
        new JavacTask(tb)
                .outdir(testClasses)
                .classpath(libClasses)
                .options("-Werror", "-Xlint:classfile")
                .files(tb.findJavaFiles(testSrc))
                .run()
                .writeAll();

        tb.writeJavaFiles(annSrc,
                          """
                          package annotations;
                          public enum E {
                              B;
                          }
                          """);

        Files.createDirectories(annClasses);

        new JavacTask(tb)
                .outdir(annClasses)
                .files(tb.findJavaFiles(annSrc))
                .run()
                .writeAll();

        List<String> log;

        //enum missing the enum constant recorded in the classfile, report warning:
        log = new JavacTask(tb)
                .outdir(testClasses)
                .classpath(libClasses, annClasses)
                .options("-Xlint:classfile", "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        tb.checkEqual(log,
                      List.of("Lib.class:-:-: compiler.warn.unknown.enum.constant: E.class, annotations.E, A",
                              "1 warning"));

        //enum is missing, but the annotation is not, report warning:
        Files.delete(annClasses.resolve("annotations").resolve("E.class"));

        log = new JavacTask(tb)
                .outdir(testClasses)
                .classpath(libClasses, annClasses)
                .options("-Xlint:classfile", "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        tb.checkEqual(log,
                      List.of("Lib.class:-:-: compiler.warn.unknown.enum.constant.reason: Ann.class, annotations.E, A, (compiler.misc.class.file.not.found: annotations.E)",
                              "1 warning"));

        tb.writeJavaFiles(annSrc,
                          """
                          package annotations;
                          public @interface Ann {
                              public E nue();
                          }
                          """,
                          """
                          package annotations;
                          public enum E {
                              A;
                          }
                          """);

        new JavacTask(tb)
                .outdir(annClasses)
                .files(tb.findJavaFiles(annSrc))
                .run()
                .writeAll();

        //enum is OK and the annotation exists, but the annotation is missing the required attribute method, report warning:
        log = new JavacTask(tb)
                .outdir(testClasses)
                .classpath(libClasses, annClasses)
                .options("-Xlint:classfile", "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        tb.checkEqual(log,
                      List.of("Lib.class:-:-: compiler.warn.annotation.method.not.found: annotations.Ann, e",
                              "1 warning"));
    }

    @BeforeEach
    public void setup(TestInfo ti) {
        base = Path.of(".").resolve(ti.getTestMethod().orElseThrow().getName());
    }
}
