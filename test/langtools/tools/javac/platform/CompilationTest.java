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
 * @bug 8365060
 * @summary Verify javac can compile given snippets with --release
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.platform
 *          jdk.compiler/com.sun.tools.javac.util:+open
 * @build toolbox.ToolBox CompilationTest
 * @run main CompilationTest
 */

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class CompilationTest {

    private final ToolBox tb = new ToolBox();
    public static void main(String... args) throws IOException, URISyntaxException {
        CompilationTest t = new CompilationTest();

        t.testJdkNet();
    }

    void testJdkNet() throws IOException {
        Path root = Paths.get(".");
        Path classes = root.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb)
            .outdir(classes)
            .options("--release", "8",
                     "-XDrawDiagnostics")
            .sources("""
                     import jdk.net.ExtendedSocketOptions;
                     public class Test {
                     }
                     """)
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);
    }

}
