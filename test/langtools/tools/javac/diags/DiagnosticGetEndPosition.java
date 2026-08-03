/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8371309
 * @summary Verify that Diagnostic.getEndPosition works reasonably
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @run junit DiagnosticGetEndPosition
 */
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;

import org.junit.jupiter.api.Test;

import toolbox.ToolBox;

import static org.junit.jupiter.api.Assertions.*;

public class DiagnosticGetEndPosition {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final ToolBox tb = new ToolBox();

    @Test
    public void testGetEndPositionDuringParsing() {
        JavaFileObject testFile =
                SimpleJavaFileObject.forSource(URI.create("mem:///Test.java"),
                                               """
                                               public class Test extends {}
                                               """);
        compiler.getTask(
            null,
            null,
            diagnostic -> assertEquals(26, diagnostic.getEndPosition()),
            null,
            null,
            List.of(testFile)
        ).call();
    }

    @Test
    public void testNoErrorsFromInternalParses() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          module m {
                             exports test;
                          }
                          """,
                          """
                          package test;
                          public class Test extends {}
                          """);

        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            compiler.getTask(
                null,
                null,
                d -> fail(d.toString()),
                List.of("-sourcepath", src.toString()),
                null,
                fm.getJavaFileObjects(src.resolve("module-info.java"))
            ).call();
        }
    }

    @Test
    public void testGetEndPositionWorkForImplicitParse() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        String implCode = """
                          package test;
                          public class Impl {
                              public static final int C = 1 / 0;
                          }
                          """;
        tb.writeJavaFiles(src,
                          implCode,
                          """
                          package test;
                          public class Test {
                              Impl i; //force parsing of Impl
                          }
                          """);

        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            compiler.getTask(
                null,
                null,
                d -> {
                    // Use line and column instead of start and end positions
                    // to handle platform-dependent newlines, which could be
                    // different between the text block and the written file.
                    int line = (int) d.getLineNumber();
                    int col = (int) d.getColumnNumber();
                    assertEquals(1, d.getEndPosition() - d.getStartPosition());
                    String substring = implCode.split("\\R")[line - 1].substring(col - 1, col);
                    assertEquals("0", substring);
                },
                List.of("-sourcepath", src.toString(), "-Xlint:divzero"),
                null,
                fm.getJavaFileObjects(src.resolve("test").resolve("Test.java"))
            ).call();
        }
    }

    @Test
    public void testWronglyNamedClass() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        tb.writeFile(src.resolve("test").resolve("WronglyNamed.java"),
                     """
                     package test;
                     class SomeOtherName {}
                     """);
        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class Test {
                              WronglyNamed l; //parse WronglyNamed.java
                          }
                          """);

        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            compiler.getTask(
                null,
                null,
                null,
                List.of("-sourcepath", src.toString()),
                null,
                fm.getJavaFileObjects(tb.findJavaFiles(src))
            ).call();
        }
    }

    @Test
    public void testWronglyNamedPackageInfo() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        tb.writeFile(src.resolve("test").resolve("package-info.java"),
                     """
                     package wrongpackage;
                     """);
        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class Test {
                          }
                          """);

        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(
                null,
                null,
                null,
                List.of("-sourcepath", src.toString()),
                null,
                fm.getJavaFileObjects(src.resolve("test").resolve("Test.java"))
            );
            task.analyze();
            task.getElements().getPackageElement("test").getAnnotationMirrors();
        }
    }

    @Test
    public void testGetEndPositionSyntheticTree() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        String testCode = """
                          package test;
                          public class Test extends Base {
                          }
                          class Base {
                              Base(int i) {}
                          }
                          """;

        tb.writeJavaFiles(src, testCode);

        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            compiler.getTask(
                null,
                null,
                d -> assertEquals("",
                                  testCode.substring((int) d.getStartPosition(),
                                                     (int) d.getEndPosition())),
                List.of("-sourcepath", src.toString(), "-Xlint:divzero"),
                null,
                fm.getJavaFileObjects(src.resolve("test").resolve("Test.java"))
            ).call();
        }
    }
}
