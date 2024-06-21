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

package jdk.internal.org.commonmark.ext.gfm.tables.internal;

import jdk.internal.org.commonmark.ext.gfm.tables.*;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.renderer.NodeRenderer;
import jdk.internal.org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import jdk.internal.org.commonmark.renderer.markdown.MarkdownWriter;
import jdk.internal.org.commonmark.text.AsciiMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * The Table node renderer that is needed for rendering GFM tables (GitHub Flavored Markdown) to text content.
 */
public class TableMarkdownNodeRenderer extends TableNodeRenderer implements NodeRenderer {
    private final MarkdownWriter writer;
    private final MarkdownNodeRendererContext context;

    private final AsciiMatcher pipe = AsciiMatcher.builder().c('|').build();

    private final List<TableCell.Alignment> columns = new ArrayList<>();

    public TableMarkdownNodeRenderer(MarkdownNodeRendererContext context) {
        this.writer = context.getWriter();
        this.context = context;
    }

    @Override
    protected void renderBlock(TableBlock node) {
        columns.clear();
        writer.pushTight(true);
        renderChildren(node);
        writer.popTight();
        writer.block();
    }

    @Override
    protected void renderHead(TableHead node) {
        renderChildren(node);
        for (TableCell.Alignment columnAlignment : columns) {
            writer.raw('|');
            if (columnAlignment == TableCell.Alignment.LEFT) {
                writer.raw(":---");
            } else if (columnAlignment == TableCell.Alignment.RIGHT) {
                writer.raw("---:");
            } else if (columnAlignment == TableCell.Alignment.CENTER) {
                writer.raw(":---:");
            } else {
                writer.raw("---");
            }
        }
        writer.raw("|");
        writer.block();
    }

    @Override
    protected void renderBody(TableBody node) {
        renderChildren(node);
    }

    @Override
    protected void renderRow(TableRow node) {
        renderChildren(node);
        // Trailing | at the end of the line
        writer.raw("|");
        writer.block();
    }

    @Override
    protected void renderCell(TableCell node) {
        if (node.getParent() != null && node.getParent().getParent() instanceof TableHead) {
            columns.add(node.getAlignment());
        }
        writer.raw("|");
        writer.pushRawEscape(pipe);
        renderChildren(node);
        writer.popRawEscape();
    }

    private void renderChildren(Node parent) {
        Node node = parent.getFirstChild();
        while (node != null) {
            Node next = node.getNext();
            context.render(node);
            node = next;
        }
    }
}
