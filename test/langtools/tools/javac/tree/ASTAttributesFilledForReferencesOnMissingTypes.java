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
 * @bug 8335385
 * @summary Verify that BadClassFile related to imports are handled properly.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main ASTAttributesFilledForReferencesOnMissingTypes
 */

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import javax.lang.model.element.Element;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class ASTAttributesFilledForReferencesOnMissingTypes {
    public static void main(String... args) throws Exception {
        new ASTAttributesFilledForReferencesOnMissingTypes().run();
    }

    ToolBox tb = new ToolBox();

    void run() throws Exception {
        new JavacTask(tb)
          .outdir(".")
          .sources("package p; public class A { }",
                   "package p; public class B { public static class I { } public static class M { } }",
                   "package p; public class C { }")
          .run()
          .writeAll();

        try (OutputStream out = Files.newOutputStream(Paths.get(".", "p", "A.class"))) {
            out.write("broken".getBytes("UTF-8"));
        }

        try (OutputStream out = Files.newOutputStream(Paths.get(".", "p", "B$I.class"))) {
            out.write("broken".getBytes("UTF-8"));
        }

        Files.delete(Paths.get(".", "p", "C.class"));
        Files.delete(Paths.get(".", "p", "B$M.class"));

        //tests for findIdent (must be in some global scope):
        doTest("""
               package p;
               public class Test {
                   A a;
               }
               """,
               "Test.java:3:5: compiler.err.cant.access: p.A, (compiler.misc.bad.class.file.header: A.class, (compiler.misc.illegal.start.of.class.file))",
               "1 error");
        doTest("""
               import p.*;
               public class Test {
                   A a;
               }
               """,
               "Test.java:3:5: compiler.err.cant.access: p.A, (compiler.misc.bad.class.file.header: A.class, (compiler.misc.illegal.start.of.class.file))",
               "1 error");
        doTest("""
               package p;
               public class Test {
                   C c;
               }
               """,
               "Test.java:3:5: compiler.err.cant.resolve.location: kindname.class, C, , , (compiler.misc.location: kindname.class, p.Test, null)",
               "1 error");
        doTest("""
               import p.*;
               public class Test {
                   C c;
               }
               """,
               "Test.java:3:5: compiler.err.cant.resolve.location: kindname.class, C, , , (compiler.misc.location: kindname.class, Test, null)",
               "1 error");

        //tests for findIdentInPackage:
        doTest("""
               import p.A;
               public class Test {
                   A a;
               }
               """,
               "Test.java:1:9: compiler.err.cant.access: p.A, (compiler.misc.bad.class.file.header: A.class, (compiler.misc.illegal.start.of.class.file))",
               "Test.java:3:5: compiler.err.cant.resolve.location: kindname.class, A, , , (compiler.misc.location: kindname.class, Test, null)",
               "2 errors");
        doTest("""
               public class Test {
                   p.A a;
               }
               """,
               "Test.java:2:6: compiler.err.cant.access: p.A, (compiler.misc.bad.class.file.header: A.class, (compiler.misc.illegal.start.of.class.file))",
               "1 error");
        doTest("""
               import p.C;
               public class Test {
                   C c;
               }
               """,
               "Test.java:1:9: compiler.err.cant.resolve.location: kindname.class, C, , , (compiler.misc.location: kindname.package, p, null)",
               "Test.java:3:5: compiler.err.cant.resolve.location: kindname.class, C, , , (compiler.misc.location: kindname.class, Test, null)",
               "2 errors");
        doTest("""
               public class Test {
                   p.C c;
               }
               """,
               "Test.java:2:6: compiler.err.cant.resolve.location: kindname.class, C, , , (compiler.misc.location: kindname.package, p, null)",
               "1 error");

        //tests for findIdentInType:
        doTest("""
               import p.B.I;
               public class Test {
                   I i;
               }
               """,
               "Test.java:1:11: compiler.err.cant.access: p.B.I, (compiler.misc.bad.class.file.header: B$I.class, (compiler.misc.illegal.start.of.class.file))",
               "Test.java:3:5: compiler.err.cant.resolve.location: kindname.class, I, , , (compiler.misc.location: kindname.class, Test, null)",
               "2 errors");
        doTest("""
               import p.B.M;
               public class Test {
                   M m;
               }
               """,
               "Test.java:1:11: compiler.err.cant.access: p.B.M, (compiler.misc.class.file.not.found: p.B$M)",
               "Test.java:3:5: compiler.err.cant.resolve.location: kindname.class, M, , , (compiler.misc.location: kindname.class, Test, null)",
               "2 errors");
        doTest("""
               public class Test {
                   p.B.I i;
               }
               """,
               "Test.java:2:8: compiler.err.cant.access: p.B.I, (compiler.misc.bad.class.file.header: B$I.class, (compiler.misc.illegal.start.of.class.file))",
               "1 error");
        doTest("""
               public class Test {
                   p.B.M m;
               }
               """,
               "Test.java:2:8: compiler.err.cant.access: p.B.M, (compiler.misc.class.file.not.found: p.B$M)",
               "1 error");
    }

    void doTest(String code, String... expectedOutput) {
        List<String> log = new JavacTask(tb)
                .classpath(".")
                .sources(code)
                .options("-XDrawDiagnostics")
                .callback(task -> {
                    task.addTaskListener(new TaskListener() {
                        @Override
                        public void finished(TaskEvent e) {
                            if (e.getKind() != TaskEvent.Kind.ANALYZE) {
                                return ;
                            }
                            Trees trees = Trees.instance(task);
                            new TreePathScanner<Void, Void>() {
                                @Override
                                public Void visitIdentifier(IdentifierTree node, Void p) {
                                    validateAttributes();
                                    return super.visitIdentifier(node, p);
                                }
                                @Override
                                public Void visitMemberSelect(MemberSelectTree node, Void p) {
                                    if (!node.getIdentifier().contentEquals("*")) {
                                        validateAttributes();
                                    }
                                    return super.visitMemberSelect(node, p);
                                }
                                void validateAttributes() {
                                    Element el = trees.getElement(getCurrentPath());
                                    if (el == null) {
                                        throw new AssertionError("A null sym attribute for: " + getCurrentPath().getLeaf() + "!");
                                    }
                                }
                            }.scan(e.getCompilationUnit(), null);
                        }
                    });
                })
                .run(expectedOutput != null ? Task.Expect.FAIL : Task.Expect.SUCCESS,
                     expectedOutput != null ? 1 : 0)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        if (expectedOutput != null && !log.equals(Arrays.asList(expectedOutput))) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }
}
