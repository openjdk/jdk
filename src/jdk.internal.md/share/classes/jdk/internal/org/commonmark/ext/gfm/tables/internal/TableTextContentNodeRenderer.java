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

import jdk.internal.org.commonmark.ext.gfm.tables.TableBlock;
import jdk.internal.org.commonmark.ext.gfm.tables.TableBody;
import jdk.internal.org.commonmark.ext.gfm.tables.TableCell;
import jdk.internal.org.commonmark.ext.gfm.tables.TableHead;
import jdk.internal.org.commonmark.ext.gfm.tables.TableRow;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.renderer.text.TextContentNodeRendererContext;
import jdk.internal.org.commonmark.renderer.text.TextContentWriter;

/**
 * The Table node renderer that is needed for rendering GFM tables (GitHub Flavored Markdown) to text content.
 */
public class TableTextContentNodeRenderer extends TableNodeRenderer {

    private final TextContentWriter textContentWriter;
    private final TextContentNodeRendererContext context;

    public TableTextContentNodeRenderer(TextContentNodeRendererContext context) {
        this.textContentWriter = context.getWriter();
        this.context = context;
    }

    @Override
    protected void renderBlock(TableBlock tableBlock) {
        // Render rows tight
        textContentWriter.pushTight(true);
        renderChildren(tableBlock);
        textContentWriter.popTight();
        textContentWriter.block();
    }

    @Override
    protected void renderHead(TableHead tableHead) {
        renderChildren(tableHead);
    }

    @Override
    protected void renderBody(TableBody tableBody) {
        renderChildren(tableBody);
    }

    @Override
    protected void renderRow(TableRow tableRow) {
        renderChildren(tableRow);
        textContentWriter.block();
    }

    @Override
    protected void renderCell(TableCell tableCell) {
        renderChildren(tableCell);
        // For the last cell in row, don't render the delimiter
        if (tableCell.getNext() != null) {
            textContentWriter.write('|');
            textContentWriter.whitespace();
        }
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
