/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8291769
 * @summary Verify the compiled code does not have unwanted constructs.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main PatternDesugaring
*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

public class PatternDesugaring extends TestRunner {

    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new PatternDesugaring().runTests();
    }

    PatternDesugaring() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testPrimitiveNoBoxUnbox(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   public int test(Object obj) {
                       return switch (obj) {
                           case R(int i) -> i;
                           default -> -1;
                       };
                   }
                   record R(int i) {}
               }
               """,
               decompiled -> {
                   if (decompiled.contains("intValue") || decompiled.contains("valueOf")) {
                       throw new AssertionError("Has boxing/unboxing.");
                   }
               });
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   public int test(Object obj) {
                       return obj instanceof R(int i) ? i : -1;
                   }
                   record R(int i) {}
               }
               """,
               decompiled -> {
                   if (decompiled.contains("intValue") || decompiled.contains("valueOf")) {
                       throw new AssertionError("Has boxing/unboxing.");
                   }
               });
    }

    @Test
    public void testCacheRecordsForRecordPatterns(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   public int test(Object obj) {
                       return switch (obj) {
                           case R(Integer i, Integer j, Integer k) -> i + j + k;
                           default -> -1;
                       };
                   }
                   record R(Integer i, Integer j, Integer k) {}
               }
               """,
               decompiled -> {
                   if (decompiled.split("checkcast").length != 2) {
                       throw new AssertionError("Unexpected number of checkcasts.");
                   }
               });
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   public int test(Object obj) {
                       return obj instanceof R(Integer i, Integer j, Integer k) ? i + j + k: -1;
                   }
                   record R(Integer i, Integer j, Integer k) {}
               }
               """,
               decompiled -> {
                   if (decompiled.split("checkcast").length != 2) {
                       throw new AssertionError("Unexpected number of checkcasts.");
                   }
               });
    }

    private void doTest(Path base, String[] libraryCode, String testCode, Consumer<String> validate) throws IOException {
        Path current = base.resolve(".");
        Path libClasses = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        if (libraryCode.length != 0) {
            Path libSrc = current.resolve("lib-src");

            for (String code : libraryCode) {
                tb.writeJavaFiles(libSrc, code);
            }

            new JavacTask(tb)
                    .options("--enable-preview",
                             "-source", JAVA_VERSION)
                    .outdir(libClasses)
                    .files(tb.findJavaFiles(libSrc))
                    .run();
        }

        Path src = current.resolve("src");
        tb.writeJavaFiles(src, testCode);

        Path classes = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        var log =
                new JavacTask(tb)
                    .options("--enable-preview",
                             "-source", JAVA_VERSION,
                             "-XDrawDiagnostics",
                             "-Xlint:-preview",
                             "--class-path", libClasses.toString(),
                             "-XDshould-stop.at=FLOW")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.SUCCESS)
                    .writeAll();
        var decompiled =
                new JavapTask(tb)
                    .classpath(classes.toString())
                    .classes("test.Test")
                    .options("-s", "-verbose")
                    .run()
                    .writeAll()
                    .getOutput(Task.OutputKind.DIRECT);

        System.err.println("decompiled: " + decompiled);

        validate.accept(decompiled);
    }

}
