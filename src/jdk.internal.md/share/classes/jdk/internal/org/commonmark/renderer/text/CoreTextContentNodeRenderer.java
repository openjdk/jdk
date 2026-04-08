/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.renderer.text;

import jdk.internal.org.commonmark.node.*;
import jdk.internal.org.commonmark.renderer.NodeRenderer;

import java.util.Set;

/**
 * The node renderer that renders all the core nodes (comes last in the order of node renderers).
 */
public class CoreTextContentNodeRenderer extends AbstractVisitor implements NodeRenderer {

    protected final TextContentNodeRendererContext context;
    private final TextContentWriter textContent;

    private ListHolder listHolder;

    public CoreTextContentNodeRenderer(TextContentNodeRendererContext context) {
        this.context = context;
        this.textContent = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(
                Document.class,
                Heading.class,
                Paragraph.class,
                BlockQuote.class,
                BulletList.class,
                FencedCodeBlock.class,
                HtmlBlock.class,
                ThematicBreak.class,
                IndentedCodeBlock.class,
                Link.class,
                ListItem.class,
                OrderedList.class,
                Image.class,
                Emphasis.class,
                StrongEmphasis.class,
                Text.class,
                Code.class,
                HtmlInline.class,
                SoftLineBreak.class,
                HardLineBreak.class
        );
    }

    @Override
    public void render(Node node) {
        node.accept(this);
    }

    @Override
    public void visit(Document document) {
        // No rendering itself
        visitChildren(document);
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        // LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
        textContent.write('\u00AB');
        visitChildren(blockQuote);
        textContent.resetBlock();
        // RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
        textContent.write('\u00BB');

        textContent.block();
    }

    @Override
    public void visit(BulletList bulletList) {
        textContent.pushTight(bulletList.isTight());
        listHolder = new BulletListHolder(listHolder, bulletList);
        visitChildren(bulletList);
        textContent.popTight();
        textContent.block();
        listHolder = listHolder.getParent();
    }

    @Override
    public void visit(Code code) {
        textContent.write('\"');
        textContent.write(code.getLiteral());
        textContent.write('\"');
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {
        var literal = stripTrailingNewline(fencedCodeBlock.getLiteral());
        if (stripNewlines()) {
            textContent.writeStripped(literal);
        } else {
            textContent.write(literal);
        }
        textContent.block();
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        if (stripNewlines()) {
            textContent.whitespace();
        } else {
            textContent.line();
        }
    }

    @Override
    public void visit(Heading heading) {
        visitChildren(heading);
        if (stripNewlines()) {
            textContent.write(": ");
        } else {
            textContent.block();
        }
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
        if (!stripNewlines()) {
            textContent.write("***");
        }
        textContent.block();
    }

    @Override
    public void visit(HtmlInline htmlInline) {
        writeText(htmlInline.getLiteral());
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
        writeText(htmlBlock.getLiteral());
    }

    @Override
    public void visit(Image image) {
        writeLink(image, image.getTitle(), image.getDestination());
    }

    @Override
    public void visit(IndentedCodeBlock indentedCodeBlock) {
        var literal = stripTrailingNewline(indentedCodeBlock.getLiteral());
        if (stripNewlines()) {
            textContent.writeStripped(literal);
        } else {
            textContent.write(literal);
        }
        textContent.block();
    }

    @Override
    public void visit(Link link) {
        writeLink(link, link.getTitle(), link.getDestination());
    }

    @Override
    public void visit(ListItem listItem) {
        if (listHolder != null && listHolder instanceof OrderedListHolder) {
            var orderedListHolder = (OrderedListHolder) listHolder;
            var marker = orderedListHolder.getCounter() + orderedListHolder.getDelimiter();
            var spaces = " ";
            textContent.write(marker);
            textContent.write(spaces);
            textContent.pushPrefix(repeat(" ", marker.length() + spaces.length()));
            visitChildren(listItem);
            textContent.block();
            textContent.popPrefix();
            orderedListHolder.increaseCounter();
        } else if (listHolder != null && listHolder instanceof BulletListHolder) {
            BulletListHolder bulletListHolder = (BulletListHolder) listHolder;
            if (!stripNewlines()) {
                var marker = bulletListHolder.getMarker();
                var spaces = " ";
                textContent.write(marker);
                textContent.write(spaces);
                textContent.pushPrefix(repeat(" ", marker.length() + spaces.length()));
            }
            visitChildren(listItem);
            textContent.block();
            if (!stripNewlines()) {
                textContent.popPrefix();
            }
        }
    }

    @Override
    public void visit(OrderedList orderedList) {
        textContent.pushTight(orderedList.isTight());
        listHolder = new OrderedListHolder(listHolder, orderedList);
        visitChildren(orderedList);
        textContent.popTight();
        textContent.block();
        listHolder = listHolder.getParent();
    }

    @Override
    public void visit(Paragraph paragraph) {
        visitChildren(paragraph);
        textContent.block();
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        if (stripNewlines()) {
            textContent.whitespace();
        } else {
            textContent.line();
        }
    }

    @Override
    public void visit(Text text) {
        writeText(text.getLiteral());
    }

    @Override
    protected void visitChildren(Node parent) {
        Node node = parent.getFirstChild();
        while (node != null) {
            Node next = node.getNext();
            context.render(node);
            node = next;
        }
    }

    private void writeText(String text) {
        if (stripNewlines()) {
            textContent.writeStripped(text);
        } else {
            textContent.write(text);
        }
    }

    private void writeLink(Node node, String title, String destination) {
        boolean hasChild = node.getFirstChild() != null;
        boolean hasTitle = title != null && !title.equals(destination);
        boolean hasDestination = destination != null && !destination.equals("");

        if (hasChild) {
            textContent.write('"');
            visitChildren(node);
            textContent.write('"');
            if (hasTitle || hasDestination) {
                textContent.whitespace();
                textContent.write('(');
            }
        }

        if (hasTitle) {
            textContent.write(title);
            if (hasDestination) {
                textContent.colon();
                textContent.whitespace();
            }
        }

        if (hasDestination) {
            textContent.write(destination);
        }

        if (hasChild && (hasTitle || hasDestination)) {
            textContent.write(')');
        }
    }

    private boolean stripNewlines() {
        return context.lineBreakRendering() == LineBreakRendering.STRIP;
    }

    private static String stripTrailingNewline(String s) {
        if (s.endsWith("\n")) {
            return s.substring(0, s.length() - 1);
        } else {
            return s;
        }
    }

    // Keep for Android compat (String.repeat only available on Android 12 and later)
    private static String repeat(String s, int count) {
        var sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static class BulletListHolder extends ListHolder {
        private final String marker;

        public BulletListHolder(ListHolder parent, BulletList list) {
            super(parent);
            marker = list.getMarker();
        }

        public String getMarker() {
            return marker;
        }
    }

    private abstract static class ListHolder {
        private final ListHolder parent;

        ListHolder(ListHolder parent) {
            this.parent = parent;
        }

        public ListHolder getParent() {
            return parent;
        }
    }

    private static class OrderedListHolder extends ListHolder {
        private final String delimiter;
        private int counter;

        public OrderedListHolder(ListHolder parent, OrderedList list) {
            super(parent);
            delimiter = list.getMarkerDelimiter() != null ? list.getMarkerDelimiter() : ".";
            counter = list.getMarkerStartNumber() != null ? list.getMarkerStartNumber() : 1;
        }

        public String getDelimiter() {
            return delimiter;
        }

        public int getCounter() {
            return counter;
        }

        public void increaseCounter() {
            counter++;
        }
    }
}
