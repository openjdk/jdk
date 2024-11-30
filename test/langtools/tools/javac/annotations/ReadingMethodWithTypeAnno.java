/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8321164
 * @summary Indirectly verify that types.erasure does not complete, called from
 *          ClassReader.isSameBinaryType, which must not complete.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox
 * @run main ReadingMethodWithTypeAnno
 */

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class ReadingMethodWithTypeAnno extends TestRunner {
    public static void main(String... args) throws Exception {
        ReadingMethodWithTypeAnno r = new ReadingMethodWithTypeAnno();
        r.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();

    public ReadingMethodWithTypeAnno() throws IOException {
        super(System.err);
    }

    @Test
    public void test_DeclNone_UseNone(Path base) throws IOException {
        Path libSrc = base.resolve("lib-src");
        Path libClasses = Files.createDirectories(base.resolve("lib-classes"));

        tb.writeJavaFiles(libSrc,
                          """
                          public class Lib {
                              public void test(java.lang.@Ann String s) {
                                  new Object() {};
                              }
                          }
                          """,
                          """
                          import java.lang.annotation.ElementType;
                          import java.lang.annotation.Target;
                          @Target(ElementType.TYPE_USE)
                          public @interface Ann {}
                          """);

        new JavacTask(tb)
                .outdir(libClasses)
                .files(tb.findJavaFiles(libSrc))
                .run(Expect.SUCCESS)
                .writeAll()
                .getOutput(OutputKind.DIRECT);

        Path src = base.resolve("src");
        Path classes = Files.createDirectories(base.resolve("classes"));

        tb.writeJavaFiles(src,
                          """
                          public class Test {
                          }
                          """);

        new JavacTask(tb)
                .outdir(classes)
                .classpath(libClasses)
                .files(tb.findJavaFiles(src))
                .callback(task -> {
                    task.addTaskListener(new TaskListener() {
                        @Override
                        public void finished(TaskEvent e) {
                            if (e.getKind() == TaskEvent.Kind.ENTER) {
                                task.getElements().getTypeElement("Lib");
                                task.getElements().getTypeElement("Lib$1");
                            }
                        }
                    });
                })
                .run(Expect.SUCCESS)
                .writeAll()
                .getOutput(OutputKind.DIRECT);
    }

}

