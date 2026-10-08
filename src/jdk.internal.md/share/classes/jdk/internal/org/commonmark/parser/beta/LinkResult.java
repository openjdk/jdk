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

package jdk.internal.org.commonmark.parser.beta;

import jdk.internal.org.commonmark.internal.inline.LinkResultImpl;
import jdk.internal.org.commonmark.node.Node;

/**
 * What to do with a link/image processed by {@link LinkProcessor}.
 */
public interface LinkResult {
    /**
     * Link not handled by processor.
     */
    static LinkResult none() {
        return null;
    }

    /**
     * Wrap the link text in a node. This is the normal behavior for links, e.g. for this:
     * <pre><code>
     * [my *text*](destination)
     * </code></pre>
     * The text is {@code my *text*}, a text node and emphasis. The text is wrapped in a
     * {@link org.commonmark.node.Link} node, which means the text is added as child nodes to it.
     *
     * @param node     the node to which the link text nodes will be added as child nodes
     * @param position the position to continue parsing from
     */
    static LinkResult wrapTextIn(Node node, Position position) {
        return new LinkResultImpl(LinkResultImpl.Type.WRAP, node, position);
    }

    /**
     * Replace the link with a node. E.g. for this:
     * <pre><code>
     * [^foo]
     * </code></pre>
     * The processor could decide to create a {@code FootnoteReference} node instead which replaces the link.
     *
     * @param node     the node to replace the link with
     * @param position the position to continue parsing from
     */
    static LinkResult replaceWith(Node node, Position position) {
        return new LinkResultImpl(LinkResultImpl.Type.REPLACE, node, position);
    }

    /**
     * If a {@link LinkInfo#marker()} is present, include it in processing (i.e. treat it the same way as the brackets).
     */
    LinkResult includeMarker();
}
