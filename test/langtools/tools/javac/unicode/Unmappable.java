/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4767128 5048557 5048773 8078559 8034251
 * @summary diagnose encoding errors in Java source files
 * @author gafter
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main Unmappable
 */

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class Unmappable {
    void main() throws Exception {
        var source = Path.of("UnmappableInput.java");
        // example from 4766897
        Files.writeString(source, """
                public class UnmappableInput {
                    String s = "z\u00e4hler";
                }
                """, StandardCharsets.ISO_8859_1);

        var output = new JavacTask(new ToolBox(), Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics", "-encoding", "ascii")
                .files(source)
                .run(Task.Expect.FAIL)
                .getOutputLines(Task.OutputKind.DIRECT);
        var expected = List.of(
                "UnmappableInput.java:2:18: compiler.err.illegal.char.for.encoding: E4, ascii",
                "1 error");
        if (!output.equals(expected)) {
            throw new AssertionError(
                    "Expected: " + expected + "\nActual: " + output);
        }
    }
}
