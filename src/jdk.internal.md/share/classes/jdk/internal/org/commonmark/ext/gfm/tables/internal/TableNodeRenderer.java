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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jdk.internal.org.commonmark.ext.gfm.tables.TableBlock;
import jdk.internal.org.commonmark.ext.gfm.tables.TableBody;
import jdk.internal.org.commonmark.ext.gfm.tables.TableCell;
import jdk.internal.org.commonmark.ext.gfm.tables.TableHead;
import jdk.internal.org.commonmark.ext.gfm.tables.TableRow;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.renderer.NodeRenderer;

abstract class TableNodeRenderer implements NodeRenderer {

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return new HashSet<>(Arrays.asList(
                TableBlock.class,
                TableHead.class,
                TableBody.class,
                TableRow.class,
                TableCell.class
        ));
    }

    @Override
    public void render(Node node) {
        if (node instanceof TableBlock) {
            renderBlock((TableBlock) node);
        } else if (node instanceof TableHead) {
            renderHead((TableHead) node);
        } else if (node instanceof TableBody) {
            renderBody((TableBody) node);
        } else if (node instanceof TableRow) {
            renderRow((TableRow) node);
        } else if (node instanceof TableCell) {
            renderCell((TableCell) node);
        }
    }

    protected abstract void renderBlock(TableBlock node);

    protected abstract void renderHead(TableHead node);

    protected abstract void renderBody(TableBody node);

    protected abstract void renderRow(TableRow node);

    protected abstract void renderCell(TableCell node);
}
