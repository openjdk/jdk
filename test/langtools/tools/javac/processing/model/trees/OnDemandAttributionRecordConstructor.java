/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8387215
 * @summary Check that javac does not report invalid errors when compiling a valid
 *          compact record constructor when an on-demand attribution is triggered
 *          by an annotation processor calling Trees.getElement(...) for identifiers
 *          inside the constructor.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.Task;

public class OnDemandAttributionRecordConstructor {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testCompactRecordConstructorWithGetElementCall() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         record Repro(String name) {
                             Repro {
                                 name = name.trim();
                             }
                         }
                         """)
                .processors(new ProcessorImpl())
                .run()
                .writeAll();
    }

    @Test
    void testCompactRecordConstructorWithoutGetElementCall() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-AskipGetElement=true")
                .sources("""
                         record Repro(String name) {
                             Repro {
                                 name = name.trim();
                             }
                         }
                         """)
                .processors(new ProcessorImpl())
                .run()
                .writeAll();
    }

    @Test
    void testCanonicalRecordConstructorWithGetElementCall() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         record Repro(String name) {
                             Repro(String name) {
                                 this.name = name.trim();
                             }
                         }
                         """)
                .processors(new ProcessorImpl())
                .run()
                .writeAll();
    }

    @Test
    void testBrokenRecordConstructorWithGetElementCall() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         record Repro(String name) {
                             Repro(String name) {
                                 super(); //illegal
                                 this.name = name.trim();
                             }
                         }
                         """)
                .processors(new ProcessorImpl())
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Repro.java:2:5: compiler.err.invalid.canonical.constructor.in.record: (compiler.misc.canonical), Repro, (compiler.misc.canonical.must.not.contain.explicit.constructor.invocation)",
                "1 error"));
    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions(ProcessorImpl.SKIP_GET_ELEMENT)
    private static class ProcessorImpl extends AbstractProcessor {

        private static final String SKIP_GET_ELEMENT = "skipGetElement";
        private Trees trees;

        @Override
        public synchronized void init(ProcessingEnvironment processingEnv) {
            super.init(processingEnv);
            trees = Trees.instance(processingEnv);
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                return false;
            }
            for (Element rootElement : roundEnv.getRootElements()) {
                TreePath rootPath = trees.getPath(rootElement);
                if (rootPath == null) {
                    continue;
                }
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitIdentifier(IdentifierTree node, Void unused) {
                        TreePath currentPath = getCurrentPath();
                        if (!skipGetElement() && insideRecordConstructor(currentPath)) {
                            processingEnv.getMessager()
                                    .printMessage(Diagnostic.Kind.NOTE,
                                                  "Calling Trees.getElement for identifier '" + node.getName()
                                                          + "' inside a record constructor");
                            trees.getElement(currentPath);
                        }
                        return super.visitIdentifier(node, unused);
                    }
                }.scan(rootPath, null);
            }
            return false;
        }

        private boolean skipGetElement() {
            return Boolean.parseBoolean(processingEnv.getOptions().get(SKIP_GET_ELEMENT));
        }

        private static boolean insideRecordConstructor(TreePath path) {
            TreePath current = path;
            while (current != null) {
                if (current.getLeaf() instanceof MethodTree method
                        && method.getReturnType() == null
                        && current.getParentPath() != null
                        && current.getParentPath().getLeaf().getKind() == Tree.Kind.RECORD) {
                    return true;
                }
                current = current.getParentPath();
            }
            return false;
        }
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
