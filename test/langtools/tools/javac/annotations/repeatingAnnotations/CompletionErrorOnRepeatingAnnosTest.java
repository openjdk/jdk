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

/*
 * @test
 * @bug 8332850
 * @summary javac crashes if container for repeatable annotation is not found
 * @library /tools/javac/lib /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import toolbox.*;
import toolbox.Task.*;

public class CompletionErrorOnRepeatingAnnosTest {
    ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        CompletionErrorOnRepeatingAnnosTest t = new CompletionErrorOnRepeatingAnnosTest();
        //t.testMissingContainerAnno();
        t.testMissingContainerTypeAnno();
    }

    void testMissingContainerTypeAnno() throws Exception {
        doTest(
                """
                import java.lang.annotation.*;
                import static java.lang.annotation.RetentionPolicy.*;
                import static java.lang.annotation.ElementType.*;
                @Target({TYPE_USE,FIELD}) @Repeatable( As.class) @interface A { }
                @Target({TYPE_USE,FIELD}) @interface As { A[] value(); }
                """,
                """
                class T {
                    @A @A String data = "test";
                }
                """,
                List.of(
                        "T.java:2:5: compiler.err.cant.access: As, (compiler.misc.class.file.not.found: As)",
                        "T.java:2:8: compiler.err.invalid.repeatable.annotation.no.value: As",
                        "2 errors"
                )
        );
    }

    void testMissingContainerAnno() throws Exception {
        doTest(
                """
                import java.lang.annotation.Repeatable;
                @Repeatable(As.class)
                @interface A {}
                @interface As {
                    A[] value();
                }
                """,
                "@A @A class T {}",
                List.of(
                        "T.java:1:1: compiler.err.cant.access: As, (compiler.misc.class.file.not.found: As)",
                        "T.java:1:4: compiler.err.invalid.repeatable.annotation.no.value: As",
                        "2 errors"
                )
        );
    }

    private void doTest(String annosSrc, String annotatedSrc, List<String> expectedOutput) throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        tb.createDirectories(src);
        tb.writeJavaFiles(src, annosSrc);
        Path out = base.resolve("out");
        tb.createDirectories(out);
        new JavacTask(tb)
                .outdir(out)
                .files(tb.findJavaFiles(src))
                .run();
        // let's now compile T.java which uses repeated annotations, we want to load the anno classes from the CP
        tb.deleteFiles(src.resolve("A.java"));
        tb.writeJavaFiles(src, annotatedSrc);
        new JavacTask(tb)
                .outdir(out)
                .classpath(out)
                .files(tb.findJavaFiles(src))
                .run();
        // now if we remove As.class there will be an error but javac should not crash
        tb.deleteFiles(out.resolve("As.class"));
        List<String> log = new JavacTask(tb)
                .outdir(out)
                .classpath(out)
                .options("-XDrawDiagnostics")
                .files(tb.findJavaFiles(src))
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        if (!expectedOutput.equals(log))
            throw new Exception("expected output not found: " + log);
    }
}
