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
 * @bug 8328481
 * @summary Verify the Trees model for module imports
 * @library /tools/lib
 * @modules java.logging
 *          java.sql
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main Imports
*/

import com.sun.source.tree.ImportTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class Imports extends TestRunner {

    private static final String SOURCE_VERSION = System.getProperty("java.specification.version");
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new Imports().runTests();
    }

    Imports() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testModuleImport(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module java.base;
                          public class Test {
                          }
                          """);

        Files.createDirectories(classes);

        AtomicInteger seenImports = new AtomicInteger(-1);

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .callback(task -> {
                task.addTaskListener(new TaskListener() {
                    @Override
                    public void finished(TaskEvent e) {
                        if (e.getKind() != Kind.PARSE) {
                            return ;
                        }

                        var imports = e.getCompilationUnit().getImports();

                        seenImports.set(imports.size());

                        if (imports.size() != 1) {
                            throw new AssertionError("Exception 1 import, " +
                                                     "but got: " + imports.size());
                        }

                        ImportTree it = imports.get(0);

                        if (!it.isModule()) {
                            throw new AssertionError("Expected module import, but got ordinary one.");
                        }

                        if (!"java.base".equals(it.getQualifiedIdentifier().toString())) {
                            throw new AssertionError("Expected module import for java.base, " +
                                                     "but got: " + it.getQualifiedIdentifier());
                        }

                        String expectedImportToString = "import module java.base;\n";
                        String actualImportToString = it.toString()
                                                        .replaceAll("\\R", "\n");

                        if (!expectedImportToString.equals(actualImportToString)) {
                            throw new AssertionError("Expected '" + expectedImportToString + "', " +
                                                     "but got: '" + it + "'");
                        }
                    }
                });
            })
            .run(Task.Expect.SUCCESS);

        if (seenImports.get() == (-1)) {
            throw new AssertionError("Did not verify any imports!");
        }
    }

}
