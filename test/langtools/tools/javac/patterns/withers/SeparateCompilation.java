/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8324651
 * @summary Verify separate compilation works w.r.t. derived record creation expression
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main SeparateCompilation
*/

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.ToolBox;

public class SeparateCompilation extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new SeparateCompilation().runTests();
    }

    SeparateCompilation() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testPattern(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          "record A(int i) {}",
                          """
                          class Test {
                              public static A test(A arg) {
                                  A a = arg with {
                                      i = 32;
                                  };
                                  return a;
                              }
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("--release", System.getProperty("java.specification.version"))
            .outdir(classes)
            .files(tb.findJavaFiles(src.resolve("A.java")))
            .run()
            .writeAll();

        new JavacTask(tb)
            .options("--enable-preview",
                     "--release", System.getProperty("java.specification.version"))
            .classpath(classes.toString())
            .outdir(classes)
            .files(tb.findJavaFiles(src.resolve("Test.java")))
            .run()
            .writeAll();
    }

}
