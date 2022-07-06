/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8273244
 * @summary check coverage of tools/javac/doctree tests
 * @modules jdk.compiler
 * @run main CoverageTest
 */

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Name;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scans for tests in the same source directory that use the DocCommentTester framework,
 * and for those tests, scans for doc comments, counting the number of occurrences
 * of different kinds of DocTree nodes.  The number are mostly just informational,
 * except for the (implicit) zeroes, indicating that no instances of a kind of tree
 * have been detected ... which is a hole in the coverage.
 *
 * If desired, we could enhance the DocTreeScanner to track different variants of
 * specific kinds of tree nodes.
 */
public class CoverageTest {
    public static void main(String... args) throws Exception {
        new CoverageTest().run(args);
    }

    void run(String... args) throws Exception {
        Path src = Path.of(System.getProperty("test.src"));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);

        List<JavaFileObject> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(src, this::isDocCommentTesterTest)) {
            for (Path p : ds) {
                files.add(fm.getJavaFileObjects(p).iterator().next());
            }
        }

        JavacTask task = (JavacTask) compiler.getTask(null, null, null, null, null, files);

        DocTrees trees = DocTrees.instance(task);
        Map<DocTree.Kind, Integer> counts = new TreeMap<>();
        DocTreeScanner dtScanner = new DocTreeScanner() {
            @Override
            public Object scan(DocTree node, Object o) {
                if (node != null) {
                    DocTree.Kind k = node.getKind();
                    counts.put(k, counts.computeIfAbsent(k, k_ -> 0) + 1);
                }
                return super.scan(node, o);
            }
        };

        TreePathScanner declScanner = new DeclScanner() {
            @Override
            void visitDecl(Tree tree, Name name) {
                TreePath path = getCurrentPath();
                DocCommentTree dc = trees.getDocCommentTree(path);
                dtScanner.scan(dc, null);
            }
        };


        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.PARSE) {
                    declScanner.scan(e.getCompilationUnit(), null);
                }
            }
        });

        task.parse();

        counts.forEach((k, v) -> System.err.printf("%20s: %5d%n", k, v));

        // Note: DOC_TYPE cannot appear in any doc comment in a *.java file,
        // and OTHER is a special value that never appears in any standard DocTree node.
        List<DocTree.Kind> notFound = Stream.of(DocTree.Kind.values())
                .filter(k -> switch (k) { case DOC_TYPE, OTHER -> false; default -> true; })
                .filter(k -> !counts.containsKey(k))
                .toList();

        if (!notFound.isEmpty()) {
            System.err.println();
            System.err.println("ERROR: The following kinds were not found: " + notFound.stream()
                    .map(DocTree.Kind::name)
                    .collect(Collectors.joining(", ")));
            System.err.println();
            throw new Exception("Not Found: " + notFound);
        }
    }

    boolean isDocCommentTesterTest(Path p) throws IOException {
        if (!p.getFileName().toString().endsWith(".java")) {
            return false;
        }

        String marker = " * @run main DocCommentTester " + p.getFileName();
        for (String line : Files.readAllLines(p)) {
            if (line.equals(marker)) {
                return true;
            } else if (line.contains("{")) {
                return false;
            }
        }
        return false;
    }

    static abstract class DeclScanner extends TreePathScanner<Void, Void> {
        abstract void visitDecl(Tree tree, Name name);

        @Override
        public Void visitClass(ClassTree tree, Void ignore) {
            super.visitClass(tree, ignore);
            visitDecl(tree, tree.getSimpleName());
            return null;
        }

        @Override
        public Void visitMethod(MethodTree tree, Void ignore) {
            super.visitMethod(tree, ignore);
            visitDecl(tree, tree.getName());
            return null;
        }

        @Override
        public Void visitVariable(VariableTree tree, Void ignore) {
            super.visitVariable(tree, ignore);
            visitDecl(tree, tree.getName());
            return null;
        }
    }
}
