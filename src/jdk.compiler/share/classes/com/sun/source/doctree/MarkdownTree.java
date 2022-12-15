/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.source.doctree;

/**
 * A tree node for a fragment of Markdown code.
 *
 * <p>
 * The code may contain plain text, entities and HTML elements,
 * all represented directly in the text of the code,
 * but not {@linkplain InlineTagTree inline tags}.
 *
 * @apiNote
 * {@code MarkdownTree} nodes will typically exist in a list of
 * {@code DocTree} nodes, along with other kinds of {@code DocTree}
 * nodes, such as for inline tags. When processing any such list,
 * any non-Markdown nodes will be processed recursively first, and then
 * treated as opaque objects within the remaining stream of Markdown nodes.
 * Thus, the content of any non-Markdown nodes will not affect how the
 * Markdown nodes will be processed.
 *
 * Note: malformed entities and HTML elements will typically be
 * processed as literal text, with no warnings or errors being
 * reported.
 *
 * @since 21
 */
public interface MarkdownTree extends DocTree {
    /**
     * {@return the code}
     */
    String getCode();
}
