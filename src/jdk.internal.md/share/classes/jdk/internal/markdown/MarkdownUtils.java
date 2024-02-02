/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.markdown;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTreeVisitor;
import com.sun.source.doctree.RawTextTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.DCTree;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Assorted utilities related to transforming Markdown javadoc comments.
 */
public class MarkdownUtils {

    public static final char PLACEHOLDER = '\uFFFC'; // Unicode Object Replacement Character

    private MarkdownUtils() {}

    /**
     * Transforms a doc tree node.
     * Uses the transformer to perform the conversion.
     *
     * @param trees the trees utilities
     * @param tree the tree node
     * @param transformerFactory the factory to use for Javadoc transformation
     * @return a transformed tree.
     */
    public static DocCommentTree transform(DocTrees trees, DocCommentTree tree,
                                    Function<JavacTrees, DCTransformer> transformerFactory) {
        if (!(trees instanceof JavacTrees t)) {
            throw new IllegalArgumentException("class not supported: " + trees.getClass());
        }
        if (!(tree instanceof DCTree.DCDocComment dc)) {
            throw new IllegalArgumentException("class not supported: " + tree.getClass());
        }

        return isMarkdown(dc) ? transformerFactory.apply(t).transform(dc) : dc;
    }

    private static boolean isMarkdown(DocCommentTree node) {
        return isMarkdownVisitor.visitDocComment(node, null);
    }

    /**
     * A fast scanner for detecting Markdown nodes in documentation comment nodes.
     * The scanner returns as soon as any Markdown node is found.
     */
    private static final DocTreeVisitor<Boolean, Void> isMarkdownVisitor = new DocTreeScanner<>() {
        @Override
        public Boolean scan(Iterable<? extends DocTree> nodes, Void ignore) {
            if (nodes != null) {
                for (DocTree node : nodes) {
                    Boolean b = scan(node, ignore);
                    if (b == Boolean.TRUE) {
                        return b;
                    }
                }
            }
            return false;
        }

        @Override
        public Boolean scan(DocTree node, Void ignore) {
            return node != null && node.getKind() == DocTree.Kind.MARKDOWN ? Boolean.TRUE : super.scan(node, ignore);
        }

        @Override
        public Boolean reduce(Boolean r1, Boolean r2) {
            return r1 == Boolean.TRUE || r2 == Boolean.TRUE;
        }
    };

    public static boolean containsMarkdown(List<? extends DCTree> trees) {
        return trees.stream().anyMatch(t -> t.getKind() == DocTree.Kind.MARKDOWN);
    }

    public static JoinedMarkdown joinMarkdown(List<? extends DCTree> trees, DCTransformer transformer) {
        var sourceBuilder = new StringBuilder();
        var replacements = new ArrayList<>();

        /*
         * Step 1: Convert the trees into a string containing Markdown text,
         *         using Unicode Object Replacement characters to mark the positions
         *         of non-Markdown content.
         */
        for (DCTree tree : trees) {
            if (tree instanceof RawTextTree t) {
                if (t.getKind() != DocTree.Kind.MARKDOWN) {
                    throw new IllegalStateException(t.getKind().toString());
                }
                String code = t.getContent();
                // handle the (unlikely) case of any U+FFFC characters existing in the code
                int start = 0;
                int pos;
                while ((pos = code.indexOf(PLACEHOLDER, start)) != -1) {
                    replacements.add(PLACEHOLDER);
                    start = pos + 1;
                }
                sourceBuilder.append(code);
            } else {
                replacements.add(transformer.transform(tree));
                sourceBuilder.append(PLACEHOLDER);
            }
        }

        return new JoinedMarkdown(sourceBuilder.toString(), replacements);
    }

    public record JoinedMarkdown(String source, List<Object> injects) {}

    
}
