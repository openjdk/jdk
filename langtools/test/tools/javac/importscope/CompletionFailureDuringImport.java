/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8131915
 * @summary Verify that CompletionFailure thrown during listing of import content is handled properly.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 */

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class CompletionFailureDuringImport {
    public static void main(String... args) throws Exception {
        new CompletionFailureDuringImport().run();
    }

    ToolBox tb = new ToolBox();

    void run() throws Exception {
        tb.new JavacTask()
          .outdir(".")
          .sources("package p; public class Super { public static final int I = 0; }",
                   "package p; public class Sub extends Super { }")
          .run()
          .writeAll();

        Files.delete(Paths.get(".", "p", "Super.class"));

        doTest("import static p.Sub.*;",
               "",
               "Test.java:1:1: compiler.err.cant.access: p.Super, (compiler.misc.class.file.not.found: p.Super)",
               "1 error");
        doTest("import static p.Sub.I;",
               "",
               "Test.java:1:1: compiler.err.cant.access: p.Super, (compiler.misc.class.file.not.found: p.Super)",
               "1 error");
        doTest("import static p.Sub.*;",
               "int i = I;",
               "Test.java:1:1: compiler.err.cant.access: p.Super, (compiler.misc.class.file.not.found: p.Super)",
               "Test.java:1:52: compiler.err.cant.resolve.location: kindname.variable, I, , , (compiler.misc.location: kindname.class, Test, null)",
               "2 errors");
        doTest("import static p.Sub.I;",
               "int i = I;",
               "Test.java:1:1: compiler.err.cant.access: p.Super, (compiler.misc.class.file.not.found: p.Super)",
               "Test.java:1:52: compiler.err.cant.resolve.location: kindname.variable, I, , , (compiler.misc.location: kindname.class, Test, null)",
               "2 errors");
    }

    void doTest(String importText, String useText, String... expectedOutput) {
        List<String> log = tb.new JavacTask()
                .classpath(".")
                .sources(importText + " public class Test { " + useText + " }")
                .options("-XDrawDiagnostics")
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutputLines(ToolBox.OutputKind.DIRECT);

        if (!log.equals(Arrays.asList(expectedOutput))) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }
}
