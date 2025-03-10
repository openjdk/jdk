/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8298405
 * @summary Make sure MDPrinter.java compiles
 * @modules jdk.internal.md/jdk.internal.org.commonmark.node
 *          jdk.internal.md/jdk.internal.org.commonmark.parser
 * @compile MDPrinter.java
 */

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jdk.internal.org.commonmark.node.BlockQuote;
import jdk.internal.org.commonmark.node.BulletList;
import jdk.internal.org.commonmark.node.Code;
import jdk.internal.org.commonmark.node.CustomBlock;
import jdk.internal.org.commonmark.node.CustomNode;
import jdk.internal.org.commonmark.node.Document;
import jdk.internal.org.commonmark.node.Emphasis;
import jdk.internal.org.commonmark.node.FencedCodeBlock;
import jdk.internal.org.commonmark.node.HardLineBreak;
import jdk.internal.org.commonmark.node.Heading;
import jdk.internal.org.commonmark.node.HtmlBlock;
import jdk.internal.org.commonmark.node.HtmlInline;
import jdk.internal.org.commonmark.node.Image;
import jdk.internal.org.commonmark.node.IndentedCodeBlock;
import jdk.internal.org.commonmark.node.Link;
import jdk.internal.org.commonmark.node.LinkReferenceDefinition;
import jdk.internal.org.commonmark.node.ListItem;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.node.OrderedList;
import jdk.internal.org.commonmark.node.Paragraph;
import jdk.internal.org.commonmark.node.SoftLineBreak;
import jdk.internal.org.commonmark.node.SourceSpan;
import jdk.internal.org.commonmark.node.StrongEmphasis;
import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.node.ThematicBreak;
import jdk.internal.org.commonmark.node.Visitor;
import jdk.internal.org.commonmark.parser.IncludeSourceSpans;
import jdk.internal.org.commonmark.parser.Parser;

/**
 * Debug printer for CommonMark nodes.
 *
 * Requires access to {@code jdk.internal.org.commonmark.node.*}.
 *
 * Conceptually based on javac's {@code DPrinter}.
 */
public class MDPrinter {
    static class MDVisitor implements Visitor {

        MDVisitor(String source, PrintStream out) {
            this.source = source;
            lines = source == null ? null : source.lines().toList();
            this.out = out;
        }

        void visit(Node node) {
            if (node == null) {
                out.print("    ".repeat(depth));
                out.println("*null*");
            } else {
                node.accept(this);
            }
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            defaultAction(blockQuote);
        }

        @Override
        public void visit(BulletList bulletList) {
            defaultAction(bulletList);
        }

        @Override
        public void visit(Code code) {
            defaultAction(code);
        }

        @Override
        public void visit(Document document) {
            defaultAction(document);
        }

        @Override
        public void visit(Emphasis emphasis) {
            defaultAction(emphasis);
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            defaultAction(fencedCodeBlock);
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            defaultAction(hardLineBreak);
        }

        @Override
        public void visit(Heading heading) {
            defaultAction(heading);
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            defaultAction(thematicBreak);
        }

        @Override
        public void visit(HtmlInline htmlInline) {
            defaultAction(htmlInline);
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            defaultAction(htmlBlock);
        }

        @Override
        public void visit(Image image) {
            defaultAction(image);
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            defaultAction(indentedCodeBlock);
        }

        @Override
        public void visit(Link link) {
            defaultAction(link);
        }

        @Override
        public void visit(ListItem listItem) {
            defaultAction(listItem);
        }

        @Override
        public void visit(OrderedList orderedList) {
            defaultAction(orderedList);
        }

        @Override
        public void visit(Paragraph paragraph) {
            defaultAction(paragraph);
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            defaultAction(softLineBreak);
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            defaultAction(strongEmphasis);
        }

        @Override
        public void visit(Text text) {
            defaultAction(text);
        }

        @Override
        public void visit(LinkReferenceDefinition linkReferenceDefinition) {
            defaultAction(linkReferenceDefinition);
        }

        @Override
        public void visit(CustomBlock customBlock) {
            defaultAction(customBlock);
        }

        @Override
        public void visit(CustomNode customNode) {
            defaultAction(customNode);
        }

        private final String source;
        private final List<String> lines;
        private final PrintStream out;

        int depth = 0;

        protected void defaultAction(Node node) {
            out.print("    ".repeat(depth));
            out.print(node.getClass().getSimpleName());
            out.print(" ");
            out.println(abbrev(node.toString(), 64));
            int i = 0;
            for (var ss : node.getSourceSpans()) {
                out.print("    ".repeat(depth));
                out.print("  span[" + (i++) + "]: " + ss);
                out.println(abbrev(source(ss).replace(' ', '_').replace('\n', '|'), 64));
            }
            depth++;
            try {
                for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
                    c.accept(this);
                }
            } finally {
                depth--;
            }
        }

        String source(SourceSpan ss) {
            if (source == null) {
                return "no source";
            }
            if (ss.getLineIndex() >= lines.size()) {
                return "error: insufficient lines [" + lines.size() + "]";
            }
            String line = lines.get(ss.getLineIndex());
            if (ss.getColumnIndex() + ss.getLength() > line.length()) {
                return "error: bounds";
            }
            return line.substring(ss.getColumnIndex(), ss.getColumnIndex() + ss.getLength());
        }

        String abbrev(String s, int maxLen) {
            return s.length() < maxLen ? s
                    : s.substring(0, maxLen / 2) + "..." + s.substring(s.length() - maxLen / 2);

        }
    }

    public static void main(String... args) throws IOException {
        show(Files.readString(Path.of(args[0])));
    }

    public static void show(String source) {
        Parser parser = Parser.builder()
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build();
        Node document = parser.parse(source);

        show(document, source, System.err);
    }

    public static void show(Node node, String source, PrintStream out) {
        new MDVisitor(source, out).visit(node);
    }

}
