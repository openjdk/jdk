/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8315458 8344706
 * @summary Make sure nesting classes don't create symbol conflicts with implicit name.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main NestedClasses
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import toolbox.ToolBox;
import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;

public class NestedClasses {
    private static ToolBox TOOLBOX = new ToolBox();
    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    public static void main(String... arg) throws IOException {
        compPass("A.java", """
            void main() {}
            class A {} // okay
            """);

        compPass("A.java", """
            void main() {}
            class B {
               class A { } // okay
            }
            """);

        compFail("A.java", """
            void main() {}
            class B {
               class B { } //error
            }
            """);
    }

    /*
     * Test source for successful compile.
     */
    static void compPass(String fileName, String code) throws IOException {
        Path path = Path.of(fileName);
        Files.writeString(path, code);
        String output = new JavacTask(TOOLBOX)
                .files(List.of(path))
                .classpath(".")
                .options("-encoding", "utf8")
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (output.contains("compiler.err")) {
            throw new RuntimeException("Error detected");
        }
    }

    /*
     * Test source for unsuccessful compile and specific error.
     */
    static void compFail(String fileName, String code) throws IOException {
        Path path = Path.of(fileName);
        Files.writeString(path, code);
        String output = new JavacTask(TOOLBOX)
                .files(List.of(path))
                .classpath(".")
                .options("-XDrawDiagnostics", "-encoding", "utf8")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!output.contains("compiler.err")) {
            throw new RuntimeException("No error detected");
        }
    }

 }
