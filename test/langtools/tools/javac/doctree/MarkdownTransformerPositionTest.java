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
 * @bug 8332858
 * @summary test case for Markdown positions
 * @run main/othervm --limit-modules jdk.compiler MarkdownTransformerPositionTest
 * @run main MarkdownTransformerPositionTest links
 */

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.RawTextTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.tree.*;
import com.sun.source.util.*;

import java.net.URI;
import java.util.*;
import javax.lang.model.element.Element;
import javax.tools.*;

/*
 * Custom test case for positions of RawTextTree nodes after passing through
 * main MarkdownTransformer, or not.
 * See https://github.com/openjdk/jdk/pull/16388#discussion_r1479306878
 */
public class MarkdownTransformerPositionTest {

    public static void main(String... args) throws Exception {
        MarkdownTransformerPositionTest t = new MarkdownTransformerPositionTest();

        t.simpleTest();
        t.testWithReplacements();

        if (args.length > 0 && "links".equals(args[0])) {
            t.linkWithEscapes();
        }
    }

    private void simpleTest() throws Exception {
        runTest("""
                /// Markdown test
                ///
                /// @author testAuthor
                public class Test {
                }
                """,
                "Markdown test",
                "testAuthor");
    }

    private void testWithReplacements() throws Exception {
        runTest("""
                /// Markdown \uFFFC test \uFFFC with \uFFFC replacements.
                ///
                /// @author testAuthor
                public class Test {
                }
                """,
                "Markdown \uFFFC test \uFFFC with \uFFFC replacements.",
                "testAuthor");
    }

    private void linkWithEscapes() throws Exception {
        runConvertedLinksTest("""
                /// Markdown comment.
                /// [java.util.Arrays#asList(Object\\[\\])]
                public class Test {
                }
                """,
                "java.util.Arrays#asList(Object\\[\\])");
    }

    private void runTest(String source, String... expectedRawSpans) throws Exception {
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        JavacTask task = (JavacTask)comp.getTask(null, null, null, null, null, Arrays.asList(new JavaSource(source)));
        CompilationUnitTree cu = task.parse().iterator().next();
        task.analyze();
        DocTrees trees = DocTrees.instance(task);
        List<String> rawSpans = new ArrayList<>();
        TreePath clazzTP = new TreePath(new TreePath(cu), cu.getTypeDecls().get(0));
        Element clazz = trees.getElement(clazzTP);
        DocCommentTree docComment = trees.getDocCommentTree(clazz);

        new DocTreeScanner<Void, Void>() {
            @Override
            public Void visitRawText(RawTextTree node, Void p) {
                int start = (int) trees.getSourcePositions().getStartPosition(cu, docComment, node);
                int end = (int) trees.getSourcePositions().getEndPosition(cu, docComment, node);
                rawSpans.add(source.substring(start, end));
                return super.visitRawText(node, p);
            }
        }.scan(docComment, null);

        List<String> expectedRawSpansList = List.of(expectedRawSpans);

        if (!expectedRawSpansList.equals(rawSpans)) {
            throw new AssertionError("Incorrect raw text spans, should be: " +
                    expectedRawSpansList + ", but is: " + rawSpans);
        }

        System.err.println("Test result: success, boot modules: " + ModuleLayer.boot().modules());
    }

    private void runConvertedLinksTest(String source, String... expectedRawSpans) throws Exception {
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        JavacTask task = (JavacTask)comp.getTask(null, null, null, null, null, Arrays.asList(new JavaSource(source)));
        CompilationUnitTree cu = task.parse().iterator().next();
        task.analyze();
        DocTrees trees = DocTrees.instance(task);
        List<String> rawSpans = new ArrayList<>();
        TreePath clazzTP = new TreePath(new TreePath(cu), cu.getTypeDecls().get(0));
        Element clazz = trees.getElement(clazzTP);
        DocCommentTree docComment = trees.getDocCommentTree(clazz);

        new DocTreeScanner<Void, Void>() {
            @Override
            public Void visitLink(LinkTree node, Void p) {
                int start = (int) trees.getSourcePositions().getStartPosition(cu, docComment, node);
                if (start != (-1)) {
                    throw new AssertionError("UNexpected start position for synthetic link: " + start);
                }
                return super.visitLink(node, p);
            }

            @Override
            public Void visitReference(ReferenceTree node, Void p) {
                int start = (int) trees.getSourcePositions().getStartPosition(cu, docComment, node);
                int end = (int) trees.getSourcePositions().getEndPosition(cu, docComment, node);
                rawSpans.add(source.substring(start, end));
                return super.visitReference(node, p);
            }
        }.scan(docComment, null);

        List<String> expectedRawSpansList = List.of(expectedRawSpans);

        if (!expectedRawSpansList.equals(rawSpans)) {
            throw new AssertionError("Incorrect raw text spans, should be: " +
                    expectedRawSpansList + ", but is: " + rawSpans);
        }

        System.err.println("Test result: success, boot modules: " + ModuleLayer.boot().modules());
    }

    static class JavaSource extends SimpleJavaFileObject {

        private final String source;

        public JavaSource(String source) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}