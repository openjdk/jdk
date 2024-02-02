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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTrees;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.DCTree;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import jdk.internal.org.commonmark.node.Node;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import jdk.internal.org.commonmark.ext.gfm.tables.TablesExtension;
import jdk.internal.org.commonmark.parser.IncludeSourceSpans;
import jdk.internal.org.commonmark.parser.Parser;
import jdk.internal.org.commonmark.renderer.html.HtmlRenderer;

/**
 * A class to transform a {@code DocTree} node into a similar one with
 * Markdown converted to HTML.
 */
public class ToHTMLMarkdownTransformer extends MarkdownTransformer {

    /**
     * Public no-args constructor, suitable for use with {@link java.util.ServiceLoader}.
     */
    public ToHTMLMarkdownTransformer() { }

    public String name() {
        return "markdown2html";
    }

    @Override @DefinedBy(DefinedBy.Api.COMPILER_TREE)
    public DocCommentTree transform(DocTrees trees, DocCommentTree tree) {
        return MarkdownUtils.transform(trees, tree, javacTrees -> new ToHTMLTransformer(javacTrees));
    }

    private static class ToHTMLTransformer extends DCTransformer {

        private final JavacTrees treeUtils;

        public ToHTMLTransformer(JavacTrees treeUtils) {
            super(treeUtils);
            this.treeUtils = treeUtils;
        }

        @Override
        public List<? extends DCTree> transform(List<? extends DCTree> trees) {
            if (MarkdownUtils.containsMarkdown(trees)) {
                /*
                 * Step 1: Convert the trees into a string containing Markdown text,
                 *         using Unicode Object Replacement characters to mark the positions
                 *         of non-Markdown content.
                 */
                MarkdownUtils.JoinedMarkdown joinedMarkdowns = MarkdownUtils.joinMarkdown(trees, this);

                /*
                 * Step 2: Build a parser, and configure it to accept additional syntactic constructs,
                 *         such as reference-style links to program elements.
                 */
                String source = joinedMarkdowns.source();
                Parser parser = Parser.builder()
                        .extensions(List.of(TablesExtension.create()))
                        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                        .build();
                Node document = parser.parse(source);

                /*
                 * Step 3: Convert the parsed Markdown to HTML:
                 */
                String htmlWithPlaceHolders = stripParagraphs(HtmlRenderer.builder().build().render(document));

                List<DCTree> result = new ArrayList<>();

                try {
                    SimpleJavaFileObject fo = new SimpleJavaFileObject(new URI("mem:///doc.html"), JavaFileObject.Kind.HTML) {
                        @Override @DefinedBy(Api.COMPILER)
                        public CharSequence getCharContent(boolean ignoreEncodingErrors)
                                throws IOException {
                            return "<body>" + htmlWithPlaceHolders + "</body>";
                        }
                    };

                    DocCommentTree parsedComment = treeUtils.getDocCommentTree(fo);

                    for (DocTree orig : parsedComment.getFullBody()) {
                        DCTree tree = (DCTree) orig;

                        if (tree.getKind() == Kind.TEXT) {
                            TextTree tt = (TextTree) tree;
                            if (tt.getBody().contains("" + MarkdownUtils.PLACEHOLDER)) {
                                boolean first = true;
                                for (String part : tt.getBody().split(PLACEHOLDER_PATTERN, -1)) {
                                    if (!first) {
                                        Object inject = joinedMarkdowns.injects().remove(0);
                                        if (inject instanceof DCTree t) {
                                            result.add(t);
                                        } else {
                                            result.add(m.newTextTree(inject.toString()));
                                        }
                                    } else {
                                        first = false;
                                    }
                                    result.add(treeUtils.getDocTreeFactory().newTextTree(part));
                                }
                                continue;
                            }
                        }
                        result.add(tree);
                    }

                    return result;
                } catch (URISyntaxException ex) {
                    throw new IllegalStateException(ex);
                }
            } else {
                return super.transform(trees);
            }
        }
    }

    private static String stripParagraphs(String input) {
        input = input.replace("</p>", "");

        if (input.startsWith("<p>")) {
            input = input.substring(3);
        }

        return input;
    }

    private static final String PLACEHOLDER_PATTERN = Pattern.quote("" + MarkdownUtils.PLACEHOLDER);

}
