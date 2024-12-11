/*
 * Copyright (c) 2024, Alphabet LLC. All rights reserved.
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
 * @bug 8337998
 * @summary CompletionFailure in getEnclosingType attaching type annotations
 * @library /tools/javac/lib /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 */

import toolbox.*;
import toolbox.Task.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CompletionErrorOnEnclosingType {
    ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        CompletionErrorOnEnclosingType t = new CompletionErrorOnEnclosingType();
        t.testMissingEnclosingType();
    }

    void testMissingEnclosingType() throws Exception {
        String annoSrc =
                """
                import static java.lang.annotation.ElementType.TYPE_USE;
                import java.lang.annotation.Target;
                @Target(TYPE_USE)
                @interface Anno {}

                class A<E> {}

                class B {
                  private @Anno A<String> a;
                }
                """;
        String cSrc =
                """
                class C {
                  B b;
                }
                """;

        Path base = Paths.get(".");
        Path src = base.resolve("src");
        tb.createDirectories(src);
        tb.writeJavaFiles(src, annoSrc, cSrc);
        Path out = base.resolve("out");
        tb.createDirectories(out);
        new JavacTask(tb).outdir(out).files(tb.findJavaFiles(src)).run();

        // now if we remove A.class there will be an error but javac should not crash
        tb.deleteFiles(out.resolve("A.class"));
        List<String> log =
                new JavacTask(tb)
                        .outdir(out)
                        .classpath(out)
                        .options("-XDrawDiagnostics")
                        .files(src.resolve("C.java"))
                        .run(Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

        var expectedOutput =
                List.of(
                        "B.class:-:-: compiler.err.cant.attach.type.annotations: @Anno, B, a,"
                            + " (compiler.misc.class.file.not.found: A)",
                        "1 error");
        if (!expectedOutput.equals(log)) {
            throw new Exception("expected output not found: " + log);
        }
    }
}
